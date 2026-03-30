# Enrollment workflow gaps (follow-up backlog)

This note captures product/backend workflows that are **not** covered by generic enrollment metadata editing (`PATCH /api/v1/enrollments/{id}`) or standard lifecycle actions (deactivate, reactivate, revoke, delete).

## Administrator MFA enrollment reset

**Exists today:** Administrators who lose a device use recovery codes → recovery token → `POST /api/v1/admin/enrollments/reset` to unbind and receive new bind credentials. Documented in `docs/ENDPOINT.md` and `docs/ADMIN_UI_RECOVERY.md`.

## General (non-admin) enrollment: device replacement / forced rebind

**Gap:** There is no Admin API equivalent to admin recovery for **arbitrary** integration enrollments (e.g. “unbind this device and issue new proof token + challenge for the same logical user”). Such a change affects cryptographic binding (`device_public_key`, proof token material) and must not be done via free-form metadata edits.

**Product decision (Ezkey):** Use the **existing** lifecycle: **revoke** the enrollment, then **create** a new enrollment for the user. No additional API surface (no dedicated “reset” or “rebind” endpoint). This matches common market practice, keeps the model simple, and preserves traceability through revoke + create as distinct audited steps.

**Operator guidance:** Document the business reason in your usual change/ticket process if required; the platform does not require a separate reason field on a dedicated reset operation because that operation is not offered for non-admin enrollments.
