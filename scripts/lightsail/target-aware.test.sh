#!/usr/bin/env bash
# Dry-run / unit checks for Lightsail SSH target resolution (#812).
# Community-only: --target community → Host ezkey-online.
# Run from repo root: ./scripts/lightsail/target-aware.test.sh
#
# Safety: a fake `aws` is prepended to PATH for the whole run. It records
# invocations and exits non-zero on any delete-instance so a regressing
# delete guard cannot destroy a real Lightsail VM (e.g. on Rootbeer).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMMON="${ROOT}/scripts/lightsail/common.sh"
EXPORT="${ROOT}/experimental-hybrid/scripts/export-backend-images-to-lightsail.sh"
UPGRADE="${ROOT}/experimental-hybrid/scripts/full-exp-environment-upgrade.sh"
BOOTSTRAP="${ROOT}/scripts/lightsail/bootstrap-host.sh"
CREATE="${ROOT}/scripts/lightsail/create-instance.sh"
DELETE="${ROOT}/scripts/lightsail/delete-instance.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
OUT="${TMP}/out"
ERR="${TMP}/err"
FAKE_AWS_BIN="${TMP}/fake-bin"
FAKE_AWS_LOG="${TMP}/fake-aws.log"
mkdir -p "${FAKE_AWS_BIN}"
: >"${FAKE_AWS_LOG}"

# Isolate from workstation AWS config / credentials (never hit real AWS).
export AWS_PROFILE="ezkey-test-no-such-profile"
export AWS_DEFAULT_PROFILE="ezkey-test-no-such-profile"
export AWS_CONFIG_FILE="${TMP}/aws-config-empty"
export AWS_SHARED_CREDENTIALS_FILE="${TMP}/aws-credentials-empty"
: >"${AWS_CONFIG_FILE}"
: >"${AWS_SHARED_CREDENTIALS_FILE}"
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN || true

# Optional: which instance name get-static-ip reports as attachedTo (empty = not attached).
export FAKE_AWS_STATIC_IP_ATTACHED_TO="${FAKE_AWS_STATIC_IP_ATTACHED_TO:-}"
export FAKE_AWS_LOG

cat >"${FAKE_AWS_BIN}/aws" <<'FAKE_AWS'
#!/usr/bin/env bash
set -euo pipefail
log="${FAKE_AWS_LOG:?FAKE_AWS_LOG unset}"
printf '%s\n' "$*" >>"${log}"
for arg in "$@"; do
  if [[ "${arg}" == "delete-instance" ]]; then
    echo "error: FAKE AWS refused delete-instance (test harness): $*" >&2
    exit 99
  fi
done
# get-static-ip — JSON for attachment checks (read-only path before delete).
for arg in "$@"; do
  if [[ "${arg}" == "get-static-ip" ]]; then
    attached="${FAKE_AWS_STATIC_IP_ATTACHED_TO:-}"
    if [[ -n "${attached}" ]]; then
      printf '%s\n' "{\"staticIp\":{\"name\":\"ezkey-online-community\",\"isAttached\":true,\"attachedTo\":\"${attached}\"}}"
    else
      printf '%s\n' '{"staticIp":{"name":"ezkey-online-community","isAttached":false}}'
    fi
    exit 0
  fi
done
# Other aws lightsail subcommands used by dry-run paths: succeed silently.
exit 0
FAKE_AWS
chmod +x "${FAKE_AWS_BIN}/aws"
export PATH="${FAKE_AWS_BIN}:${PATH}"

# Prove the harness shadows any real aws.
if ! command -v aws >/dev/null 2>&1; then
  echo "FAIL: fake aws not on PATH" >&2
  exit 1
fi
if [[ "$(command -v aws)" != "${FAKE_AWS_BIN}/aws" ]]; then
  echo "FAIL: expected fake aws at ${FAKE_AWS_BIN}/aws, got $(command -v aws)" >&2
  exit 1
fi

pass=0
fail=0

fake_aws_log_has() {
  local needle="$1"
  grep -qF -- "${needle}" "${FAKE_AWS_LOG}"
}

fake_aws_log_lacks() {
  local needle="$1"
  ! grep -qF -- "${needle}" "${FAKE_AWS_LOG}"
}

assert_ok() {
  local name="$1"
  shift
  if "$@" >"${OUT}" 2>"${ERR}"; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (expected success)" >&2
    echo "--- stdout ---" >&2
    cat "${OUT}" >&2 || true
    echo "--- stderr ---" >&2
    cat "${ERR}" >&2 || true
    fail=$((fail + 1))
  fi
}

assert_fail() {
  local name="$1"
  shift
  if "$@" >"${OUT}" 2>"${ERR}"; then
    echo "FAIL: ${name} (expected failure)" >&2
    echo "--- stdout ---" >&2
    cat "${OUT}" >&2 || true
    fail=$((fail + 1))
  else
    echo "PASS: ${name}"
    pass=$((pass + 1))
  fi
}

assert_stdout_contains() {
  local name="$1"
  local needle="$2"
  if grep -qF -- "${needle}" "${OUT}"; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (stdout missing: ${needle})" >&2
    cat "${OUT}" >&2 || true
    fail=$((fail + 1))
  fi
}

assert_stderr_contains() {
  local name="$1"
  local needle="$2"
  if grep -qF -- "${needle}" "${ERR}"; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (stderr missing: ${needle})" >&2
    cat "${ERR}" >&2 || true
    fail=$((fail + 1))
  fi
}

# --- common.sh helpers ---
(
  # shellcheck source=common.sh
  source "${COMMON}"
  unset LIGHTSAIL_SSH_HOST || true
  LIGHTSAIL_TARGET=""
  # lightsail_die uses exit — nest so we can assert without killing this shell.
  if (lightsail_resolve_ssh_target "") 2>"${ERR}"; then
    echo "FAIL: resolve without target should die" >&2
    exit 1
  fi
  grep -q 'no Lightsail SSH target declared' "${ERR}"
  echo "PASS: common resolve fails without target"
) && pass=$((pass + 1)) || fail=$((fail + 1))

(
  # shellcheck source=common.sh
  source "${COMMON}"
  unset LIGHTSAIL_SSH_HOST || true
  LIGHTSAIL_TARGET=""
  lightsail_resolve_ssh_target "community"
  [[ "${LIGHTSAIL_SSH_HOST}" == "ezkey-online" ]]
  [[ "${LIGHTSAIL_TARGET}" == "community" ]]
  src="$(lightsail_caddyfile_source_for_sync "${ROOT}/experimental-hybrid/lightsail")"
  [[ "$(basename "${src}")" == "Caddyfile.ezkey-online" ]]
  echo "PASS: community maps host + Caddyfile.ezkey-online"
) && pass=$((pass + 1)) || fail=$((fail + 1))

(
  # shellcheck source=common.sh
  source "${COMMON}"
  unset LIGHTSAIL_SSH_HOST || true
  LIGHTSAIL_TARGET=""
  if (lightsail_resolve_ssh_target "legacy") 2>"${ERR}"; then
    echo "FAIL: unknown --target legacy should die" >&2
    exit 1
  fi
  grep -q 'unknown --target' "${ERR}"
  echo "PASS: unknown --target refused (community-only)"
) && pass=$((pass + 1)) || fail=$((fail + 1))

(
  # shellcheck source=common.sh
  source "${COMMON}"
  LIGHTSAIL_SSH_HOST=ezkey
  LIGHTSAIL_TARGET=""
  # Custom/unknown host + --target community: note override (host not a known alias).
  lightsail_resolve_ssh_target "community"
  [[ "${LIGHTSAIL_SSH_HOST}" == "ezkey" ]]
  [[ "${LIGHTSAIL_TARGET}" == "community" ]]
  echo "PASS: custom LIGHTSAIL_SSH_HOST=ezkey overrides mapped community host"
) && pass=$((pass + 1)) || fail=$((fail + 1))

# --- export: no target fails loudly ---
unset LIGHTSAIL_SSH_HOST || true
unset EZKEY_CONFIRM_WIPE || true
assert_fail "export without target fails" bash "${EXPORT}" --dry-run --apis-only
assert_stderr_contains "export fail mentions #812 or target" "no Lightsail SSH target declared"

# --- export: community dry-run uses ezkey-online + community Caddyfile ---
assert_ok "export --target community --dry-run --sync" \
  bash "${EXPORT}" --target community --dry-run --apis-only --sync-operator-files
assert_stdout_contains "community dry-run SSH host" "SSH: ezkey-online"
assert_stdout_contains "community dry-run Caddyfile source" "Caddyfile source=Caddyfile.ezkey-online"
assert_stdout_contains "community dry-run scp Caddyfile.ezkey-online" "Caddyfile.ezkey-online"
# Sync must scp Caddyfile.ezkey-online (not a bare lightsail/Caddyfile path alone)
if grep -E 'scp .*lightsail/Caddyfile ' "${OUT}" | grep -vq 'Caddyfile.ezkey-online'; then
  echo "FAIL: community sync must scp Caddyfile.ezkey-online" >&2
  fail=$((fail + 1))
else
  echo "PASS: community sync uses Caddyfile.ezkey-online"
  pass=$((pass + 1))
fi

# --- export: community sync + remote-up force-recreates caddy ---
assert_ok "export community sync+remote-up dry-run" \
  bash "${EXPORT}" --target community --dry-run --apis-only --sync-operator-files --remote-up
assert_stdout_contains "community force-recreate caddy" "force-recreate caddy"
assert_stdout_contains "community sha256 verify" "verify remote Caddyfile sha256"

# --- export: community --clean-start requires wipe confirm ---
assert_fail "export community --clean-start without confirm fails" \
  bash "${EXPORT}" --target community --dry-run --apis-only --clean-start
assert_stderr_contains "export wipe confirm required" "EZKEY_CONFIRM_WIPE=ezkey-online"

assert_ok "export community --clean-start with confirm" \
  env EZKEY_CONFIRM_WIPE=ezkey-online bash "${EXPORT}" --target community --dry-run --apis-only --clean-start

# --- export: LIGHTSAIL_SSH_HOST alone ---
assert_ok "export via LIGHTSAIL_SSH_HOST=ezkey-online" \
  env LIGHTSAIL_SSH_HOST=ezkey-online bash "${EXPORT}" --dry-run --apis-only
assert_stdout_contains "env host community" "SSH: ezkey-online"

# --- export: unknown --target ---
assert_fail "export --target legacy refused" \
  bash "${EXPORT}" --target legacy --dry-run --apis-only
assert_stderr_contains "export unknown target" "unknown --target"

# --- full upgrade: no target / no subcommand ---
assert_fail "upgrade rolling without target fails" bash "${UPGRADE}" rolling --dry-run
assert_stderr_contains "upgrade fail mentions target" "no Lightsail SSH target declared"

assert_ok "upgrade --target community alone prints help" bash "${UPGRADE}" --target community
assert_stdout_contains "upgrade help mentions Usage" "Usage:"

# --- full upgrade: community dry-run ---
assert_ok "upgrade --target community rolling --dry-run" \
  bash "${UPGRADE}" --target community rolling --dry-run
assert_stdout_contains "upgrade community host" "LIGHTSAIL_SSH_HOST: ezkey-online"
assert_stdout_contains "upgrade community caddy recreate" "force-recreate caddy"
assert_stdout_contains "upgrade community stamps OCI revision" "OCI_IMAGE_REVISION="
assert_stdout_contains "upgrade community OCI label" "org.opencontainers.image.revision"

assert_ok "upgrade rolling dry-run honors OCI_IMAGE_REVISION override" \
  env OCI_IMAGE_REVISION=deadbeefcafebabe \
    bash "${UPGRADE}" --target community rolling --dry-run
assert_stdout_contains "upgrade OCI override" "OCI_IMAGE_REVISION=deadbeefcafebabe"

# Dirty-tree warning: verify the warning string exists in the script source.
assert_ok "upgrade script mentions dirty-tree warning" \
  grep -q 'working tree is dirty' "${UPGRADE}"

# --- full upgrade: community full / --clean-start wipe guard ---
assert_fail "upgrade community full without confirm fails" \
  bash "${UPGRADE}" --target community full --dry-run
assert_stderr_contains "upgrade wipe confirm required" "EZKEY_CONFIRM_WIPE=ezkey-online"

assert_ok "upgrade community full with confirm" \
  env EZKEY_CONFIRM_WIPE=ezkey-online bash "${UPGRADE}" --target community full --dry-run

assert_fail "upgrade --target legacy refused" bash "${UPGRADE}" --target legacy rolling --dry-run

# --- bootstrap ---
assert_fail "bootstrap without target fails" bash "${BOOTSTRAP}" --dry-run
assert_ok "bootstrap --target community --dry-run" bash "${BOOTSTRAP}" --target community --dry-run
assert_stdout_contains "bootstrap community" "ezkey-online"

# --- create-instance ---
assert_fail "create without target fails" bash "${CREATE}"
assert_ok "create --target community dry-run" bash "${CREATE}" --target community
assert_stdout_contains "create dry-run skips get-instance" "skipping get-instance"
assert_fail "create --target legacy refused" bash "${CREATE}" --target legacy

# --- delete-instance: static-IP warning + apply guards (fake aws only) ---
: >"${FAKE_AWS_LOG}"
unset EZKEY_CONFIRM_DELETE || true
export FAKE_AWS_STATIC_IP_ATTACHED_TO=""

assert_ok "delete dry-run shows static-IP warning" bash "${DELETE}" --name ezkey-online
assert_stdout_contains "delete dry-run warns static IP" "WARNING: Deleting a Lightsail instance detaches"
assert_stdout_contains "delete dry-run never release" "never release"
assert_stdout_contains "delete dry-run mentions community confirm" "EZKEY_CONFIRM_DELETE=ezkey-online"
if fake_aws_log_lacks "delete-instance"; then
  echo "PASS: delete dry-run never called delete-instance"
  pass=$((pass + 1))
else
  echo "FAIL: delete dry-run must not call delete-instance" >&2
  cat "${FAKE_AWS_LOG}" >&2 || true
  fail=$((fail + 1))
fi

assert_fail "delete --apply without --name fails" bash "${DELETE}" --apply --i-mean-it
assert_stderr_contains "delete apply requires --name" "--apply requires an explicit --name"

: >"${FAKE_AWS_LOG}"
assert_fail "delete community apply without EZKEY_CONFIRM_DELETE fails" \
  bash "${DELETE}" --name ezkey-online --apply --i-mean-it
assert_stderr_contains "delete community confirm required" "EZKEY_CONFIRM_DELETE=ezkey-online"
if fake_aws_log_lacks "delete-instance"; then
  echo "PASS: community delete without confirm never reached delete-instance"
  pass=$((pass + 1))
else
  echo "FAIL: community delete without confirm called delete-instance" >&2
  cat "${FAKE_AWS_LOG}" >&2 || true
  fail=$((fail + 1))
fi

: >"${FAKE_AWS_LOG}"
assert_fail "delete EZKEY-ONLINE (case) without confirm fails" \
  bash "${DELETE}" --name 'EZKEY-ONLINE' --apply --i-mean-it
assert_stderr_contains "delete case-normalize confirm" "EZKEY_CONFIRM_DELETE=ezkey-online"
if fake_aws_log_lacks "delete-instance"; then
  echo "PASS: case-variant name without confirm never reached delete-instance"
  pass=$((pass + 1))
else
  echo "FAIL: case-variant delete without confirm called delete-instance" >&2
  fail=$((fail + 1))
fi

: >"${FAKE_AWS_LOG}"
assert_fail "delete spaced name without confirm fails" \
  bash "${DELETE}" --name '  ezkey-online  ' --apply --i-mean-it
assert_stderr_contains "delete trim-normalize confirm" "EZKEY_CONFIRM_DELETE=ezkey-online"

: >"${FAKE_AWS_LOG}"
export FAKE_AWS_STATIC_IP_ATTACHED_TO="lab-scratch"
assert_fail "delete attached-static-ip instance without confirm fails" \
  bash "${DELETE}" --name lab-scratch --apply --i-mean-it
assert_stderr_contains "delete attached static IP confirm" "EZKEY_CONFIRM_DELETE=ezkey-online"
if fake_aws_log_has "get-static-ip"; then
  echo "PASS: attachment check called get-static-ip"
  pass=$((pass + 1))
else
  echo "FAIL: expected get-static-ip before delete confirm" >&2
  cat "${FAKE_AWS_LOG}" >&2 || true
  fail=$((fail + 1))
fi
if fake_aws_log_lacks "delete-instance"; then
  echo "PASS: attached-static-ip without confirm never reached delete-instance"
  pass=$((pass + 1))
else
  echo "FAIL: attached-static-ip without confirm called delete-instance" >&2
  fail=$((fail + 1))
fi

: >"${FAKE_AWS_LOG}"
export FAKE_AWS_STATIC_IP_ATTACHED_TO=""
# With confirm, apply reaches delete-instance; fake aws must refuse (exit 99).
assert_fail "delete community with confirm hits fake aws delete refusal" \
  env EZKEY_CONFIRM_DELETE=ezkey-online bash "${DELETE}" --name ezkey-online --apply --i-mean-it
assert_stderr_contains "fake aws blocks delete-instance" "FAKE AWS refused delete-instance"
if fake_aws_log_has "delete-instance"; then
  echo "PASS: confirmed delete attempted delete-instance (blocked by fake aws)"
  pass=$((pass + 1))
else
  echo "FAIL: confirmed delete should attempt delete-instance against fake aws" >&2
  cat "${FAKE_AWS_LOG}" >&2 || true
  fail=$((fail + 1))
fi

(
  # shellcheck source=common.sh
  source "${COMMON}"
  export FAKE_AWS_STATIC_IP_ATTACHED_TO=""
  [[ "$(lightsail_normalize_instance_name '  EZKEY-ONLINE  ')" == "ezkey-online" ]]
  lightsail_is_community_instance_name ' EZKEY-ONLINE ' || exit 1
  if (lightsail_require_community_delete_confirm "ezkey-online") 2>"${ERR}"; then
    echo "FAIL: community delete confirm should die without env" >&2
    exit 1
  fi
  grep -q 'EZKEY_CONFIRM_DELETE=ezkey-online' "${ERR}"
  if (lightsail_require_community_delete_confirm "  EZKEY-ONLINE  ") 2>"${ERR}"; then
    echo "FAIL: normalized community name should require confirm" >&2
    exit 1
  fi
  EZKEY_CONFIRM_DELETE=ezkey-online
  lightsail_require_community_delete_confirm "ezkey-online"
  lightsail_require_community_delete_confirm "other-instance"
  export FAKE_AWS_STATIC_IP_ATTACHED_TO="other-instance"
  unset EZKEY_CONFIRM_DELETE || true
  if (lightsail_require_community_delete_confirm "other-instance") 2>"${ERR}"; then
    echo "FAIL: static-IP attachment should require confirm" >&2
    exit 1
  fi
  grep -q 'EZKEY_CONFIRM_DELETE=ezkey-online' "${ERR}"
  EZKEY_CONFIRM_DELETE=ezkey-online
  lightsail_require_community_delete_confirm "other-instance"
  echo "PASS: community delete confirm helper (normalize + static IP)"
) && pass=$((pass + 1)) || fail=$((fail + 1))

echo
echo "Results: ${pass} passed, ${fail} failed"
if [[ "${fail}" -ne 0 ]]; then
  exit 1
fi
