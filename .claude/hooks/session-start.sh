#!/bin/bash
# Installs the toolchains that the remote sandbox image lacks: Node 24 (frontend/.nvmrc) and JDK 25 (docs/development/backend.md).
# The JDK comes from the conda-forge package, verified against its repodata SHA-256.
# Starts the Docker daemon and provides the images of infra/compose.yaml and postgres:18 (for Testcontainers) from mirror.gcr.io.
# Docker failures only warn. The Chromium download needs cdn.playwright.dev and playwright.download.prss.microsoft.com in the network policy.
# Installs Chromium for playwright-cli when missing (warn-only). Also provides ~/.nvm/nvm-exec and installs frontend dependencies. The dev stack is not started here: the backend's
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

export JAVA_HOME="$JDK_DIR"
export PATH="$NODE_DIR/bin:$JDK_DIR/bin:$PATH"

# Written now, before the steps that only warn, so a session keeps Node and the JDK whatever they do.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=\"$JDK_DIR\"" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\"$NODE_DIR/bin:$JDK_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
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
  # Docker Hub rate-limits anonymous pulls, so every image of infra/compose.yaml (and postgres:18 for Testcontainers) comes
  # through mirror.gcr.io and is tagged with its own name. An image with a registry host in its name is pulled as it is.
  images="$( (docker compose -f "$CLAUDE_PROJECT_DIR/infra/compose.yaml" config --images 2>/dev/null; echo postgres:18) | sort -u)"
  for image in $images; do
    if docker image inspect "$image" >/dev/null 2>&1; then
      continue
    fi
    host=""
    case "$image" in */*) host="${image%%/*}" ;; esac
    case "$host" in
      *.*|*:*|localhost)
        docker pull "$image" >/dev/null || echo "Could not pull $image" >&2
        ;;
      *)
        mirrored="mirror.gcr.io/$image"
        case "$image" in */*) ;; *) mirrored="mirror.gcr.io/library/$image" ;; esac
        docker pull "$mirrored" >/dev/null && docker tag "$mirrored" "$image" \
          || echo "Could not pull $image from mirror.gcr.io (Docker Hub rate-limits anonymous pulls)" >&2
        ;;
    esac
  done
else
  echo "Docker daemon did not start; see $HOME/dockerd.log" >&2
fi

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

# Chromium for playwright-cli (the manual tester); installed only when the revision it needs is missing. A failure only warns.
if [ ! -d "$CLAUDE_PROJECT_DIR/frontend/node_modules/@playwright/cli" ]; then
  echo "playwright-cli is not installed; run npm ci in frontend (the manual tester will report NOT RUN)" >&2
else
  (
    cd "$CLAUDE_PROJECT_DIR/frontend"
    chromium_dir="$(npx playwright-cli install-browser chromium --dry-run 2>/dev/null | awk '/chromium-[0-9]+$/ && /Install location/ {print $NF; exit}')"
    if [ -z "$chromium_dir" ] || [ ! -e "$chromium_dir/INSTALLATION_COMPLETE" ]; then
      npx playwright-cli install-browser chromium --with-deps >/dev/null 2>&1
    fi
  ) || echo "Could not install Chromium for playwright-cli (the network policy must allow cdn.playwright.dev and playwright.download.prss.microsoft.com); the manual tester will report NOT RUN" >&2
fi
