#!/usr/bin/env bash
# Testa o fluxo bidirecional: HOSPITAL_PRIVADO enviando resultado direto ao result-service.
#
# Cenarios:
#   - POST /v1/results SEM token        -> 401
#   - POST /v1/results COM token sem scope correto -> 403
#   - POST /v1/results COM token + scope result:write -> 201
#   - GET /v1/results/patient/{uuid}    -> resultado persistido
#   - GET /v1/notifications/patient/{uuid} -> notification gerada via Kafka (exam.completed)
#   - GET /v1/audit/patient/{uuid}      -> WRITE_RESULT registrado em audit.events
set -e

PATIENT_URL="http://localhost:8081"
AUTH_URL="http://localhost:8085"
RESULT_URL="http://localhost:8084"
NOTIF_URL="http://localhost:8089"
AUDIT_URL="http://localhost:8088"

json_field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

echo "==> 1. Setup: registra dois clients e cadastra paciente..."
# Client com scope CORRETO
curl -s -o /dev/null -X POST "$AUTH_URL/v1/clients" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"hospital-bidir-ok","clientSecret":"s1","institutionId":"HOSP-SP-PRIV","scopes":"result:write"}'

# Client com scope ERRADO (so leitura)
curl -s -o /dev/null -X POST "$AUTH_URL/v1/clients" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"hospital-bidir-readonly","clientSecret":"s2","institutionId":"HOSP-SP-RO","scopes":"history:read:own_patients"}'

PATIENT_RESP=$(curl -s -X POST "$PATIENT_URL/v1/patients" \
  -H "Content-Type: application/json" \
  -d '{"cpf":"33344455566","name":"Roberto Bidir","birthDate":"1972-11-05"}')
UUID=$(json_field "$PATIENT_RESP" "uuid")
echo "    Paciente UUID: $UUID"

PAYLOAD="{\"patientUuid\":\"$UUID\",\"examType\":\"COLESTEROL\",\"origin\":\"HOSPITAL_PRIVADO\",\"resultValue\":230.5,\"resultUnit\":\"mg/dL\"}"

echo ""
echo "==> 2. POST /v1/results SEM token (esperado: 401)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RESULT_URL/v1/results" \
  -H "Content-Type: application/json" -d "$PAYLOAD")
echo "    HTTP $STATUS"
[ "$STATUS" = "401" ] || { echo "ERRO: esperava 401"; exit 1; }

echo ""
echo "==> 3. Obtendo token do client com scope ERRADO..."
TOKEN_RO=$(curl -s -X POST "$AUTH_URL/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"hospital-bidir-readonly","clientSecret":"s2"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

echo "==> 4. POST /v1/results COM token mas SEM scope result:write (esperado: 403)..."
BODY=$(curl -s -w "\n%{http_code}" -X POST "$RESULT_URL/v1/results" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_RO" \
  -d "$PAYLOAD")
STATUS=$(echo "$BODY" | tail -1)
echo "    HTTP $STATUS  body=$(echo "$BODY" | head -1)"
[ "$STATUS" = "403" ] || { echo "ERRO: esperava 403"; exit 1; }

echo ""
echo "==> 5. Obtendo token do client com scope CORRETO..."
TOKEN=$(curl -s -X POST "$AUTH_URL/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"hospital-bidir-ok","clientSecret":"s1","scope":"result:write"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

echo "==> 6. POST /v1/results COM token + scope correto (esperado: 201)..."
BODY=$(curl -s -w "\n%{http_code}" -X POST "$RESULT_URL/v1/results" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d "$PAYLOAD")
STATUS=$(echo "$BODY" | tail -1)
JSON=$(echo "$BODY" | head -1)
echo "    HTTP $STATUS"
echo "    $JSON"
[ "$STATUS" = "201" ] || { echo "ERRO: esperava 201"; exit 1; }

echo ""
echo "==> 7. Aguardando propagacao do Kafka (exam.completed -> notification + audit.events)..."
sleep 3

echo ""
echo "==> 8. GET /v1/results/patient/$UUID (esperado: 1 resultado HOSPITAL_PRIVADO)..."
RESULTS=$(curl -s "$RESULT_URL/v1/results/patient/$UUID")
echo "    $RESULTS"
echo "$RESULTS" | grep -q "HOSPITAL_PRIVADO" || { echo "ERRO: resultado nao encontrado"; exit 1; }

echo ""
echo "==> 9. GET /v1/notifications/patient/$UUID (esperado: 1 notificacao)..."
NOTIFS=$(curl -s "$NOTIF_URL/v1/notifications/patient/$UUID")
COUNT=$(echo "$NOTIFS" | grep -o '"id"' | wc -l)
echo "    $COUNT notificacao(oes)"
[ "$COUNT" -ge 1 ] || { echo "ERRO: notificacao nao gerada"; exit 1; }

echo ""
echo "==> 10. GET /v1/audit/patient/$UUID (esperado: WRITE_RESULT por hospital-bidir-ok)..."
AUDIT=$(curl -s "$AUDIT_URL/v1/audit/patient/$UUID")
echo "    $AUDIT"
echo "$AUDIT" | grep -q "WRITE_RESULT" || { echo "ERRO: WRITE_RESULT nao registrado"; exit 1; }
echo "$AUDIT" | grep -q "hospital-bidir-ok" || { echo "ERRO: requesterId errado"; exit 1; }

echo ""
echo "==> Fluxo bidirecional OK."
echo "    Hospital privado envia resultado -> persiste -> propaga via Kafka -> notification + auditoria."
