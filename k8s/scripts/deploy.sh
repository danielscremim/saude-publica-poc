#!/usr/bin/env bash
# Deploy completo da PoC em um cluster Kubernetes com Istio.
#
# Pre-requisitos:
#   - kubectl >= 1.28
#   - istioctl 1.22+ (ou Istio ja instalado no cluster)
#   - metrics-server instalado (para HPA funcionar) - vem por padrao em GKE/EKS;
#     em kind/minikube/Docker Desktop, instalar separadamente
#   - imagens dos 10 servicos disponiveis no cluster:
#       saude-publica-poc-<servico>:latest
#     (em kind: `kind load docker-image saude-publica-poc-<servico>:latest`)
set -euo pipefail
K8S_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> 1. Verificando ferramentas..."
command -v kubectl  > /dev/null || { echo "kubectl nao encontrado"; exit 1; }
command -v istioctl > /dev/null || { echo "istioctl nao encontrado (https://istio.io/latest/docs/setup/getting-started/#download)"; exit 1; }

echo "==> 2. Instalando Istio (perfil demo, com IngressGateway)..."
if ! kubectl get namespace istio-system > /dev/null 2>&1; then
  istioctl install --set profile=demo -y
fi

echo "==> 3. Aplicando manifests de base + infra..."
kubectl apply -f "$K8S_DIR/00-namespace.yaml"
kubectl apply -f "$K8S_DIR/01-config-secrets.yaml"
kubectl apply -f "$K8S_DIR/10-postgres.yaml"
kubectl apply -f "$K8S_DIR/11-kafka.yaml"
kubectl apply -f "$K8S_DIR/12-kafka-ui.yaml"
kubectl apply -f "$K8S_DIR/13-kong.yaml"

echo "==> 4. Aguardando Postgres e Kafka ficarem prontos..."
kubectl -n saude-poc rollout status statefulset/postgres --timeout=180s
kubectl -n saude-poc rollout status statefulset/kafka    --timeout=180s

echo "==> 5. Aplicando 10 microsservicos..."
for f in "$K8S_DIR"/2[0-9]-*.yaml; do
  echo "    $(basename "$f")"
  kubectl apply -f "$f"
done

echo "==> 6. Aplicando politicas Istio (mTLS STRICT + Circuit Breaker + Gateway)..."
kubectl apply -f "$K8S_DIR/30-istio-mtls.yaml"
kubectl apply -f "$K8S_DIR/31-istio-destination-rules.yaml"
kubectl apply -f "$K8S_DIR/32-istio-gateway.yaml"

echo "==> 7. Aguardando rollouts dos microsservicos..."
for svc in patient-service exam-service lab-service result-service auth-service \
           consent-service history-service audit-service notification-service triage-service; do
  kubectl -n saude-poc rollout status deployment/"$svc" --timeout=300s
done

echo ""
echo "==> Deploy concluido. Verificacoes uteis:"
echo "    kubectl -n saude-poc get pods,svc,hpa"
echo "    istioctl proxy-status"
echo "    istioctl authn tls-check <pod> -n saude-poc   # confirmar mTLS"
echo "    kubectl -n saude-poc get peerauthentication,destinationrule"
echo ""
echo "Para acessar o IngressGateway (porta 80):"
echo "    kubectl -n istio-system get svc istio-ingressgateway"
