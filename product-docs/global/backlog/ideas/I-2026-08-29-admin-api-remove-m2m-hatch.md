# Backlog Idea — `I-2026-08-29` Remove the Admin API API-key M2M hatch

## Metadata

- **ID:** `I-2026-08-29-admin-api-remove-m2m-hatch`
- **Status:** `done`
- **Priority:** `P1`
- **Created at:** `2026-08-29`
- **Updated at:** `2026-08-29`
- **Last reviewed at:** `2026-08-29`
- **Component tags:** `admin-api`, `integration-api`, `demo-acme`, `infra (EXP1)`, `docs`, `ezkey-tests`, `bruno`, `sdk-java`
- **Captured by:** Marc
- **Tracer bullet:** `TB-2026-08-29-admin-api-remove-m2m-hatch`
- **Supersedes (remaining work of):** `I-2026-0004` deferred “Removal” row
- **Predecessor:** `I-2026-0004`, `TB-2026-05-25-admin-api-key-acceptance-flag`, `V-2026-0003`

## Intent

Make Integration API the **only** backend that authenticates API keys for auth-attempt
lifecycle traffic. Remove the Admin API opt-in hatch
(`ezkey.admin.auth.api-key-auth-attempts-enabled` and the API-key authentication filter)
so there is no supported “Admin API also accepts M2M” posture — including on EXP1 after
the next rolling upgrade.

## Problem and value

- **Problem:** R1 (`I-2026-0004`) already defaults the hatch to deny, but the toggle, the
  Admin-side API-key filter, and dual-auth controller branches remain. EXP1 Lightsail ACME
  still sets `EZKEY_ADMIN_API_URL=http://admin-api:9080`, so the live lab is still wired
  for the old dual-routing shape. Docs and Bruno still describe an opt-in that this
  product no longer wants.
- **Expected value:** one M2M boundary, a smaller Admin attack surface, honest docs, and an
  EXP1 upgrade recipe that retargets ACME to Integration API instead of re-enabling the
  hatch.

## Scope

- **In scope:**
  - Remove Admin API API-key authentication and the acceptance property.
  - Keep Admin Bearer/session auth-attempt **operator** endpoints.
  - Keep API-key **lifecycle** (create / list / revoke) on Admin API with admin auth.
  - Retarget EXP1 ACME + playbook so the next upgrade lands on Integration API.
  - Update tests, Bruno, config docs, and leftover “minimal install” language.
- **Out of scope:**
  - Changing Integration API or Auth API behavior.
  - Renaming `EZKEY_ADMIN_API_URL` on Demo ACME (legacy env name; value must point at
    Integration API).
  - Cloudflare hostname / Caddy site-block redesign.
  - Admin UI changes (operator auth-attempt flows stay on Admin API).
  - A compatibility or migration mode.

## Funded delivery

The operator funds **end-to-end implementation and pertinent functional tests** in one
agent session (clean-start, Bruno, Admin UI + Demo Device, logs/DB as needed). The TB
autonomy section is mandatory, not a later optional pass.

## Key assumptions

- There is no production fleet that depends on the hatch.
- Local clean-start ACME already targets `http://integration-api:7080`.
- Admin UI Test Auth and auth-attempt lists use admin Bearer, not API keys.
- EXP1 already deploys Integration API; the missing piece is ACME + Admin hatch removal.

## Risks and exceptions

- **Filter-deletion trap:** removing only `ApiKeyAuthAttemptsAcceptanceFilter` while
  leaving `ApiKeyAuthenticationFilter` + `hasAnyRole('ADMIN', 'API_KEY')` **re-opens**
  M2M on Admin API. The TB locks full removal of Admin API-key authentication.
- **Controller-deletion trap:** Admin `AuthAttemptController` is the operator surface, not
  a duplicate to delete.
- **EXP1 leftover env:** a live VM `.env` may still set
  `EZKEY_ADMIN_AUTH_API_KEY_AUTH_ATTEMPTS_ENABLED=true`. The upgrade recipe must drop it
  and retarget ACME in the same pass.

## Promotion notes

Ready: decisions are locked on the tracer bullet (keep/remove matrix, 401 error contract,
test list, EXP1 rolling-upgrade recipe). No further grill.

## Links

- **Tracer bullet:** [`../TB-2026-08-29-admin-api-remove-m2m-hatch.md`](../TB-2026-08-29-admin-api-remove-m2m-hatch.md)
- **Predecessor idea:** [`I-2026-0004-admin-api-key-acceptance-flag.md`](I-2026-0004-admin-api-key-acceptance-flag.md)
- **Predecessor TB:** [`../TB-2026-05-25-admin-api-key-acceptance-flag.md`](../TB-2026-05-25-admin-api-key-acceptance-flag.md)
- **Vision:** [`../../vision/V-2026-0003-api-key-acceptance-posture.md`](../../vision/V-2026-0003-api-key-acceptance-posture.md)
- **Feature:** `F-integration-api-maturity`
- **Working prompt (pointer only):** [`.github/prompts/plan-architectureCutover.prompt.md`](../../../../.github/prompts/plan-architectureCutover.prompt.md)
