#!/usr/bin/env bash
# Testa o transporte GraphQL do history-service (paridade com scripts/test-history.sh):
#   - JWT obrigatorio (sem token -> 401)
#   - Consent obrigatorio (sem consent -> errors[].classification FORBIDDEN)
#   - Consulta seletiva (so os campos pedidos voltam) e filtros examType/limit
#   - Limite de profundidade (query abusiva -> erro antes de executar) — RNF-06
#   - Comparacao de tamanho do payload REST x GraphQL (over-fetching)
#
# Uso: ./scripts/test-graphql.sh                      (docker compose, portas locais)
#      BASE=http://<IP_VM1>:8000 ./scripts/test-graphql.sh   (via Kong no Kubernetes)
set -e

if [ -n "${BASE:-}" ]; then
  PATIENT_URL="$BASE"; RESULT_URL="$BASE"; AUTH_URL="$BASE"; CONSENT_URL="$BASE"; HISTORY_URL="$BASE"
else
  PATIENT_URL="http://localhost:8081"; RESULT_URL="http://localhost:8084"
  AUTH_URL="http://localhost:8085";    CONSENT_URL="http://localhost:8086"; HISTORY_URL="http://localhost:8087"
fi

json_field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
gql() { # $1=token(ou vazio) $2=query $3=variables-json
  local auth=(); [ -n "$1" ] && auth=(-H "Authorization: Bearer $1")
  curl -s -w "\n%{http_code}" -X POST "$HISTORY_URL/graphql" -H "Content-Type: application/json" "${auth[@]}" \
    -d "$(printf '{"query":%s,"variables":%s}' "$(printf '%s' "$2" | python3 -c 'import json,sys;print(json.dumps(sys.stdin.read()))')" "${3:-{\}}")"
}

echo "==> 1. Client OAuth2 + token..."
curl -s -o /dev/null -X POST "$AUTH_URL/v1/clients" -H "Content-Type: application/json" \
  -d '{"clientId":"lab-graphql","clientSecret":"secret-gql","institutionId":"LAB-GQL-001","scopes":"history:read:own_patients result:write"}'
TOKEN=$(curl -s -X POST "$AUTH_URL/v1/auth/token" -H "Content-Type: application/json" \
  -d '{"clientId":"lab-graphql","clientSecret":"secret-gql"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
[ -n "$TOKEN" ] || { echo "ERRO: sem token"; exit 1; }
echo "    Token OK"

echo ""
echo "==> 2. Paciente + 3 resultados (POST /v1/results, fluxo bidirecional)..."
UUID=$(json_field "$(curl -s -X POST "$PATIENT_URL/v1/patients" -H "Content-Type: application/json" \
  -d '{"cpf":"66677788899","name":"Carlos GraphQL","birthDate":"1978-02-02"}')" "uuid")
[ -n "$UUID" ] || { echo "ERRO: sem UUID"; exit 1; }
echo "    Paciente UUID: $UUID"
i=0
for T in GLICEMIA COLESTEROL GLICEMIA; do
  i=$((i+1))
  curl -s -o /dev/null -X POST "$RESULT_URL/v1/results" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
    -d "{\"patientUuid\":\"$UUID\",\"examType\":\"$T\",\"origin\":\"LAB_PRIVADO\",\"resultValue\":$((90+i*7)).5,\"resultUnit\":\"mg/dL\"}"
done
echo "    3 resultados gravados"

echo ""
echo "==> 3. GraphQL SEM token (esperado: HTTP 401)..."
STATUS=$(gql "" '{ __typename }' | tail -1)
echo "    HTTP $STATUS"; [ "$STATUS" = "401" ] || { echo "ERRO: esperava 401"; exit 1; }

Q_MIN='query($id: ID!){ patientHistory(patientUuid:$id, purpose:"TREATMENT"){ totalExams exams{ examType resultValue completedAt } } }'
VARS="{\"id\":\"$UUID\"}"

echo ""
echo "==> 4. GraphQL COM token, SEM consent (esperado: errors[].classification = FORBIDDEN)..."
RESP=$(gql "$TOKEN" "$Q_MIN" "$VARS"); BODY=$(echo "$RESP" | head -1)
echo "    $BODY" | head -c 300; echo ""
echo "$BODY" | grep -q '"classification":"FORBIDDEN"' || { echo "ERRO: esperava FORBIDDEN"; exit 1; }

echo ""
echo "==> 5. Concedendo consentimento..."
CONSENT_ID=$(json_field "$(curl -s -X POST "$CONSENT_URL/v1/consents" -H "Content-Type: application/json" \
  -d "{\"patientUuid\":\"$UUID\",\"institutionId\":\"LAB-GQL-001\",\"scope\":\"history:read:own_patients\"}")" "id")
echo "    Consent ID: $CONSENT_ID"

echo ""
echo "==> 6. GraphQL consulta seletiva (esperado: 200, so os 3 campos pedidos, sem 'origin')..."
RESP=$(gql "$TOKEN" "$Q_MIN" "$VARS"); BODY=$(echo "$RESP" | head -1); GQL_BYTES=${#BODY}
echo "    $BODY" | head -c 400; echo ""
echo "$BODY" | grep -q '"totalExams":3' || { echo "ERRO: esperava totalExams=3"; exit 1; }
echo "$BODY" | grep -q '"origin"' && { echo "ERRO: campo 'origin' nao foi pedido e veio na resposta"; exit 1; }

echo ""
echo "==> 7. Filtro examType=GLICEMIA, limit=1 (esperado: 1 exame)..."
Q_F='query($id: ID!){ patientHistory(patientUuid:$id){ exams(examType:"GLICEMIA", limit:1){ examType origin } } }'
BODY=$(gql "$TOKEN" "$Q_F" "$VARS" | head -1)
echo "    $BODY"
[ "$(echo "$BODY" | grep -o '"examType"' | wc -l)" = "1" ] || { echo "ERRO: esperava exatamente 1 exame"; exit 1; }

echo ""
echo "==> 8. Query com profundidade abusiva (esperado: erro de depth, RNF-06)..."
Q_DEEP='{ __schema { types { fields { type { fields { type { fields { name } } } } } } } }'
BODY=$(gql "$TOKEN" "$Q_DEEP" | head -1)
echo "    $(echo "$BODY" | head -c 200)"
echo "$BODY" | grep -qi 'depth' || { echo "ERRO: esperava rejeicao por profundidade"; exit 1; }

echo ""
echo "==> 9. REST equivalente (payload completo) para comparar tamanho..."
REST=$(curl -s -H "Authorization: Bearer $TOKEN" "$HISTORY_URL/v1/patients/$UUID/clinical-timeline?purpose=TREATMENT")
REST_BYTES=${#REST}
echo "    REST     : $REST_BYTES bytes"
echo "    GraphQL  : $GQL_BYTES bytes (consulta seletiva)"
echo "    Reducao  : $(( (REST_BYTES - GQL_BYTES) * 100 / REST_BYTES ))%"

echo ""
echo "==> 10. Revogando consent e consultando de novo (esperado: FORBIDDEN)..."
curl -s -o /dev/null -X DELETE "$CONSENT_URL/v1/consents/$CONSENT_ID"; sleep 1
BODY=$(gql "$TOKEN" "$Q_MIN" "$VARS" | head -1)
echo "$BODY" | grep -q '"classification":"FORBIDDEN"' || { echo "ERRO: esperava FORBIDDEN apos revogacao"; exit 1; }
echo "    FORBIDDEN OK"

echo ""
echo "==> GraphQL OK. Contrato (SDL): $HISTORY_URL/graphql/schema   UI: $HISTORY_URL/graphiql"
