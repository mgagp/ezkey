# Grill Me — 2026-05-23 (EXP1 anonymous evaluator onboarding)

## Session control

| Field | Value |
|-------|--------|
| **Vision** | `V-2026-05-23-anonymous-evaluator-self-registration` |
| **Backlog** | `I-2026-05-23-exp1-anonymous-evaluator-onboarding` |
| **Tracer bullet** | `TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut` |
| **Cross-links** | [`experimental-preview-exp1.html`](../../../../sites/ezkey-org/experimental-preview-exp1.html), [`exp1-guided-tour.html`](../../../../sites/ezkey-org/exp1-guided-tour.html), [`LIFECYCLE_GOVERNANCE.md`](../../../../docs/LIFECYCLE_GOVERNANCE.md), `I-2026-0011`, `I-2026-0026` |
| **Status** | `complete` |
| **Date** | `2026-05-23` |

## Settled decisions (operator-confirmed)

| ID | Decision |
| ---- | -------- |
| G1 | Dedicated signup page on **`ezkey.org`** calling a narrow public Admin API endpoint; **not** Admin UI `/signup` in v1 |
| G2 | New unauthenticated route on Admin API, gated by **`ezkey.evaluator.self-registration.enabled`** (EXP1 installation profile only) |
| G3 | Server-generated tenant slug (`eval-{random}`) as canonical name; optional short display label (max 40 chars, no email/URL) |
| G4 | Synthetic admin username (`eval-admin-{suffix}`); **no email**; placeholder first/last name (`Evaluator`, `Preview`) |
| G5 | **Empty tenant only** at signup — tenant + pending Tenant Admin + activation code; **no** integration, API key, or enrollment (guided tour steps 2–7 remain manual) |
| G6 | **5 creations/day** global default (configurable); generic "capacity reached" when exhausted |
| G7 | **1 successful signup / IP / 24h**; failed attempts do not consume the daily global cap |
| G8 | Structured audit event + log/metric counter in v1; **no** new Global Admin UI screen |
| G9 | Public copy: **"Instant preview access (limited daily slots)"** primary; email path as alternative |
| G10 | Independent lanes — no IP/email dedup between anonymous signup and email request |
| G11 | CORS: allow `https://ezkey.org` (+ Cloudflare Pages preview origins as needed) on the signup endpoint only; feature flag off → **404** |
| G12 | Response: `{ activationCode, activationCodeExpiresAt, adminUiUrl, guidedTourUrl, tenantLabel }` — **never** `adminId` to anonymous callers |

## G5 clarification (recorded)

Auto-provisioning an Acme integration at signup was **rejected** because:

1. Guided tour steps 2–3 (create integration, create API key) are intentional pedagogy.
2. API key secret is one-time display; demo Acme holds credentials **session-scoped in browser** via Configure API Key — signup cannot wire the demo without wrong secret-handling posture.

## Additional risks (accepted for v1)

- CORS misconfiguration — mitigate with explicit allowed-origins config.
- Activation code shown in browser — copy UX must warn user to save locally.
- Orphaned anonymous tenants — monitor via audit/logs; no auto-cleanup in first cut.
- IP evasion of per-IP cap — accepted; global daily cap bounds blast radius.

## Outcome

Grill complete. Promoted to `TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut`.

## Links

- [`../ideas/I-2026-05-23-exp1-anonymous-evaluator-onboarding.md`](../ideas/I-2026-05-23-exp1-anonymous-evaluator-onboarding.md)
- [`../TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut.md`](../TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut.md)
- [`../../vision/V-2026-05-23-anonymous-evaluator-self-registration.md`](../../vision/V-2026-05-23-anonymous-evaluator-self-registration.md)
