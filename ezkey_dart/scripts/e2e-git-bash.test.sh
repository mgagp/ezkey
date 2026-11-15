#!/usr/bin/env bash
# Locks Git Bash handling in e2e.sh: MSYS_NO_PATHCONV and a Windows TMPDIR.
# Does not start Docker. EZKEY_E2E_PREPARE_ONLY stops the script after that setup.
#
#   ezkey_dart/scripts/e2e-git-bash.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="${ROOT}/ezkey_dart/scripts/e2e.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

echo "== non-Windows shell does not force MSYS_NO_PATHCONV or TMPDIR =="
out="${TMP}/linux.out"
env -u MSYS_NO_PATHCONV -u TMPDIR -u EZKEY_UNAME_S \
  EZKEY_E2E_PREPARE_ONLY=1 \
  bash "${SCRIPT}" >"${out}"
if grep -E -q '^MSYS_NO_PATHCONV=.+' "${out}"; then
  cat "${out}" >&2
  fail "non-Windows prepare set MSYS_NO_PATHCONV"
fi
if grep -E -q '^TMPDIR=.+' "${out}"; then
  cat "${out}" >&2
  fail "non-Windows prepare set TMPDIR"
fi

echo "== an existing Windows TMPDIR is kept =="
out="${TMP}/keep.out"
env -u MSYS_NO_PATHCONV \
  EZKEY_E2E_PREPARE_ONLY=1 \
  EZKEY_UNAME_S=MINGW64_NT-10.0 \
  TMPDIR='C:/Users/ezkey/AppData/Local/Temp' \
  bash "${SCRIPT}" >"${out}"
grep -F -q 'MSYS_NO_PATHCONV=1' "${out}" || fail "kept-TMPDIR path did not set MSYS_NO_PATHCONV"
grep -F -q 'TMPDIR=C:/Users/ezkey/AppData/Local/Temp' "${out}" || fail "existing Windows TMPDIR was replaced"

echo "== MSYS /tmp is replaced via cygpath =="
BIN="${TMP}/bin"
mkdir -p "${BIN}"
# cygpath -m must return a drive path. mkdir of C:/... is relative to cwd.
WIN_TMP='C:/Users/ezkey/AppData/Local/Temp'
cat >"${BIN}/cygpath" <<EOF
#!/usr/bin/env bash
printf '%s\n' '${WIN_TMP}'
EOF
chmod 755 "${BIN}/cygpath"
out="${TMP}/cygpath.out"
if ! (
  cd "${TMP}"
  env -u MSYS_NO_PATHCONV -u TMPDIR -u TEMP -u TMP \
    EZKEY_E2E_PREPARE_ONLY=1 \
    EZKEY_UNAME_S=MSYS_NT-10.0 \
    LOCALAPPDATA='C:\Users\ezkey\AppData\Local' \
    PATH="${BIN}:${PATH}" \
    bash "${SCRIPT}" >"${out}"
); then
  cat "${out}" >&2 || true
  fail "cygpath TMPDIR setup should succeed"
fi
grep -F -q 'MSYS_NO_PATHCONV=1' "${out}" || fail "cygpath path did not set MSYS_NO_PATHCONV"
grep -F -q "TMPDIR=${WIN_TMP}" "${out}" || fail "TMPDIR was not the cygpath result"
if [[ ! -d "${TMP}/${WIN_TMP}" ]]; then
  fail "Windows TMPDIR was not created"
fi

echo "== missing cygpath and Windows temp vars fail closed =="
err="${TMP}/missing.err"
if env -u MSYS_NO_PATHCONV -u TMPDIR -u TEMP -u TMP -u LOCALAPPDATA \
  EZKEY_E2E_PREPARE_ONLY=1 \
  EZKEY_UNAME_S=MINGW64_NT-10.0 \
  PATH="/usr/bin:/bin" \
  bash "${SCRIPT}" >"${TMP}/missing.out" 2>"${err}"; then
  fail "Git Bash without a Windows temp dir should fail"
fi
grep -F -q 'Git Bash needs a Windows TMPDIR' "${err}" || fail "missing-temp error text not found"

echo "e2e Git Bash prepare locks passed."
