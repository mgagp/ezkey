# Specification: Admin recovery audit trail (recovery code + enrollment reset)

**Status:** Archived — **fully implemented and tested** (see §13).  
**Former location:** `docs/audit/SPEC_ADMIN_RECOVERY_AUDIT_TRAIL.md` (moved here March 2026).  
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
- **`event_action`:** `recovery_code_used` (unchanged name; semantics: recovery token issued after code consumption).
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
- **`EventType`:** `ADMIN_RECOVERY_ENROLLMENT_RESET` (new enum value; see §11.1 for rationale and alternative).
- **`event_action`:** `enrollment_reset_via_recovery`.
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

- **`EventType`:** `ADMIN_RECOVERY_ENROLLMENT_RESET` (same as §6.4).
- **`EventStatus`:** `FAILURE` or `ERROR` as appropriate.
- **`event_action`:** `enrollment_reset_via_recovery_failed` (distinct from success for SIEM clarity).

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
- **EventType enum:** Add `ADMIN_RECOVERY_ENROLLMENT_RESET` (§6.4, §6.5) and update:
  - Admin API OpenAPI **only via code generation workflow** (maintainer runs spec update scripts),
  - Admin UI generated types / audit filters / i18n (`eventType.*`, `eventAction.*`),
  - Any allowlists that enumerate `EventType` values.

---

## 8. Admin UI contract

- **Audit log list / detail:** Display `event_details` as formatted JSON or key-value when content parses as JSON (existing patterns for other events).
- **i18n:** Add English and French labels for any new `EventType` and `event_action` values.
- **Filters:** Ensure the new `EventType` appears in filter dropdowns driven from the same source as other types.

---

## 9. Testing requirements

- Unit or integration tests that:
  - Assert **recover success** audit contains `schema_version`, `flow`, `step`, `recovery_token_fingerprint`, and `mfa_enrollment_id` when applicable.
  - Assert **reset success** audit is persisted with matching `recovery_token_fingerprint` for the same token.
  - Assert **no** secret material appears in `event_details` for any path.

---

## 10. Rollout and backward compatibility

Ezkey **does not yet have production deployments**; the product is in **full development**. There is **no requirement** to preserve legacy `event_details` string formats, dual-write reporting, or migration of historical audit rows for this change. Implement **JSON-only** `event_details` for all recovery-related events defined in this spec, without maintaining parallel plain-string variants.

---

## 11. Open points for review — decision guide

This section explains each open decision, compares options, and states **recommended defaults** so stakeholders can confirm or override before implementation.

### 11.1 `EventType` for enrollment reset: new value vs reuse of `ADMIN_RECOVERY_USE`

**What we are deciding:** Step A (recovery code → token) and Step B (enrollment reset) are two different security meanings: **credential / break-glass use** vs **MFA enrollment state change** (device unbound, new proof material). The audit system exposes both `event_type` and `event_action`; we must choose whether Step B gets its own top-level type.

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A — New `EventType`** (e.g. `ADMIN_RECOVERY_ENROLLMENT_RESET`) | Step B rows use the new enum value; Step A stays `ADMIN_RECOVERY_USE`. | Clear SIEM and UI filters (“show only enrollment resets”); aligns with other enrollment-adjacent events; avoids overloading one type with two meanings. | Slightly more churn: `EventType` enum, OpenAPI/UI/i18n allowlists. |
| **B — Reuse `ADMIN_RECOVERY_USE`** | Only `event_action` differs (e.g. `enrollment_reset_via_recovery`). | Smaller schema change; one bucket for “anything in the recovery funnel”. | Harder to report “enrollment changes” without parsing `event_action`; mixes credential events with infrastructure state change. |

**Recommended default:** **Option A** — add `ADMIN_RECOVERY_ENROLLMENT_RESET` for Step B success/failure audits. Reserve `ADMIN_RECOVERY_USE` for Step A and recovery failures tied to code validation.

---

### 11.2 Length of `recovery_token_fingerprint` (truncated SHA-256 hex)

**What we are deciding:** The fingerprint correlates Step A and Step B without storing the token. It is a prefix of `hex(SHA-256(token))`.

| Length (hex) | Bits (approx.) | Comment |
|--------------|----------------|---------|
| **12** | 48 | Short; still very unlikely to collide across unrelated sessions; fine if display width matters. |
| **16** | 64 | **Balanced default:** strong operational correlation, compact in JSON and logs. |
| **64** | 256 | Full hash; maximum redundancy; no practical gain for correlation if the token is already high-entropy and single-use. |

**Recommended default:** **16 hex characters** (as in §4.1). Use the same length everywhere (recover + reset) for join queries.

---

### 11.3 Exposing correlation in REST APIs (`recovery_token_fingerprint` or `recovery_correlation_id`)

**What we are deciding:** Audits will contain the fingerprint internally. Whether **clients** (Admin UI, support scripts) receive an explicit correlation field in HTTP responses.

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A — Audit only** | Fingerprint appears only in `audit_log.event_details`. API responses unchanged. | Minimal surface; no new fields to document or misuse; correlation is operator/SIEM concern. | Support must use audit UI or DB/API for audits to tie steps together. |
| **B — Expose fingerprint in API** | e.g. add `recoveryTokenFingerprint` to recover (and optionally echo on reset). | Easier for ticket notes (“same fingerprint as row X”); optional client-side display during dev. | New contract; risk of confusion with security-sensitive data if mislabeled (still not secret, but must be documented clearly). |
| **C — Separate UUID `recovery_correlation_id`** | Issued at recover time; stored in audit and returned to client; repeated on reset request/response. | Human-friendly opaque ID; no derivation from token in the API layer. | Requires generation, storage, and possibly passing the ID on reset (header or body), i.e. larger implementation. |

**Recommended default:** **Option A** — keep correlation **internal to audit and server-side logging** for the first implementation. Revisit **Option B** or **C** only if product/support explicitly needs correlation outside the audit store.

---

### 11.4 Decisions (confirmed)

| Topic | Decision |
|--------|----------|
| §11.1 EventType | **Option A** — `ADMIN_RECOVERY_ENROLLMENT_RESET` for Step B (success and failure) |
| §11.2 Fingerprint | **16** hex characters (`recovery_token_fingerprint`) |
| §11.3 API exposure | **Option A** — correlation fields **audit-only**; no new REST response fields |

---

## 12. References

- `ezkey-admin-api` — `AdminAuthController.recover`, `AdminEnrollmentController.resetEnrollment`, `RecoveryAuditDetails`
- `ezkey-core` — `EventType`, `AuditDetailsBuilder`, audit persistence rules
- [Audit logging implementation](../../../../docs/audit/AUDIT_LOGGING_IMPLEMENTATION.md) — general audit architecture
- `ezkey-admin-ui` — `src/locales/en/audit-logs.json`, `src/lib/audit-event-type.ts`, audit log pages
- Postman — `postman/collections/v2.1/EZ Key Authentication Login admin.postman_collection.json`, `EZ Key Audit Logs admin.postman_collection.json`

---

## 13. Implementation completion

This specification is **fully implemented and tested** in the repository.

| Area | Delivered |
|------|-----------|
| Structured audit JSON | `org.ezkey.admin.audit.RecoveryAuditDetails` (fingerprint + `AuditDetailsBuilder`) |
| Recover | `AdminAuthController` — success, `recovery_code_failed`, `recovery_error` with JSON `event_details` |
| Reset | `AdminEnrollmentController` — `EventType.ADMIN_RECOVERY_ENROLLMENT_RESET`, success and failure paths |
| Core | `EventType.ADMIN_RECOVERY_ENROLLMENT_RESET`; `AdminAuditConstants.ENROLLMENT_RESET_VIA_RECOVERY` / `_FAILED` |
| Unit tests | `RecoveryAuditDetailsTest` |
| Build | `mvn test -pl ezkey-admin-api -am` (and Spotless) passing at time of archive |
| Admin UI | i18n EN/FR and `audit-event-type.ts` for new type and actions |
| Postman | Auth login + Audit logs collections updated for `enrollmentId`, status codes, audit event types |

**OpenAPI:** Generated specs under `specs/` are refreshed by the maintainer via `scripts/update-specs.sh` after a clean API run; not a blocker for this audit-only contract (no new public DTO fields for correlation).
