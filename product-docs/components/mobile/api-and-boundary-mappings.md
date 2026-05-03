# Mobile — API and Boundary Mappings

## Intent

This document describes how the mobile app maps between the Auth API contract and its local models and UI state. The canonical internal reference is [`../../../ezkey_mobile/docs/MOBILE_API_MAPPINGS.md`](../../../ezkey_mobile/docs/MOBILE_API_MAPPINGS.md); this document summarizes two representative mappings inside the product-wide system.

## Contract Artifacts Policy

- The local `openapi-spec.json` is a **versioned copy** of the Auth API spec. Refresh it only via `scripts/update-specs.sh` or `scripts/update-specs.bat` after a clean Docker stack.
- Generated DTOs under `app/services/api/generated/auth-api/model/` are the **source of truth** for Auth API contract types. Never hand-edit.
- `app/services/api/types.ts` stays thin — local domain types and UI-friendly wrapper shapes only.

## Mapping Index

| ID | Scope | Direction | Status |
|----|-------|-----------|--------|
| `M-mob-enrollment` | Auth API bind/verify ↔ local enrollment model | bidirectional | `implemented` |
| `M-mob-pending-respond` | Auth API pending/respond ↔ auth attempt UI | bidirectional | `implemented` |

---

## `M-mob-enrollment` — Bind and Verify

### Intent

Translate the Auth API bind/verify DTOs into the mobile enrollment state and UI, and translate user inputs and device signatures back into request bodies.

### Boundary

- **Left side.** Enrollment wizard screens, secure storage, native crypto module.
- **Right side.** Auth API `/api/v1/enrollments/bind`, `/api/v1/enrollments/verify`.
- **Direction.** Bidirectional.

### Reference Artifacts

- Auth API endpoints: [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md) (Auth API — Enrollment).
- Canonical signed payloads: [`../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md).
- Local spec copy: [`../../../ezkey_mobile/openapi-spec.json`](../../../ezkey_mobile/openapi-spec.json).

### Field Mapping — Bind

| UI input | Request field | Transformation | Notes |
|----------|---------------|----------------|-------|
| QR payload `enrollmentId` | `enrollmentId` | parse as long | — |
| QR payload `enrollmentProofToken` | `enrollmentProofToken` | identity | Held in memory during bind, then persisted through secure storage after verify. |

| Response field | Local model | Transformation | Notes |
|----------------|-------------|----------------|-------|
| `integrationPublicKey` | verified via native crypto | Base64URL decode | Fail-closed on decode error. |
| `integrationKeyAlgorithm` | required contract field | must equal `ed25519` | Fail-closed otherwise. |
| `enrollmentBindPayloadSignedByIntegration` | verified on device | Ed25519 verify | Proceed only when signature is valid. |
| `integrationName`, `integrationDescription`, `enrollmentName` | UI copy | identity | Displayed during the wizard. |

### Field Mapping — Verify

| UI input / device material | Request field | Transformation | Notes |
|----------------------------|---------------|----------------|-------|
| `enrollmentId` | `enrollmentId` | identity | — |
| `challengeResponse` (user input) | `challengeResponse` | integer | — |
| Device public key (from keystore) | `devicePublicKey` | Base64 (standard) of SPKI X.509 | — |
| Device signature over canonical payload | `enrollmentProofTokenSigned` | Base64 (standard) | Canonical payload per linked doc. |
| Reported storage tier | `devicePrivateKeyStorageTier` | enum (`NONE`, `STANDARD`, `STRONG`) | Client-reported only. |

### Decision Table — Algorithm and Signature Guards

| Condition | Decision |
|-----------|----------|
| `integrationKeyAlgorithm != "ed25519"` | Abort enrollment (fail-closed). |
| `enrollmentBindPayloadSignedByIntegration` fails Ed25519 verify | Abort enrollment. |
| Bind payload missing canonical fields | Abort enrollment. |

### Constraints and Invariants

- Private key material never leaves the native side.
- One-time flow proof tokens are held in memory only.
- The long-lived `enrollmentProofToken` is persisted through secure storage, distinct from the keystore-backed private signing key.
- Signatures are produced via the native module; the JS side holds only the resulting Base64 string.

### Error Mapping

Errors arrive as RFC 9457 Problem Details under types such as `authentication.*` and `enrollment.*`. Bring-up notes live in [`exception-and-error-model.md`](exception-and-error-model.md).

### Lifecycle Coupling

- Spec version is the `openapi-spec.json` currently vendored in `ezkey_mobile/`.
- Compatibility policy: breaking changes in Auth API require a coordinated spec refresh and client regeneration.

### Related Documents

- [`functional-flows.md#w-mob-enrollment-wizard`](functional-flows.md#w-mob-enrollment-wizard).
- Canonical mapping doc: [`../../../ezkey_mobile/docs/MOBILE_API_MAPPINGS.md`](../../../ezkey_mobile/docs/MOBILE_API_MAPPINGS.md).

---

## `M-mob-pending-respond` — Pending and Respond

### Intent

Translate pending and respond DTOs between the Auth API and the mobile UI, producing and verifying the correct cryptographic material at each step.

### Boundary

- **Left side.** Pending Auth screen, Respond Result screen, native crypto module.
- **Right side.** Auth API `/api/v1/auth-attempts/pending`, `/api/v1/auth-attempts/respond`.
- **Direction.** Bidirectional.

### Reference Artifacts

- Endpoints: [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md).
- Canonical signed payloads: [`../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md).

### Field Mapping — Pending Request

| Source | Request field | Notes |
|--------|---------------|-------|
| Local enrollment | `enrollmentId`, `enrollmentProofToken` | `enrollmentProofToken` is rehydrated from secure storage into the runtime record before the call. |
| Generated via `generateProofToken` | `deviceProofToken` | One-time material. |
| Signed by device (native module) | `deviceProofTokenSigned` | ECDSA-SHA256 over canonical payload. |

### Field Mapping — Pending Response

| Response field | UI state | Notes |
|----------------|----------|-------|
| `authAttemptId`, `authAttemptProofToken` | pending state | Verified via integration signature before displaying. |
| `authAttemptProofTokenSignedByIntegration` | verification input | If verification fails, UI refuses to display context. |
| `authAttemptChallengeRequired` | UI toggle | Shows challenge input when `true`. |
| `contextTitle`, `contextMessage` | card header and body | Nullable. |
| HTTP 204 | "no pending" UI | Same signed `deviceProofToken` may be reused until a pending claim happens. |

### Field Mapping — Respond Request

| Source | Request field | Notes |
|--------|---------------|-------|
| Pending attempt id | `authAttemptId` | — |
| User decision | `authAttemptAccepted` | boolean. |
| Device signature | `authAttemptProofTokenSignedByDevice` | Signs canonical `authAttemptProofToken|accepted`. |
| User input | `authAttemptChallengeResponse` | Only when challenge required. |

### Field Mapping — Respond Response

| Response field | UI state | Notes |
|----------------|----------|-------|
| `authAttemptResult` | outcome display | `APPROVED`, `REJECTED`, `FAILED`. |
| `authAttemptMessage` | human hint | Not used for branching. |
| `authAttemptProofTokenResultSignedByIntegration` | verification input | Fail-closed on signature mismatch. |

### Decision Table — Outcome Display

| Backend reports | Signature verified | UI behavior |
|-----------------|--------------------|-------------|
| `APPROVED` | yes | Show approved state. |
| `APPROVED` | no | Show "outcome not verifiable" fail-closed state. |
| `REJECTED` | yes | Show rejected state. |
| `FAILED` | yes | Show structured failure explanation. |

### Constraints and Invariants

- Respond has no retry on first failed validation; the user must start a new flow upstream.
- The UI never claims success without a verified integration signature.
- Rate-limit responses are explicitly surfaced to the user.

### Error Mapping

- RFC 9457 Problem Details for 4xx/5xx.
- Business-level `FAILED` inside a 200 body is not an error at the HTTP level; the UI treats it as a structured failure and does not retry.

### Related Documents

- [`functional-flows.md#w-mob-pending-check`](functional-flows.md#w-mob-pending-check).
- [`functional-flows.md#w-mob-respond`](functional-flows.md#w-mob-respond).
- Canonical mapping doc: [`../../../ezkey_mobile/docs/MOBILE_API_MAPPINGS.md`](../../../ezkey_mobile/docs/MOBILE_API_MAPPINGS.md).
