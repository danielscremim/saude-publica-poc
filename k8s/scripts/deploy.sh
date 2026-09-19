#!/usr/bin/env bash
# Deploy completo da PoC em um cluster Kubernetes com Istio.
#
# Pre-requisitos:
#   - kubectl >= 1.28 e KUBECONFIG apontando para o cluster
#       (VM-1 Multitrans: export KUBECONFIG="$HOME/.kube/multitrans.yaml")
#   - Istio JA INSTALADO no cluster (o script so instala se nao existir, e
#     nesse caso precisa de istioctl). Na VM-1 Multitrans o Istio 1.30.4 ja
#     veio na entrega da infraestrutura -> o passo 2 e pulado automaticamente.
#   - metrics-server instalado (para o HPA) - presente no k3s/k3d
#   - imagens dos 10 servicos ja carregadas no cluster:
#       ./k8s/scripts/load-images-k3s.sh
set -euo pipefail
K8S_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> 1. Verificando ferramentas..."
command -v kubectl > /dev/null || { echo "kubectl nao encontrado"; exit 1; }
kubectl version --request-timeout=10s >/dev/null 2>&1 || {
  echo "kubectl nao conseguiu falar com o cluster. Exportou o KUBECONFIG?"; exit 1; }

echo "==> 2. Istio..."
if kubectl get namespace istio-system > /dev/null 2>&1; then
  echo "    istio-system ja existe - usando o Istio da infraestrutura (nao reinstala)."
  kubectl -n istio-system get deploy istiod -o jsonpath='    istiod: {.spec.template.spec.containers[0].image}{"\n"}' 2>/dev/null || true
else
  command -v istioctl > /dev/null || {
    echo "Istio ausente e istioctl nao encontrado."
    echo "Instale: curl -L https://istio.io/downloadIstio | ISTIO_VERSION=1.30.4 sh -"
    exit 1; }
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
kubectl -n saude-poc rollout status statefulset/postgres --timeout=300s
kubectl -n saude-poc rollout status statefulset/kafka    --timeout=300s

echo "==> 5. Aplicando 10 microsservicos..."
for f in "$K8S_DIR"/2[0-9]-*.yaml; do
  echo "    $(basename "$f")"
  kubectl apply -f "$f"
done

echo "==> 6. Aplicando politicas Istio (mTLS STRICT + Circuit Breaker + Gateway)..."
kubectl apply -f "$K8S_DIR/30-istio-mtls.yaml"
kubectl apply -f "$K8S_DIR/31-istio-destination-rules.yaml"
kubectl apply -f "$K8S_DIR/32-istio-gateway.yaml"

echo "==> 7. Observabilidade (PodMonitor + alertas) ..."
if kubectl get crd podmonitors.monitoring.coreos.com >/dev/null 2>&1; then
  kubectl apply -f "$K8S_DIR/40-monitoring.yaml"
  echo "    aplicado. Confira se o Prometheus adotou os alvos:"
  echo "    kubectl -n saude-poc get podmonitor,prometheusrule"
else
  echo "    CRDs do Prometheus Operator ausentes - pulando 40-monitoring.yaml."
  echo "    (sem kube-prometheus-stack, as metricas de aplicacao nao serao coletadas)"
fi

echo "==> 8. Aguardando rollouts dos microsservicos..."
for svc in patient-service exam-service lab-service result-service auth-service \
           consent-service history-service audit-service notification-service triage-service; do
  kubectl -n saude-poc rollout status deployment/"$svc" --timeout=300s
done

echo ""
echo "==> Deploy concluido. Verificacoes uteis:"
echo "    kubectl -n saude-poc get pods,svc,hpa"
echo "    kubectl -n saude-poc get pods -o wide   # esperado 2/2 (app + sidecar Envoy)"
echo "    kubectl -n saude-poc get peerauthentication,destinationrule"
echo ""
echo "Expor o Kong na porta 8000 da VM-1 (para a VM-2 gerar carga):"
echo "    ./k8s/scripts/expose-kong-k3d.sh"
