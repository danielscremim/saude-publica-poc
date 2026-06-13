#!/usr/bin/env bash
# Testa o notification-service:
#   1. Cria paciente e solicita exame.
#   2. lab processa e publica exam.completed.
#   3. notification-service consome o evento e cria notificacao (channel LOG).
#   4. GET /v1/notifications/patient/{uuid} retorna a notificacao gerada.
set -e

PATIENT_URL="http://localhost:8081"
EXAM_URL="http://localhost:8082"
NOTIF_URL="http://localhost:8089"

json_field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

echo "==> 1. Cadastrando paciente..."
PATIENT_RESP=$(curl -s -X POST "$PATIENT_URL/v1/patients" \
  -H "Content-Type: application/json" \
  -d '{"cpf":"11122233344","name":"Beatriz Notificacao","birthDate":"1995-07-15"}')
UUID=$(json_field "$PATIENT_RESP" "uuid")
echo "    Paciente UUID: $UUID"

echo ""
echo "==> 2. Solicitando exame GLICEMIA (vai gerar exam.completed apos ~ms)..."
curl -s -X POST "$EXAM_URL/v1/exams" -H "Content-Type: application/json" \
  -d "{\"patientUuid\":\"$UUID\",\"examType\":\"GLICEMIA\",\"origin\":\"UBS\"}" > /dev/null

echo "    Aguardando processamento (lab -> result -> notification)..."
sleep 4

echo ""
echo "==> 3. GET /v1/notifications/patient/$UUID..."
NOTIFS=$(curl -s "$NOTIF_URL/v1/notifications/patient/$UUID")
echo "    $NOTIFS"
COUNT=$(echo "$NOTIFS" | grep -o '"id"' | wc -l)
[ "$COUNT" -ge 1 ] || { echo "ERRO: esperava >= 1 notificacao, recebi $COUNT"; exit 1; }

echo ""
echo "==> 4. Envio manual via POST /v1/notifications..."
SEND=$(curl -s -X POST "$NOTIF_URL/v1/notifications" -H "Content-Type: application/json" \
  -d "{\"patientUuid\":\"$UUID\",\"channel\":\"LOG\",\"subject\":\"Teste manual\",\"message\":\"Notificacao enviada via PoC test\"}")
echo "    $SEND"

echo ""
echo "==> Fluxo notification-service OK."
