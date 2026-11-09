#!/usr/bin/env bash
# Host-grade acceptance (#1200). One script, two images.
#
#   ./scripts/host-grade-accept.sh
#
# Builds the existing Rocky and AL2023 builder and runtime images, then runs
# this same file inside each runtime (--inside). Inside, it sources
# host-grade-fixtures.sh and host-grade-scenarios.sh. Apply, remove, and
# rollback are scripts/host-apply.sh. The scenario proves HOST-GRADE.md
# against integration-api-stub.py.
#
# --inside refuses to run outside a container. It is not install.sh and it
# does not edit a host sshd_config. install.sh still only warns.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAM_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Read by host-grade-fixtures.sh and host-grade-scenarios.sh, sourced below.
# shellcheck disable=SC2034
hg_suite_vars() {
HG_IKEY="hgIkeyDoNotLog0001"
HG_SKEY="hgSkeyDoNotLog0001"
HG_CHALLENGE="83"
STUB_PORT="18443"
TLS_STUB_PORT="18445"
DOWN_PORT="18081"
MFA_PORT="22"
ADMIN_PORT="2224"
MFA_USER="hgmfa"
PLAIN_USER="hgplain"
ADMIN_USER="hgadmin"
MFA_GROUP="ezkey-mfa"
CONF="/etc/security/pam_ezkey.conf"
SSHD_CFG="/etc/ssh/sshd_config"
PAM_SSHD="/etc/pam.d/sshd"
ADMIN_CFG="/etc/ssh/sshd_admin_config"
STATE_DIR="/var/lib/ezkey-pam/host-grade"
SNAP="${STATE_DIR}/snapshot"
ROLL="${STATE_DIR}/rollback"
STUB_LOG="/tmp/hg-stub-reqs.log"
STUB_PID="/tmp/hg-stub.pid"
TLS_STUB_PID="/tmp/hg-tls-stub.pid"
TLS_STUB_LOG="/tmp/hg-tls-stub-reqs.log"
KEY_DIR="/etc/ezkey-pam-hostgrade/keys"
CA_DIR="/etc/ezkey-pam-hostgrade/ca"
SSHD="/usr/sbin/sshd"
}
hg_suite_vars

# shellcheck disable=SC1091
. "${SCRIPT_DIR}/host-grade-fixtures.sh"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/host-grade-scenarios.sh"

hg_host() {
  local art
  # shellcheck disable=SC1091
  . "${SCRIPT_DIR}/common.sh"
  art="$(ezkey_pam_artifact_dir)"
  mkdir -p "${art}"
  : >"${art}/host-grade-builders.txt"
  local fail=0

  hg_host_build "builder_rocky" \
    docker build --target builder -t ezkey-pam-builder:host-grade . \
    || fail=1
  if [ "${fail}" -eq 0 ]; then
    hg_host_build "runtime_rocky" \
      docker build --target runtime -t ezkey-pam-ssh:host-grade . \
      || fail=1
  else
    echo "runtime_rocky=SKIP" >>"${art}/host-grade-builders.txt"
  fi
  # Rocky suite even if we will also run AL2023. Track suite failure apart
  # from a later image. fail stays sticky.
  local rocky_fail=0
  if grep -q '^runtime_rocky=PASS$' "${art}/host-grade-builders.txt"; then
    hg_host_run rocky ezkey-pam-ssh:host-grade || rocky_fail=1
  else
    rocky_fail=1
  fi

  local al_build_fail=0
  hg_host_build "builder_al2023" \
    docker build -f Dockerfile.al2023 --target builder-al2023 \
    -t ezkey-pam-builder-al2023:host-grade . \
    || al_build_fail=1
  if [ "${al_build_fail}" -eq 0 ]; then
    hg_host_build "runtime_al2023" \
      docker build -f Dockerfile.al2023 --target runtime-al2023 \
      -t ezkey-pam-ssh-al2023:host-grade . \
      || al_build_fail=1
  else
    echo "runtime_al2023=SKIP" >>"${art}/host-grade-builders.txt"
  fi
  local al_fail=0
  if grep -q '^runtime_al2023=PASS$' "${art}/host-grade-builders.txt"; then
    hg_host_run al2023 ezkey-pam-ssh-al2023:host-grade || al_fail=1
  else
    al_fail=1
  fi

  if [ "${rocky_fail}" -ne 0 ] || [ "${al_fail}" -ne 0 ] || [ "${fail}" -ne 0 ]; then
    echo "host-grade: Rocky rc=${rocky_fail} AL2023 rc=${al_fail}" >&2
    exit 1
  fi
  echo "host-grade: both images passed"
}

hg_host_build() {
  local key="$1"
  shift
  local art
  art="$(ezkey_pam_artifact_dir)"
  echo "== docker build ${key} =="
  if "$@"; then
    echo "${key}=PASS" >>"${art}/host-grade-builders.txt"
    return 0
  fi
  echo "${key}=FAIL" >>"${art}/host-grade-builders.txt"
  return 1
}

hg_host_run() {
  local label="$1"
  local image="$2"
  local art
  art="$(ezkey_pam_artifact_dir)"
  echo "== acceptance inside ${label} (${image}) =="
  set +e
  docker run --rm \
    --name "ezkey-pam-hg-${label}" \
    -v "${PAM_ROOT}:/ezkey-pam:ro" \
    -v "${art}:/artifacts" \
    -e EZKEY_PAM_INSIDE=1 \
    -e EZKEY_PAM_IMAGE_LABEL="${label}" \
    -e EZKEY_PAM_ARTIFACT_DIR=/artifacts \
    --entrypoint /ezkey-pam/scripts/host-grade-accept.sh \
    "${image}" \
    --inside
  local rc=$?
  set -e
  echo "suite_${label}=${rc}" >>"${art}/host-grade-builders.txt"
  return "${rc}"
}

case "${1:-}" in
  --inside)
    hg_run_scenarios
    ;;
  *)
    cd "${PAM_ROOT}"
    hg_host
    ;;
esac
