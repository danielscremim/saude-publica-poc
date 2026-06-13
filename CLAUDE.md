# CLAUDE.md — Plataforma de Distribuição de Dados em Saúde Pública

> Arquivo de contexto para o Claude Code. Leia antes de qualquer tarefa neste repositório.

---

## 1. Contexto do projeto

Este repositório é a **Prova de Conceito (PoC)** de um TCC do MBA em Engenharia de
Software (USP/Esalq).

**Título:** *Arquitetura de Microsserviços e Interoperabilidade para Distribuição de Dados na Saúde Pública.*

**Problema de pesquisa:** como estruturar uma arquitetura de microsserviços
escalável e interoperável para a distribuição de dados primários na rede de saúde
pública, mantendo a integridade da informação sob a ótica da Privacidade por Design.

**Princípio orientador:** a tecnologia (Kong, Istio, Kafka) é o *meio*. O *fim* é uma
camada de interoperabilidade que permita receber, armazenar e distribuir dados de
exames clínicos entre instituições públicas e privadas, de forma escalável e segura.
Toda decisão técnica deve servir à interoperabilidade e ao controle de privacidade.

**ESCOPO — três responsabilidades, e só isso:** (1) receber dados de exames de
instituições públicas e privadas; (2) armazenar de forma confiável, suportando alto
volume de requisições; (3) distribuir/disponibilizar os dados de forma interoperável e
padronizada a consumidores autorizados.

**FORA DO ESCOPO:** NÃO implemente IA, modelos de machine learning ou análise
preditiva. O uso futuro dos dados para análise preditiva é apenas a *motivação* do
trabalho — como esse uso será feito não importa e não faz parte da entrega. NÃO existe
"ator de IA" nem "endpoint de IA" na arquitetura: qualquer consumidor autorizado (médico,
hospital ou, no futuro, um sistema analítico) usa o MESMO contrato de API padronizado.

---

## 1.1 Justificativa e Escopo (defesa)

### O termo "Saúde Pública"

No título do trabalho, **"Saúde Pública" refere-se à disciplina** (*Public Health* —
saúde da população) e não ao "setor público SUS". Por isso a arquitetura abrange
tanto produtores públicos (UBS, hospitais públicos) quanto privados (laboratórios,
hospitais privados): vigilância epidemiológica, gestão de recursos e qualquer uso
secundário relevante para saúde populacional dependem de enxergar os dois lados.

### Problema endereçado

Os dados clínicos no Brasil estão **fragmentados em silos institucionais**:
- Cada UBS, hospital público, hospital privado e laboratório mantém seus próprios
  bancos, formatos e mecanismos de autenticação.
- O paciente carrega papel ou PDFs entre instituições.
- Médicos pedem exames repetidos por não enxergarem o histórico de outras redes.
- Vigilância epidemiológica fica míope (vê fragmentos, não o todo).
- Qualquer uso secundário legítimo — pesquisa, gestão pública, análise preditiva,
  modelos de IA — esbarra em "não existe forma legal **e** técnica de acessar isso
  de forma unificada".

O obstáculo principal **não é o algoritmo nem a regulamentação isoladamente** —
é a **ausência de uma camada de distribuição** que combine interoperabilidade
(falar uma mesma API com produtores heterogêneos), escala (suportar a rede
nacional) e privacidade por design (consent inline + auditoria imutável).

### O que o trabalho entrega

Uma **arquitetura de referência com PoC funcional** dessa camada de distribuição:
10 microsserviços com responsabilidades claras, comunicação assíncrona (Kafka),
identidade OAuth2/JWT (auth-service), controle de consentimento ativo do paciente
(consent-service com verificação inline em ≤ 20 ms), auditoria imutável de cada
acesso (audit-service com detecção de anomalia), fluxo bidirecional (instituições
externas postando resultados via API autenticada com escopo `result:write`),
manifests Kubernetes com mTLS STRICT + HPA + Circuit Breaker e bateria de testes
de carga (k6 + Grafana) validando os RNFs.

### O que o trabalho NÃO entrega (e por quê)

- **Modelo de IA / análise preditiva.** É deliberadamente fora do escopo. O
  problema de pesquisa do TCC é a **infraestrutura**, não o modelo. Modelos
  preditivos em saúde são abundantes na literatura; o que é raro e técnico é
  construir a fundação que permita esses modelos operarem dentro da LGPD.
- **Substituto para a RNDS (Rede Nacional de Dados em Saúde) do Ministério da
  Saúde.** A PoC propõe um padrão arquitetural análogo, com a adição de
  produtores privados de forma estruturada e LGPD enforced no caminho crítico.
- **Sistema pronto para produção.** Faltaria certificação ANPD/CFM, conformidade
  com HL7 FHIR / TISS / TUSS, integração com e-SUS/CADSUS/CNES, DR multi-região
  e auditoria de segurança independente.

### Por que centralizar a distribuição habilita análise preditiva futura

Se uma instituição (pública ou privada) quiser usar esses dados para análise
preditiva de saúde do paciente, **a unificação remove o obstáculo dominante**:
sem ela, o consumidor analítico precisaria negociar acesso com N instituições
separadamente, integrar N APIs distintas e tratar N formatos. Com a arquitetura
proposta:

- **Um único contrato de API** (`GET /v1/patients/{uuid}/clinical-timeline`)
  expõe a série temporal consolidada.
- **Um único mecanismo de autenticação** (OAuth2 client credentials emitido pelo
  auth-service) cobre qualquer consumidor.
- **Um único modelo de autorização** (consent ativo do paciente verificado
  inline) garante conformidade LGPD em todos os acessos.
- **Auditoria uniforme** (audit-service) torna cada leitura rastreável e habilita
  detecção de anomalia (ex: > 200 consultas / 10 min por paciente → alerta).

Adicionar um novo consumidor analítico (médico privado, sistema de IA, painel
de saúde populacional) é literalmente **registrar mais um `auth_client`** com o
escopo apropriado. **Esse é o ganho técnico central do trabalho.**

### Limites conhecidos para uso analítico (mencionar na defesa)

- **Granularidade do consent.** O consent atual é binário por (paciente,
  instituição). Para uso secundário em pesquisa/IA, o ideal seria evoluir para
  escopos específicos (ex: `research:read:anonymized`) — extensão natural, não
  presente nesta PoC.
- **Anonimização.** Modelos populacionais geralmente não precisam de
  `patientUuid` específico. Uma camada de anonimização/pseudonimização entre
  history-service e consumidor analítico seria necessária para esse uso.
- **Single point of trust.** Centralizar a *distribuição* (não o armazenamento —
  cada serviço tem seu DB) concentra a superfície de auditoria. É mitigado por
  mTLS STRICT, audit log imutável e anomaly detection, mas precisa ser
  declarado abertamente.
- **Alternativa não explorada: federated learning** — treinar modelos sem mover
  dados brutos. É outra arquitetura, fora do escopo deste TCC.

---

## 1.2 Posicionamento vs RNDS (Rede Nacional de Dados em Saúde)

### O que a RNDS já cobre hoje

A **RNDS** (Rede Nacional de Dados em Saúde) existe desde 2020, operada pelo
Ministério da Saúde, baseada em **HL7 FHIR R4**, com autenticação por
certificado **ICP-Brasil + OAuth2**, exposta ao cidadão via **Conecte SUS**.
Cobre hoje, segundo o próprio Manual de Informação oficial (MI-REL):

- **Vacinação**: COVID-19, Monkeypox, Influenza e rotina vacinal (SUS e privado).
- **Receituário eletrônico**.
- **Atendimento ambulatorial** (APAC, AIH).
- **Exames laboratoriais (REL):** **apenas dois patógenos**:
  1. SARS-CoV-2 (COVID-19)
  2. Orthopoxviruses (Monkeypox e correlatos)

Tudo o mais (rotina clínica: glicemia, hemograma, colesterol, TSH, urina,
imagem, anatomia patológica, e as **41 doenças de notificação compulsória**)
está em **planos sem cronograma público de implementação**. Fonte oficial:
*"Oportunamente o escopo do MI REL será ampliado, a partir da inclusão de
termos padronizados na lista de suspeita diagnóstica, patógenos, e respectiva
lista de exames."* — https://rnds-guia.saude.gov.br/docs/rel/mi-rel/

### Tabela do gap atual (a base do argumento)

| Domínio | Coberto pela RNDS hoje | Coberto por esta PoC |
|---|---|---|
| Vacinação | ✅ COVID-19, Monkeypox, Influenza, rotina | ❌ (fora do escopo) |
| Receituário eletrônico | ✅ | ❌ (fora do escopo) |
| Exame: SARS-CoV-2 (COVID) | ✅ | ✅ (modelo genérico) |
| Exame: Orthopoxvirus (Monkeypox) | ✅ | ✅ (modelo genérico) |
| **Exames de rotina clínica (glicemia, hemograma, colesterol, TSH...)** | ❌ | ✅ |
| **Exames de imagem (raio-X, tomografia, RM)** | ❌ | ✅ (mesmo modelo, `examType` aberto) |
| **Anatomia patológica / biópsias** | ❌ | ✅ |
| **41 doenças de notificação compulsória** (dengue, hanseníase, hepatites...) | 🕒 planejado, sem prazo | ✅ |
| **Produtores privados em pé de igualdade** (laboratório/hospital) | ⚠️ via Abramed (parceria pontual) | ✅ por desenho |
| **Consent ativo granular por instituição** | ⚠️ Conecte SUS (granularidade agregada) | ✅ inline, ≤ 20 ms, com revogação ≤ 1 s |
| **Audit + anomaly detection visível ao paciente** | ⚠️ não exposto publicamente | ✅ feature de primeira classe |
| **CPF totalmente tokenizado fora do serviço de cadastro** | ❌ CPF circula em vários fluxos | ✅ UUID em tudo, exceto patient-service |

### Argumento de posicionamento para a defesa

> *"A RNDS é um sistema em produção, mas seu escopo atual de exames
> laboratoriais se restringe a dois patógenos — SARS-CoV-2 e Orthopoxvirus —
> ligados a Emergências em Saúde Pública de Importância Internacional
> declaradas pela OMS. A rotina clínica brasileira (exames de patologia clínica
> ambulatorial, exames de imagem, anatomia patológica) e as 41 doenças de
> notificação compulsória estão em planos sem cronograma de implementação.
> Esta PoC explora deliberadamente esse universo de exames clínicos genéricos,
> com tratamento uniforme público × privado, consent ativo granular e auditoria
> visível ao paciente — combinações que a RNDS hoje não pratica publicamente."*

### Onde a PoC se diferencia (5 pontos defensáveis)

1. **Tokenização total do CPF** — CPF restrito ao `patient-service`; UUID
   tokenizado em todos os outros serviços, eventos Kafka e APIs externas.
2. **Consent ativo inline com SLA mensurado** — verificação obrigatória ≤ 20 ms,
   propagação de revogação ≤ 1 s via Kafka.
3. **Tratamento uniforme público × privado** — `auth_client` agnóstico ao "lado";
   o tipo de instituição é só metadado de proveniência (`origin`).
4. **Auditoria + anomaly detection como features de primeira classe** —
   `GET /v1/audit/patient/{uuid}` poderia alimentar portal do paciente
   ("quem acessou meus dados?"); anomaly detection automática (> N consultas
   por janela vira alerta LGPD).
5. **Arquitetura aberta e reproduzível** — Compose + manifests K8s + k6 + Grafana
   documentados em open-source; a RNDS é caixa preta para quem está fora do MS.

### Onde a PoC é MAIS FRACA que a RNDS (honestidade — banca vai cobrar)

| Aspecto | RNDS | Esta PoC |
|---|---|---|
| Padrão de dados clínicos | HL7 FHIR R4 (padrão mundial) | Contrato JSON próprio simplificado |
| Identificação institucional | Certificado ICP-Brasil + OAuth2 | OAuth2 client_secret básico |
| Integração com SUS | Nativa (CADSUS, CNES, e-SUS, DataSUS) | Inexistente |
| Escala real | Produção, milhões de registros | PoC em laboratório |
| Certificação regulatória | ANPD, CFM, CFF | Nenhuma |
| Anos de evolução | ~6 anos | TCC |

### Como posicionar a relação na defesa

**Evitar:** *"minha plataforma substitui/concorre com a RNDS"*.
**Preferir** uma destas três posturas:

1. **Complementar regional**: estados, municípios e redes privadas (operadoras
   de saúde, redes hospitalares) que ainda não integram com a RNDS poderiam
   adotar este padrão para seus sistemas locais, com possibilidade futura de
   ponte para a RNDS via adapter FHIR.
2. **Laboratório acadêmico de design**: exercício que explora variações
   arquiteturais (consent ativo, tokenização rigorosa, anomaly detection
   visível) que a literatura nacional discute pouco e a RNDS não pratica
   publicamente.
3. **Material didático/referência**: PoC reproduzível para ensino de como
   combinar microsserviços + LGPD por design + interoperabilidade em saúde.

### Fontes oficiais (para citar no relatório)

- Modelo de Informação - REL (escopo dos exames laboratoriais):
  https://rnds-guia.saude.gov.br/docs/rel/mi-rel/
- RNDS — portal oficial Ministério da Saúde:
  https://www.gov.br/saude/pt-br/composicao/seidigi/rnds
- FAQ RNDS:
  https://www.gov.br/saude/pt-br/composicao/seidigi/rnds/perguntas-e-respostas/faq
- Legislação RNDS:
  https://www.gov.br/saude/pt-br/composicao/seidigi/rnds/legislacao
- Portaria nº 883/2022:
  https://bvsms.saude.gov.br/bvs/saudelegis/Saes/2022/prt0883_07_12_2022.html
- Nota técnica DATASUS sobre envio para a RNDS (Sec. Saúde RS):
  https://www.cevs.rs.gov.br/upload/arquivos/202404/29095847-nota-tecnica-conjunta-datasus-envio-de-dados-para-a-rnds-de-sistemas-proprios.pdf

---

## 2. Stack tecnológico (não substituir sem motivo)

- **Java 21** + **Spring Boot 3.3.5**
- **Maven** (cada serviço tem seu `pom.xml`; sem multi-módulo por enquanto)
- **Apache Kafka** (KRaft, sem Zookeeper) — comunicação assíncrona
- **PostgreSQL 16** — um banco dedicado por serviço (*Database per Service*)
- **Kong API Gateway** (DB-less) — entrada das requisições
- **jjwt 0.12.6** — emissão e validação de JWT HS256 (auth/history/result)
- **Docker + Docker Compose** — ambiente local atual
- **Istio + Kubernetes** — alvo futuro (mTLS, HPA, Circuit Breaker)
- **k6** — teste de carga (futuro)
- **Observabilidade futura:** Prometheus + Grafana + Jaeger + Kiali

---

## 3. Convenções do projeto (SEGUIR SEMPRE)

- **Package raiz:** `br.usp.esalq.saude.<service>` (ex: `br.usp.esalq.saude.patient`).
- **Estrutura de cada serviço:** `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `event/`, `config/` (+ `security/` quando há JWT, + `client/` em fachadas como history).
- **DTOs e eventos são `record` Java.** Entidades JPA são classes com construtor protegido vazio.
- **Endpoints REST:** versionados com prefixo `/v1/...`.
- **IDs:** o paciente é sempre referenciado por `UUID` interno, NUNCA por CPF.
- **CPF nunca aparece** em resposta de API, log, evento Kafka ou DTO. Só existe dentro do `patient-service`, no banco `patientdb`. Isso é o RNF-06 — é inegociável.
- **Eventos Kafka:** a chave (key) é sempre o `patientUuid` (garante ordenação por paciente).
- **Records de evento são duplicados em cada serviço** (sem módulo compartilhado). Consequência: **consumidores PRECISAM ter `spring.json.use.type.headers: false`** — o header `__TypeId__` do produtor aponta para a classe no pacote do produtor, que não existe no consumidor; sem essa flag o `JsonDeserializer` falha silenciosamente com `RecordDeserializationException`.
- **OpenAPI:** todo serviço com REST expõe Swagger via `springdoc` (RNF-05).
- **application.yml:** defaults apontam para `localhost` (execução via IDE). Docker sobrescreve via env vars (`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`).
- **JWT:** o segredo `AUTH_JWT_SECRET` é compartilhado entre auth-service (emissor), history-service e result-service (validadores). Mesmo segredo, mesmo `AUTH_JWT_ISSUER`.
- **Idempotência em consumidores:** quando o PK do entity coincide com o ID do evento Kafka, capturar `DataIntegrityViolationException` e ignorar (ver audit-service / result-service).
- **Idioma:** comentários e mensagens de log em português; nomes de código em inglês.
- **Sem acentos em comentários de código-fonte Java** (evita problemas de encoding em alguns ambientes).

---

## 4. Os 10 microsserviços da arquitetura

| Serviço | Porta | Status | Responsabilidade |
|---|---|---|---|
| patient-service | 8081 | ✅ FEITO | Cadastro; tokeniza CPF→UUID; único que resolve CPF |
| exam-service | 8082 | ✅ FEITO | Solicita exame; publica `exam.requested` |
| lab-service | 8083 | ✅ FEITO | Consome `exam.requested`; publica `exam.completed` |
| result-service | 8084 | ✅ FEITO | Consome `exam.completed`; POST autenticado (fluxo bidirecional); expõe resultados |
| auth-service | 8085 | ✅ FEITO | Emissão de JWT HS256 (OAuth2 Client Credentials); escopos granulares |
| consent-service | 8086 | ✅ FEITO | Consentimento LGPD; check ≤ 20 ms; publica `consent.revoked` |
| history-service | 8087 | ✅ FEITO | Fachada agregadora; checa consent (403 se negado); publica `audit.events` |
| audit-service | 8088 | ✅ FEITO | Consome `audit.events`; log imutável (PK = eventId, idempotente); anomaly detection |
| notification-service | 8089 | ✅ FEITO | Consome `exam.completed`; envia notificação (LOG / estrutura pronta p/ EMAIL/SMS/WEBHOOK) |
| triage-service | 8090 | ✅ FEITO | Registro de triagem na UBS; classificação Manchester automática |

**Grupos de domínio:** Identidade/Acesso (auth, consent); Gestão Clínica (patient,
triage, exam); Laboratório (lab, result); Integração/Suporte (history, audit, notification).

---

## 5. Esquemas dos eventos Kafka

Tópicos: `exam.requested`, `exam.completed`, `audit.events`, `consent.revoked`.

```
exam.requested   (produtor: exam-service | consumidor: lab-service)
{ examId: UUID, patientUuid: UUID, examType: String, origin: String, requestedAt: Instant }

exam.completed   (produtores: lab-service, result-service[POST externo] | consumidores: result-service, notification-service)
{ examId: UUID, patientUuid: UUID, examType: String, origin: String,
  resultValue: double, resultUnit: String, completedAt: Instant }

audit.events     (produtores: history-service, result-service | consumidor: audit-service)
{ eventId: UUID, requesterId: String, patientUuid: UUID, action: String,
  purpose: String, sourceService: String, timestamp: Instant }
  # actions atuais: READ_TIMELINE, READ_TIMELINE_DENIED, WRITE_RESULT

consent.revoked  (produtor: consent-service | consumidores: [futuro] serviços de dados p/ invalidar cache)
{ patientUuid: UUID, institutionId: String, revokedAt: Instant }
```

> `origin` (data provenance) ∈ { UBS, LAB_PUBLICO, LAB_PRIVADO, HOSPITAL_PRIVADO }.
> Records de evento duplicados por serviço (sem módulo compartilhado) — ver convenção do `use.type.headers: false` na seção 3.

---

## 6. Contratos de API

```
patient-service
  POST /v1/patients
    req:  { cpf: "11 dígitos", name: String, birthDate: "yyyy-MM-dd" }
    resp: 201 { uuid, name, birthDate }     # SEM CPF
  GET  /v1/patients/{uuid}
    resp: 200 { uuid, name, birthDate }

exam-service
  POST /v1/exams
    req:  { patientUuid: UUID, examType: String, origin: String }
    resp: 201 { examId, patientUuid, examType, origin, status, requestedAt }

result-service
  POST /v1/results                                 # FLUXO BIDIRECIONAL
    auth: Bearer JWT (scope obrigatório: result:write)
    req:  { examId?: UUID, patientUuid: UUID, examType: String,
            origin: "UBS|LAB_PUBLICO|LAB_PRIVADO|HOSPITAL_PRIVADO",
            resultValue: double, resultUnit: String, completedAt?: Instant }
    resp: 201 { examId, patientUuid, examType, origin, resultValue, resultUnit, completedAt }
    side-effects: publica exam.completed + audit.events (WRITE_RESULT)
  GET  /v1/results/patient/{patientUuid}
    resp: 200 [ { examId, patientUuid, examType, origin, resultValue, resultUnit, completedAt } ]

auth-service
  POST /v1/clients                                  # registro admin (PoC: aberto)
    req:  { clientId, clientSecret, institutionId, scopes }
    resp: 201 { id, clientId, institutionId, scopes }
  POST /v1/auth/token                               # OAuth2 client_credentials
    req:  { clientId, clientSecret, scope? }
    resp: 200 { accessToken, tokenType:"Bearer", expiresIn, scope }

consent-service
  POST /v1/consents
    req:  { patientUuid, institutionId, scope }
    resp: 201 { id, patientUuid, institutionId, scope, grantedAt, revokedAt }
  DELETE /v1/consents/{id}                          # publica consent.revoked
  GET    /v1/consents/check?patientUuid=&institutionId=
    resp: 200 { patientUuid, institutionId, granted, scope }
  GET    /v1/consents/patient/{patientUuid}

history-service                                     # FACHADA AGREGADORA
  GET /v1/patients/{patientUuid}/clinical-timeline?purpose=
    auth: Bearer JWT (qualquer escopo)
    fluxo: check consent (consent-service) -> agrega patient + result -> publica audit.events
    resp: 200 { patientUuid, patient, exams[], totalExams }
          403 consent_denied  (publica audit READ_TIMELINE_DENIED)
          401 invalid_token / missing_token

audit-service                                       # LOG IMUTÁVEL (sem UPDATE/DELETE)
  GET /v1/audit/patient/{patientUuid}
  GET /v1/audit/requester/{requesterId}
  GET /v1/audit/anomalies

notification-service
  POST /v1/notifications
    req:  { patientUuid, channel:"LOG|EMAIL|SMS|WEBHOOK", subject, message }
    resp: 201 { id, ..., status:"SENT|FAILED" }
  GET  /v1/notifications/patient/{patientUuid}

triage-service
  POST /v1/triages
    req:  { patientUuid, performedBy, unit,
            bloodPressureSystolic?, bloodPressureDiastolic?, heartRate?,
            respiratoryRate?, temperature?, oxygenSaturation?, painLevel?,
            complaint?, priority?: "RED|ORANGE|YELLOW|GREEN|BLUE" }
    resp: 201 { ..., priority (auto-classificada se omitida) }
  GET  /v1/triages/patient/{patientUuid}
```

---

## 7. Os 6 requisitos não funcionais (critérios de aceitação)

- **RNF-01 Escalabilidade:** P95 ≤ 500 ms para 1000 req simultâneas; HPA a 70% CPU.
- **RNF-02 Disponibilidade:** ≥ 99,9%; ≥ 2 réplicas; Circuit Breaker a 50% de falhas; self-healing ≤ 30 s.
- **RNF-03 Segurança/LGPD:** mTLS STRICT; JWT exp. 1 h; auditoria imutável; zero CPF em logs.
- **RNF-04 Observabilidade:** 100% trace ID; dashboards Grafana; alerta P95 > 500 ms.
- **RNF-05 Interoperabilidade:** OpenAPI 3.0 em todos os endpoints; Adapter Pattern; history ≤ 800 ms.
- **RNF-06 Consentimento:** consent explícito em 100% das requisições externas; sem consent → HTTP 403; rate limit 60/h por paciente por instituição; CPF tokenizado; revogação ≤ 1 s via Kafka; anomaly detection (default RNF: 200 consultas/10 min; PoC sobrescreve para 10 via `AUDIT_ANOMALY_THRESHOLD` para demonstração).

---

## 8. Como rodar

```bash
# Tudo em Docker (primeira execução baixa imagens e compila — alguns minutos):
docker compose up --build

# Só a infra (e rodar serviços pelo IDE):
docker compose up kafka kafka-ui postgres kong

# Suite completa de testes end-to-end (na ordem que constrói o estado):
./scripts/test-flow.sh             # patient -> exam -> lab -> result
./scripts/test-auth-consent.sh     # client OAuth2 + consent + revoke
./scripts/test-history.sh          # JWT + consent check + timeline + audit
./scripts/test-audit.sh            # consumo + anomaly detection
./scripts/test-notification.sh     # exam.completed -> notification
./scripts/test-triage.sh           # triagem com auto-classificação Manchester
./scripts/test-bidirectional.sh    # POST autenticado de hospital privado
```

Portas: Kong proxy 8000, Kong admin 8001, Kafka host 29092, **Kafka UI 8190** (mudou de 8090 para liberar triage), Postgres 5432.

---

## 9. Próximas tarefas

1. **Kubernetes + Istio** — ✅ manifests prontos em `k8s/` (10 Deployments com 2 réplicas, HPAs a 70% CPU, PeerAuthentication STRICT, DestinationRules com circuit breaker, Gateway). Falta deploy/validação em cluster real (requer kubectl + istioctl + metrics-server). Ver `k8s/README.md`.
2. **k6** — script de teste de carga validando o RNF-01.
3. Evolução opcional: paciente como titular ativo (escopo `history:read:self`), integração real de canais de notificação (EMAIL/WEBHOOK), consumidores reais de `consent.revoked` para invalidação de cache.

---

## 10. Cenário de uso completo (referência)

UBS: paciente → auth → patient-service (gera UUID) → triagem → exam-service (publica Kafka)
→ lab processa → result armazena → notification alerta → paciente registra consentimento
→ hospital privado consulta via OAuth2 Client Credentials (history checa consent; 403 se negado).

**Fluxo bidirecional:** hospital privado/lab privado faz `POST /v1/results` com JWT (scope `result:write`) → result-service persiste → publica `exam.completed` (notification dispara) e `audit.events` (audit registra WRITE_RESULT).

Qualquer consumidor autorizado usa o mesmo contrato de API, sob o mesmo controle de consentimento.
Todo acesso é auditado pelo audit-service via Kafka.

---

## 11. Restrições importantes

- **NÃO** exponha CPF em nenhuma saída (API, log, evento). Use sempre o UUID.
- **NÃO** implemente IA/ML nem qualquer lógica de análise preditiva — apenas o endpoint que disponibiliza os dados. O consumo analítico está FORA do escopo.
- **NÃO** quebre os contratos de API existentes sem versionar (`/v2/...`).
- **NÃO** adicione novas tecnologias ao stack sem necessidade justificada por um RNF.
- Ao criar um novo serviço, **siga o template** de um serviço existente (estrutura, application.yml com defaults localhost, Dockerfile multi-stage, OpenAPI).
- Ao criar um novo consumidor Kafka, **sempre** incluir `spring.json.use.type.headers: false` (ver convenção na seção 3).
