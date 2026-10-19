/*
 * pam_ezkey.c - Ezkey PAM Module
 *
 * Linux PAM module that authorizes SSH (or other PAM services) via the Ezkey
 * Integration API. Runtime settings come from /etc/security/pam_ezkey.conf
 * (root:root, mode 0600). Docker demos write that file from the entrypoint —
 * sshd does not pass container ENV into PAM.
 *
 * Flow:
 *   1. POST /api/v1/auth-attempts  (Basic auth, userIdentifier = Linux username,
 *      optional ttlSeconds = planned wait; servers that ignore the field stay OK)
 *   2. GET  /api/v1/auth-attempts/{id}/wait  (timeout aligned on applied TTL,
 *      monotonic budget — never host clock vs server expiresAt)
 *   3. PAM_SUCCESS when wait status is ACCEPTED
 *   4. Best-effort POST …/cancel on any non-ACCEPTED exit after create
 *
 * Fail-closed: API / transport / config failures never return PAM_SUCCESS or
 * PAM_IGNORE. See STATUS.md §2.1, §2.4, and § M1.
 *
 * Observability (M1): exactly one authpriv syslog line per attempt (see
 * log_attempt_summary). result distinguishes SUCCESS / EXPIRED / TIMEOUT /
 * DENIED / UNAVAIL / …. Debug chatter goes only to /var/log/ezkey-pam/ files.
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
#include "ezkey_config.h"
#include "ezkey_filelog.h"
#include "ezkey_sanitize.h"

#define EZKEY_URL_MAX 512
#define EZKEY_KEY_MAX 256
#define EZKEY_TITLE_MAX 200
#define EZKEY_MESSAGE_MAX 512
#define EZKEY_LINE_MAX 1024

typedef struct {
    char api_url[EZKEY_URL_MAX];
    char integration_key[EZKEY_KEY_MAX];
    char secret_key[EZKEY_KEY_MAX];
    int wait_timeout;
    int wait_polling;
    int api_timeout;
    int challenge_requested;
    /* When 0 (default), only https:// Integration API URLs are accepted.
     * Docker lab entrypoint sets lab_http=true so http:// on the compose
     * network is allowed. Non-lab / host installs must leave this false. */
    int lab_http;
    char context_title[EZKEY_TITLE_MAX + 1];
    char context_message[EZKEY_MESSAGE_MAX + 1];
    int debug;
    char config_file[EZKEY_URL_MAX];
} EzkeyConfig;

typedef struct {
    char *data;
    size_t size;
} EzkeyBuf;

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
                                       const struct timespec *auth_started,
                                       const char **summary_result_out);
static void trim_inplace(char *s);
static void ezkey_secure_wipe(void *ptr, size_t len);
static void wipe_cfg_secrets(EzkeyConfig *cfg);
static void log_attempt_summary(const char *username, long auth_attempt_id,
                                const char *result, int pam_rc, long latency_ms,
                                int lab_http);
static long elapsed_ms(const struct timespec *start);
static long remaining_budget_secs(const struct timespec *auth_started);
static int compute_planned_wait(const EzkeyConfig *cfg,
                                const struct timespec *auth_started);
static char *build_create_body(const EzkeyConfig *cfg, const char *username,
                               int ttl_seconds);
static void ezkey_cancel_best_effort(pam_handle_t *pamh, const EzkeyConfig *cfg,
                                     const char *api_url, const char *userpwd,
                                     long auth_attempt_id, long timeout_secs);

static void ezkey_config_init(EzkeyConfig *cfg) {
    memset(cfg, 0, sizeof(*cfg));
    /* api_url left empty — must come from conf (no localhost default; B1). */
    cfg->wait_timeout = EZKEY_DEFAULT_WAIT_TIMEOUT;
    cfg->wait_polling = EZKEY_DEFAULT_WAIT_POLLING;
    cfg->api_timeout = EZKEY_DEFAULT_API_TIMEOUT;
    cfg->challenge_requested = 0;
    cfg->lab_http = 0;
    cfg->debug = 0;
    strncpy(cfg->context_title, "SSH login", sizeof(cfg->context_title) - 1);
    strncpy(cfg->config_file, EZKEY_DEFAULT_CONFIG_FILE,
            sizeof(cfg->config_file) - 1);
#if EZKEY_DEBUG
    cfg->debug = 1;
#endif
}

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

static void trim_inplace(char *s) {
    char *start;
    char *end;
    size_t len;

    if (s == NULL) {
        return;
    }
    start = s;
    while (*start != '\0' && isspace((unsigned char)*start)) {
        start++;
    }
    if (start != s) {
        memmove(s, start, strlen(start) + 1);
    }
    len = strlen(s);
    end = s + len;
    while (end > s && isspace((unsigned char)*(end - 1))) {
        end--;
    }
    *end = '\0';
}

static void set_cfg_str(char *dest, size_t dest_size, const char *src) {
    if (dest == NULL || dest_size == 0) {
        return;
    }
    if (src == NULL) {
        dest[0] = '\0';
        return;
    }
    strncpy(dest, src, dest_size - 1);
    dest[dest_size - 1] = '\0';
}

static int parse_positive_int(const char *value, int fallback) {
    char *end = NULL;
    long parsed;

    if (value == NULL || *value == '\0') {
        return fallback;
    }
    errno = 0;
    parsed = strtol(value, &end, 10);
    if (errno != 0 || end == value || *end != '\0' || parsed <= 0 ||
        parsed > 86400) {
        return fallback;
    }
    return (int)parsed;
}

static int parse_bool(const char *value) {
    if (value == NULL) {
        return 0;
    }
    if (strcasecmp(value, "true") == 0 || strcmp(value, "1") == 0 ||
        strcasecmp(value, "yes") == 0 || strcasecmp(value, "on") == 0) {
        return 1;
    }
    return 0;
}

static void apply_config_key(EzkeyConfig *cfg, const char *key,
                             const char *value) {
    if (strcmp(key, "integration_api_url") == 0 ||
        strcmp(key, "api_url") == 0) {
        set_cfg_str(cfg->api_url, sizeof(cfg->api_url), value);
    } else if (strcmp(key, "integration_key") == 0) {
        set_cfg_str(cfg->integration_key, sizeof(cfg->integration_key), value);
    } else if (strcmp(key, "secret_key") == 0) {
        set_cfg_str(cfg->secret_key, sizeof(cfg->secret_key), value);
    } else if (strcmp(key, "wait_timeout") == 0) {
        cfg->wait_timeout = ezkey_clamp_wait_timeout(
            parse_positive_int(value, cfg->wait_timeout));
    } else if (strcmp(key, "wait_polling") == 0) {
        cfg->wait_polling = parse_positive_int(value, cfg->wait_polling);
    } else if (strcmp(key, "api_timeout") == 0) {
        cfg->api_timeout = parse_positive_int(value, cfg->api_timeout);
        if (cfg->api_timeout > EZKEY_MAX_API_TIMEOUT) {
            cfg->api_timeout = EZKEY_MAX_API_TIMEOUT;
        }
    } else if (strcmp(key, "challenge_requested") == 0) {
        cfg->challenge_requested = parse_bool(value);
    } else if (strcmp(key, "lab_http") == 0) {
        cfg->lab_http = parse_bool(value);
    } else if (strcmp(key, "context_title") == 0) {
        set_cfg_str(cfg->context_title, sizeof(cfg->context_title), value);
    } else if (strcmp(key, "context_message") == 0) {
        set_cfg_str(cfg->context_message, sizeof(cfg->context_message), value);
    } else if (strcmp(key, "debug") == 0) {
        cfg->debug = parse_bool(value);
    }
}

/**
 * Load key=value settings from path. Requires a regular file owned by root:root
 * with mode exactly 0600 or 0400 (owner-readable; rejects 0000 and anything
 * with group/other bits).
 *
 * @return 0 on success
 * @return -1 if the file could not be opened (errno set)
 * @return -2 if ownership/permissions are insecure (errno = EPERM) or not a
 *         regular file (errno = EINVAL)
 */
static int load_config_file(const char *path, EzkeyConfig *cfg) {
    int fd;
    FILE *fp;
    char line[EZKEY_LINE_MAX];
    struct stat st;
    mode_t mode;

    if (path == NULL || *path == '\0') {
        errno = EINVAL;
        return -1;
    }
    fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
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
    while (fgets(line, sizeof(line), fp) != NULL) {
        char *hash;
        char *eq;
        char *key;
        char *value;

        hash = strchr(line, '#');
        if (hash != NULL) {
            *hash = '\0';
        }
        trim_inplace(line);
        if (line[0] == '\0') {
            continue;
        }
        eq = strchr(line, '=');
        if (eq == NULL) {
            continue;
        }
        *eq = '\0';
        key = line;
        value = eq + 1;
        trim_inplace(key);
        trim_inplace(value);
        if (*key == '\0') {
            continue;
        }
        apply_config_key(cfg, key, value);
    }
    fclose(fp);
    return 0;
}

static void parse_module_args(int argc, const char **argv, EzkeyConfig *cfg) {
    int i;

    for (i = 0; i < argc; i++) {
        if (strcmp(argv[i], "debug") == 0) {
            cfg->debug = 1;
        } else if (strncmp(argv[i], "conf=", 5) == 0) {
            set_cfg_str(cfg->config_file, sizeof(cfg->config_file), argv[i] + 5);
        } else if (strncmp(argv[i], "wait_timeout=", 13) == 0) {
            cfg->wait_timeout = ezkey_clamp_wait_timeout(
                parse_positive_int(argv[i] + 13, cfg->wait_timeout));
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
    if (cfg == NULL) {
        return;
    }
    ezkey_secure_wipe(cfg->secret_key, sizeof(cfg->secret_key));
    ezkey_secure_wipe(cfg->integration_key, sizeof(cfg->integration_key));
}

/**
 * Debug chatter only — never writes to authpriv syslog (M1: one attempt line).
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
 * Exactly one authpriv syslog line per authentication attempt (M1).
 * Format:
 *   pam_ezkey: attempt user=<u> authAttemptId=<id|-> result=<R> pam_code=<n>
 *   latency_ms=<ms> lab_http=<true|false>
 * result prefers the explicit outcome from the auth path (EXPIRED / TIMEOUT /
 * DENIED / …) so lacune-5 collapses no longer hide EXPIRED behind DENIED.
 * Never includes secrets, proof tokens, HTTP bodies, or Basic credentials.
 */
static void log_attempt_summary(const char *username, long auth_attempt_id,
                                const char *result, int pam_rc, long latency_ms,
                                int lab_http) {
    char user_safe[128];
    char id_buf[32];

    ezkey_sanitize_for_log(user_safe, sizeof(user_safe),
                           username != NULL ? username : "-");

    if (result == NULL || result[0] == '\0') {
        if (pam_rc == PAM_SUCCESS) {
            result = "SUCCESS";
        } else if (pam_rc == PAM_AUTH_ERR) {
            result = "DENIED";
        } else if (pam_rc == PAM_AUTHINFO_UNAVAIL) {
            result = "UNAVAIL";
        } else if (pam_rc == PAM_USER_UNKNOWN) {
            result = "UNKNOWN_USER";
        } else {
            result = "ERROR";
        }
    }

    if (auth_attempt_id > 0) {
        snprintf(id_buf, sizeof(id_buf), "%ld", auth_attempt_id);
    } else {
        snprintf(id_buf, sizeof(id_buf), "-");
    }

    if (latency_ms < 0) {
        latency_ms = 0;
    }

    syslog(LOG_INFO,
           "%s: attempt user=%s authAttemptId=%s result=%s pam_code=%d "
           "latency_ms=%ld lab_http=%s",
           EZKEY_MODULE_NAME, user_safe, id_buf, result, pam_rc, latency_ms,
           lab_http ? "true" : "false");
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

static void ezkey_buf_init(EzkeyBuf *buf) {
    buf->data = malloc(1);
    if (buf->data != NULL) {
        buf->data[0] = '\0';
        buf->size = 0;
    } else {
        buf->size = 0;
    }
}

static void ezkey_buf_free(EzkeyBuf *buf) {
    if (buf->data != NULL) {
        free(buf->data);
        buf->data = NULL;
    }
    buf->size = 0;
}

static size_t ezkey_curl_write_cb(void *contents, size_t size, size_t nmemb,
                                  void *userp) {
    size_t realsize = size * nmemb;
    EzkeyBuf *buf = (EzkeyBuf *)userp;
    char *ptr;

    if (buf->data == NULL) {
        return 0;
    }
    ptr = realloc(buf->data, buf->size + realsize + 1);
    if (ptr == NULL) {
        return 0;
    }
    buf->data = ptr;
    memcpy(&(buf->data[buf->size]), contents, realsize);
    buf->size += realsize;
    buf->data[buf->size] = '\0';
    return realsize;
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

static int ezkey_http(const char *url, const char *userpwd, const char *post_body,
                      long timeout_seconds, EzkeyBuf *buf, long *http_code) {
    CURL *curl;
    CURLcode res;
    struct curl_slist *headers = NULL;

    curl = curl_easy_init();
    if (!curl) {
        return CURLE_FAILED_INIT;
    }
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_USERPWD, userpwd);
    curl_easy_setopt(curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, buf);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeout_seconds);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
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
 * only — never changes the PAM outcome (409 on already-final is expected).
 */
static void ezkey_cancel_best_effort(pam_handle_t *pamh, const EzkeyConfig *cfg,
                                     const char *api_url, const char *userpwd,
                                     long auth_attempt_id, long timeout_secs) {
    char cancel_url[EZKEY_URL_MAX + 96];
    EzkeyBuf buf;
    long http_code = 0;
    int curl_rc;
    char msg[256];

    if (auth_attempt_id <= 0 || api_url == NULL || userpwd == NULL) {
        return;
    }
    if (timeout_secs < 1) {
        log_pam_error_debug(pamh, cfg,
                            "cancel skipped: no monotonic budget left");
        return;
    }
    snprintf(cancel_url, sizeof(cancel_url),
             "%s/api/v1/auth-attempts/%ld/cancel", api_url, auth_attempt_id);
    ezkey_buf_init(&buf);
    if (buf.data == NULL) {
        log_pam_error_debug(pamh, cfg, "cancel skipped: out of memory");
        return;
    }
    curl_rc = ezkey_http(cancel_url, userpwd, "{}", timeout_secs, &buf,
                         &http_code);
    ezkey_buf_free(&buf);
    if (curl_rc != CURLE_OK) {
        snprintf(msg, sizeof(msg), "cancel best-effort failed: %s",
                 curl_easy_strerror(curl_rc));
        log_pam_error_debug(pamh, cfg, msg);
        return;
    }
    snprintf(msg, sizeof(msg),
             "cancel best-effort authAttemptId=%ld HTTP %ld", auth_attempt_id,
             http_code);
    log_pam_info(pamh, cfg, msg);
}

/**
 * Map unexpected HTTP status to PAM. Transport/config failures are UNAVAIL
 * (not AUTH_ERR): 401/404/5xx all deny under required/requisite the same way,
 * but UNAVAIL signals “MFA service unavailable” to the stack (Christophe nit).
 */
static int pam_rc_for_http(long http_code, long expected) {
    if (http_code == expected) {
        return PAM_SUCCESS;
    }
    (void)http_code;
    return PAM_AUTHINFO_UNAVAIL;
}

/**
 * Scheme gate (Patrick): https always OK (case-insensitive); http only when
 * lab_http=true. Rejects empty and non-http(s) schemes.
 */
static int ezkey_api_url_allowed(const EzkeyConfig *cfg) {
    if (cfg == NULL || cfg->api_url[0] == '\0') {
        return 0;
    }
    if (strncasecmp(cfg->api_url, "https://", 8) == 0) {
        return 1;
    }
    if (cfg->lab_http && strncasecmp(cfg->api_url, "http://", 7) == 0) {
        return 1;
    }
    return 0;
}

static int ezkey_authenticate_user_api(pam_handle_t *pamh, EzkeyConfig *cfg,
                                       const char *username,
                                       long *auth_attempt_id_out,
                                       const struct timespec *auth_started,
                                       const char **summary_result_out) {
    char userpwd[EZKEY_KEY_MAX * 2 + 4];
    char post_url[EZKEY_URL_MAX + 64];
    char wait_url[EZKEY_URL_MAX + 128];
    char api_url[EZKEY_URL_MAX];
    char *post_body;
    EzkeyBuf buf;
    long http_code = 0;
    long auth_attempt_id = -1;
    int curl_rc;
    int pam_rc = PAM_AUTH_ERR;
    int curl_inited = 0;
    int do_cancel = 0;
    int planned_wait;
    int applied_ttl;
    int effective_wait;
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
    long challenge = -1;
    char msg[512];

    if (auth_attempt_id_out != NULL) {
        *auth_attempt_id_out = -1;
    }
    if (summary_result_out != NULL) {
        *summary_result_out = "UNAVAIL";
    }

    if (cfg->api_url[0] == '\0' || cfg->integration_key[0] == '\0' ||
        cfg->secret_key[0] == '\0') {
        log_pam_error_debug(pamh, cfg,
                            "integration_api_url, integration_key, or "
                            "secret_key missing from conf");
        wipe_cfg_secrets(cfg);
        return PAM_AUTHINFO_UNAVAIL;
    }
    if (!ezkey_api_url_allowed(cfg)) {
        log_pam_error_debug(pamh, cfg,
                            "integration_api_url must be https:// "
                            "(or http:// with lab_http=true)");
        wipe_cfg_secrets(cfg);
        return PAM_AUTHINFO_UNAVAIL;
    }

    if (curl_global_init(CURL_GLOBAL_DEFAULT) != 0) {
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
     * Compute planned wait (and ttlSeconds) before create. Align later on the
     * applied duration from the create response — never compare host clock to
     * expiresAt (#895 Patrick §4.3).
     */
    planned_wait = compute_planned_wait(cfg, auth_started);
    if (planned_wait < 0) {
        log_pam_error_debug(pamh, cfg,
                            "auth budget too small for server-legal ttlSeconds");
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    applied_ttl = planned_wait;

    post_body = build_create_body(cfg, username, planned_wait);
    if (post_body == NULL) {
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

    ezkey_buf_init(&buf);
    if (buf.data == NULL) {
        free(post_body);
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
        log_pam_error_debug(pamh, cfg, "auth budget exhausted before create");
        ezkey_buf_free(&buf);
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    curl_rc = ezkey_http(post_url, userpwd, post_body, curl_limit, &buf,
                         &http_code);
    free(post_body);
    post_body = NULL;
    if (curl_rc != CURLE_OK) {
        snprintf(msg, sizeof(msg), "Failed to POST auth-attempt: %s",
                 curl_easy_strerror(curl_rc));
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }
    pam_rc = pam_rc_for_http(http_code, 201);
    if (pam_rc != PAM_SUCCESS) {
        snprintf(msg, sizeof(msg), "POST auth-attempt HTTP %ld", http_code);
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        summary = "UNAVAIL";
        goto cleanup;
    }

    root = cJSON_Parse(buf.data);
    if (!root) {
        log_pam_error_debug(pamh, cfg, "JSON parse error for authAttemptId");
        ezkey_buf_free(&buf);
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }
    id_val = cJSON_GetObjectItemCaseSensitive(root, "authAttemptId");
    if (!cJSON_IsNumber(id_val)) {
        log_pam_error_debug(pamh, cfg, "authAttemptId not found or not a number");
        cJSON_Delete(root);
        ezkey_buf_free(&buf);
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }
    auth_attempt_id = (long)id_val->valuedouble;
    if (auth_attempt_id_out != NULL) {
        *auth_attempt_id_out = auth_attempt_id;
    }
    do_cancel = 1;
    chal_val = cJSON_GetObjectItemCaseSensitive(root, "authAttemptChallenge");
    if (cJSON_IsNumber(chal_val)) {
        challenge = (long)chal_val->valuedouble;
    }
    /*
     * Applied TTL from create response when present. PR-A (#898) returns the
     * applied duration as timeoutSeconds (not request ttlSeconds). Fall back to
     * ttlSeconds for older stubs. Absent ⇒ keep planned (legacy servers).
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
    ezkey_buf_free(&buf);

    snprintf(msg, sizeof(msg),
             "Auth attempt created, id=%ld applied_ttl=%d", auth_attempt_id,
             applied_ttl);
    log_pam_info(pamh, cfg, msg);

    /*
     * Two-digit challenge (number matching): when challenge_requested, the
     * create response carries authAttemptChallenge. Bound to 0..99 (Christophe);
     * show via PAM conversation before /wait. Never syslog the digits (#750).
     * If no conversation can display the code → UNAVAIL (user must see it).
     *
     * Craft rule: a PAM module NEVER touches the host process's signals —
     * no sigaction/alarm around Enter (would cancel login's alarm, miss under
     * sshd/fork, or hit the wrong thread). Bound Enter by measuring duration
     * with CLOCK_MONOTONIC after pam_prompt returns (Patrick).
     */
    if (cfg->challenge_requested) {
        char *resp = NULL;
        int prc;
        const void *conv_item = NULL;
        struct timespec enter_started;
        long enter_elapsed_s;

        if (!ezkey_challenge_in_range(challenge)) {
            log_pam_error_debug(pamh, cfg,
                                "challenge_requested but authAttemptChallenge "
                                "missing or outside 0..99");
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
        if (pam_get_item(pamh, PAM_CONV, &conv_item) != PAM_SUCCESS ||
            conv_item == NULL) {
            log_pam_error_debug(pamh, cfg,
                                "challenge_requested but no PAM conversation "
                                "to display the code");
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
        rem = remaining_budget_secs(auth_started);
        if (rem <= (long)EZKEY_MIN_WAIT_SECS + (long)EZKEY_CURL_WAIT_SLACK) {
            log_pam_error_debug(pamh, cfg,
                                "auth budget exhausted before challenge Enter "
                                "prompt");
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
        /*
         * OpenSSH keyboard-interactive buffers bare PAM_TEXT_INFO until a
         * response prompt is issued, and /wait would otherwise block first
         * (Patrick). Put the digits in a PAM_PROMPT_ECHO_OFF line so the
         * code reaches the PTY before /wait. No pam_info fallback — same
         * call path as the prompt, and failure → UNAVAIL.
         */
        snprintf(msg, sizeof(msg),
                 "Ezkey challenge code: %02ld — enter this code on your Ezkey "
                 "device, then approve.\n"
                 "Press Enter to wait for Ezkey device approval: ",
                 challenge);
        clock_gettime(CLOCK_MONOTONIC, &enter_started);
        prc = pam_prompt(pamh, PAM_PROMPT_ECHO_OFF, &resp, "%s", msg);
        enter_elapsed_s = elapsed_ms(&enter_started) / 1000L;
        ezkey_secure_wipe(msg, sizeof(msg));
        if (resp != NULL) {
            ezkey_secure_wipe(resp, strlen(resp));
            free(resp);
            resp = NULL;
        }
        if (prc != PAM_SUCCESS) {
            log_pam_error_debug(pamh, cfg,
                                "challenge_requested but PAM conversation "
                                "could not display the code");
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
        if (enter_elapsed_s > (long)EZKEY_MAX_ENTER_SECS) {
            log_pam_error_debug(pamh, cfg,
                                "challenge Enter exceeded max duration");
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
        rem = remaining_budget_secs(auth_started);
        if (rem <= (long)EZKEY_CURL_WAIT_SLACK + (long)EZKEY_MIN_WAIT_SECS) {
            log_pam_error_debug(pamh, cfg,
                                "auth budget exhausted after challenge Enter");
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
            goto cleanup;
        }
    }

    /*
     * Effective /wait: min(conf, applied_ttl, rem − curl_slack). Align on the
     * applied duration (monotonic), not expiresAt. Reserve cancel margin inside
     * CURL_WAIT_SLACK (≤ MAX_API_TIMEOUT). Too little left → UNAVAIL + cancel.
     */
    rem = remaining_budget_secs(auth_started);
    effective_wait = cfg->wait_timeout;
    if (applied_ttl > 0 && applied_ttl < effective_wait) {
        effective_wait = applied_ttl;
    }
    if (rem <= (long)EZKEY_CURL_WAIT_SLACK + (long)EZKEY_MIN_WAIT_SECS) {
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
        log_pam_error_debug(pamh, cfg,
                            "auth budget too small for /wait");
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        summary = "UNAVAIL";
        goto cleanup;
    }

    snprintf(wait_url, sizeof(wait_url),
             "%s/api/v1/auth-attempts/%ld/wait?timeout=%d&polling=%d", api_url,
             auth_attempt_id, effective_wait, cfg->wait_polling);
    if (cfg->debug) {
        snprintf(msg, sizeof(msg),
                 "GET wait authAttemptId=%ld timeout=%d applied_ttl=%d "
                 "budget_rem=%ld",
                 auth_attempt_id, effective_wait, applied_ttl, rem);
        log_pam_info(pamh, cfg, msg);
    }

    ezkey_buf_init(&buf);
    if (buf.data == NULL) {
        log_pam_error_debug(pamh, cfg, "Out of memory");
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }
    http_code = 0;
    curl_limit = (long)effective_wait + (long)EZKEY_CURL_WAIT_SLACK;
    rem = remaining_budget_secs(auth_started);
    if (curl_limit > rem) {
        curl_limit = rem > 0 ? rem : 1;
    }
    curl_rc = ezkey_http(wait_url, userpwd, NULL, curl_limit, &buf, &http_code);
    if (curl_rc != CURLE_OK) {
        snprintf(msg, sizeof(msg), "Failed to GET wait API: %s",
                 curl_easy_strerror(curl_rc));
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        /* Client-side wait curl timeout → TIMEOUT (abandon); other transport → UNAVAIL. */
        if (curl_rc == CURLE_OPERATION_TIMEDOUT) {
            pam_rc = PAM_AUTH_ERR;
            summary = "TIMEOUT";
        } else {
            pam_rc = PAM_AUTHINFO_UNAVAIL;
            summary = "UNAVAIL";
        }
        goto cleanup;
    }
    pam_rc = pam_rc_for_http(http_code, 200);
    if (pam_rc != PAM_SUCCESS) {
        snprintf(msg, sizeof(msg), "Wait API HTTP %ld authAttemptId=%ld",
                 http_code, auth_attempt_id);
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        summary = "UNAVAIL";
        goto cleanup;
    }

    root = cJSON_Parse(buf.data);
    ezkey_buf_free(&buf);
    if (!root) {
        log_pam_error_debug(pamh, cfg, "JSON parse error for wait response");
        pam_rc = PAM_SERVICE_ERR;
        summary = "ERROR";
        goto cleanup;
    }
    status_val = cJSON_GetObjectItemCaseSensitive(root, "status");
    timeout_reached_val =
        cJSON_GetObjectItemCaseSensitive(root, "timeoutReached");
    if (cJSON_IsBool(timeout_reached_val)) {
        timeout_reached = cJSON_IsTrue(timeout_reached_val) ? 1 : 0;
    }
    if (cJSON_IsString(status_val) && status_val->valuestring != NULL) {
        status_str = status_val->valuestring;
    }
    /* Server-controlled status string — sanitize before any debug log (#897 n1). */
    ezkey_sanitize_for_log(status_safe, sizeof(status_safe),
                           status_str != NULL ? status_str : "missing");
    accepted = (status_str != NULL && strcmp(status_str, "ACCEPTED") == 0);
    if (accepted) {
        pam_rc = PAM_SUCCESS;
        summary = "SUCCESS";
        do_cancel = 0;
    } else if (status_str != NULL && strcmp(status_str, "EXPIRED") == 0) {
        pam_rc = PAM_AUTH_ERR;
        summary = "EXPIRED";
        snprintf(msg, sizeof(msg),
                 "Authentication expired (authAttemptId=%ld status=%s)",
                 auth_attempt_id, status_safe);
        log_pam_error_debug(pamh, cfg, msg);
    } else if (status_str != NULL &&
               (strcmp(status_str, "REJECTED") == 0 ||
                strcmp(status_str, "INVALID") == 0)) {
        pam_rc = PAM_AUTH_ERR;
        summary = "DENIED";
        snprintf(msg, sizeof(msg),
                 "Authentication not accepted (authAttemptId=%ld status=%s)",
                 auth_attempt_id, status_safe);
        log_pam_error_debug(pamh, cfg, msg);
    } else if (timeout_reached ||
               (status_str != NULL &&
                (strcmp(status_str, "PENDING") == 0 ||
                 strcmp(status_str, "READ") == 0))) {
        pam_rc = PAM_AUTH_ERR;
        summary = "TIMEOUT";
        snprintf(msg, sizeof(msg),
                 "Authentication wait timeout (authAttemptId=%ld status=%s)",
                 auth_attempt_id, status_safe);
        log_pam_error_debug(pamh, cfg, msg);
    } else {
        pam_rc = PAM_AUTH_ERR;
        summary = "DENIED";
        snprintf(msg, sizeof(msg),
                 "Authentication not accepted (authAttemptId=%ld status=%s)",
                 auth_attempt_id, status_safe);
        log_pam_error_debug(pamh, cfg, msg);
    }
    cJSON_Delete(root);

cleanup:
    if (do_cancel && auth_attempt_id > 0 && pam_rc != PAM_SUCCESS) {
        cancel_limit = (long)cfg->api_timeout;
        if (cancel_limit > (long)EZKEY_MAX_API_TIMEOUT) {
            cancel_limit = (long)EZKEY_MAX_API_TIMEOUT;
        }
        rem = remaining_budget_secs(auth_started);
        if (cancel_limit > rem) {
            cancel_limit = rem;
        }
        ezkey_cancel_best_effort(pamh, cfg, api_url, userpwd, auth_attempt_id,
                                 cancel_limit);
    }
    if (summary_result_out != NULL) {
        *summary_result_out = summary;
    }
    ezkey_secure_wipe(userpwd, sizeof(userpwd));
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
    const char *summary_result = NULL;
    struct timespec started;

    (void)flags;
    clock_gettime(CLOCK_MONOTONIC, &started);

    ezkey_config_init(&cfg);
    /* conf= / debug / wait_timeout= from pam.d — never from process ENV. */
    parse_module_args(argc, argv, &cfg);

    openlog(EZKEY_MODULE_NAME, LOG_PID, LOG_AUTHPRIV);

    conf_rc = load_config_file(cfg.config_file, &cfg);
    if (conf_rc == -2) {
        snprintf(user_msg, sizeof(user_msg),
                 "Config file %s rejected: must be root:root with mode "
                 "0600 or 0400",
                 cfg.config_file);
        log_pam_error_debug(pamh, &cfg, user_msg);
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        goto done;
    }
    if (conf_rc != 0) {
        int saved_errno = errno;
        snprintf(user_msg, sizeof(user_msg), "Cannot open config file %s: %s",
                 cfg.config_file, strerror(saved_errno));
        log_pam_error_debug(pamh, &cfg, user_msg);
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        goto done;
    }
    /* Re-apply pam.d args so wait_timeout= / debug override conf values. */
    parse_module_args(argc, argv, &cfg);

    log_pam_info(pamh, &cfg, "=== EZKEY PAM MODULE STARTED ===");

    if (cfg.debug) {
        snprintf(user_msg, sizeof(user_msg), "config_file=%s api_url=%s wait=%ds",
                 cfg.config_file, cfg.api_url, cfg.wait_timeout);
        log_pam_info(pamh, &cfg, user_msg);
    }

    /* Resolve username before credential checks so UNAVAIL attempt lines carry user=. */
    retval = pam_get_user(pamh, &username, NULL);
    if (retval != PAM_SUCCESS) {
        log_pam_error(pamh, &cfg, "Failed to get username");
        summary_result = "UNAVAIL";
        goto done;
    }
    if (username == NULL || strlen(username) == 0) {
        log_pam_error(pamh, &cfg, "Username is null or empty");
        retval = PAM_USER_UNKNOWN;
        summary_result = "UNKNOWN_USER";
        goto done;
    }

    if (cfg.api_url[0] == '\0' || cfg.integration_key[0] == '\0' ||
        cfg.secret_key[0] == '\0') {
        log_pam_error_debug(pamh, &cfg,
                            "Missing integration_api_url, integration_key, or "
                            "secret_key in conf");
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        goto done;
    }
    if (!ezkey_api_url_allowed(&cfg)) {
        log_pam_error_debug(pamh, &cfg,
                            "integration_api_url must be https:// "
                            "(or http:// with lab_http=true)");
        retval = PAM_AUTHINFO_UNAVAIL;
        summary_result = "UNAVAIL";
        goto done;
    }

    snprintf(user_msg, sizeof(user_msg), "Authentication requested for user: %s",
             username);
    log_pam_info(pamh, &cfg, user_msg);

    pam_info(pamh,
             "Approve this SSH login on your Ezkey device for user %s.",
             username);

    retval = ezkey_authenticate_user_api(pamh, &cfg, username, &auth_attempt_id,
                                         &started, &summary_result);

    if (cfg.debug) {
        snprintf(user_msg, sizeof(user_msg), "Authentication result for %s: %s",
                 username, (retval == PAM_SUCCESS) ? "SUCCESS" : "FAILURE");
        log_pam_info(pamh, &cfg, user_msg);
        snprintf(user_msg, sizeof(user_msg), "PAM return code: %d", retval);
        log_pam_info(pamh, &cfg, user_msg);
        log_pam_info(pamh, &cfg, "=== EZKEY PAM MODULE FINISHED ===");
    }

done:
    log_attempt_summary(username, auth_attempt_id, summary_result, retval,
                        elapsed_ms(&started), cfg.lab_http);
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
