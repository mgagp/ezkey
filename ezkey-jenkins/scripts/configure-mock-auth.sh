#!/usr/bin/env bash
# Install ezkey-lab-auth and apply EzkeyMockSecurityRealm (break-glass + Integration MFA).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export EZKEY_JENKINS_ROOT="${ROOT}"
# shellcheck source=lib/jenkins-auth.sh
source "${ROOT}/scripts/lib/jenkins-auth.sh"
# shellcheck source=lib/lab-temp.sh
source "${ROOT}/scripts/lib/lab-temp.sh"

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

COOKIE_JAR="$(ezkey_lab_mktemp)"
SCRIPT="$(ezkey_lab_mktemp)"
trap 'rm -f "$COOKIE_JAR" "$SCRIPT"' EXIT

ADMIN_USER="$(jenkins_user)"
ADMIN_PASS="$(jenkins_pass)"
# From Jenkins container → Integration API (join ezkey_ezkey-network; else host.docker.internal)
BASE_URL="${EZKEY_MOCK_BASE_URL:-http://integration-api:7080}"
INT_KEY="${EZKEY_MOCK_INTEGRATION_KEY:-lab-integration-key}"
SECRET="${EZKEY_MOCK_SECRET:-lab-ezkey-secret}"
FORCE_CHALLENGE="${EZKEY_JENKINS_FORCE_CHALLENGE:-false}"

# Escape for Groovy single-quoted string (keys/secrets must not contain ')
escape_sq() { printf '%s' "$1" | sed "s/'/\\\\'/g"; }

# Normalize boolean for Groovy
case "$(printf '%s' "${FORCE_CHALLENGE}" | tr '[:upper:]' '[:lower:]')" in
  1|true|yes|on) FORCE_GROOVY=true ;;
  *) FORCE_GROOVY=false ;;
esac

cat >"${SCRIPT}" <<EOF
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.util.Secret
import jenkins.model.Jenkins
import org.ezkey.jenkins.auth.EzkeyMockSecurityRealm

def j = Jenkins.get()
def realm = new EzkeyMockSecurityRealm(
  '$(escape_sq "${BASE_URL}")',
  '$(escape_sq "${INT_KEY}")',
  Secret.fromString('$(escape_sq "${SECRET}")'),
  '$(escape_sq "${ADMIN_USER}")',
  Secret.fromString('$(escape_sq "${ADMIN_PASS}")')
)
realm.setForceChallenge(${FORCE_GROOVY})
j.setSecurityRealm(realm)
def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
j.setAuthorizationStrategy(strategy)
j.save()
println 'ezkey-lab: EzkeyMockSecurityRealm applied forceChallenge=' + realm.isForceChallenge()
EOF

echo "Applying EzkeyMockSecurityRealm via script console (forceChallenge=${FORCE_GROOVY}) ..."
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
    echo "Failed to apply realm. Last output:" >&2
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

echo "Verifying Ezkey MFA entry page ..."
code="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE}/ezkey-lab-login/")"
if [[ "$code" != "200" ]]; then
  echo "Ezkey lab login page failed HTTP ${code}" >&2
  exit 1
fi

echo "Configure OK."
echo "  Break-glass: ${BASE}/login  (${ADMIN_USER})"
echo "  Ezkey MFA:   ${BASE}/ezkey-lab-login/"
echo "  Integration base URL in realm: ${BASE_URL}"
echo "  forceChallenge: ${FORCE_GROOVY}"
