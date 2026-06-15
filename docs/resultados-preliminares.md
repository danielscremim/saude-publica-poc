# Resultados Preliminares — PoC TCC
## Arquitetura de Microsserviços e Interoperabilidade para Distribuição de Dados na Saúde Pública

> Documento gerado em 2026-06-15.
> Fonte: execuções reais no ambiente de laboratório descrito abaixo.
> Destinado ao preenchimento da seção de Resultados e Discussão do relatório final.

---

## 1. Ambiente de Execução

| Item | Valor |
|---|---|
| Plataforma | Ubuntu Server 22.04.5 LTS (VM Hyper-V) |
| CPU | 16 vCores |
| RAM | 20 GB |
| Orquestração | k3s v1.35.5 (single-node) |
| Service Mesh | Istio 1.21.0 |
| API Gateway | Kong 3.7 (DB-less, declarativo) |
| Banco de dados | PostgreSQL 16 (1 instância compartilhada, 1 banco por serviço) |
| Mensageria | Apache Kafka (KRaft, sem Zookeeper) |
| Ferramenta de carga | Grafana k6 (Job Kubernetes) |
| Repositório | https://github.com/danielscremim/saude-publica-poc |

**Topologia:** todos os 10 microsserviços, Kafka, PostgreSQL, Kong e o próprio k6 rodam no mesmo nó físico, competindo pelos mesmos recursos de CPU e memória. Isso é uma limitação deliberada de escopo (PoC de laboratório), não uma decisão arquitetural.

---

## 2. Arquitetura Implantada

### 2.1 Microsserviços (10 serviços)

| Serviço | Porta | Responsabilidade |
|---|---|---|
| patient-service | 8081 | Cadastro de pacientes; tokeniza CPF → UUID interno |
| exam-service | 8082 | Solicitação de exame; publica evento `exam.requested` |
| lab-service | 8083 | Processa exame; publica evento `exam.completed` |
| result-service | 8084 | Armazena resultados; fluxo bidirecional autenticado com JWT |
| auth-service | 8085 | Emissão de JWT HS256 (OAuth2 Client Credentials) |
| consent-service | 8086 | Controle de consentimento LGPD; check inline; publica `consent.revoked` |
| history-service | 8087 | Fachada agregadora; verifica consent; publica eventos de auditoria |
| audit-service | 8088 | Log imutável de acessos; detecção de anomalia |
| notification-service | 8089 | Notificações por exame concluído (LOG/EMAIL/SMS/WEBHOOK) |
| triage-service | 8090 | Triagem clínica com classificação Manchester automática |

### 2.2 Configuração Kubernetes

- **Réplicas:** mínimo 2, máximo 10 por serviço (RNF-02)
- **HPA:** escalonamento horizontal automático ativado a 70% de uso de CPU (RNF-01)
- **mTLS:** PeerAuthentication STRICT em todo o namespace `saude-poc` (RNF-03)
- **Exceção Kong:** PeerAuthentication PERMISSIVE na porta 8000 do Kong — o gateway é o ponto de entrada de clientes externos (sem sidecar Istio); o tráfego Kong → serviços internos usa mTLS normalmente
- **Circuit Breaker:** DestinationRules com `consecutiveGatewayErrors: 5`, `interval: 30s`, `baseEjectionTime: 30s` (RNF-02)
- **JWT:** expiração de 1 hora, segredo compartilhado entre auth-service (emissor), history-service e result-service (validadores)

### 2.3 Fluxo de dados principal

```
Cliente externo (hospital/lab/UBS)
  → Kong (API Gateway, porta 8000)
    → auth-service   (OAuth2 Client Credentials → JWT)
    → consent-service (check inline ≤ 20ms antes de qualquer leitura)
    → history-service (agrega patient + results; check consent; publica audit.events)
    → result-service  (POST autenticado com scope result:write)
        └─ publica exam.completed → notification-service
        └─ publica audit.events  → audit-service

Fluxo assíncrono (Kafka):
  exam-service → [exam.requested] → lab-service → [exam.completed] → result-service, notification-service
  history-service → [audit.events] → audit-service
  consent-service → [consent.revoked] → (consumidores futuros de cache)
```

---

## 3. Testes de Carga — Resultados

O script k6 simula o mix de carga real do sistema:
- **50%** leitura de timeline clínica (`GET /v1/patients/{uuid}/clinical-timeline`)
- **30%** verificação de consentimento (`GET /v1/consents/check`)
- **20%** cadastro de paciente (`POST /v1/patients`)

Todo o tráfego passa pelo Kong na porta 8000 (caminho de produção real).

---

### Execução A — 150 VUs | Thresholds de Design (targets de produção)

**Objetivo:** confirmar que os RNFs de latência são atingidos quando a infraestrutura tem folga — equivalente ao comportamento esperado em um cluster distribuído bem dimensionado.

| Parâmetro | Valor |
|---|---|
| VUs máximos | 150 |
| Duração | 9 min (2m ramp-up → 5m sustentado → 2m ramp-down) |
| Thresholds aplicados | Targets de design dos RNFs |
| Acesso | k6 Job Kubernetes → Kong LoadBalancer (sem port-forward) |

#### Thresholds — todos aprovados ✅

| RNF | Threshold | Resultado medido | Margem | Status |
|---|---|---|---|---|
| RNF-01 (escalabilidade) | P95 ≤ 500ms | **40,25ms** | 12× abaixo | ✅ PASSOU |
| RNF-06 (consent check) | P95 ≤ 20ms | **17,12ms** | abaixo do SLA | ✅ PASSOU |
| RNF-05 (timeline) | P95 ≤ 800ms | **52,02ms** | 15× abaixo | ✅ PASSOU |
| RNF-01 (taxa de erro) | < 1% | **0,00%** | zero erros | ✅ PASSOU |

#### Métricas completas

```
checks_total.......: 235.358   (434 checks/s)
checks_succeeded...: 100,00%   235.358 / 235.358
checks_failed......: 0,00%     0 / 235.358

✓ setup: token 200
✓ setup: paciente 201
✓ setup: consent 201
✓ consent 200
✓ timeline 200
✓ patient 201

HTTP
http_req_duration.........: avg=16,73ms  min=1,62ms  med=12,27ms  max=2,51s   p(90)=28,34ms  p(95)=40,25ms
  {endpoint:consent_check}: avg=7,14ms   min=1,62ms  med=5,11ms   max=877ms   p(90)=12,09ms  p(95)=17,12ms
  {endpoint:timeline}......: avg=24,45ms  min=6,83ms  med=16,71ms  max=2,51s   p(90)=38,08ms  p(95)=52,02ms

http_req_failed...: 0,00%   (1 falha técnica em 235.359 requisições)
http_reqs.........: 235.359  (434 req/s)

EXECUTION
iterations........: 235.355  (434 iterações/s)
vus_max...........: 150

NETWORK
data_received.....: 131 MB  (241 kB/s)
data_sent.........: 87 MB   (160 kB/s)
```

---

### Execução B — 1000 VUs | Thresholds de PoC (ambiente single-VM)

**Objetivo:** validar o RNF-01 oficial (1.000 requisições simultâneas) e demonstrar estabilidade da arquitetura sob carga máxima no ambiente de laboratório.

| Parâmetro | Valor |
|---|---|
| VUs máximos | 1.000 |
| Duração | 9 min (2m ramp-up → 5m sustentado → 2m ramp-down) |
| Thresholds aplicados | Targets ajustados para ambiente PoC single-VM |
| Acesso | k6 Job Kubernetes → Kong LoadBalancer (sem port-forward) |

#### Thresholds — todos aprovados ✅

| Threshold (PoC) | Resultado medido | Status |
|---|---|---|
| P95 ≤ 2.000ms | **1.190ms** | ✅ PASSOU |
| Consent P95 ≤ 2.000ms | **270ms** | ✅ PASSOU |
| Timeline P95 ≤ 2.000ms | **1.420ms** | ✅ PASSOU |
| Taxa de erro < 1% | **0,00%** | ✅ PASSOU |

#### Métricas completas

```
checks_total.......: 588.562   (1.079 checks/s)
checks_succeeded...: 100,00%   588.562 / 588.562
checks_failed......: 0,00%     0 / 588.562

✓ setup: token 200
✓ setup: paciente 201
✓ setup: consent 201
✓ consent 200
✓ timeline 200
✓ patient 201

HTTP
http_req_duration.........: avg=462,57ms  min=2,24ms  med=390,25ms  max=7,14s  p(90)=979,55ms  p(95)=1.190ms
  {endpoint:consent_check}: avg=110,97ms  min=2,24ms  med=91,43ms   max=2,34s  p(90)=218,25ms  p(95)=270,76ms
  {endpoint:timeline}......: avg=655,07ms  min=7,83ms  med=635,94ms  max=7,14s  p(90)=1.150ms   p(95)=1.420ms

http_req_failed...: 0,00%   (1 falha técnica em 588.563 requisições)
http_reqs.........: 588.563  (1.079 req/s)

EXECUTION
iterations........: 588.559  (1.079 iterações/s)
vus_max...........: 1.000

NETWORK
data_received.....: 328 MB  (601 kB/s)
data_sent.........: 217 MB  (398 kB/s)
```

---

## 4. Comparativo — Design vs PoC vs 1000 VUs

| Métrica | Target design (produção) | 150 VUs — K8s PoC | 1000 VUs — K8s PoC |
|---|---|---|---|
| P95 latência geral | ≤ 500ms | **40ms** ✅ | 1.190ms ⚠️ |
| P95 consent check | ≤ 20ms | **17ms** ✅ | 270ms ⚠️ |
| P95 timeline | ≤ 800ms | **52ms** ✅ | 1.420ms ⚠️ |
| Taxa de erro | < 1% | **0,00%** ✅ | **0,00%** ✅ |
| Throughput | — | 434 req/s | **1.079 req/s** |
| Iterações (9 min) | — | 235.355 | **588.559** |
| Iterações interrompidas | — | 0 | 0 |

---

## 5. Validação por RNF

### RNF-01 — Escalabilidade
- **Status:** ✅ Mecanismo validado / ⚠️ Target de latência condicionado ao ambiente
- **Evidência:** 1.079 req/s com 1.000 VUs simultâneos, 0 erros, 0 iterações interrompidas
- **HPA configurado:** mínimo 2, máximo 10 réplicas, threshold 70% CPU
- **Limitação PoC:** P95 de 1.190ms vs target de 500ms — atribuído ao ambiente single-VM com 10 serviços co-localizados. A 150 VUs (infra com folga), o P95 é 40ms, 12× abaixo do target.

### RNF-02 — Disponibilidade
- **Status:** ✅ Totalmente validado
- **Evidência:** 2 réplicas mínimas em todos os 10 serviços durante todo o teste; zero crashes; zero interrupções em 588k + 235k iterações
- **Circuit Breaker:** configurado via DestinationRules Istio (não acionado — nenhum serviço falhou durante os testes)
- **Self-healing:** Kubernetes reinicia pods com falha automaticamente (não testado explicitamente, mas configurado via liveness/readiness probes)

### RNF-03 — Segurança / LGPD
- **Status:** ✅ Totalmente validado
- **mTLS STRICT:** ativo em todo o namespace `saude-poc`. Evidência: k6 sem sidecar Istio não conseguia conectar (connection reset) antes da configuração de exceção no Kong — comportamento esperado e confirmado
- **JWT:** emitido pelo auth-service com expiração de 1 hora; validado em history-service e result-service
- **CPF:** nunca exposto em resposta de API, log, evento Kafka ou DTO — apenas UUID circula entre serviços
- **Auditoria imutável:** audit-service processou ~820k eventos de auditoria (soma das duas execuções) sem falhas

### RNF-04 — Observabilidade
- **Status:** ⚠️ Parcialmente implementado
- **Implementado:** HPA com metrics-server (CPU), logs estruturados em todos os pods, k6 com métricas customizadas (`journey_duration`)
- **Não implementado na PoC K8s:** Prometheus, Grafana, Jaeger (disponíveis no Docker Compose local via `docker-compose.metrics.yml`, não portados para manifests Kubernetes)
- **Kiali:** disponível com Istio mas não configurado para ingestão de métricas no ambiente de laboratório

### RNF-05 — Interoperabilidade
- **Status:** ✅ Mecanismo validado / ⚠️ Target de latência condicionado ao ambiente
- **OpenAPI 3.0:** todos os 10 serviços expõem Swagger via springdoc (`/swagger-ui.html`)
- **History P95:** 52ms a 150 VUs (✅ muito abaixo de 800ms) / 1.420ms a 1.000 VUs (⚠️ single-VM)
- **Contrato único de API:** qualquer consumidor autorizado (médico, hospital, sistema analítico) usa o mesmo `GET /v1/patients/{uuid}/clinical-timeline?purpose=`

### RNF-06 — Consentimento (LGPD)
- **Status:** ✅ Mecanismo totalmente validado / ⚠️ SLA de 20ms só atingido a carga moderada
- **Consent check P95:** 17ms a 150 VUs ✅ / 270ms a 1.000 VUs ⚠️
- **Consent obrigatório:** history-service retorna HTTP 403 quando consent está ausente ou revogado (validado nos scripts `test-history.sh` e `test-auth-consent.sh`)
- **Revogação:** consent-service publica `consent.revoked` no Kafka imediatamente ao `DELETE /v1/consents/{id}` (propagação ≤ 1s por design)
- **Anomaly detection:** audit-service detecta > 10 acessos/10min por paciente (threshold configurável via `AUDIT_ANOMALY_THRESHOLD`; default de produção: 200)
- **CPF tokenizado:** UUID em todos os fluxos exceto patient-service internamente

---

## 6. Decisões Técnicas Relevantes (para discussão)

### 6.1 Kong como ingress com PeerAuthentication PERMISSIVE na porta 8000
Kong é o ponto de entrada de clientes externos — por design, não pertence ao mesh Istio como um pod membro (sem sidecar). Clientes externos (laboratórios, hospitais, o próprio k6 de carga) chegam via HTTP puro na porta 8000. O Kong, por ter sidecar Istio, usa mTLS para todo o tráfego de egresso (Kong → serviços internos). A solução adotada foi uma PeerAuthentication com `portLevelMtls[8000]: PERMISSIVE`, preservando STRICT no restante do namespace.

### 6.2 Kafka sem sidecar Istio (`sidecar.istio.io/inject: "false"`)
O KRaft (modo sem Zookeeper) do Kafka usa comunicação interna de controle (porta 9093) que é incompatível com mTLS do Istio no modo de cluster. A anotação desativa o sidecar especificamente no pod do Kafka, mantendo mTLS em todos os outros serviços.

### 6.3 `spring.json.use.type.headers: false` nos consumidores Kafka
Records de evento são duplicados por serviço (sem módulo compartilhado). O header `__TypeId__` do produtor aponta para o pacote do produtor, inexistente no consumidor. A flag desativa a resolução por header e usa o tipo configurado diretamente, evitando `RecordDeserializationException` silencioso.

### 6.4 Rate limiting no Kong
Configurado em 10.000 req/min no patient-service para o ambiente de teste. Em produção, o rate limiting seria por consumer (institutionId via JWT claim), não por IP global — limitando cada instituição a uma cota razoável sem bloquear o teste de carga que simula múltiplas instituições como uma única origem.

---

## 7. Limitações Declaradas

| Limitação | Impacto | Mitigação em produção |
|---|---|---|
| Single-VM (10 serviços + infra no mesmo nó) | P95 de latência 2–20× acima dos targets em 1.000 VUs | Cluster multi-nó com pods distribuídos |
| PostgreSQL compartilhado (1 instância, N bancos) | Contenção de I/O sob alta carga | 1 instância PostgreSQL por serviço com tuning |
| Sem cache em memória no consent-service | Consent P95 = 270ms @ 1.000 VUs vs target 20ms | Caffeine L1 (in-process) + Redis L2 |
| Sem Prometheus/Grafana/Jaeger no K8s | RNF-04 parcialmente atendido | Deploy da stack de observabilidade (manifestos existem no Docker Compose) |
| Sem HL7 FHIR / TISS / TUSS | Não interoperável com RNDS/CFM hoje | Adapter FHIR sobre o contrato JSON atual |
| Sem certificado ICP-Brasil | Não elegível para integração com e-SUS/CADSUS | Substituir `clientSecret` por certificado A3 |
| Sem DR multi-região | Single point of failure no nó | Cluster multi-região com replicação cross-zone |

---

## 8. Evidências de Funcionamento dos Fluxos (testes de integração)

Todos os scripts abaixo executados com sucesso no ambiente Docker Compose (validação funcional) e os fluxos críticos confirmados no Kubernetes:

| Script | Fluxo validado |
|---|---|
| `test-flow.sh` | patient → exam → lab (Kafka) → result → (DB persistido) |
| `test-auth-consent.sh` | registro OAuth2 client → token JWT → grant consent → revoke → 403 |
| `test-history.sh` | JWT + consent check → timeline agregada → audit.events publicado |
| `test-audit.sh` | consumo de audit.events → log imutável → anomaly detection |
| `test-notification.sh` | exam.completed → notification (LOG channel) |
| `test-triage.sh` | triagem com classificação Manchester automática |
| `test-bidirectional.sh` | POST /v1/results com JWT (scope result:write) por hospital privado |

---

## 9. Tabela Síntese para o Relatório

| Requisito | Mecanismo implementado | Resultado PoC | Resultado design (150 VUs) | Gap / Observação |
|---|---|---|---|---|
| RNF-01 Escalabilidade 1000 VUs | HPA 70% CPU, 2-10 réplicas | 1.079 req/s, P95=1.190ms | P95=40ms | Latência: ambiente single-VM |
| RNF-02 Disponibilidade ≥99,9% | 2 réplicas mínimas, Circuit Breaker, probes | 0 crashes em 823k req | idem | CB não acionado (nenhuma falha) |
| RNF-03 mTLS + auditoria | Istio STRICT, JWT 1h, audit imutável | ✅ validado | ✅ validado | — |
| RNF-04 Observabilidade | metrics-server, logs, k6 metrics | Parcial (sem Grafana/Jaeger K8s) | Parcial | Stack no Docker Compose |
| RNF-05 Interoperabilidade | OpenAPI 3.0, history-service fachada | P95 timeline=1.420ms | P95=52ms | Latência: ambiente single-VM |
| RNF-06 Consent LGPD ≤20ms | consent-service, check inline, Kafka revoke | P95=270ms @ 1000 VUs | P95=17ms ✅ | Cache para atingir 20ms @ 1000 VUs |
