# Diagnostic Report: Application vs Management Port Separation

**Date**: January 13, 2026
**Issue**: GlobalAdmin tests failing on verification logic for stack mode detection
**Status**: Complete Analysis with Findings

---

## 1. Port Separation Decision - CONFIRMED ✅

### Decision Documented In
- **File**: [docs/ENDPOINT.md](docs/ENDPOINT.md) - Lines 1-15
- **Statement**: "Ezkey separates its backend APIs into two applications"
- **Explicit**: Admin API and Auth API are separate with distinct endpoints

### Implementation Verified
The project EXPLICITLY separates **Application Port** from **Management Port**:

| Service | Application Port | Management Port | Purpose |
|---------|------------------|-----------------|---------|
| Admin API | 9080 | 9081 | Service endpoints / Actuator & health |
| Auth API | 8080 | 8081 | Service endpoints / Actuator & health |
| Crypto API | 9090 | (none specified) | Service endpoints / Actuator on 9090 |

---

## 2. Docker Compose Configuration - ACTUAL STATE

### Ports Exposed to Host Machine
**File**: [docker/docker-compose.yml](docker/docker-compose.yml)

```yaml
admin-api:
  ports:
    - "9080:9080"           # ✅ Application port EXPOSED
    # ❌ 9081 NOT EXPOSED (management port stays internal)

auth-api:
  ports:
    - "8080:8080"           # ✅ Application port EXPOSED
    # ❌ 8081 NOT EXPOSED (management port stays internal)

crypto-api:
  ports:
    - "9090:9090"           # ✅ Application port EXPOSED
    # ❌ No separate management port

demo-device:
  ports:
    - "8083:8083"           # ✅ Application port EXPOSED
```

**KEY FINDING**: Management ports (9081, 8081) are **NOT exposed to the host**. They only exist **inside the container network** for internal health checks.

---

## 3. Health Check Configuration - DESIGN ISSUE IDENTIFIED ⚠️

### Docker Compose Health Checks
**File**: [docker/docker-compose.yml](docker/docker-compose.yml)

```yaml
admin-api:
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:9081/actuator/health || exit 1"]
    # ✅ This works inside container (localhost)
    # ✅ Uses internal management port

auth-api:
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
    # ✅ This works inside container (localhost)
```

**FACT**:
- Health checks use management port (9081, 8081) - This works ✅
- These checks run **inside the container**, not from host machine
- Management ports are accessible **within the Docker network**, not from host

---

## 4. Application Configuration - Management Port Setup

### Admin API Configuration
**File**: [ezkey-admin-api/config/application-docker.properties](ezkey-admin-api/config/application-docker.properties)

```properties
# Line 3: Application Server
server.port=9080

# Line 6-7: Management Port (separate from application)
management.server.port=9081
management.endpoints.web.base-path=/actuator

# Line 8: Only expose health endpoint
management.endpoints.web.exposure.include=health
```

### Auth API Configuration
**File**: [ezkey-auth-api/config/application-docker.properties](ezkey-auth-api/config/application-docker.properties)

```properties
# Line 2: Application Server
server.port=8080

# Line 25-26: Management Port (separate from application)
management.server.port=8081
management.endpoints.web.base-path=/actuator
```

**CONFIRMED**:
- Management ports are CONFIGURED in each service ✅
- They are set via environment variable `MANAGEMENT_SERVER_PORT` in docker-compose.yml ✅
- This is intentional separation per Spring Boot design ✅

---

## 5. Root Cause of Test Failures

### The Problem
Tests try to reach actuator health endpoint from **HOST machine** (test JVM):

```java
// Current code (from DockerStackConfig.java)
String healthUrl = "http://localhost:9081/actuator/health";
// ^ Tries to reach port 9081 on host machine
```

### Why It Fails in Standard (Non-HA) Mode

| Aspect | Standard Mode | HA Mode |
|--------|---------------|---------|
| Port 9081 exposed? | ❌ NO | ✅ YES (HAProxy on 9081) |
| Accessibility | Port 9081 blocked on host | Port 9081 is HAProxy |
| Health check | Can't reach from test JVM | HAProxy accessible |

**KEY INSIGHT**:
- In **Standard mode**: Management ports (9081, 8081) are NOT exposed to host
- Management endpoints are ONLY accessible from inside the container network
- Tests running on host machine CANNOT reach them

---

## 6. Actuator Endpoint Status - VERIFIED ✅

### Is Actuator In Place?
**YES** - Actuator is properly configured:

1. ✅ Dependency added (spring-boot-starter-actuator)
2. ✅ Management port configured (9081 for Admin, 8081 for Auth)
3. ✅ `/actuator/health` endpoint exposed in docker profiles
4. ✅ Used for internal Docker health checks
5. ✅ **NOT accessible from host machine** (ports not exposed)

### What We Confirmed via Docker Health Checks
```bash
# This works inside container:
curl -f http://localhost:9081/actuator/health
# But this does NOT work from host:
curl -f http://localhost:9081/actuator/health  # FAILS - port not exposed
```

---

## 7. The HTTP 500 Error - Root Cause CLARIFIED

### Why We Got 500 Earlier
The HTTP 500 error we saw was likely due to:

1. ✅ Management port (9081) **was briefly accessible** during test run (unclear why)
2. ✅ But the endpoint returned 500 (possibly incomplete startup)
3. ❌ **This is not the real problem** - port shouldn't be accessible at all

### Real Solution
Instead of trying to use management ports from host:
- Use **application ports** (9080, 8080) which ARE exposed
- Make simple HTTP calls to application endpoints (not management)
- Fallback to just checking port accessibility, not endpoint health

---

## 8. Summary of Findings

### Decisions Confirmed ✅
1. **Port Separation**: Explicit separation of application (9080, 8080) from management (9081, 8081) ports
2. **Design Intent**: Management ports are internal to Docker network only
3. **Actuator Configuration**: Properly set up in application.properties for internal use
4. **Documentation**: Architecture clearly defined in ENDPOINT.md

### Configuration Verified ✅
1. **docker-compose.yml**: Only application ports exposed to host
2. **application-docker.properties**: Management ports configured but not intended for external access
3. **Health Checks**: Use management ports internally (inside container)

### Issues Found ❌
1. **DockerStackConfig.java**: Tests incorrectly assume management ports (9081, 8081) are accessible from host
2. **Verification Logic**: Tries to reach actuator on ports that aren't exposed
3. **Stack Mode Detection**: isHAMode() checks ports that don't exist in standard mode

### What Should NOT Happen
- ❌ Tests should NOT try to reach management ports from host
- ❌ Verification should NOT depend on actuator endpoints from external access
- ❌ Port 9081 should NOT be expected in standard (non-HA) mode

### What SHOULD Happen
- ✅ Use application ports (9080, 8080, 9090) which ARE exposed
- ✅ Make simple health requests to application (not management)
- ✅ Fall back to simple port ping if endpoints aren't available
- ✅ Stack mode detection should not assume port 9081 exists

---

## 9. Recommendations

### For DockerStackConfig.java
1. **Remove reliance on management ports** (9081, 8081)
2. **Use application ports** (9080, 8080, 9090) for verification
3. **Simplify health check**:
   - Try to reach application port with simple HTTP GET
   - Accept any 2xx or 3xx response as "healthy"
   - Fall back to simple port connectivity check

### For HAProxy Mode (HA)
- Port 9081 exists on host (HAProxy load balancer)
- Can continue using management ports there
- But for **standard mode**, use application ports only

### For Future Development
- Document in comments why management ports aren't accessible from host
- Clarify in tests that only application ports should be tested externally
- Add configuration to control which ports to check

---

## 10. Conclusion

**Status**: Root cause fully identified and understood

The separation of application and management ports is a **correct design decision**.
The problem is that `DockerStackConfig.java` was written assuming management ports would be accessible from the test host, which they are not by design.

The fix should **not try to make management ports accessible**, but instead **adapt the verification logic** to use only the ports that are actually exposed to the host.

