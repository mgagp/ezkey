#!/usr/bin/env bash
# Preflight check for Windows native path-length risk in Android CMake/Ninja builds.
#
# Threshold (documented): warn when the Windows path to ezkey_mobile/ is longer
# than EZKEY_ANDROID_PATH_ROOT_MAX characters (default **40**). Short roots such
# as C:\w\p\ezkey_mobile (~19 chars) are OK and must not warn. Longer clones under
# C:\Users\...\source\repos\... often hit CMake MAX_PATH on codegen object paths.
#
# Usage:
#   ./scripts/preflight-android-path-length.sh
#   ./scripts/preflight-android-path-length.sh --strict
#   ./scripts/preflight-android-path-length.sh --force-windows
#
# Env:
#   EZKEY_ANDROID_PATH_ROOT_MAX  Override root-length threshold (default 40).

set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRICT=false
FORCE_WINDOWS=false
# Default 40: C:\w\p\ezkey_mobile and C:\w\ezkey-worktree2\ezkey_mobile stay quiet;
# typical C:\Users\...\ezkey\ezkey_mobile paths (~50+) warn.
ROOT_MAX="${EZKEY_ANDROID_PATH_ROOT_MAX:-40}"

for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=true ;;
    --force-windows) FORCE_WINDOWS=true ;;
    -h|--help)
      sed -n '2,18p' "$0"
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

UNAME_S="$(uname -s 2>/dev/null || true)"
IS_WINDOWS=false
if [[ "$UNAME_S" == MINGW* || "$UNAME_S" == MSYS* || "$UNAME_S" == CYGWIN* || "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* || "${OSTYPE:-}" == win32* ]]; then
  IS_WINDOWS=true
fi
if [[ "$FORCE_WINDOWS" == true ]]; then
  IS_WINDOWS=true
fi

if [[ "$IS_WINDOWS" != true ]]; then
  echo "[preflight-path] Non-Windows shell detected: path-length preflight skipped."
  exit 0
fi

# Convert to Windows-like absolute path when running in Git Bash/MSYS.
if command -v cygpath >/dev/null 2>&1; then
  MOBILE_ROOT_WIN="$(cygpath -m "$MOBILE_ROOT")"
elif MOBILE_ROOT_WIN_CANDIDATE="$(cd "$MOBILE_ROOT" && pwd -W 2>/dev/null)"; then
  MOBILE_ROOT_WIN="$MOBILE_ROOT_WIN_CANDIDATE"
else
  MOBILE_ROOT_WIN="$MOBILE_ROOT"
fi

if [[ ! "$MOBILE_ROOT_WIN" =~ ^[A-Za-z]:/ ]]; then
  echo "[preflight-path] Could not resolve a Windows-style root path from current shell: ${MOBILE_ROOT_WIN}"
  echo "[preflight-path] Run this script from Git Bash on Windows (or use --force-windows for diagnostics only)."
  if [[ "$STRICT" == true ]]; then
    exit 1
  fi
  exit 0
fi

ROOT_LEN=${#MOBILE_ROOT_WIN}

# Diagnostic: predicted Ninja object path for the known gesture-handler offender.
# Kept for log context only — the warn decision uses ROOT_LEN vs ROOT_MAX because
# the full predicted path exceeds 260 even for short roots that build successfully.
SOURCE_REL="node_modules/react-native-gesture-handler/shared/shadowNodes/react/renderer/components/rngesturehandler_codegen/RNGestureHandlerDetectorShadowNode.cpp"
ROOT_NO_DRIVE="${MOBILE_ROOT_WIN#?:/}"
DRIVE_LETTER="${MOBILE_ROOT_WIN%%:*}"
NINJA_ESCAPED_ROOT="${DRIVE_LETTER}_/${ROOT_NO_DRIVE}"
NINJA_ESCAPED_ROOT="${NINJA_ESCAPED_ROOT//\\//}"
PREDICTED_OBJ_PATH="${MOBILE_ROOT_WIN}/android/app/.cxx/Debug/12345678/arm64-v8a/rngesturehandler_codegen_autolinked_build/CMakeFiles/react_codegen_rngesturehandler_codegen.dir/${NINJA_ESCAPED_ROOT}/${SOURCE_REL}.o"
PREDICTED_LEN=${#PREDICTED_OBJ_PATH}

echo "[preflight-path] Workspace root: ${MOBILE_ROOT_WIN}"
echo "[preflight-path] Root length: ${ROOT_LEN} (warn threshold: ${ROOT_MAX})"
echo "[preflight-path] Predicted worst native object path length (diagnostic): ${PREDICTED_LEN}"

if (( ROOT_LEN > ROOT_MAX )); then
  cat <<EOF
[preflight-path] Risk detected: Windows path to ezkey_mobile/ is ${ROOT_LEN} chars (threshold ${ROOT_MAX}).

Short roots such as C:\\w\\p are expected to stay under the threshold and should not warn.
Longer clones (especially under C:\\Users\\...) often hit CMake/Ninja MAX_PATH on
react-native-gesture-handler codegen object paths.

Recommended action before Gradle build:
  1. Use a shorter workspace root on the same drive (example: C:\\w\\p).
  2. Re-run dependency install from that short-root workspace.
  3. Run: ./scripts/build-install-debug-clean.sh

Override threshold only if you know your environment: EZKEY_ANDROID_PATH_ROOT_MAX=${ROOT_MAX}
EOF
  if [[ "$STRICT" == true ]]; then
    exit 1
  fi
  exit 0
fi

echo "[preflight-path] OK: root length ${ROOT_LEN} ≤ ${ROOT_MAX}; no path-length risk flagged."
exit 0
