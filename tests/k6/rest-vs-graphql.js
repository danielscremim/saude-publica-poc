// REST vs GraphQL — cenario comparativo (RNF-01 desempenho / RNF-05 interoperabilidade)
//
// Mede, para a MESMA consulta de historico clinico, tres variantes:
//   A) REST     GET /v1/patients/{uuid}/clinical-timeline     -> payload completo (baseline)
//   B) GraphQL  patientHistory { ...todos os campos }          -> paridade com REST
//   C) GraphQL  patientHistory { exams { examType resultValue completedAt } } -> campos minimos
//
// Metricas-chave por variante (tag endpoint=rest|graphql_full|graphql_min):
//   - http_req_duration p(95)      latencia
//   - resp_bytes (Trend custom)    bytes por resposta  <- quantifica o over-fetching
//   - http_req_failed              erros
//
// As tres variantes rodam em SEQUENCIA (startTime), nunca ao mesmo tempo, para
// que uma nao contamine a medida da outra. Cada variante: 200 VUs por 3 min.
//
// Uso (VM-2, apontando para o Kong da VM-1):
//   k6 run -e BASE_HOST=<IP_VM1> -e KONG_PORT=30080 -e EXAMS_PER_PATIENT=30 \
//          --summary-export rest-vs-graphql.json tests/k6/rest-vs-graphql.js
//
// O setup popula EXAMS_PER_PATIENT exames por paciente via POST /v1/results
// (fluxo bidirecional autenticado com scope result:write) — gravacao direta,
// sem esperar o pipeline Kafka, para o historico ter tamanho controlado.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { URLS, DEFAULT_HEADERS, RNF_THRESHOLDS } from './lib/config.js';
import { setupBaseline } from './lib/setup.js';

const respBytes = new Trend('resp_bytes');

const VUS      = parseInt(__ENV.VUS || '200');
const DURATION = __ENV.DURATION || '3m';
const EXAMS    = parseInt(__ENV.EXAMS_PER_PATIENT || '30');
const PATIENTS = parseInt(__ENV.PATIENTS || '20');
const GAP      = '30s';   // pausa entre variantes

function stage(idx) {
  // inicio escalonado: 0, 3m30s, 7m00s ...
  const durSec = parseInt(DURATION) * 60;
  const gapSec = 30;
  return `${idx * (durSec + gapSec)}s`;
}

export const options = {
  scenarios: {
    rest:         { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(0), exec: 'restFull',    tags: { endpoint: 'rest' } },
    graphql_full: { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(1), exec: 'graphqlFull', tags: { endpoint: 'graphql_full' } },
    graphql_min:  { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(2), exec: 'graphqlMin',  tags: { endpoint: 'graphql_min' } },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{endpoint:rest}':         ['p(95)<2000'],
    'http_req_duration{endpoint:graphql_full}': ['p(95)<2000'],
    'http_req_duration{endpoint:graphql_min}':  ['p(95)<2000'],
    'resp_bytes{endpoint:rest}':         ['avg>0'],
    'resp_bytes{endpoint:graphql_full}': ['avg>0'],
    'resp_bytes{endpoint:graphql_min}':  ['avg>0'],
  },
};

const TYPES = ['GLICEMIA', 'COLESTEROL', 'HEMOGLOBINA', 'TSH', 'CREATININA'];
const ORIGINS = ['UBS', 'LAB_PUBLICO', 'LAB_PRIVADO', 'HOSPITAL_PRIVADO'];

export function setup() {
  const base = setupBaseline({ clientId: 'k6-graphql-client', institutionId: 'K6-GQL' });
  const auth = { ...DEFAULT_HEADERS, Authorization: `Bearer ${base.token}` };

  // Cria PATIENTS pacientes, concede consent e grava EXAMS resultados em cada um
  const uuids = [];
  for (let p = 0; p < PATIENTS; p++) {
    const cpf = String(Date.now() + p * 7919).padStart(11, '0').slice(-11);
    const pr = http.post(`${URLS.PATIENT}/v1/patients`,
      JSON.stringify({ cpf, name: `GQL ${p}`, birthDate: '1975-05-05' }), { headers: DEFAULT_HEADERS });
    const uuid = pr.json('uuid');
    if (!uuid) continue;
    http.post(`${URLS.CONSENT}/v1/consents`,
      JSON.stringify({ patientUuid: uuid, institutionId: base.institutionId, scope: 'history:read:own_patients' }),
      { headers: DEFAULT_HEADERS });
    for (let i = 0; i < EXAMS; i++) {
      http.post(`${URLS.RESULT}/v1/results`, JSON.stringify({
        patientUuid: uuid,
        examType: TYPES[i % TYPES.length],
        origin: ORIGINS[i % ORIGINS.length],
        resultValue: 50 + (i * 3.7) % 150,
        resultUnit: 'mg/dL',
        completedAt: new Date(Date.now() - i * 86400000).toISOString(),
      }), { headers: auth });
    }
    uuids.push(uuid);
  }
  if (uuids.length === 0) throw new Error('setup: nenhum paciente criado');
  return { token: base.token, uuids };
}

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

// ---------- A) REST payload completo ----------
export function restFull(data) {
  const res = http.get(
    `${URLS.HISTORY}/v1/patients/${pick(data.uuids)}/clinical-timeline?purpose=TREATMENT`,
    { headers: { Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'rest' } });
  check(res, { 'rest 200': r => r.status === 200 });
  respBytes.add(res.body.length, { endpoint: 'rest' });
  sleep(0.1);
}

// ---------- B) GraphQL todos os campos (paridade) ----------
const Q_FULL = `query($id: ID!) { patientHistory(patientUuid: $id, purpose: "TREATMENT") {
  patientUuid totalExams patient { uuid name birthDate }
  exams { examId examType origin resultValue resultUnit completedAt } } }`;

export function graphqlFull(data) {
  const res = http.post(`${URLS.HISTORY}/graphql`,
    JSON.stringify({ query: Q_FULL, variables: { id: pick(data.uuids) } }),
    { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'graphql_full' } });
  check(res, { 'graphql_full 200 sem errors': r => r.status === 200 && !r.json('errors') });
  respBytes.add(res.body.length, { endpoint: 'graphql_full' });
  sleep(0.1);
}

// ---------- C) GraphQL campos minimos (o que um resumo clinico precisa) ----------
const Q_MIN = `query($id: ID!) { patientHistory(patientUuid: $id, purpose: "TREATMENT") {
  exams { examType resultValue completedAt } } }`;

export function graphqlMin(data) {
  const res = http.post(`${URLS.HISTORY}/graphql`,
    JSON.stringify({ query: Q_MIN, variables: { id: pick(data.uuids) } }),
    { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'graphql_min' } });
  check(res, { 'graphql_min 200 sem errors': r => r.status === 200 && !r.json('errors') });
  respBytes.add(res.body.length, { endpoint: 'graphql_min' });
  sleep(0.1);
}
