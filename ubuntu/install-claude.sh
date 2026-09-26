#!/usr/bin/env bash
# UChat — Claude Code CLI installer (runs INSIDE Ubuntu).
# Requires Node.js (installed by install-runtimes.sh); verifies after install.
set -uo pipefail

if command -v claude >/dev/null 2>&1; then
  echo "[uchat] claude already present: $(claude --version 2>/dev/null || echo unknown)"
  exit 0
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "[uchat] npm missing — run install-runtimes.sh first"
  exit 1
fi

echo "[uchat] installing @anthropic-ai/claude-code globally"
npm install -g @anthropic-ai/claude-code --silent || exit 1

echo "[uchat] verifying claude"
command -v claude >/dev/null 2>&1 || { echo "[uchat] claude NOT found after install"; exit 1; }
claude --version || { echo "[uchat] claude --version failed"; exit 1; }
exit 0
