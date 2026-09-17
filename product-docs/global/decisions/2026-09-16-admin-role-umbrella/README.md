# Decision pack — Admin `ROLE_ADMIN` umbrella (keep vs challenge)

## Metadata

- **Date:** 2026-09-16
- **Status:** proposed (decision pack for Marc / Patrick)
- **Scope:** Admin API human authorization model (Global Admin vs Tenant Admin)
- **Related ADR:** [ADR-0013](../../architecture-decisions.md#adr-0013-admin-role-admin-umbrella-keep-vs-split)
  (status `proposed`)
- **Prior hygiene settlement:** Path A keep-umbrella (2026-08-20) in
  [`../hygiene/java-controller-role-validation/2026-08-16-pass-1.md`](../hygiene/java-controller-role-validation/2026-08-16-pass-1.md)
  (`CTRL-ROLE-002` / `002b`)
- **Non-goals of this pack:** production Java redesign, Fred hygiene campaigns, deploy changes

## Why this pack exists

Ezkey grants a generic Spring authority `ROLE_ADMIN` to every authenticated human admin, then
adds `ROLE_GLOBAL_ADMIN` or `ROLE_TENANT_ADMIN`. Shared operator controllers often gate with
`@PreAuthorize("hasRole('ADMIN')")`. That umbrella was challenged (service-level role annotations
look safe but do not isolate tenants; SEC-017 showed Global-only surfaces can fail open when only
the umbrella is present).

Hygiene pass 2026-08 already **documented** the model and settled Path A (keep issuing
`ROLE_ADMIN`). This pack reopens the question as a **product / architecture decision** for Marc
and Patrick: keep and harden discoverability, split authorities greenfield-style, or hybrid /
phased — with distance, characterization tests, and a draft verdict.

## Artefacts

| File | Role |
|------|------|
| [`inventory-status-quo.md`](inventory-status-quo.md) | How the umbrella works today; failure modes; what is documented vs tribal |
| [`decision-brief.md`](decision-brief.md) | Options A / B / C, test preamble, phasing, recommended verdict draft |

## Recommended next step for Patrick

1. Read the inventory (skimmable tables + cited paths).
2. Decide on the brief’s **Option A / B / C** (draft leans **C → execute A now**).
3. Either **accept ADR-0013** (edit judgment in place) or request a short grill on one contested
   point (usually: whether annotation honesty is worth a funded peel).
4. Do **not** start a filter / authority remodel until the test preamble gaps are funded if Option
   B is chosen.

## Provenance (already in the tree)

- `ezkey-admin-api/AGENTS.md` § Authorization at controllers (canon for cold agents)
- `docs/API_SECURITY_MATRIX.md` (role story corrected 2026-08-20; endpoint tables lag)
- `docs/java-controller-role-validation-assessment-2026-08.md` (CTRL-ROLE-001…005)
- `docs/SECURITY_CHALLENGE_REPORT_2026-07.md` (SEC-017 encryption keys)
- Companion open PR context: tenant SQL isolation assessment ([PR #539](https://github.com/mgagp/ezkey/pull/539)) — object / SQL dialects, not the Spring role umbrella itself
