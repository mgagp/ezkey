#!/usr/bin/env bash
# Fixture tests for AL2023 release / updateinfo parsing. No SSH, no dnf, no host.
#   ./scripts/lightsail/os-patch-check-parse.test.sh
# Also invoked from target-aware.test.sh (the lightsail CI entry point).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=os-patch-parse.sh
source "${ROOT}/scripts/lightsail/os-patch-parse.sh"

failures=0

assert_eq() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "FAIL: ${label}: got '${actual}' expected '${expected}'" >&2
    failures=$((failures + 1))
  else
    echo "PASS: ${label}"
  fi
}

parse_release() {
  local rc="$1"
  local installed="$2"
  local text="$3"
  printf '%s\n' "${text}" | os_patch_parse_check_release_update "${rc}" "${installed}"
}

parse_summary() {
  local rc="$1"
  local text="$2"
  printf '%s\n' "${text}" | os_patch_parse_updateinfo_summary "${rc}"
}

parse_list() {
  local rc="$1"
  local text="$2"
  printf '%s\n' "${text}" | os_patch_parse_updateinfo_list "${rc}"
}

# --- installed releasever ---------------------------------------------------

assert_eq "bare releasever file" \
  "$(printf '%s\n' '2023.12.20261102' | os_patch_extract_releasever)" \
  "2023.12.20261102"

assert_eq "system-release line" \
  "$(printf '%s\n' 'Amazon Linux release 2023.12.20261102 (Amazon Linux)' | os_patch_extract_releasever)" \
  "2023.12.20261102"

assert_eq "releasever without trailing newline" \
  "$(printf '%s' '2023.12.20261102' | os_patch_extract_releasever)" \
  "2023.12.20261102"

assert_eq "missing releasever" \
  "$(printf '%s\n' '(no releasever file)' | os_patch_extract_releasever)" \
  ""

# --- check-release-update: newer releases -----------------------------------

# Six newer releases, oldest first, up to a 20261120 placeholder. Intermediate
# ids are fixture tokens in the documented dnf shape, not a release ledger.
six_newer="$(cat <<'EOF'
WARNING:
  A newer release of "Amazon Linux" is available.

  Available Versions:

  Version 2023.12.20261104:
    Run the following command to upgrade to 2023.12.20261104:

      dnf upgrade --releasever=2023.12.20261104

    Release notes:
     https://docs.aws.amazon.com/linux/al2023/release-notes/relnotes-2023.12.20261104.html

  Version 2023.12.20261106:
    Run the following command to upgrade to 2023.12.20261106:

      dnf upgrade --releasever=2023.12.20261106

  Version 2023.12.20261110:
    Run the following command to upgrade to 2023.12.20261110:

      dnf upgrade --releasever=2023.12.20261110

  Version 2023.12.20261114:
    Run the following command to upgrade to 2023.12.20261114:

      dnf upgrade --releasever=2023.12.20261114

  Version 2023.12.20261118:
    Run the following command to upgrade to 2023.12.20261118:

      dnf upgrade --releasever=2023.12.20261118

  Version 2023.12.20261120:
    Run the following command to upgrade to 2023.12.20261120:

      dnf upgrade --releasever=2023.12.20261120

    Release notes:
     https://docs.aws.amazon.com/linux/al2023/release-notes/relnotes-2023.12.20261120.html
EOF
)"

assert_eq "six newer releases, latest is last and highest" \
  "$(parse_release 0 "2023.12.20261102" "${six_newer}")" \
  "status=update latest=2023.12.20261120 behind=6 versions=2023.12.20261104,2023.12.20261106,2023.12.20261110,2023.12.20261114,2023.12.20261118,2023.12.20261120"

# Version lines and the matching --releasever= commands must not double-count.
assert_eq "version lines win over duplicate --releasever commands" \
  "$(os_patch_kv_get "$(parse_release 0 "2023.12.20261102" "${six_newer}")" behind)" \
  "6"

# Newest printed first still selects the greatest releasever (sort -V).
reversed="$(cat <<'EOF'
WARNING:
  A newer release of "Amazon Linux" is available.

  Available Versions:

  Version 2023.10.20260105:
    Run the following command to upgrade to 2023.10.20260105:

      dnf upgrade --releasever=2023.10.20260105

  Version 2023.9.20260105:
    Run the following command to upgrade to 2023.9.20260105:

      dnf upgrade --releasever=2023.9.20260105
EOF
)"

assert_eq "sort -V picks 2023.10 over 2023.9 when newest is printed first" \
  "$(parse_release 0 "2023.8.20251201" "${reversed}")" \
  "status=update latest=2023.10.20260105 behind=2 versions=2023.9.20260105,2023.10.20260105"

# Fallback when only the upgrade command is present (no Version line).
commands_only="$(cat <<'EOF'
      dnf upgrade --releasever=2023.12.20261112
      dnf upgrade --releasever=2023.12.20261120
EOF
)"

assert_eq "fallback to --releasever= commands" \
  "$(parse_release 0 "2023.12.20261102" "${commands_only}")" \
  "status=update latest=2023.12.20261120 behind=2 versions=2023.12.20261112,2023.12.20261120"

# Non-zero dnf status does not hide versions that did parse.
assert_eq "non-zero exit still reports parsed versions" \
  "$(parse_release 1 "2023.12.20261102" "${commands_only}")" \
  "status=update latest=2023.12.20261120 behind=2 versions=2023.12.20261112,2023.12.20261120"

# Do not evaluate fixture text. A command-substitution lookalike is not a version.
hostile=$'Version $(echo pwned):\n      dnf upgrade --releasever=$(reboot)'
hostile_out="$(parse_release 0 "2023.12.20261102" "${hostile}")"
assert_eq "hostile version text is not executed" \
  "${hostile_out}" \
  "status=current latest=2023.12.20261102 behind=0 versions="
if [[ "${hostile_out}" == *pwned* ]]; then
  echo "FAIL: parser evaluated fixture text" >&2
  failures=$((failures + 1))
else
  echo "PASS: parser did not evaluate fixture text"
fi

# --- check-release-update: no newer release ---------------------------------

assert_eq "empty output exit 0 is current" \
  "$(parse_release 0 "2023.12.20261120" "")" \
  "status=current latest=2023.12.20261120 behind=0 versions="

assert_eq "empty output exit 0 without installed leaves latest unset" \
  "$(parse_release 0 "" "")" \
  "status=current latest=- behind=0 versions="

no_newer=$'No newer release of Amazon Linux is available.'
assert_eq "explicit no-newer phrase exit 0 is current" \
  "$(parse_release 0 "2023.12.20261120" "${no_newer}")" \
  "status=current latest=2023.12.20261120 behind=0 versions="

assert_eq "explicit no-newer phrase exit 1 is current" \
  "$(parse_release 1 "2023.12.20261120" "${no_newer}")" \
  "status=current latest=2023.12.20261120 behind=0 versions="

assert_eq "system is up to date exit 0 is current" \
  "$(parse_release 0 "2023.12.20261120" "Your system is up to date.")" \
  "status=current latest=2023.12.20261120 behind=0 versions="

# Banner we could not turn into versions is unknown, not a quiet "current".
banner_only="$(cat <<'EOF'
WARNING:
  A newer release of "Amazon Linux" is available.

  Available Versions:
EOF
)"

assert_eq "newer-release banner without versions is unknown" \
  "$(parse_release 0 "2023.12.20261102" "${banner_only}")" \
  "status=unknown latest=- behind=- versions="

assert_eq "no such command is unknown" \
  "$(parse_release 1 "" "No such command: check-release-update.")" \
  "status=unknown latest=- behind=- versions="

assert_eq "empty output non-zero is unknown" \
  "$(parse_release 1 "2023.12.20261102" "")" \
  "status=unknown latest=- behind=- versions="

# --- updateinfo summary -------------------------------------------------------

# Critical row omitted (dnf skips zeros). The total Security row must not
# be counted as Important. Moderate/Medium are ignored.
summary_important="$(cat <<'EOF'
Last metadata expiration check: 0:00:01 ago on Sun Nov 22 2026.
Updates Information Summary: available
    20 Security notice(s)
        11 Important Security notice(s)
         9 Moderate Security notice(s)
EOF
)"

assert_eq "summary: omitted Critical is 0, Important is 11, total is not Important" \
  "$(parse_summary 0 "${summary_important}")" \
  "status=ok critical=0 important=11"

summary_both="$(cat <<'EOF'
Updates Information Summary: available
  3 Security notice(s)
      1 Critical Security notice(s)
      2 Important Security notice(s)
EOF
)"

assert_eq "summary: both severities" \
  "$(parse_summary 0 "${summary_both}")" \
  "status=ok critical=1 important=2"

assert_eq "summary header with no severity rows is zeros" \
  "$(parse_summary 0 "Updates Information Summary: available")" \
  "status=ok critical=0 important=0"

assert_eq "summary exit 0 with no header is zeros" \
  "$(parse_summary 0 "")" \
  "status=ok critical=0 important=0"

assert_eq "summary download failure is unknown" \
  "$(parse_summary 1 "Error: Failed to download metadata for repo")" \
  "status=unknown critical=- important=-"

assert_eq "summary non-zero without header is unknown" \
  "$(parse_summary 1 "")" \
  "status=unknown critical=- important=-"

# --- updateinfo list ----------------------------------------------------------

list_dup="$(cat <<'EOF'
Last metadata expiration check: 0:00:01 ago on Sun Nov 22 2026.
ALAS2023-2026-1000 Important/Sec. example-lib-1.0.0
ALAS2023-2026-1000 Important/Sec. example-lib-extra-1.0.0
ALAS2023-2026-1001 Important/Sec. example-openssl-1.0.0
ALAS2023-2026-2000 Critical/Sec. example-kernel-1.0.0
ALAS2023-2026-2000 Critical/Sec. example-kernel-core-1.0.0
ALAS2023-2026-3000 Moderate/Sec. example-docs-1.0.0
EOF
)"

assert_eq "list counts distinct advisory ids, not package rows" \
  "$(parse_list 0 "${list_dup}")" \
  "status=ok critical=1 important=2"

assert_eq "list exit 0 with no advisory rows is zeros" \
  "$(parse_list 0 "Last metadata expiration check: 0:00:01 ago.")" \
  "status=ok critical=0 important=0"

assert_eq "list non-zero is unknown even with rows" \
  "$(parse_list 1 "${list_dup}")" \
  "status=unknown critical=- important=-"

# --- summary line -------------------------------------------------------------

incident="$(parse_release 0 "2023.12.20261102" "${six_newer}")"
incident_latest="$(os_patch_kv_get "${incident}" latest)"
incident_behind="$(os_patch_kv_get "${incident}" behind)"
incident_counts="$(parse_summary 0 "${summary_important}")"
incident_critical="$(os_patch_kv_get "${incident_counts}" critical)"
incident_important="$(os_patch_kv_get "${incident_counts}" important)"

assert_eq "incident-shaped summary line" \
  "$(os_patch_format_summary_line update "2023.12.20261102" "${incident_latest}" "${incident_behind}" ok "${incident_critical}" "${incident_important}")" \
  "RELEASE_UPDATE=yes INSTALLED=2023.12.20261102 LATEST=2023.12.20261120 BEHIND=6 CRITICAL=0 IMPORTANT=11"

assert_eq "current summary line" \
  "$(os_patch_format_summary_line current "2023.12.20261120" "2023.12.20261120" 0 ok 0 0)" \
  "RELEASE_UPDATE=no INSTALLED=2023.12.20261120 LATEST=2023.12.20261120 BEHIND=0 CRITICAL=0 IMPORTANT=0"

assert_eq "yes with unusable counts keeps dash, not zero" \
  "$(os_patch_format_summary_line update "2023.12.20261102" "2023.12.20261120" 6 unknown - -)" \
  "RELEASE_UPDATE=yes INSTALLED=2023.12.20261102 LATEST=2023.12.20261120 BEHIND=6 CRITICAL=- IMPORTANT=-"

assert_eq "unknown probe blanks latest and counts" \
  "$(os_patch_format_summary_line unknown "2023.12.20261102" - - unknown - -)" \
  "RELEASE_UPDATE=unknown INSTALLED=2023.12.20261102 LATEST=- BEHIND=- CRITICAL=- IMPORTANT=-"

assert_eq "latest keyword is not a releasever" \
  "$(os_patch_is_releasever latest && echo yes || echo no)" \
  "no"

if [[ "${failures}" -ne 0 ]]; then
  echo "os-patch-check-parse.test.sh FAILED (${failures})" >&2
  exit 1
fi
echo "os-patch-check-parse.test.sh passed"
