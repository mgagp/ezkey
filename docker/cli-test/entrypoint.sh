#!/usr/bin/env sh
# Ezkey CLI Test Container Entrypoint
# This file will be in UTF-8 without BOM.

set -eu

WORK_DIR="/work"
CONFIG_FILE="${WORK_DIR}/ezkey.json"

mkdir -p "${WORK_DIR}"

if [ ! -f "${CONFIG_FILE}" ]; then
  ADMIN_URL="${EZKEY_ADMIN_API_URL:-http://admin-api:9080}"
  ADMIN_HEALTH_URL="${EZKEY_ADMIN_HEALTH_URL:-http://admin-api:9081}"
  AUTH_URL="${EZKEY_AUTH_API_URL:-http://auth-api:8080}"
  CRYPTO_URL="${EZKEY_CRYPTO_API_URL:-http://crypto-api:9090}"

  cat > "${CONFIG_FILE}" <<EOF
{
  "adminUrl": "${ADMIN_URL}",
  "adminHealthUrl": "${ADMIN_HEALTH_URL}",
  "authUrl": "${AUTH_URL}",
  "cryptoUrl": "${CRYPTO_URL}",
  "prettyPrint": true,
  "timeout": 30000,
  "dashboardRefreshSeconds": 30
}
EOF
fi

exec "$@"
