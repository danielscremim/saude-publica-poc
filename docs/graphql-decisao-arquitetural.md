# Decisão Arquitetural — GraphQL na camada de distribuição

> Insumo para as seções *Metodologia* (subtópico "Arquitetura proposta") e *Resultados e Discussão* do TCC.
> Sugestão do orientador: adotar GraphQL para otimizar a plataforma.

## 1. O que o GraphQL substitui e o que ele agrega

A arquitetura tem dois tipos de tráfego com naturezas distintas:

| Tráfego | Natureza | Tecnologia | Motivo |
|---|---|---|---|
| **Ingestão** (produtores enviam resultados) | Escrita, alto volume, sistemas legados heterogêneos | **REST + OpenAPI → Kafka** (mantido) | Simplicidade e universalidade para integradores; assíncrono por natureza |
| **Distribuição** (consumidores leem histórico) | Leitura agregada de vários serviços, consumidores com necessidades diferentes | **GraphQL** (adicionado) | Consumidor escolhe os campos; um contrato tipado evolui sem versionar |

Conclusão: o GraphQL **agrega** como transporte de leitura do `history-service`, coexistindo com o REST. Ele **não substitui** Kafka (assíncrono ≠ consulta), nem o Kong (borda: TLS, JWT, rate limit), nem a ingestão REST. A separação escrita/leitura configura o padrão **CQRS** (*Command Query Responsibility Segregation*).

## 2. Implementação (history-service)

- `spring-boot-starter-graphql` (Spring for GraphQL), abordagem *schema-first* — contrato em `src/main/resources/graphql/schema.graphqls`, publicado em `GET /graphql/schema` (RNF-05: contrato autodocumentado, análogo ao OpenAPI da ingestão).
- **Mesmo `TimelineService` do REST**: a checagem de consentimento (RNF-06) e a auditoria (`audit.events`) são idênticas nos dois transportes. Autorização não depende do protocolo.
- **JWT** validado uma única vez (`JwtValidator`) e injetado no contexto GraphQL (`CallerContextInterceptor`).
- Campo `Timeline.exams(examType, limit)` com filtros server-side — sem novos endpoints.
- **Erros**: em GraphQL o HTTP é sempre 200; a semântica vai em `errors[].extensions.classification` (`FORBIDDEN` para consent negado, `UNAUTHORIZED`, `NOT_FOUND`). Ausência de token continua sendo 401 no filtro HTTP, antes do GraphQL.
- **Proteção anti-abuso**: `MaxQueryDepthInstrumentation` (5) e `MaxQueryComplexityInstrumentation` (100) rejeitam queries aninhadas/pesadas *antes* de executar qualquer resolver. Isso compensa o fato de que, com um único endpoint `/graphql`, o rate limit por rota do Kong perde granularidade (RNF-06).
- N+1 não se aplica aqui: a agregação `patient + results` ocorre uma vez por query; os resolvers de campo operam sobre a lista em memória (por isso não foi necessário `DataLoader`).

## 3. Como o GraphQL contribui para os RNFs

| RNF | Contribuição |
|---|---|
| RNF-01 Desempenho | Menos bytes por resposta (sem *over-fetching*) → menor latência de serialização e transferência; medido em `tests/k6/rest-vs-graphql.js` |
| RNF-05 Interoperabilidade | Schema tipado = contrato; consumidores novos não exigem `/v2`; introspecção substitui documentação manual |
| RNF-06 Consentimento | Consent checado antes de qualquer campo; depth/complexity limit como defesa adicional |
| RNF-03 Segurança | Mesmo JWT, mesmo validador; `/graphql` passa pelo Kong e pelo mTLS do Istio como qualquer rota |

## 4. Trade-offs (reconhecer no TCC — a banca vai perguntar)

- **Cache HTTP** é mais difícil (tudo é `POST /graphql`). Mitigação futura: *persisted queries* + cache por hash.
- **Rate limiting por recurso** migra do gateway para a camada GraphQL (depth/complexity). Limite por paciente/instituição passa a ser responsabilidade do `consent-service`/`audit-service` (anomaly detection).
- **Curva de aprendizado** para integradores acostumados a REST; por isso a ingestão permanece REST.
- **Códigos HTTP** não refletem erros de negócio — clientes precisam inspecionar `errors[]`.

## 5. Experimento que sustenta a decisão

`tests/k6/rest-vs-graphql.js` mede, para a mesma consulta, três variantes em sequência (200 VUs × 3 min cada): REST payload completo, GraphQL com todos os campos (paridade), GraphQL com 3 campos (resumo clínico). Métricas: P95 e **bytes por resposta** (`resp_bytes`). O resultado esperado é redução substancial de bytes na variante seletiva com latência igual ou menor — a evidência quantitativa da otimização sugerida pelo orientador. Com histórico de 30 exames/paciente, a variante mínima transfere aproximadamente metade dos bytes do REST; o número exato deve vir das rodadas na VM.

## 6. Teste automatizado

`history-service/src/test/java/.../HistoryGraphQLTest.java` sobe o serviço real (HTTP, filtro JWT, interceptor, resolvers) com os clients a montante mockados e valida: consulta seletiva sem campos extras, filtros, `FORBIDDEN` sem consent (+ auditoria `READ_TIMELINE_DENIED`), 401 sem token, rejeição por profundidade e paridade com o REST. Executar: `cd history-service && mvn test`.

## 7. Referências sugeridas para o TCC

- Hartig, O.; Pérez, J. 2018. Semantics and Complexity of GraphQL. *Proceedings of the 2018 World Wide Web Conference (WWW '18)*. — formaliza a linguagem e a complexidade de queries (fundamenta o limite de profundidade/complexidade).
- Brito, G.; Valente, M.T. 2020. REST vs GraphQL: A Controlled Experiment. *IEEE International Conference on Software Architecture (ICSA)*. — experimento controlado comparando REST e GraphQL.
- Brito, G.; Mombach, T.; Valente, M.T. 2019. Migrating to GraphQL: A Practical Assessment. *IEEE SANER*. — quantifica a redução de payload em migrações REST→GraphQL.
- The GraphQL Foundation. GraphQL Specification. https://spec.graphql.org/ — especificação oficial.

> Verifique os dados bibliográficos (autores, ano, veículo) nas fontes originais antes de citar; formate conforme as normas do MBA USP/Esalq.
