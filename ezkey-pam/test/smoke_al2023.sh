#!/bin/bash
# AL2023 smoke: verify the ABI-matched pam_ezkey.so is present and loadable.
set -euo pipefail

PAM_SO="${EZKEY_PAM_SO:-/usr/lib64/security/pam_ezkey.so}"
PASS=0
FAIL=0

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

echo "=== Ezkey PAM AL2023 smoke ==="

if [ -f "$PAM_SO" ]; then
    ok "pam_ezkey.so exists at $PAM_SO"
else
    fail "pam_ezkey.so not found at $PAM_SO"
fi

if command -v ldd >/dev/null 2>&1; then
    if ldd "$PAM_SO" >/tmp/ldd.out 2>&1 && ! grep -q "not found" /tmp/ldd.out; then
        ok "ldd resolves all shared libraries"
        cat /tmp/ldd.out
    else
        fail "ldd reported missing libraries"
        cat /tmp/ldd.out || true
    fi
else
    fail "ldd not available"
fi

# Prefer a tiny C dlopen check when a compiler is present; otherwise skip.
if command -v gcc >/dev/null 2>&1; then
    cat > /tmp/dlopen_check.c <<'EOF'
#include <dlfcn.h>
#include <stdio.h>
int main(void) {
    void *h = dlopen("/usr/lib64/security/pam_ezkey.so", RTLD_NOW);
    if (!h) {
        fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 1;
    }
    dlclose(h);
    return 0;
}
EOF
    if gcc -o /tmp/dlopen_check /tmp/dlopen_check.c -ldl \
        && /tmp/dlopen_check; then
        ok "dlopen(pam_ezkey.so) succeeded"
    else
        fail "dlopen(pam_ezkey.so) failed"
    fi
else
    echo "[INFO] gcc not installed; skipping dlopen check (ldd is enough for smoke)"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]
