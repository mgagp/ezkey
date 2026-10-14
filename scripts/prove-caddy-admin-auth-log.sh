#!/usr/bin/env bash
# Prove Caddy 2.10.x admin-api access-log posture for #833 C1:
# - caddy validate on Caddyfile.ezkey-online (with temp Origin certs)
# - runtime: site-level log + format filter + log_skip — no token/CSRF/cookie in logs;
#   non-auth routes skipped.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CADDYFILE_SRC="${ROOT}/experimental-hybrid/lightsail/Caddyfile.ezkey-online"
WORKDIR="${TMPDIR:-/tmp}/ezkey-caddy-auth-log-proof-$$"
IMAGE="${CADDY_IMAGE:-caddy:2.10.2-alpine}"
CONTAINER="ezkey-caddy-auth-log-proof-$$"
ARTIFACT_DIR="${ARTIFACT_DIR:-/opt/cursor/artifacts}"
LOG_CAPTURE="${ARTIFACT_DIR}/caddy-admin-auth-log-proof.log"

cleanup() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  rm -rf "${WORKDIR}"
}
trap cleanup EXIT

mkdir -p "${WORKDIR}/certs" "${ARTIFACT_DIR}"

# Self-signed Origin stand-ins so validate / runtime can load the community Caddyfile.
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "${WORKDIR}/certs/origin-key.pem" \
  -out "${WORKDIR}/certs/origin.pem" \
  -days 1 -subj "/CN=admin-api.ezkey.online" >/dev/null 2>&1

# Runtime proof Caddyfile: same log + log_skip shape as admin-api.ezkey.online, HTTP-only.
cat >"${WORKDIR}/Caddyfile.proof" <<'EOF'
:9088 {
	log {
		output stdout
		format filter {
			wrap json
			request>headers delete
			request>uri query {
				delete token
				delete proof
				delete challenge
			}
			resp_headers delete
		}
	}
	@skipAdminAuthLog not path /api/v1/admin/auth/*
	log_skip @skipAdminAuthLog
	respond /api/v1/admin/auth/* 204
	respond 200
}
EOF

echo "==> caddy validate (community Caddyfile.ezkey-online + temp certs)"
docker run --rm \
  -v "${CADDYFILE_SRC}:/etc/caddy/Caddyfile:ro" \
  -v "${WORKDIR}/certs:/etc/caddy/certs:ro" \
  "${IMAGE}" caddy validate --config /etc/caddy/Caddyfile

echo "==> caddy validate (proof Caddyfile)"
docker run --rm \
  -v "${WORKDIR}/Caddyfile.proof:/etc/caddy/Caddyfile:ro" \
  "${IMAGE}" caddy validate --config /etc/caddy/Caddyfile

echo "==> runtime requests against ${IMAGE}"
docker run -d --name "${CONTAINER}" \
  -p 9088:9088 \
  -v "${WORKDIR}/Caddyfile.proof:/etc/caddy/Caddyfile:ro" \
  "${IMAGE}" caddy run --config /etc/caddy/Caddyfile >/dev/null

# Wait for listen
for _ in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:9088/api/v1/admin/auth/login" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

# Auth route with secrets that must not appear in logs.
curl -sf -o /dev/null \
  -H "Cookie: SESSION=super-secret-session" \
  -H "X-Csrf-Token: csrf-secret-value" \
  -H "Authorization: Bearer bearer-secret" \
  "http://127.0.0.1:9088/api/v1/admin/auth/passwordless-wait?token=leak-token&proof=leak-proof&challenge=leak-challenge&ok=1"

# Non-auth route — must be log_skip'd.
curl -sf -o /dev/null \
  -H "X-Csrf-Token: should-not-log-non-auth" \
  "http://127.0.0.1:9088/api/v1/tenants?token=should-not-appear"

sleep 0.5
docker logs "${CONTAINER}" 2>&1 | tee "${LOG_CAPTURE}" >/dev/null

fail=0
if grep -E 'csrf-secret-value|super-secret-session|bearer-secret|leak-token|leak-proof|leak-challenge|should-not-log-non-auth|should-not-appear|/api/v1/tenants' "${LOG_CAPTURE}"; then
  echo "ERROR: sensitive value or skipped route appeared in Caddy logs" >&2
  fail=1
fi

# Auth path should be present (path only — query secrets stripped).
if ! grep -q 'passwordless-wait' "${LOG_CAPTURE}"; then
  echo "ERROR: expected auth access log line for passwordless-wait" >&2
  fail=1
fi

# Headers object should be absent or empty after request>headers delete.
if grep -qE '"headers":\{[^}]' "${LOG_CAPTURE}"; then
  echo "ERROR: request headers still present in JSON access log" >&2
  fail=1
fi

if [[ "${fail}" -ne 0 ]]; then
  echo "---- captured log ----" >&2
  cat "${LOG_CAPTURE}" >&2
  exit 1
fi

echo "OK: validate passed; auth logged without secrets; non-auth skipped."
echo "Log artifact: ${LOG_CAPTURE}"
