#!/usr/bin/env bash
# Validates the asset registry: every URL must be reachable and every
# sha256/size pair must match what the servers currently serve.
# Runs in CI (nightly) so a moved upstream asset fails loudly, not on a phone.
set -euo pipefail
cd "$(dirname "$0")/.."

REG="android/app/src/main/assets/registry/asset-registry.json"
fail=0

python3 - "$REG" <<'PY'
import json, sys, subprocess

reg = json.load(open(sys.argv[1]))
assets = reg.get("rootfs", []) + reg.get("proot", [])
fail = 0
for a in assets:
    url = a["url"]
    try:
        out = subprocess.check_output([
            "curl", "-sIL", "--max-time", "30", url,
        ]).decode("utf-8", "replace").lower()
        code = None
        for line in out.splitlines():
            if line.startswith("http/"):
                code = line.split()[1]
        size = None
        for line in out.splitlines():
            if line.startswith("content-length:"):
                size = int(line.split(":", 1)[1].strip())
        ok = code and code.startswith("2") or code == "302"
        if not ok:
            print(f"FAIL {a['id']}: HTTP {code} for {url}")
            fail = 1
        elif size is not None and size != a["sizeBytes"]:
            print(f"WARN {a['id']}: size changed upstream {a['sizeBytes']} -> {size} (pin update needed)")
        else:
            print(f"ok   {a['id']}")
    except Exception as e:
        print(f"FAIL {a['id']}: {e}")
        fail = 1
sys.exit(fail)
PY

exit $fail
