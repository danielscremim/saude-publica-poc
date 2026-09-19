#!/usr/bin/env bash
# =============================================================================
# VM-2 — GERADOR DE CARGA (somente k6)
# Separada da VM-1 para que o k6 nao dispute CPU com o sistema sob teste
# (validade metodologica: o resultado mede a arquitetura, nao a contencao da VM).
#
# Idempotente: se o k6 ja veio instalado na entrega da infraestrutura, o script
# apenas confirma a versao e cuida do resto (git, jq e limite de descritores).
# =============================================================================
set -euo pipefail

echo "==> [1/3] Pacotes de apoio (jq e obrigatorio: run-2vm.sh usa para o resumo.csv)"
sudo apt-get update -y && sudo apt-get install -y curl gnupg ca-certificates jq git

echo "==> [2/3] k6"
if command -v k6 >/dev/null 2>&1; then
  echo "    ja instalado: $(k6 version | head -1)"
else
  sudo mkdir -p /etc/apt/keyrings
  curl -fsSL https://dl.k6.io/key.gpg | sudo gpg --dearmor --yes -o /etc/apt/keyrings/k6-archive-keyring.gpg
  echo "deb [signed-by=/etc/apt/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
    | sudo tee /etc/apt/sources.list.d/k6.list >/dev/null
  sudo apt-get update -y && sudo apt-get install -y k6
fi

echo "==> [3/3] Limite de descritores de arquivo"
# 1000 VUs abrem milhares de sockets. O limite padrao do Ubuntu (1024) faz o k6
# falhar com "too many open files" e o teste registra erro que NAO e do sistema
# sob teste. A versao anterior deste script tinha um bug aqui: o `grep -q nofile`
# casava com as linhas COMENTADAS que ja vem em limits.conf e pulava o ajuste.
if ! grep -qE '^\*\s+(soft|hard)\s+nofile' /etc/security/limits.conf; then
  printf '* soft nofile 65535\n* hard nofile 65535\n' | sudo tee -a /etc/security/limits.conf >/dev/null
  echo "    limites gravados - RECONECTE O SSH para valerem."
else
  echo "    ja configurado."
fi
echo "    limite da sessao atual: $(ulimit -n)   (precisa ser >= 65535 na hora do teste)"

echo ""
echo "VM-2 pronta. k6: $(k6 version | head -1)   <-- ANOTE NO TCC"
echo "Uso:  cd tests/k6 && BASE_HOST=<IP_VM1> ./run-2vm.sh"
