# Vision Note — `V-2026-10-08-global-admin-announcements`
# Global Admin in-console announcements (Annonces) for tenant dashboards

## Metadata

- **ID:** `V-2026-10-08-global-admin-announcements`
- **Status:** `parked`
- **Parked at:** `2026-10-08`
- **Lane:** program (Admin API + Admin UI consumer contract; not hygiene)
- **Created at:** `2026-10-08`
- **Updated at:** `2026-10-08`
- **Captured by:** Marc (owner) / Alex (Product Direction)
- **Priority:** `P3` (comfort; yields to mobile Play closed-testing capacity)
- **Does not edit:** [`product-orientation-notes.md`](product-orientation-notes.md) (index updated
  after merge on `main`)
- **Backlog:** parked row in [`../backlog/index.md`](../backlog/index.md) `## Parked` (same PR)

## Intent

Give a **Global Admin** (platform operator, corporate-IT style) a pragmatic 80/20 way to publish an
**in-console announcement** that Tenant Admins see on their dashboard — maintenance window,
downtime, community-alpha DB reset, upgrade, notable change, what’s new.

This is **not** an internal messaging system, inbox, ticket desk, or email fan-out. Short-term
baseline without the feature remains acceptable: email from Marc + ezkey.org (no published SLA),
plus the stopgap env-driven instance description (`EZKEY_ORGANIZATION_DESCRIPTION` — header /
sidebar / login; redeploy to change).

**G0 verdict (2026-10-08, CR2): `parked`** with re-entry triggers — no active evaluator Tenant
Admins on the community instance today; trigger (a) not met. Locks and CR2 amendments are recorded
so re-entry needs no second G0. This PR does **not** authorize application implementation.

## Decisions 2026-10-08 (Marc) — critical review #2

| ID | Decision | Where applied |
| -- | -------- | ------------- |
| **Verdict** | **PARKED** with re-entry triggers (no active evaluator Tenant Admins on community today; (a) not met) | Metadata status; Timing; backlog `## Parked` row |
| **S2-2** | True dismiss for every severity including critical; **no** collapsed strip. Single localStorage key per browser holding last dismissed `id:version`. Cheap to revisit later. | Product locks §3; wireframe note; Walk Gate |
| **S2-3** | Server-side selection: greatest `visible_from ≤ now`, tie-break highest id. Global Admin list derived status: Scheduled / Live / Shadowed / Expired | Product locks §3 / §5; Walk Gate |
| **§3 read** | No dedicated rate limits on announcements. Current row is nullable `currentAnnouncement` on `GET /api/v1/dashboard/overview` — not a separate endpoint | Product locks §5; Consequences |
| **S3-8** | No soft delete and **no Withdraw** (pattern absent in Ezkey). Physical delete, audited — consistent with [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) §2.2 Delete | Product locks §2a / §4 / §5; Rejected; Walk Gate |
| **S2-5** | Placement: Global Admin dashboard **below** Global health cards; Tenant Admin dashboard **top**. At M1: « Annonce de l’opérateur » row in signal model | Product locks §3 / §3b; Consequences |
| **S2-4** | Grants in `apply-grants.sql` / `verify-grants.sh` + role-matrix row; read failure is **silent** (render nothing) | Consequences; Product locks §5 |
| **S2-6** | Lane = **program** (~30–40 file inventory); not hygiene | Metadata Lane; inventory below |
| **S2-7** | Single timed Walk Gate; drop collapsed strip and withdraw from the checklist | Timing / Walk Gate |
| **S3-9** | Security locks: text nodes; CSS `pre-line`; auto-link only `https?://`; 500-char server limit; reject control and bidi characters; payload without author; text treated as public | Product locks §5a |
| **Gates** | Lighten as for #711: M1 brief = G1; security spot check in PR review; Julie + Isabelle share one Walk Gate; G3 = one smoke line | Timing milestones |
| **Inconsistencies** | V-2026-10-07 on `main` @ `8a86090f` (normal relative link); status `parked` | Relationship; Metadata |

## Motivation

Today there is **no in-product channel** from Global Admin to Tenant Admins. On the community alpha,
Marc can email known operators; anonymous evaluator Tenant Admins (self-registration, no email on
signup) are reached only via ezkey.org. Self-hosted operators have redeploy-time instance
name/description. That posture is honest under no-SLA while the community count stays below
re-entry — but comparable self-hosted operator consoles commonly ship a thin announcement banner.

Capacity constraint: current strategic priority is **mobile Play production**
([`V-2026-10-07-multi-tenant-closed-testing-demo-app`](V-2026-10-07-multi-tenant-closed-testing-demo-app.md)).
Announcements must not compete with that milestone for craft capacity. **Mobile Play testers are
out of reach by design** — they are not Tenant Admins on the dashboard path.

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
tenants**. Both fit the self-hosted operator-console pattern.

## Verified facts (pointers)

| Topic | Where |
| ----- | ----- |
| **Dashboard density** | [`dashboard.tsx`](../../../ezkey-admin-ui/src/pages/dashboard.tsx) — Global Admin-only alerts / batch-health / incidents; shared stats, activity, quick actions |
| **Signal model** | [`dashboard-widget-signal-model.md`](../../components/admin-ui/dashboard-widget-signal-model.md) — severity colours; conditional cards hidden when healthy |
| **Batch-health quiet** | [`dashboard-batch-health-quiet.ts`](../../../ezkey-admin-ui/src/lib/dashboard-batch-health-quiet.ts) — React state only |
| **Integrity collapse** | [`checkpoint-quiet-collapse.ts`](../../../ezkey-admin-ui/src/lib/checkpoint-quiet-collapse.ts) — URL only; **no** localStorage |
| **localStorage today** | Language, timezone, last username only (not dashboard chrome) |
| **Sidebar roles** | [`sidebar.tsx`](../../../ezkey-admin-ui/src/components/layout/sidebar.tsx) — Global-only: Tenants, Integrity, Alerts, Encryption keys |
| **Instance description stopgap** | Public `GET /api/v1/public/instance-info` ([`PublicInstanceInfoController`](../../../ezkey-admin-api/src/main/java/org/ezkey/admin/controller/PublicInstanceInfoController.java)); env `EZKEY_ORGANIZATION_DESCRIPTION` ([`CONFIGURATION.md`](../../../ezkey-admin-api/CONFIGURATION.md)) — redeploy; **not** a feature |
| **Audit event types** | `ezkey_audit_log.event_type VARCHAR(50)` — touches [`EventType`](../../../ezkey-core/src/main/java/org/ezkey/audit/domain/EventType.java), [`EventTypeFamily`](../../../ezkey-core/src/main/java/org/ezkey/audit/domain/EventTypeFamily.java), [`AdminAuditConstants`](../../../ezkey-admin-api/src/main/java/org/ezkey/admin/constants/AdminAuditConstants.java), UI audit maps |
| **Optimistic lock** | JPA `@Version` + body `version`; mismatch → **409** (no ETag) |
| **Pagination** | [`docs/PAGINATION_GUIDELINES.md`](../../../docs/PAGINATION_GUIDELINES.md) — default size 20 |
| **DB grants** | [`scripts/db/apply-grants.sql`](../../../scripts/db/apply-grants.sql), [`verify-grants.sh`](../../../scripts/db/verify-grants.sh), [`DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md) |
| **Lifecycle Delete** | [`LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) §2.2 — Delete = physical removal |
| **Flyway** | [`db/migration`](../../../ezkey-core/src/main/resources/db/migration/) — V24 at craft baseline; next migration at build time |
| **No Markdown stack** | No Markdown / sanitizer / editor lib in [`ezkey-admin-ui/package.json`](../../../ezkey-admin-ui/package.json) |
| **System tenant** | Cannot host Tenant Admins ([`AdminProvisioningService`](../../../ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java)) |
| **Related** | **P-094** mobile remote signed release announcements (deferred-unfunded); feedback-channel V-\* rejects a second mail pipeline |

## Product locks (settled by Marc 2026-10-08 + CR2 amendments)

Settled by Marc (2026-10-08) via Alex; CR2 amendments settled same day (table above). Do not reopen
v1 scope in execution unless a new product lock supersedes this section.

| # | Settled lock | Rationale |
| - | ------------ | --------- |
| 1 | **Name:** “Announcements” / FR **« Annonces »** — not “Messages” | Messaging is the explicit non-goal |
| 2 | **v1 data model:** small announcements table (not a single hard-coded slot) | Overlap, history list, and physical delete need rows — not because Grafana lacks scheduling |
| 2a | Fields: plain text (~500 chars, line breaks, URLs auto-linked); severity `info` \| `warning` (**avertissement**) \| `critical` (**critique**); `visible_from` required (default now); `visible_until` optional (**strongly encouraged**); `@Version`; created/updated by+at. **Physical delete** (no `deleted_at`, no Withdraw) — audited | Aligns with lifecycle Delete canon |
| 2b | **Audience v1:** all Tenant Admins **and** Global Admins; **no targeting** | Keeps leak surface and craft small |
| 3 | **Display v1:** at most **one** announcement — server selects greatest `visible_from ≤ now`, tie-break highest id. If none active → render **nothing**. **True dismiss** for every severity including critical (« Oui, j’ai vu » / “Got it”): widget gone (no collapsed strip). Single **localStorage** key per browser holding last dismissed `id:version` (edit → version bump → widget returns). **No** server-side per-user read tracking. Marc: cheap to revisit dismiss UX later | Fixes prior collapse/re-expand contradiction |
| 3a | **Critical severity:** dashboard widget only in v1 — **no** thin banner on every page; same true dismiss as other severities | Every-page critical banner remains a **v2 candidate** |
| 3b | **Placement:** Tenant Admin dashboard = **top**; Global Admin dashboard = **below** the Global health cards | Avoid burying open-alerts / health under the operator’s own notice |
| 4 | **Global Admin nav:** « Annonces » (`roles: GLOBAL_ADMIN`) — paginated list + create/edit/**physical delete** + widget preview; list shows derived status Scheduled / Live / Shadowed / Expired. **Tenant Admin:** widget only; **no** nav item in v1 | Mirrors Global-only Alerts / Integrity pattern |
| 5 | **API:** Global Admin CRUD (paginated list; create; update with `version` → 409; physical delete). Current active announcement = nullable `currentAnnouncement` on `GET /api/v1/dashboard/overview` — **not** a separate read endpoint. **No** dedicated announcement rate limits. Audit: `ANNOUNCEMENT_CREATED` / `ANNOUNCEMENT_UPDATED` / `ANNOUNCEMENT_DELETED`. Read failure is **fail-open** (silent: render nothing) | Overview already refreshed; rate-limit policy reserves limits for high-abuse paths |
| 5a | **Security (S3-9):** render as text nodes with CSS `white-space: pre-line`; auto-link only `^https?://` with `rel="noopener noreferrer"`; 500-char limit enforced server-side; reject control and bidi-override characters; tenant-facing payload = id, version, severity, body, window (**no author**); treat text as **public** (self-registration → authenticated Tenant Admin quickly) | Spot check locks for M1 / PR review |
| 6 | **Not on unauthenticated login page in v1** | Would publish operator notices publicly |

### Dashboard widget wireframe (ASCII) — orientation only

```
+------------------------------------------------------------------+
|  [warning]  Maintenance — community DB reset Sunday 02:00 UTC     |
|  Expect ~30 min Admin API downtime. Details: https://ezkey.org…  |
|                                              [ Oui, j'ai vu ]    |
+------------------------------------------------------------------+
|  (Tenant Admin: widget at top of dashboard)                      |
|  (Global Admin: widget below Global health cards)                |
+------------------------------------------------------------------+
```

Heading orientation (M1): « Annonce de l’opérateur » so a Tenant Admin does not read it as a
system alert. On dismiss: **zero chrome** (no collapsed strip) while that `id:version` is stored;
on edit (version bump) or a different selected row: widget returns. When nothing is active: zero
chrome.

### What this is / is not

| Is | Is not |
| -- | ------ |
| Operator → tenants (and Global peers) notice board | Messaging, inbox, threads, @mentions |
| One active notice on the dashboard | Status page, email blast, push fan-out |
| Global Admin authored, Tenant Admin consumed | Tenant-to-tenant or end-user channel |
| Plain-text maintenance / what’s-new signal | Release-notes CMS or Markdown blog |

### Program inventory (~30–40 files — S2-6)

| Area | Touchpoints |
| ---- | ----------- |
| **Core** | Flyway next migration, entity, repository, 3 `EventType` values + family (`EventTypeFamilyTest`) |
| **Admin API** | Service, controller (list/create/update/delete), DTOs, mapper, `@PreAuthorize`, audit constants, overview field, `ENDPOINT.md` section |
| **Database** | `apply-grants.sql` (`ezkey_admin` SELECT/INSERT/UPDATE only), `verify-grants.sh` denial check, role-matrix row |
| **Admin UI** | Route + sidebar, `PaginatedTable` list, form + preview, widget + dismiss helper, 2 audit maps, EN/FR i18n |
| **Contract** | clean-start, `update-specs`, Orval, Bruno folder |
| **Tests** | Service unit, MockMvc (403/409), Vitest dismiss helper |

No crypto; no lifecycle cascade. Fits one agent-crafted PR; what competes with Play is review/gate
time, not code volume.

## Rejected / deferred (with re-entry signal)

| Item | Disposition | Re-entry signal |
| ---- | ----------- | --------------- |
| Markdown/HTML editor + preview | **Deferred v2** — no lib in stack; adds sanitizer + XSS review for ~500-char notice | Global Admins repeatedly need formatting beyond line breaks/links → evaluate common renderer + sanitizer (Christophe review) |
| Tenant-side history page (« Annonces » dated list) | **Deferred v2** | Tenants ask “what changed recently” or a release-notes need appears |
| Per-tenant / multi-tenant targeting | **Deferred v2** | Operator must address a subset; must guarantee **no cross-tenant leakage** (GitLab public-API caution) |
| Critical severity as thin banner on **every** page | **Deferred v2 candidate** — settled **not** in v1 | Operators need critical notices unavoidable outside the dashboard |
| Collapsed dismiss strip while active | **Deferred** — Marc chose true dismiss for all severities (CR2); cheap to revisit | Operators ask to peek without full re-show, or critical-must-remain-visible pressure |
| Soft delete / Withdraw pattern | **Rejected** — absent in Ezkey; Delete = physical removal ([`LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) §2.2) | Would need an explicit new lifecycle convention |
| Dedicated “current active” endpoint | **Rejected for v1** — use overview field; dedicated endpoint only with v2 every-page banner | Every-page banner funded |
| Per-operation announcement rate limits | **Rejected** — not a high-abuse public surface | Abuse evidence on announcement writes |
| Mandatory acknowledgement / read receipts | **Rejected for now** | Real compliance requirement |
| Email / push fan-out from Ezkey | **Rejected** | Feedback-channel note: no second mail pipeline |
| Full messaging / inbox | **Rejected** | Explicit non-goal |

## Timing and gates

### Baseline today (acceptable while parked)

| Channel | Role |
| ------- | ---- |
| Email from Marc + ezkey.org | Primary human notice for operators Marc can reach; **no SLA published** |
| `EZKEY_ORGANIZATION_DESCRIPTION` | Header / sidebar / login stopgap for one-off notices; redeploy Admin API |
| Closed-testing admin-api cutoff | Announced by email — acceptable |

### Capacity gate

Current strategic priority is **mobile Play production** —
[`V-2026-10-07-multi-tenant-closed-testing-demo-app`](V-2026-10-07-multi-tenant-closed-testing-demo-app.md)
(on `main` @ `8a86090f`). Announcements stay parked behind that milestone.

### G0 — Critical review #2 (closed)

**Verdict: PARKED** (2026-10-08). Amendments recorded above. Re-entry needs no second G0.

### Re-entry triggers (parked)

| Trigger | Condition |
| ------- | --------- |
| **(a)** Scale beyond email reach | More than **5** active Tenant Admins Marc cannot reach by email — **anonymous evaluators included** |
| **(b)** Planned community disruption | A planned community reset or upgrade affecting those Tenant Admins |
| **(c)** Adopter pull | An adopter request |

**Mobile Play testers are out of reach by design** — they are not Tenant Admins on the dashboard
path. Until re-entry, use email / ezkey.org and the `EZKEY_ORGANIZATION_DESCRIPTION` stopgap for
one-off notices.

Canonical backlog row: [`../backlog/index.md`](../backlog/index.md) `## Parked`. GitHub issue only
once v1 is **funded**.

### If GO later — milestones (lightened, as for #711)

| Milestone | What | Gate |
| --------- | ---- | ---- |
| **M1** | One-page craft brief (inventory + locks + Walk Gate outline) | **G1** = the brief itself (Marc) |
| **M2** | Build | **G2** `ci-gate` + Christophe security spot check **in PR review** + one shared Walk Gate (Julie + Isabelle) |
| **M3** | Deploy (Edgar) | **G3** = one smoke line (e.g. in `DEPLOYED.md`) |

### Walk Gate (single timed run — S2-7, adapted)

Overview refreshes every ~60 s — use ±60 s tolerance or manual refresh. Timestamped screenshot per
step. **No** collapsed strip; **no** withdraw (physical delete instead).

1. At T0, create A (warning, T0+2m to T0+5m). Global Admin list shows **Scheduled**; Tenant Admin
   dashboard shows nothing.
2. At T0+2m, Tenant Admin sees A. Dismiss → **zero chrome**. Reload → still gone.
3. Global Admin edits A → Tenant Admin sees it again (version bump).
4. Create B (info, starting now) → A shows **Shadowed**; dashboard shows B.
5. **Physically delete** B → A returns (Live).
6. At T0+5m → Tenant Admin sees no chrome.
7. Tenant Admin write attempt → 403. Stale-version update → 409.
8. Three audit types written with `tenant_id` NULL; Tenant Admin cannot see them.
9. Body with `<script>` and `javascript:alert(1)` renders as inert text and is not linked.

## Open questions for Marc

None remaining (settled 2026-10-08; CR2 decisions closed same day).

## Risks and blind spots

| Risk | Note |
| ---- | ---- |
| **Stale notices** | Encouraged `visible_until` + physical delete; still depends on Global Admin discipline |
| **Dashboard density** | Placement split Global vs Tenant; true dismiss → zero chrome; heading « Annonce de l’opérateur » |
| **XSS / Markdown creep** | S3-9 locks; Markdown stays deferred |
| **Public text honesty** | Self-registration → treat announcement body as public |
| **Capacity collision** | Parked behind Play closed-testing |
| **Empty-tenant install** | Only Global Admins see the widget until application tenants exist |
| **Grants omission** | S2-4 — migrate without grants breaks Admin API |

## Consequences (orientation only — if GO later)

| Surface | Consequence |
| ------- | ----------- |
| **Admin UI** | Global « Annonces » nav + list/form/preview; dashboard widget (Tenant top / Global below health); true dismiss |
| **Admin API** | CRUD + `currentAnnouncement` on overview; 409 on version conflict; physical delete audited |
| **Core / Flyway** | Announcements table; audit enum + family + constants + UI maps |
| **Database** | Grants + verify + [`DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md) row |
| **Signal model** | « Annonce de l’opérateur » row in [`dashboard-widget-signal-model.md`](../../components/admin-ui/dashboard-widget-signal-model.md) |
| **Security** | S3-9 locks; Global-only write; fail-open read; no login-page publish in v1 |
| **localStorage** | Single key: last dismissed `id:version` (per browser) |
| **Bruno / OpenAPI** | Contract refresh when funded build lands — not while parked |
| **ezkey.org / stopgap** | Unchanged; email + `EZKEY_ORGANIZATION_DESCRIPTION` remain baseline |

## Relationship to existing artifacts

| Artifact | Relationship |
| -------- | ------------- |
| [`V-2026-09-27-operator-and-mobile-feedback-channel`](V-2026-09-27-operator-and-mobile-feedback-channel.md) | Rejects second mail pipeline — announcements stay in-console |
| [`V-2026-10-07-multi-tenant-closed-testing-demo-app`](V-2026-10-07-multi-tenant-closed-testing-demo-app.md) | Capacity priority — Play closed-testing ahead; on `main` @ `8a86090f` |
| [`V-2026-09-26-public-alpha-posture-closeout`](V-2026-09-26-public-alpha-posture-closeout.md) | Alpha honesty / no SLA — baseline email + stopgap stay valid |
| [`../product-intent.md`](../product-intent.md) | Global Admin vs Tenant Admin role split; pragmatism |
| [`../../../docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) | Delete = physical removal; no soft-delete convention |
| **P-094** (deferred-unfunded) | Mobile remote signed release announcements — different surface |
| [`../backlog/index.md`](../backlog/index.md) | Parked row (this PR) |

## Promotion criteria

1. ~~Marc settles open questions~~ — **done** (2026-10-08).
2. ~~G0 critical review records GO or parked~~ — **parked** (CR2, 2026-10-08); amendments recorded.
3. This note merges to `main` once `ci-gate` green and Marc GO (Mathieu verifies) — no dormant branch.
4. On re-entry (trigger met): M1 brief → funded build; GitHub issue only once v1 is **funded**.
5. This vision PR does **not** authorize application code.

## Next step

Merge on `main` once `ci-gate` is green and Marc GO (Mathieu verifies). GitHub issue only when v1
is funded. Until a re-entry trigger fires, keep the baseline (email / ezkey.org +
`EZKEY_ORGANIZATION_DESCRIPTION` stopgap). Alex does not implement.
