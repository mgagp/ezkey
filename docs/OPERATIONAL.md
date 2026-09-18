# Ezkey Operational Configuration Guide

## Overview

This document provides operational guidance for deploying and configuring Ezkey in production environments. It covers security considerations, performance tuning, monitoring, and infrastructure-specific configurations.

## Production security posture (docker / EXP1)

Spring profile **`docker`** (used by EXP1 Lightsail and `clean-start --prod-safe`) enables fail-closed and rate-limit defaults:

| Property | Docker / EXP1 | Notes |
|----------|---------------|-------|
| `ezkey.rate-limit.enabled` | `true` | Auth API pending/respond/bind/verify (SEC-005). `docker-test` turns this off for churn tests only. |
| `ezkey.encryption.required` | `true` | Fail startup if Tink cannot initialize (SEC-002). |
| `ezkey.audit.integrity.required` | `true` | Fail startup if HMAC signing is inactive (SEC-008). |
| `ezkey.trusted-proxies.required` | `true` when behind Caddy | Via `EZKEY_TRUSTED_PROXIES_REQUIRED` on Lightsail and `./docker/start.sh --with-proxy`. Leave `false` for direct-port local stacks (no reverse proxy). |
| `ezkey.trusted-proxies.cidrs` | set on proxy stacks | `EZKEY_TRUSTED_PROXIES_CIDRS` — must cover the Docker/Caddy network. |

See module `CONFIGURATION.md` files and [`experimental-hybrid/lightsail/.env.example`](../experimental-hybrid/lightsail/.env.example).

### Runtime profile (integrity vs eval)

Product key `EZKEY_RUNTIME_PROFILE` / clean-start `--runtime=` selects **integrity** (default) or
**eval** (opt-in). Eval keeps MFA encryption and HMAC audit write on, and turns off audit-integrity
**monitoring** (checkpoints + heartbeat together, nightly validation, archive seal/purge, rotation /
re-encryption jobs). Tamper-evident claims apply only under integrity. Details:
[`docker/README.md`](../docker/README.md) § Runtime profiles.

## Table of Contents

1. [Rate Limiting Security](#rate-limiting-security)
2. [IP Detection and Proxy Configuration](#ip-detection-and-proxy-configuration)
3. [Database Configuration](#database-configuration)
4. [SSL/TLS Configuration](#ssltls-configuration)
5. [Logging and Monitoring](#logging-and-monitoring)
6. [Performance Tuning](#performance-tuning)
7. [Security Headers](#security-headers)
8. [Backup and Recovery](#backup-and-recovery)
9. [Global admin bootstrap and recovery codes](#global-admin-bootstrap-and-recovery-codes)

---

## Rate Limiting Security

### IP Header Security Considerations

Ezkey's rate limiting relies on HTTP headers to identify client IP addresses. **This creates potential security vulnerabilities** if not properly configured.

#### IP Header Priority (Most Trusted to Least Trusted)

1. **`CF-Connecting-IP`** (Cloudflare) - **TRUSTED**
   - Set by Cloudflare CDN
   - Cannot be spoofed by clients
   - Most reliable for client identification

2. **`X-Forwarded-For`** (Standard Proxies) - **CAN BE SPOOFED**
   - Standard HTTP header (RFC 7239)
   - **Easily spoofed by malicious clients**
   - Should only be trusted from known proxies

3. **`X-Real-IP`** (Nginx/HAProxy) - **CAN BE SPOOFED**
   - Used by Nginx and HAProxy
   - **Can be spoofed by clients**
   - Should only be trusted from known proxies

4. **`getRemoteAddr()`** (Direct Connection) - **FALLBACK**
   - Direct TCP connection IP
   - Most reliable when no proxies involved
   - Fallback when headers are invalid

### Security Recommendations

#### 1. Use Trusted CDN/Proxy

**Recommended: Cloudflare**
```nginx
# Nginx configuration to trust Cloudflare
set_real_ip_from 103.21.244.0/22;
set_real_ip_from 103.22.200.0/22;
set_real_ip_from 103.31.4.0/22;
# ... (full Cloudflare IP ranges)
real_ip_header CF-Connecting-IP;
```

**Alternative: Configure Trusted Proxies**
```nginx
# Nginx configuration for trusted proxies
real_ip_header X-Forwarded-For;
set_real_ip_from 10.0.0.0/8;        # Internal networks
set_real_ip_from 172.16.0.0/12;     # Internal networks
set_real_ip_from 192.168.0.0/16;    # Internal networks
```

#### 2. Strip Untrusted Headers

Configure your reverse proxy to remove potentially spoofed headers:

```nginx
# Remove spoofed headers from untrusted sources
proxy_set_header X-Forwarded-For "";
proxy_set_header X-Real-IP "";
# Only allow CF-Connecting-IP from Cloudflare
```

#### 3. Monitor Rate Limiting Effectiveness

```bash
# Monitor rate limiting logs
tail -f /var/log/ezkey/auth-api.log | grep "Rate limit"

# Check for suspicious patterns
grep "429" /var/log/ezkey/access.log | awk '{print $1}' | sort | uniq -c | sort -nr
```

### Rate Limiting Configuration

```properties
# Production rate limiting configuration
ezkey.rate-limit.enabled=true

# Auth Pending - User-initiated polling (permissive for normal usage)
ezkey.rate-limit.pending.requests=10
ezkey.rate-limit.pending.window-minutes=1
ezkey.rate-limit.pending.key-strategy=enrollment-id

# Auth Verify - User responses (restrictive for security)
ezkey.rate-limit.verify.requests=5
ezkey.rate-limit.verify.window-minutes=5
ezkey.rate-limit.verify.key-strategy=client-ip
```

---

## Admin API Authentication & Security

### Initial Global Admin Bootstrap

On first startup, Ezkey automatically initializes the admin authentication system:

1. **System Tenant Creation**
   - Default name: "Ezkey System" (configurable)
   - Represents the organization hosting this instance
   - Customizable via: `ezkey.organization.name` property

2. **Initial Global Admin Configuration (REQUIRED)**
   - Username: Must identify a specific individual (configured via `ezkey.admin.initial.username`)
   - Email: Required for identifiable operator identity (configured via `ezkey.admin.initial.email`)
   - Type: `GLOBAL_ADMIN` (full instance access)
   - Passwordless authentication: Enabled via System Integration and Global Admin Enrollment

3. **Identifiable operator identity**
   - Username must not be generic (not "admin", "administrator", "root")
   - Email required for audit trail and accountability (identifiable operator identity)
   - System fails to start if requirements not met

4. **Security Features**
   - Passwordless authentication (no passwords)
   - System Integration created for global admin authentication
   - Global Admin Enrollment created with RSA-2048 keys
   - Recovery codes generated (10 single-use codes)
   - Admin linked to system tenant

### Organization Configuration

```properties
# application.properties - Admin API
ezkey.organization.name=Acme Corporation
ezkey.organization.description=Acme Corp Ezkey MFA Instance
ezkey.organization.about-url=https://www.example.com/about-ezkey
```

**Docker (optional env vars, mapped in `application-docker.properties`):**

| Variable | Maps to | Purpose |
| -------- | ------- | ------- |
| `EZKEY_ORGANIZATION_NAME` | `ezkey.organization.name` | Instance display name; seeds system tenant and system integration names; `instanceName` on instance-info |
| `EZKEY_ORGANIZATION_DESCRIPTION` | `ezkey.organization.description` | Instance blurb; seeds system tenant description; `instanceDescription` on instance-info |
| `EZKEY_ORGANIZATION_ABOUT_URL` | `ezkey.organization.about-url` | Optional “About / learn more” link; `aboutUrl` on `GET /api/v1/public/instance-info` (Admin API and Auth API) |
| `EZKEY_QR_AUTH_BASE_URL` | `ezkey.qr.auth-base-url` | Public Auth API base URL embedded as `authUrl` in enrollment QR JSON and returned as `authApiPublicBaseUrl` from `GET /api/v1/public/instance-info` (Admin API and Auth API) |

**Use Cases:**
- **Single Organization**: Default "Ezkey System" for internal use
- **Customer Instance**: Custom name like "Acme Corporation" for dedicated deployment
- **Multi-Instance SaaS**: Different names per customer instance

### Admin API Rate Limiting

Protection against brute force attacks on admin login endpoint.

**Configuration:**

```properties
# Admin API Rate Limiting
ezkey.admin.rate-limit.enabled=true

# Login endpoint protection
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=5
ezkey.admin.rate-limit.login.key-strategy=client-ip

# IP blocking after repeated failures
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30
```

**Behavior:**
- **5 login attempts** per 5 minutes per IP
- **Automatic IP blocking** after 10 consecutive failures
- **30-minute block duration** for blocked IPs
- **429 Too Many Requests** response when rate limit exceeded
- **Retry-After header** indicates when to retry

**Monitoring:**

```bash
# Check rate limiting logs
tail -f /var/log/ezkey/admin-api.log | grep "Rate limit"

# Monitor blocked IPs
grep "Client IP.*blocked" /var/log/ezkey/admin-api.log | awk '{print $NF}' | sort | uniq -c
```

### Admin Authentication Endpoints

#### Login
```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "generated-password-here"
}
```

**Success Response:**
```json
{
  "success": true,
  "bearerToken": "ezkey_abc123...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-04T10:00:00Z",
  "passwordChangeRequired": true,
  "message": "Authentication successful"
}
```

**Rate Limited Response:**
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 300

Too many login attempts. Please try again later.
```

#### Logout
```http
POST /api/v1/admin/auth/logout
Authorization: Bearer ezkey_abc123...
```

**Success Response:**
```http
HTTP/1.1 200 OK
```

### Admin API Security Best Practices

1. **Configure Initial Global Admin Credentials**
   - Use strong, unique password
   - Enable MFA when available (roadmap)
   
2. **Configure Organization Name**
   - Set meaningful name for your instance
   - Helps identify instance in logs and audits

3. **Monitor Rate Limiting**
   - Watch for suspicious login patterns
   - Investigate blocked IPs
   - Adjust thresholds if needed

4. **Use Behind Reverse Proxy**
   - Cloudflare or Nginx recommended
   - Proper IP detection configuration
   - SSL/TLS termination

5. **Regular Token Cleanup**
   - Expired tokens cleaned automatically
   - Review active sessions regularly
   - Revoke tokens on suspicious activity

### Troubleshooting

#### Initial Global Admin Not Created

**Symptom**: No admin user after fresh installation

**Solution**:
1. Check configuration: Ensure `ezkey.admin.initial.username` and `ezkey.admin.initial.email` are set
2. Verify username is not generic (not "admin", "administrator", etc.)
3. Check database migrations ran successfully: `mvn flyway:info -pl ezkey-migration` (V3, V13)
4. Check application logs for admin creation: `grep "Global Admin" logs/admin-api.log`
5. Check InitialGlobalAdminService logs for validation errors
6. If database was manually cleared, restart application to trigger fallback creation

#### Rate Limiting Too Strict

**Symptom**: Legitimate users getting blocked

**Solution**:
1. Increase request limit: `ezkey.admin.rate-limit.login.requests=10`
2. Increase time window: `ezkey.admin.rate-limit.login.window-minutes=10`
3. Monitor IP patterns to identify legitimate vs. attack traffic

#### Custom Organization Name Not Applied

**Symptom**: Still seeing "Ezkey System" as tenant name

**Solution**:
1. Verify property set in `config/application.properties`
2. Restart application
3. Check if tenant already exists with old name (property only affects new creation)

---

## IP Detection and Proxy Configuration

### Cloudflare Configuration

**Benefits:**
- Reliable client IP via `CF-Connecting-IP`
- DDoS protection
- SSL termination
- Geographic distribution

**Setup:**
1. Configure DNS to point to Cloudflare
2. Set SSL/TLS mode to "Full" or "Full (Strict)"
3. Enable "Always Use HTTPS"
4. Configure Page Rules for API endpoints

### Nginx Reverse Proxy

```nginx
upstream ezkey_auth_api {
    server 127.0.0.1:8080;
}

server {
    listen 443 ssl http2;
    server_name api.ezkey.example.com;
    
    # SSL configuration
    ssl_certificate /etc/ssl/certs/ezkey.crt;
    ssl_certificate_key /etc/ssl/private/ezkey.key;
    
    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header X-XSS-Protection "1; mode=block" always;
    
    # Trust Cloudflare IPs
    set_real_ip_from 103.21.244.0/22;
    set_real_ip_from 103.22.200.0/22;
    # ... (add all Cloudflare IP ranges)
    real_ip_header CF-Connecting-IP;
    
    location /api/ {
        proxy_pass http://ezkey_auth_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Rate limiting at proxy level
        limit_req zone=api burst=20 nodelay;
    }
}

# Rate limiting zones
http {
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
}
```

### HAProxy Configuration

```haproxy
global
    daemon
    maxconn 4096

defaults
    mode http
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms

frontend ezkey_frontend
    bind *:443 ssl crt /etc/ssl/certs/ezkey.pem
    redirect scheme https if !{ ssl_fc }
    
    # Trust Cloudflare IPs
    acl cloudflare src -f /etc/haproxy/cloudflare-ips.txt
    
    # Rate limiting
    stick-table type ip size 100k expire 30s store http_req_rate(10s)
    http-request track-sc0 src
    http-request deny if { sc_http_req_rate(0) gt 10 }
    
    default_backend ezkey_backend

backend ezkey_backend
    balance roundrobin
    server ezkey1 127.0.0.1:8080 check
```

---

## Database Configuration

### PostgreSQL Production Settings

```sql
-- PostgreSQL configuration for production
-- postgresql.conf

# Memory settings
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 4MB
maintenance_work_mem = 64MB

# Connection settings
max_connections = 100
shared_preload_libraries = 'pg_stat_statements'

# Logging
log_statement = 'mod'
log_min_duration_statement = 1000
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_checkpoints = on
log_connections = on
log_disconnections = on

# Performance
random_page_cost = 1.1
effective_io_concurrency = 200
```

### Database Security

Ezkey uses **separate PostgreSQL roles** for Flyway vs each runtime API. The
normative table × privilege matrix is
[`DATABASE_ROLE_PERMISSIONS_MATRIX.md`](DATABASE_ROLE_PERMISSIONS_MATRIX.md).

| Role | Purpose |
|------|---------|
| `postgres` | Bootstrap / emergency DBA only (not runtime APIs) |
| `ezkey_migrate` | Flyway DDL |
| `ezkey_admin` | Admin API + schedulers |
| `ezkey_auth` | Auth API |
| `ezkey_integration` | Integration API |

**Docker / clean-start:** roles are created on first Postgres init
(`docker/postgres/init/01-create-roles.sh`); DML grants are applied after
Flyway by `scripts/db/apply-grants.sh` (Compose service `db-grants`).

**Local IDE:**

```bash
./scripts/db/create-roles.sh
# run Flyway as ezkey_migrate
./scripts/db/apply-grants.sh
./scripts/db/verify-grants.sh   # optional smoke
```

**Audit immutability at the DB boundary:** only `ezkey_admin` may `DELETE`
`ezkey_audit_log` (lifecycle purge). Auth and integration may `INSERT`/`UPDATE`
(HMAC seal after identity assign) but must not `DELETE`.

---

## SSL/TLS Configuration

### SSL Certificate Management

**Recommended: Let's Encrypt with Certbot**

```bash
# Install Certbot
sudo apt-get install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d api.ezkey.example.com

# Auto-renewal
sudo crontab -e
# Add: 0 12 * * * /usr/bin/certbot renew --quiet
```

### SSL Security Configuration

```nginx
# Strong SSL configuration
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512:ECDHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES256-GCM-SHA384;
ssl_prefer_server_ciphers off;
ssl_session_cache shared:SSL:10m;
ssl_session_timeout 10m;
ssl_stapling on;
ssl_stapling_verify on;
```

---

## Logging and Monitoring

### Logging and secrets (Admin API)

Server logs must **not** duplicate high-value secrets that are already returned in API responses (for example enrollment proof tokens and challenge codes after enrollment reset). Use identifiers such as `enrollmentId` and usernames for correlation. Plaintext recovery codes appear only in deliberate bootstrap output when configured.

### Global admin bootstrap and recovery codes

`ezkey.admin.mfa.bootstrap.credentials-output-mode` controls what the Admin API prints at first global-admin enrollment:

- **`full`** (default for development and Docker clean-start): enrollment secrets and ASCII QR may appear in startup logs; `bootstrap-credentials.json` may be written when file export is enabled (for `bootstrap-init` automation).
- **`recovery_primary`**: recovery codes and operator instructions only; enrollment secrets are omitted from logs and JSON export is skipped. Use the Admin UI account-recovery flow (recover → reset enrollment → bind). For Docker, set **`EZKEY_BOOTSTRAP_INIT_ENABLED=false`** on the `bootstrap-init` service when no `bootstrap-credentials.json` is produced, or omit that service.

**Operational recommendation:** After the global administrator completes first device binding and can sign in, **regenerate recovery codes** from the Admin UI so unused codes from bootstrap logs are invalidated.

### Log Configuration

```yaml
# logback-spring.xml for production
logging:
  level:
    org.ezkey: INFO
    org.springframework.security: WARN
    org.hibernate.SQL: WARN
    org.hibernate.type.descriptor.sql.BasicBinder: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/ezkey/auth-api.log
    max-size: 100MB
    max-history: 30
```

### Monitoring Setup

**Prometheus Metrics (if implemented):**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'ezkey-auth-api'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

**Grafana Dashboard:**
- Request rate
- Response times
- Error rates
- Rate limiting metrics
- Database connection pool

---

## Performance Tuning

### JVM Configuration

```bash
# Production JVM settings
JAVA_OPTS="-Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/ezkey/ \
  -Dspring.profiles.active=production"
```

### Application Configuration

```properties
# Performance tuning
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000

# JPA optimization
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.batch_versioned_data=true
```

---

## Security Headers

### HTTP Security Headers

```nginx
# Security headers for production
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self'; frame-ancestors 'none';" always;
```

### Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                )
            )
            .csrf(csrf -> csrf.disable()) // API doesn't need CSRF
            .build();
    }
}
```

---

## Backup and Recovery

### Database Backup

```bash
#!/bin/bash
# backup-ezkey-db.sh

BACKUP_DIR="/var/backups/ezkey"
DATE=$(date +%Y%m%d_%H%M%S)
DB_NAME="ezkey_production"

# Create backup directory
mkdir -p $BACKUP_DIR

# Create database backup
pg_dump -h localhost -U ezkey_user -d $DB_NAME | gzip > $BACKUP_DIR/ezkey_$DATE.sql.gz

# Keep only last 30 days
find $BACKUP_DIR -name "ezkey_*.sql.gz" -mtime +30 -delete

# Test backup integrity
gunzip -t $BACKUP_DIR/ezkey_$DATE.sql.gz
```

### Application Backup

```bash
#!/bin/bash
# backup-ezkey-app.sh

APP_DIR="/opt/ezkey"
BACKUP_DIR="/var/backups/ezkey/app"
DATE=$(date +%Y%m%d_%H%M%S)

# Backup application configuration
tar -czf $BACKUP_DIR/ezkey_config_$DATE.tar.gz \
    $APP_DIR/config/ \
    $APP_DIR/application.properties \
    /etc/nginx/sites-available/ezkey \
    /etc/systemd/system/ezkey.service

# Keep only last 7 days
find $BACKUP_DIR -name "ezkey_config_*.tar.gz" -mtime +7 -delete
```

---

## Troubleshooting

### Common Issues

**Rate Limiting Not Working:**
1. Check if `ezkey.rate-limit.enabled=true`
2. Verify IP detection in logs
3. Check proxy configuration
4. Monitor rate limiting metrics

**Database Connection Issues:**
1. Check connection pool settings
2. Verify database credentials
3. Monitor database connections
4. Check network connectivity

**SSL/TLS Issues:**
1. Verify certificate validity
2. Check SSL configuration
3. Test with SSL Labs
4. Monitor certificate expiration

### Log Analysis

```bash
# Check rate limiting effectiveness
grep "Rate limit" /var/log/ezkey/auth-api.log | tail -100

# Monitor error rates
grep "ERROR" /var/log/ezkey/auth-api.log | tail -50

# Check database performance
grep "slow query" /var/log/postgresql/postgresql.log
```

---

## References

- [Ezkey Architecture Documentation](ARCHITECTURE.md)
- [Ezkey Development Guide](DEVELOPMENT.md)
- [Ezkey Maintenance Guide](MAINTENANCE.md)
- [Spring Boot Production Features](https://docs.spring.io/spring-boot/docs/current/reference/html/production-features.html)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Nginx Documentation](https://nginx.org/en/docs/)
- [Cloudflare Documentation](https://developers.cloudflare.com/)

---

**Last Updated:** 2025-09-12  
**Version:** 1.0  
**Author:** Ezkey Contributors
