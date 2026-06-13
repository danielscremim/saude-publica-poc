// STRESS TEST
// Vai alem dos 1000 VUs do RNF-01 (sobe ate 2000, depois 5000) para achar
// onde a plataforma comeca a degradar/falhar - util para discutir limites
// e dimensionamento no TCC.
//
// Uso:
//   k6 run --out influxdb=http://localhost:8086/k6 tests/k6/stress.js
//
// Atencao: este script PROPOSITALMENTE quebra a aplicacao. O objetivo nao
// e atingir todos os thresholds, e sim observar como ela se comporta sob
// pressao extrema (curva de degradacao, taxa de erro vs throughput).

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { URLS, DEFAULT_HEADERS } from './lib/config.js';
import { setupBaseline } from './lib/setup.js';

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 500 },    // warm up
        { duration: '2m',  target: 1000 },   // baseline
        { duration: '2m',  target: 2000 },   // 2x RNF-01
        { duration: '3m',  target: 2000 },   // sustenta
        { duration: '2m',  target: 5000 },   // pressao extrema
        { duration: '3m',  target: 5000 },
        { duration: '2m',  target: 0 },      // recovery
      ],
      gracefulRampDown: '30s',
    },
  },
  // Sem thresholds que falhem o run - aqui queremos observar, nao validar.
  thresholds: {
    'http_req_duration': ['p(95)<5000'],   // gera aviso visual, nao falha
  },
};

export function setup() {
  return setupBaseline({ clientId: 'k6-stress-client' });
}

export default function (data) {
  const auth = { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` } };

  // Mix mais agressivo no caminho hot (history-service, com auth + consent + 2 chamadas internas).
  group('history timeline (hot path)', () => {
    const res = http.get(
      `${URLS.HISTORY}/v1/patients/${data.patientUuid}/clinical-timeline?purpose=TREATMENT`,
      { headers: auth.headers, tags: { endpoint: 'timeline' } });
    check(res, { 'timeline 200 ou 429/503 esperado sob stress': (x) =>
      x.status === 200 || x.status === 429 || x.status === 503 });
  });

  sleep(Math.random() * 0.3);
}
