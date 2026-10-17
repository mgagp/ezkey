/*
 * ezkey_config.h - Configuration constants for the Ezkey PAM module
 */

#ifndef EZKEY_CONFIG_H
#define EZKEY_CONFIG_H

/* Module identification */
#define EZKEY_MODULE_NAME "pam_ezkey"
#define EZKEY_VERSION "2.2.6"

/*
 * No compiled-in default Integration API URL (Patrick B1 / M1 review).
 * integration_api_url must be set in the conf file; empty → PAM_AUTHINFO_UNAVAIL.
 */

/*
 * The module does NOT read credentials, conf path, debug, or timeouts from the
 * process environment (su/sudo inheritance). Docker compose ENV is applied only
 * by docker-entrypoint.sh when it *writes* the root-owned conf file.
 * PAM module args still allow: debug, conf=/path (from root-owned pam.d).
 */

/* Timeout defaults when keys are absent from conf (seconds) */
#define EZKEY_DEFAULT_WAIT_TIMEOUT 75
#define EZKEY_DEFAULT_WAIT_POLLING 2
#define EZKEY_DEFAULT_API_TIMEOUT 10
/*
 * Single auth wall-clock budget (Patrick P3 / #726): finish under demo sshd
 * LoginGraceTime 120 with margin. pam_sm_authenticate starts the clock;
 * wait = min(conf, remaining - CURL_SLACK); too little left → UNAVAIL + attempt
 * line. Enter is NOT signal-bounded (PAM must not touch host signals); measure
 * pam_prompt duration with CLOCK_MONOTONIC and reject if > MAX_ENTER_SECS.
 * See STATUS § timeouts inventory.
 */
#define EZKEY_AUTH_BUDGET_SECS 105
#define EZKEY_MAX_API_TIMEOUT 10
#define EZKEY_CURL_WAIT_SLACK 15
#define EZKEY_MIN_WAIT_SECS 2
#define EZKEY_MAX_ENTER_SECS 25
#define EZKEY_MAX_WAIT_TIMEOUT 90

/* Configuration file path loaded at PAM runtime (override only via pam.d conf=) */
#define EZKEY_DEFAULT_CONFIG_FILE "/etc/security/pam_ezkey.conf"

/* Debug compile-time flag (also forces debug=1 at init; conf can still override) */
#ifdef DEBUG
#define EZKEY_DEBUG 1
#else
#define EZKEY_DEBUG 0
#endif

#endif /* EZKEY_CONFIG_H */
