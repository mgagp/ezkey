#!/usr/bin/env bash
# Trigger m0-ls and assert last build is SUCCESS.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PORT="${EZKEY_JENKINS_HTTP_PORT:-28080}"
BASE="http://127.0.0.1:${PORT}"
JOB="m0-ls"

echo "Waiting for Jenkins API at ${BASE} ..."
for _ in $(seq 1 60); do
  if curl -fsS "${BASE}/api/json" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS "${BASE}/api/json" >/dev/null

echo "Waiting for job ${JOB} ..."
for _ in $(seq 1 30); do
  if curl -fsS "${BASE}/job/${JOB}/api/json" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS "${BASE}/job/${JOB}/api/json" >/dev/null

COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

echo "Fetching CSRF crumb (session-bound) ..."
CRUMB_JSON="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${BASE}/crumbIssuer/api/json")"
CRUMB_FIELD="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumbRequestField":"\([^"]*\)".*/\1/p')"
CRUMB_VALUE="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumb":"\([^"]*\)".*/\1/p')"
if [[ -z "$CRUMB_FIELD" || -z "$CRUMB_VALUE" ]]; then
  echo "Could not parse CSRF crumb from: ${CRUMB_JSON}" >&2
  exit 1
fi

echo "Triggering ${JOB} ..."
# Freestyle may return 201 with empty body; crumb + same session cookie required.
curl -fsS -X POST -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "${CRUMB_FIELD}: ${CRUMB_VALUE}" \
  -D /tmp/ezkey-jenkins-build.hdr -o /dev/null \
  "${BASE}/job/${JOB}/build" || {
  echo "Build trigger failed. Headers:" >&2
  cat /tmp/ezkey-jenkins-build.hdr >&2 || true
  exit 1
}

echo "Waiting for build SUCCESS ..."
for _ in $(seq 1 60); do
  json="$(curl -fsS "${BASE}/job/${JOB}/lastBuild/api/json" 2>/dev/null || true)"
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
      curl -fsS "${BASE}/job/${JOB}/lastBuild/consoleText" >&2 || true
      exit 1
    fi
  fi
  sleep 2
done

echo "Timed out waiting for ${JOB} build." >&2
exit 1
