#!/usr/bin/env bash
#
# Read-only Host OS hygiene signal for the community Lightsail AL2023 host.
# SSH as ec2-user via a declared target or LIGHTSAIL_SSH_HOST — no silent default (#812).
#
# Never runs dnf update/upgrade, never reboots, never creates snapshots.
# Intended for rootbeer-local agents (cloud agents have no SSH route).
#
# Exit 0 when the probe finishes, including when a newer release or advisories
# are found and when the probe is inconclusive (RELEASE_UPDATE=unknown).
# Non-zero only for bad arguments or ssh failure. The weekly routine decides
# from the RELEASE_UPDATE line, not from the exit status.
#
# Usage (from repository root, Git Bash):
#   ./scripts/lightsail/os-patch-check.sh --target community
#   ./scripts/lightsail/os-patch-check.sh --target community --dry-run
#   LIGHTSAIL_SSH_HOST=ezkey-online ./scripts/lightsail/os-patch-check.sh --dry-run
#
# Canon: product-docs/global/hygiene/host-os/README.md (#909)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

DRY_RUN=""
DECLARED_TARGET=""
HOST_FLAG=""
SSH_USER="${LIGHTSAIL_SSH_USER}"
SSH_IDENTITY="${LIGHTSAIL_SSH_IDENTITY_FILE:-}"

usage() {
  cat <<EOF
Read-only Host OS patch / releasever signal on a Lightsail AL2023 host (#909).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/os-patch-check.sh --target community [options]
  LIGHTSAIL_SSH_HOST=ezkey-online ./scripts/lightsail/os-patch-check.sh [options]

This script SSHes as ec2-user and reports (read-only):
  - /etc/system-release and /etc/dnf/vars/releasever (if present)
  - dnf check-release-update (installed vs latest dated release, releases behind)
  - cumulative advisories on that latest release:
      dnf --releasever=<latest> updateinfo summary
      dnf --releasever=<latest> updateinfo list --sec-severity=Critical
      dnf --releasever=<latest> updateinfo list --sec-severity=Important
    (one severity per call; never the locked repo, which hides newer releases)
  - needs-restarting -r (exit 1 = reboot required; exit 0 = no reboot)
  - uname -r
  - docker ps
  - one machine-readable line:
      RELEASE_UPDATE=yes|no|unknown INSTALLED=<ver or -> LATEST=<ver or -> BEHIND=<n or -> CRITICAL=<n or -> IMPORTANT=<n or ->
    A dash means unknown, not zero. Exit status stays 0 when this line is printed.

It does NOT:
  - run dnf update / dnf upgrade
  - reboot the host
  - create or delete Lightsail snapshots
  - touch Docker compose or application data

SSH target (required — no silent default; #812):
  --target community   SSH Host ${LIGHTSAIL_SSH_HOST_COMMUNITY}
  --host ALIAS         Explicit OpenSSH Host / hostname (or set LIGHTSAIL_SSH_HOST)

Options:
  --user USER             SSH user (default: ec2-user)
  --identity-file PATH    Optional IdentityFile for ssh -i (or set LIGHTSAIL_SSH_IDENTITY_FILE)
  --dry-run               Print remote commands only (no ssh)
  -h, --help              Show this help

Executable by a rootbeer-local agent (SSH + key in workstation config). Cloud agents
have no SSH route — do not run this against the live host from cloud.
EOF
  echo
  lightsail_print_defaults
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      [[ $# -ge 2 ]] || lightsail_die "--target requires community"
      DECLARED_TARGET="$2"
      shift 2
      ;;
    --host)
      [[ $# -ge 2 ]] || lightsail_die "--host requires a value"
      HOST_FLAG="$2"
      shift 2
      ;;
    --user)
      [[ $# -ge 2 ]] || lightsail_die "--user requires a value"
      SSH_USER="$2"
      shift 2
      ;;
    --identity-file)
      [[ $# -ge 2 ]] || lightsail_die "--identity-file requires a path"
      SSH_IDENTITY="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -n "${HOST_FLAG}" ]]; then
  LIGHTSAIL_SSH_HOST="${HOST_FLAG}"
fi
lightsail_resolve_ssh_target "${DECLARED_TARGET}"
SSH_HOST="${LIGHTSAIL_SSH_HOST}"

# Parser first, then the remote probe, as one bash -s payload (functions exist
# before the probe runs; the host does not need a checkout of this repo).
REMOTE_SCRIPT="$(
  cat "${SCRIPT_DIR}/os-patch-parse.sh"
  printf '\n'
  cat "${SCRIPT_DIR}/os-patch-check-remote.sh"
)"

ssh_cmd=(ssh)
if [[ -n "${SSH_IDENTITY}" ]]; then
  [[ -f "${SSH_IDENTITY}" ]] || lightsail_die "identity file not found: ${SSH_IDENTITY}"
  ssh_cmd+=(-i "${SSH_IDENTITY}")
fi
ssh_target="${SSH_HOST}"
if [[ "${SSH_HOST}" != *"@"* ]]; then
  ssh_target="${SSH_USER}@${SSH_HOST}"
fi
ssh_cmd+=("${ssh_target}" "bash -s")

echo "OS patch check target: ${ssh_target} (LIGHTSAIL_TARGET=${LIGHTSAIL_TARGET:-custom}) [read-only]"
if [[ -n "${DRY_RUN}" ]]; then
  echo "[dry-run] would run:"
  printf ' %q' "${ssh_cmd[@]}"
  echo
  echo "----- remote script -----"
  echo "${REMOTE_SCRIPT}"
  echo "----- end remote script -----"
else
  "${ssh_cmd[@]}" <<<"${REMOTE_SCRIPT}"
fi
