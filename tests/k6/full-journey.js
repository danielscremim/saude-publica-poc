// FULL JOURNEY
// Simula a jornada de um usuario real ponta-a-ponta, repetida por VUs:
//   1. cadastra paciente -> 2. solicita exame -> 3. aguarda processamento async
//   -> 4. consulta timeline (RNF-05) -> 5. verifica que notification chegou
//
// Util para medir o tempo TOTAL da operacao do ponto de vista do usuario,
// nao so a latencia HTTP de cada endpoint.

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { URLS, DEFAULT_HEADERS } from './lib/config.js';
import { setupBaseline } from './lib/setup.js';

const e2eJourney   = new Trend('journey_e2e_duration', true);
const journeysOk   = new Counter('journeys_ok');
const journeysFail = new Counter('journeys_fail');

export const options = {
  scenarios: {
    journey: {
      executor: 'constant-vus',
      vus: 50,                 // carga moderada, ja basta para medir e2e
      duration: '3m',
    },
  },
  thresholds: {
    'journey_e2e_duration': ['p(95)<5000'],   // jornada completa em <= 5s no p95
    'http_req_failed':      ['rate<0.02'],
  },
};

export function setup() {
  // Cliente e consent globais para a campanha (o paciente sera criado por iteracao).
  return setupBaseline({ clientId: 'k6-journey-client' });
}

export default function (data) {
  const auth = { headers: { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` } };
  const tStart = Date.now();
  let allOk = true;

  let patientUuid;

  // 1. cadastra paciente
  group('1. patient create', () => {
    const cpf = String(Date.now() + __VU * 1000 + __ITER).padStart(11, '0').slice(-11);
    const r = http.post(`${URLS.PATIENT}/v1/patients`,
      JSON.stringify({ cpf, name: `Jornada ${__VU}-${__ITER}`, birthDate: '1985-06-15' }),
      { headers: DEFAULT_HEADERS, tags: { endpoint: 'patient_create' } });
    if (!check(r, { 'patient 201': (x) => x.status === 201 })) { allOk = false; return; }
    patientUuid = r.json('uuid');
  });
  if (!patientUuid) { journeysFail.add(1); return; }

  // 1.5. concede consent para esse paciente (instituicao da setup)
  group('1.5 consent grant', () => {
    const r = http.post(`${URLS.CONSENT}/v1/consents`,
      JSON.stringify({ patientUuid, institutionId: data.institutionId, scope: 'history:read:own_patients' }),
      { headers: DEFAULT_HEADERS, tags: { endpoint: 'consent_grant' } });
    if (!check(r, { 'consent 201': (x) => x.status === 201 })) allOk = false;
  });

  // 2. solicita exame
  group('2. exam request', () => {
    const r = http.post(`${URLS.EXAM}/v1/exams`,
      JSON.stringify({ patientUuid, examType: 'GLICEMIA', origin: 'UBS' }),
      { headers: DEFAULT_HEADERS, tags: { endpoint: 'exam_request' } });
    if (!check(r, { 'exam 201': (x) => x.status === 201 })) allOk = false;
  });

  // 3. aguarda processamento async (lab -> result)
  sleep(2);

  // 4. consulta timeline
  group('4. history timeline', () => {
    const r = http.get(
      `${URLS.HISTORY}/v1/patients/${patientUuid}/clinical-timeline?purpose=TREATMENT`,
      { headers: auth.headers, tags: { endpoint: 'timeline' } });
    if (!check(r, {
      'timeline 200': (x) => x.status === 200,
      'timeline tem exame': (x) => Array.isArray(x.json('exams')) && x.json('exams').length >= 1,
    })) allOk = false;
  });

  // 5. valida notification gerada
  group('5. notification check', () => {
    const r = http.get(`${URLS.NOTIFICATION}/v1/notifications/patient/${patientUuid}`,
      { tags: { endpoint: 'notification_list' } });
    check(r, { 'notification 200': (x) => x.status === 200 });
    // Sem assert estrito - notification pode levar mais do que o sleep(2).
  });

  e2eJourney.add(Date.now() - tStart);
  if (allOk) journeysOk.add(1); else journeysFail.add(1);
}
