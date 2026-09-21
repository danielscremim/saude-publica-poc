# Síntese dos resultados — base para a redação

> **Como usar.** Todos os números medidos, organizados na ordem dos cinco resultados
> do `plano-teste-estresse.md`, com a evidência e a ressalva de cada um. A seção 8
> confronta os seis RNFs; a seção 9 declara **o que não foi demonstrado**.
>
> Detalhamento e memória de cálculo em `docs/resultados-bateria-carga.md`;
> ambiente e validação funcional em `docs/validacao-funcional-vm.md`; desafios e
> hipóteses descartadas em `docs/diario-de-execucao.md`.
>
> Medições de 19 e 20/09/2026.

---

## 1. Ambiente experimental

| | VM-1 — sistema sob teste | VM-2 — gerador de carga |
|---|---|---|
| Identificação | `multitransclister` · 192.168.5.3 | `multiransk6` · 192.168.5.4 |
| Recursos | 32 vCPU · 62 GB · 145 GB | 8 vCPU · 15 GB · 30 GB |
| Sistema | Ubuntu Server 24.04 LTS | Ubuntu Server 24.04 LTS |
| Orquestração | k3d 5.9.0 · K3s v1.35.5+k3s1 (nó único) | — |
| Malha de serviços | Istio 1.30.4, mTLS STRICT | — |
| Observabilidade | kube-prometheus-stack 91.4.1 · Grafana 13.2.2 · Loki 3.7.7 · Tempo 3.0.3 | — |
| Gerador | — | k6 v2.2.0 |

**Plataforma:** 10 microsserviços Java 21 / Spring Boot 3.3.5 · PostgreSQL 16 (8 bases,
*database per service*) · Apache Kafka 3.8.1 (KRaft) · Kong 3.7 como gateway.

**Rede entre as VMs:** RTT médio **0,218 ms**, 0% de perda em 20 pacotes.

**Configuração:** 2 réplicas mínimas por serviço, HPA a 70% de CPU, `maxReplicas: 6`
(valor medido — ver Resultado 1.3).

---

## 2. Resultado 1 — Desempenho sob carga

### 2.1 Carga de referência — 150 usuários virtuais, limites de **projeto**

| Métrica | Média ± desvio | Limite | |
|---|---|---|---|
| `http_req_duration` p95 | **48,4 ± 7,7 ms** | 500 ms | ✅ |
| Linha do tempo clínica p95 | **68,1 ± 11,2 ms** | 800 ms | ✅ |
| Verificação de consentimento p95 | **18,0 ± 3,2 ms** | 20 ms | ⚠️ |
| Taxa de erro | 0,00% / 0,01% / 0,01% | < 1% | ✅ |
| Vazão | **428,1 ± 5,8 req/s** | — | |

⚠️ A terceira rodada mediu **21,07 ms** no consentimento e excedeu o limite. Reportar
assim, com a rodada discrepante explícita.

**Interpretação:** com 150 usuários simultâneos a plataforma atende os alvos de
**projeto** — não os relaxados de PoC — incluindo a verificação de consentimento
inline em 18 ms, atravessando gateway, sidecar Envoy e mTLS.

### 2.2 Carga nominal — 1000 usuários virtuais (RNF-01)

| Métrica | Média ± desvio | Projeto | PoC |
|---|---|---|---|
| `http_req_duration` p95 | **818 ± 110 ms** | 500 ms ❌ | 2.000 ms ✅ |
| Linha do tempo p95 | **937 ± 142 ms** | 800 ms ❌ | 2.000 ms ✅ |
| Consentimento p95 | **398 ± 73 ms** | 20 ms ❌ | 2.000 ms ✅ |
| **Taxa de erro** | **0,00%** nas três rodadas | < 1% | ✅ |
| Vazão | **1.324,9 ± 75,0 req/s** | — | |

**Interpretação:** a plataforma sustenta 1000 usuários simultâneos **sem perder uma
única requisição**, mas a latência fica acima do alvo de projeto. A causa está na
seção 3.2 — é saturação de recurso compartilhado, não defeito de desenho.

### 2.3 O dimensionamento do autoescalador

Experimento controlado, única variável alterada: `maxReplicas` de 10 para 6.

| | Pods ativos | Vazão | P95 | Erro | **Req/s por núcleo** |
|---|---|---|---|---|---|
| Execução degradada | **57** | 130 req/s | 38,3 s | 22,8% | **4,2** |
| `maxReplicas=6` | **45** | 1.225 req/s | 918 ms | 0,00% | **40,7** |
| Execução de 19/09 | **43** | 1.325 req/s | 818 ms | 0,00% | — |

**Com praticamente a mesma CPU (98% contra 94%), a configuração menor realizou 9,7×
mais trabalho útil por núcleo.**

> **O que determina o resultado é o número de pods ativos em relação à capacidade do
> nó, não o valor nominal de `maxReplicas`.** As duas configurações saudáveis têm 43 e
> 45 pods; a degradada tem 57. Cada réplica adicional traz uma JVM e um sidecar
> Envoy — consome capacidade sem acrescentar nenhuma. Acima do que o nó comporta, o
> autoescalador entra em realimentação positiva: vê CPU alta, pede mais réplicas, que
> consomem mais CPU.

---

## 3. Resultado 2 — Ponto de ruptura e desacoplamento assíncrono

### 3.1 A curva de saturação

Taxa de chegada fixa em degraus de 200 req/s. Duas execuções independentes:

| Taxa alvo | P95 exec. 1 | P95 exec. 2 | Erro |
|---|---|---|---|
| 200 req/s | 25,9 ms | 24,9 ms | 0,00% |
| 400 | 25,6 ms | 24,5 ms | 0,00% |
| 600 | 26,2 ms | 25,9 ms | 0,00% |
| 800 | 31,1 ms | 31,6 ms | 0,00% |
| 1.000 | 56,4 ms | 59,9 ms | 0,00% |
| 1.200 | 128,6 ms | 110,1 ms | 0,00% |
| **1.400** | **356,7 ms** | **462,4 ms** | **0,00%** |
| 1.600 | 1.400,9 ms | 1.934,8 ms | 0,00% |
| 1.800 | 2.430,6 ms | 3.907,0 ms | 0,07% |
| 2.000 | 3.003,6 ms | 4.041,6 ms | 0,17% / 0,00% |

Volume: 1.361.096 e 1.295.187 requisições, **nenhuma iteração interrompida**.
Reprodutibilidade dentro de poucos milissegundos até 1.200 req/s.

**Os três números:**

- **Capacidade sustentada: 1.400 req/s** — último degrau com baixa dispersão, erro
  zero e gerador folgado.
- **Ruptura (P95 ≤ 2.000 ms): entre 1.600 e 1.800 req/s.**
- **Degradação de latência, não de disponibilidade:** mesmo a 2.000 req/s com P95 de
  4 s, o erro máximo foi **0,17%**. A plataforma **enfileirou em vez de recusar**.

> **Ressalva:** 83.873 e 67.969 `dropped_iterations`. Acima de 1.600 req/s o gerador
> também estava no limite, então os degraus superiores são **limite inferior** da
> latência real.

### 3.2 A causa — fila por conexão de banco, não CPU

| Serviço | Conexões ativas | Fila aguardando | Threads Tomcat |
|---|---|---|---|
| `patient-service` | **5 de 5** | **46** | 52 de 200 |
| `consent-service` | **5 de 5** | **41** | 47 de 200 |
| `history-service` | (sem banco) | — | 64 de 200 |

Correlação datada: o teste iniciou às 07:22:06 UTC; o degrau de 1.400 req/s começa às
07:37:06 e **a primeira fila aparece às 07:37:33** — 27 segundos depois. Até então,
zero em todos os degraus.

**Leitura:** pools completamente ocupados com dezenas de requisições aguardando,
enquanto as threads do servidor operavam a **menos de um terço** do limite e a CPU do
nó subia apenas de 74% para 82%. As threads não estavam trabalhando — estavam
**bloqueadas esperando conexão de banco**.

**O limite é ajustável:** o orçamento atual usa 240 das 500 conexões configuradas no
PostgreSQL (8 serviços × 6 réplicas × 5 conexões). Dobrar o pool cabe. O ponto de
ruptura encontrado **não é intrínseco à arquitetura**.

### 3.3 Desacoplamento assíncrono — o resultado mais forte

Medido por dois instrumentos independentes que convergem:

| | |
|---|---|
| Fila acumulada no Kafka (soma de 3 partições) | **1.385.322 eventos** |
| Eventos drenados **após** o fim da carga | **≈ 1,40 milhão** |
| Tempo de drenagem | **26 min 24 s** |
| Taxa sustentada de consumo | ~940 eventos/s |
| Divergência entre os dois instrumentos | **< 1%** |

**Gargalo localizado.** Durante toda a bateria os demais consumidores mantiveram lag
**zero**: `lab-service`, `notification-service` e `result-service`. Apenas o
`audit-service` acumulou fila — porque o `AnomalyDetector` executa um `COUNT` de
janela deslizante **a cada inserção**.

**Interpretação:** a plataforma continuou aceitando requisições no ritmo do cliente
enquanto o processamento assíncrono acumulava fila, e drenou depois **sem perder um
único evento**, com `http_req_failed` de 0,00%. Uma arquitetura síncrona teria
propagado essa pressão de volta ao cliente como latência ou erro.

---

## 4. Resultado 3 — Auto-scaling e resiliência

### 4.1 Escalonamento horizontal

Quatro serviços atingiram o máximo de réplicas: `result-service`, `patient-service`,
`history-service` e `consent-service` — exatamente o **caminho quente** do cenário, e
**os mesmos quatro** de uma execução anterior em substrato diferente.

**Tempo de reação:** de 2 para 10 réplicas em **≈ 2 minutos** (15:31:11 → 15:33:12),
limitado pela política padrão de subida do HPA. O escalonamento ao máximo ocorre já
no cenário de 150 usuários (428 req/s) — o gatilho de 70% de CPU sobre duas réplicas
é atingido com folga.

### 4.2 Resiliência a falha provocada

Eliminação de uma réplica `2/2 Running` sob carga de 1000 usuários virtuais:

| Execução | Substituto criado | Pronto | **MTTR** | P95 na janela | Erro ao cliente |
|---|---|---|---|---|---|
| 1 | +2 s | +42 s | **42 s** | 931 ms | **0,00%** |
| 2 | +0 s | +43 s | **43 s** | 1.170 ms | **0,01%** (66 de 609.846) |

**Onde estão os 43 segundos:**

| Fase | Duração | Evidência |
|---|---|---|
| Kubernetes detecta e cria o substituto | 0–2 s | registrado pelo script |
| Agendamento + init do sidecar Istio | ~9 s | intervalo até o primeiro log |
| **Arranque da JVM** | **28,5 s** | `Started ... in 28.458 seconds` |
| Confirmação da sonda | ~3 s | `periodSeconds: 5` |

**Dois terços do tempo são arranque da JVM.** Sob carga: 28,5 s; sem carga: ~20 s — a
contenção por CPU é mensurável.

**A distinção que importa:**

```
RNF-02 exige disponibilidade >= 99,9%  ->  tolera ate 0,100% de falha
medido durante a falha provocada       ->             0,011%
```

O que ficou degradado por 43 s foi a **redundância**, não a **disponibilidade**.

---

## 5. Resultado 4 — Segurança e consentimento

### 5.1 mTLS STRICT — demonstrado

```
Pod SEM sidecar  ->  HTTP 000   (conexão recusada)
Pod COM sidecar  ->  HTTP 200
```

Tráfego sem identidade do mesh é rejeitado na camada de transporte.

### 5.2 Controle de acesso à linha do tempo — os três estados

| Condição | Resultado | Evento de auditoria |
|---|---|---|
| Sem token | **401** | — |
| Token válido, sem consentimento | **403** `consent_denied` | `READ_TIMELINE_DENIED` |
| Token válido + consentimento ativo | **200** com paciente e exames | `READ_TIMELINE` |
| Após revogação do consentimento | **403** | `READ_TIMELINE_DENIED` |

**Nenhum acesso externo ocorre sem consentimento ativo, e toda tentativa — autorizada
ou negada — é auditada.**

### 5.3 Escopos e fluxo bidirecional

| Condição em `POST /v1/results` | Resultado |
|---|---|
| Sem token | **401** |
| Token com escopo incorreto | **403** `insufficient_scope` |
| Token com escopo `result:write` | **201**, `origin: HOSPITAL_PRIVADO` |

Propagação verificada: `exam.completed` disparou a notificação e `audit.events`
registrou `WRITE_RESULT`. **Produtor privado usa o mesmo contrato e o mesmo controle.**

### 5.4 Revogação e auditoria

| | |
|---|---|
| Latência da revogação até a negação efetiva | **297 ms** (limite: 1.000 ms) |
| Detecção de anomalia | automática, 10 acessos / 10 min, sem duplicação |
| Registros de auditoria acumulados na bateria | 3.399.128, com apenas **29** anomalias |
| CPF em resposta de API, log ou evento | **ausente** — apenas UUID |

Na camada GraphQL, os mesmos controles: 401 sem token, `FORBIDDEN` sem consentimento,
`FORBIDDEN` após revogação, e profundidade abusiva barrada (`8 > 5`).

---

## 6. Resultado 5 — Minimização de dados

| | Média das 3 rodadas |
|---|---|
| Linha de base REST (resposta completa e fixa) | **5.522,3 ± 0,3 B** |
| GraphQL declarando três campos | **2.651,7 ± 0,1 B** |
| **Redução** | **51,98%** |

Medições rigorosamente estáveis. Fundamento: **minimização de dados, LGPD Art. 6º,
III** — o campo não declarado simplesmente não é transmitido.

**Pontos de integração:** visão consolidada de quatro domínios em **1 requisição
contra 4**.

**A minimização NÃO reduz latência:** 187 ± 5 ms contra 205 ± 51 ms da linha de base —
estatisticamente equivalentes. Em rede local com RTT de 0,2 ms, economizar 2,9 KB não
compensa o custo de *parsing* e validação. **O ganho é de conformidade legal e de
acoplamento, não de desempenho.**

---

## 7. Achados de engenharia

Quatro classes de defeito que **não se manifestam em ambiente de estação de trabalho**:

| Achado | Impacto se não corrigido |
|---|---|
| `max_connections` padrão (100) contra 160 conexões em repouso | Réplicas em `CrashLoopBackOff` |
| Kong subiu **33 nginx workers** lendo os vCPUs do **nó**, não do contêiner | Morto por consumo de memória |
| `fs.inotify.max_user_instances` padrão (128) esgotado pelos sidecars | Réplicas novas não iniciam |
| `RLIMIT_NOFILE` de 1024 no gateway e no gerador | **Números errados sem falha visível** |

E três defeitos **na própria instrumentação**, todos silenciosos:

| Defeito | Sintoma |
|---|---|
| `micrometer-registry-prometheus` ausente | `/actuator/prometheus` não existia |
| `targetPort` depreciado no PodMonitor; `portNumber` exige porta **declarada** | Job na configuração do Prometheus com **zero alvos** |
| Spring Boot desabilita MBeans do Tomcat | Métrica de threads inexistente, sem erro |

> **Instrumento que erra em silêncio é pior que instrumento que falha.** Validar a
> instrumentação faz parte do método, e está registrado no diário de execução.

---

## 8. Confronto com os seis RNFs

| RNF | Exigência | Medido | Situação |
|---|---|---|---|
| **01** Escalabilidade | P95 ≤ 500 ms com 1000 req simultâneas; HPA a 70% | 1000 VUs com **0,00% de erro** e 1.325 req/s, mas P95 de 818 ms; HPA 2→10 em 2 min | **Parcial** |
| **02** Disponibilidade | ≥ 99,9%; ≥ 2 réplicas; self-healing ≤ 30 s | Disponibilidade **99,989%** durante falha provocada ✅; MTTR de **43 s** ❌ | **Parcial** |
| **03** Segurança / LGPD | mTLS STRICT; auditoria imutável; zero CPF | mTLS **demonstrado** (000 vs 200); 3,4 milhões de registros imutáveis; CPF ausente | **Atendido** |
| **04** Observabilidade | 100% trace ID; painéis; alerta P95 > 500 ms | Métricas de JVM, HikariCP, Tomcat e Envoy coletadas; alertas definidos; **trace ID não demonstrado** | **Parcial** |
| **05** Interoperabilidade | OpenAPI em todos os endpoints; linha do tempo ≤ 800 ms | **9 de 9** serviços com REST expõem OpenAPI; linha do tempo **68 ms** com 150 VUs | **Atendido** |
| **06** Consentimento | Consent em 100% das externas; 403 sem consent; revogação ≤ 1 s; CPF tokenizado; anomalia | Consent inline **18 ms**; 403 nos dois estados; revogação em **297 ms**; UUID em tudo; anomalia automática. **Rate limit 60/h não implementado** | **Parcial** |

---

## 9. O que NÃO foi demonstrado — declarar no trabalho

1. **Rate limit de 60 requisições/hora por paciente por instituição (RNF-06).** Não
   implementado. O Kong aplica limite genérico por rota, elevado a 10.000/min para
   viabilizar os testes de carga — não é o controle que o requisito descreve.
2. **Trace ID em 100% das requisições (RNF-04).** Os sidecars Istio geram spans, mas
   **não há propagação explícita de cabeçalhos de rastreamento no código** dos
   serviços. Sem isso, traços não se correlacionam entre saltos. O Tempo está
   instalado e recebendo dados do Istio, mas a correlação fim a fim não foi verificada.
3. **P95 ≤ 500 ms com 1000 usuários (RNF-01)** e **MTTR ≤ 30 s (RNF-02).** Ambos com
   causa isolada e caminho de correção documentado (seções 3.2 e 4.2).
4. **Otimizações identificadas e não implementadas:** paralelizar as duas chamadas
   independentes do agregador; cache de consentimento com invalidação por
   `consent.revoked` (tópico já existe, **sem consumidor**); elevar o pool de
   conexões; imagem nativa ou CRaC para o arranque da JVM.
5. **Limitações do experimento:** nó único sem *Cluster Autoscaler*; carga sintética
   concentrada em poucos pacientes (latências são teto otimista); medição do
   consolidado contaminada por `auditTrail` sem paginação; gerador de carga saturado
   acima de 1.600 req/s.

---

## 10. Figuras — geradas e prontas para inserir

Arquivos em `docs/figuras/`, em **SVG e PDF vetoriais** (escalam sem perda na
impressao) e PNG para conferencia rapida. Regeraveis com `python docs/gerar-figuras.py`
a partir dos CSVs em `docs/dados-figuras/`.

| Figura | O que mostra | Onde usar |
|---|---|---|
| `fig1-curva-saturacao` | P95 por taxa de chegada, duas execucoes, com o limite de 2.000 ms e a capacidade sustentada marcados | Resultado 2 |
| `fig2-fila-conexoes` | Latencia e fila por conexao no mesmo eixo de carga, em paineis separados | Resultado 2 — a causa |
| `fig3-replicas-hpa` | Replicas dos quatro servicos do caminho quente ao longo da bateria | Resultado 3 |
| `fig4-drenagem-kafka` | Registros de auditoria acumulados, com o instante em que a carga encerrou | Resultado 2 — desacoplamento |
| `fig5-bytes-rest-graphql` | Bytes por resposta, REST contra GraphQL declarado | Resultado 5 |
| `fig6-eficiencia-por-nucleo` | Requisicoes por segundo por nucleo nas tres configuracoes | Resultado 1 |
| `fig7-painel-grafana.jpg` | Painel de observabilidade sob carga de 400 usuarios virtuais | **Anexo** — evidencia do RNF-04 |

**Decisoes de apresentacao adotadas, caso a banca pergunte:**

- **Nenhum grafico de eixo duplo.** A figura 2 precisaria de dois eixos y (latencia e
  fila); em vez disso usa dois paineis com o mesmo eixo x. Eixo duplo permite
  sugerir correlacoes que os dados nao sustentam, ao escolher escalas convenientes.
- **Paleta verificada para daltonismo** (separacao acima do piso em todos os pares
  adjacentes, nos modos de visao normal, protanopia e tritanopia) e identidade nunca
  so por cor: toda figura com duas ou mais series tem legenda ou rotulo direto.
- **Escala linear em todas as latencias.** Escala logaritmica achataria visualmente a
  explosao de latencia entre 1.400 e 1.600 req/s, que e justamente o resultado.

**Sobre a figura 7 — ela nao e grafico de resultado, e evidencia de instrumentacao.**
Captura do painel `saude-poc-rnfs` durante carga de 400 usuarios virtuais
(501.202 requisicoes, 926 req/s, P95 de 253,3 ms, 0,00% de erro). Vai em **anexo**,
para demonstrar que a camada de observabilidade existe e opera, e nao no corpo dos
Resultados, onde os numeros devem vir das figuras vetoriais reproduziveis.

O que a captura mostra em funcionamento, simultaneamente: latencia P95 por servico,
vazao separada entre gateway e malha, taxa de erro, replicas escalando de 2 para 6,
**fila aguardando conexao de banco**, conexoes ativas, threads do servidor, fila de
eventos do Kafka e memoria de heap.

Dados de origem em `docs/dados-figuras/`; artefatos completos em
`tests/k6/resultados/` (VM-2) e `tests/k6/resultados-infra/` (VM-1).
