# Audit integrity & operational incidents — UX notes and close-out checklist

**Document role:** Follow-up UX notes for Integrity, dashboard operational card, and operator workflows — **not** the authoritative Audit Chain Heartbeat plan (see [.cursor/plans/archived/2026-04/audit_chain_heartbeat_20b04a41.plan.md](archived/2026-04/audit_chain_heartbeat_20b04a41.plan.md) for that dossier).

**Status:** Working notes after validating heartbeat incidents, gap declarations, checkpoint timeline, and dashboard follow-up widget.

**Goal:** Capture UX observations, decisions already taken, optional future chantiers, and remaining validation useful for releases and handoff.

---

## 1. Honest UX critique (Audit Logs → Integrity & lifecycle)

**Observation:** The Integrity section stacks chain verification, lifecycle overview, seal/gap tooling, undeclared gaps list, checkpoint timeline (paginated), and operational heartbeat incidents — distinct mental models (cryptographic vs operational supervision) on one scroll-heavy page.

**Assessment:**

- **Pros:** Rare-event workflow; Global Admin–only surface keeps noise away from tenant admins; conceptual grouping (“everything under audit integrity”) is defensible for specialists.
- **Cons:** Cognitive load grows with each slice; operational incidents use a **custom list**, not the app’s standard **paginated master pattern** used elsewhere (alerts, etc.); discovery still relied on scrolling until the **dashboard operational follow-up card** shipped.
- **Verdict:** Acceptable for **v1 close-out** given low frequency and role scope. If usage or incident volume increases, **splitting Integrity into its own route or sub-routes** (or a compact “Integrity hub” entry from the nav) becomes a natural **separate chantier** — not a blocker for closing the current dossier.

---

## 2. Decisions and implementation snapshot (recent)

| Topic | Decision |
|-------|----------|
| Alert vs operational incident | Alerts = acute / immediate signal; auto-resolve when heartbeat restores. Incident closure = documented operator follow-up (`RECOVERED_PENDING_DECLARATION` → Declare). |
| `docker-test` / clean-start | Peripheral heartbeat stays **enabled** (`enabled=true`, `required=true`); rate limits remain relaxed — security semantics aligned with non–docker-test stacks. |
| Dashboard | **Operational follow-up** card for incidents awaiting declaration; link to `/audit-logs?integrity=1#integrity-lifecycle-panel`; shares React Query key `audit-chain-incidents` with Integrity panel. |
| Audit Logs | `?integrity=1` expands Integrity panel; `#integrity-lifecycle-panel` anchor + scroll for navigation from dashboard. |

---

## 3. Future evolution (optional chantiers — not required to close dossier)

- **Integrity hub:** Dedicated route(s) or lighter landing that aggregates “what needs attention” (follow-ups + gap list summary) without replacing Audit Logs listing.
- **Incidents table:** Migrate operational incidents list to the shared **DataTable + pagination + filters** pattern when volume or audit requirements justify it.
- **Cross-links:** From alert detail → operational follow-up / integrity deep-link for heartbeat-related alert types (read-only context).
- **Broader “operator backlog”:** Thin read-model over existing entities (incidents pending declaration, …) — avoid a generic ticketing layer unless requirements crystallize.

---

## 4. Close-out validation checklist

**Already exercised in recent validation (informal):**

- Degraded peripheral behavior when Admin checkpoint advancement stalls; recovery after new checkpoints.
- Incident lifecycle: `IN_PROGRESS` → `RECOVERED_PENDING_DECLARATION` → Declare → `CLOSED`.
- Alert auto-resolution vs incident pending declaration semantics.
- Dashboard follow-up visibility and link into Integrity.

**Recommended before formally closing a release touching this surface:**

| Area | Suggestion |
|------|------------|
| **Regression — roles** | Tenant Admin: confirm **no** Integrity panel / incidents API exposure (403 or hidden UI). |
| **Dashboard widget** | After Declare: widget disappears on refresh / within polling interval (same session). |
| **Deep link** | From dashboard link: Integrity opens expanded and incidents section is reachable without manual hunt. |
| **Browsers / locales** | Quick smoke in FR + EN if copy-sensitive paths touched. |
| **Automated tests** | Optional: extend Admin UI Playwright only if existing suite covers dashboard — **not mandatory** if manual validation above is documented for this release. Elective dashboard spot-checks should stay aligned with current overview DTO fields. |

---

## 5. Relation to OpenAPI / Postman

No Admin API contract change was required for the dashboard widget (reuses existing incidents GET). If future refactors add endpoints, refresh specs via the repo-approved workflow.
