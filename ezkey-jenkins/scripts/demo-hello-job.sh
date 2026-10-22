#!/usr/bin/env bash
# Create/update freestyle job m1-hello that uses the Ezkey lab hello build step.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export EZKEY_JENKINS_ROOT="${ROOT}"
# shellcheck source=lib/jenkins-auth.sh
source "${ROOT}/scripts/lib/jenkins-auth.sh"
# shellcheck source=lib/lab-temp.sh
source "${ROOT}/scripts/lib/lab-temp.sh"

BASE="$(jenkins_base)"
JOB="m1-hello"
USER="$(jenkins_user)"
PASS="$(jenkins_pass)"

COOKIE_JAR="$(ezkey_lab_mktemp)"
CONFIG="$(ezkey_lab_mktemp)"
trap 'rm -f "$COOKIE_JAR" "$CONFIG"' EXIT

auth_curl() {
  if jenkins_is_unsecured; then
    curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "$@"
  else
    curl -fsS -u "${USER}:${PASS}" -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "$@"
  fi
}

echo "Waiting for Jenkins at ${BASE} ..."
for _ in $(seq 1 60); do
  if auth_curl "${BASE}/api/json" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

jenkins_prepare_crumb

cat >"${CONFIG}" <<'EOF'
<?xml version='1.1' encoding='UTF-8'?>
<project>
  <description>M1 demo: Ezkey lab hello-world build step (#972).</description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <scm class="hudson.scm.NullSCM"/>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers/>
  <concurrentBuild>false</concurrentBuild>
  <builders>
    <org.ezkey.jenkins.hello.HelloWorldBuilder plugin="ezkey-lab-hello@1.0-SNAPSHOT">
      <name>Ezkey</name>
      <useFrench>false</useFrench>
    </org.ezkey.jenkins.hello.HelloWorldBuilder>
  </builders>
  <publishers/>
  <buildWrappers/>
</project>
EOF

if auth_curl "${BASE}/job/${JOB}/api/json" >/dev/null 2>&1; then
  echo "Updating job ${JOB} ..."
  auth_curl -X POST -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
    -H "Content-Type: application/xml" \
    --data-binary @"${CONFIG}" \
    "${BASE}/job/${JOB}/config.xml" >/dev/null
else
  echo "Creating job ${JOB} ..."
  auth_curl -X POST -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
    -H "Content-Type: application/xml" \
    --data-binary @"${CONFIG}" \
    "${BASE}/createItem?name=${JOB}" >/dev/null
fi

jenkins_prepare_crumb
echo "Triggering ${JOB} ..."
auth_curl -X POST -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
  "${BASE}/job/${JOB}/build" >/dev/null

echo "Waiting for SUCCESS ..."
for _ in $(seq 1 60); do
  json="$(auth_curl "${BASE}/job/${JOB}/lastBuild/api/json" 2>/dev/null || true)"
  if [[ -n "$json" ]]; then
    result="$(printf '%s' "$json" | sed -n 's/.*"result":"\([^"]*\)".*/\1/p' | head -1)"
    building="$(printf '%s' "$json" | sed -n 's/.*"building":\([^,]*\).*/\1/p' | head -1)"
    if [[ "$building" == "false" && -n "$result" ]]; then
      echo "Build result=${result}"
      auth_curl "${BASE}/job/${JOB}/lastBuild/consoleText" || true
      if [[ "$result" == "SUCCESS" ]]; then
        echo "M1 demo job OK: ${BASE}/job/${JOB}/"
        exit 0
      fi
      exit 1
    fi
  fi
  sleep 2
done
echo "Timed out waiting for ${JOB}." >&2
exit 1
