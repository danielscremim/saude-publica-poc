# Diário de execução — fase experimental

> **Para que serve.** Registro cronológico dos problemas encontrados, das hipóteses
> levantadas (inclusive as erradas), das evidências que as confirmaram ou
> descartaram, e das correções aplicadas — entre 19 e 20/09/2026, na infraestrutura
> de datacenter.
>
> Alimenta duas coisas: a pergunta *"quais desafios você enfrentou?"* na defesa, e a
> seção de **Metodologia**, porque várias descobertas aqui mudaram o desenho do
> experimento. Nenhum dos problemas abaixo se manifesta em ambiente Docker Compose
> de estação de trabalho — todos exigiram o nó de 32 vCPUs para aparecer.

---

## Fase 1 — Adequação ao ambiente entregue

A infraestrutura foi entregue por fornecedor externo em 16/09/2026 com Kubernetes,
malha de serviços e observabilidade instalados, **sem as aplicações de negócio**.
Os scripts do repositório assumiam um ambiente que não era esse.

| # | Sintoma | Causa | Correção |
|---|---|---|---|
| 1 | `k3s ctr images import` não existia | O cluster é **k3d** — o nó roda dentro de um contêiner Docker, não direto no host | `load-images-k3s.sh` passou a detectar o runtime e usar `k3d image import` |
| 2 | `deploy.sh` abortava por falta de `istioctl` | Istio 1.30.4 **já estava instalado**; o script exigia o binário mesmo sem precisar instalar nada | Passo de instalação condicionado à ausência do namespace `istio-system` |
| 3 | Kong ficaria `<pending>` para sempre | **ServiceLB desabilitado** na entrega: um `Service` do tipo `LoadBalancer` nunca receberia IP | Kong passou a `NodePort` fixo (30800), publicado na porta 8000 pelo balanceador do k3d |
| 4 | `docker: permission denied` | Usuário fora do grupo `docker` | `usermod -aG docker` + reconexão da sessão |

**Decisão estrutural tomada aqui:** não usar o PostgreSQL (CloudNativePG) nem o
Kafka (Strimzi) gerenciados que vieram na entrega. O primeiro disponibilizava uma
**única base**, incompatível com o padrão *Database per Service* adotado (8 bases);
o segundo opera com criação automática de tópicos desabilitada e exige
SASL_SSL/SCRAM-SHA-512, o que obrigaria a alterar a configuração dos 10 serviços sem
ganho para as perguntas de pesquisa. Istio, Prometheus, Grafana, Loki e Tempo
entregues foram integralmente reaproveitados.

---

## Fase 2 — Primeira subida dos serviços

Sete dos dez serviços subiram; três ficaram com **uma réplica em
`CrashLoopBackOff`**, e o Kong com **as duas réplicas caindo**.

### 2.1 Esgotamento de conexões do banco

**Hipótese inicial (errada):** disputa de DDL do Hibernate entre as duas réplicas —
o `ddl-auto: update` de dois pods criando as mesmas tabelas ao mesmo tempo. Parecia
encaixar, porque falhava sempre *uma* réplica de cada serviço.

**O que a evidência mostrou:** o log trazia
`org.postgresql.util.PSQLException: FATAL: sorry, too many clients already`.

**Causa real:** `max_connections` padrão da imagem PostgreSQL é 100. São 8 serviços
com persistência × 2 réplicas × pool HikariCP padrão de 10 = **160 conexões apenas em
repouso**, e o HPA poderia levar isso a 800.

**Correção:** `max_connections=500`, `shared_buffers=512MB`, `/dev/shm` dedicado, e
dimensionamento **explícito** do pool — `maximum-pool-size: 5`, orçamento de
8 × 10 × 5 = 400.

**Lição:** o pool de conexões precisa ser dimensionado contra o teto do banco
*multiplicado pelo máximo do autoescalador*, não pela contagem de réplicas em
repouso. O valor padrão do HikariCP é seguro para uma instância e perigoso para dez.

### 2.2 Dimensionamento do gateway em nó grande

**Sintoma:** as duas réplicas do Kong terminando com `Exit Code: 137` (SIGKILL).

**A pista estava no log:** a criação de **33 worker processes**. O nginx lê a
contagem de CPUs **do nó** (32 vCPUs), ignora o limite de CPU imposto ao contêiner
(500m) e extrapola o limite de memória de 1 GiB.

**Correção:** `KONG_NGINX_WORKER_PROCESSES=4`, memória para 2 GiB.

**Achado correlato, mais perigoso:** o mesmo log registrava
`getrlimit(RLIMIT_NOFILE): 1024`. Sob 1000 usuários virtuais o gateway esgotaria
descritores de arquivo e registraria erros **não atribuíveis ao sistema sob teste** —
produzindo números errados *sem falhar visivelmente*. Ajustado para 65536.

**Lição:** processos que leem a capacidade do hospedeiro em vez do limite do
contêiner são uma classe de defeito que só aparece em nó grande. Em uma estação de
trabalho de 4 ou 8 núcleos, o Kong teria subido normalmente.

### 2.3 Remoção de tetos artificiais

PostgreSQL passou de 1 vCPU / 1 GiB para 8 vCPU / 4 GiB, Kong de 500m para 4 vCPU.
**Justificativa metodológica:** com os limites anteriores, o ponto de ruptura medido
seria o da configuração arbitrária de recursos, não o da arquitetura.

---

## Fase 3 — Validação funcional

**Sintoma:** os scripts `test-*.sh` paravam no primeiro passo, sem mensagem de erro.

**Causa:** apenas o `test-graphql.sh` aceitava a variável `BASE`. Os demais fixavam
`localhost:8081..8090` — portas que **não existem no Kubernetes**, onde os serviços
são `ClusterIP` e só a 8000 do Kong está publicada. O `curl` simplesmente não obtinha
resposta, e o script seguia com variáveis vazias.

**Correção:** os sete scripts passaram a honrar `BASE`, mantendo as portas diretas
como padrão para execução local.

Com isso, os sete passaram. Resultados 4 e 5 ficaram demonstrados nesta fase —
registro completo em `docs/validacao-funcional-vm.md`.

---

## Fase 4 — Bateria de carga

### 4.1 Um threshold estourado abortava a bateria inteira

**Descoberto por leitura de código, antes de acontecer.** O k6 sai com código 99
quando um threshold não é atingido. Com `set -euo pipefail` e a saída passando por
`tee`, o `pipefail` propagava o 99 e o `set -e` encerrava o `run-2vm.sh` **no meio da
execução**: uma falha no cenário A (limites estritos por definição) mataria os
cenários B e E antes de rodarem, desperdiçando o bloco de três horas.

Conceitualmente também estava errado: **um threshold estourado é um resultado do
experimento, não um erro de execução**.

**Correção:** código de saída registrado em `execucoes.csv`, bateria continua. Na
execução seguinte a rodada 3 do cenário A saiu com 99 — e a bateria prosseguiu.

### 4.2 Limite de `inotify` do kernel

**Sintoma:** pods em `Init:Error` e `Init:CrashLoopBackOff` durante picos do HPA;
reinícios com `Exit Code 255` cinco segundos após o início.

O log local já havia sido descartado pelo kubelet. **O Loki da entrega, com retenção
de 72 h, guardava a causa:**

```
failed to start default Istio SDS server: failed to start workload secret manager
couldn't initialize inotify: too many open files
```

Não era a aplicação Java — era o **sidecar Envoy** falhando ao iniciar. Cada sidecar
consome instâncias `inotify` do kernel do host, e o padrão do Ubuntu é
`fs.inotify.max_user_instances = 128`.

**Correção:** 128 → 8192.

**Lição para o trabalho:** em malha de serviços, o custo de um sidecar por pod inclui
**recursos do kernel que não aparecem em nenhum limite de CPU ou memória declarado no
manifesto**. É um limite invisível ao `kubectl describe`.

### 4.3 As rodadas não eram independentes

**Sintoma:** a rodada 1 era sistematicamente a pior em todos os cenários.

**Causa verificável:** a janela padrão de estabilização do HPA para **reduzir**
réplicas é de 300 s, e a pausa do `run-2vm.sh` entre rodadas era de 180 s. As rodadas
2 e 3 começavam com réplicas herdadas da anterior.

Somava-se a isso a **deriva do volume de dados**: o cenário faz 20% de escrita, e a
tabela `patients` saiu de 59.920 para 605.430 linhas ao longo da bateria — a rodada 3
executava contra um banco muito maior que a rodada 1.

**Correção:** `scripts/reset-ambiente.sh`, executado antes de **cada** rodada —
`TRUNCATE` das tabelas, `scale --replicas=2` explícito (o `rollout restart` sozinho
preserva a contagem herdada do HPA) e recriação dos pods. Disparado por SSH a partir
da VM-2, de forma automática.

Passou a valer: **cada rodada inicia em condições idênticas — base vazia, número
mínimo de réplicas e JVMs recém-iniciadas.**

### 4.4 O colapso ao repetir o cenário B

Com o `inotify` corrigido, a repetição do cenário B **colapsou**: 62,88% de erro,
P95 no teto de 60 s, vazão de 63 req/s — contra 818 ms e 0,00% de erro na execução
anterior.

A investigação percorreu e descartou, nesta ordem:

| Hipótese | Como foi descartada |
|---|---|
| Disco cheio | 38 GB de 145 GB |
| Conexões do banco esgotadas | 50 de 500 em uso |
| Kafka indisponível | Log mostrava apenas rebalanceamentos — *sintoma* da rotatividade de pods |
| Banco inchado degradando consultas | O `consent_check` levou 11 s sobre uma tabela de **78 linhas**: falta de CPU, não volume |
| Acúmulo de dados como motor | Repetição com **banco zerado** colapsou igualmente |

**Causa:** saturação de CPU do nó — **31 dos 32 vCPUs**, com pelo menos cinco
réplicas de `history-service` simultâneas cravadas no limite de 1 core cada.

E então a cascata: o nó satura → pods novos não conseguem CPU para o sidecar iniciar
→ `Startup probe failed: connection refused` → `CrashLoopBackOff` → cada JVM
reiniciando consome mais CPU → os pods saudáveis são estrangulados → o HPA vê CPU
alta e **pede mais réplicas, que pioram a situação**.

### 4.5 A hipótese que eu defendi e estava errada

**Afirmei:** o limite de `inotify` impedia o HPA de escalar; corrigi-lo liberou o
escalonamento e revelou o gargalo de CPU.

**A evidência desmentiu:** o HPA atingiu 10 réplicas **nas duas execuções**. A coluna
`REPLICAS` conta os pods *criados*, não os *prontos*.

**O mecanismo real, medido:**

| No pico | Execução com `inotify`=128 | Execução com `inotify`=8192 |
|---|---|---|
| **Pods ativos** | **43** | **57** |
| CPU do nó | 28.073m (88%) | 31.253m (98%) |
| P95 | 818 ms | 42 s |
| Erro | 0,00% | 21,70% |

O limite de `inotify` funcionava como **freio acidental**: os pods eram criados, mas
vários ficavam presos na inicialização — contavam como réplica sem rodar JVM nem
sidecar. Corrigido o limite, 14 pods a mais passaram a existir de fato, e a
utilização do nó foi de 88% para 98%.

**O resultado que isso produz:** mais réplicas pioraram o desempenho em cerca de
50×. Não é paradoxo, é teoria de filas — num nó de capacidade fixa, cada réplica
adicional traz uma JVM e um sidecar Envoy, **consumindo capacidade sem acrescentar
nenhuma**; ao empurrar a utilização para perto de 100%, a latência de fila cresce de
forma não-linear. Os 88% já eram a borda.

---

## Defeitos encontrados na própria instrumentação

Vale registrar: parte do esforço foi garantir que o **instrumento** não produzisse
números errados. Medir errado sem falhar visivelmente é pior que falhar.

| Defeito | Consequência se não corrigido |
|---|---|
| `coletar-metricas.sh` lia `containerStatuses[0]` | Reportava "0 reinícios" lendo só o sidecar, enquanto a aplicação acusava 5 |
| `ponto-ruptura.js` alocava 4000 VUs | A VM-2 tem 15 GB **sem swap**; a 1–5 MB por VU, OOM no meio da medição |
| `setup-vm2-loadgen.sh` usava `grep -q nofile` | Casava com as linhas **comentadas** do `limits.conf` e pulava o ajuste; 1000 VUs falhariam por descritores |
| Processo k6 órfão vivo há 9h26, com processo pai vivo há 10h36 | Contaminação silenciosa de todas as medições do período — ver detalhamento abaixo |
| `reset-ambiente.sh` não esperava os pods antigos | O `rollout status` retorna com as réplicas antigas ainda em `Terminating`; a rodada começaria com pods a mais |

### Processos órfãos de carga — risco que quase passou despercebido

Durante uma execução, ao inspecionar a VM-2 encontrei **dois processos k6 ativos ao
mesmo tempo**: a rodada corrente e um `k6 run ... MAX_VUS=50` iniciado **9 h 26 min
antes**. Era o aquecimento de uma bateria anterior, que nunca terminou.

Se ele estivesse gerando carga, todas as medições feitas naquele intervalo estariam
contaminadas por 50 usuários virtuais não contabilizados. **Só foi possível afirmar
que não estavam porque o consumo foi medido antes de matá-lo:** 0 ticks de CPU em
5 segundos de amostragem, 64 MB residentes — processo travado, não ativo.

Pior: ele tinha um **processo pai** — o `run-2vm.sh` daquela bateria, vivo havia
10 h 36 min, bloqueado esperando o filho. Ao matar apenas o filho, liberei o pai para
prosseguir: ele dispararia as rodadas seguintes sozinho, a qualquer momento,
sobrepondo carga a um experimento em andamento. Foi eliminada a árvore inteira.

**Lição operacional, aplicável a qualquer experimento de carga:** antes de cada
medição, verificar que não há gerador de carga residual — e, ao encerrar um processo
travado, encerrar a **árvore**, nunca só a folha. Um `pgrep` antes de começar custa
dois segundos e protege horas de medição.

> Armadilha correlata, no ferramental: um `pkill -f "run-2vm.sh"` executado por SSH
> derrubou a própria conexão. O padrão casa com a linha de comando do shell remoto
> que o executa — autoeliminação. Padrões de `pkill` precisam ser escritos de forma
> a não casar consigo mesmos.

**Validação do instrumento de contagem:** a série temporal de linhas usa
`n_live_tup` (estimativa), e não `COUNT(*)`, para não perturbar o experimento. A
divergência contra a contagem exata ao final foi de **0,012%** e **0,003%**.

---

## Hipóteses descartadas — resumo

Registro consolidado, porque a capacidade de descartar hipóteses com evidência é
parte do método:

| Hipótese | Desfecho |
|---|---|
| Disputa de DDL do Hibernate entre réplicas | **Errada** — era `max_connections` |
| O `consent_check` ≤ 20 ms não seria atingido através da malha | **Errada** — atingido com 13,84 ms |
| O `inotify` impedia o HPA de escalar | **Errada** — o HPA escalava; mudava quantos pods de fato funcionavam |
| O acúmulo do banco causava o colapso | **Errada** — com banco zerado, colapsou igualmente |
| Kafka era o gargalo da fila de auditoria | **Errada** — todos os outros consumidores com lag zero; o gargalo é o `COUNT` por inserção do `AnomalyDetector` |

---

## O que isso muda na Metodologia

1. **A separação do gerador de carga em segunda VM não foi só rigor — foi a condição
   que tornou os RNFs de projeto verificáveis.** A execução anterior, por
   `kubectl port-forward`, adicionava ~300 ms por requisição e reprovava três de
   quatro limites com o sistema saudável. *Instrumentação intrusiva pode dominar o
   fenômeno medido.*
2. **Cada rodada precisa partir de condições idênticas** — base vazia, réplicas no
   mínimo, JVMs novas. Sem isso, a média ± desvio mistura condições distintas.
3. **Tetos artificiais de recurso precisam ser removidos antes de medir**, senão o
   ponto de ruptura observado é o da configuração, não o da arquitetura.
4. **O ambiente tem limites que não aparecem nos manifestos** — `inotify`,
   descritores de arquivo, contagem de CPUs lida do hospedeiro. Precisam ser
   verificados explicitamente e declarados.

---

## Próximo passo

Repetir o cenário B com `maxReplicas` reduzido de 10 para **6** nos quatro serviços
do caminho quente (4 × 6 + demais ≈ 41 pods ativos, próximo dos 43 que funcionaram).

Se o P95 retornar à casa dos 800 ms com **menos** réplicas, a afirmação se fecha:

> *Limitar o autoescalador a 6 réplicas reduziu o P95 de 42 s para menos de 1 s.
> Acima da capacidade do nó, escalar horizontalmente degrada em vez de melhorar —
> o autoescalador precisa ser dimensionado para o substrato, e não configurado no
> máximo por padrão.*
