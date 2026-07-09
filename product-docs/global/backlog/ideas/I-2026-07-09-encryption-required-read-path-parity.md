# Backlog Idea — `I-2026-07-09` `encryption.required` read-path parity (at-rest fields)

## Metadata

- **ID:** `I-2026-07-09-encryption-required-read-path-parity`
- **Status:** `ready`
- **Priority:** `P2`
- **Created at:** `2026-07-09`
- **Updated at:** `2026-07-09`
- **Last reviewed at:** `2026-07-09`
- **Progression markers:** `P2-hardening`, `security-challenge-follow-up`
- **Component tags:** `core`, `core-security`, `admin-api`, `auth-api`, `docs`
- **Lane:** `D` (security / hardening)
- **Captured by:** Marc (post SEC-010 hygiene)
- **Security audit trace:** SEC-002 (startup), SEC-010 (ApiKey read-path), Suivi in
  [`docs/SECURITY_CHALLENGE_REPORT_2026-06.md`](../../../../docs/SECURITY_CHALLENGE_REPORT_2026-06.md)

## Intent

When `ezkey.encryption.required=true`, **every** sensitive at-rest field must use the same
**fail-closed read policy** already introduced for API key `secretKeyHash` (SEC-010). Today SEC-002
only guarantees Tink availability at **startup**; several entity getters still **soft-fallback** to
plaintext on read when ciphertext is missing or decryption fails.

## Problem and value

| Layer | Current behaviour | Gap |
|-------|-------------------|-----|
| **Startup (SEC-002)** | Fail-fast if Tink cannot initialize when `required=true` | Covered |
| **Write (`EncryptionEntityListener`)** | Fail-closed on persist when `required=true` | Covered for all encrypted fields |
| **Read — `ApiKey.secretKeyHash` (SEC-010)** | `AtRestEncryptionAccess.resolveEncryptedField()` → `IllegalStateException` if not `ENC:` | Reference pattern |
| **Read — `Enrollment` / `AuthAttempt` getters** | Warn + return persisted value as-is on decrypt failure or plaintext column | **Inconsistent** with `required=true` |

**Expected value:** Production posture with `encryption.required=true` cannot silently use
plaintext BCrypt hashes, proof tokens, or integration private keys. Operators get predictable
failure (auth/validation errors or controlled exceptions) instead of degraded reads.

## Scope

### In scope (recommended slice)

1. **Route entity getters through `AtRestEncryptionAccess`** (same helper as `ApiKey`):
   - `Enrollment.getIntegrationPrivateKey()`
   - `Enrollment.getEnrollmentProofToken()`
   - `AuthAttempt.getAuthAttemptProofToken()`
2. **Audit service call sites** that consume those getters — decide per path:
   - **Fail closed** (return empty / reject operation), mirroring `ApiKeyService.validateApiKey()`, or
   - **Propagate** `IllegalStateException` to a controlled API error where appropriate.
3. **Unit tests** per entity: when `isEncryptionRequired()` is true and persisted value is not
   `ENC:…`, getter or caller rejects (no silent plaintext use).
4. **Docs:** extend `ezkey-core/CONFIGURATION.md` — `encryption.required` applies to **read** and
   **write**, not startup only; link from security report Suivi row.

### Out of scope

- One-shot Flyway **data** migration (clean-start baseline is sufficient; lazy re-encrypt on update
  remains acceptable for dev).
- Changing ciphertext format or Tink key rotation (see `I-2026-0029`).
- Hash-only proof-token tiers (`I-2026-0032`) — orthogonal; this idea only aligns **remaining**
  encrypted-column reads.
- New encrypted fields — they should use `AtRestEncryptionAccess` from day one (pattern in SEC-010).

## Action checklist (implementation order)

1. Refactor the three enrollment/auth-attempt getters to call
   `AtRestEncryptionAccess.resolveEncryptedField()` (remove inline warn-and-fallback branches).
2. Grep for `getIntegrationPrivateKey`, `getEnrollmentProofToken`, `getAuthAttemptProofToken` —
   ensure no caller assumes plaintext fallback when `required=true`.
3. Add/adjust tests mirroring `ApiKeySecretKeyHashEncryptionTest` and `AtRestEncryptionAccessTest`.
4. Run full reactor + `ezkey-tests` security suite with Docker `encryption.required=true` (profile or
   env override) once to validate end-to-end auth/enrollment paths.
5. Update security report Suivi → **Fait** when merged; optional GitHub issue for visibility.

## Promotion notes

- **Hygiene vs program:** Small enough for a single PR (~3 entity getters + tests + CONFIGURATION).
  Promote to `TB-*` only if call-site audit reveals cross-module contract changes.
- **No GitHub issue required** for canon; optional issue if the team wants PR-board tracking.

## Links

- Helper: `ezkey-core/.../AtRestEncryptionAccess.java`
- Reference entity: `ezkey-core/.../ApiKey.java` (`getSecretKeyHash`)
- Listener: `ezkey-core/.../EncryptionEntityListener.java`
- Config index: `ezkey-core/CONFIGURATION.md` (`ezkey.encryption.required`)
- Security report: `docs/SECURITY_CHALLENGE_REPORT_2026-06.md` (SEC-002, SEC-010, Suivi)
