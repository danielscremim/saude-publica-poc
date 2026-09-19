#!/usr/bin/env bash
# COLETA DE INFRAESTRUTURA - o que o k6 nao enxerga (plano-teste-estresse.md, sec. 5 e 8).
#
# Roda na VM-1, em um terminal proprio, INICIADO ANTES da carga na VM-2.
# Encerra com Ctrl+C ou apos DURACAO segundos.
#
#   export KUBECONFIG="$HOME/.kube/multitrans.yaml"
#   ./tests/k6/coletar-metricas.sh                  # ate Ctrl+C
#   DURACAO=1800 INTERVALO=5 ./tests/k6/coletar-metricas.sh
#
# Saida: tests/k6/resultados-infra/<data>/
#   snapshot-inicial.txt  versoes, nos, pods, HPA, limites
#   nodes.csv             CPU e memoria do no ao longo do tempo
#   pods.csv              CPU e memoria por pod (atribui consumo ao servico)
#   hpa.csv               replicas x tempo -> tempo de reacao do HPA (Resultado 3)
#   pools.csv             HikariCP e threads Tomcat -> EXPLICA o ponto de ruptura
#   kafka-lag.csv         fila acumulada -> evidencia do desacoplamento assincrono
#   eventos.txt           Pending / OOMKilled / CrashLoopBackOff -> criterio de ruptura
#   sar-disco-rede.txt    I/O e retransmissoes (so entra no TCC se acusar saturacao)
#   snapshot-final.txt
set -euo pipefail
cd "$(dirname "$0")"

NS="${NS:-saude-poc}"
INTERVALO="${INTERVALO:-5}"
DURACAO="${DURACAO:-0}"          # 0 = ate Ctrl+C
OUT="resultados-infra/$(date +%Y%m%d-%H%M)"
mkdir -p "$OUT"

command -v kubectl >/dev/null || { echo "kubectl nao encontrado"; exit 1; }
kubectl get ns "$NS" >/dev/null 2>&1 || { echo "namespace $NS nao existe (exportou o KUBECONFIG?)"; exit 1; }

echo "==> Coletando em $OUT (intervalo ${INTERVALO}s). Ctrl+C para encerrar."

# ---------------------------------------------------------------- snapshot inicial
{
  echo "=== data ==="; date -Is
  echo; echo "=== versoes ==="
  kubectl version 2>/dev/null | sed 's/^/  /'
  kubectl -n istio-system get deploy istiod -o jsonpath='  istiod: {.spec.template.spec.containers[0].image}{"\n"}' 2>/dev/null || true
  echo; echo "=== nos ==="; kubectl get nodes -o wide
  echo; echo "=== capacidade do no ==="; kubectl describe node | grep -A8 "Allocatable:"
  echo; echo "=== pods ==="; kubectl -n "$NS" get pods -o wide
  echo; echo "=== hpa ==="; kubectl -n "$NS" get hpa
  echo; echo "=== requests/limits ==="
  kubectl -n "$NS" get deploy -o custom-columns=\
'NOME:.metadata.name,CPU_REQ:.spec.template.spec.containers[0].resources.requests.cpu,CPU_LIM:.spec.template.spec.containers[0].resources.limits.cpu,MEM_REQ:.spec.template.spec.containers[0].resources.requests.memory,MEM_LIM:.spec.template.spec.containers[0].resources.limits.memory'
  echo; echo "=== disco ==="; df -h /
} > "$OUT/snapshot-inicial.txt" 2>&1

# ---------------------------------------------------------------- Prometheus (pools/threads)
# Descobre o Service do Prometheus entregue e abre um port-forward SO para a coleta
# (fora do caminho de medicao da carga, entao nao contamina o resultado).
PROM_PF_PID=""
PROM="http://127.0.0.1:19090"
PROM_SVC="$(kubectl -n monitoring get svc -o name 2>/dev/null \
            | grep -i prometheus | grep -viE 'operated|alertmanager|grafana|operator' | head -1 || true)"
if [ -n "$PROM_SVC" ]; then
  kubectl -n monitoring port-forward "$PROM_SVC" 19090:9090 >/dev/null 2>&1 &
  PROM_PF_PID=$!
  sleep 3
  echo "    Prometheus: $PROM_SVC (port-forward 19090)"
else
  echo "    AVISO: Service do Prometheus nao encontrado no namespace monitoring."
  echo "           pools.csv ficara vazio - HikariCP e Tomcat nao serao coletados."
fi

promq() {  # $1 = consulta PromQL -> linhas "metrica,valor"
  [ -n "$PROM_PF_PID" ] || return 0
  curl -sG --max-time 5 "$PROM/api/v1/query" --data-urlencode "query=$1" 2>/dev/null \
    | jq -r '.data.result[]? | [(.metric.service // .metric.application // .metric.pod // "-"), .value[1]] | @csv' 2>/dev/null || true
}

# ---------------------------------------------------------------- sar (disco e rede)
if command -v sar >/dev/null 2>&1; then
  ( sar -d -n DEV,ETCP "$INTERVALO" > "$OUT/sar-disco-rede.txt" 2>&1 & echo $! > "$OUT/.sarpid" ) || true
else
  echo "    AVISO: sysstat ausente (sudo apt-get install -y sysstat) - sem disco/rede." \
    | tee "$OUT/sar-disco-rede.txt"
fi

encerrar() {
  echo ""
  echo "==> Encerrando coleta..."
  [ -n "$PROM_PF_PID" ] && kill "$PROM_PF_PID" 2>/dev/null || true
  [ -f "$OUT/.sarpid" ] && kill "$(cat "$OUT/.sarpid")" 2>/dev/null && rm -f "$OUT/.sarpid" || true
  {
    echo "=== data ==="; date -Is
    echo; echo "=== pods (final) ==="; kubectl -n "$NS" get pods -o wide
    echo; echo "=== hpa (final) ==="; kubectl -n "$NS" get hpa
    echo; echo "=== reinicios e ultimo motivo de termino ==="
    kubectl -n "$NS" get pods -o custom-columns=\
'POD:.metadata.name,RESTARTS:.status.containerStatuses[0].restartCount,MOTIVO:.status.containerStatuses[0].lastState.terminated.reason'
    echo; echo "=== disco ==="; df -h /
  } > "$OUT/snapshot-final.txt" 2>&1
  echo "==> Artefatos em $(pwd)/$OUT"
  ls -1 "$OUT" | sed 's/^/    /'
  exit 0
}
trap encerrar INT TERM

echo "ts,no,cpu_cores,mem" > "$OUT/nodes.csv"
echo "ts,pod,cpu,mem" > "$OUT/pods.csv"
echo "ts,hpa,replicas_atuais,replicas_desejadas,alvo_cpu" > "$OUT/hpa.csv"
echo "ts,metrica,servico,valor" > "$OUT/pools.csv"
echo "ts,grupo,topico,lag" > "$OUT/kafka-lag.csv"
: > "$OUT/eventos.txt"

INICIO=$(date +%s); CICLO=0
while :; do
  TS=$(date -Is)

  kubectl top nodes --no-headers 2>/dev/null \
    | awk -v t="$TS" '{print t","$1","$2","$4}' >> "$OUT/nodes.csv" || true

  kubectl -n "$NS" top pods --no-headers 2>/dev/null \
    | awk -v t="$TS" '{print t","$1","$2","$3}' >> "$OUT/pods.csv" || true

  kubectl -n "$NS" get hpa --no-headers 2>/dev/null \
    | awk -v t="$TS" '{print t","$1","$7","$6","$3}' >> "$OUT/hpa.csv" || true

  # Pools, threads e memoria da JVM: o "porque" da degradacao.
  for par in \
    "hikari_pendentes:hikaricp_connections_pending{namespace=\"$NS\"}" \
    "hikari_ativas:hikaricp_connections_active{namespace=\"$NS\"}" \
    "hikari_max:hikaricp_connections_max{namespace=\"$NS\"}" \
    "tomcat_threads_ocupadas:tomcat_threads_busy_threads{namespace=\"$NS\"}" \
    "tomcat_threads_total:tomcat_threads_current_threads{namespace=\"$NS\"}" \
    "jvm_heap_usado:jvm_memory_used_bytes{namespace=\"$NS\",area=\"heap\"}" \
    "gc_pausa_total:jvm_gc_pause_seconds_sum{namespace=\"$NS\"}" ; do
    nome="${par%%:*}"; query="${par#*:}"
    promq "$query" | sed "s/^/$TS,$nome,/" >> "$OUT/pools.csv" || true
  done

  # Kafka lag a cada 3 ciclos (o comando e caro).
  if [ $((CICLO % 3)) -eq 0 ]; then
    kubectl -n "$NS" exec kafka-0 -c kafka -- \
      /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
      --all-groups --describe 2>/dev/null \
      | awk -v t="$TS" 'NR>1 && $6 ~ /^[0-9]+$/ {print t","$1","$2","$6}' >> "$OUT/kafka-lag.csv" || true
  fi

  # Eventos que caracterizam ruptura de infraestrutura.
  kubectl -n "$NS" get events --field-selector type!=Normal \
    --sort-by=.lastTimestamp --no-headers 2>/dev/null | tail -5 \
    | sed "s/^/$TS /" >> "$OUT/eventos.txt" || true
  kubectl -n "$NS" get pods --no-headers 2>/dev/null \
    | grep -E 'Pending|OOMKilled|CrashLoopBackOff|Error' \
    | sed "s/^/$TS RUPTURA /" >> "$OUT/eventos.txt" || true

  CICLO=$((CICLO + 1))
  [ "$DURACAO" -gt 0 ] && [ $(($(date +%s) - INICIO)) -ge "$DURACAO" ] && encerrar
  sleep "$INTERVALO"
done
