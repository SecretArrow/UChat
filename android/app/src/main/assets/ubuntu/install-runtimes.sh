#!/usr/bin/env bash
# UChat — development runtimes installer (runs INSIDE Ubuntu).
# Node.js 22 LTS + pnpm. Python 3 + pip ship with install-essentials.sh.
#
# Node is installed by DELEGATING to install-node.sh (official nodejs.org tarball route,
# arch-aware, with offline staged-tarball + NodeSource fallback). The previous direct
# NodeSource-only path here was fragile (apt repo setup breaks easily on mobile networks
# and on partially-provisioned rootfs trees).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[uchat] installing Node.js 22.x via install-node.sh (tarball route)"
if [ -f "$SCRIPT_DIR/install-node.sh" ]; then
  bash "$SCRIPT_DIR/install-node.sh" || exit 1
else
  echo "[uchat] install-node.sh is missing next to $0 — cannot install Node.js"
  exit 1
fi

# Exit-code contract: runtimes are only done when node + npm really resolve.
command -v node >/dev/null 2>&1 || { echo "[uchat] node NOT found after install"; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "[uchat] npm NOT found after install"; exit 1; }

echo "[uchat] installing pnpm"
if command -v corepack >/dev/null 2>&1 && corepack enable pnpm >/dev/null 2>&1; then
  echo "[uchat] pnpm enabled via corepack"
else
  npm install -g pnpm@latest --silent >/dev/null 2>&1 ||
    echo "[uchat] pnpm install skipped (non-fatal)"
fi

# Tarball-based node installs keep global bins outside /usr/local/bin — corepack/npm may
# "succeed" while pnpm is still unreachable from a fresh PATH. Locate pnpm (PATH first, then
# npm's global bin dir) and expose it in /usr/local/bin, mirroring install-node.sh's links.
PNPM_BIN="$(command -v pnpm 2>/dev/null || true)"
if [ -z "$PNPM_BIN" ] && command -v npm >/dev/null 2>&1; then
  candidate="$(npm prefix -g 2>/dev/null)/bin/pnpm"
  [ -x "$candidate" ] && PNPM_BIN="$candidate"
fi
if [ -n "$PNPM_BIN" ] &&
  [ "$(readlink -f "$PNPM_BIN" 2>/dev/null)" != "$(readlink -f /usr/local/bin/pnpm 2>/dev/null)" ]; then
  ln -sf "$PNPM_BIN" /usr/local/bin/pnpm 2>/dev/null || true
fi

echo "[uchat] node: $(node --version)"
echo "[uchat] npm : $(npm --version)"
command -v pnpm >/dev/null 2>&1 && echo "[uchat] pnpm: $(pnpm --version 2>/dev/null || echo unknown)"
echo "[uchat] runtimes done"
exit 0
