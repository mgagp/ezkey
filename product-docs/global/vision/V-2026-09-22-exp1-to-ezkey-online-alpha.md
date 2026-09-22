# Vision Note — `V-2026-09-22-exp1-to-ezkey-online-alpha` EXP1 → ezkey.online alpha community instance

## Metadata

- **ID:** `V-2026-09-22-exp1-to-ezkey-online-alpha`
- **Status:** `promoted`
- **Lane:** `D`
- **Created at:** `2026-09-22`
- **Updated at:** `2026-09-22`
- **Captured by:** Marc / Alex
- **Priority:** `P1` (public posture and domain honesty; blocks coherent site + host messaging)

## Intent

Retire the public **EXP1 / experimental lab** label for the live AWS Lightsail host. Reposition
Ezkey’s public posture as: **product in alpha**, with a maintained **community instance** hosted
at **ezkey.online**. Keep **ezkey.org** as the product site. Recycle the current EXP1 VM into that
community offering. Do **not** claim production readiness, vendor parity, or WebAuthn/passkey
equivalence.

This note is the **decision record** for those locks. Site repositioning on ezkey.org is **done**
(PR #609). DNS / Cloudflare and VM recycle remain open follow-up with Edgar / ops.

## Motivation — why the EXP1 public label retires

- **EXP1** named a private experimental lab posture. That framing no longer matches how the
  project wants outsiders to meet the live host: as a real (if early) product they can try.
- Continuing to brand the host as “experimental lab” understates product intent and overstates
  disposability; continuing to imply a production SLA would overclaim.
- Alpha + community instance is the honest middle: publicly invite evaluation, keep maturity
  vocabulary accurate, and preserve a single recyclable Lightsail footprint.

## Product locks (settled)

1. **Community offering on ezkey.online** — Recycle the current EXP1 AWS Lightsail VM into the
   community instance. Hostnames follow the product domain (see hostname map below). Spelling is
   always **ezkey.online** (never “easykey”).
2. **Public posture** — Stop positioning the live host as « EXP1 / experimental lab ». Public
   language is: Ezkey product in **alpha**, with a maintained **community instance** on
   ezkey.online.
3. **ezkey.org remains the product site** — Marketing, guides, and evaluator journey stay on
   ezkey.org. EXP1 journey copy, badges, and URLs are retargeted to this alpha + ezkey.online
   reality (editorial pass shipped in PR #609).
4. **Honesty bounds** — Alpha is **not** a production SLA. Ezkey is **not** Duo / Okta /
   Keycloak parity. Ezkey is **not** WebAuthn / passkey equivalence (see
   [`../product-intent.md`](../product-intent.md)).
5. **Hostname map (Marc confirmed 2026-09-22 via Alex)** — explicit product lock, not assumed:

| Former EXP1 hostname | Community hostname |
|----------------------|--------------------|
| `exp1-admin-ui.ezkey.org` | `admin-ui.ezkey.online` |
| `exp1-admin-api.ezkey.org` | `admin-api.ezkey.online` |
| `exp1-demo-acme.ezkey.org` | `demo-acme.ezkey.online` |
| Auth API (EXP1 Auth host) | `auth-api.ezkey.online` |

## Domain map

| Surface | Role | Notes |
|---------|------|--------|
| **ezkey.org** | Product site (static / Cloudflare Pages) | Narrative, guides, API portal posture, evaluator entry. Remains the public face. Repositioned 2026-09-22 (PR #609). |
| **ezkey.online** | Community runtime (recycled Lightsail VM) | Live Admin / Auth / demo-acme (and related) endpoints for community alpha use. Hostnames per lock table above. |
| **EXP1** (historical label) | Retired public name for that VM | Internal ops history and old docs may still say EXP1; new public copy must not. |

Internal repo paths, method logs, and closed Wave D language that say “EXP1” remain valid as
**historical** references until deliberately rewritten. New operator-facing and public-facing
copy should prefer **community instance** / **ezkey.online**.

## Cutover sequence

Order is intentional: **comms and site first**, infrastructure rename second.

1. **Publish repositioning on ezkey.org** — **DONE** (2026-09-22). PR #609 squash-merged to
   `main` (tip `0ec2e91e` / merge commit `e6ff68a`) and published to production
   [https://ezkey.org/](https://ezkey.org/). Temporary migration downtime banner remains in place
   until ezkey.online is back.
2. **DNS / Cloudflare with Edgar** — Point ezkey.online (and service hostnames from the lock
   table) at the recycled community host; TLS and routing aligned with the new names. **Open.**
3. **VM recycle / EXP1 branding shutdown** — Retire residual EXP1 public branding on the host,
   confirm community hostnames, and treat residual EXP1 URLs as redirects or explicit
   deprecation. **Open** — after DNS is live.

Do **not** shut down EXP1-branded entry points before ezkey.org has been updated. Step 1
satisfied that precondition; steps 2–3 may proceed.

## Non-goals

- No production-readiness claim, uptime SLA, or “enterprise support” posture.
- No claim of feature parity with Duo, Okta, Keycloak, or similar suites.
- No claim of WebAuthn / FIDO2 / passkey protocol equivalence.
- No further ezkey.org rewrite in this decision-record PR (site pass already shipped as #609).
- No mandatory rename of every historical “EXP1” string in closed backlog, method logs, or
  ADRs — only public and forward-looking operator surfaces need the new framing first.
- No new methodology program (`I-*` / `TB-*`) required solely to record these locks; promote
  execution slices only when DNS or infra work needs a bounded tracer.

## Follow-up execution

| Work | Status | Owner hint | Notes |
|------|--------|------------|-------|
| ezkey.org EXP1 → alpha / ezkey.online editorial pass | **Done** | Product / site | PR #609 merged + live on https://ezkey.org/ (2026-09-22); migration banner until ezkey.online is back |
| DNS + Cloudflare cutover | Open | Edgar + ops | Hostnames per product-lock table |
| Lightsail VM recycle / hostname cut | Open | Ops | After DNS |
| Canon sync (operational-readiness residual EXP1 wording, OpenAPI exposure matrix “evaluator EXP1”, `product-orientation-notes.md` index row) | Open | Docs hygiene | Index row is post-merge-on-`main` only |

## Related artifacts

- [`../product-intent.md`](../product-intent.md) — protocol identity and non-equivalence honesty
- [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md) — Wave D history unchanged; public-posture pointer to this note
- [`../openapi-exposure-matrix.md`](../openapi-exposure-matrix.md) — evaluator / EXP1 exposure rows; revisit naming when community runtime is live
- [`V-2026-0014`](V-2026-0014-api-docs-exposure-portal.md) — public API docs vs raw tooling; still relevant for community host exposure
- [`I-2026-05-23-exp1-anonymous-evaluator-onboarding`](../backlog/ideas/I-2026-05-23-exp1-anonymous-evaluator-onboarding.md) — evaluator signup track; retarget host naming when executed against ezkey.online
- PR #609 — ezkey.org editorial rewrite (shipped)
- `sites/ezkey-org/` — product site; no further edits in this PR

## Promotion

Promoted 2026-09-22 after Marc approved merge of this decision record and the ezkey.org
repositioning (#609) landed on production. Remaining open work is ops cutover (DNS /
Cloudflare, VM recycle) and light canon sync — not a blocker for treating the **Product locks**,
**Hostname map**, and **Cutover sequence** as durable guidance.
