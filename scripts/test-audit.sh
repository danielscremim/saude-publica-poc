#!/usr/bin/env bash
# Testa o audit-service:
#   1. Gera 12 leituras de timeline (mesmo paciente) para acionar anomaly detection
#      (threshold do docker-compose: 10).
#   2. Consulta GET /v1/audit/patient/{uuid} -> espera >= 12 entries.
#   3. Consulta GET /v1/audit/requester/{id}  -> espera >= 12 entries.
#   4. Consulta GET /v1/audit/anomalies       -> espera >= 1 anomaly.
set -e

PATIENT_URL="http://localhost:8081"
EXAM_URL="http://localhost:8082"
AUTH_URL="http://localhost:8085"
CONSENT_URL="http://localhost:8086"
HISTORY_URL="http://localhost:8087"
AUDIT_URL="http://localhost:8088"

json_field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

echo "==> 1. Setup: client + token + paciente + consent..."
curl -s -o /dev/null -X POST "$AUTH_URL/v1/clients" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"clinica-anomaly","clientSecret":"x","institutionId":"INST-AUDIT","scopes":"history:read:own_patients"}'

TOKEN=$(curl -s -X POST "$AUTH_URL/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"clinica-anomaly","clientSecret":"x","scope":"history:read:own_patients"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

PATIENT_RESP=$(curl -s -X POST "$PATIENT_URL/v1/patients" \
  -H "Content-Type: application/json" \
  -d '{"cpf":"77788899900","name":"Carlos Auditado","birthDate":"1975-01-01"}')
UUID=$(json_field "$PATIENT_RESP" "uuid")
echo "    Paciente: $UUID"

curl -s -X POST "$EXAM_URL/v1/exams" -H "Content-Type: application/json" \
  -d "{\"patientUuid\":\"$UUID\",\"examType\":\"GLICEMIA\",\"origin\":\"UBS\"}" > /dev/null

curl -s -X POST "$CONSENT_URL/v1/consents" -H "Content-Type: application/json" \
  -d "{\"patientUuid\":\"$UUID\",\"institutionId\":\"INST-AUDIT\",\"scope\":\"history:read:own_patients\"}" > /dev/null
sleep 2

echo ""
echo "==> 2. Gerando 12 leituras de timeline (deve gerar 12 eventos READ_TIMELINE + 1 anomaly)..."
for i in $(seq 1 12); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
    "$HISTORY_URL/v1/patients/$UUID/clinical-timeline?purpose=TREATMENT")
  printf "    leitura #%02d -> HTTP %s\n" "$i" "$STATUS"
done

echo ""
echo "==> 3. Aguardando audit-service consumir os eventos do Kafka..."
sleep 3

echo ""
echo "==> 4. GET /v1/audit/patient/$UUID (todos os acessos a este paciente)..."
PATIENT_AUDIT=$(curl -s "$AUDIT_URL/v1/audit/patient/$UUID")
COUNT=$(echo "$PATIENT_AUDIT" | grep -o '"eventId"' | wc -l)
echo "    $COUNT entries"
[ "$COUNT" -ge 12 ] || { echo "ERRO: esperava >= 12, recebi $COUNT"; exit 1; }

echo ""
echo "==> 5. GET /v1/audit/requester/clinica-anomaly..."
REQUESTER_AUDIT=$(curl -s "$AUDIT_URL/v1/audit/requester/clinica-anomaly")
COUNT_REQ=$(echo "$REQUESTER_AUDIT" | grep -o '"eventId"' | wc -l)
echo "    $COUNT_REQ entries"
[ "$COUNT_REQ" -ge 12 ] || { echo "ERRO: esperava >= 12, recebi $COUNT_REQ"; exit 1; }

echo ""
echo "==> 6. GET /v1/audit/anomalies (RNF-06: > 10 acessos / 10 min)..."
ANOMALIES=$(curl -s "$AUDIT_URL/v1/audit/anomalies")
echo "    $ANOMALIES"
COUNT_ANOM=$(echo "$ANOMALIES" | grep -o '"id"' | wc -l)
[ "$COUNT_ANOM" -ge 1 ] || { echo "ERRO: esperava >= 1 anomaly, recebi $COUNT_ANOM"; exit 1; }

echo ""
echo "==> Fluxo audit-service OK. Log imutavel + anomaly detection funcionando."
