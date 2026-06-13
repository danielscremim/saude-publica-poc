#!/usr/bin/env bash
# Remove a PoC do cluster (mantem o Istio instalado).
set -euo pipefail
K8S_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Removendo manifests Istio e namespace saude-poc..."
kubectl delete -f "$K8S_DIR/32-istio-gateway.yaml"            --ignore-not-found
kubectl delete -f "$K8S_DIR/31-istio-destination-rules.yaml"  --ignore-not-found
kubectl delete -f "$K8S_DIR/30-istio-mtls.yaml"               --ignore-not-found

# Apaga tudo do namespace; PVCs do postgres/kafka ficam (compose-friendly) ate o
# namespace ser deletado explicitamente.
kubectl delete namespace saude-poc --ignore-not-found

echo "==> Pronto. Para remover o Istio tambem: istioctl uninstall --purge -y"
