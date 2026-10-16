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
 * True when at least one PAM conversation call successfully displayed the code.
 * pam_prompt is preferred; pam_info is an acceptable fallback when prompt fails
 * but info succeeds. Both failing means the user cannot see the code.
 *
 * @param pam_prompt_rc return from pam_prompt(PAM_TEXT_INFO, ...)
 * @param pam_info_rc return from pam_info(...), or PAM_AUTH_ERR if not called
 * @return 1 if displayed, 0 if neither call succeeded
 */
int ezkey_challenge_display_ok(int pam_prompt_rc, int pam_info_rc);

#endif /* EZKEY_CHALLENGE_H */
