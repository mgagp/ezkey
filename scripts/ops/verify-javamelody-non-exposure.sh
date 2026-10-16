#!/usr/bin/env bash
#
# Verify that JavaMelody / Actuator paths are not exposed on a public HTTP surface.
# Expects HTTP 404 from Caddy (or any edge that applies the #846 deny regex).
#
# Usage:
#   ./scripts/ops/verify-javamelody-non-exposure.sh
#   ./scripts/ops/verify-javamelody-non-exposure.sh --base-url http://localhost:19080
#   BASE_URLS="http://localhost:19080 http://localhost:18080 http://localhost:17080" \
#     ./scripts/ops/verify-javamelody-non-exposure.sh
#
# Community (via Cloudflare, after deploy — Edgar / Marc):
#   ./scripts/ops/verify-javamelody-non-exposure.sh \
#     --base-url https://admin-api.ezkey.online \
#     --base-url https://auth-api.ezkey.online \
#     --base-url https://integration-api.ezkey.online
#
# Exit 0 when every probe returns 404; non-zero otherwise.
#
set -euo pipefail

PATHS=(
  /monitoring
  /monitoring/
  /actuator
  /actuator/
  /actuator/health
  /actuator/monitoring
  /actuator/metrics
  /actuator/threaddump
)

BASE_URLS=()

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \?//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      [[ $# -ge 2 ]] || { echo "error: --base-url requires a value" >&2; exit 2; }
      BASE_URLS+=("$2")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ${#BASE_URLS[@]} -eq 0 ]]; then
  if [[ -n "${BASE_URLS:-}" ]]; then
    # Allow space-separated BASE_URLS env when no flags were passed.
    # shellcheck disable=SC2206
    BASE_URLS=(${BASE_URLS})
  else
    BASE_URLS=(
      http://localhost:19080
      http://localhost:18080
      http://localhost:17080
    )
  fi
fi

failures=0
echo "JavaMelody / Actuator non-exposure check (#846)"
echo "Expect HTTP 404 on every probe."
echo ""

for base in "${BASE_URLS[@]}"; do
  base="${base%/}"
  for path in "${PATHS[@]}"; do
    url="${base}${path}"
    code="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 15 "$url" || echo "000")"
    if [[ "$code" == "404" ]]; then
      echo "  OK  ${code}  ${url}"
    else
      echo "  FAIL ${code}  ${url} (expected 404)" >&2
      failures=$((failures + 1))
    fi
  done
done

echo ""
if [[ "$failures" -eq 0 ]]; then
  echo "PASS: all probes returned 404."
  exit 0
fi
echo "FAIL: ${failures} probe(s) did not return 404." >&2
exit 1
