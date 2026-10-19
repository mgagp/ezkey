#!/usr/bin/env bash
#
# Delete a Lightsail instance by name. Default is dry-run.
# Destroy requires BOTH --apply and --i-mean-it (supports future one-shot recreate tests).
# Never prints secrets. Does not release static IPs (community static IP is operator-managed)
# or change DNS.
#
# Usage (from repository root, Git Bash):
#   ./scripts/lightsail/delete-instance.sh --name ezkey-online
#   EZKEY_CONFIRM_DELETE=ezkey-online \
#     ./scripts/lightsail/delete-instance.sh --name ezkey-online --apply --i-mean-it
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

APPLY=""
I_MEAN_IT=""
NAME_SET=""
INSTANCE_NAME="${LIGHTSAIL_INSTANCE_NAME}"

# Warn on every path (dry-run and apply). Scripts never call release-static-ip.
lightsail_warn_static_ip_on_delete() {
  cat <<'EOF'
WARNING: Deleting a Lightsail instance detaches any attached static IP but does NOT
release it. An unattached static IP is billed (~USD 3.60/month). For the community
host, keep static IP ezkey-online-community allocated and re-attach it immediately
after recreate — never release it.
EOF
}

usage() {
  cat <<'EOF'
Delete a Lightsail instance by name (community-host tooling).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/delete-instance.sh [options]

Safety:
  Default is dry-run (prints planned aws CLI only).
  Actual delete requires BOTH --apply and --i-mean-it.
  --apply requires an explicit --name (no silent default).
  Deleting the community instance (name ezkey-online, case/space
  insensitive) or any instance with static IP ezkey-online-community
  attached also requires EZKEY_CONFIRM_DELETE=ezkey-online.
  Never releases static IPs (see WARNING below).

Options:
  --name NAME       Instance name (required with --apply; dry-run default:
                    ezkey-online / LIGHTSAIL_INSTANCE_NAME)
  --apply           Required with --i-mean-it to delete
  --i-mean-it       Extra confirm flag for destroy
  -h, --help        Show this help

Environment: AWS_PROFILE, LIGHTSAIL_REGION, LIGHTSAIL_INSTANCE_NAME,
  EZKEY_CONFIRM_DELETE (must be ezkey-online for community name or when
  static IP ezkey-online-community is attached to the target)
EOF
  echo
  lightsail_print_defaults
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)
      [[ $# -ge 2 ]] || lightsail_die "--name requires a value"
      INSTANCE_NAME="$2"
      NAME_SET="1"
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

lightsail_warn_static_ip_on_delete

if [[ -n "${APPLY}" && -z "${NAME_SET}" ]]; then
  lightsail_die "--apply requires an explicit --name (refusing default LIGHTSAIL_INSTANCE_NAME=${INSTANCE_NAME})"
fi

CMD_ARGS=(delete-instance --instance-name "${INSTANCE_NAME}")

echo "Plan: delete Lightsail instance '${INSTANCE_NAME}' (region=${LIGHTSAIL_REGION}, profile=${AWS_PROFILE})"

if [[ -z "${APPLY}" || -z "${I_MEAN_IT}" ]]; then
  echo "Dry-run / incomplete confirm. Delete requires: --apply --i-mean-it"
  if lightsail_is_community_instance_name "${INSTANCE_NAME}"; then
    echo "Community instance: apply also requires EZKEY_CONFIRM_DELETE=${LIGHTSAIL_COMMUNITY_INSTANCE_NAME}"
  fi
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
lightsail_require_community_delete_confirm "${INSTANCE_NAME}"
echo "Deleting instance '${INSTANCE_NAME}'…"
lightsail_warn_static_ip_on_delete
lightsail_aws "${CMD_ARGS[@]}"
echo "Delete requested. Confirm with: ./scripts/lightsail/status.sh --name ${INSTANCE_NAME}"
echo "Reminder: re-attach static IP ${LIGHTSAIL_COMMUNITY_STATIC_IP_NAME} if this was the community host; never release it."
