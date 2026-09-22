/*
 * pam_ezkey.c - Ezkey PAM Module
 *
 * Linux PAM module that authorizes SSH (or other PAM services) via the Ezkey
 * Integration API. Runtime settings come from /etc/security/pam_ezkey.conf,
 * with environment-variable overrides for Docker demos.
 *
 * Flow:
 *   1. POST /api/v1/auth-attempts  (Basic auth, userIdentifier = Linux username)
 *   2. GET  /api/v1/auth-attempts/{id}/wait
 *   3. PAM_SUCCESS when wait status is ACCEPTED
 *
 * Fail-closed: API / transport / config failures never return PAM_SUCCESS or
 * PAM_IGNORE. See STATUS.md §2.1 and §2.4.
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
#include <cjson/cJSON.h>
#include <unistd.h>
#include <syslog.h>
#include <security/pam_modules.h>
#include <security/pam_ext.h>
#include <security/pam_appl.h>
#include "ezkey_config.h"

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
static void log_pam_error(pam_handle_t *pamh, const char *message);
static void parse_module_args(int argc, const char **argv, EzkeyConfig *cfg);
static int load_config_file(const char *path, EzkeyConfig *cfg);
static void apply_env_overrides(EzkeyConfig *cfg);
static int ezkey_authenticate_user_api(pam_handle_t *pamh, EzkeyConfig *cfg,
                                       const char *username);
static void trim_inplace(char *s);
static void ezkey_secure_wipe(void *ptr, size_t len);

static void ezkey_config_init(EzkeyConfig *cfg) {
    memset(cfg, 0, sizeof(*cfg));
    strncpy(cfg->api_url, EZKEY_DEFAULT_API_URL, sizeof(cfg->api_url) - 1);
    cfg->wait_timeout = EZKEY_DEFAULT_WAIT_TIMEOUT;
    cfg->wait_polling = EZKEY_DEFAULT_WAIT_POLLING;
    cfg->api_timeout = EZKEY_DEFAULT_API_TIMEOUT;
    cfg->challenge_requested = 0;
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
    if (len == 0) {
        return;
    }
    end = s + len - 1;
    while (end >= s && isspace((unsigned char)*end)) {
        *end = '\0';
        end--;
    }
}

static int parse_bool(const char *value) {
    if (value == NULL) {
        return 0;
    }
    if (strcmp(value, "1") == 0 || strcasecmp(value, "true") == 0 ||
        strcasecmp(value, "yes") == 0) {
        return 1;
    }
    return 0;
}

static int parse_positive_int(const char *value, int fallback) {
    char *end = NULL;
    long parsed;

    if (value == NULL || *value == '\0') {
        return fallback;
    }
    parsed = strtol(value, &end, 10);
    if (end == value || parsed <= 0 || parsed > 300) {
        return fallback;
    }
    return (int)parsed;
}

static void set_cfg_str(char *dest, size_t dest_size, const char *value) {
    if (dest == NULL || dest_size == 0 || value == NULL) {
        return;
    }
    strncpy(dest, value, dest_size - 1);
    dest[dest_size - 1] = '\0';
}

static void apply_config_key(EzkeyConfig *cfg, const char *key, const char *value) {
    if (strcmp(key, "integration_api_url") == 0 || strcmp(key, "m2m_api_url") == 0) {
        set_cfg_str(cfg->api_url, sizeof(cfg->api_url), value);
    } else if (strcmp(key, "integration_key") == 0) {
        set_cfg_str(cfg->integration_key, sizeof(cfg->integration_key), value);
    } else if (strcmp(key, "secret_key") == 0) {
        set_cfg_str(cfg->secret_key, sizeof(cfg->secret_key), value);
    } else if (strcmp(key, "wait_timeout") == 0) {
        cfg->wait_timeout = parse_positive_int(value, cfg->wait_timeout);
    } else if (strcmp(key, "wait_polling") == 0) {
        cfg->wait_polling = parse_positive_int(value, cfg->wait_polling);
    } else if (strcmp(key, "api_timeout") == 0) {
        cfg->api_timeout = parse_positive_int(value, cfg->api_timeout);
    } else if (strcmp(key, "challenge_requested") == 0) {
        cfg->challenge_requested = parse_bool(value);
    } else if (strcmp(key, "context_title") == 0) {
        set_cfg_str(cfg->context_title, sizeof(cfg->context_title), value);
    } else if (strcmp(key, "context_message") == 0) {
        set_cfg_str(cfg->context_message, sizeof(cfg->context_message), value);
    } else if (strcmp(key, "debug") == 0) {
        cfg->debug = parse_bool(value);
    }
}

/**
 * Load key=value settings from path.
 *
 * @return 0 on success, -1 if the file could not be opened (errno set)
 */
static int load_config_file(const char *path, EzkeyConfig *cfg) {
    FILE *fp;
    char line[EZKEY_LINE_MAX];

    if (path == NULL || *path == '\0') {
        errno = EINVAL;
        return -1;
    }
    fp = fopen(path, "r");
    if (fp == NULL) {
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

static void apply_env_overrides(EzkeyConfig *cfg) {
    const char *value;

    value = getenv(EZKEY_ENV_API_URL);
    if (value == NULL || *value == '\0') {
        value = getenv(EZKEY_ENV_API_URL_LEGACY);
    }
    if (value != NULL && *value != '\0') {
        set_cfg_str(cfg->api_url, sizeof(cfg->api_url), value);
    }

    value = getenv(EZKEY_ENV_INTEGRATION_KEY);
    if (value != NULL && *value != '\0') {
        set_cfg_str(cfg->integration_key, sizeof(cfg->integration_key), value);
    }

    value = getenv(EZKEY_ENV_SECRET_KEY);
    if (value != NULL && *value != '\0') {
        set_cfg_str(cfg->secret_key, sizeof(cfg->secret_key), value);
    }

    value = getenv(EZKEY_ENV_WAIT_TIMEOUT);
    if (value != NULL && *value != '\0') {
        cfg->wait_timeout = parse_positive_int(value, cfg->wait_timeout);
    }

    value = getenv(EZKEY_ENV_WAIT_POLLING);
    if (value != NULL && *value != '\0') {
        cfg->wait_polling = parse_positive_int(value, cfg->wait_polling);
    }

    value = getenv(EZKEY_ENV_API_TIMEOUT);
    if (value != NULL && *value != '\0') {
        cfg->api_timeout = parse_positive_int(value, cfg->api_timeout);
    }

    value = getenv(EZKEY_ENV_DEBUG);
    if (value != NULL && *value != '\0') {
        cfg->debug = parse_bool(value);
    }
}

static void parse_module_args(int argc, const char **argv, EzkeyConfig *cfg) {
    int i;

    for (i = 0; i < argc; i++) {
        if (strcmp(argv[i], "debug") == 0) {
            cfg->debug = 1;
        } else if (strncmp(argv[i], "conf=", 5) == 0) {
            set_cfg_str(cfg->config_file, sizeof(cfg->config_file), argv[i] + 5);
        }
    }
}

static void ezkey_filesyslog_out(const EzkeyConfig *cfg, const char *message) {
    FILE *fp;

    if (cfg == NULL || !cfg->debug) {
        return;
    }
    fp = fopen("/tmp/pam_ezkey.out", "a");
    if (fp) {
        fprintf(fp, "%s\n", message);
        fclose(fp);
    }
}

static void ezkey_filesyslog_err(const EzkeyConfig *cfg, const char *message) {
    FILE *fp;

    if (cfg == NULL || !cfg->debug) {
        return;
    }
    fp = fopen("/tmp/pam_ezkey.err", "a");
    if (fp) {
        fprintf(fp, "%s\n", message);
        fclose(fp);
    }
}

static void log_pam_info(pam_handle_t *pamh, const EzkeyConfig *cfg,
                         const char *message) {
    syslog(LOG_INFO, "%s: %s", EZKEY_MODULE_NAME, message);
    ezkey_filesyslog_out(cfg, message);
    if (cfg != NULL && cfg->debug) {
        pam_syslog(pamh, LOG_INFO, "%s", message);
    }
}

static void log_pam_error(pam_handle_t *pamh, const char *message) {
    syslog(LOG_ERR, "%s: ERROR: %s", EZKEY_MODULE_NAME, message);
    pam_syslog(pamh, LOG_ERR, "ERROR: %s", message);
}

static void log_pam_error_debug(pam_handle_t *pamh, const EzkeyConfig *cfg,
                                const char *message) {
    log_pam_error(pamh, message);
    ezkey_filesyslog_err(cfg, message);
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

static char *build_create_body(const EzkeyConfig *cfg, const char *username) {
    cJSON *root = cJSON_CreateObject();
    char *printed;
    char message[EZKEY_MESSAGE_MAX + 64];

    if (root == NULL) {
        return NULL;
    }
    cJSON_AddStringToObject(root, "userIdentifier", username);
    cJSON_AddBoolToObject(root, "challengeRequested",
                          cfg->challenge_requested ? 1 : 0);
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

static int pam_rc_for_http(long http_code, long expected) {
    if (http_code == expected) {
        return PAM_SUCCESS;
    }
    if (http_code >= 500 || http_code == 0) {
        return PAM_AUTHINFO_UNAVAIL;
    }
    /* 4xx and other unexpected codes: MFA path unavailable / misconfigured */
    return PAM_AUTHINFO_UNAVAIL;
}

static int ezkey_authenticate_user_api(pam_handle_t *pamh, EzkeyConfig *cfg,
                                       const char *username) {
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
    cJSON *root;
    cJSON *id_val;
    cJSON *status_val;
    int accepted;
    char msg[512];

    if (cfg->integration_key[0] == '\0' || cfg->secret_key[0] == '\0') {
        log_pam_error_debug(pamh, cfg,
                            "integration_key or secret_key is not set "
                            "(config file or env)");
        return PAM_AUTHINFO_UNAVAIL;
    }

    if (curl_global_init(CURL_GLOBAL_DEFAULT) != 0) {
        log_pam_error_debug(pamh, cfg, "curl_global_init failed");
        return PAM_SERVICE_ERR;
    }
    curl_inited = 1;

    strncpy(api_url, cfg->api_url, sizeof(api_url) - 1);
    api_url[sizeof(api_url) - 1] = '\0';
    strip_trailing_slash(api_url);
    snprintf(userpwd, sizeof(userpwd), "%s:%s", cfg->integration_key,
             cfg->secret_key);
    snprintf(post_url, sizeof(post_url), "%s/api/v1/auth-attempts", api_url);

    post_body = build_create_body(cfg, username);
    if (post_body == NULL) {
        log_pam_error_debug(pamh, cfg, "Failed to build auth-attempt JSON body");
        pam_rc = PAM_SERVICE_ERR;
        goto cleanup;
    }

    if (cfg->debug) {
        snprintf(msg, sizeof(msg), "POST %s (body omitted)", post_url);
        log_pam_info(pamh, cfg, msg);
    }

    ezkey_buf_init(&buf);
    if (buf.data == NULL) {
        free(post_body);
        log_pam_error_debug(pamh, cfg, "Out of memory");
        pam_rc = PAM_SERVICE_ERR;
        goto cleanup;
    }

    curl_rc = ezkey_http(post_url, userpwd, post_body, cfg->api_timeout, &buf,
                         &http_code);
    free(post_body);
    post_body = NULL;
    if (curl_rc != CURLE_OK) {
        snprintf(msg, sizeof(msg), "Failed to POST auth-attempt: %s",
                 curl_easy_strerror(curl_rc));
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        goto cleanup;
    }
    pam_rc = pam_rc_for_http(http_code, 201);
    if (pam_rc != PAM_SUCCESS) {
        snprintf(msg, sizeof(msg), "POST auth-attempt HTTP %ld", http_code);
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        goto cleanup;
    }

    root = cJSON_Parse(buf.data);
    if (!root) {
        log_pam_error_debug(pamh, cfg, "JSON parse error for authAttemptId");
        ezkey_buf_free(&buf);
        pam_rc = PAM_SERVICE_ERR;
        goto cleanup;
    }
    id_val = cJSON_GetObjectItemCaseSensitive(root, "authAttemptId");
    if (!cJSON_IsNumber(id_val)) {
        log_pam_error_debug(pamh, cfg, "authAttemptId not found or not a number");
        cJSON_Delete(root);
        ezkey_buf_free(&buf);
        pam_rc = PAM_SERVICE_ERR;
        goto cleanup;
    }
    auth_attempt_id = (long)id_val->valuedouble;
    cJSON_Delete(root);
    ezkey_buf_free(&buf);

    snprintf(msg, sizeof(msg), "Auth attempt created, id=%ld", auth_attempt_id);
    log_pam_info(pamh, cfg, msg);

    snprintf(wait_url, sizeof(wait_url),
             "%s/api/v1/auth-attempts/%ld/wait?timeout=%d&polling=%d", api_url,
             auth_attempt_id, cfg->wait_timeout, cfg->wait_polling);
    if (cfg->debug) {
        snprintf(msg, sizeof(msg), "GET wait authAttemptId=%ld timeout=%d",
                 auth_attempt_id, cfg->wait_timeout);
        log_pam_info(pamh, cfg, msg);
    }

    ezkey_buf_init(&buf);
    if (buf.data == NULL) {
        log_pam_error_debug(pamh, cfg, "Out of memory");
        pam_rc = PAM_SERVICE_ERR;
        goto cleanup;
    }
    http_code = 0;
    curl_rc = ezkey_http(wait_url, userpwd, NULL, (long)(cfg->wait_timeout + 15),
                         &buf, &http_code);
    if (curl_rc != CURLE_OK) {
        snprintf(msg, sizeof(msg), "Failed to GET wait API: %s",
                 curl_easy_strerror(curl_rc));
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        pam_rc = PAM_AUTHINFO_UNAVAIL;
        goto cleanup;
    }
    pam_rc = pam_rc_for_http(http_code, 200);
    if (pam_rc != PAM_SUCCESS) {
        snprintf(msg, sizeof(msg), "Wait API HTTP %ld authAttemptId=%ld",
                 http_code, auth_attempt_id);
        log_pam_error_debug(pamh, cfg, msg);
        ezkey_buf_free(&buf);
        goto cleanup;
    }

    root = cJSON_Parse(buf.data);
    ezkey_buf_free(&buf);
    if (!root) {
        log_pam_error_debug(pamh, cfg, "JSON parse error for wait response");
        pam_rc = PAM_SERVICE_ERR;
        goto cleanup;
    }
    status_val = cJSON_GetObjectItemCaseSensitive(root, "status");
    accepted = (cJSON_IsString(status_val) && status_val->valuestring != NULL &&
                strcmp(status_val->valuestring, "ACCEPTED") == 0);
    if (!accepted && cJSON_IsString(status_val) && status_val->valuestring != NULL) {
        snprintf(msg, sizeof(msg),
                 "Authentication not accepted (authAttemptId=%ld status=%s)",
                 auth_attempt_id, status_val->valuestring);
        log_pam_error_debug(pamh, cfg, msg);
    } else if (!accepted) {
        snprintf(msg, sizeof(msg),
                 "Authentication not accepted (authAttemptId=%ld status=missing)",
                 auth_attempt_id);
        log_pam_error_debug(pamh, cfg, msg);
    }
    cJSON_Delete(root);

    pam_rc = accepted ? PAM_SUCCESS : PAM_AUTH_ERR;

cleanup:
    ezkey_secure_wipe(userpwd, sizeof(userpwd));
    ezkey_secure_wipe(cfg->secret_key, sizeof(cfg->secret_key));
    if (curl_inited) {
        curl_global_cleanup();
    }
    return pam_rc;
}

PAM_EXTERN int pam_sm_authenticate(pam_handle_t *pamh, int flags, int argc,
                                   const char **argv) {
    const char *username;
    int retval;
    int conf_rc;
    EzkeyConfig cfg;
    const char *env_conf;
    char user_msg[768];
    char result_msg[256];

    (void)flags;
    ezkey_config_init(&cfg);
    parse_module_args(argc, argv, &cfg);

    env_conf = getenv(EZKEY_ENV_CONFIG_FILE);
    if (env_conf != NULL && *env_conf != '\0') {
        set_cfg_str(cfg.config_file, sizeof(cfg.config_file), env_conf);
    }

    openlog(EZKEY_MODULE_NAME, LOG_PID, LOG_AUTHPRIV);

    conf_rc = load_config_file(cfg.config_file, &cfg);
    if (conf_rc != 0) {
        int saved_errno = errno;
        apply_env_overrides(&cfg);
        snprintf(user_msg, sizeof(user_msg),
                 "Cannot open config file %s: %s (env overrides still applied)",
                 cfg.config_file, strerror(saved_errno));
        log_pam_error_debug(pamh, &cfg, user_msg);
    } else {
        apply_env_overrides(&cfg);
    }

    log_pam_info(pamh, &cfg, "=== EZKEY PAM MODULE STARTED ===");

    if (cfg.debug) {
        snprintf(user_msg, sizeof(user_msg), "config_file=%s api_url=%s wait=%ds",
                 cfg.config_file, cfg.api_url, cfg.wait_timeout);
        log_pam_info(pamh, &cfg, user_msg);
    }

    if (cfg.integration_key[0] == '\0' || cfg.secret_key[0] == '\0') {
        log_pam_error_debug(pamh, &cfg,
                            "Missing integration_key or secret_key after config "
                            "load");
        closelog();
        return PAM_AUTHINFO_UNAVAIL;
    }

    retval = pam_get_user(pamh, &username, NULL);
    if (retval != PAM_SUCCESS) {
        log_pam_error(pamh, "Failed to get username");
        closelog();
        return retval;
    }
    if (username == NULL || strlen(username) == 0) {
        log_pam_error(pamh, "Username is null or empty");
        closelog();
        return PAM_USER_UNKNOWN;
    }

    snprintf(user_msg, sizeof(user_msg), "Authentication requested for user: %s",
             username);
    log_pam_info(pamh, &cfg, user_msg);

    pam_info(pamh,
             "Approve this SSH login on your Ezkey device for user %s.",
             username);

    retval = ezkey_authenticate_user_api(pamh, &cfg, username);

    snprintf(result_msg, sizeof(result_msg), "Authentication result for %s: %s",
             username, (retval == PAM_SUCCESS) ? "SUCCESS" : "FAILURE");
    log_pam_info(pamh, &cfg, result_msg);
    if (cfg.debug) {
        snprintf(result_msg, sizeof(result_msg), "PAM return code: %d", retval);
        log_pam_info(pamh, &cfg, result_msg);
    }
    log_pam_info(pamh, &cfg, "=== EZKEY PAM MODULE FINISHED ===");
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
