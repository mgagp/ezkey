#!/usr/bin/env bash
# Runs inside the pam-doctor sanitizer container. Do not invoke on the host.
# Reads /src (ezkey-pam, read-only) and writes logs under /out.
set -uo pipefail

export DEBIAN_FRONTEND=noninteractive
SKIP_VALGRIND="${EZKEY_PAM_SANITIZERS_SKIP_VALGRIND:-0}"

echo "[pam-doctor-sanitizers] installing gcc, PAM/curl/cJSON headers, valgrind…"
apt-get update -qq
apt-get install -y -qq --no-install-recommends \
  build-essential \
  gawk \
  libpam0g-dev \
  libcurl4-openssl-dev \
  libcjson-dev \
  valgrind \
  ca-certificates \
  >/tmp/apt-install.log 2>&1 || {
  echo "[pam-doctor-sanitizers] ERROR: apt install failed" >&2
  tail -n 40 /tmp/apt-install.log >&2 || true
  exit 2
}

{
  echo "image=${PAM_DOCTOR_IMAGE:-unset}"
  gcc --version | head -n 1
  valgrind --version
} > /out/versions.txt

rm -rf /work
mkdir -p /work
tar -C /src -cf - \
  --exclude=./build \
  --exclude=./.env \
  --exclude=./.git \
  . | tar -C /work -xf - || {
  echo "[pam-doctor-sanitizers] ERROR: failed to copy ezkey-pam into the container" >&2
  exit 2
}

cd /work || exit 2

HARNESSES=(
  test-filelog
  test-sanitize
  test-challenge
  test-conf
  test-budget
  test-session
  test-diag
  test-http
  test-decision
)

# Join the first CFLAGS assignment, including Make continuations. A raw
# backslash would be a shell escape and the compiler would treat "-g" as a file.
base_cflags="$(awk '
  /^CFLAGS = / && out == "" {
    sub(/^CFLAGS = /, "")
    out = $0
    if (out ~ /\\[[:space:]]*$/) {
      sub(/\\[[:space:]]*$/, "", out)
      next
    }
    exit
  }
  out != "" {
    sub(/^[[:space:]]+/, "")
    piece = $0
    if (piece ~ /\\[[:space:]]*$/) {
      sub(/\\[[:space:]]*$/, "", piece)
      out = out " " piece
      next
    }
    out = out " " piece
    exit
  }
  END { print out }
' Makefile)"
if [[ -z "$base_cflags" || "$base_cflags" == *"\\" ]]; then
  echo "[pam-doctor-sanitizers] ERROR: could not read CFLAGS from Makefile" >&2
  exit 2
fi

# -O1 keeps sanitizer stacks readable. Fortify and ASan both wrap libc; drop fortify
# on this build only. The baseline `make test` still checks the fortified module.
san_cflags="$(printf '%s\n' "$base_cflags" | sed 's/-O2/-O1/')"
san_cflags="${san_cflags} -g -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=address,undefined -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0"
dbg_cflags="${base_cflags} -g"

# LeakSanitizer and Valgrind need ptrace inside the container (see docker run flags).
export ASAN_OPTIONS="detect_leaks=1:halt_on_error=1:abort_on_error=1:symbolize=1"
export UBSAN_OPTIONS="halt_on_error=1:abort_on_error=1:print_stacktrace=1:symbolize=1"
export EZKEY_TEST_WRAPPER=""

SUMMARY=/out/summary.tsv
: > "$SUMMARY"
printf 'axis\tharness\texit\tsignal\tnote\n' >> "$SUMMARY"

run_logged() {
  local log="$1"
  local rc
  shift
  "$@" >"$log" 2>&1
  rc=$?
  return "$rc"
}

signal_of() {
  local log="$1"
  local found="none"
  if grep -E -q 'ERROR: AddressSanitizer|ERROR: LeakSanitizer|SUMMARY: AddressSanitizer|SUMMARY: LeakSanitizer' "$log"; then
    found="asan"
  fi
  if grep -E -q 'runtime error:|SUMMARY: UndefinedBehaviorSanitizer' "$log"; then
    if [[ "$found" == "none" ]]; then
      found="ubsan"
    else
      found="${found}+ubsan"
    fi
  fi
  if grep -E -q 'ERROR SUMMARY: [1-9]|definitely lost: [1-9]|indirectly lost: [1-9]|Invalid (read|write|free) of size|Conditional jump or move depends on uninitialised|Use of uninitialised value' "$log"; then
    if [[ "$found" == "none" ]]; then
      found="valgrind"
    else
      found="${found}+valgrind"
    fi
  fi
  printf '%s' "$found"
}

note_of() {
  local log="$1"
  local line
  line="$(grep -E -m1 'ERROR: AddressSanitizer|ERROR: LeakSanitizer|runtime error:|definitely lost: [1-9]|indirectly lost: [1-9]|Invalid (read|write) of size|\[FAIL\]|error:' "$log" || true)"
  line="${line//$'\t'/ }"
  printf '%s' "${line:0:220}"
}

record() {
  local axis="$1"
  local harness="$2"
  local rc="$3"
  local log="$4"
  local signal note
  signal="$(signal_of "$log")"
  note=""
  if [[ "$signal" != "none" || "$rc" -ne 0 ]]; then
    note="$(note_of "$log")"
  fi
  printf '%s\t%s\t%s\t%s\t%s\n' "$axis" "$harness" "$rc" "$signal" "$note" >> "$SUMMARY"
}

any_bad=0

echo "[pam-doctor-sanitizers] baseline make test (no sanitizer)…"
run_logged /out/baseline.log make EZKEY_TEST_WRAPPER= test
base_rc=$?
record baseline make-test "$base_rc" /out/baseline.log
if [[ "$base_rc" -ne 0 ]]; then
  echo "[pam-doctor-sanitizers] baseline make test failed (exit $base_rc); sanitizers not run" >&2
  any_bad=1
else
  make clean

  echo "[pam-doctor-sanitizers] ASan + UBSan…"
  : > /out/asan-ubsan-cflags.txt
  printf '%s\n' "$san_cflags" > /out/asan-ubsan-cflags.txt
  for h in "${HARNESSES[@]}"; do
    echo "[pam-doctor-sanitizers]   asan+ubsan $h"
    log="/out/asan-ubsan-${h}.log"
    run_logged "$log" make \
      CFLAGS="$san_cflags" \
      EZKEY_TEST_WRAPPER= \
      TEST_DIAG_TIMEOUT_SECS=60 \
      TEST_HTTP_TIMEOUT_SECS=90 \
      "$h"
    rc=$?
    record asan-ubsan "$h" "$rc" "$log"
    if [[ "$rc" -ne 0 || "$(signal_of "$log")" != "none" ]]; then
      any_bad=1
    fi
  done

  if [[ "$SKIP_VALGRIND" -eq 0 ]]; then
    make clean
    echo "[pam-doctor-sanitizers] Valgrind memcheck (separate non-sanitized build)…"
    vg="valgrind --tool=memcheck --leak-check=full --show-leak-kinds=definite,indirect --errors-for-leak-kinds=definite,indirect --track-origins=yes --error-exitcode=99 --num-callers=20"
    for h in "${HARNESSES[@]}"; do
      echo "[pam-doctor-sanitizers]   valgrind $h"
      log="/out/valgrind-${h}.log"
      run_logged "$log" make \
        CFLAGS="$dbg_cflags" \
        EZKEY_TEST_WRAPPER="$vg" \
        TEST_DIAG_TIMEOUT_SECS=120 \
        TEST_HTTP_TIMEOUT_SECS=240 \
        "$h"
      rc=$?
      record valgrind "$h" "$rc" "$log"
      if [[ "$rc" -ne 0 || "$(signal_of "$log")" != "none" ]]; then
        any_bad=1
      fi
    done
  fi
fi

{
  echo "# PAM sanitizer replay"
  echo
  echo "Image: ${PAM_DOCTOR_IMAGE:-unset}"
  echo
  echo '```'
  cat /out/versions.txt
  echo '```'
  echo
  echo "| Axis | Harness | Exit | Signal | Note |"
  echo "|------|---------|------|--------|------|"
  tail -n +2 "$SUMMARY" | while IFS="$(printf '\t')" read -r axis harness rc signal note; do
    note="${note//|/\\|}"
    printf '| %s | %s | %s | %s | %s |\n' "$axis" "$harness" "$rc" "$signal" "$note"
  done
} > /out/summary.md

chmod a+rX /out /out/* 2>/dev/null || true

echo "[pam-doctor-sanitizers] summary: /out/summary.md"
if [[ "$any_bad" -ne 0 ]]; then
  exit 1
fi
exit 0
