# Roteiro de apresentação — TCC

> Script breve para você se reorientar no projeto e estruturar a fala inicial.
> Versão sem resultados quantitativos (preencha após rodar o k6 no server).
> **Tempo estimado:** ~7-10 minutos de fala corrida; ~12-15 com pausas/respiração.

---

## 0. Antes de começar — você precisa estar confortável com 3 frases

Decore (ou pelo menos saiba reformular):

1. **O título resumido:** *"Construí uma arquitetura de microsserviços que distribui dados clínicos entre instituições públicas e privadas, com privacidade por design."*

2. **O argumento de existência (por que isso, não outra coisa):** *"A RNDS do Ministério da Saúde cobre hoje só dois patógenos em exames laboratoriais — COVID e Monkeypox. A rotina clínica brasileira não tem caminho estruturado de distribuição. Meu trabalho explora arquiteturalmente esse espaço."*

3. **O escopo deliberado (o que NÃO fiz):** *"Não implementei IA, não implementei algoritmo preditivo. O trabalho é sobre a infraestrutura que torna esses usos possíveis sob LGPD — o algoritmo é outro trabalho."*

Se você perder o fio em qualquer momento, volte a uma dessas três frases.

---

## 1. Abertura (~1 min)

> "Boa tarde. Meu TCC se chama **'Arquitetura de Microsserviços e Interoperabilidade para Distribuição de Dados na Saúde Pública'**.
>
> Antes de entrar no que fiz, deixa eu situar o problema que motivou o trabalho.
>
> Os dados clínicos no Brasil — exames laboratoriais, prontuários, históricos — estão hoje **fragmentados em silos institucionais**. Cada UBS tem seu sistema, cada hospital público tem o dele, cada laboratório privado tem o dele, cada hospital privado tem o dele. **O paciente carrega papel ou PDF entre eles.** O médico pede exame repetido porque não vê o histórico. A vigilância epidemiológica olha pedaços, não o todo.
>
> Existe a RNDS, do Ministério da Saúde, que está em produção desde 2020. Mas — e isso é central pra defender meu trabalho — **o módulo de exames laboratoriais da RNDS cobre hoje apenas dois patógenos: SARS-CoV-2 e Monkeypox**. Isso está documentado no Manual de Informação oficial. Toda a rotina clínica (glicemia, hemograma, exames de imagem, anatomia patológica, as 41 doenças de notificação compulsória) está em planos sem cronograma.
>
> Esse é o espaço que meu trabalho explora."

**[Cue para slide se tiver: tabela do gap RNDS vs PoC — está no CLAUDE.md §1.2]**

---

## 2. Esclarecimento de termos (~30s)

> "Uma observação importante de terminologia: **'Saúde Pública' no título refere-se à disciplina** — saúde da população — **não ao setor público SUS**. Por isso a arquitetura inclui produtores privados também. Saúde pública moderna, no sentido epidemiológico, depende de enxergar os dois lados."

> *(Por que isso? Porque algum jurado da banca vai ler 'Saúde Pública' e perguntar 'mas isso é só do SUS?'. Você adianta a pergunta.)*

---

## 3. O que foi construído — visão geral (~2 min)

> "Implementei uma **arquitetura de referência** — não um produto pronto — com 10 microsserviços organizados em 4 grupos de domínio:
>
> 1. **Gestão clínica:** `patient-service`, `exam-service`, `triage-service`
> 2. **Laboratório:** `lab-service`, `result-service`
> 3. **Identidade e acesso:** `auth-service` (OAuth2, JWT), `consent-service` (LGPD)
> 4. **Integração e suporte:** `history-service`, `audit-service`, `notification-service`
>
> A comunicação assíncrona acontece via **Kafka** — quando um exame é solicitado, um evento `exam.requested` vai para a fila; o laboratório consome, processa, e publica `exam.completed`. O result-service armazena, o notification-service alerta o paciente. Tudo desacoplado.
>
> Na entrada, **Kong** funciona como API Gateway — roteamento, rate limiting. Em produção, **Istio** entraria como service mesh — mTLS estrito entre todos os pods, circuit breaker, observabilidade. Deixei os manifests prontos para Kubernetes."

**[Cue: diagrama da arquitetura — está no README.md]**

---

## 3a. O papel de cada microsserviço (~2.5 min)

> "Antes de entrar nas decisões, vou detalhar rapidamente o papel de cada um dos 10 microsserviços, agrupando por domínio. Isso ajuda a entender por que estão separados.
>
> **Identidade e acesso — 2 serviços:**
>
> - **`auth-service`** — emite tokens JWT no padrão OAuth2 Client Credentials. Toda instituição (UBS, hospital, laboratório) se cadastra como `auth_client`, recebe um clientId e clientSecret, e troca por um JWT válido por 1 hora. O token carrega o identificador da instituição e os escopos concedidos.
>
> - **`consent-service`** — gerencia o consentimento LGPD por paciente, por instituição, por escopo. Quando o paciente concede, fica registrado em banco. Quando ele revoga, publica um evento `consent.revoked` no Kafka, e qualquer cache de consumidor é invalidado em até 1 segundo. O endpoint `check` responde sim/não em menos de 20 milissegundos.
>
> **Gestão clínica — 3 serviços:**
>
> - **`patient-service`** — o único componente do sistema que conhece o CPF. Recebe o cadastro, gera um UUID interno, e a partir desse ponto **o CPF nunca mais aparece** — em nenhuma API, evento Kafka, log ou DTO de outro serviço.
>
> - **`triage-service`** — registra a triagem do paciente quando ele chega na UBS ou pronto-atendimento. Coleta sinais vitais (pressão, frequência cardíaca, SpO₂, temperatura, nível de dor, queixa) e aplica classificação automática **Manchester** — vermelho, laranja, amarelo, verde ou azul.
>
> - **`exam-service`** — registra a solicitação de exame pelo médico. Quando solicitado, publica um evento `exam.requested` no Kafka, com o UUID do paciente como chave de partição (garante ordenação por paciente).
>
> **Laboratório — 2 serviços:**
>
> - **`lab-service`** — consome `exam.requested`, simula o processamento laboratorial — em produção, integraria com equipamentos ou sistemas LIS reais — e publica `exam.completed` com o resultado.
>
> - **`result-service`** — consome `exam.completed` e armazena o histórico. **Também aceita POST autenticado** — esse é o fluxo bidirecional: um laboratório privado pode enviar resultado diretamente via API, com JWT e escopo `result:write`, sem passar pelo fluxo interno do `lab-service`.
>
> **Integração e suporte — 3 serviços:**
>
> - **`history-service`** — a fachada agregadora. Recebe o pedido de timeline clínica, valida o JWT, **chama o consent-service para verificar autorização**, e só então consulta o `patient-service` e o `result-service` para montar a resposta consolidada. Publica evento `audit.events` em cada acesso, seja autorizado ou negado.
>
> - **`audit-service`** — consome `audit.events` e mantém log imutável de todos os acessos. Identifica anomalias: se uma instituição consultar o mesmo paciente mais de N vezes em janela curta, gera alerta de incidente LGPD.
>
> - **`notification-service`** — consome `exam.completed` e gera notificações para o paciente. A arquitetura suporta múltiplos canais — hoje grava em log, com a estrutura preparada para EMAIL, SMS ou WEBHOOK como extensão natural."

**[Cue: se tiver slide com a tabela de 10 serviços, mostrar aqui. Está no CLAUDE.md §4]**

---

## 3b. Três cenários de uso real (~3 min)

> "Para concretizar como tudo isso se conecta, vou narrar três cenários típicos. Esses são os fluxos que validei nos scripts de teste e que demonstram a arquitetura ponta-a-ponta.
>
> **Cenário 1 — Maria vai à UBS porque está se sentindo cansada.**
>
> Ela chega no posto. A enfermeira faz a triagem: pressão 130 por 85, glicemia capilar levemente alta, queixa de fadiga. O `triage-service` registra e classifica como verde — pouco urgente. O médico atende, suspeita de diabetes, e solicita uma glicemia em jejum. O `exam-service` registra a solicitação e publica `exam.requested` no Kafka.
>
> Em segundos, o `lab-service` consome o evento, simula o processamento e publica `exam.completed` com o resultado: 187 mg/dL. O `result-service` consome e armazena. Em paralelo, o `notification-service` também consome o mesmo evento e dispara notificação para a Maria. Quando ela abrir o app, vai ver que o exame ficou pronto.
>
> **O ponto importante: tudo isso aconteceu sem chamada síncrona entre os serviços de pós-exame.** Mesmo se o notification-service estivesse fora do ar, o lab-service continuaria publicando, e a notificação aconteceria quando o notification voltasse — Kafka garante a entrega.
>
> **Cenário 2 — Maria desmaia no shopping e é levada ao Hospital São Lucas, privado.**
>
> O médico do pronto-socorro precisa ver o histórico clínico urgentemente. O sistema do hospital faz uma requisição: `GET /v1/patients/{uuid}/clinical-timeline` com o JWT do hospital.
>
> O `history-service` recebe, valida o token, **chama o consent-service**: 'a Maria autorizou o Hospital São Lucas a ler seus dados?'. Como ela ainda não autorizou nenhum hospital privado, a resposta é **não**. **O history-service retorna 403, e publica auditoria 'READ_TIMELINE_DENIED'.** O médico não vê nada.
>
> A Maria está consciente. O médico explica a situação. Ela autoriza pelo app, via consent-service: 'concedo ao Hospital São Lucas o escopo history:read:own_patients'. Em menos de 1 segundo, o evento de concessão propaga. **O médico tenta de novo: 200 OK** — a timeline aparece, com a glicemia alterada de duas semanas atrás. Auditoria 'READ_TIMELINE' publicada.
>
> **O ponto importante: a Maria pode, no app, ver exatamente quem acessou seus dados, quando, e por qual finalidade declarada — `audit-service` torna isso visível ao paciente, não só ao regulador.**
>
> **Cenário 3 — A Maria refaz a glicemia em um laboratório privado, o LabTech.**
>
> O LabTech tem o resultado no sistema deles, e precisa enviar para a plataforma. Faz um POST direto: `POST /v1/results` com JWT, escopo `result:write`, origin `LAB_PRIVADO`, o UUID da Maria, o valor do resultado.
>
> O `result-service` valida o token, confirma o escopo `result:write`, armazena. Em seguida publica `exam.completed` no Kafka — e a partir desse evento, exatamente como no Cenário 1, o `notification-service` dispara notificação para a Maria, e o `audit-service` registra 'WRITE_RESULT' por LabTech.
>
> **O ponto importante: o `lab-service` nem participou desse fluxo.** O lab-service simula laboratórios internos da rede; instituições externas entram pelo fluxo bidirecional, com autenticação OAuth2 e escopo verificado. **O resultado final vai pro mesmo banco, é exposto pela mesma API, e é agregado na mesma timeline que o exame do Cenário 1.** Para a Maria — e para o médico que for consultar — não há diferença entre 'exame público' e 'exame privado'. Tem apenas o metadado `origin` registrando de onde veio."

**[Cue: se quiser, slide separado por cenário com diagrama de sequência. Pode usar os scripts em `scripts/test-*.sh` como base]**

---

## 4. As decisões arquiteturais que importam (~2 min)

> "Tem cinco decisões deliberadas que diferenciam o trabalho. Vou destacar três que considero as mais defensáveis:
>
> **Primeira: o CPF nunca sai do `patient-service`.**
> Quando uma instituição cadastra um paciente, ela manda o CPF para o patient-service, que tokeniza em um UUID interno. **Em todos os outros 9 serviços, em todos os eventos Kafka, em todas as APIs externas, só existe o UUID.** Isso é privacidade por design no sentido literal — o dado sensível tem um único contexto de circulação.
>
> **Segunda: consent é verificado inline, antes de cada leitura clínica.**
> Quando uma instituição quer ler o histórico de um paciente — via o endpoint `/v1/patients/{uuid}/clinical-timeline` — o history-service **bloqueia a requisição** e chama o consent-service. Sem consentimento ativo daquele paciente para aquela instituição: 403. **Sempre. Sem exceção.** A meta de SLA é 20 milissegundos, e a verificação é feita via consulta indexada. Quando o paciente revoga o consentimento, o evento propaga via Kafka em menos de 1 segundo.
>
> **Terceira: tratamento uniforme público e privado.**
> Não existe 'fluxo para hospital público' e 'fluxo para hospital privado'. Existe `auth_client`. Uma UBS é um auth_client com determinados escopos. Um laboratório privado é outro auth_client com outros escopos. Os endpoints, o consent, a auditoria — tudo é o mesmo. **A distinção pública/privada é metadado de proveniência do dado**, não estrutural da arquitetura."

> *(Se tiver tempo nas perguntas, mencionar as outras duas: auditoria + anomaly detection como features de primeira classe; arquitetura aberta e reproduzível.)*

---

## 5. O que está deliberadamente FORA do escopo (~1 min)

> "É importante que eu seja explícito sobre o que **não** fiz.
>
> **Não implementei modelos de IA, nem machine learning, nem análise preditiva.** Essa decisão é central. Modelos preditivos em saúde têm literatura abundante. O que é raro e tecnicamente difícil é construir a infraestrutura que permita esses modelos operarem **dentro da LGPD, com auditoria, com consent rastreável**. Esse é o foco do meu trabalho.
>
> Um sistema de análise preditiva, no futuro, entra como **mais um `auth_client`** — cadastra-se, recebe um JWT com escopos próprios, consome o **mesmo endpoint** que qualquer outro consumidor autorizado. Não há 'ator de IA' na arquitetura. **A unificação remove o obstáculo dominante para esse tipo de uso secundário.**
>
> Também não substituo a RNDS. Não tenho certificação ANPD, não estou integrado com CADSUS, e-SUS, CNES. Não falo HL7 FHIR — meu contrato é JSON simplificado. **É arquitetura de referência, não produto.**"

---

## 6. Como validei a arquitetura (~1-2 min)

> "A validação tem três camadas.
>
> **Primeira: validação funcional.** Sete scripts de teste end-to-end cobrem todos os fluxos — cadastro de paciente, fluxo assíncrono via Kafka, OAuth2, consent grant e revoke, leitura autorizada e negada, auditoria com anomaly detection, classificação Manchester na triagem, e o fluxo bidirecional onde uma instituição privada envia resultado via POST autenticado. Todos passam.
>
> **Segunda: validação arquitetural para escala.** Os manifests Kubernetes estão prontos: cada serviço com **duas réplicas mínimas** (RNF-02), **HorizontalPodAutoscaler escalando até 10 réplicas a 70% de CPU** (RNF-01), **mTLS STRICT via PeerAuthentication do Istio** (RNF-03), **Circuit Breaker via DestinationRule** com 5 erros consecutivos para ejeção e 30 segundos de tempo mínimo ejetado (RNF-02).
>
> **Terceira: validação numérica via k6.** Tenho quatro cenários prontos: smoke (sanity), load (RNF-01 — 1000 VUs por 5 minutos), stress (até 5000 VUs), e jornada end-to-end. Os resultados saem para InfluxDB e o Grafana exibe em dois dashboards com painéis mapeados aos RNFs do projeto. **Vou executar isso no servidor da empresa antes da apresentação final** para ter os números reais."

> *(Quando tiver os números do k6, troque esta última frase para: "Os resultados mostram que…" + tabela)*

---

## 7. Limitações honestas (~30s)

> "Reconhecendo limites: **não testei em escala real**. Os dados são sintéticos. **Não tem auditoria de segurança independente.** O padrão de dados clínicos não é HL7 FHIR — em produção, isso seria mandatório. O consent é granular por instituição mas binário (concedido ou revogado); para uso secundário em pesquisa, precisaria evoluir para escopos específicos como `research:read:anonymized`.
>
> Tudo isso são extensões naturais. Não invalidam o desenho — explicitam onde ele para hoje."

---

## 8. Fechamento (~30s)

> "Resumindo: o trabalho entrega uma **arquitetura de referência funcional** para distribuição de dados clínicos no Brasil, explorando arquitetural e tecnicamente o espaço que sistemas em produção como a RNDS ainda não cobrem na rotina clínica. **A contribuição é o blueprint reproduzível** — código aberto, manifests, testes de carga, documentação arquitetural — não a substituição de sistemas existentes.
>
> Estou à disposição para perguntas."

---

## 9. Perguntas previsíveis da banca + como responder

| Pergunta | Resposta-modelo |
|---|---|
| *"Mas a RNDS já não faz isso?"* | "A RNDS cobre, em exames laboratoriais, apenas SARS-CoV-2 e Monkeypox hoje. Isso está no Manual de Informação oficial do MS. A rotina clínica não tem caminho estruturado. Meu trabalho explora justamente esse espaço — sem competir com a RNDS, mas como referência arquitetural complementar." |
| *"Por que não usou HL7 FHIR?"* | "FHIR é o padrão correto para produção, sem dúvida. Para uma PoC com foco em decisões arquiteturais — privacidade, consent, auditoria — usei um contrato JSON simplificado para manter o trabalho gerenciável dentro do escopo de um TCC. A migração para FHIR seria um adapter no histroy-service e nos contratos REST, não exige mudar a arquitetura de microsserviços." |
| *"Como o paciente revoga consentimento?"* | "Hoje, via API direta no consent-service. Em produção, isso seria um app/portal — equivalente ao Meu SUS Digital. A revogação publica um evento Kafka `consent.revoked` que propaga em menos de 1 segundo, e a próxima leitura do history-service já retorna 403." |
| *"Você considerou IA federada?"* | "Sim, considerei e está documentado como limitação. Federated learning evita centralizar dados brutos para treino — é outra arquitetura, mais avançada. Minha PoC centraliza a **distribuição**, não o armazenamento bruto (cada serviço tem seu DB). Federated learning seria uma evolução natural se o caso de uso analítico exigir." |
| *"Os dados estão seguros?"* | "Em três camadas: identidade institucional via OAuth2 + JWT com expiração de 1 hora; mTLS STRICT entre todos os pods do mesh via Istio; auditoria imutável de cada acesso com detecção automática de anomalia. CPF tokenizado em UUID em todas as fronteiras." |
| *"Por que microsserviços e não monolito?"* | "Três razões: isolamento de domínios (patient-service não compartilha DB com auth-service — falha em um não derruba o outro), escalabilidade independente (history-service escala diferente de triage-service, refletindo padrões de tráfego diferentes), e zonas de segurança independentes (o serviço que toca CPF é fisicamente separado dos que não tocam)." |
| *"Como isso vai para produção?"* | "Os manifests Kubernetes estão prontos. Em produção real exigiria: certificação ANPD, conformidade HL7 FHIR, integração com CADSUS/CNES/e-SUS, certificados ICP-Brasil para autenticação institucional, auditoria de segurança independente, e operação 24/7 com DR multi-região. Está documentado no §1.1 do CLAUDE.md." |
| *"Qual é a contribuição original?"* | "A combinação. Microsserviços, Kafka, OAuth2, mTLS — nada disso é novo isoladamente. O que é específico do meu trabalho: (1) tokenização integral do CPF como decisão arquitetural, (2) consent ativo verificado inline com SLA mensurável, (3) tratamento uniforme público/privado por desenho, (4) auditoria com anomaly detection visível ao paciente, e (5) reprodutibilidade — toda a PoC é open-source, com testes de carga e manifests K8s." |

---

## 10. Armadilhas a evitar

- ❌ **Não diga** "minha plataforma substitui a RNDS" — você perde.
- ❌ **Não venda** "implementei IA" — você não implementou, e a banca vai cobrar.
- ❌ **Não fuja** da limitação sobre FHIR — admita logo, fica mais forte.
- ❌ **Não enrole** sobre o que está nos manifests K8s se não rodou em cluster — diga "manifests prontos, validação em cluster real fora do escopo desta entrega".
- ❌ **Não tente decorar nomes de classes Java** — fala em conceito (`patient-service`, `consent-service`), não em método.

---

## 11. Lembrete de tempo

| Bloco | Tempo |
|---|---|
| 1. Abertura | 1 min |
| 2. Termos | 30 s |
| 3. Arquitetura — visão geral | 2 min |
| 3a. Papel de cada microsserviço | 2.5 min |
| 3b. Cenários de uso real | 3 min |
| 4. Decisões arquiteturais | 2 min |
| 5. Fora de escopo | 1 min |
| 6. Validação | 1.5 min |
| 7. Limitações | 30 s |
| 8. Fechamento | 30 s |
| **TOTAL** | **~14 min** |

Margem confortável para banca de 20-25 minutos. Estratégia de corte por tempo disponível:

- **Banca de 20 min:** roteiro completo, 14 min de fala + 6 min de perguntas.
- **Banca de 15 min:** corte §7 (limitações entram nas perguntas) e §3a (vá direto do diagrama para os cenários do §3b — os cenários já implicam o papel de cada serviço). Total ~10 min.
- **Banca de 10 min:** mantenha §1, §3 (visão geral), §3b (1 cenário só, o mais completo — Cenário 2), §4 (3 decisões), §5 (fora de escopo), §8 (fechamento). Total ~7 min.

**Regra prática:** os cenários do §3b são o conteúdo mais memorável. Se cortar algo, corte detalhe arquitetural (§3a), não cenários (§3b).

---

## 12. Material de apoio que você já tem no repo

- **Diagrama da arquitetura:** `README.md` (topo, em ASCII art) — pode virar slide
- **Tabela RNFs ↔ mecanismos:** `CLAUDE.md §7` — pode virar slide
- **Tabela do gap RNDS:** `CLAUDE.md §1.2` — slide de impacto
- **Painéis Grafana:** screenshots após rodar o k6 — slides de validação
- **Códigos de exemplo (`curl`) dos fluxos:** scripts em `scripts/*.sh`
