#!/usr/bin/env bash
#
# Validate Caddyfile syntax (including the #846 path_regexp observability deny)
# against Caddy 2.10.x via the official Docker image, then smoke the regexp with
# case / double-slash / semicolon path variants.
#
# Also verifies every site block imports the file's observability-deny snippet
# (Patrick craft — a site that omits the import would silently expose /monitoring).
#
# Usage (from repo root):
#   ./scripts/ops/validate-caddy-observability-regexp.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Pin digest (Caddy 2.10 alpine) — override with CADDY_VALIDATE_IMAGE if needed.
CADDY_IMAGE="${CADDY_VALIDATE_IMAGE:-caddy:2.10-alpine@sha256:4c6e91c6ed0e2fa03efd5b44747b625fec79bc9cd06ac5235a779726618e530d}"
EXPECTED_REGEXP='(?i)^/+(monitoring|actuator)([/;].*)?$'

FILES=(
  "${REPO_ROOT}/experimental-hybrid/lightsail/Caddyfile.ezkey-online"
  "${REPO_ROOT}/docker/caddy/Caddyfile"
)

TMPDIR_WORK="$(mktemp -d "${TMPDIR:-/tmp}/ezkey-caddy-obs.XXXXXX")"
cleanup() {
  rm -rf "${TMPDIR_WORK}"
  docker rm -f caddy-obs-regexp-smoke >/dev/null 2>&1 || true
}
trap cleanup EXIT

if ! docker info >/dev/null 2>&1; then
  echo "error: Docker daemon not available" >&2
  exit 1
fi

echo "Pulling ${CADDY_IMAGE} (Caddy 2.10 regex validation, digest-pinned)..."
docker pull "${CADDY_IMAGE}" >/dev/null

# Every site block (not a snippet) must import the observability deny snippet.
# Snippets: (name) { ... }   Sites: hostname { ... } or :port { ... }
check_every_site_imports_deny() {
  local f="$1"
  python3 - "$f" <<'PY'
import re, sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
# Find observability deny snippet name: (something_observability_deny) {
m = re.search(r"^\(([A-Za-z0-9_-]*observability_deny)\)\s*\{", text, re.M)
if not m:
    print(f"FAIL: no (*observability_deny) snippet in {path}", file=sys.stderr)
    sys.exit(1)
snippet = m.group(1)

# Tokenize top-level blocks by brace depth.
sites = []
i = 0
n = len(text)
while i < n:
    # Skip comments
    if text.startswith("#", i):
        nl = text.find("\n", i)
        i = n if nl < 0 else nl + 1
        continue
    # Snippet or site opener at column 0 (or after newline)
    if (i == 0 or text[i - 1] == "\n") and text[i] not in " \t\r\n#":
        # Read header until '{'
        j = text.find("{", i)
        if j < 0:
            break
        header = text[i:j].strip()
        # Walk braces
        depth = 0
        k = j
        while k < n:
            if text[k] == "{":
                depth += 1
            elif text[k] == "}":
                depth -= 1
                if depth == 0:
                    k += 1
                    break
            k += 1
        body = text[j:k]
        i = k
        if not header:
            continue  # global options block `{ ... }`
        if header.startswith("(") and header.endswith(")"):
            continue  # snippet
        # Site: hostname, :port, or *.example (reject anything else)
        if re.match(r"^(:\d+|[\w.*-]+)$", header):
            sites.append((header, body))
        continue
    i += 1

if not sites:
    print(f"FAIL: no site blocks found in {path}", file=sys.stderr)
    sys.exit(1)

fail = 0
import_re = re.compile(rf"(?m)^\s*import\s+{re.escape(snippet)}\b")
for header, body in sites:
    if import_re.search(body):
        print(f"  OK  site {header!r} imports {snippet}")
    else:
        print(f"FAIL: site {header!r} missing 'import {snippet}' in {path}", file=sys.stderr)
        fail = 1
sys.exit(fail)
PY
}

fail=0
for f in "${FILES[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL missing: ${f}" >&2
    fail=1
    continue
  fi
  echo "→ caddy adapt --config $(basename "$f")"
  adapt_out="${TMPDIR_WORK}/adapt-$(basename "$f").json"
  adapt_err="${TMPDIR_WORK}/adapt-$(basename "$f").err"
  if ! docker run --rm \
    -v "${f}:/etc/caddy/Caddyfile:ro" \
    "${CADDY_IMAGE}" \
    caddy adapt --config /etc/caddy/Caddyfile >"${adapt_out}" 2>"${adapt_err}"; then
    echo "FAIL adapt: ${f}" >&2
    cat "${adapt_err}" >&2
    fail=1
    continue
  fi
  if ! grep -q 'path_regexp' "${adapt_out}" \
    && ! grep -q 'monitoring' "${adapt_out}"; then
    echo "FAIL: adapted JSON missing observability matcher for ${f}" >&2
    fail=1
    continue
  fi
  if ! grep -E 'monitoring|actuator' "${adapt_out}" >/dev/null; then
    echo "FAIL: adapted JSON missing monitoring/actuator for ${f}" >&2
    fail=1
    continue
  fi
  if ! grep -Fq "${EXPECTED_REGEXP}" "$f"; then
    echo "FAIL: source missing expected path_regexp in ${f}" >&2
    echo "  expected: ${EXPECTED_REGEXP}" >&2
    fail=1
    continue
  fi
  echo "→ every site imports observability deny ($(basename "$f"))"
  if ! check_every_site_imports_deny "$f"; then
    fail=1
    continue
  fi
  echo "  OK ${f}"
done

# Source-level: community Caddyfile must carry the hardened regexp.
if ! grep -Fq "${EXPECTED_REGEXP}" \
  "${REPO_ROOT}/experimental-hybrid/lightsail/Caddyfile.ezkey-online"; then
  echo "FAIL: observability path_regexp missing from Caddyfile.ezkey-online" >&2
  fail=1
fi

# Live smoke: same regexp, case / // / ; variants (Christophe #846 review).
SMOKE_CADDYFILE="${TMPDIR_WORK}/smoke.Caddyfile"
cat >"${SMOKE_CADDYFILE}" <<EOF
:8888 {
	@blockedObs path_regexp ${EXPECTED_REGEXP}
	respond @blockedObs 404
	respond "ok" 200
}
EOF

echo "→ live Caddy 2.10 smoke (case / // / ; variants)"
docker rm -f caddy-obs-regexp-smoke >/dev/null 2>&1 || true
docker run -d --name caddy-obs-regexp-smoke \
  -p 127.0.0.1:8888:8888 \
  -v "${SMOKE_CADDYFILE}:/etc/caddy/Caddyfile:ro" \
  "${CADDY_IMAGE}" >/dev/null
sleep 1

SMOKE_PATHS=(
  /monitoring
  //monitoring
  /Monitoring
  /MONITORING
  /monitoring\;x
  /actuator
  //actuator
  /Actuator
  /actuator/health
  /actuator\;x
  /api/v1/ok
)

for p in "${SMOKE_PATHS[@]}"; do
  code="$(curl -sS -o /dev/null -w '%{http_code}' --path-as-is "http://127.0.0.1:8888${p}" || echo "000")"
  if [[ "$p" == /api/v1/ok ]]; then
    expect=200
  else
    expect=404
  fi
  if [[ "$code" == "$expect" ]]; then
    echo "  OK  ${code}  ${p}"
  else
    echo "  FAIL ${code} (want ${expect}) ${p}" >&2
    fail=1
  fi
done

if [[ "$fail" -ne 0 ]]; then
  echo "Caddy observability validation FAILED." >&2
  exit 1
fi
echo "Caddy observability validation PASS (Caddy ${CADDY_IMAGE} adapt + per-site import + smoke)."
exit 0
