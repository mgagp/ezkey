# Admin UI — Recovery codes and recovery funnel

## Contract

- **Passwordless login** uses a normal admin bearer token after device approval (`POST /api/v1/admin/auth/login` + `/passwordless-wait`).
- **Recovery codes** are single-use break-glass credentials. Entering a valid code at `POST /api/v1/admin/auth/recover` returns a **temporary recovery token** (`ezkey_recovery_*`) persisted with purpose `RECOVERY`. It is **not** a full admin session: the shared Admin API authentication filter rejects it on ordinary authenticated routes (SEC-021). It may only be used for **MFA enrollment reset**, and is deactivated after a successful reset.
- **Reset** (`POST /api/v1/admin/enrollments/reset` with the recovery token) unbinds the old device and returns **new** enrollment proof token and challenge for binding a replacement device in the mobile app.
- After binding, the operator uses **normal passwordless login** again.

The Admin UI implements this as a **recovery funnel** on the login page, not as an alternate “logged-in” console session.

### After reset (bind step)

- The operator is **not** authenticated with a normal admin token on the login page, so the UI cannot call `GET /api/v1/admins/{id}/onboarding/qrcode` after reset.
- The success step therefore builds the **same JSON payload** as the Admin API QR generator (`enrollmentId`, `enrollmentProofToken`, optional `authUrl`) and renders a **QR image in the browser** (PNG data URL). Set `VITE_QR_AUTH_BASE_URL` to the same value as the Admin API’s `ezkey.qr.auth-base-url` (and the `authApiPublicBaseUrl` field from `GET /api/v1/public/instance-info`) so browser-generated QR matches server-generated QR and the mobile app receives a consistent `authUrl`.
- The **binding challenge** stays visible for manual verification flows. The **enrollment proof token** remains available under a collapsed “Manual entry” section for operators who cannot scan.

## Provisioning

- **`IMMEDIATE` (default):** first enrollment is created at provisioning. Bind material comes from
  `GET /api/v1/admins/{id}/onboarding`, not from recovery. Plaintext recovery codes are deferred
  from bootstrap responses.
- **`ACTIVATION_CODE`:** admin is `PENDING_ACTIVATION` with no enrollment. The new admin consumes
  `POST /api/v1/admin/auth/activate` (login-page activation branch), then binds a device. That
  unauthenticated response also omits recovery codes.
- After an enrollment exists, operators obtain a **replacement set** through
  `POST /api/v1/admins/{id}/recovery-codes/regenerate`; the previous unused set is invalidated
  immediately.
- `GET /api/v1/admins/{id}/onboarding` does **not** return plaintext recovery codes (hashed at rest).

Canon: [LIFECYCLE_GOVERNANCE.md](LIFECYCLE_GOVERNANCE.md) §3.5 (activation ≠ recovery).

## Audit (recover + enrollment reset)

Break-glass steps are first-class audit events (JSON `event_details` via
`org.ezkey.admin.audit.RecoveryAuditDetails`):

| Step | Typical `EventType` | Notes |
|------|---------------------|--------|
| Recovery code validated / rejected | `ADMIN_RECOVERY_USE` | Actions such as `recovery_code_used` / failure variants |
| Enrollment reset success / failure | `ADMIN_RECOVERY_ENROLLMENT_RESET` | Security-critical unbind + new proof material |

- Payload includes `schema_version`, `flow: "admin_recovery"`, `step`, and
  **`recovery_token_fingerprint`** (first 16 hex chars of SHA-256 of the temporary recovery
  token) so SIEM/operators can **join** recover → reset rows. Correlation is **audit-only** (not
  exposed on REST DTOs).
- **Never** log plaintext recovery codes, full `ezkey_recovery_*` tokens, enrollment proof tokens,
  or device keys in `event_details`.

## References

- API details: [ENDPOINT.md](./ENDPOINT.md) (admin auth recover, enrollments reset, administrator provisioning).
- Audit architecture: [audit/AUDIT_LOGGING_IMPLEMENTATION.md](./audit/AUDIT_LOGGING_IMPLEMENTATION.md).
- Bruno: `bruno/authentication-login-admin/` (recover, enrollments-reset, passwordless-wait); public instance metadata (no auth): `bruno/public-admin/get-public-instance-info.bru`. The `postman/` tree is a historical leftover — see `postman/README.md`.
