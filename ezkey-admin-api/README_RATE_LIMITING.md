# Admin API Rate Limiting

## Overview

The Admin API implements rate limiting on the login endpoint to protect against brute force attacks. The implementation uses the Bucket4j token bucket algorithm with Caffeine caching for efficient rate limit enforcement.

## Features

- ✅ **Token Bucket Algorithm**: Efficient rate limiting using Bucket4j
- ✅ **IP-based Tracking**: Identifies clients by IP address with proxy header support
- ✅ **Automatic Blocking**: Blocks IPs after repeated failed login attempts
- ✅ **Configurable Limits**: All settings configurable via application.properties
- ✅ **Conditional Activation**: Can be enabled/disabled without code changes
- ✅ **Security Logging**: Detailed logging of rate limit events and blocks

## Configuration

### Enable/Disable Rate Limiting

Rate limiting is controlled by the `ezkey.admin.rate-limit.enabled` property:

```properties
# Enable rate limiting (default: true)
ezkey.admin.rate-limit.enabled=true
```

### Login Endpoint Configuration

Configure rate limits for the login endpoint in `application.properties`:

```properties
# Maximum login attempts allowed per time window
ezkey.admin.rate-limit.login.requests=5

# Time window in minutes
ezkey.admin.rate-limit.login.window-minutes=5

# Client identification strategy (currently only "client-ip" supported)
ezkey.admin.rate-limit.login.key-strategy=client-ip

# Number of consecutive failures before blocking the IP
# Set to 0 to disable IP blocking
ezkey.admin.rate-limit.login.block-after-failures=10

# Duration in minutes to block an IP after too many failures
ezkey.admin.rate-limit.login.block-duration-minutes=30
```

### Default Configuration

The default configuration provides reasonable security:
- **5 login attempts** per **5 minutes** (prevents rapid brute force)
- **IP blocking** after **10 failed attempts** for **30 minutes**
- Automatic unblocking after the blocking duration expires

## How It Works

### Rate Limiting Flow

1. **Client makes login request** → POST /api/v1/admin/auth/login
2. **IP extraction** → Filter extracts client IP from headers or connection
3. **Block check** → Filter checks if IP is currently blocked
4. **Rate limit check** → Filter checks if IP has exceeded rate limit
5. **Request processing** → If checks pass, request proceeds to authentication
6. **Failure tracking** → On authentication failure, increment failure count
7. **Success tracking** → On authentication success, reset failure count

### IP Blocking Logic

```
Login Attempt → Failed Authentication
    ↓
Increment Failure Count
    ↓
Failure Count >= Block Threshold?
    ↓ YES
Block IP for X minutes
    ↓
Subsequent requests → HTTP 429 Too Many Requests
```

### HTTP Response Codes

| Status Code | Condition | Headers |
|-------------|-----------|---------|
| 200 OK | Successful login | - |
| 400 Bad Request | Invalid credentials | - |
| 429 Too Many Requests | Rate limit exceeded | `Retry-After`, `X-Rate-Limit-Limit`, `X-Rate-Limit-Window` |
| 429 Too Many Requests | IP blocked | `Retry-After`, `X-Rate-Limit-Reason` |

### Example Response Headers

When rate limit is exceeded:
```
HTTP/1.1 429 Too Many Requests
Retry-After: 300
X-Rate-Limit-Limit: 5
X-Rate-Limit-Window: 5
```

When IP is blocked:
```
HTTP/1.1 429 Too Many Requests
Retry-After: 1800
X-Rate-Limit-Reason: IP blocked due to too many failed attempts
```

## IP Detection

The filter uses the following priority for IP detection:

1. **X-Forwarded-For** header (standard proxy header)
2. **X-Real-IP** header (nginx proxy header)
3. **Direct connection IP** (fallback)

### Security Warning

⚠️ **Important**: HTTP headers like `X-Forwarded-For` can be spoofed. In production:

- Deploy behind a trusted reverse proxy (nginx, Apache, CloudFlare)
- Configure proxy to set X-Forwarded-For correctly
- Use firewall rules to restrict proxy access
- Monitor rate limiting effectiveness

The application logs a security warning on startup when rate limiting is enabled.

## Architecture

### Components

1. **AdminRateLimitProperties** - Configuration properties
   - Location: `org.ezkey.admin.config.AdminRateLimitProperties`
   - Purpose: Externalized configuration via `@ConfigurationProperties`

2. **AdminRateLimitFilter** - Servlet filter
   - Location: `org.ezkey.admin.security.AdminRateLimitFilter`
   - Purpose: Intercepts HTTP requests, enforces rate limits

3. **AdminRateLimitConfig** - Spring configuration
   - Location: `org.ezkey.admin.config.AdminRateLimitConfig`
   - Purpose: Conditionally activates rate limiting

4. **AdminAuthController** - Integration point
   - Location: `org.ezkey.admin.controller.AdminAuthController`
   - Purpose: Records authentication success/failure for tracking

### Dependencies

```xml
<!-- Rate Limiting -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

## Testing

### Manual Testing with curl

#### Test Rate Limiting

```bash
# Make 6 rapid login attempts (should hit rate limit on 6th)
for i in {1..6}; do
    echo "Attempt $i:"
    curl -X POST http://localhost:9080/api/v1/admin/auth/login \
         -H "Content-Type: application/json" \
         -d '{"username":"admin","password":"wrong"}' \
         -w "\nHTTP Status: %{http_code}\n\n"
    sleep 1
done
```

Expected output:
- Attempts 1-5: HTTP 400 (invalid credentials)
- Attempt 6: HTTP 429 (rate limit exceeded)

#### Test IP Blocking

```bash
# Make 11 failed login attempts (should block IP on 11th)
for i in {1..11}; do
    echo "Attempt $i:"
    curl -X POST http://localhost:9080/api/v1/admin/auth/login \
         -H "Content-Type: application/json" \
         -d '{"username":"admin","password":"wrong"}' \
         -w "\nHTTP Status: %{http_code}\n\n"
done
```

Expected output:
- Attempts 1-10: HTTP 400 (invalid credentials)
- Attempt 11+: HTTP 429 (IP blocked)

### Automated Testing

Unit and integration tests are located in:
```
ezkey-admin-api/src/test/java/org/ezkey/admin/
├── config/
│   └── AdminRateLimitConfigTest.java
├── security/
│   └── AdminRateLimitFilterTest.java
└── controller/
    └── AdminAuthControllerTest.java
```

Run tests with:
```bash
mvn test -pl ezkey-admin-api
```

## Monitoring and Logging

### Log Levels

The rate limiting filter logs events at different levels:

```
INFO  - Rate limiting initialization
DEBUG - Individual rate limit checks
WARN  - Rate limit exceeded, IP blocked
ERROR - Configuration or runtime errors
```

### Log Examples

**Rate limit exceeded:**
```
WARN  AdminRateLimitFilter - Rate limit exceeded for IP: 192.168.1.100 - Retry after 300 seconds
```

**IP blocked:**
```
WARN  AdminRateLimitFilter - IP BLOCKED due to 10 failed attempts: 192.168.1.100 - Blocked for 30 minutes
```

**Successful login (clears failure count):**
```
DEBUG AdminRateLimitFilter - Successful login for IP: 192.168.1.100 - Failure count reset
```

### Monitoring Recommendations

1. **Monitor blocked IPs** - Alert on frequent IP blocks
2. **Track rate limit hits** - Identify potential attack patterns
3. **Analyze failure patterns** - Detect distributed attacks
4. **Review proxy configuration** - Ensure correct IP detection

## Performance Considerations

### Cache Configuration

The rate limiting filter uses Caffeine cache with:
- **Maximum size**: 1000 buckets
- **Expiration**: 1 hour after last access
- **Cleanup**: Automatic

This configuration handles:
- Up to 1000 concurrent IP addresses
- Automatic cleanup of inactive buckets
- Minimal memory footprint

### Performance Impact

Rate limiting adds minimal overhead:
- **Bucket lookup**: O(1) via Caffeine cache
- **Token consumption**: O(1) via Bucket4j
- **Memory**: ~1KB per tracked IP

Expected impact:
- **< 1ms** additional latency per request
- **< 1MB** memory for 1000 tracked IPs

## Troubleshooting

### Issue: Rate limit not working

**Solution**: Check that rate limiting is enabled:
```properties
ezkey.admin.rate-limit.enabled=true
```

**Solution**: Verify dependencies are present:
```bash
mvn dependency:tree | grep bucket4j
mvn dependency:tree | grep caffeine
```

### Issue: Legitimate users getting blocked

**Solution**: Increase rate limit threshold:
```properties
ezkey.admin.rate-limit.login.requests=10
ezkey.admin.rate-limit.login.window-minutes=10
```

**Solution**: Increase block threshold:
```properties
ezkey.admin.rate-limit.login.block-after-failures=20
```

### Issue: IP detection incorrect behind proxy

**Solution**: Configure proxy to set correct headers

For nginx:
```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Real-IP $remote_addr;
```

For Apache:
```apache
RequestHeader set X-Forwarded-For "%{REMOTE_ADDR}e"
RequestHeader set X-Real-IP "%{REMOTE_ADDR}e"
```

### Issue: Rate limit persists after restart

**Note**: Rate limit state is stored in memory and **resets on application restart**. This is by design for simplicity and security.

## Future Enhancements

Planned improvements for future phases:

1. **Distributed rate limiting** - Redis backend for multi-instance deployments
2. **Advanced strategies** - Username-based, session-based rate limiting
3. **Whitelist/Blacklist** - Permanent IP allow/block lists
4. **Metrics API** - Expose rate limit metrics via Actuator
5. **Dynamic configuration** - Update limits without restart

## Security Best Practices

1. ✅ **Always enable in production**: `ezkey.admin.rate-limit.enabled=true`
2. ✅ **Deploy behind trusted proxy**: Configure X-Forwarded-For correctly
3. ✅ **Monitor blocked IPs**: Set up alerts for security incidents
4. ✅ **Adjust limits per environment**: Stricter in production
5. ✅ **Regular security audits**: Review rate limit effectiveness

## References

- [Bucket4j Documentation](https://bucket4j.com/)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [OWASP Brute Force Prevention](https://owasp.org/www-community/controls/Blocking_Brute_Force_Attacks)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)

---

**Version**: 1.0  
**Last Updated**: 2025-10-03  
**Status**: Production Ready


