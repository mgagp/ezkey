/*
 * Diagnostics harness (#1011 / #1016): attempt-line fields, signal-safe emit,
 * SOCK_NONBLOCK (+ fcntl fallback), double-buffer SIGNAL snapshot (N5),
 * wait-entry helper from real source (T1), live /dev/log when present (T2).
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
#include "ezkey_wait_entry.h"

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
 * #1016 T1 — exercise the real wait-entry helper (ezkey_wait_entry.h), the
 * same source pam_ezkey.c calls. check-source awk remains the mutation gate
 * against clearing ezkey_wait_abort at wait entry.
 */
static void test_wait_abort_real_helper(void) {
    volatile sig_atomic_t ezkey_wait_abort = 1;
    int skip_wait = 0;

    if (ezkey_wait_entry_abort_raised(&ezkey_wait_abort)) {
        skip_wait = 1;
    }
    expect_true("T1: abort before wait → skip wait (real helper)",
                skip_wait == 1);
    expect_int("T1: ezkey_wait_abort preserved (not cleared by helper)",
               (int)ezkey_wait_abort, 1);
    ezkey_wait_abort = 0;
    expect_int("T1: abort clear → helper returns 0",
               ezkey_wait_entry_abort_raised(&ezkey_wait_abort), 0);
    expect_int("T1: NULL abort_flag → 0",
               ezkey_wait_entry_abort_raised(NULL), 0);
}

/*
 * #1016 N5 — double-buffer publish must never expose a zero length while a
 * prior non-empty snapshot is the only complete one. The old pattern
 * (active_len = 0; format; active_len = n) fails this characterization.
 */
static void test_syslog_snap_no_zero_window(void) {
    EzkeyDiagSyslogSnap snap;
    EzkeyDiag diag;
    const char *msg = NULL;
    size_t len = 0;
    int next;
    size_t n = 0;

    ezkey_diag_init(&diag, "snapuser", 0);
    diag.result = "SIGNAL";
    diag.reason = "signal";
    diag.stage = "confirm";
    ezkey_diag_syslog_snap_clear(&snap);
    expect_int("N5: first publish ok",
               ezkey_diag_syslog_snap_publish(&snap, &diag), 0);
    ezkey_diag_syslog_snap_read(&snap, &msg, &len);
    expect_true("N5: published len > 0", len > 0);
    expect_true("N5: published body has SIGNAL",
                msg != NULL && strstr(msg, "result=SIGNAL") != NULL);

    /*
     * Simulate a mid-refresh into the inactive slot without flipping yet.
     * Reader must still see the previous complete snapshot (never len=0).
     */
    next = snap.slot != 0 ? 0 : 1;
    diag.stage = "challenge";
    expect_int("N5: format into inactive slot",
               ezkey_diag_format_syslog(snap.buf[next], sizeof(snap.buf[next]),
                                        &diag, &n),
               0);
    /* Deliberately do NOT set len[next] / flip slot yet. */
    ezkey_diag_syslog_snap_read(&snap, &msg, &len);
    expect_true("N5: mid-refresh still has prior len > 0", len > 0);
    expect_true("N5: mid-refresh still prior stage=confirm",
                msg != NULL && strstr(msg, "stage=confirm") != NULL);

    /*
     * Old single-buffer pattern (would fail this suite if used as the live
     * publish): zero the *active* length first → handler observes len=0.
     */
    {
        EzkeyDiagSyslogSnap bad;
        const char *bad_msg = NULL;
        size_t bad_len = 0;
        size_t tmp_n = 0;

        ezkey_diag_syslog_snap_clear(&bad);
        expect_int("N5: seed old-pattern snap",
                   ezkey_diag_syslog_snap_publish(&bad, &diag), 0);
        ezkey_diag_syslog_snap_read(&bad, &bad_msg, &bad_len);
        expect_true("N5: old-pattern seed len > 0", bad_len > 0);
        /* Buggy refresh: zero active length before rewrite. */
        bad.len[bad.slot != 0 ? 1 : 0] = 0;
        ezkey_diag_syslog_snap_read(&bad, &bad_msg, &bad_len);
        expect_true(
            "N5: old zero-first pattern exposes len=0 (bug this PR kills)",
            bad_len == 0);
        (void)tmp_n;
    }

    snap.len[next] = n;
    snap.slot = (sig_atomic_t)next;
    ezkey_diag_syslog_snap_read(&snap, &msg, &len);
    expect_true("N5: after publish flip stage=challenge",
                msg != NULL && strstr(msg, "stage=challenge") != NULL);
    expect_true("N5: after flip len > 0", len > 0);
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
        ssize_t nw = write(sv[0], junk, sizeof(junk));
        if (nw < 0) {
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
    expect_true("N1: emit returns in <200ms on full queue", elapsed_ms < 200);
    expect_int("N1: emitted flag set even when write drops", (int)emitted, 1);

    close(sv[0]);
    close(sv[1]);
}

/*
 * #1016 T2 — real /dev/log when a log daemon socket exists.
 * Set EZKEY_PAM_REQUIRE_DEVLOG=1 (CI Rocky runtime) to fail instead of skip.
 */
static void test_live_devlog(void) {
    const char *require = getenv("EZKEY_PAM_REQUIRE_DEVLOG");
    int log_fd = ezkey_diag_open_log_fd();

    if (log_fd >= 0) {
        int fl = fcntl(log_fd, F_GETFL);
        expect_true("T2/N1: live /dev/log fd is O_NONBLOCK",
                    fl >= 0 && (fl & O_NONBLOCK) != 0);
        {
            /* Best-effort write must not block (P-a / N1). */
            const char *probe =
                "<86>pam_ezkey: attempt user=- authAttemptId=- result=SIGNAL "
                "pam_code=9 latency_ms=- lab_http=false cancel=skipped "
                "timeoutSeconds=- stage=init reason=signal curl_rc=- http=- "
                "confirm_ms=- api_ms=-";
            struct timespec t0;
            struct timespec t1;
            long elapsed_ms;
            volatile sig_atomic_t emitted = 0;

            clock_gettime(CLOCK_MONOTONIC, &t0);
            (void)ezkey_diag_emit_signal_safe(log_fd, probe, strlen(probe),
                                              &emitted);
            clock_gettime(CLOCK_MONOTONIC, &t1);
            elapsed_ms = (t1.tv_sec - t0.tv_sec) * 1000L +
                         (t1.tv_nsec - t0.tv_nsec) / 1000000L;
            expect_true("T2: live /dev/log emit returns in <200ms",
                        elapsed_ms < 200);
        }
        ezkey_diag_close_log_fd(log_fd);
        return;
    }

    if (require != NULL && require[0] != '\0' && strcmp(require, "0") != 0) {
        fprintf(stderr,
                "[FAIL] T2: EZKEY_PAM_REQUIRE_DEVLOG set but /dev/log open "
                "failed: %s\n",
                strerror(errno));
        failures++;
        return;
    }
    printf(
        "[SKIP] T2/N1: /dev/log open failed (%s) — no log-daemon socket on "
        "this host. Gap: host `make test` without journald/rsyslog cannot "
        "exercise the real path; CI Rocky runtime sets "
        "EZKEY_PAM_REQUIRE_DEVLOG=1 after rsyslog is up (#1016 T2).\n",
        strerror(errno));
}

int main(void) {
    EzkeyDiag diag;
    char line[EZKEY_DIAG_LINE_MAX];
    char syslog_buf[EZKEY_DIAG_LINE_MAX];
    size_t syslog_len = 0;
    volatile sig_atomic_t emitted = 0;
    int n;

    printf("=== ezkey_diag harness (#1011 / #1016) ===\n");

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
        /* P9: pin STATUS literal 768 — mutating the macro alone must fail. */
        expect_int("P9: EZKEY_DIAG_LINE_MAX literal 768", EZKEY_DIAG_LINE_MAX,
                   768);
        expect_true("P9: worst-case under literal 768", (size_t)n < 768);
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

    test_syslog_snap_no_zero_window();
    test_live_devlog();
    test_emit_nonblock_full_queue();
    test_wait_abort_real_helper();

    if (failures != 0) {
        fprintf(stderr, "=== Results: %d failed ===\n", failures);
        return 1;
    }
    printf("=== Results: all passed ===\n");
    return 0;
}
