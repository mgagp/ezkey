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
# On the VM (ss bind gate — IPv4 0.0.0.0 and IPv6 [::] / *):
#   ./scripts/ops/verify-javamelody-non-exposure.sh --check-ss
#
# Business-port /monitoring (C2) via the caddy container (Edgar live gate).
# The official Caddy image has ash/curl but not bash — run this script on the VM
# (or laptop) and pass --via-caddy so probes use Docker DNS inside the network:
#   ./scripts/ops/verify-javamelody-non-exposure.sh --business-ports --via-caddy ezkey-exp-caddy
#   # Local stack: --via-caddy ezkey-caddy
#   # Default URLs (override with BUSINESS_PORT_URLS / --business-port-url):
#   #   http://admin-api:9080  http://auth-api:8080  http://integration-api:7080
#
# Authenticated business-port refusal (Christophe): with a valid admin bearer and/or
# cookie, /monitoring on each business port must still be refused (401 or 403 — both
# OK; do not harmonize). If the JavaMelody report HTML is returned, that is blocking.
#   EZKEY_ADMIN_BEARER='…' EZKEY_ADMIN_COOKIE='…' \
#     ./scripts/ops/verify-javamelody-non-exposure.sh --business-ports \
#       --via-caddy ezkey-exp-caddy --auth-probe
#
# Exit 0 when every selected probe passes; non-zero otherwise.
#
set -euo pipefail

# Capture env BEFORE shadowing with a local array (Patrick m3).
BASE_URLS_FROM_ENV="${BASE_URLS-}"
BUSINESS_PORT_URLS_FROM_ENV="${BUSINESS_PORT_URLS-}"
AUTH_BEARER="${EZKEY_ADMIN_BEARER-}"
AUTH_COOKIE="${EZKEY_ADMIN_COOKIE-}"
VIA_CADDY="${VIA_CADDY-}"

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

# Default Docker-network business ports (reachable from the caddy container).
DEFAULT_BUSINESS_PORT_URLS=(
  http://admin-api:9080
  http://auth-api:8080
  http://integration-api:7080
)

BASE_URLS=()
BUSINESS_PORT_URLS=()
CHECK_SS=""
CHECK_BUSINESS=""
AUTH_PROBE=""

usage() {
  sed -n '2,42p' "$0" | sed 's/^# \?//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      [[ $# -ge 2 ]] || { echo "error: --base-url requires a value" >&2; exit 2; }
      BASE_URLS+=("$2")
      shift 2
      ;;
    --business-port-url)
      [[ $# -ge 2 ]] || { echo "error: --business-port-url requires a value" >&2; exit 2; }
      BUSINESS_PORT_URLS+=("$2")
      CHECK_BUSINESS="1"
      shift 2
      ;;
    --business-ports)
      # Probe /monitoring on each API business port (prefer --via-caddy for Docker DNS).
      CHECK_BUSINESS="1"
      shift
      ;;
    --via-caddy)
      [[ $# -ge 2 ]] || { echo "error: --via-caddy requires a container name" >&2; exit 2; }
      VIA_CADDY="$2"
      CHECK_BUSINESS="1"
      shift 2
      ;;
    --auth-probe)
      # Require EZKEY_ADMIN_BEARER and/or EZKEY_ADMIN_COOKIE; refuse if report leaks.
      AUTH_PROBE="1"
      CHECK_BUSINESS="1"
      shift
      ;;
    --check-ss)
      # Optional: on the Lightsail VM, confirm management / collector ports are not
      # listening on non-loopback addresses — IPv4 0.0.0.0 and IPv6 [::] (Edgar).
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
  elif [[ -z "$CHECK_SS" && -z "$CHECK_BUSINESS" ]]; then
    BASE_URLS=(
      http://localhost:19080
      http://localhost:18080
      http://localhost:17080
    )
  fi
fi

if [[ -n "$CHECK_BUSINESS" && ${#BUSINESS_PORT_URLS[@]} -eq 0 ]]; then
  if [[ -n "$BUSINESS_PORT_URLS_FROM_ENV" ]]; then
    # shellcheck disable=SC2206
    BUSINESS_PORT_URLS=(${BUSINESS_PORT_URLS_FROM_ENV})
  else
    BUSINESS_PORT_URLS=("${DEFAULT_BUSINESS_PORT_URLS[@]}")
  fi
fi

if [[ -n "$AUTH_PROBE" && -z "$AUTH_BEARER" && -z "$AUTH_COOKIE" ]]; then
  echo "FAIL: --auth-probe requires EZKEY_ADMIN_BEARER and/or EZKEY_ADMIN_COOKIE" >&2
  exit 2
fi

failures=0
echo "JavaMelody / Actuator non-exposure check (#846)"
echo ""

is_public_ss_bind() {
  # Fail on wildcard / all-interfaces binds for JM collector + management ports.
  # Matches: 0.0.0.0:PORT, *:PORT, [::]:PORT, and ss "*:PORT" IPv6 forms.
  local line="$1"
  echo "$line" | grep -Eq \
    '0\.0\.0\.0:(8088|9081|8085|7081)\b|\*:(8088|9081|8085|7081)\b|\[::\]:(8088|9081|8085|7081)\b'
}

if [[ -n "$CHECK_SS" ]]; then
  if ! command -v ss >/dev/null 2>&1; then
    echo "FAIL: --check-ss requested but ss is not installed" >&2
    failures=$((failures + 1))
  else
    echo "→ ss: management / collector ports must not listen on 0.0.0.0 or [::] (loopback OK)"
    while IFS= read -r line; do
      if is_public_ss_bind "$line"; then
        echo "  FAIL public bind: ${line}" >&2
        failures=$((failures + 1))
      else
        echo "  OK  ${line}"
      fi
    done < <(ss -lnt 2>/dev/null | grep -E ':(8088|9081|8085|7081)\b' || true)
  fi
  echo ""
fi

# True when body looks like a JavaMelody HTML report (blocking if returned on business port).
looks_like_javamelody_report() {
  local body_file="$1"
  grep -qiE 'javamelody|Execute the garbage collector|Monitoring on' "$body_file" 2>/dev/null
}

# HTTP GET; writes body to $1, prints status code. Optional curl -H args follow.
http_get() {
  local body_file="$1"
  local url="$2"
  shift 2
  local extra=("$@")
  if [[ -n "$VIA_CADDY" ]]; then
    # Probe from inside the caddy container (Docker DNS). Official image has curl.
    local code
    code="$(docker exec "$VIA_CADDY" curl -sS -o /tmp/ezkey-jm-probe.body -w '%{http_code}' \
      --path-as-is --connect-timeout 5 --max-time 15 "${extra[@]}" "$url" 2>/dev/null || echo "000")"
    docker exec "$VIA_CADDY" cat /tmp/ezkey-jm-probe.body >"$body_file" 2>/dev/null || : >"$body_file"
    printf '%s' "$code"
  else
    curl -sS -o "$body_file" -w '%{http_code}' --path-as-is --connect-timeout 5 --max-time 15 \
      "${extra[@]}" "$url" || echo "000"
  fi
}

probe_business_monitoring() {
  local base="$1"
  local label="$2"
  shift 2
  local curl_auth=("$@")
  local url="${base%/}/monitoring"
  local body
  body="$(mktemp)"
  local code
  code="$(http_get "$body" "$url" "${curl_auth[@]}")"
  # Accept 401 or 403 only. Never 200 with a JM report.
  if [[ "$code" == "401" || "$code" == "403" ]]; then
    if looks_like_javamelody_report "$body"; then
      echo "  FAIL ${code}  ${label} ${url} (status refused but body looks like JM report)" >&2
      failures=$((failures + 1))
    else
      echo "  OK  ${code}  ${label} ${url}"
    fi
  elif [[ "$code" == "200" ]] && looks_like_javamelody_report "$body"; then
    echo "  FAIL ${code}  ${label} ${url} (JavaMelody report on business port — blocking)" >&2
    failures=$((failures + 1))
  else
    echo "  FAIL ${code}  ${label} ${url} (expected 401 or 403)" >&2
    failures=$((failures + 1))
  fi
  rm -f "$body"
}

if [[ -n "$CHECK_BUSINESS" ]]; then
  echo "→ business-port /monitoring (C2): expect 401 or 403; JM report HTML is blocking"
  if [[ -n "$VIA_CADDY" ]]; then
    echo "  (via docker exec ${VIA_CADDY} — Docker DNS for admin-api/auth-api/integration-api)"
    if ! docker inspect "$VIA_CADDY" >/dev/null 2>&1; then
      echo "FAIL: --via-caddy container not found: ${VIA_CADDY}" >&2
      failures=$((failures + 1))
    fi
  else
    echo "  (direct curl; use --via-caddy <caddy-container> for Docker-network DNS)"
  fi
  for base in "${BUSINESS_PORT_URLS[@]}"; do
    probe_business_monitoring "$base" "anon"
  done
  if [[ -n "$AUTH_PROBE" ]]; then
    echo "→ authenticated business-port /monitoring (Christophe): bearer/cookie must still be refused"
    auth_args=()
    if [[ -n "$AUTH_BEARER" ]]; then
      auth_args+=(-H "Authorization: Bearer ${AUTH_BEARER}")
    fi
    if [[ -n "$AUTH_COOKIE" ]]; then
      auth_args+=(-H "Cookie: ${AUTH_COOKIE}")
    fi
    for base in "${BUSINESS_PORT_URLS[@]}"; do
      probe_business_monitoring "$base" "auth" "${auth_args[@]}"
    done
  fi
  echo ""
fi

if [[ ${#BASE_URLS[@]} -gt 0 ]]; then
  echo "Expect HTTP 404 on every public/edge probe."
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

if [[ ${#BASE_URLS[@]} -eq 0 && -z "$CHECK_SS" && -z "$CHECK_BUSINESS" ]]; then
  echo "FAIL: no base URLs and no --check-ss / --business-ports" >&2
  exit 2
fi

if [[ "$failures" -eq 0 ]]; then
  echo "PASS."
  exit 0
fi
echo "FAIL: ${failures} check(s) failed." >&2
exit 1
