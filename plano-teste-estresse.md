# Plano de Teste de Carga e Estresse

> Insumo para a *Metodologia* (subtópico "Cenários de teste") e para *Resultados e Discussão*.
> Contexto: a solução executa em duas VMs de datacenter, não em nuvem — o que delimita
> o que é mensurável e o que precisa ser declarado como limitação.

---

## 1. Princípio do recorte

A instrumentação observa muitas métricas; o trabalho **destaca as que sustentam os requisitos não funcionais** e usa as demais como apoio diagnóstico — para *explicar* um resultado, não como resultado em si.

Com limite de 30 páginas, a seção de Resultados apresenta **cinco resultados**, não uma lista de métricas:

| # | Resultado | RNF | Métricas de apoio (dentro da seção, não como seção) |
|---|---|---|---|
| 1 | Desempenho sob carga | RNF-01 | throughput, P50/P95/P99, taxa de erro |
| 2 | Ponto de ruptura e recuperação | RNF-01, 02 | CPU, memória, pools HikariCP, threads Tomcat |
| 3 | Auto-scaling e resiliência a falha | RNF-01, 02 | réplicas × tempo, tempo de reação do HPA, MTTR |
| 4 | Segurança e consentimento | RNF-03, 06 | mTLS, consent inline, auditoria imutável |
| 5 | Minimização de dados e pontos de integração | RNF-05, 06 | bytes por resposta, round-trips (4→1) |

**Lag do Kafka** merece destaque próprio dentro do resultado 2: sob sobrecarga, a ingestão continua aceitando requisições enquanto o processamento assíncrono acumula fila e drena depois. É evidência direta do **desacoplamento** da arquitetura — comportamento que uma arquitetura síncrona não teria.

**I/O de disco e retransmissões de rede** são coletados, mas só entram no documento se acusarem saturação. Não é onde o gargalo desta aplicação está.

---

## 2. Ambiente

| | VM-1 (sistema sob teste) | VM-2 (gerador de carga) |
|---|---|---|
| Componentes | k3s + Istio + Kafka + PostgreSQL + 10 microsserviços + observabilidade | apenas k6 |
| Motivo | — | isolar a geração de carga |

Nos Resultados Preliminares o k6 rodou como Job **no mesmo nó** dos serviços, competindo por CPU. A separação corrige essa limitação metodológica e torna as medidas atribuíveis à arquitetura.

**Recurso finito e conhecido.** Sem elasticidade de nuvem, o teto de CPU e memória é fixo. Isso é vantagem para o experimento: torna o ponto de ruptura determinável, em vez de mascarado por provisionamento automático de nós.

---

## 3. Cenários de execução

| Cenário | Script | Objetivo | Resultado que alimenta |
|---|---|---|---|
| A. Carga de referência | `load.js` (150 VUs, `THRESHOLDS=design`) | confirmar que os alvos de projeto são atingidos com folga | 1 |
| B. Carga nominal | `load.js` (1000 VUs, `THRESHOLDS=poc`) | validar o RNF-01 com 1000 requisições simultâneas | 1 |
| C. Ponto de ruptura | `ponto-ruptura.js` | degraus de RPS até o colapso; MTTR na volta | 2 |
| D. Resiliência | `load.js` + `kubectl delete pod` durante a execução | erro durante a falha e tempo de auto-recuperação | 3 |
| E. Minimização de dados | `minimizacao-dados.js` | bytes por resposta e round-trips | 5 |
| F. Segurança | comando manual (mTLS) + `test-graphql.sh` | requisição sem mTLS rejeitada; consent inline | 4 |

Cada cenário: **3 rodadas**, descartando o primeiro minuto (aquecimento da JVM e do HPA). Resultados reportados como **média ± desvio-padrão**.

---

## 4. Por que o ponto de ruptura usa taxa de chegada, e não VUs

Os testes anteriores usaram `ramping-vus`. Com VUs a carga **se auto-regula**: quando o sistema fica lento, cada usuário virtual envia menos requisições e o limite real fica mascarado. O `stress.js` existente tem essa limitação.

O `ponto-ruptura.js` usa `ramping-arrival-rate`, que fixa a **taxa de chegada em requisições por segundo** independentemente do tempo de resposta. É o que permite afirmar *"a plataforma sustentou N req/s; acima disso, degradou"*.

**Critério de ruptura** — primeiro degrau em que ocorre qualquer um destes:
- taxa de erro acima de 1%
- P95 acima de 2000 ms (limite adotado para a PoC)
- `dropped_iterations` maior que zero (o gerador não sustentou a taxa alvo)
- pods em `Pending`, `OOMKilled` ou `CrashLoopBackOff`

---

## 5. Por que instrumentar pools, threads e memória

Não como resultado próprio, mas para **explicar** o ponto de ruptura. A diferença entre as duas afirmações:

> "A plataforma degradou a N req/s."
> "A plataforma degradou a N req/s porque o pool de conexões do HikariCP saturou e as requisições passaram a aguardar em fila, com as threads do Tomcat ocupadas em espera de I/O."

A segunda é resultado de engenharia; a primeira é observação. O histórico do projeto reforça isso: as execuções [1] e [2] registradas em `tests/k6/load.js` mostram que ajustar os pools levou o P95 de 6,76 s para 7,36 ms na mesma carga — o gargalo já era conhecido, faltava instrumentação para demonstrá-lo em número.

---

## 6. Limitações do ambiente (declarar no trabalho)

**Auto-scaling.** Mensurável no nível de **pods** (HPA, 2→10 réplicas a 70% de CPU), mas não no nível de **nós**: não há *Cluster Autoscaler* sem elasticidade de infraestrutura. Acima de certa carga, o HPA solicitará réplicas que o nó único não tem CPU para acomodar, e os pods ficarão `Pending`. Esse comportamento é, em si, um resultado: demonstra que a arquitetura escala horizontalmente até o limite do substrato, e que o gargalo passa a ser o dimensionamento da infraestrutura, não o desenho do software.

**Disco e rede.** Métricas do nó inteiro, não isoláveis por microsserviço, já que todos compartilham a mesma VM. Servem para identificar saturação e correlacionar com a degradação, não para atribuir consumo a um serviço.

**Generalização.** Os números valem para esta configuração de hardware. A contribuição é **arquitetural e metodológica** — comportamento sob carga, pontos de saturação e resposta dos mecanismos de resiliência — não uma promessa de capacidade absoluta em produção.

---

## 7. Procedimento de execução

```bash
# VM-1, terminal 1 — coleta de infraestrutura (iniciar ANTES da carga)
./tests/k6/coletar-metricas.sh

# VM-1, terminal 2 — acompanhar auto-scaling
watch -n 5 'kubectl -n saude-poc get hpa; kubectl -n saude-poc get pods'

# VM-2 — bateria completa, 3 rodadas por cenário
cd tests/k6 && BASE_HOST=<IP_VM1> ./run-2vm.sh

# VM-2 — apenas o ponto de ruptura
k6 run -e BASE_HOST=<IP_VM1> -e KONG_PORT=8000 \
       --out csv=ruptura.csv --summary-export ruptura.json ponto-ruptura.js
```

Pré-requisito na VM-1: `sudo apt-get install -y sysstat`.

**Artefatos:** `tests/k6/resultados/<data>/` (k6) e `tests/k6/resultados-infra/<data>/` (nó, pods, HPA, Kafka lag, pools, eventos, disco, rede).

---

## 8. Instrumentação

| Item | Onde | Para quê |
|---|---|---|
| `micrometer-registry-prometheus` nos 10 serviços | `pom.xml` | **correção de lacuna**: o `application.yml` declarava `include: prometheus`, mas sem essa dependência o endpoint `/actuator/prometheus` não existia — JVM, HikariCP e Tomcat eram imensuráveis |
| Anotações `prometheus.io/scrape` | 10 Deployments | Prometheus do Istio coleta métricas de aplicação |
| Tag `application` | `application.yml` | separar serviços nos painéis |
| `tests/k6/ponto-ruptura.js` | novo | limite de carga e MTTR |
| `tests/k6/coletar-metricas.sh` | novo | o que o k6 não vê: nó, pods, pools, threads, HPA, Kafka lag |

---

## 9. Origem

O escopo acima foi consolidado após o orientador solicitar, por e-mail, a descrição dos pontos avaliados no teste de estresse. A lista dele (throughput, latência, erros, saturação, pools e threads, I/O, rede, ponto de ruptura, MTTR, auto-scaling e queue lag) serviu como checklist de cobertura da instrumentação — todos são observáveis. O recorte da §1 define quais **sustentam os resultados** do trabalho e quais entram como apoio diagnóstico.
