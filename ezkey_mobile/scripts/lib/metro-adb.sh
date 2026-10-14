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

# Extract an ezkey_mobile project root hint from a process command line.
# Supports Windows paths (backslashes) and POSIX paths. Prints one path or empty.
# Uses bash [[ =~ ]] (not sed) so backslashes in Windows paths are not excluded.
# Exported for self-test (do not require a live listener).
ezkey_metro_project_hint_from_cmdline() {
  local cmdline="${1:-}"
  [[ -n "$cmdline" ]] || return 0

  local hint=""
  # Windows drive path containing ezkey_mobile (allow \ and / after the drive).
  # Terminators: quotes and whitespace only — backslash must remain allowed.
  if [[ "$cmdline" =~ ([A-Za-z]:[\\/][^\"\'[:space:]]*[Ee]zkey_[Mm]obile) ]]; then
    hint="${BASH_REMATCH[1]}"
  elif [[ "$cmdline" =~ (/[^\"\'[:space:]]*/ezkey_mobile) ]]; then
    # POSIX / Git Bash: /c/w/p/ezkey_mobile or /home/.../ezkey_mobile
    hint="${BASH_REMATCH[1]}"
  fi

  if [[ -n "$hint" ]]; then
    # Trim trailing path segments after ezkey_mobile when the match over-captured
    # (e.g. .../ezkey_mobile/node_modules/...). Keep the ezkey_mobile root.
    if [[ "$hint" == *"/ezkey_mobile/"* ]]; then
      hint="${hint%%/ezkey_mobile/*}/ezkey_mobile"
    elif [[ "$hint" == *"\\ezkey_mobile\\"* ]]; then
      hint="${hint%%\\ezkey_mobile\\*}\\ezkey_mobile"
    fi
    printf '%s\n' "$hint"
  fi
}

# True when a string looks like a Node/Metro executable path, not a project root.
ezkey_looks_like_node_exe() {
  local p="${1:-}"
  local lower
  lower="$(printf '%s\n' "$p" | tr '[:upper:]' '[:lower:]')"
  [[ "$lower" == *"/node" || "$lower" == *"\\node.exe" || "$lower" == *"/node.exe" || "$lower" == *"nodejs/node"* || "$lower" == *"nodejs\\node"* ]]
}

# Best-effort: find project root of a process listening on localhost:PORT.
# Prints absolute path or empty if unknown.
ezkey_listener_cwd_for_port() {
  local port="${1:?port required}"
  local pid="" cwd="" cmdline=""

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

  if command -v powershell.exe >/dev/null 2>&1; then
    # Prefer CommandLine (project path); do NOT treat Process.Path (node.exe) as cwd.
    cmdline="$(
      powershell.exe -NoProfile -Command \
        "try { (Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}').CommandLine } catch { '' }" \
        2>/dev/null | tr -d '\r' || true
    )"
    if [[ -n "$cmdline" ]]; then
      local hint
      hint="$(ezkey_metro_project_hint_from_cmdline "$cmdline" || true)"
      if [[ -n "$hint" ]]; then
        cwd="$hint"
      fi
    fi
  fi

  # Drop node.exe / similar — that is not a project root.
  if [[ -n "$cwd" ]] && ezkey_looks_like_node_exe "$cwd"; then
    cwd=""
  fi

  if [[ -n "$cwd" ]]; then
    printf '%s\n' "$cwd"
  fi
}

# Normalize path for loose comparison across Git Bash vs Windows forms.
# Uses cygpath -m when available; lowercase; forward slashes; no trailing slash.
ezkey_norm_path() {
  local p="$1"
  [[ -n "$p" ]] || {
    printf '\n'
    return 0
  }

  if command -v cygpath >/dev/null 2>&1; then
    # cygpath -m → C:/w/r/ezkey_mobile even when given /c/w/r/ezkey_mobile
    local converted
    converted="$(cygpath -m "$p" 2>/dev/null || true)"
    if [[ -n "$converted" ]]; then
      p="$converted"
    fi
  else
    # Lightweight Git Bash → Windows-ish: /c/foo → C:/foo (no cygpath, e.g. Linux CI)
    if [[ "$p" =~ ^/([a-zA-Z])/(.*)$ ]]; then
      local drive
      drive="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:lower:]' '[:upper:]')"
      p="${drive}:/${BASH_REMATCH[2]}"
    fi
  fi

  p="${p//\\//}"
  p="${p%/}"
  # Collapse duplicate slashes
  while [[ "$p" == *//* ]]; do
    p="${p//\/\//\/}"
  done
  printf '%s\n' "$p" | tr '[:upper:]' '[:lower:]'
}

# True when listener path and mobile root refer to the same worktree (or one contains the other).
ezkey_metro_paths_same_worktree() {
  local norm_root="$1"
  local norm_cwd="$2"
  [[ -n "$norm_root" && -n "$norm_cwd" ]] || return 1
  [[ "$norm_cwd" == "$norm_root" || "$norm_cwd" == "$norm_root"/* || "$norm_root" == "$norm_cwd" || "$norm_root" == "$norm_cwd"/* ]]
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
    echo "[metro] Note: something answers on port ${port}, but its project root could not be determined."
    echo "[metro]   If JS bundles look wrong, stop other Metro instances and start from this worktree:"
    echo "[metro]   yarn start --port ${port}"
    return 0
  fi

  local norm_root norm_cwd
  norm_root="$(ezkey_norm_path "$mobile_root")"
  norm_cwd="$(ezkey_norm_path "$listener_cwd")"

  if ezkey_metro_paths_same_worktree "$norm_root" "$norm_cwd"; then
    echo "[metro] Packager on port ${port} looks tied to this worktree (${listener_cwd})."
    return 0
  fi

  cat <<EOF
[metro] Warning: port ${port} is already serving a different project root.
  Current worktree: ${mobile_root}
  Listener project root: ${listener_cwd}
  Stale Metro from another worktree often loads the wrong JS bundle.
  Fix: stop the other Metro, then from this worktree run:
    yarn start --port ${port}
EOF
}
