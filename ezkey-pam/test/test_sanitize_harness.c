/*
 * Harness for ezkey_sanitize_for_log (M1 review): space, '=', controls, >=0x80.
 *
 * Build/run via: make test-sanitize
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <string.h>

#include "ezkey_sanitize.h"

static int failures;

static void expect_eq(const char *name, const char *got, const char *want) {
    if (strcmp(got, want) == 0) {
        printf("[PASS] %s\n", name);
    } else {
        printf("[FAIL] %s: got='%s' want='%s'\n", name, got, want);
        failures++;
    }
}

int main(void) {
    char buf[64];

    printf("=== ezkey_sanitize_for_log harness ===\n");

    ezkey_sanitize_for_log(buf, sizeof(buf), "alice");
    expect_eq("plain ascii", buf, "alice");

    ezkey_sanitize_for_log(buf, sizeof(buf), "a b=c");
    expect_eq("space and equals", buf, "a_b_c");

    ezkey_sanitize_for_log(buf, sizeof(buf), "a\nb\tc\r");
    expect_eq("controls CR/LF/TAB", buf, "a_b_c_");

    {
        char src[] = {'x', (char)0x7f, 'y', (char)0x80, (char)0xff, 'z', '\0'};
        ezkey_sanitize_for_log(buf, sizeof(buf), src);
        expect_eq("DEL and high bytes", buf, "x_y__z");
    }

    ezkey_sanitize_for_log(buf, sizeof(buf), NULL);
    expect_eq("NULL src", buf, "");

    ezkey_sanitize_for_log(buf, sizeof(buf), "user=evil authAttemptId=1");
    expect_eq("field injection attempt", buf, "user_evil_authAttemptId_1");

    printf("=== ezkey_sanitize_for_prompt harness (#941 nit b) ===\n");

    ezkey_sanitize_for_prompt(buf, sizeof(buf), "alice");
    expect_eq("prompt plain ascii", buf, "alice");

    ezkey_sanitize_for_prompt(buf, sizeof(buf), "a b=c");
    expect_eq("prompt keeps space and equals", buf, "a b=c");

    ezkey_sanitize_for_prompt(buf, sizeof(buf), "a\nb\tc\r");
    expect_eq("prompt controls CR/LF/TAB", buf, "a_b_c_");

    {
        char src[] = {'x', (char)0x7f, 'y', (char)0x80, (char)0xff, 'z', '\0'};
        ezkey_sanitize_for_prompt(buf, sizeof(buf), src);
        expect_eq("prompt DEL and high bytes", buf, "x_y__z");
    }

    ezkey_sanitize_for_prompt(buf, sizeof(buf), NULL);
    expect_eq("prompt NULL src", buf, "");

    printf("=== Results: %s ===\n", failures == 0 ? "all passed" : "FAILED");
    return failures == 0 ? 0 : 1;
}
