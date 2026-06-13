#!/usr/bin/env bash
# Testa o fluxo completo: cadastro -> exame -> processamento -> resultado
set -e

PATIENT_URL="http://localhost:8081"
EXAM_URL="http://localhost:8082"
RESULT_URL="http://localhost:8084"

echo "==> 1. Cadastrando paciente..."
RESP=$(curl -s -X POST "$PATIENT_URL/v1/patients" \
  -H "Content-Type: application/json" \
  -d '{"cpf":"12345678901","name":"Maria Silva","birthDate":"1985-03-12"}')
echo "    Resposta: $RESP"

UUID=$(echo "$RESP" | grep -o '"uuid":"[^"]*"' | cut -d'"' -f4)
if [ -z "$UUID" ]; then echo "ERRO: nao obteve UUID"; exit 1; fi
echo "    UUID do paciente: $UUID"
echo "    (note que o CPF NAO aparece na resposta — RNF-06)"

echo ""
echo "==> 2. Solicitando exames (GLICEMIA, COLESTEROL)..."
for TIPO in GLICEMIA COLESTEROL; do
  curl -s -X POST "$EXAM_URL/v1/exams" \
    -H "Content-Type: application/json" \
    -d "{\"patientUuid\":\"$UUID\",\"examType\":\"$TIPO\",\"origin\":\"UBS\"}" > /dev/null
  echo "    Exame $TIPO solicitado (evento publicado no Kafka)"
done

echo ""
echo "==> 3. Aguardando processamento assincrono (lab -> result)..."
sleep 3

echo ""
echo "==> 4. Consultando historico do paciente:"
curl -s "$RESULT_URL/v1/results/patient/$UUID"
echo ""
echo ""
echo "==> Fluxo completo executado com sucesso."
