#!/usr/bin/env bash
# UChat — OpenCode CLI installer (runs INSIDE Ubuntu).
# Uses the official installer from opencode.ai; verifies the executable after install.
# Idempotent: safe to re-run (already-installed environments are only re-linked).
set -uo pipefail

if command -v opencode >/dev/null 2>&1; then
  echo "[uchat] opencode already present: $(opencode --version 2>/dev/null || echo unknown)"
else
  echo "[uchat] installing OpenCode via official installer"
  curl -fsSL https://opencode.ai/install -o /tmp/opencode-install.sh ||
    { echo "[uchat] opencode installer download failed"; exit 1; }
  bash /tmp/opencode-install.sh ||
    { echo "[uchat] opencode installer failed"; exit 1; }
  rm -f /tmp/opencode-install.sh
fi

# Link into EVERY location future shells may use:
# - /usr/local/bin (standard PATH)
# - $HOME/.opencode/bin (the official installer's home; Proot.kt appends it to PATH too)
# This section always runs, so re-runs repair missing links.
mkdir -p "$HOME/.opencode/bin" /usr/local/bin 2>/dev/null || true
SRC="$(command -v opencode 2>/dev/null || true)"
if [ -z "$SRC" ]; then
  for candidate in "$HOME/.opencode/bin/opencode" "/usr/local/bin/opencode"; do
    if [ -x "$candidate" ]; then
      SRC="$candidate"
      break
    fi
  done
fi
if [ -n "$SRC" ]; then
  echo "[uchat] linking opencode into /usr/local/bin and \$HOME/.opencode/bin"
  for dst in /usr/local/bin/opencode "$HOME/.opencode/bin/opencode"; do
    # Never link a path onto itself (ln -sf would leave a dangling self-reference).
    if [ "$(readlink -f "$SRC" 2>/dev/null)" != "$(readlink -f "$dst" 2>/dev/null)" ]; then
      ln -sf "$SRC" "$dst" 2>/dev/null || true
    fi
  done
fi

echo "[uchat] verifying opencode"
command -v opencode >/dev/null 2>&1 ||
  { echo "[uchat] opencode NOT found after install"; exit 1; }
echo "[uchat] opencode: $(opencode --version 2>/dev/null | head -n 1 || echo present)"
exit 0
