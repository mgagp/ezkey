#!/usr/bin/env bash
# Preflight check for Windows native path-length risk in Android CMake/Ninja builds.
# Usage:
#   ./scripts/preflight-android-path-length.sh
#   ./scripts/preflight-android-path-length.sh --strict
#   ./scripts/preflight-android-path-length.sh --force-windows

set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRICT=false
FORCE_WINDOWS=false

for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=true ;;
    --force-windows) FORCE_WINDOWS=true ;;
    -h|--help)
      sed -n '2,7p' "$0"
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

# This path is the recurring offender in observed failures with react-native-gesture-handler codegen.
SOURCE_REL="node_modules/react-native-gesture-handler/shared/shadowNodes/react/renderer/components/rngesturehandler_codegen/RNGestureHandlerDetectorShadowNode.cpp"

# Ninja object path observed in failures includes:
#   <root>/android/app/.cxx/Debug/<id>/arm64-v8a/.../C_/.../<source>.o
# We conservatively estimate with an 8-char build id placeholder.
ROOT_NO_DRIVE="${MOBILE_ROOT_WIN#?:/}"
DRIVE_LETTER="${MOBILE_ROOT_WIN%%:*}"
NINJA_ESCAPED_ROOT="${DRIVE_LETTER}_/${ROOT_NO_DRIVE}"
NINJA_ESCAPED_ROOT="${NINJA_ESCAPED_ROOT//\\//}"

PREDICTED_OBJ_PATH="${MOBILE_ROOT_WIN}/android/app/.cxx/Debug/12345678/arm64-v8a/rngesturehandler_codegen_autolinked_build/CMakeFiles/react_codegen_rngesturehandler_codegen.dir/${NINJA_ESCAPED_ROOT}/${SOURCE_REL}.o"
PREDICTED_LEN=${#PREDICTED_OBJ_PATH}

echo "[preflight-path] Workspace root: ${MOBILE_ROOT_WIN}"
echo "[preflight-path] Root length: ${ROOT_LEN}"
echo "[preflight-path] Predicted worst native object path length: ${PREDICTED_LEN}"

if (( PREDICTED_LEN > 260 )); then
  cat <<EOF
[preflight-path] Risk detected: predicted native object path exceeds Windows MAX_PATH (260).

Recommended action before Gradle build:
  1. Use a shorter workspace root on the same drive.
     Example: C:\\w\\ezkey-worktree2
  2. Re-run dependency install from that short-root workspace.
  3. Run: ./scripts/build-install-debug-clean.sh
EOF
  if [[ "$STRICT" == true ]]; then
    exit 1
  fi
  exit 0
fi

echo "[preflight-path] OK: no path-length risk detected for known native offender."
exit 0
