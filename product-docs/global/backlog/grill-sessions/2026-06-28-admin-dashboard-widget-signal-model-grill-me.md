# Grill Me — 2026-06-28 (Admin Dashboard widget signal model)

## Session control

| Field | Value |
|-------|--------|
| **Backlog** | `I-2026-0030-admin-dashboard-widget-signal-model-review` |
| **Related** | `I-2026-0007` (future integrity widgets — out of scope for this grill) |
| **Lane** | `C` (operator UX / product alignment) |
| **Status** | `complete` |
| **Date** | `2026-06-28` |
| **Captured by** | Marc |

## Context (observed)

- Dashboard stat cards: integrations, enrollments, auth attempts (24h), auth health (24h).
- Conditional cards (Global Admin): open alerts, audit-chain incidents awaiting declaration — hidden
  when empty (good posture).
- Enrollment **incidents** badge: backend `INVALID` + `REVOKED`; UI variant **`error`** when count
  > 0; label « incidents » — misaligned with operator values when revocations are intentional.
- Headline enrollments = **verified** (active MFA-ready devices); badges are complementary buckets
  (do not sum to headline — by design since 2026-04).
- Integrations and auth widgets align with rolling-window / lifecycle drilldown patterns.
- `screens-and-wireflow.md` § Dashboard lacks widget-level operator contract.

## Settled decisions (operator-confirmed 2026-06-28)

| ID | Decision |
|----|----------|
| G1 | **Split enrollment security badges:** replace combined « incidents » with **`invalid`** (INVALID only) — **`error` variant when count > 0**; separate **`revoked`** (REVOKED only) — **`muted` variant**, labels EN/FR « revoked » / « révoqués », no urgency connotation. |
| G2 | **Entity stat-card red rule:** on the four entity stat cards, **`error` = investigate now or active risk** — not for expected lifecycle terminals or admin actions already taken. |
| G3 | **Keep revoked badge** on dashboard: **muted**, clickable drilldown to filtered enrollments list — useful for occasional audit, not alarmist; do not remove. |
| G4 | **Headline enrollments KPI:** keep **`verified`** (VERIFIED + active=true) as the large number — primary MFA-ready metric. |
| G5 | **Integrations widget:** **statu quo** — active/retired badges unchanged unless copy/tooltip pass finds a gap during TB. |
| G6 | **Auth Attempts 24h + Auth Health 24h:** **statu quo** — document in signal-model canon only. |
| G7 | **No API keys or encryption keys widgets** on dashboard R1 — sidebar / dedicated screens suffice. |
| G8 | **Conditional cards pattern:** **keep** — open alerts and audit-chain follow-up visible only when action required; reference pattern for future `I-2026-0007` widgets (no per-stat-card duplication). |
| G9 | **Sequence:** close **`I-2026-0030`** (review + Pass A enrollment + doc Pass C) **before** promoting TB for **`I-2026-0007`** integrity/batch widgets (Wave B B3). |
| G10 | **Canon doc:** create **`product-docs/components/admin-ui/dashboard-widget-signal-model.md`** in TB Pass C; add one paragraph + link in `screens-and-wireflow.md` § Dashboard — operator contract table, no OpenAPI field duplication. |

## Implementation hints (from grill, for future TB)

- **Pass A:** split `EnrollmentDashboardStats` / DTO (`invalid`, `revoked` vs `incidents`); update
  `dashboard.tsx`, drilldown links, enrollments bucket queries, EN/FR i18n; remove « incidents »
  bucket or remap list filter.
- **Pass B:** none required beyond grill — integrations/auth statu quo.
- **Pass C:** signal-model doc + wireflow cross-link.

## Open questions

_None — grill complete._

## Outcome

Grill complete. **`I-2026-0030`** → **`ready`** (2026-06-28). Implementation closed **`done`** on
`I-*` only (2026-07-05; no retroactive `TB-*`).

## Links

- [`../ideas/I-2026-0030-admin-dashboard-widget-signal-model-review.md`](../ideas/I-2026-0030-admin-dashboard-widget-signal-model-review.md)
- [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
