#!/usr/bin/env bash
# Resolve a local JDK for React Native Android builds (never JDK 25 from PATH).
#
# Project posture: **JDK 17** is the Ezkey Mobile standard (CI Temurin 17 via
# actions/setup-java). Migrating the project to JDK 21 is a separate decision —
# do not reintroduce gradle-daemon-jvm.properties / Foojay auto-download to pin 21.
# Locally, Android Studio JBR 21 is still accepted when no JDK 17 install is found,
# because RN/AGP allow it; prefer dedicated JDK 17 paths when both exist.
#
# Usage:
#   source "$(dirname "$0")/resolve-android-jdk.sh"   # sets JAVA_HOME
#   ./scripts/resolve-android-jdk.sh --print          # prints path and exits
set -euo pipefail

_resolve_android_jdk() {
  local candidate java_bin major
  local -a jdk17_hits=() jdk21_hits=()

  # Explicit override (CI, maintainer workstation).
  if [[ -n "${EZKEY_ANDROID_JAVA_HOME:-}" && -x "${EZKEY_ANDROID_JAVA_HOME}/bin/java" ]]; then
    echo "${EZKEY_ANDROID_JAVA_HOME}"
    return 0
  fi

  if [[ "$OSTYPE" == "darwin"* ]]; then
    candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
      echo "$candidate"
      return 0
    fi
  fi

  # Windows Git Bash / MSYS — prefer dedicated JDK 17 installs, then Studio JBR.
  local -a candidates=(
    "/c/Tools/jdk17"
    "/c/Tools/jdk-17.0.18+8"
    "/c/Program Files/Microsoft/jdk-17.0.18.8-hotspot"
    "/c/Program Files/Android/Android Studio/jbr"
    "C:/Program Files/Android/Android Studio/jbr"
    "/c/Program Files/Android/Android Studio1/jbr"
    "C:/Program Files/Android/Android Studio1/jbr"
  )

  for candidate in "${candidates[@]}"; do
    java_bin="${candidate}/bin/java"
    if [[ -x "$java_bin" ]]; then
      major="$("$java_bin" -version 2>&1 | sed -n 's/.* version "\([0-9]*\).*/\1/p' | head -1)"
      if [[ "$major" == "17" ]]; then
        jdk17_hits+=("$candidate")
      elif [[ "$major" == "21" ]]; then
        jdk21_hits+=("$candidate")
      fi
    fi
  done

  if ((${#jdk17_hits[@]} > 0)); then
    echo "${jdk17_hits[0]}"
    return 0
  fi
  if ((${#jdk21_hits[@]} > 0)); then
    echo "${jdk21_hits[0]}"
    return 0
  fi

  return 1
}

if ! _jdk="$(_resolve_android_jdk)"; then
  echo "ezkey_mobile: no JDK 17 found for Android (JDK 21 Studio JBR also accepted). Set EZKEY_ANDROID_JAVA_HOME or install JDK 17." >&2
  echo "  Project standard is JDK 17; do not use JDK 25 (major version 69 / react.settings plugin errors)." >&2
  exit 1
fi

export JAVA_HOME="$_jdk"
export PATH="${JAVA_HOME}/bin:${PATH}"

if [[ "${1:-}" == "--print" ]]; then
  echo "${JAVA_HOME}"
  exit 0
fi
