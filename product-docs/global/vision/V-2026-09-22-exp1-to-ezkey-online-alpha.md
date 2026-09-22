# Vision Note — `V-2026-09-22-exp1-to-ezkey-online-alpha` EXP1 → ezkey.online alpha community instance

## Metadata

- **ID:** `V-2026-09-22-exp1-to-ezkey-online-alpha`
- **Status:** `under-review`
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

This note records the product locks. The full `sites/ezkey-org/` editorial rewrite and DNS /
Cloudflare cutover are **follow-up execution**, not this artifact.

## Motivation — why the EXP1 public label retires

- **EXP1** named a private experimental lab posture. That framing no longer matches how the
  project wants outsiders to meet the live host: as a real (if early) product they can try.
- Continuing to brand the host as “experimental lab” understates product intent and overstates
  disposability; continuing to imply a production SLA would overclaim.
- Alpha + community instance is the honest middle: publicly invite evaluation, keep maturity
  vocabulary accurate, and preserve a single recyclable Lightsail footprint.

## Product locks (settled)

1. **Community offering on ezkey.online** — Recycle the current EXP1 AWS Lightsail VM into the
   community instance. Hostnames follow the product domain (e.g. `admin-api.ezkey.online`).
   Spelling is always **ezkey.online** (never “easykey”).
2. **Public posture** — Stop positioning the live host as « EXP1 / experimental lab ». Public
   language is: Ezkey product in **alpha**, with a maintained **community instance** on
   ezkey.online.
3. **ezkey.org remains the product site** — Marketing, guides, and evaluator journey stay on
   ezkey.org. A later editorial pass must retarget all EXP1 journey copy, badges, and URLs to
   this alpha + ezkey.online reality.
4. **Honesty bounds** — Alpha is **not** a production SLA. Ezkey is **not** Duo / Okta /
   Keycloak parity. Ezkey is **not** WebAuthn / passkey equivalence (see
   [`../product-intent.md`](../product-intent.md)).

## Domain map

| Surface | Role | Notes |
|---------|------|--------|
| **ezkey.org** | Product site (static / Cloudflare Pages) | Narrative, guides, API portal posture, evaluator entry. Remains the public face. |
| **ezkey.online** | Community runtime (recycled Lightsail VM) | Live Admin / Auth / Integration (and related) endpoints for community alpha use. |
| **EXP1** (historical label) | Retired public name for that VM | Internal ops history and old docs may still say EXP1; new public copy must not. |

Internal repo paths, method logs, and closed Wave D language that say “EXP1” remain valid as
**historical** references until deliberately rewritten. New operator-facing and public-facing
copy should prefer **community instance** / **ezkey.online**.

## Cutover sequence

Order is intentional: **comms and site first**, infrastructure rename second.

1. **Publish repositioning on ezkey.org** — Editorial pass: retire EXP1 journey language; point
   evaluators at alpha + ezkey.online; keep honesty bounds visible. *(Follow-up; not this PR.)*
2. **DNS / Cloudflare with Edgar** — Point ezkey.online (and service hostnames) at the recycled
   community host; TLS and routing aligned with the new names.
3. **VM recycle / EXP1 branding shutdown** — Only after the site no longer sends people into an
   EXP1-framed journey: retire EXP1 public branding on the host, confirm community hostnames,
   and treat residual EXP1 URLs as redirects or explicit deprecation.

Do **not** shut down EXP1-branded entry points before ezkey.org has been updated. That ordering
avoids a window where the site still promises EXP1 while the host has already moved.

## Non-goals

- No production-readiness claim, uptime SLA, or “enterprise support” posture.
- No claim of feature parity with Duo, Okta, Keycloak, or similar suites.
- No claim of WebAuthn / FIDO2 / passkey protocol equivalence.
- No full rewrite of `sites/ezkey-org/` in the same change set as this note.
- No mandatory rename of every historical “EXP1” string in closed backlog, method logs, or
  ADRs — only public and forward-looking operator surfaces need the new framing first.
- No new methodology program (`I-*` / `TB-*`) required solely to record these locks; promote
  execution slices only when site rewrite, DNS, or infra work needs a bounded tracer.

## Follow-up execution (out of scope for this note’s PR)

| Work | Owner hint | Depends on |
|------|------------|------------|
| ezkey.org EXP1 → alpha / ezkey.online editorial pass | Product / site | This note |
| DNS + Cloudflare cutover | Edgar + ops | Site publish (step 1) |
| Lightsail VM recycle / hostname cut | Ops | DNS + site |
| Canon sync (operational-readiness, backlog index EXP1 wording, OpenAPI exposure matrix “evaluator EXP1”) | Docs hygiene | After public cutover or in parallel once locks are accepted |

## Related artifacts

- [`../product-intent.md`](../product-intent.md) — protocol identity and non-equivalence honesty
- [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md) — still describes EXP1 as live experimental host (historical Wave D); superseded for **public** posture by this note
- [`../openapi-exposure-matrix.md`](../openapi-exposure-matrix.md) — evaluator / EXP1 exposure rows; revisit naming when community posture lands
- [`V-2026-0014`](V-2026-0014-api-docs-exposure-portal.md) — public API docs vs raw tooling; still relevant for community host exposure
- [`I-2026-05-23-exp1-anonymous-evaluator-onboarding`](../backlog/ideas/I-2026-05-23-exp1-anonymous-evaluator-onboarding.md) — evaluator signup track; retarget host naming when executed against ezkey.online
- `sites/ezkey-org/` — product site; editorial rewrite is the primary follow-up consumer of this note

## Promotion path

When the orientation is absorbed:

1. Short public-posture paragraph (or link) in `product-intent.md` and/or operator-facing deploy docs.
2. Soften or footnote EXP1-as-current-host wording in `operational-readiness-prioritization-2026-09.md`
   and `backlog/index.md`.
3. Set this `V-*` to `promoted` (or `archived` if a successor vision supersedes it).

Until then, treat the **Product locks** and **Cutover sequence** sections as the decision record
for agents and humans touching public copy, DNS, or the Lightsail host.
