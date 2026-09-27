#!/usr/bin/env bash
# UChat — Node.js 22 LTS installer (runs INSIDE Ubuntu).
#
# Primary method: official nodejs.org tarball — no apt repo, no gpg, no NodeSource.
# Works on any rootfs arch (arm64/armv7l/x64/x86) and survives partial apt breakage.
# Fallback: NodeSource apt setup when the tarball download is unavailable.
set -uo pipefail

NODE_VERSION="v22.14.0"

if command -v node >/dev/null 2>&1; then
  echo "[uchat] node already present: $(node --version)"
  command -v npm >/dev/null 2>&1 && echo "[uchat] npm: $(npm --version)"
  exit 0
fi

# Map uname -m to nodejs.org directory names.
ARCH="$(uname -m)"
case "$ARCH" in
  aarch64|arm64) NODE_ARCH="arm64" ;;
  x86_64) NODE_ARCH="x64" ;;
  armv7l|armv8l) NODE_ARCH="armv7l" ;;
  i686|i386) NODE_ARCH="x86" ;;
  *)
    echo "[uchat] unsupported architecture: $ARCH"
    exit 1
    ;;
esac

install_from_tarball() {
  local url="https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz"
  local tarball="/tmp/node-dl/node.tar.xz"
  mkdir -p /tmp/node-dl /usr/local/lib/nodejs

  # Offline route (real feature): a staged tarball in the Downloads bind (/root/downloads)
  # skips the network entirely. UChat's e2e uses it, and users with flaky connectivity can
  # pre-place node-${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz there themselves.
  local staged="/root/downloads/node-${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz"
  if [ -f "$staged" ]; then
    echo "[uchat] using staged tarball: $staged"
    cp "$staged" "$tarball"
  else
    echo "[uchat] downloading ${url}"
    curl -fSL --retry 3 --retry-delay 2 -o "$tarball" "$url" || return 1
  fi

  tar -xJf "$tarball" -C /usr/local/lib/nodejs || return 2
  local dist_dir="/usr/local/lib/nodejs/node-${NODE_VERSION}-linux-${NODE_ARCH}"
  [ -d "$dist_dir" ] || return 3
  ln -sf "$dist_dir/bin/node" /usr/local/bin/node
  ln -sf "$dist_dir/bin/npm" /usr/local/bin/npm
  ln -sf "$dist_dir/bin/npx" /usr/local/bin/npx
  ln -sf "$dist_dir/bin/corepack" /usr/local/bin/corepack 2>/dev/null || true
  rm -rf /tmp/node-dl
  return 0
}

install_from_nodesource() {
  echo "[uchat] tarball route failed — trying NodeSource apt setup"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq || true
  apt-get install -y --no-install-recommends gnupg ca-certificates curl xz-utils || return 1
  curl -fsSL https://deb.nodesource.com/setup_22.x -o /tmp/nodesource-setup.sh || return 1
  bash /tmp/nodesource-setup.sh || return 1
  apt-get install -y nodejs || return 1
  rm -f /tmp/nodesource-setup.sh
  return 0
}

if ! install_from_tarball; then
  echo "[uchat] tarball install failed — falling back to NodeSource"
  install_from_nodesource || {
    echo "[uchat] node install failed (both tarball and NodeSource routes)"
    exit 1
  }
fi

command -v node >/dev/null 2>&1 || { echo "[uchat] node NOT found after install"; exit 1; }
echo "[uchat] node: $(node --version)"
echo "[uchat] npm : $(npm --version)"

# pnpm via corepack (bundled with node); non-fatal when unavailable.
if command -v corepack >/dev/null 2>&1; then
  corepack enable >/dev/null 2>&1 || true
  npm install -g pnpm@latest --silent >/dev/null 2>&1 || echo "[uchat] pnpm install skipped (non-fatal)"
fi

echo "[uchat] node install done"
exit 0
