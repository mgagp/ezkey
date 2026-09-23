#!/usr/bin/env bash
#
# Open Lightsail firewall ports for the community / ezkey.online host (SSH 22 + HTTPS 443).
# Default: dry-run. Pass --apply to call Lightsail APIs.
#
# Lab default opens 443 to 0.0.0.0/0. TODO (follow-up, human OK): restrict 443 to Cloudflare
# IPv4 CIDRs like EXP1 (https://www.cloudflare.com/ips-v4). No DNS/cert changes here.
#
# Usage (from repository root, Git Bash):
#   ./scripts/lightsail/open-ports.sh
#   ./scripts/lightsail/open-ports.sh --apply
#   ./scripts/lightsail/open-ports.sh --apply --name ezkey-online --ssh-cidr 203.0.113.10/32
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

APPLY=""
INSTANCE_NAME="${LIGHTSAIL_INSTANCE_NAME}"
SSH_CIDR="${LIGHTSAIL_SSH_CIDR:-0.0.0.0/0}"
HTTPS_CIDR="${LIGHTSAIL_HTTPS_CIDR:-0.0.0.0/0}"
MODE="open" # open = additive open-instance-public-ports; put = replace-all put-instance-public-ports

usage() {
  cat <<'EOF'
Open Lightsail instance public ports (SSH 22 + HTTPS 443) for the community host.

Usage (from repository root, Git Bash):
  ./scripts/lightsail/open-ports.sh [options]

Safety:
  Default is dry-run. Pass --apply to mutate the instance firewall.
  Does not allocate static IPs, change DNS, or touch certificates.
  Does not target exp1-ezkey unless you explicitly pass --name exp1-ezkey (discouraged).

Options:
  --name NAME           Instance name (default: ezkey-online / LIGHTSAIL_INSTANCE_NAME)
  --ssh-cidr CIDR       Source CIDR for TCP/22 (default: 0.0.0.0/0 or LIGHTSAIL_SSH_CIDR)
  --https-cidr CIDR     Source CIDR for TCP/443 (default: 0.0.0.0/0 or LIGHTSAIL_HTTPS_CIDR)
  --mode open|put       open = additive OpenInstancePublicPorts (default);
                        put  = PutInstancePublicPorts (replaces ALL rules with 22+443 only)
  --apply               Actually open/replace ports
  -h, --help            Show this help

TODO (not automated here): restrict 443 to Cloudflare IPv4 CIDRs after lab cutoff
  (see experimental-hybrid/DEPLOYMENT_PLAYBOOK.md Phase 0a).

Environment: AWS_PROFILE, LIGHTSAIL_REGION, LIGHTSAIL_INSTANCE_NAME,
  LIGHTSAIL_SSH_CIDR, LIGHTSAIL_HTTPS_CIDR
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
    --ssh-cidr)
      [[ $# -ge 2 ]] || lightsail_die "--ssh-cidr requires a value"
      SSH_CIDR="$2"
      shift 2
      ;;
    --https-cidr)
      [[ $# -ge 2 ]] || lightsail_die "--https-cidr requires a value"
      HTTPS_CIDR="$2"
      shift 2
      ;;
    --mode)
      [[ $# -ge 2 ]] || lightsail_die "--mode requires open or put"
      MODE="$2"
      shift 2
      ;;
    --apply)
      APPLY="1"
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

case "${MODE}" in
  open|put) ;;
  *) lightsail_die "--mode must be open or put" ;;
esac

if [[ "${INSTANCE_NAME}" == "${LIGHTSAIL_EXP1_INSTANCE_NAME}" ]]; then
  echo "warning: targeting live EXP1 instance '${LIGHTSAIL_EXP1_INSTANCE_NAME}'. Proceed only if intentional." >&2
fi

if [[ -n "${APPLY}" ]]; then
  lightsail_require_aws_cli
fi

echo "Plan: firewall ports on '${INSTANCE_NAME}' (mode=${MODE})"
echo "  SSH   TCP/22  cidrs=${SSH_CIDR}"
echo "  HTTPS TCP/443 cidrs=${HTTPS_CIDR}"
echo "  TODO: replace world-open 443 with Cloudflare IPv4 CIDRs for production-like posture."

if [[ "${MODE}" == "put" ]]; then
  PUT_ARGS=(
    put-instance-public-ports
    --instance-name "${INSTANCE_NAME}"
    --port-infos
    "fromPort=22,toPort=22,protocol=TCP,cidrs=${SSH_CIDR}"
    "fromPort=443,toPort=443,protocol=TCP,cidrs=${HTTPS_CIDR}"
  )
  if [[ -z "${APPLY}" ]]; then
    echo "Dry-run only. Re-run with --apply to apply."
    lightsail_print_dry_run_aws "${PUT_ARGS[@]}"
    exit 0
  fi
  echo "Replacing public ports (put-instance-public-ports)…"
  lightsail_aws "${PUT_ARGS[@]}"
else
  SSH_ARGS=(
    open-instance-public-ports
    --instance-name "${INSTANCE_NAME}"
    --port-info "fromPort=22,toPort=22,protocol=TCP,cidrs=${SSH_CIDR}"
  )
  HTTPS_ARGS=(
    open-instance-public-ports
    --instance-name "${INSTANCE_NAME}"
    --port-info "fromPort=443,toPort=443,protocol=TCP,cidrs=${HTTPS_CIDR}"
  )
  if [[ -z "${APPLY}" ]]; then
    echo "Dry-run only. Re-run with --apply to apply."
    lightsail_print_dry_run_aws "${SSH_ARGS[@]}"
    lightsail_print_dry_run_aws "${HTTPS_ARGS[@]}"
    exit 0
  fi
  echo "Opening TCP/22…"
  lightsail_aws "${SSH_ARGS[@]}"
  echo "Opening TCP/443…"
  lightsail_aws "${HTTPS_ARGS[@]}"
fi

echo "Done. Verify in Lightsail console → Networking, or:"
echo "  ./scripts/lightsail/status.sh --name ${INSTANCE_NAME}"
