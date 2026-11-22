#!/usr/bin/env bash
# rotate-role-passwords.test.sh — rehearsal tests for rotate-role-passwords.sh.
# Needs a running throwaway Postgres container with the ezkey_* roles (rehearsal stack only).
# LIGHT scope (Marc 2026-11-22 13:55): usage, dry-run, rotate+login, idempotence, one rollback, no value printed, modes.
# Usage: ./scripts/db/rotate-role-passwords.test.sh <container> <compose-dir> <docker-network>
# Works on a COPY of the .env (never the live one). Prints names and results only.
set -uo pipefail; set +x; umask 077
CT="${1:?container}"; CD="${2:?compose dir}"; NET="${3:?network}"
S="$(cd "$(dirname "$0")" && pwd)/rotate-role-passwords.sh"
T=$(mktemp -d); cp -p "$CD/.env" "$T/.env"; cp "$CD/docker-compose.yml" "$T/docker-compose.yml"
pass=0; fail=0
ok(){ echo "PASS $*"; pass=$((pass+1)); }; ko(){ echo "FAIL $*"; fail=$((fail+1)); }
run(){ out=$("$S" --docker --container "$CT" --env-file "$T/.env" --compose-file "$T/docker-compose.yml" "$@" 2>&1); rc=$?; }
nohex(){ ! printf '%s' "$out" | grep -qE '[0-9a-f]{64}' && ! printf '%s' "$out" | grep -qE "PASSWORD '|ezkey_(admin|auth|migrate|integration)['\"]"; }
cnt(){ grep -cE "^EZKEY_DB_$1_PASSWORD=" "$T/.env" || true; }
ORIG="$T/.env.orig"; cp -p "$T/.env" "$ORIG"

# Server duration logging is the case a log_statement=none check does not see.
docker exec -i "$CT" psql -X -q -U postgres -d ezkey_db -v ON_ERROR_STOP=1 -c "ALTER SYSTEM SET log_min_duration_statement = 0;" >/dev/null
docker exec -i "$CT" psql -X -q -U postgres -d ezkey_db -c "SELECT pg_reload_conf();" >/dev/null
shown=$(docker exec -i "$CT" psql -X -q -U postgres -d ezkey_db -Atc "SHOW log_min_duration_statement;")
if [ "$shown" = "0" ]; then ok "server log_min_duration_statement=0"; else ko "log_min_duration_statement is '$shown'"; fi
log_from=$(date -u +%Y-%m-%dT%H:%M:%SZ)

run --bogus
if [ "$rc" -eq 2 ]; then ok "usage -> 2"; else ko "usage rc=$rc"; fi
run --dry-run
if [ "$rc" -eq 0 ] && nohex && cmp -s "$T/.env" "$ORIG"; then
  ok "dry-run -> 0, .env unchanged, no value printed"
else
  ko "dry-run rc=$rc"
fi
if docker logs --since 2m "$CT" 2>&1 | grep -q "ALTER ROLE"; then
  ko "ALTER ROLE found in server log"
else
  ok "server log has no ALTER ROLE statement"
fi
run --verify-network "$NET"
if [ "$rc" -eq 0 ] && nohex; then
  ok "rotate -> 0, login ok over network, no value printed"
else
  ko "rotate rc=$rc :: $(printf '%s' "$out" | grep -vE '[0-9a-f]{64}' | tail -3)"
fi
for R in MIGRATE ADMIN AUTH INTEGRATION; do [ "$(cnt "$R")" = 1 ] || ko "key $R count $(cnt "$R")"; done
ok "each key present once"
B1=$(find "$T" -maxdepth 1 -name '.env.bak.pre-rotate-*' -print | head -n 1)
cp -p "$T/.env" "$T/.env.after1"; sleep 1
run --verify-network "$NET"
if [ "$rc" -eq 0 ] && ! cmp -s "$T/.env" "$T/.env.after1"; then
  ok "second rotate -> 0, new values"
else
  ko "second rotate rc=$rc"
fi
for R in MIGRATE ADMIN AUTH INTEGRATION; do [ "$(cnt "$R")" = 1 ] || ko "idempotence key $R count $(cnt "$R")"; done
ok "idempotent: no duplicate keys"
if [ "$(wc -l < "$T/.env")" -eq "$(wc -l < "$T/.env.after1")" ]; then
  ok "line count stable"
else
  ko "line count changed"
fi
run --rollback "$B1" --verify-network "$NET"
if [ "$rc" -eq 0 ] && nohex; then
  ok "rollback to original (keys absent => compose defaults) -> 0, logins ok"
else
  ko "rollback rc=$rc :: $(printf '%s' "$out" | tail -3)"
fi
for R in MIGRATE ADMIN AUTH INTEGRATION; do [ "$(cnt "$R")" = 0 ] || ko "after rollback key $R count $(cnt "$R")"; done
ok "keys absent again after rollback"
badb="$T/.env.bak.quote"
cp -p "$ORIG" "$badb"
printf '%s\n' "EZKEY_DB_ADMIN_PASSWORD=unsafe'value" >> "$badb"
chmod 600 "$badb"
env_before="$T/.env.before-quote"
cp -p "$T/.env" "$env_before"
run --rollback "$badb"
if [ "$rc" -eq 3 ] && cmp -s "$T/.env" "$env_before" && ! printf '%s' "$out" | grep -q "unsafe"; then
  ok "rollback value with apostrophe refused (exit 3), .env unchanged, value not printed"
else
  ko "apostrophe rollback rc=$rc"
fi
sleep 1
logtmp=$(mktemp)
docker logs --since "$log_from" "$CT" >"$logtmp" 2>&1 || true
if grep -q "ALTER ROLE" "$logtmp"; then
  ko "ALTER ROLE found in server log while log_min_duration_statement=0"
elif grep -q "duration:" "$logtmp"; then
  ok "duration logging active and ALTER ROLE absent"
else
  ko "no duration log lines; log_min_duration_statement=0 check is inconclusive"
fi
shred -u "$logtmp" 2>/dev/null || rm -f "$logtmp"
if grep -q 'PGPASSWORD' "$S"; then
  ko "script still references PGPASSWORD"
else
  ok "verify login does not use PGPASSWORD"
fi
img=$(docker inspect -f '{{.Config.Image}}' "$CT")
if docker run --rm --user root --entrypoint sh "$img" -c 'test -f "$(pg_config --pkglibdir)/pg_stat_statements.so"'; then
  pre="ezkey-rotate-preload-$$"
  docker rm -f "$pre" >/dev/null 2>&1 || true
  if docker run -d --name "$pre" --network "$NET" \
      -e POSTGRES_PASSWORD=ezkey -e POSTGRES_DB=ezkey_db \
      "$img" -c shared_preload_libraries=pg_stat_statements -c log_statement=none >/dev/null; then
    ready=0
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
      if docker exec "$pre" pg_isready -U postgres -d ezkey_db >/dev/null 2>&1; then ready=1; break; fi
      sleep 1
    done
    penv=$(mktemp -d)
    chmod 700 "$penv"
    cp -p "$ORIG" "$penv/.env"
    chmod 600 "$penv/.env"
    cp "$T/docker-compose.yml" "$penv/docker-compose.yml"
    if [ "$ready" -eq 1 ]; then
      pout=$("$S" --docker --container "$pre" --env-file "$penv/.env" --compose-file "$penv/docker-compose.yml" --dry-run 2>&1)
      prc=$?
      if [ "$prc" -eq 8 ] && cmp -s "$penv/.env" "$ORIG" && ! printf '%s' "$pout" | grep -q "PASSWORD"; then
        ok "pg_stat_statements preload refused (exit 8), .env unchanged"
      else
        ko "preload refusal rc=$prc"
      fi
    else
      ko "preload postgres did not become ready"
    fi
    docker rm -f "$pre" >/dev/null 2>&1 || true
    rm -rf "$penv"
  else
    ko "could not start postgres with pg_stat_statements"
  fi
else
  ko "pg_stat_statements.so missing; preload refusal not tested"
fi
mode_bad=0
for f in "$T"/.env*; do
  [ -e "$f" ] || continue
  [ "$(stat -c '%A' "$f")" = "-rw-------" ] || mode_bad=1
done
if [ "$mode_bad" -eq 0 ]; then ok "all .env* files mode 600"; else ko "file modes"; fi
for f in "$T"/.env*; do shred -u "$f" 2>/dev/null || rm -f "$f"; done; rm -rf "$T"
echo "RESULT pass=$pass fail=$fail"; [ $fail -eq 0 ]
