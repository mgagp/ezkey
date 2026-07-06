# Tracer Bullet Brief — `TB-2026-07-05` Admin UI audit chain checkpoints polish (Wave C)

## Metadata

- **ID:** `TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish`
- **Status:** `done`
- **Related idea:** `I-2026-0028` (P3 audit chain checkpoints row — last Wave C residual)
- **Lane:** `B` (Tier B embedded list — Global Admin only)
- **Posture:** `single-pass`
- **Parent context:** Wave C per
  [`operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md)
- **Created at:** `2026-07-05`
- **Captured by:** Marc (post–Wave B / alerts / dashboard signal model closeout)

## Objective

Align the **embedded checkpoint timeline** on `/audit-logs` (Integrity & Lifecycle panel) with the
paginated-screens matrix so a Global Admin can answer in ~3 seconds: **are integrity windows
healthy?** — without reading raw DB or opening every checkpoint detail. Close the last Wave C
operator-list residual.

## Operator question (matrix)

> Integrity windows healthy?

Reference: [`admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md) —
Audit chain checkpoints row currently `draft` / columns *TBD*.

## Current baseline (gaps observed)

| Area | Today | Gap |
|------|-------|-----|
| Surface | Embedded `CheckpointTimelineTable` in Integrity panel | Functional but matrix row never promoted |
| Columns | ID, window start/end, entries, type, lifecycle, notes | Largely aligned; **MANIPULATION_CONCILIATION** type missing from badge + filter (Wave B B2.6) |
| Type filter | REGULAR, ARCHIVE_SEAL, GAP_DECLARATION | Fourth checkpoint type not filterable |
| Empty windows | All checkpoints returned | No quick **non-empty only** toggle (`entryCountMin=1` API exists) |
| Deep link | `?integrity=1` opens panel; `focusCheckpointId` expands timeline | Timeline stays collapsed on generic integrity deep link |
| API | `GET /api/v1/audit-logs/chain-checkpoints` | Sufficient for R1 polish (no DTO change) |

## Proposed column order (R1 — confirm at slice start)

| Order | Column | Notes |
|-------|--------|-------|
| 1 | **ID** | `font-mono text-xs`; `sortKey: checkpointId` |
| 2 | **Window start** | Absolute `formatDateWithTimezone` |
| 3 | **Window end** | Absolute |
| 4 | **Entries** | `entryCount`; mono |
| 5 | **Type** | Badge for all four `CheckpointType` values |
| 6 | **Lifecycle** | `lifecycleState` badge |
| 7 | **Notes** | Truncated; full text in `title` tooltip |

**Default sort:** `windowStart,ASC` (enables inline undeclared gap rows between consecutive checkpoints).

## In scope

- `audit-logs.tsx` — `CheckpointTypeBadge`, type filter, optional **hide empty windows** (`entryCountMin=1`)
- Expand checkpoint timeline when `?integrity=1` deep link opens the panel
- i18n EN+FR for conciliation type + hide-empty label
- Matrix row Audit chain checkpoints → `implemented`
- `I-2026-0028` residual + operational-readiness Wave C note on merge

## Out of scope

- Top-level route for checkpoints (stays embedded in audit-logs)
- Lifecycle state server filter (API has no param R1)
- New checkpoint API fields or joins
- Checkpoint detail dialog (notes tooltip sufficient R1)
- Manual resolve / snooze (N/A)

## Exit criteria

- Global Admin opens Integrity panel → timeline: all four types visible and filterable
- `?integrity=1` deep link shows timeline expanded without extra clicks
- `npm run build` pass
- Matrix row + Wave C traceability updated

## Test posture

- **Automated:** Admin UI build
- **Manual exploratory (clean-start or EXP1):** scan timeline after scheduler run; filter by conciliation if present; toggle hide-empty
- **Browser tests:** optional — low-risk embedded-table polish

## Suggested branch

`feature/wave-c-audit-chain-checkpoints-polish`

## Links

- Parent queue: [`ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md`](ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
- Prior Wave C: [`TB-2026-07-03-admin-ui-alerts-list-polish.md`](TB-2026-07-03-admin-ui-alerts-list-polish.md)
- Integrity design: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
