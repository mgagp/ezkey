#!/usr/bin/env bash
# Reliable pull-request helper for this Windows workstation. It exists because creating a PR from the
# agent shell repeatedly fails for the same class of reasons as commits:
#   - multi-layer quoting (PowerShell -> `bash -lc '...'` -> `gh pr create`) mangles any of () " ' # ! < >;
#   - conventional titles like `fix(security): ...` ALWAYS contain parentheses;
#   - HEREDOC / inline `--body "$(cat <<'EOF' …)"` patterns break when PowerShell re-parses `$()`, quotes,
#     or newlines before Git Bash ever sees them.
#
# Root cause: the PR *title and body text* crossing shell boundaries. The fix: never let them. Write
# the text to files with the editor/tools, then pass only file paths into this script.
#
# CANONICAL ZERO-ARGUMENT FLOW (recommended for cold agents, Cursor or Copilot):
#   1. Ensure the branch is pushed (`git push -u origin HEAD` if needed).
#   2. Write the PR title (single line) to:
#        .ezkey/pr-title.txt
#   3. Write the full PR body (Markdown) to:
#        .ezkey/pr-body.md
#   4. Run, from a Windows-hosted shell (PowerShell/cmd) the SINGLE constant command:
#        & "C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-pr.sh'
#      or, already inside Git Bash:
#        ./scripts/git-pr.sh
#   The title/body text never crosses a shell boundary, so escaping is impossible to get wrong.
#   On success the title/body files are removed so stale content is never reused.
#
# POWER-USER FORMS (still supported):
#   ./scripts/git-pr.sh --title-file path --body-file path
#   ./scripts/git-pr.sh --draft
#   ./scripts/git-pr.sh --base main
#   Extra unknown flags are forwarded to `gh pr create` after the title/body arguments.
#
# Do NOT use bare PowerShell `gh pr create` with inline `--title` / HEREDOC `--body`, and do NOT use
# WSL bash (`C:\Windows\System32\bash.exe`); use Git Bash (`C:\Program Files\Git\bin\bash.exe`).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TITLE_FILE="$ROOT/.ezkey/pr-title.txt"
BODY_FILE="$ROOT/.ezkey/pr-body.md"
REMOVE_DEFAULT_FILES=0
DRAFT_ARGS=()
FORWARD_ARGS=()

usage() {
  cat >&2 <<'EOF'
git-pr.sh: create a GitHub pull request without shell-escaping title/body text.

Canonical flow:
  1. Write title to .ezkey/pr-title.txt
  2. Write body to .ezkey/pr-body.md
  3. Run: ./scripts/git-pr.sh

Options:
  --title-file <path>   Title file (default: .ezkey/pr-title.txt)
  --body-file <path>    Body Markdown file (default: .ezkey/pr-body.md)
  --draft               Create as draft PR
  --base <branch>       Base branch for the PR
  -h, --help            Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --title-file)
      TITLE_FILE="${2:-}"
      if [[ -z "$TITLE_FILE" ]]; then
        echo "git-pr.sh: --title-file requires a path." >&2
        exit 1
      fi
      shift 2
      ;;
    --body-file)
      BODY_FILE="${2:-}"
      if [[ -z "$BODY_FILE" ]]; then
        echo "git-pr.sh: --body-file requires a path." >&2
        exit 1
      fi
      shift 2
      ;;
    --draft)
      DRAFT_ARGS+=(--draft)
      shift
      ;;
    --base)
      if [[ -z "${2:-}" ]]; then
        echo "git-pr.sh: --base requires a branch name." >&2
        exit 1
      fi
      FORWARD_ARGS+=(--base "$2")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      FORWARD_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ "$TITLE_FILE" == "$ROOT/.ezkey/pr-title.txt" && "$BODY_FILE" == "$ROOT/.ezkey/pr-body.md" ]]; then
  REMOVE_DEFAULT_FILES=1
fi

if [[ ! -s "$TITLE_FILE" ]]; then
  echo "git-pr.sh: no PR title found at: $TITLE_FILE" >&2
  echo "Canonical flow: write the title to .ezkey/pr-title.txt, then re-run this script." >&2
  exit 1
fi

if [[ ! -s "$BODY_FILE" ]]; then
  echo "git-pr.sh: no PR body found at: $BODY_FILE" >&2
  echo "Canonical flow: write the body to .ezkey/pr-body.md, then re-run this script." >&2
  exit 1
fi

# First non-empty line is the title; keep body Markdown as written.
TITLE="$(tr -d '\r' <"$TITLE_FILE" | sed '/^[[:space:]]*$/d' | head -n 1 || true)"
if [[ -z "$TITLE" ]]; then
  echo "git-pr.sh: title file is empty after trimming: $TITLE_FILE" >&2
  exit 1
fi

GH_BIN=""
if command -v gh >/dev/null 2>&1; then
  GH_BIN="$(command -v gh)"
elif [[ -x "/c/Program Files/GitHub CLI/gh.exe" ]]; then
  GH_BIN="/c/Program Files/GitHub CLI/gh.exe"
elif [[ -x "/mnt/c/Program Files/GitHub CLI/gh.exe" ]]; then
  GH_BIN="/mnt/c/Program Files/GitHub CLI/gh.exe"
else
  echo "git-pr.sh: gh not found on PATH (install GitHub CLI)." >&2
  exit 1
fi

"$GH_BIN" pr create --title "$TITLE" --body-file "$BODY_FILE" "${DRAFT_ARGS[@]}" "${FORWARD_ARGS[@]}"

if [[ "$REMOVE_DEFAULT_FILES" -eq 1 ]]; then
  rm -f "$ROOT/.ezkey/pr-title.txt" "$ROOT/.ezkey/pr-body.md"
  echo "git-pr.sh: created PR from .ezkey/pr-title.txt + .ezkey/pr-body.md (message files removed)."
else
  echo "git-pr.sh: created PR from title-file=$TITLE_FILE body-file=$BODY_FILE."
fi
