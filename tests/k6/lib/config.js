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
//
// Valores de design (producao, infraestrutura dedicada):
//   RNF-01: p(95) <= 500ms | RNF-05 timeline: p(95) <= 800ms | RNF-06 consent: p(95) <= 20ms
// Valores adotados para PoC (single-VM, todos servicos co-localizados):
//   P95 geral <= 2000ms | timeline <= 2000ms | consent <= 2000ms | erro < 1%
// A diferenca entre design e PoC e documentada no relatorio (limitacao de ambiente).
export const RNF_THRESHOLDS = {
  // RNF-01: escalabilidade (PoC: 2000ms; design producao: 500ms)
  'http_req_duration':                                ['p(95)<2000'],
  'http_req_failed':                                  ['rate<0.01'],
  // RNF-05: interoperabilidade / history-service (PoC: 2000ms; design: 800ms)
  'http_req_duration{endpoint:timeline}':             ['p(95)<2000'],
  // RNF-06: consent check (PoC: 2000ms; design producao: 20ms com cache dedicado)
  'http_req_duration{endpoint:consent_check}':        ['p(95)<2000'],
};
