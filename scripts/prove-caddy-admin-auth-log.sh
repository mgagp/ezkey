#!/usr/bin/env bash
# Prove Caddy 2.10.x admin-api access-log posture for #833 C1:
# - caddy validate on the real Caddyfile.ezkey-online (with temp Origin certs)
# - runtime proof derived from that file's log + log_skip block (not a divergent inline copy)
# - no token/CSRF/cookie/waiterSecret/code in logs; non-auth /api/v1/admin/tenants skipped
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

# Self-signed Origin stand-ins so validate can load the community Caddyfile.
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "${WORKDIR}/certs/origin-key.pem" \
  -out "${WORKDIR}/certs/origin.pem" \
  -days 1 -subj "/CN=admin-api.ezkey.online" >/dev/null 2>&1

# Extract the admin-api site's log { … } … log_skip block from the real Caddyfile.
python3 - "${CADDYFILE_SRC}" "${WORKDIR}/Caddyfile.proof" <<'PY'
import re, sys
src, dst = sys.argv[1], sys.argv[2]
text = open(src, encoding="utf-8").read()
m = re.search(
    r"admin-api\.ezkey\.online\s*\{(.*?)\n\}",
    text,
    flags=re.S,
)
if not m:
    sys.exit("admin-api.ezkey.online site block not found in " + src)
site = m.group(1)
# Keep only the access-log directives (log + named matcher + log_skip).
log_bits = []
# log { ... }
lm = re.search(r"\n\tlog\s*\{.*?\n\t\}", site, flags=re.S)
if not lm:
    sys.exit("log { ... } block not found under admin-api.ezkey.online")
log_bits.append(lm.group(0).strip("\n"))
# @skipAdminAuthLog ...
sm = re.search(r"\n\t@skipAdminAuthLog[^\n]*", site)
if not sm:
    sys.exit("@skipAdminAuthLog matcher not found")
log_bits.append(sm.group(0).strip("\n"))
# log_skip ...
sk = re.search(r"\n\tlog_skip[^\n]*", site)
if not sk:
    sys.exit("log_skip directive not found")
log_bits.append(sk.group(0).strip("\n"))
body = "\n".join(log_bits)
proof = f""":9088 {{
{body}
	respond /api/v1/admin/auth/* 204
	respond /api/v1/admin/tenants* 200
	respond 404
}}
"""
open(dst, "w", encoding="utf-8").write(proof)
print("Wrote proof Caddyfile derived from real admin-api log block →", dst)
PY

echo "==> caddy validate (community Caddyfile.ezkey-online + temp certs)"
docker run --rm \
  -v "${CADDYFILE_SRC}:/etc/caddy/Caddyfile:ro" \
  -v "${WORKDIR}/certs:/etc/caddy/certs:ro" \
  "${IMAGE}" caddy validate --config /etc/caddy/Caddyfile

echo "==> caddy validate (proof Caddyfile derived from real log block)"
docker run --rm \
  -v "${WORKDIR}/Caddyfile.proof:/etc/caddy/Caddyfile:ro" \
  "${IMAGE}" caddy validate --config /etc/caddy/Caddyfile

echo "==> runtime requests against ${IMAGE}"
docker run -d --name "${CONTAINER}" \
  -p 9088:9088 \
  -v "${WORKDIR}/Caddyfile.proof:/etc/caddy/Caddyfile:ro" \
  "${IMAGE}" caddy run --config /etc/caddy/Caddyfile >/dev/null

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
  "http://127.0.0.1:9088/api/v1/admin/auth/passwordless-wait?token=leak-token&proof=leak-proof&challenge=leak-challenge&waiterSecret=leak-waiter&code=leak-code&ok=1"

# Non-auth admin route — must be log_skip'd (#833 n2).
curl -sf -o /dev/null \
  -H "X-Csrf-Token: should-not-log-non-auth" \
  "http://127.0.0.1:9088/api/v1/admin/tenants?token=should-not-appear&waiterSecret=nope"

sleep 0.5
docker logs "${CONTAINER}" 2>&1 | tee "${LOG_CAPTURE}" >/dev/null

fail=0
if grep -E 'csrf-secret-value|super-secret-session|bearer-secret|leak-token|leak-proof|leak-challenge|leak-waiter|leak-code|should-not-log-non-auth|should-not-appear|/api/v1/admin/tenants' "${LOG_CAPTURE}"; then
  echo "ERROR: sensitive value or skipped route appeared in Caddy logs" >&2
  fail=1
fi

if ! grep -q 'passwordless-wait' "${LOG_CAPTURE}"; then
  echo "ERROR: expected auth access log line for passwordless-wait" >&2
  fail=1
fi

if grep -qE '"headers":\{[^}]' "${LOG_CAPTURE}"; then
  echo "ERROR: request headers still present in JSON access log" >&2
  fail=1
fi

if [[ "${fail}" -ne 0 ]]; then
  echo "---- captured log ----" >&2
  cat "${LOG_CAPTURE}" >&2
  exit 1
fi

echo "OK: validate passed; auth logged without secrets; non-auth /api/v1/admin/tenants skipped."
echo "Log artifact: ${LOG_CAPTURE}"
