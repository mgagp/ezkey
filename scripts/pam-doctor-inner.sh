#!/usr/bin/env bash
# Runs inside the pam-doctor container. Do not invoke on the host.
# Reads /src (ezkey-pam, read-only) and writes raw reports under /out.
set -u

export DEBIAN_FRONTEND=noninteractive

echo "[pam-doctor-inner] installing clang-format, clang-tidy, cppcheck, PAM headers…"
apt-get update -qq
apt-get install -y -qq --no-install-recommends \
  clang-format \
  clang-tidy \
  cppcheck \
  libpam0g-dev \
  libcurl4-openssl-dev \
  libcjson-dev \
  ca-certificates \
  >/tmp/apt-install.log 2>&1 || {
  echo "[pam-doctor-inner] ERROR: apt install failed" >&2
  tail -n 40 /tmp/apt-install.log >&2 || true
  exit 1
}

{
  echo "image=${PAM_DOCTOR_IMAGE:-unset}"
  clang-format --version
  clang-tidy --version | head -n 1
  cppcheck --version
} > /out/versions.txt

echo "[pam-doctor-inner] clang-format (dry-run)…"
: > /out/clang-format.tsv
format_status=0
for f in /src/src/*.c /src/src/*.h; do
  [[ -f "$f" ]] || continue
  xml="$(clang-format --style=file --output-replacements-xml "$f" 2>/dev/null || true)"
  count="$(printf '%s\n' "$xml" | grep -c '<replacement ' || true)"
  if [[ "$count" -gt 0 ]]; then
    printf '%s\t%s\n' "$f" "$count" >> /out/clang-format.tsv
    format_status=1
  fi
done

echo "[pam-doctor-inner] cppcheck (warning,performance,portability)…"
cppcheck_template="$(printf '%s\t%s\t%s\t%s\t%s' '{file}' '{line}' '{severity}' '{id}' '{message}')"
# Findings go to stderr. Keep both streams in the raw file the curator reads.
cppcheck \
  --std=c99 \
  --quiet \
  --enable=warning,performance,portability \
  --inline-suppr \
  --suppress=missingIncludeSystem \
  --suppress=unmatchedSuppression \
  --library=posix \
  --template="$cppcheck_template" \
  /src/src \
  > /out/cppcheck.txt 2>&1 || true

echo "[pam-doctor-inner] clang-tidy…"
# Linux reference flags. No __APPLE__. Fortify is a compiler harden, not a tidy input.
tidy_files=()
for f in /src/src/*.c; do
  [[ -f "$f" ]] || continue
  tidy_files+=("$f")
done
clang-tidy \
  --config-file=/src/.clang-tidy \
  --header-filter='(ezkey_|pam_ezkey)' \
  "${tidy_files[@]}" -- \
  -std=c99 \
  -D_POSIX_C_SOURCE=200809L \
  -D_DEFAULT_SOURCE \
  -I/src/src \
  > /out/clang-tidy.txt 2> /out/clang-tidy.err || true

{
  echo "clang-format-files-with-replacements=$(wc -l < /out/clang-format.tsv | tr -d ' ')"
  echo "clang-format-status=${format_status}"
  echo "cppcheck-bytes=$(wc -c < /out/cppcheck.txt | tr -d ' ')"
  echo "clang-tidy-bytes=$(wc -c < /out/clang-tidy.txt | tr -d ' ')"
} > /out/tool-status.txt

echo "[pam-doctor-inner] done."
exit 0
