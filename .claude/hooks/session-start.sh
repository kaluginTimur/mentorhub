#!/bin/bash
# Installs the toolchain that the remote sandbox image lacks: Node 24 (frontend/.nvmrc).
# JDK 25 (backend.md) is not installed here yet; the image has JDK 21 and no reachable JDK 25 source was found.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

NODE_VERSION="24.22.1"
NODE_DIR="$HOME/.local/node-v${NODE_VERSION}-linux-x64"
BASE_URL="https://nodejs.org/dist/v${NODE_VERSION}"

if [ ! -x "$NODE_DIR/bin/node" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL -o "$tmp/node.tar.xz" "$BASE_URL/node-v${NODE_VERSION}-linux-x64.tar.xz"
  curl -fsSL -o "$tmp/SHASUMS256.txt" "$BASE_URL/SHASUMS256.txt"
  (cd "$tmp" && grep "node-v${NODE_VERSION}-linux-x64.tar.xz\$" SHASUMS256.txt | awk '{print $1"  node.tar.xz"}' | sha256sum -c -)
  mkdir -p "$HOME/.local"
  tar -xJf "$tmp/node.tar.xz" -C "$HOME/.local"
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export PATH=\"$NODE_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi
