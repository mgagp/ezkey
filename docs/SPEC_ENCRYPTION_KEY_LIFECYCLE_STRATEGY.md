# Specification: Encryption key lifecycle strategy and decommissioning blueprint

**Status:** Draft for review  
**Audience:** Engineering, Product, Security, Operations, Compliance  
**Scope:** Admin UI encryption keys page, Admin API encryption key endpoints, core key lifecycle services, audit evidence, future key decommissioning workflow.

---

## 1. Executive summary

This specification defines the recommended product and architecture strategy for encryption keys after
key rotation.

Today, Ezkey supports:

- introduction of a new key as `PENDING`,
- promotion to `PRIMARY`,
- retention of old keys as `ENABLED`,
- targeted or full re-encryption of data from old keys to the current primary key,
- eventual disablement of old keys.

The main product gap is that the system does **not clearly identify** when an `ENABLED` key has
become operationally obsolete because it no longer protects any active records.

This specification recommends:

1. Keep the current **technical key statuses** (`PENDING`, `PRIMARY`, `ENABLED`, `DISABLED`).
2. Add a **derived lifecycle layer** exposed in the Admin UI and API, rather than replacing the
   existing domain status model immediately.
3. Introduce a first-class concept of a **drained** key: a non-primary key for which the system has
   verified that no tracked encrypted records remain.
4. Use a **delayed, cancellable decommissioning workflow** for future deletion rather than immediate
   irreversible removal.
5. Base all lifecycle decisions on an explicit **zero remaining records** verification model, not on
   historical counters alone.

This delivers a simpler and safer operator experience while preserving strong auditability and SOC 2
alignment.

---

## 2. Problem statement

### 2.1 Current operator problem

On the Admin UI encryption keys screen, an old `ENABLED` key still shows the `Re-encrypt` action even
after re-encryption has already drained all records associated with that key.

As a result:

- operators must click key by key to discover whether anything remains,
- the meaning of `ENABLED` is ambiguous,
- the screen does not distinguish between a key that is still required and a key that is effectively
  dead but not yet decommissioned,
- the future decommissioning workflow has no clear conceptual precursor in the product.

### 2.2 Current technical gap

The most trustworthy signal for "this key still protects data" currently comes from SQL counting logic
based on the ciphertext prefix `ENC:{keyId}:...`, not from a dedicated lifecycle verification model.

At the same time, list and detail responses expose `recordsEncrypted` (migration-scope baseline set
when a key becomes `ENABLED`) and `recordsReencrypted` (cumulative units migrated in completed
batches, reset at demotion). These support progress and health views but are not sufficient by
themselves to support irreversible lifecycle decisions; drain remains prefix-based verification.

---

## 3. Product decisions

### 3.1 Lifecycle modeling

**Decision:** Use a **derived lifecycle state** on top of the existing persisted technical status.

This means:

- `keyStatus` remains the technical source of truth for cryptographic role,
- lifecycle meaning for operators is computed and surfaced separately,
- the product can evolve safely without forcing an immediate schema-level status redesign.

### 3.2 Naming

**Recommendation:** Use `Drained` as the primary operator-facing term.

Rationale:

- it is short and concrete,
- it describes the actual condition: the key no longer has remaining protected records,
- it avoids overloading the audit-log concept of `SEAL`,
- it is clearer than `Obsolete`, which sounds like a business judgment rather than a verified state.

Secondary supporting labels may be used in UI help:

- `Eligible for decommission`
- `Pending deletion`

### 3.3 Decommissioning posture

**Decision:** The target decommissioning flow should be **scheduled, delayed, and cancellable**, with
full audit evidence.

This specification does **not** recommend secure export as a default step for V1.

---

## 4. Target lifecycle model

The following model separates technical status from operator lifecycle meaning.

```mermaid
flowchart LR
  pending[Pending]
  primary[Primary]
  enabledInUse[EnabledInUse]
  drained[Drained]
  pendingDeletion[PendingDeletion]
  disabled[DisabledOrRemoved]

  pending --> primary
  primary --> enabledInUse
  enabledInUse --> drained
  drained --> pendingDeletion
  pendingDeletion --> disabled
```

### 4.1 Technical status

Persisted status remains:

- `PENDING`
- `PRIMARY`
- `ENABLED`
- `DISABLED`

### 4.2 Derived lifecycle stage

Recommended derived stages:

| Lifecycle stage | Typical technical status | Meaning |
|----------------|--------------------------|---------|
| `PENDING` | `PENDING` | New key introduced, not yet active for new writes |
| `PRIMARY` | `PRIMARY` | Current key for new encryption |
| `ENABLED_IN_USE` | `ENABLED` | Old key still protects active records |
| `DRAINED` | `ENABLED` | Verified zero remaining records across all tracked targets |
| `PENDING_DELETION` | `ENABLED` or dedicated deletion metadata | Key drained, deletion scheduled, waiting period active |
| `DISABLED` | `DISABLED` | Key retired from active lifecycle |

### 4.3 Important principle

`Drained` is **not** a synonym for `DISABLED`.

A key may be drained but still intentionally retained during:

- an audit retention window,
- an operator validation period,
- a deletion waiting period,
- an incident review.

---

## 5. Source-of-truth model for "zero remaining records"

### 5.1 Core rule

A key can be considered `Drained` only when **all registered encrypted targets** confirm that zero
records remain for that key.

This verification must be based on the same target inventory model used by re-encryption discovery.

### 5.2 Recommended architecture

Introduce a dedicated service, conceptually named:

- `KeyUsageVerificationService`

Responsibilities:

- discover all registered re-encryptable targets,
- count remaining ciphertext rows for a specific key on each target,
- aggregate the result into a per-key verification snapshot,
- expose both summary and detailed evidence to callers.

### 5.3 Verification output

Recommended output shape:

| Field | Description |
|------|-------------|
| `keyId` | Key being evaluated |
| `remainingRecords` | Sum across all tracked targets |
| `remainingTargets` | Number of targets still containing rows for that key |
| `verifiedAt` | Timestamp of the verification snapshot |
| `verificationState` | `VERIFIED_ZERO`, `REMAINS_IN_USE`, `INCOMPLETE`, `FAILED` |
| `targetsChecked` | Detailed per-target counts |

### 5.4 Why counters are not enough

Persisted counters (`recordsEncrypted` baseline at demotion, `recordsReencrypted` cumulative) are
useful for trend and progress versus that baseline, but they are not a strong enough basis for
decommissioning because:

- they do not by themselves prove the current live state,
- they do not protect against missing target inventory,
- they do not express verification recency,
- they may be operationally misleading when interpreted as a deletion guard rail.

### 5.5 Recommended strategy

Use a hybrid model:

- **Fast path:** list endpoints can return the latest persisted verification snapshot.
- **Strong path:** detail view and all decommissioning transitions should trigger or require a fresh
  verification run.

This keeps the UI responsive while preserving correctness for sensitive actions.

### 5.6 Implementation alignment (re-encryption refactor)

Phase A verification **must** reuse the same building blocks as batch creation and processing:

- **Indexed key-id counts:** [`ReencryptionTargetQueryService`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java) is the single implementation of
  `countRecordsEncryptedWithKey(table, column, keyId)` and `fetchRecords(...)`. Since I-2026-0029 /
  `TB-2026-07-26`, both query the target's indexed `*_encryption_key_id BIGINT` companion column with
  an equality predicate, not a `LIKE 'ENC:{keyId}:%'` scan on the ciphertext (see
  `docs/REENCRYPTION_OPERATIONS.md` §1.2).
- **Target inventory:** [`ReencryptionBatchCreationService#discoverReencryptableTargets()`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchCreationService.java) is the current source of
  `(table, column)` pairs derived from `Reencryptable` entities. A future `ReencryptionTargetRegistry`
  may extract this list to avoid drift; until then, lifecycle code should call the same discovery API
  as batch creation.
- **Parallel execution:** [`ReencryptionBatchParallelRunner`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchParallelRunner.java) does not change the definition of “drained”; it only
  affects throughput. Lifecycle eligibility must still consider non-terminal batches for the old key
  (for example `PENDING`, `IN_PROGRESS`, `FAILED`, `PAUSED`).

The implemented [`KeyUsageVerificationService`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/KeyUsageVerificationService.java) follows this model.

---

## 6. Admin UI strategy

### 6.1 UX goals

The encryption keys page should help operators answer three questions quickly:

1. Which key encrypts new data?
2. Which old keys still protect active records?
3. Which old keys are safe candidates for the next lifecycle step?

### 6.2 Recommended list redesign

The current screen should evolve from a purely technical table into an operational lifecycle view.

Recommended table changes:

- keep the current technical status badge,
- add a new `Lifecycle` badge,
- replace or complement `Records` with `Remaining records`,
- display `Last verified`,
- keep direct access to re-encryption and batch visibility.

Recommended row actions:

| Condition | Primary action |
|-----------|----------------|
| `PRIMARY` | No re-encryption action |
| `ENABLED_IN_USE` | `Re-encrypt` |
| `DRAINED` | `Schedule decommission` |
| `PENDING_DELETION` | `Cancel decommission` |
| `DISABLED` | No mutating action |

### 6.3 Detail drawer or dialog

The key detail view should expose lifecycle evidence explicitly:

- technical status,
- lifecycle stage,
- remaining records,
- last verification timestamp,
- targets checked and counts,
- batch history summary,
- decommission eligibility,
- decommission schedule metadata,
- blocking reasons if deletion is not allowed.

### 6.4 Copy and help

Update the encryption key copy and help so that:

- `Enabled` means decrypt-capable, not necessarily still useful,
- `Drained` means no active records remain after verification,
- `Pending deletion` means deletion is scheduled but not final,
- `Re-encrypt` is presented as a migration action, not a discovery mechanism.

### 6.5 Immediate UX correction

The current inconsistency between list and detail actions should be removed in the first UI pass.

The product should not suggest re-encryption on states where that action is not conceptually correct
or useful.

---

## 7. API evolution

### 7.1 Existing endpoints that should evolve

- `GET /api/v1/encryption-keys`
- `GET /api/v1/encryption-keys/{keyId}`

### 7.2 Recommended additive fields

Recommended new response fields:

| Field | Type | Purpose |
|------|------|---------|
| `lifecycleStage` | string | Derived operator-facing lifecycle meaning |
| `remainingRecords` | number | Current aggregated count of rows still tied to the key |
| `remainingTargets` | number | Number of targets still containing rows |
| `lastVerifiedAt` | datetime | Recency of lifecycle evidence |
| `verificationState` | string | Trust state of the usage evidence |
| `decommissionEligible` | boolean | Whether scheduling deletion is currently allowed |
| `decommissionScheduledAt` | datetime | When pending deletion starts |
| `decommissionWindowEndsAt` | datetime | When final deletion may execute |
| `decommissionBlockedReasons` | array of strings | Operator-readable blockers |

### 7.3 Recommended new endpoints

For Phase B, add dedicated lifecycle endpoints:

- `POST /api/v1/encryption-keys/{keyId}/verify-usage`
- `POST /api/v1/encryption-keys/{keyId}/decommission/schedule`
- `POST /api/v1/encryption-keys/{keyId}/decommission/cancel`

An eventual execution endpoint is optional if final deletion is done by a scheduler rather than
direct synchronous admin request.

### 7.4 Contract principle

Do not overload `Re-encrypt` responses to carry lifecycle truth. Re-encryption and lifecycle
verification should remain related but distinct concepts.

---

## 8. Backend and domain strategy

### 8.1 Service responsibilities

Recommended service split:

- `ReencryptionService`
  - create and process re-encryption batches,
  - continue to own migration execution.
- `KeyUsageVerificationService`
  - compute lifecycle evidence,
  - produce remaining-record snapshots.
- `KeyDecommissionService`
  - enforce deletion policy,
  - schedule or cancel decommission,
  - re-verify before final deletion.

This is a pragmatic split inside the existing stack, not a microservice boundary.

### 8.2 Data model considerations

The first implementation does not require replacing `EncryptionKey.keyStatus`.

It may require additional persisted metadata, for example:

- `last_verified_at`
- `last_verified_remaining_records`
- `decommission_scheduled_at`
- `decommission_window_ends_at`
- `decommission_reason`
- `decommission_cancelled_at`

The exact schema can be refined later, but these fields fit the target operator workflow.

### 8.3 Inventory completeness

Any encrypted column that is not registered in the target inventory can invalidate the meaning of
`Drained`.

Therefore:

- inventory completeness must be treated as a lifecycle dependency,
- verification should fail closed if target discovery is unhealthy,
- the product must never present `Drained` when verification coverage is incomplete.

---

## 9. Guard rails

Deletion or retirement transitions must fail closed.

### 9.1 A key must never be scheduled for deletion when

- it is `PRIMARY`,
- it is `PENDING`,
- verification reports `remainingRecords > 0`,
- there are active or failed batches that make the usage result unreliable,
- the verification snapshot is stale beyond policy limits,
- target inventory is incomplete or unhealthy.

### 9.2 Mandatory double verification

Use two mandatory controls:

1. verification when the operator schedules decommission,
2. verification immediately before final execution.

### 9.3 Waiting period

Deletion should use a configurable waiting period, similar in spirit to mature KMS systems:

- schedule deletion,
- expose a pending deletion state,
- allow cancellation,
- execute only after the window expires and a final verification succeeds.

---

## 10. Audit and compliance posture

Every lifecycle step must be attributable and evidence-oriented.

### 10.1 Required audit coverage

Recommended audit events:

- key usage verification started
- key usage verification completed
- key marked drained
- decommission scheduled
- decommission cancelled
- decommission execution started
- decommission execution blocked
- key deleted

### 10.2 Recommended audit fields

Structured `event_details` should include:

- `schema_version`
- `flow` = `encryption_key_lifecycle`
- `key_id`
- `technical_status`
- `lifecycle_stage`
- `remaining_records`
- `remaining_targets`
- `verified_at`
- `reason`
- `decommission_window_days`
- `scheduled_by`
- `blocking_reasons`

### 10.3 Compliance posture

This strategy supports high-level SOC 2 expectations by making it possible to show:

- who initiated a lifecycle action,
- why it was done,
- what evidence justified it,
- what waiting period applied,
- whether the action was cancelled or completed,
- and that deletion could not proceed while active data still existed.

---

## 11. Position on secure export

Secure export of obsolete keys should **not** be part of the default workflow in V1.

Reasons:

- it adds complexity and a new secret-handling surface,
- it is not required to satisfy the primary operator problem,
- audit evidence and delayed deletion provide better value for the first version,
- symmetric at-rest keys are not naturally meaningful as a routine export artifact.

Future export support may be evaluated later for advanced enterprise scenarios, but it should remain
optional and isolated from the standard lifecycle.

---

## 12. Phased delivery plan

### Phase A - Lifecycle visibility

Goal: make obsolete keys visible before any deletion workflow exists.

Scope:

- add derived lifecycle stages,
- add verification summary fields to list and detail responses,
- expose `remainingRecords`,
- update Admin UI labels, help, and action logic,
- correct the current `Re-encrypt` ambiguity.

Expected outcome:

- operators can distinguish keys still in use from keys that are drained,
- no blind click-through is required to understand key reality.

### Phase B - Decommission scheduling

Goal: allow safe, auditable scheduling of obsolete key removal.

Scope:

- add schedule and cancel endpoints,
- persist decommission metadata,
- expose pending deletion in UI/API,
- add policy checks and blocking reasons.

Expected outcome:

- operators can prepare safe removal without immediate destruction.

### Phase C - Final deletion execution

Goal: complete the lifecycle with guarded final removal.

Scope:

- scheduler or controlled executor for final deletion,
- mandatory re-verification before execution,
- terminal audit trail,
- operator visibility into completion or failure.

Expected outcome:

- Ezkey supports a complete, auditable lifecycle for rotated encryption keys.

---

## 13. Actionable backlog

### 13.1 Product and UX

- Define the final English and French labels for `Drained` and `Pending deletion`.
- Redesign the encryption key list to include lifecycle evidence.
- Redesign the key detail dialog to show verification evidence and blockers.
- Update contextual help and operator copy.

### 13.2 Admin API

- Extend encryption key DTOs with lifecycle and verification fields.
- Add usage verification endpoint.
- Add decommission scheduling and cancellation endpoints.
- Add Problem Detail responses for blocked lifecycle transitions.

### 13.3 Core services

- Introduce `KeyUsageVerificationService`.
- Persist lifecycle verification snapshots.
- Add `KeyDecommissionService`.
- Refactor disablement logic so final lifecycle transitions use explicit verification rather than
  counter comparison alone.

### 13.4 Audit

- Define new audit actions and structured JSON payloads.
- Add lifecycle event labels to Admin UI audit localization.
- Ensure lifecycle evidence is SIEM-friendly.

### 13.5 Documentation

- Update `docs/ENDPOINT.md` for new lifecycle fields and endpoints.
- Update `docs/REENCRYPTION_OPERATIONS.md` to separate migration from lifecycle verification.
- Add operator guidance for drained keys and pending deletion.

---

## 14. Open points for review

1. Whether the final UI badge should be exactly `Drained` or `Drained key`.
2. Whether `Pending deletion` should continue to use technical status `ENABLED` underneath or
   introduce dedicated persisted metadata only.
3. Whether lifecycle verification snapshots should be refreshed only on demand or also periodically by
   scheduler.

---

## 15. References

- `ezkey-admin-ui/src/pages/encryption-keys.tsx`
- `ezkey-admin-ui/src/locales/en/encryption-keys.json`
- `ezkey-admin-ui/src/locales/en/help.json`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java`
- `ezkey-core/src/main/java/org/ezkey/security/KeyRotationService.java`
- `ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java`
- `ezkey-core/src/main/java/org/ezkey/security/EncryptionService.java`
- `docs/ENDPOINT.md`
- `docs/REENCRYPTION_OPERATIONS.md`
