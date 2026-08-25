# Administrator Provisioning and Deprovisioning Procedure

This procedure describes how to create, deactivate, and reactivate administrators in Ezkey, and how to use the operator-visible audit trail for operational review.

## Scope

- **Provisioning:** Creating global and tenant administrators (peer admins).
- **Deprovisioning:** Deactivating an administrator (revokes access and tokens).
- **Reactivation:** Activating a previously deactivated administrator.

## Who Can Perform Each Action

| Action | Who |
|--------|-----|
| Create Global Admin | Global Admin only |
| Create Tenant Admin | Global Admin (any tenant), or Tenant Admin (own tenant only) |
| Get admin by ID, List admins, Get onboarding / QR | Global Admin (any), Tenant Admin (own tenant only) |
| Deactivate admin | Global Admin only |
| Activate admin | Global Admin only |

System tenant (tenant_id=1) cannot have Tenant Admins created in it.

## Provisioning (Create Admin)

1. **Create the admin** via API:
   - Global: `POST /api/v1/admins/global` (body: username, email, firstName, lastName).
   - Tenant: `POST /api/v1/admins/tenant` (body: username, email, firstName, lastName, tenantId; TenantAdmin omits tenantId and their tenant is used).
2. **Retrieve onboarding credentials** once: `GET /api/v1/admins/{id}/onboarding` (or QR: `GET /api/v1/admins/{id}/onboarding/qrcode`).
3. **Share credentials securely** with the new admin. Recovery codes and enrollment credentials are shown only at creation; recovery codes cannot be retrieved later (stored as BCrypt hashes).
4. **Audit:** Events `ADMIN_CREATED` with action `admin_global_created` or `admin_tenant_created` are logged. The created admin ID is stored in the audit log as `target_admin_id` for querying.

## Deprovisioning (Deactivate Admin)

1. **Deactivate** via API: `POST /api/v1/admins/{id}/deactivate`.
2. **Optional:** Supply a `reason` query parameter (10–500 characters) for the audit trail (recommended for internal procedures).
3. **Effect:** The admin’s account is set to inactive and all active bearer tokens are revoked. Data is preserved for audit. The operation is idempotent if the admin is already inactive.
4. **Limits:** A Global Admin cannot deactivate themselves. Deactivation cannot reduce the number of active Global Admins below the configured minimum (to avoid lockout).
5. **Audit:** Event `ADMIN_DEACTIVATED` with action `admin_deactivated` is logged. The deactivated admin ID is stored as `target_admin_id`. The optional `reason` is stored in the audit entry.

## Reactivation (Activate Admin)

1. **Activate** via API: `POST /api/v1/admins/{id}/activate`. Global Admin only.
2. **Effect:** The admin’s account is set to active. They must log in again to obtain a new bearer token. Idempotent if already active.
3. **Audit:** Event `ADMIN_ACTIVATED` with action `admin_activated` is logged; `target_admin_id` is set.

## Where to Find Audit Events

- **API:** `GET /api/v1/audit-logs` with filters:
  - `eventType`: `ADMIN_CREATED`, `ADMIN_DEACTIVATED`, or `ADMIN_ACTIVATED`.
  - `targetAdminId`: Admin ID that is the subject of the event (e.g. “all events affecting admin 5”).
  - `adminId`: Admin ID of the actor who performed the action.
- **Retention:** Controlled by the audit lifecycle policy via `ezkey.audit.archive.*`; physical
  deletion happens only after checkpoint lifecycle progression reaches a purgeable state.

## Product terms

- **Least privilege / role split** — Create and deactivate/activate are Global Admin vs Tenant Admin scoped as above.
- **Identifiable operator identity** — Creation provisions onboarding credentials once; recovery codes cannot be retrieved after creation.
- **Rapid access removal** — Deactivation discontinues access and revokes tokens.
- **Operator-visible audit trail** — Audit events and optional `reason` on deactivation support review.

Mapping vocabulary (not a claim): [`product-docs/global/normative-posture.md`](../product-docs/global/normative-posture.md).

## Quick Reference

| Operation | Method and path | Auth |
|-----------|-----------------|------|
| Create Global Admin | POST /api/v1/admins/global | Global Admin |
| Create Tenant Admin | POST /api/v1/admins/tenant | Global or Tenant Admin |
| Get admin by ID | GET /api/v1/admins/{id} | Global or Tenant Admin (scope) |
| List admins | GET /api/v1/admins | Global or Tenant Admin (scope) |
| Get onboarding | GET /api/v1/admins/{id}/onboarding | Global or Tenant Admin (scope) |
| Get QR code | GET /api/v1/admins/{id}/onboarding/qrcode | Global or Tenant Admin (scope) |
| Deactivate admin | POST /api/v1/admins/{id}/deactivate | Global Admin |
| Activate admin | POST /api/v1/admins/{id}/activate | Global Admin |

For full request/response details, see [ENDPOINT.md](ENDPOINT.md) (Administrator Provisioning section).
