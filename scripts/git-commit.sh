#!/usr/bin/env bash
# Commit staged changes from Git Bash (avoids Windows PowerShell + Cursor trailer injection).
# Usage: ./scripts/git-commit.sh -m "subject"  OR  ./scripts/git-commit.sh -F message.txt
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ $# -lt 1 ]]; then
  echo "Usage: $0 -m \"message\" | -F message-file" >&2
  exit 1
fi
git commit "$@"
