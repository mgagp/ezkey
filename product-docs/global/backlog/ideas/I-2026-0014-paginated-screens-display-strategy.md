# Backlog Idea — `I-2026-0014` Paginated screens display strategy: foreign keys vs intelligent joins

## Metadata

- **ID:** `I-2026-0014`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-ui`, `admin-api`, `audit`

## Intent

Adopt a deliberate, screen-by-screen display strategy for paginated views, balancing operator readability and pragmatic performance. Where the underlying volume is bounded by realistic Ezkey audience expectations (small-to-medium businesses), prefer **intelligent joins** that surface meaningful labels rather than raw foreign-key IDs, even at the cost of a few extra columns or indexes. Where volume is genuinely high (audit logs, authentication requests), evaluate the trade-off more carefully and accept FK IDs with a "More information" affordance only when joins would meaningfully degrade performance.

## Problem and value

- **Problem:** Paginated screens were initially designed for maximum performance (DAO output mapped directly to the UI), exposing foreign-key IDs. The current "More information" button per row works but is awkward for operators consulting screens daily. The administrators screen was refactored with intelligent joins because the row count is bounded, and the result is a clearly better operator surface. Other screens deserve the same treatment when their volume profile allows it.
- **Expected value:** Operator readability prioritized where volume permits (`Design Principle #5` operator-first, `#1` simplicity); honest performance choices on high-volume tables; an explicit, documented per-screen decision rather than a single global heuristic.

## Scope

- **In scope:**
  - For each paginated screen, evaluate the realistic volume profile of the underlying table (bounded vs unbounded).
  - For bounded tables (admins, integrations, enrollments, ...), refactor to use joins surfacing meaningful labels instead of raw FK IDs.
  - For high-volume tables (audit logs, authentication requests), evaluate carefully; accept FK + on-demand fetch only if joins would meaningfully degrade performance, otherwise add a targeted index to support the join.
  - Document the decision per screen so the rationale is auditable.
  - Cross-reference `I-2026-0013` (functional review) since the two analyses share input.
- **Out of scope:**
  - Replacement of the underlying pagination mechanics (`docs/PAGINATION_GUIDELINES.md` is fine).
  - Materialized views or denormalized projections at this stage; if needed, those are a future evolution.

## Key assumptions

- Ezkey's target audience (SMEs) implies bounded counts for admins, integrations, and enrollments; joins on those tables are pragmatically affordable.
- Audit logs and authentication-request tables have a fundamentally different volume profile; the decision must be made deliberately for those.
- A targeted index can absorb the cost of a meaningful join on a moderate-volume table; this is acceptable additional complexity (`#2` essential vs accidental).

## Risks and exceptions

- Over-optimizing for performance on screens where it does not matter has been the historical default and reduced operator experience. The new posture must explicitly prioritize readability where volume permits.
- Joins on truly high-volume tables can degrade performance; the per-screen evaluation must be honest about volume expectations rather than wishful.

## Promotion notes

Move to `triaged` once the per-screen volume profile inventory is captured. Promote pieces to `TB-*` per group, in coordination with `I-2026-0013`.

## Links

- Companion: `I-2026-0013` (functional review), `I-2026-0015` (volume limits).
- Related reading: `docs/PAGINATION_GUIDELINES.md`, `docs/PAGINATION_AUDIT_REPORT.md` (mechanics).
- Related principles: `#1` (simplicity), `#2` (essential vs accidental), `#5` (operator-first), `#10` (Admin UI sobriety).
