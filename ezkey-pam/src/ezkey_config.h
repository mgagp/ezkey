/*
 * ezkey_config.h - Configuration constants for Ezkey PAM module
 */

#ifndef EZKEY_CONFIG_H
#define EZKEY_CONFIG_H

/* Module identification */
#define EZKEY_MODULE_NAME "pam_ezkey"
#define EZKEY_VERSION "1.1.0"

/* Default Integration API endpoint (legacy env name EZKEY_M2M_API_URL) */
#define EZKEY_M2M_API_URL "http://localhost:7080"

/* Environment variable names for runtime configuration */
#define EZKEY_ENV_M2M_URL        "EZKEY_M2M_API_URL"
#define EZKEY_ENV_INTEGRATION_KEY "EZKEY_INTEGRATION_KEY"
#define EZKEY_ENV_SECRET_KEY     "EZKEY_SECRET_KEY"

/* Timeout settings */
#define EZKEY_WAIT_TIMEOUT 30      /* seconds to wait for user response */
#define EZKEY_WAIT_POLLING 2       /* polling interval in seconds */
#define EZKEY_API_TIMEOUT 10       /* seconds for API calls */

/* Configuration file path */
#define EZKEY_CONFIG_FILE "/etc/security/pam_ezkey.conf"

/* Debug settings */
#ifdef DEBUG
#define EZKEY_DEBUG 1
#else
#define EZKEY_DEBUG 0
#endif

#endif /* EZKEY_CONFIG_H */
