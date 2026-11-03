#!/usr/bin/env bash
# Shared Jenkins HTTP helpers (basic auth + CSRF crumb).
# shellcheck shell=bash

jenkins_base() {
  echo "http://127.0.0.1:${EZKEY_JENKINS_HTTP_PORT:-28080}"
}

jenkins_user() {
  echo "${EZKEY_JENKINS_ADMIN_USER:-admin}"
}

jenkins_pass() {
  echo "${EZKEY_JENKINS_ADMIN_PASSWORD:-ezkey-lab-admin}"
}

# Probe whether Jenkins still allows anonymous read of /api/json.
jenkins_is_unsecured() {
  local base
  base="$(jenkins_base)"
  curl -fsS "${base}/api/json" >/dev/null 2>&1
}

# Populate COOKIE_JAR and set CRUMB_FIELD / CRUMB_VALUE.
# Requires COOKIE_JAR path set by caller.
jenkins_prepare_crumb() {
  local base user pass
  base="$(jenkins_base)"
  user="$(jenkins_user)"
  pass="$(jenkins_pass)"
  if jenkins_is_unsecured; then
    CRUMB_JSON="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${base}/crumbIssuer/api/json")"
  else
    CRUMB_JSON="$(curl -fsS -u "${user}:${pass}" -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      "${base}/crumbIssuer/api/json")"
  fi
  CRUMB_FIELD="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumbRequestField":"\([^"]*\)".*/\1/p')"
  CRUMB_VALUE="$(printf '%s' "$CRUMB_JSON" | sed -n 's/.*"crumb":"\([^"]*\)".*/\1/p')"
  if [[ -z "$CRUMB_FIELD" || -z "$CRUMB_VALUE" ]]; then
    echo "Could not obtain CSRF crumb" >&2
    return 1
  fi
}

jenkins_curl() {
  local base user pass
  base="$(jenkins_base)"
  user="$(jenkins_user)"
  pass="$(jenkins_pass)"
  if jenkins_is_unsecured; then
    curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "$@"
  else
    curl -fsS -u "${user}:${pass}" -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "$@"
  fi
}
