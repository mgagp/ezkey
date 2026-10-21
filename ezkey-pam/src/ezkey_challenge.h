/*
 * ezkey_challenge.h — two-digit challenge helpers + waiting-prompt format.
 *
 * @since 2026
 */

#ifndef EZKEY_CHALLENGE_H
#define EZKEY_CHALLENGE_H

#include <stddef.h>

/**
 * True when challenge is in the closed range 0..99 (two-digit number matching).
 *
 * @param challenge candidate from create response (or -1 if absent)
 * @return 1 if in range, 0 otherwise
 */
int ezkey_challenge_in_range(long challenge);

/**
 * True when pam_prompt successfully displayed a pre-wait prompt (challenge or
 * no-challenge waiting message). There is no pam_info fallback — OpenSSH
 * buffers bare TEXT_INFO until the next prompt or the end of the round, so
 * production uses PAM_PROMPT_ECHO_OFF (Patrick / #941 F1).
 *
 * @param pam_prompt_rc return from pam_prompt(PAM_PROMPT_ECHO_OFF, ...)
 * @return 1 if displayed, 0 otherwise
 */
int ezkey_challenge_display_ok(int pam_prompt_rc);

/**
 * Format the no-challenge SSH waiting prompt (shown via PAM_PROMPT_ECHO_OFF
 * before /wait so OpenSSH flushes it to the PTY — #941).
 *
 * @param buf destination buffer
 * @param buflen capacity of buf
 * @param username Linux username (also Ezkey userIdentifier); must be non-empty
 * @return 0 on success, -1 if args invalid or the message does not fit
 */
int ezkey_format_waiting_prompt(char *buf, size_t buflen, const char *username);

#endif /* EZKEY_CHALLENGE_H */
