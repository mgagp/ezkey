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
mode_bad=0
for f in "$T"/.env*; do
  [ -e "$f" ] || continue
  [ "$(stat -c '%A' "$f")" = "-rw-------" ] || mode_bad=1
done
if [ "$mode_bad" -eq 0 ]; then ok "all .env* files mode 600"; else ko "file modes"; fi
for f in "$T"/.env*; do shred -u "$f" 2>/dev/null || rm -f "$f"; done; rm -rf "$T"
echo "RESULT pass=$pass fail=$fail"; [ $fail -eq 0 ]
