# Tracer Bullet — `TB-2026-07-03-dashboard-batch-health-widgets`

## Metadata

- **ID:** `TB-2026-07-03-dashboard-batch-health-widgets`
- **Status:** `done`
- **Priority:** `P1`
- **Lane:** Wave B integrity cluster (GitHub #269, slice B3)
- **Backlog idea:** [`I-2026-0007`](ideas/I-2026-0007-admin-dashboard-integrity-widgets.md)
- **GitHub PR:** #289 (merged)
- **Branch:** `feature/269-b3-dashboard-batch-widgets`
- **Created at:** `2026-07-03`

## Objective

Global Admin dashboard: 3-second batch health visibility (scheduled job last-run rows + integrity
config summary) and count-first open-alerts banner.

## Delivered (R1)

| Area | Change |
|------|--------|
| Admin API | `DashboardOverviewDto`: `openAlertCount`, `integrityJobs`, `operationalJobs`, `integrityConfigSummary` |
| ezkey-core | `AlertService.countOpen()`; `ReencryptionService` registry hooks |
| Admin UI | Two batch-health cards; compact open-alert count banner; EN+FR i18n |

## Validation

- Maven baseline (`./scripts/build.sh`) — pass
- `DashboardServiceTest`, `ScheduledJobLastRunServiceTest`, `ReencryptionServiceTest` — pass
- `npm run build` (admin-ui) — pass
- Maintainer exploratory: Global Admin dashboard widgets + alert count banner — OK
- OpenAPI spec refresh — included in PR #289

## Non-goals (unchanged)

- Batch staleness alerts (C9)
- Snooze UI
- Run history timeline
