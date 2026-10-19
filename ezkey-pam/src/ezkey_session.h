/*
 * ezkey_session.h — PAM-handle session guards (final verdict / interrupt cancel).
 *
 * Pure helpers shared by pam_ezkey.c and the session harness (#907 F9):
 * in-process final-verdict marker (fail-closed for same PAM handle only —
 * under OpenSSH portable each keyboard-interactive round is a forked child
 * that _exit's, so the marker does not span rounds); interrupt cancel uses a
 * short bound so cancel is not deferred until the full wait limit.
 */

#ifndef EZKEY_SESSION_H
#define EZKEY_SESSION_H

/**
 * True when result is a terminal authentication verdict for this PAM handle
 * (SUCCESS, DENIED, EXPIRED, TIMEOUT). UNAVAIL / ERROR stay retryable.
 *
 * @param result summary result string (may be NULL)
 * @return 1 if a later pam_sm_authenticate in the same handle must not create
 * @return 0 otherwise
 */
int ezkey_result_is_final(const char *result);

/**
 * Bounded cancel HTTP timeout (seconds). Normal path respects remaining auth
 * budget (0 → skip). Interrupt path (conversation failed / client disconnect
 * during wait) uses min(api_timeout, EZKEY_INTERRUPT_CANCEL_MAX) even when
 * rem_secs is 0 (#907 R5) so worst-case stays under LoginGraceTime.
 *
 * @param api_timeout conf api_timeout (clamped to EZKEY_MAX_API_TIMEOUT)
 * @param rem_secs remaining monotonic auth budget
 * @param interrupted non-zero when cancel must not wait on rem
 * @return seconds for CURLOPT_TIMEOUT, or 0 when cancel should be skipped
 */
long ezkey_bounded_cancel_timeout(long api_timeout, long rem_secs,
                                  int interrupted);

/**
 * Whether an in-flight attempt should be cancelled on this exit path.
 *
 * @param have_attempt non-zero after successful create (authAttemptId > 0)
 * @param accepted non-zero when wait status was ACCEPTED
 * @return 1 when best-effort cancel should run
 * @return 0 when cancel must be skipped
 */
int ezkey_should_cancel_attempt(int have_attempt, int accepted);

#endif /* EZKEY_SESSION_H */
