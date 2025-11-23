# Admin Token Management - Architecture and Philosophy

## Overview

This document explains the architecture and philosophy behind admin token management in Ezkey tests, focusing on independence, idempotence, and efficiency.

## Core Principles

### 1. Independence

Tests should be **independent** - they can be run in any order without dependencies on other tests:

- ✅ Each test can obtain its own admin token
- ✅ Tests don't rely on execution order
- ✅ Tests can be run individually or in parallel
- ✅ No shared mutable state between tests

### 2. Idempotence

Tests should be **idempotent** - running them multiple times produces the same result:

- ✅ Same test can be executed multiple times
- ✅ No side effects from repeated execution
- ✅ State is restored after destructive operations (e.g., logout)
- ✅ Cached resources are reused efficiently

### 3. Efficiency

Tests should be **efficient** - minimize unnecessary operations:

- ✅ Reuse cached tokens when available
- ✅ Reuse device credentials for token creation
- ✅ Only perform bootstrap when necessary
- ✅ Fast execution for repeated test runs

## Architecture

### Three-Tier Token Strategy

The `AdminBootstrapService.ensureAdminToken()` method implements a three-tier strategy:

```
┌─────────────────────────────────────────────────────────┐
│              THREE-TIER TOKEN STRATEGY                   │
└─────────────────────────────────────────────────────────┘

Tier 1: Cached Token (Fastest)
  ↓ Check: admin-token.json exists?
  ↓ YES → Load and return token
  ↓ NO → Continue to Tier 2

Tier 2: Reuse Device Credentials (Fast)
  ↓ Check: device-credentials.json exists?
  ↓ YES → Load device credentials
  ↓       → Create token (login → respond → wait)
  ↓       → Save token to admin-token.json
  ↓       → Return token
  ↓ NO → Continue to Tier 3

Tier 3: Initial Bootstrap (Slower, One-Time)
  ↓ Extract bootstrap credentials
  ↓ Generate device key pair
  ↓ Bind device to enrollment
  ↓ Verify enrollment
  ↓ Save device credentials to device-credentials.json
  ↓ Create token (login → respond → wait)
  ↓ Save token to admin-token.json
  ↓ Return token
```

### File Structure

```
.ezkey-test/
├── bootstrap-credentials.json    # Bootstrap credentials (created once)
├── device-credentials.json        # Device credentials (created after initial bootstrap)
└── admin-token.json               # Admin token (created/updated on each token creation)
```

### File Lifecycle

#### bootstrap-credentials.json
- **Created**: Once, when extracting credentials from Docker logs
- **Updated**: Never (read-only after creation)
- **Used**: For all bootstrap and token operations
- **Lifetime**: Persistent across test runs

#### device-credentials.json
- **Created**: After initial bootstrap enrollment (bind + verify)
- **Updated**: Never (read-only after creation)
- **Used**: For creating new tokens without redoing bootstrap
- **Lifetime**: Persistent across test runs (until Docker reset)

#### admin-token.json
- **Created**: After successful token creation
- **Updated**: On each token creation (overwrites previous token)
- **Used**: For authenticated API requests
- **Lifetime**: Valid until invalidated (logout, expiration, etc.)

## Test Organization

### Building Block Tests

#### AdminTokenCreationTest
- **Purpose**: Core building block for creating admin tokens
- **Characteristics**:
  - Independent: Can be run in any order
  - Idempotent: Can be run multiple times
  - Efficient: Reuses cache and device credentials
- **Usage**: Use as dependency for other tests that need admin token

#### AdminInitialBootstrapTest
- **Purpose**: Force initial bootstrap enrollment
- **Characteristics**:
  - Deletes existing device credentials to force fresh bootstrap
  - Useful for testing bootstrap flow in isolation
  - Useful after Docker reset
- **Usage**: Optional, for explicit bootstrap testing

#### BootstrapCredentialsExtractionTest
- **Purpose**: Extract bootstrap credentials from Docker logs
- **Characteristics**:
  - Utility test for credential extraction
  - Creates bootstrap-credentials.json
- **Usage**: Run once after Docker stack startup

### Security Tests

#### AdminAuthenticationSecurityTest
- **Purpose**: Test admin authentication security features
- **Characteristics**:
  - Tests unauthorized access, invalid tokens, logout
  - Automatically recreates token after logout (idempotent)
  - Uses `AuthTokenManager` for token management
- **Usage**: Validates authentication security

## Implementation Details

### AdminBootstrapService

The `AdminBootstrapService` class orchestrates the bootstrap and token creation flow:

```java
public class AdminBootstrapService {
  // Three-tier strategy
  public String ensureAdminToken() {
    // Tier 1: Check cached token
    String cachedToken = loadTokenFromFile();
    if (cachedToken != null) return cachedToken;
    
    // Tier 2: Check device credentials
    DeviceCredentials deviceCredentials = loadDeviceCredentials();
    if (deviceCredentials != null) {
      return createAdminToken(deviceCredentials);
    }
    
    // Tier 3: Perform initial bootstrap
    deviceCredentials = performInitialBootstrap();
    return createAdminToken(deviceCredentials);
  }
  
  // Phase 1: Initial Bootstrap (Steps 1-4)
  private DeviceCredentials performInitialBootstrap() {
    // Extract credentials, generate keys, bind, verify
    // Save device credentials
  }
  
  // Phase 2: Token Creation (Steps 5-9)
  private String createAdminToken(DeviceCredentials deviceCredentials) {
    // Login, respond, wait, save token
  }
}
```

### AuthTokenManager

The `AuthTokenManager` class provides a higher-level interface for token management:

```java
public class AuthTokenManager {
  // Priority order:
  // 1. Environment variable (EZKEY_ADMIN_TOKEN)
  // 2. Cached token from file
  // 3. Automatic bootstrap via AdminBootstrapService
  public String getAdminToken() {
    // ... priority logic ...
  }
}
```

## Best Practices

### For Test Writers

1. **Use `AuthTokenManager`** for token access:
   ```java
   String adminToken = authTokenManager.getAdminToken();
   ```

2. **Don't assume token exists**: Always use `AuthTokenManager` or `AdminBootstrapService`

3. **Handle token invalidation**: After logout, clear token and let it be recreated:
   ```java
   authTokenManager.setAdminToken(null);
   // Next call to getAdminToken() will recreate token
   ```

4. **Test independence**: Don't rely on other tests to create tokens

### For Test Execution

1. **Run `BootstrapCredentialsExtractionTest`** once after Docker startup
2. **Run `AdminTokenCreationTest`** to ensure token is available
3. **Run other tests** - they will automatically use cached token or create new one

### For Maintenance

1. **After Docker reset**: Run `AdminInitialBootstrapTest` to force fresh bootstrap
2. **Clear cache files**: Delete `.ezkey-test/*.json` to force fresh start
3. **Check file permissions**: Ensure test process can write to `.ezkey-test/` directory

## Troubleshooting

### Token Not Created

**Problem**: `AdminTokenCreationTest` fails

**Solutions**:
1. Check Docker stack is running
2. Run `BootstrapCredentialsExtractionTest` first
3. Check `.ezkey-test/bootstrap-credentials.json` exists
4. Review logs for specific error

### Token Invalidated After Logout

**Problem**: Tests fail after logout test

**Solution**: This is expected. The logout test automatically recreates the token for idempotence.

### Device Credentials Missing

**Problem**: Token creation is slow (performs full bootstrap)

**Solution**: Run `AdminInitialBootstrapTest` once to create device credentials, then subsequent token creations will be fast.

## Future Enhancements

- [ ] Token expiration handling
- [ ] Automatic token refresh
- [ ] Multiple device support
- [ ] Token rotation testing
- [ ] Performance benchmarking

## References

- [Bootstrap Flow Analysis](BOOTSTRAP_FLOW_ANALYSIS.md)
- [README](README.md)
- [AdminBootstrapService](../src/test/java/org/ezkey/tests/util/AdminBootstrapService.java)
- [AuthTokenManager](../src/test/java/org/ezkey/tests/util/AuthTokenManager.java)

