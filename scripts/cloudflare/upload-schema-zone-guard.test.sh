#!/usr/bin/env bash
# Locks the community schema upload zone guard: ezkey.org zone id in repo-root .env
# must not receive the ezkey.online artifact. No network.
#
#   ./scripts/cloudflare/upload-schema-zone-guard.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

ORG_ID="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
ONLINE_ID="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

BIN="${TMP}/bin"
mkdir -p "${BIN}"
cat >"${BIN}/curl" <<EOF
#!/usr/bin/env bash
set -euo pipefail
outfile=""
url=""
while [[ \$# -gt 0 ]]; do
  case "\$1" in
    -o)
      outfile="\$2"
      shift 2
      ;;
    -w|-H|-X|--data-binary)
      shift 2
      ;;
    -sS|-s|-S|--fail)
      shift
      ;;
    http*)
      url="\$1"
      shift
      ;;
    *)
      shift
      ;;
  esac
done
body='{"success":true,"result":{}}'
code=200
if [[ "\${url}" == *"/user/tokens/verify" ]]; then
  body='{"success":true,"result":{"status":"active"}}'
elif [[ "\${url}" == *"/zones?name="* ]]; then
  body="{\"success\":true,\"result\":[{\"id\":\"${ONLINE_ID}\",\"name\":\"ezkey.online\"}]}"
elif [[ "\${url}" == *"/zones/${ORG_ID}"* ]]; then
  body="{\"success\":true,\"result\":{\"id\":\"${ORG_ID}\",\"name\":\"ezkey.org\"}}"
elif [[ "\${url}" == *"/zones/${ONLINE_ID}"* ]]; then
  body="{\"success\":true,\"result\":{\"id\":\"${ONLINE_ID}\",\"name\":\"ezkey.online\"}}"
elif [[ "\${url}" == *"/schema_validation/schemas"* || "\${url}" == *"/api_gateway/user_schemas"* ]]; then
  body='{"success":true,"result":[]}'
else
  body='{"success":false,"errors":[{"code":1,"message":"unexpected url"}]}'
  code=500
fi
if [[ -n "\${outfile}" ]]; then
  printf '%s' "\${body}" >"\${outfile}"
fi
printf '%s' "\${code}"
EOF
chmod 755 "${BIN}/curl"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

run_upload() {
  local script="$1"
  local out="$2"
  local err="$3"
  shift 3
  set +e
  # env -i drops a workstation .env's inherited zone id. Callers pass zone vars.
  env -i \
    HOME="${HOME}" \
    PATH="${BIN}:${PATH}" \
    EZKEY_CLOUDFLARE_SKIP_ENV_FILE=1 \
    CLOUDFLARE_API_SHIELD_TOKEN=shield-test-token \
    "$@" \
    bash "${script}" --list >"${out}" 2>"${err}"
  local rc=$?
  set -e
  return "${rc}"
}

for script in \
  "${ROOT}/scripts/cloudflare/upload-auth-api-schema.sh" \
  "${ROOT}/scripts/cloudflare/upload-integration-api-schema.sh"; do
  name="$(basename "${script}")"
  echo "== ${name}: ezkey.org zone id is refused =="
  out="${TMP}/${name}.org.out"
  err="${TMP}/${name}.org.err"
  if run_upload "${script}" "${out}" "${err}" "CLOUDFLARE_ZONE_ID=${ORG_ID}"; then
    cat "${out}" >&2 || true
    cat "${err}" >&2 || true
    fail "${name} accepted the ezkey.org zone id"
  fi
  if ! grep -F -q "resolved zone is 'ezkey.org'" "${err}"; then
    cat "${err}" >&2 || true
    fail "${name} did not name the resolved ezkey.org zone"
  fi
  if ! grep -F -q "unset CLOUDFLARE_ZONE_ID" "${err}"; then
    cat "${err}" >&2 || true
    fail "${name} did not tell the operator to unset CLOUDFLARE_ZONE_ID"
  fi

  echo "== ${name}: CLOUDFLARE_ZONE_NAME other than ezkey.online is refused =="
  out="${TMP}/${name}.name.out"
  err="${TMP}/${name}.name.err"
  if run_upload "${script}" "${out}" "${err}" CLOUDFLARE_ZONE_NAME=ezkey.org; then
    cat "${err}" >&2 || true
    fail "${name} accepted CLOUDFLARE_ZONE_NAME=ezkey.org"
  fi
  if ! grep -F -q "CLOUDFLARE_ZONE_NAME is 'ezkey.org'" "${err}"; then
    cat "${err}" >&2 || true
    fail "${name} did not reject the non-community zone name"
  fi

  echo "== ${name}: default lookup stays on ezkey.online =="
  out="${TMP}/${name}.ok.out"
  err="${TMP}/${name}.ok.err"
  if ! run_upload "${script}" "${out}" "${err}"; then
    cat "${out}" >&2 || true
    cat "${err}" >&2 || true
    fail "${name} rejected the ezkey.online zone"
  fi
  if ! grep -F -q "zone: ezkey.online (${ONLINE_ID})" "${out}"; then
    cat "${out}" >&2 || true
    fail "${name} did not report the ezkey.online zone"
  fi
  if ! grep -F -q "schema_count: 0" "${out}"; then
    cat "${out}" >&2 || true
    fail "${name} did not list schemas on ezkey.online"
  fi
done

echo "community schema zone guard passed."
