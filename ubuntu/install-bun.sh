#!/bin/bash
# UChat — Bun installer (runs INSIDE Ubuntu). Official installer, arch-aware.
set -uo pipefail
if command -v bun >/dev/null 2>&1; then
  echo "[uchat] bun already present: $(bun --version)"
  exit 0
fi
curl -fsSL https://bun.sh/install | bash || exit 1
ln -sf "$HOME/.bun/bin/bun" /usr/local/bin/bun 2>/dev/null || true
bun --version || exit 1
exit 0
