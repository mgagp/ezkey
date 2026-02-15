/*
 * pam_ezkey.c - Ezkey PAM Module (Mock Version)
 * 
 * Mock PAM module for testing SSH integration with Ezkey MFA system
 * This version only logs activities for development purposes
 */

#include <curl/curl.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
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
static int ezkey_authenticate_user(pam_handle_t *pamh, const char *username);
static void log_pam_info(pam_handle_t *pamh, const char *message);
static void log_pam_error(pam_handle_t *pamh, const char *message);
static int parse_config_args(int argc, const char **argv);

/* Global configuration */
static int debug_mode = 0;
static int mock_success = 1; /* 1=success, 0=failure for testing */
static int mock_delay = 5;   /* seconds to simulate wait */

static size_t ezkey_curl_write_cb(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t realsize = size * nmemb;
    strncat((char *)userp, (char *)contents, realsize);
    return realsize;
}

static int ezkey_authenticate_user_api(pam_handle_t *pamh, const char *username) {
    CURL *curl;
    CURLcode res;
    char response[1024] = {0};
    long authAttemptId = -1;

    // 1. Création de l'authAttempt
    curl = curl_easy_init();
    if (!curl) {
        log_pam_error(pamh, "curl_easy_init failed");
        return PAM_AUTH_ERR;
    }
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    curl_easy_setopt(curl, CURLOPT_URL, "http://host.docker.internal:9080/api/v1/auth-attempts");
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, "{\"enrollmentId\": 1}");
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, response);

    res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);

    if (res != CURLE_OK) {
        log_pam_error(pamh, "Failed to POST auth-attempt");
        return PAM_AUTH_ERR;
    }

    // 2. Extraction de l'ID depuis le JSON (cJSON)
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
    authAttemptId = (long)id_val->valuedouble; // cJSON stores numbers as double
    cJSON_Delete(root);

    // 3. Appel de l'API wait
    char wait_url[256];
    snprintf(wait_url, sizeof(wait_url),
             "http://host.docker.internal:9080/api/v1/auth-attempts/%ld/wait", authAttemptId);

    memset(response, 0, sizeof(response));
    curl = curl_easy_init();
    if (!curl) {
        log_pam_error(pamh, "curl_easy_init failed for wait");
        return PAM_AUTH_ERR;
    }
    curl_easy_setopt(curl, CURLOPT_URL, wait_url);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, ezkey_curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, response);

    res = curl_easy_perform(curl);
    curl_easy_cleanup(curl);

    if (res != CURLE_OK) {
        log_pam_error(pamh, "Failed to GET wait API");
        return PAM_AUTH_ERR;
    }

    // 4. Interprétation de la réponse : status doit être "ACCEPTED"
    root = cJSON_Parse(response);
    if (!root) {
        log_pam_error(pamh, "JSON parse error for wait response");
        return PAM_AUTH_ERR;
    }
    cJSON *status_val = cJSON_GetObjectItemCaseSensitive(root, "status");
    int accepted = (cJSON_IsString(status_val) && status_val->valuestring && strcmp(status_val->valuestring, "ACCEPTED") == 0);
    cJSON_Delete(root);
    if (accepted) {
        return PAM_SUCCESS;
    }
    log_pam_error(pamh, "Authentication rejected or timeout (status not ACCEPTED)");
    return PAM_AUTH_ERR;
}

static size_t write_callback(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t realsize = size * nmemb;
    FILE *fp = (FILE *)userp;
    fwrite(contents, size, nmemb, fp);
    return realsize;
}

int ezkey_poc1_prooftoken() {
    CURL *curl;
    CURLcode res;
    FILE *fp = fopen("/tmp/prooftoken", "w");
    if (!fp) return -1;

    curl = curl_easy_init();
    if(curl) {
        curl_easy_setopt(curl, CURLOPT_URL, "http://host.docker.internal:8085/api/v1/sim/prooftoken");
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, fp);
        res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);
        fclose(fp);
        return (res == CURLE_OK) ? 0 : -2;
    }
    fclose(fp);
    return -3;
}

/*
 * Main authentication function called by PAM
 */
PAM_EXTERN int pam_sm_authenticate(pam_handle_t *pamh, int flags,
                                   int argc, const char **argv) {
    const char *username;
    int retval;
    int i;

    int fd = open("/tmp/pam_ezkey_was_here", O_CREAT|O_WRONLY, 0644);

    if (fd != -1) {
        write(fd, "called\n", 7);
        close(fd);
    }

    ezkey_poc1_prooftoken();

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

    /* Perform mock Ezkey authentication */
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
 * Mock Ezkey authentication - simulates API calls to Ezkey backend
 */
static int ezkey_authenticate_user(pam_handle_t *pamh, const char *username) {
    char msg[512];
    int i;

    log_pam_info(pamh, "--- Starting Ezkey MFA Process ---");

    /* Step 1: Mock API call to create auth attempt */
    snprintf(msg, sizeof(msg), 
            "MOCK: POST %s/api/v1/auth-attempts (user: %s)", 
            EZKEY_ADMIN_API_URL, username);
    log_pam_info(pamh, msg);

    /* Simulate API processing time */
    sleep(1);

    /* Mock auth attempt ID */
    const char *mock_auth_id = "auth_12345_mock";
    snprintf(msg, sizeof(msg), 
            "MOCK: Created auth attempt ID: %s", mock_auth_id);
    log_pam_info(pamh, msg);

    /* Step 2: Mock sending notification to mobile device */
    log_pam_info(pamh, "MOCK: Notification sent to user's mobile device");
    log_pam_info(pamh, "MOCK: User should receive push notification now");

    /* Step 3: Mock wait for user response using Wait API */
    snprintf(msg, sizeof(msg), 
            "MOCK: Waiting for user response (timeout: %ds)...", 
            EZKEY_WAIT_TIMEOUT);
    log_pam_info(pamh, msg);

    /* Simulate waiting with progress updates */
    for (i = 0; i < mock_delay; i++) {
        snprintf(msg, sizeof(msg), 
                "MOCK: Polling... %d/%d seconds", i+1, mock_delay);
        if (debug_mode) {
            log_pam_info(pamh, msg);
        }
        sleep(1);
    }

    /* Step 4: Mock API response */
    if (mock_success) {
        snprintf(msg, sizeof(msg), 
                "MOCK: GET %s/api/v1/auth-attempts/%s/wait -> ACCEPTED", 
                EZKEY_ADMIN_API_URL, mock_auth_id);
        log_pam_info(pamh, msg);
        log_pam_info(pamh, "MOCK: User accepted authentication on mobile device");
        return PAM_SUCCESS;
    } else {
        snprintf(msg, sizeof(msg), 
                "MOCK: GET %s/api/v1/auth-attempts/%s/wait -> REJECTED", 
                EZKEY_ADMIN_API_URL, mock_auth_id);
        log_pam_info(pamh, msg);
        log_pam_info(pamh, "MOCK: User rejected authentication or timeout occurred");
        return PAM_AUTH_ERR;
    }
}

/*
 * Parse module configuration arguments
 */
static int parse_config_args(int argc, const char **argv) {
    int i;
    
    for (i = 0; i < argc; i++) {
        if (strcmp(argv[i], "debug") == 0) {
            debug_mode = 1;
        } else if (strcmp(argv[i], "mock_failure") == 0) {
            mock_success = 0;
        } else if (strncmp(argv[i], "mock_delay=", 11) == 0) {
            mock_delay = atoi(argv[i] + 11);
            if (mock_delay < 1) mock_delay = 5;
            if (mock_delay > 60) mock_delay = 60;
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
