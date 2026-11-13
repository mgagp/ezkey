#!/usr/bin/env bash
#
# Thin status helper for the community Lightsail instance (get-instance).
#
# Usage (from repository root, Git Bash):
#   ./scripts/lightsail/status.sh
#   ./scripts/lightsail/status.sh --name ezkey-online
#   ./scripts/lightsail/status.sh --list
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

INSTANCE_NAME="${LIGHTSAIL_INSTANCE_NAME}"
LIST_ALL=""

usage() {
  cat <<'EOF'
Show Lightsail instance status for the community host (read-only).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/status.sh [options]

Options:
  --name NAME   Instance name (default: ezkey-online / LIGHTSAIL_INSTANCE_NAME)
  --list        Call get-instances (all instances in the region) instead of get-instance
  -h, --help    Show this help

Environment: AWS_PROFILE, LIGHTSAIL_REGION, LIGHTSAIL_INSTANCE_NAME
EOF
  echo
  lightsail_print_defaults
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)
      [[ $# -ge 2 ]] || lightsail_die "--name requires a value"
      INSTANCE_NAME="$2"
      shift 2
      ;;
    --list)
      LIST_ALL="1"
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

lightsail_require_aws_cli

if [[ -n "${LIST_ALL}" ]]; then
  lightsail_aws get-instances \
    --query 'instances[].{name:name,state:state.name,publicIp:publicIpAddress,az:location.availabilityZone,blueprint:blueprintId,bundle:bundleId}' \
    --output table
  exit 0
fi

echo "Instance: ${INSTANCE_NAME} (profile=${AWS_PROFILE}, region=${LIGHTSAIL_REGION})"
lightsail_aws get-instance --instance-name "${INSTANCE_NAME}" \
  --query 'instance.{name:name,state:state.name,publicIp:publicIpAddress,privateIp:privateIpAddress,az:location.availabilityZone,blueprint:blueprintId,bundle:bundleId,username:username}' \
  --output table
