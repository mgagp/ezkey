# Tracer Bullet Brief — `TB-2026-05-23` EXP1 anonymous evaluator signup first cut

## Metadata

- **ID:** `TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut`
- **Status:** `under-review`
- **Related idea:** `I-2026-05-23-exp1-anonymous-evaluator-onboarding`
- **Grill session:** [`../grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md`](../grill-sessions/2026-05-23-exp1-anonymous-evaluator-onboarding-grill-me.md)
- **Created at:** `2026-05-23`
- **Updated at:** `2026-05-23`

## Objective

Deliver the smallest end-to-end slice that proves anonymous EXP1 evaluator onboarding: a visitor on
`ezkey.org` self-provisions an **empty tenant** and a **pending Tenant Admin** with an **activation
code**, then continues the existing guided tour unchanged (steps 2–7 manual).

## Boundaries in scope

- **Admin API:** installation-scoped feature flag + one public POST endpoint + rate limiting (global
  daily cap + per-IP) + CORS for `ezkey.org` origins + audit event.
- **Internal reuse:** existing tenant creation and `ACTIVATION_CODE` tenant-admin provisioning
  semantics via a dedicated service path (not exposed authenticated controllers).
- **ezkey.org:** signup page (English first) + copy update on `experimental-preview-exp1.html`.
- **Configuration:** properties documented in `ezkey-admin-api/CONFIGURATION.md` (prefix TBD, e.g.
  `ezkey.evaluator.self-registration.*`).
- **Tests:** unit tests for limiter logic; controller/service tests for happy path and cap exhaustion.

## Out of scope

- Admin UI `/signup` route.
- Auto-provision integration, API key, or enrollment at signup (G5).
- CAPTCHA, invite tokens, email verification.
- Global Admin UI counter screen (audit/logs only in v1).
- French signup page (follow-up after English-first validation).
- Tenant cleanup / TTL policy.
- Non-EXP1 installations.

## Critical flows

### Nominal path

1. Visitor opens `ezkey.org` signup page (linked from EXP1 preview copy).
2. Optionally enters a short tenant label; submits.
3. Browser POSTs to public Admin API endpoint (CORS with credentials off).
4. Backend checks feature flag, per-IP limit, global daily cap.
5. Backend creates tenant (empty), tenant admin (`ACTIVATION_CODE`), returns activation code + URLs.
6. Page displays code with copy/save warning; links to Admin UI and guided tour.
7. Evaluator completes guided tour from step 1 (activation + mobile), then steps 2–7 unchanged.

### Critical exception paths

- Feature disabled → **404** (no enumeration).
- Global daily cap reached → generic capacity message; does not consume IP success quota.
- IP already succeeded in 24h → generic rejection.
- Invalid label (too long, URL/email pattern) → **400** validation error.
- CORS origin not allowed → browser blocks (config must include production + preview origins).

## Implementation sketch

| Layer | First-cut deliverable |
| ----- | --------------------- |
| Config | `enabled`, `daily-cap` (default 5), `per-ip-window-hours` (24), `per-ip-max-success` (1), `allowed-origins`, URL templates for `adminUiUrl` / `guidedTourUrl` |
| Service | `EvaluatorSelfRegistrationService` — atomic tenant + admin provisioning under system authority |
| Controller | `POST /api/v1/public/evaluator-signup` (path TBD at implementation; unauthenticated, narrow DTO) |
| Rate limit | Dedicated limiter or extension of existing rate-limit patterns; persisted or in-memory counter for daily cap (acceptable in-memory for EXP1 single node) |
| Audit | Event type e.g. `evaluator_self_registration_completed` with tenant slug, client IP (coarse), no activation code in audit payload |
| Site | `exp1-signup.html` + EN copy on `experimental-preview-exp1.html` |

## Evidence plan

- Unit tests: daily cap, IP cap, flag off → 404.
- Admin API tests: happy path returns activation code fields; no `adminId` in response.
- Manual exploratory: signup → activation in Admin UI → guided tour step 2 (integration create) succeeds.
- `CONFIGURATION.md` updated for new properties.
- `ENDPOINT.md` documents public signup endpoint (hand-written ops doc, not generated spec edit).
- Postman collection update if a public signup request is added to evaluator flows.

## Quality gates

- **Boundary gate:** public endpoint cannot call arbitrary provisioning operations; single atomic use case only.
- **Security gate:** no adminId leak; activation code treated as secret in page copy; CORS minimal.
- **Pedagogy gate:** guided tour steps 2–7 unchanged; no integration/key in signup response.
- **Proportionality gate:** rate limits configurable; operator can disable via flag without code deploy of site copy.

## Implementation outcome (2026-05-23)

- Admin API: `POST /api/v1/public/evaluator-signup` with feature flag, rate limits, audit event.
- Site: [`exp1-signup.html`](../../../../sites/ezkey-org/exp1-signup.html) + EXP1 preview copy (EN/FR).
- Docs: `CONFIGURATION.md`, `ENDPOINT.md`, Postman public admin collection, Lightsail `.env.example`.

Pending operator validation on EXP1 deploy (enable flag + CORS + redeploy site).

## Exit criteria

`TB-2026-05-23` is validated when:

1. EXP1 (or clean-start with flag enabled) accepts anonymous signup and returns activation code + URLs.
2. Rate limits enforce 5/day global and 1 success/IP/24h defaults.
3. Created tenant is empty (no integrations); evaluator can complete tour step 2 after activation.
4. `ezkey.org` signup page and EXP1 preview copy describe instant access + email alternative.
5. Audit/log evidence allows operator to count daily signups without a new Admin UI screen.

## Links

- [`I-2026-05-23-exp1-anonymous-evaluator-onboarding.md`](I-2026-05-23-exp1-anonymous-evaluator-onboarding.md)
- [`../../vision/V-2026-05-23-anonymous-evaluator-self-registration.md`](../../vision/V-2026-05-23-anonymous-evaluator-self-registration.md)
- [`../../../../sites/ezkey-org/exp1-guided-tour.html`](../../../../sites/ezkey-org/exp1-guided-tour.html)
