# Docker Stack Test Modes

## Overview

The Ezkey Docker stack supports two testing modes, allowing you to choose between production-like constraints or unrestricted testing based on your needs.

## Modes

### 1. Production Mode (Default)

**Profile**: `docker`

**Activation**: Default (no configuration needed)
```bash
./docker/start.sh
```

**Characteristics**:
- ✅ Rate limiting enabled with production values
- ✅ Tests must handle rate limits (synchronization + retry mechanisms)
- ✅ Validates production-like behavior
- ✅ Tests are resilient and production-ready

**Rate Limit Configuration**:
- **Bind**: 3 requests / 5 minutes per IP (very restrictive for enumeration protection)
- **Verify**: 5 requests / 5 minutes per IP (restrictive for security)
- **Pending**: 10 requests / 1 minute per enrollment (permissive for normal usage)

**Use Cases**:
- Full test suite execution
- Production readiness validation
- Security testing with real constraints
- CI/CD pipeline testing
- Validating test resilience

---

### 2. Local Docker Diagnostics Mode (Actuator)

**Profile**: `docker-dev`

**Activation**: Set `SPRING_PROFILES_ACTIVE` environment variable

```bash
SPRING_PROFILES_ACTIVE=docker-dev ./docker/start.sh
```

**Characteristics**:
- ✅ Designed for local Docker development only
- ✅ Exposes a richer Actuator surface for diagnostics (health/info/metrics/threaddump)
- ✅ Useful for memory and heap analysis via `/actuator/metrics`
- ✅ Uses a dedicated management port: `8085` (not published by default)
- ⚠️ Not intended to be exposed publicly in staging/prod

---

### 2. Test Mode (Permissive)

**Profile**: `docker,docker-test`

**Activation**: Set `SPRING_PROFILES_ACTIVE` environment variable
```bash
SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh
```

**Characteristics**:
- ✅ Rate limiting disabled or very permissive
- ✅ Allows unrestricted testing in any order and frequency
- ✅ No rate limit constraints
- ✅ Synchronization and retry mechanisms remain active (defense in depth)
- ✅ **Audit-chain peripheral heartbeat supervision stays enabled** (`ezkey.audit.chain.heartbeat.enabled=true`, `required=true`) — Auth API and Integration API still fail-close MFA gates when checkpoints stall; only operational churn mitigations (HTTP/API-key rate limits) are relaxed.

**Rate Limit Configuration**:
- All rate limits disabled (`ezkey.rate-limit.enabled=false`)

**Use Cases**:
- Development and debugging
- Ad-hoc testing
- Rapid iteration
- Testing without rate limit concerns
- Exploring API behavior

---

### 3. Eval runtime profile (opt-in product key)

**Product key**: `--runtime=eval` / `EZKEY_RUNTIME_PROFILE=eval`  
**Spring mechanism**: profile `docker-eval` (last) → `application-docker-eval.properties`

**Default**: integrity (this mode is **not** the clean-start default).

**Claim boundary**: MFA crypto on; audit-integrity **monitoring** off. Not tamper-evident.

**Hard coupling**: checkpoints OFF and heartbeat OFF together on Admin, Auth, and Integration.

See [`docker/README.md`](README.md) § Runtime profiles and `./docker/verify-runtime-profile.sh`.

---

## How It Works

### Profile Inheritance

Spring Boot profiles work hierarchically:
1. `application.properties` - Base configuration
2. `application-docker.properties` - Docker-specific (production values)
3. `application-docker-test.properties` - Test mode overrides (disables rate limits)
4. `application-docker-eval.properties` - Eval runtime overrides (when product key selects eval)

When `docker-test` profile is active, it **extends** `docker` profile and overrides **rate limiting** settings only — heartbeat thresholds inherit docker defaults unless explicitly overridden.

When `docker-eval` is active (appended last by `docker/runtime-profile.sh`), it overrides the eval feature matrix on top of docker / docker-test.

### Configuration Files

**Production Mode** (`application-docker.properties`):
```properties
ezkey.rate-limit.enabled=true
ezkey.rate-limit.bind.requests=3
ezkey.rate-limit.bind.window-minutes=5
```

**Test Mode** (`application-docker-test.properties`):
```properties
ezkey.rate-limit.enabled=false
```

### Test Resilience Mechanisms

Regardless of the mode, tests include built-in resilience:

1. **Synchronization** (`ReentrantLock`):
   - Prevents parallel bootstrap attempts
   - Ensures only one thread performs bootstrap at a time
   - Useful even without rate limits (prevents state conflicts)

2. **Retry with Exponential Backoff**:
   - Handles rate limit errors (429) gracefully
   - Exponential backoff: 1s, 2s, 4s, 8s, 16s
   - Maximum 5 retry attempts
   - In test mode, retry is rarely triggered but remains as safety net

---

## Switching Between Modes

### Important Notes

1. **Profile is Set at Startup**: The profile is determined when the Docker stack starts and persists for the lifetime of the stack.

2. **Restart Required**: To change modes, you must restart the Docker stack:
   ```bash
   # Stop current stack
   ./docker/manage.sh stop
   
   # Start with new mode
   SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh
   ```

3. **Database State**: Changing modes does not affect database state. Bootstrap credentials and device credentials persist across mode changes.

---

## Recommendations

### When to Use Production Mode

- ✅ Running full test suite
- ✅ Validating production readiness
- ✅ Security testing
- ✅ CI/CD pipelines
- ✅ Testing rate limit handling

### When to Use Test Mode

- ✅ Development and debugging
- ✅ Ad-hoc API exploration
- ✅ Rapid test iteration
- ✅ Testing without rate-limit constraints (heartbeat gates unchanged)
- ✅ Learning and experimentation

---

## Verification

### Check Active Profile

```bash
# Check Admin API logs
docker logs ezkey-admin-api | grep "The following profiles are active"

# Check Auth API logs
docker logs ezkey-auth-api | grep "The following profiles are active"
```

### Verify Rate Limiting Status

```bash
# Check rate limit configuration in logs
docker logs ezkey-auth-api | grep "rate-limit"
```

---

## Troubleshooting

### Rate Limits Still Active in Test Mode

**Problem**: Rate limits are still being enforced despite using test mode.

**Solutions**:
1. Verify profile is set correctly: `SPRING_PROFILES_ACTIVE=docker,docker-test`
2. Restart the stack (profile is set at startup)
3. Check logs for active profiles
4. Verify `application-docker-test.properties` files exist

### Tests Fail in Production Mode

**Problem**: Tests fail with rate limit errors (429) in production mode.

**Solutions**:
1. This is expected - tests should handle rate limits
2. Verify synchronization and retry mechanisms are working
3. Consider running tests sequentially if parallel execution causes issues
4. Use test mode for development, production mode for validation

---

## Files Modified

- `docker/docker-compose.yml` - Supports `SPRING_PROFILES_ACTIVE` environment variable
- `ezkey-auth-api/config/application-docker-test.properties` - Test mode configuration
- `ezkey-admin-api/config/application-docker-test.properties` - Test mode configuration
- `docker/start.sh` - Stack entrypoint (supports profile env vars)
- `docker/README.md` - Profile documentation

---

## References

- [Spring Boot Profiles Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Rate Limiting Configuration](../ezkey-auth-api/config/application-docker.properties)
- [Test Resilience Mechanisms](../ezkey-tests/src/test/java/org/ezkey/tests/util/AdminBootstrapService.java)

