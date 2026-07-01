# V-2026-06-30 — SDK dogfood integrity posture

- **Date:** `2026-06-30`
- **Status:** `parked`
- **Intent:** Separate **cryptographic dogfood** (Ezkey secures Ezkey) from **SDK consumption
  dogfood** (first-party apps use published SDKs where the integration shape matches). Analytical
  record retained; active program (`I-2026-0031`) paused for September 2026 operable-release focus.
- **Signals:** Operator review (2026-06-30) — Admin UI uses Orval against Admin API; published
  Integration SDKs (Java, TypeScript) are not consumed by Admin UI or Admin API passwordless paths;
  concern that external messaging ("we ship SDKs") may overclaim internal validation.
- **Potential impact:** `admin-ui`, `admin-api`, `sdk-java`, `sdk` (TypeScript workspace),
  `ezkey-demo-app-acme`, `docs`, `product-docs`, public site copy.
- **Next step:** None while parked. Resume via `I-2026-0031` when a distribution trigger fires
  (see idea § Parked). M2M alignment stays on `I-2026-0004`, not this vision.
- **Captured by:** Marc

## Problem shape

Ezkey promotes **integrity** and **eat your own dog food** in multiple places (Admin API
passwordless auth, bootstrap MFA, public essays). A separate product line ships **Integration API
SDKs** for backend developers who create auth attempts and wait for device approval.

The question is whether first-party surfaces — especially **Admin UI** (React/TypeScript) and
**Admin API** (Java) — should consume those SDKs, and what "dogfood" means when they do not.

## Two distinct dogfood layers

| Layer | Question | Current posture (2026-06-30) |
| ----- | -------- | ---------------------------- |
| **A — Cryptographic / product** | Does Ezkey authenticate operators and admins with Ezkey MFA instead of passwords? | **Yes.** Admin API passwordless login uses `AuthAttemptService` and enrollment binding via `ezkey-core`. Admin UI drives `/admin/auth/login` + `passwordless-wait` against Admin API. |
| **B — SDK consumption** | Do first-party apps use published SDK packages for the same integration shape we recommend to customers? | **Partial.** `ezkey-demo-app-acme` uses Java `EzkeyClient` for M2M auth attempts. TypeScript `ezkey-integration-sdk` is exercised by `ezkey-sdk-typescript` demo API only. **Admin UI and Admin API auth paths do not use either SDK.** |

Layer A is strong and should remain the primary meaning of "Ezkey secures Ezkey" in operator docs.

Layer B is where the credibility gap lives: we publish SDKs but our flagship Admin console does not
demonstrate them — because its integration shape is different (see below).

## Why Admin UI is not a direct SDK consumer today

This is **architecture**, not an accidental omission:

1. **Published SDK scope** — Java and TypeScript SDKs target **Integration API** M2M flows:
   API key + secret, `createAuthAttempt`, `wait`, `cancel`. TypeScript SDK explicitly excludes
   browser-first usage (`PRODUCT_BRIEF.md` non-goals).
2. **Admin UI shape** — Browser SPA calling **Admin API** with session JWT or cookie build;
   passwordless login uses **admin-specific** endpoints (`/api/v1/admin/auth/*`), not Integration
   API credentials.
3. **Admin API shape** — Passwordless admin auth calls **`ezkey-core` domain services in-process**
   (`AdminAuthService` → `AuthAttemptService`), not HTTP through `EzkeyClient`. That is deeper
   coupling than SDK usage and is appropriate for the auth authority — but it does not stress-test
   the published SDK.

**Conclusion:** Forcing Admin UI onto `ezkey-integration-sdk` would be the wrong tool (credentials
model, API surface, browser boundary). Claiming we already dogfood the SDK via Admin UI would be
misleading.

## What we do consume today (SDK-adjacent matrix)

| Consumer | SDK / client pattern | API surface | Dogfood layer |
| -------- | -------------------- | ----------- | ------------- |
| Admin UI | Orval-generated Admin API client + hand `api-client` | Admin API (bearer/cookie) | A yes; B no (no published admin-session SDK) |
| Admin API auth | `ezkey-core` services | In-process domain | A yes; B no |
| `ezkey-demo-app-acme` | Java `EzkeyClient` | Integration API (target); historically Admin API mis-pointing noted in `V-2026-0003` | B yes (M2M reference) |
| `ezkey-sdk-typescript` demo | `EzkeyIntegrationClient` | Integration API | B yes (reference only) |
| Mobile app | Orval Auth API generated types | Auth API (device crypto) | A yes; B N/A (device protocol, not M2M SDK) |

## Strategic orientation

1. **Do not collapse layers A and B** in marketing or internal docs. Both matter; they answer
   different questions.
2. **Treat SDK dogfood as a bounded program**, not an Admin UI rewrite. Validate SDKs where the
   integration shape matches: demo apps, future BFFs, CLI, automation — then extend SDK surface
   only where analysis proves a shared abstraction.
3. **Prefer honest narrative over forced coupling.** Orval + Admin API for the console is a
   legitimate first-party pattern (spec-first admin client). The gap is **missing explicit canon**
   that explains when to use Integration SDK vs Admin API client vs device/mobile path.
4. **Align existing M2M consumers first** — close `I-2026-0004` / `V-2026-0003` (Demo ACME +
   Java SDK examples → Integration API) before inventing new admin-side SDK layers.
5. **Consider a future "admin session client"** (TypeScript, browser-safe) only if we want Admin
   UI to share a packaged passwordless-login flow with third-party admin tools — a separate product
   decision, not a prerequisite for integrity.

## Priority relative to September 2026 operable release

SDK consumption dogfood is **distribution credibility**, not a blocker for core operator workflows
already secured by layer A. **Parked 2026-06-30:** pre-analysis complete; program deferred so effort
stays on integrity cluster and operable-release structural work. M2M alignment (`I-2026-0004`)
remains separate and in flight.

## Related artifacts

- [`I-2026-0031`](../backlog/ideas/I-2026-0031-sdk-dogfood-first-party-consumption.md)
- [`2026-06-30-sdk-dogfood-grill-me.md`](../backlog/grill-sessions/2026-06-30-sdk-dogfood-grill-me.md)
- [`V-2026-0003`](V-2026-0003-api-key-acceptance-posture.md) — Integration API as canonical M2M
- [`I-2026-0004`](../backlog/ideas/I-2026-0004-admin-api-key-acceptance-flag.md)
- [`design-principles.md`](../design-principles.md) — #3 backend-first, #4 trust boundaries, #13 transparency
- [`features-and-phases.md`](../features-and-phases.md) — `F-sdk-and-cli-growth`
