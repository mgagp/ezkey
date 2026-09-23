#!/usr/bin/env bash
#
# Delete a Lightsail instance by name. Default is dry-run.
# Destroy requires BOTH --apply and --i-mean-it (supports future one-shot recreate tests).
# Never prints secrets. Does not release static IPs (v1 allocates none) or change DNS.
#
# Usage (from repository root, Git Bash):
#   ./scripts/lightsail/delete-instance.sh --name ezkey-online
#   ./scripts/lightsail/delete-instance.sh --name ezkey-online --apply --i-mean-it
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

APPLY=""
I_MEAN_IT=""
INSTANCE_NAME="${LIGHTSAIL_INSTANCE_NAME}"

usage() {
  cat <<'EOF'
Delete a Lightsail instance by name (community-host tooling).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/delete-instance.sh [options]

Safety:
  Default is dry-run (prints planned aws CLI only).
  Actual delete requires BOTH --apply and --i-mean-it.
  Refuses to delete live EXP1 (exp1-ezkey) unless --force-exp1 is also passed
  (still requires --apply --i-mean-it). Prefer leaving EXP1 alone.

Options:
  --name NAME       Instance name (default: ezkey-online / LIGHTSAIL_INSTANCE_NAME)
  --apply           Required with --i-mean-it to delete
  --i-mean-it       Extra confirm flag for destroy
  --force-exp1      Allow targeting exp1-ezkey (discouraged)
  -h, --help        Show this help

Environment: AWS_PROFILE, LIGHTSAIL_REGION, LIGHTSAIL_INSTANCE_NAME
EOF
  echo
  lightsail_print_defaults
}

FORCE_EXP1=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)
      [[ $# -ge 2 ]] || lightsail_die "--name requires a value"
      INSTANCE_NAME="$2"
      shift 2
      ;;
    --apply)
      APPLY="1"
      shift
      ;;
    --i-mean-it)
      I_MEAN_IT="1"
      shift
      ;;
    --force-exp1)
      FORCE_EXP1="1"
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

if [[ "${INSTANCE_NAME}" == "${LIGHTSAIL_EXP1_INSTANCE_NAME}" && -z "${FORCE_EXP1}" ]]; then
  lightsail_die "refusing to delete live EXP1 '${LIGHTSAIL_EXP1_INSTANCE_NAME}'. Use a community name, or pass --force-exp1 with --apply --i-mean-it (discouraged)."
fi

CMD_ARGS=(delete-instance --instance-name "${INSTANCE_NAME}")

echo "Plan: delete Lightsail instance '${INSTANCE_NAME}' (region=${LIGHTSAIL_REGION}, profile=${AWS_PROFILE})"

if [[ -z "${APPLY}" || -z "${I_MEAN_IT}" ]]; then
  echo "Dry-run / incomplete confirm. Delete requires: --apply --i-mean-it"
  lightsail_print_dry_run_aws "${CMD_ARGS[@]}"
  if [[ -n "${APPLY}" && -z "${I_MEAN_IT}" ]]; then
    echo "note: --apply was set but --i-mean-it was not; no delete performed." >&2
  fi
  if [[ -z "${APPLY}" && -n "${I_MEAN_IT}" ]]; then
    echo "note: --i-mean-it was set but --apply was not; no delete performed." >&2
  fi
  exit 0
fi

lightsail_require_aws_cli
echo "Deleting instance '${INSTANCE_NAME}'…"
lightsail_aws "${CMD_ARGS[@]}"
echo "Delete requested. Confirm with: ./scripts/lightsail/status.sh --name ${INSTANCE_NAME}"
