/*
 * ezkey_challenge.c — two-digit challenge helpers (Christophe M1 nits).
 */

#include "ezkey_challenge.h"

#include <security/pam_appl.h>

int ezkey_challenge_in_range(long challenge) {
    return challenge >= 0 && challenge <= 99;
}

int ezkey_challenge_display_ok(int pam_prompt_rc) {
    return pam_prompt_rc == PAM_SUCCESS;
}
