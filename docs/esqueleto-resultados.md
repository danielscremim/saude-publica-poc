# Esqueleto da seção de Resultados

> **Como usar.** A estrutura abaixo segue a ordem da Metodologia. Os números já estão
> encaixados e conferidos; as figuras já existem em `docs/figuras/`. Os blocos em
> *itálico entre colchetes* são **orientação de redação**, não texto do trabalho —
> apague-os conforme escrever por cima.
>
> Fontes: `sintese-resultados-tcc.md` (todos os números), `resultados-bateria-carga.md`
> (memória de cálculo), `diario-de-execucao.md` (desafios), `validacao-funcional-vm.md`
> (ambiente e validação funcional).

---

# 4 RESULTADOS E DISCUSSÃO

## 4.1 Ambiente experimental e procedimento

*[Abrir situando o ambiente e, principalmente, justificando a separação em duas
máquinas — é uma decisão de método, não de infraestrutura.]*

Os experimentos foram conduzidos em duas máquinas virtuais de datacenter. A primeira
(32 vCPU, 62 GB) hospedou o sistema sob teste: cluster Kubernetes (K3s 1.35.5 sobre
k3d 5.9.0), malha de serviços Istio 1.30.4 com mTLS obrigatório, PostgreSQL 16, Apache
Kafka 3.8.1 e os dez microsserviços (Java 21, Spring Boot 3.3.5). A segunda (8 vCPU,
15 GB) executou exclusivamente o gerador de carga k6 v2.2.0. A latência de rede entre
elas foi de **0,218 ms** em média, com 0% de perda.

**A separação do gerador de carga não foi apenas rigor metodológico — foi a condição
que tornou os requisitos de projeto verificáveis.** Em execução anterior, com o
gerador acessando o cluster por encaminhamento de porta, o canal de medição
acrescentava cerca de 300 ms por requisição e reprovava três dos quatro limites com o
sistema saudável. *[Este é um bom parágrafo para a Metodologia também: instrumentação
intrusiva pode dominar o fenômeno medido.]*

Cada rodada foi precedida de reposição completa do estado — tabelas truncadas, número
mínimo de réplicas e processos recém-iniciados — de modo que **todas as rodadas
partissem de condições idênticas**. Os resultados são reportados como média ± desvio
padrão de três rodadas, salvo indicação contrária.

---

## 4.2 Resultado 1 — Desempenho sob carga

### 4.2.1 Carga de referência (150 usuários simultâneos)

| Métrica | Medido | Limite de projeto | |
|---|---|---|---|
| Latência geral (P95) | **48,4 ± 7,7 ms** | 500 ms | atendido |
| Linha do tempo clínica (P95) | **68,1 ± 11,2 ms** | 800 ms | atendido |
| Verificação de consentimento (P95) | **18,0 ± 3,2 ms** | 20 ms | limítrofe |
| Taxa de erro | 0,00% a 0,01% | < 1% | atendido |
| Vazão sustentada | **428,1 ± 5,8 req/s** | — | |

*[Destacar que estes são os limites de PROJETO, não os relaxados de PoC. E reportar
com honestidade a rodada discrepante: a terceira mediu 21,07 ms no consentimento e
excedeu. Diluir isso na média seria esconder informação.]*

O valor de 18 ms na verificação de consentimento corresponde a uma checagem **inline**,
no caminho crítico da requisição, atravessando gateway, *sidecar* Envoy e mTLS — não a
uma consulta direta ao banco.

### 4.2.2 Carga nominal (1000 usuários simultâneos) — RNF-01

| Métrica | Medido | Projeto | PoC |
|---|---|---|---|
| Latência geral (P95) | **818 ± 110 ms** | 500 ms ✗ | 2.000 ms ✓ |
| Linha do tempo (P95) | **937 ± 142 ms** | 800 ms ✗ | 2.000 ms ✓ |
| Consentimento (P95) | **398 ± 73 ms** | 20 ms ✗ | 2.000 ms ✓ |
| Taxa de erro | **0,00%** nas três rodadas | < 1% | ✓ |
| Vazão sustentada | **1.324,9 ± 75,0 req/s** | — | |

*[O resultado honesto: a plataforma sustenta a carga SEM PERDER REQUISIÇÃO, mas não
atinge o alvo de latência de projeto. A distinção entre limites de projeto e de PoC
foi adotada antes das medições — vale dizer isso, porque mostra que não foi ajuste
posterior ao resultado.]*

A causa da diferença entre os dois cenários é saturação de recurso compartilhado, não
defeito de desenho, e está isolada em 4.3.2. Evidência a favor: **os três endpoints
degradam por fatores semelhantes** (17× a 26×) entre um cenário e outro. Uma consulta
ineficiente ou índice ausente degradaria um deles desproporcionalmente.

### 4.2.3 O dimensionamento do autoescalador

*[Este é um dos achados mais fortes e mais contraintuitivos do trabalho. Vale um
subtítulo próprio e espaço para a explicação — não é um detalhe de configuração.]*

**Figura 6** — Requisições por segundo por núcleo de CPU nas três configurações.

| Configuração | Pods ativos | Vazão | P95 | Erro | Req/s por núcleo |
|---|---|---|---|---|---|
| Degradada | **57** | 130 req/s | 38,3 s | 22,8% | **4,2** |
| `maxReplicas = 6` | **45** | 1.225 req/s | 918 ms | 0,00% | **40,7** |
| Execução de 19/09 | **43** | 1.325 req/s | 818 ms | 0,00% | **47,2** |

Com praticamente a mesma utilização de CPU — 98% contra 94% — a configuração com
menos réplicas realizou **9,7 vezes mais trabalho útil por núcleo**.

**O que determina o resultado é o número de pods ativos em relação à capacidade do nó,
não o valor nominal de `maxReplicas`.** Cada réplica adicional traz uma máquina virtual
Java e um *sidecar* Envoy: consome capacidade sem acrescentar nenhuma. Acima do que o
nó comporta, o autoescalador entra em realimentação positiva — observa CPU alta,
solicita mais réplicas, que consomem mais CPU.

*[Enunciar como princípio, não como receita. "Reduza o maxReplicas" é fraco;
"o autoescalador precisa ser dimensionado para o substrato, e não configurado no
máximo por padrão" é o resultado.]*

---

## 4.3 Resultado 2 — Ponto de ruptura e desacoplamento assíncrono

### 4.3.1 Capacidade sustentada

*[Justificar primeiro POR QUE a taxa de chegada substitui os usuários virtuais aqui —
sem isso o leitor não entende a diferença entre este cenário e o anterior.]*

Com usuários virtuais, a carga se auto-regula: quando o sistema fica lento, cada
usuário aguarda a resposta e envia menos requisições, e o limite real fica mascarado.
Adotou-se, portanto, **taxa de chegada fixa em degraus** de 200 req/s, independente do
tempo de resposta.

**Figura 1** — Curva de saturação: latência P95 por taxa de chegada, duas execuções
independentes.

| Taxa | P95 exec. 1 | P95 exec. 2 | Erro |
|---|---|---|---|
| 200 a 800 req/s | 25,9 a 31,1 ms | 24,9 a 31,6 ms | 0,00% |
| 1.000 | 56,4 ms | 59,9 ms | 0,00% |
| 1.200 | 128,6 ms | 110,1 ms | 0,00% |
| **1.400** | **356,7 ms** | **462,4 ms** | **0,00%** |
| 1.600 | 1.400,9 ms | 1.934,8 ms | 0,00% |
| 1.800 | 2.430,6 ms | 3.907,0 ms | 0,07% |
| 2.000 | 3.003,6 ms | 4.041,6 ms | 0,17% |

Volume total: 1.361.096 e 1.295.187 requisições, nenhuma iteração interrompida. As
duas execuções coincidem dentro de poucos milissegundos até 1.200 req/s; a divergência
cresce depois do joelho, comportamento esperado perto da saturação.

**Três afirmações sustentadas:**

1. **Capacidade sustentada: 1.400 req/s** — último degrau com baixa dispersão e erro
   zero.
2. **Ruptura entre 1.600 e 1.800 req/s**, pelo critério de P95 ≤ 2.000 ms.
3. **A degradação é de latência, não de disponibilidade.** Mesmo a 2.000 req/s com P95
   de 4 s, o erro máximo foi de **0,17%**: a plataforma enfileirou em vez de recusar.

*[Declarar a ressalva: acima de 1.600 req/s o gerador também saturou (83.873 e 67.969
iterações descartadas), então os degraus superiores são limite inferior da latência
real. Omitir isso seria reportar um número que não se sustenta.]*

### 4.3.2 A causa: fila por conexão, não processamento

**Figura 2** — Latência e fila por conexão de banco sob a mesma carga, em painéis
separados.

| Serviço | Conexões ativas | Fila aguardando | Threads ocupadas |
|---|---|---|---|
| `patient-service` | **5 de 5** | **46** | 52 de 200 |
| `consent-service` | **5 de 5** | **41** | 47 de 200 |
| `history-service` | sem banco | — | 64 de 200 |

A correlação é datada: o degrau de 1.400 req/s inicia às 07:37:06 e **a primeira fila
aparece às 07:37:33**, vinte e sete segundos depois. Em todos os degraus anteriores a
fila era zero.

Os *pools* estavam **completamente ocupados** com dezenas de requisições aguardando,
enquanto as threads do servidor operavam a **menos de um terço** do limite e a CPU do
nó subia apenas de 74% para 82%. **As threads não estavam trabalhando — estavam
bloqueadas aguardando conexão de banco.**

*[Aqui está a diferença entre observação e engenharia. "A plataforma degradou a
1.600 req/s" é observação; a frase acima é diagnóstico. Vale explicitar isso.]*

**O limite é ajustável e isso deve ser dito:** o orçamento atual consome 240 das 500
conexões configuradas no PostgreSQL. Dobrar o *pool* cabe no teto existente. **O ponto
de ruptura medido não é intrínseco à arquitetura** — é consequência de uma decisão de
dimensionamento documentada.

### 4.3.3 Desacoplamento assíncrono

**Figura 4** — Registros de auditoria acumulados, com o instante do encerramento da
carga assinalado.

| | |
|---|---|
| Fila acumulada no Kafka | **1.385.322 eventos** |
| Eventos processados **após** o fim da carga | **≈ 1,40 milhão** |
| Tempo de drenagem | **26 min 24 s** |
| Taxa de consumo | ~940 eventos/s |
| Divergência entre os dois instrumentos de medição | **< 1%** |

*[O valor deste resultado está na convergência: fila do consumidor Kafka e contagem de
linhas no banco são instrumentos independentes, e concordam em menos de 1%. Isso é
rigor que a banca reconhece.]*

A plataforma continuou aceitando requisições no ritmo do cliente enquanto o
processamento assíncrono acumulava fila, e drenou depois **sem perder um único
evento**, com taxa de erro de 0,00%. Uma arquitetura síncrona teria propagado essa
pressão de volta ao cliente como latência crescente ou recusa.

**O gargalo é localizado e identificado.** Todos os demais consumidores mantiveram
fila zero; apenas o serviço de auditoria acumulou, porque executa uma contagem de
janela deslizante **a cada inserção**. É otimização localizada, que não altera o
desenho da arquitetura.

---

## 4.4 Resultado 3 — Elasticidade e resiliência

### 4.4.1 Escalonamento horizontal

**Figura 3** — Réplicas dos quatro serviços do caminho crítico ao longo da bateria.

Quatro serviços atingiram o máximo de réplicas — resultado, paciente, histórico e
consentimento —, exatamente o caminho crítico do cenário de carga, e **os mesmos
quatro** de uma execução anterior em substrato diferente. *[A reprodutibilidade entre
ambientes distintos reforça que o comportamento decorre do desenho, não da
infraestrutura.]*

O tempo de reação foi de **aproximadamente 2 minutos** de duas para dez réplicas,
limitado pela política padrão de subida do orquestrador. O escalonamento ao máximo
ocorre já no cenário de 150 usuários (428 req/s).

### 4.4.2 Resiliência a falha provocada

| Execução | Substituto criado | Pronto | MTTR | Erro ao cliente |
|---|---|---|---|---|
| 1 | +2 s | +42 s | **42 s** | **0,00%** |
| 2 | +0 s | +43 s | **43 s** | **0,01%** (66 de 609.846) |

O RNF-02 estabelece recuperação em até 30 s. **O alvo não foi atingido** — e a
decomposição mostra onde está o tempo:

| Fase | Duração |
|---|---|
| Orquestrador detecta e cria o substituto | 0 a 2 s |
| Agendamento e inicialização do *sidecar* | ~9 s |
| **Inicialização da máquina virtual Java** | **28,5 s** |
| Confirmação pela sonda de prontidão | ~3 s |

**Dois terços do tempo são inicialização da JVM**, medidos diretamente no registro da
aplicação. Sob carga a inicialização levou 28,5 s contra ~20 s sem carga — a contenção
por CPU é mensurável.

*[Uma hipótese testada e refutada merece estar no texto: atribuiu-se inicialmente o
tempo à configuração da sonda de prontidão, que impunha atraso inicial de 30 s. A
substituição por sonda de inicialização levou o tempo de 42 s para 43 s — ou seja, não
mudou. A restrição real era outra. Descartar hipótese com evidência é parte do método.]*

**A distinção central:**

```
RNF-02 exige disponibilidade >= 99,9%  ->  tolera ate 0,100% de falha
medido durante a falha provocada       ->             0,011%
```

O que ficou degradado por 43 s foi a **redundância**, não a **disponibilidade**.

*[Conclusão forte e defensável: o requisito, como formulado, mede tempo de reposição de
réplica quando o que declara proteger é disponibilidade — e as duas grandezas divergem
quando há redundância suficiente. Criticar o próprio requisito, com dado, é sinal de
maturidade.]*

---

## 4.5 Resultado 4 — Segurança e consentimento

### 4.5.1 Comunicação autenticada entre serviços

```
Pod SEM sidecar  ->  HTTP 000   (conexão recusada)
Pod COM sidecar  ->  HTTP 200
```

Tráfego sem identidade da malha é rejeitado na camada de transporte.

### 4.5.2 Controle de acesso — os três estados

| Condição | Resultado | Evento de auditoria |
|---|---|---|
| Sem token | **401** | — |
| Token válido, sem consentimento | **403** | `READ_TIMELINE_DENIED` |
| Token válido + consentimento ativo | **200** | `READ_TIMELINE` |
| Após revogação | **403** | `READ_TIMELINE_DENIED` |

**Nenhum acesso externo ocorre sem consentimento ativo, e toda tentativa — autorizada
ou negada — é registrada.**

### 4.5.3 Escopos, revogação e tokenização

| | |
|---|---|
| `POST /v1/results` sem token / escopo errado / escopo correto | **401 / 403 / 201** |
| Latência da revogação até negação efetiva | **297 ms** (limite: 1.000 ms) |
| Detecção de anomalia | automática, sem duplicação: **29 alertas** em 3.399.128 registros |
| CPF em resposta, registro ou evento | **ausente** — apenas identificador opaco |

*[O contraste público × privado é um ponto central da tese: o produtor privado usa o
mesmo contrato e o mesmo controle de escopo que o público. Vale uma frase explícita.]*

---

## 4.6 Resultado 5 — Minimização de dados

**Figura 5** — Bytes por resposta: linha de base REST contra consulta declarativa.

| | Média de 3 rodadas |
|---|---|
| Linha de base REST (resposta completa e fixa) | **5.522,3 ± 0,3 B** |
| Consulta declarando três campos | **2.651,7 ± 0,1 B** |
| **Redução** | **51,98%** |
| Pontos de integração (visão consolidada) | **4 → 1** |

Fundamento: **minimização de dados, LGPD Art. 6º, III** — o campo não declarado
simplesmente não é transmitido.

*[E a ressalva que protege o trabalho: a minimização NÃO reduz latência. Medido:
187 ± 5 ms contra 205 ± 51 ms — estatisticamente equivalentes. Em rede local com RTT de
0,2 ms, economizar 2,9 KB não compensa o custo de interpretação da consulta.
Reivindicar ganho de desempenho aqui seria indefensável; o ganho é de conformidade
legal e de acoplamento.]*

---

## 4.7 Síntese: confronto com os requisitos não funcionais

| RNF | Situação | Evidência |
|---|---|---|
| **01** Escalabilidade | **Parcial** | 1000 usuários com 0,00% de erro e 1.325 req/s; P95 de 818 ms contra alvo de 500 ms |
| **02** Disponibilidade | **Parcial** | 99,989% durante falha provocada; MTTR de 43 s contra alvo de 30 s |
| **03** Segurança / LGPD | **Atendido** | mTLS demonstrado; 3,4 milhões de registros imutáveis; CPF ausente |
| **04** Observabilidade | **Parcial** | Métricas de JVM, pools, threads e malha coletadas; três alertas carregados; identificador de rastreamento não demonstrado |
| **05** Interoperabilidade | **Atendido** | Especificação aberta em 9 de 9 serviços com API REST; linha do tempo em 68 ms |
| **06** Consentimento | **Parcial** | Verificação inline em 18 ms; revogação em 297 ms; limite de requisições por paciente não implementado |

---

## 4.8 Limitações

*[Esta seção protege o trabalho. Cada limitação abaixo já está declarada e
quantificada nos documentos de apoio — não é confissão de fraqueza, é delimitação de
validade.]*

1. **Nó único, sem escalonamento de nós.** A arquitetura escala horizontalmente até o
   limite do substrato; acima disso, o autoescalador solicita réplicas que não há onde
   acomodar. Esse comportamento é, ele próprio, um resultado (4.2.3).
2. **Carga sintética concentrada.** As leituras incidem sobre poucos pacientes,
   mantendo o *cache* do banco aquecido — as latências são um **teto otimista**.
3. **Gerador saturado acima de 1.600 req/s.** Os degraus superiores são limite inferior.
4. **Limite de requisições por paciente não implementado.** O mecanismo de detecção
   existe e foi demonstrado; a variante bloqueante é decisão de política. Exercitá-la
   exigiria carga com cardinalidade realista de pacientes.
5. **Rastreamento distribuído não verificado fim a fim.** Os *sidecars* geram os
   registros, mas não há propagação explícita de cabeçalhos no código da aplicação.
6. **Otimizações identificadas e não implementadas:** paralelizar as chamadas
   independentes do agregador; *cache* de consentimento com invalidação por evento
   (o tópico já existe, sem consumidor); elevar o *pool* de conexões; imagem nativa
   para reduzir a inicialização.

---

## Apoio — onde buscar cada coisa

| Precisa de | Documento |
|---|---|
| Qualquer número e sua ressalva | `sintese-resultados-tcc.md` |
| Memória de cálculo e séries completas | `resultados-bateria-carga.md` |
| Desafios, hipóteses descartadas, lições de método | `diario-de-execucao.md` |
| Ambiente, versões, validação funcional | `validacao-funcional-vm.md` |
| Recorte metodológico e limitações | `plano-teste-estresse.md` |
| Figuras (SVG e PDF) | `docs/figuras/` |
| Dados de origem, para regerar figuras | `docs/dados-figuras/` |
