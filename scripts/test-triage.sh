#!/usr/bin/env bash
# Testa o triage-service:
#   1. Cria paciente.
#   2. Registra triagem com sinais vitais NORMAIS sem priority -> classificador -> GREEN ou BLUE.
#   3. Registra triagem com sinais CRITICOS (SpO2 baixo) sem priority -> RED automatico.
#   4. Registra com prioridade EXPLICITA -> respeitada.
#   5. Lista historico por paciente.
set -e

PATIENT_URL="http://localhost:8081"
TRIAGE_URL="http://localhost:8090"

json_field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

echo "==> 1. Cadastrando paciente..."
PATIENT_RESP=$(curl -s -X POST "$PATIENT_URL/v1/patients" \
  -H "Content-Type: application/json" \
  -d '{"cpf":"22233344455","name":"Daniela Triagem","birthDate":"1988-04-22"}')
UUID=$(json_field "$PATIENT_RESP" "uuid")
echo "    Paciente UUID: $UUID"

echo ""
echo "==> 2. Triagem com sinais NORMAIS (sem priority -> classificador automatico)..."
NORMAL=$(curl -s -X POST "$TRIAGE_URL/v1/triages" -H "Content-Type: application/json" \
  -d "{
        \"patientUuid\":\"$UUID\",
        \"performedBy\":\"enf. Joana\",
        \"unit\":\"UBS-VILA-MARIANA\",
        \"bloodPressureSystolic\":120, \"bloodPressureDiastolic\":80,
        \"heartRate\":75, \"respiratoryRate\":16,
        \"temperature\":36.5, \"oxygenSaturation\":98, \"painLevel\":1,
        \"complaint\":\"Consulta de rotina\"
      }")
PRIORITY_NORMAL=$(json_field "$NORMAL" "priority")
echo "    Prioridade calculada: $PRIORITY_NORMAL"
[ "$PRIORITY_NORMAL" = "BLUE" ] || [ "$PRIORITY_NORMAL" = "GREEN" ] || { echo "ERRO: esperava BLUE/GREEN, recebi $PRIORITY_NORMAL"; exit 1; }

echo ""
echo "==> 3. Triagem com SpO2 critico (80) -> RED automatico..."
CRITICO=$(curl -s -X POST "$TRIAGE_URL/v1/triages" -H "Content-Type: application/json" \
  -d "{
        \"patientUuid\":\"$UUID\",
        \"performedBy\":\"enf. Carlos\",
        \"unit\":\"UBS-VILA-MARIANA\",
        \"bloodPressureSystolic\":110, \"bloodPressureDiastolic\":70,
        \"heartRate\":120, \"respiratoryRate\":28,
        \"temperature\":37.2, \"oxygenSaturation\":80, \"painLevel\":7,
        \"complaint\":\"Falta de ar grave\"
      }")
PRIORITY_CRIT=$(json_field "$CRITICO" "priority")
echo "    Prioridade calculada: $PRIORITY_CRIT"
[ "$PRIORITY_CRIT" = "RED" ] || { echo "ERRO: esperava RED, recebi $PRIORITY_CRIT"; exit 1; }

echo ""
echo "==> 4. Triagem com prioridade EXPLICITA ORANGE (sobrescreve classificador)..."
EXPLICITO=$(curl -s -X POST "$TRIAGE_URL/v1/triages" -H "Content-Type: application/json" \
  -d "{
        \"patientUuid\":\"$UUID\",
        \"performedBy\":\"enf. Joana\",
        \"unit\":\"UBS-VILA-MARIANA\",
        \"painLevel\":3, \"complaint\":\"Dor de cabeca persistente\",
        \"priority\":\"ORANGE\"
      }")
PRIORITY_EXP=$(json_field "$EXPLICITO" "priority")
echo "    Prioridade gravada: $PRIORITY_EXP"
[ "$PRIORITY_EXP" = "ORANGE" ] || { echo "ERRO: esperava ORANGE, recebi $PRIORITY_EXP"; exit 1; }

echo ""
echo "==> 5. GET /v1/triages/patient/$UUID (esperado: >= 3 registros, mais recentes primeiro)..."
HIST=$(curl -s "$TRIAGE_URL/v1/triages/patient/$UUID")
COUNT=$(echo "$HIST" | grep -o '"id"' | wc -l)
echo "    $COUNT triagens no historico"
# >= 3 porque o paciente e idempotente por CPF; runs acumulam triagens (esperado).
[ "$COUNT" -ge 3 ] || { echo "ERRO: esperava >= 3, recebi $COUNT"; exit 1; }

echo ""
echo "==> Fluxo triage-service OK."
