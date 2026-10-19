/*
 * ezkey_budget.h — wait/TTL clamp and planned-wait math (testable).
 *
 * Shared by pam_ezkey.c and the budget harness so Patrick #897 CHANGES can
 * prove AUTH_BUDGET 105 and “rem too small for legal TTL → −1” without grepping
 * headers alone.
 */

#ifndef EZKEY_BUDGET_H
#define EZKEY_BUDGET_H

/**
 * Clamp conf / pam.d wait_timeout into the Integration API ttlSeconds window
 * (EZKEY_MIN_TTL_SECONDS .. EZKEY_MAX_WAIT_TIMEOUT).
 *
 * @param value candidate wait_timeout seconds
 * @return clamped value
 */
int ezkey_clamp_wait_timeout(int value);

/**
 * Planned /wait (and create ttlSeconds) from remaining monotonic budget.
 * Formula: min(wait_timeout, rem − CURL_WAIT_SLACK), still
 * ≥ EZKEY_MIN_TTL_SECONDS. Returns −1 when rem cannot cover a server-legal TTL
 * (PAM must UNAVAIL before create).
 *
 * @param wait_timeout conf wait already clamped
 * @param rem_secs remaining EZKEY_AUTH_BUDGET_SECS
 * @return planned seconds, or −1 if too little budget remains
 */
int ezkey_planned_wait_from_rem(int wait_timeout, long rem_secs);

#endif /* EZKEY_BUDGET_H */
