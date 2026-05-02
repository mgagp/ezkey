# Mobile — Exception and Error Model

## Intent

This document defines how the mobile app handles errors coming from the Auth API, cryptographic failures, and local operational problems. The canonical internal reference is [`../../../ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`](../../../ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md) (exception sections) and [`../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`](../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md).

The mobile app follows a **fail-closed** posture: when a check fails, the UI refuses to report success and surfaces a non-deceptive state to the user.

## Error Categories

| Category | Description | Typical response |
|----------|-------------|------------------|
| **Cryptographic** | Signature verification, algorithm mismatch, keystore failure. | Fail-closed; abort the flow. |
| **Protocol** | Auth API returns a modeled error. | Surface a translated user-facing message. |
| **Validation** | Local input fails basic checks (challenge, enrollment id). | Prompt the user to correct. |
| **Rate-limited** | HTTP 429 from Auth API. | Polite message with `Retry-After`. |
| **Degraded** | HTTP 503 with heartbeat-degraded problem type. | Banner with retry guidance. |
| **Network** | Connectivity or timeout. | Retryable state; user-initiated retry only. |
| **Internal** | Unexpected client-side exception. | Log non-secret identifiers and surface a generic state. |

## Error Contract

- **External contract.** Auth API errors follow RFC 9457 Problem Details. The app branches on `type` and HTTP status.
- **Internal contract.** Typed error classes at the API services layer (`app/services/api/`), plus a Problem Details parser.

## Decision Matrix

| Scenario | HTTP | Problem `type` | UI response |
|----------|------|----------------|-------------|
| Rate-limit on pending or respond | 429 | generic | Show polite message with `Retry-After`. |
| Heartbeat-degraded | 503 | `system/audit-chain-heartbeat-degraded` | Banner "Service temporarily unavailable, retry in …". |
| Auth attempt already terminal | 409 | `auth-attempt/conflict` | Refuse to display attempt; advise restarting from the integrating app. |
| Wrong challenge response | 400 | `authentication/invalid-credentials` | Prompt user to re-enter; attempt may become INVALID on repeat. |
| Signature verify fails on device | n/a | n/a | Fail-closed; abort flow with a clear "verification failed" state. |
| Algorithm mismatch during bind | n/a | n/a | Fail-closed; abort enrollment. |
| Respond returns `FAILED` in 200 body | 200 | (modeled in body) | Display structured failure; no retry. |

## Error Inventory (Seed)

### `algorithm-mismatch`

- **Category.** Cryptographic.
- **Trigger.** `integrationKeyAlgorithm` is not `ed25519`.
- **Externalization.** Local UI state; no backend call is sent after detection.
- **User response.** Enrollment cannot continue; user contacts operator.

### `signature-verify-failed`

- **Category.** Cryptographic.
- **Trigger.** Ed25519 verify fails on the bind payload, or integration signature fails on pending/respond responses.
- **Externalization.** Local fail-closed UI state.
- **Audit.** Log non-secret identifiers (`enrollmentId`, `authAttemptId`) only.

### `auth-attempt-conflict`

- **Category.** Protocol / Conflict.
- **Trigger.** Attempt already terminal or superseded.
- **Externalization.** HTTP 409 Problem Details.
- **User response.** Restart from the integrating application.

### `heartbeat-degraded`

- **Category.** Degraded.
- **Trigger.** HTTP 503 with `system/audit-chain-heartbeat-degraded`.
- **Externalization.** Banner with `Retry-After` guidance.
- **User response.** Wait and retry.

## Cross-Boundary Propagation

- Mobile only talks to the Auth API; Admin API errors are not part of this surface.
- Native module errors (keystore, signing) are wrapped into typed JS errors with non-secret diagnostics.

## Authoring Rules

- Never log proof tokens, signatures, plaintext recovery-code-like artifacts, or keystore bytes.
- Prefer identifier-only logs (for example `enrollmentId`, `authAttemptId`).
- Keep user-facing messages short and non-deceptive; do not speculate about success when verification fails.

## Related Documents

- [`functional-flows.md`](functional-flows.md).
- [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md).
- Global decision: [ADR-0003](../../global/architecture-decisions.md#adr-0003-rfc9457-as-external-error-contract).
- Mobile canonical: [`../../../ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`](../../../ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md).
