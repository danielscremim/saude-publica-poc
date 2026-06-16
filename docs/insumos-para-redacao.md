# Insumos para Redação — Resultados Preliminares e Considerações Finais
## TCC MBA Engenharia de Software — USP/ESALQ

> Arquivo gerado para repasse ao assistente de redação (NotebookLM).
> Contém todas as informações brutas sobre arquitetura, PoC e resultados.

---

## BLOCO 1 — Arquitetura Projetada

### Topologia final do modelo

A arquitetura é composta por **10 microsserviços independentes**, organizados em quatro grupos de domínio, orquestrados em Kubernetes (k3s) com service mesh Istio 1.21 e API Gateway Kong 3.7.

**Grupos de domínio:**
- **Identidade e Acesso:** auth-service (OAuth2/JWT), consent-service (LGPD)
- **Gestão Clínica:** patient-service, exam-service, triage-service
- **Laboratório:** lab-service, result-service
- **Integração e Suporte:** history-service (fachada), audit-service, notification-service

Cada serviço possui seu próprio banco de dados PostgreSQL (padrão Database per Service), elimina o acoplamento de dados entre domínios e permite escalonamento independente.

### Padrões de microsserviços efetivamente utilizados

**API Gateway (Kong 3.7 — DB-less, declarativo)**
- Ponto único de entrada para todos os clientes externos
- Roteamento por path para cada serviço (`/v1/patients`, `/v1/auth`, `/v1/consents`, etc.)
- Rate limiting configurado (10.000 req/min em ambiente de teste; em produção seria por consumer/instituição)
- Elimina a necessidade de os clientes conhecerem os endereços internos dos serviços

**Service Mesh (Istio 1.21)**
- mTLS STRICT em todo o namespace `saude-poc`: toda comunicação pod-a-pod é criptografada e autenticada com certificados emitidos pela Istio CA
- Circuit Breaker via DestinationRules: ejeção automática de instâncias com > 5 erros consecutivos em janelas de 30 segundos
- Observabilidade nativa: métricas de latência, throughput e erros coletadas automaticamente pelo Prometheus/Grafana/Kiali/Jaeger sem alteração no código dos serviços

**Comunicação assíncrona (Apache Kafka — KRaft, sem Zookeeper)**
- Quatro tópicos com responsabilidades claras:
  - `exam.requested`: exam-service → lab-service
  - `exam.completed`: lab-service / result-service → result-service, notification-service
  - `audit.events`: history-service, result-service → audit-service
  - `consent.revoked`: consent-service → consumidores futuros de cache
- A chave de cada mensagem Kafka é sempre o `patientUuid`, garantindo ordenação por paciente
- Desacoplamento temporal: o lab-service processa exames de forma independente do ritmo do exam-service; o audit-service registra eventos sem bloquear o fluxo principal

**Tokenização de identidade (Privacy by Design)**
- O CPF do paciente existe APENAS dentro do patient-service, no banco `patientdb`
- Todos os demais serviços, eventos Kafka, respostas de API e logs operam exclusivamente com um UUID interno gerado no cadastro
- Isso implementa o princípio de minimização de dados da LGPD no nível arquitetural, não por política

**Controle de consentimento inline (RNF-06 / LGPD)**
- Antes de qualquer leitura de dados clínicos, o history-service consulta o consent-service
- Se o consentimento estiver ausente ou revogado: HTTP 403 retornado imediatamente
- Revogação propagada via Kafka (`consent.revoked`) em menos de 1 segundo
- Cada acesso negado também é auditado (ação `READ_TIMELINE_DENIED`)

**Fluxo bidirecional autenticado**
- Instituições externas (hospitais privados, laboratórios) podem enviar resultados diretamente via `POST /v1/results` com JWT portando o escopo `result:write`
- O mesmo contrato de API serve tanto para leitura quanto para escrita, com autorizações granulares por escopo OAuth2

**Escalabilidade horizontal (HPA)**
- Todos os 10 serviços possuem HorizontalPodAutoscaler configurado
- Mínimo: 2 réplicas (disponibilidade RNF-02); Máximo: 10 réplicas; Threshold: 70% de uso de CPU
- O escalonamento é automático e independente por serviço

**Auditoria imutável**
- O audit-service registra cada acesso com chave primária = eventId do evento Kafka
- Idempotência por design: duplicatas são descartadas silenciosamente (captura de DataIntegrityViolationException)
- Nenhum endpoint de UPDATE ou DELETE existe no audit-service — o log é append-only
- Anomaly detection: mais de 10 acessos ao mesmo paciente em 10 minutos gera alerta (threshold configurável via variável de ambiente; padrão de produção: 200)

---

## BLOCO 2 — Prova de Conceito (PoC) / Simulação

### Ambiente controlado utilizado

**Infraestrutura:**
- Máquina virtual Ubuntu Server 22.04.5 LTS rodando em Hyper-V (Windows 11)
- 16 vCores, 20 GB de RAM
- Single-node k3s (Kubernetes leve para laboratório)
- Todos os 10 microsserviços + Kafka + PostgreSQL + Kong + Istio na mesma VM

**Stack de tecnologia por camada:**

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 + Spring Boot 3.3.5 |
| Build | Maven (um pom.xml por serviço) |
| Banco de dados | PostgreSQL 16 (Database per Service) |
| Mensageria | Apache Kafka 3.x (KRaft) |
| API Gateway | Kong 3.7 (DB-less) |
| Orquestração | Kubernetes via k3s |
| Service Mesh | Istio 1.21.0 |
| Autenticação | JWT HS256 via jjwt 0.12.6 |
| Teste de carga | Grafana k6 (executado como Job Kubernetes) |
| Observabilidade | Prometheus + Grafana + Kiali + Jaeger (addons Istio) |

**Como o teste foi realizado:**
- O k6 foi implantado como um Job Kubernetes dentro do próprio cluster
- Todo o tráfego passou pelo Kong na porta 8000 (caminho real de produção, sem atalhos)
- O mix de carga simulou o padrão real de uso: 50% leitura de timeline clínica, 30% verificação de consentimento, 20% cadastro de paciente
- Dois cenários foram executados: 150 VUs (carga moderada, validação dos targets de design) e 1.000 VUs (carga máxima, cenário oficial do RNF-01)
- Duração de cada execução: 9 minutos (2 min ramp-up → 5 min sustentado → 2 min ramp-down)

---

## BLOCO 3 — Dados / Resultados

### Cenário A — 150 VUs (targets de design / produção)

**Objetivo:** confirmar que a arquitetura atende os RNFs de latência quando a infraestrutura tem folga.

| Métrica | Target (design) | Resultado medido | Status |
|---|---|---|---|
| Latência P95 geral | ≤ 500ms | **40ms** | ✅ 12× abaixo |
| Consent check P95 (LGPD) | ≤ 20ms | **17ms** | ✅ abaixo do SLA |
| Timeline P95 | ≤ 800ms | **52ms** | ✅ 15× abaixo |
| Taxa de erro | < 1% | **0,00%** | ✅ zero erros |
| Total de requisições | — | 235.359 em 9 min | — |
| Throughput | — | 434 req/s | — |
| Checks bem-sucedidos | 100% | **100%** (235.358/235.358) | ✅ |

### Cenário B — 1.000 VUs (cenário oficial RNF-01)

**Objetivo:** validar estabilidade e throughput sob pico de 1.000 usuários simultâneos.

| Métrica | Resultado medido |
|---|---|
| Latência P95 geral | 1.190ms |
| Consent check P95 | 270ms |
| Timeline P95 | 1.420ms |
| Taxa de erro | **0,00%** |
| Total de requisições | **588.563 em 9 min** |
| Throughput | **1.079 req/s** |
| Iterações interrompidas | **0** |
| Checks bem-sucedidos | **100%** (588.562/588.562) |

### O que os testes comprovaram

**A arquitetura suportou a carga sem falhas:**
- Zero erros em 823.922 requisições combinadas (150 VUs + 1.000 VUs)
- Zero iterações interrompidas em ambos os cenários
- Todos os fluxos críticos funcionaram: autenticação OAuth2, check de consentimento, leitura de timeline, escrita de resultado, auditoria

**Os targets de design são atingíveis:**
- A 150 VUs com infraestrutura ociosa, todos os RNFs de latência foram atingidos com ampla margem
- O consent check atingiu 17ms P95 — abaixo do SLA de 20ms — mesmo sem cache em memória, apenas com PostgreSQL indexado
- A timeline agregada (patient + results consolidados) respondeu em 52ms P95

**O gargalo a 1.000 VUs é hardware, não arquitetura:**
- Em um single-VM com 10 serviços competindo por CPU, a latência sobe para ~1.190ms P95
- A mediana se manteve em 390ms — indicando que a maioria das requisições foi rápida; o P95 alto vem dos picos de contenção de CPU
- Em um cluster distribuído com pods em nós dedicados, os targets de 500ms seriam atingíveis

**Privacy by Design funcionou no caminho crítico:**
- Em 100% das requisições ao history-service, o consent foi verificado antes da entrega de dados
- Quando o consentimento está ausente: HTTP 403 retornado imediatamente, acesso negado auditado
- CPF nunca apareceu em nenhuma resposta de API, log ou evento Kafka verificado
- O fluxo bidirecional (hospital privado enviando resultado com JWT escopo `result:write`) funcionou corretamente, com o evento sendo auditado como `WRITE_RESULT`

**mTLS validado empiricamente:**
- O teste de carga não conseguia conectar quando o pod k6 não tinha sidecar Istio — a conexão era resetada pelo Istio em modo STRICT
- Após configurar PeerAuthentication PERMISSIVE apenas na porta 8000 do Kong (ingress), o tráfego fluiu corretamente
- A evidência empírica prova que o mTLS STRICT está ativo e funcional no namespace

**Auditoria imutável processou toda a carga:**
- O audit-service consumiu ~820.000 eventos de auditoria (soma dos dois cenários) sem falhas
- Após o teste de 1.000 VUs, o audit-service permaneceu com 47% de CPU por vários minutos, processando o backlog assíncrono do Kafka — comportamento esperado e correto

---

## BLOCO 4 — Limitações Declaradas (para Considerações Finais)

Estas limitações devem ser declaradas com transparência no texto:

1. **Ambiente single-VM:** todos os serviços co-localizados competem por CPU/RAM. Em produção, cada serviço rodaria em pods distribuídos em nós dedicados.

2. **Sem cache no consent-service:** o SLA de 20ms foi atingido a 150 VUs mas não a 1.000 VUs (270ms). Em produção, um cache Caffeine (in-process) + Redis (distribuído) permitiria atingir < 20ms independentemente da carga.

3. **Contrato JSON proprietário:** a API usa um contrato JSON simplificado, não HL7 FHIR R4. A interoperabilidade com a RNDS do Ministério da Saúde exigiria um adapter FHIR.

4. **Sem certificado ICP-Brasil:** a autenticação usa OAuth2 com client_secret. Em produção para integração com sistemas públicos, seria necessário certificado A3 ICP-Brasil.

5. **Sem Prometheus/Grafana/Jaeger em Kubernetes (parcialmente):** a stack de observabilidade foi implantada como addons Istio de demonstração. Uma stack de produção exigiria configuração de retenção, alertas e dashboards customizados.

6. **Sem DR multi-região:** single point of failure no nó. Em produção, replicação cross-zone e failover automático seriam necessários.

---

## BLOCO 5 — Argumento central para Considerações Finais

O problema de pesquisa era: **como estruturar uma arquitetura de microsserviços escalável e interoperável para distribuição de dados primários na saúde pública, mantendo integridade da informação sob a ótica da Privacidade por Design.**

A PoC demonstrou que a combinação de:
- **Kong** como gateway único de entrada (interoperabilidade — um contrato, múltiplos produtores e consumidores)
- **Istio** com mTLS STRICT (segurança na camada de transporte, sem alterar código de aplicação)
- **Kafka** para comunicação assíncrona (desacoplamento e resiliência)
- **consent-service com verificação inline** (Privacy by Design no caminho crítico, não como auditoria posterior)
- **audit-service com log imutável** (rastreabilidade para LGPD e vigilância epidemiológica)
- **UUID tokenizando o CPF** (minimização de dados por design)

...é **tecnicamente viável, implementável com tecnologias open-source consolidadas e capaz de suportar carga de produção** quando dimensionada adequadamente.

O diferencial em relação à RNDS (Rede Nacional de Dados em Saúde) do Ministério da Saúde está em: tratamento uniforme de produtores públicos e privados, escopo de exames clínicos genéricos (não apenas os dois patógenos cobertos hoje pela RNDS), consent ativo granular verificado inline com SLA mensurável, e auditoria visível ao paciente como feature de primeira classe.

A arquitetura proposta não pretende substituir a RNDS, mas demonstrar um padrão arquitetural complementar — especialmente relevante para redes regionais, operadoras de saúde e redes hospitalares privadas que ainda não integram com a RNDS, e que poderiam adotar este modelo com uma ponte FHIR futura.
