#!/usr/bin/env bash
# Reliable commit helper for this Windows workstation. It exists because committing from the agent
# shell repeatedly fails for avoidable reasons:
#   - PowerShell parses `<` (Cursor's injected attribution trailer) as redirection;
#   - multi-layer quoting (PowerShell -> `bash -lc '...'` -> `git commit`) mangles any of () " ' # ! < >;
#   - conventional prefixes like `docs(product):` ALWAYS contain parentheses, so inline `-m` is fragile.
#
# Root cause: the commit *message text* crossing shell boundaries. The fix: never let it. Pass only a
# file path (no special characters), or — best — let this script read a fixed message file.
#
# CANONICAL ZERO-ARGUMENT FLOW (recommended for cold agents, Cursor or Copilot):
#   1. Write the full commit message (subject line + optional blank line + body) to:
#        .ezkey/commit-msg.txt
#   2. Run, from a Windows-hosted shell (PowerShell/cmd) the SINGLE constant command:
#        & "C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-commit.sh'
#      or, already inside Git Bash:
#        ./scripts/git-commit.sh
#   The message text never crosses a shell boundary, so escaping is impossible to get wrong.
#   On success the message file is removed so a stale message is never reused.
#
# POWER-USER FORMS (still supported):
#   ./scripts/git-commit.sh -F path/to/message.txt   # robust: only a path crosses boundaries
#   ./scripts/git-commit.sh -m "subject"             # avoid from PowerShell: () in prefixes break it
#
# Do NOT use bare PowerShell `git commit` (trailer/`<` and HEREDOC failures), and do NOT use WSL bash
# (`C:\Windows\System32\bash.exe`); use Git Bash (`C:\Program Files\Git\bin\bash.exe`).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MSG_FILE="$ROOT/.ezkey/commit-msg.txt"

if [[ $# -eq 0 ]]; then
  if [[ ! -s "$MSG_FILE" ]]; then
    echo "git-commit.sh: no commit message found." >&2
    echo "Canonical flow: write your message to .ezkey/commit-msg.txt, then re-run this script." >&2
    echo "Or pass a message explicitly:  -F <message-file>  |  -m \"subject\"" >&2
    exit 1
  fi
  git commit -F "$MSG_FILE"
  rm -f "$MSG_FILE"
  echo "git-commit.sh: committed from .ezkey/commit-msg.txt (message file removed)."
  exit 0
fi

git commit "$@"
