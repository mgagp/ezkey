# Admin UI — Dashboard widget signal model

Operator contract for Admin UI dashboard stat badges, conditional cards, and drilldown semantics.
Field names match the dashboard overview API; this document does **not** duplicate OpenAPI schemas.

**Traceability:** [`I-2026-0030`](../../global/backlog/ideas/I-2026-0030-admin-dashboard-widget-signal-model-review.md),
grill [`2026-06-28`](../../global/backlog/grill-sessions/2026-06-28-admin-dashboard-widget-signal-model-grill-me.md).

## Principles

| Rule | Meaning |
|------|---------|
| **Headline KPI** | Large number = primary operational question for that card. |
| **Badges** | Complementary buckets; they do **not** sum to the headline (by design). |
| **`error` (red)** | Investigate **now** or active risk on entity stat cards — not for expected admin actions already taken. |
| **`warning` (amber)** | Worth monitoring; not necessarily an emergency. |
| **`success` (green)** | Healthy or positive terminal outcome. |
| **`muted`** | Informational; zero or routine lifecycle state. |
| **Conditional cards** | Shown only when operator action is required (hidden when healthy). |
| **Drilldown** | Badge links use `dashboard-drilldown-links.ts` and `source=dashboard` where applicable. |

## Integrations stat card

| Element | Operator question | Variant rule | Drilldown |
|---------|-------------------|--------------|-----------|
| Headline | How many integrations exist (all lifecycle states)? | — | — |
| **active** badge | How many are in service? | `success` | `/integrations?lifecycleFilter=active` |
| **retired** badge | How many are retired? | `muted` | `/integrations?lifecycleFilter=retired` |

**Posture:** statu quo (2026-07 Pass B — no change).

## Enrollments stat card

| Element | Operator question | Variant rule | Drilldown |
|---------|-------------------|--------------|-----------|
| Headline **verified** | How many devices are MFA-ready now? | — | — |
| **in progress** | Onboarding stuck or awaiting verify? | `warning` if > 0, else `muted` | `bucket=inProgress` (CREATED + BOUND) |
| **suspended** | Verified devices disabled by admin? | `warning` if > 0, else `muted` | `status=VERIFIED&active=false` |
| **expired** | Invitations that timed out? | `warning` if > 0, else `muted` | `status=EXPIRED` |
| **invalid** | Failed crypto validation — investigate? | **`error` if > 0**, else `muted` | `bucket=invalid` (INVALID, any active) |
| **revoked** | Admin revocations (often intentional)? | **`muted` always** | `bucket=revoked` (REVOKED, any active) |

**Change (Pass A):** Retired combined **incidents** badge (`INVALID` + `REVOKED`, red when non-zero). Revocations are not emergencies; invalid enrollments warrant red.

## Auth attempts (24h) stat card

| Element | Operator question | Variant rule | Drilldown |
|---------|-------------------|--------------|-----------|
| Headline **total** | How many attempts started in rolling 24h? | — | — |
| **pending** | Waiting for device pickup? | `warning` if > 0, else `muted` | `status=PENDING&preset=rolling24h` |
| **read** | Claimed but not finished? | `warning` if > 0, else `muted` | `status=READ&preset=rolling24h` |
| **accepted** | Successful outcomes? | `success` | `status=ACCEPTED&preset=rolling24h` |
| **rejected** | User denied? | `error` | `status=REJECTED&preset=rolling24h` |
| **invalid** | Protocol/signature failure? | `error` if > 0, else `muted` | `status=INVALID&preset=rolling24h` |
| **expired** | Timed out? | `warning` if > 0, else `muted` | `status=EXPIRED&preset=rolling24h` |
| Audit trail link | Forensic event stream (not row counts)? | accent link | `/audit-logs?eventType=AUTH_ATTEMPT&…` |

**Posture:** status quo (document only).

## Auth health (24h) card

Success / invalid / expired / denied rates over **terminal** outcomes in the same 24h window as auth attempts.
Percentages hidden when no terminal outcomes exist.

**Posture:** status quo (document only).

## Conditional surfaces (Global Admin)

| Surface | When visible | Operator question |
|---------|--------------|-------------------|
| Open alerts banner | `openAlertCount > 0` | Integrity or operational alerts needing resolution |
| Audit-chain gap banner | Undeclared gaps in overview payload | Cryptographic chain continuity |
| Batch health widgets | Global Admin overview | Last run of integrity / operational scheduled jobs |
| Operational follow-up | Heartbeat incidents `RECOVERED_PENDING_DECLARATION` | Formal closure after recovery (distinct from red alert urgency) |

**Posture:** hidden-when-healthy pattern retained (G8).

## Out of scope (dashboard R1)

- API keys and encryption keys stat widgets — dedicated sidebar routes suffice (G7).
- New integrity batch widgets beyond existing Wave B batch-health strip — see `I-2026-0007` (after this slice).

## Implementation map

| Layer | Location |
|-------|----------|
| Backend buckets | `EnrollmentDashboardStats`, `DashboardEnrollmentStatsDto`, `DashboardService` |
| Drilldown builders | `ezkey-admin-ui/src/lib/dashboard-drilldown-links.ts` |
| Dashboard UI | `ezkey-admin-ui/src/pages/dashboard.tsx` |
| Enrollments bucket lists | `ezkey-admin-ui/src/pages/enrollments.tsx` (`bucket` query param) |
| Copy | `ezkey-admin-ui/src/locales/{en,fr}/dashboard.json` |
