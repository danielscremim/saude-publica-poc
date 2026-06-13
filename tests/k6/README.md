# Testes de carga com k6

Validação numérica dos **RNF-01 (escalabilidade)**, **RNF-05 (interoperabilidade)**
e **RNF-06 (consent ≤ 20ms)** da PoC, com visualização em Grafana ao vivo.

```
tests/k6/
├── smoke.js           # 1 VU x 1 min — sanity check, rode SEMPRE primeiro
├── load.js            # 0→1000 VUs em 9 min — cenário RNF-01
├── stress.js          # até 5000 VUs — encontra o teto (degradação)
├── full-journey.js    # jornada e2e (patient → exam → timeline → notification)
└── lib/
    ├── config.js      # URLs + thresholds mapeados aos RNFs
    └── setup.js       # registra client, obtém JWT, cadastra paciente, dá consent
```

---

## 1. Pré-requisitos

### Instalar o k6

| OS | Comando |
|---|---|
| Windows | `choco install k6` (ou baixar em https://k6.io/docs/get-started/installation/) |
| macOS | `brew install k6` |
| Linux (Debian/Ubuntu) | `sudo apt install k6` (após adicionar o repo oficial) |
| Docker (qualquer SO) | `docker run --rm -i grafana/k6 run - < tests/k6/smoke.js` |

Confirme: `k6 version` → `k6 v0.5x.x`.

### Subir a aplicação

```bash
docker compose up -d --build
bash scripts/test-flow.sh          # opcional: confirma que tudo responde
```

### Subir a stack de métricas (InfluxDB + Grafana)

```bash
docker compose -f docker-compose.metrics.yml up -d
```

Verifique:
- InfluxDB: http://localhost:8086/ping → `204 No Content`
- Grafana: http://localhost:3000 (admin / admin)
  - Datasource `InfluxDB-k6` já provisionado
  - Dashboards **"Saude PoC - RNFs (k6)"** e **"k6 Load Testing - Overview"** já carregados

---

## 2. Executando

Todos os scripts gravam métricas no InfluxDB (`--out influxdb=...`) e o Grafana
mostra ao vivo durante a execução.

### Smoke (1 min) — rode antes de tudo

```bash
k6 run --out influxdb=http://localhost:8086/k6 tests/k6/smoke.js
```

Esperado:
```
✓ checks........................: 100.00%
✓ http_req_duration............: p(95)=180ms
✓ http_req_failed..............: 0.00%
```

Se passou: a infra está OK, pode subir para `load.js`.

### Load — cenário do RNF-01 (~9 min)

```bash
k6 run --out influxdb=http://localhost:8086/k6 tests/k6/load.js
```

Estágios:
- **0-2 min:** ramp up de 0 → 1000 VUs
- **2-7 min:** sustenta 1000 VUs (5 min — janela do RNF-01)
- **7-9 min:** ramp down

Saída final mostra o veredito dos **thresholds** (mapeados aos RNFs):
```
✓ http_req_duration............: p(95)=487ms   THRESHOLD: p(95)<500   PASS
✓ http_req_duration{endpoint:timeline}........: p(95)=750ms  THRESHOLD: p(95)<800   PASS  ← RNF-05
✓ http_req_duration{endpoint:consent_check}..: p(95)=18ms   THRESHOLD: p(95)<20    PASS  ← RNF-06
✓ http_req_failed..............: 0.34%        THRESHOLD: rate<0.01   PASS  ← RNF-01
```

Se algum threshold falhar, k6 sai com código 99 (útil em CI).

### Stress — até 5000 VUs (~15 min)

```bash
k6 run --out influxdb=http://localhost:8086/k6 tests/k6/stress.js
```

Aqui o objetivo é **observar a curva de degradação**, não passar thresholds. Use
o painel "Latência por endpoint" do Grafana para identificar quem quebra primeiro
(provavelmente `history-service`, pois faz 2 chamadas internas).

### Full-journey — jornada e2e (~3 min)

```bash
k6 run --out influxdb=http://localhost:8086/k6 tests/k6/full-journey.js
```

Mede o **tempo total da operação do ponto de vista do usuário** (cadastra → exame →
async lab/result → consulta timeline → confirma notification). Métrica customizada
`journey_e2e_duration` com threshold p95 < 5s.

---

## 3. Variações úteis

### Rodar contra outro servidor

```bash
k6 run -e BASE_HOST=10.0.0.42 --out influxdb=http://10.0.0.42:8086/k6 tests/k6/load.js
```

### Ajustar carga ad-hoc (sem editar o arquivo)

```bash
# 500 VUs por 3 min (override do scenario do load.js)
k6 run --vus 500 --duration 3m tests/k6/load.js
```

### Salvar resultado em JSON (para comparar runs depois)

```bash
k6 run --summary-export=runs/load-$(date +%Y%m%d-%H%M).json tests/k6/load.js
```

### Sem InfluxDB (só terminal)

```bash
k6 run tests/k6/smoke.js
```

---

## 4. Onde ler os resultados no Grafana

Acesse http://localhost:3000 e abra **"Saude PoC - RNFs (k6)"**:

| Painel | Métrica | RNF |
|---|---|---|
| Throughput (req/s) | `http_reqs` rate | RNF-01 (deve sustentar ≥ 1000) |
| P95 latência HTTP | `http_req_duration` p95 | RNF-01 (≤ 500 ms = verde) |
| VUs concorrentes (max) | `vus` max | RNF-01 (atingiu 1000?) |
| Taxa de falha | `http_req_failed` | < 1% = verde |
| Latência clinical-timeline | `http_req_duration{endpoint:timeline}` | RNF-05 (≤ 800 ms = linha tracejada vermelha) |
| Latência consent check | `http_req_duration{endpoint:consent_check}` | RNF-06 (≤ 20 ms) |
| P95 por endpoint | breakdown | identifica gargalo |

O dashboard "k6 Load Testing - Overview" tem os números agregados padrão (úteis
para colocar uma screenshot direta no relatório).

---

## 5. Tabela-modelo para o relatório do TCC

Após rodar `load.js`, monte assim no relatório:

| RNF | Critério | Medição (k6) | Status |
|---|---|---|---|
| RNF-01 | P95 ≤ 500 ms a 1000 req simultâneas | P95 = **___** ms, throughput = **___** req/s, VUs pico = 1000 | ✅/❌ |
| RNF-01 | Taxa de falha < 1% | falha = **___** % | ✅/❌ |
| RNF-05 | history-service ≤ 800 ms | P95 timeline = **___** ms | ✅/❌ |
| RNF-06 | consent check ≤ 20 ms | P95 consent = **___** ms | ✅/❌ |

Capture screenshots dos painéis correspondentes do Grafana para anexar.

---

## 6. Troubleshooting

| Sintoma | Causa provável | Correção |
|---|---|---|
| `setup falhou ao obter token` | auth-service não está no ar | `docker compose ps auth-service` |
| Throughput trava em ~200 req/s | limite de pool HTTP no host | Aumentar `ulimit -n 65536` antes de rodar k6 |
| Latência sobe enquanto VUs aumentam | gargalo de CPU em algum container | `docker stats` para identificar |
| Grafana sem dados | InfluxDB não recebe métricas | confira flag `--out influxdb=http://localhost:8086/k6` no comando k6 |
| Dashboard vazio mesmo com dados | janela do tempo do Grafana | canto superior direito → "Last 30 minutes" |

---

## 7. Limpeza

```bash
# para a stack de métricas (preserva dados em volumes)
docker compose -f docker-compose.metrics.yml down

# remove tambem os volumes (apaga histórico de runs)
docker compose -f docker-compose.metrics.yml down -v
```
