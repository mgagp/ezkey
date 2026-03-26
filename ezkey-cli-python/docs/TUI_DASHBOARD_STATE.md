# TUI Home / Dashboard — State of the art and alignment with modern Dashboard API

## Current state (TUI)

The TUI home screen (`home.py`) loads data via **`get_dashboard_snapshot()`** in `api_client.py`. That method **does not** use the Admin API’s unified dashboard endpoint. It:

- Calls **many** Admin API endpoints and aggregates in Python:
  - **Integrations:** `get_integrations(size=1)` × 3 (total, active, inactive)
  - **Enrollments:** `get_enrollments(size=1)` × 5 (total + CREATED, BOUND, VERIFIED, INVALID)
  - **Auth 24h:** `get_auth_attempts_list(size=1, ...)` × 7+ (total, ACCEPTED, PENDING, REJECTED, INVALID, EXPIRED, plus “pending >5m”)
  - **Action Required:** `get_auth_attempts_list(..., created_before=5m)`, `get_enrollments(status=CREATED, created_before=24h)`, `get_api_keys()` for expiring count
  - **Recent activity + Security:** `get_audit_logs(page=0, size=50)` then `_format_audit_activity()` and `_compute_security_snapshot()` in Python
  - **Health:** `get_health()`

- Returns a single dict: `status`, `actions`, `activity`, `security`, `meta`.

So the TUI dashboard is a **custom, multi-call aggregation** that predates the unified Dashboard API.

## Modern Dashboard API (Admin UI)

The Admin API exposes:

- **`GET /api/v1/dashboard/overview`** — single endpoint used by the Admin UI. Returns `DashboardOverviewDto`:
  - **integrations:** `{ total, active, inactive }`
  - **enrollments:** `{ total, verified, bound, created }` (no `invalid` in DTO)
  - **auth24h:** `{ total, pending, readCount, accepted, rejected, invalid, expired, terminalTotal, successRatePct, invalidRatePct, expiredRatePct, rejectedRatePct }` (rates are vs terminal outcomes; `successRatePct` null if no terminal outcomes)
  - **recentActivity:** list of `DashboardRecentActivityItemDto` (audit-derived, same idea as TUI’s activity)
  - **alerts:** (Global Admin only) e.g. `AUDIT_CHAIN_GAP_PENDING`

The Admin UI also calls **`GET /api/v1/auth-attempts/pending-count`** for the “pending” badge (short refresh interval). The overview is designed for a ~60s refresh; pending count is refreshed more often.

## Comparison

| Source | Integrations | Enrollments | Auth 24h | Recent activity | Alerts | Pending count | Action Required (extra) | Security (extra) |
|--------|--------------|-------------|----------|------------------|--------|---------------|--------------------------|------------------|
| **TUI today** | 3 GET list calls | 5 GET list calls | 7+ GET list calls | 1 GET audit-logs + format | — | derived from list | 3 extra calls | from same audit-logs |
| **Modern API** | 1 GET overview | (in overview) | (in overview) | (in overview) | (in overview) | 1 GET pending-count | — | — |

So the TUI currently does **many more** requests per refresh than the Admin UI, and duplicates aggregation logic that now lives in `DashboardService`.

## Recommendation

- **Use the modern Dashboard API** for the TUI home screen:
  1. Call **`GET /api/v1/dashboard/overview`** once per refresh.
  2. Optionally call **`GET /api/v1/auth-attempts/pending-count`** for the “pending” metric (like the Admin UI).
  3. Map **overview** to the existing TUI widgets (Status, Activity; optional Alerts when present).
  4. **Simplify or drop** widgets that the overview does not provide:
     - **Action Required:** overview does not expose “pending >5m”, “enrollments CREATED >24h”, “API keys expiring 30d”. Either remove this panel or show only “Pending auth: N” from `pending-count`.
     - **Security:** overview does not expose “login failures 24h”, “recoveries 7d”, “keys revoked 7d”. Those were computed from audit log in the TUI. Options: remove the Security panel, or keep it and show zeros (no extra audit call).

- **Benefits:** One (or two) requests instead of many; same contract as the Admin UI; less drift when the backend changes; TUI stays read-only and aligned with the “dashboard moderne”.

- **Concession:** We lose the extra “Action Required” and “Security” metrics unless we add back one or two dedicated calls. For a read-only investigation TUI, aligning with the overview and showing at most “pending count” is a reasonable trade-off.

## Implementation (done)

- **api_client:** added `get_dashboard_overview()` and `get_auth_attempts_pending_count()`; home screen uses them instead of `get_dashboard_snapshot()`.
- **home.py:** maps `overview` (+ optional `pending_count`) to StatusPanel, ActivityPanel; ActionPanel shows only pending from pending-count; SecurityPanel receives zeros (no extra audit call). Optional display of `overview.alerts` when present (Global Admin).
