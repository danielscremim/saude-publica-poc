// PONTO DE RUPTURA - cenario C do plano-teste-estresse.md (alimenta o Resultado 2)
//
// POR QUE ESTE SCRIPT EXISTE, SE JA HA stress.js:
// o stress.js usa `ramping-vus`. Com VUs a carga SE AUTO-REGULA: quando o sistema
// fica lento, cada usuario virtual espera a resposta e envia menos requisicoes -
// o limite real fica mascarado. Aqui usamos `ramping-arrival-rate`, que fixa a
// TAXA DE CHEGADA em requisicoes por segundo independentemente do tempo de
// resposta. E o que permite afirmar "a plataforma sustentou N req/s; acima disso,
// degradou" em vez de apenas "ficou lenta".
//
// CRITERIO DE RUPTURA (plano, secao 4) - primeiro degrau em que ocorre qualquer um:
//   - taxa de erro acima de 1%
//   - P95 acima de 2000 ms
//   - dropped_iterations > 0  (o proprio gerador nao sustentou a taxa alvo)
//   - pods em Pending / OOMKilled / CrashLoopBackOff (visto na VM-1, nao aqui)
// Os tres primeiros estao como thresholds com abortOnFail: o teste PARA sozinho
// no degrau de ruptura, que e exatamente o numero que se quer reportar.
//
// USO (na VM-2):
//   k6 run -e BASE_HOST=<IP_VM1> -e KONG_PORT=8000 \
//          --out csv=ruptura.csv --summary-export ruptura.json ponto-ruptura.js
//
//   Ajustes:  -e START_RPS=200 -e STEP_RPS=200 -e MAX_RPS=2000 -e STEP_DURATION=2m
//             -e ABORT=false          (percorre todos os degraus sem parar na ruptura)
//
// LEITURA DO RESULTADO: o JSON traz submetricas http_req_duration{degrau:N}.
// O maior N com P95 dentro do limite e taxa de erro < 1% e a capacidade sustentada.
// dropped_iterations > 0 invalida o degrau: quem nao sustentou foi o gerador.

import http from 'k6/http';
import { check, group } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { URLS, DEFAULT_HEADERS } from './lib/config.js';
import { setupBaseline } from './lib/setup.js';

const START_RPS      = parseInt(__ENV.START_RPS || '200');
const STEP_RPS       = parseInt(__ENV.STEP_RPS || '200');
const MAX_RPS        = parseInt(__ENV.MAX_RPS || '2000');
const STEP_SECONDS   = parseInt(__ENV.STEP_SECONDS || '120');
const ABORT          = (__ENV.ABORT || 'true') !== 'false';

// Degraus: START_RPS, +STEP_RPS ... ate MAX_RPS. Cada degrau tem uma rampa curta
// (10s) e um patamar de STEP_SECONDS, para o sistema estabilizar antes de medir.
const DEGRAUS = [];
for (let rps = START_RPS; rps <= MAX_RPS; rps += STEP_RPS) DEGRAUS.push(rps);

const stages = [];
for (const rps of DEGRAUS) {
  stages.push({ target: rps, duration: '10s' });
  stages.push({ target: rps, duration: `${STEP_SECONDS}s` });
}

const degrauRps = new Trend('degrau_rps_alvo');
const errosPorDegrau = new Counter('erros_por_degrau');

// Threshold "sempre verdadeiro" por degrau: serve para materializar as
// submetricas http_req_duration{degrau:N} no --summary-export. Sem isso o k6
// nao quebra a metrica por tag no resumo final.
const thresholds = {
  'dropped_iterations': ['count<1'],
  'http_req_failed':    [{ threshold: 'rate<0.01',      abortOnFail: ABORT, delayAbortEval: '30s' }],
  'http_req_duration':  [{ threshold: 'p(95)<2000',     abortOnFail: ABORT, delayAbortEval: '30s' }],
};
DEGRAUS.forEach((rps, i) => {
  thresholds[`http_req_duration{degrau:${i}}`] = ['p(95)>=0'];
  thresholds[`http_req_failed{degrau:${i}}`]   = ['rate>=0'];
});

export const options = {
  scenarios: {
    ruptura: {
      executor: 'ramping-arrival-rate',
      startRate: START_RPS,
      timeUnit: '1s',
      // VUs suficientes para sustentar a taxa mesmo quando a latencia sobe:
      // com P95 de 2 s, sustentar MAX_RPS exige ~MAX_RPS*2 VUs em voo.
      preAllocatedVUs: Math.min(500, MAX_RPS),
      maxVUs: Math.max(1000, MAX_RPS * 2),
      stages,
    },
  },
  thresholds,
  // Descarta o aquecimento da JVM/HPA do calculo final.
  discardResponseBodies: false,
};

export function setup() {
  const base = setupBaseline({ clientId: 'k6-ruptura-client' });
  return { ...base, inicio: Date.now() };
}

// Deduz o degrau atual pelo tempo decorrido - o k6 nao expoe o stage corrente.
function degrauAtual(inicio) {
  const s = (Date.now() - inicio) / 1000;
  const porDegrau = STEP_SECONDS + 10;
  return Math.min(Math.floor(s / porDegrau), DEGRAUS.length - 1);
}

export default function (data) {
  const i = degrauAtual(data.inicio);
  const tags = { degrau: String(i) };
  degrauRps.add(DEGRAUS[i], tags);

  const auth = { ...DEFAULT_HEADERS, Authorization: `Bearer ${data.token}` };

  // Mesma distribuicao do load.js (50% leitura agregada / 30% consent / 20% escrita),
  // para que a capacidade medida aqui seja comparavel com o cenario nominal.
  const r = Math.random();
  let res;

  if (r < 0.5) {
    group('history timeline', () => {
      res = http.get(
        `${URLS.HISTORY}/v1/patients/${data.patientUuid}/clinical-timeline?purpose=TREATMENT`,
        { headers: auth, tags: { ...tags, endpoint: 'timeline' } });
      check(res, { 'timeline 200': (x) => x.status === 200 });
    });
  } else if (r < 0.8) {
    group('consent check', () => {
      res = http.get(
        `${URLS.CONSENT}/v1/consents/check?patientUuid=${data.patientUuid}&institutionId=${data.institutionId}`,
        { tags: { ...tags, endpoint: 'consent_check' } });
      check(res, { 'consent 200': (x) => x.status === 200 });
    });
  } else {
    group('patient create', () => {
      const cpf = String(Date.now() + __VU * 1000 + Math.floor(Math.random() * 1000)).padStart(11, '0').slice(-11);
      res = http.post(`${URLS.PATIENT}/v1/patients`,
        JSON.stringify({ cpf, name: `Ruptura ${__VU}`, birthDate: '1990-01-01' }),
        { headers: DEFAULT_HEADERS, tags: { ...tags, endpoint: 'patient_create' } });
      check(res, { 'patient 201': (x) => x.status === 201 });
    });
  }

  if (res && res.status >= 500) errosPorDegrau.add(1, tags);
  // Sem think time: em arrival-rate quem controla o ritmo e o executor, nao o VU.
}

export function handleSummary(data) {
  const linhas = ['degrau,rps_alvo,p95_ms,taxa_erro'];
  DEGRAUS.forEach((rps, i) => {
    const d = data.metrics[`http_req_duration{degrau:${i}}`];
    const f = data.metrics[`http_req_failed{degrau:${i}}`];
    if (d) {
      linhas.push([i, rps, (d.values['p(95)'] || 0).toFixed(1), (f ? f.values.rate : 0).toFixed(4)].join(','));
    }
  });
  const dropped = data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0;
  return {
    stdout: '\n=== CAPACIDADE POR DEGRAU ===\n' + linhas.join('\n') +
            `\n\ndropped_iterations: ${dropped}` +
            (dropped > 0 ? '  <-- gerador nao sustentou a taxa; degraus finais nao sao validos\n' : '\n'),
    'ruptura-degraus.csv': linhas.join('\n') + '\n',
  };
}
