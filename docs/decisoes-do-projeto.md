# Registro de decisões do projeto

> Complementa o `CLAUDE.md`. Enquanto aquele descreve **o que** o projeto é, este
> registra **por que** cada decisão foi tomada e **o que foi descartado**. Leia antes
> de propor mudanças de escopo, enquadramento ou redação — várias alternativas
> aparentemente razoáveis já foram avaliadas e rejeitadas por motivos que não são
> óbvios a partir do código.

---

## D-01 — O trabalho é sobre interoperabilidade, não sobre IA

**Decisão:** a plataforma recebe, armazena e distribui dados clínicos. Não implementa IA, ML nem análise preditiva.

**Por quê:** o uso futuro dos dados para análise preditiva é a *motivação* do trabalho, não a entrega. Como esse uso será feito depois não importa e está fora do escopo.

**Descartado:** em uma versão anterior havia um "Sistema de IA" como ator nos diagramas C4 e uma etapa do fluxo dedicada a ele. Foi removido: não existe ator nem endpoint de IA na arquitetura. Qualquer consumidor autorizado — médico, hospital ou, no futuro, um sistema analítico — usa o **mesmo** contrato de API.

**Consequência:** não reintroduzir "IA" em diagramas, endpoints ou textos. Na defesa, a resposta para *"a arquitetura previne doenças?"* é: ela remove o obstáculo técnico (fragmentação de dados clínicos com privacidade) para que sistemas preventivos possam existir.

---

## D-02 — O título e os objetivos específicos NÃO mudam

**Decisão:** o título permanece *"Arquitetura de Microsserviços e Interoperabilidade para Distribuição de Dados na Saúde Pública"*. Os três objetivos específicos do Projeto de Pesquisa aprovado permanecem como estão.

**Por quê:** o projeto foi aprovado assim. O GraphQL entra na estrutura, não no título.

**Descartado:** (a) evoluir o título para incluir GraphQL; (b) criar um quarto objetivo específico sobre GraphQL. Ambos promoveriam a ferramenta à categoria de fim, contrariando o princípio de que a tecnologia é o meio. O GraphQL já cabe no objetivo (ii) — padrões de comunicação — e serve ao (iii) — Privacidade por Design.

**Consequência:** ao redigir o TCC, mencionar GraphQL na Metodologia (decisão de arquitetura) e nos Resultados (evidência), nunca nos objetivos ou no título.

---

## D-03 — GraphQL é minimização de dados, NUNCA comparação REST × GraphQL

**Decisão:** o GraphQL é apresentado como o mecanismo que permite à camada de distribuição entregar apenas o dado necessário à finalidade declarada — minimização de dados (LGPD, Art. 6º, III; Privacy by Design, Cavoukian 2011).

**Por quê:** um confronto entre protocolos deslocaria o eixo do trabalho para *"qual protocolo é melhor"*, que é pergunta de outro TCC. A banca legitimamente questionaria por que um trabalho sobre interoperabilidade em saúde dedica seus resultados a um benchmark de protocolo.

**Descartado:** o enquadramento comparativo, inclusive o nome do script (`rest-vs-graphql.js` → `minimizacao-dados.js`).

**Como escrever:**

| Errado | Certo |
|---|---|
| "GraphQL foi X% mais rápido que REST" | "A camada de distribuição transferiu X% menos dados quando o consumidor declarou apenas os campos necessários à sua finalidade" |

O REST **não é adversário**: é a linha de base da própria plataforma — o comportamento sem minimização. A arquitetura é medida contra si mesma.

**Duas evidências, dois argumentos:**
1. `baseline_full` × `declarado_min` → bytes por resposta → **minimização de dados** (objetivo iii)
2. `baseline_multi` × `consolidado` → round-trips (4 → 1) → **redução de pontos de integração** (objetivo i)

---

## D-04 — GraphQL dentro do history-service, não em um gateway separado

**Decisão:** o `history-service` atua como BFF da camada de distribuição, com dois transportes (REST e GraphQL) sobre a mesma lógica.

**Por quê:** o `history-service` concentra a verificação obrigatória de consentimento e a publicação de `audit.events`. Um 11º serviço como gateway criaria um segundo caminho de acesso a dados clínicos, com risco de nascer fora desse portão — regressão no RNF-06, que é o núcleo do trabalho. Um serviço, um portão, uma trilha de auditoria. Secundariamente, evita mais um pod competindo por CPU no ambiente de teste.

**Descartado:** `graphql-gateway` como serviço separado à frente dos microsserviços. Funcionalmente equivalente ao que foi feito; o ganho não compensa o risco nem o prazo.

**Consequência:** na redação, descrever o `history-service` como *"BFF / fachada da camada de distribuição"*. Se o orientador pedir que o GraphQL cubra toda a superfície de leitura da plataforma (e não apenas o escopo do paciente), a decisão precisa ser reavaliada.

---

## D-05 — Escopo do GraphQL: tudo no escopo do paciente

**Decisão:** a query `patientHistory` agrega exames, dados do paciente, triagens, notificações e trilha de auditoria. Ficam de fora `consents/check` (plano de controle interno) e `audit/anomalies` (visão administrativa, autorização diferente).

**Por quê:** permite demonstrar **under-fetching** além de over-fetching. A visão consolidada de um paciente exigiria 4 chamadas REST; vira 1 query. Isso fala diretamente do objetivo (i) — gargalos de integração — e não de protocolo.

---

## D-06 — Resolvers preguiçosos são a evidência, não um detalhe

**Decisão:** `triages`, `notifications` e `auditTrail` só chamam o serviço a montante **se o campo for solicitado**.

**Por quê:** é o que torna a minimização verificável. Campo não pedido = serviço não consultado = dado não trafega nem é lido. Está provado em teste automatizado (`verifyNoInteractions`), não apenas afirmado.

**Consequência:** nunca "pré-carregar" os campos agregados para simplificar o código — isso destruiria a evidência central do trabalho.

---

## D-07 — Ingestão continua REST; leitura é GraphQL (CQRS)

**Decisão:** produtores (UBS, laboratórios públicos e privados, hospitais) continuam usando `POST /v1/results` com OpenAPI. Não criar mutations GraphQL para ingestão.

**Por quê:** escrita de alto volume vinda de sistemas legados heterogêneos se beneficia da simplicidade e universalidade do REST. A separação escrita/leitura configura CQRS, que é um padrão reconhecido e fácil de defender.

---

## D-08 — Testes em 2 VMs separadas

**Decisão:** VM-1 executa o sistema sob teste; VM-2 executa apenas o k6.

**Por quê:** nos Resultados Preliminares o k6 rodou como Job no mesmo nó dos serviços, competindo por CPU. Isso está documentado como limitação. Separar torna as medidas atribuíveis à arquitetura, não à contenção da VM.

**Consequência:** declarar isso na Metodologia. Reportar 3 rodadas por cenário como média ± desvio-padrão, com o primeiro minuto (aquecimento da JVM e do HPA) descartado.

---

## D-09 — Fronteira de escopo declarada abertamente

**Decisão:** o trabalho entrega arquitetura de referência com PoC funcional. Não entrega: modelo de IA, substituto da RNDS, sistema pronto para produção (faltariam certificação ANPD/CFM, HL7 FHIR/TISS/TUSS, integração e-SUS/CADSUS/CNES, DR multi-região, auditoria de segurança independente).

**Por quê:** honestidade de escopo é avaliada positivamente. Ver `CLAUDE.md` §1.1 e §1.2 para o posicionamento detalhado frente à RNDS.

---

## Como usar este arquivo

Ao redigir qualquer parte do TCC, produzir diagramas ou alterar código, verifique se a mudança contraria alguma decisão acima. Se contrariar e houver bom motivo, **pergunte antes** — as alternativas descartadas foram avaliadas e rejeitadas por razões que não aparecem no código.
