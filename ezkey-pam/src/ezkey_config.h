/*
 * ezkey_config.h - Configuration constants for the Ezkey PAM module
 */

#ifndef EZKEY_CONFIG_H
#define EZKEY_CONFIG_H

/* Module identification */
#define EZKEY_MODULE_NAME "pam_ezkey"
#define EZKEY_VERSION "2.2.10"

/*
 * Cancel outcome on the authpriv attempt summary line (Patrick #897 M2;
 * Christophe observability: FINAL for HTTP 409 already-final).
 * Only these literals — never free-form HTTP text.
 */
#define EZKEY_CANCEL_OK "ok"
#define EZKEY_CANCEL_FAIL "fail"
#define EZKEY_CANCEL_SKIPPED "skipped"
#define EZKEY_CANCEL_FINAL "final"

/*
 * No compiled-in default Integration API URL (Patrick B1 / M1 review).
 * integration_api_url must be set in the conf file; empty → PAM_AUTHINFO_UNAVAIL.
 */

/*
 * The module does NOT read credentials, conf path, debug, or timeouts from the
 * process environment (su/sudo inheritance). Docker compose ENV is applied only
 * by docker-entrypoint.sh when it *writes* the root-owned conf file.
 * PAM module args still allow: debug, conf=/path, wait_timeout=N (from
 * root-owned pam.d). Env EZKEY_WAIT_TIMEOUT is entrypoint-only.
 */

/* Timeout defaults when keys are absent from conf (seconds) */
#define EZKEY_DEFAULT_WAIT_TIMEOUT 90
#define EZKEY_DEFAULT_WAIT_POLLING 2
#define EZKEY_DEFAULT_API_TIMEOUT 10
/*
 * Single auth wall-clock budget (Patrick P3 / #726): finish under demo sshd
 * LoginGraceTime 120 with margin. pam_sm_authenticate starts the clock;
 * wait = min(conf, remaining - CURL_SLACK); too little left → UNAVAIL + attempt
 * line. Enter is NOT signal-bounded (PAM must not touch host signals); measure
 * pam_prompt duration with CLOCK_MONOTONIC and reject if > MAX_ENTER_SECS.
 * See STATUS § timeouts inventory.
 *
 * Server attempt TTL (#895 / PR-B): create sends ttlSeconds = planned wait
 * (floor EZKEY_MIN_TTL_SECONDS — Integration API rejects < 15). Align /wait
 * on the applied duration from the create response (monotonic), never on
 * server wall clock vs host clock. Cancel best-effort on non-ACCEPTED exit.
 */
#define EZKEY_AUTH_BUDGET_SECS 105
#define EZKEY_MAX_API_TIMEOUT 10
#define EZKEY_CURL_WAIT_SLACK 15
#define EZKEY_MIN_WAIT_SECS 2
#define EZKEY_MIN_TTL_SECONDS 15
#define EZKEY_MAX_ENTER_SECS 25
#define EZKEY_MAX_WAIT_TIMEOUT 90
/*
 * #907 review (Patrick/Christophe, option A): chunked /wait so the waiting
 * process can abort on client disconnect (SIGTERM) and cancel in-process.
 * Interrupt cancel is capped at min(api_timeout, INTERRUPT_CANCEL_MAX) so
 * worst-case auth+cancel stays under LoginGraceTime 120 (105+5=110).
 */
#define EZKEY_WAIT_CHUNK_SECS 5
#define EZKEY_INTERRUPT_CANCEL_MAX 5

/* Configuration file path loaded at PAM runtime (override only via pam.d conf=) */
#define EZKEY_DEFAULT_CONFIG_FILE "/etc/security/pam_ezkey.conf"

/* Debug compile-time flag (also forces debug=1 at init; conf can still override) */
#ifdef DEBUG
#define EZKEY_DEBUG 1
#else
#define EZKEY_DEBUG 0
#endif

#endif /* EZKEY_CONFIG_H */
