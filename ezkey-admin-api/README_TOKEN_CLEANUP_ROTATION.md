# Token Cleanup & Rotation on Login - Phase 2A

## Overview

Phase 2A implements two complementary security features optimized for Ezkey's ultra-short admin sessions:

1. **Automatic Cleanup** - Removes expired tokens from database (scheduled)
2. **Rotation on Login** - Deactivates old tokens when admin logs in (1 active token per admin)

## Why This Approach?

### Context: Ezkey Admin Sessions

**Typical session durations:**
- **Global Admin**: 2-5 minutes (Login → Add/Modify Tenant → Logout)
- **Tenant Admin**: 2-5 minutes (Login → Add/Modify Integration → Logout)
- **Integration Admin**: 1-3 minutes (Login → Create AuthAttempt → Logout)

**Example session:**
```
10:00:00 - Login → Token (expires +24h = 34:00)
10:00:05 - Create AuthAttempt
10:00:10 - Logout → Token revoked

Session duration: 10 seconds
```

### Why NOT Automatic Rotation (50% lifetime)?

**Problem:**
```
Token lifetime: 24 hours
Rotation threshold (50%): 12 hours
Typical session: 2-10 minutes

Result: Rotation NEVER triggered ❌
```

**Automatic rotation at 50% would:**
- Add ~150 lines of complex code
- Never be used in practice
- Provide minimal security benefit

**Verdict:** ❌ Skip automatic rotation

### Why YES Cleanup + Rotation Login?

**Benefits:**
- ✅ Simple implementation (~80 lines total)
- ✅ High value for Ezkey's context
- ✅ Database hygiene (remove old tokens)
- ✅ Security (1 active token per admin)
- ✅ Token theft mitigation

**Effort:** ~3 hours  
**ROI:** Excellent

---

## Feature 1: Automatic Token Cleanup

### Purpose

Remove expired and inactive tokens from database to:
- Reduce attack surface (fewer tokens in DB)
- Improve query performance
- Maintain database hygiene

### How It Works

**Scheduled Task:**
```java
@Scheduled(cron = "0 0 * * * *") // Every hour
public void cleanupExpiredTokens() {
    LocalDateTime cutoff = LocalDateTime.now();
    
    // Delete tokens that are BOTH expired AND inactive
    int deleted = tokenRepository.deleteByExpiresAtBeforeAndActiveFalse(cutoff);
    
    logger.info("🧹 Cleaned up {} expired tokens", deleted);
}
```

**Deletion criteria:**
- Token `expiresAt` < now (expired)
- Token `active` = false (already deactivated)

**Preserved tokens:**
- Active tokens (even if expired) → kept for audit trail
- Recently created tokens → kept until expiration

### Configuration

**File:** `config/application.properties`

```properties
# Token cleanup configuration
ezkey.admin.token.cleanup.enabled=true
ezkey.admin.token.cleanup.schedule=0 0 * * * *  # Every hour
```

**Schedule format:** Cron expression
- `0 0 * * * *` - Every hour
- `0 0 */4 * * *` - Every 4 hours
- `0 0 0 * * *` - Daily at midnight

**Disable in development:**
```properties
ezkey.admin.token.cleanup.enabled=false
```

### Monitoring

**Check cleanup logs:**
```bash
# View cleanup activity
tail -f /var/log/ezkey/admin-api.log | grep "Cleaned up"

# Count tokens in database
psql -U ezkey -d ezkey_db -c "SELECT COUNT(*) FROM ezkey_admin_tokens WHERE active = false AND expires_at < NOW();"
```

---

## Feature 2: Rotation on Login

### Purpose

Enforce "one active token per admin" policy by deactivating all previous tokens when admin logs in.

### How It Works

**Login flow:**
```
1. Admin provides credentials
   ↓
2. Validate credentials
   ↓
3. Deactivate ALL existing active tokens for this admin
   ↓
4. Generate NEW token
   ↓
5. Return new token
```

**Code:**
```java
public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
    // 1. Validate credentials
    EzkeyAdmin admin = validateCredentials(request);
    
    // 2. Deactivate old tokens (NEW)
    int deactivated = tokenRepository.deactivateAllTokensForAdmin(admin.getAdminId());
    
    if (deactivated > 0) {
        logger.info("🔄 Rotated {} old tokens for admin {}", 
            deactivated, admin.getUsername());
    }
    
    // 3. Generate new token
    String bearerToken = generateBearerToken();
    LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
    
    AdminToken token = new AdminToken();
    token.setAdmin(admin);
    token.setBearerToken(bearerToken);
    token.setExpiresAt(expiresAt);
    token.setActive(true);
    tokenRepository.save(token);
    
    // 4. Return response
    return buildSuccessResponse(admin, bearerToken, expiresAt);
}
```

**Database operation:**
```sql
-- Deactivate all active tokens for admin_id = 1
UPDATE ezkey_admin_tokens 
SET active = false 
WHERE admin_id = 1 AND active = true;
```

### Security Benefit: Token Theft Mitigation

**Scenario:**
```
1. Admin logs in from laptop → Token A (active)
2. Token A stolen by attacker
3. Admin logs in from desktop → Token B (active), Token A (inactive)
4. Attacker tries Token A → 401 Unauthorized ✅
```

**Result:** Stolen token immediately invalidated on next legitimate login.

### Configuration

**File:** `config/application.properties`

```properties
# Token rotation on login
ezkey.admin.token.rotation-on-login=true  # Can disable if needed
```

**Disable rotation:**
```properties
ezkey.admin.token.rotation-on-login=false
```

**Use case for disabling:**
- Testing scenarios requiring multiple active tokens
- Temporary troubleshooting
- Development environments with shared accounts (not recommended)

### Monitoring

**Check rotation activity:**
```bash
# View rotation logs
tail -f /var/log/ezkey/admin-api.log | grep "Rotated"

# Example output:
# 2025-10-03 10:00:15 INFO - 🔄 Rotated 2 old tokens for admin 'john.doe' on login
# 2025-10-03 14:30:42 INFO - 🔄 Rotated 1 old tokens for admin 'admin' on login
```

**Query active tokens per admin:**
```sql
-- Count active tokens per admin
SELECT a.username, COUNT(t.token_id) as active_tokens
FROM ezkey_admin a
LEFT JOIN ezkey_admin_tokens t ON a.admin_id = t.admin_id AND t.active = true
GROUP BY a.username
ORDER BY active_tokens DESC;
```

**Expected result:** Each admin should have 0 or 1 active token.

---

## Implementation Checklist

### Components to Create

- [ ] `AdminTokenCleanupProperties.java` - Configuration properties
- [ ] `AdminTokenCleanupService.java` - Scheduled cleanup service
- [ ] `AdminTokenRotationProperties.java` - Configuration properties
- [ ] Modify `AdminAuthService.java` - Add rotation on login
- [ ] Modify `AdminTokenRepository.java` - Add `deactivateAllTokensForAdmin()` method
- [ ] Modify `AdminTokenRepository.java` - Add `deleteByExpiresAtBeforeAndActiveFalse()` method
- [ ] Update `config/application.properties` - Add configuration
- [ ] Add unit tests for cleanup service
- [ ] Add unit tests for rotation logic
- [ ] Add integration tests for login flow

### Configuration to Add

**File:** `config/application.properties`

```properties
# ==============================================
# Token Cleanup & Rotation Configuration
# ==============================================

# Automatic cleanup of expired tokens
ezkey.admin.token.cleanup.enabled=true
ezkey.admin.token.cleanup.schedule=0 0 * * * *  # Every hour

# Rotation on login (1 active token per admin)
ezkey.admin.token.rotation-on-login=true
```

---

## Testing

### 1. Test Cleanup Service

**Unit Test:**
```java
@Test
void testCleanupExpiredTokens() {
    // Arrange: Create expired inactive tokens
    AdminToken expiredToken = createToken(
        admin, 
        LocalDateTime.now().minusDays(2), // expired
        false // inactive
    );
    tokenRepository.save(expiredToken);
    
    // Act: Run cleanup
    cleanupService.cleanupExpiredTokens();
    
    // Assert: Token deleted
    assertFalse(tokenRepository.existsById(expiredToken.getTokenId()));
}

@Test
void testCleanupPreservesActiveTokens() {
    // Arrange: Create expired but ACTIVE token
    AdminToken activeToken = createToken(
        admin,
        LocalDateTime.now().minusDays(2), // expired
        true // ACTIVE
    );
    tokenRepository.save(activeToken);
    
    // Act: Run cleanup
    cleanupService.cleanupExpiredTokens();
    
    // Assert: Token preserved (for audit)
    assertTrue(tokenRepository.existsById(activeToken.getTokenId()));
}
```

### 2. Test Rotation on Login

**Integration Test:**
```java
@Test
void testRotationOnLogin() {
    // Arrange: Create existing active token
    AdminToken oldToken = createActiveToken(admin);
    tokenRepository.save(oldToken);
    
    // Act: Login
    AdminLoginRequestDto request = new AdminLoginRequestDto("admin", "password");
    AdminLoginResponseDto response = authService.authenticate(request);
    
    // Assert: Old token deactivated
    AdminToken oldTokenRefreshed = tokenRepository.findById(oldToken.getTokenId()).orElseThrow();
    assertFalse(oldTokenRefreshed.getActive());
    
    // Assert: New token active
    AdminToken newToken = tokenRepository.findByBearerToken(response.getBearerToken()).orElseThrow();
    assertTrue(newToken.getActive());
    
    // Assert: Only 1 active token per admin
    long activeCount = tokenRepository.countByAdminAndActiveTrue(admin);
    assertEquals(1, activeCount);
}

@Test
void testStolenTokenInvalidatedOnLogin() {
    // Arrange: Simulate token theft
    AdminToken stolenToken = createActiveToken(admin);
    tokenRepository.save(stolenToken);
    
    // Act: Legitimate admin logs in again
    authService.authenticate(new AdminLoginRequestDto("admin", "password"));
    
    // Assert: Stolen token no longer works
    assertThrows(UnauthorizedException.class, () -> {
        authService.validateToken(stolenToken.getBearerToken());
    });
}
```

### 3. Manual Testing

**Test cleanup:**
```bash
# 1. Create expired tokens manually
psql -U ezkey -d ezkey_db -c "
UPDATE ezkey_admin_tokens 
SET expires_at = NOW() - INTERVAL '1 day', active = false 
WHERE token_id IN (SELECT token_id FROM ezkey_admin_tokens LIMIT 5);
"

# 2. Wait for next hour or trigger manually
# 3. Check logs for cleanup message
tail -f logs/admin-api.log | grep "Cleaned up"

# 4. Verify tokens deleted
psql -U ezkey -d ezkey_db -c "
SELECT COUNT(*) FROM ezkey_admin_tokens 
WHERE expires_at < NOW() AND active = false;
"
```

**Test rotation on login:**
```bash
# 1. Login and save token
curl -X POST http://localhost:9080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "your-password"}' \
  | jq -r '.bearerToken' > token1.txt

# 2. Login again and save new token
curl -X POST http://localhost:9080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "your-password"}' \
  | jq -r '.bearerToken' > token2.txt

# 3. Verify token1 no longer works
TOKEN1=$(cat token1.txt)
curl -X GET http://localhost:9080/api/v1/admin/integrations \
  -H "Authorization: Bearer $TOKEN1"
# Expected: 401 Unauthorized

# 4. Verify token2 works
TOKEN2=$(cat token2.txt)
curl -X GET http://localhost:9080/api/v1/admin/integrations \
  -H "Authorization: Bearer $TOKEN2"
# Expected: 200 OK
```

---

## Troubleshooting

### Cleanup Not Running

**Symptom:** Expired tokens not being deleted

**Checks:**
1. Verify cleanup enabled:
   ```properties
   ezkey.admin.token.cleanup.enabled=true
   ```

2. Check scheduler is active:
   ```bash
   grep "@EnableScheduling" src/main/java/org/ezkey/admin/AdminApiApplication.java
   ```

3. Verify cron expression valid:
   ```bash
   # Test cron online: https://crontab.guru/
   ```

4. Check application logs:
   ```bash
   grep "Cleaned up" logs/admin-api.log
   ```

### Rotation Not Working

**Symptom:** Multiple active tokens per admin

**Checks:**
1. Verify rotation enabled:
   ```properties
   ezkey.admin.token.rotation-on-login=true
   ```

2. Check login logs:
   ```bash
   grep "Rotated" logs/admin-api.log
   ```

3. Query database:
   ```sql
   SELECT a.username, COUNT(t.token_id) as active_tokens
   FROM ezkey_admin a
   LEFT JOIN ezkey_admin_tokens t ON a.admin_id = t.admin_id AND t.active = true
   GROUP BY a.username
   HAVING COUNT(t.token_id) > 1;
   ```

4. Verify repository method:
   ```java
   // Check method exists in AdminTokenRepository
   int deactivateAllTokensForAdmin(Integer adminId);
   ```

### Old Token Still Works After Login

**Symptom:** Token should be inactive but still authenticates

**Possible causes:**
1. Rotation disabled in config
2. Caching issue in `AdminTokenAuthenticationFilter`
3. Database transaction not committed

**Fix:**
1. Enable rotation:
   ```properties
   ezkey.admin.token.rotation-on-login=true
   ```

2. Verify filter checks `active` flag:
   ```java
   AdminToken token = tokenRepository.findByBearerToken(bearerToken);
   if (token == null || !token.getActive()) {
       throw new UnauthorizedException();
   }
   ```

3. Add `@Transactional` to login method:
   ```java
   @Transactional
   public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
       // ...
   }
   ```

---

## Performance Considerations

### Cleanup Service

**Impact:** Low
- Runs once per hour (configurable)
- Single `DELETE` query with index on `expires_at` and `active`
- Typical deletion: 0-100 tokens per run

**Optimization:**
```sql
-- Add compound index for efficient cleanup
CREATE INDEX idx_admin_tokens_cleanup 
ON ezkey_admin_tokens(expires_at, active) 
WHERE active = false;
```

### Rotation on Login

**Impact:** Low
- Single `UPDATE` query per login
- Index on `admin_id` and `active`
- Typical update: 0-2 tokens per login

**Optimization:**
```sql
-- Add compound index for efficient rotation
CREATE INDEX idx_admin_tokens_rotation 
ON ezkey_admin_tokens(admin_id, active) 
WHERE active = true;
```

**Login performance:**
- Before Phase 2A: ~50ms
- After Phase 2A: ~55ms (+5ms for rotation query)
- Impact: Negligible (10% increase)

---

## Migration Plan

### Step 1: Deploy Configuration
```properties
# Start with both features enabled
ezkey.admin.token.cleanup.enabled=true
ezkey.admin.token.cleanup.schedule=0 0 * * * *
ezkey.admin.token.rotation-on-login=true
```

### Step 2: Monitor Initial Cleanup
```bash
# Watch first cleanup run
tail -f logs/admin-api.log | grep "Cleaned up"

# Verify database
psql -U ezkey -d ezkey_db -c "
SELECT COUNT(*) FROM ezkey_admin_tokens WHERE active = false;
"
```

### Step 3: Verify Rotation
```bash
# Monitor rotation activity
tail -f logs/admin-api.log | grep "Rotated"

# Check token counts per admin
psql -U ezkey -d ezkey_db -c "
SELECT a.username, COUNT(t.token_id) as active_tokens
FROM ezkey_admin a
LEFT JOIN ezkey_admin_tokens t ON a.admin_id = t.admin_id AND t.active = true
GROUP BY a.username;
"
```

### Step 4: Adjust if Needed
```properties
# If cleanup too aggressive, reduce frequency
ezkey.admin.token.cleanup.schedule=0 0 */4 * * *  # Every 4 hours

# If rotation causes issues (rare), disable temporarily
ezkey.admin.token.rotation-on-login=false
```

---

## Summary

**Phase 2A delivers:**
- ✅ Automatic database cleanup (expired tokens removed)
- ✅ One active token per admin (security hardening)
- ✅ Token theft mitigation (stolen tokens invalidated on login)
- ✅ Simple implementation (~80 lines)
- ✅ No breaking changes
- ✅ Configurable and monitorable

**Effort:** ~3 hours  
**Value:** High for Ezkey's context  
**Risk:** Low (can disable both features independently)

**Next Phase:** MFA Ezkey integration (Phase 4 - Roadmap)

