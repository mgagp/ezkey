#!/usr/bin/env bash
# Install ezkey-lab-auth if needed and switch SecurityRealm to EzkeyMockSecurityRealm.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/jenkins-auth.sh
source "${ROOT}/scripts/lib/jenkins-auth.sh"

CONTAINER="${EZKEY_JENKINS_CONTAINER:-ezkey-jenkins}"
HPI="${ROOT}/ezkey-lab-auth/target/ezkey-lab-auth.hpi"
BASE="$(jenkins_base)"

if [[ ! -f "${HPI}" ]]; then
  echo "Missing ${HPI}. Run ./scripts/build-auth-plugin.sh first." >&2
  exit 1
fi

echo "Installing ${HPI} into ${CONTAINER} ..."
docker cp "${HPI}" "${CONTAINER}:/var/jenkins_home/plugins/ezkey-lab-auth.hpi"
docker exec "${CONTAINER}" rm -f \
  /var/jenkins_home/plugins/ezkey-lab-auth.jpi.disabled \
  /var/jenkins_home/plugins/ezkey-lab-auth.hpi.disabled 2>/dev/null || true

echo "Restarting ${CONTAINER} ..."
docker restart "${CONTAINER}" >/dev/null

echo "Waiting for Jenkins ..."
for _ in $(seq 1 90); do
  if curl -fsS "${BASE}/login" >/dev/null 2>&1 || curl -fsS "${BASE}/api/json" >/dev/null 2>&1; then
    status="$(docker inspect --format='{{.State.Health.Status}}' "${CONTAINER}" 2>/dev/null || echo starting)"
    if [[ "$status" == "healthy" ]]; then
      break
    fi
  fi
  sleep 2
done

# Wait until plugin class is loadable via script console.
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

ADMIN_USER="$(jenkins_user)"
ADMIN_PASS="$(jenkins_pass)"
BASE_URL="${EZKEY_MOCK_BASE_URL:-http://127.0.0.1:7080}"
INT_KEY="${EZKEY_MOCK_INTEGRATION_KEY:-lab-integration-key}"
SECRET="${EZKEY_MOCK_SECRET:-lab-ezkey-secret}"

SCRIPT="$(mktemp)"
trap 'rm -f "$COOKIE_JAR" "$SCRIPT"' EXIT
cat >"${SCRIPT}" <<EOF
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.util.Secret
import jenkins.model.Jenkins
import org.ezkey.jenkins.auth.EzkeyMockSecurityRealm

def j = Jenkins.get()
def realm = new EzkeyMockSecurityRealm(
  '${BASE_URL}',
  '${INT_KEY}',
  Secret.fromString('${SECRET}'),
  '${ADMIN_USER}',
  Secret.fromString('${ADMIN_PASS}')
)
j.setSecurityRealm(realm)
def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
j.setAuthorizationStrategy(strategy)
j.save()
println 'ezkey-lab: EzkeyMockSecurityRealm applied'
EOF

echo "Applying EzkeyMockSecurityRealm via script console ..."
for attempt in $(seq 1 30); do
  jenkins_prepare_crumb || { sleep 2; continue; }
  if jenkins_is_unsecured; then
    out="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
      --data-urlencode "script@${SCRIPT}" \
      "${BASE}/scriptText" 2>&1 || true)"
  else
    out="$(curl -fsS -u "${ADMIN_USER}:${ADMIN_PASS}" -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
      --data-urlencode "script@${SCRIPT}" \
      "${BASE}/scriptText" 2>&1 || true)"
  fi
  if printf '%s' "$out" | grep -q 'EzkeyMockSecurityRealm applied'; then
    echo "$out"
    break
  fi
  if [[ "$attempt" -eq 30 ]]; then
    echo "Failed to apply mock realm. Last output:" >&2
    echo "$out" >&2
    exit 1
  fi
  sleep 3
done

echo "Verifying break-glass login ..."
code="$(curl -sS -o /dev/null -w '%{http_code}' -u "${ADMIN_USER}:${ADMIN_PASS}" "${BASE}/api/json")"
if [[ "$code" != "200" ]]; then
  echo "Break-glass basic auth failed HTTP ${code}" >&2
  exit 1
fi

echo "Verifying mock auto-accept login (user=labuser) ..."
code="$(curl -sS -o /dev/null -w '%{http_code}' -u "labuser:mock-pass" "${BASE}/api/json")"
if [[ "$code" != "200" ]]; then
  echo "Mock user basic auth failed HTTP ${code}" >&2
  exit 1
fi

echo "M2 configure OK."
echo "  UI login: ${BASE}/login"
echo "  Break-glass: ${ADMIN_USER} / (EZKEY_JENKINS_ADMIN_PASSWORD)"
echo "  Mock user: any username / any non-empty password (secret+URL+key must be set)"
