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
# Authenticated business-port refusal (Christophe): secrets must NOT appear on the
# curl/docker argv (visible in `ps`). Pass 0600 files (or stdin for bearer):
#   printf '%s' "$TOKEN" > /tmp/jm-bearer && chmod 0600 /tmp/jm-bearer
#   ./scripts/ops/verify-javamelody-non-exposure.sh --business-ports \
#     --via-caddy ezkey-exp-caddy --auth-probe \
#     --auth-bearer-file /tmp/jm-bearer \
#     --auth-cookie-file /tmp/jm-cookie
#   # Or: EZKEY_ADMIN_BEARER_FILE=… EZKEY_ADMIN_COOKIE_FILE=…
#   # Bearer via stdin: --auth-bearer-file -  (reads one line from stdin)
#
# Exit 0 when every selected probe passes; non-zero otherwise.
#
set -euo pipefail

# Capture env BEFORE shadowing with a local array (Patrick m3).
BASE_URLS_FROM_ENV="${BASE_URLS-}"
BUSINESS_PORT_URLS_FROM_ENV="${BUSINESS_PORT_URLS-}"
AUTH_BEARER_FILE="${EZKEY_ADMIN_BEARER_FILE-}"
AUTH_COOKIE_FILE="${EZKEY_ADMIN_COOKIE_FILE-}"
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
  sed -n '2,48p' "$0" | sed 's/^# \?//'
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
      AUTH_PROBE="1"
      CHECK_BUSINESS="1"
      shift
      ;;
    --auth-bearer-file)
      [[ $# -ge 2 ]] || { echo "error: --auth-bearer-file requires a path (or - for stdin)" >&2; exit 2; }
      AUTH_BEARER_FILE="$2"
      shift 2
      ;;
    --auth-cookie-file)
      [[ $# -ge 2 ]] || { echo "error: --auth-cookie-file requires a path" >&2; exit 2; }
      AUTH_COOKIE_FILE="$2"
      shift 2
      ;;
    --check-ss)
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

# Reject legacy env that would encourage putting secrets on the command line.
if [[ -n "${EZKEY_ADMIN_BEARER-}" || -n "${EZKEY_ADMIN_COOKIE-}" ]]; then
  echo "FAIL: EZKEY_ADMIN_BEARER / EZKEY_ADMIN_COOKIE are no longer accepted" >&2
  echo "  (values would leak via curl/docker argv in ps). Use --auth-bearer-file /" >&2
  echo "  --auth-cookie-file (0600) or EZKEY_ADMIN_BEARER_FILE / EZKEY_ADMIN_COOKIE_FILE." >&2
  exit 2
fi

AUTH_BEARER=""
AUTH_COOKIE=""
if [[ -n "$AUTH_PROBE" ]]; then
  if [[ -z "$AUTH_BEARER_FILE" && -z "$AUTH_COOKIE_FILE" ]]; then
    echo "FAIL: --auth-probe requires --auth-bearer-file and/or --auth-cookie-file" >&2
    exit 2
  fi
  if [[ -n "$AUTH_BEARER_FILE" ]]; then
    if [[ "$AUTH_BEARER_FILE" == "-" ]]; then
      IFS= read -r AUTH_BEARER || true
    else
      if [[ ! -f "$AUTH_BEARER_FILE" ]]; then
        echo "FAIL: bearer file not found: ${AUTH_BEARER_FILE}" >&2
        exit 2
      fi
      mode="$(stat -c '%a' "$AUTH_BEARER_FILE" 2>/dev/null || stat -f '%Lp' "$AUTH_BEARER_FILE")"
      if [[ "$mode" != "600" && "$mode" != "0600" ]]; then
        echo "FAIL: bearer file must be mode 0600 (got ${mode}): ${AUTH_BEARER_FILE}" >&2
        exit 2
      fi
      IFS= read -r AUTH_BEARER <"$AUTH_BEARER_FILE" || true
    fi
    AUTH_BEARER="${AUTH_BEARER%%$'\r'}"
    if [[ -z "$AUTH_BEARER" ]]; then
      echo "FAIL: bearer file/stdin is empty" >&2
      exit 2
    fi
  fi
  if [[ -n "$AUTH_COOKIE_FILE" ]]; then
    if [[ ! -f "$AUTH_COOKIE_FILE" ]]; then
      echo "FAIL: cookie file not found: ${AUTH_COOKIE_FILE}" >&2
      exit 2
    fi
    mode="$(stat -c '%a' "$AUTH_COOKIE_FILE" 2>/dev/null || stat -f '%Lp' "$AUTH_COOKIE_FILE")"
    if [[ "$mode" != "600" && "$mode" != "0600" ]]; then
      echo "FAIL: cookie file must be mode 0600 (got ${mode}): ${AUTH_COOKIE_FILE}" >&2
      exit 2
    fi
    IFS= read -r AUTH_COOKIE <"$AUTH_COOKIE_FILE" || true
    AUTH_COOKIE="${AUTH_COOKIE%%$'\r'}"
    if [[ -z "$AUTH_COOKIE" ]]; then
      echo "FAIL: cookie file is empty" >&2
      exit 2
    fi
  fi
fi

failures=0
echo "JavaMelody / Actuator non-exposure check (#846)"
echo ""

is_public_ss_bind() {
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

looks_like_javamelody_report() {
  local body_file="$1"
  grep -qiE 'javamelody|Execute the garbage collector|Monitoring on' "$body_file" 2>/dev/null
}

# Classify a business-port /monitoring probe (#863 B2 / Edgar):
#   ok            — 401, 403, or 404 without a JavaMelody body (not exposed)
#   exposed       — HTTP 200, or any status whose body looks like a JM report
#   inconclusive  — everything else (000, 3xx, 5xx, 204, …) — probe did not verify
business_port_probe_classify() {
  local code="$1"
  local body_file="$2"
  if looks_like_javamelody_report "$body_file"; then
    printf '%s\n' "exposed"
    return 0
  fi
  if [[ "$code" == "200" ]]; then
    printf '%s\n' "exposed"
    return 0
  fi
  if [[ "$code" == "401" || "$code" == "403" || "$code" == "404" ]]; then
    printf '%s\n' "ok"
    return 0
  fi
  printf '%s\n' "inconclusive"
}

# True when classify == ok (kept for callers/tests that want a boolean).
business_port_probe_is_ok() {
  local code="$1"
  local body_file="$2"
  [[ "$(business_port_probe_classify "$code" "$body_file")" == "ok" ]]
}

# Build a curl --config file (0600) so Authorization/Cookie never appear in argv/ps.
write_curl_config() {
  local cfg="$1"
  local url="$2"
  local body_out="$3"
  : >"$cfg"
  chmod 0600 "$cfg"
  {
    printf 'url = "%s"\n' "$url"
    printf 'output = "%s"\n' "$body_out"
    printf 'write-out = "%%{http_code}"\n'
    printf 'silent\n'
    printf 'show-error\n'
    printf 'path-as-is\n'
    printf 'connect-timeout = 5\n'
    printf 'max-time = 15\n'
    if [[ -n "$AUTH_BEARER" ]]; then
      # Escape backslashes and double-quotes for curl config double-quoted strings.
      local esc="${AUTH_BEARER//\\/\\\\}"
      esc="${esc//\"/\\\"}"
      printf 'header = "Authorization: Bearer %s"\n' "$esc"
    fi
    if [[ -n "$AUTH_COOKIE" ]]; then
      local esc="${AUTH_COOKIE//\\/\\\\}"
      esc="${esc//\"/\\\"}"
      printf 'header = "Cookie: %s"\n' "$esc"
    fi
  } >>"$cfg"
}

# HTTP GET without putting secrets on the process command line.
http_get() {
  local body_file="$1"
  local url="$2"
  local with_auth="${3-}"
  local cfg
  cfg="$(mktemp)"
  local remote_body="/tmp/ezkey-jm-probe.body"
  local remote_cfg="/tmp/ezkey-jm-curl.cfg"
  if [[ -n "$VIA_CADDY" ]]; then
    if [[ -n "$with_auth" ]]; then
      write_curl_config "$cfg" "$url" "$remote_body"
    else
      : >"$cfg"
      chmod 0600 "$cfg"
      {
        printf 'url = "%s"\n' "$url"
        printf 'output = "%s"\n' "$remote_body"
        printf 'write-out = "%%{http_code}"\n'
        printf 'silent\n'
        printf 'show-error\n'
        printf 'path-as-is\n'
        printf 'connect-timeout = 5\n'
        printf 'max-time = 15\n'
      } >>"$cfg"
    fi
    docker cp "$cfg" "${VIA_CADDY}:${remote_cfg}" >/dev/null
    local code
    code="$(docker exec "$VIA_CADDY" curl --config "$remote_cfg" 2>/dev/null || echo "000")"
    docker exec "$VIA_CADDY" cat "$remote_body" >"$body_file" 2>/dev/null || : >"$body_file"
    docker exec "$VIA_CADDY" rm -f "$remote_cfg" "$remote_body" >/dev/null 2>&1 || true
    rm -f "$cfg"
    printf '%s' "$code"
  else
    if [[ -n "$with_auth" ]]; then
      write_curl_config "$cfg" "$url" "$body_file"
    else
      : >"$cfg"
      chmod 0600 "$cfg"
      {
        printf 'url = "%s"\n' "$url"
        printf 'output = "%s"\n' "$body_file"
        printf 'write-out = "%%{http_code}"\n'
        printf 'silent\n'
        printf 'show-error\n'
        printf 'path-as-is\n'
        printf 'connect-timeout = 5\n'
        printf 'max-time = 15\n'
      } >>"$cfg"
    fi
    local code
    code="$(curl --config "$cfg" 2>/dev/null || echo "000")"
    rm -f "$cfg"
    printf '%s' "$code"
  fi
}

probe_business_monitoring() {
  local base="$1"
  local label="$2"
  local with_auth="${3-}"
  local url="${base%/}/monitoring"
  local body
  body="$(mktemp)"
  local code verdict
  code="$(http_get "$body" "$url" "$with_auth")"
  verdict="$(business_port_probe_classify "$code" "$body")"
  case "${verdict}" in
    ok)
      echo "  OK  ${code}  ${label} ${url}"
      ;;
    exposed)
      if looks_like_javamelody_report "$body"; then
        echo "  FAIL ${code}  ${label} ${url} (JavaMelody report on business port — blocking)" >&2
      else
        echo "  FAIL ${code}  ${label} ${url} (HTTP ${code} on business-port /monitoring — blocking)" >&2
      fi
      failures=$((failures + 1))
      ;;
    *)
      echo "  FAIL ${code}  ${label} ${url} (probe inconclusive — expected 401/403/404 without JM body)" >&2
      failures=$((failures + 1))
      ;;
  esac
  rm -f "$body"
}

if [[ -n "$CHECK_BUSINESS" ]]; then
  echo "→ business-port /monitoring (C2): 401/403/404 = not exposed; 200 or JM HTML = exposed; other codes = inconclusive"
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
    echo "→ authenticated business-port /monitoring (Christophe): bearer/cookie via 0600 curl --config (not argv)"
    for base in "${BUSINESS_PORT_URLS[@]}"; do
      probe_business_monitoring "$base" "auth" "1"
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
