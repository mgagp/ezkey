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
# Defaults match the community experimental-hybrid instance shape (AL2023, medium_3_0,
# ca-central-1) for the ezkey.online community host.
#
# SSH host: NEVER silent-default to a Host alias. Require --target community or an
# explicit LIGHTSAIL_SSH_HOST (#812).
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

# Canonical OpenSSH Host alias — map from --target; do not use as a silent default.
readonly LIGHTSAIL_SSH_HOST_COMMUNITY="ezkey-online"
# Canonical Lightsail instance name for the community host (delete confirm compares to this).
readonly LIGHTSAIL_COMMUNITY_INSTANCE_NAME="ezkey-online"
# Operator-managed static IP name attached to the community instance (never released by scripts).
readonly LIGHTSAIL_COMMUNITY_STATIC_IP_NAME="ezkey-online-community"

# Optional: set by lightsail_resolve_ssh_target (community). Empty until resolved.
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
    *)
      lightsail_die "unknown Lightsail target '$1' (expected community)"
      ;;
  esac
}

# Infer logical target from a known SSH Host alias (empty if custom/unknown).
lightsail_infer_target_from_host() {
  case "$1" in
    "${LIGHTSAIL_SSH_HOST_COMMUNITY}"|*"@${LIGHTSAIL_SSH_HOST_COMMUNITY}")
      echo "community"
      ;;
    *)
      echo ""
      ;;
  esac
}

# Resolve LIGHTSAIL_SSH_HOST and LIGHTSAIL_TARGET.
# Call after parsing CLI flags. Pass optional --target value as $1 (may be empty).
# Requires either a declared target (community) or an explicit LIGHTSAIL_SSH_HOST
# (env or --host). Fails loudly otherwise — no silent default (#812).
#
# Sets: LIGHTSAIL_SSH_HOST, LIGHTSAIL_TARGET (community|empty for custom host).
lightsail_resolve_ssh_target() {
  local declared_target="${1:-}"
  local host_from_env="${LIGHTSAIL_SSH_HOST:-}"

  if [[ -n "${declared_target}" ]]; then
    case "${declared_target}" in
      community) ;;
      *)
        lightsail_die "unknown --target '${declared_target}' (expected community)"
        ;;
    esac
    LIGHTSAIL_TARGET="${declared_target}"
    local mapped
    mapped="$(lightsail_ssh_host_for_target "${declared_target}")"
    if [[ -n "${host_from_env}" && "${host_from_env}" != "${mapped}" ]]; then
      # Custom host may override the mapped alias; target still selects the Caddyfile source.
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

  lightsail_die "no Lightsail SSH target declared. Pass --target community or set LIGHTSAIL_SSH_HOST (see docs/lightsail/community-host.md; #812)."
}

# Refuse community Postgres wipe unless the operator explicitly confirms.
# Required value: EZKEY_CONFIRM_WIPE=ezkey-online (the community SSH Host alias).
lightsail_require_community_wipe_confirm() {
  if [[ "${LIGHTSAIL_TARGET:-}" != "community" ]]; then
    return 0
  fi
  if [[ "${EZKEY_CONFIRM_WIPE:-}" != "${LIGHTSAIL_SSH_HOST_COMMUNITY}" ]]; then
    lightsail_die "refusing community wipe (--clean-start / full). Set EZKEY_CONFIRM_WIPE=${LIGHTSAIL_SSH_HOST_COMMUNITY} for first commissioning only (wipes Postgres). Prefer --target community rolling for upgrades (#812)."
  fi
}

# Trim leading/trailing whitespace and lowercase an instance name for comparisons.
lightsail_normalize_instance_name() {
  local n="$1"
  n="${n#"${n%%[![:space:]]*}"}"
  n="${n%"${n##*[![:space:]]}"}"
  n="${n,,}"
  printf '%s' "${n}"
}

# True if normalized name equals the community instance name.
lightsail_is_community_instance_name() {
  local normalized
  normalized="$(lightsail_normalize_instance_name "$1")"
  [[ "${normalized}" == "${LIGHTSAIL_COMMUNITY_INSTANCE_NAME}" ]]
}

# True (exit 0) if community static IP is attached to the given instance name.
# Uses get-static-ip (read-only). Returns 1 when not attached or the IP is missing.
lightsail_community_static_ip_attached_to() {
  local instance_name="$1"
  local normalized json attached_to
  normalized="$(lightsail_normalize_instance_name "${instance_name}")"
  [[ -n "${normalized}" ]] || return 1
  if ! json="$(lightsail_aws get-static-ip --static-ip-name "${LIGHTSAIL_COMMUNITY_STATIC_IP_NAME}" --output json 2>/dev/null)"; then
    return 1
  fi
  attached_to="$(
    printf '%s' "${json}" | python3 -c '
import json, sys
data = json.load(sys.stdin)
static_ip = data.get("staticIp") or {}
if not static_ip.get("isAttached"):
    print("")
    raise SystemExit(0)
print(static_ip.get("attachedTo") or "")
'
  )"
  [[ -n "${attached_to}" ]] || return 1
  [[ "$(lightsail_normalize_instance_name "${attached_to}")" == "${normalized}" ]]
}

# Refuse delete of the community instance unless the operator explicitly confirms.
# Required when normalized name is ezkey-online, or when static IP
# ezkey-online-community is attached to the target instance (AWS read).
# Confirm value: EZKEY_CONFIRM_DELETE=ezkey-online.
# Scripts never release that static IP.
lightsail_require_community_delete_confirm() {
  local instance_name="$1"
  local need_confirm=0
  if lightsail_is_community_instance_name "${instance_name}"; then
    need_confirm=1
  elif lightsail_community_static_ip_attached_to "${instance_name}"; then
    need_confirm=1
  fi
  if [[ "${need_confirm}" -eq 0 ]]; then
    return 0
  fi
  if [[ "${EZKEY_CONFIRM_DELETE:-}" != "${LIGHTSAIL_COMMUNITY_INSTANCE_NAME}" ]]; then
    lightsail_die "refusing to delete instance '${instance_name}' (community name or static IP ${LIGHTSAIL_COMMUNITY_STATIC_IP_NAME} attached). Set EZKEY_CONFIRM_DELETE=${LIGHTSAIL_COMMUNITY_INSTANCE_NAME}. Scripts never release that static IP — re-attach it after recreate."
  fi
}

# Host-side basename of the Caddyfile bind mount in the Lightsail compose file.
# Single source of truth for the remote sync name (#863): Compose mounts
# ./<name>:/etc/caddy/Caddyfile — sync must scp to that same <name> on the VM.
# Prints e.g. Caddyfile.ezkey-online. Dies if the bind is missing or ambiguous.
lightsail_caddyfile_remote_basename_from_compose() {
  local compose_file="$1"
  local basename_out
  if [[ ! -f "${compose_file}" ]]; then
    lightsail_die "compose file not found: ${compose_file}"
  fi
  basename_out="$(
    python3 - "${compose_file}" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
# Short or long-ish forms: ./Name:/etc/caddy/Caddyfile or ./Name:/etc/caddy/Caddyfile:ro
pat = re.compile(
    r"^\s*-\s*(?:\./)?(?P<name>[^\s:]+):/etc/caddy/Caddyfile(?::\w+)?\s*$",
    re.MULTILINE,
)
names = [m.group("name") for m in pat.finditer(text)]
# Ignore named-volume style false positives (no path separators / dots only if Caddyfile*)
names = [n for n in names if "Caddyfile" in n or n.endswith("Caddyfile")]
if not names:
    # Broader: any host path mapped to /etc/caddy/Caddyfile
    pat2 = re.compile(
        r"^\s*-\s*(?P<source>\./[^\s:]+):/etc/caddy/Caddyfile(?::\w+)?\s*$",
        re.MULTILINE,
    )
    names = [m.group("source").lstrip("./") for m in pat2.finditer(text)]
if len(names) != 1:
    sys.stderr.write(
        f"error: expected exactly one ./…:/etc/caddy/Caddyfile bind in {path}, found {len(names)}: {names}\n"
    )
    raise SystemExit(1)
print(names[0])
PY
  )" || lightsail_die "failed to parse Caddyfile bind mount from ${compose_file}"
  printf '%s\n' "${basename_out}"
}

# Local Caddyfile path to scp for the resolved target.
# Canonical source basename is derived from the Lightsail compose bind mount (#863).
# Sync installs it under that same remote basename (not a renamed remote "Caddyfile").
# Custom host without LIGHTSAIL_TARGET → die (must declare --target for sync).
lightsail_caddyfile_source_for_sync() {
  local lightsail_local_dir="$1"
  local compose_file="${2:-${lightsail_local_dir}/docker-compose.yml}"
  local remote_basename
  case "${LIGHTSAIL_TARGET:-}" in
    community)
      remote_basename="$(lightsail_caddyfile_remote_basename_from_compose "${compose_file}")"
      echo "${lightsail_local_dir}/${remote_basename}"
      ;;
    "")
      lightsail_die "cannot sync Caddyfile: SSH host '${LIGHTSAIL_SSH_HOST:-}' is not a known alias; pass --target community so the correct Caddyfile is chosen (#812)."
      ;;
    *)
      lightsail_die "cannot sync Caddyfile for unknown LIGHTSAIL_TARGET='${LIGHTSAIL_TARGET}'"
      ;;
  esac
}

# Assert the remote sync basename matches the compose Caddyfile bind mount (#863).
# Pass the basename that will be used as the scp destination (and sha256 target).
# Prints a dry-run-friendly note on success; dies on mismatch (the #859/#863 failure mode).
lightsail_assert_caddyfile_sync_matches_compose() {
  local compose_file="$1"
  local sync_remote_basename="$2"
  local compose_basename
  compose_basename="$(lightsail_caddyfile_remote_basename_from_compose "${compose_file}")"
  if [[ "${sync_remote_basename}" != "${compose_basename}" ]]; then
    lightsail_die "Caddyfile sync/compose mismatch (#863): sync remote name='${sync_remote_basename}' but compose mounts './${compose_basename}'. Align sync remote name with the compose bind mount (do not rename to a different file)."
  fi
  echo "Caddyfile sync aligned with compose bind: remote name='${compose_basename}' (sha verified on that file)"
}

# List short-form bind-mount host paths from a compose file (relative or absolute).
# Each line: TYPE<TAB>REL_OR_ABS_PATH  where TYPE is "file" or "dir".
# Heuristic: paths whose basename contains "Caddyfile" or ends with common file suffixes → file;
# otherwise → dir. Used for dry-run and remote preflight without requiring docker (#863).
lightsail_list_compose_bind_mounts() {
  local compose_file="$1"
  if [[ ! -f "${compose_file}" ]]; then
    lightsail_die "compose file not found: ${compose_file}"
  fi
  python3 - "${compose_file}" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
# Match host:container[:mode] where host looks like a path (./ ../ or absolute)
pat = re.compile(
    r"^\s*-\s*(?P<source>(?:\.\.?/|/)[^\s:]+):(?P<target>[^\s:]+)(?::(?P<mode>\w+))?\s*$",
    re.MULTILINE,
)
file_basenames = ("Caddyfile", ".yml", ".yaml", ".sh", ".sql", ".properties", ".json", ".pem", ".key")
seen = set()
for m in pat.finditer(text):
    src = m.group("source")
    if src in seen:
        continue
    seen.add(src)
    base = src.rsplit("/", 1)[-1]
    kind = "dir"
    if "Caddyfile" in base:
        kind = "file"
    else:
        for suf in file_basenames:
            if base == suf or base.endswith(suf):
                kind = "file"
                break
    print(f"{kind}\t{src}")
PY
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
  or set LIGHTSAIL_SSH_HOST explicitly
  current LIGHTSAIL_SSH_HOST=${LIGHTSAIL_SSH_HOST:-<unset>}
  current LIGHTSAIL_TARGET=${LIGHTSAIL_TARGET:-<unset>}

Preferred instance name: ezkey-online (override with --name / LIGHTSAIL_INSTANCE_NAME;
  use community-ezkey if the preferred name is taken).
Key pair default: ezkey-online-kp — must differ from the instance name (Lightsail
  names are unique across resource types).
EOF
}
