# Vision Note — `V-2026-10-07-multi-tenant-closed-testing-demo-app`
# Multi-tenant closed-testing demo app (third-party integrator on ezkey.online)

## Metadata

- **ID:** `V-2026-10-07-multi-tenant-closed-testing-demo-app`
- **Status:** `draft`
- **Lane:** `D` (community alpha / mobile Play closed-testing friction)
- **Created at:** `2026-10-07`
- **Updated at:** `2026-10-08`
- **Captured by:** Marc / Alex (Product Direction)
- **Priority:** `P1` (serves current strategic objective: mobile Play production; ahead of Mode C
  implementation)
- **Does not edit:** [`product-orientation-notes.md`](product-orientation-notes.md) (index updated
  after merge on `main`)

## Intent

Ship a **new, small demo application** on the community instance (`ezkey.online`) that behaves like
a real third-party integrator: production-grade passwordless login via the public Integration API,
then a minimal protected success page. One deployment serves every provisioned tester tenant.

Goal: reduce daily friction for Google Play **closed-testing** testers of the Ezkey mobile app so
they can generate realistic end-user activity without pasting integration keys into Acme every
session, and without treating Admin UI tenant-admin login as a stand-in for the real product use
case (an end user logging into a third-party app protected by Ezkey).

This note records **proposed product locks** (with Marc settlements of 2026-10-08 called out),
verified facts, milestones/gates, and risks. It does **not** authorize application implementation
in this PR.

## Motivation

Play closed testing is on the critical path to production
([`MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md): Internal →
Closed → Production; new developer accounts need **12 testers opted in for 14 days**). Testers
should log in **daily**. Today the evaluator path through the Acme demo
([`ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/), `demo-acme.ezkey.online`) requires pasting
an integration key + secret into the Acme server-side HTTP session every session
([`DemoApiKeyConfigService`](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/service/DemoApiKeyConfigService.java)),
single tenant per session.

That Acme flow is excellent **self-serve pedagogy** (EXP1 lineage;
[`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md))
but repetitive for a closed-testing cohort. Logging into the Admin UI as tenant admin repeatedly is
artificial. The honest product story is: end user → third-party app → Ezkey MFA on device.

## Verified facts (repo research on `main` @ `76451a14`)

| Topic | Fact |
| ----- | ---- |
| **Integration API** | Three ops: create auth attempt (by `enrollmentId` or `userIdentifier`), long-poll wait, cancel. HTTP Basic `ikey:skey`. One API key → one integration → one tenant (max 5 active keys per integration; `expires_at`, `ip_whitelist`, revoke). Rate limits per key: 100/min create, 200/min wait/cancel. TTL default 120s. Statuses: `PENDING` / `READ` / `ACCEPTED` / `REJECTED` / `INVALID` / `EXPIRED`. No `CANCELLED` (cancel → `EXPIRED`; 409 if already finished). `userIdentifier` lookup is scoped to the integration; duplicates → error. |
| **Admin UI login** | [`login.tsx`](../../../ezkey-admin-ui/src/pages/login.tsx) + [`map-passwordless-wait-error.ts`](../../../ezkey-admin-ui/src/lib/map-passwordless-wait-error.ts) + EN/FR [`locales/*/login.json`](../../../ezkey-admin-ui/src/locales/en/login.json) talk to **Admin API** `/api/v1/admin/auth/*`, **not** the Integration API. Reuse for the demo = UX / visual / i18n reuse, rewired to a demo backend. |
| **Secrets** | Integration secret is server-side only → a **backend is mandatory**; no browser-only app. |
| **Java SDK** | In-repo [`ezkey-sdk/java`](../../../ezkey-sdk/java/) — aligned; living demo = Acme. |
| **TypeScript SDK** | Real SDK = separate repo `mgagp/ezkey-sdk-typescript` (`ezkey-integration-sdk` 1.0.0, Node ≥ 18, server-side only; last push 2026-08-23; minor spec drift: stale `demoMitm*` fields from PR #536, missing newer 401s). Contains a single-tenant reference demo (`apps/demo-ui` Vite + `apps/demo-api` NestJS) defaulting to EXP1 hostnames. In-repo [`ezkey-sdk/javascript`](../../../ezkey-sdk/javascript/) is a different, effectively obsolete package (Admin API M2M removed by PR #501) → two “TS SDKs” confusion. |
| **Deploy** | No Workers in this repo. Admin UI / site on Cloudflare Pages; APIs + Acme in Docker on Lightsail behind Caddy. Host map locked in PR #609 ([`Caddyfile.ezkey-online`](../../../experimental-hybrid/lightsail/Caddyfile.ezkey-online)). Ledger: [`docs/lightsail/community/DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md). |
| **Site** | [`community-instance.html`](../../../sites/ezkey-org/community-instance.html) (~1000 words) and [`community-guided-tour.html`](../../../sites/ezkey-org/community-guided-tour.html) (~1850 words, EN+FR) are dense; **no** closed-testing tester page exists today. |

## Product locks (proposed by Product Direction, pending Marc merge)

Do not treat the full set as settled until Marc merges this note (gate **G0**). Items marked
**Settled by Marc (2026-10-08)** below are locked and must not be reopened in execution.

1. **Pure third-party integrator.** The demo talks to Ezkey **only** through the public Integration
   API via an official SDK. Never reads/writes the Ezkey core DB; never uses Admin API or privileged
   paths. (Dogfood + honesty.) A future provisioning store belongs to the **demo app**, not the core
   DB.
2. **Secrets server-side only.** Never in browser, repo, logs, or client bundles; encrypted at rest
   / host secret store; per-tenant entries.
3. **Access code (code d’accès) — Settled by Marc (2026-10-08).** Multi-tenant, single deployment.
   Tenant selection uses a short opaque **access code** chosen by Marc. The mapping
   `access code → ikey/skey` (per tenant) exists **only** in the demo app’s own server-side
   configuration. Nothing is added to the Ezkey core DB; Ezkey never sees the access code.
   - Login form: a **text field** (not a dropdown) for the access code + a username field.
   - Personal link `demo.ezkey.online/t/<code>` **pre-fills and hides** the access-code field
     (primary day-to-day path: tester bookmarks the link).
   - Generic error that does **not** say which of access code or username is wrong (no public
     tenant list/dropdown).
   - **Request routing:** access code → demo config key pair → Integration API (HTTP Basic) →
     enrollment lookup by `userIdentifier` within that integration.
4. **Login box UX parity** with Admin UI login states (waiting with server `expiresAt` countdown,
   optional 2-digit challenge, rejected / expired / timeout / network / rate-limited / unknown-user
   messages) **plus** a real server-side cancel via Integration API cancel (which Admin UI login
   lacks today). EN/FR.
5. **Success page = minimal protected page**, not a stripped Admin UI clone. Show tenant, username,
   timestamp, a short honest explanation of what was cryptographically verified, logout; short
   session. No tenant data access.
6. **SDK showcase split.** Acme stays the **Java** SDK self-serve pedagogy demo (unchanged, no new
   scope). The new demo dogfoods the **TypeScript** SDK (refresh SDK + publish/version it as part of
   the work), so each official SDK has one living demo. Final stack is a **Patrick craft decision at
   gate G1** against these criteria: server-held secret, login UX parity, living SDK, minimal new
   deploy surface (**preference:** existing Docker-on-Lightsail-behind-Caddy path over introducing
   Cloudflare Workers, unless Patrick + Christophe + Edgar justify otherwise), reuse of the TS SDK
   repo demo scaffolding where sensible.
7. **Provisioning v1 = manual by Marc.** Host config / secret file keyed by **access code**; adding
   a tenant may require a restart; N is small (~12–20). Tester is advised to set the API key
   `ip_whitelist` to the demo egress IP and can revoke anytime (= offboarding; demo must fail
   gracefully with an honest “this tenant is not (or no longer) connected” message). Secret transfer
   tester → Marc must use a secure, expiring channel chosen by Christophe; **plain email is
   rejected**.
8. **Hostname:** new subdomain on `ezkey.online` (placeholder `demo.ezkey.online`); requires a
   host-map amendment owned by Edgar.
9. **Demo brand — Settled by Marc (2026-10-08).** Fictional company **Northwind Portal** (signals a
   third-party app, not the Ezkey console).
10. **Closed-testing recruitment — Settled by Marc (2026-10-08): out of scope of this note.**
    Recruitment and the human test campaign are coordinated separately by Mathieu and Isabelle
    (commercial QA tool POC with QA Sphere). This demo is not blocked by that track, and that track
    is not owned by this vision note.

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

### Tester onboarding (pilot, manual) — 6 steps

1. Marc onboards the tester person-to-person; tester obtains a tenant (**activation code** → Admin UI).
2. Tester creates an **integration + API key** in Admin UI.
3. Tester creates an enrollment **with a `userIdentifier` under that same integration**, then binds
   the device.
4. Tester transfers the key pair to Marc (secure, expiring channel — Christophe; no plain email).
5. Marc adds one demo config line (`access code → ikey/skey`) and sends the personal link
   `demo.ezkey.online/t/<code>`.
6. Tester is autonomous: bookmarks the link, signs in daily with username only (access code hidden).

## Rejected intentions / non-goals

- Browser-only app with keys in the client.
- Keys in Ezkey core DB / demo reading core tables.
- Public tenant dropdown or directory.
- Cloning the full Admin UI.
- New SDKs (Python / .NET / PHP / Go) now.
- Changing Acme (scope stays pedagogy / Java SDK).
- Self-provisioning in v1 (see **Later**).
- New auth-attempt status `CANCELLED` in v1 (open question: today the mobile sees a cancelled
  attempt as expired; candidate `I-*` later).
- Implementing application code from this vision PR alone.
- Claiming a public “Java + TS cover 80–90% of backends” percentage (unverified — see risks).
- Owning closed-testing recruitment or the human test campaign inside this vision note (separate
  Mathieu / Isabelle track).

## Risks and blind spots

| Risk | Note |
| ---- | ---- |
| **Play cohort vs this demo** | Play production still needs **12 testers × 14 days**. Closed-testing recruitment and the human test campaign are a **separate track** (Mathieu + Isabelle; QA Sphere POC) and must not wait on this demo — and this demo must not pretend to own that track. |
| **Onboarding trap** | Enrollments must carry a `userIdentifier` and live under the **same integration** whose key is provisioned. The tester page must say so plainly. |
| **Secret handling via Marc** | Weakest link; acceptable only for the pilot cohort. Self-provisioning is the fast-follow. |
| **Market coverage claim** | “Java + TS cover 80–90%” is an **unverified assumption**: plausible for Ezkey’s target (Spring / Node backends), but .NET, Python, and PHP are significant. Since the Integration API is three REST calls with Basic auth, a short “integrate in 3 calls (curl)” doc page covers other stacks better than new SDKs. **Do not claim the percentage publicly.** |
| **Enumeration / abuse** | Access-code / username enumeration risk → demo-layer rate limiting per IP and per access code **on top of** per-key limits; generic errors only. |
| **Two TS SDK packages** | Confuse developers. Deprecate / archive in-repo `ezkey-sdk/javascript` integration usage (hygiene, Fred) once the TS SDK refresh lands. |
| **Supportability** | Seb needs a runbook: how to tell tenant-not-provisioned vs key revoked vs Ezkey down. |
| **Prioritization** | This serves the current strategic objective (mobile production) and goes **ahead of** Mode C implementation ([`V-2026-09-26-temporary-evaluator-console-access`](V-2026-09-26-temporary-evaluator-console-access.md) stays locked; implementation not started). |

## Milestones, owners, and gates

Five gates. Each gate: owner of the GO, verifiable evidence, go / no-go rule.

| Milestone | What | Owners | Gate | GO owner | Evidence / go–no-go |
| --------- | ---- | ------ | ---- | -------- | ------------------- |
| **M0** Vision lock | This note; locks settled | Alex (Product Direction) | **G0** | Marc | Marc merges this note → locks treated as settled |
| **M1** Craft + security design | Stack choice, architecture, TS SDK refresh scope, Java SDK alignment check; security obligations; UX intent; host-map + deploy feasibility → tracer-bullet brief | Mathieu routes; Patrick (craft), Christophe (security), Julie (UX), Edgar (host-map / deploy) | **G1** | Marc | Brief exists per [`tracer-bullet-brief.template.md`](../../templates/tracer-bullet-brief.template.md); plan-hardened per [`methodology/README.md`](../../methodology/README.md) two-stage human review; Marc GO on brief |
| **M2** Build | Mathieu orchestrates cloud agents per brief (TS SDK refresh **first**, then demo app) | Mathieu (+ agents); Christophe review; Julie UI walk; Isabelle QA | **G2** | Marc (via evidence pack) | CI job `ci-gate` green ([`ci.yml`](../../../.github/workflows/ci.yml)); Christophe security review PASS; Julie UI walk PASS per [`ui-walk-done-gate.md`](../ui-walk-done-gate.md); Isabelle exploratory QA PASS on local stack covering at least: accepted, rejected, expired/timeout, server cancel, unknown access code, unknown username, duplicate `userIdentifier`, revoked key, rate limited, network loss, EN/FR |
| **M3** Deploy | Host-map amendment, secrets on host, full-SHA ledger entry, rollback path; live smoke; support runbook | Edgar (**never Seb** for publish); Isabelle smoke; Seb runbook | **G3** | Marc | Live smoke PASS with Marc’s own tenant as tenant zero; ledger entry in [`DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md); Seb support runbook handed over |
| **M4** Publish + pilot | Short dedicated Android closed-testing tester page on ezkey.org; message framing; first close tester onboarded | Audrey (page), Alex (framing), Marc (pilot onboarding), Edgar (publish) | **G4** | Marc | Marc approves copy; Edgar publishes; pilot tester logs in autonomously via phone; REX captured; then scale toward 12 |

### M1 design obligations (capture for the brief)

| Owner | Obligation |
| ----- | ---------- |
| **Patrick** | Stack choice against lock #6 criteria; architecture; TS SDK refresh + publish/version scope; Java SDK alignment check (Acme unchanged). |
| **Christophe** | Secret storage at rest; tester→Marc transfer channel (no plain email); enumeration-safe errors (access code / username); demo-layer rate limits; short session model; fail-graceful revoked / unprovisioned tenant. |
| **Julie** | Northwind Portal login box + success page UX intent; access-code text field + `/t/<code>` hide/pre-fill; reuse map from Admin UI login states / EN+FR; cancel affordance. |
| **Edgar** | Host-map amendment feasibility (`demo.ezkey.online` placeholder); Docker-on-Lightsail-behind-Caddy path vs any Workers exception. |

## Parallel track (independent)

Can start **before G1** on Marc’s GO:

- **Audrey** — major editorial pass on
  [`community-instance.html`](../../../sites/ezkey-org/community-instance.html) and
  [`community-guided-tour.html`](../../../sites/ezkey-org/community-guided-tour.html) (EN+FR):
  split by audience, shorter paragraphs or several pages, lighter play-by-play.

## Later (separate `V-*` when signalled)

**Self-provisioning:** tester connects their tenant to the demo through a one-time invite code;
demo-owned encrypted store — so keys never transit through Marc. Not in v1 scope.

## Open questions for Marc

None remaining (all settled 2026-10-08).

## Consequences (when executed later)

Orientation only — promote via tracer-bullet brief then execution on a **new branch**. No application
code in this PR.

| Surface | Consequence |
| ------- | ----------- |
| **New demo app** | Multi-tenant Northwind Portal login + minimal success page on community instance; Integration API only via official TS SDK; access-code config owned by the demo. |
| **TS SDK repo** | Refresh + publish/version as part of M2; living demo dogfood. |
| **Acme / Java SDK** | Unchanged pedagogy path. |
| **Host map / Caddy** | New subdomain; Edgar amendment + ledger entry. |
| **ezkey.org** | Short dedicated closed-testing tester page (explorer-first + developer section; how to join Play closed test). |
| **In-repo `ezkey-sdk/javascript`** | Hygiene deprecate/archive of obsolete integration usage (Fred) after TS SDK refresh. |
| **Support** | Seb runbook (provisioning / revoke / down). |
| **Mode C** | Remains locked vision; this work is prioritized ahead of Mode C implementation. |

## Relationship to existing artifacts

| Artifact | Relationship |
| -------- | ------------- |
| [`../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md) | Play track (Internal → Closed → Production); closed-testing cohort size drives urgency. |
| [`V-2026-08-02-mobile-official-play-release-posture`](V-2026-08-02-mobile-official-play-release-posture.md) | Broader mobile official release posture; this note is the closed-testing **daily activity** friction slice. |
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | Parent funnel / Acme pedagogy lineage; Acme stays. |
| [`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md) | Community host honesty and host-map bounds. |
| [`V-2026-09-26-temporary-evaluator-console-access`](V-2026-09-26-temporary-evaluator-console-access.md) | Mode C — locked, not started; this demo is prioritized ahead. |
| [`V-2026-06-30-sdk-dogfood-integrity-posture`](V-2026-06-30-sdk-dogfood-integrity-posture.md) | SDK dogfood honesty; one living demo per official SDK. |
| [`../product-intent.md`](../product-intent.md) | Adopter posture and honesty of claims. |
| [`../../../docs/lightsail/community/DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md) | Community deploy ledger (M3). |
| [`../../templates/tracer-bullet-brief.template.md`](../../templates/tracer-bullet-brief.template.md) | M1 brief shape. |

## Promotion criteria

Promote (spawn tracer-bullet brief / execution) when:

1. Marc merges this note (**G0**) — remaining proposed locks become settled (access code, brand, and
   recruitment scope already settled 2026-10-08).
2. M1 brief lands and Marc issues **G1** GO.
3. Execution opens on a **new branch** (not this vision PR).

## Next step

Keep this note as the **decision draft**. Marc merges to lock (**G0**), then Mathieu routes M1 craft
+ security design toward a plan-hardened tracer-bullet brief. Closed-testing recruitment remains on
the separate Mathieu / Isabelle track. Alex does not implement.
