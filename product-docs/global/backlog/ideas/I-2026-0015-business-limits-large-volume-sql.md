# Backlog Idea — `I-2026-0015` Business limits on potentially large-volume SQL queries

## Metadata

- **ID:** `I-2026-0015`
- **Status:** `triaged`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`, `repositories`

## Intent

Introduce explicit business limits at the repository layer for SQL queries with high potential volume, so that legitimate but abusive callers cannot trigger denial-of-service-like behavior simply by repeatedly invoking endpoints that fetch unbounded result sets. Use SQL `LIMIT` clauses with externalized configuration where appropriate, so the cap is visible, tunable, and documented.

## Problem and value

- **Problem:** Some SQL queries supporting list operations or analytical lookups can return very large result sets if invoked without explicit limits. This creates two risks: (a) abuse via repeated invocation that consumes disproportionate database and API resources, and (b) accidental overload from a legitimate caller (or buggy SDK) requesting more than they need. Today, there is no explicit business-limit policy at the repository layer to cap such queries.
- **Expected value:** Predictable bound on query cost; reduced abuse surface beyond rate limiting (which limits frequency but not per-call cost); honest documentation of the cap so SDK and operator behavior aligns with the platform's expectations. Aligns with `Design Principle #12` (security as posture).

## Scope

- **In scope:**
  - Inventory repository methods with high-volume potential (no `LIMIT`, large table base, no obvious upstream pagination).
  - Define a business-limit policy: per-query `LIMIT` value, externalized configuration where applicable.
  - Apply the limit at the repository layer for the identified methods.
  - Document the limit and the rationale in component docs.
  - Decide whether a structured review pattern (or a future skill) should periodically check that new repository methods comply.
- **Out of scope:**
  - Replacing existing pagination contracts (covered by `docs/PAGINATION_GUIDELINES.md`).
  - Per-tenant or per-API-key limit overrides (separate concern).

## Key assumptions

- The cap can be set high enough to never affect legitimate operator workflows and still meaningfully bound abuse cost.
- Externalizing the limit (config) is cheap; a small set of constants is enough at first.
- Repository-layer enforcement is the right boundary because it is below all callers (controllers, services, scheduled jobs).

## Risks and exceptions

- A cap that is too low could surprise legitimate operators on edge data sets; the documentation must make the cap visible and tunable.
- Some queries are intentionally bounded by upstream pagination; for those, a repository-level cap is redundant but harmless.

## Promotion notes

Move to `triaged` after the inventory is complete. **Future skill candidate:** `ezkey-volume-limit-review` — a lightweight check that flags new repository methods missing a cap when their target table is in the high-volume catalog. Track this enhancement once the registry from `I-2026-0009` is in place; the two together would provide the data for the check at low token cost.

## Links

- Companions: `I-2026-0013` (paginated screens functional review), `I-2026-0014` (display strategy), `I-2026-0009` (controllers registry — would enable the future skill).
- Related feature: `F-rate-limiting`.
- Related principles: `#1` (simplicity), `#12` (security as posture).
