# Resultados da bateria de carga — 19/09/2026

> **Insumo para a seção de Resultados e Discussão.** Bateria oficial executada em
> 19/09/2026, das 15:19 às 17:39 (UTC), nas duas VMs de datacenter. Cenários A
> (referência), B (nominal) e E (minimização), três rodadas cada. Os cenários C
> (ponto de ruptura) e D (resiliência) estão pendentes.
> Ambiente e validação funcional em `docs/validacao-funcional-vm.md`; recorte dos
> cinco resultados em `plano-teste-estresse.md`.

---

## 1. Condições da execução

| | |
|---|---|
| Sistema sob teste | VM-1 `multitransclister` (32 vCPU / 62 GB), k3d + K3s 1.35.5, Istio 1.30.4 |
| Gerador de carga | VM-2 `multiransk6` (8 vCPU / 15 GB), k6 v2.2.0 |
| Rede entre as VMs | RTT médio **0,218 ms**, 0% de perda (20 pacotes) |
| Entrada | Kong 3.7 (NodePort 30800 → porta 8000 da VM-1) |
| Início / fim da carga | 15:19:00 / **17:39:05** |
| Aquecimento | 2 min a 50 VUs, descartado |
| Pausa entre rodadas | 180 s |

**Condição relevante a declarar:** a bateria foi executada com
`fs.inotify.max_user_instances = 128` (padrão do Ubuntu). Esse limite do kernel
impediu que parte das réplicas novas iniciasse durante os picos do HPA — ver §6.1.
O parâmetro foi elevado para 8192 **após** esta bateria.

---

## 2. Resultado 1 — Desempenho sob carga

### 2.1 Cenário A — 150 VUs, limites de **projeto**

| Métrica | r1 | r2 | r3 | **Média ± dp** | Limite | |
|---|---|---|---|---|---|---|
| `http_req_duration` p95 | 49,34 | 40,21 | 55,56 | **48,4 ± 7,7 ms** | 500 ms | ✓ |
| `{endpoint:timeline}` p95 | 71,42 | 55,57 | 77,16 | **68,1 ± 11,2 ms** | 800 ms | ✓ |
| `{endpoint:consent_check}` p95 | 18,16 | 14,69 | **21,07** | **18,0 ± 3,2 ms** | 20 ms | ⚠ |
| `http_req_failed` | 0,00% | 0,01% | 0,01% | — | < 1% | ✓ |

Mediana geral: 14,86 / 14,55 / 16,77 ms.

**Leitura.** Com 150 usuários virtuais a plataforma atende os limites de projeto —
não os relaxados de PoC. O `consent_check` é o único apertado: a média (18,0 ms)
fica abaixo do teto de 20 ms do RNF-06, mas **a terceira rodada mediu 21,07 ms e
excedeu**. Deve ser reportado assim, com a rodada discrepante explícita, e não
mascarado pela média. O k6 encerrou essa rodada com código 99 (`execucoes.csv`).

Esse é o valor de uma verificação de consentimento **inline**, atravessando gateway,
sidecar Envoy e mTLS — não uma consulta direta ao banco.

### 2.2 Cenário B — 1000 VUs (RNF-01)

| Métrica | r1 | r2 | r3 | **Média ± dp** | Projeto | PoC |
|---|---|---|---|---|---|---|
| `http_req_duration` p95 | 944,91 | 745,31 | 764,41 | **818 ± 110 ms** | 500 ✗ | 2000 ✓ |
| `{endpoint:timeline}` p95 | 1100,42 | 845,20 | 866,16 | **937 ± 142 ms** | 800 ✗ | 2000 ✓ |
| `{endpoint:consent_check}` p95 | 481,49 | 365,20 | 348,00 | **398 ± 73 ms** | 20 ✗ | 2000 ✓ |
| `http_req_failed` | 0,00% | 0,00% | 0,00% | **0,00%** | < 1% | ✓ |

Mediana geral: 313,67 / 269,14 / 275,95 ms.

**Leitura.** Sob 1000 usuários virtuais simultâneos a plataforma sustentou a carga
com **zero erros nas três rodadas** — nenhuma requisição perdida em centenas de
milhares. A latência, porém, ficou acima do alvo de projeto: P95 de 818 ms contra
500 ms. Os limites adotados para a PoC (2000 ms) foram atendidos com folga.

É o resultado honesto do RNF-01: **a arquitetura escala sem perder requisições, mas
o alvo de latência de projeto não é alcançado neste substrato**. A distinção entre
limites de projeto e de PoC, adotada desde o início em `tests/k6/lib/config.js`,
existe exatamente para tornar esse tipo de resultado reportável sem ambiguidade.

> **Repetição pendente.** Esta execução ocorreu sob o limite de `inotify` descrito
> em §6.1, que degradou o escalonamento efetivo. O cenário B será repetido com o
> substrato corrigido. A comparação não será um A/B controlado: entre as duas
> execuções mudaram tanto o `inotify` quanto o volume do banco (§5). Se o P95
> melhorar, a conclusão é forte; se não, reportam-se as duas execuções com suas
> condições, sem afirmar causalidade.

---

## 3. Resultado 2 — Desacoplamento assíncrono

**É o resultado mais forte da bateria**, e foi medido por dois caminhos
independentes que convergem.

### 3.1 Fila acumulada no Kafka

Lag do consumidor, somando as três partições de `audit.events`, às 17:35:02:

```
partição 1: 591.555
partição 2: 530.425
partição 3: 263.342
            ---------
total:    1.385.322 eventos em fila
```

> Atenção ao ler o `kafka-lag.csv`: há uma linha por partição. O pico de uma
> partição isolada foi 608.890 (16:26:14); o número que importa é a **soma**.

### 3.2 Drenagem após o fim da carga

A carga terminou às **17:39:05**. A tabela `audit_log` continuou crescendo:

| Hora | `audit_log` | Situação |
|---|---|---|
| 17:38:50 | 1.986.888 | carga ainda ativa |
| 17:39:05 | ~2.000.000 | **carga encerrada** |
| 17:45:33 | 2.400.871 | drenando |
| 17:55:00 | 2.965.902 | drenando |
| 18:02:53 | 3.346.459 | drenando |
| **18:05:29** | **3.399.017** | **fila zerada** |
| 18:14:41 | 3.399.017 | estável |

**≈ 1,40 milhão de eventos processados em 26 min 24 s com a carga parada**, a uma
taxa sustentada de ~940 eventos/s (média de ~880 eventos/s incluindo a cauda final).

As duas medições — lag do consumidor (1.385.322) e contagem de linhas no banco
(1.398.568) — convergem com diferença inferior a 1%. São instrumentos independentes
medindo o mesmo fenômeno.

### 3.3 Interpretação

A plataforma **continuou aceitando requisições no ritmo do cliente** enquanto o
processamento assíncrono acumulava fila, e drenou depois **sem perder um único
evento**. O `http_req_failed` foi 0,00% durante todo o período.

Uma arquitetura síncrona teria propagado essa pressão de volta ao cliente como
latência crescente ou erro. É a demonstração direta do desacoplamento por
mensageria — e a justificativa empírica da escolha do Kafka no lugar de chamadas
diretas entre serviços.

### 3.4 O gargalo é localizado, e identificado

Durante toda a bateria, os demais consumidores mantiveram **lag zero**:

| Consumidor | Tópico | Lag máximo |
|---|---|---|
| `lab-service` | `exam.requested` | 0 |
| `notification-service` | `exam.completed` | 0 |
| `result-service` | `exam.completed` | 0 |
| **`audit-service`** | **`audit.events`** | **1.385.322** |

Não é o Kafka nem a arquitetura assíncrona em geral: é especificamente o
`audit-service`. A causa é conhecida e está no código — o `AnomalyDetector`
executa um `COUNT` de janela deslizante **a cada inserção** para a detecção de
anomalia do RNF-06. Com 3,4 milhões de registros, esse custo cresce
progressivamente, mesmo com o índice `idx_audit_patient_ts`.

**Trabalho futuro concreto:** substituir a contagem por inserção por uma agregação
periódica ou um contador incremental por janela. É uma otimização localizada, que
não altera o desenho da arquitetura.

---

## 4. Resultado 3 — Auto-scaling e resiliência

### 4.1 Escalonamento horizontal

Quatro serviços atingiram o **máximo de 10 réplicas** (de um mínimo de 2), a 70% de
CPU:

```
result-service · patient-service · history-service · consent-service  →  10/10
```

São exatamente os quatro serviços do **caminho quente** do cenário de carga, e são
**os mesmos quatro** da execução [3] registrada no cabeçalho de `tests/k6/load.js`,
realizada em ambiente anterior. A reprodutibilidade entre substratos diferentes
reforça que o comportamento decorre do desenho, não da infraestrutura.

Os demais seis serviços mantiveram 2 réplicas — CPU abaixo do gatilho. Ao final,
todos retornaram a 2 réplicas: o ciclo de subida e descida está documentado.

> **A extrair do `hpa.csv`:** o instante em que cada serviço atingiu 10 réplicas pela
> primeira vez, para calcular o **tempo de reação do HPA** desde o início da rampa.
> Comando: `awk -F, '$3==10' hpa.csv | head`.

### 4.2 Auto-recuperação sob falha real

Durante a bateria, dois pods reiniciaram sozinhos:

```
patient-service-f498ccc4-z5mq2   5 reinícios   ~17:04
result-service-b6f4bdc94-6z96k   4 reinícios   ~17:04
```

E o `http_req_failed` das três rodadas de 1000 VUs foi **0,00%**.

As réplicas restantes absorveram a carga sem erro visível ao cliente. É o RNF-02
demonstrado por uma falha **não provocada** — mais convincente que a injeção
manual do cenário D, porque não foi encenada.

> **Nota metodológica:** o `snapshot-final.txt` reporta `RESTARTS 0` para todos os
> pods. É defeito do coletor, que lê `containerStatuses[0]` — o sidecar, não a
> aplicação. Use os números de `kubectl get pods`, que estão corretos.

---

## 5. Resultado 5 — Minimização de dados (cenário E)

### 5.1 Redução por declaração de campos

| | Média das 3 rodadas | Desvio |
|---|---|---|
| Linha de base REST (resposta completa e fixa) | **5.522,3 B** | ± 0,3 B |
| GraphQL declarando três campos | **2.651,7 B** | ± 0,1 B |
| **Redução** | **51,98%** | — |

Medições rigorosamente estáveis nas três rodadas (5522,65 / 5522,10 / 5522,30 e
2651,80 / 2651,60 / 2651,80). Consistente com os 56% medidos na validação funcional
com outro conjunto de dados.

Fundamento: minimização de dados, **LGPD Art. 6º, III**. O campo `origin`, presente
na linha de base, simplesmente não é transmitido quando não é declarado.

### 5.2 Redução de pontos de integração

Visão consolidada de quatro domínios (paciente, exames, notificações, auditoria)
em **1 requisição contra 4** — métrica `roundtrips`: 1 para o consolidado, 4 para a
linha de base REST.

### 5.3 A minimização NÃO reduz latência — e isso precisa ser dito

| | p95 médio |
|---|---|
| Linha de base REST | 205 ± 51 ms |
| GraphQL declarado (mínimo) | **187 ± 5 ms** |

Estatisticamente equivalentes. Em rede local com RTT de 0,2 ms, economizar 2,9 KB
não compensa o custo de *parsing* e validação da consulta GraphQL.

**Não reivindicar ganho de desempenho.** O ganho é de **conformidade legal** e de
**acoplamento** (menos pontos de integração). Resposta à banca: *o benefício é
regulatório e arquitetural, não de latência; em rede local a economia de banda não
se converte em tempo.*

### 5.4 A medição do consolidado está contaminada — declarar

O `consolidado` mediu p95 de 2349,77 / 860,33 / 782,54 ms (**1331 ± 883 ms** — o
desvio quase igual à média denuncia o problema).

**Causa identificada.** A consulta consolidada pede `auditTrail` **sem limite**, e
cada leitura de linha do tempo *gera* um evento de auditoria. O estágio
`consolidado` é o **quinto e último** do cenário: carrega toda a trilha acumulada
pelos quatro estágios anteriores de 200 VUs. O `baseline_multi`, seu par de
comparação, rodou um estágio antes, com trilha muito menor.

Consequências:
- A comparação de **latência e bytes** entre `consolidado` e `baseline_multi`
  **não é pareada** e não deve ser usada.
- A redução de **round-trips (4 → 1)** permanece válida: é estrutural, não medida.

**Vira achado, não apenas defeito:** o experimento demonstrou involuntariamente que
**campos de lista sem paginação em um BFF são um risco real de crescimento
descontrolado da resposta**. Paginar `auditTrail` é trabalho futuro concreto.

---

## 6. Achados de engenharia

### 6.1 Limite de `inotify` do kernel estrangulou o escalonamento

**Sintomas:** pods em `Init:Error` e `Init:CrashLoopBackOff` às 15:33 e 17:44;
reinícios com `Exit Code 255` cinco segundos após o início, às 17:04.

**Causa raiz** (obtida do Loki, retenção de 72 h):

```
failed to start default Istio SDS server: failed to start workload secret manager
couldn't initialize inotify: too many open files
```

Não é a aplicação Java: é o **sidecar Envoy** falhando ao iniciar. Cada sidecar
Istio consome instâncias `inotify` do kernel do host, e o padrão do Ubuntu é
`fs.inotify.max_user_instances = 128`. Ao tentar subir dezenas de réplicas novas
simultaneamente, o HPA esgotou o limite.

**Correção aplicada após a bateria:**

```
fs.inotify.max_user_instances:  128  →  8192
fs.inotify.max_user_watches: 501677  →  524288
```

**Por que importa para o trabalho:** é um limite do **substrato**, não da
arquitetura — mas precisa ser corrigido antes do cenário C, sob pena de o "ponto de
ruptura" medido ser o esgotamento de `inotify` do kernel e não o limite da
plataforma. É também um resultado em si: *em malha de serviços, o custo de um
sidecar por pod inclui recursos do kernel que não aparecem em nenhum limite de CPU
ou memória declarado no manifesto.*

### 6.2 As rodadas não são independentes

A **rodada 1 é a pior em todos os cenários**: 944,91 contra 745,31/764,41 ms no
cenário B; 49,34 contra 40,21 ms no A; 2349,77 contra 860,33/782,54 ms no
consolidado.

A causa é verificável: o HPA do Kubernetes usa janela padrão de estabilização de
**300 s** para reduzir réplicas, e o `run-2vm.sh` pausa **180 s** entre rodadas. As
rodadas 2 e 3 começaram com réplicas ainda elevadas da rodada anterior.

**Como reportar:** tratar a rodada 1 como **partida a frio** e as rodadas 2–3 como
regime estacionário, em vez de fundir as três em uma média que mistura duas
condições distintas. A alternativa seria elevar a pausa para além de 300 s e repetir
a bateria.

### 6.3 Deriva do volume de dados entre rodadas

O cenário de carga faz 20% de escrita. Crescimento da tabela `patients`:

| Hora | Linhas | Cenário |
|---|---|---|
| 15:16 | 59.920 | antes da bateria |
| 15:35 | 108.817 | A |
| 15:55 | 174.410 | A |
| 16:17 | 338.041 | B |
| **16:41** | **605.442** | **fim de B** |
| 17:39 | 605.503 | E (não cria pacientes) |

A rodada 3 do cenário B executou contra um banco com centenas de milhares de linhas
a mais que a rodada 1. O congelamento às 16:41 confirma, de forma independente, a
fronteira entre os cenários B e E.

**Estado final do banco:**

| Base | Tabela | Linhas |
|---|---|---|
| `patientdb` | `patients` | 605.430 |
| `auditdb` | `audit_log` | 3.399.128 |
| `auditdb` | `audit_anomaly` | 29 |
| `resultdb` | `exam_results` | 1.810 |
| `notificationdb` | `notifications` | 1.811 |
| `consentdb` | `consents` | 78 |

As 29 anomalias contra 3,4 milhões de eventos confirmam que a guarda de
deduplicação por janela do `AnomalyDetector` funciona — sem ela seriam milhões de
alertas redundantes.

Disco da VM-1 ao final: 39 GB de 145 GB (29%).

### 6.4 Validação do próprio instrumento de medição

A série temporal de linhas usa `n_live_tup` (estimativa do coletor de estatísticas),
e não `COUNT(*)`, para não perturbar o experimento. A divergência contra a contagem
exata ao final foi de **0,012%** (605.503 vs 605.430) e **0,003%** (3.399.017 vs
3.399.128). O instrumento é confiável para a finalidade.

---

## 7. Pendências

| Item | Estado |
|---|---|
| Cenário C — ponto de ruptura | pendente, executar com `inotify` corrigido |
| Cenário D — resiliência provocada | parcialmente coberto pelos reinícios espontâneos (§4.2) |
| Repetição do cenário B | pendente, substrato corrigido |
| Tempo de reação do HPA | extrair de `hpa.csv` (§4.1) |
| Vazão (req/s) por rodada de A e B | extrair dos `*.log` (`http_reqs`) |

**Artefatos:** `tests/k6/resultados/20260919-1519/` (VM-2) e
`tests/k6/resultados-infra/20260919-1516/` (VM-1).

---

## 8. O dimensionamento do autoescalador — experimento controlado (20/09/2026)

> Acrescentado após a §7. Este é o resultado mais contraintuitivo do trabalho e
> fecha a discussão do Resultado 1 e do Resultado 3.

### 8.1 A pergunta

A repetição do cenário B com o ambiente saneado (`inotify` corrigido, base zerada,
réplicas no mínimo, JVMs novas) **colapsou**: P95 de 38,3 s e 22,8% de erro, contra
818 ms e 0,00% na execução anterior. A investigação isolou a causa em **saturação de
CPU do nó** — 31 dos 32 vCPUs — e mostrou que a diferença entre as duas execuções era
o número de pods que de fato rodavam: **43 contra 57**.

Daí a hipótese a testar: *se o excesso de réplicas é a causa, limitar o autoescalador
deve melhorar o desempenho.*

### 8.2 O experimento

Única variável alterada: `maxReplicas` de **10 para 6** nos dez HPAs. Tudo o mais
idêntico — 1000 VUs, três rodadas, reset completo antes de cada uma (base vazia,
réplicas no mínimo, JVMs recém-iniciadas).

### 8.3 Resultado

| Métrica | `maxReplicas=10` | `maxReplicas=6` | Diferença |
|---|---|---|---|
| **P95 geral** | 38,3 ± 3,6 s | **918 ± 194 ms** | **42× melhor** |
| `{endpoint:timeline}` p95 | 60 s (teto do cliente) | **1.144 ± 378 ms** | > 52× |
| `{endpoint:consent_check}` p95 | ~11,8 s | **467 ± 36 ms** | 25× melhor |
| **Taxa de erro** | 22,8 ± 3,7% | **0,00%** | — |
| **Vazão** | 130 req/s | **1.225 ± 61 req/s** | **9,4× maior** |
| Pods ativos no pico | 57 | 45 | −21% |
| CPU do nó no pico | 31.253m (98%) | 30.073m (94%) | −4 p.p. |
| Eventos anormais de pod | dezenas | 1 | — |

Rodadas individuais (P95 geral / vazão): 1,14 s / 1.158 req/s · 832 ms / 1.240 req/s
· 781 ms / 1.278 req/s. **Todas com 0,00% de erro e código de saída 0.**

### 8.4 A leitura que importa

O dado decisivo não é a latência — é a **eficiência por núcleo**:

| | Vazão | CPU | **Requisições por núcleo** |
|---|---|---|---|
| `maxReplicas=10` | 130 req/s | 31,25 cores | **4,2 req/s** |
| `maxReplicas=6` | 1.225 req/s | 30,07 cores | **40,7 req/s** |

**Com praticamente a mesma CPU, a configuração de 6 réplicas fez 9,7× mais trabalho
útil por núcleo.** Ou seja: com 12 pods a mais, o nó não ficou sem capacidade — ele
passou a **gastar a capacidade consigo mesmo**. Cada réplica adicional traz uma JVM e
um sidecar Envoy; acima da capacidade do nó, esse custo fixo desloca o trabalho útil,
e o autoescalador entra em realimentação positiva: vê CPU alta, pede mais réplicas,
que consomem mais CPU.

A utilização caiu apenas de 98% para 94% — quatro pontos percentuais separam um
sistema estável de um em colapso. É o comportamento esperado pela teoria de filas
nas proximidades da saturação, aqui medido em um sistema real.

### 8.5 Afirmação para a defesa

> Reduzir o limite do autoescalador de 10 para 6 réplicas diminuiu o P95 de 38,3 s
> para 918 ms, eliminou os 22,8% de erro e multiplicou a vazão por 9,4 — com a mesma
> infraestrutura e a mesma carga. Em um substrato de capacidade fixa, escalar
> horizontalmente além do que o nó comporta **degrada** o desempenho em vez de
> melhorá-lo. O autoescalador precisa ser dimensionado para o substrato, e não
> configurado no máximo por padrão.

**Ressalva honesta:** mesmo na melhor configuração, o P95 de 918 ms permanece acima
do alvo de projeto de 500 ms (RNF-01), e o da linha do tempo, 1.144 ms, acima dos
800 ms do RNF-05. Os limites adotados para a PoC (2.000 ms) foram atendidos com
0,00% de erro e 1.225 req/s sustentados. O que o experimento demonstra é o
**mecanismo** e o dimensionamento correto — não que o alvo de produção seja
alcançável neste nó único.

### 8.6 Consequência para os manifestos

O `maxReplicas: 10` de `k8s/2x-*.yaml` foi dimensionado sem referência à capacidade
do nó. O valor correto depende do substrato:

```
pods sustentáveis ≈ (núcleos do nó − reserva do sistema) ÷ custo por pod
```

Com 32 vCPUs e ~45 pods ativos como teto observado, `maxReplicas=6` nos dez serviços
é o dimensionamento que este nó comporta. Em um cluster com mais nós — ou com
*Cluster Autoscaler* — o valor seria outro, e é isso que a limitação de nó único
declarada em `plano-teste-estresse.md` §6 antecipava.

---

## 9. Por que o alvo de projeto não é atingido — e o que pode ser otimizado

> Seção escrita para responder diretamente à pergunta de banca: *"o P95 ficou em
> 918 ms com alvo de 500 ms, e a linha do tempo em 1.144 ms com alvo de 800 ms.
> Por quê? É limitação da arquitetura?"*

### 9.1 A evidência diz que não é um ponto específico — é contenção

Comparando os dois cenários, com a mesma arquitetura e o mesmo código:

| Métrica | Cenário A (150 VUs) | Cenário B (1000 VUs) | Fator |
|---|---|---|---|
| `http_req_duration` p95 | **48 ms** ✓ | 918 ms | 19× |
| `{endpoint:timeline}` p95 | **68 ms** ✓ | 1.144 ms | 17× |
| `{endpoint:consent_check}` p95 | **18 ms** ✓ | 467 ms | 26× |
| CPU do nó no pico | baixa | **94%** | — |

**Com 150 usuários virtuais, os três alvos de projeto são atendidos** — inclusive os
20 ms do consentimento inline. O que muda entre um cenário e outro não é o código:
é a utilização do nó.

E os três endpoints degradam por **fatores semelhantes** (17× a 26×). Essa uniformidade
é a assinatura de disputa por um **recurso compartilhado**. Se a causa fosse uma
consulta ineficiente ou um índice ausente, **um** endpoint degradaria
desproporcionalmente aos outros — não todos na mesma proporção.

A teoria de filas prevê que o tempo de resposta cresce com 1/(1−ρ). Com ρ = 0,94, o
multiplicador esperado é ≈ 16,7×. O observado foi de 17× a 26×. **A degradação é
explicada pela saturação do nó, não por um defeito de desenho.**

### 9.2 Três oportunidades reais de otimização, verificadas no código

Dito isso, há ganhos concretos possíveis — e é honesto reconhecê-los.

#### (a) O leque de chamadas do BFF é **sequencial**

`TimelineService.getPatientView()` executa três chamadas HTTP **em série**:

```java
ConsentCheckDto consent = consentClient.check(...);      // 1 - obrigatória primeiro
PatientDto patient      = patientClient.findByUuid(...); // 2 ─┐ independentes
List<ResultDto> results = resultClient.findByPatient(...);// 3 ─┘ entre si
```

A verificação de consentimento **precisa** vir primeiro: é ela que autoriza o acesso,
e executar as outras antes violaria o princípio de negar por padrão. Mas as chamadas
2 e 3 são independentes entre si e hoje somam quando poderiam se sobrepor.

Ganho esperado: a latência da etapa de agregação deixa de ser `patient + result` e
passa a ser `max(patient, result)`.

#### (b) Não há cache de consentimento — e a invalidação **já está desenhada**

Não existe `@Cacheable` nem qualquer camada de cache no `history-service` ou no
`consent-service`. A verificação vai ao banco a cada requisição.

O consentimento responde por **467 ms dos 1.144 ms** da linha do tempo — cerca de
**41% da latência**.

O ponto importante para a defesa: **o alvo de 20 ms sempre pressupôs esse cache.** O
comentário em `tests/k6/lib/config.js`, escrito antes de qualquer medição, diz
literalmente: *"RNF-06 consent: p(95) ≤ 20ms ... design produção: 20ms com cache
dedicado"*. Não se trata de não atingir uma meta — trata-se de uma meta definida para
um desenho que inclui um componente que a PoC não implementou.

E a peça que torna esse cache seguro **já existe**: o tópico `consent.revoked` é
publicado pelo `consent-service` a cada revogação e **não tem nenhum consumidor**. Ele
foi criado exatamente para invalidação de cache — está assim em `CLAUDE.md` §5:
*"consumidores: [futuro] serviços de dados p/ invalidar cache"*. A arquitetura
antecipou a otimização; a implementação ficou como evolução.

Isso preserva o RNF-06: a revogação continua propagando em ≤ 1 s via Kafka, porque a
invalidação é dirigida por evento, não por expiração de TTL.

#### (c) Cada chamada atravessa dois sidecars

Com mTLS STRICT, uma chamada de A para B passa pelo Envoy de saída de A e pelo Envoy
de entrada de B. As três chamadas sequenciais da linha do tempo somam **seis
travessias de proxy** por requisição, além dos saltos de rede.

É o preço da malha — e ele compra mTLS, observabilidade e política. O modo *ambient*
do Istio (sem sidecar por pod) reduziria esse custo, mas altera o modelo de segurança
e está fora do escopo deste trabalho. Vale como trabalho futuro declarado.

### 9.3 O que **não** é a causa

Verificado, para não atribuir a latência ao lugar errado:

- **A publicação de auditoria não bloqueia.** `AuditPublisher` usa
  `kafkaTemplate.send(...)` sem `.get()` — é assíncrona e não entra no caminho crítico
  da resposta.
- **Não é volume de dados.** No cenário B o banco parte **vazio** (reset antes de cada
  rodada) e o `consent_check` opera sobre dezenas de linhas.
- **Não é o Kafka.** Todos os consumidores exceto o `audit-service` mantiveram lag
  zero, e a publicação é assíncrona.
- **Não é falta de índice.** O `audit_log` tem `idx_audit_patient_ts`; e um índice
  ausente degradaria um endpoint, não todos na mesma proporção (§9.1).

### 9.4 Resposta curta para a banca

> Os alvos de projeto são atendidos com 150 usuários simultâneos — P95 de 48 ms
> contra 500 ms, e consentimento inline em 18 ms contra 20 ms. Com 1000 usuários, o
> nó único chega a 94% de CPU e a latência cresce por saturação, não por defeito de
> desenho: os três endpoints degradam por fatores semelhantes, o que caracteriza
> disputa por recurso compartilhado, e o valor observado é compatível com o previsto
> pela teoria de filas para essa utilização.
>
> Há duas otimizações identificadas e não implementadas: paralelizar as duas chamadas
> independentes do agregador, e adicionar cache de consentimento — cuja invalidação
> por evento já está prevista na arquitetura pelo tópico `consent.revoked`, hoje sem
> consumidor. O alvo de 20 ms para o consentimento, aliás, sempre pressupôs esse
> cache, conforme registrado no projeto antes das medições.
>
> A terceira via é de infraestrutura: a arquitetura escala horizontalmente, mas um
> nó único não oferece para onde escalar. Essa limitação está declarada na
> Metodologia desde o início do experimento.

---

## 10. Cenário C — Ponto de ruptura (20/09/2026)

> Fecha o **Resultado 2**. Executado com taxa de chegada fixa em degraus, após a
> correção do dimensionamento do autoescalador (§8) e da instrumentação (§10.4).

### 10.1 Por que taxa de chegada e não usuários virtuais

O cenário B usa `ramping-vus`: cada usuário virtual **espera a resposta** antes da
próxima requisição. Quando o sistema fica lento, ele envia menos — a carga se
auto-regula e o limite real fica mascarado.

O `ponto-ruptura.js` usa `ramping-arrival-rate`, que fixa a taxa em requisições por
segundo **independentemente do tempo de resposta**. É o que permite afirmar *"a
plataforma sustentou N req/s; acima disso, degradou"*.

Dez degraus de 200 a 2.000 req/s, 130 s cada, precedidos de 120 s de aquecimento
cujas requisições ficam fora das métricas.

### 10.2 A curva — duas execuções independentes

| Degrau | Taxa alvo | P95 execução 1 | P95 execução 2 | Erro |
|---|---|---|---|---|
| 0 | 200 req/s | 25,9 ms | 24,9 ms | 0,00% |
| 1 | 400 | 25,6 ms | 24,5 ms | 0,00% |
| 2 | 600 | 26,2 ms | 25,9 ms | 0,00% |
| 3 | 800 | 31,1 ms | 31,6 ms | 0,00% |
| 4 | 1.000 | 56,4 ms | 59,9 ms | 0,00% |
| 5 | 1.200 | 128,6 ms | 110,1 ms | 0,00% |
| **6** | **1.400** | **356,7 ms** | **462,4 ms** | **0,00%** |
| 7 | 1.600 | 1.400,9 ms | 1.934,8 ms | 0,00% |
| 8 | 1.800 | 2.430,6 ms | 3.907,0 ms | 0,07% |
| 9 | 2.000 | 3.003,6 ms | 4.041,6 ms | 0,17% / 0,00% |

Volume: 1.361.096 e 1.295.187 requisições, **nenhuma iteração interrompida**.

**Reprodutibilidade.** Até 1.200 req/s as duas execuções coincidem dentro de poucos
milissegundos. A divergência cresce depois do joelho — comportamento esperado, porque
perto da saturação pequenas diferenças de estado se amplificam.

### 10.3 Os três números a reportar

**Capacidade sustentada: 1.400 req/s.** Último degrau com latência de baixa dispersão
(357 e 462 ms), erro zero e gerador folgado.

**Ruptura pelo critério do plano (P95 ≤ 2.000 ms): entre 1.600 e 1.800 req/s.** O
degrau de 1.600 fica no limite (1.401 ms e 1.935 ms); o de 1.800 o ultrapassa nas duas
execuções.

**A degradação é de latência, não de disponibilidade.** Mesmo a 2.000 req/s com P95 de
4 s, a taxa de erro máxima foi de **0,17%**. A plataforma **enfileirou em vez de
recusar** — comportamento de sistema com contrapressão, não de sistema que quebra.

> **Ressalva obrigatória:** houve 83.873 e 67.969 `dropped_iterations`. Os usuários
> virtuais atingiram o teto (1.500 e depois 2.000) a partir do degrau 7, então
> **acima de 1.600 req/s o gerador também estava no limite**. Os degraus 7 a 9 são
> limite inferior da latência real. É exatamente o critério previsto em
> `plano-teste-estresse.md` §4 para invalidar um degrau.

### 10.4 A causa — não é CPU, é fila por conexão de banco

Esta é a diferença entre observar e explicar, e exigiu corrigir três defeitos
encadeados de instrumentação (§11).

**Estado no pico, por serviço:**

| Serviço | Conexões ativas | Fila aguardando conexão | Threads Tomcat ocupadas |
|---|---|---|---|
| `patient-service` | **5 de 5** | **46** | 52 de 200 |
| `consent-service` | **5 de 5** | **41** | 47 de 200 |
| `result-service` | 5 de 5 | 1 | 7 de 200 |
| `history-service` | — (sem banco) | — | 64 de 200 |

**Correlação temporal.** O teste começou às 07:22:06 UTC; com 120 s de aquecimento e
130 s por degrau, o degrau 6 inicia às 07:37:06:

| Instante | Degrau | Fila por conexão | P95 |
|---|---|---|---|
| até 07:37:06 | 0 a 5 (≤ 1.200 req/s) | **0** | ≤ 110 ms |
| **07:37:33** | 6 (1.400) | **primeira fila: 2** | 462 ms |
| 07:38–07:39 | 6 | 9 → 13 | — |
| 07:40–07:41 | 7 (1.600) | **41 → 46** | 1.935 ms |

A primeira fila aparece **27 segundos após o início do degrau de 1.400 req/s**, e a
latência acompanha na mesma cadência.

**A leitura.** Os pools estavam **completamente ocupados** (5 de 5) com dezenas de
requisições aguardando, enquanto as threads do servidor operavam a **menos de um
terço** do limite (64 de 200) e a CPU do nó subia apenas de 74% para 82%.

As threads não estavam trabalhando — estavam **bloqueadas esperando conexão de
banco**. É o diagnóstico que `plano-teste-estresse.md` §5 define como objetivo:

> *"A plataforma degradou a 1.600 req/s porque o pool de conexões do HikariCP saturou
> e as requisições passaram a aguardar em fila, com as threads do Tomcat ocupadas em
> espera de I/O."*

### 10.5 O limite é uma decisão de projeto, e é ajustável

O pool de 5 conexões por instância não é arbitrário: foi dimensionado em §6.1 para
respeitar o teto do PostgreSQL. O orçamento é

```
8 serviços com banco × 6 réplicas (maxReplicas) × 5 conexões = 240
                                    teto configurado: max_connections = 500
```

Ou seja, **há folga no banco**: o limite atual é o pool da aplicação, não o servidor.
Elevar o pool de 5 para 10 dobraria o orçamento para 480, ainda dentro dos 500 — e
deslocaria o ponto de ruptura para cima.

Isso transforma o resultado em recomendação verificável: *o ponto de ruptura desta
plataforma é determinado pelo dimensionamento do pool de conexões, e há margem
documentada para elevá-lo*. Fica como trabalho futuro mensurável — e como resposta
pronta caso a banca pergunte se o limite encontrado é intrínseco. **Não é.**

---

## 11. Cenário D — Resiliência provocada (20/09/2026)

> Fecha o **Resultado 3**. Duas execuções independentes, com `kubectl delete pod`
> sobre uma réplica `2/2 Running` do `history-service` no meio de carga de 1000 VUs.

### 11.1 Por que provocar, se já houve falha espontânea

Na bateria de 19/09 dois pods reiniciaram sozinhos e o erro ao cliente foi 0,00%
(§4.2). Isso prova que a auto-recuperação funciona, mas **não permite medir**: sem
instante datado não há como calcular tempo de reposição nem isolar a janela de erro.

A eliminação deliberada dispara o mesmo caminho de um crash — o ReplicaSet reconcilia
— de forma determinística e cronometrada.

### 11.2 Resultado

| Execução | t1 (substituto criado) | t2 (pronto) | **MTTR** | Erro ao cliente |
|---|---|---|---|---|
| 07:57:26 | **+2 s** | +42 s | **42 s** | **0,00%** |
| 08:19:22 | **+0 s** | +43 s | **43 s** | **0,00%** |

O RNF-02 estabelece auto-recuperação em até 30 s. **O alvo não foi atingido**, e a
decomposição mostra por quê.

### 11.3 Onde estão os 43 segundos

O log do Spring Boot do pod substituto permite decompor o tempo:

| Fase | Duração | Evidência |
|---|---|---|
| Kubernetes detecta e cria o substituto | **0–2 s** | `t1` registrado pelo script |
| Agendamento + inicialização do sidecar Istio | ~9 s | intervalo até o primeiro log da aplicação |
| **Arranque da JVM (Spring Boot)** | **28,5 s** | `Started HistoryServiceApplication in 28.458 seconds` |
| Confirmação pela sonda de prontidão | ~3 s | `periodSeconds: 5` |
| **Total** | **~43 s** | |

**Dois terços do tempo de reposição são arranque da JVM.** O orquestrador reagiu em
menos de dois segundos.

O efeito da contenção é mensurável: sem carga, serviços da mesma base sobem em
**~20 s** (`Started AuthServiceApplication in 19.805 seconds`); sob 1000 usuários
virtuais, o `history-service` levou **28,5 s** — a JVM nova disputa CPU com o sistema
em regime.

### 11.4 Uma hipótese testada e refutada

A primeira medição (42 s) sugeriu que a causa fosse a `readinessProbe`, configurada
com `initialDelaySeconds: 30` e `periodSeconds: 10` — o que impõe, em tese, um piso
de 30 a 40 s antes da primeira verificação.

A hipótese foi testada substituindo essa configuração por `startupProbe` com
`periodSeconds: 5`, que remove o atraso inicial. **O MTTR passou de 42 s para 43 s —
ou seja, não mudou.** A restrição real era o arranque da JVM, que por coincidência
tem duração próxima ao atraso configurado.

A alteração foi **mantida**, porque é tecnicamente correta — remove um piso
artificial e faz a medição refletir a restrição verdadeira — mas não se atribui a ela
nenhum ganho.

### 11.5 A distinção que importa para o RNF-02

Há dois tempos, e confundi-los seria erro de interpretação:

**Tempo de reposição da réplica: 43 s.** Acima do alvo de 30 s.

**Indisponibilidade percebida pelo cliente: zero.** Em ambas as execuções o
`http_req_failed` foi **0,00%**, com P95 de 931 ms durante toda a janela. As réplicas
remanescentes absorveram a carga sem nenhuma requisição perdida.

O que ficou degradado por 43 s foi a **redundância**, não a **disponibilidade**. Um
requisito de disponibilidade de 99,9% (RNF-02) não é violado por isso: nenhuma
requisição falhou.

### 11.6 Como atingir o alvo de 30 s — caminhos reais

O limite é o arranque da JVM, e há três saídas conhecidas, nenhuma implementada aqui:

1. **Imagem nativa (GraalVM / Spring AOT)** — arranque em dezenas de milissegundos
   em vez de dezenas de segundos. É a solução direta, ao custo de restrições de
   reflexão e de um processo de compilação mais longo.
2. **CRaC (*Coordinated Restore at Checkpoint*)** — restaura a JVM a partir de um
   instantâneo já aquecido, eliminando também o problema de desempenho a frio (§4.6).
3. **Réplicas de reserva** — sobreprovisionar para que a perda de uma não exija
   reposição imediata. Troca tempo de recuperação por custo permanente de recursos.

**Conclusão honesta para a defesa:** *o alvo de 30 s do RNF-02 não é alcançável por
um serviço em JVM neste substrato, porque a JVM sozinha consome 28 s do orçamento. O
orquestrador cumpre sua parte em menos de dois segundos. A disponibilidade percebida,
no entanto, não foi afetada — o que sugere que o requisito deveria ser formulado em
termos de erro percebido pelo cliente, e não de tempo de reposição de réplica.*
