#!/usr/bin/env bash
#
# Create a NEW Amazon Lightsail instance for the ezkey.online community-host evaluation path.
# Defaults match EXP1 shape (AL2023, medium_3_0, ca-central-1a) but use a separate instance name.
# Does NOT touch exp1-ezkey. Does NOT change Cloudflare DNS or certificates.
#
# AWS CLI only (no Terraform / CloudFormation). Default is dry-run; pass --apply to create.
#
# Usage (from repository root, Git Bash):
#   export AWS_PROFILE=ezkey-lightsail
#   ./scripts/lightsail/create-instance.sh
#   ./scripts/lightsail/create-instance.sh --apply --key-pair-name ezkey-online
#   ./scripts/lightsail/create-instance.sh --import-public-key ~/.ssh/id_ed25519.pub --apply
#
# Prefer ImportKeyPair of an existing workstation public key over CreateKeyPair.
# Scripts never print private keys or other secrets.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

APPLY=""
WAIT_SECONDS="${LIGHTSAIL_WAIT_SECONDS:-300}"
POLL_SECONDS="${LIGHTSAIL_POLL_SECONDS:-5}"
IMPORT_PUBLIC_KEY=""
CREATE_KEY_PAIR=""
INSTANCE_NAME="${LIGHTSAIL_INSTANCE_NAME}"
AZ="${LIGHTSAIL_AZ}"
BLUEPRINT="${LIGHTSAIL_BLUEPRINT}"
BUNDLE="${LIGHTSAIL_BUNDLE}"
KEY_PAIR_NAME="${LIGHTSAIL_KEY_PAIR_NAME}"
# Empty means: do not pass --key-pair-name (Lightsail default key behavior).
USE_KEY_PAIR="1"

usage() {
  cat <<'EOF'
Create a NEW Lightsail instance for ezkey.online community-host evaluation (parallel to EXP1).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/create-instance.sh [options]

Safety:
  Default is dry-run (prints planned aws CLI). Pass --apply to create.
  Refuses to create if the instance name already exists.
  Never targets exp1-ezkey; never allocates a static IP; never changes DNS/certs.

Options:
  --name NAME                 Instance name (default: ezkey-online / LIGHTSAIL_INSTANCE_NAME)
  --az ZONE                   Availability zone (default: ca-central-1a)
  --blueprint ID              Blueprint id (default: amazon_linux_2023)
  --bundle ID                 Bundle id (default: medium_3_0)
  --key-pair-name NAME        Lightsail key pair to attach (default: ezkey-online)
  --no-key-pair               Omit --key-pair-name on create-instances
  --import-public-key PATH    Import OpenSSH .pub into Lightsail as --key-pair-name (preferred)
  --create-key-pair           Create a Lightsail key pair; write private key to ~/.ssh/ only
                              (never prints the private key; prefer --import-public-key)
  --wait-seconds N            Max seconds to wait for running state after create (default: 300)
  --apply                     Actually create (and import/create key pair if requested)
  -h, --help                  Show this help

Environment:
  AWS_PROFILE                 default ezkey-lightsail
  LIGHTSAIL_REGION            default ca-central-1
  LIGHTSAIL_INSTANCE_NAME     default ezkey-online (use community-ezkey if taken)
  LIGHTSAIL_AZ / LIGHTSAIL_BLUEPRINT / LIGHTSAIL_BUNDLE / LIGHTSAIL_KEY_PAIR_NAME

Next steps after create: ./scripts/lightsail/open-ports.sh then bootstrap-host.sh
  See docs/lightsail/community-host.md
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
    --az)
      [[ $# -ge 2 ]] || lightsail_die "--az requires a value"
      AZ="$2"
      shift 2
      ;;
    --blueprint)
      [[ $# -ge 2 ]] || lightsail_die "--blueprint requires a value"
      BLUEPRINT="$2"
      shift 2
      ;;
    --bundle)
      [[ $# -ge 2 ]] || lightsail_die "--bundle requires a value"
      BUNDLE="$2"
      shift 2
      ;;
    --key-pair-name)
      [[ $# -ge 2 ]] || lightsail_die "--key-pair-name requires a value"
      KEY_PAIR_NAME="$2"
      USE_KEY_PAIR="1"
      shift 2
      ;;
    --no-key-pair)
      USE_KEY_PAIR=""
      shift
      ;;
    --import-public-key)
      [[ $# -ge 2 ]] || lightsail_die "--import-public-key requires a path"
      IMPORT_PUBLIC_KEY="$2"
      shift 2
      ;;
    --create-key-pair)
      CREATE_KEY_PAIR="1"
      shift
      ;;
    --wait-seconds)
      [[ $# -ge 2 ]] || lightsail_die "--wait-seconds requires a value"
      WAIT_SECONDS="$2"
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

if [[ "${INSTANCE_NAME}" == "${LIGHTSAIL_EXP1_INSTANCE_NAME}" ]]; then
  lightsail_die "refusing to create/replace live EXP1 instance '${LIGHTSAIL_EXP1_INSTANCE_NAME}'. Choose another --name."
fi

if [[ -n "${IMPORT_PUBLIC_KEY}" && -n "${CREATE_KEY_PAIR}" ]]; then
  lightsail_die "use either --import-public-key or --create-key-pair, not both"
fi

if [[ -n "${IMPORT_PUBLIC_KEY}" || -n "${CREATE_KEY_PAIR}" ]]; then
  USE_KEY_PAIR="1"
fi

if [[ -n "${APPLY}" ]]; then
  lightsail_require_aws_cli
elif command -v aws >/dev/null 2>&1; then
  :
else
  echo "note: aws CLI not on PATH; dry-run will print planned commands only (no existence check)." >&2
fi

echo "Plan: create Lightsail instance"
echo "  name=${INSTANCE_NAME}  az=${AZ}  blueprint=${BLUEPRINT}  bundle=${BUNDLE}"
echo "  region=${LIGHTSAIL_REGION}  profile=${AWS_PROFILE}"
if [[ -n "${USE_KEY_PAIR}" ]]; then
  echo "  key-pair-name=${KEY_PAIR_NAME}"
else
  echo "  key-pair-name=(none)"
fi
if [[ -n "${IMPORT_PUBLIC_KEY}" ]]; then
  echo "  import-public-key=${IMPORT_PUBLIC_KEY}"
fi
if [[ -n "${CREATE_KEY_PAIR}" ]]; then
  echo "  create-key-pair=yes (private key → ~/.ssh/lightsail-${KEY_PAIR_NAME}.pem, never echoed)"
fi

# Existence check when aws is available (required on --apply; best-effort on dry-run).
if command -v aws >/dev/null 2>&1; then
  if lightsail_instance_exists "${INSTANCE_NAME}"; then
    lightsail_die "instance '${INSTANCE_NAME}' already exists. Pick another --name (e.g. community-ezkey) or delete first."
  fi
  echo "  existence check: name '${INSTANCE_NAME}' is free (or get-instance not authorized / not found)"
fi

ensure_key_pair() {
  if [[ -z "${USE_KEY_PAIR}" ]]; then
    return 0
  fi

# Key pair checks need aws; skip on dry-run when aws is absent.
  if command -v aws >/dev/null 2>&1; then
    if lightsail_aws get-key-pair --key-pair-name "${KEY_PAIR_NAME}" >/dev/null 2>&1; then
      echo "Key pair '${KEY_PAIR_NAME}' already exists in Lightsail; reusing."
      if [[ -n "${IMPORT_PUBLIC_KEY}" || -n "${CREATE_KEY_PAIR}" ]]; then
        echo "note: --import-public-key / --create-key-pair ignored because key pair already exists." >&2
      fi
      return 0
    fi
  elif [[ -z "${APPLY}" ]]; then
    echo "[dry-run] skipping get-key-pair (aws CLI not on PATH)"
  fi

  if [[ -n "${IMPORT_PUBLIC_KEY}" ]]; then
    [[ -f "${IMPORT_PUBLIC_KEY}" ]] || lightsail_die "public key file not found: ${IMPORT_PUBLIC_KEY}"
    # Lightsail CLI expects the OpenSSH public key line (ssh-rsa / ssh-ed25519 …), not a
    # separately base64-wrapped blob — see aws-cli#6020. Strip trailing newline only.
    local pub
    pub="$(tr -d '\r\n' <"${IMPORT_PUBLIC_KEY}")"
    [[ -n "${pub}" ]] || lightsail_die "public key file is empty: ${IMPORT_PUBLIC_KEY}"
    echo "Importing public key as Lightsail key pair '${KEY_PAIR_NAME}'…"
    lightsail_run_or_dry_aws "${APPLY}" import-key-pair \
      --key-pair-name "${KEY_PAIR_NAME}" \
      --public-key-base64 "${pub}"
    return 0
  fi

  if [[ -n "${CREATE_KEY_PAIR}" ]]; then
    local pem_path="${HOME}/.ssh/lightsail-${KEY_PAIR_NAME}.pem"
    if [[ -z "${APPLY}" ]]; then
      lightsail_print_dry_run_aws create-key-pair --key-pair-name "${KEY_PAIR_NAME}"
      echo "[dry-run] would write private key to ${pem_path} (chmod 600); content never printed"
      return 0
    fi
    mkdir -p "${HOME}/.ssh"
    if [[ -e "${pem_path}" ]]; then
      lightsail_die "refusing to overwrite existing private key file: ${pem_path}"
    fi
    echo "Creating Lightsail key pair '${KEY_PAIR_NAME}' (private key → ${pem_path})…"
    # Query only privateKeyBase64 into the file; do not print AWS JSON that contains secrets.
    umask 077
    lightsail_aws create-key-pair \
      --key-pair-name "${KEY_PAIR_NAME}" \
      --query 'privateKeyBase64' \
      --output text >"${pem_path}"
    chmod 600 "${pem_path}"
    echo "Private key written to ${pem_path} (chmod 600). Add IdentityFile to your SSH Host alias."
    return 0
  fi

  # Key pair name requested but does not exist and no import/create flag.
  if [[ -n "${APPLY}" ]]; then
    lightsail_die "key pair '${KEY_PAIR_NAME}' not found. Pass --import-public-key PATH or --create-key-pair, or --no-key-pair."
  else
    echo "[dry-run] key pair '${KEY_PAIR_NAME}' not found yet; with --apply you need --import-public-key or --create-key-pair"
  fi
}

build_create_args() {
  CREATE_ARGS=(
    create-instances
    --instance-names "${INSTANCE_NAME}"
    --availability-zone "${AZ}"
    --blueprint-id "${BLUEPRINT}"
    --bundle-id "${BUNDLE}"
  )
  if [[ -n "${USE_KEY_PAIR}" ]]; then
    CREATE_ARGS+=(--key-pair-name "${KEY_PAIR_NAME}")
  fi
}

ensure_key_pair
build_create_args

echo
if [[ -z "${APPLY}" ]]; then
  echo "Dry-run only. Re-run with --apply to create."
  lightsail_print_dry_run_aws "${CREATE_ARGS[@]}"
  exit 0
fi

echo "Creating instance '${INSTANCE_NAME}'…"
lightsail_aws "${CREATE_ARGS[@]}" >/dev/null

echo "Waiting up to ${WAIT_SECONDS}s for state=running…"
deadline=$((SECONDS + WAIT_SECONDS))
state=""
public_ip=""
while ((SECONDS < deadline)); do
  # shellcheck disable=SC2016
  read -r state public_ip az_out <<<"$(lightsail_aws get-instance --instance-name "${INSTANCE_NAME}" \
    --query 'instance.[state.name,publicIpAddress,location.availabilityZone]' \
    --output text 2>/dev/null || echo "unknown  ")"
  if [[ "${state}" == "running" ]]; then
    break
  fi
  sleep "${POLL_SECONDS}"
done

if [[ "${state}" != "running" ]]; then
  lightsail_die "timed out waiting for '${INSTANCE_NAME}' to become running (last state=${state:-unknown})"
fi

echo
echo "Instance ready:"
echo "  name=${INSTANCE_NAME}"
echo "  state=${state}"
echo "  publicIp=${public_ip}"
echo "  az=${az_out:-${AZ}}"
echo
echo "Next:"
echo "  1. Add an OpenSSH Host alias (e.g. ${LIGHTSAIL_SSH_HOST}) separate from EXP1 Host ezkey"
echo "  2. ./scripts/lightsail/open-ports.sh --apply --name ${INSTANCE_NAME}"
echo "  3. LIGHTSAIL_SSH_HOST=${LIGHTSAIL_SSH_HOST} ./scripts/lightsail/bootstrap-host.sh"
echo "  See docs/lightsail/community-host.md"
