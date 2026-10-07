#!/usr/bin/env bash
# Helpers for Metro port + adb reverse (sourced by build-install-debug-clean.sh).
# shellcheck shell=bash

# Resolve Metro TCP port: --metro-port / EZKEY_METRO_PORT / RCT_METRO_PORT / 8081.
ezkey_metro_port() {
  local explicit="${1:-}"
  if [[ -n "$explicit" ]]; then
    echo "$explicit"
    return 0
  fi
  if [[ -n "${EZKEY_METRO_PORT:-}" ]]; then
    echo "${EZKEY_METRO_PORT}"
    return 0
  fi
  if [[ -n "${RCT_METRO_PORT:-}" ]]; then
    echo "${RCT_METRO_PORT}"
    return 0
  fi
  echo "8081"
}

# True when android/gradle.properties has ezkey.useMetroInDebug=true.
ezkey_metro_debug_enabled() {
  local props="${1:?gradle.properties path required}"
  [[ -f "$props" ]] || return 1
  grep -Eq '^[[:space:]]*ezkey\.useMetroInDebug[[:space:]]*=[[:space:]]*true[[:space:]]*$' "$props"
}

# Re-apply adb reverse for Metro (needed after wireless ADB reconnect).
ezkey_adb_reverse_metro() {
  local port="${1:?port required}"
  echo "Applying adb reverse tcp:${port} -> tcp:${port} (re-run after wireless ADB reconnect)..."
  adb reverse "tcp:${port}" "tcp:${port}"
}

# Best-effort: find cwd of a process listening on localhost:PORT.
# Prints absolute path or empty if unknown.
ezkey_listener_cwd_for_port() {
  local port="${1:?port required}"
  local pid="" cwd=""

  if command -v lsof >/dev/null 2>&1; then
    pid="$(lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null | head -1 || true)"
  fi

  if [[ -z "$pid" ]] && command -v ss >/dev/null 2>&1; then
    pid="$(ss -ltnp 2>/dev/null | sed -n "s/.*:${port} .*pid=\\([0-9][0-9]*\\).*/\\1/p" | head -1 || true)"
  fi

  if [[ -z "$pid" ]] && command -v netstat >/dev/null 2>&1; then
    # Windows netstat -ano; Git Bash often has this.
    pid="$(netstat -ano 2>/dev/null | tr -d '\r' | awk -v p=":${port}" '
      $0 ~ p && $0 ~ /LISTENING/ {
        print $NF
        exit
      }')"
  fi

  [[ -n "$pid" ]] || return 0

  if [[ -d "/proc/${pid}/cwd" ]]; then
    cwd="$(readlink -f "/proc/${pid}/cwd" 2>/dev/null || true)"
  fi

  if [[ -z "$cwd" ]] && command -v pwdx >/dev/null 2>&1; then
    cwd="$(pwdx "$pid" 2>/dev/null | awk '{print $2}' || true)"
  fi

  if [[ -z "$cwd" ]] && command -v powershell.exe >/dev/null 2>&1; then
    cwd="$(
      powershell.exe -NoProfile -Command \
        "try { (Get-Process -Id ${pid}).Path } catch { '' }" 2>/dev/null \
        | tr -d '\r' | head -1 || true
    )"
    # Process Path is the exe; try CommandLine for project root hints.
    local cmdline
    cmdline="$(
      powershell.exe -NoProfile -Command \
        "try { (Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}').CommandLine } catch { '' }" \
        2>/dev/null | tr -d '\r' || true
    )"
    if [[ -n "$cmdline" ]]; then
      # Prefer an absolute path segment that contains ezkey_mobile.
      local hint
      hint="$(printf '%s\n' "$cmdline" | sed -n 's/.*\([A-Za-z]:[\\/][^\"'"'"' ]*[Ee]zkey[^\"'"'"' ]*[Mm]obile[^\"'"'"' ]*\).*/\1/p' | head -1 || true)"
      if [[ -z "$hint" ]]; then
        hint="$(printf '%s\n' "$cmdline" | sed -n 's/.*\([A-Za-z]:[\\/][^\"'"'"']*ezkey_mobile\).*/\1/p' | head -1 || true)"
      fi
      if [[ -n "$hint" ]]; then
        cwd="$hint"
      fi
    fi
  fi

  if [[ -n "$cwd" ]]; then
    printf '%s\n' "$cwd"
  fi
}

# Normalize path for loose comparison (lowercase, forward slashes, strip trailing slash).
ezkey_norm_path() {
  local p="$1"
  p="${p//\\//}"
  p="${p%/}"
  printf '%s\n' "$p" | tr '[:upper:]' '[:lower:]'
}

# Warn when a Metro (or other listener) on PORT appears bound to a different project root.
ezkey_warn_stale_metro() {
  local port="${1:?port required}"
  local mobile_root="${2:?mobile root required}"
  local status_url="http://127.0.0.1:${port}/status"
  local listening=false

  if command -v curl >/dev/null 2>&1; then
    if curl -fsS --max-time 1 "$status_url" >/dev/null 2>&1; then
      listening=true
    fi
  elif command -v wget >/dev/null 2>&1; then
    if wget -q -T 1 -O /dev/null "$status_url" 2>/dev/null; then
      listening=true
    fi
  fi

  if [[ "$listening" != true ]]; then
    # No packager status — nothing to compare (or Metro not up yet).
    return 0
  fi

  local listener_cwd
  listener_cwd="$(ezkey_listener_cwd_for_port "$port" || true)"
  if [[ -z "$listener_cwd" ]]; then
    echo "[metro] Warning: something answers on port ${port}, but its project root could not be determined."
    echo "[metro]   If JS bundles look wrong, stop other Metro instances and start from this worktree:"
    echo "[metro]   yarn start --port ${port}"
    return 0
  fi

  local norm_root norm_cwd
  norm_root="$(ezkey_norm_path "$mobile_root")"
  norm_cwd="$(ezkey_norm_path "$listener_cwd")"

  if [[ "$norm_cwd" == "$norm_root"* ]] || [[ "$norm_root" == "$norm_cwd"* ]]; then
    echo "[metro] Packager on port ${port} looks tied to this worktree (${listener_cwd})."
    return 0
  fi

  cat <<EOF
[metro] Warning: port ${port} is already serving a different project root.
  Current worktree: ${mobile_root}
  Listener cwd/hint: ${listener_cwd}
  Stale Metro from another worktree often loads the wrong JS bundle.
  Fix: stop the other Metro, then from this worktree run:
    yarn start --port ${port}
EOF
}
