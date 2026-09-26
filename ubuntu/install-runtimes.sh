#!/usr/bin/env bash
# UChat — Node.js 22 LTS installer (NodeSource) + pnpm (runs INSIDE Ubuntu).
set -uo pipefail

if command -v node >/dev/null 2>&1; then
  echo "[uchat] node already present: $(node --version)"
  node --version || exit 1
  exit 0
fi

echo "[uchat] installing Node.js 22.x via NodeSource"
curl -fsSL https://deb.nodesource.com/setup_22.x -o /tmp/nodesource-setup.sh || exit 1
bash /tmp/nodesource-setup.sh || exit 1
apt-get install -y nodejs || exit 1
rm -f /tmp/nodesource-setup.sh

echo "[uchat] installing pnpm"
npm install -g pnpm@latest --silent || echo "[uchat] pnpm install skipped (non-fatal)"

echo "[uchat] node: $(node --version)"
echo "[uchat] npm : $(npm --version)"
exit 0
