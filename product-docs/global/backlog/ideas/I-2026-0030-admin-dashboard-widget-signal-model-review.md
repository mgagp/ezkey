# Backlog Idea — `I-2026-0030` Admin Dashboard: widget signal model review and realignment

## Metadata

- **ID:** `I-2026-0030`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-06-28`
- **Updated at:** `2026-07-05`
- **Last reviewed at:** `2026-07-05`
- **Closed at:** `2026-07-05`
- **GitHub branch:** `feature/i-2026-0030-dashboard-widget-signal-model`
- **GitHub PR:** #291
- **Git commit:** `3d1196ec`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-ui`, `admin-api`, `docs (product-docs)`
- **Feature anchor:** Admin UI operator experience (dashboard entry surface)
- **Captured by:** Marc
- **Related (distinct):** `I-2026-0007` — **future** integrity/batch widgets (Wave B); implement **after** this slice

## Intent

Critically review **existing** Admin Dashboard stat widgets and conditional cards (integrations,
enrollments, auth attempts 24h, auth health 24h, open alerts, audit-chain follow-up). Realign badge
colors, labels, and backend buckets where they conflict with Ezkey operator values (red = investigate
now; invisible when healthy; PME pragmatism). Document the **operator signal contract** in the Admin
UI component pack without duplicating OpenAPI field lists.

## Problem and value

- **Problem:** The enrollment **incidents** badge counts `INVALID` + `REVOKED` and uses **error/red**
  when non-zero — but many **revocations are intentional** admin actions, not operational emergencies.
  Dashboard widget semantics live mainly in code/i18n; `screens-and-wireflow.md` does not capture
  badge intent → drift risk and inconsistent operator messaging.
- **Expected value:** Widgets that support daily operability for a PME operator; canonical
  decision table for signal colors and drilldowns; targeted implementation passes; foundation before
  adding integrity widgets (`I-2026-0007`).

## Scope

### In scope

- **Review matrix** — per widget: operator question, headline KPI, each badge, color variant rules,
  drilldown target, role visibility (Global vs Tenant Admin).
- **Grill-me** — complete (2026-06-28).
- **Implementation passes** (promoted later via `TB-*`):
  - **Pass A:** enrollment widget — split **`invalid`** (error if > 0) and **`revoked`** (muted);
    backend/DTO, drilldowns, EN/FR i18n; retire combined « incidents » bucket.
  - **Pass B:** none required (integrations + auth widgets statu quo per grill).
  - **Pass C:** canon doc `product-docs/components/admin-ui/dashboard-widget-signal-model.md` +
    light cross-link in `screens-and-wireflow.md` § Dashboard.
- **Validation:** clean-start manual exploratory per widget; optional targeted UI test if drilldown
  contract changes materially.

### Out of scope

- New integrity / batch last-run widgets (`I-2026-0007`) — **after** this slice.
- Dashboard widgets for API keys or encryption keys (grill: statu quo — no widgets).
- Hand-editing OpenAPI specs; duplicating DTO schemas in product-docs.
- Paginated list screen review (`I-2026-0013`) except drilldown parity fixes triggered by Pass A.

## Grilling decisions (2026-06-28)

See
[`../grill-sessions/2026-06-28-admin-dashboard-widget-signal-model-grill-me.md`](../grill-sessions/2026-06-28-admin-dashboard-widget-signal-model-grill-me.md).

| Topic | Decision |
|-------|----------|
| Enrollment badges | Split **invalid** (red if > 0) + **revoked** (muted); drop combined « incidents » |
| Entity stat-card red | **error** = investigate now / active risk only |
| Headline enrollments | **verified** KPI unchanged |
| Integrations / auth widgets | **Statu quo** |
| API keys / encryption widgets | **None** on dashboard R1 |
| Conditional cards | **Keep** hidden-when-healthy pattern |
| vs `I-2026-0007` | **This slice first**, then integrity widgets TB |
| Canon doc | **`dashboard-widget-signal-model.md`** + wireflow link (Pass C) |

## Requirements (R1 at implementation)

- **R1:** Each dashboard badge maps to a documented operator question in the signal-model doc.
- **R2:** **Red (`error`)** on entity stat cards only for states warranting immediate attention (G2);
  **revoked** uses **muted**, not error (G1/G3).
- **R3:** Drilldown URLs consistent with list filters (`dashboard-drilldown-links.ts` +
  enrollments bucket queries).
- **R4:** EN/FR i18n parity for relabeled badges and tooltips (`invalid`, `revoked`).

## Closeout (2026-07-05)

**Delivery path:** program slice closed on **`I-*` only** (no retroactive `TB-*` — acceptable latitude
when grill + `I-*` already carry decisions and evidence).

### Delivered

| Pass | Outcome |
|------|---------|
| **A** | Split enrollment dashboard buckets: `invalid` (error if > 0) + `revoked` (muted); retired combined `incidents`. Backend `EnrollmentDashboardStats` / `DashboardEnrollmentStatsDto`; UI drilldowns; EN/FR i18n. |
| **B** | Statu quo (integrations + auth widgets unchanged). |
| **C** | [`dashboard-widget-signal-model.md`](../../../components/admin-ui/dashboard-widget-signal-model.md) + wireflow cross-link. |

### Validation evidence

- Maven baseline (`install -DskipTests`) — pass
- `EnrollmentDashboardStatsTest`, `DashboardServiceTest` — pass
- Admin API container rebuild + `update-specs --admin-only` + Orval regen — pass
- `npm run build` (admin-ui) — pass
- Maintainer manual exploratory (dashboard badges + drilldowns EN/FR) — OK

### Residual risks / deferred

- No Playwright coverage for enrollment badge drilldowns (low risk; manual pass sufficient for Lane C).
- Pass B copy/tooltip polish for integrations/auth not revisited (grill statu quo).

### GitHub issue posture

**Canon sufficient** — no GitHub issue opened (Lane C, single monorepo PR).

### Unblocks

- **`I-2026-0007`** integrity widget follow-ups no longer blocked by G9 (Wave B B3 widgets already shipped; signal-model doc now canonical for future dashboard work).

## Links

- Grill: [`../grill-sessions/2026-06-28-admin-dashboard-widget-signal-model-grill-me.md`](../grill-sessions/2026-06-28-admin-dashboard-widget-signal-model-grill-me.md)
- Values: [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
- Wireflow: [`../../../components/admin-ui/screens-and-wireflow.md`](../../../components/admin-ui/screens-and-wireflow.md)
- Prior enrollment widget: `.cursor/plans/archived/2026-04/dashboard_enrollment_widget_1aaa3560.plan.md`
- Future widgets: [`I-2026-0007-admin-dashboard-integrity-widgets.md`](I-2026-0007-admin-dashboard-integrity-widgets.md)
- Code: [`../../../../ezkey-admin-ui/src/pages/dashboard.tsx`](../../../../ezkey-admin-ui/src/pages/dashboard.tsx)
- Signal model: [`../../../components/admin-ui/dashboard-widget-signal-model.md`](../../../components/admin-ui/dashboard-widget-signal-model.md)
