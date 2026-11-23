#!/usr/bin/env bash
# Parsers for AL2023 `dnf check-release-update` and `dnf updateinfo` text.
# Sourced by os-patch-check-remote.sh (and prepended to the SSH payload) and by
# fixture tests. No SSH, no dnf, no host mutation.
#
# shellcheck shell=bash

if [[ -n "${_EZKEY_OS_PATCH_PARSE_SOURCED:-}" ]]; then
  return 0
fi
_EZKEY_OS_PATCH_PARSE_SOURCED=1

# Print the last AL2023 releasever token on stdin (empty if none).
# Accepts a bare releasever file (`2023.12.20261102`) or system-release
# (`Amazon Linux release 2023.12.20261102 (Amazon Linux)`).
os_patch_extract_releasever() {
  local line ver=""
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    if [[ "${line}" =~ ([0-9]+\.[0-9]+\.[0-9]+) ]]; then
      ver="${BASH_REMATCH[1]}"
    fi
  done
  printf '%s\n' "${ver}"
}

# True when $1 is a dated releasever, never the `latest` keyword.
os_patch_is_releasever() {
  [[ "${1:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

# Read one `key=value` token from a single parser line. Prints empty and
# returns 0 when the key is absent (safe under `set -e`).
os_patch_kv_get() {
  local line="$1" key="$2" token
  local -a tokens=()
  read -r -a tokens <<<"${line}" || true
  if [[ ${#tokens[@]} -eq 0 ]]; then
    printf '\n'
    return 0
  fi
  for token in "${tokens[@]}"; do
    if [[ "${token}" == "${key}="* ]]; then
      printf '%s\n' "${token#"${key}"=}"
      return 0
    fi
  done
  printf '\n'
}

# Parse `dnf check-release-update` output.
# Args: <dnf-exit-code> [installed-releasever]
# stdin: combined stdout/stderr
# stdout: status=update|current|unknown latest=<ver|-> behind=<n|-> versions=<csv>
#
# Versions are taken from `Version <rel>:` lines, or from
# `dnf upgrade --releasever=<rel>` when no Version line parsed. Duplicate
# lines count once. Latest is the greatest releasever (`sort -V`), not
# whichever line dnf printed last. A newer-release banner with zero parsed
# versions is unknown (format we do not understand), not "up to date".
# Exit 0 with no versions, or an explicit "no newer release" phrase, is current.
os_patch_parse_check_release_update() {
  local cru_rc="${1:?}"
  local installed="${2:-}"
  local line folded
  local saw_no_newer=0 saw_newer_banner=0 saw_hard_error=0
  local -a version_lines=() releasever_lines=()

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    folded="${line,,}"
    if [[ "${folded}" == *"no newer release"* || "${folded}" == *"no new release"* \
      || "${folded}" == *"already the latest"* || "${folded}" == *"already up to date"* \
      || "${folded}" == *"is the latest version"* || "${folded}" == *"system is up to date"* ]]; then
      saw_no_newer=1
    elif [[ "${folded}" == *"newer release"* && "${folded}" == *"available"* ]]; then
      saw_newer_banner=1
    fi
    if [[ "${folded}" == *"no such command"* || "${folded}" == *"command not found"* \
      || "${folded}" == *"permission denied"* || "${folded}" == *"failed to download"* \
      || "${folded}" == *"error: "* ]]; then
      saw_hard_error=1
    fi
    if [[ "${line}" =~ ^[[:space:]]*Version[[:space:]]+([0-9]+\.[0-9]+\.[0-9]+):?[[:space:]]*$ ]]; then
      version_lines+=("${BASH_REMATCH[1]}")
    fi
    if [[ "${line}" =~ --releasever=([0-9]+\.[0-9]+\.[0-9]+)([^0-9]|$) ]]; then
      releasever_lines+=("${BASH_REMATCH[1]}")
    fi
  done

  local -a found=()
  if [[ ${#version_lines[@]} -gt 0 ]]; then
    found=("${version_lines[@]}")
  elif [[ ${#releasever_lines[@]} -gt 0 ]]; then
    found=("${releasever_lines[@]}")
  fi

  local -a uniq=()
  local seen=" " v
  if [[ ${#found[@]} -gt 0 ]]; then
    for v in "${found[@]}"; do
      if [[ "${seen}" != *" ${v} "* ]]; then
        uniq+=("${v}")
        seen+=" ${v} "
      fi
    done
  fi

  if [[ ${#uniq[@]} -gt 0 ]]; then
    local -a sorted=()
    local latest csv
    mapfile -t sorted < <(printf '%s\n' "${uniq[@]}" | LC_ALL=C sort -V)
    if [[ ${#sorted[@]} -eq 0 || -z "${sorted[0]}" ]]; then
      printf 'status=unknown latest=- behind=- versions=\n'
      return 0
    fi
    latest="${sorted[${#sorted[@]}-1]}"
    csv="$(IFS=,; printf '%s' "${sorted[*]}")"
    printf 'status=update latest=%s behind=%s versions=%s\n' \
      "${latest}" "${#sorted[@]}" "${csv}"
    return 0
  fi

  if [[ "${saw_hard_error}" -eq 1 || "${saw_newer_banner}" -eq 1 ]]; then
    printf 'status=unknown latest=- behind=- versions=\n'
    return 0
  fi

  if [[ "${cru_rc}" -eq 0 || "${saw_no_newer}" -eq 1 ]]; then
    local latest_out="-"
    if os_patch_is_releasever "${installed}"; then
      latest_out="${installed}"
    fi
    printf 'status=current latest=%s behind=0 versions=\n' "${latest_out}"
    return 0
  fi

  printf 'status=unknown latest=- behind=- versions=\n'
}

# Parse `dnf updateinfo summary` text.
# Args: <dnf-exit-code>
# stdout: status=ok|unknown critical=<n|-> important=<n|->
# Missing Critical/Important rows mean 0 when the summary header is present
# (dnf omits zero rows). A hard error is unknown, not zero.
os_patch_parse_updateinfo_summary() {
  local ui_rc="${1:?}"
  local line folded
  local critical="" important=""
  local saw_header=0 saw_hard_error=0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    folded="${line,,}"
    if [[ "${line}" == *"Updates Information Summary:"* ]]; then
      saw_header=1
    fi
    if [[ "${folded}" == *"no such command"* || "${folded}" == *"command not found"* \
      || "${folded}" == *"permission denied"* || "${folded}" == *"failed to download"* \
      || "${folded}" == *"error: "* ]]; then
      saw_hard_error=1
    fi
    if [[ "${line}" =~ ^[[:space:]]*([0-9]+)[[:space:]]+Critical[[:space:]]+Security[[:space:]]+notice ]]; then
      critical="${BASH_REMATCH[1]}"
    fi
    if [[ "${line}" =~ ^[[:space:]]*([0-9]+)[[:space:]]+Important[[:space:]]+Security[[:space:]]+notice ]]; then
      important="${BASH_REMATCH[1]}"
    fi
  done

  if [[ "${saw_hard_error}" -eq 1 ]]; then
    printf 'status=unknown critical=- important=-\n'
    return 0
  fi
  if [[ "${saw_header}" -eq 1 ]]; then
    printf 'status=ok critical=%s important=%s\n' "${critical:-0}" "${important:-0}"
    return 0
  fi
  if [[ "${ui_rc}" -eq 0 ]]; then
    printf 'status=ok critical=%s important=%s\n' "${critical:-0}" "${important:-0}"
    return 0
  fi
  printf 'status=unknown critical=- important=-\n'
}

# Parse `dnf updateinfo list` text. Counts distinct advisory ids
# (`ALAS… Critical/Sec.` / `Important/Sec.`), not package rows.
# Args: <dnf-exit-code>
# stdout: status=ok|unknown critical=<n|-> important=<n|->
os_patch_parse_updateinfo_list() {
  local list_rc="${1:?}"
  local line folded id
  local saw_hard_error=0
  local -A crit_ids=() imp_ids=()

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    folded="${line,,}"
    if [[ "${folded}" == *"no such command"* || "${folded}" == *"command not found"* \
      || "${folded}" == *"permission denied"* || "${folded}" == *"failed to download"* \
      || "${folded}" == *"error: "* ]]; then
      saw_hard_error=1
    fi
    if [[ "${line}" =~ ^[[:space:]]*([A-Za-z0-9._-]+)[[:space:]]+Critical/Sec\. ]]; then
      id="${BASH_REMATCH[1]}"
      crit_ids["${id}"]=1
    elif [[ "${line}" =~ ^[[:space:]]*([A-Za-z0-9._-]+)[[:space:]]+Important/Sec\. ]]; then
      id="${BASH_REMATCH[1]}"
      imp_ids["${id}"]=1
    fi
  done

  if [[ "${saw_hard_error}" -eq 1 || "${list_rc}" -ne 0 ]]; then
    printf 'status=unknown critical=- important=-\n'
    return 0
  fi
  printf 'status=ok critical=%s important=%s\n' "${#crit_ids[@]}" "${#imp_ids[@]}"
}

# Machine-readable decision line for the weekly read-only check.
# Args: <update|current|unknown> <installed> <latest> <behind>
#       <ok|unknown> <critical> <important>
# Non-numeric counts stay `-` (unknown is not zero).
os_patch_format_summary_line() {
  local release_state="$1"
  local installed="$2"
  local latest="$3"
  local behind="$4"
  local counts_state="$5"
  local critical="$6"
  local important="$7"
  local flag="unknown"
  local inst_out="-" lat_out="-" behind_out="-" crit_out="-" imp_out="-"

  case "${release_state}" in
    update) flag="yes" ;;
    current) flag="no" ;;
    *) flag="unknown" ;;
  esac

  if os_patch_is_releasever "${installed}"; then
    inst_out="${installed}"
  fi
  if os_patch_is_releasever "${latest}"; then
    lat_out="${latest}"
  fi
  if [[ "${behind}" =~ ^[0-9]+$ ]]; then
    behind_out="${behind}"
  fi
  if [[ "${counts_state}" == ok && "${critical}" =~ ^[0-9]+$ && "${important}" =~ ^[0-9]+$ ]]; then
    crit_out="${critical}"
    imp_out="${important}"
  fi
  if [[ "${flag}" == unknown ]]; then
    lat_out="-"
    behind_out="-"
    crit_out="-"
    imp_out="-"
  fi

  printf 'RELEASE_UPDATE=%s INSTALLED=%s LATEST=%s BEHIND=%s CRITICAL=%s IMPORTANT=%s\n' \
    "${flag}" "${inst_out}" "${lat_out}" "${behind_out}" "${crit_out}" "${imp_out}"
}
