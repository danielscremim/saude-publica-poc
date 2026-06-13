// SMOKE TEST
// Carga minima (1 VU, 1 min). So confirma que o setup funciona, que os
// endpoints respondem corretamente e que os scripts auxiliares estao OK.
// Rodar PRIMEIRO, sempre, antes de qualquer teste de carga.
//
// Uso:
//   k6 run tests/k6/smoke.js
//   k6 run --out influxdb=http://localhost:8086/k6 tests/k6/smoke.js

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { URLS, DEFAULT_HEADERS } from './lib/config.js';
import { setupBaseline } from './lib/setup.js';

export const options = {
  vus: 1,
  duration: '1m',
  thresholds: {
    'checks':           ['rate>0.99'],   // 99% das asserções devem passar
    'http_req_failed':  ['rate<0.01'],   // <1% de falha
    'http_req_duration': ['p(95)<2000'], // P95 generoso para o smoke
  },
};

export function setup() {
  return setupBaseline();
}

export default function (data) {
  const auth = { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` } };

  group('patient: GET por uuid', () => {
    const r = http.get(`${URLS.PATIENT}/v1/patients/${data.patientUuid}`,
      { tags: { endpoint: 'patient_get' } });
    check(r, { 'patient 200': (x) => x.status === 200 });
  });

  group('consent: check', () => {
    const r = http.get(
      `${URLS.CONSENT}/v1/consents/check?patientUuid=${data.patientUuid}&institutionId=${data.institutionId}`,
      { tags: { endpoint: 'consent_check' } });
    check(r, {
      'consent 200':  (x) => x.status === 200,
      'consent granted': (x) => x.json('granted') === true,
    });
  });

  group('history: timeline', () => {
    const r = http.get(
      `${URLS.HISTORY}/v1/patients/${data.patientUuid}/clinical-timeline?purpose=TREATMENT`,
      { headers: auth.headers, tags: { endpoint: 'timeline' } });
    check(r, { 'timeline 200': (x) => x.status === 200 });
  });

  sleep(1);
}
