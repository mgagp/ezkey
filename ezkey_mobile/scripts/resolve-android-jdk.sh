#!/usr/bin/env bash
# Resolve a local JDK for React Native Android builds (never JDK 25 from PATH).
#
# Project posture: **JDK 17** is the Ezkey Mobile standard (CI Temurin 17 via
# actions/setup-java). Migrating the project to JDK 21 is a separate decision —
# do not reintroduce gradle-daemon-jvm.properties / Foojay auto-download to pin 21.
# Locally, Android Studio JBR 21 is still accepted when no JDK 17 install is found,
# because RN/AGP allow it; prefer dedicated JDK 17 paths when both exist.
#
# Discovery order:
#   1. EZKEY_ANDROID_JAVA_HOME (explicit override; must contain bin/java)
#   2. macOS: /usr/libexec/java_home -v 17
#   3. Versionless + glob candidates on Windows / Linux / macOS (prefer major 17)
#   4. Android Studio JBR (17 preferred, else 21)
#
# Usage:
#   source "$(dirname "$0")/resolve-android-jdk.sh"   # sets JAVA_HOME
#   ./scripts/resolve-android-jdk.sh --print          # prints path and exits
set -euo pipefail

_java_major() {
  local java_bin="$1"
  "$java_bin" -version 2>&1 | sed -n 's/.* version "\([0-9]*\).*/\1/p' | head -1
}

_has_java() {
  local home="$1"
  [[ -x "${home}/bin/java" || -x "${home}/bin/java.exe" ]]
}

_java_bin_for() {
  local home="$1"
  if [[ -x "${home}/bin/java" ]]; then
    echo "${home}/bin/java"
  else
    echo "${home}/bin/java.exe"
  fi
}

# Returns 0 if $1 (newline-separated list) already contains exact line $2.
_list_has() {
  local list=$1
  local value=$2
  [[ -n "$list" ]] || return 1
  printf '%s\n' "$list" | grep -Fqx -- "$value"
}

_resolve_android_jdk() {
  local candidate java_bin major
  local ordered="" jdk17_hits="" jdk21_hits=""
  local _nullglob_was_on=0

  # Explicit override (CI, maintainer workstation).
  if [[ -n "${EZKEY_ANDROID_JAVA_HOME:-}" ]]; then
    if _has_java "${EZKEY_ANDROID_JAVA_HOME}"; then
      echo "${EZKEY_ANDROID_JAVA_HOME}"
      return 0
    fi
    echo "ezkey_mobile: EZKEY_ANDROID_JAVA_HOME is set but bin/java is missing: ${EZKEY_ANDROID_JAVA_HOME}" >&2
    return 1
  fi

  if [[ "$OSTYPE" == "darwin"* ]]; then
    candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "$candidate" ]] && _has_java "$candidate"; then
      echo "$candidate"
      return 0
    fi
  fi

  if shopt -q nullglob 2>/dev/null; then
    _nullglob_was_on=1
  else
    shopt -s nullglob
  fi

  # Versionless paths first, then globs (patch versions change across machines).
  # Escaped spaces keep "Program Files" / "Android Studio*" as single words.
  for candidate in \
    /c/Tools/jdk17 \
    C:/Tools/jdk17 \
    /c/Tools/jdk-17 \
    C:/Tools/jdk-17 \
    /c/Program\ Files/Microsoft/jdk-17* \
    /c/Program\ Files/Eclipse\ Adoptium/jdk-17* \
    /c/Program\ Files/Java/jdk-17* \
    /c/Tools/jdk-17* \
    /c/Program\ Files/Android/Android\ Studio/jbr \
    /c/Program\ Files/Android/Android\ Studio1/jbr \
    /c/Program\ Files/Android/Android\ Studio*/jbr \
    C:/Program\ Files/Android/Android\ Studio/jbr \
    C:/Program\ Files/Android/Android\ Studio1/jbr \
    /usr/lib/jvm/temurin-17-jdk* \
    /usr/lib/jvm/java-17-openjdk* \
    /usr/lib/jvm/java-17-temurin* \
    /usr/lib/jvm/jdk-17* \
    /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
    /opt/homebrew/opt/openjdk@17 \
    /usr/local/opt/openjdk@17; do
    if [[ -d "$candidate" ]] && _has_java "$candidate"; then
      if ! _list_has "$ordered" "$candidate"; then
        ordered="${ordered}${ordered:+$'\n'}${candidate}"
      fi
    fi
  done

  if [[ "$_nullglob_was_on" -eq 0 ]]; then
    shopt -u nullglob
  fi

  while IFS= read -r candidate; do
    [[ -n "$candidate" ]] || continue
    java_bin="$(_java_bin_for "$candidate")"
    major="$(_java_major "$java_bin")"
    if [[ "$major" == "17" ]]; then
      jdk17_hits="${jdk17_hits}${jdk17_hits:+$'\n'}${candidate}"
    elif [[ "$major" == "21" ]]; then
      jdk21_hits="${jdk21_hits}${jdk21_hits:+$'\n'}${candidate}"
    fi
  done <<<"$ordered"

  if [[ -n "$jdk17_hits" ]]; then
    printf '%s\n' "$jdk17_hits" | head -1
    return 0
  fi
  if [[ -n "$jdk21_hits" ]]; then
    local fallback
    fallback="$(printf '%s\n' "$jdk21_hits" | head -1)"
    # Selection order unchanged — warn so silent JDK 21 use is visible.
    echo "ezkey_mobile: WARNING: no JDK 17 found; falling back to JDK 21 at:" >&2
    echo "  ${fallback}" >&2
    echo "  Project standard is JDK 17 (CI Temurin 17). Install JDK 17 or set:" >&2
    echo "  export EZKEY_ANDROID_JAVA_HOME=/path/to/jdk-17" >&2
    echo "  Migrating the project to JDK 21 is a separate decision." >&2
    printf '%s\n' "$fallback"
    return 0
  fi

  return 1
}

if ! _jdk="$(_resolve_android_jdk)"; then
  echo "ezkey_mobile: no JDK 17 found for Android (JDK 21 Studio JBR also accepted). Set EZKEY_ANDROID_JAVA_HOME or install JDK 17." >&2
  echo "  Project standard is JDK 17; do not use JDK 25 (major version 69 / react.settings plugin errors)." >&2
  echo "  Tip: export EZKEY_ANDROID_JAVA_HOME=/path/to/jdk-17  (must contain bin/java)." >&2
  exit 1
fi

export JAVA_HOME="$_jdk"
export PATH="${JAVA_HOME}/bin:${PATH}"

if [[ "${1:-}" == "--print" ]]; then
  echo "${JAVA_HOME}"
  exit 0
fi
