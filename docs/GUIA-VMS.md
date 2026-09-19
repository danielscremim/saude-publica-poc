# Guia — Bateria de testes nas 2 VMs (ambiente Multitrans)

**Por que 2 VMs:** nos testes anteriores o k6 rodou como Job dentro do mesmo nó dos
serviços. Separar o gerador de carga do sistema sob teste elimina a disputa de CPU e
torna as medidas atribuíveis à arquitetura (declare isso na Metodologia).

---

## 0. O ponto de partida

| | VM-1 · `multitransclister` · 192.168.5.3 | VM-2 · `multiransk6` · 192.168.5.4 |
|---|---|---|
| Papel | Sistema sob teste | Gerador de carga |
| Recursos | 32 vCPU / 62 GB / 117 GB livres | 8 vCPU / 15 GB |
| Usuário | `rootmulti` | `rootmulti` |
| Já instalado | Ubuntu 24.04, Docker, k3d (cluster `multitrans`), K3s 1.35.5, **Istio 1.30.4**, metrics-server, CloudNativePG, Strimzi, kube-prometheus-stack, Grafana, Loki, Tempo | Ubuntu 24.04, **k6 v2.2.0** |
| Falta | **os 10 microsserviços da PoC** | o repositório + `jq` |

A entrega da Nuvem Acessível montou o *substrato*. Na seção 1 do documento de entrega:
*"Os serviços de negócio do cliente não foram implantados."* Este guia cobre exatamente
essa lacuna.

### Decisões de arquitetura deste ambiente (defender na banca se perguntarem)

- **A PoC traz o próprio PostgreSQL e o próprio Kafka**, no namespace `saude-poc`, sem usar
  o CloudNativePG e o Strimzi entregues. Motivo objetivo: o CNPG entregou **um** banco
  (`multitrans`) e a arquitetura é *database per service*, com 10 bancos; e o Strimzi está
  com **criação automática de tópicos desabilitada** e exige SASL_SSL + SCRAM-SHA-512, o
  que obrigaria a alterar a configuração dos 10 serviços e distribuir o CA. O ganho seria
  nulo para as perguntas de pesquisa do trabalho.
- **O Istio entregue é reutilizado** (1.30.4). O `deploy.sh` detecta o namespace
  `istio-system` e não reinstala.
- **O Prometheus entregue é reutilizado.** Como é o kube-prometheus-stack, ele descobre
  alvos por CRD (`PodMonitor`), não por anotação — daí o `k8s/40-monitoring.yaml`.

---

## 1. Levar o código para as VMs (faça no Windows, antes de ir)

O repositório já tem remoto no GitHub. O caminho mais simples é empurrar a branch e
cloná-la nas duas VMs.

```powershell
cd C:\DANIEL\TCC\saude-publica-poc
git push origin feat/graphql
```

Se o repositório for **privado**, gere um token em GitHub → Settings → Developer settings →
Personal access tokens → *Fine-grained*, com permissão de leitura no repositório, e use-o
como senha no `git clone`. Se for público, o clone não pede nada.

> **Não suba o PDF da entrega** (`Entrega-Multitrans-Nuvem-Acessivel.pdf`): ele traz IPs
> internos e detalhes de infraestrutura de terceiro. Já está no `.gitignore`.

---

## 2. VM-1 — sistema sob teste

### 2.1 Preparar a sessão (uma vez por sessão SSH)

```bash
ssh rootmulti@192.168.5.3
export KUBECONFIG="$HOME/.kube/multitrans.yaml"
echo 'export KUBECONFIG=$HOME/.kube/multitrans.yaml' >> ~/.bashrc   # para não repetir
kubectl get nodes                                                   # deve dizer Ready
```

### 2.2 Liberar o Docker para o seu usuário (uma vez só)

No diagnóstico o `docker ps` deu `permission denied` — o `rootmulti` não está no grupo
`docker`, e sem isso não dá para construir as imagens.

```bash
sudo usermod -aG docker "$USER"
exit
```

Reconecte o SSH (o grupo só vale em sessão nova) e confirme:

```bash
ssh rootmulti@192.168.5.3
docker info >/dev/null && echo "docker OK"
```

### 2.3 Clonar e construir

```bash
sudo apt-get update -y && sudo apt-get install -y git jq sysstat
git clone -b feat/graphql https://github.com/danielscremim/saude-publica-poc.git
cd saude-publica-poc
chmod +x k8s/scripts/*.sh scripts/*.sh tests/k6/*.sh

./k8s/scripts/load-images-k3s.sh     # compila as 10 imagens e importa no k3d
```

Esse passo leva **15–25 min na primeira vez** (baixa Maven, dependências e compila 10
serviços). Precisa de internet na VM. O script detecta sozinho que o cluster é k3d e usa
`k3d image import`.

### 2.4 Implantar

```bash
./k8s/scripts/deploy.sh
kubectl -n saude-poc get pods -w
```

Espere **`2/2 Running`** nos 10 serviços (aplicação + sidecar Envoy) e `1/1` em
postgres/kafka. Se algum ficar `1/1`, a injeção de sidecar falhou — sem sidecar não há
mTLS para medir. Nesse caso:

```bash
kubectl get ns saude-poc --show-labels        # precisa ter istio-injection=enabled
kubectl -n saude-poc rollout restart deploy   # força nova injeção
```

### 2.5 Publicar o Kong na porta 8000

```bash
./k8s/scripts/expose-kong-k3d.sh
```

O script adiciona a porta ao load balancer do k3d e testa. **Não use `kubectl port-forward`
para a carga**: a execução [3] registrada no cabeçalho de `tests/k6/load.js` foi feita
assim e adicionou ~300 ms por requisição — 3 dos 4 thresholds falharam com o sistema
saudável.

### 2.6 Validar que a PoC está de pé

```bash
BASE=http://localhost:8000 ./scripts/test-graphql.sh
./scripts/test-flow.sh
./scripts/test-bidirectional.sh
```

> Depois de alterar código de um serviço:
> `./k8s/scripts/load-images-k3s.sh history-service && kubectl -n saude-poc rollout restart deploy/history-service`

---

## 3. VM-2 — gerador de carga

```bash
ssh rootmulti@192.168.5.4
hostname                                     # confirme: multiransk6

git clone -b feat/graphql https://github.com/danielscremim/saude-publica-poc.git
cd saude-publica-poc
chmod +x infra/vm/*.sh tests/k6/*.sh

./infra/vm/setup-vm2-loadgen.sh              # instala jq/git, confirma o k6, ajusta limites
exit                                         # reconecte: o limite de descritores só vale em sessão nova
```

Reconecte e confirme os dois pré-requisitos:

```bash
ssh rootmulti@192.168.5.4
ulimit -n                                    # precisa ser 65535 (com 1024, 1000 VUs falham)
curl -s -o /dev/null -w "Kong: %{http_code}\n" \
  http://192.168.5.3:8000/v1/patients/00000000-0000-0000-0000-000000000000
```

**404 é o resultado esperado** (o paciente não existe, mas a rota respondeu). Se vier
`000`, a porta 8000 não chegou até aqui — volte ao passo 2.5.

Registre a latência de rede entre as VMs, que entra na Metodologia:

```bash
ping -c 20 192.168.5.3 | tail -2
```

---

## 4. Executar a bateria

**Ordem importa.** A coleta de infraestrutura precisa começar *antes* da carga.

### Terminal 1 — VM-1, coleta de infraestrutura

```bash
cd ~/saude-publica-poc && export KUBECONFIG="$HOME/.kube/multitrans.yaml"
./tests/k6/coletar-metricas.sh
```

Colhe o que o k6 não enxerga: CPU/memória por pod, réplicas do HPA ao longo do tempo,
HikariCP, threads do Tomcat, lag do Kafka, eventos de `Pending`/`OOMKilled` e I/O de disco
e rede. Encerre com `Ctrl+C` quando a bateria acabar.

### Terminal 2 — VM-1, acompanhar o auto-scaling (para os prints do TCC)

```bash
watch -n 5 'kubectl -n saude-poc get hpa; kubectl -n saude-poc get pods | head -20'
```

### Terminal 3 — VM-2, a carga

```bash
cd ~/saude-publica-poc/tests/k6
BASE_HOST=192.168.5.3 ./run-2vm.sh                                  # A + B + E, 3 rodadas cada (~2h)
SCENARIOS="ruptura" BASE_HOST=192.168.5.3 ./run-2vm.sh              # C, ponto de ruptura
```

| Cenário | Comando | Alimenta |
|---|---|---|
| A — referência, 150 VUs | `SCENARIOS="design"` | Resultado 1 |
| B — nominal, 1000 VUs (RNF-01) | `SCENARIOS="load1000"` | Resultado 1 |
| C — ponto de ruptura | `SCENARIOS="ruptura"` | Resultado 2 |
| D — resiliência | B rodando + `kubectl -n saude-poc delete pod <history-service-xxx>` na VM-1 | Resultado 3 |
| E — minimização de dados | `SCENARIOS="minimizacao"` | Resultado 5 |
| F — segurança | `./scripts/test-graphql.sh` + checagem de mTLS | Resultado 4 |

**Cenário D**, com a carga de 1000 VUs em andamento, na VM-1: anote o horário, apague um
pod do `history-service` e meça quanto tempo até voltar a `Running` (self-healing ≤ 30 s,
RNF-02). O erro observado pelo k6 nesse intervalo é o dado do Resultado 3.

**Cenário F**, prova de que o mTLS STRICT rejeita tráfego sem identidade:

```bash
kubectl -n saude-poc run teste-sem-mtls --rm -it --restart=Never \
  --annotations sidecar.istio.io/inject=false --image=curlimages/curl -- \
  curl -sS -o /dev/null -w "%{http_code}\n" --max-time 5 http://patient-service:8081/actuator/health
```

Sem sidecar, o pod não tem certificado do mesh: a conexão é **recusada**. Compare com o
mesmo comando sem a anotação (com sidecar), que responde 200. Guarde as duas saídas.

### Onde ficam os resultados

- VM-2: `tests/k6/resultados/<data>/` — `*.json`, `*.log`, `resumo.csv`, `ruptura-degraus.csv`
- VM-1: `tests/k6/resultados-infra/<data>/` — `nodes.csv`, `pods.csv`, `hpa.csv`, `pools.csv`, `kafka-lag.csv`, `eventos.txt`

Traga tudo para o Windows:

```powershell
scp -r rootmulti@192.168.5.4:~/saude-publica-poc/tests/k6/resultados .
scp -r rootmulti@192.168.5.3:~/saude-publica-poc/tests/k6/resultados-infra .
```

---

## 5. Dashboards

O ambiente entregue **não tem Kiali nem Jaeger** — tem Grafana + Prometheus + Loki + Tempo.
O acesso segue as seções 5 a 7 do documento de entrega: `kubectl port-forward` na VM-1 +
túnel SSH do PuTTY.

```bash
# VM-1, em uma sessão SSH dedicada:
kubectl --kubeconfig="$HOME/.kube/multitrans.yaml" port-forward \
  -n monitoring svc/monitoring-grafana 3000:80 --address 127.0.0.1
```

No PuTTY: *Change Settings → Connection → SSH → Tunnels*, Source `3000`, Destination
`127.0.0.1:3000`, **Add**, **Apply**. Abra `http://127.0.0.1:3000`, usuário `admin`.
Senha inicial:

```bash
kubectl -n monitoring get secret monitoring-grafana \
  -o jsonpath='{.data.admin-password}' | base64 --decode; echo
```

Consultas úteis no *Explore* → Prometheus, depois que a carga estiver rodando:

```promql
# P95 por serviço (RNF-01)
histogram_quantile(0.95, sum by (destination_service_name, le) (
  rate(istio_request_duration_milliseconds_bucket{destination_service_namespace="saude-poc"}[1m])))

# réplicas por serviço ao longo do tempo (HPA — RNF-01/02)
kube_deployment_status_replicas{namespace="saude-poc"}

# saturação do pool de conexões (explica o ponto de ruptura)
hikaricp_connections_pending{namespace="saude-poc"}
tomcat_threads_busy_threads{namespace="saude-poc"}
```

Se essas duas últimas não retornarem nada, o `PodMonitor` não foi adotado pelo Prometheus —
confira o label da release:

```bash
helm list -A                                     # nome real da release do kube-prometheus-stack
kubectl -n monitoring get prometheus -o yaml | grep -A5 podMonitorSelector
```

e ajuste `release:` em `k8s/40-monitoring.yaml`.

---

## 6. Registrar no TCC

- Specs das duas VMs (32 vCPU/62 GB e 8 vCPU/15 GB) e que o gerador de carga estava isolado.
- Versões: Ubuntu 24.04, k3d 5.9.0, K3s 1.35.5, Istio 1.30.4, Kafka 3.8.1 (da PoC),
  PostgreSQL 16 (da PoC), Java 21, Spring Boot 3.3.5, k6 v2.2.0.
- Latência de rede VM-2 → VM-1 (`ping`).
- 3 rodadas por cenário, média ± desvio-padrão, aquecimento descartado.
- **Limitação a declarar:** nó único, sem Cluster Autoscaler. Acima de certa carga o HPA
  pede réplicas que o nó não tem CPU para acomodar e os pods ficam `Pending`. Isso é em si
  um resultado: a arquitetura escala horizontalmente até o limite do substrato, e a partir
  daí o gargalo é o dimensionamento da infraestrutura, não o desenho do software.

---

## 7. Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `docker: permission denied` | usuário fora do grupo `docker` | passo 2.2 e **reconectar o SSH** |
| Pods `1/1` em vez de `2/2` | sidecar não injetado | `kubectl get ns saude-poc --show-labels`; `rollout restart deploy` |
| Kong `<pending>` / porta 8000 sem resposta | ServiceLB desabilitado no cluster | `./k8s/scripts/expose-kong-k3d.sh` |
| `too many open files` no k6 | `ulimit -n` em 1024 | passo 3 e **reconectar o SSH** |
| Pods `Pending` sob carga | nó sem CPU para mais réplicas | é um resultado, não um defeito — registre (seção 6) |
| `pools.csv` vazio | PodMonitor não adotado | seção 5, ajustar o label `release:` |
| `ImagePullBackOff` | imagem não importada no k3d | rodar `./k8s/scripts/load-images-k3s.sh` de novo |
