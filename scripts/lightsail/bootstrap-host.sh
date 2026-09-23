#!/usr/bin/env bash
#
# Bootstrap Docker + Compose v2 on a fresh Amazon Linux 2023 Lightsail host (Phase 0b/0c).
# SSH as ec2-user via LIGHTSAIL_SSH_HOST (default: ezkey-online — NOT the EXP1 alias ezkey).
#
# Idempotent where practical. Does NOT run clean-start, push images, or change DNS/certs.
# Optional --print-next-steps only prints handoff commands for the existing export script.
#
# Usage (from repository root, Git Bash):
#   export LIGHTSAIL_SSH_HOST=ezkey-online
#   ./scripts/lightsail/bootstrap-host.sh
#   ./scripts/lightsail/bootstrap-host.sh --dry-run
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

DRY_RUN=""
PRINT_NEXT=""
SSH_HOST="${LIGHTSAIL_SSH_HOST}"
SSH_USER="${LIGHTSAIL_SSH_USER}"
SSH_IDENTITY="${LIGHTSAIL_SSH_IDENTITY_FILE:-}"

usage() {
  cat <<'EOF'
Bootstrap Docker + Compose v2 on a Lightsail AL2023 host (playbook Phase 0b/0c).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/bootstrap-host.sh [options]

This script SSHes to LIGHTSAIL_SSH_HOST (default ezkey-online) as ec2-user and:
  - dnf install/update docker; enable --now docker
  - install Compose v2 CLI plugin if `docker compose version` is missing
  - usermod -aG docker ec2-user

It does NOT:
  - run experimental-hybrid/lightsail/clean-start.sh
  - export/push backend images
  - create Cloudflare DNS or Origin CA material

Options:
  --host ALIAS            SSH Host alias or hostname (default: LIGHTSAIL_SSH_HOST / ezkey-online)
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
  LIGHTSAIL_SSH_HOST=ezkey-online ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --dry-run
EOF
  echo
  lightsail_print_defaults
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      [[ $# -ge 2 ]] || lightsail_die "--host requires a value"
      SSH_HOST="$2"
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

if [[ "${SSH_HOST}" == "ezkey" ]]; then
  echo "warning: LIGHTSAIL_SSH_HOST=ezkey is the EXP1 alias. Prefer a dedicated Host (e.g. ezkey-online)." >&2
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

echo "Bootstrap target: ${ssh_target}"
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
  cat <<EOF

Next steps (manual / existing scripts — not run by bootstrap-host.sh):
  1. New SSH session so docker group applies:  ssh ${SSH_HOST}
  2. Copy lightsail layout / images (EXP1 export path, new host alias):
       export LIGHTSAIL_SSH_HOST=${SSH_HOST}
       ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --dry-run
       # then without --dry-run, optionally --sync-operator-files / --clean-start
  3. Origin CA + Cloudflare DNS for ezkey.online: human OK only (not automated here).
  See docs/lightsail/community-host.md
EOF
}

if [[ -n "${PRINT_NEXT}" || -z "${DRY_RUN}" ]]; then
  print_next_steps
fi
