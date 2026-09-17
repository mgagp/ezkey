# Admin UI — Audit trail vs Integrity: job → surface map

## Metadata

- **Document ID:** `admin-ui-audit-integrity-job-surface-map`
- **Status:** `ready` (2026-09-17)
- **Parent intention:** [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md)
- **Grill:** [`backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md`](backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md)
- **Purpose:** One-page map from operator jobs to Admin UI surfaces after the hard split.
  Proof constraint: **one** cryptographic journal (`audit_log` + chain); surfaces are views / ateliers, not second audit citizens.

---

## Surfaces

| Surface | Who sees it | Role |
|---------|-------------|------|
| **Audit trail** (`/audit-logs`) | All admins | Everyday “who did what?” + around-event context |
| **Integrity** (new GA-only sidebar route; label **Integrity**) | Global Admin | Investigate / verify / remediate on the **same** `audit_log` rows |
| **Alerts** (`/alerts`, detail) | Global Admin (as today) | Exceptional **signal list** only — deep-link into Integrity |

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

In-app emitters (alerts, dashboard, etc.) point at the **Integrity** route directly. No redirects, dual-read, or shims for former `/audit-logs?integrity=…` params.

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

- **Intention + this map** = IA lock for engineering.
- **Isabelle** = exploratory walk once the split is on a stack.
- **Alex** = priority framing already settled (not P0; do it properly).
