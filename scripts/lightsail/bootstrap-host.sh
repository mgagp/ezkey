#!/usr/bin/env bash
#
# Bootstrap Docker + Compose v2 on a fresh Amazon Linux 2023 Lightsail host (Phase 0b/0c).
# SSH as ec2-user via a declared target or LIGHTSAIL_SSH_HOST — no silent default (#812).
#
# Also installs /etc/ssh/sshd_config.d/40-pq-kex.conf (#1177): hybrid PQ key exchange on the
# operator SSH channel; authentication still classical. Reloads sshd; never restarts it.
#
# Idempotent where practical. Does NOT run clean-start, push images, or change DNS/certs.
# Optional --print-next-steps only prints handoff commands for the existing export script.
#
# Usage (from repository root, Git Bash):
#   ./scripts/lightsail/bootstrap-host.sh --target community
#   ./scripts/lightsail/bootstrap-host.sh --target community --dry-run
#   LIGHTSAIL_SSH_HOST=ezkey-online ./scripts/lightsail/bootstrap-host.sh --dry-run
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

DRY_RUN=""
PRINT_NEXT=""
DECLARED_TARGET=""
HOST_FLAG=""
SSH_USER="${LIGHTSAIL_SSH_USER}"
SSH_IDENTITY="${LIGHTSAIL_SSH_IDENTITY_FILE:-}"

usage() {
  cat <<EOF
Bootstrap Docker + Compose v2 on a Lightsail AL2023 host (playbook Phase 0b/0c).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/bootstrap-host.sh --target community [options]
  LIGHTSAIL_SSH_HOST=ezkey-online ./scripts/lightsail/bootstrap-host.sh [options]

This script SSHes as ec2-user and:
  - dnf install/update docker; enable --now docker
  - install Compose v2 CLI plugin if \`docker compose version\` is missing
  - usermod -aG docker ec2-user
  - install /etc/ssh/sshd_config.d/40-pq-kex.conf (#1177)

The drop-in is hybrid PQ key exchange on the operator SSH channel; authentication still
classical. It prepends mlkem768x25519-sha256 and sntrup761x25519-sha512@openssh.com to the
current \`sshd -T\` list (explicit list, no ^). Classical names stay as fallback. The file is
named 40- so it is read before 50-redhat.conf (sshd keeps the first KexAlgorithms). After
\`sshd -t\` and an order guard, it runs \`systemctl reload sshd\` and never restarts sshd.

It does NOT:
  - require PQ (classical algorithms remain in the offered list)
  - restart sshd
  - run experimental-hybrid/lightsail/clean-start.sh
  - export/push backend images
  - create Cloudflare DNS or Origin CA material

SSH target (required — no silent default; #812):
  --target community   SSH Host ${LIGHTSAIL_SSH_HOST_COMMUNITY}
  --host ALIAS         Explicit OpenSSH Host / hostname (or set LIGHTSAIL_SSH_HOST)

Options:
  --user USER             SSH user (default: ec2-user)
  --identity-file PATH    Optional IdentityFile for ssh -i (or set LIGHTSAIL_SSH_IDENTITY_FILE)
  --dry-run               Print remote commands only (no ssh)
  --print-next-steps      After success (or with --dry-run), print image-export handoff
  -h, --help              Show this help

SSH config tip (workstation ~/.ssh/config):

  Host ezkey-online
    HostName <static-ip ezkey-online-community>
    User ec2-user
    IdentityFile ~/.ssh/<your-key>
    IdentitiesOnly yes

Then:
  ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --target community --dry-run
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
    --print-next-steps)
      PRINT_NEXT="1"
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

# >>> pq-kex-functions
# Build the explicit KexAlgorithms list for #1177.
# Prepend the two hybrid names only when CUR does not already contain them.
# Classical names stay, in the order sshd -T reported. Empty classical list is an error:
# the channel must not require PQ.
lightsail_pq_kex_desired() {
  local cur="$1"
  local pq1="mlkem768x25519-sha256"
  local pq2="sntrup761x25519-sha512@openssh.com"
  local classic="" alg
  local -a parts=()
  IFS=',' read -r -a parts <<<"${cur}"
  for alg in "${parts[@]}"; do
    alg="${alg#"${alg%%[![:space:]]*}"}"
    alg="${alg%"${alg##*[![:space:]]}"}"
    if [[ -z "${alg}" || "${alg}" == "${pq1}" || "${alg}" == "${pq2}" ]]; then
      continue
    fi
    if [[ -n "${classic}" ]]; then
      classic+=","
    fi
    classic+="${alg}"
  done
  if [[ -z "${classic}" ]]; then
    return 1
  fi
  printf '%s\n' "${pq1},${pq2},${classic}"
}

# Order guard: effective kexalgorithms must start with mlkem, keep sntrup second, and
# still offer curve25519-sha256 (classical fallback; do not require PQ).
lightsail_pq_kex_order_ok() {
  local effective="$1"
  local pq1="mlkem768x25519-sha256"
  local pq2="sntrup761x25519-sha512@openssh.com"
  [[ "${effective}" == "${pq1},${pq2},"* ]] || return 1
  case ",${effective}," in
    *",curve25519-sha256,"*) return 0 ;;
    *) return 1 ;;
  esac
}
# <<< pq-kex-functions

REMOTE_SCRIPT="$(declare -f lightsail_pq_kex_desired)

$(declare -f lightsail_pq_kex_order_ok)

$(cat <<'REMOTE'
set -euo pipefail
echo "==> Phase 0b: Docker daemon"
if ! command -v docker >/dev/null 2>&1; then
  sudo dnf update -y
  sudo dnf install -y docker
else
  echo "docker already installed: $(command -v docker)"
fi
sudo systemctl enable --now docker
sudo docker info >/dev/null && echo "Docker daemon OK"

echo "==> Phase 0b: Compose v2 plugin"
if ! docker compose version >/dev/null 2>&1; then
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  arch="$(uname -m)"
  sudo curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${arch}" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi
docker compose version

echo "==> Phase 0c: docker group for ec2-user"
if id -nG ec2-user | tr ' ' '\n' | grep -qx docker; then
  echo "ec2-user already in docker group"
else
  sudo usermod -aG docker ec2-user
  echo "Added ec2-user to docker group (new SSH session or newgrp docker required)"
fi

echo "==> Operator SSH channel (#1177)"
echo "Hybrid PQ key exchange on the operator SSH channel; authentication still classical."
# 40- is read before 50-redhat.conf (crypto policy). sshd keeps the first KexAlgorithms.
# Explicit list, no ^. Classical names stay. Reload only — never restart sshd.
# Operators leave EZKEY_SSHD_PQ_DROPIN unset. A local test may point it at a temp file.
DROPIN="${EZKEY_SSHD_PQ_DROPIN:-/etc/ssh/sshd_config.d/40-pq-kex.conf}"
dropin_dir="$(dirname "${DROPIN}")"
if ! sudo test -d "${dropin_dir}"; then
  echo "ERROR: ${dropin_dir} is missing; refusing to install ${DROPIN}" >&2
  exit 1
fi
CUR="$(sudo sshd -T | awk 'tolower($1)=="kexalgorithms"{print $2; exit}')"
if [[ -z "${CUR}" ]]; then
  echo "ERROR: sshd -T did not report kexalgorithms" >&2
  exit 1
fi
if ! desired="$(lightsail_pq_kex_desired "${CUR}")"; then
  echo "ERROR: classic KexAlgorithms list is empty; refusing to require PQ" >&2
  exit 1
fi
case "${desired}" in
  *'^'*)
    echo "ERROR: refusing a KexAlgorithms list that contains ^" >&2
    exit 1
    ;;
esac
echo "KexAlgorithms ${desired}"
tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT
cat >"${tmp}" <<EOF
# #1177 — hybrid PQ key exchange on the operator SSH channel.
# Authentication (host and user keys) still classical.
# Explicit list (no ^). Classical fallback kept intact.
KexAlgorithms ${desired}
EOF
sudo install -m 0600 -o root -g root "${tmp}" "${DROPIN}"
rm -f "${tmp}"
# `command` is a shell builtin; `sudo command -v` does not find it. Probe the binary.
if [[ -x /usr/sbin/restorecon || -x /sbin/restorecon ]]; then
  sudo restorecon -F "${DROPIN}" \
    || echo "WARN: restorecon failed for ${DROPIN}; continuing to sshd -t" >&2
fi
mode="$(sudo stat -c '%a %U:%G' "${DROPIN}")"
if [[ "${mode}" != "600 root:root" ]]; then
  echo "ERROR: ${DROPIN} is '${mode}', expected '600 root:root'. Removing drop-in. sshd was not reloaded." >&2
  sudo rm -f "${DROPIN}"
  if ! sudo sshd -t; then
    echo "ERROR: sshd -t failed after removing ${DROPIN}" >&2
  fi
  exit 1
fi
if ! sudo sshd -t; then
  echo "ERROR: sshd -t failed after writing ${DROPIN}. Removing drop-in. sshd was not reloaded." >&2
  sudo rm -f "${DROPIN}"
  if ! sudo sshd -t; then
    echo "ERROR: sshd -t failed after removing ${DROPIN}" >&2
  fi
  exit 1
fi
effective="$(sudo sshd -T | awk 'tolower($1)=="kexalgorithms"{print $2; exit}')"
if ! lightsail_pq_kex_order_ok "${effective}"; then
  echo "ERROR: order guard failed: sshd -T kexalgorithms must start with mlkem (got: ${effective}). Removing ${DROPIN}, re-running sshd -t, and stopping. sshd was not reloaded." >&2
  sudo rm -f "${DROPIN}"
  if ! sudo sshd -t; then
    echo "ERROR: sshd -t failed after removing ${DROPIN}" >&2
  fi
  exit 1
fi
echo "Order guard OK: kexalgorithms starts with mlkem"
if ! sudo systemctl reload sshd; then
  echo "ERROR: systemctl reload sshd failed. Drop-in left in place (sshd -t passed). sshd was not restarted." >&2
  exit 1
fi
echo "sshd reloaded (not restarted). Classic fallback kept."

echo "==> bootstrap complete"
REMOTE
)"

ssh_cmd=(ssh)
if [[ -n "${SSH_IDENTITY}" ]]; then
  [[ -f "${SSH_IDENTITY}" ]] || lightsail_die "identity file not found: ${SSH_IDENTITY}"
  ssh_cmd+=(-i "${SSH_IDENTITY}")
fi
# Prefer Host alias from ~/.ssh/config; if operator passes user@host style, respect it.
ssh_target="${SSH_HOST}"
if [[ "${SSH_HOST}" != *"@"* ]]; then
  ssh_target="${SSH_USER}@${SSH_HOST}"
fi
ssh_cmd+=("${ssh_target}" "bash -s")

echo "Bootstrap target: ${ssh_target} (LIGHTSAIL_TARGET=${LIGHTSAIL_TARGET:-custom})"
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

print_next_steps() {
  local export_target_flag=""
  if [[ -n "${LIGHTSAIL_TARGET:-}" ]]; then
    export_target_flag="--target ${LIGHTSAIL_TARGET}"
  else
    export_target_flag="LIGHTSAIL_SSH_HOST=${SSH_HOST}"
  fi
  cat <<EOF

Next steps (manual / existing scripts — not run by bootstrap-host.sh):
  1. New SSH session so docker group applies:  ssh ${SSH_HOST}
  2. Copy lightsail layout / images (declared target — no silent SSH default; #812):
       ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh ${export_target_flag} --dry-run
       # then without --dry-run, optionally --sync-operator-files / --clean-start
       # community --sync-operator-files installs Caddyfile.ezkey-online (compose bind basename)
  3. Origin CA + Cloudflare DNS for ezkey.online: human OK only (not automated here).
  See docs/lightsail/community-host.md
EOF
}

if [[ -n "${PRINT_NEXT}" || -z "${DRY_RUN}" ]]; then
  print_next_steps
fi
