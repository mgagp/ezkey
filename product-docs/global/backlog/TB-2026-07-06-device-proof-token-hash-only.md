# Tracer Bullet Brief — `TB-2026-07-06` Device proof token hash-only storage

## Metadata

- **ID:** `TB-2026-07-06-device-proof-token-hash-only`
- **Status:** `ready` → `active` (implementation branch `feature/296-i-2026-0032-device-proof-token-hash-only`)
- **Related idea:** `I-2026-0032-proof-token-hash-only-storage`
- **Parent context:** Tier 0 of proof-token hash-only program; ADR-0007
- **Lane:** `D`
- **Posture:** `single-pass`
- **GitHub issue:** `#296`
- **Issue labels:** `lane:d`, `type:security`, `component:core`, `component:auth-api`, `component:migration`, `priority:p1`, `status:ready`
- **GitHub branch:** `feature/296-i-2026-0032-device-proof-token-hash-only`
- **Created at:** `2026-07-06`
- **Updated at:** `2026-07-06`
- **Captured by:** Marc (plan incubation handoff)

## Objective

Persist **only** `device_proof_token_hash` on claimed auth attempts — remove the encrypted
`device_proof_token` column and all decrypt/re-encryption paths — without changing the Auth API
contract, mobile behavior, or Demo Device flows.

## Evidence summary (why this slice is safe)

Code audit (2026-07-06) confirms:

| Check | Result |
| --- | --- |
| Runtime lookup / replay | `existsByDeviceProofTokenHash` only ([`AuthAttemptPendingService`](../../../../ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptPendingService.java)) |
| Service reads `getDeviceProofToken()` from DB | **None** outside entity |
| Write path | `setDeviceProofToken` on claim → sets hash via entity setter; switch to `setDeviceProofTokenHash` only |
| Protocol / mobile | Device generates token per poll; server never re-emits stored device token |

Reference: SEC-007 in [`docs/SECURITY_CHALLENGE_REPORT_2026-06.md`](../../../../docs/SECURITY_CHALLENGE_REPORT_2026-06.md).

## Boundaries in scope

- `ezkey-core`: [`AuthAttempt`](../../../../ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java) entity, [`AuthAttemptPendingService.claimPendingAttempt`](../../../../ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptPendingService.java)
- Flyway: drop `device_proof_token` on `ezkey_auth_attempt` (greenfield migration)
- Re-encryption: remove `device_proof_token` from [`ReencryptionTargetQueryService`](../../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java) and related tests
- [`I-2026-0029`](ideas/I-2026-0029-reencryption-indexed-encryption-key-id-columns.md): exclude `device_proof_token` from indexed-key-id scope when implementing that slice

## Out of scope

- `enrollment_proof_token`, `auth_attempt_proof_token` columns (Tier 1 / Tier 2 — see ADR-0007)
- OpenAPI / Postman changes (no Auth API DTO change)
- Admin UI, mobile, Demo Device code changes
- Crypto API decrypt endpoint behavior for auth attempts (document as N/A for this column)

## First executable slice

1. **Entity:** Remove `encryptedDeviceProofToken`, transient `deviceProofToken`, encryption listener
   mapping, and `getDeviceProofToken()` / `setDeviceProofToken(String)`; add or keep
   `setDeviceProofTokenHash` as the claim write path.
2. **Service:** In `claimPendingAttempt`, set hash from request (reuse hash computed in
   `validateDeviceSignature`).
3. **Migration:** New Flyway script — `ALTER TABLE ezkey_auth_attempt DROP COLUMN device_proof_token`;
   retain `device_proof_token_hash` and partitioned unique index (V4 pattern).
4. **Re-encryption:** Remove target column from query service, batch creation samples, and tests.
5. **Tests:** Update unit tests ([`AuthAttemptServiceDeviceProofTokenTest`](../../../../ezkey-core/src/test/java/org/ezkey/authattempt/service/AuthAttemptServiceDeviceProofTokenTest.java)), re-encryption integration tests, any functional SQL that INSERTs `device_proof_token`.
6. **Docs:** Note hash-only posture in ADR-0007 validation section; optional one-line in
   [`docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md`](../../../../docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md).

## Rollback or fallback posture

- Single migration + entity revert if functional pending/respond regression appears.
- No client rollback required (no contract change).

## Critical flows

- **Nominal:** Device polls pending with fresh signed `deviceProofToken` → claim stores hash → respond
  succeeds; replay of same device token on a later claim fails at hash uniqueness check.
- **Exception:** Empty poll (204) — no hash stored; same signed pair may be reused until a claim occurs
  (existing documented behavior).

## Evidence plan

| Layer | Required |
| --- | --- |
| Unit | `ezkey-core` auth-attempt pending/device-proof tests |
| Integration | Re-encryption tests no longer reference `device_proof_token` |
| Functional | Pending claim + respond path on clean-start stack (`ezkey-tests`) |
| Build | Root Maven baseline per `AGENTS.md` |
| Manual | Clean-start → auth attempt → Demo Device approve; confirm row has hash, no ciphertext column |

See [`test-plans/TSP-2026-07-06-device-proof-token-hash-only.md`](test-plans/TSP-2026-07-06-device-proof-token-hash-only.md).

## Quality gates

- **Analysis gate:** ADR-0007 accepted; Tier 0 evidence recorded in this TB.
- **Design gate:** No Auth API contract drift.
- **Implementation gate:** Maven baseline + targeted tests green.
- **Closeout gate:** `I-2026-0032` Tier 0 note; update `I-2026-0029` re-encryption column list; optional GitHub issue.

## Exit criteria

1. `device_proof_token` column absent from schema; `device_proof_token_hash` populated on claim.
2. No production code path decrypts or re-encrypts device proof token.
3. Pending → respond E2E passes on clean-start with Demo Device.
4. ADR-0007 validation section updated with execution evidence.

## Links

- Idea: [`ideas/I-2026-0032-proof-token-hash-only-storage.md`](ideas/I-2026-0032-proof-token-hash-only-storage.md)
- ADR: [`../../architecture-decisions.md#adr-0007-proof-token-storage-hash-only-where-protocol-allows`](../../architecture-decisions.md#adr-0007-proof-token-storage-hash-only-where-protocol-allows)
- Payload spec (unchanged): [`../../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)

## Incubation sources

- Working plan (repo): [`.cursor/plans/proof_token_hash-only_storage.plan.md`](../../../.cursor/plans/proof_token_hash-only_storage.plan.md)
- Lane: `B` — plan incubation, materialized `2026-07-06`
