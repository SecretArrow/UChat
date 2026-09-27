#!/usr/bin/env bash
# UChat — Claude Code CLI installer (runs INSIDE Ubuntu).
# Requires Node.js (installed by install-runtimes.sh); verifies after install.
# Idempotent: safe to re-run (already-installed environments are only re-linked).
set -uo pipefail

if ! command -v claude >/dev/null 2>&1; then
  if ! command -v npm >/dev/null 2>&1; then
    echo "[uchat] npm missing — installing Node.js first (self-heal)"
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    if [ -f "$SCRIPT_DIR/install-node.sh" ]; then
      bash "$SCRIPT_DIR/install-node.sh" || { echo "[uchat] node bootstrap failed"; exit 1; }
    elif [ -f "$SCRIPT_DIR/install-runtimes.sh" ]; then
      bash "$SCRIPT_DIR/install-runtimes.sh" || { echo "[uchat] runtimes bootstrap failed"; exit 1; }
    else
      echo "[uchat] no node installer available — cannot install claude"
      exit 1
    fi
  fi

  command -v npm >/dev/null 2>&1 || { echo "[uchat] npm still missing after bootstrap"; exit 1; }

  echo "[uchat] installing @anthropic-ai/claude-code globally"
  npm install -g @anthropic-ai/claude-code --silent || exit 1
else
  echo "[uchat] claude already present: $(claude --version 2>/dev/null || echo unknown)"
fi

# npm's global bin dir is NOT always on PATH (tarball node installs keep it under
# /usr/local/lib/nodejs/...). Expose the claude binary in /usr/local/bin so healthcheck
# and every future shell find it. Always runs — re-runs repair missing links.
NPM_GLOBAL_BIN="$(npm prefix -g 2>/dev/null)/bin"
if [ -x "$NPM_GLOBAL_BIN/claude" ] &&
  [ "$(readlink -f "$NPM_GLOBAL_BIN/claude" 2>/dev/null)" != "$(readlink -f /usr/local/bin/claude 2>/dev/null)" ]; then
  echo "[uchat] linking claude into /usr/local/bin"
  ln -sf "$NPM_GLOBAL_BIN/claude" /usr/local/bin/claude 2>/dev/null || true
fi

echo "[uchat] verifying claude"
command -v claude >/dev/null 2>&1 || { echo "[uchat] claude NOT found after install"; exit 1; }
echo "[uchat] claude: $(claude --version 2>/dev/null | head -n 1 || echo present)"
exit 0
