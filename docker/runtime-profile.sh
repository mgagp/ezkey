#!/bin/bash
# Ezkey runtime profile resolver (product language → Spring mechanism).
#
# Product-facing key: EZKEY_RUNTIME_PROFILE=integrity|eval (unset = integrity).
# Operators also use clean-start --runtime=eval|integrity.
#
# Mechanism: when eval is selected, append Spring profile `docker-eval` (last) so
# application-docker-eval.properties overrides docker / docker-test defaults.
# Do not treat Spring profile names as the ops language (V-2026-0010).
#
# Claim boundary: eval = MFA crypto on; audit-integrity monitoring off by design.
# Tamper-evident claims apply only under the integrity preset.
#
# Source this file from docker/start.sh, docker/start-ha.sh, and ezkey-tests/clean-start.sh.
# Requires: contains_profile <profiles> <name>  (caller may define; fallback provided).

if ! declare -F contains_profile >/dev/null 2>&1; then
  contains_profile() {
    local profiles="$1"
    local profile="$2"
    [[ ",${profiles}," == *",${profile},"* ]]
  }
fi

# Normalize EZKEY_RUNTIME_PROFILE and mutate SPRING_PROFILES_ACTIVE for eval.
# Prints a one-line summary to stdout for operator logs.
resolve_ezkey_runtime_profile() {
  local raw="${EZKEY_RUNTIME_PROFILE:-integrity}"
  local normalized
  normalized="$(printf '%s' "${raw}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"

  case "${normalized}" in
    "" | integrity)
      export EZKEY_RUNTIME_PROFILE="integrity"
      # Strip accidental docker-eval from a previous shell export when reverting to integrity.
      if contains_profile "${SPRING_PROFILES_ACTIVE:-}" "docker-eval"; then
        local cleaned
        cleaned="$(printf '%s' "${SPRING_PROFILES_ACTIVE}" | sed -E 's/(^|,)docker-eval(,|$)/\1/g; s/,,/,/g; s/^,//; s/,$//')"
        export SPRING_PROFILES_ACTIVE="${cleaned}"
      fi
      echo "Runtime profile: integrity (default — audit-chain checkpoints + heartbeat supervision on)"
      ;;
    eval)
      export EZKEY_RUNTIME_PROFILE="eval"
      # Ensure docker base is present (docker-eval overrides docker / docker-test).
      if [ -z "${SPRING_PROFILES_ACTIVE:-}" ]; then
        export SPRING_PROFILES_ACTIVE="docker,docker-eval"
      else
        if ! contains_profile "${SPRING_PROFILES_ACTIVE}" "docker"; then
          export SPRING_PROFILES_ACTIVE="docker,${SPRING_PROFILES_ACTIVE}"
        fi
        if ! contains_profile "${SPRING_PROFILES_ACTIVE}" "docker-eval"; then
          export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE},docker-eval"
        fi
      fi
      echo "Runtime profile: eval (opt-in — MFA crypto on; audit-integrity monitoring off)"
      ;;
    *)
      echo "❌ Error: EZKEY_RUNTIME_PROFILE must be 'integrity' or 'eval' (got: ${raw})" >&2
      echo "   Unset or EZKEY_RUNTIME_PROFILE=integrity keeps the default integrity posture." >&2
      return 1
      ;;
  esac
  return 0
}
