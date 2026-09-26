#!/usr/bin/env bash
# Copies the canonical Ubuntu scripts (ubuntu/*.sh) into the APK assets
# consumed at runtime. CI runs this before building so a fresh checkout
# always has matching assets.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p android/app/src/main/assets/ubuntu
cp -v ubuntu/*.sh android/app/src/main/assets/ubuntu/
echo "ubuntu assets synced."
