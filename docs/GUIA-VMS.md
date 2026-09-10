# Guia — Ambiente experimental em 2 VMs

**Por que 2 VMs:** nos testes anteriores o k6 rodou como Job dentro do mesmo nó dos serviços. Separar o gerador de carga do sistema sob teste elimina a disputa de CPU e torna as medidas atribuíveis à arquitetura (declare isso na Metodologia).

## 1. Provisionar

| VM | Papel | Mínimo | Recomendado | SO |
|---|---|---|---|---|
| **VM-1** | SUT: k3s + Istio + Kafka + Postgres + 10 serviços | 8 vCPU / 16 GB / 100 GB | 16 vCPU / 32 GB / 150 GB | Ubuntu 24.04 |
| **VM-2** | Gerador de carga (k6) | 2 vCPU / 4 GB | 4 vCPU / 8 GB | Ubuntu 24.04 |

Firewall da VM-1: liberar **22** (seu IP) e **8000** (Kong — para o IP da VM-2 e o seu). Dashboards por túnel SSH (§5). Anote o **IP da VM-1**.

## 2. VM-1 (sistema sob teste)

```bash
ssh usuario@IP_VM1
git clone https://github.com/danielscremim/saude-publica-poc.git && cd saude-publica-poc
chmod +x infra/vm/*.sh k8s/scripts/*.sh
./infra/vm/setup-vm1-sut.sh          # Docker + k3s + istioctl (anote a versão do Istio)
exit && ssh usuario@IP_VM1            # reentrar (grupo docker)
cd saude-publica-poc
./k8s/scripts/load-images-k3s.sh     # compose build + import no k3s (10-15 min na 1ª vez)
./k8s/scripts/deploy.sh              # Istio (perfil demo) + infra + serviços + políticas
kubectl -n saude-poc get pods -w     # tudo 2/2 Running (postgres/kafka 1/1)
```
Smoke test via Kong (na VM-1): `BASE=http://localhost:8000 ./scripts/test-graphql.sh`

Após alterar um serviço: `./k8s/scripts/load-images-k3s.sh history-service && kubectl -n saude-poc rollout restart deploy/history-service`

## 3. VM-2 (carga)

```bash
ssh usuario@IP_VM2
git clone https://github.com/danielscremim/saude-publica-poc.git && cd saude-publica-poc
chmod +x infra/vm/*.sh tests/k6/*.sh
./infra/vm/setup-vm2-loadgen.sh      # k6 (anote a versão)
curl -s -o /dev/null -w "%{http_code}\n" http://IP_VM1:8000/v1/patients/00000000-0000-0000-0000-000000000000   # 404 = OK
```

## 4. Executar a bateria

```bash
cd tests/k6
BASE_HOST=IP_VM1 ./run-2vm.sh                       # design(150) + load1000 + rest-vs-graphql, 3 rodadas cada
SCENARIOS="graphql" ROUNDS=3 BASE_HOST=IP_VM1 ./run-2vm.sh   # só o comparativo
```
Saída em `tests/k6/resultados/<data>/` (`*.json`, `*.log`, `resumo.csv`). Em paralelo, na VM-1: `watch -n 5 'kubectl -n saude-poc get hpa'` e tire prints.

## 5. Dashboards (do seu computador)

```bash
ssh -L 3000:localhost:3000 -L 20001:localhost:20001 -L 16686:localhost:16686 usuario@IP_VM1
# na VM-1:
kubectl -n istio-system port-forward svc/grafana 3000:3000 &
kubectl -n istio-system port-forward svc/kiali 20001:20001 &
kubectl -n istio-system port-forward svc/tracing 16686:80 &
```
Grafana http://localhost:3000 · Kiali http://localhost:20001 · Jaeger http://localhost:16686
(o perfil `demo` do Istio já instala esses addons; se faltar algum: `kubectl apply -f ~/istio-*/samples/addons/`)

## 6. Registrar no TCC
Specs das duas VMs; versões (Ubuntu, k3s, Istio, Kafka 3.8, Postgres 16, Java 21, Spring Boot 3.3.5, k6); latência de rede VM-2→VM-1 (`ping`); e que o gerador de carga estava isolado. Reporte 3 rodadas como média ± desvio-padrão, com o aquecimento descartado.
