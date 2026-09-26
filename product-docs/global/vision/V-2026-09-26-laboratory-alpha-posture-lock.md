# Vision Note — `V-2026-09-26-laboratory-alpha-posture-lock` Laboratory / alpha community posture lock

## Metadata

- **ID:** `V-2026-09-26-laboratory-alpha-posture-lock`
- **Status:** `promoted`
- **Lane:** `D`
- **Created at:** `2026-09-26`
- **Updated at:** `2026-09-26`
- **Captured by:** Marc / Alex
- **Priority:** `P1` (cold-start compass; replaces live “next P0” use of the September operable-release note)
- **Supersedes (as live priority compass):** [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)

## Intent

Lock the **current project posture** for cold agents and operators after Waves A–C and Wave D
(attention freeze) closed, and after the public EXP1 → ezkey.online alpha repositioning
([`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md)).

This note is the **live “Where are we?” compass**. It does **not** invent a new release program,
rewrite the backlog, or parallel `product-intent.md`.

## Posture locks (settled 2026-09-26)

1. **Laboratory / opinionated / experimental** — Serious engineering and honest maturity language;
   not a world-conquest product campaign. Prefer discrete collaboration over marketing push.
2. **Public alpha community** — Live evaluator surface is **ezkey.online** (public alpha). Product
   site remains **ezkey.org**. Deploy ledger:
   [`../../../docs/lightsail/community/DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md).
3. **GitHub open** — Source and issues are public; no claim of production SLA or vendor parity.
4. **Play Store: closed testing only** — Android distribution is accepted for **closed testing**
   (public-alpha labeled builds). This is **not** mobile production / open production track.
5. **Intention-first collaboration** — New work starts from maintainer intention and proportional
   rigor; do not invent P0 gates or soak programs from historical September compass text.
6. **No remaining P0 on the active backlog** — Waves A–C closed; Wave D closed as attention freeze
   (not an executable gate). Next work is ordinary backlog triage, not a release-critical path.
7. **Evaluator self-registration off by default** —
   `ezkey.evaluator.self-registration.enabled` defaults **off**. Community instance may enable it
   **explicitly**; self-host / clean-start must not inherit an open signup path by default.

## Session restart phrase (cold start)

> **“Where are we?” / “What should be next?”** → Read **this note** first, then
> [`../backlog/index.md`](../backlog/index.md). Treat
> [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)
> as **historical Waves A–D** context, not the live next-P0 gate.

## Relationship to prior compasses

| Artifact | Role after this lock |
|----------|----------------------|
| [`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md) | **Historical** — Waves A–D narrative and integrity-cluster ordering retained; status superseded as live priority compass |
| [`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md) | **Promoted** domain/host posture — alpha + community hostnames; site PR #609 done |
| [`../product-intent.md`](../product-intent.md) | Durable product identity; community vs self-host signup default called out there |
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | Evaluator signup track — installation-scoped flag; not a platform default |

## Non-goals

- No massive backlog rewrite or new September-style P0 program.
- No parallel PRD or public-site marketing copy in this closeout.
- No deploy, DNS, or code changes outside product-docs / agent compass pointers.
- No claim of production readiness, Duo/Okta/Keycloak parity, or WebAuthn equivalence.

## Canon sync performed with this note

- Operational-readiness September note marked **historical / superseded** as live compass.
- OpenAPI exposure matrix evaluator row renamed away from public “EXP1” framing.
- `V-2026-09-22` follow-up “canon sync” row closed.
- Light pointer fixes on backlog index, `AGENTS.md`, and normative posture.

`product-orientation-notes.md` index row for this `V-*` (and any lagging `V-2026-09-22` row)
remains **post-merge on `main`** per vision folder practice.

## Related artifacts

- [`../backlog/index.md`](../backlog/index.md) — active ideas; no P0 remaining
- [`../../../docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../../../docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md) — public alpha label
- [`V-2026-08-02-mobile-official-play-release-posture`](V-2026-08-02-mobile-official-play-release-posture.md) — longer mobile Play track (orthogonal; closed testing ≠ official open production)
