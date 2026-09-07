#!/usr/bin/env bash
#
# Cloud Agent — per-boot startup for the Ezkey monorepo.
#
# Runs on every environment boot. Must be idempotent and return once the stack
# is healthy. Responsibilities:
#   1. Start the Docker daemon (no systemd on this image) and wait for it.
#   2. Disable bridge-netfilter so nested container-to-container traffic works.
#   3. Bring up the full backend stack via the canonical clean-start entrypoint.
#
# The Admin UI Vite dev server runs as a `terminals` entry (see environment.json)
# so its logs stay visible and it can be restarted independently.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
export JAVA_HOME="/usr/lib/jvm/temurin-25-jdk-amd64"

echo "[start] ensuring the Docker daemon is running"
if ! sudo docker info >/dev/null 2>&1; then
  sudo rm -f /var/run/docker.pid 2>/dev/null || true
  sudo nohup dockerd >/tmp/dockerd.log 2>&1 &
  for _ in $(seq 1 60); do
    sudo docker info >/dev/null 2>&1 && break
    sleep 1
  done
fi
# The socket is recreated root-owned on each daemon start; make it usable by the
# ubuntu user so clean-start.sh (which calls `docker` without sudo) works.
sudo chmod 666 /var/run/docker.sock 2>/dev/null || true

if ! docker info >/dev/null 2>&1; then
  echo "[start] ERROR: Docker daemon did not become ready" >&2
  tail -n 40 /tmp/dockerd.log >&2 || true
  exit 1
fi
echo "[start] Docker is ready ($(docker --version))"

echo "[start] disabling bridge-netfilter for nested-Docker connectivity"
# Applied AFTER dockerd starts (it loads br_netfilter and would reset these).
echo 0 | sudo tee /proc/sys/net/bridge/bridge-nf-call-iptables  >/dev/null 2>&1 || true
echo 0 | sudo tee /proc/sys/net/bridge/bridge-nf-call-ip6tables >/dev/null 2>&1 || true

echo "[start] bringing up the Ezkey backend stack (clean-start)"
cd "$REPO_ROOT/ezkey-tests"
./clean-start.sh

echo "[start] backend stack is up and healthy"
