# API Key Authentication Rate Limiting - Not Implemented

**Scope note:** this document covers a **historical config-naming collision** only
(`ezkey.admin.rate-limit.api-key.*`, dead/unread). For the **current cross-module rate-limit
model** — including how API key usage is rate-limited by **API key id** (not IP) on
Integration API — see
[`../product-docs/global/rate-limit-baseline-policy.md`](../product-docs/global/rate-limit-baseline-policy.md)
(inventory) and
[`ADR-0010`](../product-docs/global/architecture-decisions.md#adr-0010-rate-limiting-scoped-by-actor-identity-not-by-ip)
(why actor identity, not IP).

## Issue

The configuration `ezkey.admin.rate-limit.api-key.*` exists in `application.properties` but is **NOT implemented** in the codebase. This caused confusion when tests started failing with HTTP 429 errors.

## Current State

### Configuration Exists But Unused

**File**: `config/application.properties`
```properties
ezkey.admin.rate-limit.api-key.enabled=true
ezkey.admin.rate-limit.api-key.requests=5
ezkey.admin.rate-limit.api-key.window-minutes=1
ezkey.admin.rate-limit.api-key.key-strategy=integration-key
```

### No Implementation Found

- ❌ No `AdminRateLimitProperties.ApiKeyConfig` class
- ❌ No `ApiKeyAuthenticationRateLimitFilter` filter
- ❌ No service that reads `ezkey.admin.rate-limit.api-key.*` properties
- ✅ `AdminRateLimitProperties` only has `LoginConfig` (no API key config)

## What IS Rate Limited

### 1. Admin Login Rate Limiting ✅
- **Filter**: `AdminRateLimitFilter`
- **Config**: `ezkey.admin.rate-limit.login.*`
- **Applies to**: `POST /api/v1/admin/auth/login`

### 2. API-key usage rate limiting (Integration API)
- **Config**: `ezkey.api-key.rate-limit.*` on Integration API
- Admin API does not authenticate API keys

### 3. Admin Operations Rate Limiting ✅
- **Service**: `AdminOperationsRateLimitService`
- **Config**: `ezkey.admin-operations.rate-limit.*`
- **Applies to**: Admin operations (create integration, create enrollment, etc.)

## What Is NOT Rate Limited

- ❌ API key authentication itself (HTTP Basic Auth with API key)
- ❌ Requests authenticated with API keys to general endpoints (like `POST /integrations`)

## Solution

### Option 1: Disable Configuration (Current Fix) ✅

**Action**: Set `ezkey.admin.rate-limit.api-key.enabled=false`

**Pros**:
- Quick fix
- No code changes needed
- Prevents confusion

**Cons**:
- Configuration still exists (dead code)

### Option 2: Remove Configuration

**Action**: Remove `ezkey.admin.rate-limit.api-key.*` properties

**Pros**:
- Cleaner configuration
- No dead code

**Cons**:
- If implementation is added later, config needs to be re-added

### Option 3: Implement Rate Limiting (Future)

**Action**: Create `ApiKeyAuthenticationRateLimitFilter` similar to `AdminRateLimitFilter`

**Implementation needed**:
1. Add `ApiKeyConfig` to `AdminRateLimitProperties`
2. Create `ApiKeyAuthenticationRateLimitFilter` that:
   - Detects API key authentication attempts
   - Applies rate limiting based on integration key or IP
   - Returns HTTP 429 when limit exceeded
3. Add filter to `SecurityConfig` filter chain
4. Add NOHOP configuration for tests

## Test Impact

Tests that use API keys (like `testApiKeyCannotCreateIntegration`) were failing with HTTP 429 because:
- The configuration suggested rate limiting was enabled
- But no implementation existed to actually enforce it
- However, some other rate limiter might have been triggered

**Fix**: Disabled the unused configuration to prevent confusion.

## Related Files

- `ezkey-admin-api/config/application.properties` - Configuration (now disabled)
- `ezkey-admin-api/src/main/java/org/ezkey/admin/config/AdminRateLimitProperties.java` - Properties class (no API key config)
- `ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminRateLimitFilter.java` - Login rate limiting filter

