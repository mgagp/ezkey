#!/usr/bin/env bash
#
# pam-doctor sanitizer replay — ASan + UBSan, then Valgrind, on the existing
# ezkey-pam harnesses. Not a CI gate. Does not edit ezkey-pam/src.
#
# Usage (Git Bash on Windows, Linux, or macOS), from repo root:
#   ./scripts/pam-doctor-sanitizers.sh
#   ./scripts/pam-doctor-sanitizers.sh --skip-valgrind
#
# Image: ubuntu:24.04 (same default as ./scripts/pam-doctor-curated.sh).
# Override with EZKEY_PAM_DOCTOR_IMAGE.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

KEYWORD="pam-doctor-curated"
OUTPUT_DIR="$REPO_ROOT/logs/pam-doctor/sanitizers"
PAM_DIR="$REPO_ROOT/ezkey-pam"
IMAGE="${EZKEY_PAM_DOCTOR_IMAGE:-ubuntu:24.04}"
SKIP_VALGRIND=0

usage() {
  cat <<'EOF'
Usage: ./scripts/pam-doctor-sanitizers.sh [--skip-valgrind]

  Builds the existing ezkey-pam harnesses (make test targets) with
  AddressSanitizer and UndefinedBehaviorSanitizer, runs them, then repeats
  the run under Valgrind on a separate non-sanitized build.

  --skip-valgrind   ASan + UBSan only

Outputs under logs/pam-doctor/sanitizers/ (gitignored).
Not a CI gate. Keyword: pam-doctor-curated
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-valgrind)
      SKIP_VALGRIND=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

command -v docker >/dev/null 2>&1 || {
  echo "[$KEYWORD] ERROR: docker is not available." >&2
  echo "The sanitizer replay runs in ${IMAGE}." >&2
  exit 2
}

[[ -f "$PAM_DIR/Makefile" && -d "$PAM_DIR/test" ]] || {
  echo "[$KEYWORD] ERROR: ezkey-pam Makefile or test/ is missing." >&2
  exit 2
}

docker_mount_src() {
  local path="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$path"
  else
    echo "$path" | sed -e 's|^/c/|C:/|' -e 's|^/mnt/c/|C:/|'
  fi
}

mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_DIR"/* 2>/dev/null || true

pam_mount="$(docker_mount_src "$PAM_DIR")"
out_mount="$(docker_mount_src "$OUTPUT_DIR")"
inner_mount="$(docker_mount_src "$SCRIPT_DIR/pam-doctor-sanitizers-inner.sh")"

echo "[$KEYWORD] sanitizers image=$IMAGE"
echo "[$KEYWORD] output=$OUTPUT_DIR"
echo "[$KEYWORD] ASan+UBSan on existing harnesses; valgrind=$([ "$SKIP_VALGRIND" -eq 0 ] && echo on || echo off)"

rc=0
(
  export MSYS_NO_PATHCONV=1
  docker run --rm \
    --cap-add=SYS_PTRACE \
    --security-opt seccomp=unconfined \
    -e "PAM_DOCTOR_IMAGE=$IMAGE" \
    -e "EZKEY_PAM_SANITIZERS_SKIP_VALGRIND=$SKIP_VALGRIND" \
    -v "${pam_mount}:/src:ro" \
    -v "${out_mount}:/out" \
    -v "${inner_mount}:/inner.sh:ro" \
    "$IMAGE" \
    bash /inner.sh
) || rc=$?

echo "[$KEYWORD] sanitizers done (exit ${rc})."
echo "  Summary: $OUTPUT_DIR/summary.md"
exit "$rc"
