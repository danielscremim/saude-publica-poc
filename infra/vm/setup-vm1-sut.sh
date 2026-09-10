#!/usr/bin/env bash
# =============================================================================
# VM-1 — SISTEMA SOB TESTE (SUT)
# Instala Docker (build), k3s (Kubernetes, ja traz metrics-server p/ HPA) e
# istioctl. Depois disso use os scripts do repo:
#   ./k8s/scripts/load-images-k3s.sh  ->  ./k8s/scripts/deploy.sh
# Ubuntu 24.04/22.04. Rode como usuario comum com sudo.
# =============================================================================
set -euo pipefail

echo "==> [1/4] Pacotes base"
sudo apt-get update -y && sudo apt-get install -y curl git jq ca-certificates

echo "==> [2/4] Docker (apenas para construir as imagens)"
command -v docker >/dev/null 2>&1 || curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"

echo "==> [3/4] k3s (sem Traefik: o Kong e o gateway; ServiceLB expoe o Kong na porta 8000 do no)"
command -v k3s >/dev/null 2>&1 || \
  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik --write-kubeconfig-mode 644" sh -
mkdir -p "$HOME/.kube" && sudo cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config" && sudo chown "$USER":"$USER" "$HOME/.kube/config"
grep -q KUBECONFIG "$HOME/.bashrc" || echo 'export KUBECONFIG=$HOME/.kube/config' >> "$HOME/.bashrc"
export KUBECONFIG="$HOME/.kube/config"
until kubectl get nodes 2>/dev/null | grep -q " Ready"; do sleep 5; done
kubectl get nodes

echo "==> [4/4] istioctl (o deploy.sh instala o Istio no cluster com perfil demo)"
# Reprodutibilidade: fixe a versao, ex.:  ISTIO_VERSION=1.26.2 ./setup-vm1-sut.sh
ISTIO_VERSION="${ISTIO_VERSION:-}"
if ! command -v istioctl >/dev/null 2>&1; then
  (cd "$HOME" && curl -L https://istio.io/downloadIstio | ISTIO_VERSION="$ISTIO_VERSION" sh -)
  ISTIO_DIR="$(ls -d "$HOME"/istio-* | head -1)"
  sudo cp "$ISTIO_DIR/bin/istioctl" /usr/local/bin/
fi
echo "    istioctl: $(istioctl version --remote=false 2>/dev/null)   <-- ANOTE NO TCC"

echo ""
echo "============================================================"
echo " VM-1 pronta. Saia e entre de novo no SSH (grupo docker)."
echo " Depois, na raiz do repositorio:"
echo "   ./k8s/scripts/load-images-k3s.sh     # ~10-15 min na 1a vez"
echo "   ./k8s/scripts/deploy.sh              # Istio + infra + 10 servicos + politicas"
echo "   kubectl -n saude-poc get pods -w     # aguarde tudo 2/2 Running"
echo " Kong fica em:  http://$(hostname -I | awk '{print $1}'):8000"
echo "============================================================"
