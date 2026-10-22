/*
 * ezkey_challenge.c — two-digit challenge helpers (Christophe M1 nits) and
 * no-challenge waiting prompt format (#941 F1).
 */

#include "ezkey_challenge.h"

#include <stddef.h>
#include <stdio.h>
#include <security/pam_appl.h>

#include "ezkey_sanitize.h"

int ezkey_challenge_in_range(long challenge) {
    return challenge >= 0 && challenge <= 99;
}

int ezkey_prewait_prompt_mode(int challenge_requested, long challenge) {
    if (ezkey_challenge_in_range(challenge)) {
        return 1;
    }
    if (challenge_requested) {
        return -1;
    }
    return 0;
}

int ezkey_challenge_display_ok(int pam_prompt_rc) {
    return pam_prompt_rc == PAM_SUCCESS;
}

int ezkey_prewait_response_confirmed(const char *resp) {
    size_t i = 0;

    if (resp == NULL) {
        return 0;
    }
    while (resp[i] == ' ' || resp[i] == '\t') {
        i++;
    }
    /* Exact token "ok" (ASCII case-insensitive), optional trailing space. */
    if ((resp[i] == 'o' || resp[i] == 'O') &&
        (resp[i + 1] == 'k' || resp[i + 1] == 'K')) {
        i += 2;
        while (resp[i] == ' ' || resp[i] == '\t' || resp[i] == '\r' ||
               resp[i] == '\n') {
            i++;
        }
        return resp[i] == '\0';
    }
    return 0;
}

int ezkey_format_waiting_prompt(char *buf, size_t buflen,
                                const char *username) {
    char safe_user[128];
    int n;

    if (buf == NULL || buflen == 0 || username == NULL || username[0] == '\0') {
        return -1;
    }
    /* Strip controls / non-ASCII from client-supplied username before PTY. */
    ezkey_sanitize_for_prompt(safe_user, sizeof(safe_user), username);
    if (safe_user[0] == '\0') {
        return -1;
    }
    /* Confirm-before-create (#975): ok here creates the Integration API attempt. */
    n = snprintf(buf, buflen,
                 "Approve this SSH login on your Ezkey device for user %s.\n"
                 "Type ok and press Enter to request device approval: ",
                 safe_user);
    if (n < 0 || (size_t)n >= buflen) {
        return -1;
    }
    return 0;
}

int ezkey_format_challenge_prompt(char *buf, size_t buflen, long challenge) {
    int n;

    if (buf == NULL || buflen == 0 || !ezkey_challenge_in_range(challenge)) {
        return -1;
    }
    n = snprintf(buf, buflen,
                 "Ezkey challenge code: %02ld — enter this code on your "
                 "Ezkey device, then approve.\n"
                 "Type ok and press Enter to wait for Ezkey device approval: ",
                 challenge);
    if (n < 0 || (size_t)n >= buflen) {
        return -1;
    }
    return 0;
}
