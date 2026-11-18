# Vision Note — `V-2026-09-26-public-alpha-posture-closeout` Public alpha posture closeout

## Metadata

- **ID:** `V-2026-09-26-public-alpha-posture-closeout`
- **Status:** `promoted`
- **Lane:** `D`
- **Created at:** `2026-09-26`
- **Updated at:** `2026-10-08`
- **Captured by:** Marc / Alex (editorial lock: Audrey)
- **Priority:** `P1` (cold-start compass; replaces live “next P0” use of the September operable-release note)
- **Supersedes (as live next-P0 gate):** [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)

## Intent

Light **posture closeout** for cold agents and operators after Waves A–C and Wave D (attention
freeze) closed, and after the public community host → ezkey.online alpha repositioning
([`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md)).

This note is the live **“Where are we?”** compass. It is **not** a PRD, not a new release program,
and not a backlog rewrite.

## Honesty (locked)

- **Laboratory** — opinionated, experimental, serious engineering; not world-conquest.
- **Discreet** — no marketing campaign; collaboration is intention-first.
- **Alpha ≠ production** — public alpha on ezkey.online is not a production claim.
- **No SLA** — no uptime, support, or enterprise-readiness promise.
- **No IdP parity** — not Duo / Okta / Keycloak / WebAuthn / passkey equivalence.

## Public runtime naming (locked)

Public product vocabulary for runtime presets is **`base`** and **`integrity`**
(`--runtime=base|integrity` / `EZKEY_RUNTIME_PROFILE=base|integrity`). Do **not** call these an
« eval profile » (or `--runtime=eval`) in product-docs or operator-facing copy. See
[`../admin-ui-integrity-base-monitoring-honesty.md`](../admin-ui-integrity-base-monitoring-honesty.md).

## Posture locks (settled 2026-09-26)

1. **Public alpha community** — Live surface is **ezkey.online** (public alpha). Product site
   remains **ezkey.org**. Deploy ledger:
   [`../../../docs/lightsail/community/DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md).
2. **GitHub open** — Source and issues are public under the honesty bounds above.
3. **Play Store: mobile production targeted; platform stays alpha** — The **mobile app** may
   proceed through closed testing toward **Play production**. Rationale (2026-10-08): the API
   surface is stabilizing; in particular the cryptographic relationship between the mobile app and
   the Authentication API is judged stable enough for the mobile app to reach Play production. The
   **rest of the platform** (backends / community instance) remains **alpha / laboratory** — not a
   production claim. Closed testing remains the required Play track before production.
4. **Intention-first collaboration** — New work starts from maintainer intention; do not invent
   P0 gates from historical September compass text.
5. **No remaining P0 on the active backlog** — Waves A–C closed; Wave D closed as attention freeze
   (not an executable gate).
6. ~~**Evaluator self-registration off by default**~~ — **superseded 2026-11-18:** the
   capability is retired (see the amendment below). There is no signup path to turn on.

### Amendment 2026-10-08 (Marc)

Clarifies lock 3: **production targets the mobile app only**, not the backend platform. Does not
weaken honesty bounds (laboratory · discreet · alpha ≠ prod for the platform · no SLA). Follow-up
(not designed here): product management must stay consistent with this split (tagging / version
tags / branch management for the mobile app versus the platform) — see
[`V-2026-10-07-multi-tenant-closed-testing-demo-app`](V-2026-10-07-multi-tenant-closed-testing-demo-app.md).

### Amendment 2026-11-18 (Marc) — lock 6 superseded: self-registration retired

Lock 6 kept anonymous evaluator self-registration off by default and let the community host turn
it on. The capability is now **retired** ([#1365](https://github.com/mgagp/ezkey/issues/1365)):
the signup endpoint, the temporary evaluator console and their settings leave the code
([#1368](https://github.com/mgagp/ezkey/issues/1368)).

**Reason.** The hypothesis was tested: self-registration was added (2026-05) on the idea that the
email request kept evaluators away. After several months without that request, no evaluator came.
The limit is discoverability, a deliberate choice (lab, discreet). Meanwhile the path handed a
Tenant Admin identity to anyone on the internet, so any defect that needs an authenticated Tenant
Admin became reachable anonymously (#1122), and the temporary console was a session without MFA
in an MFA product (#1123). It was also a second onboarding path that no adopter will ever run.

**What replaces it.** One onboarding path, the adopter's: a Global Admin creates the tenant and the
Tenant Admin and hands over an activation code. Two ways to evaluate Ezkey:

- **Technical exploration:** clone the repository and run a clean start, with the documentation
  in GitHub. No contact needed. This is also the only evaluation faithful to a self-hosted
  adoption.
- **Community instance (`ezkey.online`):** write to the Ezkey team by email; the team replies
  with an identifier.

**What is lost, stated honestly.** The effortless look at the console for a hesitant visitor (the
guided tour and the technical trace on ezkey.org stand in for it), and an anonymous interest
counter that never counted anyone.

The other locks are unchanged. The community instance moves closer to an adopter's posture: the
same single path runs locally, on the community host and at an adopter's.

## Session restart phrase (cold start)

> **“Where are we?” / “What should be next?”** → Read **this note** first, then
> [`../backlog/index.md`](../backlog/index.md). Treat
> [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)
> as **historical Waves A–C + freeze D** context, not the live next-P0 gate.

## Relationship to prior compasses

| Artifact | Role after this closeout |
|----------|--------------------------|
| [`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md) | **Historical** — Waves A–C + freeze D retained; superseded as next-P0 gate |
| [`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md) | **Promoted** domain/host posture — alpha + community hostnames |
| [`../product-intent.md`](../product-intent.md) | Durable product identity; community vs self-host signup default |
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | **Retired 2026-11-18** (#1365) — historical record of the signup track |

## Non-goals

- No massive backlog rewrite or parallel PRD.
- No deploy and no ezkey.org marketing sync in this closeout.
- No claim of production readiness or IdP / WebAuthn parity.

## Canon sync with this note

- Operational-readiness September note: superseded as next-P0 gate; Waves history intact.
- `V-2026-09-22` residuals closed; OpenAPI matrix community naming retained.
- `product-orientation-notes.md` index updated for this closeout and `V-2026-09-22`.

## Related artifacts

- [`../backlog/index.md`](../backlog/index.md) — active ideas; no P0 remaining
- [`../../../docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../../../docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md) — public alpha label
- [`V-2026-08-02-mobile-official-play-release-posture`](V-2026-08-02-mobile-official-play-release-posture.md) — mobile Play track (closed testing → mobile production; platform stays alpha)
- [`V-2026-10-07-multi-tenant-closed-testing-demo-app`](V-2026-10-07-multi-tenant-closed-testing-demo-app.md) — closed-testing friction / Acme access-code path for mobile production cohort
