# Authentication Reference - Ezkey Functional Tests

## Overview

This document provides a complete reference for authentication mechanisms in Ezkey functional tests, including token acquisition strategies, cache files, and building block tests.

## Authentication Mechanisms

### Single Login Endpoint for All Admins

Ezkey uses **one single login endpoint** for all admin types:

```
POST /admin/auth/login
```

The system automatically determines the admin type from the database and generates tokens with appropriate scope information via `AdminPrincipal`:

**Currently Implemented Admin Types:**
- `GLOBAL_ADMIN`: System-wide administrator with full access
- `TENANT_ADMIN`: Tenant-specific administrator with limited scope

**Future (Not Yet Implemented):**
- `INTEGRATION_ADMIN`: Integration-specific administrator (defined in schema but not activated in Phase 1)

**AdminPrincipal Fields:**
- `adminType`: GLOBAL_ADMIN or TENANT_ADMIN (currently)
- `tenantId`: null for GlobalAdmin, tenant ID for TenantAdmin
- `integrationId`: null (reserved for future INTEGRATION_ADMIN)

## GlobalAdmin Token Acquisition

### Preferred Entrypoint

Use `AuthTokenManager.getAdminToken()` in tests.

### Three-Tier Strategy

`AuthTokenManager.getAdminToken()` uses this priority order:

1. **Tier 1**: Environment variable `EZKEY_ADMIN_TOKEN` (highest priority)
2. **Tier 2**: Cached token from `.ezkey-test/admin-token.json` (**validated** before reuse; invalid cache is deleted)
3. **Tier 3**: Automatic bootstrap via `AdminBootstrapService.ensureAdminToken()` (when bootstrap dependencies are set)

### AdminBootstrapService Three-Tier Strategy

Separately, `AdminBootstrapService.ensureAdminToken()` uses a three-tier strategy:

1. **Tier 1**: Cached token (`.ezkey-test/admin-token.json`, validated)
2. **Tier 2**: Reuse device credentials (`.ezkey-test/device-credentials.json`) to mint a new token
3. **Tier 3**: Initial bootstrap (extract credentials → enroll device → verify) then mint a token

### Example Usage

```java
@BeforeEach
public void setUp() {
  super.setUp();

  try {
    String adminToken = authTokenManager.getAdminToken();
    // Use adminToken for API calls
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
      false,
      "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable."
    );
  }
}
```

## TenantAdmin Token Acquisition

### Device Simulation Flow

Use `TenantAdminTestHelper.createAndLoginTenantAdmin()` which performs complete device simulation:

1. Creates TenantAdmin record via Admin API
2. Retrieves onboarding credentials (enrollmentId, enrollmentProofToken, enrollmentChallenge) via `GET /api/v1/admins/{id}/onboarding`
3. Generates device key pair via Crypto API
4. Binds device to enrollment via Auth API
5. Verifies enrollment with signature
6. Performs login and returns token

**Note:** The onboarding endpoint returns `recoveryCodes: null` by design (recovery codes cannot be retrieved in plain text after provisioning).

### Three-Tier Strategy for TenantAdmin

The helper also follows a 3-tier strategy:
1. **Tier 1**: Cached token from `.ezkey-test/tenant-admin-{tenantId}-token.json` (validated)
2. **Tier 2**: Cached device credentials from `.ezkey-test/tenant-admin-{tenantId}-device-credentials.json`
3. **Tier 3**: Full device simulation and enrollment

### Example Usage

```java
@BeforeEach
public void setUp() {
  super.setUp();

  try {
    String globalAdminToken = authTokenManager.getAdminToken();

    TenantAdminTestHelper tenantAdminTestHelper =
      new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);

    Integer tenantId = testDataFactory.createTenant("Test Tenant", globalAdminToken);

    String tenantAdminToken = tenantAdminTestHelper.createAndLoginTenantAdmin(
      "tenant.admin",
      tenantId,
      globalAdminToken
    );

    // Use tenantAdminToken for API calls
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
      false,
      "Test setup failed: " + e.getMessage()
    );
  }
}
```

## Cache Files

### GlobalAdmin Cache Files

- **`.ezkey-test/bootstrap-credentials.json`**: Extracted from Docker logs; reused across runs
  - Contains: enrollmentId, enrollmentProofToken, enrollmentChallengeCode, recoveryCodes
  - Created by: `BootstrapCredentialsExtractor.extractCredentials()`
  - Reused: For all bootstrap and token creation operations

- **`.ezkey-test/device-credentials.json`**: Created after successful bootstrap; enables fast token creation
  - Contains: enrollmentId, privateKey, publicKey, keySize
  - Created by: `AdminBootstrapService.saveDeviceCredentials()`
  - Reused: For creating new tokens without redoing bootstrap (Tier 2)
  - Created once: After initial bootstrap enrollment completes

- **`.ezkey-test/admin-token.json`**: The cached bearer token; may be invalidated by logout/rotation and will be regenerated
  - Contains: token (admin bearer token)
  - Created by: `AdminBootstrapService.saveTokenToFile()`
  - Reused: For subsequent test runs (Tier 1 - fastest path)
  - Recreated: Automatically if invalidated (e.g., after logout)

### TenantAdmin Cache Files

- **`.ezkey-test/tenant-admin-{tenantId}-device-credentials.json`**: TenantAdmin device keys per tenant
- **`.ezkey-test/tenant-admin-{tenantId}-token.json`**: TenantAdmin bearer token per tenant

These files enable efficient test execution by avoiding repetitive bootstrap and authentication flows.

## Building Block Tests

These tests are mainly helpful to **pre-warm caches** or **debug token/bootstrapping issues**:

### AdminTokenCreationTest

Obtains a token (reusing cache/credentials when possible) and validates it against a protected endpoint.

**Use cases:**
- Pre-warm token cache before running other tests
- Validate token acquisition flow
- Debug token validation issues

### AdminInitialBootstrapTest

Forces a fresh bootstrap (useful after a Docker reset, or to validate bootstrap behavior).

**Use cases:**
- After Docker stack reset (`docker-compose down -v`)
- To validate bootstrap flow in isolation
- To force regeneration of device credentials

### BootstrapCredentialsExtractionTest

Extracts bootstrap credentials from Docker logs (useful when diagnosing extraction issues).

**Use cases:**
- Diagnose credential extraction problems
- Validate Docker log parsing
- Pre-warm bootstrap credentials cache

## Practical Guidance

### Do Not Rely on Test Order

Any test should be able to call `getAdminToken()` and proceed. Tests are designed to be independent and idempotent.

### After a Docker Reset

Delete `.ezkey-test/*.json` (or run `AdminInitialBootstrapTest`) to rebuild credentials cleanly.

**Why:** Docker reset clears the database, but cache files may still exist locally, creating a state mismatch.

### After a Logout Test

Expect the cached token to become invalid; the framework regenerates it on the next `getAdminToken()` call.

**How it works:**
1. Logout invalidates the token in the database
2. Next `getAdminToken()` call validates the cached token (Tier 1)
3. Validation fails → framework automatically tries Tier 2 (device credentials)
4. If Tier 2 fails → framework tries Tier 3 (full bootstrap)

### Token Validation

The framework validates cached tokens before reuse:

- **Tier 1 validation**: Checks if token is still valid by making a test API call
- **Invalid token handling**: Deletes invalid cache and falls back to Tier 2 or Tier 3

## Complete Bootstrap Flow

For the complete step-by-step bootstrap/token flow including all API calls, cache files, and RestAssured reconfiguration points, see `reference/BOOTSTRAP_FLOW.md`.

## Code References

- `src/test/java/org/ezkey/tests/util/AuthTokenManager.java` - Token management utility
- `src/test/java/org/ezkey/tests/util/AdminBootstrapService.java` - Bootstrap and token creation service
- `src/test/java/org/ezkey/tests/util/TenantAdminTestHelper.java` - TenantAdmin device simulation helper
- `src/test/java/org/ezkey/tests/util/BootstrapCredentialsExtractor.java` - Credentials extraction utility

## Troubleshooting

### Token Not Available

**Error**: `Admin token not available`

**Solution**:
1. Set environment variable: `export EZKEY_ADMIN_TOKEN=your-token-here`
2. Or run the bootstrap service to generate tokens automatically
3. Or run `AdminTokenCreationTest` to pre-warm the cache

### Token Invalid After Logout

**Problem**: Cached token returns 401 after logout test

**Solution**: The framework automatically detects invalid tokens (Tier 1 validation) and regenerates them using device credentials (Tier 2) or full bootstrap (Tier 3). No manual intervention needed.

### Device Simulation Fails

**Error**: Failed to create TenantAdmin token

**Solution**: Verify that:
1. Docker stack is healthy (`dockerStackConfig.verifyServicesHealthy()`)
2. Crypto API is accessible
3. Auth API is accessible
4. Admin API is accessible

Check container logs for errors.

### Bootstrap Credentials Not Found

**Problem**: `BootstrapCredentialsExtractor` cannot find credentials in logs

**Solution**:
1. Ensure Docker stack has completed startup
2. Check Admin API logs: `docker logs ezkey-admin-api | grep "GLOBAL ADMIN"`
3. Verify bootstrap completed: Look for "✅ Global Admin Enrollment created"
4. Run extraction test manually: `mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest`

## Related Documentation

- **[guides/WRITING_TESTS.md](../guides/WRITING_TESTS.md)** - Guide on writing functional tests, including authentication patterns
- **[reference/BOOTSTRAP_FLOW.md](BOOTSTRAP_FLOW.md)** - Detailed step-by-step bootstrap and token creation flow
- **[guides/TEST_PHILOSOPHY.md](../guides/TEST_PHILOSOPHY.md)** - Test philosophy and opportunistic resource usage
