#!/usr/bin/env bash
# UChat — OpenCode CLI installer (runs INSIDE Ubuntu).
# Uses the official installer from opencode.ai; verifies the executable after install.
set -uo pipefail

if command -v opencode >/dev/null 2>&1; then
  echo "[uchat] opencode already present: $(opencode --version 2>/dev/null || echo unknown)"
  exit 0
fi

echo "[uchat] installing OpenCode via official installer"
curl -fsSL https://opencode.ai/install -o /tmp/opencode-install.sh || exit 1
bash /tmp/opencode-install.sh || exit 1
rm -f /tmp/opencode-install.sh

# Make sure common install locations are on PATH for future shells.
if ! command -v opencode >/dev/null 2>&1; then
  echo "[uchat] linking opencode into /usr/local/bin"
  for candidate in "$HOME/.opencode/bin/opencode" "/usr/local/bin/opencode"; do
    if [ -x "$candidate" ]; then
      ln -sf "$candidate" /usr/local/bin/opencode
      break
    fi
  done
fi

echo "[uchat] verifying opencode"
command -v opencode >/dev/null 2>&1 || { echo "[uchat] opencode NOT found after install"; exit 1; }
opencode --version || { echo "[uchat] opencode --version failed"; exit 1; }
exit 0
