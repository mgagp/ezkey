/*
 * ezkey_challenge.h — two-digit challenge helpers (number matching).
 *
 * @since 2026
 */

#ifndef EZKEY_CHALLENGE_H
#define EZKEY_CHALLENGE_H

/**
 * True when challenge is in the closed range 0..99 (two-digit number matching).
 *
 * @param challenge candidate from create response (or -1 if absent)
 * @return 1 if in range, 0 otherwise
 */
int ezkey_challenge_in_range(long challenge);

/**
 * True when pam_prompt successfully displayed the challenge code.
 * There is no pam_info fallback — OpenSSH buffers bare TEXT_INFO the same
 * way, and production uses a single PAM_PROMPT_ECHO_OFF path (Patrick).
 *
 * @param pam_prompt_rc return from pam_prompt(PAM_PROMPT_ECHO_OFF, ...)
 * @return 1 if displayed, 0 otherwise
 */
int ezkey_challenge_display_ok(int pam_prompt_rc);

#endif /* EZKEY_CHALLENGE_H */
