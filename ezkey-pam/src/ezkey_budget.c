/*
 * ezkey_budget.c — wait/TTL clamp and planned-wait math.
 */

#include "ezkey_budget.h"
#include "ezkey_config.h"

int ezkey_clamp_wait_timeout(int value) {
    if (value < EZKEY_MIN_TTL_SECONDS) {
        return EZKEY_MIN_TTL_SECONDS;
    }
    if (value > EZKEY_MAX_WAIT_TIMEOUT) {
        return EZKEY_MAX_WAIT_TIMEOUT;
    }
    return value;
}

int ezkey_planned_wait_from_rem(int wait_timeout, long rem_secs) {
    int planned;

    planned = wait_timeout;
    if (rem_secs <= (long)EZKEY_CURL_WAIT_SLACK + (long)EZKEY_MIN_TTL_SECONDS) {
        return -1;
    }
    if ((long)planned > rem_secs - (long)EZKEY_CURL_WAIT_SLACK) {
        planned = (int)(rem_secs - (long)EZKEY_CURL_WAIT_SLACK);
    }
    if (planned < EZKEY_MIN_TTL_SECONDS) {
        return -1;
    }
    return planned;
}
