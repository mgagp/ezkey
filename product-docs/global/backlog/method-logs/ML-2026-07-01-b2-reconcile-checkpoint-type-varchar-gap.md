# Method log — B2 reconcile failure: `checkpoint_type` VARCHAR(20) gap

## Metadata

- **Date:** `2026-07-01`
- **Program:** Wave B — `I-2026-0005` / PR #285
- **Symptom:** Admin UI reconcile dialog returns **400** — `Invalid data: constraint violation`
- **Environment:** clean-start Docker stack (37h uptime), induced entry HMAC tampering, nightly
  `AUDIT_INTEGRITY_RUPTURE` alert OPEN

## Reproduction (maintainer)

1. Tamper an audit log row (entry HMAC mismatch).
2. Nightly batch raises `AUDIT_INTEGRITY_RUPTURE` (alert id=1, OPEN).
3. Open alert → Reconcile dialog → category `INVESTIGATED_BENIGN`, justification
   `QA testing of seal and gap declaration procedures` → **Reconcile** fails.

## Root cause (confirmed)

**PostgreSQL:** `ERROR: value too long for type character varying(20)` (`SQLState: 22001`)

**Cause:** B2 inserts `checkpoint_type = 'MANIPULATION_CONCILIATION'` (25 characters) into
`ezkey_audit_chain_checkpoint.checkpoint_type`, defined as **`VARCHAR(20)`** in
`V6__audit_integrity_and_chain_checkpoints.sql`. JPA entity
`AuditChainCheckpoint` mirrors `length = 20`.

Existing types fit: `REGULAR` (7), `ARCHIVE_SEAL` (12), `GAP_DECLARATION` (16).

Admin API log sequence (`2026-07-01T13:05:44Z`):

```
AuditLifecycleService: Removed 287 REGULAR checkpoint(s) in rupture window [2026-06-30T02:00Z to 2026-07-01T01:55Z)
HHH000247: ErrorCode: 0, SQLState: 22001
ERROR: value too long for type character varying(20)
```

HTTP mapping: `DataIntegrityViolationException` → `ValidationExceptionHandler` → sanitized
**"Invalid data: constraint violation"** (no schema detail to client).

## Database state after failure

| Check | Result |
|-------|--------|
| Alert `AUDIT_INTEGRITY_RUPTURE` | Still **OPEN** (id=1) |
| Checkpoints in rupture window | **287 REGULAR** still present (transaction **rolled back**) |
| `MANIPULATION_CONCILIATION` rows | **0** (none inserted) |
| Meta-event `AUDIT_INTEGRITY_RUPTURE_CONCILIATED` | Not written |

Transactional boundary on `reconcileIntegrityRupture` prevented partial chain damage.

## Fix (applied 2026-07-01 on PR #285 branch)

1. **Flyway V14:** `checkpoint_type` widened to `VARCHAR(40)`.
2. **JPA:** `AuditChainCheckpoint` column `length = 40`.
3. **Test:** `reconcileIntegrityRupture_whenValid` asserts saved type `MANIPULATION_CONCILIATION`.

Redeploy admin-api (or `clean-start`) required for existing stacks so Flyway applies V14.

## Relation to B2.5

Orthogonal to investigation operability TB; blocks **resolution** path until schema fix lands.
Recommend **fix on PR #285 branch** (or immediate follow-up commit) before merge.

## Links

- B2 TB: [`../TB-2026-06-28-manipulation-integrity-remediation.md`](../TB-2026-06-28-manipulation-integrity-remediation.md)
- Migration origin: `ezkey-core/.../V6__audit_integrity_and_chain_checkpoints.sql`
- Service: `AuditLifecycleService.reconcileIntegrityRupture`
