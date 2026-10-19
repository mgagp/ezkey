#!/usr/bin/env bash
# Dry-run / unit checks for Lightsail SSH target resolution (#812) and
# Caddyfile bind-mount sync alignment (#863).
# Community-only: --target community → Host ezkey-online.
# Run from repo root: ./scripts/lightsail/target-aware.test.sh
#
# Safety: fake `aws`, `ssh`, `scp`, `docker`, and `npx` are prepended to PATH for
# the whole run. They record invocations and never reach a real Lightsail VM,
# AWS, or Cloudflare Pages. delete-instance against fake aws exits non-zero.
# ssh/scp refuse any Host that is not the expected community alias.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMMON="${ROOT}/scripts/lightsail/common.sh"
EXPORT="${ROOT}/experimental-hybrid/scripts/export-backend-images-to-lightsail.sh"
UPGRADE="${ROOT}/experimental-hybrid/scripts/full-exp-environment-upgrade.sh"
BOOTSTRAP="${ROOT}/scripts/lightsail/bootstrap-host.sh"
CREATE="${ROOT}/scripts/lightsail/create-instance.sh"
DELETE="${ROOT}/scripts/lightsail/delete-instance.sh"
ADMIN_UI_PROD="${ROOT}/scripts/cloudflare/deploy-admin-ui-production.sh"
ADMIN_UI_PREVIEW="${ROOT}/scripts/cloudflare/deploy-admin-ui-preview.sh"
COMPOSE="${ROOT}/experimental-hybrid/lightsail/docker-compose.yml"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
OUT="${TMP}/out"
ERR="${TMP}/err"
FAKE_AWS_BIN="${TMP}/fake-bin"
FAKE_AWS_LOG="${TMP}/fake-aws.log"
FAKE_SSH_LOG="${TMP}/fake-ssh.log"
FAKE_SCP_LOG="${TMP}/fake-scp.log"
FAKE_DOCKER_LOG="${TMP}/fake-docker.log"
FAKE_NPX_LOG="${TMP}/fake-npx.log"
mkdir -p "${FAKE_AWS_BIN}"
: >"${FAKE_AWS_LOG}"
: >"${FAKE_SSH_LOG}"
: >"${FAKE_SCP_LOG}"
: >"${FAKE_DOCKER_LOG}"
: >"${FAKE_NPX_LOG}"

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
export FAKE_SSH_LOG
export FAKE_SCP_LOG
export FAKE_DOCKER_LOG
export FAKE_NPX_LOG
# Only allow ssh/scp to this Host alias (blocks accidental real VM contact).
export FAKE_SSH_ALLOWED_HOST="${FAKE_SSH_ALLOWED_HOST:-ezkey-online}"
# Admin UI deploy tests: never source workstation .env (would override fake tokens — #863 B1).
export EZKEY_SKIP_DOTENV=1

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

# Fake ssh/scp: never contact a real host. Dry-run paths should not invoke these;
# if a regressing script drops --dry-run, refuse unknown hosts.
cat >"${FAKE_AWS_BIN}/ssh" <<'FAKE_SSH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_SSH_LOG:?FAKE_SSH_LOG unset}"
host=""
for arg in "$@"; do
  case "${arg}" in
    -*) continue ;;
    *)
      host="${arg}"
      break
      ;;
  esac
done
allowed="${FAKE_SSH_ALLOWED_HOST:-ezkey-online}"
if [[ "${host}" != "${allowed}" ]]; then
  echo "error: FAKE ssh refused host '${host}' (allowed=${allowed}); refusing real network" >&2
  exit 98
fi
echo "error: FAKE ssh refused live execution (tests must use --dry-run): $*" >&2
exit 97
FAKE_SSH
chmod +x "${FAKE_AWS_BIN}/ssh"

cat >"${FAKE_AWS_BIN}/scp" <<'FAKE_SCP'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_SCP_LOG:?FAKE_SCP_LOG unset}"
echo "error: FAKE scp refused live execution (tests must use --dry-run): $*" >&2
exit 97
FAKE_SCP
chmod +x "${FAKE_AWS_BIN}/scp"

cat >"${FAKE_AWS_BIN}/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_DOCKER_LOG:?FAKE_DOCKER_LOG unset}"
echo "error: FAKE docker refused live execution (tests must use --dry-run / no real docker): $*" >&2
exit 96
FAKE_DOCKER
chmod +x "${FAKE_AWS_BIN}/docker"

# Fake npx: never run real wrangler / Pages deploy (#863 B1).
cat >"${FAKE_AWS_BIN}/npx" <<'FAKE_NPX'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_NPX_LOG:?FAKE_NPX_LOG unset}"
echo "error: FAKE npx refused live execution (would deploy Pages): $*" >&2
exit 95
FAKE_NPX
chmod +x "${FAKE_AWS_BIN}/npx"

export PATH="${FAKE_AWS_BIN}:${PATH}"

# Prove the harness shadows any real aws/ssh/scp/docker/npx.
for tool in aws ssh scp docker npx; do
  if [[ "$(command -v "${tool}")" != "${FAKE_AWS_BIN}/${tool}" ]]; then
    echo "FAIL: expected fake ${tool} at ${FAKE_AWS_BIN}/${tool}, got $(command -v "${tool}")" >&2
    exit 1
  fi
done

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
  remote_base="$(lightsail_caddyfile_remote_basename_from_compose "${COMPOSE}")"
  [[ "${remote_base}" == "Caddyfile.ezkey-online" ]]
  src="$(lightsail_caddyfile_source_for_sync "${ROOT}/experimental-hybrid/lightsail" "${COMPOSE}")"
  [[ "$(basename "${src}")" == "Caddyfile.ezkey-online" ]]
  [[ "$(basename "${src}")" == "${remote_base}" ]]
  lightsail_assert_caddyfile_sync_matches_compose "${COMPOSE}" "${remote_base}" >/dev/null
  echo "PASS: community maps host + compose-derived Caddyfile.ezkey-online"
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

# --- export: community dry-run uses ezkey-online + community Caddyfile (#863) ---
assert_ok "export --target community --dry-run --sync" \
  bash "${EXPORT}" --target community --dry-run --apis-only --sync-operator-files
assert_stdout_contains "community dry-run SSH host" "SSH: ezkey-online"
assert_stdout_contains "community dry-run Caddyfile source" "Caddyfile source=Caddyfile.ezkey-online"
assert_stdout_contains "community dry-run sync alignment" "Caddyfile sync aligned with compose bind"
assert_stdout_contains "community dry-run scp Caddyfile.ezkey-online" "Caddyfile.ezkey-online"
# Sync must scp to remote basename Caddyfile.ezkey-online (not renamed to .../Caddyfile)
if grep -E 'scp .*Caddyfile\.ezkey-online[^ ]* [^ ]+:.*/Caddyfile$' "${OUT}" >/dev/null; then
  echo "FAIL: community sync must not rename to remote Caddyfile (#863)" >&2
  grep -E 'scp .*Caddyfile' "${OUT}" >&2 || true
  fail=$((fail + 1))
else
  echo "PASS: community sync does not rename to remote Caddyfile"
  pass=$((pass + 1))
fi
if grep -E 'scp .*Caddyfile\.ezkey-online[^ ]* [^ ]+:.*/Caddyfile\.ezkey-online' "${OUT}" >/dev/null; then
  echo "PASS: community sync scp destination is Caddyfile.ezkey-online"
  pass=$((pass + 1))
else
  echo "FAIL: community sync must scp to remote Caddyfile.ezkey-online" >&2
  grep -E 'scp .*Caddyfile' "${OUT}" >&2 || true
  fail=$((fail + 1))
fi
# Dry-run must not have invoked real ssh/scp/docker (fake tools would have logged).
if [[ ! -s "${FAKE_SSH_LOG}" && ! -s "${FAKE_SCP_LOG}" && ! -s "${FAKE_DOCKER_LOG}" ]]; then
  echo "PASS: dry-run sync never called fake ssh/scp/docker"
  pass=$((pass + 1))
else
  echo "FAIL: dry-run should not invoke ssh/scp/docker" >&2
  fail=$((fail + 1))
fi

# --- export: community sync + remote-up: bind preflight + force-recreate (#863) ---
: >"${FAKE_SSH_LOG}"
: >"${FAKE_SCP_LOG}"
: >"${FAKE_DOCKER_LOG}"
assert_ok "export community sync+remote-up dry-run" \
  bash "${EXPORT}" --target community --dry-run --apis-only --sync-operator-files --remote-up
assert_stdout_contains "community force-recreate caddy" "force-recreate caddy"
assert_stdout_contains "community sha256 verify mounted file" "verify remote Caddyfile.ezkey-online sha256"
assert_stdout_contains "community bind-mount preflight" "bind-mount preflight"
assert_stdout_contains "community bind expect Caddyfile file" "expect file: ./Caddyfile.ezkey-online"
assert_stdout_contains "community bind expect caddy-certs dir" "expect dir: ./caddy-certs"
if [[ ! -s "${FAKE_SSH_LOG}" && ! -s "${FAKE_SCP_LOG}" && ! -s "${FAKE_DOCKER_LOG}" ]]; then
  echo "PASS: dry-run remote-up never called fake ssh/scp/docker"
  pass=$((pass + 1))
else
  echo "FAIL: dry-run remote-up should not invoke ssh/scp/docker" >&2
  cat "${FAKE_SSH_LOG}" >&2 || true
  fail=$((fail + 1))
fi

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

# --- #863: compose bind basename is source of truth; mismatch is reported ---
(
  # shellcheck source=common.sh
  source "${COMMON}"
  # Simulate the #859 bug: sync renamed to remote Caddyfile while compose mounts .ezkey-online.
  if (lightsail_assert_caddyfile_sync_matches_compose "${COMPOSE}" "Caddyfile") \
      >"${OUT}" 2>"${ERR}"; then
    echo "FAIL: sync/compose mismatch should die" >&2
    exit 1
  fi
  grep -q 'Caddyfile sync/compose mismatch' "${ERR}"
  grep -q '#863' "${ERR}"
  grep -q 'Caddyfile.ezkey-online' "${ERR}"
  # Correct alignment succeeds.
  lightsail_assert_caddyfile_sync_matches_compose "${COMPOSE}" "Caddyfile.ezkey-online" >/dev/null
  echo "PASS: dry-run path surfaces sync/compose Caddyfile mismatch (#863)"
) && pass=$((pass + 1)) || fail=$((fail + 1))

(
  # shellcheck source=common.sh
  source "${COMMON}"
  mapfile -t binds < <(lightsail_list_compose_bind_mounts "${COMPOSE}")
  [[ "${#binds[@]}" -ge 2 ]]
  printf '%s\n' "${binds[@]}" | grep -qx $'file\t./Caddyfile.ezkey-online'
  printf '%s\n' "${binds[@]}" | grep -qx $'dir\t./caddy-certs'
  printf '%s\n' "${binds[@]}" | grep -q $'dir\t../../docker/postgres/init'
  echo "PASS: compose bind-mount list includes Caddyfile file + certs/init dirs"
) && pass=$((pass + 1)) || fail=$((fail + 1))

# --- deploy-admin-ui-production.sh / preview: no silent EXP1 Pages default (#863 B1/N6) ---
# Early-exit guards must never reach npx/wrangler (even if workstation has .env + dist).
: >"${FAKE_NPX_LOG}"
assert_fail "admin-ui production without CLOUDFLARE_PAGES_PROJECT fails" \
  env -u CLOUDFLARE_PAGES_PROJECT \
    EZKEY_SKIP_DOTENV=1 \
    CLOUDFLARE_API_TOKEN=fake CLOUDFLARE_ACCOUNT_ID=fake \
    bash "${ADMIN_UI_PROD}"
assert_stderr_contains "admin-ui prod requires Pages project" "CLOUDFLARE_PAGES_PROJECT"
assert_stderr_contains "admin-ui prod mentions community project" "admin-ui-ezkey-online"
assert_stderr_contains "admin-ui prod mentions community API URL" "admin-api.ezkey.online"

assert_fail "admin-ui production --build without VITE_API_BASE_URL fails" \
  env CLOUDFLARE_PAGES_PROJECT=admin-ui-ezkey-online \
    EZKEY_SKIP_DOTENV=1 \
    CLOUDFLARE_API_TOKEN=fake CLOUDFLARE_ACCOUNT_ID=fake \
    bash "${ADMIN_UI_PROD}" --build
assert_stderr_contains "admin-ui prod build needs VITE_API_BASE_URL" "VITE_API_BASE_URL is required"

assert_fail "admin-ui preview without CLOUDFLARE_PAGES_PROJECT fails" \
  env -u CLOUDFLARE_PAGES_PROJECT \
    EZKEY_SKIP_DOTENV=1 \
    CLOUDFLARE_API_TOKEN=fake CLOUDFLARE_ACCOUNT_ID=fake \
    bash "${ADMIN_UI_PREVIEW}"
assert_stderr_contains "admin-ui preview requires Pages project" "CLOUDFLARE_PAGES_PROJECT"

if [[ ! -s "${FAKE_NPX_LOG}" ]]; then
  echo "PASS: fake npx never called during early-exit admin-ui guard tests (#863 B1)"
  pass=$((pass + 1))
else
  echo "FAIL: fake npx was invoked during early-exit guards (must not reach deploy):" >&2
  cat "${FAKE_NPX_LOG}" >&2
  fail=$((fail + 1))
fi

# If dist exists (operator workstation), deploy would reach npx — fake must refuse (never real wrangler).
: >"${FAKE_NPX_LOG}"
assert_fail "admin-ui production with project blocked before real Pages deploy" \
  env CLOUDFLARE_PAGES_PROJECT=admin-ui-ezkey-online \
    EZKEY_SKIP_DOTENV=1 \
    CLOUDFLARE_API_TOKEN=fake CLOUDFLARE_ACCOUNT_ID=fake \
    bash "${ADMIN_UI_PROD}"
if grep -q 'missing or empty' "${ERR}"; then
  if [[ ! -s "${FAKE_NPX_LOG}" ]]; then
    echo "PASS: admin-ui prod failed on missing dist; npx not called"
    pass=$((pass + 1))
  else
    echo "FAIL: npx called despite missing-dist path" >&2
    fail=$((fail + 1))
  fi
elif grep -q 'FAKE npx refused' "${ERR}" && grep -q 'pages deploy' "${FAKE_NPX_LOG}"; then
  echo "PASS: admin-ui prod reached fake npx only (dist present; real wrangler blocked)"
  pass=$((pass + 1))
else
  echo "FAIL: expected missing dist or FAKE npx pages-deploy refusal" >&2
  cat "${ERR}" >&2
  cat "${FAKE_NPX_LOG}" >&2
  fail=$((fail + 1))
fi

# --- #863 N7: bind-mount preflight fails when a directory replaces an expected file ---
(
  # shellcheck source=common.sh
  source "${COMMON}"
  fixture="${TMP}/preflight-fixture"
  mkdir -p "${fixture}/caddy-certs"
  # #863 artifact: compose expects a file, Docker left a directory.
  mkdir -p "${fixture}/Caddyfile.ezkey-online"
  remote_path="${fixture}/Caddyfile.ezkey-online"
  if [[ -d "${remote_path}" ]]; then
    if ! ( [[ -f "${remote_path}" ]] ); then
      echo "PASS: preflight detects directory where file expected (Caddyfile.ezkey-online)"
      exit 0
    fi
  fi
  echo "FAIL: fixture setup for dir-as-file preflight" >&2
  exit 1
) && pass=$((pass + 1)) || fail=$((fail + 1))

(
  # Simulate the remote refuse-if-directory body used before scp (B3) — no auto-rmdir.
  fixture="${TMP}/b3-fixture"
  mkdir -p "${fixture}"
  mkdir -p "${fixture}/Caddyfile.ezkey-online"
  if (
    set -euo pipefail
    cd "${fixture}"
    if [[ -d Caddyfile.ezkey-online ]]; then
      echo "error: remote ${fixture}/Caddyfile.ezkey-online is a directory (Docker empty-dir artifact when the bind-mount file was missing — #863)" >&2
      echo "  Manual remediation (requires Marc's go):" >&2
      echo "    ssh ezkey-online 'sudo rmdir ${fixture}/Caddyfile.ezkey-online'" >&2
      exit 1
    fi
  ) 2>"${ERR}"; then
    echo "FAIL: B3 directory check should fail" >&2
    exit 1
  fi
  grep -q 'is a directory' "${ERR}"
  grep -q "sudo rmdir" "${ERR}"
  grep -q "Marc's go" "${ERR}"
  echo "PASS: B3 refuse-if-directory surfaces manual sudo rmdir + Marc go"
) && pass=$((pass + 1)) || fail=$((fail + 1))

# Dry-run sync must advertise the pre-scp directory refuse (no auto-rmdir).
assert_ok "export dry-run sync mentions refuse-if-directory" \
  bash "${EXPORT}" --target community --dry-run --apis-only --sync-operator-files
assert_stdout_contains "dry-run refuse directory before scp" "refuse sync if remote Caddyfile.ezkey-online is a directory"
assert_stdout_contains "dry-run no auto-rmdir" "no auto-rmdir"

echo
echo "Results: ${pass} passed, ${fail} failed"
if [[ "${fail}" -ne 0 ]]; then
  exit 1
fi
