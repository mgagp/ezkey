/*
 * Harness: challenge 0..99 bound + display_ok (Christophe nits on #610) and
 * no-challenge waiting prompt format (#941 F1).
 */

#include "ezkey_challenge.h"

#include <security/pam_appl.h>
#include <stdio.h>
#include <stdlib.h>
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

static void expect_eq(const char *name, int got, int want) {
    if (got == want) {
        printf("[PASS] %s (got=%d)\n", name, got);
    } else {
        printf("[FAIL] %s (got=%d want=%d)\n", name, got, want);
        fails++;
    }
}

int main(void) {
    char buf[256];
    char tiny[8];

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

    /* #941: waiting prompt format (no-challenge path). */
    expect_eq("format_waiting null buf",
              ezkey_format_waiting_prompt(NULL, 64, "u"), -1);
    expect_eq("format_waiting empty user",
              ezkey_format_waiting_prompt(buf, sizeof(buf), ""), -1);
    expect_eq("format_waiting null user",
              ezkey_format_waiting_prompt(buf, sizeof(buf), NULL), -1);
    expect_eq("format_waiting ok",
              ezkey_format_waiting_prompt(buf, sizeof(buf), "testuser"), 0);
    expect_true("format_waiting contains Approve line",
                strstr(buf, "Approve this SSH login on your Ezkey device for "
                            "user testuser.") != NULL);
    expect_true("format_waiting contains Press Enter",
                strstr(buf, "Press Enter to wait for Ezkey device approval:") !=
                    NULL);
    expect_eq("format_waiting too small",
              ezkey_format_waiting_prompt(tiny, sizeof(tiny), "testuser"), -1);
    expect_eq("format_waiting strips controls in username",
              ezkey_format_waiting_prompt(buf, sizeof(buf), "u\nser"), 0);
    expect_true("format_waiting sanitized user is u_ser",
                strstr(buf, "user u_ser.") != NULL);
    expect_true("format_waiting no raw control from username",
                strstr(buf, "user u\nser") == NULL);

    printf("=== Results: %s ===\n", fails == 0 ? "all passed" : "FAILED");
    return fails == 0 ? 0 : 1;
}
