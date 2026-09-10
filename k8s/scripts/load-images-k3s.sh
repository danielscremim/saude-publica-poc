#!/usr/bin/env bash
# Constroi as 10 imagens via docker compose e importa no containerd do k3s (VM-1).
# Nao precisa de registry externo. Rode na raiz do repositorio.
#   ./k8s/scripts/load-images-k3s.sh                 # todas
#   ./k8s/scripts/load-images-k3s.sh history-service # so uma (apos alterar codigo)
set -euo pipefail
cd "$(dirname "$0")/../.."

if [ $# -gt 0 ]; then SERVICES=("$@"); else
  SERVICES=(patient-service exam-service lab-service result-service auth-service
            consent-service history-service audit-service notification-service triage-service)
fi

echo "==> Construindo imagens (docker compose build)..."
docker compose build "${SERVICES[@]}"

for svc in "${SERVICES[@]}"; do
  IMG="saude-publica-poc-${svc}:latest"
  echo "==> Importando $IMG no k3s"
  docker save "$IMG" | sudo k3s ctr images import - >/dev/null
done

echo ""
echo "Imagens no k3s:"; sudo k3s ctr images ls -q | grep 'saude-publica-poc' | sed 's/^/   /'
echo ""
echo "Depois: ./k8s/scripts/deploy.sh   (ou, para um servico ja implantado:"
echo "        kubectl -n saude-poc rollout restart deploy/<servico>)"
