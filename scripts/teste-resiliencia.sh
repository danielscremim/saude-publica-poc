#!/usr/bin/env bash
# CENARIO D — RESILIENCIA PROVOCADA (plano-teste-estresse.md, alimenta o Resultado 3)
#
# Roda na VM-1, COM a carga ja em andamento a partir da VM-2. Elimina uma replica
# do servico no meio da execucao e cronometra a auto-recuperacao.
#
#   ./scripts/teste-resiliencia.sh                      # history-service, mata aos 180 s
#   SERVICO=consent-service ESPERA=240 ./scripts/teste-resiliencia.sh
#
# O QUE MEDE (RNF-02: self-healing <= 30 s, >= 2 replicas):
#   t0  instante da eliminacao
#   t1  instante em que o pod substituto e criado pelo ReplicaSet
#   t2  instante em que ele passa a Ready (pronto para receber trafego)
#   MTTR = t2 - t0
#
# O erro percebido pelo cliente nao e medido aqui: sai do k6, na VM-2. O casamento
# entre os dois e feito pelo horario — por isso todos os instantes sao gravados em
# UTC com segundos, no mesmo relogio que o coletor usa.
#
# POR QUE ELIMINAR E MELHOR QUE DESLIGAR: `kubectl delete pod` dispara o mesmo
# caminho de um crash (o ReplicaSet reconcilia), mas de forma deterministica e
# datada — o que uma falha espontanea nao oferece.
set -euo pipefail
cd "$(dirname "$0")/.."

NS="${NS:-saude-poc}"
SERVICO="${SERVICO:-history-service}"
ESPERA="${ESPERA:-180}"
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/multitrans.yaml}"

OUT="tests/k6/resultados-infra/resiliencia-$(date +%Y%m%d-%H%M)"
mkdir -p "$OUT"
LOG="$OUT/resiliencia.log"

reg() { echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*" | tee -a "$LOG"; }

reg "servico alvo: $SERVICO | namespace: $NS"
reg "estado inicial:"
kubectl -n "$NS" get pods -l app="$SERVICO" -o wide --no-headers | tee -a "$LOG"

reg "aguardando ${ESPERA}s com a carga em regime antes de provocar a falha..."
sleep "$ESPERA"

# Escolhe uma replica Ready — eliminar uma que ja esta com problema nao mede nada.
ALVO=$(kubectl -n "$NS" get pods -l app="$SERVICO" --no-headers \
        | awk '$2=="2/2" && $3=="Running"{print $1; exit}')
[ -n "$ALVO" ] || { reg "ERRO: nenhuma replica 2/2 Running de $SERVICO"; exit 1; }

ANTES=$(kubectl -n "$NS" get pods -l app="$SERVICO" --no-headers | wc -l)
T0=$(date -u +%s)
reg "t0 — ELIMINANDO $ALVO (replicas antes: $ANTES)"
kubectl -n "$NS" delete pod "$ALVO" --wait=false >/dev/null

# t1: substituto criado. t2: substituto Ready.
T1=""; T2=""
for _ in $(seq 1 120); do
  NOVO=$(kubectl -n "$NS" get pods -l app="$SERVICO" --no-headers 2>/dev/null \
          | grep -v "^$ALVO " | awk '{print $1}' | sort > /tmp/.pods_now; cat /tmp/.pods_now)
  if [ -z "$T1" ] && [ "$(echo "$NOVO" | wc -l)" -ge "$ANTES" ]; then
    T1=$(date -u +%s); reg "t1 — substituto criado (+$((T1-T0))s)"
  fi
  PRONTOS=$(kubectl -n "$NS" get pods -l app="$SERVICO" --no-headers 2>/dev/null \
             | grep -c "2/2 *Running" || true)
  if [ "$PRONTOS" -ge "$ANTES" ]; then
    T2=$(date -u +%s); reg "t2 — todas as $ANTES replicas prontas (+$((T2-T0))s)"
    break
  fi
  sleep 1
done

reg "estado final:"
kubectl -n "$NS" get pods -l app="$SERVICO" -o wide --no-headers | tee -a "$LOG"

{
  echo "servico,replicas,t0_utc,t1_criado_s,t2_pronto_s,mttr_s"
  echo "$SERVICO,$ANTES,$(date -u -d @"$T0" +%Y-%m-%dT%H:%M:%SZ),$([ -n "$T1" ] && echo $((T1-T0)) || echo NA),$([ -n "$T2" ] && echo $((T2-T0)) || echo NA),$([ -n "$T2" ] && echo $((T2-T0)) || echo NA)"
} > "$OUT/mttr.csv"

reg "=== RESULTADO ==="
cat "$OUT/mttr.csv" | tee -a "$LOG"
reg "RNF-02 exige self-healing em ate 30 s."
reg "Cruze o t0 acima com o k6 da VM-2 para obter o erro percebido pelo cliente"
reg "durante a janela — e com hpa.csv/pods.csv para ver a redistribuicao de carga."
echo
echo "Artefatos em $(pwd)/$OUT"
