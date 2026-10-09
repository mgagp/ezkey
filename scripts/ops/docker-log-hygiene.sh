#!/usr/bin/env bash
# Host-local Docker json-file log check / purge. No SSH, no remote actions.
# Exit: 0 ok, 1 findings, 2 error.
# shellcheck shell=bash
set -euo pipefail

MODE="check"
SCAN=0
APPLY=0
YES=0
OLDER_THAN_DAYS=7
DOCKER_ROOT="${DOCKER_ROOT:-/var/lib/docker}"
FINDINGS=0

usage() {
  cat <<'EOF'
Usage: docker-log-hygiene.sh [options]
  (default)         Check: driver, rotation, log sizes; flag missing rotation
  --scan            Count credential-like patterns (counts only, never values)
  --purge           Purge plan (dry-run): stopped-container logs + rotated files
  --apply           With --purge, perform purge (prompts unless --yes)
  --yes             Skip confirmation for --purge --apply
  --older-than N    Rotated-file age in days (default: 7)
  -h, --help        Help
Exit: 0 ok, 1 findings, 2 error.
EOF
}

die() { echo "ERROR: $*" >&2; exit 2; }

human_bytes() {
  awk -v b="$1" 'BEGIN{
    if (b<1024){printf "%dB", b; exit}
    if (b<1048576){printf "%.1fKiB", b/1024; exit}
    if (b<1073741824){printf "%.1fMiB", b/1048576; exit}
    printf "%.2fGiB", b/1073741824
  }'
}

file_bytes() {
  local path="$1" n
  if [ -r "$path" ]; then
    n="$(wc -c <"$path" | tr -d ' ')"
  elif n="$(sudo -n wc -c "$path" 2>/dev/null | awk '{print $1}')"; then
    :
  else
    n=0
  fi
  echo "${n:-0}"
}

require_docker() {
  command -v docker >/dev/null 2>&1 || die "docker not found on PATH"
  docker info >/dev/null 2>&1 || die "docker not usable (daemon down or permission denied)"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --scan) SCAN=1 ;;
    --purge) MODE="purge" ;;
    --apply) APPLY=1 ;;
    --yes) YES=1 ;;
    --older-than)
      shift
      [ $# -gt 0 ] || die "--older-than requires a number"
      OLDER_THAN_DAYS="$1"
      ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
  shift
done

case "$OLDER_THAN_DAYS" in
  ''|*[!0-9]*) die "--older-than must be a non-negative integer" ;;
esac

require_docker

check_containers() {
  local id name driver max_size max_file log_path size total=0 missing=0
  echo "=== Docker log hygiene (check) ==="
  printf '%-28s %-12s %-10s %-10s %10s\n' "CONTAINER" "DRIVER" "MAX-SIZE" "MAX-FILE" "LOG-SIZE"
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    name="$(docker inspect -f '{{.Name}}' "$id" | sed 's#^/##')"
    driver="$(docker inspect -f '{{.HostConfig.LogConfig.Type}}' "$id")"
    max_size="$(docker inspect -f '{{index .HostConfig.LogConfig.Config "max-size"}}' "$id")"
    max_file="$(docker inspect -f '{{index .HostConfig.LogConfig.Config "max-file"}}' "$id")"
    [ -n "$max_size" ] || max_size="-"
    [ -n "$max_file" ] || max_file="-"
    log_path="$(docker inspect -f '{{.LogPath}}' "$id" 2>/dev/null || true)"
    size=0
    [ -n "$log_path" ] && size="$(file_bytes "$log_path")"
    total=$((total + size))
    printf '%-28s %-12s %-10s %-10s %10s\n' \
      "$name" "$driver" "$max_size" "$max_file" "$(human_bytes "$size")"
    if [ "$driver" = "json-file" ] && { [ "$max_size" = "-" ] || [ "$max_file" = "-" ]; }; then
      echo "  FINDING: $name json-file without max-size/max-file"
      missing=$((missing + 1))
      FINDINGS=1
    fi
  done < <(docker ps -aq)
  echo "Total readable log bytes (active files): $(human_bytes "$total")"
  echo "Containers missing rotation: $missing"
}

# Counts only — never prints matching lines or captured values.
scan_patterns() {
  local id name c1 c2 c3 c4
  echo
  echo "=== Credential-like pattern scan (counts only) ==="
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    name="$(docker inspect -f '{{.Name}}' "$id" | sed 's#^/##')"
    c1="$(docker logs --tail 5000 "$id" 2>/dev/null | grep -cE '[0-9]{4}(-[0-9]{4}){7}' || true)"
    c2="$(docker logs --tail 5000 "$id" 2>/dev/null | grep -ciE 'enrollment[[:space:]]*proof[[:space:]]*token' || true)"
    c3="$(docker logs --tail 5000 "$id" 2>/dev/null | grep -ciE 'recovery[[:space:]]*codes?[[:space:]]*:' || true)"
    c4="$(docker logs --tail 5000 "$id" 2>/dev/null | grep -cE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' || true)"
    printf '%-28s recovery_code_shape=%s proof_token_label=%s recovery_label=%s email_shape=%s\n' \
      "$name" "$c1" "$c2" "$c3" "$c4"
    if [ "${c1:-0}" -gt 0 ] || [ "${c4:-0}" -gt 0 ]; then
      FINDINGS=1
    fi
  done < <(docker ps -aq)
  echo "(Counts only; values never printed. Label hits may be expected in full bootstrap.)"
}

run_action() {
  local op="$1" path="$2"
  if [ "$op" = "truncate" ]; then
    truncate -s 0 "$path" 2>/dev/null || sudo -n truncate -s 0 "$path" 2>/dev/null \
      || die "cannot truncate $path (need write access / sudo)"
    echo "truncated $path"
  else
    rm -f "$path" 2>/dev/null || sudo -n rm -f "$path" 2>/dev/null \
      || die "cannot remove $path (need write access / sudo)"
    echo "removed $path"
  fi
}

purge_plan_or_apply() {
  local id name status log_path f action="DRY-RUN"
  local -a ops=() paths=() notes=()
  echo "=== Docker log hygiene (purge) ==="
  [ "$APPLY" -eq 1 ] && action="APPLY"
  echo "Mode: $action (rotated files older than ${OLDER_THAN_DAYS}d; stopped-container logs)"

  while IFS= read -r id; do
    [ -n "$id" ] || continue
    name="$(docker inspect -f '{{.Name}}' "$id" | sed 's#^/##')"
    status="$(docker inspect -f '{{.State.Status}}' "$id")"
    [ "$status" = "running" ] && continue
    log_path="$(docker inspect -f '{{.LogPath}}' "$id" 2>/dev/null || true)"
    [ -n "$log_path" ] || continue
    [ -e "$log_path" ] || continue
    ops+=("truncate"); paths+=("$log_path"); notes+=("stopped $name")
  done < <(docker ps -aq)

  if [ -d "$DOCKER_ROOT/containers" ]; then
    while IFS= read -r f; do
      [ -n "$f" ] || continue
      ops+=("rm"); paths+=("$f"); notes+=("rotated")
    done < <(find "$DOCKER_ROOT/containers" -type f -name '*-json.log.*' -mtime "+${OLDER_THAN_DAYS}" 2>/dev/null || true)
  else
    echo "NOTE: $DOCKER_ROOT/containers not readable; rotated-file scan skipped (try sudo)."
  fi

  if [ "${#ops[@]}" -eq 0 ]; then
    echo "No purge candidates."
    return 0
  fi

  FINDINGS=1
  local i
  for i in "${!ops[@]}"; do
    echo "  ${ops[$i]}:${paths[$i]} # ${notes[$i]}"
  done
  echo "Candidates: ${#ops[@]}"

  if [ "$APPLY" -eq 0 ]; then
    echo "Dry-run only. Re-run with: $0 --purge --apply [--yes]"
    return 0
  fi

  if [ "$YES" -eq 0 ]; then
    [ -t 0 ] || die "confirmation required; use --yes for non-interactive apply"
    printf 'Apply purge of %s candidates? [y/N] ' "${#ops[@]}"
    local ans
    read -r ans
    case "$ans" in y|Y|yes|YES) ;; *) echo "Aborted."; exit 0 ;; esac
  fi

  for i in "${!ops[@]}"; do
    run_action "${ops[$i]}" "${paths[$i]}"
  done
  echo "Purge apply complete."
}

if [ "$MODE" = "purge" ]; then
  purge_plan_or_apply
else
  check_containers
  [ "$SCAN" -eq 1 ] && scan_patterns
fi

[ "$FINDINGS" -eq 1 ] && exit 1
exit 0
