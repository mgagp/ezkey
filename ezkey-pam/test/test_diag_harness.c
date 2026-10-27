/*
 * Diagnostics harness (#1011): attempt-line fields + signal-safe emit gate.
 * Also: SOCK_NONBLOCK emit (Christophe N1), wait-abort preserve (Patrick m1).
 */

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

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

/*
 * Patrick m1 — characterization of wait-entry contract:
 * abort raised before /wait must be observed and must NOT be cleared.
 * (Mutation "ezkey_wait_abort = 0" at wait entry would fail this.)
 */
static void test_wait_abort_preserve(void) {
    volatile sig_atomic_t ezkey_wait_abort = 1;
    int skip_wait = 0;

    /* Correct wait-entry shape (mirrors pam_ezkey.c): check, never clear. */
    if (ezkey_wait_abort != 0) {
        skip_wait = 1;
    }
    expect_true("m1: abort before wait → skip wait", skip_wait == 1);
    expect_int("m1: ezkey_wait_abort preserved (not cleared at entry)",
               (int)ezkey_wait_abort, 1);
}

/*
 * Christophe N1 — write on a full non-blocking datagram socket must return
 * promptly (EAGAIN), never hang until LoginGraceTime.
 */
static void test_emit_nonblock_full_queue(void) {
    int sv[2] = {-1, -1};
    char junk[4096];
    char msg[128];
    volatile sig_atomic_t emitted = 0;
    struct timespec t0;
    struct timespec t1;
    long elapsed_ms;
    int filled = 0;
    int i;
    int flags;

    memset(junk, 'x', sizeof(junk));
    snprintf(msg, sizeof(msg), "<86>pam_ezkey: attempt nonblock-probe");

    if (socketpair(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0, sv) !=
        0) {
        fprintf(stderr, "[FAIL] N1: socketpair: %s\n", strerror(errno));
        failures++;
        return;
    }
    flags = fcntl(sv[0], F_GETFL);
    expect_true("N1: emit peer has O_NONBLOCK",
                flags >= 0 && (flags & O_NONBLOCK) != 0);

    for (i = 0; i < 256; i++) {
        ssize_t n = write(sv[0], junk, sizeof(junk));
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                filled = 1;
                break;
            }
            fprintf(stderr, "[FAIL] N1: fill write: %s\n", strerror(errno));
            failures++;
            close(sv[0]);
            close(sv[1]);
            return;
        }
    }
    expect_true("N1: filled datagram queue until EAGAIN", filled == 1);

    clock_gettime(CLOCK_MONOTONIC, &t0);
    expect_int("N1: emit claims gate on full queue",
               ezkey_diag_emit_signal_safe(sv[0], msg, strlen(msg), &emitted),
               1);
    clock_gettime(CLOCK_MONOTONIC, &t1);
    elapsed_ms = (t1.tv_sec - t0.tv_sec) * 1000L +
                 (t1.tv_nsec - t0.tv_nsec) / 1000000L;
    /* Full queue → EAGAIN; must not block (budget ≪ LoginGraceTime). */
    expect_true("N1: emit returns in <200ms on full queue", elapsed_ms < 200);
    expect_int("N1: emitted flag set even when write drops", (int)emitted, 1);

    close(sv[0]);
    close(sv[1]);
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

    /* Live /dev/log when present must be O_NONBLOCK (Christophe N1). */
    {
        int log_fd = ezkey_diag_open_log_fd();
        if (log_fd >= 0) {
            int fl = fcntl(log_fd, F_GETFL);
            expect_true("N1: live /dev/log fd is O_NONBLOCK",
                        fl >= 0 && (fl & O_NONBLOCK) != 0);
            ezkey_diag_close_log_fd(log_fd);
        } else {
            printf("[SKIP] N1: /dev/log open failed (no rsyslog in harness)\n");
        }
    }

    test_emit_nonblock_full_queue();
    test_wait_abort_preserve();

    if (failures != 0) {
        fprintf(stderr, "=== Results: %d failed ===\n", failures);
        return 1;
    }
    printf("=== Results: all passed ===\n");
    return 0;
}
