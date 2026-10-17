/*
 * Harness: challenge 0..99 bound + display_ok (Christophe nits on #610).
 */

#include "ezkey_challenge.h"

#include <security/pam_appl.h>
#include <stdio.h>
#include <stdlib.h>

static int fails;

static void expect_true(const char *name, int cond) {
    if (cond) {
        printf("[PASS] %s\n", name);
    } else {
        printf("[FAIL] %s\n", name);
        fails++;
    }
}

static void expect_eq(const char *name, int got, int want) {
    if (got == want) {
        printf("[PASS] %s (got=%d)\n", name, got);
    } else {
        printf("[FAIL] %s (got=%d want=%d)\n", name, got, want);
        fails++;
    }
}

int main(void) {
    printf("=== ezkey_challenge harness ===\n");

    expect_eq("in_range(-1)", ezkey_challenge_in_range(-1), 0);
    expect_eq("in_range(0)", ezkey_challenge_in_range(0), 1);
    expect_eq("in_range(42)", ezkey_challenge_in_range(42), 1);
    expect_eq("in_range(99)", ezkey_challenge_in_range(99), 1);
    expect_eq("in_range(100)", ezkey_challenge_in_range(100), 0);
    expect_eq("in_range(1000)", ezkey_challenge_in_range(1000), 0);

    /* Production: single pam_prompt path — no pam_info fallback (Patrick). */
    expect_true("display_ok prompt SUCCESS",
                ezkey_challenge_display_ok(PAM_SUCCESS));
    expect_eq("display_ok prompt fail → reject (no pam_info fallback)",
              ezkey_challenge_display_ok(PAM_CONV_ERR), 0);

    printf("=== Results: %s ===\n", fails == 0 ? "all passed" : "FAILED");
    return fails == 0 ? 0 : 1;
}
