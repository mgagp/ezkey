/*
 * Diagnostics harness (#1011): attempt-line fields + signal-safe emit gate.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "ezkey_config.h"
#include "ezkey_diag.h"

static int failures = 0;

static void expect_true(const char *name, int cond) {
    if (!cond) {
        fprintf(stderr, "[FAIL] %s\n", name);
        failures++;
    } else {
        printf("[PASS] %s\n", name);
    }
}

static void expect_int(const char *name, int got, int want) {
    if (got != want) {
        fprintf(stderr, "[FAIL] %s: got=%d want=%d\n", name, got, want);
        failures++;
    } else {
        printf("[PASS] %s (got=%d)\n", name, got);
    }
}

int main(void) {
    EzkeyDiag diag;
    char line[EZKEY_DIAG_LINE_MAX];
    char syslog_buf[EZKEY_DIAG_LINE_MAX];
    size_t syslog_len = 0;
    volatile sig_atomic_t emitted = 0;
    int n;

    printf("=== ezkey_diag harness (#1011) ===\n");

    ezkey_diag_init(&diag, "testuser", 1);
    expect_int("init latency_ms unknown (-1)", (int)diag.latency_ms, -1);
    expect_int("init pam_rc EZKEY_PAM_AUTHINFO_UNAVAIL", diag.pam_rc,
               EZKEY_PAM_AUTHINFO_UNAVAIL);
    diag.result = "UNAVAIL";
    diag.pam_rc = EZKEY_PAM_AUTHINFO_UNAVAIL;
    diag.stage = "confirm";
    diag.reason = "empty_confirm";
    diag.confirm_ms = 1200;
    diag.curl_rc = -1;
    diag.http = -1;
    diag.api_ms = -1;

    n = ezkey_diag_format_attempt(line, sizeof(line), &diag);
    expect_true("format attempt >= 0", n >= 0);
    expect_true("line has stage=confirm", strstr(line, "stage=confirm") != NULL);
    expect_true("line has reason=empty_confirm",
                strstr(line, "reason=empty_confirm") != NULL);
    expect_true("line has confirm_ms=1200",
                strstr(line, "confirm_ms=1200") != NULL);
    expect_true("line has latency_ms=- (unknown)",
                strstr(line, "latency_ms=-") != NULL);
    expect_true("line has curl_rc=-", strstr(line, "curl_rc=-") != NULL);
    expect_true("line has http=-", strstr(line, "http=-") != NULL);
    expect_true("line has api_ms=-", strstr(line, "api_ms=-") != NULL);
    expect_true("line has lab_http=true", strstr(line, "lab_http=true") != NULL);
    expect_true("no secret-looking token field",
                strstr(line, "token=") == NULL &&
                    strstr(line, "secret") == NULL);

    /* B4: EZKEY_DIAG_LINE_MAX / user[128] truncation. */
    {
        char huge_user[400];
        memset(huge_user, 'u', sizeof(huge_user) - 1);
        huge_user[sizeof(huge_user) - 1] = '\0';
        ezkey_diag_init(&diag, huge_user, 0);
        expect_true("user truncated to <128 incl NUL",
                    strlen(diag.user) < sizeof(diag.user));
        diag.result = "SIGNAL";
        diag.reason = "signal";
        diag.stage = "challenge";
        diag.latency_ms = 999999;
        diag.confirm_ms = 999999;
        diag.api_ms = 999999;
        diag.curl_rc = 28;
        diag.http = 503;
        diag.timeout_seconds = 90;
        diag.auth_attempt_id = 123456789;
        n = ezkey_diag_format_attempt(line, sizeof(line), &diag);
        expect_true("worst-case format fits EZKEY_DIAG_LINE_MAX", n >= 0);
        expect_true("worst-case under 768", (size_t)n < EZKEY_DIAG_LINE_MAX);
        expect_int("dest_size=20 → -1",
                   ezkey_diag_format_attempt(line, 20, &diag), -1);
    }

    diag.result = "CONFIRM";
    diag.curl_rc = 28;
    diag.http = 503;
    diag.api_ms = 42;
    diag.stage = "create";
    diag.reason = "curl";
    n = ezkey_diag_format_attempt(line, sizeof(line), &diag);
    expect_true("format create UNAVAIL fields", n >= 0);
    expect_true("result=CONFIRM present", strstr(line, "result=CONFIRM") != NULL);
    expect_true("curl_rc=28", strstr(line, "curl_rc=28") != NULL);
    expect_true("http=503", strstr(line, "http=503") != NULL);
    expect_true("api_ms=42", strstr(line, "api_ms=42") != NULL);

    diag.result = "SIGNAL";
    diag.reason = "signal";
    diag.stage = "challenge";
    expect_int("syslog format ok",
               ezkey_diag_format_syslog(syslog_buf, sizeof(syslog_buf), &diag,
                                        &syslog_len),
               0);
    expect_true("syslog len > 0", syslog_len > 0);
    expect_true("syslog has PRI prefix", syslog_buf[0] == '<');
    expect_true("syslog body has SIGNAL",
                strstr(syslog_buf, "result=SIGNAL") != NULL);

    expect_int("emit claims gate",
               ezkey_diag_emit_signal_safe(-1, syslog_buf, syslog_len, &emitted),
               1);
    expect_int("emitted flag set", (int)emitted, 1);
    expect_int("second emit skipped",
               ezkey_diag_emit_signal_safe(-1, syslog_buf, syslog_len, &emitted),
               0);

    /* Sanitized user: space/= become underscore */
    ezkey_diag_init(&diag, "bad=user name", 0);
    n = ezkey_diag_format_attempt(line, sizeof(line), &diag);
    expect_true("sanitize user in attempt line", n >= 0);
    expect_true("no raw space in user field segment",
                strstr(line, "user=bad_user_name") != NULL);

    if (failures != 0) {
        fprintf(stderr, "=== Results: %d failed ===\n", failures);
        return 1;
    }
    printf("=== Results: all passed ===\n");
    return 0;
}
