# Backlog Idea — `I-2026-10-20-demo-device-api-and-mobile-parity` Demo Device parity with Auth/Integration contracts and mobile FSM

## Metadata

- **ID:** `I-2026-10-20-demo-device-api-and-mobile-parity`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-10-20`
- **Updated at:** `2026-10-20`
- **Last reviewed at:** `2026-10-20`
- **Component tags:** `demo-device`, `auth-api`, `integration-api`, `mobile`, `docs`
- **Lane:** `D` (post-delivery correction — contract and FSM drift after #895 / #844 / #833)
- **Captured by:** Marc
- **GitHub issue:** `#943`

## Intent

Restore a **pragmatic fidelity** of the Demo Device (internal lab tool) to current Auth and
Integration API contracts and to the mobile reference app’s specified finite-state machine — enough
that operators and agents trust simulator behaviour, without cloning full mobile complexity
(biometrics, leave-as-deny race refs, Keystore, i18n, installation trust zones).

## Problem and value

- **Problem:** After Auth/Integration evolutions (typed 409 expiry #895, 503 signing #844, 429
  rate limits, wait/verdict `EXPIRED`, leave-as-deny #833), the Demo Device still covers the happy
  path and partial 409 expiry UX, but drifts on HTTP error catalog handling, FSM edge cases (leave
  without deny, uncertain decision, 503 retry), and on-screen state honesty compared with
  [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md).
- **Expected value:**
  - One draft balance point stakeholders (Alex, Julie, Patrick, Marc) can accept or trim.
  - Clear keep / simplify / omit map so implementation PRs do not overbuild the simulator.
  - Fewer false “session expired / stuck wait” lab incidents when the Demo Device is the device.

## Scope

- **In scope:**
  - Inventory: Demo Device vs Auth OpenAPI + Integration OpenAPI (error codes, TTL, wait/verdicts).
  - Inventory: Demo Device vs mobile FSM and screen-state presentation.
  - Draft tracer-bullet brief with classification, effort groups, open questions, balance proposal.
  - No product code in the analysis PR.
- **Out of scope:**
  - Implementing the accepted balance (follow-up PRs after Marc go/no-go on budget).
  - Making Demo Device a second mobile client (StrongBox, protected approve, Settings hub, …).
  - Evaluator self-registration (Admin API / ezkey.org — not a Demo Device consumer).
  - Acme wait-page countdown (#728) — initiator surface, not device.

## Key assumptions

- Target fidelity: **faithful in behaviour, not exhaustive** (issue #943).
- Mobile FSM + cited code win over older mobile narrative docs when they disagree.
- Countdown stays on the integrating application (#834 Q1); Demo Device must not invent a second
  phone timer unless Marc revisits that decision for the lab tool only.

## Risks and exceptions

- Over-fitting Demo Device to every mobile edge case → accidental complexity for an internal tool.
- Under-fitting leave-as-deny / 503 / 429 → Admin passwordless and PAM lab waits appear “stuck.”
- Pending wire still omits `expiresAt` (#834); Demo Device local TTL fallback can drift vs PAM
  `ttlSeconds` (documented in `ezkey-demo-device/AGENTS.md`).

## Promotion notes

Ready for implementation TBs once Marc validates the balance proposal in
`TB-2026-10-20-demo-device-api-and-mobile-parity` (go/no-go on budget and open questions).

## Links

- GitHub issue: [#943](https://github.com/mgagp/ezkey/issues/943)
- Tracer bullet (draft): [`TB-2026-10-20-demo-device-api-and-mobile-parity`](../TB-2026-10-20-demo-device-api-and-mobile-parity.md)
- Related: #895, #844, #834, #833, #799 (session cookies — done), #753 (log scrub — done), #728 (Acme wait UX)
- Mobile FSM: [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md)
- Mobile orientation: [`ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md`](../../../../ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md)
