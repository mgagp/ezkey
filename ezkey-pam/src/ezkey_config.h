/*
 * ezkey_config.h - Configuration constants for the Ezkey PAM module
 */

#ifndef EZKEY_CONFIG_H
#define EZKEY_CONFIG_H

/* Module identification */
#define EZKEY_MODULE_NAME "pam_ezkey"
#define EZKEY_VERSION "2.2.0"

/* Default Integration API endpoint */
#define EZKEY_DEFAULT_API_URL "http://localhost:7080"

/*
 * Environment variable names.
 * M1: integration_api_url / integration_key / secret_key are NEVER read from
 * the environment by the module — only from the conf file (root:root ≤0600).
 * Docker entrypoint still uses EZKEY_INTEGRATION_* to *write* that conf.
 * Module-side env overrides are limited to timeouts/debug and conf path.
 */
#define EZKEY_ENV_WAIT_TIMEOUT "EZKEY_WAIT_TIMEOUT"
#define EZKEY_ENV_WAIT_POLLING "EZKEY_WAIT_POLLING"
#define EZKEY_ENV_API_TIMEOUT "EZKEY_API_TIMEOUT"
#define EZKEY_ENV_CONFIG_FILE "EZKEY_CONFIG_FILE"
#define EZKEY_ENV_DEBUG "EZKEY_DEBUG"

/* Timeout defaults (seconds) */
#define EZKEY_DEFAULT_WAIT_TIMEOUT 90
#define EZKEY_DEFAULT_WAIT_POLLING 2
#define EZKEY_DEFAULT_API_TIMEOUT 10

/* Configuration file path loaded at PAM runtime */
#define EZKEY_DEFAULT_CONFIG_FILE "/etc/security/pam_ezkey.conf"

/* Debug compile-time flag (also forces debug=1 at init; conf can still override) */
#ifdef DEBUG
#define EZKEY_DEBUG 1
#else
#define EZKEY_DEBUG 0
#endif

#endif /* EZKEY_CONFIG_H */
