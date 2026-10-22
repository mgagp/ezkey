#!/usr/bin/env bash
# Trigger m0-ls and assert last build is SUCCESS (works with secured or unsecured).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export EZKEY_JENKINS_ROOT="${ROOT}"
# shellcheck source=lib/jenkins-auth.sh
source "${ROOT}/scripts/lib/jenkins-auth.sh"
# shellcheck source=lib/lab-temp.sh
source "${ROOT}/scripts/lib/lab-temp.sh"
cd "$ROOT"

BASE="$(jenkins_base)"
JOB="m0-ls"
USER="$(jenkins_user)"
PASS="$(jenkins_pass)"

COOKIE_JAR="$(ezkey_lab_mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

auth_curl() {
  if jenkins_is_unsecured; then
    curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "$@"
  else
    curl -fsS -u "${USER}:${PASS}" -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "$@"
  fi
}

echo "Waiting for Jenkins API at ${BASE} ..."
for _ in $(seq 1 60); do
  if auth_curl "${BASE}/api/json" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
auth_curl "${BASE}/api/json" >/dev/null

echo "Waiting for job ${JOB} ..."
for _ in $(seq 1 30); do
  if auth_curl "${BASE}/job/${JOB}/api/json" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
auth_curl "${BASE}/job/${JOB}/api/json" >/dev/null

jenkins_prepare_crumb

echo "Triggering ${JOB} ..."
auth_curl -X POST -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
  -D /tmp/ezkey-jenkins-build.hdr -o /dev/null \
  "${BASE}/job/${JOB}/build" || {
  echo "Build trigger failed. Headers:" >&2
  cat /tmp/ezkey-jenkins-build.hdr >&2 || true
  exit 1
}

echo "Waiting for build SUCCESS ..."
for _ in $(seq 1 60); do
  json="$(auth_curl "${BASE}/job/${JOB}/lastBuild/api/json" 2>/dev/null || true)"
  if [[ -n "$json" ]]; then
    result="$(printf '%s' "$json" | sed -n 's/.*"result":"\([^"]*\)".*/\1/p' | head -1)"
    building="$(printf '%s' "$json" | sed -n 's/.*"building":\([^,]*\).*/\1/p' | head -1)"
    number="$(printf '%s' "$json" | sed -n 's/.*"number":\([0-9]*\).*/\1/p' | head -1)"
    if [[ "$building" == "false" && -n "$result" ]]; then
      echo "Build #${number} result=${result}"
      if [[ "$result" == "SUCCESS" ]]; then
        echo "M0 smoke OK: job ${JOB} is green."
        echo "UI: ${BASE}/job/${JOB}/"
        exit 0
      fi
      echo "Expected SUCCESS, got ${result}. Console:" >&2
      auth_curl "${BASE}/job/${JOB}/lastBuild/consoleText" >&2 || true
      exit 1
    fi
  fi
  sleep 2
done

echo "Timed out waiting for ${JOB} build." >&2
exit 1
