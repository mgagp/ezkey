# Admin login audit actions (SIEM / operations)

Ezkey records **Admin API** authentication steps in the audit log (`event_type` = `ADMIN_LOGIN`). The **`event_action`** string is the stable field for filters, exports, and SIEM rules.

## Current action strings

| `event_action` | Typical `event_status` | When |
|----------------|------------------------|------|
| `login_success` | SUCCESS | Single-call `/login` completed with a bearer token (blocking path without separate `/passwordless-wait`). |
| `login_mfa_requested` | SUCCESS | Two-call flow: `/login` returned `status: pending` and created an MFA attempt (not a full session yet). |
| `login_mfa_session_issued` | SUCCESS | `/passwordless-wait` completed and issued a bearer session. |
| `login_mfa_expired` | FAILURE | `/passwordless-wait` ended: attempt expired or was superseded. |
| `login_mfa_rejected` | FAILURE | Device rejected the request. |
| `login_mfa_timeout` | FAILURE | No device response within the wait window (HTTP 408 class outcome). |
| `login_mfa_invalid_signature` | FAILURE | Device signature validation failed. |
| `login_mfa_invalid_challenge` | FAILURE | Challenge code did not match (challenge flow). |
| `login_mfa_error` | ERROR | Unexpected processing failure (including bad `authAttemptId` / admin link when applicable). |
| `login_failure` | FAILURE | Unexpected response status from `/login` (should be rare). |
| `login_pending` | SUCCESS | **Legacy** — older rows only; new pending flows use `login_mfa_requested`. |

## Semantics: SUCCESS vs FAILURE vs ERROR

- **SUCCESS** — The audited **step** completed as intended (including `login_mfa_requested`: the server accepted creation of the MFA request).
- **FAILURE** — Expected business or terminal outcome (expired MFA, rejected, invalid challenge, etc.).
- **ERROR** — Unexpected exception while handling the request; correlate with application logs.

## Cutover note for `login_pending`

Existing databases may still contain `event_action = login_pending`. New events from pending `/login` responses use **`login_mfa_requested`**. Queries that must match both should use `event_action IN ('login_pending', 'login_mfa_requested')` during a transition period.
