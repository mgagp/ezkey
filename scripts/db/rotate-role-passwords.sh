#!/usr/bin/env bash
# rotate-role-passwords.sh — rotate the Ezkey application database role passwords (#1017).
#
# Rotates ezkey_migrate / ezkey_admin / ezkey_auth / ezkey_integration (default) inside the
# Postgres container and writes the matching EZKEY_DB_<ROLE>_PASSWORD keys to the compose .env.
#
# Guarantees:
#   * no password value is ever printed (stdout/stderr), passed as a command argument, or put in
#     a process environment; values travel through files (mode 600) and stdin only;
#   * one transaction for all ALTER ROLE statements. Before BEGIN the session sets
#     log_statement=none, log_min_duration_statement=-1, log_min_error_statement=PANIC,
#     log_transaction_sample_rate=0, and log_min_duration_sample=-1;
#   * refuses unless server log_statement is already none (exit 4). The session SET does not
#     change other connections. Also refuses (exit 8) when a shared_preload_libraries entry
#     names pg_stat_statements, pgaudit, or auto_explain after the directory prefix, quotes,
#     and a trailing .so are removed (those libraries can record statement text anyway);
#   * the .env is backed up first, the new .env is prepared BEFORE the ALTER ROLE and moved into
#     place only AFTER the COMMIT. On SQL failure the live .env is not replaced (see exit 5);
#   * idempotent .env update: keys are replaced in place or appended once;
#   * rollback values must match [A-Za-z0-9_-] (a quote or a backslash is refused);
#   * the script never recreates a service: it prints which ones to recreate.
#
# Run under tmux, or another session that survives an SSH disconnect. A dropped session
# during the SQL step is the case exit 5 does not fully cover.
#
# Usage (from the compose directory, e.g. ~/ezkey/experimental-hybrid/lightsail):
#   ../../scripts/db/rotate-role-passwords.sh --docker [--dry-run]
#   ../../scripts/db/rotate-role-passwords.sh --docker --rollback .env.bak.pre-rotation-<ts>
# Options:
#   --docker                 use the Postgres container (required; only mode supported)
#   --container NAME         default: $EZKEY_POSTGRES_CONTAINER or ezkey-exp-postgres
#   --db NAME                default: ezkey_db
#   --env-file PATH          default: ./.env
#   --compose-file PATH      default: ./docker-compose.yml (compose defaults for --rollback)
#   --roles LIST             default: migrate,admin,auth,integration
#   --verify-network NET     after rotation/rollback, log in as each role over this Docker network
#   --keep-dir DIR           keep generated values in DIR (mode 700/600) instead of shredding them
#   --dry-run                checks only; generates and changes nothing
#   --rollback BACKUP        restore BACKUP as .env and set the roles back to its values
#                            (a key absent from BACKUP means the compose default;
#                            each value must match [A-Za-z0-9_-])
# Exit codes:
#   0 ok · 2 usage · 3 prerequisite missing or unsafe password value
#   4 log_statement is not 'none'
#   5 SQL session failed (live .env unchanged; prepared file removed; rollback
#     command printed. Transaction abort only if the error was before COMMIT)
#   6 .env write failed AFTER COMMIT
#   7 post-write equality check failed
#   8 shared_preload_libraries includes pg_stat_statements, pgaudit, or auto_explain
set -uo pipefail
set +x
umask 077

CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-exp-postgres}"
DB="ezkey_db"; ENV_FILE="./.env"; COMPOSE_FILE="./docker-compose.yml"
ROLES="migrate,admin,auth,integration"; DOCKER_MODE=""; DRY_RUN=""; ROLLBACK=""; KEEP_DIR=""; VERIFY_NET=""

say()  { printf '%s\n' "$*"; }
err()  { printf 'error: %s\n' "$*" >&2; }
usage(){ sed -n '2,/^set -uo pipefail$/p' "$0" | sed '/^set -uo pipefail$/d; s/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --docker) DOCKER_MODE=1 ;;
    --container) CONTAINER="${2:?}"; shift ;;
    --db) DB="${2:?}"; shift ;;
    --env-file) ENV_FILE="${2:?}"; shift ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift ;;
    --roles) ROLES="${2:?}"; shift ;;
    --verify-network) VERIFY_NET="${2:?}"; shift ;;
    --keep-dir) KEEP_DIR="${2:?}"; shift ;;
    --dry-run) DRY_RUN=1 ;;
    --rollback) ROLLBACK="${2:?}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) err "unknown option: $1"; usage >&2; exit 2 ;;
  esac
  shift
done
[ -n "$DOCKER_MODE" ] || { err "--docker is required"; exit 2; }

IFS=',' read -r -a ROLE_LIST <<< "$ROLES"
for r in "${ROLE_LIST[@]}"; do
  [[ "$r" =~ ^[a-z]+$ ]] || { err "invalid role name: $r"; exit 2; }
done
key_of() { printf 'EZKEY_DB_%s_PASSWORD' "$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')"; }

# psql in the container; SQL on stdin. stderr is filtered: quoted literals redacted, LINE/context dropped.
psql_in() {
  local out rc
  out=$(docker exec -i "$CONTAINER" psql -X -q -U postgres -d "$DB" -v ON_ERROR_STOP=1 -At 2>&1); rc=$?
  if [ $rc -ne 0 ]; then
    printf '%s\n' "$out" | grep -E '^(psql:.*)?ERROR:' | sed -E "s/'[^']*'/'<redacted>'/g" >&2
  else
    printf '%s\n' "$out"
  fi
  return $rc
}
q() { printf '%s\n' "$1" | psql_in; }

# Library name only: trim, drop quotes, drop a directory prefix, drop one trailing .so.
# "$libdir/pg_stat_statements.so" and pg_stat_statements.so both become pg_stat_statements.
preload_base() {
  local e="$1" i=0
  e="${e#"${e%%[![:space:]]*}"}"
  e="${e%"${e##*[![:space:]]}"}"
  while [ "$i" -lt 3 ]; do
    i=$((i + 1))
    case "$e" in
      \"*\") e=${e#\"}; e=${e%\"} ;;
      \'*\') e=${e#\'}; e=${e%\'} ;;
      *) break ;;
    esac
    e="${e#"${e%%[![:space:]]*}"}"
    e="${e%"${e##*[![:space:]]}"}"
  done
  e=${e##*/}
  e=${e#\"}; e=${e%\"}; e=${e#\'}; e=${e%\'}
  e=${e%.so}
  e="${e#"${e%%[![:space:]]*}"}"
  e="${e%"${e##*[![:space:]]}"}"
  printf '%s' "$e"
}

# Whole file, not one line: a newline must not split a value into a safe prefix.
value_is_safe() {
  local content
  content=$(<"$1")
  [[ "$content" =~ ^[A-Za-z0-9_-]+$ ]]
}

env_get_to() { # env_get_to FILE KEY DEST — copy the value (unquoted) into DEST file; returns 1 if absent
  local line
  line=$(grep -E "^$2=" "$1" | tail -n 1) || return 1
  line="${line#*=}"; line="${line%$'\r'}"; line="${line%\"}"; line="${line#\"}"; line="${line%\'}"; line="${line#\'}"
  printf '%s' "$line" > "$3"
}
compose_default_to() { # compose_default_to KEY DEST
  local d
  d=$(grep -oE "\\\$\{$1:-[^}]*\}" "$COMPOSE_FILE" | head -n 1) || return 1
  d="${d#*:-}"; d="${d%\}}"
  printf '%s' "$d" > "$2"
}

# ---------- prerequisites ----------
command -v docker >/dev/null || { err "docker not found"; exit 3; }
[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" = "true" ] || { err "container $CONTAINER not running"; exit 3; }
[ -f "$ENV_FILE" ] || { err "env file not found: $ENV_FILE"; exit 3; }
if [ ! -w "$ENV_FILE" ] || [ ! -w "$(dirname "$ENV_FILE")" ]; then
  err "env file or its directory not writable: $ENV_FILE"
  exit 3
fi
if [ -n "$ROLLBACK" ]; then
  [ -f "$ROLLBACK" ] || { err "rollback backup not found: $ROLLBACK"; exit 3; }
  [ -f "$COMPOSE_FILE" ] || { err "compose file not found (needed for defaults): $COMPOSE_FILE"; exit 3; }
fi
command -v openssl >/dev/null || [ -n "$ROLLBACK" ] || { err "openssl not found"; exit 3; }
ls=$(q "SHOW log_statement") || { err "cannot read log_statement"; exit 3; }
[ "$ls" = "none" ] || { err "log_statement is '$ls' (must be 'none'); refusing (ALTER ROLE would be logged)"; exit 4; }
libs=$(q "SHOW shared_preload_libraries") || { err "cannot read shared_preload_libraries"; exit 3; }
if [ -n "$libs" ]; then
  IFS=',' read -r -a LIB_LIST <<< "$libs"
  for lib in "${LIB_LIST[@]}"; do
    base=$(preload_base "$lib")
    [ -n "$base" ] || continue
    case "$base" in
      pg_stat_statements|pgaudit|auto_explain)
        err "shared_preload_libraries includes ${base}; refusing (it can record ALTER ROLE text)"
        exit 8
        ;;
    esac
  done
fi
for r in "${ROLE_LIST[@]}"; do
  n=$(q "select count(*) from pg_roles where rolname='ezkey_$r'") || { err "cannot query roles"; exit 3; }
  [ "$n" = "1" ] || { err "role ezkey_$r does not exist"; exit 3; }
done

MODE=rotate; [ -n "$ROLLBACK" ] && MODE=rollback
say "mode=$MODE container=$CONTAINER db=$DB env=$ENV_FILE roles=${ROLES}"
for r in "${ROLE_LIST[@]}"; do
  k=$(key_of "$r"); c=$(grep -cE "^$k=" "$ENV_FILE" || true)
  say "  $k: $( [ "$c" -gt 0 ] && echo "present in .env ($c)" || echo "absent from .env (compose default in effect)")"
done
if [ -n "$DRY_RUN" ]; then
  say "dry-run: prerequisites OK; would back up the .env, $( [ $MODE = rotate ] && echo 'generate new values,' ) run one ALTER ROLE transaction, then write the .env"
  exit 0
fi

# ---------- values ----------
TS=$(date +%Y%m%d-%H%M%S)
if [ -n "$KEEP_DIR" ]; then mkdir -p "$KEEP_DIR" && chmod 700 "$KEEP_DIR"; W="$KEEP_DIR"; else W=$(mktemp -d); fi
# EXIT and signal traps only; shellcheck does not treat trap strings as calls (SC2317).
# shellcheck disable=SC2317
cleanup() {
  if [ -z "$KEEP_DIR" ] && [ -n "${W:-}" ] && [ -d "$W" ]; then
    local f
    for f in "$W"/*; do
      [ -f "$f" ] && { shred -u "$f" 2>/dev/null || rm -f "$f"; }
    done
    rmdir "$W" 2>/dev/null || true
  fi
  # After COMMIT and before mv, this file is the only copy of the new passwords.
  # Deleting it on a signal would make the roles unrecoverable. Print the path only.
  if [ "${announced_new:-0}" -eq 0 ] && [ -n "${NEW:-}" ] && [ -f "$NEW" ]; then
    announced_new=1
    err "kept ${NEW} (mode 600). Not deleted: if COMMIT already succeeded this is the only copy of the new passwords. Apply it or shred it yourself. Contents were not printed."
  fi
}
trap cleanup EXIT
trap 'cleanup; exit 129' HUP
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM
for r in "${ROLE_LIST[@]}"; do
  k=$(key_of "$r")
  if [ $MODE = rotate ]; then
    # tr strips CR too: openssl on Windows (Git Bash) ends its output with CRLF.
    openssl rand -hex 32 | tr -d '\r\n' > "$W/$r" || { err "value generation failed"; exit 3; }
    grep -qE '^[0-9a-f]{64}$' "$W/$r" || { err "generated value for $r is malformed (expected 64 hex chars)"; exit 3; }
  else
    env_get_to "$ROLLBACK" "$k" "$W/$r" || compose_default_to "$k" "$W/$r" || { err "no value for $k in backup nor compose default"; exit 3; }
  fi
  chmod 600 "$W/$r"
  # Single-quoted SQL literal. Reject quote, backslash, and anything else outside this class.
  value_is_safe "$W/$r" || { err "refusing value for ezkey_$r: characters outside [A-Za-z0-9_-]"; exit 3; }
done

# ---------- backup + prepare new .env (before SQL) ----------
BAK="${ENV_FILE}.bak.pre-$MODE-$TS"
if ! cp -p "$ENV_FILE" "$BAK" || ! chmod 600 "$BAK"; then
  err "backup failed"
  exit 3
fi
say "backup: $BAK"
announced_new=0
NEW="${ENV_FILE}.new.$$"
if [ $MODE = rotate ]; then
  awk -v dir="$W" -v roles="$ROLES" '
    BEGIN { n=split(roles, R, ","); for (i=1;i<=n;i++){ k="EZKEY_DB_" toupper(R[i]) "_PASSWORD"; f=dir "/" R[i]; getline v < f; close(f); val[k]=v; seen[k]=0 } }
    { split($0, a, "="); k=a[1]; if (k in val) { if (!seen[k]) { print k "=" val[k]; seen[k]=1 } ; next } print }
    END { for (k in val) if (!seen[k]) print k "=" val[k] }' "$ENV_FILE" > "$NEW" || { err "prepare .env failed"; rm -f "$NEW"; exit 3; }
else
  cp -p "$ROLLBACK" "$NEW" || { err "prepare .env failed"; exit 3; }
fi
chmod 600 "$NEW"

# ---------- one transaction ----------
if ! {
  printf "SET log_statement = 'none';\n"
  printf 'SET log_min_duration_statement = -1;\n'
  printf 'SET log_min_error_statement = PANIC;\n'
  printf 'SET log_transaction_sample_rate = 0;\n'
  printf 'SET log_min_duration_sample = -1;\n'
  printf 'BEGIN;\n'
  for r in "${ROLE_LIST[@]}"; do printf "ALTER ROLE ezkey_%s PASSWORD '%s';\n" "$r" "$(<"$W/$r")"; done
  [ -n "${EZKEY_ROTATE_TEST_FAIL_SQL:-}" ] && printf 'SELECT 1/0;\n'
  printf 'COMMIT;\n'
} | psql_in >/dev/null; then
  rm -f "$NEW"
  err "SQL session failed (exit 5). Guaranteed: the live .env was not replaced and the prepared file was removed. If this was a statement error before COMMIT, PostgreSQL aborted that transaction. Not guaranteed: that the server did not already commit, or that these session SETs kept the statement text out of the server log. If the roles may have changed, restore them with:"
  err "  $0 --docker --container $CONTAINER --env-file $ENV_FILE --roles $ROLES --rollback $BAK"
  exit 5
fi
say "roles updated (one transaction): $(printf 'ezkey_%s ' "${ROLE_LIST[@]}")"

# ---------- write .env (after COMMIT) ----------
if [ -n "${EZKEY_ROTATE_TEST_FAIL_ENV_WRITE:-}" ] || ! mv -f "$NEW" "$ENV_FILE"; then
  rm -f "$NEW"
  err "INCONSISTENT: roles changed but the .env was NOT written. Fix now with:"
  err "  $0 --docker --container $CONTAINER --env-file $ENV_FILE --roles $ROLES --rollback $BAK"
  exit 6
fi
chmod 600 "$ENV_FILE"

# ---------- equality check (no print) ----------
bad=0
for r in "${ROLE_LIST[@]}"; do
  k=$(key_of "$r"); env_get_to "$ENV_FILE" "$k" "$W/.chk" 2>/dev/null || : > "$W/.chk"
  if [ $MODE = rollback ] && ! grep -qE "^$k=" "$ROLLBACK"; then
    if grep -qE "^$k=" "$ENV_FILE"; then
      say "  $k: MISMATCH (should be absent)"
      bad=1
    else
      say "  $k: absent (compose default) ok"
    fi
  elif cmp -s "$W/.chk" "$W/$r"; then say "  $k: ok"; else say "  $k: MISMATCH"; bad=1; fi
  c=$(grep -cE "^$k=" "$ENV_FILE" || true); [ "$c" -le 1 ] || { say "  $k: duplicated ($c)"; bad=1; }
done
rm -f "$W/.chk"
[ $bad -eq 0 ] || { err "equality check failed"; exit 7; }

# ---------- optional login check over the Docker network ----------
if [ -n "$VERIFY_NET" ]; then
  IMG=$(docker inspect -f '{{.Config.Image}}' "$CONTAINER")
  for r in "${ROLE_LIST[@]}"; do
    # Password stays on stdin and in a mode-600 .pgpass. PGPASSFILE is a path, not the secret.
    u=$(printf 'postgres:5432:%s:ezkey_%s:%s\n' "$DB" "$r" "$(<"$W/$r")" \
      | docker run --rm -i --network "$VERIFY_NET" \
          -e PGPASSFILE=/tmp/.pgpass \
          -e PGUSER="ezkey_${r}" \
          -e PGDATABASE="$DB" \
          "$IMG" \
          sh -c 'umask 077; cat > "$PGPASSFILE"; chmod 600 "$PGPASSFILE"; psql -X -h postgres -w -Atc "select current_user"' \
      2>/dev/null)
    if [ "$u" = "ezkey_$r" ]; then
      say "  login ezkey_$r over $VERIFY_NET: ok"
    else
      say "  login ezkey_$r over $VERIFY_NET: FAILED"
      bad=1
    fi
  done
  [ $bad -eq 0 ] || { err "login check failed"; exit 7; }
fi

say "done. Recreate now (3-file compose, --no-deps --no-build --force-recreate), in order:"
say "  admin-api (ezkey_admin), auth-api (ezkey_auth), integration-api (ezkey_integration);"
say "  migration uses ezkey_migrate at its next run. Do not recreate postgres for this."
exit 0
