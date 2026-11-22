# Backlog Idea — `I-2026-11-21-demo-login-reference-integration` Demo Login replaces the Demo App Acme

## Metadata

- **ID:** `I-2026-11-21-demo-login-reference-integration`
- **Status:** `active`
- **Priority:** `P2`
- **Created at:** `2026-11-21`
- **Updated at:** `2026-11-21`
- **Captured by:** Marc
- **Component tags:** `demo-login`, `sdk` (TypeScript), `infra (community)`, `site`, `docs`
- **GitHub issue:** `#1430`
- **Supersedes:** the Acme part of
  [`V-2026-10-07-multi-tenant-closed-testing-demo-app`](../../vision/V-2026-10-07-multi-tenant-closed-testing-demo-app.md)
- **Related:** [`I-2026-0031`](I-2026-0031-sdk-dogfood-first-party-consumption.md) (SDK dogfood,
  phase 1 realised here with the TypeScript SDK), `#1365` (single onboarding path), `#1411`
  (JavaScript SDK drift, fixed here), `#1433` (API key optimistic-lock 401 found by the matrix)

## Intent

Replace the Demo App Acme — an all-in-one Spring Boot / Thymeleaf app from the initial vibe-coding
phase, with pasted API keys and per-tenant `/t/{code}` links that no real application has — by a
small application shaped like what an adopter builds: a login box, a back end that holds the API
key and calls the Integration API through an SDK, and its own users. Destroy and replace: no
adopter, no migration (no-compat posture, `design-principles.md` §2).

## Decisions (Marc, 2026-11-18 and 2026-11-21)

| # | Decision | Reason |
|---|----------|--------|
| D1 | Name `demo-login` (module `ezkey-demo-login`, host `demo-login.ezkey.online`) | Says what it does; every invented name was already taken somewhere. |
| D2 | Front end on the Admin UI stack (React, Vite, Tailwind), same login box, neo-brutalist yellow/pink | Same design choices; never mistaken for the Admin UI. |
| D3 | TypeScript back end with the TypeScript SDK | Covers the second SDK on a concrete integration; the TypeScript SDK had no Integration API client. |
| D4 | One Node container everywhere, no Cloudflare Worker | Local testability without a third-party account (clean start, or standalone against any Integration API); one runtime to test. The SDK stays fetch-only, so a Worker variant remains possible. |
| D5 | SQLite file (`node:sqlite`), users + hashed session tokens | No performance need, test data only; built into Node 24, no native dependency. |
| D6 | CLI administration (`user add / remove / disable / list`), no admin screen | A handful of users; SSH on the community instance. |
| D7 | Hosted demo bound to the demo tenant; no pasted keys, no access codes | Realistic shape. The integrator loop with one's own key runs Demo Login locally (clean start or `ezkey-demo-login/docker-compose.yml`). |
| D8 | Context mode / business approval out of scope | Later. |

## Lock trace

| Lock | Where |
|------|-------|
| D1–D6 | `ezkey-demo-login/` (README, AGENTS, CONFIGURATION), `ezkey-sdk/javascript/src/integration-api.ts` |
| D4 local testability | `docker/docker-compose.yml` (`demo-login`), `ezkey-demo-login/docker-compose.yml` |
| D7 | `experimental-hybrid/lightsail/` (compose, Caddyfile, `.env` example), ezkey.org guided tour EN/FR, `product-docs/global/tester-onboarding.md` |
| Test matrix (21 cases, screenshots) | `ezkey-demo-login/docs/e2e/README.md` |
| D8 | deferred |

## Remaining

- Community deployment: DNS `demo-login.ezkey.online`, origin certificate coverage, image export,
  `.env` keys, remove the `demo-acme` DNS record and the old container/volumes. Separate go from
  Marc.
