/*
 * pam_ezkey.c - Ezkey PAM Module
 *
 * Linux PAM module that authorizes SSH (or other PAM services) via the Ezkey
 * Integration API. Runtime settings come from /etc/security/pam_ezkey.conf
 * (root:root, mode 0600). Docker demos write that file from the entrypoint —
 * sshd does not pass container ENV into PAM.
 *
 * Flow (#982 tiers 0–1):
 *   1. PAM_PROMPT_ECHO_ON pre-create confirm (trimmed "ok" required)
 *   2. POST /api/v1/auth-attempts  (Basic auth, userIdentifier = Linux username,
 *      optional ttlSeconds = planned wait; servers that ignore the field stay OK)
 *   3. Optional PAM_PROMPT_ECHO_ON challenge line (code visible; Enter continues —
 *      no second "ok"; OpenSSH flush before /wait — #941 / #982)
 *   4. GET  /api/v1/auth-attempts/{id}/wait  (timeout aligned on applied TTL,
 *      monotonic budget — never host clock vs server expiresAt)
 *   5. PAM_SUCCESS when wait status is ACCEPTED
 *   6. Best-effort POST …/cancel on any non-ACCEPTED exit after create
 *
 * Fail-closed: API / transport / config failures never return PAM_SUCCESS or
 * PAM_IGNORE. See STATUS.md §2.1, §2.4, and § M1.
 *
 * Observability (M1 / #907 / #1011): authpriv start line at call begin
 * (before create), then exactly one attempt summary per authenticate
 * including timeoutSeconds plus stage/reason/curl_rc/http/confirm_ms/api_ms.
 * result distinguishes SUCCESS / EXPIRED / TIMEOUT / DENIED / MAXTRIES /
 * UNAVAIL / CONFIRM / SIGNAL / …. cancel= is only ok|fail|skipped|final
 * (EZKEY_CANCEL_*). Best-effort SIGNAL line when sshd SIGTERMs the child
 * during a blocking prompt (async-signal-safe write to /dev/log). In-process
 * final-verdict marker is fail-closed for the same PAM handle only; under
 * OpenSSH portable each keyboard-interactive round forks and _exit's, so
 * sshd must enforce a single try via Match MaxAuthTries (option A) — do not
 * treat PAM_MAXTRIES as effective across OpenSSH rounds. Debug chatter goes
 * only to /var/log/ezkey-pam/ files.
 */

#define _DEFAULT_SOURCE
#define _POSIX_C_SOURCE 200809L

#include <curl/curl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/stat.h>
#include <time.h>
#include <cjson/cJSON.h>
#include <unistd.h>
#include <syslog.h>
#include <security/pam_modules.h>
#include <security/pam_ext.h>
#include <security/pam_appl.h>
#include "ezkey_budget.h"
#include "ezkey_challenge.h"
#include "ezkey_conf.h"
#include "ezkey_config.h"
#include "ezkey_decision.h"
#include "ezkey_diag.h"
#include "ezkey_filelog.h"
#include "ezkey_http.h"
#include "ezkey_sanitize.h"
#include "ezkey_session.h"
#include "ezkey_wait_entry.h"

#define EZKEY_URL_MAX EZKEY_CONF_URL_MAX
#define EZKEY_KEY_MAX EZKEY_CONF_KEY_MAX
#define EZKEY_TITLE_MAX EZKEY_CONF_TITLE_MAX
#define EZKEY_MESSAGE_MAX EZKEY_CONF_MESSAGE_MAX

#define EZKEY_PAM_DATA_FINAL "ezkey_session_final"

/* Non-heap marker for pam_set_data(EZKEY_PAM_DATA_FINAL) — same process only. */
static char ezkey_final_marker = 1;

/* Set by SIGTERM handler during /wait; curl xferinfo aborts (#907 R5). */
static volatile sig_atomic_t ezkey_wait_abort = 0;
/* Non-zero only while chunked /wait is armed — handler aborts curl then. */
static volatile sig_atomic_t ezkey_in_wait = 0;
/* Set after authpriv attempt line is emitted (normal path or signal-safe). */
static volatile sig_atomic_t ezkey_diag_emitted = 0;

/* Per-authenticate diagnostics (PAM child is single-threaded). */
static EzkeyDiag ezkey_diag;
static int ezkey_diag_log_fd = -1;
/* Double-buffered SIGNAL snapshot — never zero active len mid-refresh (#1016 N5). */
static EzkeyDiagSyslogSnap ezkey_diag_syslog_snap;
/* Auth start clock for SIGNAL latency_ms (updated outside the handler). */
static struct timespec ezkey_auth_started_ts;
static int ezkey_auth_started_valid = 0;

PAM_EXTERN int pam_sm_authenticate(pam_handle_t *pamh, int flags, int argc,
                                   const char **argv);
PAM_EXTERN int pam_sm_setcred(pam_handle_t *pamh, int flags, int argc,
                              const char **argv);

static void log_pam_info(pam_handle_t *pamh, const EzkeyConfig *cfg,
                         const char *message);
static void log_pam_error(pam_handle_t *pamh, const EzkeyConfig *cfg,
                          const char *message);
static void parse_module_args(int argc, const char **argv, EzkeyConfig *cfg);
static int load_config_file(const char *path, EzkeyConfig *cfg);
static int ezkey_authenticate_user_api(pam_handle_t *pamh, EzkeyConfig *cfg,
                                       const char *username,
                                       long *auth_attempt_id_out,
                                       int *timeout_seconds_out,
                                       const struct timespec *auth_started,
                                       const char **summary_result_out,
                                       const char **cancel_status_out);
static void ezkey_secure_wipe(void *ptr, size_t len);
static void wipe_cfg_secrets(EzkeyConfig *cfg);
static void log_attempt_start(const char *username, long auth_attempt_id,
                              int timeout_seconds);
static void log_attempt_summary(void);
static void ezkey_diag_refresh_signal_snapshot(const char *stage);
static void ezkey_sig_install(struct sigaction *old_out, int *installed_out);
static void ezkey_sig_restore(const struct sigaction *old, int installed);
static long elapsed_ms(const struct timespec *start);
static long remaining_budget_secs(const struct timespec *auth_started);
static int compute_planned_wait(const EzkeyConfig *cfg,
                                const struct timespec *auth_started);
static char *build_create_body(const EzkeyConfig *cfg, const char *username,
                               int ttl_seconds);
/**
 * Show msg via PAM_PROMPT_ECHO_ON.
 *
 * @param require_ok 1 = trimmed "ok" required (pre-create); 0 = any Enter
 *                   continues (challenge path — #982 tier 1)
 * @return 0 on success; -1 conversation/display failure; -2 empty/wrong
 *         confirm (require_ok only); -3 Enter over EZKEY_MAX_ENTER_SECS;
 *         -4 budget too small
 */
static int prompt_echo_on(pam_handle_t *pamh, const EzkeyConfig *cfg,
                          const struct timespec *auth_started, char *msg,
                          size_t msg_len, int require_ok, const char *stage,
                          const char *prompt_fail_dbg,
                          const char *enter_over_dbg,
                          const char *budget_before_dbg,
                          const char *budget_after_dbg,
                          const char *no_conv_dbg, int *interrupted_out);
static const char *ezkey_cancel_best_effort(pam_handle_t *pamh,
                                            const EzkeyConfig *cfg,
                                            const char *api_url,
                                            const char *userpwd,
                                            long auth_attempt_id,
                                            long timeout_secs);
static int ezkey_xferinfo_abort(void *clientp, curl_off_t dltotal,
                                curl_off_t dlnow, curl_off_t ultotal,
                                curl_off_t ulnow);

static void ezkey_secure_wipe(void *ptr, size_t len) {
    if (ptr == NULL || len == 0) {
        return;
    }
#if defined(__GLIBC__) || defined(__FreeBSD__) || defined(__OpenBSD__)
    explicit_bzero(ptr, len);
#else
    {
        volatile unsigned char *p = (volatile unsigned char *)ptr;
        while (len-- > 0) {
            *p++ = 0;
        }
    }
#endif
}

/**
 * Load key=value settings from path. Requires a regular file owned by root:root
 * with mode exactly 0600 or 0400 (owner-readable; rejects 0000 and anything
 * with group/other bits). Parsing / defaults live in ezkey_conf.c.
 *
 * @return 0 on success
 * @return -1 if the file could not be opened (errno set)
 * @return -2 if ownership/permissions are insecure (errno = EPERM) or not a
 *         regular file (errno = EINVAL)
 */
static int load_config_file(const char *path, EzkeyConfig *cfg) {
    int fd;
    FILE *fp;
    struct stat st;
    mode_t mode;
    int parse_rc;

    if (path == NULL || *path == '\0') {
        errno = EINVAL;
        return -1;
    }
    /*
     * O_NONBLOCK: a FIFO with no writer must not block open() before S_ISREG
     * (#1054 / Christophe). O_NOFOLLOW still refuses symlinks first.
     */
    fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (fd < 0) {
        return -1;
    }
    if (fstat(fd, &st) != 0) {
        int saved = errno;
        close(fd);
        errno = saved;
        return -1;
    }
    if (!S_ISREG(st.st_mode)) {
        close(fd);
        errno = EINVAL;
        return -2;
    }
    if (st.st_uid != 0 || st.st_gid != 0) {
        close(fd);
        errno = EPERM;
        return -2;
    }
    /* Exact owner-readable modes only — root bypasses DAC, so 0000 must fail. */
    mode = st.st_mode & 0777;
    if (mode != 0600 && mode != 0400) {
        close(fd);
        errno = EPERM;
        return -2;
    }

    fp = fdopen(fd, "r");
    if (fp == NULL) {
        int saved = errno;
        close(fd);
        errno = saved;
        return -1;
    }
    parse_rc = ezkey_conf_load_fp(fp, cfg);
    fclose(fp);
    return parse_rc;
}

static void parse_module_args(int argc, const char **argv, EzkeyConfig *cfg) {
    int i;

    for (i = 0; i < argc; i++) {
        if (strcmp(argv[i], "debug") == 0) {
            cfg->debug = 1;
        } else if (strncmp(argv[i], "conf=", 5) == 0) {
            strncpy(cfg->config_file, argv[i] + 5, sizeof(cfg->config_file) - 1);
            cfg->config_file[sizeof(cfg->config_file) - 1] = '\0';
        } else if (strncmp(argv[i], "wait_timeout=", 13) == 0) {
            /* Reuse conf apply so clamp/floor match the conf-file path. */
            ezkey_conf_apply_key(cfg, "wait_timeout", argv[i] + 13);
        }
    }
}

/**
 * Planned /wait duration before create from remaining monotonic budget.
 * See ezkey_planned_wait_from_rem (UNAVAIL before create when rem too small).
 */
static int compute_planned_wait(const EzkeyConfig *cfg,
                                const struct timespec *auth_started) {
    if (cfg == NULL) {
        return -1;
    }
    return ezkey_planned_wait_from_rem(cfg->wait_timeout,
                                       remaining_budget_secs(auth_started));
}

static void wipe_cfg_secrets(EzkeyConfig *cfg) {
    ezkey_config_wipe_secrets(cfg);
}

/**
 * Debug chatter only — never writes to authpriv syslog (M1: start + attempt).
 */
static void log_pam_info(pam_handle_t *pamh, const EzkeyConfig *cfg,
                         const char *message) {
    char safe[2048];

    (void)pamh;
    if (cfg == NULL || !cfg->debug) {
        return;
    }
    ezkey_sanitize_for_log(safe, sizeof(safe), message);
    ezkey_filelog_append(EZKEY_LOG_OUT_NAME, safe);
}

static void log_pam_error(pam_handle_t *pamh, const EzkeyConfig *cfg,
                          const char *message) {
    char safe[2048];

    (void)pamh;
    if (cfg == NULL || !cfg->debug) {
        return;
    }
    ezkey_sanitize_for_log(safe, sizeof(safe), message);
    ezkey_filelog_append(EZKEY_LOG_ERR_NAME, safe);
}

static void log_pam_error_debug(pam_handle_t *pamh, const EzkeyConfig *cfg,
                                const char *message) {
    log_pam_error(pamh, cfg, message);
}

/**
 * Authpriv start line at the beginning of authenticate (#907), before create.
 * Format:
 *   pam_ezkey: start user=<u> authAttemptId=<id|-> timeoutSeconds=<n|->
 */
static void log_attempt_start(const char *username, long auth_attempt_id,
                              int timeout_seconds) {
    char user_safe[128];
    char id_buf[32];
    char timeout_buf[32];

    ezkey_sanitize_for_log(user_safe, sizeof(user_safe),
                           username != NULL ? username : "-");
    if (auth_attempt_id > 0) {
        snprintf(id_buf, sizeof(id_buf), "%ld", auth_attempt_id);
    } else {
        snprintf(id_buf, sizeof(id_buf), "-");
    }
    if (timeout_seconds > 0) {
        snprintf(timeout_buf, sizeof(timeout_buf), "%d", timeout_seconds);
    } else {
        snprintf(timeout_buf, sizeof(timeout_buf), "-");
    }
    syslog(LOG_INFO, "%s: start user=%s authAttemptId=%s timeoutSeconds=%s",
           EZKEY_MODULE_NAME, user_safe, id_buf, timeout_buf);
}

/**
 * Authpriv summary at end of authentication (M1 / #907 / #1011).
 * Uses ezkey_diag (stage/reason/curl_rc/http/confirm_ms/api_ms). Skips when a
 * signal-safe emit already wrote the line during a blocking prompt. Never
 * includes secrets, proof tokens, HTTP bodies, Basic credentials, or IPs.
 */
static void log_attempt_summary(void) {
    char line[EZKEY_DIAG_LINE_MAX];

    if (ezkey_diag_emitted != 0) {
        return;
    }
    if (ezkey_diag.result == NULL || ezkey_diag.result[0] == '\0') {
        if (ezkey_diag.pam_rc == PAM_SUCCESS) {
            ezkey_diag.result = "SUCCESS";
        } else if (ezkey_diag.pam_rc == PAM_MAXTRIES) {
            ezkey_diag.result = "MAXTRIES";
        } else if (ezkey_diag.pam_rc == PAM_AUTH_ERR) {
            ezkey_diag.result = "DENIED";
        } else if (ezkey_diag.pam_rc == PAM_AUTHINFO_UNAVAIL) {
            ezkey_diag.result = "UNAVAIL";
        } else if (ezkey_diag.pam_rc == PAM_USER_UNKNOWN) {
            ezkey_diag.result = "UNKNOWN_USER";
        } else {
            ezkey_diag.result = "ERROR";
        }
    }
    if (ezkey_diag_format_attempt(line, sizeof(line), &ezkey_diag) < 0) {
        return;
    }
    syslog(LOG_INFO, "%s", line);
    ezkey_diag_emitted = 1;
}

/**
 * Rebuild the SIGNAL snapshot for a kill outside /wait (#1011).
 * Must run outside the signal handler (snprintf is not async-signal-safe).
 * latency_ms uses the real auth elapsed when known; otherwise `-`.
 */
static void ezkey_diag_refresh_signal_snapshot(const char *stage) {
    EzkeyDiag snap = ezkey_diag;

    if (stage != NULL && stage[0] != '\0') {
        snap.stage = stage;
    }
    snap.result = "SIGNAL";
    snap.reason = "signal";
    snap.pam_rc = PAM_AUTHINFO_UNAVAIL;
    snap.cancel_status = EZKEY_CANCEL_SKIPPED;
    if (ezkey_auth_started_valid) {
        snap.latency_ms = elapsed_ms(&ezkey_auth_started_ts);
    } else {
        snap.latency_ms = -1;
    }
    (void)ezkey_diag_syslog_snap_publish(&ezkey_diag_syslog_snap, &snap);
}

/**
 * SIGTERM: during /wait only set abort (curl xferinfo + cancel path).
 * Everywhere else emit best-effort SIGNAL then restore SIG_DFL and re-raise
 * so OpenSSH does not leave a hung [pam] child (atomicio retries EINTR).
 * errno is saved/restored (async-signal-safe ops only).
 */
static void ezkey_sigterm_handler(int signo) {
    int saved_errno = errno;

    (void)signo;
    if (ezkey_in_wait != 0) {
        ezkey_wait_abort = 1;
        errno = saved_errno;
        return;
    }
    {
        const char *msg = "";
        size_t len = 0;

        ezkey_diag_syslog_snap_read(&ezkey_diag_syslog_snap, &msg, &len);
        (void)ezkey_diag_emit_signal_safe(ezkey_diag_log_fd, msg, len,
                                          &ezkey_diag_emitted);
    }
    {
        struct sigaction sa_dfl;

        sa_dfl.sa_handler = SIG_DFL;
        sa_dfl.sa_flags = 0;
        sigemptyset(&sa_dfl.sa_mask);
        (void)sigaction(SIGTERM, &sa_dfl, NULL);
    }
    errno = saved_errno;
    raise(SIGTERM);
}

static void ezkey_sig_install(struct sigaction *old_out, int *installed_out) {
    struct sigaction sa_new;

    if (installed_out != NULL) {
        *installed_out = 0;
    }
    memset(&sa_new, 0, sizeof(sa_new));
    sa_new.sa_handler = ezkey_sigterm_handler;
    sigemptyset(&sa_new.sa_mask);
    if (sigaction(SIGTERM, &sa_new, old_out) == 0) {
        if (installed_out != NULL) {
            *installed_out = 1;
        }
    }
}

static void ezkey_sig_restore(const struct sigaction *old, int installed) {
    if (installed && old != NULL) {
        (void)sigaction(SIGTERM, old, NULL);
    }
}

static int ezkey_xferinfo_abort(void *clientp, curl_off_t dltotal,
                                curl_off_t dlnow, curl_off_t ultotal,
                                curl_off_t ulnow) {
    (void)dltotal;
    (void)dlnow;
    (void)ultotal;
    (void)ulnow;
    return ezkey_xferinfo_should_abort(clientp);
}

static long elapsed_ms(const struct timespec *start) {
    struct timespec now;
    long sec;
    long nsec;

    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
        return 0;
    }
    sec = now.tv_sec - start->tv_sec;
    nsec = now.tv_nsec - start->tv_nsec;
    if (nsec < 0) {
        sec -= 1;
        nsec += 1000000000L;
    }
    return sec * 1000L + nsec / 1000000L;
}

/**
 * Echo-visible PAM prompt (#982 tier 0 / Patrick B1).
 *
 * Pre-create (require_ok): trimmed "ok" + EZKEY_MAX_ENTER_SECS (25) + rem
 * budget. Challenge path (require_ok=0): any Enter; **no** 25 s Enter cap —
 * only the remaining auth-budget bound so a late Enter after phone approve
 * still reaches /wait (does not cancel an already-approved attempt).
 *
 * @param stage fixed vocabulary for #1011 (`confirm` or `challenge`)
 */
static int prompt_echo_on(pam_handle_t *pamh, const EzkeyConfig *cfg,
                          const struct timespec *auth_started, char *msg,
                          size_t msg_len, int require_ok, const char *stage,
                          const char *prompt_fail_dbg,
                          const char *enter_over_dbg,
                          const char *budget_before_dbg,
                          const char *budget_after_dbg,
                          const char *no_conv_dbg, int *interrupted_out) {
    char *resp = NULL;
    int prc;
    const void *conv_item = NULL;
    struct timespec enter_started;
    long enter_elapsed_ms;
    long enter_elapsed_s;
    long rem;

    if (interrupted_out != NULL) {
        *interrupted_out = 0;
    }
    ezkey_diag.stage = (stage != NULL && stage[0] != '\0') ? stage : "confirm";
    if (pam_get_item(pamh, PAM_CONV, &conv_item) != PAM_SUCCESS ||
        conv_item == NULL) {
        ezkey_diag.reason = "no_conv";
        log_pam_error_debug(pamh, cfg, no_conv_dbg);
        ezkey_secure_wipe(msg, msg_len);
        return -1;
    }
    rem = remaining_budget_secs(auth_started);
    if (rem <= (long)EZKEY_MIN_WAIT_SECS + (long)EZKEY_CURL_WAIT_SLACK) {
        ezkey_diag.reason = "budget";
        log_pam_error_debug(pamh, cfg, budget_before_dbg);
        ezkey_secure_wipe(msg, msg_len);
        return -4;
    }
    /* Snapshot before prompt: SIGTERM here emits SIGNAL then re-raises (B1). */
    ezkey_diag_refresh_signal_snapshot(ezkey_diag.stage);
    clock_gettime(CLOCK_MONOTONIC, &enter_started);
    prc = pam_prompt(pamh, PAM_PROMPT_ECHO_ON, &resp, "%s", msg);
    enter_elapsed_ms = elapsed_ms(&enter_started);
    enter_elapsed_s = enter_elapsed_ms / 1000L;
    if (require_ok) {
        ezkey_diag.confirm_ms = enter_elapsed_ms;
    }
    ezkey_secure_wipe(msg, msg_len);
    if (!ezkey_challenge_display_ok(prc)) {
        if (resp != NULL) {
            ezkey_secure_wipe(resp, strlen(resp));
            free(resp);
        }
        if (interrupted_out != NULL) {
            *interrupted_out = 1;
        }
        ezkey_diag.reason = "prompt_fail";
        log_pam_error_debug(pamh, cfg, prompt_fail_dbg);
        return -1;
    }
    if (require_ok && !ezkey_prewait_response_confirmed(resp)) {
        if (resp != NULL) {
            ezkey_secure_wipe(resp, strlen(resp));
            free(resp);
        }
        ezkey_diag.reason = "empty_confirm";
        log_pam_error_debug(pamh, cfg,
                            "pre-wait confirm missing or not ok "
                            "(empty/EOF is not sufficient)");
        return -2;
    }
    if (resp != NULL) {
        ezkey_secure_wipe(resp, strlen(resp));
        free(resp);
    }
    /* 25 s Enter cap: pre-create only (#982 Patrick B1). */
    if (require_ok && enter_elapsed_s > (long)EZKEY_MAX_ENTER_SECS) {
        ezkey_diag.reason = "enter_over";
        log_pam_error_debug(pamh, cfg, enter_over_dbg);
        return -3;
    }
    rem = remaining_budget_secs(auth_started);
    if (rem <= (long)EZKEY_CURL_WAIT_SLACK + (long)EZKEY_MIN_WAIT_SECS) {
        ezkey_diag.reason = "budget";
        log_pam_error_debug(pamh, cfg, budget_after_dbg);
        return -4;
    }
    return 0;
}

/**
 * Seconds left on the single auth budget (EZKEY_AUTH_BUDGET_SECS from auth
 * start). Floor at 0. Patrick P3: wait and Enter must fit under LoginGraceTime.
 */
static long remaining_budget_secs(const struct timespec *auth_started) {
    long elapsed_s;
    long rem;

    if (auth_started == NULL) {
        return EZKEY_AUTH_BUDGET_SECS;
    }
    elapsed_s = elapsed_ms(auth_started) / 1000L;
    rem = (long)EZKEY_AUTH_BUDGET_SECS - elapsed_s;
    return rem > 0 ? rem : 0;
}

static void strip_trailing_slash(char *url) {
    size_t len;

    if (url == NULL) {
        return;
    }
    len = strlen(url);
    while (len > 0 && url[len - 1] == '/') {
        url[len - 1] = '\0';
        len--;
    }
}

static char *build_create_body(const EzkeyConfig *cfg, const char *username,
                               int ttl_seconds) {
    cJSON *root = cJSON_CreateObject();
    char *printed;
    char message[EZKEY_MESSAGE_MAX + 64];

    if (root == NULL) {
        return NULL;
    }
    cJSON_AddStringToObject(root, "userIdentifier", username);
    cJSON_AddBoolToObject(root, "challengeRequested",
                          cfg->challenge_requested ? 1 : 0);
    /* Optional for older servers that ignore unknown fields (#895 PR-B). */
    if (ttl_seconds >= EZKEY_MIN_TTL_SECONDS) {
        cJSON_AddNumberToObject(root, "ttlSeconds", ttl_seconds);
    }
    if (cfg->context_title[0] != '\0') {
        cJSON_AddStringToObject(root, "contextTitle", cfg->context_title);
    }
    if (cfg->context_message[0] != '\0') {
        cJSON_AddStringToObject(root, "contextMessage", cfg->context_message);
    } else {
        snprintf(message, sizeof(message),
                 "Linux user '%s' is requesting SSH access.", username);
        cJSON_AddStringToObject(root, "contextMessage", message);
    }
    printed = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    return printed;
}

/**
 * HTTP helper. When abort_flag is non-NULL, install xferinfo so SIGTERM during
 * /wait can abort curl in-process (OpenSSH kills the auth child on disconnect;
 * pam_end cleanup never runs in that child — #907 R5).
 */
static int ezkey_http(const char *url, const char *userpwd, const char *post_body,
                      long timeout_seconds, EzkeyHttpBuf *buf, long *http_code,
                      volatile sig_atomic_t *abort_flag) {
    CURL *curl;
    CURLcode res;
    struct curl_slist *headers = NULL;

    curl = curl_easy_init();
    if (!curl) {
        return CURLE_FAILED_INIT;
    }
    if (ezkey_http_apply_transport_guards(curl) != 0) {
        curl_easy_cleanup(curl);
        return CURLE_FAILED_INIT;
    }
    curl_easy_setopt(curl, CURLOPT_URL, url);
    /*
     * CURLOPT_USERPWD lets libcurl keep an internal copy of ikey:skey until
     * easy cleanup (freed without wiping — #1016 S2). Building Authorization
     * ourselves would still leave a Base64 header copy in curl_slist. Keep
     * USERPWD; module buffers are wiped; curl-internal copies are not.
     */
    curl_easy_setopt(curl, CURLOPT_USERPWD, userpwd);
    curl_easy_setopt(curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_http_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, buf);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeout_seconds);
    if (abort_flag != NULL) {
        curl_easy_setopt(curl, CURLOPT_XFERINFOFUNCTION, ezkey_xferinfo_abort);
        curl_easy_setopt(curl, CURLOPT_XFERINFODATA, (void *)abort_flag);
        curl_easy_setopt(curl, CURLOPT_NOPROGRESS, 0L);
    }
    if (post_body != NULL) {
        headers = curl_slist_append(headers, "Content-Type: application/json");
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(curl, CURLOPT_POST, 1L);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, post_body);
    }
    res = curl_easy_perform(curl);
    if (res == CURLE_OK && http_code != NULL) {
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, http_code);
    }
    if (headers != NULL) {
        curl_slist_free_all(headers);
    }
    curl_easy_cleanup(curl);
    return res;
}

/**
 * Best-effort cancel after abandon. Uses a short curl timeout taken from the
 * remaining monotonic budget (≤ api_timeout / MAX_API). Failure is debug-logged
 * only — never changes the PAM outcome (409 already-final → cancel=final).
 *
 * @return EZKEY_CANCEL_OK, FAIL, SKIPPED, or FINAL only (no pam_rc effect).
 */
static const char *ezkey_cancel_best_effort(pam_handle_t *pamh,
                                            const EzkeyConfig *cfg,
                                            const char *api_url,
                                            const char *userpwd,
                                            long auth_attempt_id,
                                            long timeout_secs) {
    char cancel_url[EZKEY_URL_MAX + 96];
    EzkeyHttpBuf buf;
    long http_code = 0;
    int curl_rc;
    char msg[256];

    if (auth_attempt_id <= 0 || api_url == NULL || userpwd == NULL) {
        return EZKEY_CANCEL_SKIPPED;
    }
    if (timeout_secs < 1) {
        log_pam_error_debug(pamh, cfg,
                            "cancel skipped: no monotonic budget left");
        return EZKEY_CANCEL_SKIPPED;
    }
    snprintf(cancel_url, sizeof(cancel_url),
             "%s/api/v1/auth-attempts/%ld/cancel", api_url, auth_attempt_id);
    ezkey_http_buf_init(&buf);
    if (buf.data == NULL) {
        log_pam_error_debug(pamh, cfg, "cancel skipped: out of memory");
        return EZKEY_CANCEL_SKIPPED;
    }
    curl_rc = ezkey_http(cancel_url, userpwd, "{}", timeout_secs, &buf,
                         &http_code, NULL);
    ezkey_http_buf_free(&buf);
    if (curl_rc != CURLE_OK) {
        snprintf(msg, sizeof(msg), "cancel best-effort failed: %s",
                 curl_easy_strerror(curl_rc));
        log_pam_error_debug(pamh, cfg, msg);
        return EZKEY_CANCEL_FAIL;
    }
    snprintf(msg, sizeof(msg),
             "cancel best-effort authAttemptId=%ld HTTP %ld", auth_attempt_id,
             http_code);
    log_pam_info(pamh, cfg, msg);
    if (http_code >= 200 && http_code < 300) {
        return EZKEY_CANCEL_OK;
    }
    /* Already final (e.g. late device approve) — observability only; pam_rc unchanged. */
    if (http_code == 409) {
        return EZKEY_CANCEL_FINAL;
    }
    return EZKEY_CANCEL_FAIL;
}

/**
 * Map unexpected HTTP status to PAM (shared with decision harness — #1016 P1).
 */
static int pam_rc_for_http(long http_code, long expected) {
    return ezkey_pam_rc_for_http(http_code, expected);
}

/**
 * Scheme gate (Patrick): https always OK; http only when lab_http=true.
 */
static int ezkey_api_url_allowed(const EzkeyConfig *cfg) {
    if (cfg == NULL) {
        return 0;
    }
    return ezkey_api_url_allowed_url(cfg->api_url, cfg->lab_http);
}

static int ezkey_authenticate_user_api(pam_handle_t *pamh, EzkeyConfig *cfg,
                                       const char *username,
                                       long *auth_attempt_id_out,
                                       int *timeout_seconds_out,
                                       const struct timespec *auth_started,
                                       const char **summary_result_out,
                                       const char **cancel_status_out) {
    char userpwd[EZKEY_KEY_MAX * 2 + 4];
    char post_url[EZKEY_URL_MAX + 64];
    char wait_url[EZKEY_URL_MAX + 128];
    char api_url[EZKEY_URL_MAX];
    char *post_body;
    EzkeyHttpBuf buf;
    long http_code = 0;
    long auth_attempt_id = -1;
    int curl_rc;
    int pam_rc = PAM_AUTH_ERR;
    int curl_inited = 0;
    int do_cancel = 0;
    int interrupted = 0;
    int planned_wait;
    int applied_ttl;
    int effective_wait;
    int logged_timeout_seconds = -1;
    long rem;
    long curl_limit;
    long cancel_limit;
    cJSON *root;
    cJSON *id_val;
    cJSON *chal_val;
    cJSON *ttl_val;
    cJSON *status_val;
    cJSON *timeout_reached_val;
    int accepted = 0;
    int timeout_reached = 0;
    const char *status_str = NULL;
    char status_safe[64];
    const char *summary = "DENIED";
    const char *cancel_status = EZKEY_CANCEL_SKIPPED;
    long challenge = -1;
    char msg[512];

    if (auth_attempt_id_out != NULL) {
        *auth_attempt_id_out = -1;
    }
    if (timeout_seconds_out != NULL) {
        *timeout_seconds_out = -1;
    }
    if (summary_result_out != NULL) {
        *summary_result_out = "UNAVAIL";
    }
    if (cancel_status_out != NULL) {
        *cancel_status_out = EZKEY_CANCEL_SKIPPED;
    }

    if (cfg->api_url[0] == '\0' || cfg->integration_key[0] == '\0' ||
        cfg->secret_key[0] == '\0') {
        ezkey_diag.stage = "conf";
        ezkey_diag.reason = "bad_conf";
        log_pam_error_debug(pamh, cfg,
                            "integration_api_url, integration_key, or "
                            "secret_key missing from conf");
        wipe_cfg_secrets(cfg);
        return PAM_AUTHINFO_UNAVAIL;
    }
    if (!ezkey_api_url_allowed(cfg)) {
        ezkey_diag.stage = "conf";
        ezkey_diag.reason = "url_scheme";
        log_pam_error_debug(pamh, cfg,
                            "integration_api_url must be https:// "
                            "(or http:// with lab_http=true)");
        wipe_cfg_secrets(cfg);
        return PAM_AUTHINFO_UNAVAIL;
    }

    if (curl_global_init(CURL_GLOBAL_DEFAULT) != 0) {
        ezkey_diag.stage = "init";
        ezkey_diag.reason = "curl_init";
        log_pam_error_debug(pamh, cfg, "curl_global_init failed");
        wipe_cfg_secrets(cfg);
        if (summary_result_out != NULL) {
            *summary_result_out = "ERROR";
        }
        return PAM_SERVICE_ERR;
    }
    curl_inited = 1;

    strncpy(api_url, cfg->api_url, sizeof(api_url) - 1);
    api_url[sizeof(api_url) - 1] = '\0';
    strip_trailing_slash(api_url);
    snprintf(userpwd, sizeof(userpwd), "%s:%s", cfg->integration_key,
             cfg->secret_key);
    snprintf(post_url, sizeof(post_url), "%s/api/v1/auth-attempts", api_url);

    /*
     * Confirm-before-create (#975 / Patrick INTERACTION-MODEL §5.2): require
     * trimmed "ok" **before** POST /auth-attempts so batch/empty clients
     * (ssh -T </dev/null, cron) fail with zero phone noise. Ctrl-C / empty at
     * this prompt creates nothing (do_cancel still 0).
     */
    if (ezkey_format_waiting_prompt(msg, sizeof(msg), username) != 0) {
        ezkey_diag.stage = "confirm";
        ezkey_diag.reason = "prompt_fmt";
        log_pam_error_debug(pamh, cfg, "failed to format pre-create confirm");
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    {
        int conf_rc;

        conf_rc = prompt_echo_on(
            pamh, cfg, auth_started, msg, sizeof(msg), 1 /* require ok */,
            "confirm",
            "PAM conversation could not display the pre-create confirm",
            "pre-create confirm exceeded max duration",
            "auth budget exhausted before pre-create confirm",
            "auth budget exhausted after pre-create confirm",
            "no PAM conversation to display the pre-create confirm",
            &interrupted);
        if (conf_rc != 0) {
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            /* Distinct result for empty/wrong ok — #1011 (no debug needed). */
            if (conf_rc == -2) {
                summary = "CONFIRM";
            } else {
                summary = "UNAVAIL";
            }
            goto cleanup;
        }
    }

    /*
     * Planned wait / create ttlSeconds from remaining budget **after** the
     * pre-create confirm (Patrick B1 / #976). Typing time must shrink the
     * server TTL, not only the PAM-side wait. Align later on applied duration
     * from the create response — never host clock vs expiresAt (#895).
     */
    planned_wait = compute_planned_wait(cfg, auth_started);
    if (planned_wait < 0) {
        ezkey_diag.stage = "create";
        ezkey_diag.reason = "budget";
        log_pam_error_debug(pamh, cfg,
                            "auth budget too small for server-legal ttlSeconds "
                            "after pre-create confirm");
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    applied_ttl = planned_wait;

    ezkey_diag.stage = "create";
    /* Snapshot before POST: SIGTERM during create must not be absorbed (B2). */
    ezkey_diag_refresh_signal_snapshot("create");
    post_body = build_create_body(cfg, username, planned_wait);
    if (post_body == NULL) {
        ezkey_diag.reason = "oom";
        log_pam_error_debug(pamh, cfg, "Failed to build auth-attempt JSON body");
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }

    if (cfg->debug) {
        snprintf(msg, sizeof(msg),
                 "POST auth-attempt ttlSeconds=%d (body omitted)", planned_wait);
        log_pam_info(pamh, cfg, msg);
    }

    ezkey_http_buf_init(&buf);
    if (buf.data == NULL) {
        free(post_body);
        ezkey_diag.reason = "oom";
        log_pam_error_debug(pamh, cfg, "Out of memory");
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }

    rem = remaining_budget_secs(auth_started);
    curl_limit = (long)cfg->api_timeout;
    if (curl_limit > rem) {
        curl_limit = rem > 0 ? rem : 1;
    }
    if (curl_limit < 1) {
        free(post_body);
        ezkey_diag.reason = "budget";
        log_pam_error_debug(pamh, cfg, "auth budget exhausted before create");
        ezkey_http_buf_free(&buf);
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    {
        struct timespec api_started;
        long api_elapsed;

        clock_gettime(CLOCK_MONOTONIC, &api_started);
        curl_rc = ezkey_http(post_url, userpwd, post_body, curl_limit, &buf,
                             &http_code, NULL);
        api_elapsed = elapsed_ms(&api_started);
        if (ezkey_diag.api_ms < 0) {
            ezkey_diag.api_ms = api_elapsed;
        } else {
            ezkey_diag.api_ms += api_elapsed;
        }
    }
    free(post_body);
    post_body = NULL;
    ezkey_diag.curl_rc = curl_rc;
    ezkey_diag.http = http_code;
    if (curl_rc != CURLE_OK) {
        ezkey_diag.reason = "curl";
        snprintf(msg, sizeof(msg), "Failed to POST auth-attempt: %s",
                 curl_easy_strerror(curl_rc));
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_http_buf_free(&buf);
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    pam_rc = pam_rc_for_http(http_code, 201);
    if (pam_rc != PAM_SUCCESS) {
        ezkey_diag.reason = "http";
        snprintf(msg, sizeof(msg), "POST auth-attempt HTTP %ld", http_code);
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_http_buf_free(&buf);
        summary = "UNAVAIL";
        goto cleanup;
    }

    root = cJSON_Parse(buf.data);
    if (!root) {
        ezkey_diag.reason = "json";
        log_pam_error_debug(pamh, cfg, "JSON parse error for authAttemptId");
        ezkey_http_buf_free(&buf);
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }
    id_val = cJSON_GetObjectItemCaseSensitive(root, "authAttemptId");
    if (!cJSON_IsNumber(id_val) ||
        ezkey_json_double_to_long(id_val->valuedouble, 1L, (long)(1LL << 53),
                                  &auth_attempt_id) != 0) {
        ezkey_diag.reason = "json";
        log_pam_error_debug(pamh, cfg,
                            "authAttemptId missing or out of range");
        cJSON_Delete(root);
        ezkey_http_buf_free(&buf);
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }
    if (auth_attempt_id_out != NULL) {
        *auth_attempt_id_out = auth_attempt_id;
    }
    ezkey_diag.auth_attempt_id = auth_attempt_id;
    do_cancel = 1;
    chal_val = cJSON_GetObjectItemCaseSensitive(root, "authAttemptChallenge");
    if (cJSON_IsNumber(chal_val)) {
        long chal_tmp = -1;

        if (ezkey_json_double_to_long(chal_val->valuedouble, 0L, 99L,
                                      &chal_tmp) != 0) {
            ezkey_diag.reason = "json";
            log_pam_error_debug(pamh, cfg,
                                "authAttemptChallenge out of range 0..99");
            cJSON_Delete(root);
            ezkey_http_buf_free(&buf);
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
        challenge = chal_tmp;
    }
    /*
     * Applied TTL from create response when present. PR-A (#898) returns the
     * applied duration as timeoutSeconds (not request ttlSeconds). Fall back to
     * ttlSeconds for older stubs. Absent / null / 0 / value > planned ⇒ keep
     * planned (legacy servers; Patrick #897 M1 applied_grow / zero / null).
     * Never derive wait from expiresAt wall-clock arithmetic. Validate the
     * double in-range before casting to int (Christophe #897 n2).
     */
    ttl_val = cJSON_GetObjectItemCaseSensitive(root, "timeoutSeconds");
    if (!cJSON_IsNumber(ttl_val)) {
        ttl_val = cJSON_GetObjectItemCaseSensitive(root, "ttlSeconds");
    }
    if (cJSON_IsNumber(ttl_val)) {
        double ttl_d = ttl_val->valuedouble;

        if (ttl_d >= 1.0 && ttl_d <= (double)planned_wait) {
            applied_ttl = (int)ttl_d;
            if (applied_ttl < planned_wait) {
                planned_wait = applied_ttl;
            }
        }
    }
    cJSON_Delete(root);
    ezkey_http_buf_free(&buf);

    logged_timeout_seconds = applied_ttl;
    if (timeout_seconds_out != NULL) {
        *timeout_seconds_out = applied_ttl;
    }

    snprintf(msg, sizeof(msg),
             "Auth attempt created, id=%ld applied_ttl=%d", auth_attempt_id,
             applied_ttl);
    log_pam_info(pamh, cfg, msg);

    /*
     * Post-create challenge prompt (#975 / #982 tier 1): if create returned a
     * valid code, show digits in PAM_PROMPT_ECHO_ON (never syslog — #750).
     * Enter continues to /wait — no second "Type ok". Operator may approve on
     * the phone before or after Enter; the module polls until ACCEPTED /
     * REJECTED / TTL. challenge_requested but missing/out-of-range → UNAVAIL
     * + cancel. No-challenge path: skip; pre-create confirm already ran.
     */
    {
        int prewait_mode;
        int conf_rc;

        prewait_mode =
            ezkey_prewait_prompt_mode(cfg->challenge_requested, challenge);
        if (prewait_mode < 0) {
            ezkey_diag.stage = "challenge";
            ezkey_diag.reason = "challenge_missing";
            log_pam_error_debug(pamh, cfg,
                                "challenge_requested but authAttemptChallenge "
                                "missing or outside 0..99");
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
        if (prewait_mode > 0) {
            if (ezkey_format_challenge_prompt(msg, sizeof(msg), challenge) !=
                0) {
                ezkey_diag.stage = "challenge";
                ezkey_diag.reason = "prompt_fmt";
                log_pam_error_debug(pamh, cfg,
                                    "failed to format challenge prompt");
                pam_rc = PAM_AUTHINFO_UNAVAIL;
                summary = "UNAVAIL";
                goto cleanup;
            }
            conf_rc = prompt_echo_on(
                pamh, cfg, auth_started, msg, sizeof(msg), 0 /* Enter only */,
                "challenge",
                "PAM conversation could not display the challenge code",
                "challenge prompt exceeded max duration",
                "auth budget exhausted before challenge prompt",
                "auth budget exhausted after challenge prompt",
                "no PAM conversation to display the challenge code",
                &interrupted);
            if (conf_rc != 0) {
                pam_rc = PAM_AUTHINFO_UNAVAIL;
                summary = "UNAVAIL";
                goto cleanup;
            }
        }
    }

    /*
     * Effective /wait: min(conf, applied_ttl, rem − curl_slack). Align on the
     * applied duration (monotonic), not expiresAt. Reserve cancel margin inside
     * CURL_WAIT_SLACK (≤ MAX_API_TIMEOUT). Too little left → UNAVAIL + cancel.
     */
    ezkey_diag.stage = "wait";
    rem = remaining_budget_secs(auth_started);
    effective_wait = cfg->wait_timeout;
    if (applied_ttl > 0 && applied_ttl < effective_wait) {
        effective_wait = applied_ttl;
    }
    if (rem <= (long)EZKEY_CURL_WAIT_SLACK + (long)EZKEY_MIN_WAIT_SECS) {
        ezkey_diag.reason = "budget";
        log_pam_error_debug(pamh, cfg,
                            "auth budget exhausted before /wait");
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    if ((long)effective_wait > rem - (long)EZKEY_CURL_WAIT_SLACK) {
        effective_wait = (int)(rem - (long)EZKEY_CURL_WAIT_SLACK);
    }
    if (effective_wait < EZKEY_MIN_WAIT_SECS) {
        ezkey_diag.reason = "budget";
        log_pam_error_debug(pamh, cfg,
                            "auth budget too small for /wait");
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }

    /*
     * Chunked /wait (#907 R5): OpenSSH portable runs PAM in a forked child that
     * _exit's — pam_end cleanup never runs. Slice wait so SIGTERM on client
     * disconnect can abort curl via xferinfo and cancel in this process.
     * Handler absorbs SIGTERM only while ezkey_in_wait=1 (#1011 B1/B2); do not
     * clear ezkey_wait_abort if it was already raised before wait entry.
     */
    {
        int wait_left = effective_wait;

        pam_rc = PAM_AUTH_ERR;
        summary = "TIMEOUT";

        if (ezkey_wait_entry_abort_raised(&ezkey_wait_abort)) {
            interrupted = 1;
            ezkey_diag.reason = "signal";
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            log_pam_error_debug(pamh, cfg,
                                "SIGTERM observed before /wait; skipping wait");
            goto after_wait;
        }

        ezkey_in_wait = 1;
        while (wait_left > 0) {
            int chunk = wait_left;

            if (chunk > EZKEY_WAIT_CHUNK_SECS) {
                chunk = EZKEY_WAIT_CHUNK_SECS;
            }
            if (ezkey_wait_abort != 0) {
                interrupted = 1;
                ezkey_diag.reason = "signal";
                pam_rc = PAM_AUTHINFO_UNAVAIL;
                summary = "UNAVAIL";
                log_pam_error_debug(pamh, cfg,
                                    "client disconnect during wait (SIGTERM)");
                break;
            }

            snprintf(wait_url, sizeof(wait_url),
                     "%s/api/v1/auth-attempts/%ld/wait?timeout=%d&polling=%d",
                     api_url, auth_attempt_id, chunk, cfg->wait_polling);
            if (cfg->debug) {
                snprintf(msg, sizeof(msg),
                         "GET wait authAttemptId=%ld chunk=%d wait_left=%d "
                         "applied_ttl=%d budget_rem=%ld",
                         auth_attempt_id, chunk, wait_left, applied_ttl,
                         remaining_budget_secs(auth_started));
                log_pam_info(pamh, cfg, msg);
            }

            ezkey_http_buf_init(&buf);
            if (buf.data == NULL) {
                ezkey_diag.reason = "oom";
                log_pam_error_debug(pamh, cfg, "Out of memory");
                pam_rc = PAM_SERVICE_ERR;
                summary = "ERROR";
                break;
            }
            http_code = 0;
            rem = remaining_budget_secs(auth_started);
            /* Christophe M-a: never continue with curl_limit=1 when rem<=0. */
            if (rem <= 0) {
                ezkey_http_buf_free(&buf);
                pam_rc = PAM_AUTH_ERR;
                summary = "TIMEOUT";
                break;
            }
            curl_limit = (long)chunk + 2;
            if (curl_limit > rem) {
                curl_limit = rem;
            }
            {
                struct timespec api_started;
                long api_elapsed;

                clock_gettime(CLOCK_MONOTONIC, &api_started);
                curl_rc = ezkey_http(wait_url, userpwd, NULL, curl_limit, &buf,
                                     &http_code, &ezkey_wait_abort);
                api_elapsed = elapsed_ms(&api_started);
                if (ezkey_diag.api_ms < 0) {
                    ezkey_diag.api_ms = api_elapsed;
                } else {
                    ezkey_diag.api_ms += api_elapsed;
                }
            }
            ezkey_diag.curl_rc = curl_rc;
            ezkey_diag.http = http_code;
            if (curl_rc != CURLE_OK) {
                snprintf(msg, sizeof(msg), "Failed to GET wait API: %s",
                         curl_easy_strerror(curl_rc));
                log_pam_error_debug(pamh, cfg, msg);
                ezkey_http_buf_free(&buf);
                if (ezkey_wait_abort != 0 ||
                    curl_rc == CURLE_ABORTED_BY_CALLBACK) {
                    interrupted = 1;
                    ezkey_diag.reason = "signal";
                    pam_rc = PAM_AUTHINFO_UNAVAIL;
                    summary = "UNAVAIL";
                } else if (curl_rc == CURLE_OPERATION_TIMEDOUT) {
                    wait_left -= chunk;
                    if (wait_left <= 0 ||
                        remaining_budget_secs(auth_started) <= 0) {
                        pam_rc = PAM_AUTH_ERR;
                        summary = "TIMEOUT";
                        break;
                    }
                    continue;
                } else {
                    interrupted = 1;
                    ezkey_diag.reason = "curl";
                    pam_rc = PAM_AUTHINFO_UNAVAIL;
                    summary = "UNAVAIL";
                }
                break;
            }
            if (pam_rc_for_http(http_code, 200) != PAM_SUCCESS) {
                ezkey_diag.reason = "http";
                snprintf(msg, sizeof(msg),
                         "Wait API HTTP %ld authAttemptId=%ld", http_code,
                         auth_attempt_id);
                log_pam_error_debug(pamh, cfg, msg);
                ezkey_http_buf_free(&buf);
                pam_rc = PAM_AUTHINFO_UNAVAIL;
                summary = "UNAVAIL";
                break;
            }

            root = cJSON_Parse(buf.data);
            ezkey_http_buf_free(&buf);
            if (!root) {
                ezkey_diag.reason = "json";
                log_pam_error_debug(pamh, cfg,
                                    "JSON parse error for wait response");
                pam_rc = PAM_SERVICE_ERR;
                summary = "ERROR";
                break;
            }
            status_val = cJSON_GetObjectItemCaseSensitive(root, "status");
            timeout_reached_val =
                cJSON_GetObjectItemCaseSensitive(root, "timeoutReached");
            timeout_reached = 0;
            if (cJSON_IsBool(timeout_reached_val)) {
                timeout_reached = cJSON_IsTrue(timeout_reached_val) ? 1 : 0;
            }
            status_str = NULL;
            if (cJSON_IsString(status_val) && status_val->valuestring != NULL) {
                status_str = status_val->valuestring;
            }
            ezkey_sanitize_for_log(status_safe, sizeof(status_safe),
                                   status_str != NULL ? status_str : "missing");
            /*
             * Decision helper pins ACCEPTED→SUCCESS and REJECTED→DENIED
             * (#1016 P1 / M20). Keep strcmp("ACCEPTED") visible for check-source.
             */
            accepted =
                (status_str != NULL && strcmp(status_str, "ACCEPTED") == 0);
            if (ezkey_wait_status_decide(status_str, &pam_rc, &summary)) {
                ezkey_diag.reason = "-";
                if (accepted) {
                    do_cancel = 0;
                } else {
                    snprintf(msg, sizeof(msg),
                             "Authentication not accepted "
                             "(authAttemptId=%ld status=%s)",
                             auth_attempt_id, status_safe);
                    if (summary != NULL && strcmp(summary, "EXPIRED") == 0) {
                        snprintf(msg, sizeof(msg),
                                 "Authentication expired "
                                 "(authAttemptId=%ld status=%s)",
                                 auth_attempt_id, status_safe);
                    }
                    log_pam_error_debug(pamh, cfg, msg);
                }
                cJSON_Delete(root);
                break;
            }
            cJSON_Delete(root);
            (void)timeout_reached;
            wait_left -= chunk;
            if (wait_left <= 0) {
                pam_rc = PAM_AUTH_ERR;
                summary = "TIMEOUT";
                ezkey_diag.reason = "-";
                snprintf(msg, sizeof(msg),
                         "Authentication wait timeout "
                         "(authAttemptId=%ld status=%s)",
                         auth_attempt_id, status_safe);
                log_pam_error_debug(pamh, cfg, msg);
                break;
            }
            /* PENDING/READ (or chunk timeoutReached): continue next chunk. */
        }

        ezkey_in_wait = 0;
    }
after_wait:
    ezkey_in_wait = 0;

cleanup:
    if (ezkey_should_cancel_attempt(do_cancel && auth_attempt_id > 0,
                                    pam_rc == PAM_SUCCESS)) {
        rem = remaining_budget_secs(auth_started);
        cancel_limit = ezkey_bounded_cancel_timeout((long)cfg->api_timeout, rem,
                                                    interrupted);
        if (cancel_limit >= 1) {
            cancel_status = ezkey_cancel_best_effort(
                pamh, cfg, api_url, userpwd, auth_attempt_id, cancel_limit);
        } else {
            cancel_status = EZKEY_CANCEL_SKIPPED;
        }
    }
    /* Wipe Basic credentials immediately after last HTTP use (cancel/create). */
    ezkey_secure_wipe(userpwd, sizeof(userpwd));
    if (timeout_seconds_out != NULL && logged_timeout_seconds > 0) {
        *timeout_seconds_out = logged_timeout_seconds;
    }
    if (summary_result_out != NULL) {
        *summary_result_out = summary;
    }
    if (cancel_status_out != NULL) {
        *cancel_status_out = cancel_status;
    }
    wipe_cfg_secrets(cfg);
    if (curl_inited) {
        curl_global_cleanup();
    }
    return pam_rc;
}

PAM_EXTERN int pam_sm_authenticate(pam_handle_t *pamh, int flags, int argc,
                                   const char **argv) {
    const char *username = NULL;
    int retval = PAM_AUTHINFO_UNAVAIL;
    int conf_rc;
    EzkeyConfig cfg;
    char user_msg[768];
    long auth_attempt_id = -1;
    int timeout_seconds = -1;
    const char *summary_result = NULL;
    const char *cancel_status = EZKEY_CANCEL_SKIPPED;
    const void *final_item = NULL;
    struct timespec started;
    struct sigaction sa_old;
    int sig_installed = 0;

    (void)flags;
    clock_gettime(CLOCK_MONOTONIC, &started);

    ezkey_config_init(&cfg);
    /* conf= / debug / wait_timeout= from pam.d — never from process ENV. */
    parse_module_args(argc, argv, &cfg);

    ezkey_wait_abort = 0;
    ezkey_in_wait = 0;
    ezkey_diag_emitted = 0;
    ezkey_diag_syslog_snap_clear(&ezkey_diag_syslog_snap);
    ezkey_auth_started_ts = started;
    ezkey_auth_started_valid = 1;
    ezkey_diag_log_fd = ezkey_diag_open_log_fd();
    ezkey_diag_init(&ezkey_diag, NULL, cfg.lab_http);
    ezkey_diag.latency_ms = -1;
    ezkey_diag_refresh_signal_snapshot("init");
    ezkey_sig_install(&sa_old, &sig_installed);

    openlog(EZKEY_MODULE_NAME, LOG_PID, LOG_AUTHPRIV);

    /*
     * #1016 S1: libcurl/OpenSSL honour SSLKEYLOGFILE — as root that writes
     * TLS key material to a caller-chosen path (mode 0644). Refuse before
     * any network call (and before loading secrets into curl).
     */
    if (ezkey_env_sslkeylogfile_set()) {
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        cancel_status = EZKEY_CANCEL_SKIPPED;
        ezkey_diag.stage = "init";
        ezkey_diag.reason = "env";
        log_pam_error_debug(pamh, &cfg,
                            "SSLKEYLOGFILE is set; refusing authenticate");
        goto done;
    }

    /*
     * In-process fail-closed only: if this PAM handle already saw a final
     * verdict, refuse recreate. Under OpenSSH portable this does NOT span
     * keyboard-interactive rounds (each forks and _exit's) — host Match
     * MaxAuthTries is the real guarantee (#907 option A).
     */
    if (pam_get_data(pamh, EZKEY_PAM_DATA_FINAL, &final_item) == PAM_SUCCESS &&
        final_item != NULL) {
        (void)pam_get_user(pamh, &username, NULL);
        retval = PAM_MAXTRIES;
        summary_result = "MAXTRIES";
        cancel_status = EZKEY_CANCEL_SKIPPED;
        ezkey_diag.stage = "init";
        ezkey_diag.reason = "maxtries";
        log_pam_error_debug(pamh, &cfg,
                            "final verdict already recorded for this PAM "
                            "handle; refusing recreate");
        goto done;
    }

    conf_rc = load_config_file(cfg.config_file, &cfg);
    if (conf_rc == -2) {
        snprintf(user_msg, sizeof(user_msg),
                 "Config file %s rejected: must be root:root with mode "
                 "0600 or 0400",
                 cfg.config_file);
        log_pam_error_debug(pamh, &cfg, user_msg);
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        ezkey_diag.stage = "conf";
        ezkey_diag.reason = "bad_conf";
        goto done;
    }
    if (conf_rc == -3) {
        snprintf(user_msg, sizeof(user_msg),
                 "Config file %s rejected: line exceeds %d characters",
                 cfg.config_file, EZKEY_CONF_LINE_MAX - 2);
        log_pam_error_debug(pamh, &cfg, user_msg);
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        ezkey_diag.stage = "conf";
        ezkey_diag.reason = "bad_conf";
        goto done;
    }
    if (conf_rc != 0) {
        int saved_errno = errno;
        snprintf(user_msg, sizeof(user_msg), "Cannot open config file %s: %s",
                 cfg.config_file, strerror(saved_errno));
        log_pam_error_debug(pamh, &cfg, user_msg);
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        ezkey_diag.stage = "conf";
        ezkey_diag.reason = "bad_conf";
        goto done;
    }
    /* Re-apply pam.d args so wait_timeout= / debug override conf values. */
    parse_module_args(argc, argv, &cfg);
    ezkey_diag.lab_http = cfg.lab_http ? 1 : 0;

    log_pam_info(pamh, &cfg, "=== EZKEY PAM MODULE STARTED ===");

    if (cfg.debug) {
        char url_log[256];
        char conf_log[256];

        (void)ezkey_url_for_log(url_log, sizeof(url_log), cfg.api_url);
        ezkey_sanitize_for_log(conf_log, sizeof(conf_log), cfg.config_file);
        snprintf(user_msg, sizeof(user_msg),
                 "config_file=%s api_url=%s wait=%ds", conf_log, url_log,
                 cfg.wait_timeout);
        log_pam_info(pamh, &cfg, user_msg);
    }

    /* Resolve username before credential checks so UNAVAIL attempt lines carry user=. */
    retval = pam_get_user(pamh, &username, NULL);
    if (retval != PAM_SUCCESS) {
        log_pam_error(pamh, &cfg, "Failed to get username");
        summary_result = "UNAVAIL";
        ezkey_diag.stage = "init";
        ezkey_diag.reason = "no_user";
        goto done;
    }
    if (username == NULL || strlen(username) == 0) {
        log_pam_error(pamh, &cfg, "Username is null or empty");
        retval = PAM_USER_UNKNOWN;
        summary_result = "UNKNOWN_USER";
        ezkey_diag.stage = "init";
        ezkey_diag.reason = "no_user";
        goto done;
    }
    ezkey_sanitize_for_log(ezkey_diag.user, sizeof(ezkey_diag.user), username);

    if (cfg.api_url[0] == '\0' || cfg.integration_key[0] == '\0' ||
        cfg.secret_key[0] == '\0') {
        log_pam_error_debug(pamh, &cfg,
                            "Missing integration_api_url, integration_key, or "
                            "secret_key in conf");
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        ezkey_diag.stage = "conf";
        ezkey_diag.reason = "bad_conf";
        goto done;
    }
    if (!ezkey_api_url_allowed(&cfg)) {
        log_pam_error_debug(pamh, &cfg,
                            "integration_api_url must be https:// "
                            "(or http:// with lab_http=true)");
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        ezkey_diag.stage = "conf";
        ezkey_diag.reason = "url_scheme";
        goto done;
    }

    snprintf(user_msg, sizeof(user_msg), "Authentication requested for user: %s",
             username);
    log_pam_info(pamh, &cfg, user_msg);

    {
        int planned = compute_planned_wait(&cfg, &started);

        /* Start line before create (#907) — id unknown until POST succeeds. */
        log_attempt_start(username, -1,
                          planned > 0 ? planned : cfg.wait_timeout);
    }

    /*
     * Pre-create confirm + optional challenge lines are shown inside
     * ezkey_authenticate_user_api via PAM_PROMPT_ECHO_ON (#941 / #975 / #982
     * confirm-before-create). Do not pam_info here — OpenSSH buffers
     * TEXT_INFO until the verdict without a prompt round.
     */
    retval = ezkey_authenticate_user_api(pamh, &cfg, username, &auth_attempt_id,
                                         &timeout_seconds, &started,
                                         &summary_result, &cancel_status);

    if (ezkey_result_is_final(summary_result)) {
        (void)pam_set_data(pamh, EZKEY_PAM_DATA_FINAL, &ezkey_final_marker,
                           NULL);
    }

    if (cfg.debug) {
        snprintf(user_msg, sizeof(user_msg), "Authentication result for %s: %s",
                 username, (retval == PAM_SUCCESS) ? "SUCCESS" : "FAILURE");
        log_pam_info(pamh, &cfg, user_msg);
        snprintf(user_msg, sizeof(user_msg), "PAM return code: %d", retval);
        log_pam_info(pamh, &cfg, user_msg);
        log_pam_info(pamh, &cfg, "=== EZKEY PAM MODULE FINISHED ===");
    }

done:
    ezkey_diag.auth_attempt_id = auth_attempt_id;
    ezkey_diag.result = summary_result;
    ezkey_diag.cancel_status = cancel_status;
    ezkey_diag.pam_rc = retval;
    ezkey_diag.latency_ms = elapsed_ms(&started);
    ezkey_diag.lab_http = cfg.lab_http ? 1 : 0;
    ezkey_diag.timeout_seconds = timeout_seconds;
    if (username != NULL && username[0] != '\0') {
        ezkey_sanitize_for_log(ezkey_diag.user, sizeof(ezkey_diag.user),
                               username);
    }
    log_attempt_summary();
    ezkey_sig_restore(&sa_old, sig_installed);
    ezkey_diag_close_log_fd(ezkey_diag_log_fd);
    ezkey_diag_log_fd = -1;
    wipe_cfg_secrets(&cfg);
    closelog();
    return retval;
}

PAM_EXTERN int pam_sm_setcred(pam_handle_t *pamh, int flags, int argc,
                              const char **argv) {
    (void)pamh;
    (void)flags;
    (void)argc;
    (void)argv;
    return PAM_SUCCESS;
}
