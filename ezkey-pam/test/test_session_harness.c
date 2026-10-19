/*
 * Session harness (#907 F9 / #726): final-verdict + interrupt cancel + thresholds.
 */

#include <stdio.h>
#include <stdlib.h>

#include "ezkey_config.h"
#include "ezkey_session.h"

static int failures = 0;

static void expect_int(const char *name, int got, int want) {
    if (got != want) {
        fprintf(stderr, "[FAIL] %s: got=%d want=%d\n", name, got, want);
        failures++;
    } else {
        printf("[PASS] %s (got=%d)\n", name, got);
    }
}

static void expect_long(const char *name, long got, long want) {
    if (got != want) {
        fprintf(stderr, "[FAIL] %s: got=%ld want=%ld\n", name, got, want);
        failures++;
    } else {
        printf("[PASS] %s (got=%ld)\n", name, got);
    }
}

int main(void) {
    printf("=== ezkey_session harness (#907 / #726) ===\n");

    /* #726 new thresholds */
    expect_int("#726 WAIT_CHUNK_SECS 5", EZKEY_WAIT_CHUNK_SECS, 5);
    expect_int("#726 INTERRUPT_CANCEL_MAX 5", EZKEY_INTERRUPT_CANCEL_MAX, 5);
    expect_int("#726 AUTH_BUDGET_SECS 105", EZKEY_AUTH_BUDGET_SECS, 105);
    expect_int("#726 worst-case budget+interrupt_cancel",
               EZKEY_AUTH_BUDGET_SECS + EZKEY_INTERRUPT_CANCEL_MAX, 110);
    expect_int("#726 worst-case 110 < LoginGraceTime 120",
               (EZKEY_AUTH_BUDGET_SECS + EZKEY_INTERRUPT_CANCEL_MAX) < 120 ? 1
                                                                          : 0,
               1);
    expect_int("#726 LoginGraceTime headroom (120-110)",
               120 - (EZKEY_AUTH_BUDGET_SECS + EZKEY_INTERRUPT_CANCEL_MAX), 10);

    /* (b) final verdict — in-process only (OpenSSH forks each kbdint round) */
    expect_int("final DENIED", ezkey_result_is_final("DENIED"), 1);
    expect_int("final EXPIRED", ezkey_result_is_final("EXPIRED"), 1);
    expect_int("final TIMEOUT", ezkey_result_is_final("TIMEOUT"), 1);
    expect_int("final SUCCESS", ezkey_result_is_final("SUCCESS"), 1);
    expect_int("not final UNAVAIL", ezkey_result_is_final("UNAVAIL"), 0);
    expect_int("not final ERROR", ezkey_result_is_final("ERROR"), 0);
    expect_int("not final MAXTRIES", ezkey_result_is_final("MAXTRIES"), 0);
    expect_int("not final NULL", ezkey_result_is_final(NULL), 0);
    expect_int("not final empty", ezkey_result_is_final(""), 0);

    /* (c) bounded cancel — interrupt ignores exhausted rem; capped at 5 */
    expect_long("normal rem=8 api=10 → 8",
                ezkey_bounded_cancel_timeout(10, 8, 0), 8);
    expect_long("normal rem=0 → skip 0",
                ezkey_bounded_cancel_timeout(10, 0, 0), 0);
    expect_long("#726 interrupt rem=0 api=10 → 5 (cap)",
                ezkey_bounded_cancel_timeout(10, 0, 1), 5);
    expect_long("#726 interrupt rem=3 api=10 → 5 (cap, not rem)",
                ezkey_bounded_cancel_timeout(10, 3, 1), 5);
    expect_long("#726 interrupt api=3 → 3 (below cap)",
                ezkey_bounded_cancel_timeout(3, 0, 1), 3);
    expect_long("#726 interrupt api=60 → 5 (beyond threshold)",
                ezkey_bounded_cancel_timeout(60, 0, 1), 5);
    expect_long("#726 interrupt api=5 → 5 (at threshold)",
                ezkey_bounded_cancel_timeout(5, 0, 1), 5);

    expect_int("cancel when have+!accepted", ezkey_should_cancel_attempt(1, 0),
               1);
    expect_int("no cancel on SUCCESS", ezkey_should_cancel_attempt(1, 1), 0);
    expect_int("no cancel pre-create", ezkey_should_cancel_attempt(0, 0), 0);

    if (failures != 0) {
        fprintf(stderr, "=== Results: %d failed ===\n", failures);
        return 1;
    }
    printf("=== Results: all passed ===\n");
    return 0;
}
