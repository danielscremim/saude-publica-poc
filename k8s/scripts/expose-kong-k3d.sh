#!/usr/bin/env bash
# Publica o Kong (NodePort 30800) na porta 8000 da VM-1, para que a VM-2 gere
# carga por uma conexao TCP direta.
#
# POR QUE NAO kubectl port-forward:
# a execucao [3] registrada no cabecalho de tests/k6/load.js foi feita por
# port-forward e adicionou ~300 ms por requisicao - 3 dos 4 thresholds falharam
# com o sistema saudavel. Repetir isso invalida a bateria inteira.
#
# Caminho preferido: adicionar a porta ao load balancer do k3d (o mesmo que ja
# publica 80/443). Alternativa, se o cluster nao tiver load balancer: DNAT no
# kernel do host - mesma tecnica que o Docker usa, sem proxy em espaco de usuario.
set -euo pipefail

HOST_PORT="${HOST_PORT:-8000}"
NODE_PORT="${NODE_PORT:-30800}"

command -v k3d >/dev/null 2>&1 || { echo "k3d nao encontrado nesta maquina."; exit 1; }
docker info >/dev/null 2>&1 || { echo "sem acesso ao Docker: sudo usermod -aG docker \$USER e reconecte."; exit 1; }

CLUSTER="${K3D_CLUSTER:-$(k3d cluster list --no-headers | awk 'NR==1{print $1}')}"
[ -n "$CLUSTER" ] || { echo "nenhum cluster k3d encontrado."; exit 1; }
echo "==> Cluster: $CLUSTER"

if ss -lnt "( sport = :$HOST_PORT )" | grep -q LISTEN; then
  echo "==> Porta $HOST_PORT ja esta escutando no host. Nada a fazer."
else
  LB="k3d-${CLUSTER}-serverlb"
  if docker ps --format '{{.Names}}' | grep -qx "$LB"; then
    echo "==> Adicionando $HOST_PORT -> NodePort $NODE_PORT no load balancer do k3d"
    echo "    (o container $LB sera recriado; as portas 80/443 ja existentes sao preservadas)"
    k3d cluster edit "$CLUSTER" --port-add "${HOST_PORT}:${NODE_PORT}@loadbalancer"
  else
    echo "==> Cluster sem load balancer. Usando DNAT no host."
    NODE_IP="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "k3d-${CLUSTER}-server-0")"
    echo "    no: $NODE_IP:$NODE_PORT"
    sudo sysctl -w net.ipv4.ip_forward=1 >/dev/null
    sudo iptables -t nat -C PREROUTING -p tcp --dport "$HOST_PORT" -j DNAT --to-destination "${NODE_IP}:${NODE_PORT}" 2>/dev/null || \
      sudo iptables -t nat -A PREROUTING -p tcp --dport "$HOST_PORT" -j DNAT --to-destination "${NODE_IP}:${NODE_PORT}"
    sudo iptables -t nat -C POSTROUTING -d "$NODE_IP" -p tcp --dport "$NODE_PORT" -j MASQUERADE 2>/dev/null || \
      sudo iptables -t nat -A POSTROUTING -d "$NODE_IP" -p tcp --dport "$NODE_PORT" -j MASQUERADE
    echo "    ATENCAO: regra nao persiste apos reboot da VM. Rode este script de novo."
  fi
fi

echo "==> Firewall"
if command -v ufw >/dev/null 2>&1 && sudo ufw status 2>/dev/null | grep -q "Status: active"; then
  sudo ufw allow "$HOST_PORT"/tcp
else
  echo "    ufw inativo - nada a liberar."
fi

echo "==> Teste local (esperado: um codigo HTTP, nao 000)"
IP="$(hostname -I | awk '{print $1}')"
for alvo in "http://127.0.0.1:${HOST_PORT}" "http://${IP}:${HOST_PORT}"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
    "${alvo}/v1/patients/00000000-0000-0000-0000-000000000000" || true)
  echo "    ${alvo} -> HTTP ${code}"
done

echo ""
echo "Se os dois responderam (404 e o esperado: o paciente nao existe), rode na VM-2:"
echo "    cd tests/k6 && BASE_HOST=${IP} ./run-2vm.sh"
