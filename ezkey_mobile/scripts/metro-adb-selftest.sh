#!/usr/bin/env bash
# Self-test for Metro stale-worktree path extraction + normalization
# (scripts/lib/metro-adb.sh). No live Metro / adb required.
#
# Usage (from ezkey_mobile/):
#   ./scripts/metro-adb-selftest.sh
#
# Wired into CI js-validate alongside assert-no-gradle-daemon-jvm --self-test.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/metro-adb.sh
source "${SCRIPT_DIR}/lib/metro-adb.sh"

pass=0
fail=0

assert_eq() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo "  OK  ${label}"
    pass=$((pass + 1))
  else
    echo "  FAIL ${label}" >&2
    echo "       expected: ${expected}" >&2
    echo "       actual:   ${actual}" >&2
    fail=$((fail + 1))
  fi
}

assert_true() {
  local label="$1"
  shift
  if "$@"; then
    echo "  OK  ${label}"
    pass=$((pass + 1))
  else
    echo "  FAIL ${label}" >&2
    fail=$((fail + 1))
  fi
}

assert_false() {
  local label="$1"
  shift
  if "$@"; then
    echo "  FAIL ${label} (expected false)" >&2
    fail=$((fail + 1))
  else
    echo "  OK  ${label}"
    pass=$((pass + 1))
  fi
}

echo "[metro-adb-selftest] path extraction from command lines"

WIN_CMD='node.exe C:\w\p\ezkey_mobile\node_modules\react-native\cli.js start --port 8084'
hint="$(ezkey_metro_project_hint_from_cmdline "$WIN_CMD")"
assert_eq "Windows cmdline → C:\\w\\p\\ezkey_mobile" 'C:\w\p\ezkey_mobile' "$hint"

WIN_CMD2='"C:\Program Files\nodejs\node.exe" "C:\w\r\ezkey_mobile\node_modules\react-native\cli.js" start'
hint="$(ezkey_metro_project_hint_from_cmdline "$WIN_CMD2")"
assert_eq "Windows quoted cmdline → C:\\w\\r\\ezkey_mobile" 'C:\w\r\ezkey_mobile' "$hint"

POSIX_CMD='/usr/bin/node /c/w/r/ezkey_mobile/node_modules/react-native/cli.js start --port 8081'
hint="$(ezkey_metro_project_hint_from_cmdline "$POSIX_CMD")"
assert_eq "POSIX cmdline → /c/w/r/ezkey_mobile" '/c/w/r/ezkey_mobile' "$hint"

EMPTY="$(ezkey_metro_project_hint_from_cmdline 'node.exe C:\Tools\other\cli.js start' || true)"
assert_eq "non-ezkey cmdline → empty" '' "$EMPTY"

echo "[metro-adb-selftest] path normalization (Git Bash vs Windows)"

norm_win="$(ezkey_norm_path 'C:\w\r\ezkey_mobile')"
norm_git="$(ezkey_norm_path '/c/w/r/ezkey_mobile')"
# Without cygpath (Linux CI), /c/w/... is rewritten to c:/w/... via lightweight rule.
assert_eq "Windows path normalizes to forward slashes" 'c:/w/r/ezkey_mobile' "$norm_win"
assert_eq "Git Bash /c/... matches Windows form" "$norm_win" "$norm_git"

assert_true "same worktree: exact" ezkey_metro_paths_same_worktree 'c:/w/r/ezkey_mobile' 'c:/w/r/ezkey_mobile'
assert_true "same worktree: child" ezkey_metro_paths_same_worktree 'c:/w/r/ezkey_mobile' 'c:/w/r/ezkey_mobile/node_modules'
assert_false "different worktree" ezkey_metro_paths_same_worktree 'c:/w/r/ezkey_mobile' 'c:/w/p/ezkey_mobile'

assert_true "node.exe detection" ezkey_looks_like_node_exe 'C:\Program Files\nodejs\node.exe'
assert_false "project root is not node.exe" ezkey_looks_like_node_exe 'C:\w\r\ezkey_mobile'

echo "[metro-adb-selftest] ${pass} passed, ${fail} failed"
if ((fail > 0)); then
  exit 1
fi
echo "[metro-adb-selftest] OK"
exit 0
