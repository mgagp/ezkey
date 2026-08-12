# Bruno parity — enrollment + auth attempt (numbered folders)

Canonical payload rules: [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md), [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md).

Exploratory collection root: [`bruno/`](../bruno/).

## `enrollments-admin/`

| CLI step id | Bruno request |
|-------------|----------------|
| `admin.enrollment_create` | `create` (or minimal variant; CLI uses configurable body) |
| `admin.enrollment_get` | `get` — **required** after create to obtain `enrollmentProofToken` (not returned by create-only DTO) |

## `enrollments-auth/`

| CLI step id | Bruno numbered request |
|-------------|------------------------|
| `mobile.enrollment_bind` | `1 bind` |
| `crypto.enrollment_bind_payload` | `2 crypto build enrollment bind payload` |
| `crypto.enrollment_bind_verify` | `3 crypto validate enrollment bind by integration` |
| `crypto.device_keypair` | `4 crypto generate device keypair` |
| `crypto.enrollment_verify_device_payload` | `5 crypto build enrollment verify device payload` |
| `crypto.enrollment_verify_sign` | `6 crypto sign enrollment verify device payload` |
| `mobile.enrollment_verify` | `7 verify` |
| `crypto.enrollment_verify_result_payload` | `8 crypto build enrollment verify result payload` |
| `crypto.enrollment_verify_result_verify` | `9 crypto validate enrollment verify result by integration` |

## `auth-attempts-admin/`

| CLI step id | Bruno request |
|-------------|----------------|
| `admin.auth_attempt_create` | `create` |
| `admin.auth_attempt_wait` | `wait` (after mobile has completed respond) |

## `auth-attempts-auth/`

| CLI step id | Bruno numbered request |
|-------------|------------------------|
| `crypto.device_proof_token` | `1 crypto generate device proof token` |
| `crypto.device_proof_token_sign` | `2 crypto device prooftoken signature` |
| `mobile.auth_pending` | `3 pending` |
| `crypto.pending_payload` | `3b crypto build pending payload` |
| `crypto.pending_verify` | `4 crypto validate auth prooftoken by integration` |
| `crypto.respond_device_sign` | `5 crypto sign authAttempt prooftoken by device` |
| `mobile.auth_respond` | `6 respond` (same endpoint as `6b respond with context`; CLI uses plain create + no-context copy) |
| `crypto.respond_result_payload` | `6c crypto build respond result payload` |
| `crypto.respond_result_verify` | `7 crypto validate respond result by integration` |

**Note:** `authAttemptAccepted` / `scenario.respond_accepted` drives step `5` and `mobile.auth_respond` exactly like Bruno env var `authAttemptAccepted` (default `true` in `bruno/environments/*.bru`).
