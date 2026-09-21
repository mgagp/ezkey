#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

UPSTREAM="${1:-origin/main}"
REMOTE="${UPSTREAM%%/*}"
REMOTE_BRANCH="${UPSTREAM#*/}"

fail() {
  echo "git-ff-only.sh: $*" >&2
  exit 1
}

run_step() {
  local label="$1"
  shift
  if ! "$@"; then
    fail "step failed (${label}): $*"
  fi
}

if [[ "$REMOTE" == "$UPSTREAM" || "$REMOTE_BRANCH" == "$UPSTREAM" ]]; then
  fail "upstream must use remote/branch form (example: origin/main). Received: ${UPSTREAM}"
fi

if ! TOP_LEVEL="$(git rev-parse --show-toplevel 2>/dev/null)"; then
  fail "not inside a git repository"
fi

BRANCH_NAME="$(git branch --show-current)"
if [[ -z "$BRANCH_NAME" ]]; then
  fail "detached HEAD is not supported for this workflow"
fi

# Refuse to proceed on any staged or unstaged change.
if ! git diff --quiet; then
  fail "working tree is dirty (unstaged changes)"
fi
if ! git diff --cached --quiet; then
  fail "working tree is dirty (staged changes)"
fi

OLD_HEAD="$(git rev-parse --short=12 HEAD)"

run_step "fetch upstream" git fetch --prune "$REMOTE" "$REMOTE_BRANCH"

if ! git rev-parse --verify --quiet "${UPSTREAM}^{commit}" >/dev/null; then
  fail "upstream ref not found after fetch: ${UPSTREAM}"
fi

if ! git merge-base --is-ancestor HEAD "$UPSTREAM"; then
  fail "local and upstream have diverged; fast-forward is not possible"
fi

run_step "fast-forward merge" git merge --ff-only "$UPSTREAM"

NEW_HEAD="$(git rev-parse --short=12 HEAD)"
FAST_FORWARD_APPLIED="yes"
if [[ "$OLD_HEAD" == "$NEW_HEAD" ]]; then
  FAST_FORWARD_APPLIED="no (already up to date)"
fi

echo
echo "branch: ${BRANCH_NAME}"
echo "upstream: ${UPSTREAM}"
echo "fast_forward_applied: ${FAST_FORWARD_APPLIED}"
echo "head: ${OLD_HEAD} -> ${NEW_HEAD}"
echo
git status -sb
echo "---"
git log --oneline --decorate -n 5
