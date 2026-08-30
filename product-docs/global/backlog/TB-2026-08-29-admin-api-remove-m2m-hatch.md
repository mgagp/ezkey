# Tracer Bullet Brief — `TB-2026-08-29` Remove the Admin API API-key M2M hatch

## Metadata

- **ID:** `TB-2026-08-29-admin-api-remove-m2m-hatch`
- **Status:** `done`
- **Related idea:** `I-2026-08-29-admin-api-remove-m2m-hatch`
- **Created at:** `2026-08-29`
- **Updated at:** `2026-08-29`
- **Captured by:** Marc
- **Posture:** `single-pass`
- **Supersedes (remaining work of):** `TB-2026-05-25-admin-api-key-acceptance-flag` deferred removal

## Objective

Prove that Admin API no longer authenticates API keys at all, that operator auth-attempt
and API-key **lifecycle** flows still work on Admin Bearer/session, that Integration API
remains the sole M2M auth-attempt backend, and that EXP1 Compose + upgrade docs are
positioned so the next Lightsail rolling update lands ACME on Integration API.

## Current state (do not rediscover)

R1 already shipped (`I-2026-0004` / `TB-2026-05-25`):

- Integration API (`7080`) is the canonical M2M surface.
- Admin API rejects `ROLE_API_KEY` by default via `ApiKeyAuthAttemptsAcceptanceFilter`.
- Local clean-start ACME already uses `EZKEY_ADMIN_API_URL=http://integration-api:7080`.
- Java SDK default base URL is already `http://localhost:7080`.
- TypeScript SDK is already Integration-API-only.

This slice is the **deferred removal**, not a new architecture. The leftover hatch and
the EXP1 ACME mis-wire are the work.

Known EXP1 drift (must be fixed in this slice):

| Location | Today | Target |
| --- | --- | --- |
| `experimental-hybrid/lightsail/docker-compose.yml` `demo-app-acme` | `EZKEY_ADMIN_API_URL: http://admin-api:9080` | `http://integration-api:7080` |
| `experimental-hybrid/local/.env.example` | `EZKEY_ADMIN_API_URL=https://exp1-admin-api.ezkey.org` | `https://exp1-integration-api.ezkey.org` |
| Live VM `.env` (not in git) | may still set `EZKEY_ADMIN_AUTH_API_KEY_AUTH_ATTEMPTS_ENABLED` | delete that key during upgrade |

## Locked decisions

1. **Direct cutover.** No property, no opt-in, no “minimal Admin+Auth without Integration API”
   product path.
2. **Remove Admin API-key authentication entirely** (`ApiKeyAuthenticationFilter` and
   `ApiKeyAuthAttemptsAcceptanceFilter`). Do not keep a toggle. Do not keep a permanent
   reject-filter that still parses API keys.
3. **Post-cutover error contract:** HTTP Basic API-key credentials on Admin API → **401
   Unauthorized** (unauthenticated). Do **not** keep
   `https://ezkey.io/problems/admin/api-key-auth-attempts-disabled`. Spring’s existing
   401 entry point is enough; a custom “use Integration API” problem body is optional
   polish and is **not** an exit criterion.
4. **Admin `AuthAttemptController` stays.** It is the operator surface (Admin UI Test Auth,
   list, get, wait, cancel, pending-count). Shared URL paths with Integration API are
   intentional, not a duplicate to delete.
5. **API-key lifecycle stays on Admin API** (create / list / get / revoke) with admin
   Bearer/session only.
6. **EXP1 is a lab host, not an architecture exception.** The next rolling upgrade must
   apply the new Compose wiring. Do not re-enable the hatch on the VM to “keep ACME
   working.”
7. **Do not rename** Demo ACME `ezkey.admin-api-url` / `EZKEY_ADMIN_API_URL` in this
   slice. Only the **value** must be Integration API.

## Keep / remove matrix

| Keep | Remove from Admin API |
| --- | --- |
| `AuthAttemptController` operator routes under Bearer/session: list, get, create, wait, cancel, pending-count | `ApiKeyAuthenticationFilter` |
| `@PreAuthorize` tightened to admin roles only (`hasRole('ADMIN')` / existing `canAccess*` object checks) | `ApiKeyAuthAttemptsAcceptanceFilter` |
| API-key lifecycle controllers/services (admin auth) | `AdminApiKeyAuthAttemptsProperties` and `ezkey.admin.auth.api-key-auth-attempts-enabled` / `EZKEY_ADMIN_AUTH_API_KEY_AUTH_ATTEMPTS_ENABLED` |
| Integration API `ROLE_API_KEY` create / wait / cancel / get (unchanged) | `ROLE_API_KEY` branches in Admin `AuthAttemptController` (`extractApiKeyId`, `extractIntegrationId`, `validateEnrollmentOwnership`) |
| Admin UI auth-attempt screens | `hasAnyRole('ADMIN', 'API_KEY')` on Admin auth-attempt mappings |
| | Admin `AccessControlService` `ROLE_API_KEY` branches (Integration API keeps its own ACL) |
| | `AdminApiProblemCatalog` `api-key-auth-attempts-disabled` type/title/detail |
| | Admin-only docs that present the opt-in as a supported install |

Leave Admin `ezkey.api-key.rate-limit.create-auth-attempt.*` / `wait-auth-attempt.*` **only if**
a remaining Admin Bearer path still uses them. If they exist solely for the removed API-key
path, delete the Admin wiring and config rows. Do **not** touch Integration API rate limits.

## Implementation traps (closed)

- **Do not** delete Admin `AuthAttemptController` or strip create/wait because the paths
  look like M2M.
- **Do not** delete only the acceptance filter. That re-opens M2M.
- **Do not** keep the property “just in case” for a local compatibility artifact.
- **Do not** hand-edit generated OpenAPI under `specs/`. Operator auth-attempt paths
  **remain** on the Admin spec; what disappears is API-key **security**, not those
  operations. Refresh via clean-start + `scripts/update-specs.sh` only if a security
  scheme or annotation actually changes.
- **Do not** treat TypeScript SDK as in-scope rewrite; it already targets Integration API.
  Touch it only if a leftover sentence still mentions the Admin hatch.
- **Do not** change EXP1 public hostnames (`exp1-admin-api`, `exp1-integration-api`,
  `exp1-demo-acme`).

## First executable slice

One PR, this order:

1. Remove Admin API-key auth filters, property class, SecurityConfig wiring, problem-catalog
   entries, and `ROLE_API_KEY` controller/ACL branches. Tighten `@PreAuthorize`.
2. Update unit tests listed below (delete hatch tests; keep operator tests; drop Admin
   API-key ownership cases).
3. Retarget EXP1 Compose + local companion env example + upgrade docs (see EXP1 section).
4. Update Bruno negatives, functional reject test, and product/config docs.
5. `./scripts/build.sh`, then clean-start + the functional/Bruno checks below.

## EXP1 upgrade positioning

Repo changes in this slice (so the next upgrade cannot keep the old wire):

1. `experimental-hybrid/lightsail/docker-compose.yml`
   - `demo-app-acme.environment.EZKEY_ADMIN_API_URL: http://integration-api:7080`
   - `depends_on`: require `integration-api` healthy (keep admin-api if ACME still reads
     bootstrap artifacts; do not keep Admin as the M2M base URL).
2. `experimental-hybrid/local/.env.example`
   - ACME base URL → `https://exp1-integration-api.ezkey.org`
3. `experimental-hybrid/README.md` (ACME “App configuration” still says credentials are
   “for the Admin API”) → Integration API.
4. `experimental-hybrid/lightsail/.env.example`
   - Add an operator note: the acceptance env var is **removed**; if it exists on the VM,
     delete it. Do not document it as a supported override.
5. `experimental-hybrid/BACKEND_ROLLING_UPDATE.md` and `DEPLOYMENT_PLAYBOOK.md`
   - Add a short **cutover recipe** (not a second upgrade tool):

**Required EXP1 apply (after this PR is on the branch you deploy):**

```text
./experimental-hybrid/scripts/full-exp-environment-upgrade.sh rolling --include-demo-acme
```

That syncs Compose, recreates Admin API (hatch gone) and ACME (new base URL), and keeps
Postgres. A partial recreate that updates Admin API **without** syncing Compose and
recreating `demo-app-acme` leaves EXP1 on the old dual-routing wire.

On the VM, after sync:

- Confirm `demo-app-acme` env is `http://integration-api:7080`.
- Remove `EZKEY_ADMIN_AUTH_API_KEY_AUTH_ATTEMPTS_ENABLED` from the live `.env` if present.
- Smoke: ACME login create/wait succeeds against Integration API; Admin API
  `POST /api/v1/auth-attempts` with API-key Basic returns **401**.

EXP1 remains a temporary lab host. This slice does **not** invent a new domain model; it
makes the existing three-API EXP1 stack match the locked product boundary.

## Tests to deploy

### Unit (must change in the same PR)

| Test | Action |
| --- | --- |
| `ApiKeyAuthAttemptsAcceptanceFilterTest` | **Delete** with the filter. |
| `AdminCorsTestFilterBeans` | Remove both Admin API-key filter stubs. |
| `AuthAttemptController` / `AuthAttemptControllerTest` | Keep Bearer/admin cases. Drop any `ROLE_API_KEY` create/wait cases. |
| `AuthAttemptControllerOwnershipTest` | **Delete** API-key ownership cases (`setupApiKeyAuthentication`). Keep admin-auth cases. Integration API already owns M2M ownership. |
| `AccessControlServiceTest.ApiKeyAccessTests` | **Delete** with the Admin `ROLE_API_KEY` branches. |
| `EncryptionKeyControllerSecurityWebMvcTest` (and similar “API key must not reach this admin route”) | Keep the **deny** intent: Basic API-key (or unauthenticated) must not reach the route. Expect **401**, not a role-based 403 that assumes `ROLE_API_KEY` authenticated. |
| New focused SecurityConfig / filter-chain test (optional but useful) | One WebMvc/security test: Admin `POST /api/v1/auth-attempts` with HTTP Basic API-key credentials → **401**; same route with admin auth still authorized at the security layer. |

Do **not** add a test that the acceptance flag can be set `true`.

### Functional (`ezkey-tests`, after clean-start)

| Test | Action |
| --- | --- |
| `ApiKeySecurityTest` M2M create on Integration API | **Keep** — still 201 on `configureForIntegrationApi`. |
| `ApiKeySecurityTest.testAdminApiRejectsApiKeyAuthAttemptByDefault` | **Rewrite:** Admin API + API-key Basic → **401**. Stop asserting `api-key-auth-attempts-disabled`. |
| Other `configureForIntegrationApi` auth-attempt / rate-limit tests | **Keep** unchanged unless they mention the hatch. |

No new long functional suite. One rewritten reject test is the contract lock.

### Bruno

| Collection | Action |
| --- | --- |
| `bruno/auth-attempts-admin/create-with-api-key.bru` | Keep as the **one** Admin negative. Assert **401**. Docs: use Integration API for M2M. |
| `wait-with-api-key.bru`, `cancel-with-api-key.bru`, `create-with-useridentifier-api-key.bru` | Same 401 assertion **or** fold into comments that point at the Integration API collection. Do not keep opt-in `=true` instructions. |
| Integration API auth-attempt Bruno folder | Unchanged canonical happy path. |

### Specs / clients

- Do **not** expect Admin OpenAPI to lose `/api/v1/auth-attempts*`. Those stay for operators.
- Run `scripts/update-specs.sh` only if security-scheme annotations change; then regenerate
  Admin UI clients if the dispatched Admin spec changes.
- Java SDK README: remove the “disabled by default / set true for minimal install” sentence.
  Default URL stays Integration API.

## Agent validation autonomy

**Funded scope:** the operator is paying the token budget for **end-to-end delivery**,
including every functional proof that is pertinent to this cutover. Do **not** shorten
the ladder to save tokens. Do **not** stop at `build.sh` and call the slice done. Do
**not** treat clean-start, Bruno, Admin UI + Demo Device, logs, or DB checks as
“nice if cheap.” Run the full autonomy path in the same implementation session.

The implementing agent has **full autonomy** to use that stack without waiting for a
second approval. Combine the tools that prove the adjusted API surface contracts.
Collect evidence while running (command, status, and the decisive response or UI
outcome). Do not invent a leftover hatch flag to test: the new posture is **no Admin
API-key auth** plus **EXP1/ACME base URL = Integration API**.

The only legitimate skip is an environment that cannot start Docker or the browser.
Then say exactly what was blocked. Do not substitute a hand-edited spec or a
compile-only closeout.

### Tools the agent may use without waiting

| Tool | Use |
| --- | --- |
| `./scripts/build.sh` (Git Bash) | Canonical Java baseline: Spotless, Checkstyle, reactor install, unit tests. |
| `ezkey-tests/clean-start.sh` | Full local Docker stack (empty DB, default params). |
| API / container logs | `docker logs` on `ezkey-admin-api`, `ezkey-integration-api`, `ezkey-auth-api`, ACME if used. |
| Database | Opportunistic checks (`docker exec ezkey-postgres …` or MCP Postgres) when an API response is ambiguous (key created, attempt row, integration ownership). |
| Browser (Admin UI) | `http://localhost:<admin-ui-port>` (often `5173` or `3090`). Login as **`admin.docker`**. Hostname `localhost`, not `127.0.0.1`, on Windows. |
| Demo Device | `http://localhost:8083/phone/ezkey` — open the `admin.docker` enrollment, **Approve**, then `[data-testid=demo-device-back-to-enrollments]`. That unlocks the full Admin console. |
| Bruno CLI | Targeted Admin-negative and Integration-positive requests against the live stack. Prefer existing collections; use `./scripts/bruno-health.sh` only when it already covers the path. |
| Functional module | `ApiKeySecurityTest` and any extra `ezkey-tests` cases needed after clean-start. |

Playwright is **not** required as a new suite. A live Admin UI + Demo Device session **is**
in scope for operator-path evidence. Recipe:
[`docs/testing/AGENT_UI_VALIDATION.md`](../../../docs/testing/AGENT_UI_VALIDATION.md) § MCP
browser.

### Required live proofs (positive / negative)

Run these on the clean-start stack after the code change. They are **in the funded
session**, not a follow-up the operator must ask for.

| Proof | Kind | Expected |
| --- | --- | --- |
| `./scripts/build.sh` | Baseline | Green (style + unit). |
| Admin `POST /api/v1/auth-attempts` with API-key HTTP Basic | **Negative** | **401**. No `api-key-auth-attempts-disabled` body. |
| Integration API `POST /api/v1/auth-attempts` with the same API-key | **Positive** | **201** and a persisted attempt (API and/or DB). |
| Admin API API-key **lifecycle** with `admin.docker` Bearer (UI or Bruno) | **Positive** | Create/list still works (operator surface). |
| Admin UI login `admin.docker` via Demo Device Approve | **Positive** | Lands on dashboard; console usable. |
| Operator auth-attempt (Admin UI Test Auth **or** Admin Bearer create + Demo Device approve) | **Positive** | Attempt completes; proves Admin auth-attempt routes were **not** deleted. |
| Compose / config docs | **Docs** | Hatch property gone from `CONFIGURATION.md` and config index. EXP1 ACME URL documented as Integration API. No “set the flag true” leftover. |

Use Bruno when it is the fastest way to hit the exact Admin-negative and Integration-positive
contracts. Use the browser + Demo Device when the proof is operator workflow. Use the DB
when HTTP alone is not enough.

### Closeout summary (mandatory)

The implementation turn ends with a short report to the operator covering:

1. **Implemented** — what was removed, what was kept, EXP1/ACME wiring and doc changes.
2. **Tested** — unit / `build.sh` / clean-start functional / Bruno / browser+Demo Device.
   Name any environment-blocked step; do not omit a live proof to save tokens.
3. **Evidence** — the decisive positives and negatives (status codes, problem types or
   their absence, UI outcome, optional DB row ids). Enough that a cold reader can see the
   new surface contract works.

## Documentation to update in the same PR

- `docs/API_KEYS_GUIDE.md` — drop opt-in; lifecycle stays on Admin API.
- `ezkey-admin-api/CONFIGURATION.md` and `docs/configuration/README.md` — delete the
  property row.
- `docs/ENDPOINT.md` — only the Admin vs Integration auth-attempt **caller** wording
  (section-scoped). Do not ritual-rewrite the whole file.
- `docs/API_SECURITY_MATRIX.md` — Admin auth-attempt rows are `ROLE_ADMIN` only;
  `ROLE_API_KEY` is Integration API only.
- `ezkey-sdk/java/README.md` — remove hatch language.
- `ezkey-admin-api/README_RATE_LIMITING.md` if it still describes Admin API-key
  create/wait limits after those properties are removed.
- EXP1 files listed above.
- `ezkey-demo-app-acme` docs only if they still say to point API keys at Admin API
  **9080**. Do not rename the legacy env var.

## Out of scope

- Integration API behavior, Auth API behavior, Admin UI routes.
- Renaming ACME `EZKEY_ADMIN_API_URL`.
- New ADR (boundary already decided; this TB executes the deferred removal).
- GitHub issue (optional visibility only; not required to start coding).

## Rollback or fallback posture

Revert the PR. There is no residual compatibility flag to leave behind. If EXP1 ACME
breaks mid-upgrade, finish Compose sync + ACME recreate against Integration API; do
**not** turn the hatch back on.

## Exit criteria

- Admin API has no API-key authentication filter and no acceptance property.
- Admin `POST /api/v1/auth-attempts` with API-key Basic returns **401**.
- Integration API create/wait with API-key still returns **201** / wait contract.
- Admin Bearer create/list/get/wait/cancel and API-key lifecycle still work.
- EXP1 Lightsail Compose ACME URL is Integration API; local companion example matches;
  rolling-upgrade docs name Admin + ACME + Compose sync as the apply set.
- No canonical doc still presents “set the flag true for minimal installs.”
- Agent closeout includes `./scripts/build.sh`, clean-start live proofs (Admin 401 +
  Integration 201), operator Admin UI + Demo Device evidence, and the implemented /
  tested / evidence summary.

## Links

- Idea: [`ideas/I-2026-08-29-admin-api-remove-m2m-hatch.md`](ideas/I-2026-08-29-admin-api-remove-m2m-hatch.md)
- Predecessor: [`ideas/I-2026-0004-admin-api-key-acceptance-flag.md`](ideas/I-2026-0004-admin-api-key-acceptance-flag.md),
  [`TB-2026-05-25-admin-api-key-acceptance-flag.md`](TB-2026-05-25-admin-api-key-acceptance-flag.md)
- Vision: [`../vision/V-2026-0003-api-key-acceptance-posture.md`](../vision/V-2026-0003-api-key-acceptance-posture.md)
- Feature: `F-integration-api-maturity`
- Prompt pointer: [`.github/prompts/plan-architectureCutover.prompt.md`](../../../.github/prompts/plan-architectureCutover.prompt.md)
