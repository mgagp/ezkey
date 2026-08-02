# Backlog Idea — `I-2026-0032` Proof token hash-only storage (tiered hardening)

## Metadata

- **ID:** `I-2026-0032`
- **Status:** `incubating` (Tier 0 done via `TB-2026-07-06`; Tier 1 / Tier 2 remain)
- **Priority:** `P1`
- **Created at:** `2026-07-06`
- **Updated at:** `2026-08-02`
- **Last reviewed at:** `2026-08-02`
- **Progression markers:** `P2-hardening`
- **Component tags:** `core`, `auth-api`, `admin-api`, `docs`, `crypto`
- **Lane:** `D`
- **Captured by:** Marc (plan incubation handoff)
- **GitHub issue:** `#296` (closed with Tier 0; reopen or new issue for Tier 1+)
- **Issue labels:** `lane:d`, `type:security`, `component:core`, `component:auth-api`, `component:migration`, `priority:p1`, `status:ready`
- **GitHub branch:** `feature/296-i-2026-0032-device-proof-token-hash-only`

## Intent

Reduce breach impact and operational complexity for proof-token persistence by moving toward
**hash-only storage** where the protocol allows it — starting with `device_proof_token` on
`ezkey_auth_attempt`, while documenting why `enrollment_proof_token` and
`auth_attempt_proof_token` still require recoverable storage under the current pull MFA model.

## Problem and value

- **Problem:** Proof tokens today use **encrypted ciphertext + SHA-256 hash**. Lookups and replay
  checks already use hashes; encrypted columns add Tink dependency, re-encryption batch surface, and
  risk if encryption is misconfigured (as surfaced in the 2026 security protocol audit). The
  admin bearer token already follows hash-only (`bearer_token_hash`); the question is how far that
  pattern extends to proof tokens.
- **Expected value:** For fields where the server never re-reads plaintext, drop recoverable storage
  entirely (Tier 0). For other tokens, record explicit ADR-backed rationale instead of implicit
  dual-column habit. Pre-production timing makes schema simplification low-cost.

## Scope

- **In scope (program):**
  - Tier 0: `device_proof_token` → `device_proof_token_hash` only (`TB-2026-07-06`).
  - ADR-0007 per-token verdict and session-token analogy limits.
  - Cross-links to security audit (SEC-007) and encryption docs.
- **Out of scope (this idea, deferred slices):**
  - Tier 1: `enrollment_proof_token` hash-only + show-once Admin UX + verify request field.
  - Tier 2: `auth_attempt_proof_token` protocol v2 or deterministic derivation.
  - Mobile / Admin UI contract changes (not required for Tier 0).

## Key assumptions

- No production database exists; Flyway can drop `device_proof_token` without backfill complexity.
- `AuthAttemptPendingService` remains the sole writer of device proof token hash on claim.
- Encryption at rest stays enabled for remaining proof-token columns and integration private keys.

## Risks and exceptions

- **I-2026-0029** (indexed encryption key id columns) lists `device_proof_token` as a re-encryption
  target — Tier 0 completion should remove that row from scope.
- Functional tests that INSERT raw `device_proof_token` ciphertext must be updated to hash-only.
- SECURITY_CHALLENGE_REPORT F-02-A incorrectly states enrollment proof token is hash-only; correct
  in ADR-0007 closeout.

## Promotion notes

- **Tier 0:** promoted 2026-07-06 → `TB-2026-07-06-device-proof-token-hash-only`; **done** 2026-07-07
  (PR `#297` / issue `#296`). Canon status synced 2026-08-02.
- **Tier 1:** promote to new `TB-*` only after show-once enrollment policy and verify-request
  protocol change are accepted.
- **Tier 2:** remains design-only until pull-model delivery constraints are resolved.
- **Parent status:** stay `incubating` until Tier 1 / Tier 2 are either delivered, parked, or
  explicitly dropped; do not mark this idea `done` on Tier 0 alone.

## Links

- Tracer bullet (Tier 0): [`../TB-2026-07-06-device-proof-token-hash-only.md`](../TB-2026-07-06-device-proof-token-hash-only.md)
- Test plan: [`../test-plans/TSP-2026-07-06-device-proof-token-hash-only.md`](../test-plans/TSP-2026-07-06-device-proof-token-hash-only.md)
- ADR: [`../../architecture-decisions.md#adr-0007-proof-token-storage-hash-only-where-protocol-allows`](../../architecture-decisions.md#adr-0007-proof-token-storage-hash-only-where-protocol-allows)
- Related idea (re-encryption indexing): [`I-2026-0029-reencryption-indexed-encryption-key-id-columns.md`](I-2026-0029-reencryption-indexed-encryption-key-id-columns.md)
- Security audit: [`../../../../docs/SECURITY_CHALLENGE_REPORT_2026-06.md`](../../../../docs/SECURITY_CHALLENGE_REPORT_2026-06.md) (SEC-007)
- Bearer token precedent: [`.cursor/plans/bearer_token_hash_storage_analysis.plan.md`](../../../../.cursor/plans/bearer_token_hash_storage_analysis.plan.md)

## Incubation sources

- Working plan (Cursor): `.cursor/plans/proof_token_hash-only_storage.plan.md`
- Working plan (session): `proof_token_hash-only_45368295.plan.md` (Cursor plans store)
- Lane: `B` — plan incubation, materialized `2026-07-06`
