#!/usr/bin/env bash
# Executa a bateria oficial a partir da VM-2 contra o Kong da VM-1, com rigor
# estatistico: aquecimento descartado + ROUNDS rodadas por cenario + resumo.csv.
#   BASE_HOST=<IP_VM1> ./run-2vm.sh
#   ROUNDS=3 SCENARIOS="design load1000 graphql" BASE_HOST=... ./run-2vm.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${BASE_HOST:?Defina BASE_HOST=<IP da VM-1>}"
KONG_PORT="${KONG_PORT:-8000}"
ROUNDS="${ROUNDS:-3}"
SCENARIOS="${SCENARIOS:-design load1000 graphql}"
OUT="resultados/$(date +%Y%m%d-%H%M)"; mkdir -p "$OUT"
E=(-e BASE_HOST="$BASE_HOST" -e KONG_PORT="$KONG_PORT")

echo "==> Conectividade com http://$BASE_HOST:$KONG_PORT"
curl -s -o /dev/null -w "   Kong HTTP %{http_code}\n" "http://$BASE_HOST:$KONG_PORT/v1/patients/00000000-0000-0000-0000-000000000000"

echo "==> Aquecimento (2 min, descartado)"
k6 run --quiet "${E[@]}" -e MAX_VUS=50 -e THRESHOLDS=poc load.js >/dev/null 2>&1 || true; sleep 60

run() { # $1=nome $2..=args k6
  local name="$1"; shift
  for r in $(seq 1 "$ROUNDS"); do
    echo "==> $name  rodada $r/$ROUNDS  ($(date +%H:%M:%S))"
    k6 run "${E[@]}" "$@" --summary-export "$OUT/${name}_r${r}.json" | tee "$OUT/${name}_r${r}.log"
    echo "    pausa 3 min (HPA volta ao minimo)"; sleep 180
  done
}

for sc in $SCENARIOS; do
  case "$sc" in
    design)   run design_150vus  -e MAX_VUS=150  -e THRESHOLDS=design load.js ;;
    load1000) run load_1000vus   -e MAX_VUS=1000 -e THRESHOLDS=poc    load.js ;;
    graphql)  run rest_vs_graphql -e EXAMS_PER_PATIENT=30 rest-vs-graphql.js ;;
    stress)   run stress          stress.js ;;
    *) echo "cenario desconhecido: $sc" ;;
  esac
done

echo "==> Consolidando $OUT/resumo.csv"
echo "cenario,rodada,metrica,p50_ms,p95_ms,p99_ms,avg" > "$OUT/resumo.csv"
for f in "$OUT"/*.json; do
  b=$(basename "$f" .json); sc=${b%_r*}; r=${b##*_r}
  jq -r --arg sc "$sc" --arg r "$r" '
    .metrics | to_entries[]
    | select(.key | test("^http_req_duration|^resp_bytes"))
    | [$sc, $r, .key, (.value["p(50)"] // .value.med // ""), (.value["p(95)"] // ""), (.value["p(99)"] // ""), (.value.avg // "")]
    | @csv' "$f" >> "$OUT/resumo.csv"
done
echo "   linhas: $(wc -l < "$OUT/resumo.csv")"
echo "Copie para seu computador:  scp -r $USER@$(hostname -I | awk '{print $1}'):$(pwd)/$OUT ./"
