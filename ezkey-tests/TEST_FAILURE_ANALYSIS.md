# Test Failure Analysis - AdminAuthenticationSecurityTest.testValidTokenAccess

## Problem Summary

The test `AdminAuthenticationSecurityTest.testValidTokenAccess` (Order 1) is failing with:
- **Expected**: 200 OK
- **Actual**: 401 Unauthorized

## Test Flow

1. `setUp()` is called before each test
2. `setUp()` calls `authTokenManager.getAdminToken()` which triggers `AdminBootstrapService.ensureAdminToken()`
3. Token is created via passwordless flow and saved to `.ezkey-test/admin-token.json`
4. Test `testValidTokenAccess` (Order 1) executes
5. Test calls `authTokenManager.getAdminToken()` which loads token from cache file
6. Test makes GET request to `/integrations` with `Authorization: Bearer <token>`
7. **FAILURE**: Returns 401 instead of 200

## Root Cause Analysis

### Hypothesis 1: Token Not Persisted in Database

**Theory**: The token created in `setUp()` might not be committed to the database before the test executes.

**Evidence**:
- Token is created via HTTP call to `/api/v1/admin/auth/passwordless-wait`
- This endpoint calls `AdminAuthService.generateAndPersistToken()` which calls `tokenRepository.save(token)`
- The transaction should be committed after the HTTP response

**Investigation Needed**:
- Check if token exists in database when test executes
- Verify transaction boundaries in `AdminAuthService`

### Hypothesis 2: Token Validation Issue

**Theory**: The `AdminTokenValidationService.validateToken()` method might not be finding the token.

**Evidence**:
- `AdminTokenValidationService.validateToken()` uses `@Transactional(readOnly = true)`
- It queries `tokenRepository.findByBearerTokenAndActiveTrue(token)`
- If token is not found, it returns `Optional.empty()`
- Filter then doesn't set authentication, causing 401

**Investigation Needed**:
- Check logs for "❌ Invalid token" or "❌ Token expired" messages
- Verify token is active and not expired
- Check if there's a transaction isolation issue

### Hypothesis 3: Token Format Issue

**Theory**: The token format might be incorrect or there's a mismatch between what's saved and what's queried.

**Evidence**:
- Token format: `ezkey_<UUID without hyphens>`
- Token is saved with `tokenRepository.save(token)`
- Token is queried with `findByBearerTokenAndActiveTrue(token)`

**Investigation Needed**:
- Verify token format matches exactly
- Check for any trimming or encoding issues

### Hypothesis 4: Test Ordering Issue

**Theory**: Another test might be running before `testValidTokenAccess` and invalidating the token.

**Evidence**:
- Test has `@Order(1)` annotation
- `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` is set on class
- But JUnit might execute tests in parallel or in unexpected order

**Investigation Needed**:
- Verify test execution order
- Check if any test invalidates tokens

## Debugging Steps

### Step 1: Add Logging to Test

Add logging to see what token is being used:

```java
@Test
@Order(1)
@DisplayName("Valid admin token should allow access to protected endpoints")
public void testValidTokenAccess() {
  try {
    String adminToken = authTokenManager.getAdminToken();
    log.info("Using token: {}...", adminToken.substring(0, Math.min(20, adminToken.length())));
    configureForAdminApi(dockerStackConfig);
    
    // ... rest of test
  }
}
```

### Step 2: Check Docker Logs

Check Admin API logs for token validation messages:
- `🔍 Validating bearer token: ...`
- `✅ Token validated successfully for admin: ...`
- `❌ Invalid token: ...`
- `❌ Token expired for: ...`

### Step 3: Verify Token in Database

Query database directly to verify token exists:
```sql
SELECT * FROM admin_token WHERE bearer_token = '<token>' AND active = true;
```

### Step 4: Check Token Expiration

Verify token expiration time:
```sql
SELECT bearer_token, expires_at, created_at, active 
FROM admin_token 
WHERE bearer_token = '<token>' 
ORDER BY created_at DESC;
```

## Potential Fixes

### Fix 1: Ensure Token is Committed

If transaction issue, ensure token is flushed and committed:
```java
@Transactional
public AdminToken generateAndPersistToken(EzkeyAdmin admin) {
  // ... create token
  AdminToken saved = tokenRepository.save(token);
  tokenRepository.flush(); // Force flush to database
  return saved;
}
```

### Fix 2: Add Retry Logic in Test

If timing issue, add small delay or retry:
```java
@Test
@Order(1)
public void testValidTokenAccess() {
  try {
    String adminToken = authTokenManager.getAdminToken();
    configureForAdminApi(dockerStackConfig);
    
    // Small delay to ensure token is committed
    Thread.sleep(100);
    
    Response response = // ... make request
  }
}
```

### Fix 3: Verify Token Before Test

Add verification step in test:
```java
@Test
@Order(1)
public void testValidTokenAccess() {
  try {
    String adminToken = authTokenManager.getAdminToken();
    
    // Verify token is valid before making request
    // (could call a validation endpoint or check database)
    
    configureForAdminApi(dockerStackConfig);
    // ... rest of test
  }
}
```

## Next Steps

1. **Run test with debug logging enabled** to see token validation messages
2. **Check Docker logs** for Admin API token validation messages
3. **Query database** to verify token exists and is active
4. **Verify test execution order** to ensure no interference
5. **Check if token is being invalidated** by another test or cleanup

## Related Files

- `ezkey-tests/src/test/java/org/ezkey/tests/security/admin/AdminAuthenticationSecurityTest.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminTokenAuthenticationFilter.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminTokenValidationService.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java`
- `ezkey-tests/src/test/java/org/ezkey/tests/util/AdminBootstrapService.java`

