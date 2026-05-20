# Backlog Idea — `I-2026-0021` PostgreSQL application role and table permissions matrix

## Metadata

- **ID:** `I-2026-0021`
- **Status:** `captured`
- **Priority:** `P1`
- **Created at:** `2026-05-19`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P2-hardening`
- **Component tags:** `infra`, `core`, `admin-api`, `auth-api`, `integration-api`, `audit`

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
  - Example rule already identified: `audit_log` — application roles get **SELECT + INSERT only**; no UPDATE/DELETE for runtime app roles.
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

## Promotion notes

Move to `triaged` after analysis charter is accepted. Move to `incubating` when the matrix document exists under `docs/` or `product-docs/components/`. Promote to `TB-*` for first implementation slice (audit_log immutability grants + migration role split).

## Links

- Surfaced during: [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (C8, 2026-05-19)
- Related backlog: `I-2026-0005` (integrity rupture remediation), `I-2026-0006` (validation batch)
- Related vision: `V-2026-0004` (integrity strategy)
- Comparable effort: `docs/LIFECYCLE_GOVERNANCE.md` (entity lifecycle global analysis pattern)
