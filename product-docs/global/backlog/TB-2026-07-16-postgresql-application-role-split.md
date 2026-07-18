# Tracer Bullet Brief — `TB-2026-07-16` PostgreSQL application role split

## Metadata

- **ID:** `TB-2026-07-16-postgresql-application-role-split`
- **Status:** `done`
- **Related idea:** `I-2026-0021-postgresql-application-role-permissions-matrix`
- **Parent context:** Integrity defense-in-depth; parallel R1 hardening
- **Lane:** `C`
- **Posture:** `single-pass`
- **Created at:** `2026-07-16`
- **Updated at:** `2026-07-18`
- **Closed at:** `2026-07-18`
- **Captured by:** plan incubation → implementation

## Objective

Replace the single Docker `postgres` superuser used by Flyway and all APIs with:

1. `ezkey_migrate` for Flyway DDL
2. `ezkey_admin` / `ezkey_auth` / `ezkey_integration` for runtime DML per [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)

Include audit immutability at the DB boundary (no UPDATE/DELETE on `ezkey_audit_log` for auth/integration; DELETE only for admin purge).

## Boundaries in scope

- Matrix document under `docs/`
- `docker/postgres/init` role bootstrap (Bash)
- `scripts/db/create-roles.sh`, `apply-grants.sql`, `apply-grants.sh`, `verify-grants.sh`
- Docker Compose (main, HA, Lightsail): migrate + db-grants + per-API credentials
- Spring `application*.properties` / docker profile datasource defaults
- Partition function ownership / EXECUTE grant activation via apply-grants
- `OPERATIONAL.md` Database Security rewrite; docker `.env.example`; brief CONFIGURATION notes

## Out of scope

- Row-level security (RLS)
- Column-level grants
- Changing Java business logic
- Grafana/monitoring dedicated read-only role
- Production in-place upgrade of existing non-greenfield DBs (EXP1/clean-start volume recreate)

## Critical flows

1. Clean-start: init roles → Flyway as `ezkey_migrate` → apply-grants → APIs start with role credentials
2. Enrollment bind/verify (auth role) + auth-attempt create (integration/admin) + respond (auth)
3. Audit INSERT from all APIs; DELETE denied to auth/integration; UPDATE retained only for the
   current atomic two-step HMAC seal
4. Admin partition scheduler EXECUTE on `create_monthly_partition`

## Evidence

- Matrix doc accepted and linked from `I-2026-0021`.
- Implementation committed as `568f1423` and present on `main`.
- Clean-start stack completed with role bootstrap, Flyway as `ezkey_migrate`, grants step, and
  per-API credentials.
- `scripts/db/verify-grants.sh --docker` passed, including positive grants and forbidden-operation
  assertions.
- Standard functional suite passed under the segregated runtime roles.
- Elective suite passed: 15 tests, 0 failures/errors, 1 intentional archive-eligibility skip.
- Operator completed the representative real-mobile workflow successfully after the separate
  mobile ECDSA low-S correction: integration + enrollment, pending retrieval, and respond.

## Rollback

Revert compose/properties to `postgres` and recreate volume; grants scripts are additive and safe to drop with the volume.

## Follow-up analysis (open question — do not lose)

> **Promoted 2026-07-18:** this open question now lives as its own first-class backlog idea
> [`I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence`](ideas/I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence.md)
> (`incubating`, `P2`). The analysis below is preserved as the source signal; active design work
> continues in that idea.

**Trigger:** During implementation the audit-log grant had to be widened from the originally
imagined **INSERT-only** for `ezkey_auth` / `ezkey_integration` to **INSERT + UPDATE**, because
[`AuditLogService.log()`](../../../ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java)
seals each row with an HMAC in a **two-step save**: (1) `save()` to obtain the database-assigned
`audit_log_id`, then (2) `refresh` + `computeHmac` + `save()` to persist `entry_hmac`. The
`audit_log_id` must be part of the signed canonical form, so the UPDATE is intrinsic to the current
design. This weakens the "peripherals can only append, never mutate audit rows" story we wanted at
the DB boundary.

**Question to revisit:** can we restore **INSERT-only** for the peripheral roles (`ezkey_auth`,
`ezkey_integration`) by moving the HMAC-sealing UPDATE elsewhere, without losing integrity
guarantees or adding disproportionate complexity?

**Confirmed signal (2026-07-16):** the two-step save is **atomic** — both `save()` calls execute
inside a single `requiresNewTx.execute(...)` (`PROPAGATION_REQUIRES_NEW`). Insert and seal commit
or roll back together. This is a strong property: a row is either fully present *and* sealed, or
absent. Any redesign must preserve this all-or-nothing seal; losing it to chase INSERT-only purity
would be a bad trade.

**Options to weigh (keep under Ezkey value guardrails):**

1. **Keep current design (default / status quo).** UPDATE grant on `ezkey_audit_log` for peripheral
   roles is a narrow, well-understood exception (HMAC seal only, no business mutate path; DELETE
   still denied). Atomicity preserved. Lowest *change* cost. Pragmatic baseline unless a clearly
   superior option emerges.
2. **Externalize the primary key via an explicit sequence, then single INSERT with HMAC already
   computed (preferred candidate to validate).** Root cause of the forced UPDATE is that
   `audit_log_id` is currently assigned only at insert (`GENERATED ALWAYS AS IDENTITY` / equivalent).
   If the application obtains the next monotonic id from an explicit sequence *before* insert
   (`nextval`), it can: (a) allocate id, (b) compute HMAC including that id, (c) **INSERT once**
   with id + `entry_hmac` already set. No post-insert UPDATE. **Double pay-off:**
   - Independent of DB-role separation: sequence + one INSERT is simpler and cheaper than
     INSERT + refresh + UPDATE (removes most of the accidental complexity of the two-step path).
   - Combined with role separation: peripherals can drop to true **INSERT-only** on
     `ezkey_audit_log` (no UPDATE grant), restoring the original matrix intent — two birds,
     one stone.
   - Atomicity: keep allocate + HMAC + INSERT inside the same `REQUIRES_NEW` transaction so the
     sealed row still appears as all-or-nothing (unused sequence gaps on rollback are normal and
     acceptable for audit ids).
   **To validate:** Hibernate / JPA mapping for assigned ids on a partitioned table; grants for
   `USAGE` on the sequence for all inserting roles; greenfield migration shape; no regression in
   HMAC canonical form or integrity verification. Complexity of owning a sequence is low — likely
   the best balance if validation holds.
3. **DB trigger computes/writes the seal.** Would let peripheral roles be INSERT-only. **Caveat:**
   if the trigger must *compute* the HMAC, that delegates a significant cryptographic
   responsibility into the database — undesirable a priori. Only worth analyzing if the HMAC can be
   produced without moving key material / crypto logic into PostgreSQL (e.g. a vetted extension or
   a Java-implemented callback via a PL bridge). Higher accidental complexity than option 2.
4. **Admin-api performs the sealing UPDATE (batch or event).** Admin-api already iterates for
   checkpoint chaining, so a sealing pass could piggyback there. **Caveat:** this introduces a
   window between insert and seal during which a manipulation that is *currently* detected might no
   longer be — a real increase in risk, plus added moving parts. Likely not worth it just to keep
   INSERT-only; option 2 is preferable if it works.

**Guardrail:** aim for virtue, not dogma. INSERT-only for peripherals is desirable, but forcing it
at the cost of atomicity, DB-side crypto responsibility, or a detection-gap window would be an
escalation-of-commitment to a design ideal. Option 2 is interesting precisely because it may
*reduce* accidental complexity while also tightening grants — validate that before choosing status
quo out of habit. Balance the SQL-role security objective against essential and accidental
complexity; prefer the pragmatic middle ground.

**Broader avenue this opens:** more generally, if other permission-tightening opportunities surface,
treat them as a prompt to re-examine how responsibilities and trust boundaries are drawn *between*
the backends — not only which grants each role holds. Strengthening those inter-backend boundaries
is the underlying goal of this whole effort; some boundaries may deserve a second look. Same balance
exercise applies (security gain vs complexity). If this theme accumulates more than the single
audit-seal case, promote it to a dedicated `I-*`.

## Related capture (not blocking this TB)

**Alert table growth:** `ezkey_alert` has no DELETE by design (resolve = UPDATE). That intentional
gap implies a future retention/purge need for aged `RESOLVED` rows — captured as P3
[`I-2026-07-17-alert-resolved-retention-purge`](ideas/I-2026-07-17-alert-resolved-retention-purge.md).
Out of scope for this TB and for R1.

**Keyset blob shared writer:** `ezkey_keyset_blob` grants INSERT/UPDATE to auth and integration
because shared `TinkKeyManager` startup can upsert when the DB blob is empty. Desired end-state is
Admin-first materialization + peripheral readiness wait, then SELECT-only for peripherals —
captured as P3
[`I-2026-07-17-keyset-blob-admin-first-bootstrap`](ideas/I-2026-07-17-keyset-blob-admin-first-bootstrap.md).
Do not tighten grants before that readiness contract exists.

## Closeout

- **Outcome:** delivered. DDL ownership and runtime DML are separated through `ezkey_migrate`,
  `ezkey_admin`, `ezkey_auth`, and `ezkey_integration`; Docker variants and application defaults
  use the intended credentials; the canonical table-operation matrix and verification tooling are
  available.
- **Traceability sync:** no row was added to
  [`../spec-test-traceability.md`](../spec-test-traceability.md) or component feature matrices.
  This slice changes deployment/database trust boundaries, not an API or user-visible feature
  contract. The matrix, this TB, operational documentation, grant verification, functional suites,
  and mobile smoke are the proportionate evidence chain.
- **Deferred items:** peripheral audit-log UPDATE removal
  ([`I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence`](ideas/I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence.md),
  `incubating` P2 as of 2026-07-18) and Admin-first keyset/key-metadata bootstrap are separate
  hardening analyses; alert retention/purge is a separate P3 lifecycle idea. None blocks the
  delivered role split.
- **Residual risk:** application credentials still have the documented exceptions required by
  current shared services (audit HMAC seal; keyset bootstrap). PostgreSQL superuser/DBA access
  remains outside application-role containment, as expected.
- **Next action:** reopen through the linked P3 ideas or a dedicated TB only when those hardening
  slices are prioritized; no scheduled review is required for this completed TB.
- **GitHub issue posture:** canon is sufficient. No retroactive issue was opened because the work
  is already committed on `main`, has no pending PR/coordination need, and the durable evidence is
  linked here.
