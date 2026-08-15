# Backlog Idea — I-2026-07-07-auth-attempt-challenge-protection Auth Attempt Challenge Protection Strategy

## Metadata

- **ID:** `I-2026-07-07-auth-attempt-challenge-protection`
- **Status:** `ready`
- **Priority:** `P1`
- **Created at:** `2026-07-07`
- **Updated at:** `2026-07-07`
- **Last reviewed at:** `2026-07-07`
- **Progression markers:** `P1-alpha-stability`, `P2-security-readiness`
- **Component tags:** `ezkey-core`, `ezkey-auth-api`
- **Lane:** `B`
- **Captured by:** `GitHub Copilot`
- **GitHub issue:** 

## Intent

Implement a pragmatic protection strategy for the `auth_attempt_challenge` field by prioritizing data lifecycle minimization (post-terminal nullification) instead of complex at-rest cryptography.

## Problem and value

- **Problem:** Storing the `auth_attempt_challenge` in plaintext poses a theoretical risk, although the protocol structure naturally prevents brute-force. Encrypting a 2-digit low-entropy transient field produces "accidental complexity" while failing to add proportional security.
- **Expected value:** Keep the system simple and pragmatically secure by discarding the transient data when it's no longer needed for protocol validation, avoiding schema complexifications or re-encryption payloads, while preserving necessary historical metadata.

## Scope

- **In scope:**
  - Establishing a terminal-state cleanup policy (`auth_attempt_challenge` set to null on `ACCEPTED`, `REJECTED`, `INVALID`, `EXPIRED`).
  - Introducing `auth_attempt_challenge_requirement_source` (Enum) to serve as the unified historical source of truth indicating *why* a challenge was required (`NONE`, `POLICY`, `AD_HOC`, `BOTH`).
  - Ensuring the `AuthAttemptExpiryScheduler` correctly sweeps expired attempts to trigger this cleanup.
  - Adding a parameter (e.g. `ttl_post_terminal`) for delayed cleanup if immediate debug context is prioritized.
- **Out of scope:**
  - Database schema encryption (Phase C) for `auth_attempt_challenge` (deferred to future compliance pressure rather than baseline implementation).
  - Custom cryptographic matching/hashing for this explicit low-entropy field.

## Key assumptions

- The primary defense against guessing attacks is the protocol itself: a unique proof token signature is consumed on verification (1-attempt guarantee) along with standard rate limiting.
- The `Enum` effectively replaces the need for the plaintext challenge value for audit trails or operational debugging logic post-termination.
- Raising the challenge from 2 digits to 3–4 digits is optional UX, not required for Phase B (protocol already consumes the proof token on one verification).

## Risks and exceptions

- **Race Conditions:** Immediate cleanup of the challenge during an asynchronous process could conflict with reading clients checking the challenge value if not executed atomically within the state transition transaction.
- **Zombie states:** If an attempt fails to transition to `EXPIRED` effectively through the scheduler, its challenge value might permanently rest in plaintext.
- **Debug context loss:** Nullifying the challenge directly might complicate fast-paced operations debugging. A delayed asynchronous sweeping behavior parameter answers this.

## Promotion notes

Ready for implementation. See `TB-2026-07-07-auth-attempt-challenge-protection-phase-b` for Phase B execution slice.

## Links

- Related TB: [`TB-2026-07-07-auth-attempt-challenge-protection-phase-b`](../TB-2026-07-07-auth-attempt-challenge-protection-phase-b.md) (Phase B execution). Phase C encryption remains out of scope until compliance pressure.
