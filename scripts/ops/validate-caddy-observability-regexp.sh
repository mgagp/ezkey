#!/usr/bin/env bash
#
# Validate Caddyfile syntax (including the #846 path_regexp observability deny)
# against Caddy 2.10.x via the official Docker image.
#
# Lightsail Caddyfiles reference Origin CA PEMs that are not in git. This script
# uses `caddy adapt` (parse + adapt to JSON) rather than full `caddy validate`
# provisioning, then asserts the observability regexp is present.
#
# Usage (from repo root):
#   ./scripts/ops/validate-caddy-observability-regexp.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Pin to 2.10 for the regex matcher contract stated in #846.
CADDY_IMAGE="${CADDY_VALIDATE_IMAGE:-caddy:2.10-alpine}"

FILES=(
  "${REPO_ROOT}/experimental-hybrid/lightsail/Caddyfile.ezkey-online"
  "${REPO_ROOT}/experimental-hybrid/lightsail/Caddyfile"
  "${REPO_ROOT}/docker/caddy/Caddyfile"
)

if ! docker info >/dev/null 2>&1; then
  echo "error: Docker daemon not available" >&2
  exit 1
fi

echo "Pulling ${CADDY_IMAGE} (Caddy 2.10 regex validation)..."
docker pull "${CADDY_IMAGE}" >/dev/null

fail=0
for f in "${FILES[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL missing: ${f}" >&2
    fail=1
    continue
  fi
  echo "→ caddy adapt --config $(basename "$f")"
  # adapt parses the Caddyfile (including path_regexp) without requiring TLS PEMs.
  if ! docker run --rm \
    -v "${f}:/etc/caddy/Caddyfile:ro" \
    "${CADDY_IMAGE}" \
    caddy adapt --config /etc/caddy/Caddyfile >/tmp/caddy-adapt-out.json 2>/tmp/caddy-adapt-err.txt; then
    echo "FAIL adapt: ${f}" >&2
    cat /tmp/caddy-adapt-err.txt >&2
    fail=1
    continue
  fi
  if ! grep -q 'path_regexp' /tmp/caddy-adapt-out.json \
    && ! grep -q 'monitoring' /tmp/caddy-adapt-out.json; then
    # docker/caddy and lightsail both emit a matcher; require monitoring in adapted JSON.
    echo "FAIL: adapted JSON missing observability matcher for ${f}" >&2
    fail=1
    continue
  fi
  if ! grep -E 'monitoring|actuator' /tmp/caddy-adapt-out.json >/dev/null; then
    echo "FAIL: adapted JSON missing monitoring/actuator for ${f}" >&2
    fail=1
    continue
  fi
  echo "  OK ${f}"
done

# Source-level: confirm the observability regex is present in community Caddyfile.
if ! grep -qE 'path_regexp \^/\(monitoring\|actuator\)' \
  "${REPO_ROOT}/experimental-hybrid/lightsail/Caddyfile.ezkey-online"; then
  echo "FAIL: observability path_regexp missing from Caddyfile.ezkey-online" >&2
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  echo "Caddy observability validation FAILED." >&2
  exit 1
fi
echo "Caddy observability validation PASS (Caddy ${CADDY_IMAGE} adapt)."
exit 0
