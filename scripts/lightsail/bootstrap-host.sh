#!/usr/bin/env bash
#
# Bootstrap Docker + Compose v2 on a fresh Amazon Linux 2023 Lightsail host (Phase 0b/0c).
# SSH as ec2-user via a declared target or LIGHTSAIL_SSH_HOST — no silent default (#812).
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

It does NOT:
  - run experimental-hybrid/lightsail/clean-start.sh
  - export/push backend images
  - create Cloudflare DNS or Origin CA material

SSH target (required — no silent default; #812):
  --target community   SSH Host ${LIGHTSAIL_SSH_HOST_COMMUNITY}
  --target exp1        SSH Host ${LIGHTSAIL_SSH_HOST_EXP1} (discouraged for this community bootstrap path)
  --host ALIAS         Explicit OpenSSH Host / hostname (or set LIGHTSAIL_SSH_HOST)

Options:
  --user USER             SSH user (default: ec2-user)
  --identity-file PATH    Optional IdentityFile for ssh -i (or set LIGHTSAIL_SSH_IDENTITY_FILE)
  --dry-run               Print remote commands only (no ssh)
  --print-next-steps      After success (or with --dry-run), print image-export handoff
  -h, --help              Show this help

SSH config tip (workstation ~/.ssh/config) — keep EXP1 on Host ezkey untouched:

  Host ezkey-online
    HostName <public-ip-from-create-instance>
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
      [[ $# -ge 2 ]] || lightsail_die "--target requires community|exp1"
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

if [[ "${LIGHTSAIL_TARGET:-}" == "exp1" ]]; then
  echo "warning: bootstrapping EXP1 alias (${LIGHTSAIL_SSH_HOST_EXP1}). Prefer --target community for ezkey.online." >&2
fi

REMOTE_SCRIPT=$(
  cat <<'REMOTE'
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

echo "==> bootstrap complete"
REMOTE
)

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
       # community --sync-operator-files installs Caddyfile.ezkey-online as remote Caddyfile
  3. Origin CA + Cloudflare DNS for ezkey.online: human OK only (not automated here).
  See docs/lightsail/community-host.md
EOF
}

if [[ -n "${PRINT_NEXT}" || -z "${DRY_RUN}" ]]; then
  print_next_steps
fi
