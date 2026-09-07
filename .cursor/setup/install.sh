#!/usr/bin/env bash
#
# Cloud Agent — durable setup for the Ezkey monorepo.
#
# Idempotent. Installs the system toolchain that is NOT preinstalled on the
# default Cloud Agent image and prepares project dependencies:
#   - Docker Engine + Compose plugin      (runs the ezkey-tests/clean-start stack)
#   - fuse-overlayfs                      (Docker storage driver that works when
#                                          the VM root is itself an overlay mount)
#   - Temurin JDK 25 + Maven              (Java reactor build; project requires 25)
#   - Admin UI Node dependencies          (Vite dev server)
#
# Per-boot runtime concerns (starting dockerd, bringing up the stack) live in
# .cursor/setup/start.sh — not here. See .cursor/environment.json.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

export DEBIAN_FRONTEND=noninteractive
# --force-conf* keeps existing conffiles (e.g. /etc/fuse.conf) without an
# interactive prompt that would otherwise abort the fuse3 postinstall.
APT_OPTS=(-y -o Dpkg::Options::=--force-confold -o Dpkg::Options::=--force-confdef)

echo "[install] apt update"
sudo apt-get update -qq

echo "[install] Docker Engine, Compose plugin, fuse-overlayfs, base tools"
sudo apt-get install "${APT_OPTS[@]}" \
  docker.io docker-compose-v2 fuse-overlayfs \
  wget ca-certificates gnupg

echo "[install] Temurin JDK 25 + Maven (Adoptium apt repo)"
sudo install -d -m 0755 /etc/apt/keyrings
if [ ! -f /etc/apt/keyrings/adoptium.gpg ]; then
  wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
fi
. /etc/os-release
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb ${VERSION_CODENAME} main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null
sudo apt-get update -qq
sudo apt-get install "${APT_OPTS[@]}" temurin-25-jdk maven

JAVA25="/usr/lib/jvm/temurin-25-jdk-amd64"
echo "[install] make JDK 25 the default java/javac"
sudo update-alternatives --install /usr/bin/java  java  "$JAVA25/bin/java"  2500
sudo update-alternatives --install /usr/bin/javac javac "$JAVA25/bin/javac" 2500
sudo update-alternatives --set java  "$JAVA25/bin/java"
sudo update-alternatives --set javac "$JAVA25/bin/javac"
# Persist JAVA_HOME for interactive shells (scripts/build.sh honours it too).
if ! grep -q 'JAVA_HOME=/usr/lib/jvm/temurin-25' "$HOME/.bashrc" 2>/dev/null; then
  echo "export JAVA_HOME=$JAVA25" >> "$HOME/.bashrc"
fi

echo "[install] Docker daemon config: legacy graphdriver + fuse-overlayfs"
# The VM root filesystem is an overlay mount; the default overlay2 / containerd
# overlayfs snapshotter cannot mount overlay-on-overlay (EINVAL). fuse-overlayfs
# avoids that and is far faster than vfs.
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json >/dev/null <<'JSON'
{
  "features": { "containerd-snapshotter": false },
  "storage-driver": "fuse-overlayfs"
}
JSON

echo "[install] allow the ubuntu user to use the docker socket"
sudo usermod -aG docker ubuntu || true

echo "[install] persist bridge-netfilter sysctl (see start.sh for why)"
sudo tee /etc/sysctl.d/99-ezkey-docker-bridge.conf >/dev/null <<'SYSCTL'
# Nested Docker: keep same-bridge container traffic as pure L2 so it bypasses
# the host iptables FORWARD chain (whose Docker DROP rules otherwise blackhole
# container-to-container connections in this environment).
net.bridge.bridge-nf-call-iptables=0
net.bridge.bridge-nf-call-ip6tables=0
SYSCTL

echo "[install] Admin UI dependencies (npm ci)"
cd "$REPO_ROOT/ezkey-admin-ui"
npm ci

echo "[install] Admin UI API client (Orval codegen from committed openapi-spec.json)"
# src/generated/ is gitignored; the Vite dev server imports these modules, so
# generate them at install time (otherwise the first page load fails to resolve
# @/generated/admin-api/*).
npm run generate:api

echo "[install] Playwright Chromium + system libraries (for the Admin UI e2e suite)"
# scripts/run-ui-tests.sh only runs `playwright install chromium` (browser binary,
# no OS libraries). On a fresh Linux VM the browser fails to launch without system
# deps (libnss3, libgbm, ...). Install both here so the e2e suite works on a cold
# start. Non-fatal: a browser hiccup must not block bringing up the stack.
npx playwright install --with-deps chromium || echo "[install] WARN: Playwright browser install failed; run 'npx playwright install --with-deps chromium' in ezkey-admin-ui before e2e tests"

echo "[install] done"
