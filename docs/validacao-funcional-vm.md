# Validação funcional na infraestrutura de datacenter — 19/09/2026

> **Insumo para a redação.** Este documento registra a *validação funcional* da PoC
> implantada na VM-1 (`multitransclister`). Não é o experimento de carga — esse vem
> depois, conforme `plano-teste-estresse.md`. Serve para a seção de **Metodologia**
> (descrição do ambiente e do procedimento de validação) e para o **Resultado 4**
> (segurança e consentimento) e **Resultado 5** (minimização de dados), que já ficam
> integralmente demonstrados aqui.

---

## 1. Ambiente

| | VM-1 · sistema sob teste | VM-2 · gerador de carga |
|---|---|---|
| Hostname / IP | `multitransclister` / 192.168.5.3 | `multiransk6` / 192.168.5.4 |
| SO | Ubuntu Server 24.04 LTS | Ubuntu Server 24.04 LTS |
| Recursos | 32 vCPU / 62 GB RAM / 145 GB disco | 8 vCPU / 15 GB RAM / 30 GB disco |
| Orquestração | k3d 5.9.0 · K3s v1.35.5+k3s1 (nó único `k3d-multitrans-server-0`) | — |
| Service mesh | Istio 1.30.4 (`registry.istio.io/release/pilot:1.30.4`) | — |
| Observabilidade | kube-prometheus-stack (chart 91.4.1), Grafana 13.2.2, Loki 3.7.7, Tempo 3.0.3, Alloy 1.19.2 | — |
| Gerador de carga | — | k6 v2.2.0 (Go 1.26.5, linux/amd64) |

**Provisionamento:** a infraestrutura base foi entregue por fornecedor externo em
16/09/2026 (Kubernetes, malha de serviços e observabilidade), sem as aplicações de
negócio. A implantação dos 10 microsserviços da PoC, das políticas de segurança e
da instrumentação foi realizada em 19/09/2026.

**Componentes da própria PoC** (namespace `saude-poc`): PostgreSQL 16 (banco por
serviço, 8 bases), Apache Kafka 3.8.1 em modo KRaft, Kong 3.7 DB-less como gateway
norte-sul, 10 microsserviços Java 21 / Spring Boot 3.3.5, com 2 réplicas cada e HPA
configurado para 2–10 réplicas a 70% de CPU.

> **Decisão de projeto.** O PostgreSQL e o Kafka gerenciados que acompanhavam a
> infraestrutura (CloudNativePG e Strimzi) não foram utilizados: o primeiro
> disponibilizava uma única base, incompatível com o padrão *Database per Service*
> adotado; o segundo opera com criação automática de tópicos desabilitada e
> autenticação SASL_SSL/SCRAM-SHA-512, cuja adequação não traria ganho para as
> perguntas de pesquisa. Istio, Prometheus, Grafana, Loki e Tempo entregues foram
> integralmente reaproveitados.

---

## 2. Ajustes de configuração descobertos empiricamente

Três falhas surgiram na primeira implantação e **nenhuma delas se manifesta em ambiente
Docker Compose de estação de trabalho**. São achados de engenharia relevantes para a
discussão: demonstram que o dimensionamento da plataforma, e não o desenho do software,
governa o comportamento sob concorrência.

### 2.1 Esgotamento do pool de conexões do banco

**Sintoma:** uma réplica de `audit-service`, `consent-service` e `triage-service` em
`CrashLoopBackOff`, com `org.postgresql.util.PSQLException: FATAL: sorry, too many
clients already` durante a inicialização do `SessionFactory` do Hibernate.

**Causa:** `max_connections` padrão da imagem PostgreSQL é 100. São 8 serviços com
persistência × 2 réplicas × pool HikariCP padrão de 10 = 160 conexões apenas em
repouso. Com o HPA em máximo (10 réplicas), a demanda chegaria a 800.

**Correção:** `max_connections=500`, `shared_buffers=512MB`, memória compartilhada
(`/dev/shm`) dedicada, e dimensionamento explícito do pool: `maximum-pool-size: 5`,
`minimum-idle: 1` nos 8 serviços — orçamento máximo de 8 × 10 × 5 = 400 conexões.

**Verificação:** `SHOW max_connections` → 500; conexões em repouso → 33.

### 2.2 Dimensionamento do gateway em nó de 32 vCPUs

**Sintoma:** as duas réplicas do Kong terminando com `Exit Code: 137` (SIGKILL).

**Causa:** o log registra a criação de **33 worker processes**. O nginx lê a contagem
de CPUs do *nó* (32 vCPUs), ignora o limite de CPU imposto ao contêiner (500m) e
extrapola o limite de memória de 1 GiB.

**Correção:** `KONG_NGINX_WORKER_PROCESSES=4` e limite de memória elevado para 2 GiB.

**Achado correlato:** o mesmo log registrava `getrlimit(RLIMIT_NOFILE): 1024`. Sob
1000 usuários virtuais o gateway esgotaria descritores de arquivo e registraria erros
*não atribuíveis ao sistema sob teste*. Ajustado para
`KONG_NGINX_MAIN_WORKER_RLIMIT_NOFILE=65536` e `worker_connections=16384`.

### 2.3 Remoção de tetos artificiais de recurso

PostgreSQL passou de 1 vCPU / 1 GiB para 8 vCPU / 4 GiB, e Kong de 500m / 1 GiB para
4 vCPU / 2 GiB. **Justificativa metodológica:** com os limites anteriores, o ponto de
ruptura medido seria o da configuração arbitrária de recursos, e não o da arquitetura.

> **Limitação do gerador de carga a declarar:** o limite de descritores da VM-2 também
> precisou ser elevado para 65535. O ajuste anterior do script de preparação era
> ineficaz, pois a verificação casava com linhas comentadas de `/etc/security/limits.conf`.

---

## 3. Resultado da validação — 7 baterias, todas aprovadas

Executadas via gateway (`BASE=http://localhost:8000`), roteando pelo Kong como em
produção. Estado final: **20 pods de aplicação, todos `2/2 Running`** (contêiner da
aplicação + sidecar Envoy), nenhum reinício.

### 3.1 Fluxo clínico completo (`test-flow.sh`)

Cadastro → solicitação de exame → publicação em Kafka → processamento pelo laboratório
→ persistência do resultado. Dois exames (GLICEMIA, COLESTEROL) percorreram a cadeia
assíncrona completa.

**Evidência do RNF-06 (tokenização):** a resposta do cadastro retorna
`{"uuid":"66c735a2-…","name":"Maria Silva","birthDate":"1985-03-12"}` — **sem CPF**.
O identificador trafega como UUID em todos os serviços subsequentes, eventos Kafka e
respostas de API.

### 3.2 Fluxo bidirecional com escopo (`test-bidirectional.sh`)

Ingestão por instituição externa (hospital privado) em `POST /v1/results`:

| Condição | HTTP | Corpo |
|---|---|---|
| Sem token | **401** | — |
| Token com escopo incorreto | **403** | `{"error":"insufficient_scope","error_description":"scope 'result:write' obrigatorio"}` |
| Token com escopo `result:write` | **201** | resultado persistido, `origin: HOSPITAL_PRIVADO` |

Propagação verificada: o evento `exam.completed` disparou a notificação, e
`audit.events` registrou `WRITE_RESULT` com `requesterId: hospital-bidir-ok` e
`sourceService: result-service`. **Tratamento uniforme público × privado confirmado:**
o produtor privado usa o mesmo contrato e o mesmo controle de escopo.

### 3.3 Identidade e consentimento (`test-auth-consent.sh`)

Emissão de JWT por *client credentials* (escopo `history:read:own_patients`),
concessão de consentimento e revogação com publicação de `consent.revoked` em Kafka.
Verificação inline antes e depois:

```
granted: true   →  revogação  →  granted: false, scope: ""
```

### 3.4 Distribuição autorizada — REST (`test-history.sh`)

Os três estados do controle de acesso à linha do tempo clínica consolidada:

| Etapa | Condição | Resultado | Evento de auditoria |
|---|---|---|---|
| 5 | sem token | **401** | — |
| 6 | token válido, sem consentimento | **403** `consent_denied` | `READ_TIMELINE_DENIED` |
| 8 | token válido + consentimento ativo | **200** com paciente e 2 exames | `READ_TIMELINE` |
| 10 | após revogação do consentimento | **403** | `READ_TIMELINE_DENIED` |

**É a demonstração central do RNF-06:** nenhum acesso externo ocorre sem consentimento
ativo, e toda tentativa — autorizada ou negada — é auditada.

### 3.5 Auditoria imutável e detecção de anomalia (`test-audit.sh`)

12 leituras consecutivas de linha do tempo pelo mesmo requisitante. Registros:
12 entradas por paciente, 12 por requisitante, e **alerta de anomalia disparado
automaticamente**:

```json
{"patientUuid":"8d57cf71-…","requesterId":"clinica-anomaly",
 "eventCount":10,"windowMinutes":10,"threshold":10,
 "detectedAt":"2026-09-19T05:38:54.625443Z"}
```

> Limiar de 10 acessos / 10 min configurado via `AUDIT_ANOMALY_THRESHOLD` para tornar
> a demonstração viável; o valor de projeto do RNF-06 é 200 acessos / 10 min.

### 3.6 Notificação assíncrona (`test-notification.sh`)

O evento `exam.completed` gerou notificação automática (`status: SENT`) sem qualquer
chamada síncrona entre laboratório e serviço de notificação — evidência do
desacoplamento por mensageria. Envio manual por `POST /v1/notifications` também
validado.

### 3.7 Triagem com classificação de Manchester (`test-triage.sh`)

| Entrada | Prioridade | Mecanismo |
|---|---|---|
| Sinais vitais normais | **BLUE** | classificador automático |
| Saturação de oxigênio 80% | **RED** | classificador automático |
| `priority: ORANGE` explícita | **ORANGE** | sobrescreve o classificador |

---

## 4. Camada de leitura GraphQL — minimização de dados (`test-graphql.sh`)

Os 10 passos foram aprovados. Os dois resultados quantitativos:

### 4.1 Minimização por declaração de campos

| Consulta | Bytes trafegados |
|---|---|
| Linha de base REST (resposta completa e fixa) | **727 B** |
| GraphQL declarando 3 campos | **319 B** |
| **Redução** | **56%** |

Consulta de 3 campos (`examType`, `resultValue`, `completedAt`) retorna exatamente
esses campos — o campo `origin`, presente na linha de base, **não é transmitido**.
Fundamento legal: minimização de dados, LGPD Art. 6º, III.

### 4.2 Redução de pontos de integração

Visão consolidada de **4 domínios em 1 requisição** (paciente, exames, notificações,
trilha de auditoria) contra 4 chamadas REST distintas. Os campos não solicitados
**não geram chamada ao serviço a montante** — a resolução é sob demanda, e é isso que
torna a minimização real e não cosmética.

### 4.3 Controles de segurança na camada GraphQL

| Cenário | Resultado |
|---|---|
| Sem token | **HTTP 401** |
| Token válido, sem consentimento | `errors[].extensions.classification = FORBIDDEN` |
| Profundidade abusiva | `maximum query depth exceeded 8 > 5` |
| Após revogação do consentimento | **FORBIDDEN** |

O GraphQL herda o mesmo controle de consentimento e a mesma auditoria do REST: a
autorização reside no serviço de aplicação, nunca no *resolver*.

---

## 5. Situação e próximo passo

**Concluído.** Implantação, políticas de segurança (mTLS STRICT, sidecar em 100% dos
pods), instrumentação de métricas e validação funcional dos 10 microsserviços.
Resultados 4 e 5 do plano de teste estão demonstrados.

**Pendente.** O experimento de carga (`plano-teste-estresse.md`): cenários A
(referência, 150 VUs), B (nominal, 1000 VUs), C (ponto de ruptura), D (resiliência) e
a coleta de infraestrutura correspondente, a partir da VM-2. Alimentam os
Resultados 1, 2 e 3.

**Limitação já declarada para o experimento de carga:** a carga sintética concentra-se
em um único paciente, o que torna os valores de latência um teto otimista. Justificativa
e forma de responder à banca em `plano-teste-estresse.md` §6.

**Identificadores desta execução** (para rastreabilidade, caso seja preciso reconsultar
os registros de auditoria): pacientes `66c735a2-81cd-4b5c-b39b-2307e03dd7a2`,
`d014cc5c-69ef-492e-b0f5-b75d9310fd97`, `4b3294b1-6abb-4143-b334-0ad47483a7d5`,
`8d57cf71-ca61-4e8c-b0ca-26e7957ff97d`; execução em 19/09/2026, 05:38–05:39 UTC.

---

## 6. Ensaio de carga — cenário A, 1 rodada (19/09/2026, 14:05)

> **Não é o resultado oficial.** Uma única rodada, executada para validar o
> encadeamento VM-2 → Kong → malha antes do bloco experimental. Os números do
> trabalho virão da bateria de 3 rodadas por cenário, reportada como média ±
> desvio-padrão. Este registro existe porque o resultado é significativo por si e
> serve de referência caso a bateria oficial divirja.

**Configuração:** 150 usuários virtuais (`THRESHOLDS=design`), 2 min de rampa +
5 min sustentados + 2 min de descida, gerados da VM-2 contra o Kong da VM-1.
Latência de rede entre as VMs: RTT médio 0,218 ms, 0% de perda (20 pacotes).

**Volume:** 233.428 requisições em 9 min · **432 req/s** sustentados ·
233.424 iterações completas, **nenhuma interrompida** · 129 MB recebidos / 85 MB enviados.

### 6.1 Requisitos não funcionais — limites de *projeto*, não de PoC

| Métrica | Medido | Limite de design | RNF |
|---|---|---|---|
| `http_req_duration` p(95) | **37,85 ms** | 500 ms | RNF-01 |
| `{endpoint:timeline}` p(95) | **52,59 ms** | 800 ms | RNF-05 |
| `{endpoint:consent_check}` p(95) | **13,84 ms** | 20 ms | RNF-06 |
| `http_req_failed` | **0,00%** (3 de 233.428) | < 1% | RNF-01 |

Os quatro limites foram atingidos com margem — e são os valores de **projeto**, não
os relaxados adotados para PoC em execuções anteriores.

### 6.2 Comparação com a execução [3] e o que ela esclarece

A execução [3] registrada no cabeçalho de `tests/k6/load.js` (k3s + Istio, 1000 VUs,
acesso por `kubectl port-forward`) falhou em três dos quatro limites:

| Threshold | Execução [3] | Ensaio atual | Limite |
|---|---|---|---|
| `http_req_duration` p(95) | 841 ms ✗ | 37,85 ms ✓ | 500 ms |
| `consent_check` p(95) | 63,42 ms ✗ | 13,84 ms ✓ | 20 ms |
| `timeline` p(95) | 840,22 ms ✗ | 52,59 ms ✓ | 800 ms |
| `http_req_failed` | 0,00% ✓ | 0,00% ✓ | < 1% |

A hipótese registrada à época — de que as falhas decorriam do **canal de medição** e
não da arquitetura — fica confirmada. A separação do gerador de carga em uma segunda
máquina, adotada por rigor metodológico, foi também a condição que tornou os RNFs de
projeto verificáveis. **É um argumento de método para a Metodologia**, não apenas um
detalhe de infraestrutura: instrumentação intrusiva pode dominar o fenômeno medido.

> Ressalva de comparabilidade: a execução [3] usou 1000 VUs e este ensaio, 150. A
> comparação vale para a ordem de grandeza do overhead do canal, não como medida
> pareada. O cenário B da bateria oficial (1000 VUs) fornecerá a comparação direta.

### 6.3 Observações para a análise

- **`iteration_duration` p(95) = 493,91 ms não é latência.** Inclui o *think time*
  aleatório de 0–500 ms embutido no `load.js`. A latência do sistema é o
  `http_req_duration`. Evitar a troca ao redigir.
- **Valores máximos discrepantes:** 2,94 s em `timeline` e 2,29 s em `consent_check`,
  contra medianas de 18,82 ms e 5,66 ms. Compatível com aquecimento da JVM ou pausa de
  coleta de lixo durante a rampa. Na bateria oficial, verificar no
  `resultados-infra/*/pools.csv` se o instante do máximo coincide com pico de
  `jvm_gc_pause_seconds_sum` ou com evento do HPA.
- **Falhas:** 3 requisições em 233.428 (0,0013%) — 1 `consent` e 1 `patient` entre os
  *checks*. Volume compatível com ruído de rampa; se reaparecer na bateria, investigar.
- **Escalonamento:** com 150 VUs e infraestrutura folgada, espera-se pouco ou nenhum
  acionamento do HPA. O escalonamento é objeto do cenário B.

**Artefatos:** `tests/k6/resultados/20260919-1405/` na VM-2.
