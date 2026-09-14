# Decisão Arquitetural — GraphQL na camada de distribuição

> Insumo para a *Metodologia* (subtópico "Arquitetura proposta") e para os *Resultados e Discussão* do TCC.
> Origem: sugestão do orientador para a fase final, mantendo o título e os objetivos do projeto aprovado.

## 1. Enquadramento: por que GraphQL entra no trabalho

O trabalho é sobre a **camada de interoperabilidade** — receber, armazenar e distribuir dados clínicos com privacidade. O GraphQL **não** entra como objeto de comparação entre protocolos; entra como o mecanismo que permite à camada de distribuição entregar **apenas o dado necessário à finalidade declarada** pelo consumidor.

Isso é a **minimização de dados**, princípio da LGPD (Art. 6º, III) e um dos sete princípios de Privacidade por Design (Cavoukian, 2011). Até aqui o trabalho tratava privacidade como **controle de acesso** (consentimento, auditoria, tokenização de CPF). O GraphQL acrescenta uma dimensão que faltava: privacidade no **volume de dado trafegado**.

Enquadramento correto dos resultados:

| Evitar | Adotar |
|---|---|
| "GraphQL é X% mais rápido que REST" | "A camada de distribuição transferiu X% menos dados quando o consumidor declarou apenas os campos necessários à sua finalidade" |

O REST não é adversário: é a **linha de base da própria plataforma** — o comportamento sem minimização. A arquitetura é medida contra si mesma.

## 2. Posicionamento: BFF no history-service (não um gateway separado)

O `history-service` já é a fachada agregadora da plataforma; com o GraphQL ele passa a operar como **BFF (Backend for Frontend) da camada de distribuição**, com dois transportes sobre a mesma lógica:

- `GET /v1/patients/{uuid}/clinical-timeline` — REST, contrato inalterado
- `POST /graphql` — consulta declarativa

**Por que não criar um 11º serviço como gateway:** o `history-service` concentra a verificação obrigatória de consentimento e a publicação de `audit.events`. Um gateway separado criaria um segundo caminho de acesso a dados clínicos, com risco de nascer fora desse portão — uma regressão no RNF-06, que é o núcleo do trabalho. Um serviço, um portão, uma trilha de auditoria. Secundariamente, evita mais um pod competindo por CPU no ambiente de teste.

A ingestão permanece REST (`POST /v1/results` + Kafka): escrita de alto volume vinda de sistemas heterogêneos se beneficia da simplicidade do REST. A separação escrita/leitura configura **CQRS**.

## 3. Implementação

- `spring-boot-starter-graphql`, *schema-first* (`src/main/resources/graphql/schema.graphqls`), SDL publicado em `GET /graphql/schema` — contrato autodocumentado, análogo ao OpenAPI da ingestão (RNF-05).
- **Mesma lógica de autorização do REST**: ambos chamam `TimelineService.getPatientView()`, que verifica o consentimento **uma vez** antes de qualquer campo ser resolvido e publica `audit.events`. Autorização não depende do transporte.
- JWT validado uma única vez (`JwtValidator`), compartilhado entre o filtro HTTP (REST e GraphQL) e o contexto GraphQL (`CallerContextInterceptor`). Sem token: 401 antes do GraphQL.
- **Resolução sob demanda**: `exams` vem da agregação inicial; `triages`, `notifications` e `auditTrail` só chamam os serviços correspondentes **se o campo for solicitado**. Campo não pedido = serviço não consultado = dado não trafegado. Verificado em teste automatizado (`verifyNoInteractions`).
- **Erros**: HTTP é sempre 200 em GraphQL; a semântica vai em `errors[].extensions.classification` — `FORBIDDEN` (consent negado), `UNAUTHORIZED`, `NOT_FOUND`.
- **Limites anti-abuso**: profundidade 5 e complexidade 100 rejeitam queries aninhadas antes de executar qualquer resolver — compensa a perda de granularidade do rate limit por rota do Kong, já que há um único endpoint `/graphql` (RNF-06).
- N+1 não se aplica: cada resolver faz no máximo uma chamada por consulta, sobre coleções já materializadas.

## 4. Contribuição por requisito

| RNF | Contribuição |
|---|---|
| RNF-05 Interoperabilidade | Schema tipado como contrato; visão consolidada reduz os pontos de integração do consumidor externo (4 chamadas → 1) |
| RNF-06 Consentimento / privacidade | Minimização de dados no protocolo; consent verificado uma vez para toda a visão; limites de profundidade/complexidade |
| RNF-01 Desempenho | Menos bytes por resposta e menos round-trips por visão consolidada |
| RNF-03 Segurança | Mesmo JWT e mesmo mTLS do restante da malha; `/graphql` passa pelo Kong como qualquer rota |

## 5. Trade-offs (reconhecer no TCC)

- **Cache HTTP** é mais difícil (tudo é `POST /graphql`). Mitigação futura: *persisted queries* com cache por hash.
- **Rate limiting por recurso** migra do gateway para a camada GraphQL (profundidade/complexidade) e para o `audit-service` (detecção de anomalia por volume).
- **Curva de aprendizado** para integradores — por isso a ingestão permanece REST.
- **Códigos HTTP** não refletem erros de negócio; clientes precisam inspecionar `errors[]`.

## 6. Evidências

**Experimento** (`tests/k6/minimizacao-dados.js`) — cinco variantes em sequência, 200 VUs cada:

| Variante | O que representa | Métrica |
|---|---|---|
| `baseline_full` | REST, payload completo e fixo | bytes, 1 round-trip |
| `declarado_full` | GraphQL declarando todos os campos (controle) | bytes |
| `declarado_min` | GraphQL declarando 3 campos (resumo clínico) | **bytes — minimização** |
| `baseline_multi` | 4 chamadas REST para montar a visão consolidada | bytes, **4 round-trips** |
| `consolidado` | A mesma visão em 1 query declarativa | bytes, **1 round-trip** |

Duas afirmações sustentadas por números: *minimização* (baseline_full × declarado_min) e *redução de pontos de integração* (baseline_multi × consolidado).

**Teste automatizado** (`HistoryGraphQLTest`, 10 casos): consulta seletiva sem campos extras, campo não solicitado não aciona o serviço a montante, visão consolidada com consent verificado uma única vez, `FORBIDDEN` sem consent (nenhum serviço consultado), 401 sem token, rejeição por profundidade, filtros e paridade com o REST. `cd history-service && mvn test`.

## 7. Onde entra no documento final

Um subtópico curto em *Resultados e Discussão*, subordinado à discussão de interoperabilidade e privacidade — não uma seção própria. Aproximadamente meia página: tabela de bytes e round-trips, dois parágrafos ligando à LGPD e a Cavoukian (2011). Os resultados de destaque continuam sendo escalabilidade sob carga, HPA, mTLS, consent inline e taxa de erro zero.

Os **objetivos específicos do projeto aprovado permanecem inalterados**: o GraphQL é meio para o objetivo (iii) (Privacidade por Design) e cabe no (ii) (padrões de comunicação). Promovê-lo a objetivo próprio contrariaria o princípio de que a tecnologia é o meio, não o fim.

## 8. Referências sugeridas

- Hartig, O.; Pérez, J. 2018. Semantics and Complexity of GraphQL. *WWW '18*. — fundamenta os limites de profundidade/complexidade.
- Brito, G.; Valente, M.T. 2020. REST vs GraphQL: A Controlled Experiment. *IEEE ICSA*.
- Brito, G.; Mombach, T.; Valente, M.T. 2019. Migrating to GraphQL: A Practical Assessment. *IEEE SANER*. — quantifica redução de payload.
- The GraphQL Foundation. GraphQL Specification. https://spec.graphql.org/

> Confirme autores, ano e veículo nas fontes originais antes de citar; formate conforme as normas do MBA USP/Esalq.
