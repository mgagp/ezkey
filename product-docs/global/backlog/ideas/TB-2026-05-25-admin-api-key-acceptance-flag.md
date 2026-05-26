# Tracer Bullet Brief — `TB-2026-05-25` Admin API: configurable acceptance of API-key authentication

## Metadata

- **ID:** `TB-2026-05-25-admin-api-key-acceptance-flag`
- **Status:** `ready-for-implementation`
- **Related idea:** `I-2026-0004`
- **Created at:** `2026-05-25`
- **Posture:** `single-pass`

---

## Objective

Introduce a configuration property on Admin API that gates whether **API-key M2M auth-attempt traffic** is accepted. Default **`false`** everywhere (deny by default, security-first); optional **`true`** only for documented minimal deployments (Admin + Auth binaries, no Integration API). Validate:

1. Property works end to end (default `false`, opt-in `true`).
2. API-key auth-attempt requests are correctly rejected with RFC 9457 problem response.
3. SDK examples, Demo ACME, and functional tests target Integration API for M2M flows.
4. Documentation (CONFIGURATION.md, SDK README, API keys guide) reflects the new posture.

---

## Why now

- **Problem observed:** API-key auth was implicitly accepted on Admin API for auth attempts. Demo ACME + Java SDK were observed targeting Admin API while operators assumed Integration API — masked misalignment, weakened trust boundary.
- **Intent:** Deliberate posture by default; smaller Admin attack surface; clear operator/SDK guidance; PME minimal-install escape hatch without forcing a third binary.
- **Grill session:** All assumptions validated 2026-05-19; no blockers remain.

---

## Scope boundaries

### In scope

- **Admin API module** (`ezkey-admin-api`):
  - Add boolean configuration property: `ezkey.admin.auth.api-key-auth-attempts-enabled` (default: `false`).
  - Add servlet filter or security guard to intercept `ROLE_API_KEY` auth-attempt requests.
  - Return RFC 9457 `application/problem+json` response on rejection (`403 Forbidden`).

- **Admin API property documentation**:
  - Add entry to `ezkey-admin-api/CONFIGURATION.md` with obligation level, profile matrix, Docker env mapping.
  - Update central config index: `docs/configuration/README.md`.

- **API keys guide** (`docs/API_KEYS_*.md`):
  - Document minimal-install opt-in (`true` only for Admin + Auth deployments).
  - Clarify that Integration API is the canonical M2M surface for API-key auth attempts.
  - Update examples to use Integration API base URL.

- **SDK and Demo ACME**:
  - `ezkey-sdk` (Java): README examples and code comments point Integration API for M2M flows.
  - `ezkey-demo-app-acme`: configuration targets Integration API for API-key auth-attempt requests.
  - Verify no hardcoded Admin API base URL in M2M code paths.

- **Functional tests**:
  - Review `ezkey-tests` for auth-attempt flows using API keys.
  - Update any tests that were using Admin API base URL to target Integration API (or minimal-install opt-in scenario with property `true`).

- **Integration API** (`ezkey-integration-api`):
  - No behavior change; confirm API-key auth-attempt acceptance remains unchanged.
  - Verify existing tests validate API-key flows on Integration API.

### Out of scope

- Removing API-key code from Admin API code paths (deferred, not needed in R1).
- Per-endpoint policy matrix or granular scoping (simplicity principle).
- Profile-aware platform code or UI toggles (covered by `V-2026-0010`; not blocked on catalog).
- Full deprecation or sunset timeline.

---

## Technical design

### Admin API filter / guard implementation

**Property name:**
```
ezkey.admin.auth.api-key-auth-attempts-enabled
```

**Type:** Boolean  
**Default:** `false`  
**Environment variable mapping:** `EZKEY_ADMIN_AUTH_API_KEY_AUTH_ATTEMPTS_ENABLED` (Docker / deployment)

**Placement:**
- Add to `ezkey-admin-api/src/main/java/org/ezkey/admin/config/AdminApiProperties.java` (or colocated properties class).
- Bind via `@ConfigurationProperties(prefix = "ezkey.admin.auth")`.

**Filter placement in Spring Security chain:**
- Insert **before** role-based authorization (before `@EnableMethodSecurity` or security filter chain).
- Logic: if `api-key-auth-attempts-enabled == false` AND current authentication has `ROLE_API_KEY`, reject immediately.
- Do **not** break existing session-based (non-API-key) admin flows.

### Error response (RFC 9457)

**Rejection condition:**  
Request has `ROLE_API_KEY` authority AND `api-key-auth-attempts-enabled == false`

**HTTP status:** `403 Forbidden`

**Content-Type:** `application/problem+json`

**Problem type URI:**
```
https://docs.ezkey.org/problem/admin-api-key-auth-attempts-disabled
```

**Response shape:**
```json
{
  "type": "https://docs.ezkey.org/problem/admin-api-key-auth-attempts-disabled",
  "title": "API-key authentication for auth attempts is not enabled on this Admin API instance",
  "status": 403,
  "detail": "API-key authentication for auth-attempt flows is disabled. Use Integration API for M2M authentication flows. To enable this feature for minimal installations, set the property 'ezkey.admin.auth.api-key-auth-attempts-enabled' to 'true'.",
  "instance": "[request URI]"
}
```

**Link header (optional):**
```
Link: <https://docs.ezkey.org/guides/api-keys/>; rel="documentation"
```

### Integration API (no change)

- Verify existing `ROLE_API_KEY` behavior on Integration API is unchanged.
- Confirm API-key auth-attempt tests pass unchanged.

---

## Component-level boundaries

| Component | Boundary / contract touched | Change | Test evidence |
|-----------|-------------------------|--------|---|
| **Admin API** | Auth filter chain; `ROLE_API_KEY` interceptor | New filter gates M2M; rejection response shape | Auth-attempt with API key → 403 + RFC 9457 |
| **Integration API** | (None) | No change; confirms baseline | Auth-attempt with API key → 200 + normal flow |
| **Admin API config** | Property binding; Docker env mapping | New property added | Property reads default `false`, env var overrides |
| **Demo ACME** | Base URL for auth-attempt requests | Migrate from Admin API → Integration API | Demo auth flow succeeds via Integration API |
| **Java SDK** | Code examples; README | Update to Integration API for M2M | SDK examples point Integration API |
| **Functional tests** | Auth-attempt test fixtures | Migrate to Integration API; keep Admin API session tests unchanged | Tests pass; no regression in session flows |

---

## Deployment scenarios

| Scenario | Property value | Notes |
|----------|--------|-------|
| **Standard production** (Admin + Integration + Auth, incl. HA) | `false` | M2M flows via Integration API behind HAProxy; Admin API restricted |
| **Standard development** (`clean-start` baseline) | `false` | Default; developers can test minimal-install by setting `true` if needed |
| **Minimal installation** (Admin + Auth only, no Integration binary) | `true` | Documented exception; conscious trade-off for PME use case |

---

## Exit criteria — vertical slice validation

`TB-2026-05-25` is validated when all five conditions are met:

1. **Property works end to end:** Admin API starts with property `false` by default; test fixture explicitly sets `true` and verifies acceptance.
2. **Rejection response correct:** Auth-attempt with API key on Admin API (property `false`) returns `403 Forbidden` with RFC 9457 `application/problem+json` shape.
3. **Integration API untouched:** Auth-attempt with API key on Integration API continues to work; existing tests pass unchanged.
4. **SDK and Demo ACME updated:** Java SDK README and Demo ACME configuration target Integration API for auth-attempt flows; no hardcoded Admin API in M2M paths.
5. **Functional tests passing:** `ezkey-tests` suite for auth flows passes; any tests previously using Admin API base URL for M2M have been migrated to Integration API or marked as "minimal-install opt-in" scenario.

---

## Test strategy by layer

### Unit tests (Admin API)
- Property binding: verify property reads default `false` and env-var override works.
- Filter logic: verify filter gates API-key auth-attempts when property `false`; does not affect session-based admin flows.
- RFC 9457 response: verify problem `type`, `title`, `status`, `detail` fields match specification.

### Functional tests (Admin API)
- **Baseline scenario (property `false`):** API-key auth-attempt → 403 + RFC 9457.
- **Opt-in scenario (property `true`):** API-key auth-attempt → 200 + normal auth-attempt response (backward-compat signal for minimal install).
- **Session-based (unchanged):** Session-based admin flows (non-API-key) continue to work regardless of property.

### Functional tests (Integration API)
- **Regression:** API-key auth-attempt on Integration API → 200 (unchanged behavior).

### Integration tests
- **Demo ACME:** Demo auth flow succeeds when pointing Integration API; fails or must opt-in if pointing Admin API (catches misconfiguration early).
- **Java SDK examples:** Sample code compiles and runs with Integration API base URL.

### Documentation validation
- CONFIGURATION.md entry is present and correct (obligation level, Docker env mapping).
- API keys guide reflects Integration API as canonical M2M surface.
- SDK README examples use Integration API.

---

## Rollback / fallback posture

If validation fails:

- **Property not readable:** Rollback to code commit; verify property class binding before reimplementation.
- **Filter breaks session flows:** Adjust filter order or scope to exclude non-API-key flows.
- **RFC 9457 mismatch:** Align response shape with specification; iterate on detail text.
- **Demo ACME or SDK migration issues:** Identify root cause (hardcoded URL, config precedence), fix in migration change, add regression test.

No database or irreversible state changes; rollback is a clean code checkout.

---

## Key assumptions

- Existing `ROLE_API_KEY` restriction (auth attempts only) remains in place; the new property is a **second gate on top**, not a replacement.
- HA and standard deployments keep property `false`; operators explicitly choose Integration API behind HAProxy for M2M.
- Demo ACME and Java SDK have no hidden fallback logic that would silently retry Admin API if Integration API failed (no silent degradation).
- Functional test suites do not have shared state or ordering dependencies that would hide misconfigured API-key flows.

---

## Implementation plan (execution order)

1. **Admin API property + filter** — implement property binding and security filter.
2. **Admin API unit + functional tests** — validate property, filter, RFC 9457 response.
3. **Integration API regression tests** — confirm no change on Integration API.
4. **SDK + Demo ACME migration** — update base URLs and documentation.
5. **Functional test suite migration** — update auth-attempt test fixtures.
6. **Documentation updates** — CONFIGURATION.md, API keys guide, SDK README.
7. **Postman collection review** — update requests/examples for M2M to use Integration API base URL.

---

## Documentation and traceability updates (post-implementation)

Once validated:

1. Update `I-2026-0004` status → `implemented` (close backlog idea).
2. Add GitHub issue reference if applicable.
3. Update `product-docs/global/backlog/index.md` to reflect status.
4. Update `product-docs/components/admin-api/AGENTS.md` if needed (new property, new test coverage).
5. Link to release notes / changelog entry.

---

## Links

- **Vision:** `V-2026-0003`, `V-2026-0010`
- **Grill session:** `../grill-sessions/blitz-2026-05-08-1-D3-api-key-grill-me.md`
- **Features:** `F-integration-api-maturity`, `F-api-key-lifecycle`
- **Design Principles:** `#1` (pragmatism), `#4` (trust boundary), `#5` (security-by-default), `#12` (minimal attack surface)
- **Related:** `docs/API_KEYS_GUIDE.md`, `docs/API_KEYS_HOW_IT_WORKS.md`, `ezkey-admin-api/CONFIGURATION.md`

---

## Notes

- This TB is **single-pass** — scope, risk, and cross-boundary assumptions are all explicit and settled. No follow-up TBs expected.
- The filter placement must be **before** role-based authorization to avoid confusion with authorization failure vs. feature-disabled failure.
- The property name uses the `ezkey.admin.auth.*` prefix per existing Admin API configuration style.
- The problem `type` URI can be refined post-implementation if needed; the structure and content are what matter for RFC 9457 compliance.
