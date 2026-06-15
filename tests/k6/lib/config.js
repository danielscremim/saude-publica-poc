// URLs dos servicos. Sobrescreva via env vars do k6 ao rodar fora do localhost,
// ex: k6 run -e BASE_HOST=192.168.0.10 load.js
//
// No Kubernetes, passe KONG_PORT=8000 para rotear tudo pelo API Gateway:
// ex: k6 run -e BASE_HOST=<vm-ip> -e KONG_PORT=8000 load.js

const HOST = __ENV.BASE_HOST || 'localhost';
const KONG = __ENV.KONG_PORT ? `http://${HOST}:${__ENV.KONG_PORT}` : null;

function svc(port) {
  return KONG || `http://${HOST}:${port}`;
}

export const URLS = {
  PATIENT:      svc(8081),
  EXAM:         svc(8082),
  RESULT:       svc(8084),
  AUTH:         svc(8085),
  CONSENT:      svc(8086),
  HISTORY:      svc(8087),
  AUDIT:        svc(8088),
  NOTIFICATION: svc(8089),
  TRIAGE:       svc(8090),
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
