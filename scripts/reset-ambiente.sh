#!/usr/bin/env bash
# RESET DO AMBIENTE — executa na VM-1, antes de CADA rodada do experimento.
#
# POR QUE EXISTE: na bateria de 19/09/2026 duas condicoes variavam entre rodadas
# e contaminavam a media reportada como "media +/- desvio":
#   1. dados acumulados - a rodada 3 rodava contra um banco com centenas de
#      milhares de linhas a mais que a rodada 1 (605.430 pacientes ao final);
#   2. replicas herdadas - a pausa de 180 s do run-2vm.sh e MENOR que a janela
#      de estabilizacao do HPA para reduzir replicas (300 s), entao a rodada 2
#      comecava ja escalada. A degradacao monotonica observada em 19/09
#      (p50 de 374 ms -> 963 ms -> 2372 ms) e a assinatura desse efeito.
#
# Depois deste script toda rodada parte do mesmo estado: base vazia, numero
# minimo de replicas e JVMs recem-iniciadas.
#
#   ./scripts/reset-ambiente.sh              # completo
#   SKIP_TRUNCATE=1 ./scripts/reset-ambiente.sh   # so recria os pods
set -euo pipefail

NS="${NS:-saude-poc}"
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/multitrans.yaml}"

kubectl get ns "$NS" >/dev/null 2>&1 || { echo "namespace $NS nao existe"; exit 1; }

if [ "${SKIP_TRUNCATE:-0}" != "1" ]; then
  echo "==> 1/4 Zerando as tabelas de teste"
  # TRUNCATE (e nao DELETE) porque recupera o espaco imediatamente e nao deixa
  # tuplas mortas para o autovacuum limpar depois - o que seria, ele proprio,
  # uma variavel a mais entre as rodadas.
  kubectl -n "$NS" exec -i postgres-0 -c postgres -- sh -s <<'SQL'
psql -U saude -d patientdb      -c "TRUNCATE patients;"
psql -U saude -d examdb         -c "TRUNCATE exam_requests;"
psql -U saude -d resultdb       -c "TRUNCATE exam_results;"
psql -U saude -d consentdb      -c "TRUNCATE consents;"
psql -U saude -d auditdb        -c "TRUNCATE audit_log, audit_anomaly;"
psql -U saude -d notificationdb -c "TRUNCATE notifications;"
psql -U saude -d triagedb       -c "TRUNCATE triages;"
psql -U saude -d authdb         -c "TRUNCATE auth_clients;"
SQL
fi

echo "==> 2/4 Devolvendo as replicas ao minimo (2)"
# O `rollout restart` sozinho NAO reduz a contagem: se o HPA deixou 10 replicas,
# ele recria as 10. O scale explicito zera essa heranca; com CPU baixa o HPA
# nao volta a subir.
kubectl -n "$NS" scale deploy --all --replicas=2 >/dev/null

echo "==> 3/4 Recriando os pods (JVM limpa, sem estado acumulado)"
kubectl -n "$NS" rollout restart deploy >/dev/null
kubectl -n "$NS" rollout status deploy --timeout=600s

echo "==> 4/4 Verificacao"
NOK=$(kubectl -n "$NS" get pods --no-headers | grep -vc "2/2 *Running" || true)
# kafka-0 e postgres-0 sao StatefulSet; kafka nao tem sidecar (1/1).
echo "    pods fora de 2/2 Running: $NOK  (esperado: 1, o kafka-0)"
kubectl top nodes 2>/dev/null | tail -1

# O HPA leva ate 15 s para publicar as metricas dos pods novos; sem essa espera
# a rodada comeca com o autoscaler ainda cego.
echo "    aguardando 60 s para o ambiente assentar..."
sleep 60
kubectl -n "$NS" get hpa --no-headers | awk '{print "    "$1": "$7" replicas"}'
echo "==> Ambiente pronto."
