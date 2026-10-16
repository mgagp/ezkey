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
#     --base-url https://integration-api.ezkey.online \
#     --base-url https://demo-acme.ezkey.online
#
# Exit 0 when every probe returns 404; non-zero otherwise.
#
set -euo pipefail

# Capture env BEFORE shadowing with a local array (Patrick m3).
BASE_URLS_FROM_ENV="${BASE_URLS-}"

# Paths matched by: (?i)^/+(monitoring|actuator)([/;].*)?$
# Include case, double-slash, and semicolon (matrix/path-param) variants.
PATHS=(
  /monitoring
  /monitoring/
  //monitoring
  /Monitoring
  /MONITORING
  /monitoring\;x
  /actuator
  /actuator/
  //actuator
  /Actuator
  /ACTUATOR
  /actuator/health
  /actuator/monitoring
  /actuator/metrics
  /actuator/threaddump
  /actuator\;x
)

BASE_URLS=()
CHECK_SS=""

usage() {
  sed -n '2,22p' "$0" | sed 's/^# \?//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      [[ $# -ge 2 ]] || { echo "error: --base-url requires a value" >&2; exit 2; }
      BASE_URLS+=("$2")
      shift 2
      ;;
    --check-ss)
      # Optional: on the Lightsail VM, confirm management / collector ports are not
      # listening on non-loopback addresses (Edgar live gate).
      CHECK_SS="1"
      shift
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
  if [[ -n "$BASE_URLS_FROM_ENV" ]]; then
    # shellcheck disable=SC2206
    BASE_URLS=(${BASE_URLS_FROM_ENV})
  elif [[ -z "$CHECK_SS" ]]; then
    BASE_URLS=(
      http://localhost:19080
      http://localhost:18080
      http://localhost:17080
    )
  fi
fi

failures=0
echo "JavaMelody / Actuator non-exposure check (#846)"
echo ""

if [[ -n "$CHECK_SS" ]]; then
  if ! command -v ss >/dev/null 2>&1; then
    echo "FAIL: --check-ss requested but ss is not installed" >&2
    failures=$((failures + 1))
  else
    echo "→ ss: management / collector ports must not listen on 0.0.0.0 (loopback OK)"
    while IFS= read -r line; do
      if echo "$line" | grep -Eq '0\.0\.0\.0:(8088|9081|8085|7081)\b|\*:(8088|9081|8085|7081)\b'; then
        echo "  FAIL public bind: ${line}" >&2
        failures=$((failures + 1))
      else
        echo "  OK  ${line}"
      fi
    done < <(ss -lnt 2>/dev/null | grep -E ':(8088|9081|8085|7081)\b' || true)
  fi
  echo ""
fi

if [[ ${#BASE_URLS[@]} -gt 0 ]]; then
  echo "Expect HTTP 404 on every probe."
  for base in "${BASE_URLS[@]}"; do
    base="${base%/}"
    for path in "${PATHS[@]}"; do
      url="${base}${path}"
      code="$(curl -sS -o /dev/null -w '%{http_code}' --path-as-is --connect-timeout 5 --max-time 15 "$url" || echo "000")"
      if [[ "$code" == "404" ]]; then
        echo "  OK  ${code}  ${url}"
      else
        echo "  FAIL ${code}  ${url} (expected 404)" >&2
        failures=$((failures + 1))
      fi
    done
  done
  echo ""
fi

if [[ ${#BASE_URLS[@]} -eq 0 && -z "$CHECK_SS" ]]; then
  echo "FAIL: no base URLs and no --check-ss" >&2
  exit 2
fi

if [[ "$failures" -eq 0 ]]; then
  echo "PASS."
  exit 0
fi
echo "FAIL: ${failures} check(s) failed." >&2
exit 1
