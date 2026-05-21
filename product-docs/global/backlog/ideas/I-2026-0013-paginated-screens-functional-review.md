# Backlog Idea — `I-2026-0013` Paginated admin screens: functional and operational pertinence review

## Metadata

- **ID:** `I-2026-0013`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P1-operability`
- **Component tags:** `admin-ui`, `admin-api`

## Intent

Run a screen-by-screen review of every paginated search screen in the Admin UI, challenging what is displayed against the perspective of an actual operator with no time to lose. The administrators screen has already been through this exercise and serves as the reference. The review must produce, for each screen, an explicit decision on column relevance, glance-readability, and the operational signal an operator should be able to extract within a few seconds.

## Problem and value

- **Problem:** Most paginated screens were initially built to map directly from the API output to the UI, optimized for performance. That choice is fine technically, but it short-circuits the operator-first design step (`Design Principle #5`). Without a deliberate functional review, the screens may show columns that are technically present but operationally weak.
- **Expected value:** Each screen earns its place in the Admin UI by passing an explicit operator-first test; columns are picked because they serve operational decisions, not because they happened to be in the API payload. This aligns with `Design Principle #10` (Admin UI sobriety) and elevates daily operator UX.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D8-D9-pagination-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D8-D9-pagination-grill-me.md).

- Combined pass with `I-2026-0014`; canonical output [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md).
- Per-screen operator question + columns; Global vs Tenant Admin notes.
- Admins screen = reference; fill matrix rows then promote `TB-*` by group.

## Scope

- **In scope:**
  - Inventory all paginated search screens in the Admin UI.
  - For each screen, document the operator question it serves (one line) and the columns chosen to answer that question.
  - Flag screens where columns appear to be incidental to the API rather than intentional.
  - Propose column changes (additions, removals, reorderings) where applicable.
  - Cross-reference with `I-2026-0014` (display strategy: foreign keys vs intelligent joins) since the two reviews share material.
- **Out of scope:**
  - Implementation of column changes (covered by follow-up tracer bullets per screen group).
  - Non-paginated detail views (separate evaluation if needed).

## Key assumptions

- The administrators screen exemplifies the target quality; it can be cited as the reference for "what good looks like".
- The review can be conducted screen-by-screen without major coordination, since each screen is a relatively independent operator surface.

## Risks and exceptions

- Resist the temptation to redesign too much at once. A single screen review must produce decisions, not a full rewrite.
- Operator perspectives may differ between Global Admin and Tenant Admin (`Design Principle #10` split); some columns may need role-aware rendering rather than uniform changes.

## Promotion notes

Complete matrix analysis rows (`draft` → `reviewed`), then **`TB-*` per screen group** with `I-2026-0014`.

## Links

- Canonical matrix: [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md)
- Grill: `../grill-sessions/blitz-2026-05-08-2-D8-D9-pagination-grill-me.md`
- Companion: `I-2026-0014` (display strategy), `I-2026-0015` (volume limits).
- Related reading: `docs/PAGINATION_GUIDELINES.md`, `docs/PAGINATION_AUDIT_REPORT.md` (mechanics, complementary to this functional review).
- Related principles: `#5` (operator-first), `#10` (Admin UI sobriety), `#1` (simplicity).
