# Vision Note — `V-2026-10-07-multi-tenant-closed-testing-demo-app`
# Multi-tenant closed-testing demo path (Acme + Northwind Portal tenant)

## Metadata

- **ID:** `V-2026-10-07-multi-tenant-closed-testing-demo-app`
- **Status:** `under-review`
- **Lane:** `D` (community alpha / mobile Play closed-testing friction)
- **Created at:** `2026-10-07`
- **Updated at:** `2026-10-08`
- **Captured by:** Marc / Alex (Product Direction)
- **Priority:** `P1` (serves **mobile app** Play production; platform backends stay alpha/lab —
  compass amended 2026-10-08 in
  [`V-2026-09-26-public-alpha-posture-closeout`](V-2026-09-26-public-alpha-posture-closeout.md))
- **Does not edit:** [`product-orientation-notes.md`](product-orientation-notes.md) (index updated
  after merge on `main`)

## Intent

Reduce friction for Google Play **closed-testing** testers of the Ezkey **mobile app** by giving them
a realistic third-party-integrator login path on the community instance — without pasting
integration keys every session, and without treating Admin UI tenant-admin login as the product use
case.

**Play path (locked):** small adjustments to the **existing** Acme demo
([`ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/), already at `demo-acme.ezkey.online`) —
access-code table → `(ikey, skey)` in config, `/t/{code}` feeding
[`DemoApiKeyConfigService`](../../../ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/service/DemoApiKeyConfigService.java),
generic errors, switch-code session safety. Keeps Acme’s portability and low tester friction. **No
new app folder, no new subdomain / Caddy / origin-cert work.**

**Northwind Portal** remains the name of **Marc’s demo tenant** (brand of the tenant under test), not
a separate application.

This note records product locks and Marc settlements. It does **not** authorize application
implementation in this PR.

## Decisions 2026-10-08 (Marc)

- **C1 — Play rule:** Personal accounts after 2023-11-13 need ≥12 testers **opted in continuously**
  for 14 days (not daily logins); cohort grows progressively from people close to Marc (many
  non-developers); no hard deadline; demo makes usage realistic for Google’s engagement questions but
  does not itself advance the opt-in counter; open closed-test opt-ins **now** (do not wait on Acme
  work).
- **C2 — Tenancy:** Marc runs the **Northwind Portal** tenant for mobile-only testers (enrollments
  only, no admin steps for them); ≥1 second tenant (collaborator) for cross-tenant oracles; **Reine**
  is the person enrolled in both tenants; access code per tenant stays for admins who plug their own.
- **C3 — Security framing:** Ezkey is a **pull** model — username creates a pending request the user
  **goes to consume in the Ezkey app** (no push; avoiding push fatigue is a core value); Northwind
  path uses Ezkey as **sole factor**; `/t/{code}` may appear in history/logs (code already anonymous);
  residual abuse risk accepted for alpha.
- **C4 — Code location (amended CR1):** Play path = **existing Acme** with small multi-tenant
  access-code adjustments; reuse current host, CI, and deploy. TS SDK dogfooding is a **separate
  follow-up / `I-*`**, no deadline, decoupled from Play. No `ezkey-demo-app-northwind/` for this path.
- **C5 — Keep it simple:** (1) this note, (2) one-page M1 brief, (3) smoke pilot with a first tester;
  indicative non-binding horizon below.

## Decisions 2026-10-08 (Marc) — critical review #1

- **S1-1 — Amend:** production target is the **mobile app** only; backends stay alpha/lab; compass +
  backlog index amended same PR; product-management consistency (mobile vs platform tags / branches)
  is a follow-up, not designed here.
- **S1-2 — Amend:** Play path = Acme + access codes (not a new Northwind app); TS SDK dogfood
  decoupled (`I-*`, no deadline); C4 updated; drop new subdomain/Caddy/origin-cert.
- **S2-3 — Amend:** open closed-test opt-ins now; feedback channel in pilot (link
  [`V-2026-09-27-operator-and-mobile-feedback-channel`](V-2026-09-27-operator-and-mobile-feedback-channel.md));
  App access demo credentials for Google reviewers decided at M1.
- **S2-4 — Amend:** one-page tester mission sheet EN/FR is a **mandatory** pilot deliverable
  (Android 12+, opt-in, camera scan from a second screen in release builds, never opt out, open app
  within 120 s after Sign in, 3–4 actions).
- **S2-5 — Amend:** three named oracles below; Reine enrolled in both tenants.
- **S2-6 — Amend:** challenge always on; key `ip_whitelist` = internal Docker network; named secure
  channel for QR + enrollment code. **Remove** tester IP allowlist and encrypted-at-rest requirement.
  Keep backend per-IP rate limit; Cloudflare rate-limit rule optional.
- **S3-7 — Amend:** Play publishing wording = personal accounts after 2023-11-13; continuously 14
  days; organization accounts exempt (and **ruled out** here — no legal entity).
- **S3-8 — Amend:** unknown user / 404 / duplicate folded into the generic error.
- **S3-9 — Amend:** verified-facts table links to [`docs/ENDPOINT.md`](../../../docs/ENDPOINT.md)
  sections instead of copying the Integration API contract.
- **§3 over-process — Amend:** one-page M1 brief; two targeted reviews (Christophe: secrets +
  challenge; Edgar: host); plan hardening only if a cold agent executes autonomously; remove Audrey
  editorial / Fred SDK deprecation / Seb runbook from this note; keep Admin UI-like states minimal
  (waiting with challenge + countdown, denied, expired, generic error).

## Motivation

Play closed testing is on the critical path to **mobile app** production
([`MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md)). The opt-in
counter advances from **continuous opt-in**, not daily logins — but Google also asks whether testers
exercised real product flows; a credible integrator path answers that. Platform backends remain
alpha/lab (compass 2026-10-08).

Today Acme
([`ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/), `demo-acme.ezkey.online`) already creates
auth attempts by `userIdentifier` with challenge and accepts server-side keys — but requires pasting
ikey/skey into the session every time. That pedagogy path
([`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md))
is heavy for non-developer closed testers. Small Acme adjustments remove that friction while keeping
the existing deploy surface.

**Organization Play account path is ruled out** for now (no legal entity). Stay on the personal
developer account and satisfy the 12×14 continuous opt-in rule.

## Verified facts (pointers — do not copy the contract)

| Topic | Where |
| ----- | ----- |
| **Pull / user-initiated consume** | [`ENDPOINT.md` § User-Initiated Polling Model](../../../docs/ENDPOINT.md) |
| **Integration API keys + rate limits** | [`ENDPOINT.md` § API Keys Authentication (Machine-to-Machine)](../../../docs/ENDPOINT.md) (create / use / rate limits) |
| **Create / wait / cancel auth attempts** | [`ENDPOINT.md` § Authentication Attempts](../../../docs/ENDPOINT.md) (`userIdentifier` scoped to integration; statuses; TTL) |
| **Admin UI login** | Admin API passwordless — UX reference only; demo talks Integration API |
| **Acme today** | [`ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/) — Java SDK living demo; host `demo-acme.ezkey.online` already on community Caddy |
| **Deploy / CI** | Lightsail + Caddy ledger [`DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md); Acme in backend path filter → `ci-gate` ([`ci.yml`](../../../.github/workflows/ci.yml)) |
| **TS SDK** | Separate public repo `mgagp/ezkey-sdk-typescript` — **not** on the Play path (follow-up `I-*`) |

## Product locks (settled)

1. **Acme Play path.** Multi-tenant access-code table in Acme config → `(ikey, skey)`; route
   `/t/{code}` pre-fills/hides the code and feeds `DemoApiKeyConfigService`; generic errors; switching
   `/t/A` → `/t/B` in the same browser session **replaces credentials and logs out** (switch-code
   safety). Reuse existing host `demo-acme.ezkey.online`. No new subdomain.
2. **Secrets server-side only.** Never in browser, repo, logs, or client bundles. Env / Docker secret
   / root-owned host file is enough for 2–3 tenants — **no encrypted-at-rest requirement** for this
   pilot. **Never log the access code.**
3. **Northwind Portal = Marc’s demo tenant** (name/brand of the tenant), not a separate app.
4. **Login UI states (minimal):** waiting with **challenge always on** + countdown; denied; expired;
   generic error (access code / username / 404 / duplicate all fold into one message). Short session.
   EN/FR as needed for the thin surface — not full Admin UI parity.
5. **Success page stays Acme’s minimal protected page** (or equivalent thin success). No Admin UI
   clone.
6. **TS SDK dogfood — out of this path.** Separate `I-*` / follow-up, no deadline. Acme continues to
   dogfood the Java SDK.
7. **Tenancy + oracles.**
   - Primary: Marc’s **Northwind Portal** tenant — mobile-only testers, no Admin UI steps.
   - Second tenant: collaborator-run (e.g. Reine’s admin role); **Reine** is enrolled in **both**.
   - Oracles: (1) one phone, enrollments from two tenants on the same install — pending requests hit
     the right enrollment; Purpose/Account labels correct; (2) same `userIdentifier` in two
     integrations — no cross-tenant leak; (3) demo `/t/A` → `/t/B` same session replaces credentials
     and disconnects.
8. **Factor posture:** sole factor on this demo path (integrator choice elsewhere may differ).
9. **Security controls (pilot):** always `challengeRequested=true`; API key `ip_whitelist` restricted
   to the **internal Docker network**; named secure channel for **QR + enrollment code** (activation
   secret — not plain email); backend global per-IP rate limit; Cloudflare rate-limit rule optional;
   `Referrer-Policy: no-referrer`. **No** tester IP allowlist.
10. **Play cohort ops:** open closed-test opt-ins **now**; never opt out (resets the 14-day window);
    feedback channel in the pilot; App access demo credentials for Google reviewers — **M1 decision**.
11. **Closed-testing commercial recruitment** (Mathieu / Isabelle / QA Sphere) remains a separate
    track and does not own this note.

### Screen wireframe (ASCII) — Acme surface, Northwind tenant

```
+------------------------------------------+
|  (Acme demo — Northwind Portal tenant)   |
|------------------------------------------|
|  Access code  [____________]  (hidden    |
|                if /t/{code})             |
|  Username     [____________]             |
|                                          |
|              [  Sign in  ]               |
+------------------------------------------+

+------------------------------------------+
|  Waiting for approval…                   |
|  Challenge:  4 2                         |
|  Expires in: 01:47                       |
+------------------------------------------+

+------------------------------------------+
|  Bienvenue <user> — accès vérifié        |
|  Tenant · timestamp · short honesty line |
|              [  Logout  ]                |
+------------------------------------------+
```

### Tester mission sheet (mandatory pilot deliverable, EN/FR, one page)

Must state clearly:

1. **Android 12+** required (iPhone excluded for this cohort).
2. Join the Play **closed test** (Google account + opt-in link); **never opt out**.
3. Enrollment: **camera scan of the QR from a second screen** (release builds have no manual entry).
4. Bookmark `demo-acme.ezkey.online/t/{code}`; Sign in with username only.
5. Within **120 seconds** after Sign in, **open the Ezkey app** and consume the pending request.
6. Do **3–4 actions** across sessions (e.g. approve, deny, challenge match) so engagement is real.
7. How to send feedback (channel from
   [`V-2026-09-27`](V-2026-09-27-operator-and-mobile-feedback-channel.md)).

### Tester path (mobile-only)

Marc provisions username + enrollment under Northwind Portal; delivers QR + enrollment code via the
named secure channel; tester installs closed-test app, scans from a second screen, bookmarks
`/t/{code}`, signs in, opens Ezkey within 120 s. No Admin UI steps for the tester.

## Rejected intentions / non-goals

- New `ezkey-demo-app-northwind/` (or any new demo app) on the Play path.
- New subdomain / Caddy host-map / origin-cert for this work.
- Coupling TS SDK refresh or npm publish to Play production.
- Browser-only keys; keys in Ezkey core DB; public tenant directory.
- Full Admin UI UX parity; unknown-user as a distinct message.
- Tester IP allowlist; encrypted-at-rest mandate for 2–3 tenant keys.
- Organization Play account path (no legal entity).
- Self-provisioning in v1; application code from this vision PR alone.
- Audrey site editorial, Fred SDK deprecation, Seb support runbook inside this note.

## Risks and blind spots

| Risk | Note |
| ---- | ---- |
| **Engagement / App access** | Opt-ins open now; mission sheet + feedback channel; App access credentials decided at M1. |
| **Non-developer logistics** | Second screen for QR; Android 12+; 120 s pull window — mission sheet owns this. |
| **Switch-code session** | Acme currently keeps keys after logout — must fix when adding `/t/{code}`. |
| **Enumeration / abuse** | Residual `/t/{code}` risk accepted for alpha; per-IP rate limit; generic errors. |
| **Secret channels** | QR + enrollment code and optional own-tenant key transfer — named secure channels only. |

## Milestones and gates (light)

| Pass | What | Owners | Evidence |
| ---- | ---- | ------ | -------- |
| **1 — This note** | Vision lock | Alex; Marc | Merge → locks settled |
| **2 — M1 brief** | **One-page** brief: Acme access-code design, challenge-always-on, key whitelist, mission sheet outline, App access credentials choice | Christophe (secrets + challenge); Edgar (host — existing Acme host, no new subdomain) | Two targeted reviews recorded; plan hardening **only if** a cold agent executes autonomously |
| **3 — Smoke pilot** | Acme adjustments + deploy + first tester + mission sheet EN/FR | Mathieu (+ agents) as needed; Marc onboards first tester | `ci-gate` green; live smoke on Northwind Portal tenant; first tester autonomous; REX; widen cohort |

### Horizon indicatif (non-binding)

Serious lab project, promised to no one. If a step slips, re-evaluate there — no self-imposed
pressure on the 14-day window.

| Step | Horizon (indicative) |
| ---- | -------------------- |
| Note merge + open closed-test opt-ins | days |
| One-page M1 brief + two reviews | ~1 week |
| Acme adjustments + smoke pilot | ~1–3 weeks after brief |
| Widen cohort toward 12 continuous opt-ins | rolls as long as needed (weeks+) |

## Later / follow-ups (not this note)

- **TS SDK dogfood** — separate `I-*`, no deadline.
- **Product-management consistency** — tagging / version tags / branch management so **mobile app**
  production claims stay distinct from **platform** alpha/lab (follow-up; not designed here).
- **Self-provisioning** of tester tenants into the demo — separate `V-*` when signalled.

## Open questions for Marc

None remaining for CR1 (S1–S3 + §3 settled 2026-10-08).

## Consequences (when executed later)

Orientation only — M1 one-page brief, then execution on a **new branch**. No application code here.

| Surface | Consequence |
| ------- | ----------- |
| **Acme** | Access-code table, `/t/{code}`, generic errors, switch-code safety, challenge always on. |
| **Host / Caddy** | **Unchanged** (`demo-acme.ezkey.online`). |
| **Tenancy** | Northwind Portal (Marc) + second tenant; Reine in both; three oracles. |
| **Play** | Opt-ins open now; mission sheet EN/FR; feedback channel; App access at M1. |
| **TS SDK / new demo app** | Not on this path. |
| **Compass** | Mobile production targeted; platform stays alpha/lab. |

## Relationship to existing artifacts

| Artifact | Relationship |
| -------- | ------------- |
| [`V-2026-09-26-public-alpha-posture-closeout`](V-2026-09-26-public-alpha-posture-closeout.md) | Compass — amended 2026-10-08 (mobile production vs platform alpha). |
| [`../backlog/index.md`](../backlog/index.md) | Prioritization summary aligned with compass amendment. |
| [`../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md) | Personal-account 12×14 continuous opt-in wording. |
| [`../../../ezkey-demo-app-acme/`](../../../ezkey-demo-app-acme/) | Play-path implementation surface. |
| [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md) | Integration API / auth-attempt / pull-model contract. |
| [`V-2026-09-27-operator-and-mobile-feedback-channel`](V-2026-09-27-operator-and-mobile-feedback-channel.md) | Feedback channel for the pilot. |
| [`V-2026-08-02-mobile-official-play-release-posture`](V-2026-08-02-mobile-official-play-release-posture.md) | Broader mobile Play posture. |
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | Acme pedagogy lineage (self-serve paste path remains for evaluators). |
| [`../product-intent.md`](../product-intent.md) | Adopter posture and honesty of claims. |

## Promotion criteria

1. Marc merges this note.
2. One-page M1 brief with Christophe + Edgar reviews recorded.
3. Execution on a **new branch**; smoke pilot + mission sheet follow.

## Next step

Marc merges. Open closed-test opt-ins now. Write the one-page M1 brief (Christophe / Edgar). Then Acme
adjustments and first-tester smoke — Alex does not implement.
