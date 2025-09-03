/*
 * ezkey_config.h - Configuration constants for Ezkey PAM module
 */

#ifndef EZKEY_CONFIG_H
#define EZKEY_CONFIG_H

/* Module identification */
#define EZKEY_MODULE_NAME "pam_ezkey"
#define EZKEY_VERSION "1.0.0-mock"

/* Default Ezkey API endpoints (modify for your environment) */
#define EZKEY_ADMIN_API_URL "http://localhost:9080"
#define EZKEY_AUTH_API_URL "http://localhost:8080" 

/* Timeout settings */
#define EZKEY_WAIT_TIMEOUT 30      /* seconds to wait for user response */
#define EZKEY_API_TIMEOUT 10       /* seconds for API calls */
#define EZKEY_RETRY_COUNT 3        /* number of API retry attempts */

/* Configuration file path */
#define EZKEY_CONFIG_FILE "/etc/security/pam_ezkey.conf"

/* Debug settings */
#ifdef DEBUG
#define EZKEY_DEBUG 1
#else
#define EZKEY_DEBUG 0
#endif

#endif /* EZKEY_CONFIG_H */
