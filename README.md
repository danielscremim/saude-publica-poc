# Plataforma de Distribuição de Dados em Saúde Pública — PoC

Prova de Conceito da arquitetura de microsserviços do TCC *"Arquitetura de
Microsserviços e Interoperabilidade para Distribuição de Dados na Saúde Pública"*
(MBA em Engenharia de Software — USP/Esalq).

**10 microsserviços** distribuídos em 4 grupos de domínio, comunicação assíncrona
via Kafka, identidade OAuth2/JWT, consent LGPD com checagem inline, log de
auditoria imutável e fluxo bidirecional (UBS interno + instituições privadas
enviando resultados via API autenticada).

## Justificativa e Escopo

**"Saúde Pública"** no título refere-se à **disciplina** (saúde da população), não ao setor
público SUS exclusivamente — por isso a arquitetura abrange produtores públicos (UBS,
hospitais públicos) **e** privados (laboratórios, hospitais privados) de forma uniforme.

**Problema:** dados clínicos no Brasil estão fragmentados em silos institucionais — cada
instituição tem seu sistema, seus formatos, seu mecanismo de autenticação. Vigilância
epidemiológica, gestão pública e qualquer uso secundário legítimo (incluindo análise
preditiva por IA, no futuro) esbarra em "não existe forma legal **e** técnica de acessar
isso de forma unificada".

**O que esta PoC entrega:** uma **arquitetura de referência** dessa camada de distribuição —
interoperabilidade (uma única API para produtores e consumidores heterogêneos), escala
(microsserviços + Kafka), privacidade por design (consent inline + auditoria imutável).
É **blueprint**, não produto pronto para produção (faltam certificações ANPD/CFM,
integração com RNDS/e-SUS/CADSUS, padrões HL7 FHIR/TISS, etc.).

**O que está fora do escopo:** modelos de IA / análise preditiva. O TCC é sobre a
**infraestrutura que torna esses usos possíveis sob LGPD**, não sobre o algoritmo. Um
sistema analítico no futuro entra como mais um `auth_client` consumindo o **mesmo
contrato de API** (`GET /v1/patients/{uuid}/clinical-timeline`), sob o mesmo controle de
consentimento e auditoria — sem precisar de "endpoint especial de IA". Discussão completa
em [CLAUDE.md §1.1](CLAUDE.md).

### Posicionamento vs RNDS

A **RNDS** (Rede Nacional de Dados em Saúde / MS) existe e está em operação, mas seu
[Manual de Informação oficial](https://rnds-guia.saude.gov.br/docs/rel/mi-rel/) declara
que o módulo de **exames laboratoriais cobre hoje apenas dois patógenos**: SARS-CoV-2
(COVID-19) e Orthopoxviruses (Monkeypox). A rotina clínica brasileira — glicemia,
hemograma, colesterol, exames de imagem, anatomia patológica, e as **41 doenças de
notificação compulsória** — está em planos sem cronograma público. Esta PoC explora
deliberadamente esse universo, com tratamento uniforme público × privado, consent ativo
granular (≤ 20 ms inline) e auditoria visível ao paciente — combinações que a RNDS hoje
não pratica publicamente. Comparação completa, diferenciais e fraquezas honestas em
[CLAUDE.md §1.2](CLAUDE.md).

```
                ┌──────────────────────────────────────────────────────┐
                │                Kong API Gateway (8000)               │
                └─────┬──────────────┬──────────────┬──────────────────┘
                      │              │              │
   ┌──────────────────┘              │              └──────────────────┐
   ▼                                 ▼                                 ▼
patient  exam ──Kafka(exam.requested)──> lab ──Kafka(exam.completed)──> result
                                                                         ▲
                                            ┌────POST autenticado──HOSPITAL_PRIVADO
                                            │       (fluxo bidirecional)
                                            ▼
                                    ──Kafka(exam.completed)──> notification
                                                              audit
                                                              (consome audit.events
                                                               de history+result)

  auth ── emite JWT ──> usado por history e result
  consent ── checado por history (403 se negado) ── publica consent.revoked
  triage ── registra triagem na UBS (auto-classificação Manchester)
```

## Tecnologias

- Java 21 + Spring Boot 3.3.5
- Apache Kafka (KRaft, sem Zookeeper)
- PostgreSQL 16 (um banco dedicado por serviço — *Database per Service*)
- Kong API Gateway (DB-less)
- jjwt 0.12.6 (JWT HS256 — auth/history/result)
- Docker + Docker Compose

## Pré-requisitos

- Docker e Docker Compose
- (Opcional, para rodar serviços fora do Docker) JDK 21 e Maven 3.9+

## Serviços e portas

| Serviço | Porta | Responsabilidade |
|---|---|---|
| patient-service | 8081 | Cadastro de paciente; tokeniza CPF → UUID interno (RNF-06) |
| exam-service | 8082 | Solicita exame; publica `exam.requested` |
| lab-service | 8083 | Consome `exam.requested`; publica `exam.completed` |
| result-service | 8084 | Consome `exam.completed`; POST autenticado (fluxo bidirecional) |
| auth-service | 8085 | Emite JWT HS256 (OAuth2 Client Credentials) com escopos granulares |
| consent-service | 8086 | Consentimento LGPD; check ≤ 20 ms; publica `consent.revoked` |
| history-service | 8087 | Fachada agregadora (timeline clínica) — checa consent + audita |
| audit-service | 8088 | Log imutável de acessos; anomaly detection (RNF-06) |
| notification-service | 8089 | Reage a `exam.completed`; envio multi-canal (LOG hoje) |
| triage-service | 8090 | Triagem UBS com classificação Manchester automática |
| Kong (proxy) | 8000 | Gateway de entrada |
| Kong (admin) | 8001 | API administrativa do Kong |
| Kafka UI | 8190 | Visualização de tópicos (http://localhost:8190) |
| PostgreSQL | 5432 | Banco de dados |

---

## Como rodar (tudo em Docker)

```bash
docker compose up --build
```

A primeira execução baixa imagens e compila os 10 serviços (alguns minutos).

Para subir apenas a infraestrutura (e rodar serviços pelo IDE):

```bash
docker compose up kafka kafka-ui postgres kong
```

Depois, em cada serviço, rode pelo VSCode ou via:

```bash
cd patient-service && ./mvnw spring-boot:run   # ou: mvn spring-boot:run
```

> Os serviços usam `localhost:29092` (Kafka) e `localhost:5432` (Postgres) por
> padrão quando rodados fora do Docker. Dentro do Docker Compose, essas URLs são
> sobrescritas automaticamente por variáveis de ambiente.

---

## Testando os fluxos

Execute os scripts na ordem abaixo para construir o estado completo:

```bash
./scripts/test-flow.sh             # patient -> exam -> lab -> result
./scripts/test-auth-consent.sh     # OAuth2 client_credentials + consent + revoke
./scripts/test-history.sh          # JWT + consent check + timeline agregada + audit
./scripts/test-audit.sh            # consumo de audit.events + anomaly detection
./scripts/test-notification.sh     # exam.completed -> notification automática
./scripts/test-triage.sh           # triagem com auto-classificação Manchester
./scripts/test-bidirectional.sh    # hospital privado POSTa resultado (JWT + scope)
```

Cada script imprime cada etapa e o resultado HTTP esperado.

### Exemplo manual (smoke test)

```bash
# 1. Cadastrar paciente (resposta traz UUID, nunca o CPF)
curl -s -X POST http://localhost:8081/v1/patients \
  -H "Content-Type: application/json" \
  -d '{"cpf":"12345678901","name":"Maria Silva","birthDate":"1985-03-12"}'

PACIENTE_UUID="<cole-o-uuid-aqui>"

# 2. Solicitar um exame (publica evento no Kafka)
curl -s -X POST http://localhost:8082/v1/exams \
  -H "Content-Type: application/json" \
  -d "{\"patientUuid\":\"$PACIENTE_UUID\",\"examType\":\"GLICEMIA\",\"origin\":\"UBS\"}"

# 3. Aguarde ~2s (lab processa e result armazena) e consulte o resultado
sleep 2
curl -s http://localhost:8084/v1/results/patient/$PACIENTE_UUID
```

### Documentação OpenAPI (RNF-05)

Cada serviço expõe Swagger UI em `http://localhost:<porta>/swagger-ui.html` (8081 a 8090).

### Observabilidade

- Health checks: `http://localhost:808X/actuator/health`
- Métricas Prometheus: `http://localhost:808X/actuator/prometheus`
- Tópicos Kafka: http://localhost:8190

---

## Deploy em Kubernetes + Istio

Manifests prontos em [`k8s/`](k8s/README.md): 10 Deployments com 2 réplicas + HPAs a 70% CPU (RNF-01),
mTLS STRICT via `PeerAuthentication` (RNF-03), Circuit Breaker via `DestinationRule.outlierDetection`
(RNF-02), Gateway alternativo + VirtualService. Script `k8s/scripts/deploy.sh` aplica tudo em ordem.

## Teste de carga com k6 + visualização Grafana

Scripts em [`tests/k6/`](tests/k6/README.md): smoke, load (cenário do RNF-01 — 1000 VUs sustentados),
stress (até 5000 VUs para achar o teto) e full-journey (jornada e2e). Métricas vão para InfluxDB e o
Grafana mostra ao vivo com dashboards pré-provisionados (RNFs mapeados em painéis).

```bash
docker compose -f docker-compose.metrics.yml up -d         # sobe InfluxDB + Grafana (3000/8086)
k6 run --out influxdb=http://localhost:8086/k6 tests/k6/load.js
# abrir http://localhost:3000 -> dashboard "Saude PoC - RNFs (k6)"
```

## Próximas fatias da PoC

- [ ] Deploy + validação dos manifests em cluster real (kind/Docker Desktop/cloud)
- [ ] Consumidores reais de `consent.revoked` (invalidação de cache em cascata)
- [ ] Integração de canais de notificação (EMAIL/WEBHOOK reais)
- [ ] Paciente como titular ativo (escopo `history:read:self`)

## Estrutura

```
saude-publica-poc/
├── docker-compose.yml
├── infra/
│   ├── postgres/init-databases.sql
│   └── kong/kong.yml
├── patient-service/     exam-service/        lab-service/      result-service/
├── auth-service/        consent-service/     history-service/  audit-service/
├── notification-service/ triage-service/
└── scripts/             # 7 scripts de teste end-to-end
```

## Convenções principais

- Package raiz `br.usp.esalq.saude.<service>`, REST sob `/v1/...`
- DTOs e eventos como `record` Java; entidades JPA com construtor protegido vazio
- `patientUuid` é a chave de partição de **todos** os eventos Kafka (ordenação por paciente)
- CPF nunca sai do `patient-service` (RNF-06 — inegociável)
- Consumidores Kafka **sempre** com `spring.json.use.type.headers: false` (records duplicados por serviço)
- JWT compartilhado entre auth (emissor) e history/result (validadores) via `AUTH_JWT_SECRET`

Detalhes completos em [CLAUDE.md](CLAUDE.md).


## GraphQL (camada de leitura)

O `history-service` expõe, além do REST, um endpoint GraphQL para consumidores autorizados
escolherem exatamente os campos de que precisam (evita *over-fetching*). Mesmo JWT, mesmo
consentimento, mesma auditoria do REST. Detalhes e justificativa: `docs/graphql-decisao-arquitetural.md`.

```bash
./scripts/test-graphql.sh                     # smoke test (docker compose)
cd history-service && mvn test                # teste automatizado (sem Kafka/Postgres)
k6 run -e BASE_HOST=<ip> -e KONG_PORT=8000 tests/k6/rest-vs-graphql.js   # comparativo REST x GraphQL
```
UI: http://localhost:8087/graphiql · Contrato (SDL): http://localhost:8087/graphql/schema
