#!/usr/bin/env bash
# Resolve JDK 17/21 for React Native Android builds (never JDK 25 from PATH).
# Usage:
#   source "$(dirname "$0")/resolve-android-jdk.sh"   # sets JAVA_HOME
#   ./scripts/resolve-android-jdk.sh --print          # prints path and exits
set -euo pipefail

_resolve_android_jdk() {
  local candidate dir java_bin major

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

  # Windows Git Bash / MSYS — probe common installs (order: Android Studio JBR, then JDK 17).
  local -a candidates=(
    "/c/Program Files/Android/Android Studio/jbr"
    "C:/Program Files/Android/Android Studio/jbr"
    "/c/Program Files/Android/Android Studio1/jbr"
    "C:/Program Files/Android/Android Studio1/jbr"
    "/c/Tools/jdk17"
    "/c/Tools/jdk-17.0.18+8"
    "/c/Program Files/Microsoft/jdk-17.0.18.8-hotspot"
  )

  for candidate in "${candidates[@]}"; do
    java_bin="${candidate}/bin/java"
    if [[ -x "$java_bin" ]]; then
      major="$("$java_bin" -version 2>&1 | sed -n 's/.* version "\([0-9]*\).*/\1/p' | head -1)"
      if [[ "$major" == "17" || "$major" == "21" ]]; then
        echo "$candidate"
        return 0
      fi
    fi
  done

  return 1
}

if ! _jdk="$(_resolve_android_jdk)"; then
  echo "ezkey_mobile: no JDK 17/21 found for Android. Set EZKEY_ANDROID_JAVA_HOME or install Android Studio JBR / JDK 17." >&2
  echo "  RN 0.85 Android must not use JDK 25 (major version 69 / react.settings plugin errors)." >&2
  exit 1
fi

export JAVA_HOME="$_jdk"
export PATH="${JAVA_HOME}/bin:${PATH}"

if [[ "${1:-}" == "--print" ]]; then
  echo "${JAVA_HOME}"
  exit 0
fi
