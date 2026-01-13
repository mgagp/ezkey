# Ezkey Functional Tests – Agent Notes

This file is UTF-8 without BOM.

## 🎯 Multi-Tenant Critical Rules

**⚠️ CRITICAL: Read [MULTI_TENANT_PHILOSOPHY.md](MULTI_TENANT_PHILOSOPHY.md) for complete context**

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
- If a list returns empty/null fields, cross-check DB to confirm creation; Admin API logs show actual tenant assignment.
- Logging is already verbose around auth steps—keep it enabled when debugging.
- Tests are independent/idempotent/opportunistic: assume a fresh docker stack from `./clean-start.sh` before runs; failed cases should be investigated directly via DB and logs without reusing state.

## Investigation Protocol
- DB checks: use MCP Postgres access (or `docker exec ezkey-postgres psql -U postgres -d ezkey_db ...`) to verify entities (tenants, integrations, enrollments, api keys) when results look empty or unexpected.
- Logs: use Docker CLI (`docker logs ezkey-admin-api`, `docker logs ezkey-auth-api`) to confirm auth-attempt flows, integration creation, and access control decisions.
- Assume docker stack from `./clean-start.sh` is a prerequisite for functional tests; relaunch if in doubt.

## Watchouts

### Multi-Tenant Pitfalls
- ⚠️ **GlobalAdmin-created resources → System Tenant**: Always use TenantAdmin tokens for tenant-specific resources in isolation tests
- ⚠️ **GET endpoints filter differently**: GlobalAdmin sees all, TenantAdmin sees only own tenant (automatic filtering via `AdminPrincipal.tenantId()`)
- ⚠️ **Cross-tenant access returns 403 or 404**: Implementation may vary between endpoints
- ⚠️ **List endpoints don't fail**: TenantAdmin accessing cross-tenant lists gets empty/filtered results, not 403

### General Testing
- Status code for pending auth is 200 (not 202), by design.
- DTO coverage gaps (e.g., missing `tenantId`) require indirect assertions.

## 📚 Documentation References

- **[MULTI_TENANT_PHILOSOPHY.md](MULTI_TENANT_PHILOSOPHY.md)** - Complete multi-tenant philosophy, matrices, and examples
- **[FUNCTIONAL_TESTING_GUIDE.md](FUNCTIONAL_TESTING_GUIDE.md)** - General functional testing guide
- **[TEST_PHILOSOPHY.md](TEST_PHILOSOPHY.md)** - Overall test philosophy and conventions
- **`docs/testing/MULTI_TENANT_TEST_PLAN.md`** - Detailed multi-tenant test plan
- **`docs/testing/TENANT_PERMISSIONS_TEST_STRATEGY.md`** - Permission matrices and test strategy

## Next Steps
- Run full P0 isolation suite after auth/device fixes.
- Validate GlobalAdmin viewing behavior (sees all tenants)
- Validate TenantAdmin isolation (sees only own tenant)
- Consider adding `tenantId` to integration responses (API change) to simplify assertions.
- Generate SDKs for Admin/Auth APIs to prevent field-name/status-code drift.

