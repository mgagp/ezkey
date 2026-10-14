#!/usr/bin/env bash
# Shared helpers for scripts/lightsail/*.sh and experimental-hybrid Lightsail deploy scripts.
# Sourced by sibling scripts; not meant to be run directly.
#
# Scope: VM lifecycle (create / ports / bootstrap / status / delete) plus shared SSH-target
# resolution for image export / rolling upgrade. App layer stays in
# experimental-hybrid/lightsail/ + export-backend-images-to-lightsail.sh.
# This tree is monorepo-pragmatic and may later extract to a private ops repo after e2e
# validation (see docs/lightsail/community-host.md § Ownership boundary).
#
# Defaults match the live EXP1 experimental-hybrid instance shape (AL2023, medium_3_0,
# ca-central-1) but target a NEW parallel instance name. They never touch exp1-ezkey.
#
# SSH host: NEVER silent-default to ezkey (dead EXP1 trap) or ezkey-online. Require
# --target community|exp1 or an explicit LIGHTSAIL_SSH_HOST (#812).
#
# shellcheck shell=bash

# Prevent double-source
if [[ -n "${_EZKEY_LIGHTSAIL_COMMON_SOURCED:-}" ]]; then
  return 0 2>/dev/null || true
fi
_EZKEY_LIGHTSAIL_COMMON_SOURCED=1

# --- Defaults (overridable via env or flags in callers) ---
: "${AWS_PROFILE:=ezkey-lightsail}"
: "${AWS_REGION:=ca-central-1}"
: "${LIGHTSAIL_REGION:=${AWS_REGION}}"
: "${LIGHTSAIL_INSTANCE_NAME:=ezkey-online}"
: "${LIGHTSAIL_AZ:=ca-central-1a}"
: "${LIGHTSAIL_BLUEPRINT:=amazon_linux_2023}"
: "${LIGHTSAIL_BUNDLE:=medium_3_0}"
# Lightsail resource names are unique across types — key pair must not equal instance name.
: "${LIGHTSAIL_KEY_PAIR_NAME:=ezkey-online-kp}"
: "${LIGHTSAIL_SSH_USER:=ec2-user}"

# Canonical OpenSSH Host aliases — map from --target; do not use as silent defaults.
readonly LIGHTSAIL_SSH_HOST_COMMUNITY="ezkey-online"
readonly LIGHTSAIL_SSH_HOST_EXP1="ezkey"

# Live EXP1 instance name — documented for operators; scripts must never default to this name.
readonly LIGHTSAIL_EXP1_INSTANCE_NAME="exp1-ezkey"

# Optional: set by lightsail_resolve_ssh_target (community|exp1). Empty until resolved.
LIGHTSAIL_TARGET="${LIGHTSAIL_TARGET:-}"

lightsail_die() {
  echo "error: $*" >&2
  exit 1
}

lightsail_require_aws_cli() {
  if ! command -v aws >/dev/null 2>&1; then
    lightsail_die "aws CLI not found on PATH. Install AWS CLI v2 and configure profile '${AWS_PROFILE}'."
  fi
}

# Run aws lightsail with profile + region. Extra args are appended.
lightsail_aws() {
  AWS_PROFILE="${AWS_PROFILE}" aws --profile "${AWS_PROFILE}" --region "${LIGHTSAIL_REGION}" lightsail "$@"
}

# True (exit 0) if get-instance succeeds for the name.
lightsail_instance_exists() {
  local name="$1"
  lightsail_aws get-instance --instance-name "${name}" >/dev/null 2>&1
}

# Print a dry-run line for a planned command (no secrets).
lightsail_print_dry_run() {
  printf '[dry-run]'
  printf ' %q' "$@"
  echo
}

# Print dry-run for the equivalent aws lightsail CLI (expanded profile/region).
lightsail_print_dry_run_aws() {
  lightsail_print_dry_run aws --profile "${AWS_PROFILE}" --region "${LIGHTSAIL_REGION}" lightsail "$@"
}

# If APPLY is set (non-empty), run aws lightsail; else print expanded dry-run.
lightsail_run_or_dry_aws() {
  local apply_flag="$1"
  shift
  if [[ -n "${apply_flag}" ]]; then
    lightsail_aws "$@"
  else
    lightsail_print_dry_run_aws "$@"
  fi
}

# Map logical target → OpenSSH Host alias. Prints host; exits 1 on unknown target.
lightsail_ssh_host_for_target() {
  case "$1" in
    community) echo "${LIGHTSAIL_SSH_HOST_COMMUNITY}" ;;
    exp1) echo "${LIGHTSAIL_SSH_HOST_EXP1}" ;;
    *)
      lightsail_die "unknown Lightsail target '$1' (expected community|exp1)"
      ;;
  esac
}

# Infer logical target from a known SSH Host alias (empty if custom/unknown).
lightsail_infer_target_from_host() {
  case "$1" in
    "${LIGHTSAIL_SSH_HOST_COMMUNITY}"|*"@${LIGHTSAIL_SSH_HOST_COMMUNITY}")
      echo "community"
      ;;
    "${LIGHTSAIL_SSH_HOST_EXP1}"|*"@${LIGHTSAIL_SSH_HOST_EXP1}")
      echo "exp1"
      ;;
    *)
      echo ""
      ;;
  esac
}

# Resolve LIGHTSAIL_SSH_HOST and LIGHTSAIL_TARGET.
# Call after parsing CLI flags. Pass optional --target value as $1 (may be empty).
# Requires either a declared target (community|exp1) or an explicit LIGHTSAIL_SSH_HOST
# (env or --host). Fails loudly otherwise — no silent default (#812).
#
# Sets: LIGHTSAIL_SSH_HOST, LIGHTSAIL_TARGET (community|exp1|empty for custom host).
lightsail_resolve_ssh_target() {
  local declared_target="${1:-}"
  local host_from_env="${LIGHTSAIL_SSH_HOST:-}"

  if [[ -n "${declared_target}" ]]; then
    case "${declared_target}" in
      community|exp1) ;;
      *)
        lightsail_die "unknown --target '${declared_target}' (expected community|exp1)"
        ;;
    esac
    LIGHTSAIL_TARGET="${declared_target}"
    local mapped
    mapped="$(lightsail_ssh_host_for_target "${declared_target}")"
    if [[ -n "${host_from_env}" && "${host_from_env}" != "${mapped}" ]]; then
      # Explicit host wins when both are set, but target stays for Caddyfile selection.
      LIGHTSAIL_SSH_HOST="${host_from_env}"
      echo "note: LIGHTSAIL_SSH_HOST=${host_from_env} overrides mapped host ${mapped} for --target ${declared_target}" >&2
    else
      LIGHTSAIL_SSH_HOST="${mapped}"
    fi
    return 0
  fi

  if [[ -n "${host_from_env}" ]]; then
    LIGHTSAIL_SSH_HOST="${host_from_env}"
    if [[ -z "${LIGHTSAIL_TARGET}" ]]; then
      LIGHTSAIL_TARGET="$(lightsail_infer_target_from_host "${host_from_env}")"
    fi
    return 0
  fi

  lightsail_die "no Lightsail SSH target declared. Pass --target community|exp1 or set LIGHTSAIL_SSH_HOST (see docs/lightsail/community-host.md; #812)."
}

# Local Caddyfile path to scp as remote Caddyfile for the resolved target.
# community → Caddyfile.ezkey-online (never the EXP1 Caddyfile — #812).
# exp1 → Caddyfile.
# Custom host without LIGHTSAIL_TARGET → die (must declare --target for sync).
lightsail_caddyfile_source_for_sync() {
  local lightsail_local_dir="$1"
  case "${LIGHTSAIL_TARGET:-}" in
    community)
      echo "${lightsail_local_dir}/Caddyfile.ezkey-online"
      ;;
    exp1)
      echo "${lightsail_local_dir}/Caddyfile"
      ;;
    "")
      lightsail_die "cannot sync Caddyfile: SSH host '${LIGHTSAIL_SSH_HOST:-}' is not a known alias; pass --target community|exp1 so the correct Caddyfile is chosen (#812)."
      ;;
    *)
      lightsail_die "cannot sync Caddyfile for unknown LIGHTSAIL_TARGET='${LIGHTSAIL_TARGET}'"
      ;;
  esac
}

lightsail_print_defaults() {
  cat <<EOF
Defaults (env overrides):
  AWS_PROFILE=${AWS_PROFILE}
  LIGHTSAIL_REGION=${LIGHTSAIL_REGION}
  LIGHTSAIL_INSTANCE_NAME=${LIGHTSAIL_INSTANCE_NAME}
  LIGHTSAIL_AZ=${LIGHTSAIL_AZ}
  LIGHTSAIL_BLUEPRINT=${LIGHTSAIL_BLUEPRINT}
  LIGHTSAIL_BUNDLE=${LIGHTSAIL_BUNDLE}
  LIGHTSAIL_KEY_PAIR_NAME=${LIGHTSAIL_KEY_PAIR_NAME}
  LIGHTSAIL_SSH_USER=${LIGHTSAIL_SSH_USER}

SSH host (no silent default — #812):
  --target community  →  ${LIGHTSAIL_SSH_HOST_COMMUNITY}
  --target exp1       →  ${LIGHTSAIL_SSH_HOST_EXP1}
  or set LIGHTSAIL_SSH_HOST explicitly
  current LIGHTSAIL_SSH_HOST=${LIGHTSAIL_SSH_HOST:-<unset>}
  current LIGHTSAIL_TARGET=${LIGHTSAIL_TARGET:-<unset>}

Live EXP1 (do not modify with community VM scripts): ${LIGHTSAIL_EXP1_INSTANCE_NAME}
Preferred new name: ezkey-online (override with --name / LIGHTSAIL_INSTANCE_NAME;
  use community-ezkey if the preferred name is taken).
Key pair default: ezkey-online-kp — must differ from the instance name (Lightsail
  names are unique across resource types).
EOF
}
