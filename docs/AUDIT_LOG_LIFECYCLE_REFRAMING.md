# Audit Log Lifecycle Reframing

> **Historical (2026-04).** Session-start reframing. The opinionated lifecycle shipped. Living
> canon: [`ezkey-core/CONFIGURATION.md`](../ezkey-core/CONFIGURATION.md) § Audit Log Archive,
> [`docs/AUDIT_LOG_INTEGRITY.md`](AUDIT_LOG_INTEGRITY.md) (FSM + exceptional `seal-archive`).
> Remaining export work is [`I-2026-06-28`](../product-docs/global/backlog/ideas/I-2026-06-28-audit-archive-export-spi.md).
> Do not treat this file as an implementation kickoff.

## Purpose

This document captures the conceptual shift between the earlier audit-log lifecycle posture and the
new opinionated posture now preferred for Ezkey.

It is intended to serve as the clean starting point for a new implementation session.

---

## 1. Previous Posture

The previous posture, as reflected across the lifecycle planning documents, converged toward a
mixed model:

- chain checkpoints and lifecycle states were introduced as the ideal path,
- but the older date-based deletion model was still kept alive as a legitimate fallback,
- manual SEAL remained part of the product surface,
- export and purge were added on top of the existing retention-cleanup model,
- the system still conceptually tolerated two deletion philosophies:
  - legacy retention cleanup,
  - lifecycle-driven archival and purge.

In practice, that posture tried to be pragmatic and migration-friendly, but it preserved
accidental complexity:

- dual mental models for deletion,
- dual operator stories,
- extra configuration to govern whether simple delete is allowed,
- UI and API surfaces that still exposed manual lifecycle operations as first-class actions.

This was a reasonable transition posture, but it is no longer aligned with the desired Ezkey
product philosophy.

---

## 2. New Posture

The new posture is intentionally opinionated:

- Ezkey is secure by default.
- Audit logs always participate in the tamper-evident model.
- Chain checkpoints are not optional operational garnish; they are part of the normal product
  contract.
- Audit-log deletion must happen only through the checkpoint lifecycle.
- The legacy simple retention-delete path should no longer remain as a parallel supported model.
- Lifecycle policy is system-owned configuration, not a manual operator workflow.

The result is a single conceptual model:

- audit logs are written,
- checkpoints are created continuously,
- eligible checkpoint ranges are sealed automatically according to lifecycle policy,
- sealed data may then be exported or may skip export depending on policy,
- only lifecycle-promoted data becomes purgeable,
- only purgeable data is physically deleted,
- checkpoint rows remain for traceability and normative evidence.

This is the product model. There is no second-class deletion mode beside it.

---

## 3. Core Product Principles After Reframing

### 3.1 One lifecycle, not two deletion mechanisms

Deletion is no longer framed as:

- either retention cleanup,
- or lifecycle-driven purge.

Instead, deletion is always the last step of the lifecycle.

### 3.2 SEAL is system-owned, not operator-owned

The earlier posture still left room for humans to think in terms of "SEAL this range manually".
The new posture removes that from the core product story.

Humans own policy.
The system owns lifecycle execution.

### 3.3 Admin UI should expose observability, not low-level lifecycle surgery

The Admin UI should help operators:

- inspect checkpoint states,
- understand what is happening,
- trigger export-related workflows if applicable,
- investigate anomalies.

It should not center the operator around choosing arbitrary `seal from / seal to` ranges as a
routine product behavior.

### 3.4 Configuration is platform-level policy

Lifecycle policy belongs to system configuration, not to mutable admin actions.

That includes:

- retention horizon,
- SEAL granularity,
- export enablement,
- export-to-purge grace period,
- final purge cadence.

This policy should not be modifiable by a Global Admin in the normal Admin UI.

---

## 4. Old vs New Summary

| Topic | Previous posture | New posture |
|---|---|---|
| Deletion model | Two coexisting models: legacy cleanup and lifecycle purge | One model only: lifecycle-driven purge |
| Chain mode | Strongly recommended but coexistence with non-chain delete remained | Part of the product contract; not a side mode |
| Manual SEAL | Still present as meaningful operator action | Removed from normal product workflow |
| Retention cleanup | Date-based cleanup still structurally present | Replaced by lifecycle progression |
| UI posture | Mixed: observability + manual lifecycle operations | Observability-first; lifecycle execution mostly automatic |
| Export role | Added as a later lifecycle capability | Optional step within the single lifecycle model |
| Configuration | Partly policy, partly legacy compatibility | Clearly system-owned lifecycle policy |
| Product philosophy | Transitional and migration-friendly | Opinionated, security-first, simpler by design |

---

## 5. Clarifying the `EXPORTED -> PURGEABLE` Transition

This is the main point that needed clarification.

`EXPORTED` and `PURGEABLE` should remain distinct because they answer different questions:

- `EXPORTED` means Ezkey has evidence that the sealed audit data was successfully retrieved by the
  external archival workflow.
- `PURGEABLE` means Ezkey is now allowed to delete the live audit-log rows from the primary store.

Those are not the same event.

The value of the separation is the safety buffer between:

- successful export,
- and destructive deletion.

That buffer exists to reduce operational risk:

- export may have succeeded technically but the downstream storage move may still be incomplete,
- an operator may need time to validate the archive,
- external storage may have eventual-consistency or replication lag,
- the organization may want a cooling-off period before destructive purge.

So the recommended rule is:

- `SEALED -> EXPORTED`: occurs when the archival workflow has successfully retrieved the sealed
  data and Ezkey records that success.
- `EXPORTED -> PURGEABLE`: occurs only after a policy-defined safety delay has elapsed after the
  export confirmation.
- `PURGEABLE -> PURGED`: occurs when the purge batch deletes the underlying audit-log rows and
  leaves checkpoint metadata intact.

This makes `PURGEABLE` the system's internal authorization-to-delete state, not merely a synonym
for "already exported".

---

## 6. Optional No-Long-Term-Archive Variant

The reframed model also supports a simpler policy variant:

- the organization keeps live audit logs for a long retention period,
- but does not want external long-term archival storage.

In that case, `EXPORTED` should not disappear from the conceptual model entirely, but it should be
treated as an optional state that policy can skip.

Recommended interpretation:

- if external archival is enabled:
  - `ACTIVE -> SEALED -> EXPORTED -> PURGEABLE -> PURGED`
- if external archival is disabled:
  - `ACTIVE -> SEALED -> PURGEABLE -> PURGED`

This is still one lifecycle model, not two deletion mechanisms.

The difference is only whether the export stage is enabled by policy.

That distinction is important:

- disabling export does not bring back legacy simple-delete,
- it only short-circuits one state transition inside the lifecycle FSM.

---

## 7. Consequences for API and UI

### API

- Manual `seal-archive` should no longer be treated as the normal product path.
- Export remains useful, but primarily as a system-integrated workflow for the Ezkey CLI.
- Confirmation of archival remains meaningful only when export is enabled.
- Purge should be driven by lifecycle state selectors, never by plain age-only deletion logic.

### Admin UI

- Remove the routine operator workflow around selecting arbitrary manual SEAL ranges.
- Keep checkpoint visibility and lifecycle-state visibility.
- Add simple operator-facing surfaces only where needed:
  - export status,
  - archive confirmation,
  - purge status,
  - anomaly visibility.

The UI becomes simpler because the product stops exposing internal lifecycle mechanics as manual
tools.

---

## 8. Consequences for Implementation

This reframing implies the following implementation direction:

- keep the checkpoint lifecycle FSM,
- keep checkpoint metadata for seal/export/purge traceability,
- keep export-oriented APIs and CLI integration direction,
- remove the legacy chain-off/simple-delete posture from the target design,
- remove or demote manual SEAL as a product workflow,
- replace retention cleanup semantics with lifecycle-promotion semantics,
- make purge selectors depend on lifecycle state rather than raw age alone.

The most important cleanup is conceptual rather than purely mechanical:

- one lifecycle,
- one deletion story,
- one operator narrative.

---

## 9. Recommended Baseline for the Next Session

The next implementation session should start from these explicit assumptions:

1. There is no supported legacy deletion mode in the target design.
2. The checkpoint lifecycle is the only valid route to physical deletion.
3. Automatic SEAL is part of the normal product behavior.
4. Manual SEAL is not a first-class product workflow.
5. Export is policy-controlled and may be skipped when external archival is disabled.
6. `PURGEABLE` exists to represent explicit authorization for destructive deletion, not merely
   successful export.
7. Checkpoint rows are retained after purge for forensic and normative continuity.

These assumptions should be treated as settled unless a strong product reason reopens them.