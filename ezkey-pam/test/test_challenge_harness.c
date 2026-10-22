/*
 * Harness: challenge 0..99 bound + display_ok (Christophe nits on #610) and
 * confirm / challenge prompt format (#941 F1 / #982 tiers 0–1).
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

    /* #975: show server challenge even when host did not request it. */
    expect_eq("prewait enrollment chal + conf false → show code",
              ezkey_prewait_prompt_mode(0, 42), 1);
    expect_eq("prewait requested + chal → show code",
              ezkey_prewait_prompt_mode(1, 7), 1);
    expect_eq("prewait no chal + conf false → skip post-create prompt",
              ezkey_prewait_prompt_mode(0, -1), 0);
    expect_eq("prewait requested + missing → fail-closed",
              ezkey_prewait_prompt_mode(1, -1), -1);
    expect_eq("prewait requested + out of range → fail-closed",
              ezkey_prewait_prompt_mode(1, 100), -1);
    expect_eq("prewait conf false + out of range → skip post-create prompt",
              ezkey_prewait_prompt_mode(0, 100), 0);

    /* #975 D4: empty/EOF is not confirmation; require trimmed "ok". */
    expect_eq("confirm NULL → reject", ezkey_prewait_response_confirmed(NULL),
              0);
    expect_eq("confirm empty → reject", ezkey_prewait_response_confirmed(""),
              0);
    expect_eq("confirm bare space → reject",
              ezkey_prewait_response_confirmed("   "), 0);
    expect_eq("confirm wrong token → reject",
              ezkey_prewait_response_confirmed("yes"), 0);
    expect_eq("confirm ok → accept", ezkey_prewait_response_confirmed("ok"), 1);
    expect_eq("confirm OK → accept", ezkey_prewait_response_confirmed("OK"), 1);
    expect_eq("confirm padded ok → accept",
              ezkey_prewait_response_confirmed("  ok  "), 1);
    expect_eq("confirm oko → reject", ezkey_prewait_response_confirmed("oko"),
              0);

    /* Production: single pam_prompt path — no pam_info fallback (Patrick). */
    expect_true("display_ok prompt SUCCESS",
                ezkey_challenge_display_ok(PAM_SUCCESS));
    expect_eq("display_ok prompt fail → reject (no pam_info fallback)",
              ezkey_challenge_display_ok(PAM_CONV_ERR), 0);

    /* #982 tier 0 / M1: pre-create wording (no phone-approve before create). */
    expect_eq("format_waiting null buf",
              ezkey_format_waiting_prompt(NULL, 64, "u"), -1);
    expect_eq("format_waiting empty user",
              ezkey_format_waiting_prompt(buf, sizeof(buf), ""), -1);
    expect_eq("format_waiting null user",
              ezkey_format_waiting_prompt(buf, sizeof(buf), NULL), -1);
    expect_eq("format_waiting ok",
              ezkey_format_waiting_prompt(buf, sizeof(buf), "testuser"), 0);
    expect_true("format_waiting contains SSH login for user",
                strstr(buf, "SSH login for user testuser.") != NULL);
    expect_true("format_waiting contains Type ok request confirm",
                strstr(buf, "Type ok and press Enter to request device "
                            "approval:") != NULL);
    expect_true("format_waiting has no Approve on your phone before create",
                strstr(buf, "Approve on your phone") == NULL);
    expect_true("format_waiting has no second-wait Type ok",
                strstr(buf, "Type ok and press Enter to wait") == NULL);

    /* #982 tier 1: challenge prompt — Enter only, no Type ok. */
    expect_eq("format_challenge ok",
              ezkey_format_challenge_prompt(buf, sizeof(buf), 42), 0);
    expect_true("format_challenge contains code 42",
                strstr(buf, "Ezkey challenge code: 42") != NULL);
    expect_true("format_challenge contains Approve on your phone Enter",
                strstr(buf, "Approve on your phone, then press Enter:") != NULL);
    expect_true("format_challenge has no Type ok",
                strstr(buf, "Type ok") == NULL);
    expect_eq("format_challenge out of range",
              ezkey_format_challenge_prompt(buf, sizeof(buf), 100), -1);
    expect_eq("format_waiting too small",
              ezkey_format_waiting_prompt(tiny, sizeof(tiny), "testuser"), -1);
    expect_eq("format_waiting strips controls in username",
              ezkey_format_waiting_prompt(buf, sizeof(buf), "u\nser"), 0);
    expect_true("format_waiting sanitized user is u_ser",
                strstr(buf, "user u_ser.") != NULL ||
                    strstr(buf, "SSH login for user u_ser.") != NULL);
    expect_true("format_waiting no raw control from username",
                strstr(buf, "user u\nser") == NULL);

    printf("=== Results: %s ===\n", fails == 0 ? "all passed" : "FAILED");
    return fails == 0 ? 0 : 1;
}
