---
name: key lifecycle strategy
overview: Define a high-level product and architecture strategy for the lifecycle of rotated encryption keys, starting from the current Admin UI and backend model. The plan will clarify how to identify keys with zero remaining protected records, how to represent them in UX and API contracts, and how to frame a safe future decommissioning path with audit evidence.
todos:
  - id: baseline-lifecycle
    content: Document the current UI and backend lifecycle model and isolate the main ambiguity around drained-but-still-enabled keys.
    status: pending
  - id: truth-model
    content: Define the high-level source-of-truth strategy for proving that a key has zero remaining protected records.
    status: pending
  - id: ux-lifecycle
    content: Design the conceptual Admin UI lifecycle states, actions, labels, help, and operator evidence model.
    status: pending
  - id: api-domain-shape
    content: Outline the backend/API/service evolution needed to support lifecycle visibility and safe future decommissioning.
    status: pending
  - id: policy-audit
    content: Recommend a decommissioning policy with SOC 2-friendly audit evidence, delayed deletion guard rails, and an explicit position on secure export.
    status: pending
isProject: false
---

# Encryption Key Lifecycle Strategy

## Current baseline

- The current lifecycle implemented in `[C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/domain/entity/EncryptionKey.java](C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/domain/entity/EncryptionKey.java)` is `PENDING -> PRIMARY -> ENABLED -> DISABLED`.
- The Admin UI at `[C:/github/ezkey/ezkey-admin-ui/src/pages/encryption-keys.tsx](C:/github/ezkey/ezkey-admin-ui/src/pages/encryption-keys.tsx)` exposes `Re-encrypt` on `ENABLED` keys, but does not tell operators when an `ENABLED` key has already been fully drained.
- The backend truth for "remaining usage" currently lives mainly in re-encryption target scans inside `[C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java](C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java)`, while the UI surfaces `recordsEncrypted` / `recordsReencrypted`. That creates a strategic ambiguity because those counters are not currently a sufficiently strong source of truth for lifecycle decisions.
- Operational and compliance references point toward a staged model: identify safe retirement conditions, preserve audit evidence, then use a delayed and cancellable deletion step rather than immediate irreversible removal.

## Strategic workstreams

### 1. Define the product lifecycle model

- Establish the target vocabulary for keys after re-encryption is complete but before final removal.
- Compare naming options such as `Drained`, `Sealed`, `Retired`, or `Eligible for Decommission` against the current model and the existing audit-log "seal" concept, so terminology stays intuitive and does not overload unrelated concepts.
- Produce a state model that separates:
  - cryptographic role for new writes,
  - decryption fallback role,
  - proven zero remaining records,
  - scheduled decommission,
  - final removal.
- Decide whether the new concept is:
  - a new explicit domain status,
  - a computed derived state shown in UI/API,
  - or a decommissioning sub-status layered on top of `ENABLED` / `DISABLED`.

```mermaid
flowchart LR
  pending[Pending] --> primary[Primary]
  primary --> enabled[Enabled]
  enabled --> drained[DrainedOrSealed]
  drained --> pendingDeletion[PendingDeletion]
  pendingDeletion --> removed[Removed]
```



### 2. Define the trustworthy "zero remaining records" signal

- Audit the current proof model in `[C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java](C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java)`, `[C:/github/ezkey/docs/REENCRYPTION_OPERATIONS.md](C:/github/ezkey/docs/REENCRYPTION_OPERATIONS.md)`, and the encryption key list/detail contracts documented in `[C:/github/ezkey/docs/ENDPOINT.md](C:/github/ezkey/docs/ENDPOINT.md)`.
- Define the product decision rule for "this key is dead/obsolete": a key must be marked as such only when all tracked encrypted targets confirm zero remaining ciphertext for that key.
- Evaluate the backend options at high level:
  - live SQL scans on known re-encryptable targets,
  - a maintained usage snapshot/service,
  - or a hybrid model combining fast summary data with explicit verification before lifecycle transitions.
- Include the operational edge cases that must be guarded: new concurrent writes during migration, missing target registration, failed batches, and false zero signals.

### 3. Redesign the Admin UI operator experience

- Redefine the intent of the key list in `[C:/github/ezkey/ezkey-admin-ui/src/pages/encryption-keys.tsx](C:/github/ezkey/ezkey-admin-ui/src/pages/encryption-keys.tsx)` from a pure status table to a lifecycle and actionability surface.
- Plan UI changes conceptually for:
  - a distinct badge or lifecycle label for drained keys,
  - clear action transitions such as `Re-encrypt`, `Verify usage`, `Schedule decommission`, `Cancel decommission`,
  - operator-safe tooltips and contextual help,
  - explicit evidence in the detail panel: remaining records, last verification time, targets checked, audit reason.
- Update supporting product copy in `[C:/github/ezkey/ezkey-admin-ui/src/locales/en/encryption-keys.json](C:/github/ezkey/ezkey-admin-ui/src/locales/en/encryption-keys.json)`, `[C:/github/ezkey/ezkey-admin-ui/src/locales/fr/encryption-keys.json](C:/github/ezkey/ezkey-admin-ui/src/locales/fr/encryption-keys.json)`, and help content tied to the page.
- Resolve the current UX inconsistency where the list only offers `Re-encrypt` for `ENABLED`, while the detail dialog currently allows it for any non-`PRIMARY`, non-`DISABLED` key.

### 4. Frame the API and backend evolution

- Identify the minimum API additions or contract changes needed so the UI can explain lifecycle truth without forcing operators to click blindly.
- Assess whether the Admin API should expose, per key:
  - remaining-record count,
  - lifecycle evidence status,
  - decommission eligibility,
  - scheduled deletion metadata,
  - and blocking reasons.
- Map likely touchpoints without implementing yet:
  - `[C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java](C:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java)`
  - `[C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/KeyRotationService.java](C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/KeyRotationService.java)`
  - `[C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java](C:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java)`
- Include whether this should remain inside existing services or justify a dedicated lifecycle/orchestration service for verification and decommission workflows.

### 5. Define decommissioning policy and audit posture

- Separate this work into two product phases:
  - Phase A: identify and represent obsolete keys safely.
  - Phase B: decommission them with guard rails.
- For Phase B, evaluate two policy options:
  - soft decommission only: disable and retain in-system with audit evidence,
  - controlled removal: scheduled deletion after a waiting period, with cancellation and full audit trail.
- Treat secure export as an option to evaluate, not a default assumption. The plan should clarify when export adds real value versus unnecessary complexity for symmetric at-rest keys already preserved in audit history.
- Align the design with auditable actions, reasons, and evidence expected in a SOC 2-oriented process: who initiated, why, what proof established zero usage, when deletion became eligible, when it was scheduled, and whether it was cancelled or completed.

## Expected outputs of this work

- A recommended lifecycle model and naming set for encryption keys after rotation.
- A UX strategy for making obsolete keys visible and actionable in the Admin UI.
- A backend/API strategy for producing reliable evidence that a key no longer protects active records.
- A policy recommendation on decommissioning, including whether to keep deletion delayed/cancellable and whether secure export is justified.
- Documentation updates to embed operator guidance in product help and repo docs, especially `[C:/github/ezkey/docs/ENDPOINT.md](C:/github/ezkey/docs/ENDPOINT.md)` and `[C:/github/ezkey/docs/REENCRYPTION_OPERATIONS.md](C:/github/ezkey/docs/REENCRYPTION_OPERATIONS.md)`.

