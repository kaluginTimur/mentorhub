#!/bin/bash
# Installs the toolchains that the remote sandbox image lacks: Node 24 (frontend/.nvmrc) and JDK 25 (docs/development/backend.md).
# The JDK comes from the conda-forge package, verified against its repodata SHA-256.
# Starts the Docker daemon and provides postgres:18 for Testcontainers. Docker failures only warn.
# Also provides ~/.nvm/nvm-exec and installs frontend dependencies. The dev stack is not started here: the backend's
# spring-boot-docker-compose support starts infra/compose.yaml on `spring-boot:run`.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

NODE_VERSION="24.22.1"
NODE_DIR="$HOME/.local/node-v${NODE_VERSION}-linux-x64"
NODE_BASE_URL="https://nodejs.org/dist/v${NODE_VERSION}"

JDK_DIR="$HOME/.local/jdk-25.0.2"
JDK_URL="https://conda.anaconda.org/conda-forge/linux-64/openjdk-25.0.2-h1602c4f_26.conda"
JDK_SHA256="703aa71462f1b7b8804e1ba6f2663073c47808c693e8a912eb0ce3191ebe90fb"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$HOME/.local"

if [ ! -x "$NODE_DIR/bin/node" ]; then
  curl -fsSL -o "$tmp/node.tar.xz" "$NODE_BASE_URL/node-v${NODE_VERSION}-linux-x64.tar.xz"
  curl -fsSL -o "$tmp/SHASUMS256.txt" "$NODE_BASE_URL/SHASUMS256.txt"
  (cd "$tmp" && grep "node-v${NODE_VERSION}-linux-x64.tar.xz\$" SHASUMS256.txt | awk '{print $1"  node.tar.xz"}' | sha256sum -c -)
  tar -xJf "$tmp/node.tar.xz" -C "$HOME/.local"
fi

if [ ! -x "$JDK_DIR/bin/javac" ]; then
  curl -fsSL -o "$tmp/openjdk.conda" "$JDK_URL"
  echo "$JDK_SHA256  $tmp/openjdk.conda" | sha256sum -c -
  unzip -q "$tmp/openjdk.conda" -d "$tmp/outer"
  python3 -m pip install -q --user zstandard
  python3 - "$tmp/outer" "$tmp/jdk" <<'EOF'
import glob, sys, tarfile, zstandard

outer, dest = sys.argv[1], sys.argv[2]
for package in glob.glob(f"{outer}/pkg-*.tar.zst"):
    with open(package, "rb") as fh:
        with tarfile.open(fileobj=zstandard.ZstdDecompressor().stream_reader(fh), mode="r|") as archive:
            archive.extractall(dest, filter="data")
EOF
  rm -rf "$JDK_DIR"
  mv "$tmp/jdk" "$JDK_DIR"
fi

start_docker() {
  if docker info >/dev/null 2>&1; then
    return 0
  fi
  setsid nohup dockerd > "$HOME/dockerd.log" 2>&1 < /dev/null &
  for _ in $(seq 30); do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# Docker is optional for the session: a failure here must not block it, so it only warns.
if start_docker; then
  if ! docker image inspect postgres:18 >/dev/null 2>&1; then
    docker pull mirror.gcr.io/library/postgres:18 >/dev/null && docker tag mirror.gcr.io/library/postgres:18 postgres:18 \
      || echo "Could not pull postgres:18 from mirror.gcr.io (Docker Hub rate-limits anonymous pulls)" >&2
  fi
else
  echo "Docker daemon did not start; see $HOME/dockerd.log" >&2
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$NODE_DIR/bin:$JDK_DIR/bin:$PATH"

# The commands in docs/development/frontend.md and the allow rules call ~/.nvm/nvm-exec. The sandbox has no nvm, and the
# Node on PATH already satisfies frontend/.nvmrc, so the wrapper only runs its arguments.
if [ ! -e "$HOME/.nvm/nvm-exec" ]; then
  mkdir -p "$HOME/.nvm"
  printf '#!/bin/bash\nexec "$@"\n' > "$HOME/.nvm/nvm-exec"
  chmod +x "$HOME/.nvm/nvm-exec"
fi

# Dependencies for `npm start`; a failure only warns, so the session still starts.
if [ ! -d "$CLAUDE_PROJECT_DIR/frontend/node_modules" ]; then
  (cd "$CLAUDE_PROJECT_DIR/frontend" && npm ci) >/dev/null 2>&1 \
    || echo "npm ci in frontend failed; run it manually" >&2
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=\"$JDK_DIR\"" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\"$NODE_DIR/bin:$JDK_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi
