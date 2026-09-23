#!/usr/bin/env bash
# Shared helpers for scripts/lightsail/*.sh (community / ezkey.online host automation).
# Sourced by sibling scripts; not meant to be run directly.
#
# Scope: VM lifecycle only (create / ports / bootstrap / status / delete). App layer stays in
# experimental-hybrid/lightsail/ + export-backend-images-to-lightsail.sh.
# This tree is monorepo-pragmatic and may later extract to a private ops repo after e2e
# validation (see docs/lightsail/community-host.md § Ownership boundary).
#
# Defaults match the live EXP1 experimental-hybrid instance shape (AL2023, medium_3_0,
# ca-central-1) but target a NEW parallel instance name. They never touch exp1-ezkey.
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
: "${LIGHTSAIL_SSH_HOST:=ezkey-online}"
: "${LIGHTSAIL_SSH_USER:=ec2-user}"

# Live EXP1 — documented for operators; scripts must never default to this name.
readonly LIGHTSAIL_EXP1_INSTANCE_NAME="exp1-ezkey"

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
  LIGHTSAIL_SSH_HOST=${LIGHTSAIL_SSH_HOST}
  LIGHTSAIL_SSH_USER=${LIGHTSAIL_SSH_USER}

Live EXP1 (do not modify with these scripts): ${LIGHTSAIL_EXP1_INSTANCE_NAME}
Preferred new name: ezkey-online (override with --name / LIGHTSAIL_INSTANCE_NAME;
  use community-ezkey if the preferred name is taken).
Key pair default: ezkey-online-kp — must differ from the instance name (Lightsail
  names are unique across resource types).
EOF
}
