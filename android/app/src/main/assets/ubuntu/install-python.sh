#!/bin/bash
# UChat — Python 3 + pip + venv (runs INSIDE Ubuntu). Mostly preinstalled by essentials.
set -uo pipefail
if command -v python3 >/dev/null 2>&1; then
  echo "[uchat] python3 present: $(python3 --version)"
  python3 -m pip --version >/dev/null 2>&1 || apt-get install -y python3-pip || exit 1
  exit 0
fi
apt-get update -qq || true
apt-get install -y --no-install-recommends python3 python3-pip python3-venv || exit 1
python3 --version || exit 1
exit 0
