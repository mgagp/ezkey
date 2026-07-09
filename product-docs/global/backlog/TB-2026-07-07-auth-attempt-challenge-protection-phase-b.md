# Tracer Bullet Brief — TB-2026-07-07-auth-attempt-challenge-protection-phase-b Auth Attempt Challenge Lifecycle Minimization

## Metadata

- **ID:** `TB-2026-07-07-auth-attempt-challenge-protection-phase-b`
- **Status:** `promoted`
- **Related idea:** `I-2026-07-07-auth-attempt-challenge-protection`
- **Lane:** `B`
- **Posture:** `single-pass`
- **GitHub issue:** 
- **GitHub branch:** 
- **GitHub PR:** 
- **Created at:** `2026-07-07`
- **Updated at:** `2026-07-07`
- **Captured by:** `GitHub Copilot`

## Objective

Execute the core lifecycle minimization strategy for `auth_attempt_challenge`. This involves implementing terminal-state clearing logic while preserving challenge requirement metadata via a single persistent enum (`auth_attempt_challenge_requirement_source`).

## Boundaries in scope

- `ezkey-core` Domain Entity (`AuthAttempt`) ↔ Service Layer (`AuthAttemptService`, `AuthAttemptRespondService`, `AuthAttemptExpiryScheduler`)
- Database migration (adding the enum column, nullable definitions).

## Out of scope

- Setting up encryption schemas, encrypted column mappings, or altering `EncryptionEntityListener` logic for the challenge.

## First executable slice

- Add the `Enum` source column.
- Integrate the cleanup step triggering when attempting a transition into `ACCEPTED`, `REJECTED`, `INVALID`, or `EXPIRED`.
- Add test coverage preventing race conditions or guaranteeing atomic execution for the challenge purge during responses.
- Implement configuration parameter (e.g. `ttl_post_terminal`) allowing a delay, or default synchronous wiping.

## Rollback or fallback posture

- Keep previous column plaintext behaviour but suppress the terminal cleanups if locking failures appear. 
- Retain Phase C encryption backup strategy if required downstream before release.

## Critical flows

- nominal path: Attempt verification completes (`ACCEPTED`), the state updates and the challenge is wiped out / scheduled for wiping.
- critical exception path: Race condition between `PENDING`/`READ` and validation. The validation process must ensure the challenge is strictly available until transaction boundaries finalize.
- automated job path: `AuthAttemptExpiryScheduler` correctly sweeps expired attempts to transition to `EXPIRED` state, thereby invoking the challenge cleanup seamlessly.

## Evidence plan

- unit test verifying the `auth_attempt_challenge` clears once state becomes terminal.
- unit test protecting against zombie states in scheduler.
- documentation specifying the behaviour for operations and audit.

## Quality gates

- implementation gate: Ensure concurrent execution does not prematurely lock or wipe out data. Rate limiting controls remain unimpacted.

## Exit criteria

The challenge field is reliably flushed upon final transition and the database retains the reason contextual enum successfully with all tests running green.
