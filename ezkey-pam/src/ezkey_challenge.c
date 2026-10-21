/*
 * ezkey_challenge.c — two-digit challenge helpers (Christophe M1 nits) and
 * no-challenge waiting prompt format (#941 F1).
 */

#include "ezkey_challenge.h"

#include <stdio.h>
#include <security/pam_appl.h>

#include "ezkey_sanitize.h"

int ezkey_challenge_in_range(long challenge) {
    return challenge >= 0 && challenge <= 99;
}

int ezkey_challenge_display_ok(int pam_prompt_rc) {
    return pam_prompt_rc == PAM_SUCCESS;
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
    n = snprintf(buf, buflen,
                 "Approve this SSH login on your Ezkey device for user %s.\n"
                 "Press Enter to wait for Ezkey device approval: ",
                 safe_user);
    if (n < 0 || (size_t)n >= buflen) {
        return -1;
    }
    return 0;
}
