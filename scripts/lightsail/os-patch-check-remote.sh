#!/usr/bin/env bash
# Read-only probe executed ON the Lightsail host via `ssh host bash -s`.
# os-patch-check.sh prepends os-patch-parse.sh so the functions exist here.
# Direct runs source the sibling parser. Never upgrades, reboots, or edits config.
#
# shellcheck shell=bash
set -euo pipefail

if ! declare -F os_patch_format_summary_line >/dev/null 2>&1; then
  _os_patch_parse_lib="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/os-patch-parse.sh"
  # shellcheck source=os-patch-parse.sh
  source "${_os_patch_parse_lib}"
fi

section() {
  printf '\n==> %s\n' "$1"
}

installed=""

section "system-release"
if [[ -r /etc/system-release ]]; then
  cat /etc/system-release
  installed="$(os_patch_extract_releasever < /etc/system-release)"
else
  echo "(missing /etc/system-release)"
fi

section "dnf releasever (locked)"
if [[ -r /etc/dnf/vars/releasever ]]; then
  cat /etc/dnf/vars/releasever
  locked="$(os_patch_extract_releasever < /etc/dnf/vars/releasever)"
  if [[ -n "${locked}" ]]; then
    installed="${locked}"
  fi
else
  echo "(no /etc/dnf/vars/releasever — dnf may use image default)"
fi

section "dnf check-release-update"
# Read-only probe. Exit 0 with no Version lines means no newer release.
# A non-zero exit with an explicit "no newer release" phrase is the same.
# Any other non-zero exit without versions is unknown (not "up to date").
set +e
cru_out="$(sudo dnf check-release-update 2>&1)"
cru_rc=$?
set -e
if [[ -n "${cru_out}" ]]; then
  printf '%s\n' "${cru_out}"
fi
echo "(dnf check-release-update exit=${cru_rc})"

parse_line="$(printf '%s\n' "${cru_out}" | os_patch_parse_check_release_update "${cru_rc}" "${installed}")"
release_state="$(os_patch_kv_get "${parse_line}" status)"
latest="$(os_patch_kv_get "${parse_line}" latest)"
behind="$(os_patch_kv_get "${parse_line}" behind)"
versions="$(os_patch_kv_get "${parse_line}" versions)"

section "release comparison (installed vs latest)"
echo "installed=${installed:--}"
echo "latest=${latest}"
echo "releases_behind=${behind}"
if [[ "${release_state}" == update && -n "${versions}" ]]; then
  echo "newer_releases=${versions//,/ }"
elif [[ "${release_state}" == current ]]; then
  echo "(no newer AL2023 release)"
else
  echo "(check-release-update could not be parsed; not treating this as up to date)"
fi

section "security updateinfo (Critical, Important)"
# Cumulative advisories on the latest dated release. The locked repo is immutable,
# so updateinfo without --releasever stays empty while newer releases exist.
query_mode=""
query_rel=""
if [[ "${release_state}" == update ]] && os_patch_is_releasever "${latest}"; then
  query_mode="pinned"
  query_rel="${latest}"
  echo "query: dnf --releasever=${query_rel} updateinfo summary + list (read-only; not the locked repo)"
elif [[ "${release_state}" == current ]]; then
  query_mode="locked"
  echo "query: dnf updateinfo summary + list (read-only; no newer release, locked repo is current)"
else
  echo "(skipped — latest release unknown; not querying the locked repo, which hides newer advisories)"
fi

counts_state="unknown"
critical="-"
important="-"

if [[ -n "${query_mode}" ]]; then
  set +e
  if [[ "${query_mode}" == pinned ]]; then
    summary_out="$(sudo dnf --releasever="${query_rel}" updateinfo summary 2>&1)"
  else
    summary_out="$(sudo dnf updateinfo summary 2>&1)"
  fi
  summary_rc=$?
  set -e
  if [[ -n "${summary_out}" ]]; then
    printf '%s\n' "${summary_out}"
  fi
  if [[ "${query_mode}" == pinned ]]; then
    echo "(dnf --releasever=${query_rel} updateinfo summary exit=${summary_rc})"
  else
    echo "(dnf updateinfo summary exit=${summary_rc})"
  fi
  summary_line="$(printf '%s\n' "${summary_out}" | os_patch_parse_updateinfo_summary "${summary_rc}")"

  # One severity per dnf call. dnf rejects a comma-separated --sec-severity list.
  list_out=""
  list_rc=0
  set +e
  for sev in Critical Important; do
    echo "-- sec-severity=${sev}"
    if [[ "${query_mode}" == pinned ]]; then
      one="$(sudo dnf --releasever="${query_rel}" updateinfo list --sec-severity="${sev}" 2>&1)"
    else
      one="$(sudo dnf updateinfo list --sec-severity="${sev}" 2>&1)"
    fi
    one_rc=$?
    if [[ -n "${one}" ]]; then
      printf '%s\n' "${one}"
    fi
    if [[ "${query_mode}" == pinned ]]; then
      echo "(dnf --releasever=${query_rel} updateinfo list --sec-severity=${sev} exit=${one_rc})"
    else
      echo "(dnf updateinfo list --sec-severity=${sev} exit=${one_rc})"
    fi
    list_out+="${one}"$'\n'
    if [[ "${one_rc}" -ne 0 ]]; then
      list_rc="${one_rc}"
    fi
  done
  set -e

  sum_status="$(os_patch_kv_get "${summary_line}" status)"
  if [[ "${sum_status}" == ok ]]; then
    counts_state="ok"
    critical="$(os_patch_kv_get "${summary_line}" critical)"
    important="$(os_patch_kv_get "${summary_line}" important)"
  else
    list_line="$(printf '%s\n' "${list_out}" | os_patch_parse_updateinfo_list "${list_rc}")"
    list_status="$(os_patch_kv_get "${list_line}" status)"
    if [[ "${list_status}" == ok ]]; then
      counts_state="ok"
      critical="$(os_patch_kv_get "${list_line}" critical)"
      important="$(os_patch_kv_get "${list_line}" important)"
      echo "(summary unusable; Critical/Important counts are distinct advisory ids from updateinfo list)"
    else
      echo "(updateinfo summary and list unusable; counts left unknown)"
    fi
  fi
fi

section "needs-restarting -r"
# needs-restarting -r: exit 0 = no reboot required; exit 1 = reboot required.
if command -v needs-restarting >/dev/null 2>&1; then
  set +e
  sudo needs-restarting -r
  nr_rc=$?
  set -e
  if [[ "${nr_rc}" -eq 1 ]]; then
    echo "(needs-restarting -r exit=1 — reboot is required)"
  elif [[ "${nr_rc}" -eq 0 ]]; then
    echo "(needs-restarting -r exit=0 — no reboot required)"
  else
    echo "(needs-restarting -r exit=${nr_rc} — unexpected; treat as inconclusive)"
  fi
else
  echo "(needs-restarting not installed — skip; do not install from this script)"
fi

section "uname -r"
uname -r

section "docker ps"
if command -v docker >/dev/null 2>&1; then
  docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'
else
  echo "(docker not on PATH for this user)"
fi

section "summary"
os_patch_format_summary_line \
  "${release_state}" "${installed}" "${latest}" "${behind}" \
  "${counts_state}" "${critical}" "${important}"

section "os-patch-check complete (read-only)"
