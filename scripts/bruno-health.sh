#!/usr/bin/env bash
# Bruno collection health runner for Ezkey (Git Bash / Linux / macOS).
# Proves the bruno/ exploratory surface after migration; not a replacement for ezkey-tests.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRUNO_DIR="${ROOT}/bruno"
ENV_NAME="local"
SUITE="g0"
EXTRA_ARGS=()
REPORT_DIR="${ROOT}/logs/bruno"

usage() {
  cat <<'EOF'
Usage: ./scripts/bruno-health.sh [--suite g0|g4|public|crypto|all-catalog] [--env NAME] [--] [bru args...]

Suites:
  g0           crypto + public-auth + public-admin (default; no bearer required)
  crypto       crypto folder only
  public       public-auth + public-admin
  g4           catalog smoke folders that need a bearer token
               (pass --env-var token=... or export EZKEY_ADMIN_TOKEN)
  all-catalog  tenants-admin integrations-admin enrollments-admin api-keys-admin
               alerts-admin dashboard-admin encryption-keys-admin audit-logs-admin
               (requires token)

Install (once):
  cd bruno && npm install

Windows (from PowerShell):
  & "C:\Program Files\Git\bin\bash.exe" -lc './scripts/bruno-health.sh'
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --suite)
      SUITE="$2"
      shift 2
      ;;
    --env)
      ENV_NAME="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      EXTRA_ARGS+=("$@")
      break
      ;;
    *)
      EXTRA_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ ! -d "${BRUNO_DIR}/node_modules/@usebruno/cli" ]]; then
  echo "Bruno CLI not installed. Run: (cd bruno && npm install)" >&2
  exit 1
fi

mkdir -p "${REPORT_DIR}"

TARGETS=()
case "${SUITE}" in
  g0)
    # public-admin signup is feature-flagged (404 when disabled) — exclude from default smoke.
    TARGETS=(
      crypto
      public-auth/get-public-instance-info.bru
      public-admin/get-public-instance-info.bru
    )
    ;;
  crypto)
    TARGETS=(crypto)
    ;;
  public)
    TARGETS=(
      public-auth/get-public-instance-info.bru
      public-admin/get-public-instance-info.bru
    )
    ;;
  g4)
    # One read-only happy-path per Admin catalog folder (not full mutate chains).
    TARGETS=(
      tenants-admin/list.bru
      integrations-admin/search.bru
      enrollments-admin/search.bru
      api-keys-admin/list-all-keys.bru
      alerts-admin/list-alerts.bru
      dashboard-admin/get-overview.bru
      encryption-keys-admin/list-keys.bru
      audit-logs-admin/read-audit-logs.bru
    )
    if [[ -z "${EZKEY_ADMIN_TOKEN:-}" ]]; then
      echo "G4 requires EZKEY_ADMIN_TOKEN (or --env-var token=...)." >&2
      exit 1
    fi
    EXTRA_ARGS+=(--env-var "token=${EZKEY_ADMIN_TOKEN}")
    ;;
  all-catalog)
    TARGETS=(
      tenants-admin
      integrations-admin
      enrollments-admin
      api-keys-admin
      alerts-admin
      dashboard-admin
      encryption-keys-admin
      audit-logs-admin
    )
    if [[ -z "${EZKEY_ADMIN_TOKEN:-}" ]]; then
      echo "all-catalog requires EZKEY_ADMIN_TOKEN (or --env-var token=...)." >&2
      exit 1
    fi
    EXTRA_ARGS+=(--env-var "token=${EZKEY_ADMIN_TOKEN}")
    ;;
  *)
    echo "Unknown suite: ${SUITE}" >&2
    usage
    exit 1
    ;;
esac

REPORT_JSON="${REPORT_DIR}/health-${SUITE}.json"
cd "${BRUNO_DIR}"

echo "Running Bruno health suite=${SUITE} env=${ENV_NAME} targets=${TARGETS[*]}"
# shellcheck disable=SC2086
npx bru run "${TARGETS[@]}" --env "${ENV_NAME}" --reporter-json "${REPORT_JSON}" "${EXTRA_ARGS[@]}"
echo "Report: ${REPORT_JSON}"
