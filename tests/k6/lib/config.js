// URLs dos servicos. Sobrescreva via env vars do k6 ao rodar fora do localhost,
// ex: k6 run -e BASE_HOST=192.168.0.10 load.js

const HOST = __ENV.BASE_HOST || 'localhost';

export const URLS = {
  PATIENT:      `http://${HOST}:8081`,
  EXAM:         `http://${HOST}:8082`,
  RESULT:       `http://${HOST}:8084`,
  AUTH:         `http://${HOST}:8085`,
  CONSENT:      `http://${HOST}:8086`,
  HISTORY:      `http://${HOST}:8087`,
  AUDIT:        `http://${HOST}:8088`,
  NOTIFICATION: `http://${HOST}:8089`,
  TRIAGE:       `http://${HOST}:8090`,
};

export const DEFAULT_HEADERS = { 'Content-Type': 'application/json' };

// Thresholds mapeados aos RNFs do TCC. Use no objeto options.thresholds.
// O k6 falha o run se algum threshold nao for atingido (saida 99) - util pra
// CI / regressao.
export const RNF_THRESHOLDS = {
  // RNF-01: escalabilidade
  'http_req_duration':                                ['p(95)<500'],
  'http_req_failed':                                  ['rate<0.01'],
  // RNF-05: interoperabilidade (history-service)
  'http_req_duration{endpoint:timeline}':             ['p(95)<800'],
  // RNF-06: consent check
  'http_req_duration{endpoint:consent_check}':        ['p(95)<20'],
};
