#!/usr/bin/env bash
# Create/update freestyle job m1-hello that uses the Ezkey lab hello build step.
set -euo pipefail

PORT="${EZKEY_JENKINS_HTTP_PORT:-28080}"
BASE="http://127.0.0.1:${PORT}"
JOB="m1-hello"

COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

echo "Waiting for Jenkins at ${BASE} ..."
for _ in $(seq 1 60); do
  if curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${BASE}/api/json" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

CRUMB_JSON="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${BASE}/crumbIssuer/api/json")"
CRUMB_FIELD="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumbRequestField":"\([^"]*\)".*/\1/p')"
CRUMB_VALUE="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumb":"\([^"]*\)".*/\1/p')"

CONFIG="$(mktemp)"
trap 'rm -f "$COOKIE_JAR" "$CONFIG"' EXIT
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

if curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${BASE}/job/${JOB}/api/json" >/dev/null 2>&1; then
  echo "Updating job ${JOB} ..."
  curl -fsS -X POST -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
    -H "Content-Type: application/xml" \
    --data-binary @"${CONFIG}" \
    "${BASE}/job/${JOB}/config.xml" >/dev/null
else
  echo "Creating job ${JOB} ..."
  curl -fsS -X POST -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
    -H "Content-Type: application/xml" \
    --data-binary @"${CONFIG}" \
    "${BASE}/createItem?name=${JOB}" >/dev/null
fi

echo "Triggering ${JOB} ..."
# Refresh crumb after create (same jar).
CRUMB_JSON="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${BASE}/crumbIssuer/api/json")"
CRUMB_FIELD="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumbRequestField":"\([^"]*\)".*/\1/p')"
CRUMB_VALUE="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumb":"\([^"]*\)".*/\1/p')"
curl -fsS -X POST -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
  "${BASE}/job/${JOB}/build" >/dev/null

echo "Waiting for SUCCESS ..."
for _ in $(seq 1 60); do
  json="$(curl -fsS "${BASE}/job/${JOB}/lastBuild/api/json" 2>/dev/null || true)"
  if [[ -n "$json" ]]; then
    result="$(printf '%s' "$json" | sed -n 's/.*"result":"\([^"]*\)".*/\1/p' | head -1)"
    building="$(printf '%s' "$json" | sed -n 's/.*"building":\([^,]*\).*/\1/p' | head -1)"
    if [[ "$building" == "false" && -n "$result" ]]; then
      echo "Build result=${result}"
      curl -fsS "${BASE}/job/${JOB}/lastBuild/consoleText" || true
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
