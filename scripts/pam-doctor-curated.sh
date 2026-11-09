#!/usr/bin/env bash
#
# pam-doctor-curated — punctual C static-analysis hygiene pass for ezkey-pam.
# clang-format (Google) drift + cppcheck (narrow) + clang-tidy (narrow) → shortlist.
# Report-only. Not a CI gate. Linux reference; macOS install work is out of scope.
#
# Usage (Git Bash on Windows, Linux, or macOS), from repo root:
#   ./scripts/pam-doctor-curated.sh
#   ./scripts/pam-doctor-curated.sh --offline
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

KEYWORD="pam-doctor-curated"
OUTPUT_DIR="$REPO_ROOT/logs/pam-doctor"
RAW_DIR="$OUTPUT_DIR/raw"
PAM_DIR="$REPO_ROOT/ezkey-pam"
IMAGE="${EZKEY_PAM_DOCTOR_IMAGE:-ubuntu:24.04}"
OFFLINE=0

usage() {
  cat <<'EOF'
Usage: ./scripts/pam-doctor-curated.sh [--offline]

  --offline   Re-curate logs/pam-doctor/raw without running the container

Outputs under logs/pam-doctor/ (gitignored). Keyword: pam-doctor-curated
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --offline)
      OFFLINE=1
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

command -v node >/dev/null 2>&1 || {
  echo "Node.js (node) not found on PATH (needed for the curator)." >&2
  exit 1
}

mkdir -p "$RAW_DIR"

docker_mount_src() {
  local path="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$path"
  else
    echo "$path" | sed -e 's|^/c/|C:/|' -e 's|^/mnt/c/|C:/|'
  fi
}

if [[ "$OFFLINE" -eq 0 ]]; then
  command -v docker >/dev/null 2>&1 || {
    echo "[$KEYWORD] ERROR: docker is not available." >&2
    echo "The Linux reference scan runs in ${IMAGE} (PAM headers + clang + cppcheck)." >&2
    exit 1
  }
  [[ -f "$PAM_DIR/.clang-format" && -f "$PAM_DIR/.clang-tidy" ]] || {
    echo "[$KEYWORD] ERROR: ezkey-pam/.clang-format or .clang-tidy is missing." >&2
    exit 1
  }

  echo "[$KEYWORD] image=$IMAGE"
  echo "[$KEYWORD] output=$OUTPUT_DIR"
  rm -f "$RAW_DIR"/* 2>/dev/null || true

  pam_mount="$(docker_mount_src "$PAM_DIR")"
  raw_mount="$(docker_mount_src "$RAW_DIR")"
  inner_mount="$(docker_mount_src "$SCRIPT_DIR/pam-doctor-inner.sh")"

  echo "[$KEYWORD] scanning ezkey-pam/src in Docker (Linux headers)…"
  (
    export MSYS_NO_PATHCONV=1
    docker run --rm \
      -e "PAM_DOCTOR_IMAGE=$IMAGE" \
      -v "${pam_mount}:/src:ro" \
      -v "${raw_mount}:/out" \
      -v "${inner_mount}:/inner.sh:ro" \
      -w /src \
      "$IMAGE" \
      bash /inner.sh
  )
else
  echo "[$KEYWORD] offline: re-curating $RAW_DIR"
  [[ -f "$RAW_DIR/versions.txt" || -f "$RAW_DIR/clang-tidy.txt" || -f "$RAW_DIR/cppcheck.txt" ]] || {
    echo "[$KEYWORD] ERROR: no raw inputs in $RAW_DIR. Run without --offline first." >&2
    exit 1
  }
fi

echo "[$KEYWORD] curating…"
node "$SCRIPT_DIR/pam-doctor-curated.mjs"

echo "[$KEYWORD] done."
echo "  Curated Markdown: $OUTPUT_DIR/pam-doctor.curated.md"
echo "  Curated JSON:     $OUTPUT_DIR/pam-doctor.curated.json"
