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
