# Postman parity — enrollment + auth attempt (numbered collections)

Canonical payload rules: [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md), [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md).

## EZ Key Enrollments admin (`EZ Key Enrollments admin`)

| CLI step id | Postman folder / request |
|-------------|---------------------------|
| `admin.enrollment_create` | `create` (or minimal variant; CLI uses configurable body) |
| `admin.enrollment_get` | `get` — **required** after create to obtain `enrollmentProofToken` (not returned by create-only DTO) |

## EZ Key Enrollments auth (`EZ Key Enrollments auth`)

| CLI step id | Postman numbered request |
|-------------|---------------------------|
| `mobile.enrollment_bind` | `1 bind` |
| `crypto.enrollment_bind_payload` | `2 crypto build enrollment bind payload` |
| `crypto.enrollment_bind_verify` | `3 crypto validate enrollment bind by integration` |
| `crypto.device_keypair` | `4 crypto generate device keypair` |
| `crypto.enrollment_verify_device_payload` | `5 crypto build enrollment verify device payload` |
| `crypto.enrollment_verify_sign` | `6 crypto sign enrollment verify device payload` |
| `mobile.enrollment_verify` | `7 verify` |
| `crypto.enrollment_verify_result_payload` | `8 crypto build enrollment verify result payload` |
| `crypto.enrollment_verify_result_verify` | `9 crypto validate enrollment verify result by integration` |

## EZ Key Auth Attempts admin (`EZ Key Auth Attempts admin`)

| CLI step id | Postman folder / request |
|-------------|---------------------------|
| `admin.auth_attempt_create` | `create` |
| `admin.auth_attempt_wait` | `wait` (after mobile has completed respond) |

## EZ Key Auth Attempts auth (`EZ Key Auth Attempts auth`)

| CLI step id | Postman numbered request |
|-------------|---------------------------|
| `crypto.device_proof_token` | `1 crypto generate device proof token` |
| `crypto.device_proof_token_sign` | `2 crypto device prooftoken signature` |
| `mobile.auth_pending` | `3 pending` |
| `crypto.pending_payload` | `3b crypto build pending payload` |
| `crypto.pending_verify` | `4 crypto validate auth prooftoken by integration` |
| `crypto.respond_device_sign` | `5 crypto sign authAttempt prooftoken by device` |
| `mobile.auth_respond` | `6 respond` (same endpoint as `6b respond with context`; CLI uses plain create + no-context copy) |
| `crypto.respond_result_payload` | `6c crypto build respond result payload` |
| `crypto.respond_result_verify` | `7 crypto validate respond result by integration` |

**Note:** `authAttemptAccepted` / `scenario.respond_accepted` drives step `5` and `mobile.auth_respond` exactly like Postman collection variable `authAttemptAccepted`.
