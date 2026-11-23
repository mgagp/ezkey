#!/bin/bash
# Claude Code cloud sessions only: per-session runtime for the Ezkey stack.
#
# Does nothing outside a Claude Code cloud container (CLAUDE_CODE_REMOTE != true): local Claude Code
# on the Mac or Rootbeer, Cursor (local or Cursor Cloud, which has its own .cursor/setup/), and every
# other tool are unaffected.
#
# In a cloud container it:
#   1. installs JDK 25 and socat if the environment's setup script did not (fallback only);
#   2. starts dockerd (the container has no init system; without it the Testcontainers unit tests
#      are silently skipped and the clean-start stack cannot run);
#   3. relays the session HTTPS proxy onto the Docker bridge, so image builds can download
#      (docker/README.md § Building behind an HTTPS proxy);
#   4. exports JAVA_HOME (JDK 25), PATH and EZKEY_BUILD_HTTPS_PROXY for the session.
#
# Idempotent and quick when everything already runs. A container restart mid-session kills dockerd
# and the relay and changes the proxy port, and no SessionStart fires then: rerun this script by
# hand (`bash .claude/hooks/session-start.sh`) and use the exports it prints.
# Canon: docs/CLAUDE_CODE_CLOUD.md.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

log() { echo "[ezkey cloud] $*" >&2; }
SUDO=""
[ "$(id -u)" -ne 0 ] && SUDO="sudo"

jdk=/usr/lib/jvm/java-25-openjdk-amd64
missing=()
[ -x "$jdk/bin/java" ] || missing+=(openjdk-25-jdk-headless)
command -v socat >/dev/null || missing+=(socat)
if [ "${#missing[@]}" -gt 0 ]; then
  log "installing ${missing[*]} (prefer the cloud environment setup script for this)"
  $SUDO env DEBIAN_FRONTEND=noninteractive apt-get update -qq >/dev/null
  $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${missing[@]}" >/dev/null
fi

if command -v dockerd >/dev/null && ! docker info >/dev/null 2>&1; then
  log "starting dockerd"
  $SUDO setsid nohup dockerd >/tmp/dockerd.log 2>&1 < /dev/null &
  for _ in $(seq 1 60); do docker info >/dev/null 2>&1 && break; sleep 1; done
  docker info >/dev/null 2>&1 || log "dockerd did not start; see /tmp/dockerd.log"
fi

build_proxy=""
proxy="${HTTPS_PROXY:-${https_proxy:-}}"
if [ -n "$proxy" ] && docker info >/dev/null 2>&1; then
  hostport="${proxy#*://}"; hostport="${hostport%%/*}"; port="${hostport##*:}"
  gw="$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}' 2>/dev/null || true)"
  if [ -n "$gw" ]; then
    if ! pgrep -f "socat TCP-LISTEN:${port},bind=${gw}" >/dev/null; then
      log "relaying the session proxy on ${gw}:${port} for image builds"
      setsid nohup socat "TCP-LISTEN:${port},bind=${gw},fork,reuseaddr" "TCP:${hostport}" \
        >/tmp/socat-build-proxy.log 2>&1 < /dev/null &
    fi
    build_proxy="http://${gw}:${port}"
  fi
fi

exports=("export JAVA_HOME=$jdk")
if [ -x /opt/node24/bin/node ]; then
  exports+=("export PATH=/opt/node24/bin:$jdk/bin:\$PATH")
else
  exports+=("export PATH=$jdk/bin:\$PATH")
fi
[ -n "$build_proxy" ] && exports+=("export EZKEY_BUILD_HTTPS_PROXY=$build_proxy")

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  printf '%s\n' "${exports[@]}" >> "$CLAUDE_ENV_FILE"
else
  printf '%s\n' "${exports[@]}"
fi
