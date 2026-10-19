/*
 * ezkey_session.c — final-verdict and bounded interrupt-cancel helpers (#907).
 */

#include <string.h>

#include "ezkey_config.h"
#include "ezkey_session.h"

int ezkey_result_is_final(const char *result) {
    if (result == NULL || result[0] == '\0') {
        return 0;
    }
    if (strcmp(result, "SUCCESS") == 0 || strcmp(result, "DENIED") == 0 ||
        strcmp(result, "EXPIRED") == 0 || strcmp(result, "TIMEOUT") == 0) {
        return 1;
    }
    return 0;
}

long ezkey_bounded_cancel_timeout(long api_timeout, long rem_secs,
                                  int interrupted) {
    long t = api_timeout;

    if (t > (long)EZKEY_MAX_API_TIMEOUT) {
        t = (long)EZKEY_MAX_API_TIMEOUT;
    }
    if (t < 1) {
        t = 1;
    }
    if (interrupted) {
        /* R5: cancel promptly; cap so 105+5 < LoginGraceTime 120. */
        if (t > (long)EZKEY_INTERRUPT_CANCEL_MAX) {
            t = (long)EZKEY_INTERRUPT_CANCEL_MAX;
        }
        return t;
    }
    if (rem_secs < 1) {
        return 0;
    }
    if (t > rem_secs) {
        t = rem_secs;
    }
    return t;
}

int ezkey_should_cancel_attempt(int have_attempt, int accepted) {
    if (!have_attempt || accepted) {
        return 0;
    }
    return 1;
}
