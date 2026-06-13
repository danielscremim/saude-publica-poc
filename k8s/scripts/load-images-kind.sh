#!/usr/bin/env bash
# Carrega as 10 imagens locais (build via docker compose) em um cluster kind.
# Em clusters reais (cloud), use docker push para um registry no lugar disso.
set -euo pipefail
CLUSTER="${KIND_CLUSTER_NAME:-kind}"

SERVICES=(
  patient-service exam-service lab-service result-service auth-service
  consent-service history-service audit-service notification-service triage-service
)

echo "==> Carregando imagens no cluster kind '$CLUSTER'..."
for svc in "${SERVICES[@]}"; do
  IMG="saude-publica-poc-${svc}:latest"
  if docker image inspect "$IMG" > /dev/null 2>&1; then
    echo "    $IMG"
    kind load docker-image "$IMG" --name "$CLUSTER"
  else
    echo "    [SKIP] $IMG nao existe localmente (rode 'docker compose build' antes)"
  fi
done

echo "==> Imagens carregadas. Agora rode k8s/scripts/deploy.sh"
