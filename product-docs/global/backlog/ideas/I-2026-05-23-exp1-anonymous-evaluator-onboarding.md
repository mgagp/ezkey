# Backlog Idea — `I-2026-05-23` EXP1 anonymous evaluator onboarding

## Metadata

- **ID:** `I-2026-05-23-exp1-anonymous-evaluator-onboarding`
- **Status:** `active`
- **Priority:** `P2`
- **Created at:** `2026-05-23`
- **Updated at:** `2026-05-23`
- **Last reviewed at:** `2026-05-23`
- **Phase tags:** `P2-functional-mode`
- **Component tags:** `admin-api`, `admin-ui`, `ezkey-org`, `infra (EXP1)`, `docs`

## Intent

Add an **anonymous self-service evaluator onboarding path** on **EXP1** that creates an **empty
tenant** and a **Tenant Admin** provisioned with **`ACTIVATION_CODE` onboarding**, returns the
activation code immediately, and applies **proportionate rate limits** so the operator can monitor
volume without building heavyweight anti-abuse infrastructure.

## Problem and value

- **Problem:** EXP1 access today requires email to `info@ezkey.org` and manual review. With minimal
  public promotion, **no inbound requests** have arrived. Some potential evaluators may avoid email
  due to **trust friction** or desire for **anonymity**, leaving no signal of latent interest.
- **Expected value:**
  - Lower friction for cautious but interested evaluators.
  - **Measurable anonymous interest** (creation counts via audit/logs).
  - Reuse of existing activation-code onboarding instead of inventing a parallel enrollment story.
  - Keeps email-reviewed access as a higher-trust lane without removing it.
  - Guided tour steps 2–7 (integration, API key, enrollment, Acme demo) remain **manual pedagogy**.

## Scope

- **In scope:**
  - Installation-scoped feature (EXP1) enabling anonymous **empty tenant** + tenant-admin provisioning.
  - Signup page on `ezkey.org` (English first).
  - Return activation code + expiry + URLs; user continues existing guided tour from step 1.
  - Abuse controls: **global daily creation cap (default 5)**, **per-IP success limit (1/24h)**,
    audit-friendly logging.
  - Public copy update on ezkey.org EXP1 preview page.
  - Configuration documented in relevant `CONFIGURATION.md` / EXP1 operator notes.

- **Out of scope (first cut):**
  - Integration, API key, or enrollment auto-provision at signup.
  - CAPTCHA, invite tokens, or email verification for anonymous path.
  - Self-service on non-EXP1 installations.
  - Automated tenant cleanup / TTL.
  - Marketing or advertising push to drive volume.
  - Admin UI `/signup` route.
  - Global Admin counter UI screen.

## Settled decisions (Grill Me 2026-05-23)

See [`../grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md`](../grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md).

| ID | Decision summary |
| ---- | ---------------- |
| G1 | `ezkey.org` signup page → public Admin API endpoint |
| G2 | Feature flag `ezkey.evaluator.self-registration.enabled` |
| G3 | Server slug `eval-{random}` + optional label (max 40 chars) |
| G4 | Synthetic username; no email; placeholder names |
| G5 | **Empty tenant only** — tour steps 2–7 unchanged |
| G6–G7 | 5/day global; 1 success/IP/24h |
| G8 | Audit + logs; no Admin UI screen v1 |
| G9 | "Instant preview access (limited daily slots)" framing |
| G10 | Independent from email path |
| G11 | CORS `ezkey.org`; flag off → 404 |
| G12 | Response without `adminId` |

## Key assumptions

- Volume stays low; daily cap of 5 is adequate initially.
- Evaluators who complete signup will run the full guided tour including integration and API key
  creation — that is desirable, not friction to remove.
- Existing authenticated provisioning APIs remain unchanged.

## Risks and exceptions

- **Abuse / spam tenants:** daily cap + IP throttle; disable via config.
- **Scam perception:** copy frames experimental scope and daily limits clearly.
- **Lifecycle drift:** orphaned tenants — monitor; cleanup deferred.
- **CORS / secret display:** minimal origins; warn user to save activation code locally.

## Promotion notes

Promoted to **`TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut`**. Ready for implementation
when operator prioritizes the slice.

## Links

- Vision: [`../../vision/V-2026-05-23-anonymous-evaluator-self-registration.md`](../../vision/V-2026-05-23-anonymous-evaluator-self-registration.md)
- Tracer bullet: [`TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut.md`](TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut.md)
- Grill: [`../grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md`](../grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md)
- Site: [`../../../../sites/ezkey-org/experimental-preview-exp1.html`](../../../../sites/ezkey-org/experimental-preview-exp1.html)
- Tour: [`../../../../sites/ezkey-org/exp1-guided-tour.html`](../../../../sites/ezkey-org/exp1-guided-tour.html)
- Features: `F-public-site`, `F-provisioning-procedures`
- Adjacent: `I-2026-0011`, `I-2026-0026`
- API reference: [`../../../../docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md)
- Lifecycle: [`../../../../docs/LIFECYCLE_GOVERNANCE.md`](../../../../docs/LIFECYCLE_GOVERNANCE.md)
