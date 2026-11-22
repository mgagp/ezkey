#!/usr/bin/env bash
# rotate-role-passwords.sh — rotate the Ezkey application database role passwords (#1017).
#
# Rotates ezkey_migrate / ezkey_admin / ezkey_auth / ezkey_integration (default) inside the
# Postgres container and writes the matching EZKEY_DB_<ROLE>_PASSWORD keys to the compose .env.
#
# Guarantees:
#   * no password value is ever printed (stdout/stderr), passed as a command argument, or put in
#     a process environment; values travel through files (mode 600) and stdin only;
#   * one transaction for all ALTER ROLE statements, with log_min_error_statement = PANIC so a
#     failing statement is never written to the server log; refuses to run unless log_statement=none;
#   * the .env is backed up first, the new .env is prepared BEFORE the ALTER ROLE and moved into
#     place only AFTER the COMMIT (SQL failure => .env untouched);
#   * idempotent .env update: keys are replaced in place or appended once;
#   * the script never recreates a service: it prints which ones to recreate.
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
#                            (a key absent from BACKUP means the compose default)
# Exit codes:
#   0 ok · 2 usage · 3 prerequisite missing · 4 log_statement is not 'none'
#   5 SQL failed (transaction rolled back, .env untouched) · 6 .env write failed AFTER COMMIT
#   7 post-write equality check failed
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
for r in "${ROLE_LIST[@]}"; do
  n=$(q "select count(*) from pg_roles where rolname='ezkey_$r'") || { err "cannot query roles"; exit 3; }
  [ "$n" = "1" ] || { err "role ezkey_$r does not exist"; exit 3; }
done
ls=$(q "show log_statement") || { err "cannot read log_statement"; exit 3; }
[ "$ls" = "none" ] || { err "log_statement is '$ls' (must be 'none'); refusing (ALTER ROLE would be logged)"; exit 4; }

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
# EXIT trap only; shellcheck does not treat that reference as a call (SC2317).
# shellcheck disable=SC2317
cleanup() { if [ -z "$KEEP_DIR" ]; then for f in "$W"/*; do [ -f "$f" ] && { shred -u "$f" 2>/dev/null || rm -f "$f"; }; done; rmdir "$W" 2>/dev/null; fi; }
trap cleanup EXIT
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
done

# ---------- backup + prepare new .env (before SQL) ----------
BAK="${ENV_FILE}.bak.pre-$MODE-$TS"
if ! cp -p "$ENV_FILE" "$BAK" || ! chmod 600 "$BAK"; then
  err "backup failed"
  exit 3
fi
say "backup: $BAK"
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
  printf 'SET log_min_error_statement = PANIC;\n'
  printf 'BEGIN;\n'
  for r in "${ROLE_LIST[@]}"; do printf "ALTER ROLE ezkey_%s PASSWORD '%s';\n" "$r" "$(<"$W/$r")"; done
  [ -n "${EZKEY_ROTATE_TEST_FAIL_SQL:-}" ] && printf 'SELECT 1/0;\n'
  printf 'COMMIT;\n'
} | psql_in >/dev/null; then
  rm -f "$NEW"; err "SQL failed: transaction rolled back, roles and .env unchanged (backup kept: $BAK)"; exit 5
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
    u=$(docker run --rm -i --network "$VERIFY_NET" "$IMG" sh -c "read -r PGPASSWORD; export PGPASSWORD; psql -X -h postgres -U ezkey_$r -d $DB -Atc 'select current_user'" < "$W/$r" 2>/dev/null)
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
