# Admin UI — Audit trail vs Integrity: job → surface map

## Metadata

- **Document ID:** `admin-ui-audit-integrity-job-surface-map`
- **Status:** `promoted` (IA 2026-09-17; cut 1 route `/integrity` shipped 2026-09-18 — PR `#563`)
- **Parent intention:** [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md)
- **Grill:** [`backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md`](backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md)
- **Purpose:** One-page map from operator jobs to Admin UI surfaces after the hard split.
  Proof constraint: **one** cryptographic journal (`audit_log` + chain); surfaces are views / ateliers, not second audit citizens.
- **Related:** [`admin-ui-integrity-base-monitoring-honesty.md`](admin-ui-integrity-base-monitoring-honesty.md) — Integrity honesty when monitoring is off (base / jobs disabled); cut-2 phase A [`admin-ui-integrity-cut2-phase-a.md`](admin-ui-integrity-cut2-phase-a.md); cut-2 phase B+B′ [`admin-ui-integrity-cut2-phase-b.md`](admin-ui-integrity-cut2-phase-b.md) (in-page modes — still one Integrity surface)

---

## Surfaces

| Surface | Who sees it | Role |
|---------|-------------|------|
| **Audit trail** (`/audit-logs`) | All admins | Everyday “who did what?” + around-event context |
| **Integrity** (`/integrity`, GA-only sidebar; label **Integrity**) | Global Admin | Investigate / verify / remediate on the **same** `audit_log` rows. Cut-2 phase B locks **in-page** Observe / Verify / Remediate modes on this **one** surface — not new nav items or sub-routes ([`admin-ui-integrity-cut2-phase-b.md`](admin-ui-integrity-cut2-phase-b.md)). |
| **Alerts** (`/alerts`, detail) | Global Admin (as today) | Exceptional **signal list** only — deep-link into Integrity |
| **Dashboard** (`/dashboard`) | Role-scoped widgets | Batch-health **signal** for scheduled jobs (checkpoint, nightly validation, re-encryption). Exit / follow-up to Integrity. Not the atelier. |

Complementary stores (alert rows, heartbeat incidents, conciliation / justification records) remain **operational indexes**. They are not a second audit trail.

---

## Job → surface

| Operator job | Primary surface | Also uses | Data / proof |
|--------------|-----------------|-----------|--------------|
| Browse / filter / paginate the trail | Audit trail | — | `audit_log` |
| Open one event detail | Audit trail | — | `audit_log` |
| Entity-scoped context (enrollment / auth attempt / integration) | Audit trail | — | `audit_log` (filtered) |
| Around-one-event context (“view around”) | Audit trail | Link **from** Integrity when needed | `audit_log` (window) |
| See affected rows after integrity rupture | **Integrity** | Alert as entry signal | Same `audit_log` rows (filter / highlight) |
| Verify chain (read-only) | Integrity | — | Checkpoints + chain over `audit_log` |
| Verify entry HMAC (read-only) | Integrity | Detail badges may reflect session | `audit_log` HMAC fields |
| Run validation (detect; may raise/touch alert) | Integrity | Alerts updated as signal | Same crypto validation path |
| Checkpoint timeline / lifecycle observability | Integrity | — | Checkpoint table (chain), not a second event journal |
| Seal archive / declare gap / confirm archived | Integrity | — | Lifecycle ops on chain; justification as explanation |
| Heartbeat incident declare closure | Integrity | Alert may signal | Incident index + audit events as today |
| Reconcile integrity rupture | Integrity | Alerts **Resolve** deep-links here | Conciliation = explanation / reattach, not rewrite |
| Notice that something needs attention | Alerts | → Integrity | Alert row = signal, not the proof |
| See whether integrity jobs last ran clean | Dashboard | → Integrity | `ezkey_scheduled_job_last_run` (not the proof) |

---

## Journeys (happy paths)

### Everyday trail

`Audit trail` → filter → row detail → (optional) around-event / entity context → done.

Tenant Admin stops here. No Integrity chrome.

### Rupture / integrity incident (Global Admin)

1. **Detect** — nightly / run validation / verify (Integrity) or batch → **Alert** raised.
2. **Signal** — operator opens **Alerts** detail.
3. **Investigate / Resolve** — deep-link into **Integrity** (affected `audit_log` rows + verify + remediate).
4. **Context bridge** (optional) — from Integrity, “view around event” opens **Audit trail** on the same `audit_log` window.
5. **Close** — conciliation / declare / confirm as appropriate; alert resolvable when process says so.

### Greenfield deep-links

In-app emitters (alerts, dashboard, etc.) point at **`/integrity`** directly. No redirects, dual-read, or shims for former `/audit-logs?integrity=…` params.

---

## Explicit non-mappings

| Do not put here | Why |
|-----------------|-----|
| Full remediation UI on Alerts detail | Blurs signal vs atelier |
| Integrity investigation landing on Audit trail | Re-pollutes everyday journal; blurs Tenant vs GA |
| A durable “affected rows” list outside `audit_log` | Second audit citizen |
| Presenting Alerts / incidents as “the audit” | Dilutes proof claims |

---

## Phase 2 (after walkable split)

Ensure Admin UI callers exist end-to-end for ops already in OpenAPI but unevenly owned (notably reconcile integrity rupture, confirm archived) — still on **Integrity**, still same proof model.

---

## Handoff

- **Intention + this map** = IA lock. Cut 1 is on `main` (PR `#563`).
- **Isabelle** = exploratory walk (cut 1 is walkable).
- **Alex** = priority framing already settled (not P0; do it properly).
- **Remaining:** cut 2 phase B+B′ (modes + incidents pager — [`admin-ui-integrity-cut2-phase-b.md`](admin-ui-integrity-cut2-phase-b.md)); cut 2 phase C later; cut 3 (OpenAPI callers still missing on the page). Phase A density is delivered ([`admin-ui-integrity-cut2-phase-a.md`](admin-ui-integrity-cut2-phase-a.md)).
