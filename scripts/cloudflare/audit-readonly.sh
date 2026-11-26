#!/usr/bin/env bash
# Read-only review of the Ezkey Cloudflare edge against docs/cloudflare/edge-posture.md.
#
# Two parts:
#   1. Live response headers on every public hostname (no token needed).
#   2. Zone and account state through the Cloudflare API (GET only), with
#      CLOUDFLARE_AUDIT_TOKEN, a read-only token (see edge-posture.md § Tokens, #1553).
#
# Each check prints PASS, DRIFT (differs from a decided target), INFO (observed,
# no target yet: a pending decision) or SKIP (could not be read). Raw responses
# go to the output folder, outside the repo. The token is passed to curl on stdin,
# never on the command line, and is never written anywhere.
#
# Usage (repo root, Git Bash / macOS / Linux):
#   ./scripts/cloudflare/audit-readonly.sh                 # headers + API
#   ./scripts/cloudflare/audit-readonly.sh --headers-only  # no token needed
#   ./scripts/cloudflare/audit-readonly.sh --out DIR
#
# Exit: 0 no DRIFT, 1 at least one DRIFT, 2 usage or missing tool.
# Not a CI gate: a review tool for the operator or an agent (edge-posture.md § Review).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CF_API="https://api.cloudflare.com/client/v4"
OUT="${HOME}/.ezkey-tmp/cloudflare-audit-$(date +%Y%m%d-%H%M%S)"
HEADERS_ONLY=false
ZONES=(ezkey.org ezkey.online)
N_PASS=0 N_DRIFT=0 N_INFO=0 N_SKIP=0

usage() { sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --headers-only) HEADERS_ONLY=true ;;
    --out) OUT="${2:?--out needs a directory}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "error: unknown argument $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "error: $tool is required" >&2; exit 2; }
done
mkdir -p "$OUT/headers" "$OUT/api"

report() { # status check detail
  printf '%-5s  %-58s %s\n' "$1" "$2" "${3:-}"
  case "$1" in
    PASS) N_PASS=$((N_PASS + 1)) ;; DRIFT) N_DRIFT=$((N_DRIFT + 1)) ;;
    INFO) N_INFO=$((N_INFO + 1)) ;; SKIP) N_SKIP=$((N_SKIP + 1)) ;;
  esac
}

# ---------------------------------------------------------------- 1. live headers

fetch_headers() { # url -> file
  local url="$1" file
  file="$OUT/headers/$(printf '%s' "$url" | sed 's#https://##; s#[/:?]#_#g').txt"
  curl -sS -o /dev/null -D "$file" --max-time 15 "$url" 2>/dev/null || : >"$file"
  tr -d '\r' <"$file" >"$file.tmp" && mv "$file.tmp" "$file"
  printf '%s' "$file"
}

hdr_values() { grep -i "^$2:" "$1" | sed 's/^[^:]*:[[:space:]]*//' || true; }

# expect_header file host header rule [regex]
#   once <re>  : exactly one occurrence, matching re
#   present    : at least one occurrence
#   absent     : no occurrence
expect_header() {
  local file="$1" host="$2" name="$3" rule="$4" re="${5:-}" values count
  values="$(hdr_values "$file" "$name")"
  count="$(printf '%s' "$values" | grep -c . || true)"
  local label="$host $name"
  case "$rule" in
    once)
      if [[ "$count" -eq 1 ]] && grep -Eqi -- "$re" <<<"$values"; then report PASS "$label" "$values"
      else report DRIFT "$label" "want one '$re', got $count: $(tr '\n' '|' <<<"$values")"; fi ;;
    present)
      if [[ "$count" -ge 1 ]]; then report PASS "$label" "present"
      else report DRIFT "$label" "missing"; fi ;;
    absent)
      if [[ "$count" -eq 0 ]]; then report PASS "$label" "absent"
      else report DRIFT "$label" "want absent, got: $(tr '\n' '|' <<<"$values")"; fi ;;
  esac
}

# check_host url host: sets $f to the header file; reports SKIP (in this shell) when no response.
check_host() {
  f="$(fetch_headers "$1")"
  grep -q '^HTTP/' "$f" && return 0
  report SKIP "$2" "no response from $1"
  return 1
}

echo "== Live response headers (targets: edge-posture.md § Header ownership)"

# Static sites on Pages: _headers owns the application headers, the zone owns HSTS.
if check_host https://ezkey.org/ ezkey.org; then
  expect_header "$f" ezkey.org Strict-Transport-Security once '^max-age=31536000$'
  expect_header "$f" ezkey.org X-Frame-Options once '^DENY$'
  expect_header "$f" ezkey.org Referrer-Policy once '^strict-origin-when-cross-origin$'
  expect_header "$f" ezkey.org Content-Security-Policy once "frame-ancestors 'none'"
  expect_header "$f" ezkey.org X-XSS-Protection absent
  expect_header "$f" ezkey.org Expect-CT absent
fi
if check_host https://methodology.ezkey.org/ methodology.ezkey.org; then
  expect_header "$f" methodology.ezkey.org Strict-Transport-Security once '^max-age=31536000$'
  expect_header "$f" methodology.ezkey.org X-XSS-Protection absent
  expect_header "$f" methodology.ezkey.org Expect-CT absent
fi
# Admin UI on Pages.
for host in admin-ui.ezkey.online admin-ui-ezkey-online.pages.dev; do
  if check_host "https://$host/" "$host"; then
    expect_header "$f" "$host" Content-Security-Policy once "frame-ancestors 'none'"
    expect_header "$f" "$host" X-Frame-Options once '^DENY$'
    expect_header "$f" "$host" X-Content-Type-Options once '^nosniff$'
    expect_header "$f" "$host" Permissions-Policy present
  fi
done
# APIs and Demo Login behind Caddy on Lightsail: the origin owns the headers, once each.
for url in https://auth-api.ezkey.online/ https://admin-api.ezkey.online/api/v1/admin/auth/me \
  https://integration-api.ezkey.online/ https://demo-login.ezkey.online/; do
  host="${url#https://}"; host="${host%%/*}"
  if check_host "$url" "$host"; then
    expect_header "$f" "$host" Strict-Transport-Security present
    expect_header "$f" "$host" X-Frame-Options once '^DENY$'
    expect_header "$f" "$host" X-Content-Type-Options once '^nosniff$'
  fi
done

if $HEADERS_ONLY; then
  echo
  echo "Summary: $N_PASS PASS, $N_DRIFT DRIFT, $N_INFO INFO, $N_SKIP SKIP (headers only). Raw: $OUT"
  [[ "$N_DRIFT" -eq 0 ]]; exit $?
fi

# ---------------------------------------------------------------- 2. API (GET only)

if [[ "${EZKEY_CLOUDFLARE_SKIP_ENV_FILE:-}" != 1 && -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
if [[ -z "${CLOUDFLARE_AUDIT_TOKEN:-}" ]]; then
  echo "error: set CLOUDFLARE_AUDIT_TOKEN (read-only, edge-posture.md § Tokens) or use --headers-only" >&2
  exit 2
fi

# cf_get path name -> prints HTTP status; body in $OUT/api/name.json. GET only, by construction.
cf_get() {
  local body="$OUT/api/$2.json"
  printf 'header = "Authorization: Bearer %s"\n' "$CLOUDFLARE_AUDIT_TOKEN" |
    curl -sS -K - -X GET -o "$body" -w '%{http_code}' --max-time 30 "$CF_API$1" 2>/dev/null || true
}

echo
echo "== Cloudflare API (GET only)"

zone_check() { # zone
  local zone="$1" st id body hsts mh
  st="$(cf_get "/zones?name=$zone" "zone-$zone")"
  id="$(jq -r '.result[0].id // empty' "$OUT/api/zone-$zone.json" 2>/dev/null || true)"
  if [[ "$st" != 200 || -z "$id" ]]; then report SKIP "$zone zone" "HTTP $st (token scope?)"; return; fi
  report INFO "$zone plan" "$(jq -r '.result[0].plan.name' "$OUT/api/zone-$zone.json")"

  st="$(cf_get "/zones/$id/settings" "settings-$zone")"; body="$OUT/api/settings-$zone.json"
  if [[ "$st" == 200 ]]; then
    s() { jq -r --arg k "$1" '.result[] | select(.id==$k) | .value | tostring' "$body"; }
    if [[ "$(s ssl)" == strict ]]; then report PASS "$zone SSL mode" strict; else report DRIFT "$zone SSL mode" "want strict, got $(s ssl)"; fi
    if [[ "$(s always_use_https)" == on ]]; then report PASS "$zone Always Use HTTPS" on; else report DRIFT "$zone Always Use HTTPS" "$(s always_use_https)"; fi
    report INFO "$zone min TLS (edge)" "$(s min_tls_version)"
    hsts="$(jq -c '.result[] | select(.id=="security_header") | .value.strict_transport_security' "$body")"
    if [[ "$zone" == ezkey.org ]]; then
      if jq -e '.enabled and .max_age==31536000 and (.include_subdomains|not) and (.preload|not)' <<<"$hsts" >/dev/null; then
        report PASS "$zone zone HSTS" "$hsts"
      else report DRIFT "$zone zone HSTS" "want 1y, no subdomains, no preload; got $hsts"; fi
    else
      report INFO "$zone zone HSTS" "$hsts"
    fi
  else report SKIP "$zone settings" "HTTP $st"; fi

  st="$(cf_get "/zones/$id/managed_headers" "managed-headers-$zone")"
  if [[ "$st" == 200 ]]; then
    mh="$(jq -r '.result.managed_response_headers[]? | select(.id=="add_security_headers") | .enabled' "$OUT/api/managed-headers-$zone.json")"
    if [[ "$mh" == true ]]; then report DRIFT "$zone managed 'Add security headers'" "enabled (want off)"; else report PASS "$zone managed 'Add security headers'" "off"; fi
  else report SKIP "$zone managed transforms" "HTTP $st"; fi

  local phase n
  for phase in http_request_firewall_custom http_ratelimit http_response_headers_transform http_request_dynamic_redirect; do
    st="$(cf_get "/zones/$id/rulesets/phases/$phase/entrypoint" "phase-$zone-$phase")"
    case "$st" in
      200) n="$(jq '.result.rules // [] | length' "$OUT/api/phase-$zone-$phase.json")"
           report INFO "$zone $phase" "$n rule(s): $(jq -r '[.result.rules[]?.description] | join(" ; ")' "$OUT/api/phase-$zone-$phase.json")" ;;
      404) report INFO "$zone $phase" "0 rule(s)" ;;
      *)   report SKIP "$zone $phase" "HTTP $st" ;;
    esac
  done

  st="$(cf_get "/zones/$id/origin_tls_client_auth/settings" "aop-$zone")"
  if [[ "$st" == 200 ]]; then report INFO "$zone Authenticated Origin Pulls" "enabled=$(jq -r '.result.enabled' "$OUT/api/aop-$zone.json")"; else report SKIP "$zone Authenticated Origin Pulls" "HTTP $st"; fi

  st="$(cf_get "/zones/$id/api_gateway/settings/schema_validation" "schema-validation-$zone")"
  if [[ "$st" == 200 ]]; then
    local mit; mit="$(jq -r '.result.validation_default_mitigation_action' "$OUT/api/schema-validation-$zone.json")"
    if [[ "$zone" == ezkey.online ]]; then
      if [[ "$mit" == block ]]; then report PASS "$zone API Shield default mitigation" block; else report DRIFT "$zone API Shield default mitigation" "want block, got $mit"; fi
    else report INFO "$zone API Shield default mitigation" "$mit"; fi
  else report SKIP "$zone API Shield" "HTTP $st"; fi
  st="$(cf_get "/zones/$id/api_gateway/user_schemas" "schemas-$zone")"
  if [[ "$st" == 200 ]]; then report INFO "$zone API Shield schemas" "$(jq -r '[.result[]?.name] | join(", ")' "$OUT/api/schemas-$zone.json")"
  else report SKIP "$zone API Shield schemas" "HTTP $st"; fi
}

for z in "${ZONES[@]}"; do zone_check "$z"; done

if [[ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  st="$(cf_get "/accounts/$CLOUDFLARE_ACCOUNT_ID/pages/projects" pages-projects)"
  if [[ "$st" == 200 ]]; then
    while IFS=$'\t' read -r name domains; do
      if [[ "$name" == ezkey-admin-ui ]]; then report DRIFT "Pages $name" "exp1 leftover, still serves $domains"
      else report INFO "Pages $name" "$domains"; fi
    done < <(jq -r '.result[] | [.name, (.domains | join(" "))] | @tsv' "$OUT/api/pages-projects.json")
  else report SKIP "Pages projects" "HTTP $st"; fi
else
  report SKIP "Pages projects" "CLOUDFLARE_ACCOUNT_ID not set"
fi

echo
echo "Summary: $N_PASS PASS, $N_DRIFT DRIFT, $N_INFO INFO, $N_SKIP SKIP. Raw: $OUT"
[[ "$N_DRIFT" -eq 0 ]]
