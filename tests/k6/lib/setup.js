// Setup compartilhado: registra um client OAuth2, obtem JWT, cadastra um paciente
// e concede consent. Cada script k6 chama setup() uma unica vez por execucao;
// o retorno e injetado em todas as VUs (sem precisar repetir as chamadas).

import http from 'k6/http';
import { check, sleep } from 'k6';
import { URLS, DEFAULT_HEADERS } from './config.js';

export function setupBaseline(opts = {}) {
  const clientId      = opts.clientId      || `k6-client-${Date.now()}`;
  const clientSecret  = opts.clientSecret  || 'k6-secret';
  const institutionId = opts.institutionId || 'K6-LAB';
  const scopes        = opts.scopes        || 'history:read:own_patients result:write';

  // 1. Registra client. 201 = criado, 400 = ja existe (ambos seguem).
  // A resposta PRECISA ser verificada: em 20/09 um registro que falhou em
  // silencio logo apos o reset do ambiente (JVM fria) apareceu adiante como
  // "401 invalid_client" na obtencao do token - erro que aponta para o lugar
  // errado e custa tempo de diagnostico.
  let reg;
  for (let i = 0; i < 5; i++) {
    reg = http.post(`${URLS.AUTH}/v1/clients`,
      JSON.stringify({ clientId, clientSecret, institutionId, scopes }),
      { headers: DEFAULT_HEADERS, tags: { setup: 'register-client' } });
    if (reg.status === 201 || reg.status === 400) break;
    sleep(2);   // servico ainda aquecendo: tenta de novo
  }
  if (reg.status !== 201 && reg.status !== 400) {
    throw new Error(`Setup falhou ao registrar o client apos 5 tentativas: `
      + `${reg.status} ${reg.body}`);
  }

  // 2. Obtem token (mesma tolerancia ao aquecimento)
  let tokenResp;
  for (let i = 0; i < 5; i++) {
    tokenResp = http.post(`${URLS.AUTH}/v1/auth/token`,
      JSON.stringify({ clientId, clientSecret }),
      { headers: DEFAULT_HEADERS, tags: { setup: 'get-token' } });
    if (tokenResp.status === 200) break;
    sleep(2);
  }

  check(tokenResp, { 'setup: token 200': (r) => r.status === 200 });
  const token = tokenResp.json('accessToken');
  if (!token) {
    throw new Error(`Setup falhou ao obter token: ${tokenResp.status} ${tokenResp.body}`
      + ` (registro do client retornou ${reg.status})`);
  }

  // 3. Cadastra paciente (CPF unico por timestamp)
  const cpf = String(Date.now()).padStart(11, '0').slice(-11);
  const patientResp = http.post(`${URLS.PATIENT}/v1/patients`,
    JSON.stringify({ cpf, name: 'Paciente K6', birthDate: '1980-01-01' }),
    { headers: DEFAULT_HEADERS, tags: { setup: 'create-patient' } });
  check(patientResp, { 'setup: paciente 201': (r) => r.status === 201 });
  const patientUuid = patientResp.json('uuid');

  // 4. Concede consent (para que o GET timeline retorne 200)
  const consentResp = http.post(`${URLS.CONSENT}/v1/consents`,
    JSON.stringify({ patientUuid, institutionId, scope: 'history:read:own_patients' }),
    { headers: DEFAULT_HEADERS, tags: { setup: 'grant-consent' } });
  check(consentResp, { 'setup: consent 201': (r) => r.status === 201 });

  return { token, patientUuid, institutionId, clientId };
}
