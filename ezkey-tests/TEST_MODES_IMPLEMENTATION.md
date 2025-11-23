# Test Modes Implementation Summary

## Overview

Implementation of two Docker stack testing modes: **Production Mode** (default) and **Test Mode** (permissive). This allows developers to choose between production-like constraints or unrestricted testing based on their needs.

## Implementation Details

### Files Created

1. **`ezkey-auth-api/config/application-docker-test.properties`**
   - Disables rate limiting for Auth API
   - Extends `docker` profile
   - Usage: `SPRING_PROFILES_ACTIVE=docker,docker-test`

2. **`ezkey-admin-api/config/application-docker-test.properties`**
   - Disables rate limiting for Admin API
   - Extends `docker` profile
   - Usage: `SPRING_PROFILES_ACTIVE=docker,docker-test`

3. **`docker/TEST_MODES.md`**
   - Comprehensive documentation of both modes
   - Usage instructions
   - Troubleshooting guide

### Files Modified

1. **`docker/docker-compose.yml`**
   - Updated all services to use `${SPRING_PROFILES_ACTIVE:-docker}`
   - Supports environment variable override
   - Default remains `docker` (production mode)

2. **`docker/start.sh`**
   - Added documentation about test mode
   - Shows how to activate test mode

3. **`docker/start.ps1`**
   - Added documentation about test mode
   - Shows PowerShell syntax for test mode

4. **`docker/README.md`**
   - Added section on Spring Profiles
   - Documented both modes and their usage

5. **`ezkey-tests/README.md`**
   - Added "Docker Stack Modes" section
   - Documented both modes and their characteristics
   - Updated Quick Start section

## Usage

### Production Mode (Default)

```bash
# Linux/Mac
./docker/start.sh

# Windows PowerShell
.\docker\start.ps1
```

**Characteristics**:
- Rate limiting enabled (production values)
- Tests handle rate limits via synchronization + retry
- Production-like behavior validation

### Test Mode (Permissive)

```bash
# Linux/Mac
SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="docker,docker-test"; .\docker\start.ps1
```

**Characteristics**:
- Rate limiting disabled
- Unrestricted testing
- Synchronization + retry remain active (defense in depth)

## Key Points

### Test Resilience Mechanisms Remain Active

Both modes include built-in resilience mechanisms in test code:

1. **Synchronization** (`ReentrantLock`):
   - Prevents parallel bootstrap attempts
   - Useful even without rate limits (prevents state conflicts)

2. **Retry with Exponential Backoff**:
   - Handles rate limit errors (429) gracefully
   - In test mode, rarely triggered but remains as safety net

### Profile Inheritance

Spring Boot profiles work hierarchically:
- `application.properties` → Base
- `application-docker.properties` → Production values
- `application-docker-test.properties` → Test overrides

### Profile Persistence

- Profile is set at stack startup
- Persists for lifetime of Docker stack
- Restart required to change modes

## Benefits

1. **Flexibility**: Choose mode based on testing needs
2. **Production Validation**: Test with real constraints
3. **Development Speed**: Unrestricted testing for rapid iteration
4. **Backward Compatible**: Default behavior unchanged
5. **Simple**: Single environment variable to switch modes

## Testing Recommendations

### Use Production Mode For:
- ✅ Full test suite execution
- ✅ Production readiness validation
- ✅ Security testing
- ✅ CI/CD pipelines
- ✅ Validating test resilience

### Use Test Mode For:
- ✅ Development and debugging
- ✅ Ad-hoc API exploration
- ✅ Rapid test iteration
- ✅ Learning and experimentation

## Verification

After starting the stack, verify the active profile:

```bash
# Check Admin API logs
docker logs ezkey-admin-api | grep "The following profiles are active"

# Check Auth API logs
docker logs ezkey-auth-api | grep "The following profiles are active"
```

Expected output:
- Production Mode: `docker`
- Test Mode: `docker,docker-test`

## References

- [Docker Test Modes Documentation](../docker/TEST_MODES.md)
- [Docker README](../docker/README.md)
- [Test README](README.md)

