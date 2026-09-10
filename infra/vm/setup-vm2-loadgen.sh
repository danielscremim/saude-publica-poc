#!/usr/bin/env bash
# =============================================================================
# VM-2 — GERADOR DE CARGA (somente k6)
# Separada da VM-1 para que o k6 nao dispute CPU com o sistema sob teste
# (validade metodologica: o resultado mede a arquitetura, nao a contencao da VM).
# =============================================================================
set -euo pipefail
sudo apt-get update -y && sudo apt-get install -y curl gnupg ca-certificates jq git
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://dl.k6.io/key.gpg | sudo gpg --dearmor --yes -o /etc/apt/keyrings/k6-archive-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list >/dev/null
sudo apt-get update -y && sudo apt-get install -y k6
grep -q nofile /etc/security/limits.conf || { echo "* soft nofile 65535"; echo "* hard nofile 65535"; } | sudo tee -a /etc/security/limits.conf >/dev/null
echo ""
echo "VM-2 pronta. k6: $(k6 version | head -1)   <-- ANOTE NO TCC"
echo "Uso:  cd tests/k6 && BASE_HOST=<IP_VM1> ./run-2vm.sh"
