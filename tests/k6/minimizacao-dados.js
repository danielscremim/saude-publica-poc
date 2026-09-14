// MINIMIZACAO DE DADOS NA CAMADA DE DISTRIBUICAO
//
// NAO e um benchmark entre protocolos. Mede a propria plataforma contra si mesma:
// quanto dado ela entrega quando o consumidor declara apenas os campos necessarios
// a sua finalidade, versus a linha de base (resposta completa e fixa).
// Evidencia empirica da minimizacao de dados (LGPD Art. 6, III; Privacy by Design).
//
// Quatro variantes, executadas em SEQUENCIA (nunca simultaneas):
//   A) baseline_full   REST   payload completo e fixo              -> linha de base
//   B) declarado_full  GraphQL declarando TODOS os campos          -> paridade (controle)
//   C) declarado_min   GraphQL declarando 3 campos (resumo clinico) -> minimizacao
//   D) consolidado     GraphQL visao consolidada em 1 requisicao   -> pontos de integracao
//      (exames + triagens + notificacoes + trilha de auditoria)
//   D') baseline_multi REST equivalente ao D: 4 chamadas separadas  -> linha de base do D
//
// Metricas por variante (tag endpoint):
//   - resp_bytes (Trend)        bytes por resposta  -> minimizacao
//   - roundtrips (Trend)        requisicoes HTTP por visao consolidada -> integracao
//   - http_req_duration p(95)   latencia
//
// A comparacao A x C sustenta a afirmacao de minimizacao; D' x D sustenta a
// reducao de pontos de integracao do consumidor externo.
//
// Uso (VM-2, apontando para o Kong da VM-1):
//   k6 run -e BASE_HOST=<IP_VM1> -e KONG_PORT=8000 -e EXAMS_PER_PATIENT=30 \
//          --summary-export minimizacao.json tests/k6/minimizacao-dados.js
//
// O setup popula EXAMS_PER_PATIENT exames por paciente via POST /v1/results
// (fluxo bidirecional autenticado com scope result:write) — gravacao direta,
// sem esperar o pipeline Kafka, para o historico ter tamanho controlado.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { URLS, DEFAULT_HEADERS, RNF_THRESHOLDS } from './lib/config.js';
import { setupBaseline } from './lib/setup.js';

const respBytes  = new Trend('resp_bytes');
const roundtrips = new Trend('roundtrips');

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
    baseline_full:  { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(0), exec: 'baselineFull',  tags: { endpoint: 'baseline_full' } },
    declarado_full: { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(1), exec: 'declaradoFull', tags: { endpoint: 'declarado_full' } },
    declarado_min:  { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(2), exec: 'declaradoMin',  tags: { endpoint: 'declarado_min' } },
    baseline_multi: { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(3), exec: 'baselineMulti', tags: { endpoint: 'baseline_multi' } },
    consolidado:    { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: stage(4), exec: 'consolidado',   tags: { endpoint: 'consolidado' } },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{endpoint:baseline_full}':  ['p(95)<2000'],
    'http_req_duration{endpoint:declarado_min}':  ['p(95)<2000'],
    'http_req_duration{endpoint:consolidado}':    ['p(95)<3000'],
    'resp_bytes{endpoint:baseline_full}': ['avg>0'],
    'resp_bytes{endpoint:declarado_min}': ['avg>0'],
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

// ---------- A) Linha de base: REST, payload completo e fixo ----------
export function baselineFull(data) {
  const res = http.get(
    `${URLS.HISTORY}/v1/patients/${pick(data.uuids)}/clinical-timeline?purpose=TREATMENT`,
    { headers: { Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'baseline_full' } });
  check(res, { 'baseline 200': r => r.status === 200 });
  respBytes.add(res.body.length, { endpoint: 'baseline_full' });
  roundtrips.add(1, { endpoint: 'baseline_full' });
  sleep(0.1);
}

// ---------- B) Controle: declara TODOS os campos (paridade com a linha de base) ----------
const Q_FULL = `query($id: ID!) { patientHistory(patientUuid: $id, purpose: "TREATMENT") {
  patientUuid totalExams patient { uuid name birthDate }
  exams { examId examType origin resultValue resultUnit completedAt } } }`;

export function declaradoFull(data) {
  const res = http.post(`${URLS.HISTORY}/graphql`,
    JSON.stringify({ query: Q_FULL, variables: { id: pick(data.uuids) } }),
    { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'declarado_full' } });
  check(res, { 'declarado_full 200 sem errors': r => r.status === 200 && !r.json('errors') });
  respBytes.add(res.body.length, { endpoint: 'declarado_full' });
  roundtrips.add(1, { endpoint: 'declarado_full' });
  sleep(0.1);
}

// ---------- C) Minimizacao: declara so o necessario a finalidade (resumo clinico) ----------
const Q_MIN = `query($id: ID!) { patientHistory(patientUuid: $id, purpose: "TREATMENT") {
  exams { examType resultValue completedAt } } }`;

export function declaradoMin(data) {
  const res = http.post(`${URLS.HISTORY}/graphql`,
    JSON.stringify({ query: Q_MIN, variables: { id: pick(data.uuids) } }),
    { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'declarado_min' } });
  check(res, { 'declarado_min 200 sem errors': r => r.status === 200 && !r.json('errors') });
  respBytes.add(res.body.length, { endpoint: 'declarado_min' });
  roundtrips.add(1, { endpoint: 'declarado_min' });
  sleep(0.1);
}

// ---------- D') Linha de base da visao consolidada: 4 chamadas REST separadas ----------
export function baselineMulti(data) {
  const uuid = pick(data.uuids);
  const h = { headers: { Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'baseline_multi' } };
  let bytes = 0, n = 0;
  for (const url of [
    `${URLS.HISTORY}/v1/patients/${uuid}/clinical-timeline?purpose=TREATMENT`,
    `${URLS.TRIAGE}/v1/triages/patient/${uuid}`,
    `${URLS.NOTIFICATION}/v1/notifications/patient/${uuid}`,
    `${URLS.AUDIT}/v1/audit/patient/${uuid}`,
  ]) {
    const res = http.get(url, h);
    check(res, { 'baseline_multi 200': r => r.status === 200 });
    bytes += res.body.length; n++;
  }
  respBytes.add(bytes, { endpoint: 'baseline_multi' });
  roundtrips.add(n, { endpoint: 'baseline_multi' });   // esperado: 4
  sleep(0.1);
}

// ---------- D) Consolidado: a mesma visao em UMA requisicao declarativa ----------
const Q_CONSOLIDADO = `query($id: ID!) { patientHistory(patientUuid: $id, purpose: "TREATMENT") {
  patient { name }
  exams { examType resultValue completedAt }
  triages { priority performedAt }
  notifications { channel status }
  auditTrail { action requesterId timestamp } } }`;

export function consolidado(data) {
  const res = http.post(`${URLS.HISTORY}/graphql`,
    JSON.stringify({ query: Q_CONSOLIDADO, variables: { id: pick(data.uuids) } }),
    { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` }, tags: { endpoint: 'consolidado' } });
  check(res, { 'consolidado 200 sem errors': r => r.status === 200 && !r.json('errors') });
  respBytes.add(res.body.length, { endpoint: 'consolidado' });
  roundtrips.add(1, { endpoint: 'consolidado' });      // esperado: 1
  sleep(0.1);
}
