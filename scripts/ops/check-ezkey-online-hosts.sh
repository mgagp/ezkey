#!/usr/bin/env bash
# Fail if tracked files mention *.ezkey.online hostnames outside the product allowlist.
#
# Allowlist (served): auth-api, admin-api, integration-api, demo-acme, admin-ui.
# Bare zone name ezkey.online is allowed only when it is not a URL (https?://ezkey.online fails).
# Documentation wildcard *.ezkey.online is allowed.
# Not-served names may appear only in docs/lightsail/community-host.md (SSOT inventory).
#
# Override tree root for unit tests: CHECK_EZKEY_ONLINE_HOSTS_ROOT=/path/to/fixture
# Optional: CHECK_EZKEY_ONLINE_HOSTS_GIT=0 to scan all text files under ROOT (no git ls-files).
set -euo pipefail

ROOT="${CHECK_EZKEY_ONLINE_HOSTS_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
SSOT_REL="docs/lightsail/community-host.md"
USE_GIT="${CHECK_EZKEY_ONLINE_HOSTS_GIT:-1}"

ALLOW_LABELS='auth-api|admin-api|integration-api|demo-acme|admin-ui'
# Documented as not served in community-host.md only.
NOT_SERVED_LABELS='www|n|auth|admin|acme|api'

fail=0
hits=0

is_textish() {
  local f="$1"
  case "${f}" in
    *.png|*.jpg|*.jpeg|*.gif|*.webp|*.ico|*.pdf|*.jar|*.class|*.zip|*.gz|*.tgz|*.woff|*.woff2|*.ttf|*.eot|*.mp4|*.webm|*.so|*.dylib|*.bin)
      return 1
      ;;
  esac
  return 0
}

list_files() {
  if [[ "${USE_GIT}" == "1" ]] && command -v git >/dev/null 2>&1 && [[ -d "${ROOT}/.git" || -f "${ROOT}/.git" ]]; then
    # Tracked + staged/unstaged text paths under ROOT (relative to ROOT).
    (
      cd "${ROOT}"
      git ls-files -z
      git ls-files -z --others --exclude-standard
    ) | tr '\0' '\n' | sort -u
  else
    # Fixture / no-git mode: walk regular files.
    find "${ROOT}" -type f ! -path '*/.git/*' | sed "s|^${ROOT}/||" | sort -u
  fi
}

while IFS= read -r rel; do
  [[ -z "${rel}" ]] && continue
  is_textish "${rel}" || continue
  file="${ROOT}/${rel}"
  [[ -f "${file}" ]] || continue

  # Skip this script and its test (they mention forbidden names on purpose).
  case "${rel}" in
    scripts/ops/check-ezkey-online-hosts.sh|scripts/ops/check-ezkey-online-hosts.test.sh)
      continue
      ;;
  esac

  # Match host-like tokens ending in .ezkey.online (optional leading label(s)).
  # Also catch scheme+apex URL separately via the same host capture.
  while IFS= read -r line; do
    [[ -z "${line}" ]] && continue
    # line format from grep -nH: path:lineno:content — but we already have rel.
    lineno="${line%%:*}"
    content="${line#*:}"

    # Find every *.ezkey.online / label.ezkey.online / ezkey.online occurrence.
    # Use grep -oE for matches on the content line.
    matches="$(printf '%s\n' "${content}" | grep -oE '([Hh][Tt][Tt][Pp][Ss]?://)?([A-Za-z0-9_.*-]+\.)?ezkey\.online' || true)"
    [[ -z "${matches}" ]] && continue

    while IFS= read -r match; do
      [[ -z "${match}" ]] && continue
      hits=$((hits + 1))

      scheme=""
      host="${match}"
      if [[ "${match}" =~ ^[Hh][Tt][Tt][Pp][Ss]?://(.*)$ ]]; then
        scheme="url"
        host="${BASH_REMATCH[1]}"
      fi

      # Strip trailing dots sometimes used in DNS examples.
      host="${host%.}"

      if [[ "${host}" == "ezkey.online" ]]; then
        if [[ "${scheme}" == "url" ]]; then
          echo "FAIL: ${rel}:${lineno}: URL to apex zone is forbidden (${match})" >&2
          fail=$((fail + 1))
        fi
        # Bare zone name (not a URL) is allowed.
        continue
      fi

      if [[ "${host}" == "*.ezkey.online" ]]; then
        # Documentation wildcard for the product zone.
        continue
      fi

      label="${host%.ezkey.online}"
      if [[ "${label}" == "${host}" || -z "${label}" ]]; then
        echo "FAIL: ${rel}:${lineno}: could not parse host (${match})" >&2
        fail=$((fail + 1))
        continue
      fi

      # Multi-label (a.b.ezkey.online) — not in allowlist.
      if [[ "${label}" == *.* ]]; then
        echo "FAIL: ${rel}:${lineno}: multi-label host outside allowlist (${host})" >&2
        fail=$((fail + 1))
        continue
      fi

      if printf '%s\n' "${label}" | grep -Eq "^(${ALLOW_LABELS})$"; then
        continue
      fi

      if printf '%s\n' "${label}" | grep -Eq "^(${NOT_SERVED_LABELS})$"; then
        if [[ "${rel}" == "${SSOT_REL}" ]]; then
          # Explicit exclusion: SSOT may inventory not-served names.
          continue
        fi
        echo "FAIL: ${rel}:${lineno}: not-served host must only appear in ${SSOT_REL} (${host})" >&2
        fail=$((fail + 1))
        continue
      fi

      echo "FAIL: ${rel}:${lineno}: host outside allowlist (${host})" >&2
      fail=$((fail + 1))
    done <<<"${matches}"
  done < <(grep -nE '([Hh][Tt][Tt][Pp][Ss]?://)?([A-Za-z0-9_.*-]+\.)?ezkey\.online' "${file}" || true)
done < <(list_files)

if [[ "${fail}" -gt 0 ]]; then
  echo "check-ezkey-online-hosts: ${fail} violation(s) (scanned matches≈${hits})" >&2
  echo "Allowlist: auth-api, admin-api, integration-api, demo-acme, admin-ui (+ bare ezkey.online non-URL, *.ezkey.online)." >&2
  echo "Canon: ${SSOT_REL} § Product hostnames (ezkey.online)" >&2
  exit 1
fi

echo "PASS: check-ezkey-online-hosts (no forbidden *.ezkey.online hosts; matches≈${hits})"
exit 0
