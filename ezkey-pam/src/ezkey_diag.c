/*
 * ezkey_diag.c — attempt-line formatting + signal-safe /dev/log emit (#1011).
 */

#define _POSIX_C_SOURCE 200809L

#include "ezkey_diag.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <syslog.h>
#include <unistd.h>

#include "ezkey_config.h"
#include "ezkey_sanitize.h"

void ezkey_diag_init(EzkeyDiag *diag, const char *username, int lab_http) {
    if (diag == NULL) {
        return;
    }
    memset(diag, 0, sizeof(*diag));
    ezkey_sanitize_for_log(diag->user, sizeof(diag->user),
                           username != NULL ? username : "-");
    diag->auth_attempt_id = -1;
    diag->result = "UNAVAIL";
    diag->cancel_status = EZKEY_CANCEL_SKIPPED;
    /*
     * PAM_AUTHINFO_UNAVAIL is 9 on Linux. Kept numeric here so ezkey_diag.c
     * does not pull in pam.h; pam_ezkey.c overwrites with PAM_AUTHINFO_UNAVAIL
     * on the SIGNAL snapshot and final summary paths.
     */
    diag->pam_rc = EZKEY_PAM_AUTHINFO_UNAVAIL;
    diag->latency_ms = -1; /* unknown until measured (#1011 N3) */
    diag->lab_http = lab_http ? 1 : 0;
    diag->timeout_seconds = -1;
    diag->stage = "init";
    diag->reason = "-";
    diag->curl_rc = -1;
    diag->http = -1;
    diag->confirm_ms = -1;
    diag->api_ms = -1;
}

static void fmt_dash_long(char *buf, size_t buf_size, long value) {
    if (value < 0) {
        snprintf(buf, buf_size, "-");
    } else {
        snprintf(buf, buf_size, "%ld", value);
    }
}

static void fmt_dash_int(char *buf, size_t buf_size, int value) {
    if (value < 0) {
        snprintf(buf, buf_size, "-");
    } else {
        snprintf(buf, buf_size, "%d", value);
    }
}

int ezkey_diag_format_attempt(char *dest, size_t dest_size,
                              const EzkeyDiag *diag) {
    char id_buf[32];
    char timeout_buf[32];
    char curl_buf[32];
    char http_buf[32];
    char confirm_buf[32];
    char api_buf[32];
    char latency_buf[32];
    const char *result;
    const char *cancel_status;
    const char *stage;
    const char *reason;
    int n;

    if (dest == NULL || dest_size == 0 || diag == NULL) {
        return -1;
    }

    result = (diag->result != NULL && diag->result[0] != '\0') ? diag->result
                                                              : "UNAVAIL";
    cancel_status = diag->cancel_status;
    if (cancel_status == NULL || cancel_status[0] == '\0') {
        cancel_status = EZKEY_CANCEL_SKIPPED;
    } else if (strcmp(cancel_status, EZKEY_CANCEL_OK) != 0 &&
               strcmp(cancel_status, EZKEY_CANCEL_FAIL) != 0 &&
               strcmp(cancel_status, EZKEY_CANCEL_SKIPPED) != 0 &&
               strcmp(cancel_status, EZKEY_CANCEL_FINAL) != 0) {
        cancel_status = EZKEY_CANCEL_SKIPPED;
    }
    stage = (diag->stage != NULL && diag->stage[0] != '\0') ? diag->stage : "-";
    reason =
        (diag->reason != NULL && diag->reason[0] != '\0') ? diag->reason : "-";

    if (diag->auth_attempt_id > 0) {
        snprintf(id_buf, sizeof(id_buf), "%ld", diag->auth_attempt_id);
    } else {
        snprintf(id_buf, sizeof(id_buf), "-");
    }
    if (diag->timeout_seconds > 0) {
        snprintf(timeout_buf, sizeof(timeout_buf), "%d", diag->timeout_seconds);
    } else {
        snprintf(timeout_buf, sizeof(timeout_buf), "-");
    }
    /* latency_ms < 0 → "-" (unknown); never invent 0 (#1011 N3). */
    fmt_dash_long(latency_buf, sizeof(latency_buf), diag->latency_ms);
    fmt_dash_int(curl_buf, sizeof(curl_buf), diag->curl_rc);
    fmt_dash_long(http_buf, sizeof(http_buf), diag->http);
    fmt_dash_long(confirm_buf, sizeof(confirm_buf), diag->confirm_ms);
    fmt_dash_long(api_buf, sizeof(api_buf), diag->api_ms);

    n = snprintf(
        dest, dest_size,
        "%s: attempt user=%s authAttemptId=%s result=%s pam_code=%d "
        "latency_ms=%s lab_http=%s cancel=%s timeoutSeconds=%s "
        "stage=%s reason=%s curl_rc=%s http=%s confirm_ms=%s api_ms=%s",
        EZKEY_MODULE_NAME, diag->user[0] != '\0' ? diag->user : "-", id_buf,
        result, diag->pam_rc, latency_buf, diag->lab_http ? "true" : "false",
        cancel_status, timeout_buf, stage, reason, curl_buf, http_buf,
        confirm_buf, api_buf);
    if (n < 0 || (size_t)n >= dest_size) {
        return -1;
    }
    return n;
}

int ezkey_diag_format_syslog(char *dest, size_t dest_size, const EzkeyDiag *diag,
                             size_t *out_len) {
    char body[EZKEY_DIAG_LINE_MAX];
    int body_len;
    int n;

    if (dest == NULL || dest_size == 0 || diag == NULL || out_len == NULL) {
        return -1;
    }
    body_len = ezkey_diag_format_attempt(body, sizeof(body), diag);
    if (body_len < 0) {
        return -1;
    }
    /* RFC 3164-style PRI for authpriv.info — glibc accepts this on /dev/log. */
    n = snprintf(dest, dest_size, "<%d>%s", LOG_AUTHPRIV | LOG_INFO, body);
    if (n < 0 || (size_t)n >= dest_size) {
        return -1;
    }
    *out_len = (size_t)n;
    return 0;
}

int ezkey_diag_open_log_fd(void) {
    int fd;
    struct sockaddr_un addr;

    fd = socket(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        return -1;
    }
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, "/dev/log", sizeof(addr.sun_path) - 1);
    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
        int saved = errno;
        close(fd);
        errno = saved;
        return -1;
    }
    return fd;
}

void ezkey_diag_close_log_fd(int fd) {
    if (fd >= 0) {
        close(fd);
    }
}

int ezkey_diag_emit_signal_safe(int fd, const char *msg, size_t len,
                                volatile sig_atomic_t *emitted_flag) {
    if (emitted_flag == NULL) {
        return 0;
    }
    if (*emitted_flag != 0) {
        return 0;
    }
    *emitted_flag = 1;
    if (fd >= 0 && msg != NULL && len > 0) {
        ssize_t wrote = write(fd, msg, len);
        if (wrote < 0) {
            /* Best-effort only — ignore errno in signal context. */
        }
    }
    return 1;
}
