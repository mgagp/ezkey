/*
 * Decision harness (#1016 cycle 2b P1) — pins wait/HTTP/URL/xferinfo contracts
 * from ezkey_decision.h so auth-decision mutations fail make test.
 */

#include "ezkey_challenge.h"
#include "ezkey_config.h"
#include "ezkey_decision.h"
#include "ezkey_diag.h"

#include <stdio.h>
#include <string.h>

static int fails;

static void expect_true(const char *name, int cond) {
    if (cond) {
        printf("[PASS] %s\n", name);
    } else {
        printf("[FAIL] %s\n", name);
        fails++;
    }
}

static void expect_int(const char *name, int got, int want) {
    if (got == want) {
        printf("[PASS] %s (got=%d)\n", name, got);
    } else {
        printf("[FAIL] %s (got=%d want=%d)\n", name, got, want);
        fails++;
    }
}

static void expect_str(const char *name, const char *got, const char *want) {
    if (got != NULL && want != NULL && strcmp(got, want) == 0) {
        printf("[PASS] %s (got=%s)\n", name, got);
    } else {
        printf("[FAIL] %s (got=%s want=%s)\n", name,
               got != NULL ? got : "(null)", want != NULL ? want : "(null)");
        fails++;
    }
}

static void test_http_rc(void) {
    expect_int("P1: HTTP 201 → SUCCESS", ezkey_pam_rc_for_http(201, 201),
               PAM_SUCCESS);
    expect_int("P1: HTTP 200 → SUCCESS", ezkey_pam_rc_for_http(200, 200),
               PAM_SUCCESS);
    expect_int("P1: HTTP 404 → UNAVAIL (not SUCCESS)",
               ezkey_pam_rc_for_http(404, 201), PAM_AUTHINFO_UNAVAIL);
    expect_int("P1: HTTP 500 → UNAVAIL", ezkey_pam_rc_for_http(500, 200),
               PAM_AUTHINFO_UNAVAIL);
    expect_true("P1: mutation always-SUCCESS would fail 404 check",
                ezkey_pam_rc_for_http(404, 201) != PAM_SUCCESS);
}

static void test_url_policy(void) {
    expect_int("P1: https allowed lab_http=0",
               ezkey_api_url_allowed_url("https://example.invalid", 0), 1);
    expect_int("P1: http rejected lab_http=0",
               ezkey_api_url_allowed_url("http://example.invalid", 0), 0);
    expect_int("P1: http allowed lab_http=1",
               ezkey_api_url_allowed_url("http://example.invalid", 1), 1);
    expect_int("P1: empty rejected", ezkey_api_url_allowed_url("", 1), 0);
    expect_int("P1: NULL rejected", ezkey_api_url_allowed_url(NULL, 1), 0);
    expect_int("P1: ftp rejected",
               ezkey_api_url_allowed_url("ftp://example.invalid", 1), 0);
    expect_true("P1: always-1 mutation fails http+lab0",
                ezkey_api_url_allowed_url("http://x", 0) == 0);
}

static void test_wait_status(void) {
    int pam_rc = -1;
    const char *summary = NULL;

    expect_int("P1 M20: ACCEPTED terminal",
               ezkey_wait_status_decide("ACCEPTED", &pam_rc, &summary), 1);
    expect_int("P1 M20: ACCEPTED → PAM_SUCCESS", pam_rc, PAM_SUCCESS);
    expect_str("P1 M20: ACCEPTED → SUCCESS", summary, "SUCCESS");
    expect_true("P1 M20: ACCEPTED must not be AUTH_ERR",
                pam_rc != PAM_AUTH_ERR);

    expect_int("P1: REJECTED terminal",
               ezkey_wait_status_decide("REJECTED", &pam_rc, &summary), 1);
    expect_int("P1: REJECTED → AUTH_ERR", pam_rc, PAM_AUTH_ERR);
    expect_str("P1: REJECTED → DENIED", summary, "DENIED");
    expect_true("P1: REJECTED must not be SUCCESS", pam_rc != PAM_SUCCESS);

    expect_int("P1: EXPIRED terminal",
               ezkey_wait_status_decide("EXPIRED", &pam_rc, &summary), 1);
    expect_str("P1: EXPIRED summary", summary, "EXPIRED");
    expect_int("P1: EXPIRED → AUTH_ERR", pam_rc, PAM_AUTH_ERR);

    expect_int("P1: INVALID → DENIED",
               ezkey_wait_status_decide("INVALID", &pam_rc, &summary), 1);
    expect_str("P1: INVALID summary", summary, "DENIED");

    expect_int("P1: unknown FOO → DENIED",
               ezkey_wait_status_decide("FOO", &pam_rc, &summary), 1);
    expect_str("P1: FOO summary", summary, "DENIED");
    expect_int("P1: FOO → AUTH_ERR", pam_rc, PAM_AUTH_ERR);

    expect_int("P1: PENDING continue",
               ezkey_wait_status_decide("PENDING", &pam_rc, &summary), 0);
    expect_int("P1: READ continue",
               ezkey_wait_status_decide("READ", &pam_rc, &summary), 0);
    expect_int("P1: missing status continue",
               ezkey_wait_status_decide(NULL, &pam_rc, &summary), 0);
}

static void test_xferinfo(void) {
    volatile sig_atomic_t flag = 0;

    expect_int("P1: xferinfo clear → 0", ezkey_xferinfo_should_abort(NULL), 0);
    expect_int("P1: xferinfo flag0 → 0",
               ezkey_xferinfo_should_abort((void *)&flag), 0);
    flag = 1;
    expect_int("P1: xferinfo flag1 → abort 1",
               ezkey_xferinfo_should_abort((void *)&flag), 1);
}

static void test_confirm_ok(void) {
    expect_int("P1: confirm ok required", ezkey_prewait_response_confirmed("ok"),
               1);
    expect_int("P1: confirm empty rejected",
               ezkey_prewait_response_confirmed(""), 0);
    expect_true("P1: skipping confirm would accept empty",
                ezkey_prewait_response_confirmed("") == 0);
}

static void test_soft_constants(void) {
    /* P9: pin STATUS-promised literals, not only the macros. */
    expect_int("P9: EZKEY_MAX_ENTER_SECS is 25", EZKEY_MAX_ENTER_SECS, 25);
    expect_int("P9: EZKEY_DIAG_LINE_MAX is 768", EZKEY_DIAG_LINE_MAX, 768);
}

int main(void) {
    printf("=== ezkey_decision harness (#1016 cycle 2b P1/P9) ===\n");
    test_http_rc();
    test_url_policy();
    test_wait_status();
    test_xferinfo();
    test_confirm_ok();
    test_soft_constants();
    if (fails != 0) {
        printf("=== Results: %d failed ===\n", fails);
        return 1;
    }
    printf("=== Results: all passed ===\n");
    return 0;
}
