/*
 * ezkey_config.h - Configuration constants for the Ezkey PAM module
 */

#ifndef EZKEY_CONFIG_H
#define EZKEY_CONFIG_H

/* Module identification */
#define EZKEY_MODULE_NAME "pam_ezkey"
#define EZKEY_VERSION "2.0.0"

/* Default Integration API endpoint */
#define EZKEY_DEFAULT_API_URL "http://localhost:7080"

/* Environment variable names (runtime overrides) */
#define EZKEY_ENV_API_URL "EZKEY_INTEGRATION_API_URL"
#define EZKEY_ENV_API_URL_LEGACY "EZKEY_M2M_API_URL"
#define EZKEY_ENV_INTEGRATION_KEY "EZKEY_INTEGRATION_KEY"
#define EZKEY_ENV_SECRET_KEY "EZKEY_SECRET_KEY"
#define EZKEY_ENV_WAIT_TIMEOUT "EZKEY_WAIT_TIMEOUT"
#define EZKEY_ENV_WAIT_POLLING "EZKEY_WAIT_POLLING"
#define EZKEY_ENV_API_TIMEOUT "EZKEY_API_TIMEOUT"
#define EZKEY_ENV_CONFIG_FILE "EZKEY_CONFIG_FILE"

/* Timeout defaults (seconds) */
#define EZKEY_DEFAULT_WAIT_TIMEOUT 90
#define EZKEY_DEFAULT_WAIT_POLLING 2
#define EZKEY_DEFAULT_API_TIMEOUT 10

/* Configuration file path loaded at PAM runtime */
#define EZKEY_DEFAULT_CONFIG_FILE "/etc/security/pam_ezkey.conf"

/* Debug compile-time flag */
#ifdef DEBUG
#define EZKEY_DEBUG 1
#else
#define EZKEY_DEBUG 0
#endif

#endif /* EZKEY_CONFIG_H */
