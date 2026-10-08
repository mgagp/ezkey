# Vision Note — `V-2026-10-08-global-admin-announcements`
# Global Admin in-console announcements (Annonces) for tenant dashboards

## Metadata

- **ID:** `V-2026-10-08-global-admin-announcements`
- **Status:** `draft`
- **Lane:** hygiene / operator clarity (community alpha + self-hosted operator → tenants)
- **Created at:** `2026-10-08`
- **Updated at:** `2026-10-08`
- **Captured by:** Marc (owner) / Alex (Product Direction)
- **Priority:** `P3` (comfort; must not compete with mobile Play closed-testing capacity)
- **Does not edit:** [`product-orientation-notes.md`](product-orientation-notes.md) (index updated
  after merge on `main`); does **not** edit [`../backlog/index.md`](../backlog/index.md) in this PR

## Intent

Give a **Global Admin** (platform operator, corporate-IT style) a pragmatic 80/20 way to publish an
**in-console announcement** that Tenant Admins see on their dashboard — maintenance window,
downtime, community-alpha DB reset, upgrade, notable change, what’s new.

This is **not** an internal messaging system, inbox, ticket desk, or email fan-out. Short-term
baseline without the feature remains acceptable: email from Marc + ezkey.org (no published SLA),
plus the optional env-driven instance description.

This note records orientation and **product locks settled by Marc (2026-10-08)**. G0 (Mathieu’s
critical-review process) still decides **GO v1 now** vs **`parked`**. It does **not** authorize
application implementation in this PR.

## Motivation

Today there is **no in-product channel** from Global Admin to Tenant Admins. On the community alpha,
Marc announces maintenance and resets by email; self-hosted operators have only redeploy-time
instance name/description. That posture is honest and fine while tenant count stays personal — but
comparable self-hosted operator consoles commonly ship a thin announcement banner, and Ezkey’s
dashboard already carries Global-only density that a Tenant Admin never sees.

Capacity constraint: current strategic priority is **mobile Play production** (closed-testing demo
path). Announcements must not compete with that milestone for craft capacity.

## Gate question 1 — expected in modern comparable consoles?

**Answer (spot check 2026-10-08): yes**, for the right reference class.

| Reference | Pattern | Notes / link |
| --------- | ------- | ------------ |
| **GitLab Self-Managed** | Broadcast messages — banner or notification; start/end; optional dismiss; Markdown; path/role targeting | [Broadcast messages](https://docs.gitlab.com/administration/broadcast_messages/) — **caution:** API exposes all messages publicly regardless of targeting |
| **Vault Enterprise 1.16+** | Custom messages — banner/modal; login or post-login; start required, end optional; preview | [Custom messages](https://developer.hashicorp.com/vault/docs/ui/custom-messages) |
| **Grafana 11.4+** | Announcement banner GA — **one active banner**; start/end; Markdown + preview; severity colour | [Announcement banner](https://grafana.com/docs/grafana/latest/administration/announcement-banner/) |
| **Jira DC / Jenkins / Rancher** | System / UI announcement banners | Same operator-console class |
| **Auth0 / Duo / Entra·M365** | Vendor-run channels (status page, email, M365 Message center with dated history) | SaaS identity class — not self-hosted operator → tenants |
| **Keycloak / Boundary** | None found (search-based) | Gap, not a counter-pattern |
| **Okta** | End-user dashboard notifications (medium confidence; no official doc cited here) | Weak signal |

**Shared minimal core:** text, start, optional end, on/off or delete, severity colour.
**Common extras:** dismiss, audience, preview.
**Rare:** acknowledgement / read receipts; recipient-side history page (M365 vendor-run);
per-tenant targeting.

Ezkey is **self-hosted** (operator → tenants) and the community alpha behaves like **vendor →
tenants**. Both fit the self-hosted operator-console pattern, not the SaaS-vendor status-page
substitute alone.

## Verified facts (main `@ 90251d16` — pointers)

| Topic | Where |
| ----- | ----- |
| **Dashboard density** | [`dashboard.tsx`](../../../ezkey-admin-ui/src/pages/dashboard.tsx) — Global Admin-only alerts / batch-health / incidents; shared stats, activity, quick actions |
| **Batch-health quiet** | [`dashboard-batch-health-quiet.ts`](../../../ezkey-admin-ui/src/lib/dashboard-batch-health-quiet.ts) — React state only; auto-collapse when quiet |
| **Integrity collapse** | [`checkpoint-quiet-collapse.ts`](../../../ezkey-admin-ui/src/lib/checkpoint-quiet-collapse.ts) — URL only; **no** localStorage |
| **localStorage today** | Language, timezone, last username only (not dashboard chrome) |
| **Sidebar roles** | [`sidebar.tsx`](../../../ezkey-admin-ui/src/components/layout/sidebar.tsx) — `roles` filter; Global-only: Tenants, Integrity, Alerts, Encryption keys; role from `session.adminType` `GLOBAL_ADMIN` \| `TENANT_ADMIN` |
| **No runtime banner** | Header shows `instanceName` / `instanceDescription` from public `GET /api/v1/public/instance-info` ([`PublicInstanceInfoController`](../../../ezkey-admin-api/src/main/java/org/ezkey/admin/controller/PublicInstanceInfoController.java)); env `EZKEY_ORGANIZATION_NAME` / `DESCRIPTION` ([`CONFIGURATION.md`](../../../ezkey-admin-api/CONFIGURATION.md)) — redeploy to change; **zero-code stopgap**, not a feature |
| **Audit event types** | `ezkey_audit_log.event_type VARCHAR(50)` (no CHECK) — V2/V4 migrations under [`db/migration`](../../../ezkey-core/src/main/resources/db/migration/); adding a type touches [`EventType`](../../../ezkey-core/src/main/java/org/ezkey/audit/domain/EventType.java), [`EventTypeFamily`](../../../ezkey-core/src/main/java/org/ezkey/audit/domain/EventTypeFamily.java), [`AdminAuditConstants`](../../../ezkey-admin-api/src/main/java/org/ezkey/admin/constants/AdminAuditConstants.java), UI [`audit-event-type.ts`](../../../ezkey-admin-ui/src/lib/audit-event-type.ts) + [`audit-event-type-family.ts`](../../../ezkey-admin-ui/src/lib/audit-event-type-family.ts) |
| **Optimistic lock** | JPA `@Version` + body `version`; mismatch → **409** (no ETag) — existing Admin convention |
| **Pagination** | [`docs/PAGINATION_GUIDELINES.md`](../../../docs/PAGINATION_GUIDELINES.md) — default size 20, `createdAt` DESC typical |
| **Rate limits** | Admin per-operation `ezkey.admin-operations.rate-limit.*` ([`CONFIGURATION.md`](../../../ezkey-admin-api/CONFIGURATION.md)) |
| **Flyway** | [`db/migration`](../../../ezkey-core/src/main/resources/db/migration/) — latest on this SHA: **V24** |
| **No Markdown stack** | No Markdown / sanitizer / editor lib in [`ezkey-admin-ui/package.json`](../../../ezkey-admin-ui/package.json) |
| **System tenant** | `tenant_id=1` / `is_system_tenant` (V5); **cannot** host Tenant Admins ([`AdminProvisioningService`](../../../ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java)) — with no application tenants, only Global Admins would see an announcement |
| **No prior V-\*/I-\*/issue** | No announcements vision/issue found; related: **P-094** remote signed release announcements (mobile) deferred-unfunded 2026-08-17 ([corpus ablation](../hygiene/corpus-ablation/2026-08-cursor-plans-pass.md)); feedback-channel V-\* rejects a second mail pipeline |

## Product locks (settled by Marc 2026-10-08)

Settled by Marc (2026-10-08) via Alex. Do not reopen v1 scope in execution unless a new product lock
supersedes this section. G0 still decides **timing** (GO now vs parked) — not the locks below.

| # | Settled lock | Rationale |
| - | ------------ | --------- |
| 1 | **Name:** “Announcements” / FR **« Annonces »** — not “Messages” | “Messages” implies inbox/messaging — explicit non-goal |
| 2 | **v1 data model:** small announcements table (not a single hard-coded slot) | Scheduling implies rows |
| 2a | Fields: plain text (~500 chars, line breaks, URLs auto-linked); severity `info` \| `warning` (**avertissement**) \| `critical` (**critique**); `visible_from` required (default now); `visible_until` optional (**strongly encouraged** so stale notices auto-expire — main failure mode of a single-slot MVP); `@Version`; created/updated by+at; soft delete (`deleted_at` / `deleted_by`) | Plain text = React escaping, no XSS surface |
| 2b | **Audience v1:** all Tenant Admins **and** Global Admins; **no targeting** | Keeps leak surface and craft small |
| 3 | **Display v1:** at most **one** announcement — most recent active (Grafana model). If none active → render **nothing** (no empty closed widget). Dashboard widget at top; « Oui, j’ai vu » / “Got it” collapses; dismissal in **localStorage** keyed by announcement id + version (edit re-opens); re-expand shows same text. **No** server-side per-user read tracking | Protects already-dense dashboard; matches existing localStorage restraint |
| 3a | **Critical severity:** dashboard widget only in v1 — **no** thin banner on every page | Default accepted; every-page critical banner is a **v2 candidate** (see deferred) |
| 4 | **Global Admin nav:** new « Annonces » item (`roles: GLOBAL_ADMIN`) — paginated list + create/edit/soft delete + dashboard-widget preview. **Tenant Admin:** widget only; **no** nav item in v1 | Mirrors Global-only Alerts / Integrity pattern in sidebar |
| 5 | **API:** Global Admin CRUD (paginated list; create; update with `version` → 409; soft delete) + one read for any authenticated admin returning only the **current active** announcement. Rate-limited per admin-operations convention. Audit: `ANNOUNCEMENT_CREATED` / `ANNOUNCEMENT_UPDATED` / `ANNOUNCEMENT_DELETED` with before/after summary. Plain text only — **no HTML** | Aligns with pagination, optimistic lock, audit touchpoints above |
| 6 | **Not on unauthenticated login page in v1** | Would publish operator notices publicly; reconsider for community alpha only via existing public instance-info pattern if signalled |

### Dashboard widget wireframe (ASCII) — orientation only

```
+------------------------------------------------------------------+
|  [warning]  Maintenance — community DB reset Sunday 02:00 UTC     |
|  Expect ~30 min Admin API downtime. Details: https://ezkey.org…  |
|                                              [ Oui, j'ai vu ]    |
+------------------------------------------------------------------+
|  (existing dashboard: stats / activity / quick actions …)        |
+------------------------------------------------------------------+
```

When dismissed (localStorage keyed by id + version): widget gone until the operator re-expands
or the Global Admin edits the row (version bump → dismiss key misses → widget returns). When no
row is active (`visible_from`…`visible_until`, not soft-deleted): **zero chrome** — do not leave a
collapsed empty shell on an already dense Tenant Admin dashboard.

### What this is / is not

| Is | Is not |
| -- | ------ |
| Operator → tenants (and Global peers) notice board | Messaging, inbox, threads, @mentions |
| One active notice on the dashboard | Status page, email blast, push fan-out |
| Global Admin authored, Tenant Admin consumed | Tenant-to-tenant or end-user channel |
| Plain-text maintenance / what’s-new signal | Release-notes CMS or Markdown blog |

## Rejected / deferred (with re-entry signal)

| Item | Disposition | Re-entry signal |
| ---- | ----------- | --------------- |
| Markdown/HTML editor + preview | **Deferred v2** — no lib in stack; adds sanitizer + XSS review for ~500-char notice | Global Admins repeatedly need formatting beyond line breaks/links → evaluate common renderer + sanitizer (Christophe review) |
| Tenant-side history page (« Annonces » dated list) | **Deferred v2** | Tenants ask “what changed recently” or a release-notes need appears |
| Per-tenant / multi-tenant targeting | **Deferred v2** | Operator must address a subset; must guarantee **no cross-tenant leakage** (GitLab public-API caution) |
| Critical severity as thin banner on **every** page (not just dashboard) | **Deferred v2 candidate** — settled **not** in v1 (Marc 2026-10-08) | Operators need critical notices unavoidable outside the dashboard |
| Mandatory acknowledgement / read receipts | **Rejected for now** — rare in market; compliance-flavoured; server tracking per user | Real compliance requirement |
| Email / push fan-out from Ezkey | **Rejected** | Feedback-channel note: no second mail pipeline |
| Full messaging / inbox | **Rejected** | Explicit non-goal |

## Timing and gates

### Baseline today (acceptable short term)

| Channel | Role |
| ------- | ---- |
| Email from Marc + ezkey.org | Primary human notice; **no SLA published** |
| Optional instance description env var | Redeploy-time stopgap only |
| Closed-testing admin-api cutoff | Currently announced by email — acceptable |

### Capacity gate

Current strategic priority is **mobile Play production** — see closed-testing demo path
`V-2026-10-07-multi-tenant-closed-testing-demo-app` (branch
`cursor/v-2026-10-07-closed-testing-demo-app-e481`; **not yet on `main` @ 90251d16**). Announcements
must **not** compete with that milestone for craft capacity.

### G0 — Critical review (before any craft)

Mathieu’s critical-review process (product locks above are already settled; G0 decides timing).

| Reviewer | Focus |
| -------- | ----- |
| **Patrick** | Craft size estimate |
| **Christophe** | Security spot check (XSS, authz, audit, login-page exposure) |
| **Julie** | Dashboard density / UX (one widget, dismiss, no empty chrome) |
| **Isabelle** | Testability of the G2 checklist below |

**G0 outcomes:** **GO v1 now**, or **`parked`** with re-entry triggers + backlog row. This note
merges to `main` after that verdict (no dormant branch). GitHub issue only once v1 is **funded**.

### Proposed re-entry triggers (if parked)

| Trigger | Example |
| ------- | ------- |
| **(a)** Scale beyond personal reach | \>5 active Tenant Admins not known personally to Marc |
| **(b)** Scheduled disruption affecting outsiders | First scheduled DB reset / upgrade / admin-api outage on the community instance affecting testers or evaluators not reachable directly |
| **(c)** Adopter pull | A real adopter asks for it |

If parked: set status `parked` and add a row under [`../backlog/index.md`](../backlog/index.md)
`## Parked` with the re-entry trigger (canonical backlog; GitHub issues reserved for bounded,
funded work per `AGENTS.md` labels). **Do not** invent that backlog row from this vision PR.

### If GO — milestones (not “phases”)

| Milestone | What | Gate |
| --------- | ---- | ---- |
| **M1** | Craft brief (Patrick, Christophe, Julie) | **G1** Marc |
| **M2** | Build | **G2** `ci-gate` + Christophe PASS + Julie UI walk PASS + Isabelle QA PASS — create/edit; conflict 409; expiry; soft delete; dismiss + edit reopen; no active → nothing rendered; Tenant Admin cannot write; audit rows |
| **M3** | Deploy (Edgar) | **G3** live smoke |

## Open questions for Marc

None remaining (settled 2026-10-08).

## Risks and blind spots

| Risk | Note |
| ---- | ---- |
| **Stale single-slot** | Mitigated by rows + encouraged `visible_until`; still depends on Global Admin discipline |
| **Dashboard density** | Julie G0 — one widget max; nothing when inactive; dismiss must not leave empty chrome |
| **XSS / Markdown creep** | Plain text + React escape in v1; Markdown stays deferred with sanitizer review |
| **Cross-tenant targeting later** | v2 only; GitLab public-API caution is the cautionary tale |
| **Capacity collision** | Must stay parked or thin behind Play closed-testing — not a parallel craft track |
| **Empty-tenant install** | System tenant has no Tenant Admins — only Global Admins see the widget until tenants exist |

## Consequences (orientation only — if GO later)

| Surface | Consequence |
| ------- | ----------- |
| **Admin UI** | Global « Annonces » nav + list/form/preview; dashboard widget (top); Tenant: widget only |
| **Admin API** | CRUD + current-active read; rate limits; 409 on version conflict |
| **Core / Flyway** | New announcements table (next migration after V24 at craft time); audit enum + family + constants + UI maps |
| **Security** | Plain text / React escape; Global-only write; authenticated read of active row; no login-page publish in v1 |
| **localStorage** | First dashboard-chrome use: dismiss key = id + version |
| **Login / public** | Unchanged in v1 |
| **Bruno / OpenAPI** | Contract refresh only if GO and M2 lands — not this vision PR |
| **ezkey.org** | Unchanged; email + site remain the baseline outbound channel |

## Relationship to existing artifacts

| Artifact | Relationship |
| -------- | ------------- |
| [`V-2026-09-27-operator-and-mobile-feedback-channel`](V-2026-09-27-operator-and-mobile-feedback-channel.md) | Rejects second mail pipeline — announcements stay **in-console**, no email fan-out |
| `V-2026-10-07-multi-tenant-closed-testing-demo-app` | Capacity priority — Play closed-testing ahead of this; note pending merge on branch `cursor/v-2026-10-07-closed-testing-demo-app-e481` |
| [`V-2026-09-26-public-alpha-posture-closeout`](V-2026-09-26-public-alpha-posture-closeout.md) | Alpha honesty / no SLA — baseline email posture stays valid |
| [`../product-intent.md`](../product-intent.md) | Global Admin vs Tenant Admin role split; pragmatism |
| **P-094** (deferred-unfunded) | Mobile remote **signed release** announcements — different surface; do not conflate |
| [`../backlog/index.md`](../backlog/index.md) | Parked row only **if** G0 parks — not edited in this PR |

## Promotion criteria

1. ~~Marc settles open questions (term, severity, every-page critical banner)~~ — **done**
   (2026-10-08).
2. G0 critical review (Mathieu’s process: Patrick / Christophe / Julie / Isabelle) records **GO**
   or **`parked`**.
3. Note merges to `main` after the G0 verdict (no dormant branch).
4. On GO: M1 craft brief → G1 Marc before any implementation branch; GitHub issue only once v1 is
   **funded**.
5. On parked: status flip + backlog `## Parked` row with re-entry trigger — no `I-*` / GitHub
   issue unless later funded as bounded work.
6. This vision PR does **not** authorize application code.

## Next step

Mathieu’s critical-review process (Patrick size, Christophe security spot check, Julie dashboard
density, Isabelle testability) decides **G0: GO v1 now** vs **`parked`** (with re-entry triggers +
row in [`../backlog/index.md`](../backlog/index.md) `## Parked`). Merge this note to `main` after
that verdict — no dormant branch. GitHub issue only once v1 is funded. Alex does not implement. No
craft brief until **GO**. Keep mobile Play closed-testing capacity uncontested.
