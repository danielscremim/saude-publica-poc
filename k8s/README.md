# Deploy Kubernetes + Istio

Esta pasta contém os manifests para deploy da PoC em **qualquer cluster Kubernetes
com Istio instalado**. Todos os 10 microsserviços + infra (Postgres, Kafka, Kafka UI, Kong)
+ políticas Istio (mTLS STRICT, Circuit Breaker, Gateway) que satisfazem os RNFs do TCC.

> **Status atual:** manifests escritos e validados estaticamente. **Não executados** em
> cluster real ainda — pré-requisito é ter um cluster K8s 1.28+ com Istio 1.22+ e
> metrics-server. Tempo estimado de setup do cluster local: 20–40 min.

---

## Como os manifests cobrem os RNFs

| RNF | Manifest | Como satisfaz |
|---|---|---|
| **RNF-01** Escalabilidade (P95 ≤ 500 ms, HPA 70% CPU) | `2X-*.yaml` (HPA por serviço) | HPA v2 com `averageUtilization: 70`, range 2–10 réplicas |
| **RNF-02** Disponibilidade (≥ 2 réplicas, Circuit Breaker 50%, self-healing ≤ 30s) | `2X-*.yaml` + `31-istio-destination-rules.yaml` | `replicas: 2`; `outlierDetection.maxEjectionPercent: 50`, `baseEjectionTime: 30s` |
| **RNF-03** Segurança (mTLS STRICT) | `30-istio-mtls.yaml` | `PeerAuthentication mode: STRICT` em todo o namespace |
| **RNF-05** Interoperabilidade (history ≤ 800 ms) | `32-istio-gateway.yaml` | `timeout: 800ms` na rota do clinical-timeline |
| **RNF-06** Consent (rate-limit 60/h/paciente/instituição) | `13-kong.yaml` (rate-limiting plugin) | Kong rate-limiting (atual: 120/min global; refinar por paciente é evolução) |

---

## Estrutura

```
k8s/
├── 00-namespace.yaml                   # ns saude-poc com istio-injection=enabled
├── 01-config-secrets.yaml              # ConfigMap shared-config + Secret shared-secrets
├── 10-postgres.yaml                    # StatefulSet + init DBs via ConfigMap
├── 11-kafka.yaml                       # KRaft StatefulSet (1 replica)
├── 12-kafka-ui.yaml
├── 13-kong.yaml                        # ingress norte-sul (2 replicas)
├── 20-patient-service.yaml             # Deployment(2) + Service + HPA
├── 21-exam-service.yaml
├── 22-lab-service.yaml
├── 23-result-service.yaml
├── 24-auth-service.yaml
├── 25-consent-service.yaml
├── 26-history-service.yaml
├── 27-audit-service.yaml
├── 28-notification-service.yaml
├── 29-triage-service.yaml
├── 30-istio-mtls.yaml                  # PeerAuthentication STRICT
├── 31-istio-destination-rules.yaml     # 10 DestinationRules com circuit breaker
├── 32-istio-gateway.yaml               # Gateway + VirtualService (alternativa ao Kong)
└── scripts/
    ├── deploy.sh                       # aplica tudo na ordem certa
    ├── teardown.sh                     # remove o namespace
    └── load-images-kind.sh             # carrega imagens locais no kind
```

---

## Pré-requisitos

1. **kubectl** ≥ 1.28
   ```bash
   kubectl version --client
   ```

2. **istioctl** ≥ 1.22 — [instalação oficial](https://istio.io/latest/docs/setup/getting-started/#download)
   ```bash
   curl -L https://istio.io/downloadIstio | sh -
   sudo mv istio-*/bin/istioctl /usr/local/bin/
   ```

3. **Cluster Kubernetes**. Algumas opções:
   - **kind** (recomendado para local): `brew install kind && kind create cluster --name saude-poc`
   - **Docker Desktop**: Settings → Kubernetes → Enable
   - **minikube**: `minikube start --memory=8192 --cpus=4`
   - **GKE / EKS / AKS**: configurar `kubectl` no contexto desejado

4. **metrics-server** (para HPA funcionar):
   ```bash
   # kind / Docker Desktop
   kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
   # Se estiver em kind, editar Deployment metrics-server e adicionar args:
   #   --kubelet-insecure-tls
   ```

5. **Imagens dos microsserviços** disponíveis ao cluster.
   - Local: `cd .. && docker compose build` gera as imagens com prefixo `saude-publica-poc-`.
   - Em kind: `bash k8s/scripts/load-images-kind.sh` carrega as 10 imagens no cluster.
   - Em cloud: push para um registry (ex: `docker tag saude-publica-poc-patient-service:latest <registry>/saude/patient:latest && docker push ...`) e editar `image:` nos manifests.

---

## Deploy

```bash
# Tudo de uma vez (instala Istio se necessário):
bash k8s/scripts/deploy.sh
```

O script faz:
1. Instala Istio (perfil `demo`) se ainda não estiver no cluster
2. Cria namespace + ConfigMaps/Secrets
3. Sobe Postgres (StatefulSet) e Kafka (KRaft)
4. Aguarda infra ficar pronta
5. Sobe Kong + Kafka UI
6. Sobe os 10 microsserviços (Deployment + Service + HPA)
7. Aplica políticas Istio: PeerAuthentication STRICT, DestinationRules, Gateway

---

## Validar os RNFs

```bash
# RNF-02: replicas em pé
kubectl -n saude-poc get deploy
# Esperado: cada deploy com READY 2/2

# RNF-01: HPA configurado
kubectl -n saude-poc get hpa
# Esperado: cada HPA com TARGETS mostrando uso atual / 70%

# RNF-03: mTLS STRICT ativo
kubectl -n saude-poc get peerauthentication
istioctl authn tls-check $(kubectl -n saude-poc get pod -l app=history-service -o jsonpath='{.items[0].metadata.name}').saude-poc patient-service.saude-poc.svc.cluster.local
# Esperado: STATUS = OK, AUTHN POLICY = default-mtls-strict, SERVER mTLS = STRICT

# RNF-02: DestinationRules com circuit breaker
kubectl -n saude-poc get destinationrule
kubectl -n saude-poc describe destinationrule result-service | grep -A5 outlierDetection

# Métricas / dashboards (perfil demo já instala Prometheus, Grafana, Kiali, Jaeger):
istioctl dashboard kiali
istioctl dashboard grafana
istioctl dashboard jaeger
```

---

## Acessando os serviços

### Via Kong (mantém o gateway de aplicação)

```bash
# kind / Docker Desktop: kong fica como LoadBalancer mas sem LB real -> use port-forward
kubectl -n saude-poc port-forward svc/kong 8000:8000

# Testa
curl -X POST http://localhost:8000/v1/patients \
  -H "Content-Type: application/json" \
  -d '{"cpf":"12345678901","name":"Maria","birthDate":"1985-03-12"}'
```

### Via Istio IngressGateway

```bash
kubectl -n istio-system port-forward svc/istio-ingressgateway 8080:80

curl http://localhost:8080/v1/patients/<uuid>
```

---

## Diferenças vs docker-compose

| Aspecto | docker-compose | Kubernetes |
|---|---|---|
| Réplicas por serviço | 1 | 2 (escala até 10 via HPA) |
| Segurança entre serviços | rede Docker plana | mTLS STRICT (Istio) |
| Circuit breaker | nenhum | DestinationRule.outlierDetection |
| Auto-escala | não | HPA a 70% CPU |
| Self-healing | restart manual | liveness probe + outlier ejection |
| Observabilidade | logs stdout | Prometheus + Grafana + Jaeger + Kiali |
| Storage | volume Docker | PVC (StatefulSet) |

---

## Limpeza

```bash
bash k8s/scripts/teardown.sh           # remove só o namespace saude-poc
istioctl uninstall --purge -y          # remove o Istio
kind delete cluster --name saude-poc   # se usou kind
```
