---
name: Admin deactivation reason and justification coherence
overview: "The admin deactivation API already accepts an optional reason (query param, 10–500 chars) and records it in the audit log. The gap is solely in the Admin UI: the deactivation dialog does not collect or send the reason. The plan adds reason capture to the admin deactivate flow, aligns the two entry points (list and detail), and includes a coherence assessment of all reason/justification handling across API and UI for SOC2 and uniformity."
todos: []
isProject: false
---

# Admin deactivation reason and justification coherence

## Findings

### Admin deactivation: API vs UI

- **API ([AdminProvisioningController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java))**: The endpoint `POST /api/v1/admins/{id}/deactivate` already accepts an **optional** `reason` as a query parameter (`@RequestParam(required = false)`), with `@Size(min = 10, max = 500)` when provided, and records it in the audit log via `.reason(reason)` (lines 810–813, 833). **No API change is required.**
- **UI ([admins.tsx](ezkey-admin-ui/src/pages/admins.tsx))**: Both entry points call the API **without** a reason:
  - **DeactivateAdminDialog** (list flow): `api.post(\`/api/v1/admins/${adminId}/deactivate, {})` (line 179).
  - **AdminDetailDialog** (detail flow): `api.post(\`/api/v1/admins/${adm!.adminId}/deactivate, {})` (line 262).
- **Generated client**: [deactivateAdminParams.ts](ezkey-admin-ui/src/generated/admin-api/model/deactivateAdminParams.ts) and [administrator-provisioning.ts](ezkey-admin-ui/src/generated/admin-api/administrator-provisioning/administrator-provisioning.ts) already expose `DeactivateAdminParams { reason?: string }` and `deactivateAdmin(id, params?)`. The client is ready; the UI simply does not use it.

**Conclusion**: The missing reason is a **UI-only gap**. The API is already aligned with the “optional reason; when provided, min 10 chars” rule and SOC2-friendly.

---

## Coherence snapshot: reason/justification across the product


| Area                                            | API                                      | UI pattern                                                |
| ----------------------------------------------- | ---------------------------------------- | --------------------------------------------------------- |
| **Admin deactivate**                            | Optional query `reason` (10–500)         | No reason field (gap)                                     |
| **Admin activate**                              | No reason                                | No reason (parity with API)                               |
| **Tenant deactivate/activate**                  | Optional body `reason` (10–500)          | Optional reason field, validation when provided (aligned) |
| **Enrollment deactivate/reactivate**            | Optional query `reason` (max 500)        | Hardcoded strings only (inconsistent)                     |
| **Enrollment revoke**                           | Mandatory `reason` (10–500)              | Dialog with required reason (aligned)                     |
| **Integration revoke-all**                      | Mandatory `reason`                       | DangerConfirmDialog with `requireReason` (aligned)        |
| **Integration deactivate-all / reactivate-all** | Optional `reason`                        | Dialog passes reason (aligned)                            |
| **Integration delete**                          | Mandatory `reason`                       | Dialog with `requireReason` (aligned)                     |
| **API key revoke**                              | Optional `reason` (10–500 when provided) | Dialog, optional; min 10 when provided (aligned)          |


**Normative rule in use**: SOC2 does not require forcing a reason; when the user provides one, it must be validated (min 10, max 500). Empty/null is accepted for optional reason.

---

## Implementation plan

### 1. Admin deactivation UI: add reason and use the API (mandatory)

- **DeactivateAdminDialog** ([admins.tsx](ezkey-admin-ui/src/pages/admins.tsx)):
  - Add local state `reason` and an optional “Reason (min 10 chars, for audit trail)” input, matching the pattern in [tenant-detail.tsx](ezkey-admin-ui/src/pages/tenant-detail.tsx) (Label + Input, placeholder, same copy).
  - Validation: allow empty; if non-empty, require length ≥ 10 to enable the Deactivate button (`disabled={reason.length > 0 && reason.length < 10}`).
  - Call the generated client: `deactivateAdmin(admin.adminId, { reason: reason.trim() || undefined })` instead of raw `api.post(..., {})`. Only send `reason` when `reason.trim().length >= 10` (or omit for empty) so the backend never receives an invalid length.
  - Reset `reason` on close and on success.
- **AdminDetailDialog** (same file):
  - Remove the direct deactivate mutation from the Deactivate button.
  - Add a callback prop, e.g. `onRequestDeactivate(admin: AdminResponseDto)`. When the user clicks Deactivate, call `onRequestDeactivate(adm)` and close the detail dialog (e.g. parent sets `setSelectedAdmin(null)` and `setDeactivateTarget(adm)`), so **DeactivateAdminDialog** opens with that admin. This keeps a single place for reason capture and avoids duplicating the dialog.
- **Page**: Pass `onRequestDeactivate={(a) => { setSelectedAdmin(null); setDeactivateTarget(a); }}` (or equivalent) into **AdminDetailDialog** so that “Deactivate” from the detail view opens the same **DeactivateAdminDialog** with the selected admin.

Result: both list and detail flows go through one dialog that collects an optional reason and calls the existing API correctly.

---

### 2. Coherence assessment: “reason” as part of the PI (procédure d’identification / justification)

- **Scope**: All admin and tenant operations that are audited and that accept or could accept a reason/justification (admin/tenant/enrollment/integration/api-key deactivate, activate, revoke, delete, bulk actions, key rotation, archive/gap if applicable).
- **Deliverable**: A short coherence note (e.g. in `docs/` or in a follow-up ADR) that:
  - Lists each such operation, whether the API has optional vs mandatory reason, and whether the UI collects it.
  - States the product rule: “Optional reason: empty allowed; if provided, min 10, max 500. Mandatory reason: min 10 (and max 500 where applicable).”
  - Flags any remaining gaps (e.g. admin activate, enrollment deactivate/reactivate) and recommends next steps (see below).
- **Alignment**: Ensure the new admin deactivate dialog (and any future “reason” dialogs) follow the same rule and the same UX pattern (optional vs required, label, validation, and when to send/omit the param).

---

### 3. Optional follow-ups (recommended in the coherence note)

- **Admin activate**: For parity with tenant activate, consider adding an optional `reason` to `POST /api/v1/admins/{id}/activate` (query or body) and to the audit log, and an optional reason field in the UI when activating an admin.
- **Enrollment deactivate/reactivate**: The API already supports optional reason; the UI currently uses hardcoded strings. For uniformity and better traceability, consider adding an optional reason field (same rule: empty ok, else min 10) on the enrollment detail page for deactivate and reactivate, and passing it to the API instead of a fixed string.
- **Shared dialog pattern**: Consider extracting a small reusable “Confirm with optional reason” component (or extending the integration DangerConfirmDialog) so that optional-reason flows (tenant toggle, admin deactivate, future admin activate, enrollment deactivate/reactivate) share the same label, validation, and button-disable logic.

---

## Edge cases and validation

- **Backend**: `@Size(min=10, max=500)` on optional `reason` means “omit or null” is valid; sending an empty string or 1–9 characters can yield 400. The UI must only attach `reason` when `reason.trim().length >= 10` (or omit when empty).
- **Generated client**: Use `deactivateAdmin(id, { reason: reason.trim() || undefined })` so that an empty string is not sent; use `undefined` when the user leaves the field empty.

---

## Summary


| Item                 | Action                                                                                                                                                                             |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Admin deactivate API | No change (already supports optional reason and audit).                                                                                                                            |
| Admin deactivate UI  | Add optional reason to **DeactivateAdminDialog**; route **AdminDetailDialog** Deactivate through the same dialog; use generated `deactivateAdmin` with `reason`.                   |
| Coherence            | Document all reason/justification points and the product rule; recommend admin activate and enrollment deactivate/reactivate UI improvements and a shared optional-reason pattern. |


