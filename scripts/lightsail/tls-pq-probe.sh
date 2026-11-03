#!/usr/bin/env bash
#
# Read-only hybrid post-quantum TLS key-exchange probe for the community host (#1174).
#
# Edge (default): from this workstation, offer ONLY X25519MLKEM768 to each public hostname,
# then a negative control offering ONLY X25519. Hybrid is proven when the first handshake
# succeeds with "Negotiated TLS1.3 group: X25519MLKEM768" and the control reports X25519.
#
# Origin (--origin): same probe run ON the host against 127.0.0.1:443 with SNI (the origin
# firewall only admits Cloudflare ranges), plus the running Caddy version and image digest.
# SSH needs a declared target or LIGHTSAIL_SSH_HOST — no silent default (#812).
#
# Never changes TLS, Caddy, Cloudflare, or host configuration.
#
# Usage (from repository root, Git Bash):
#   ./scripts/lightsail/tls-pq-probe.sh
#   ./scripts/lightsail/tls-pq-probe.sh --origin --target community
#   ./scripts/lightsail/tls-pq-probe.sh --origin --target community --dry-run
#
# Canon: product-docs/global/vision/V-2026-09-15-post-quantum-crypto-posture.md § TLS layer
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

readonly PQ_GROUP="X25519MLKEM768"
readonly CLASSICAL_GROUP="X25519"
readonly DEFAULT_HOSTS="admin-api.ezkey.online,auth-api.ezkey.online,integration-api.ezkey.online,demo-acme.ezkey.online"

HOSTS_CSV="${DEFAULT_HOSTS}"
ORIGIN=""
DRY_RUN=""
DECLARED_TARGET=""
HOST_FLAG=""
SSH_USER="${LIGHTSAIL_SSH_USER}"
SSH_IDENTITY="${LIGHTSAIL_SSH_IDENTITY_FILE:-}"

usage() {
  cat <<EOF
Read-only hybrid post-quantum TLS key-exchange probe (#1174).

Usage (from repository root, Git Bash):
  ./scripts/lightsail/tls-pq-probe.sh [options]
  ./scripts/lightsail/tls-pq-probe.sh --origin --target community [options]

For each hostname, offers only ${PQ_GROUP}, then only ${CLASSICAL_GROUP} (negative control),
and prints the negotiated group. Requires OpenSSL >= 3.5 where the probe runs.

Modes:
  (default)            Edge: probe public hostnames from this workstation (Cloudflare hop)
  --origin             Origin: SSH to the host, probe 127.0.0.1:443 with SNI, and print
                       the Caddy version + image digest (Cloudflare -> origin hop)

SSH target for --origin (required — no silent default; #812):
  --target community   SSH Host ${LIGHTSAIL_SSH_HOST_COMMUNITY}
  --host ALIAS         Explicit OpenSSH Host / hostname (or set LIGHTSAIL_SSH_HOST)

Options:
  --hosts CSV             Hostnames to probe (default: ${DEFAULT_HOSTS})
  --user USER             SSH user (default: ec2-user)
  --identity-file PATH    Optional IdentityFile for ssh -i
  --dry-run               Print what would run (no network)
  -h, --help              Show this help

Exit: 0 = every host negotiated ${PQ_GROUP} and the control fell back to ${CLASSICAL_GROUP};
      1 = at least one host failed; 2 = bad arguments or OpenSSL too old.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --origin)
      ORIGIN="1"
      shift
      ;;
    --hosts)
      [[ $# -ge 2 ]] || { echo "--hosts requires a value" >&2; exit 2; }
      HOSTS_CSV="$2"
      shift 2
      ;;
    --target)
      [[ $# -ge 2 ]] || { echo "--target requires community" >&2; exit 2; }
      DECLARED_TARGET="$2"
      shift 2
      ;;
    --host)
      [[ $# -ge 2 ]] || { echo "--host requires a value" >&2; exit 2; }
      HOST_FLAG="$2"
      shift 2
      ;;
    --user)
      [[ $# -ge 2 ]] || { echo "--user requires a value" >&2; exit 2; }
      SSH_USER="$2"
      shift 2
      ;;
    --identity-file)
      [[ $# -ge 2 ]] || { echo "--identity-file requires a path" >&2; exit 2; }
      SSH_IDENTITY="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

# Probe body shared by edge (local) and origin (remote). Args: CONNECT_MODE HOSTS_CSV.
# CONNECT_MODE "edge" connects to <host>:443; "origin" connects to 127.0.0.1:443 with SNI.
PROBE_SCRIPT=$(
  cat <<'PROBE'
set -uo pipefail
mode="$1"
hosts_csv="$2"
pq="X25519MLKEM768"
classical="X25519"

ver="$(openssl version 2>/dev/null || true)"
major_minor="$(echo "${ver}" | sed -nE 's/^OpenSSL ([0-9]+)\.([0-9]+).*/\1 \2/p')"
read -r maj min <<<"${major_minor:-0 0}"
if (( maj < 3 || (maj == 3 && min < 5) )); then
  echo "OpenSSL >= 3.5 required for ${pq} (found: ${ver:-none})" >&2
  exit 2
fi
echo "openssl: ${ver}"
echo "probed_at_utc: $(date -u +%Y-%m-%dT%H:%MZ)"

negotiated() {
  # Prints the negotiated key-exchange group, or "FAILED".
  local host="$1" group="$2" connect out line
  if [[ "${mode}" == "origin" ]]; then connect="127.0.0.1:443"; else connect="${host}:443"; fi
  out="$(timeout 15 openssl s_client -connect "${connect}" -servername "${host}" \
    -tls1_3 -groups "${group}" </dev/null 2>&1)"
  line="$(echo "${out}" | grep -E "Negotiated TLS1.3 group:|Peer Temp Key:" | head -1)"
  if [[ -z "${line}" ]]; then echo "FAILED"; return; fi
  echo "${line}" | sed -E 's/.*(Negotiated TLS1.3 group|Peer Temp Key): *//; s/,.*//'
}

failures=0
IFS=',' read -r -a hosts <<<"${hosts_csv}"
for host in "${hosts[@]}"; do
  got_pq="$(negotiated "${host}" "${pq}")"
  got_classical="$(negotiated "${host}" "${classical}")"
  verdict="OK"
  if [[ "${got_pq}" != "${pq}" || "${got_classical}" != "${classical}" ]]; then
    verdict="FAIL"
    failures=$((failures + 1))
  fi
  printf '%s | %s | offer=%s -> %s | control offer=%s -> %s | %s\n' \
    "${mode}" "${host}" "${pq}" "${got_pq}" "${classical}" "${got_classical}" "${verdict}"
done

if [[ "${mode}" == "origin" ]] && command -v docker >/dev/null 2>&1; then
  c="$(docker ps --format '{{.Names}}' | grep -i caddy | head -1 || true)"
  if [[ -n "${c}" ]]; then
    echo "caddy_container: ${c}"
    echo "caddy_version: $(docker exec "${c}" caddy version 2>/dev/null | awk '{print $1}')"
    image_id="$(docker inspect --format '{{.Image}}' "${c}")"
    echo "caddy_image: $(docker inspect --format '{{.Config.Image}}' "${c}")"
    echo "caddy_digest: $(docker inspect --format '{{index .RepoDigests 0}}' "${image_id}" 2>/dev/null || echo unknown)"
  else
    echo "caddy_container: (none running)"
  fi
fi

if (( failures > 0 )); then
  echo "result: FAIL (${failures} host(s))"
  exit 1
fi
echo "result: OK (hybrid ${pq} negotiated on every host; control fell back to ${classical})"
PROBE
)

if [[ -z "${ORIGIN}" ]]; then
  echo "TLS PQ probe: edge (from this workstation) [read-only]"
  if [[ -n "${DRY_RUN}" ]]; then
    echo "[dry-run] would probe: ${HOSTS_CSV}"
    exit 0
  fi
  bash -c "${PROBE_SCRIPT}" probe edge "${HOSTS_CSV}"
  exit $?
fi

if [[ -n "${HOST_FLAG}" ]]; then
  LIGHTSAIL_SSH_HOST="${HOST_FLAG}"
fi
lightsail_resolve_ssh_target "${DECLARED_TARGET}"
SSH_HOST="${LIGHTSAIL_SSH_HOST}"

ssh_cmd=(ssh -o BatchMode=yes -o ConnectTimeout=15 -o LogLevel=ERROR)
if [[ -n "${SSH_IDENTITY}" ]]; then
  [[ -f "${SSH_IDENTITY}" ]] || lightsail_die "identity file not found: ${SSH_IDENTITY}"
  ssh_cmd+=(-i "${SSH_IDENTITY}")
fi
ssh_target="${SSH_HOST}"
if [[ "${SSH_HOST}" != *"@"* ]]; then
  ssh_target="${SSH_USER}@${SSH_HOST}"
fi
ssh_cmd+=("${ssh_target}" "bash -s -- origin $(printf '%q' "${HOSTS_CSV}")")

echo "TLS PQ probe: origin via ${ssh_target} (LIGHTSAIL_TARGET=${LIGHTSAIL_TARGET:-custom}) [read-only]"
if [[ -n "${DRY_RUN}" ]]; then
  echo "[dry-run] would run:"
  printf ' %q' "${ssh_cmd[@]}"
  echo
  exit 0
fi
"${ssh_cmd[@]}" <<<"${PROBE_SCRIPT}"
