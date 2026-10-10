#!/usr/bin/env bash
# Host-local Docker json-file log check / scan / purge. No SSH, no remote actions.
# Invoke: bash scripts/ops/docker-log-hygiene.sh […]  Exit: 0 ok, 1 findings, 2 error.
# shellcheck shell=bash
set -euo pipefail

MODE="check"
SCAN=0
APPLY=0
YES=0
OLDER_THAN_DAYS=7
SCAN_SINCE="${SCAN_SINCE:-720h}"
PROJECT="${COMPOSE_PROJECT_NAME:-}"
DOCKER_ROOT="${DOCKER_ROOT:-/var/lib/docker}"
FINDINGS=0

usage() {
  cat <<'EOF'
Usage: bash scripts/ops/docker-log-hygiene.sh [options]
  (default)              Check: driver, rotation, log sizes
  --scan                 Count credential-like patterns (never values); stdout+stderr
  --since DUR            Scan window for docker logs (default: 720h / SCAN_SINCE)
  --project NAME         Limit to compose project (label com.docker.compose.project)
  --purge                Purge plan (dry-run): stopped logs + old rotated files
  --apply                With --purge; requires --project or COMPOSE_PROJECT_NAME (no host-wide)
  --yes                  Skip confirmation for --purge --apply
  --older-than N         Rotated-file age in days (default: 7)
  -h, --help             Help
Rotated purge often needs sudo under /var/lib/docker/containers. Exit: 0 ok, 1 findings, 2 error.
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

container_ids() {
  if [ -n "$PROJECT" ]; then
    docker ps -aq --filter "label=com.docker.compose.project=${PROJECT}"
  else
    docker ps -aq
  fi
}

is_migration_name() {
  case "$1" in
    *migration*|*flyway*) return 0 ;;
    *) return 1 ;;
  esac
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
    --project)
      shift
      [ $# -gt 0 ] || die "--project requires a name"
      PROJECT="$1"
      ;;
    --since)
      shift
      [ $# -gt 0 ] || die "--since requires a duration (e.g. 720h)"
      SCAN_SINCE="$1"
      ;;
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

[ "$APPLY" -eq 1 ] && [ "$MODE" != "purge" ] && die "--apply requires --purge"
[ "$APPLY" -eq 1 ] && [ -z "$PROJECT" ] \
  && die "--purge --apply requires --project NAME or COMPOSE_PROJECT_NAME (refusing host-wide apply)"

require_docker

check_containers() {
  local id name driver max_size max_file log_path size total=0 missing=0
  echo "=== Docker log hygiene (check)${PROJECT:+ project=$PROJECT} ==="
  printf '%-36s %-12s %-10s %-10s %10s\n' "CONTAINER" "DRIVER" "MAX-SIZE" "MAX-FILE" "LOG-SIZE"
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
    printf '%-36s %-12s %-10s %-10s %10s\n' \
      "$name" "$driver" "$max_size" "$max_file" "$(human_bytes "$size")"
    if [ "$driver" = "json-file" ] && { [ "$max_size" = "-" ] || [ "$max_file" = "-" ]; }; then
      echo "  FINDING: $name json-file without max-size/max-file"
      missing=$((missing + 1))
      FINDINGS=1
    fi
  done < <(container_ids)
  echo "Total readable log bytes (active files): $(human_bytes "$total")"
  echo "Containers missing rotation: $missing"
}

# Counts only — stream docker logs into grep (bounded memory; never load the window into a var).
scan_count() { local id="$1"; shift; docker logs --since "$SCAN_SINCE" "$id" 2>&1 | grep "$@" || true; }
scan_patterns() {
  local id name c1 c2 c3 c4 c5
  echo; echo "=== Credential-like pattern scan (counts only; since=${SCAN_SINCE}) ==="
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    name="$(docker inspect -f '{{.Name}}' "$id" | sed 's#^/##')"
    c1="$(scan_count "$id" -cE '[0-9]{4}(-[0-9]{4}){7}')"
    c2="$(scan_count "$id" -ciE 'enrollment[[:space:]]*proof[[:space:]]*token')"
    c3="$(scan_count "$id" -ciE 'recovery[[:space:]]*codes?[[:space:]]*:')"
    c4="$(scan_count "$id" -cE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}')"
    c5="$(scan_count "$id" -ciE 'Using generated security password')"
    printf '%-36s recovery=%s proof=%s recovery_lbl=%s email=%s gen_sec_pwd=%s\n' \
      "$name" "$c1" "$c2" "$c3" "$c4" "$c5"
    { [ "${c1:-0}" -gt 0 ] || [ "${c4:-0}" -gt 0 ] || [ "${c5:-0}" -gt 0 ]; } && FINDINGS=1
  done < <(container_ids)
  echo "(Counts only; values never printed. Label/gen_sec_pwd hits may be expected in full bootstrap.)"
}

run_action() {
  local op="$1" path="$2"
  if [ "$op" = "truncate" ]; then
    truncate -s 0 "$path" 2>/dev/null || sudo -n truncate -s 0 "$path" 2>/dev/null \
      || die "cannot truncate $path (need write access or passwordless sudo)"
    echo "truncated $path"
  else
    rm -f "$path" 2>/dev/null || sudo -n rm -f "$path" 2>/dev/null \
      || die "cannot remove $path (need write access or passwordless sudo — rotated logs often need sudo)"
    echo "removed $path"
  fi
}

# Rotated files: only under selected project container IDs (never host-wide). Needs sudo often.
purge_plan_or_apply() {
  local id name status log_path log_dir f action="DRY-RUN" mig_warn=0
  local -a ops=() paths=() notes=()
  echo "=== Docker log hygiene (purge)${PROJECT:+ project=$PROJECT} ==="
  [ "$APPLY" -eq 1 ] && action="APPLY"
  echo "Mode: $action (rotated files older than ${OLDER_THAN_DAYS}d; stopped-container logs)"

  while IFS= read -r id; do
    [ -n "$id" ] || continue
    name="$(docker inspect -f '{{.Name}}' "$id" | sed 's#^/##')"
    if is_migration_name "$name"; then
      echo "WARN: excluding migration/Flyway container from purge: $name (keep Flyway trace)"
      mig_warn=1
      continue
    fi
    status="$(docker inspect -f '{{.State.Status}}' "$id")"
    log_path="$(docker inspect -f '{{.LogPath}}' "$id" 2>/dev/null || true)"
    if [ "$status" != "running" ] && [ -n "$log_path" ] && [ -e "$log_path" ]; then
      ops+=("truncate"); paths+=("$log_path"); notes+=("stopped $name")
    fi
    [ -n "$log_path" ] || continue
    log_dir="$(dirname "$log_path")"
    if [ -d "$log_dir" ] && [ -r "$log_dir" ]; then
      while IFS= read -r f; do
        [ -n "$f" ] || continue
        ops+=("rm"); paths+=("$f"); notes+=("rotated $name")
      done < <(find "$log_dir" -type f -name '*-json.log.*' -mtime "+${OLDER_THAN_DAYS}" 2>/dev/null || true)
    fi
  done < <(container_ids)

  [ -r "$DOCKER_ROOT/containers" ] \
    || echo "NOTE: $DOCKER_ROOT/containers not readable — re-run with sudo for rotated logs."

  if [ "${#ops[@]}" -eq 0 ]; then
    [ "$mig_warn" -eq 1 ] && echo "No purge candidates (migration containers were excluded)." \
      || echo "No purge candidates."
    return 0
  fi

  FINDINGS=1
  local i
  for i in "${!ops[@]}"; do
    echo "  ${ops[$i]}:${paths[$i]} # ${notes[$i]}"
  done
  echo "Candidates: ${#ops[@]}"

  if [ "$APPLY" -eq 0 ]; then
    echo "Dry-run only. Re-run with: bash $0 --purge --apply --yes${PROJECT:+ --project $PROJECT}"
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
