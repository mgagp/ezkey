/*
 * pam_ezkey.c - Ezkey PAM Module
 *
 * PAM module for SSH integration with Ezkey MFA via the Integration API.
 * Credentials are read from environment variables:
 *   EZKEY_M2M_API_URL      - Integration API base URL (legacy name; default: http://localhost:7080)
 *   EZKEY_INTEGRATION_KEY  - API integration key
 *   EZKEY_SECRET_KEY       - API secret key
 */

#include <curl/curl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <cjson/cJSON.h> // Parsing JSON via cJSON
#include <unistd.h>
#include <syslog.h>
#include <security/pam_modules.h>
#include <security/pam_ext.h>
#include <security/pam_appl.h>
#include "ezkey_config.h"

/* PAM entry points */
PAM_EXTERN int pam_sm_authenticate(pam_handle_t *pamh, int flags,
                                   int argc, const char **argv);
PAM_EXTERN int pam_sm_setcred(pam_handle_t *pamh, int flags,
                              int argc, const char **argv);

/* Internal functions */
static int ezkey_authenticate_user_api(pam_handle_t *pamh, const char *username);
static void log_pam_info(pam_handle_t *pamh, const char *message);
static void log_pam_error(pam_handle_t *pamh, const char *message);
static int parse_config_args(int argc, const char **argv);

/* Global configuration */
static int debug_mode = 0;

static size_t ezkey_curl_write_cb(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t realsize = size * nmemb;
    strncat((char *)userp, (char *)contents, realsize);
    return realsize;
}

static int ezkey_authenticate_user_api(pam_handle_t *pamh, const char *username) {
    CURL *curl;
    CURLcode res;
    char response[2048] = {0};
    long authAttemptId = -1;

    /* Read runtime configuration from environment variables */
    const char *m2m_url = getenv(EZKEY_ENV_M2M_URL);
    if (!m2m_url || strlen(m2m_url) == 0) {
        m2m_url = EZKEY_M2M_API_URL;
    }
    const char *integration_key = getenv(EZKEY_ENV_INTEGRATION_KEY);
    const char *secret_key = getenv(EZKEY_ENV_SECRET_KEY);

    if (!integration_key || !secret_key ||
        strlen(integration_key) == 0 || strlen(secret_key) == 0) {
        log_pam_error(pamh, "EZKEY_INTEGRATION_KEY or EZKEY_SECRET_KEY not set");
        return PAM_AUTH_ERR;
    }

    /* Build userpwd string for Basic Auth: integrationKey:secretKey */
    char userpwd[512];
    snprintf(userpwd, sizeof(userpwd), "%s:%s", integration_key, secret_key);

    /* Build POST body: {"userIdentifier": "<username>"} */
    char post_body[512];
    snprintf(post_body, sizeof(post_body), "{\"userIdentifier\": \"%s\"}", username);

    /* Build POST URL */
    char post_url[512];
    snprintf(post_url, sizeof(post_url), "%s/api/v1/auth-attempts", m2m_url);

    if (debug_mode) {
        char dbg[512];
        snprintf(dbg, sizeof(dbg), "POST %s body=%s", post_url, post_body);
        log_pam_info(pamh, dbg);
    }

    /* 1. Create auth attempt */
    curl = curl_easy_init();
    if (!curl) {
        log_pam_error(pamh, "curl_easy_init failed");
        return PAM_AUTH_ERR;
    }
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    curl_easy_setopt(curl, CURLOPT_URL, post_url);
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, post_body);
    curl_easy_setopt(curl, CURLOPT_USERPWD, userpwd);
    curl_easy_setopt(curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, response);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, (long)EZKEY_API_TIMEOUT);

    res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);

    if (res != CURLE_OK) {
        char err[256];
        snprintf(err, sizeof(err), "Failed to POST auth-attempt: %s", curl_easy_strerror(res));
        log_pam_error(pamh, err);
        return PAM_AUTH_ERR;
    }

    if (debug_mode) {
        char dbg[512];
        snprintf(dbg, sizeof(dbg), "POST response: %s", response);
        log_pam_info(pamh, dbg);
    }

    /* 2. Extract authAttemptId from JSON response */
    cJSON *root = cJSON_Parse(response);
    if (!root) {
        log_pam_error(pamh, "JSON parse error for authAttemptId");
        return PAM_AUTH_ERR;
    }
    cJSON *id_val = cJSON_GetObjectItemCaseSensitive(root, "authAttemptId");
    if (!cJSON_IsNumber(id_val)) {
        log_pam_error(pamh, "authAttemptId not found or not a number");
        cJSON_Delete(root);
        return PAM_AUTH_ERR;
    }
    authAttemptId = (long)id_val->valuedouble;
    cJSON_Delete(root);

    char id_msg[128];
    snprintf(id_msg, sizeof(id_msg), "Auth attempt created, id=%ld", authAttemptId);
    log_pam_info(pamh, id_msg);

    /* 3. Call wait API with timeout and polling query params */
    char wait_url[512];
    snprintf(wait_url, sizeof(wait_url),
             "%s/api/v1/auth-attempts/%ld/wait?timeout=%d&polling=%d",
             m2m_url, authAttemptId, EZKEY_WAIT_TIMEOUT, EZKEY_WAIT_POLLING);

    if (debug_mode) {
        log_pam_info(pamh, wait_url);
    }

    memset(response, 0, sizeof(response));
    curl = curl_easy_init();
    if (!curl) {
        log_pam_error(pamh, "curl_easy_init failed for wait");
        return PAM_AUTH_ERR;
    }
    curl_easy_setopt(curl, CURLOPT_URL, wait_url);
    curl_easy_setopt(curl, CURLOPT_USERPWD, userpwd);
    curl_easy_setopt(curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, response);
    /* Timeout slightly longer than server-side wait to allow response to arrive */
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, (long)(EZKEY_WAIT_TIMEOUT + 10));

    res = curl_easy_perform(curl);
    curl_easy_cleanup(curl);

    if (res != CURLE_OK) {
        char err[256];
        snprintf(err, sizeof(err), "Failed to GET wait API: %s", curl_easy_strerror(res));
        log_pam_error(pamh, err);
        return PAM_AUTH_ERR;
    }

    if (debug_mode) {
        char dbg[512];
        snprintf(dbg, sizeof(dbg), "Wait response: %s", response);
        log_pam_info(pamh, dbg);
    }

    /* 4. Check status == "ACCEPTED" */
    root = cJSON_Parse(response);
    if (!root) {
        log_pam_error(pamh, "JSON parse error for wait response");
        return PAM_AUTH_ERR;
    }
    cJSON *status_val = cJSON_GetObjectItemCaseSensitive(root, "status");
    int accepted = (cJSON_IsString(status_val) &&
                    status_val->valuestring &&
                    strcmp(status_val->valuestring, "ACCEPTED") == 0);
    cJSON_Delete(root);

    if (accepted) {
        return PAM_SUCCESS;
    }
    log_pam_error(pamh, "Authentication rejected or timeout (status not ACCEPTED)");
    return PAM_AUTH_ERR;
}

/*
 * Main authentication function called by PAM
 */
PAM_EXTERN int pam_sm_authenticate(pam_handle_t *pamh, int flags,
                                   int argc, const char **argv) {
    const char *username;
    int retval;
    int i;

    /* Parse module arguments */
    parse_config_args(argc, argv);

    /* Open syslog for this session */
    openlog(EZKEY_MODULE_NAME, LOG_PID, LOG_AUTHPRIV);

    log_pam_info(pamh, "=== EZKEY PAM MODULE STARTED ===");
    
    if (debug_mode) {
        char debug_msg[256];
        snprintf(debug_msg, sizeof(debug_msg), 
                "Module arguments: argc=%d", argc);
        log_pam_info(pamh, debug_msg);
        
        for (i = 0; i < argc; i++) {
            char arg_msg[256];
            snprintf(arg_msg, sizeof(arg_msg), 
                    "  argv[%d] = %s", i, argv[i]);
            log_pam_info(pamh, arg_msg);
        }
    }

    /* Get the username */
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

    char user_msg[256];
    snprintf(user_msg, sizeof(user_msg), 
            "Authentication requested for user: %s", username);
    log_pam_info(pamh, user_msg);

    /* Perform Ezkey MFA authentication via M2M API */
    retval = ezkey_authenticate_user_api(pamh, username);

    char result_msg[256];
    snprintf(result_msg, sizeof(result_msg), 
            "Authentication result for %s: %s", 
            username, 
            (retval == PAM_SUCCESS) ? "SUCCESS" : "FAILURE");
    log_pam_info(pamh, result_msg);

    log_pam_info(pamh, "=== EZKEY PAM MODULE FINISHED ===");
    closelog();

    return retval;
}

/*
 * Credential management function (usually a no-op for our use case)
 */
PAM_EXTERN int pam_sm_setcred(pam_handle_t *pamh, int flags,
                              int argc, const char **argv) {
    return PAM_SUCCESS;
}

/*
 * Parse module configuration arguments
 */
static int parse_config_args(int argc, const char **argv) {
    int i;

    for (i = 0; i < argc; i++) {
        if (strcmp(argv[i], "debug") == 0) {
            debug_mode = 1;
        }
    }

    return 0;
}

/*
 * Logging helper functions
 */


// File logger for info messages (logs to /tmp/pam_ezkey.out)
static void ezkey_filesyslog_out(const char *message) {
    FILE *fp = fopen("/tmp/pam_ezkey.out", "a");
    if (fp) {
        fprintf(fp, "%s\n", message);
        fclose(fp);
    }
}

// File logger for error messages (logs to /tmp/pam_ezkey.err)
static void ezkey_filesyslog_err(const char *message) {
    FILE *fp = fopen("/tmp/pam_ezkey.err", "a");
    if (fp) {
        fprintf(fp, "%s\n", message);
        fclose(fp);
    }
}

static void log_pam_info(pam_handle_t *pamh, const char *message) {
    syslog(LOG_INFO, "%s: %s", EZKEY_MODULE_NAME, message);
    ezkey_filesyslog_out(message);
    if (debug_mode) {
        pam_syslog(pamh, LOG_INFO, "%s", message);
    }
}

static void log_pam_error(pam_handle_t *pamh, const char *message) {
    syslog(LOG_ERR, "%s: ERROR: %s", EZKEY_MODULE_NAME, message);
    ezkey_filesyslog_err(message);
    pam_syslog(pamh, LOG_ERR, "ERROR: %s", message);
}
