# Vision Note — `V-2026-10-07-multi-tenant-closed-testing-demo-app`
# Multi-tenant closed-testing demo app (third-party integrator on ezkey.online)

## Metadata

- **ID:** `V-2026-10-07-multi-tenant-closed-testing-demo-app`
- **Status:** `under-review`
- **Lane:** `D` (community alpha / mobile Play closed-testing friction)
- **Created at:** `2026-10-07`
- **Updated at:** `2026-10-08`
- **Captured by:** Marc / Alex (Product Direction)
- **Priority:** `P1` (serves current strategic objective: mobile Play production; ahead of
  temporary evaluator console access —
  [`V-2026-09-26-temporary-evaluator-console-access`](V-2026-09-26-temporary-evaluator-console-access.md))
- **Does not edit:** [`product-orientation-notes.md`](product-orientation-notes.md) (index updated
  after merge on `main`)

## Intent

Ship a **new, small demo application** on the community instance (`ezkey.online`) that behaves like
a real third-party integrator: production-grade passwordless login via the public Integration API,
then a minimal protected success page. One deployment serves every provisioned tester tenant.

Goal: give Google Play **closed-testing** testers of the Ezkey mobile app a realistic end-user path
(third-party app → Ezkey on device) without pasting integration keys into Acme every session, and
without treating Admin UI tenant-admin login as a stand-in for the real product use case.

This note records **product locks** (Marc settlements of 2026-10-08), verified facts, a light gate
path, and risks. It does **not** authorize application implementation in this PR.

## Decisions 2026-10-08 (Marc)

- **C1 — Play rule:** 12 testers **opted in** for 14 consecutive days (not daily logins); cohort grows
  progressively from people close to Marc (many non-developers); no hard deadline — the window rolls
  as long as needed; demo makes usage realistic but does not advance the Play counter; Acme / Admin UI
  remain fallback if the demo slips.
- **C2 — Tenancy:** Marc runs a **Northwind Portal** tenant for mobile-only testers (enrollments only,
  no admin steps for them); keep ≥1 second tenant run by a close collaborator (e.g. Reine) to catch
  cross-tenant issues (same email in two tenants) — balanced functional-test strategy for the test
  project, not “one tenant per tester”; access code per tenant stays for admins who plug their own.
- **C3 — Security framing:** Ezkey is a **pull** model (username creates a pending request the user
  goes to consume in the Ezkey app — no push; avoiding push fatigue is a core value); Northwind demo
  uses Ezkey as **sole factor** (integrator may also use it as second factor elsewhere); `/t/<code>`
  may appear in history/logs — code is already anonymous; residual abuse risk accepted for alpha;
  M1 design notes below (do not overstate risk).
- **C4 — Code location:** TS SDK stays in its separate public repo (not officially published to npm
  for now); Northwind Portal lives as a monorepo subfolder mirroring Acme
  ([`ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/) → propose **`ezkey-demo-app-northwind/`**);
  CI follows existing path-filtered jobs wired into `ci-gate` when code lands.
- **C5 — Keep it simple:** lighten gates to (1) this note, (2) M1 brief, (3) smoke pilot with a first
  tester; collapse the rest; indicative non-binding horizon below.

## Motivation

Play closed testing is on the critical path to production
([`MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md): Internal →
Closed → Production; new developer accounts need **12 testers opted in for 14 consecutive days**).
The Play counter advances from **opt-in continuity**, not from daily logins. The demo exists so
usage is realistic when testers do exercise the app; it does **not** itself satisfy the 14-day rule.

Today the evaluator path through the Acme demo
([`ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/), `demo-acme.ezkey.online`) requires pasting
an integration key + secret into the Acme server-side HTTP session every session
([`DemoApiKeyConfigService`](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/service/DemoApiKeyConfigService.java)),
single tenant per session. That flow is excellent **self-serve pedagogy** (EXP1 lineage;
[`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md))
but heavy for a closed-testing cohort that includes non-developers. Logging into the Admin UI as
tenant admin repeatedly is artificial. The honest product story is: end user → third-party app →
Ezkey on device.

If Northwind Portal slips, **Acme and Admin UI remain valid fallback paths** so the Play cohort is
not blocked on this demo.

## Verified facts (repo research on `main` @ `76451a14`)

| Topic | Fact |
| ----- | ---- |
| **Integration API** | Three ops: create auth attempt (by `enrollmentId` or `userIdentifier`), long-poll wait, cancel. HTTP Basic `ikey:skey`. One API key → one integration → one tenant (max 5 active keys per integration; `expires_at`, `ip_whitelist`, revoke). Rate limits per key: 100/min create, 200/min wait/cancel. TTL default 120s. Statuses: `PENDING` / `READ` / `ACCEPTED` / `REJECTED` / `INVALID` / `EXPIRED`. No `CANCELLED` (cancel → `EXPIRED`; 409 if already finished). `userIdentifier` lookup is scoped to the integration; duplicates → error. |
| **Admin UI login** | [`login.tsx`](../../../ezkey-admin-ui/src/pages/login.tsx) + [`map-passwordless-wait-error.ts`](../../../ezkey-admin-ui/src/lib/map-passwordless-wait-error.ts) + EN/FR [`locales/*/login.json`](../../../ezkey-admin-ui/src/locales/en/login.json) talk to **Admin API** `/api/v1/admin/auth/*`, **not** the Integration API. Reuse for the demo = UX / visual / i18n reuse, rewired to a demo backend. |
| **Pull model** | Entering a username in an integrator app does **not** push a notification; it creates a pending auth request that the user intentionally opens and consumes in the Ezkey app. Avoiding push fatigue is a core Ezkey value. Factor posture is the **integrator’s choice**: sole/first factor (Admin UI dogfood today) or second factor. |
| **Secrets** | Integration secret is server-side only → a **backend is mandatory**; no browser-only app. |
| **Java SDK** | In-repo [`ezkey-sdk/java`](../../../ezkey-sdk/java/) — aligned; living demo = Acme. |
| **TypeScript SDK** | Real SDK = separate public repo `mgagp/ezkey-sdk-typescript` (not officially published to npm for now; Node ≥ 18, server-side only; last push 2026-08-23; minor spec drift). Contains a single-tenant reference demo (`apps/demo-ui` Vite + `apps/demo-api` NestJS). In-repo [`ezkey-sdk/javascript`](../../../ezkey-sdk/javascript/) is a different, effectively obsolete package (Admin API M2M removed by PR #501). |
| **Deploy** | No Workers in this repo. Admin UI / site on Cloudflare Pages; APIs + Acme in Docker on Lightsail behind Caddy. Host map locked in PR #609 ([`Caddyfile.ezkey-online`](../../../experimental-hybrid/lightsail/Caddyfile.ezkey-online)). Ledger: [`docs/lightsail/community/DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md). |
| **Site** | [`community-instance.html`](../../../sites/ezkey-org/community-instance.html) and [`community-guided-tour.html`](../../../sites/ezkey-org/community-guided-tour.html) are dense; **no** closed-testing tester page exists today. |
| **CI** | Path-filtered jobs in [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml); Acme is under the backend filter (`ezkey-demo-app-acme/**`); final always-running job is **`ci-gate`**. |

## Product locks (settled by Marc 2026-10-08 unless noted)

1. **Pure third-party integrator.** The demo talks to Ezkey **only** through the public Integration
   API via the official TypeScript SDK. Never reads/writes the Ezkey core DB; never uses Admin API or
   privileged paths. A future provisioning store belongs to the **demo app**, not the core DB.
2. **Secrets server-side only.** Never in browser, repo, logs, or client bundles; encrypted at rest
   / host secret store; per-tenant entries. **Never log the access code.**
3. **Access code (code d’accès).** Multi-tenant, single deployment. Tenant selection uses a short
   opaque **access code** chosen by Marc. Mapping `access code → ikey/skey` lives **only** in the
   demo app’s server-side config. Ezkey never sees the access code.
   - Login form: **text field** (not a dropdown) for the access code + username.
   - Personal link `demo.ezkey.online/t/<code>` **pre-fills and hides** the access-code field
     (primary path for bookmarked use). The code may appear in browser history / proxy logs; it is
     already anonymous (no tenant-specific description shown). Residual abuse risk is **accepted for
     alpha**.
   - Generic error that does **not** say which of access code or username is wrong.
4. **Login UX parity** with Admin UI login states (waiting with `expiresAt` countdown, optional
   2-digit challenge, rejected / expired / timeout / network / rate-limited / unknown-user) **plus**
   server-side cancel via Integration API cancel. EN/FR.
5. **Success page = minimal protected page.** Tenant, username, timestamp, short honesty line,
   logout; short session. No tenant data access.
6. **SDK showcase split.** Acme stays the **Java** SDK pedagogy demo (unchanged). Northwind dogfoods
   the **TypeScript** SDK (refresh as needed; **no npm publish required** for this work). Stack is a
   **Patrick craft decision at M1** against: server-held secret, login UX parity, living SDK, minimal
   new deploy surface (**preference:** Docker-on-Lightsail-behind-Caddy over Workers unless
   Patrick + Christophe + Edgar justify otherwise).
7. **Tenancy for testing (balanced functional strategy).**
   - **Primary:** Marc owns/runs a **Northwind Portal** tenant. Mobile-only testers validate
     enrollments there — **no Admin UI steps for them**.
   - **Second tenant (required):** at least one other tenant run by a close collaborator (e.g.
     Reine) so functional tests catch **cross-tenant** issues (multi-tenant / same email in two
     tenants). This is part of the **test project**, not only of mobile production.
   - **Optional:** admins who plug their own tenant keep an access code of their own (v1 manual
     provisioning by Marc; N small; revoke = offboarding).
8. **Factor posture (Northwind).** In the demo, Ezkey is the **sole factor** (same eat-your-own-dogfood
   posture as Admin UI). Integrators elsewhere may use Ezkey as sole/first factor or as a second
   factor — that choice is theirs.
9. **Hostname:** new subdomain on `ezkey.online` (placeholder `demo.ezkey.online`); host-map
   amendment owned by Edgar.
10. **Demo brand.** Fictional company **Northwind Portal**.
11. **Code location.** Propose monorepo folder **`ezkey-demo-app-northwind/`**, same convention as
    [`ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/). No special case. When code lands, add a
    path filter and wire it into **`ci-gate`** like other components.
12. **Closed-testing recruitment — out of scope of this note.** Broader commercial QA recruitment
    (Mathieu / Isabelle / QA Sphere) is a separate track and does not own or block this demo. Marc’s
    progressive personal cohort (C1) is how the Play opt-in window is fed in practice.

### Screen wireframe (ASCII)

```
+------------------------------------------+
|  Northwind Portal                        |
|------------------------------------------|
|  Access code  [____________]  (hidden    |
|                if /t/<code>)             |
|  Username     [____________]             |
|                                          |
|              [  Sign in  ]               |
+------------------------------------------+

+------------------------------------------+
|  Northwind Portal                        |
|------------------------------------------|
|  Waiting for approval…                   |
|  Challenge:  4 2                         |
|  Expires in: 01:47                       |
|                                          |
|              [  Cancel  ]                |
+------------------------------------------+

+------------------------------------------+
|  Northwind Portal                        |
|------------------------------------------|
|  Bienvenue <user> — accès vérifié        |
|  Tenant · timestamp · short honesty line |
|                                          |
|              [  Logout  ]                |
+------------------------------------------+
```

### Tester paths (pilot)

**Mobile-only (primary):** Marc provisions username + enrollment under the Northwind Portal tenant;
tester installs the closed-test app, binds the enrollment, bookmarks `demo.ezkey.online/t/<code>`,
signs in with username only, then **goes to the Ezkey app to consume the pending authentication
request** (pull model). No Admin UI steps.

**Cross-tenant functional check:** same pattern on the second collaborator-run tenant (e.g. Reine),
including same-email-in-two-tenants cases.

**Admin who plugs their own tenant (optional):** activation → integration + API key → enrollment with
`userIdentifier` under that integration → secure key transfer to Marc → Marc adds one config line →
personal `/t/<code>` link.

## Rejected intentions / non-goals

- Browser-only app with keys in the client.
- Keys in Ezkey core DB / demo reading core tables.
- Public tenant dropdown or directory.
- Cloning the full Admin UI.
- New SDKs (Python / .NET / PHP / Go) now; npm-publishing the TS SDK as a prerequisite.
- Changing Acme (scope stays pedagogy / Java SDK).
- Self-provisioning in v1 (see **Later**).
- New auth-attempt status `CANCELLED` in v1.
- Implementing application code from this vision PR alone.
- Claiming a public “Java + TS cover 80–90% of backends” percentage (unverified).
- Owning closed-testing recruitment or treating daily logins as the Play production rule.
- Push notifications from the integrator login form.

## Risks and blind spots

| Risk | Note |
| ---- | ---- |
| **Play cohort vs this demo** | Play production needs **12 testers opted in × 14 consecutive days**. The demo does not advance that counter. Cohort grows progressively; no self-imposed deadline. If the demo slips, **Acme / Admin UI** remain fallback. Separate Mathieu / Isabelle recruitment track stays independent. |
| **Onboarding trap** | Enrollments must carry a `userIdentifier` under the integration whose key is provisioned. Mobile-only path must stay free of Admin UI steps. |
| **Secret handling via Marc** | Weakest link for optional “own tenant” path; acceptable for the pilot. Self-provisioning is the fast-follow. |
| **Market coverage claim** | “Java + TS cover 80–90%” is unverified — do not claim publicly. Integration API is three REST calls; a short curl doc covers other stacks better than new SDKs. |
| **Enumeration / abuse** | Residual risk on `/t/<code>` accepted for alpha. Mitigations for M1 (below) — do not overstate. |
| **Two TS SDK packages** | Deprecate / archive in-repo `ezkey-sdk/javascript` integration usage (Fred) once the TS SDK refresh lands. |
| **Supportability** | Seb needs a runbook: tenant-not-provisioned vs key revoked vs Ezkey down. |
| **Prioritization** | Ahead of temporary evaluator console access ([`V-2026-09-26-temporary-evaluator-console-access`](V-2026-09-26-temporary-evaluator-console-access.md); locked, not started). |

## Milestones and gates (light)

Three valued passes only — Marc is not the sole GO on a five-gate chain. Collapse former build /
deploy / publish gates into the smoke-pilot pass; evidence stays real, ceremony stays light.

| Pass | What | Owners | Evidence / go–no-go |
| ---- | ---- | ------ | ------------------- |
| **1 — This note** | Vision lock (this document) | Alex; Marc | Status `under-review` → Marc merges → locks treated as settled |
| **2 — M1 brief** | Design brief: stack, architecture, TS SDK refresh scope (no npm publish required), tenancy ops, security notes, UX intent, host-map / deploy feasibility | Mathieu routes → Patrick (craft), Christophe (security), Julie (operability / UX), Edgar (deploy) | Brief exists per [`tracer-bullet-brief.template.md`](../../templates/tracer-bullet-brief.template.md); plan-hardened per [`methodology/README.md`](../../methodology/README.md); reviews recorded; **then start building** |
| **3 — Smoke pilot** | Build + deploy + first-tester smoke on community instance | Mathieu (+ agents); Christophe / Julie / Isabelle as needed; Edgar publish; Marc onboards first tester | `ci-gate` green when code lands; live smoke with Northwind Portal tenant; first tester autonomous via phone (pull consume); REX captured; then widen cohort progressively |

### M1 design notes (Christophe to confirm)

Capture in the brief — modest effort, alpha-honest:

- Backend **global per-IP rate limit** (same spirit as Admin UI), optionally reinforced by a
  **Cloudflare rate-limit rule**.
- `Referrer-Policy: no-referrer` on demo responses.
- **Never log** the access code.
- Optional **IP allowlisting** if useful for the pilot cohort.
- Enumeration-safe generic errors; short session; fail-graceful revoked / unprovisioned tenant.
- Secret storage at rest; tester→Marc transfer channel for optional own-tenant path (no plain email).

### Horizon indicatif (non-binding)

Order-of-magnitude only. This is a **serious lab** project, promised to no one. If a step slips,
re-evaluate at that step — no self-imposed pressure on the Play 14-day window.

| Step | Horizon (indicative) |
| ---- | -------------------- |
| Note merge | days |
| M1 brief + reviews | ~1–2 weeks |
| Build + community deploy + first-tester smoke | ~2–4 weeks after brief GO |
| Widen cohort toward 12 opted-in | rolls as long as needed (weeks+) |

## Parallel track (independent)

Can start on Marc’s GO without waiting on build:

- **Audrey** — major editorial pass on
  [`community-instance.html`](../../../sites/ezkey-org/community-instance.html) and
  [`community-guided-tour.html`](../../../sites/ezkey-org/community-guided-tour.html) (EN+FR):
  split by audience, shorter paragraphs or several pages.
- Optional short closed-testing tester page on ezkey.org when the pilot is ready (Audrey / Alex /
  Edgar) — not a separate Marc-only gate.

## Later (separate `V-*` when signalled)

**Self-provisioning:** tester connects their tenant to the demo through a one-time invite code;
demo-owned encrypted store — so keys never transit through Marc. Not in v1 scope.

## Open questions for Marc

None remaining (C1–C5 settled 2026-10-08).

## Consequences (when executed later)

Orientation only — promote via M1 brief then execution on a **new branch**. No application code in
this PR.

| Surface | Consequence |
| ------- | ----------- |
| **New demo app** | `ezkey-demo-app-northwind/` — Northwind Portal login + minimal success page; Integration API via TS SDK; access-code config owned by the demo; CI path filter → `ci-gate`. |
| **TS SDK repo** | Refresh as needed; living demo dogfood; **no official npm publish** required for this work. |
| **Acme / Java SDK** | Unchanged pedagogy path; remains Play-cohort fallback. |
| **Tenancy** | Marc’s Northwind Portal tenant (mobile-only) + ≥1 collaborator tenant (cross-tenant checks). |
| **Host map / Caddy** | New subdomain; Edgar amendment + ledger entry. |
| **ezkey.org** | Optional short closed-testing tester page when pilot-ready. |
| **In-repo `ezkey-sdk/javascript`** | Hygiene deprecate/archive after TS SDK refresh (Fred). |
| **Support** | Seb runbook (provisioning / revoke / down). |
| **Temporary evaluator console access** | Remains locked vision; this work is prioritized ahead. |

## Relationship to existing artifacts

| Artifact | Relationship |
| -------- | ------------- |
| [`../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md) | Play track; **12 testers opted in for 14 consecutive days**. |
| [`V-2026-08-02-mobile-official-play-release-posture`](V-2026-08-02-mobile-official-play-release-posture.md) | Broader mobile official release posture; this note is the closed-testing **realistic activity** friction slice. |
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | Parent funnel / Acme pedagogy lineage; Acme stays (and is fallback). |
| [`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md) | Community host honesty and host-map bounds. |
| [`V-2026-09-26-temporary-evaluator-console-access`](V-2026-09-26-temporary-evaluator-console-access.md) | Temporary evaluator console access — locked, not started; this demo is prioritized ahead. |
| [`V-2026-06-30-sdk-dogfood-integrity-posture`](V-2026-06-30-sdk-dogfood-integrity-posture.md) | SDK dogfood honesty; one living demo per official SDK. |
| [`../product-intent.md`](../product-intent.md) | Adopter posture and honesty of claims. |
| [`../../../docs/lightsail/community/DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md) | Community deploy ledger (smoke-pilot pass). |
| [`../../templates/tracer-bullet-brief.template.md`](../../templates/tracer-bullet-brief.template.md) | M1 brief shape. |
| [`../../../ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/) | Folder/CI convention to mirror (`ezkey-demo-app-northwind/`). |

## Promotion criteria

Promote (spawn M1 brief / execution) when:

1. Marc merges this note — locks treated as settled.
2. M1 brief lands with craft / security / operability / deploy reviews recorded.
3. Execution opens on a **new branch** (not this vision PR); smoke pilot follows.

## Next step

Marc reviews this `under-review` note. On merge, Mathieu routes the M1 brief (Patrick / Christophe /
Julie / Edgar). Then start a smoke pilot with a first tester — no five-gate Marc-only chain.
Closed-testing cohort continues to widen progressively under C1; recruitment commercial track stays
separate. Alex does not implement.
