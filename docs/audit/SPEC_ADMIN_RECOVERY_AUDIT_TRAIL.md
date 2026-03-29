# Specification: Admin recovery audit trail (recovery code + enrollment reset)

**Status:** Draft for review  
**Audience:** Engineering, Security, Operations (SOC / SIEM), Compliance  
**Scope:** `POST /api/v1/admin/auth/recover`, `POST /api/v1/admin/enrollments/reset`, persisted `audit_log` rows, Admin UI audit presentation.

---

## 1. Problem statement

Today:

- **Recovery code submission** (`/admin/auth/recover`) writes an audit row with `EventType.ADMIN_RECOVERY_USE` and `event_action` `recovery_code_used` (or failure variants), but `event_details` is a **plain string** with limited structure.
- **Enrollment reset** (`/admin/enrollments/reset`) performs a security-critical operation (unbind device, rotate enrollment proof material) but **does not persist a corresponding audit log entry** in the same way as other admin actions.
- Operators and compliance reviewers cannot **reliably correlate** “code used” → “MFA enrollment reset completed” in the audit database, and cannot filter or report on the **full recovery funnel** as a single logical process.

This specification defines an **evolution** of the audit contract: structured `event_details` (JSON), new `event_action` values where needed, optional **correlation** between steps, and alignment with operational and **SOC 2**-style expectations (traceability of privileged access paths, without logging secrets).

---

## 2. Goals

### 2.1 Operational visibility

- Identify **who** initiated recovery (subject admin), **when**, **from which client** (IP / user agent already captured).
- Distinguish **steps**: (A) recovery code validated and recovery token issued, (B) enrollment reset executed with recovery authorization.
- Support **search and dashboards**: filter by `event_action`, `event_type`, `admin_id`, `tenant_id`, and structured fields inside `event_details`.

### 2.2 Normative / compliance alignment (high level)

This spec does not replace your formal SOC 2 control matrix; it **maps** the implementation to common expectations:

| Theme | Expectation | How this spec helps |
|--------|-------------|---------------------|
| Logical access (e.g. CC6.x) | Sensitive operations are attributable | `admin_id`, tenant context, success/failure |
| Monitoring / anomaly detection (e.g. CC7.x) | Security-relevant events are logged | Distinct actions for success/failure on each step |
| Audit trail completeness | Critical state changes are recorded | Enrollment reset becomes a first-class audit event |
| Confidentiality | Credentials are not logged | **No** recovery code, **no** proof token, **no** full bearer/recovery token in audit |

---

## 3. Non-goals and prohibitions

- **Never** store in `event_details` (or anywhere in audit): plaintext recovery code, full `ezkey_recovery_*` token, enrollment proof token, or device keys.
- **Do not** use unstructured plain strings as the **only** form of `event_details` for new or revised recovery events; use **valid JSON** (see `AuditDetailsBuilder` and `ezkey-core` guidance on JSON `event_details`).
- This document does **not** mandate changes to Auth API enrollment bind/verify audits; those remain separate. Optional **cross-reference** via `enrollment_id` is sufficient for investigations.

---

## 4. Correlation model (between recover and reset)

Operators need a stable link between the audit line emitted when a recovery token is issued and the audit line when that token is used to reset enrollment.

### 4.1 Recommended approach: `recovery_token_fingerprint`

- **Definition:** Let `T` be the recovery bearer token string (`ezkey_recovery_…`) issued after successful code validation.  
  `recovery_token_fingerprint =` first **16** hexadecimal characters of `SHA-256(T)` (UTF-8 bytes of the token string).
- **Usage:**
  - On **recover success**, compute the fingerprint from `T` **before** returning the token to the client (same `T` that is persisted as a hash in the token store).
  - On **reset success or failure** (when a Bearer recovery token is present), compute the fingerprint from the presented token and include the **same** field in `event_details`.
- **Properties:** Does not reveal the token; sufficient to **join** two audit rows in SIEM queries; stable for the lifetime of that token.

### 4.2 Optional future enhancement: `recovery_correlation_id` (UUID)

- A UUID generated at recover time, stored only in audit JSON and optionally returned in API responses, repeated on reset.
- **Not required** for minimal compliance if fingerprint correlation is implemented.
- If added later, it must appear in **both** step (A) and step (B) `event_details` under the same key.

---

## 5. `event_details` JSON schema (versioned)

All new or revised recovery-related audits **MUST** set:

| Field | Type | Required | Description |
|--------|------|----------|-------------|
| `schema_version` | integer | Yes | Start at **1**; increment only on breaking semantic changes. |
| `flow` | string | Yes | Constant `"admin_recovery"`. |
| `step` | string | Yes | See §6. |

Additional fields are **step-specific** (§6).

Serialization: single JSON object string in `audit_log.event_details`, UTF-8, valid JSON (PostgreSQL `jsonb`-friendly).

---

## 6. Events and `event_action` values

### 6.1 Step A — Recovery code accepted; recovery token issued

- **HTTP:** `POST /api/v1/admin/auth/recover` — success path.
- **`EventType`:** `ADMIN_RECOVERY_USE` (unchanged).
- **`event_action`:** `recovery_code_used` (retain for backward compatibility; treat as “recovery token issued after code consumption”).
- **`EventStatus`:** `SUCCESS`.
- **`admin_id`:** Set to the subject administrator.
- **`tenant_id`:** As today (tenant admin) or `null` (global admin).

**`event_details` (JSON, schema_version 1):**

```json
{
  "schema_version": 1,
  "flow": "admin_recovery",
  "step": "recovery_code_validated",
  "username": "string",
  "admin_id": 0,
  "tenant_id": null,
  "recovery_codes_remaining": 0,
  "mfa_enrollment_id": 0,
  "recovery_token_fingerprint": "string"
}
```

| Field | Notes |
|--------|--------|
| `mfa_enrollment_id` | MFA enrollment targeted for subsequent reset (same semantics as API response `enrollmentId`). |
| `recovery_codes_remaining` | Count **after** removing the used code. |

### 6.2 Step A — Recovery code rejected (expected failures)

- **HTTP:** `403` with authentication-style failure.
- **`EventType`:** `ADMIN_RECOVERY_USE`.
- **`event_action`:** `recovery_code_failed` (unchanged).
- **`EventStatus`:** `FAILURE`.

**`event_details` (JSON):**

```json
{
  "schema_version": 1,
  "flow": "admin_recovery",
  "step": "recovery_code_rejected",
  "username": "string",
  "tenant_id": null,
  "reason_code": "string",
  "message": "string"
}
```

`reason_code` SHOULD be a short machine-readable token (e.g. `invalid_code`, `no_codes_remaining`, `account_inactive`, `unknown_user`).  
`message` MAY duplicate the safe client-facing message (no stack traces).

### 6.3 Step A — Unexpected error during recover

- **`event_action`:** `recovery_error` (unchanged).
- **`EventStatus`:** `ERROR`.

**`event_details` (JSON):** same as §6.2 with `step`: `"recovery_error"` and optional `exception_class` (sanitized, no PII).

---

### 6.4 Step B — Enrollment reset via recovery token (new audit)

- **HTTP:** `POST /api/v1/admin/enrollments/reset` — success path.
- **`EventType`:** **Option 1 (recommended):** add `ADMIN_RECOVERY_ENROLLMENT_RESET` to `EventType` enum for clear SIEM filtering **or** **Option 2:** reuse `ADMIN_RECOVERY_USE` with a distinct `event_action` only.  
  **Recommendation:** **Option 1** — new `EventType` so dashboards can separate “credential recovery” from “enrollment state change” while still being part of the same product flow.

If Option 2 is chosen for a smaller change set, use:

- **`EventType`:** `ADMIN_RECOVERY_USE`
- **`event_action`:** `enrollment_reset_via_recovery`

- **`EventStatus`:** `SUCCESS`.
- **`admin_id`:** Subject administrator (from validated recovery token).

**`event_details` (JSON):**

```json
{
  "schema_version": 1,
  "flow": "admin_recovery",
  "step": "enrollment_reset_completed",
  "admin_id": 0,
  "tenant_id": null,
  "enrollment_id": 0,
  "integration_id": 0,
  "recovery_token_fingerprint": "string",
  "device_unbound": true,
  "enrollment_status_after": "CREATED"
}
```

| Field | Notes |
|--------|--------|
| `device_unbound` | `true` when previous device binding is cleared (expected for this flow). |
| `enrollment_status_after` | Enrollment workflow status after reset (string enum as persisted). |

**Secrets:** Do **not** include new proof token or challenge values.

### 6.5 Step B — Enrollment reset failed (auth or validation)

- **`EventStatus`:** `FAILURE` or `ERROR` as appropriate.
- **`event_action`:** `enrollment_reset_via_recovery_failed` (new constant) or the same `event_action` as success with `FAILURE` (prefer **distinct** action for query clarity).

**`event_details` (JSON):**

```json
{
  "schema_version": 1,
  "flow": "admin_recovery",
  "step": "enrollment_reset_failed",
  "admin_id": null,
  "tenant_id": null,
  "enrollment_id": 0,
  "recovery_token_fingerprint": "string",
  "reason_code": "string",
  "message": "string"
}
```

When the token is missing or not a recovery token, `recovery_token_fingerprint` MAY be omitted.

---

## 7. Server implementation notes

- **Controllers:** `AdminAuthController.recover` — replace string `event_details` with JSON built via `AuditDetailsBuilder` (or a dedicated small builder for recovery to avoid key typos).
- **Controllers:** `AdminEnrollmentController.resetEnrollment` — inject `AuditLogService` and emit §6.4 / §6.5 after validation boundaries used today (success after `resetEnrollment`; failures in existing `catch` branches with safe messages).
- **Core constants:** Extend `AdminAuditConstants` with new `event_action` strings; document them in `AUDIT_ADMIN_LOGIN_ACTIONS.md` (or equivalent) if present.
- **EventType enum:** If adding `ADMIN_RECOVERY_ENROLLMENT_RESET`, update:
  - Admin API OpenAPI **only via code generation workflow** (maintainer runs spec update scripts),
  - Admin UI generated types / audit filters / i18n (`eventType.*`, `eventAction.*`),
  - Any allowlists that enumerate `EventType` values.

---

## 8. Admin UI contract

- **Audit log list / detail:** Display `event_details` as formatted JSON or key-value when content parses as JSON (existing patterns for other events).
- **i18n:** Add English and French labels for any new `EventType` and `event_action` values.
- **Filters:** Ensure new `EventType` (if any) appears in filter dropdowns driven from the same source as other types.

---

## 9. Testing requirements

- Unit or integration tests that:
  - Assert **recover success** audit contains `schema_version`, `flow`, `step`, `recovery_token_fingerprint`, and `mfa_enrollment_id` when applicable.
  - Assert **reset success** audit is persisted with matching `recovery_token_fingerprint` for the same token.
  - Assert **no** secret material appears in `event_details` for any path.

---

## 10. Rollout and backward compatibility

- Existing rows keep legacy string `event_details`; reporting tools SHOULD tolerate **either** legacy strings or JSON for `ADMIN_RECOVERY_USE` / `recovery_code_used`.
- New writes use JSON only for the events touched by this spec.
- SIEM rules SHOULD key on `event_action` and structured fields when `schema_version` is present.

---

## 11. Open points for review

1. **Enum choice:** New `EventType` `ADMIN_RECOVERY_ENROLLMENT_RESET` vs reusing `ADMIN_RECOVERY_USE` — product and SIEM preference.
2. **Fingerprint length:** 16 hex chars vs 12 vs full 64 — trade-off between collision resistance and column width (16 is acceptable for operational correlation).
3. **Optional API surface:** Whether to expose `recovery_token_fingerprint` or a `recovery_correlation_id` in REST responses for **support tickets** (not required for audit storage if fingerprint is internal-only).

---

## 12. References

- `ezkey-admin-api` — `AdminAuthController.recover`, `AdminEnrollmentController.resetEnrollment`
- `ezkey-core` — `EventType`, `AuditDetailsBuilder`, audit persistence rules
- `docs/audit/AUDIT_LOGGING_IMPLEMENTATION.md` (if present) — general audit architecture
- `ezkey-admin-ui` — `src/locales/en/audit-logs.json`, audit log pages
