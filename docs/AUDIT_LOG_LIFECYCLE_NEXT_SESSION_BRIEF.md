# Audit Log Lifecycle — Next Session Brief

## Mission

Reprendre l'implémentation du lifecycle des audit logs avec un cadrage propre, aligné sur une
philosophie unique et opinionated:

- Ezkey est secure by default.
- Les audit logs participent toujours au modèle de traçabilité et de liaison cryptographique.
- Il n'existe plus deux mécanismes concurrents de suppression.
- La suppression physique des audit logs ne peut se faire que via le lifecycle des checkpoints.
- Le système exécute automatiquement les transitions de lifecycle selon une policy de
  configuration.
- Le Global Admin n'orchestre pas manuellement le SEAL comme workflow produit normal.

Le but de la nouvelle session n'est pas de prolonger le compromis précédent. Le but est de
converger vers le modèle final simplifié.

---

## Product Decisions To Treat As Settled

1. Le seul mécanisme légitime de suppression est le lifecycle des checkpoints.
2. Le fallback legacy de suppression simple par âge ne fait plus partie du design cible.
3. Le chaînage cryptographique n'est pas un mode optionnel de luxe; il fait partie du contrat du
   produit.
4. Le SEAL devient un mécanisme piloté par policy et exécuté par le système.
5. L'Admin UI ne doit plus exposer un workflow central de type `seal from / seal to`.
6. `EXPORTED` et `PURGEABLE` restent distincts.
7. Le passage `EXPORTED -> PURGEABLE` représente une autorisation différée à la suppression après
   une fenêtre de sécurité.
8. Si l'archivage long terme externe est désactivé, on ne réintroduit pas le legacy delete:
   on short-circuite simplement l'étape `EXPORTED` dans le FSM.
9. Les lignes de checkpoints restent en base après la suppression des audit logs pour la
   traçabilité et la continuité normative.

---

## Canonical Lifecycle Model

### Standard variant with long-term archival enabled

`ACTIVE -> SEALED -> EXPORTED -> PURGEABLE -> PURGED`

Meaning:

- `ACTIVE`: checkpoints in the normal live chain.
- `SEALED`: historical tranche detached from the live mutable horizon and ready for next lifecycle
  steps.
- `EXPORTED`: archival workflow has successfully retrieved the sealed data.
- `PURGEABLE`: policy-defined safety delay has elapsed; live deletion is now authorized.
- `PURGED`: audit-log rows are deleted from the live store, checkpoint metadata remains.

### Variant without external long-term archival

`ACTIVE -> SEALED -> PURGEABLE -> PURGED`

This is not a second deletion mechanism.
It is the same lifecycle with the export step disabled by policy.

---

## Key Clarification About `EXPORTED -> PURGEABLE`

`EXPORTED` does not mean “safe to delete immediately”.

It means the external archival workflow successfully obtained the sealed data.

`PURGEABLE` is a separate internal authorization state used to express that:

- export success has already happened,
- any required safety delay has elapsed,
- destructive deletion from the primary store is now permitted.

This separation is intentional and should be preserved.

---

## Required Design Direction For The Next Session

The next session should implement or reshape the backend and plans around these principles:

- automatic SEAL based on lifecycle policy,
- lifecycle-driven export integration,
- lifecycle-driven purge eligibility,
- purge batch based on lifecycle selectors rather than raw age-only deletion,
- simplified Admin UI centered on observability and downstream export status rather than manual
  SEAL control,
- removal of code and docs that normalize the old dual deletion model.

---

## Keep / Delete / Rewrite

Use this strict classification when re-entering the codebase.

### Keep

These pieces are aligned enough with the new philosophy to remain as foundation material.

- `ezkey-core/src/main/java/org/ezkey/audit/integrity/CheckpointLifecycleState.java`
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainCheckpoint.java`
- `ezkey-core/src/main/java/org/ezkey/audit/exception/AuditLifecycleConflictException.java`
- `ezkey-admin-api/src/main/java/org/ezkey/exception/AuditLifecycleExceptionHandler.java`
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditArchiveProperties.java`
- `ezkey-core/src/main/resources/db/migration/V6__audit_integrity_and_chain_checkpoints.sql`
  for the checkpoint lifecycle columns already introduced
- DTO and mapper adjustments that expose lifecycle state on checkpoints, as long as they do not
  encode the old deletion posture
- `docs/AUDIT_LOG_LIFECYCLE_REFRAMING.md`

### Delete Or Intentionally Remove From The Target Design

These pieces represent the old dual-mode deletion posture and should not survive in the target
design unless a strong reason emerges.

- `ezkey.audit.retention.simple-delete-allowed` as a supported product concept
- the chain-off simple-delete fallback path
- documentation that presents simple date-based deletion as a legitimate parallel operating model
- Admin UI or API framing that preserves manual SEAL as a core product workflow

Concrete likely removals/refactors:

- `ezkey-core/src/main/java/org/ezkey/audit/service/AuditRetentionProperties.java`
  removed from the backend contract because it only served the legacy cleanup mode
- the chain-disabled branch in `ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java`
  removed so physical deletion is lifecycle-only
- any doc sections that explain “retention modes” as two supported product paths

### Rewrite

These areas are useful anchors but must be reframed before being treated as final design.

- `ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java`
  Continue refining the lifecycle-only purge contract.
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditLifecyclePurgeScheduler.java`
  Continue refining the lifecycle-aware purge executor that replaced the generalized retention
  cleanup job.
- `ezkey-core/CONFIGURATION.md`
  Rewrite property story so lifecycle policy is first-class and legacy simple-delete disappears.
- `docs/configuration/README.md`
  Align prefix registry with the new single-lifecycle posture.
- `.github/prompts/plan-auditLogLifecycleSealExportPurge.prompt.md`
  Rewrite to remove the remaining hybrid model language.
- any phase prompt that still treats manual SEAL or legacy deletion as part of the desired product
  posture
- tests that validate chain-off simple-delete behavior as acceptable behavior

---

## First Implementation Priorities In The New Session

Recommended order:

1. Normalize the plans so they express only the single-lifecycle model.
2. Remove legacy deletion posture from configuration and docs.
3. Refactor cleanup code so purge is exclusively lifecycle-based.
4. Recast SEAL as automatic policy-driven system behavior.
5. Preserve export and `confirm-archived` work only insofar as it fits the new FSM.
6. Simplify Admin UI/API expectations around manual lifecycle operations.

This order matters because it prevents the code from being re-shaped by outdated plan language.

---

## Explicit Anti-Goals For The New Session

Do not spend time preserving transitional product behavior just because it already exists in the
current generated code.

Specifically:

- do not optimize for coexistence of lifecycle purge and legacy cleanup,
- do not preserve manual SEAL as a first-class operator workflow for convenience,
- do not keep configuration flags whose sole purpose is to make the non-lifecycle deletion path
  acceptable,
- do not let temporary implementation history dictate the final product model.

---

## Suggested Opening Prompt For The Next Session

Use the following brief as the opening instruction:

"Reframe and implement the audit-log lifecycle around a single opinionated model. Ezkey must be
secure by default. There is no supported legacy simple-delete mode anymore. Physical deletion of
audit logs can only happen through checkpoint lifecycle progression. SEAL is policy-driven and
system-executed, not a normal manual operator workflow. Preserve the checkpoint FSM and existing
lifecycle columns where useful, remove or rewrite the retention cleanup fallback model, and align
the plans, config, backend behavior, and docs with the canonical lifecycle: ACTIVE -> SEALED ->
EXPORTED -> PURGEABLE -> PURGED, with the export step skippable by policy when external archival is
disabled." 

---

## Reference Documents

- `docs/AUDIT_LOG_LIFECYCLE_REFRAMING.md`
- `.github/prompts/plan-auditLogLifecycleSealExportPurge.prompt.md`
- `.github/prompts/plan-auditLogCivPhase2OrderedExportArchiveConfirmation.prompt.md`
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/CheckpointLifecycleState.java`
- `ezkey-core/src/main/resources/db/migration/V6__audit_integrity_and_chain_checkpoints.sql`