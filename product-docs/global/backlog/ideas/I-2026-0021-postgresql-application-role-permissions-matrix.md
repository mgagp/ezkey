# Backlog Idea — `I-2026-0021` PostgreSQL application role and table permissions matrix

## Metadata

- **ID:** `I-2026-0021`
- **Status:** `done`
- **Priority:** `P1`
- **Created at:** `2026-05-19`
- **Updated at:** `2026-07-18`
- **Last reviewed at:** `2026-07-18`
- **Matrix:** [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)
- **Tracer bullet:** [`TB-2026-07-16-postgresql-application-role-split`](../TB-2026-07-16-postgresql-application-role-split.md)
- **Phase tags:** `P2-hardening`
- **Component tags:** `infra`, `core`, `admin-api`, `auth-api`, `integration-api`, `audit`
- **Captured by:** Marc

## Intent

Produce a global analysis (decision tables + recommendations) of PostgreSQL roles and per-table permissions for Ezkey, then implement the minimum credible hardening for R1 credibility. Today a single application role can read, insert, update, and delete on all tables including `audit_log`; that undermines the integrity story when manipulation is a first-class threat (`I-2026-0005`). This effort is the data-layer counterpart to lifecycle governance analysis: one structured pass, explicit decisions, bounded implementation.

## Problem and value

- **Problem:** Application code discipline alone cannot prove that audit rows are immutable at the database boundary. A mistaken migration script, compromised credential, or direct DBA session using the app role can delete or alter audit/checkpoint/incident data — exactly the scenarios the integrity rupture model must detect and remediate.
- **Expected value:** Normative credibility for operators and reviewers; reduced blast radius; clear separation between schema migration (DDL) and runtime application (DML); optional per-backend role split without accidental complexity.

## Scope

- **In scope — analysis deliverable:**
  - Inventory tables (at minimum: `audit_log`, checkpoint/chain tables, incident/alert resolution tables; extend to full schema).
  - Matrix dimensions: **table** × **operation** (SELECT, INSERT, UPDATE, DELETE, DDL) × **role** × **backend** (admin-api, auth-api, integration-api, crypto-api if applicable).
  - Recommended roles at minimum:
    - `ezkey_migration` (or equivalent) — Flyway / `ezkey-cli` migration only; DDL + structural changes.
    - `ezkey_app` baseline — runtime default; tightened per table.
  - Evaluate per-backend roles (e.g. `ezkey_admin_app`, `ezkey_auth_app`, `ezkey_integration_app`) — recommended if the matrix shows materially different DML needs without undue deployment cost.
  - Example rule: `audit_log` — runtime roles get **SELECT + INSERT + UPDATE** (UPDATE required for `AuditLogService` HMAC seal after identity assign); **DELETE only for `ezkey_admin`** (lifecycle purge). Auth/integration must not DELETE.
  - Observations, risks, phased implementation order, Docker/clean-start credential wiring notes.
- **In scope — implementation (follow-on, may be TB):**
  - Flyway or init scripts defining roles and grants.
  - Spring datasource configuration per backend module.
  - Verification that reconciliation/conciliation flows still work with constrained grants.
- **Out of scope:**
  - Row-level security (RLS) policies — evaluate in analysis, implement only if clearly justified.
  - Multi-tenant DB isolation — separate topic.
  - Replacing Postgres — not in question.

## Key assumptions

- Analysis precedes implementation (same pattern as `docs/LIFECYCLE_GOVERNANCE.md` style global pass).
- Manipulation detection remains necessary even with DB hardening — defense in depth, not either/or.
- R1 can ship incremental grants (audit table first) if full matrix implementation is too large for one slice.

## Risks and exceptions

- Over-segmentation (too many roles) increases deployment and secret-management cost — align with `Design Principle #2`.
- Conciliation/remediation flows may require controlled UPDATE on specific non-audit tables; matrix must document exceptions explicitly.
- Local dev and clean-start must remain frictionless after role split.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (C8).

## Promotion notes

Matrix delivered: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md).
Promoted to [`TB-2026-07-16-postgresql-application-role-split`](../TB-2026-07-16-postgresql-application-role-split.md) (single-pass: migrate role + three runtime roles + audit immutability grants + Docker wiring).

Closed 2026-07-18 after clean-start, explicit grant verification, standard and elective functional
tests, and representative real-mobile pending/respond validation. Residual hardening was captured
separately rather than holding this idea open: alert retention/purge and Admin-first keyset
bootstrap remain P3; the audit HMAC single-INSERT option remains an explicit TB follow-up.

## Links

- Surfaced during: [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (C8, 2026-05-19)
- Related backlog: `I-2026-0005` (integrity rupture remediation), `I-2026-0006` (validation batch)
- Related vision: `V-2026-0004` (integrity strategy)
- Comparable effort: `docs/LIFECYCLE_GOVERNANCE.md` (entity lifecycle global analysis pattern)
- Matrix: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)
- TB: [`TB-2026-07-16-postgresql-application-role-split`](../TB-2026-07-16-postgresql-application-role-split.md)
- Downstream (P3): [`I-2026-07-17-alert-resolved-retention-purge`](I-2026-07-17-alert-resolved-retention-purge.md) — no DELETE on `ezkey_alert` is intentional; future retention/purge of aged `RESOLVED` rows
- Downstream (P3): [`I-2026-07-17-keyset-blob-admin-first-bootstrap`](I-2026-07-17-keyset-blob-admin-first-bootstrap.md) — peripheral INSERT/UPDATE on `ezkey_keyset_blob` is accidental shared startup; Admin-first readiness before SELECT-only
