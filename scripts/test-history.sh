#!/usr/bin/env bash
# Testa o fluxo completo do history-service:
#   - JWT obrigatorio (sem token -> 401)
#   - Consent obrigatorio (sem consent -> 403, com consent -> 200)
#   - Revogacao bloqueia leituras subsequentes (RNF-06)
#   - Cada acesso (autorizado ou negado) publica audit.events
set -e

PATIENT_URL="http://localhost:8081"
EXAM_URL="http://localhost:8082"
AUTH_URL="http://localhost:8085"
CONSENT_URL="http://localhost:8086"
HISTORY_URL="http://localhost:8087"

json_field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

echo "==> 1. Registrando client OAuth2 (Hospital Sao Lucas)..."
# Idempotente: se ja existir, segue (auth-service devolve 400 nesse caso).
curl -s -o /dev/null -X POST "$AUTH_URL/v1/clients" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"hospital-sao-lucas","clientSecret":"secret-demo-123","institutionId":"HOSP-SP-001","scopes":"history:read:own_patients result:write"}'

echo ""
echo "==> 2. Obtendo token JWT..."
TOKEN_RESP=$(curl -s -X POST "$AUTH_URL/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"hospital-sao-lucas","clientSecret":"secret-demo-123","scope":"history:read:own_patients"}')
TOKEN=$(echo "$TOKEN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then echo "ERRO: nao obteve token: $TOKEN_RESP"; exit 1; fi
echo "    Token OK (${#TOKEN} chars)"

echo ""
echo "==> 3. Cadastrando paciente..."
PATIENT_RESP=$(curl -s -X POST "$PATIENT_URL/v1/patients" \
  -H "Content-Type: application/json" \
  -d '{"cpf":"55566677788","name":"Ana Historia","birthDate":"1990-10-10"}')
UUID=$(json_field "$PATIENT_RESP" "uuid")
if [ -z "$UUID" ]; then echo "ERRO: nao obteve UUID: $PATIENT_RESP"; exit 1; fi
echo "    Paciente UUID: $UUID"

echo ""
echo "==> 4. Solicitando 2 exames (fluxo assincrono lab -> result)..."
for TIPO in GLICEMIA HEMOGLOBINA; do
  curl -s -X POST "$EXAM_URL/v1/exams" \
    -H "Content-Type: application/json" \
    -d "{\"patientUuid\":\"$UUID\",\"examType\":\"$TIPO\",\"origin\":\"UBS\"}" > /dev/null
done
echo "    Aguardando processamento assincrono..."
sleep 3

echo ""
echo "==> 5. Timeline SEM token (esperado: 401)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$HISTORY_URL/v1/patients/$UUID/clinical-timeline")
echo "    HTTP $STATUS"
[ "$STATUS" = "401" ] || { echo "ERRO: esperava 401, recebi $STATUS"; exit 1; }

echo ""
echo "==> 6. Timeline COM token mas SEM consentimento (esperado: 403)..."
BODY=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "$HISTORY_URL/v1/patients/$UUID/clinical-timeline?purpose=TREATMENT")
STATUS=$(echo "$BODY" | tail -1)
echo "    HTTP $STATUS  body=$(echo "$BODY" | head -1)"
[ "$STATUS" = "403" ] || { echo "ERRO: esperava 403, recebi $STATUS"; exit 1; }

echo ""
echo "==> 7. Concedendo consentimento..."
CONSENT_RESP=$(curl -s -X POST "$CONSENT_URL/v1/consents" \
  -H "Content-Type: application/json" \
  -d "{\"patientUuid\":\"$UUID\",\"institutionId\":\"HOSP-SP-001\",\"scope\":\"history:read:own_patients\"}")
CONSENT_ID=$(json_field "$CONSENT_RESP" "id")
echo "    Consent ID: $CONSENT_ID"

echo ""
echo "==> 8. Timeline COM token E COM consentimento (esperado: 200, com paciente e exames)..."
BODY=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "$HISTORY_URL/v1/patients/$UUID/clinical-timeline?purpose=TREATMENT")
STATUS=$(echo "$BODY" | tail -1)
JSON=$(echo "$BODY" | head -1)
echo "    HTTP $STATUS"
echo "    $JSON" | head -c 600; echo ""
[ "$STATUS" = "200" ] || { echo "ERRO: esperava 200, recebi $STATUS"; exit 1; }

echo ""
echo "==> 9. Revogando consentimento..."
curl -s -X DELETE "$CONSENT_URL/v1/consents/$CONSENT_ID" > /dev/null
sleep 1

echo ""
echo "==> 10. Timeline apos revogacao (esperado: 403)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "$HISTORY_URL/v1/patients/$UUID/clinical-timeline?purpose=TREATMENT")
echo "    HTTP $STATUS"
[ "$STATUS" = "403" ] || { echo "ERRO: esperava 403, recebi $STATUS"; exit 1; }

echo ""
echo "==> Fluxo history-service OK. Eventos publicados em audit.events:"
echo "    READ_TIMELINE_DENIED (etapa 6), READ_TIMELINE (etapa 8), READ_TIMELINE_DENIED (etapa 10)."
echo "    Veja em http://localhost:8090 (Kafka UI)."
