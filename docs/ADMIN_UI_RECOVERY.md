# Admin UI — Recovery codes and recovery funnel

## Contract

- **Passwordless login** uses a normal admin bearer token after device approval (`POST /api/v1/admin/auth/login` + `/passwordless-wait`).
- **Recovery codes** are single-use break-glass credentials. Entering a valid code at `POST /api/v1/admin/auth/recover` returns a **temporary recovery token** (`ezkey_recovery_*`) that is **not** a full admin session: it is intended for **MFA enrollment reset** only.
- **Reset** (`POST /api/v1/admin/enrollments/reset` with the recovery token) unbinds the old device and returns **new** enrollment proof token and challenge for binding a replacement device in the mobile app.
- After binding, the operator uses **normal passwordless login** again.

The Admin UI implements this as a **recovery funnel** on the login page, not as an alternate “logged-in” console session.

### After reset (bind step)

- The operator is **not** authenticated with a normal admin token on the login page, so the UI cannot call `GET /api/v1/admins/{id}/onboarding/qrcode` after reset.
- The success step therefore builds the **same JSON payload** as the Admin API QR generator (`enrollmentId`, `enrollmentProofToken`, optional `authUrl`) and renders a **QR image in the browser** (PNG data URL). Optional `VITE_QR_AUTH_BASE_URL` mirrors server `ezkey.qr.auth-base-url` when operators need the mobile app to target a specific auth-api base URL.
- The **binding challenge** stays visible for manual verification flows. The **enrollment proof token** remains available under a collapsed “Manual entry” section for operators who cannot scan.

## Provisioning

- New administrators receive recovery codes at **creation** time in the provisioning API response (`recoveryCodes` on the create response).
- `GET /api/v1/admins/{id}/onboarding` does **not** return plaintext recovery codes (hashed at rest). Operators must save codes from the create-success screen or out-of-band processes (e.g. bootstrap logs for the initial global admin).

## References

- API details: [ENDPOINT.md](./ENDPOINT.md) (admin auth recover, enrollments reset, administrator provisioning).
- Postman: `postman/collections/v2.1/EZ Key Authentication Login admin.postman_collection.json`.
