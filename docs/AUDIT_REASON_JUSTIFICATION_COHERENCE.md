# Audit Reason and Justification — Coherence Note

This document records the product rule and current state of "reason" / "justification" handling across audited operations in the Admin API and Admin UI, for SOC2 traceability and operational uniformity.

## Product rule

- **Optional reason**: Empty or null is accepted. When the user provides a value, it must be validated: **min 10 characters, max 500 characters**. The UI must not send a reason shorter than 10 characters (backend would return 400).
- **Mandatory reason**: Required only where the product explicitly requires it (e.g. single enrollment revoke). Same validation: **min 10, max 500** characters. Bulk revoke and delete integration use **optional** reason for normative consistency.

From a normative (SOC2) perspective, forcing a reason is not essential; when a reason is provided, it must meet the minimum length so it is operationally useful in the audit trail.

## Operations inventory

| Area | API | UI | Notes |
|------|-----|----|--------|
| **Admin deactivate** | Optional query `reason` (10–500) | Optional reason field in DeactivateAdminDialog; validation when provided | Aligned. |
| **Admin activate** | No reason | No reason | Parity. Optional reason could be added for consistency with tenant activate. |
| **Tenant deactivate / activate** | Optional body `reason` (10–500) | Optional reason field; validation when provided | Aligned. |
| **Enrollment deactivate** | Optional query `reason` (max 500) | Dialog with optional reason (min 10 when provided) | Aligned. |
| **Enrollment reactivate** | Optional query `reason` (max 500) | Dialog with optional reason (min 10 when provided) | Aligned. |
| **Enrollment revoke** | Mandatory `reason` (10–500) | Dialog with required reason (min 10) | Aligned. |
| **Integration revoke-all** | Optional `reason` (10–500 when provided) | Optional reason; button enabled when empty | Aligned. |
| **Integration deactivate-all / reactivate-all** | Optional `reason` | Optional reason; same pattern | Aligned. |
| **Integration delete** | Optional query `reason` (10–500) | Dialog with optional reason (min 10 when provided) | Aligned. |
| **API key revoke** | Optional `reason` (10–500 when provided) | Dialog; optional; min 10 when provided | Aligned. |
| **Key rotation** | Optional `reason` (10–500) | Reason field in UI | Aligned. |
| **Audit archive seal / gap declaration** | Justification in request body | N/A (specialized audit endpoints) | API-only. |

## Recommended follow-ups

1. **Admin activate**: For parity with tenant activate, consider adding an optional `reason` parameter to `POST /api/v1/admins/{id}/activate` and recording it in the audit log, plus an optional reason field in the Admin UI when activating an admin.
2. **Shared dialog pattern**: Consider extracting a reusable "Confirm with optional reason" component (or extending the integration DangerConfirmDialog) so that optional-reason flows (tenant toggle, admin deactivate, future admin activate, enrollment deactivate/reactivate) share the same label, validation, and button-disable logic.

## Alignment checklist for new or updated flows

When adding or changing an audited operation that accepts a reason or justification:

- [ ] API: Optional vs mandatory is explicit; when provided, validate min 10 and max 500 (unless a different limit is documented).
- [ ] UI: If optional, allow empty and disable submit only when the user has entered 1–9 characters; if mandatory, require at least 10 characters.
- [ ] Never send a reason with length in [1, 9] to the API (backend will reject with 400).
- [ ] Use the same copy and placeholder pattern as existing dialogs (e.g. "Reason (min 10 chars, for audit trail)", "Justification for this action...").
