# Vision Note — `V-2026-05-23` Anonymous evaluator self-registration (EXP1)

## Metadata

- **ID:** `V-2026-05-23-anonymous-evaluator-self-registration`
- **Status:** `under-review`
- **Created at:** `2026-05-23`
- **Updated at:** `2026-05-23`
- **Author:** operator session (voice dictation intake)

## Intent

Offer a **low-friction, anonymous onboarding path** on the **EXP1 evaluator instance** so
technically curious people can self-provision a **tenant-scoped Tenant Admin** and receive an
**activation code** without sending email first — while keeping abuse controls proportional to
EXP1's current low visibility and limited capacity.

## Motivation

The current EXP1 access model on [ezkey.org](https://ezkey.org/experimental-preview-exp1.html)
relies on **invitation or email request** to `info@ezkey.org`, manual review, and delivery of an
activation code by email (typically within about two business days). With **no broad public
advertising**, inbound interest has been **effectively zero** — which was expected.

A plausible secondary blocker is **trust and exposure**: some evaluators may hesitate to email an
unknown project (scam concern, identity exposure, or simply friction). An **anonymous self-service
path** could lower that barrier and produce a **useful signal** (anonymous but measurable interest)
even before broader promotion.

This is **not** a general multi-tenant SaaS signup model. It is an **evaluator-instance posture**
for a deliberately small, experimental deployment.

## Potential impact

- **Product phases:** `P2-functional-mode` (evaluator funnel), `F-public-site` (site copy and CTA),
  future `F-provisioning-procedures` (operator runbook for EXP1 provisioning modes).
- **Components:** `admin-api` (new unauthenticated or public provisioning boundary),
  `admin-ui` or a **dedicated lightweight portal** (UX TBD), `ezkey.org` (second access path
  alongside email), EXP1 **configuration / edge** (rate limits, feature flag, observability).
- **User segments:** anonymous technical evaluators; existing invited / email-reviewed evaluators
  unchanged.

## Signals and constraints

### Evidence

- EXP1 preview page documents email-based access only; activation-code onboarding is already the
  documented evaluator path once access is granted.
- Platform already supports **two-step admin onboarding** via `ACTIVATION_CODE` mode on admin
  creation (`POST /api/v1/admins/tenant` today requires an authenticated admin).
- Tenant creation today is **GlobalAdmin-only** (`POST /api/v1/tenants`).

### Assumptions

- Anonymous signup interest, if it exists, is **low volume** for the foreseeable future.
- **Activation code** remains the right first-enrollment mechanism for anonymous evaluators (no
  email delivery of secrets; user copies code locally).
- A **hard daily cap** plus modest per-IP throttling is sufficient initial abuse control; CAPTCHA
  and identity proof are **not** first-cut requirements unless abuse appears.
- The operator wants **visibility** into creation volume (dashboard, logs, or a simple counter).

### Constraints

- EXP1 is **prod-like** for several exposure decisions (see `I-2026-0026`, `openapi-exposure-matrix.md`).
- Must not weaken production-grade tenant/admin lifecycle semantics; anonymous provisioning is a
  **controlled exception** scoped to an installation profile, not a platform default.
- Repository and broader promotion remain intentionally limited until later milestones.

## Design space (orientation only)

Three plausible surfaces for the first cut — decision deferred to `I-*` triage and grill session:

1. **Dedicated evaluator signup portal** (static page on `ezkey.org` calling a narrow Admin API
   endpoint, or a minimal standalone UI) — clearest separation from authenticated Admin UI.
2. **Admin UI anonymous route** (e.g. `/signup` on EXP1 Admin UI) — fewer moving parts, but mixes
   evaluator funnel with operator product chrome.
3. **API-only endpoint** documented for integrators — insufficient alone for the trust/friction
   problem; likely needs a human-facing portal regardless.

Recommended working direction: **(1) dedicated portal or ezkey.org page + narrow backend endpoint**,
reusing existing tenant + tenant-admin + `ACTIVATION_CODE` provisioning semantics behind an
installation-scoped feature flag.

## Relationship to existing artifacts (not duplicates)

| Artifact | Relationship |
| -------- | ------------- |
| `I-2026-0011` Bootstrap activation-code default | Adjacent — platform bootstrap posture; not evaluator self-registration |
| `I-2026-0026` / `V-2026-0014` API portal | Adjacent — public API **documentation** for evaluators, not access provisioning |
| `F-provisioning-procedures` | Complementary — formal operator procedures; this vision adds an EXP1-specific self-service mode |
| EXP1 email request flow on ezkey.org | **Coexists** — email path remains for reviewed access; anonymous path is an additional lane |

## Non-goals (initial)

- Open self-registration on non-EXP1 installations by default.
- Replacing Global Admin bootstrap or clean-start Docker ceremonies.
- Full anti-abuse program (CAPTCHA farms, payment verification, KYC, invite codes).
- Guarantee of perpetual anonymous tenants (retention / cleanup policy is a separate decision).

## Promotion criteria

Promote to backlog execution when:

1. EXP1 operator accepts **coexistence** of email-reviewed and anonymous paths with clear public copy.
2. Abuse posture (daily cap, IP throttle, operator visibility) is agreed at a proportionate level.
3. Surface choice (portal vs Admin UI route) is narrowed to one first cut.
4. Lifecycle questions are answered: tenant naming, default demo integration, optional auto-expiry.

## Related documents

- [experimental-preview-exp1.html](../../../sites/ezkey-org/experimental-preview-exp1.html)
- [exp1-guided-tour.html](../../../sites/ezkey-org/exp1-guided-tour.html)
- [ENDPOINT.md](../../../docs/ENDPOINT.md) — tenant and admin provisioning
- [LIFECYCLE_GOVERNANCE.md](../../../docs/LIFECYCLE_GOVERNANCE.md)
- `I-2026-05-23-exp1-anonymous-evaluator-onboarding.md` — backlog seed
- `TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut.md` — first implementation slice
- `I-2026-0011`, `I-2026-0026`, `F-public-site`, `F-provisioning-procedures`

## Grill outcome (2026-05-23)

Grill Me complete. Decisions recorded in
[`../backlog/grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md`](../backlog/grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md).

Key orientation lock: signup creates **empty tenant + activation code only**; guided tour steps 2–7
(integration, API key, enrollment, Acme demo session key) remain evaluator-driven pedagogy.

## Next step

Execute **`TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut`** when prioritized.
