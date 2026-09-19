#!/usr/bin/env bash
# Constroi as 10 imagens via docker compose e carrega no cluster da VM-1.
# Detecta automaticamente se o cluster e k3d (no dentro de container Docker,
# usa `k3d image import`) ou k3s nativo (usa `k3s ctr images import`).
# Nao precisa de registry externo. Rode na raiz do repositorio.
#   ./k8s/scripts/load-images-k3s.sh                 # todas
#   ./k8s/scripts/load-images-k3s.sh history-service # so uma (apos alterar codigo)
#   K3D_CLUSTER=multitrans ./k8s/scripts/load-images-k3s.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

if ! docker info >/dev/null 2>&1; then
  cat <<'MSG' >&2
ERRO: este usuario nao consegue falar com o Docker.
Corrija uma vez e reconecte o SSH:
    sudo usermod -aG docker "$USER"
    exit        # e entre de novo (o grupo so vale em sessao nova)
MSG
  exit 1
fi

if [ $# -gt 0 ]; then SERVICES=("$@"); else
  SERVICES=(patient-service exam-service lab-service result-service auth-service
            consent-service history-service audit-service notification-service triage-service)
fi

echo "==> Construindo imagens (docker compose build)..."
docker compose build "${SERVICES[@]}"

IMAGES=()
for svc in "${SERVICES[@]}"; do IMAGES+=("saude-publica-poc-${svc}:latest"); done

# Deteccao do runtime do cluster.
K3D_CLUSTER="${K3D_CLUSTER:-}"
if [ -z "$K3D_CLUSTER" ] && command -v k3d >/dev/null 2>&1; then
  K3D_CLUSTER="$(k3d cluster list --no-headers 2>/dev/null | awk 'NR==1{print $1}')"
fi

if [ -n "$K3D_CLUSTER" ]; then
  echo "==> Cluster k3d detectado: $K3D_CLUSTER"
  echo "==> Importando ${#IMAGES[@]} imagem(ns) (pode levar alguns minutos)"
  k3d image import "${IMAGES[@]}" -c "$K3D_CLUSTER"
  echo ""
  echo "Imagens no no do k3d:"
  docker exec "k3d-${K3D_CLUSTER}-server-0" crictl images 2>/dev/null \
    | grep 'saude-publica-poc' | sed 's/^/   /' || true
elif command -v k3s >/dev/null 2>&1; then
  echo "==> k3s nativo detectado"
  for img in "${IMAGES[@]}"; do
    echo "==> Importando $img"
    docker save "$img" | sudo k3s ctr images import - >/dev/null
  done
  echo ""
  echo "Imagens no k3s:"; sudo k3s ctr images ls -q | grep 'saude-publica-poc' | sed 's/^/   /'
else
  echo "ERRO: nao encontrei nem k3d nem k3s nesta maquina." >&2
  exit 1
fi

echo ""
echo "Depois: ./k8s/scripts/deploy.sh   (ou, para um servico ja implantado:"
echo "        kubectl -n saude-poc rollout restart deploy/<servico>)"
