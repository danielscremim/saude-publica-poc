// LOAD TEST - cenario do RNF-01
// Sobe de 0 a 1000 VUs em 2 min, sustenta 5 min, desce em 2 min.
// Esse e o teste a citar no relatorio: prova que a plataforma atende
// "1000 req simultaneas com P95 <= 500ms" (RNF-01).
//
// Uso (recomendado):
//   docker compose up -d                                        # garante a app no ar
//   docker compose -f docker-compose.metrics.yml up -d          # InfluxDB + Grafana
//   k6 run --out influxdb=http://localhost:8086/k6 tests/k6/load.js
//
// Resultado vai para o Grafana em http://localhost:3000
// (dashboard "Saude PoC - RNFs (k6)").

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { URLS, DEFAULT_HEADERS, RNF_THRESHOLDS } from './lib/config.js';
import { setupBaseline } from './lib/setup.js';

// Metricas customizadas para gerar paineis dedicados no Grafana.
const journeyDuration = new Trend('journey_duration', true);

export const options = {
  scenarios: {
    load_rnf01: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 1000 },   // ramp up
        { duration: '5m', target: 1000 },   // sustain
        { duration: '2m', target: 0 },      // ramp down
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: RNF_THRESHOLDS,
};

export function setup() {
  return setupBaseline({ clientId: 'k6-load-client' });
}

export default function (data) {
  const auth = { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` } };
  const start = Date.now();

  // 50% leitura de timeline (RNF-05), 30% consent check (RNF-06),
  // 20% escrita (POST patient). Distribuicao reflete um sistema de leitura pesado.
  const r = Math.random();

  if (r < 0.5) {
    group('history timeline', () => {
      const res = http.get(
        `${URLS.HISTORY}/v1/patients/${data.patientUuid}/clinical-timeline?purpose=TREATMENT`,
        { headers: auth.headers, tags: { endpoint: 'timeline' } });
      check(res, { 'timeline 200': (x) => x.status === 200 });
    });
  } else if (r < 0.8) {
    group('consent check', () => {
      const res = http.get(
        `${URLS.CONSENT}/v1/consents/check?patientUuid=${data.patientUuid}&institutionId=${data.institutionId}`,
        { tags: { endpoint: 'consent_check' } });
      check(res, { 'consent 200': (x) => x.status === 200 });
    });
  } else {
    group('patient create', () => {
      const cpf = String(Date.now() + __VU * 1000 + Math.floor(Math.random() * 1000)).padStart(11, '0').slice(-11);
      const res = http.post(`${URLS.PATIENT}/v1/patients`,
        JSON.stringify({ cpf, name: `Carga ${__VU}`, birthDate: '1990-01-01' }),
        { headers: DEFAULT_HEADERS, tags: { endpoint: 'patient_create' } });
      check(res, { 'patient 201': (x) => x.status === 201 });
    });
  }

  journeyDuration.add(Date.now() - start);
  sleep(Math.random() * 0.5);   // think time aleatorio 0-500ms
}
