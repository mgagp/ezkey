# Tracer Bullet Brief — `TB-2026-07-03` Admin UI alerts list polish (Wave C)

## Metadata

- **ID:** `TB-2026-07-03-admin-ui-alerts-list-polish`
- **Status:** `done`
- **Related idea:** `I-2026-0028` (P3 alerts row — residual from closed operator program)
- **Lane:** `A` (Tier A bounded list — Global Admin only)
- **Posture:** `single-pass`
- **Parent context:** Wave C per
  [`operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md)
  (after Wave B #269 closeout 2026-07-03)
- **GitHub issue:** optional (hygiene slice — issue only if board visibility needed)
- **Created at:** `2026-07-03`
- **Captured by:** Marc (Wave B closeout session)

## Objective

Polish the **Alerts** paginated list so a Global Admin can answer in ~3 seconds: **what needs my
attention now?** — without opening every detail page. Align the matrix row with Tier A operator-list
conventions now that all three R1 alert types and resolution paths are stable (post-Wave B).

## Operator question (matrix)

> What needs operator action now?

Reference: [`admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md) —
Alerts row currently `draft` / columns *TBD*.

## Current baseline (gaps observed)

| Area | Today | Gap |
|------|-------|-----|
| Columns | ID, type, severity, status, occurrences, created, lastSeen, resolvedAt | No **glance summary** from payload; `resolvedAt` noisy when default filter is OPEN |
| Type filter | `GAP_PENDING`, `INTEGRITY_RUPTURE` only | **`AUDIT_CHAIN_HEARTBEAT_STALE` missing** |
| Detail | Rich typed renderers per alert type (B2.5) | List does not surface 1–2 key payload fields |
| Default filter | OPEN | OK — keep |
| API | `GET /api/v1/alerts` with filters | Sufficient for R1 polish (no joins required) |

## Proposed column order (R1 — confirm at slice start)

| Order | Column | Notes |
|-------|--------|-------|
| 1 | ID | `font-mono text-xs`; `sortKey: alertId` |
| 2 | Type | Badge or compact label; i18n `alerts:type.*` |
| 3 | Severity | Badge (existing variants) |
| 4 | Status | Badge; OPEN emphasized |
| 5 | **Summary** | Type-specific one-liner from `payload` JSON (client-side parse) |
| 6 | Occurrences | Mono count when &gt; 1 or always show |
| 7 | Created | Absolute `formatDate` (matrix timestamp rule) |
| 8 | Last seen | Absolute; optional hide when same as created |

**Hide by default when filter = OPEN:** `resolvedAt` column (or omit entirely from list — detail only).

### Summary field sketch (UI-only, no API change R1)

| Alert type | Summary example |
|------------|-----------------|
| `AUDIT_CHAIN_GAP_PENDING` | Anchor checkpoint #N · ~M min gap |
| `AUDIT_CHAIN_HEARTBEAT_STALE` | Phase / stalled checkpoints (from payload `phase`) |
| `AUDIT_INTEGRITY_RUPTURE` | N entry + M chain violations (from payload counts) |

Implement via small helper in e.g. `src/lib/alert-list-summary.ts` with safe JSON parse + fallbacks.

## In scope

- `alerts.tsx` column layout, summary column, filter completeness (all `AlertType` values)
- Shared summary helper + unit tests for payload shapes (optional but recommended)
- i18n EN+FR (`alerts:list.*`, summary templates)
- Matrix row Alerts → `implemented`
- `I-2026-0028` P3 row marked **Done** on merge

## Out of scope

- Manual resolve / snooze UI (no API R1)
- New alert types or producer changes
- List API DTO enrichment (defer unless summary parsing becomes unwieldy)
- Dashboard banner changes (B3 done)
- Audit chain checkpoints embedded list (separate matrix row)

## Exit criteria

- Global Admin opens `/alerts` (default OPEN): can prioritize without opening detail for typical cases
- All three alert types filterable and summarized
- `npm run build` pass; no regression on `/alerts/{id}` detail
- Matrix + `I-2026-0028` residual updated

## Test posture

- **Automated:** Admin UI build; optional focused unit test on summary helper
- **Manual exploratory (clean-start):** induce or seed one OPEN alert per family; confirm list summary + filters
- **Browser tests:** optional — low-risk list polish; extend Playwright only if maintainer wants smoke

## Suggested branch

`feature/wave-c-alerts-list-polish`

## Links

- Runtime: [`docs/ALERTS.md`](../../../docs/ALERTS.md)
- Parent queue: [`ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md`](ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
- Wave B closeout: [`method-logs/ML-2026-07-03-wave-b-integrity-cluster-closeout.md`](method-logs/ML-2026-07-03-wave-b-integrity-cluster-closeout.md)
