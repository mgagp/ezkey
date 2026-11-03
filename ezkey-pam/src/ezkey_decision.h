/*
 * ezkey_decision.h — auth decision contracts testable without full PAM load
 * (#1016 cycle 2b P1). Shared by pam_ezkey.c and test_decision_harness.c so
 * mutations of wait/HTTP/URL policy fail make test.
 *
 * @since 2026
 */

#ifndef EZKEY_DECISION_H
#define EZKEY_DECISION_H

#include <security/pam_modules.h>
#include <signal.h>
#include <string.h>
#include <strings.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Map HTTP status to PAM: expected → SUCCESS, else AUTHINFO_UNAVAIL.
 *
 * @param http_code response code
 * @param expected required code (201 create / 200 wait)
 * @return PAM_SUCCESS or PAM_AUTHINFO_UNAVAIL
 */
static inline int ezkey_pam_rc_for_http(long http_code, long expected) {
    if (http_code == expected) {
        return PAM_SUCCESS;
    }
    (void)http_code;
    return PAM_AUTHINFO_UNAVAIL;
}

/**
 * Scheme gate: https always OK; http only when lab_http is non-zero.
 *
 * @param url Integration API base URL
 * @param lab_http conf lab_http
 * @return 1 allowed, 0 rejected
 */
static inline int ezkey_api_url_allowed_url(const char *url, int lab_http) {
    if (url == NULL || url[0] == '\0') {
        return 0;
    }
    if (strncasecmp(url, "https://", 8) == 0) {
        return 1;
    }
    if (lab_http && strncasecmp(url, "http://", 7) == 0) {
        return 1;
    }
    return 0;
}

/**
 * Wait-status decision after a successful /wait JSON parse.
 * PENDING/READ (or NULL treated as continue) → return 0 (keep polling).
 * Terminal outcomes set *pam_rc_out / *summary_out and return 1.
 *
 * ACCEPTED → PAM_SUCCESS / SUCCESS (must never become AUTH_ERR).
 * REJECTED/INVALID/unknown → PAM_AUTH_ERR / DENIED.
 * EXPIRED → PAM_AUTH_ERR / EXPIRED.
 *
 * @param status_str wait JSON status (may be NULL)
 * @param pam_rc_out receives PAM code on terminal
 * @param summary_out receives attempt result literal on terminal
 * @return 1 terminal, 0 continue polling
 */
static inline int ezkey_wait_status_decide(const char *status_str, int *pam_rc_out,
                                          const char **summary_out) {
    if (pam_rc_out == NULL || summary_out == NULL) {
        return 1;
    }
    if (status_str != NULL && strcmp(status_str, "ACCEPTED") == 0) {
        *pam_rc_out = PAM_SUCCESS;
        *summary_out = "SUCCESS";
        return 1;
    }
    if (status_str != NULL && strcmp(status_str, "EXPIRED") == 0) {
        *pam_rc_out = PAM_AUTH_ERR;
        *summary_out = "EXPIRED";
        return 1;
    }
    if (status_str != NULL &&
        (strcmp(status_str, "REJECTED") == 0 ||
         strcmp(status_str, "INVALID") == 0)) {
        *pam_rc_out = PAM_AUTH_ERR;
        *summary_out = "DENIED";
        return 1;
    }
    if (status_str != NULL && strcmp(status_str, "PENDING") != 0 &&
        strcmp(status_str, "READ") != 0) {
        *pam_rc_out = PAM_AUTH_ERR;
        *summary_out = "DENIED";
        return 1;
    }
    /* PENDING, READ, or missing → continue. */
    return 0;
}

/**
 * CURLOPT_XFERINFOFUNCTION abort: non-zero flag → abort transfer (1).
 *
 * @param clientp pointer to volatile sig_atomic_t abort flag
 * @return 1 abort, 0 continue
 */
static inline int ezkey_xferinfo_should_abort(void *clientp) {
    volatile sig_atomic_t *flag = (volatile sig_atomic_t *)clientp;

    if (flag != NULL && *flag != 0) {
        return 1;
    }
    return 0;
}

#ifdef __cplusplus
}
#endif

#endif /* EZKEY_DECISION_H */
