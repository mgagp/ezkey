/*
 * Budget / TTL planned-wait harness (Patrick #897 CHANGES).
 * Replaces header-only grep for EZKEY_AUTH_BUDGET_SECS 105 with executable
 * proofs, including rem too short for a legal TTL (UNAVAIL before create).
 */

#include <stdio.h>
#include <stdlib.h>

#include "ezkey_budget.h"
#include "ezkey_config.h"

static int failures = 0;

static void expect_int(const char *name, int got, int want) {
    if (got != want) {
        fprintf(stderr, "[FAIL] %s: got=%d want=%d\n", name, got, want);
        failures++;
    } else {
        printf("[PASS] %s (got=%d)\n", name, got);
    }
}

int main(void) {
    printf("=== ezkey_budget harness ===\n");

    expect_int("AUTH_BUDGET_SECS", EZKEY_AUTH_BUDGET_SECS, 105);
    expect_int("budget+15 under LoginGraceTime 120",
               EZKEY_AUTH_BUDGET_SECS + 15, 120);
    expect_int("MAX_WAIT 90", EZKEY_MAX_WAIT_TIMEOUT, 90);
    expect_int("MIN_TTL 15", EZKEY_MIN_TTL_SECONDS, 15);
    expect_int("CURL_SLACK 15", EZKEY_CURL_WAIT_SLACK, 15);

    expect_int("clamp 2→15", ezkey_clamp_wait_timeout(2), 15);
    expect_int("clamp 200→90", ezkey_clamp_wait_timeout(200), 90);
    expect_int("clamp 45→45", ezkey_clamp_wait_timeout(45), 45);

    /* Full budget: planned = min(90, 105-15) = 90 */
    expect_int("rem=105 wait=90 → planned 90",
               ezkey_planned_wait_from_rem(90, 105), 90);
    /* Applied-style shrink of conf already done by caller; rem still full */
    expect_int("rem=105 wait=30 → planned 30",
               ezkey_planned_wait_from_rem(30, 105), 30);

    /*
     * rem too short for server-legal TTL (≤ slack + MIN_TTL) → −1.
     * This is the UNAVAIL-before-create branch in pam_ezkey.c.
     */
    expect_int("rem=30 → -1 (UNAVAIL before create)",
               ezkey_planned_wait_from_rem(90, 30), -1);
    expect_int("rem=20 → -1", ezkey_planned_wait_from_rem(90, 20), -1);
    expect_int("rem=0 → -1", ezkey_planned_wait_from_rem(90, 0), -1);

    /* rem=31 → planned = 31-15 = 16 (≥ MIN_TTL) */
    expect_int("rem=31 wait=90 → planned 16",
               ezkey_planned_wait_from_rem(90, 31), 16);
    /* rem=45 wait=90 → 30 */
    expect_int("rem=45 wait=90 → planned 30",
               ezkey_planned_wait_from_rem(90, 45), 30);

    if (failures != 0) {
        fprintf(stderr, "=== Results: %d failed ===\n", failures);
        return 1;
    }
    printf("=== Results: all passed ===\n");
    return 0;
}
