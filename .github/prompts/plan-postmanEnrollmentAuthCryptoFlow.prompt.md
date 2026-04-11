# Plan: Postman enrollment auth collection — cryptographic chaining (canonical payloads)

**TL;DR** — The [EZ Key Enrollments auth](postman/collections/v2.1/EZ%20Key%20Enrollments%20auth.postman_collection.json) collection is broken after enrollment hardening: it signs only the raw `enrollmentProofToken`, but the Auth API expects **ECDSA-SHA256 over the canonical verify-device payload** `enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey` (see [docs/ENROLLMENT_SIGNATURE_PAYLOAD.md](../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)). Mirror [EZ Key Auth Attempts auth](postman/collections/v2.1/EZ%20Key%20Auth%20Attempts%20auth.postman_collection.json): extend Crypto API `payload-helper` with enrollment types delegating to `EnrollmentSignaturePayload`, add Ed25519 verification steps after bind and after verify, renumber requests 1–9, update scripts/env vars. Legacy `docs/testing/POSTMAN_COLLECTIONS_UPDATE_PLAN.md` was removed as superseded.

**Related Cursor plan:** `.cursor/plans/postman_enrollment_crypto_flow_b1fcbbbb.plan.md` (if present on the workstation).

---

## Problem statement

- **Verify request:** `enrollmentProofTokenSigned` must cover the **canonical string** (UTF-8), not the proof token alone. See [docs/MOBILE_DEVELOPER_GUIDE.md](../docs/MOBILE_DEVELOPER_GUIDE.md) (verify section).
- **Bind response:** clients must verify `enrollmentBindPayloadSignedByIntegration` (Ed25519) before trusting bind metadata.
- **Verify response:** clients must verify `enrollmentVerifyPayloadSignedByIntegration` on the verify-result payload before treating enrollment as complete.

---

## Gap in Crypto API

[`CryptoController.buildPayload`](ezkey-crypto-api/src/main/java/org/ezkey/crypto/controller/CryptoController.java) only supports `pending`, `respond`, `respond-result`. Add types such as `enrollment-bind`, `enrollment-verify-device`, `enrollment-verify-result` that call [`EnrollmentSignaturePayload`](ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentSignaturePayload.java) (`ezkey-crypto-api` already depends on `ezkey-core`).

---

## Execution checklist

### 1. Extend Crypto API `payload-helper`

- Extend [`PayloadHelperRequestDto`](ezkey-crypto-api/src/main/java/org/ezkey/crypto/dto/PayloadHelperRequestDto.java) with optional fields for enrollment builders; keep backward compatibility for existing payload types.
- Update `CryptoController.buildPayload` switch; add unit tests in [`CryptoControllerTest`](ezkey-crypto-api/src/test/java/org/ezkey/crypto/controller/CryptoControllerTest.java).

### 2. Revise Postman `EZ Key Enrollments auth`

| Step | Request (name pattern) | Purpose |
|------|------------------------|---------|
| 1 | bind | Auth API; test script saves bind fields + `enrollmentBindPayloadSignedByIntegration` |
| 2 | crypto build enrollment bind payload | `POST /payload-helper` `type: enrollment-bind` → `enrollmentBindPayload` |
| 3 | crypto validate enrollment bind by integration | `POST /verify-ed25519` |
| 4 | crypto generate device keypair | `GET /keypair` |
| 5 | crypto build enrollment verify device payload | `payload-helper` `enrollment-verify-device` → `enrollmentVerifyDevicePayload` |
| 6 | crypto sign enrollment verify device payload | `POST /sign` on canonical string from step 5 |
| 7 | verify | Auth API `POST /enrollments/verify` |
| 8 | crypto build enrollment verify result payload | `payload-helper` `enrollment-verify-result` |
| 9 | crypto validate enrollment verify result by integration | `POST /verify-ed25519` |

- Prerequest guards when a prior step’s env var is required (same idea as Auth Attempts “run 3b first”).
- Replace misleading env names (e.g. `enrollment_proofkey_signature`).
- Update collection `info.description`; document prerequisite: `enrollmentChallenge` and credentials from Admin enrollment creation.

### 3. Validation

- `mvn test -pl ezkey-crypto-api` after Java changes.
- Manual Postman run against clean-start stack (steps 1–9).

### 4. Repository cleanup

- Done: removed legacy `docs/testing/POSTMAN_COLLECTIONS_UPDATE_PLAN.md` (superseded).

---

## Out of scope

- Hand-editing generated OpenAPI under `specs/` (maintainer runs `scripts/update-specs.sh` / `.bat` after services reflect changes).

---

## Todos (tracking)

1. `crypto-payload-helper-enrollment` — DTO + controller + tests
2. `postman-enrollments-auth` — collection JSON rewrite
3. `validate-crypto-tests` — module tests
4. `remove-legacy-postman-doc` — completed (legacy doc removed; references updated)
