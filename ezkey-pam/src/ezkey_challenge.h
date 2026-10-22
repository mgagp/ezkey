/*
 * ezkey_challenge.h — two-digit challenge helpers + confirm/challenge prompts.
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
 * Pre-wait prompt decision after create (#975 / #941 / #982).
 *
 * Show the challenge code whenever the create response contains a valid
 * value (host requested it, or enrollment requires it), regardless of the
 * host `challenge_requested` flag. Fail closed only when the host requested
 * a challenge and none came back. Otherwise skip the post-create prompt
 * (no-challenge path: pre-create confirm already ran).
 *
 * @param challenge_requested host conf challenge_requested (0/1)
 * @param challenge create response authAttemptChallenge, or -1 if absent
 * @return 1 show challenge code, 0 skip post-create prompt, -1 fail-closed
 */
int ezkey_prewait_prompt_mode(int challenge_requested, long challenge);

/**
 * True when pam_prompt successfully displayed a confirm or challenge prompt.
 * There is no pam_info fallback — OpenSSH buffers bare TEXT_INFO until the
 * next prompt or the end of the round (#941 F1 / #982 ECHO_ON).
 *
 * @param pam_prompt_rc return from pam_prompt(...)
 * @return 1 if displayed, 0 otherwise
 */
int ezkey_challenge_display_ok(int pam_prompt_rc);

/**
 * True when the pre-create conversation response is an explicit confirmation
 * (trimmed case-insensitive "ok"). Empty / EOF / bare Enter is rejected —
 * OpenSSH keyboard-interactive with `ssh -T </dev/null` answers the prompt
 * with an empty string and PAM_SUCCESS, so "Press Enter" alone cannot prove
 * the operator saw the prompt (Isabelle D4 / #956 M2 correction).
 *
 * @param resp pam_prompt response (may be NULL)
 * @return 1 if confirmed, 0 otherwise
 */
int ezkey_prewait_response_confirmed(const char *resp);

/**
 * Format the pre-create confirm prompt (shown via PAM_PROMPT_ECHO_ON
 * **before** POST /auth-attempts — #941 / #975 / #982 tier 0).
 * Batch/empty clients fail here with zero attempts created.
 *
 * @param buf destination buffer
 * @param buflen capacity of buf
 * @param username Linux username (also Ezkey userIdentifier); must be non-empty
 * @return 0 on success, -1 if args invalid or the message does not fit
 */
int ezkey_format_waiting_prompt(char *buf, size_t buflen, const char *username);

/**
 * Format the post-create challenge-code prompt (#975 / #982 tier 1).
 * This prompt is the only wait for the challenge path: after Enter the module
 * polls /wait (no second "Type ok").
 *
 * @param buf destination buffer
 * @param buflen capacity of buf
 * @param challenge value in 0..99 (caller must validate)
 * @return 0 on success, -1 if args invalid or the message does not fit
 */
int ezkey_format_challenge_prompt(char *buf, size_t buflen, long challenge);

#endif /* EZKEY_CHALLENGE_H */
