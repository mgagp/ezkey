# Rate Limiting Implementation - Ezkey Admin API

## Overview

The Ezkey Admin API implements comprehensive rate limiting using the Bucket4j library to prevent abuse and ensure fair usage across different types of operations. This document describes the implementation, configuration, and monitoring of rate limiting features.

## Architecture

### Rate Limiting Systems

The admin API implements **three distinct rate limiting systems**:

1. **Admin Login Rate Limiting** (IP-based) - `AdminRateLimitFilter`
2. **API Key Operations Rate Limiting** (key-based) - `RateLimitService`  
3. **Admin Operations Rate Limiting** (admin-based) - `AdminOperationsRateLimitService`

### Technology Stack

- **Bucket4j**: Token bucket algorithm for efficient rate limiting
- **Caffeine**: High-performance in-memory cache for bucket storage
- **Spring Boot Actuator**: Metrics exposure for monitoring
- **Micrometer**: Metrics collection and aggregation

## Configuration

### 1. Admin Login Rate Limiting

**Namespace**: `ezkey.admin.rate-limit.*`

```properties
# Admin login rate limiting (IP-based)
ezkey.admin.rate-limit.enabled=true
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=5
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30
```

**Protected Endpoints**:
- `POST /api/v1/admin/auth/login`
- `POST /api/v1/admin/auth/recover`

**Features**:
- IP-based rate limiting with proxy header support
- Automatic IP blocking after repeated failures
- Configurable block duration and failure thresholds

### 2. API Key Operations Rate Limiting

**Namespace**: `ezkey.api-key.rate-limit.*`

```properties
# API key operations rate limiting (key-based)
ezkey.api-key.rate-limit.enabled=true
ezkey.api-key.rate-limit.create-auth-attempt.requests=100
ezkey.api-key.rate-limit.create-auth-attempt.window-minutes=15
ezkey.api-key.rate-limit.wait-auth-attempt.requests=200
ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes=15
```

**Protected Endpoints**:
- `POST /api/v1/auth-attempts` (create auth attempt)
- `GET /api/v1/auth-attempts/{id}/wait` (wait for response)

**Features**:
- Per-API-key rate limiting
- Separate limits for different operation types
- Token bucket algorithm with burst capacity

### 3. Admin Operations Rate Limiting

**Namespace**: `ezkey.admin-operations.rate-limit.*`

```properties
# Admin operations rate limiting (admin-based)
ezkey.admin-operations.rate-limit.enabled=true
ezkey.admin-operations.rate-limit.api-key-create.requests=5
ezkey.admin-operations.rate-limit.api-key-create.window-minutes=15
ezkey.admin-operations.rate-limit.enrollment-reset.requests=3
ezkey.admin-operations.rate-limit.enrollment-reset.window-minutes=30
```

**Protected Endpoints**:
- `POST /api/v1/api-keys` (create API key)
- `POST /api/v1/admin/enrollments/reset` (reset enrollment)

**Features**:
- Per-admin rate limiting for high-value operations
- Strict limits for credential generation
- Recovery token-based limiting for enrollment reset

## Priority 1 Critical Endpoints

The implementation focuses on **Priority 1 critical security gaps**:

| Endpoint | Rate Limit | Rationale |
|----------|-------------|------------|
| `POST /api/v1/auth-attempts` | 100/15min per API key | High-value MFA operations |
| `GET /api/v1/auth-attempts/{id}/wait` | 200/15min per API key | Long polling protection |
| `POST /api/v1/api-keys` | 5/15min per admin | Credential generation protection |
| `POST /api/v1/admin/enrollments/reset` | 3/30min per token | Device unbind protection |

## Algorithm Details

### Bucket4j Token Bucket Algorithm

All rate limiting uses Bucket4j's token bucket algorithm:

```java
Bandwidth limit = Bandwidth.builder()
    .capacity(requests)
    .refillIntervally(requests, Duration.ofMinutes(windowMinutes))
    .build();
```

**Benefits**:
- Smooth rate limiting with burst capacity
- Memory efficient with automatic cleanup
- Thread-safe and high-performance
- Configurable refill rates

### Caffeine Cache Storage

Buckets are stored in Caffeine caches with automatic expiration:

```java
Cache<String, Bucket> buckets = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterAccess(Duration.ofHours(1))
    .build();
```

**Features**:
- Automatic cleanup of unused buckets
- Memory-bounded storage
- High-performance concurrent access

## Monitoring and Metrics

### Spring Boot Actuator Integration

Rate limiting metrics are exposed via Spring Boot Actuator:

**Endpoint**: `/actuator/metrics`

**Available Metrics**:
- `rate_limit.checks.total` - Total rate limit checks
- `rate_limit.exceeded.total` - Rate limit violations
- `rate_limit.ip_blocked.total` - IP blocks (admin login)
- `rate_limit.login_blocked.total` - Login blocks (admin login)

**Metric Tags**:
- `operation`: create_auth_attempt, wait_auth_attempt, api_key_create, enrollment_reset
- `type`: api_key, admin

### Example Metrics Query

```bash
# Get rate limit exceeded count
curl "http://localhost:9080/actuator/metrics/rate_limit.exceeded.total"

# Get rate limit checks by operation
curl "http://localhost:9080/actuator/metrics/rate_limit.checks.total?tag=operation:create_auth_attempt"
```

## Error Responses

### Rate Limit Exceeded (HTTP 429)

When rate limits are exceeded, the API returns HTTP 429 with headers:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 900
X-Rate-Limit-Limit: 100
X-Rate-Limit-Window: 15
X-Rate-Limit-Reason: Rate limit exceeded for operation 'CREATE_AUTH_ATTEMPT'
```

**Response Body**:
```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded for operation 'CREATE_AUTH_ATTEMPT': 100/100 operations in 15-minute window",
  "path": "/api/v1/auth-attempts"
}
```

## Security Considerations

### IP Address Detection

Admin login rate limiting uses multiple headers for IP detection:

1. `CF-Connecting-IP` (Cloudflare)
2. `X-Forwarded-For` (standard proxy)
3. `X-Real-IP` (nginx proxy)
4. Direct connection IP (fallback)

**Security Warning**: HTTP headers can be spoofed. Deploy behind trusted reverse proxies.

### Single-Instance Design

The current implementation is designed for single-instance deployments:

- In-memory storage (Caffeine caches)
- No distributed coordination
- CloudFlare recommended for multi-instance deployments

**Future Multi-Instance**: CloudFlare will handle rate limiting at the edge.

## Performance Characteristics

### Memory Usage

- **Admin Login**: ~1KB per IP (1000 IPs = ~1MB)
- **API Key Operations**: ~1KB per API key (10000 keys = ~10MB)
- **Admin Operations**: ~1KB per admin (1000 admins = ~1MB)

### CPU Impact

- **Token Consumption**: O(1) per request
- **Cache Operations**: O(1) average case
- **Metrics Recording**: Minimal overhead

## Configuration Tuning

### Production Recommendations

**Admin Login**:
```properties
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=5
ezkey.admin.rate-limit.login.block-after-failures=5
ezkey.admin.rate-limit.login.block-duration-minutes=60
```

**API Key Operations**:
```properties
ezkey.api-key.rate-limit.create-auth-attempt.requests=50
ezkey.api-key.rate-limit.create-auth-attempt.window-minutes=15
ezkey.api-key.rate-limit.wait-auth-attempt.requests=100
ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes=15
```

**Admin Operations**:
```properties
ezkey.admin-operations.rate-limit.api-key-create.requests=3
ezkey.admin-operations.rate-limit.api-key-create.window-minutes=15
ezkey.admin-operations.rate-limit.enrollment-reset.requests=2
ezkey.admin-operations.rate-limit.enrollment-reset.window-minutes=60
```

### Development Settings

For development, use more permissive limits:

```properties
# Development - more permissive
ezkey.admin.rate-limit.login.requests=50
ezkey.admin.rate-limit.login.window-minutes=1
ezkey.api-key.rate-limit.create-auth-attempt.requests=1000
ezkey.admin-operations.rate-limit.api-key-create.requests=100
```

## Troubleshooting

### Common Issues

**1. Rate Limits Too Strict**
- Check configuration values
- Monitor metrics for actual usage patterns
- Adjust limits based on legitimate usage

**2. IP Blocking Issues**
- Verify proxy header configuration
- Check for shared IP addresses (NAT, corporate networks)
- Review `X-Forwarded-For` header handling

**3. Memory Usage**
- Monitor cache sizes via metrics
- Adjust `maximumSize` if needed
- Consider cache expiration policies

### Debug Logging

Enable debug logging for rate limiting:

```properties
logging.level.org.ezkey.admin.security=DEBUG
```

This will show:
- Rate limit checks and results
- Bucket creation and expiration
- Metrics recording

## Implementation Files

### Core Components

- `RateLimitService.java` - API key operations rate limiting
- `AdminOperationsRateLimitService.java` - Admin operations rate limiting  
- `AdminRateLimitFilter.java` - Admin login rate limiting
- `ApiKeyRateLimitProperties.java` - API key configuration
- `AdminOperationsRateLimitProperties.java` - Admin operations configuration

### Configuration

- `ApiKeyRateLimitConfig.java` - API key Spring configuration
- `AdminOperationsRateLimitConfig.java` - Admin operations Spring configuration
- `AdminRateLimitConfig.java` - Admin login Spring configuration
- `application.properties` - External configuration

### Controllers

- `AuthAttemptController.java` - Rate limiting for auth attempts
- `ApiKeyController.java` - Rate limiting for API key creation
- `AdminEnrollmentController.java` - Rate limiting for enrollment reset

## Future Enhancements

### Priority 2 Endpoints

Future rate limiting for additional endpoints:
- `EnrollmentController` - Enrollment management
- `IntegrationController` - Integration management
- `AuditLogController` - Audit log queries

### Advanced Features

- **Distributed Rate Limiting**: Redis-backed buckets for multi-instance
- **Dynamic Configuration**: Runtime configuration updates
- **Advanced Metrics**: Custom dashboards and alerting
- **Rate Limit Headers**: Standard rate limit headers in responses

## Conclusion

The Ezkey Admin API rate limiting implementation provides comprehensive protection against abuse while maintaining high performance and configurability. The Bucket4j-based approach ensures smooth rate limiting with burst capacity, while the three-tier system provides appropriate protection for different types of operations.

The implementation follows the 80/20 principle - providing 80% of the security benefits with 20% of the complexity, making it suitable for production use while remaining maintainable and understandable.