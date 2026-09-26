# Vision Note — `V-2026-09-26-public-alpha-posture-closeout` Public alpha posture closeout

## Metadata

- **ID:** `V-2026-09-26-public-alpha-posture-closeout`
- **Status:** `promoted`
- **Lane:** `D`
- **Created at:** `2026-09-26`
- **Updated at:** `2026-09-26`
- **Captured by:** Marc / Alex (editorial lock: Audrey)
- **Priority:** `P1` (cold-start compass; replaces live “next P0” use of the September operable-release note)
- **Supersedes (as live next-P0 gate):** [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)

## Intent

Light **posture closeout** for cold agents and operators after Waves A–C and Wave D (attention
freeze) closed, and after the public EXP1 → ezkey.online alpha repositioning
([`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md)).

This note is the live **“Where are we?”** compass. It is **not** a PRD, not a new release program,
and not a backlog rewrite.

## Honesty (locked)

- **Laboratory** — opinionated, experimental, serious engineering; not world-conquest.
- **Discrete** — no marketing campaign; collaboration is intention-first.
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
3. **Play Store: closed testing only** — Android accepted for **closed testing** (public-alpha
   labeled builds). Not mobile production / open production.
4. **Intention-first collaboration** — New work starts from maintainer intention; do not invent
   P0 gates from historical September compass text.
5. **No remaining P0 on the active backlog** — Waves A–C closed; Wave D closed as attention freeze
   (not an executable gate).
6. **Evaluator self-registration off by default** —
   `ezkey.evaluator.self-registration.enabled` defaults **off**. Community may enable it
   **explicitly**; self-host / clean-start must not inherit an open signup path by default.

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
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | Evaluator signup track — installation-scoped flag; not a platform default |

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
- [`V-2026-08-02-mobile-official-play-release-posture`](V-2026-08-02-mobile-official-play-release-posture.md) — mobile Play track (closed testing ≠ open production)
