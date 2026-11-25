#!/usr/bin/env bash
#
# caddy fmt + caddy validate on every Caddyfile in the repo (#832 phase 1).
#
# The image is the one the community host runs: the caddy service in
# experimental-hybrid/lightsail/docker-compose.yml (tag@sha256, bumped by Dependabot).
# The production Caddyfile loads Cloudflare Origin CA files that are never in git, so
# validate mounts a throwaway self-signed pair at /etc/caddy/certs.
#
# Usage (from repo root, Docker required):
#   ./scripts/ops/check-caddyfiles.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE="${REPO_ROOT}/experimental-hybrid/lightsail/docker-compose.yml"

# First `image: caddy:...` line of the production compose file.
CADDY_IMAGE="$(sed -n 's/^[[:space:]]*image:[[:space:]]*\(caddy:[^[:space:]]*\).*/\1/p' "${COMPOSE}" | head -n 1)"
if [[ -z "${CADDY_IMAGE}" ]]; then
  echo "FAIL: no caddy image in ${COMPOSE}" >&2
  exit 1
fi
if [[ "${CADDY_IMAGE}" != *@sha256:* ]]; then
  echo "FAIL: caddy image is not pinned by digest in ${COMPOSE}: ${CADDY_IMAGE}" >&2
  exit 1
fi

FILES=(
  experimental-hybrid/lightsail/Caddyfile.ezkey-online
  docker/caddy/Caddyfile
  ezkey-admin-ui/docker/Caddyfile
)

if ! docker info >/dev/null 2>&1; then
  echo "error: Docker daemon not available" >&2
  exit 1
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/ezkey-caddyfiles.XXXXXX")"
trap 'rm -rf "${WORK}"' EXIT
mkdir -p "${WORK}/certs"
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 1 \
  -subj /CN=caddy-validate.invalid \
  -keyout "${WORK}/certs/origin-key.pem" -out "${WORK}/certs/origin.pem" 2>/dev/null
chmod 0644 "${WORK}/certs/"*.pem

echo "Pulling ${CADDY_IMAGE}..."
pulled=0
for attempt in 1 2 3 4; do
  if docker pull -q "${CADDY_IMAGE}" >/dev/null; then
    pulled=1
    break
  fi
  sleep $((attempt * 10))
done
if [[ "${pulled}" -ne 1 ]]; then
  echo "FAIL: could not pull ${CADDY_IMAGE}" >&2
  exit 1
fi
docker run --rm "${CADDY_IMAGE}" caddy version

fail=0
for rel in "${FILES[@]}"; do
  f="${REPO_ROOT}/${rel}"
  if [[ ! -f "${f}" ]]; then
    echo "FAIL missing: ${rel}" >&2
    fail=1
    continue
  fi
  echo "→ caddy fmt ${rel}"
  if ! docker run --rm -v "${f}:/etc/caddy/Caddyfile:ro" "${CADDY_IMAGE}" \
    caddy fmt --diff /etc/caddy/Caddyfile >"${WORK}/fmt.out" 2>&1; then
    echo "FAIL fmt: ${rel} (fix with: caddy fmt --overwrite)" >&2
    grep -E '^[-+] ' "${WORK}/fmt.out" >&2 || cat "${WORK}/fmt.out" >&2
    fail=1
  fi
  echo "→ caddy validate ${rel}"
  if ! docker run --rm \
    -v "${f}:/etc/caddy/Caddyfile:ro" \
    -v "${WORK}/certs:/etc/caddy/certs:ro" \
    "${CADDY_IMAGE}" \
    caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >"${WORK}/validate.out" 2>&1; then
    echo "FAIL validate: ${rel}" >&2
    tail -n 5 "${WORK}/validate.out" >&2
    fail=1
  fi
done

if [[ "${fail}" -ne 0 ]]; then
  echo "Caddyfile check FAILED." >&2
  exit 1
fi
echo "Caddyfile check PASS (${#FILES[@]} files, ${CADDY_IMAGE})."
