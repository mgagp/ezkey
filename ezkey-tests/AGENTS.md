# Ezkey Functional Tests – Agent Notes

This file is UTF-8 without BOM.

## Module dependencies (standalone HTTP client)

`ezkey-tests` is **not** a Spring Boot application. Tests hit live APIs over HTTP (RestAssured)
against the Docker stack; they do not load admin-api or auth-api Spring contexts.

| Dependency | Role |
|------------|------|
| `junit-jupiter` | Test execution |
| `rest-assured` (+ json-path, xml-path) | HTTP client and response assertions |
| `assertj-core` | Fluent assertions |
| `tools.jackson.core:jackson-databind` | Helper JSON I/O only (`.ezkey-test/` cache files, bootstrap) |
| `logback-classic` | Test-scoped logging for helpers |

Rest Assured 6 pins Jackson 3 for request/response mapping via {@code RestAssuredTestConfig}.
Avoid {@code jsonPath().getObject(..., Class)} — that path still expects Jackson 2 in Rest Assured
6.0.0; use {@code jsonPath().get(...)} or {@link org.ezkey.tests.util.RestAssuredTestConfig#readNumber}.

Do **not** add `spring-boot-starter-test` or API module dependencies — that would pull Spring/Mockito
and couple tests to internal DTOs. Most test classes use RestAssured `jsonPath()` / `Map` bodies and
never touch `ObjectMapper` directly.

## 🎯 Multi-Tenant Critical Rules

**⚠️ CRITICAL: Read [reference/MULTI_TENANT.md](reference/MULTI_TENANT.md) for complete context**

### The "No Impersonation" Rule
- ❌ **GlobalAdmin CANNOT create resources for other tenants**
- ✅ **GlobalAdmin VIEWS all resources** (cross-tenant read access)
- ✅ **GlobalAdmin CREATES in System Tenant ONLY** (tenantId: 1)
- ✅ **TenantAdmin CREATES in own tenant** (automatic assignment)

### Quick Reference Matrix

| Operation | GlobalAdmin | TenantAdmin A | TenantAdmin B |
|-----------|-------------|---------------|---------------|
| **GET /integrations** | ✅ All tenants | ✅ Tenant A only | ✅ Tenant B only |
| **POST /integrations** | ✅ System Tenant (ID:1) | ✅ Tenant A | ✅ Tenant B |
| **GET /api-keys** | ✅ All tenants | ✅ Tenant A only | ✅ Tenant B only |
| **Cross-tenant GET** | ✅ Allowed | ❌ 403/404 | ❌ 403/404 |

**Why This Matters:**
- Tests using GlobalAdmin tokens will create integrations in System Tenant (ID: 1)
- For tenant isolation tests, **always use TenantAdmin tokens** to create resources
- GlobalAdmin can VIEW all resources but cannot CREATE for specific tenants

## Core Workflows
- Passwordless auth (TenantAdmin): use two-step mode (`challengeRequested=true`), status code is 200 with `authAttemptId` + `challengeCode`; then `/auth-attempts/pending`, `/auth-attempts/respond`, `/admin/auth/passwordless-wait`.
- Integrations creation for tenant isolation: create integrations with **TenantAdmin tokens**, not GlobalAdmin, otherwise they land in System Tenant (ID 1) regardless of `tenantId` in request.
- Integration listings: `IntegrationResponseDto` currently omits `tenantId`; isolation is verified by presence/absence of expected integration IDs (not by tenantId field).

## Recent Fixes (Jan 12, 2026)
- Removed 120s hang: switched to two-call auth and expected HTTP 200 pending.
- Tenant isolation test: adjusted to create integrations with TenantAdmin tokens and assert A-sees-A, not B.
- Multi-tenant philosophy documented: "No Impersonation" rule clarified (GlobalAdmin views all, creates in System Tenant only)

## Testing Tips
- Prefer per-tenant device simulation via `TenantAdminTestHelper` (full enrollment + token reuse tiers).
- **Admin API is the default** (`configureForAdminApi`, port 9080, Bearer). For Integration API
  auth-attempt create/wait, use `RestAssuredTestConfig.configureForIntegrationApi` and
  `EZKEY_INTEGRATION_API_URL` (default `http://localhost:7080`). Auth is HTTP Basic
  (`integrationKey:secretKey`), not an admin Bearer. See `guides/WRITING_TESTS.md`.
- If a list returns empty/null fields, cross-check DB to confirm creation; Admin API logs show actual tenant assignment.
- Logging is already verbose around auth steps—keep it enabled when debugging.
- Tests are independent/idempotent/opportunistic: assume a fresh docker stack from `./clean-start.sh` before runs; failed cases should be investigated directly via DB and logs without reusing state.

### Real-device Maestro campaigns

Tag `mobile-real-device` / profile `mobile-real-device-tests` is excluded from default Surefire
(same pattern as elective and operational-churn). Canonical orchestrator:

`./ezkey-tests/scripts/run-mobile-real-device.sh`

JUnit building blocks live in `org.ezkey.tests.mobile`. On failure, read session `rca.md` before
full Maestro logs. Design: `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`.
The runner keeps the device screen on for the session (`--no-stay-awake` to skip) so a 30s
display timeout does not lock the Pixel between JUnit and Maestro.

### Test Values
- **Data accumulation is a feature**: tests intentionally create new tenants/integrations/admins over time (production-like dataset). Avoid cleanup unless a test *must* reset state for correctness.
- **Independence**: each test must pass regardless of existing data (use unique identifiers, avoid relying on ordering or “first page” defaults).
- **Idempotence**: reruns should behave deterministically; prefer explicit pagination params (e.g., `size`, stable `sort`) and API filters that scope to the test’s unique suffix.
- **Opportunistic verification**: when API responses are ambiguous (pagination/DTO gaps), use DB/logs for diagnosis—not to bypass the behavior being validated.

## Investigation Protocol
- DB checks: use MCP Postgres access (or `docker exec ezkey-postgres psql -U postgres -d ezkey_db ...`) to verify entities (tenants, integrations, enrollments, api keys) when results look empty or unexpected.
- Logs: use Docker CLI (`docker logs ezkey-admin-api`, `docker logs ezkey-auth-api`) to confirm auth-attempt flows, integration creation, and access control decisions.
- Assume docker stack from `./clean-start.sh` is a prerequisite for functional tests; relaunch if in doubt.

### Debugging approach — lesson learned (Mar 2026)
When tests that were previously stable start failing, **first ask: what changed recently?** Do not jump to technical hypotheses (e.g. time skew, environment differences). If the full test suite was green before, the cause is almost certainly in recent code changes. Work from that hypothesis: inspect `git log`, identify the last known-good state, then examine startup logs and errors for the failing path. Challenging "was it stable recently?" saves time versus pursuing speculative technical fixes.

## Watchouts

### Multi-Tenant Pitfalls
- ⚠️ **GlobalAdmin-created resources → System Tenant**: Always use TenantAdmin tokens for tenant-specific resources in isolation tests
- ⚠️ **GET endpoints filter differently**: GlobalAdmin sees all, TenantAdmin sees only own tenant (automatic filtering via `AdminPrincipal.tenantId()`)
- ⚠️ **Cross-tenant access returns 403 or 404**: Implementation may vary between endpoints
- ⚠️ **Sub-resources inherit parent isolation**: `GET /{id}` coverage does not include
  `GET /{id}/qrcode` (or other `/{id}/…` that re-expose the same object). Add a deny +
  own-tenant 200 in `TenantCrossIsolationSecurityTest` when you add such a route.
- ⚠️ **List endpoints don't fail**: TenantAdmin accessing cross-tenant lists gets empty/filtered results, not 403

### General Testing
- Status code for pending auth is 200 (not 202), by design.
- DTO coverage gaps (e.g., missing `tenantId`) require indirect assertions.

## 📚 Documentation References

### Guides (Practical How-To)
- **[guides/WRITING_TESTS.md](guides/WRITING_TESTS.md)** - Complete guide on writing functional tests, including authentication, pagination, filtering, and best practices
- **[guides/TEST_PHILOSOPHY.md](guides/TEST_PHILOSOPHY.md)** - Overall test philosophy and opportunistic E2E testing strategy

### Reference (Technical Details)
- **[reference/MULTI_TENANT.md](reference/MULTI_TENANT.md)** - Complete multi-tenant philosophy, matrices, and examples
- **[reference/AUTHENTICATION.md](reference/AUTHENTICATION.md)** - Complete authentication reference: token acquisition, cache files, building block tests
- **[reference/BOOTSTRAP_FLOW.md](reference/BOOTSTRAP_FLOW.md)** - Deep-dive reference: full admin bootstrap + token creation flow and RestAssured configuration points

### External Documentation
- **`docs/testing/MULTI_TENANT_TEST_PLAN.md`** - Detailed multi-tenant test plan
- **`docs/testing/TENANT_PERMISSIONS_TEST_STRATEGY.md`** - Permission matrices and test strategy

## Next Steps
- Run full P0 isolation suite after auth/device fixes.
- Validate GlobalAdmin viewing behavior (sees all tenants)
- Validate TenantAdmin isolation (sees only own tenant)
- Consider adding `tenantId` to integration responses (API change) to simplify assertions.
- Generate SDKs for Admin/Auth APIs to prevent field-name/status-code drift.

