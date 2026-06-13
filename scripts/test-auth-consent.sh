#!/usr/bin/env bash
# Testa o fluxo de autenticacao + consentimento (auth-service + consent-service).
#
# Cenario:
#   1. Registra um client OAuth2 (hospital privado).
#   2. Obtem token JWT via client_credentials.
#   3. Cadastra um paciente (usa patient-service ja existente).
#   4. Concede consentimento do paciente ao hospital.
#   5. Verifica check -> granted=true.
#   6. Revoga e verifica check -> granted=false (evento consent.revoked no Kafka).
set -e

PATIENT_URL="http://localhost:8081"
AUTH_URL="http://localhost:8085"
CONSENT_URL="http://localhost:8086"

# Pequeno helper para extrair campos JSON sem depender de jq.
json_field() {
  # uso: json_field <json> <campo>
  echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

echo "==> 1. Registrando client OAuth2 (Hospital Sao Lucas)..."
CLIENT_RESP=$(curl -s -X POST "$AUTH_URL/v1/clients" \
  -H "Content-Type: application/json" \
  -d '{
        "clientId": "hospital-sao-lucas",
        "clientSecret": "secret-demo-123",
        "institutionId": "HOSP-SP-001",
        "scopes": "history:read:own_patients result:write"
      }')
echo "    Resposta: $CLIENT_RESP"

echo ""
echo "==> 2. Obtendo token JWT (client_credentials, escopo history:read:own_patients)..."
TOKEN_RESP=$(curl -s -X POST "$AUTH_URL/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{
        "clientId": "hospital-sao-lucas",
        "clientSecret": "secret-demo-123",
        "scope": "history:read:own_patients"
      }')
TOKEN=$(echo "$TOKEN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then echo "ERRO: nao obteve token. Resposta: $TOKEN_RESP"; exit 1; fi
echo "    Token (primeiros 40 chars): ${TOKEN:0:40}..."
echo "    Escopo concedido: $(echo $TOKEN_RESP | grep -o '"scope":"[^"]*"')"

echo ""
echo "==> 3. Cadastrando paciente..."
PATIENT_RESP=$(curl -s -X POST "$PATIENT_URL/v1/patients" \
  -H "Content-Type: application/json" \
  -d '{"cpf":"98765432100","name":"Joao Paciente","birthDate":"1980-05-20"}')
UUID=$(json_field "$PATIENT_RESP" "uuid")
if [ -z "$UUID" ]; then echo "ERRO: nao obteve UUID. Resposta: $PATIENT_RESP"; exit 1; fi
echo "    Paciente UUID: $UUID"

echo ""
echo "==> 4. Concedendo consentimento (paciente -> HOSP-SP-001)..."
CONSENT_RESP=$(curl -s -X POST "$CONSENT_URL/v1/consents" \
  -H "Content-Type: application/json" \
  -d "{
        \"patientUuid\":\"$UUID\",
        \"institutionId\":\"HOSP-SP-001\",
        \"scope\":\"history:read:own_patients\"
      }")
echo "    Resposta: $CONSENT_RESP"
CONSENT_ID=$(json_field "$CONSENT_RESP" "id")

echo ""
echo "==> 5. Verificando consentimento (esperado: granted=true)..."
CHECK1=$(curl -s "$CONSENT_URL/v1/consents/check?patientUuid=$UUID&institutionId=HOSP-SP-001")
echo "    $CHECK1"

echo ""
echo "==> 6. Revogando consentimento (publica consent.revoked no Kafka)..."
curl -s -X DELETE "$CONSENT_URL/v1/consents/$CONSENT_ID" > /dev/null
echo "    (consent $CONSENT_ID revogado)"

echo ""
echo "==> 7. Re-verificando consentimento (esperado: granted=false)..."
CHECK2=$(curl -s "$CONSENT_URL/v1/consents/check?patientUuid=$UUID&institutionId=HOSP-SP-001")
echo "    $CHECK2"

echo ""
echo "==> Fluxo auth + consent executado com sucesso."
echo "    Veja o evento consent.revoked em http://localhost:8090 (Kafka UI)."
