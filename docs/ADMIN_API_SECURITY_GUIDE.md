# Ezkey Admin API - Security Implementation Guide (Passwordless-Only)

**Version:** 2.0 (Passwordless)  
**Last Updated:** October 13, 2025  
**Audience:** Developers integrating Ezkey  
**Status:** Production Ready

---

## Table of Contents

1. [Overview](#overview)
2. [Security Architecture](#security-architecture)
3. [Initial Setup Workflow](#initial-setup-workflow)
4. [Daily Authentication Workflow](#daily-authentication-workflow)
5. [Challenge-Based Authentication](#challenge-based-authentication)
6. [Emergency Recovery](#emergency-recovery)
7. [Token Management](#token-management)
8. [Security Best Practices](#security-best-practices)
9. [Troubleshooting](#troubleshooting)

---

## Overview

The Ezkey Admin API implements **passwordless-only authentication** - a revolutionary approach that eliminates passwords entirely. Ezkey "eats its own dogfood" by using its own cryptographic authentication system for admin access.

### Key Security Features

- ✅ **No passwords stored** - Nothing to steal, phish, or guess
- ✅ **Device-bound credentials** - Private keys never leave the device
- ✅ **Cryptographic signatures** - FIDO2-like authentication
- ✅ **Recovery codes** - 106-bit entropy emergency access (paranoia-level)
- ✅ **Bearer token management** - 24-hour tokens with rotation
- ✅ **Rate limiting** - IP-based protection against brute force

### Authentication Modes

1. **Single-call (no challenge):** Blocking wait for device approval (convenient)
2. **Two-call (with challenge):** Challenge code verification (secure)
3. **Recovery mode:** Emergency access via single-use codes (rare)

---

## Security Architecture

### Components Overview

```mermaid
flowchart TB
    subgraph "Administrator"
        A[Admin User]
    end
    
    subgraph "Ezkey Admin API :9080"
        B[Passwordless Login]
        C[Recovery Endpoint]
        D[Enrollment Reset]
        E[Protected Resources]
    end
    
    subgraph "Ezkey Auth API :8080"
        F[Enrollment Binding]
        G[Auth Attempt Response]
    end
    
    subgraph "Security Layer"
        H[Rate Limiter]
        I[Token Manager]
        J[Crypto Validator]
    end
    
    subgraph "Storage"
        K[(Database)]
    end
    
    subgraph "Device"
        L[Ezkey Mobile / Demo Device]
    end
    
    A -->|1. Login Request| H
    H --> B
    B -->|2. Create Auth Attempt| K
    
    L -->|3. Check Pending| G
    L -->|4. Approve + Sign| J
    
    J -->|5. Validate Signature| K
    B -->|6. Issue Bearer Token| I
    I -->|7. Access| E
    
    A -.->|Emergency| C
    C -.->|Recovery Token| D
    D -.->|New Credentials| F
    
    style H fill:#ffcccc
    style I fill:#ccffcc
    style J fill:#ccccff
```

### Security Layers

| Layer | Component | Purpose |
|-------|-----------|---------|
| **1. Authentication** | Passwordless Crypto | Verify identity via device signature |
| **2. Authorization** | Bearer Tokens | Access control (24h expiry) |
| **3. Rate Limiting** | IP-based limits | Prevent brute force (5/min) |
| **4. Recovery** | Single-use codes | Emergency access (106-bit entropy) |
| **5. Audit** | Database logging | Complete authentication trail |

---

## Initial Setup Workflow

### Step 1: Database Migration

Run migrations V1-V9 to set up the passwordless-only schema:

```bash
cd ezkey-migration
mvn spring-boot:run
```

**Migrations:**
- V1-V5: Core schema
- V6: Add passwordless support
- V7: Add recovery codes
- V8-V9: Remove password infrastructure

**Result:**
- `ezkey_admin` table without password columns
- `challenge_required` per-admin flag
- `recovery_codes` array (BCrypt hashed)

### Step 2: Bootstrap Admin Zero

Start the Admin API for the first time:

```bash
cd ezkey-admin-api
mvn spring-boot:run
```

**Bootstrap Process (Automatic):**

The system automatically creates "Admin Zero" on first startup if no admin exists:

```
================================================================================
📱 ADMIN ZERO PASSWORDLESS ENROLLMENT - SAVE CREDENTIALS NOW!
================================================================================

✅ Admin Zero: admin (GLOBAL_ADMIN)
✅ Enrollment Zero: Admin MFA (ID: 1)

🔐 ENROLLMENT CREDENTIALS:
   Enrollment ID: 1
   Enrollment Proof Token: abc123xyz...def789 (64 chars)
   Challenge Code: 654321

🔑 RECOVERY CODES (106-BIT ENTROPY - SAVE SECURELY):
   1. 1234-5678-9012-3456-7890-1234-5678-9012
   2. 4567-8901-2345-6789-0123-4567-8901-2345
   3. 7890-1234-5678-9012-3456-7890-1234-5678
   4. 0123-4567-8901-2345-6789-0123-4567-8901
   5. 3456-7890-1234-5678-9012-3456-7890-1234
   6. 6789-0123-4567-8901-2345-6789-0123-4567
   7. 9012-3456-7890-1234-5678-9012-3456-7890
   8. 2345-6789-0123-4567-8901-2345-6789-0123
   9. 5678-9012-3456-7890-1234-5678-9012-3456
  10. 8901-2345-6789-0123-4567-8901-2345-6789

⚠️ CRITICAL: Save these credentials NOW.
⚠️ Recovery codes cannot be retrieved later.
⚠️ These codes are SINGLE-USE ONLY.
================================================================================
```

**⚠️ CRITICAL ACTIONS:**

1. **Copy enrollment credentials** - You need these to bind your device
2. **Save recovery codes** - Print or store in password manager
3. **Test recovery codes** - Verify at least one works before production

### Step 3: Bind Your Device

Use the Demo Device or Ezkey Mobile app to bind enrollment:

**Demo Device (http://localhost:8082):**
1. Go to "Bind Enrollment"
2. Enter credentials from bootstrap:
   - Enrollment ID: `1`
   - Enrollment Proof Token: `[64-char token]`
3. Click "Bind"
4. When prompted, enter Challenge Code: `654321`
5. Click "Verify"

**Result:**
```
✅ Enrollment bound successfully!
Device public key registered for enrollment 1
```

**Verification (Database):**
```sql
SELECT enrollment_id, device_public_key IS NOT NULL as is_bound
FROM ezkey_enrollment WHERE enrollment_id = 1;
-- is_bound should be TRUE
```

### Step 4: Test Passwordless Login

**Postman/cURL:**
```http
POST http://localhost:9080/api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "challengeRequested": false
}
```

**Expected Behavior:**
1. ⏳ Request blocks (waiting for device)
2. On device: Check "Pending Authentications" → See login request → Approve
3. ✅ Postman receives bearer token

**Success Response:**
```json
{
  "success": true,
  "token": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-14T10:00:00Z",
  "message": "Authentication successful"
}
```

**Setup Complete!** ✅

---

## Daily Authentication Workflow

### Scenario 1: Quick Login (No Challenge)

**Use Case:** Daily admin tasks, low-risk operations

**Flow:**
```
Admin → POST /login → (blocks) → Device Approval → Bearer Token
```

**Request:**
```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "challengeRequested": false
}
```

**Timeline:**
- `0s`: Request sent (blocks)
- `5s`: Admin opens device, checks pending
- `7s`: Admin approves
- `7s`: Response received with bearer token

**Advantages:**
- ✅ Single API call
- ✅ Automatic wait (no polling logic needed)
- ✅ Simple integration

**Disadvantages:**
- ⏳ Blocking (up to 5 minutes)
- ❌ No challenge verification (slightly less secure)

---

### Scenario 2: Secure Login (With Challenge)

**Use Case:** High-security operations, sensitive data access, production systems

**Flow:**
```
Admin → POST /login → Challenge Code → Device Entry → POST /passwordless-wait → Bearer Token
```

**Step 1 - Initiate Login:**
```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "challengeRequested": true
}
```

**Step 1 Response (Immediate):**
```json
{
  "success": false,
  "status": "pending",
  "authAttemptId": 123,
  "challengeCode": 654321,
  "username": "admin",
  "adminType": "GLOBAL_ADMIN",
  "expiresAt": "2025-10-04T10:05:00Z",
  "message": "Challenge verification required. Enter code 654321 on your device, then call /passwordless-wait."
}
```

**Step 2 - Display Challenge:**
```
UI displays: "Enter code 654321 on your Ezkey device"
```

**Step 3 - Admin Actions on Device:**
1. Check "Pending Authentications"
2. See login request with challenge input field
3. Enter code: `654321`
4. Approve

**Step 4 - Wait for Completion:**
```http
POST /api/v1/admin/auth/passwordless-wait
Content-Type: application/json

{
  "authAttemptId": 123,
  "challengeCode": 654321
}
```

**Step 4 Response (After Approval):**
```json
{
  "success": true,
  "token": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-04T10:00:00Z",
  "message": "Authentication successful"
}
```

**Timeline:**
- `0s`: Login with challenge (immediate response)
- `0s-30s`: User sees challenge code, opens device
- `30s`: User enters 654321 on device
- `35s`: User approves on device
- `35s`: `/passwordless-wait` returns bearer token

**Advantages:**
- ✅ Non-blocking initial request
- ✅ Challenge verification adds security layer
- ✅ Protection against device theft
- ✅ Anti-enumeration protection (challengeCode proves legitimacy)

**Security Enhancement:**
The `challengeCode` serves two purposes:
1. **User verification:** User must have seen the challenge code (proves legitimate login)
2. **Anti-enumeration:** Prevents attackers from guessing authAttemptId values

---

## Challenge-Based Authentication

### Why Use Challenges?

**Without Challenge (Convenience):**
```
Risk: If device is stolen, thief can approve any login attempt
```

**With Challenge (Security):**
```
Protection: Even with stolen device, thief doesn't know the challenge code
            displayed on the admin's screen
```

### Configuration

**Per-Admin Default:**
```sql
-- Set challenge required for specific admin
UPDATE ezkey_admin 
SET challenge_required = true 
WHERE username = 'admin';
```

**Per-Request Override:**
```json
// Override admin's default (convenience for trusted network)
{
  "username": "admin",
  "challengeRequested": false
}
```

### Challenge Code Properties

- **Format:** 6-digit integer (000000-999999)
- **Entropy:** ~20 bits (1 million combinations)
- **Lifetime:** 5 minutes
- **Display:** Admin screen shows code, user enters on device
- **Validation:** Device-side validation before signature
- **Audit:** Invalid challenges logged as INVALID status

---

## Emergency Recovery

### When You Need Recovery

**Scenario:** Admin loses their enrolled device (lost, stolen, broken)

**Problem:** Cannot authenticate without device approval

**Solution:** Single-use recovery codes (generated at account creation)

### Recovery Workflow

#### Step 1: Use Recovery Code

```http
POST /api/v1/admin/auth/recover
Content-Type: application/json

{
  "username": "admin",
  "recoveryCode": "1234-5678-9012-3456-7890-1234-5678-9012"
}
```

**Response:**
```json
{
  "success": true,
  "recoveryToken": "ezkey_recovery_abc123...",
  "expiresAt": "2025-10-04T10:30:00Z",
  "codesRemaining": 9,
  "message": "Recovery successful. Token valid for 30 minutes. Re-bind enrollment immediately."
}
```

**Notes:**
- Recovery token expires after **30 minutes** (limited time window)
- Code is **single-use** (removed from array after validation)
- `codesRemaining` shows how many codes you have left

#### Step 2: Reset Enrollment (Unbind Lost Device)

```http
POST /api/v1/admin/enrollments/reset
Authorization: Bearer ezkey_recovery_abc123...
Content-Type: application/json

{
  "enrollmentId": 1
}
```

**Response:**
```json
{
  "success": true,
  "enrollmentId": 1,
  "enrollmentProofToken": "new-token-xyz789...",
  "enrollmentChallenge": 123456,
  "integrationId": 1,
  "message": "Enrollment reset successfully. Old device unbound. Use these credentials to bind new device."
}
```

**Effect:**
- Old device's public key removed (`device_public_key = NULL`)
- Enrollment status reset to `CREATED` (ready for new bind)
- New credentials generated for binding

#### Step 3: Bind New Device

Use new credentials from Step 2 to bind your new/replacement device:

**Demo Device:**
1. Go to "Bind Enrollment"
2. Enter **NEW** credentials:
   - Enrollment ID: `1`
   - Enrollment Proof Token: `new-token-xyz789...`
3. Bind and verify with challenge code: `123456`

#### Step 4: Resume Normal Login

After binding new device, passwordless login works as before:

```http
POST /api/v1/admin/auth/login
{
  "username": "admin"
}
```

### Recovery Code Security

**Format:** `XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX` (32 digits)

**Entropy:** 106 bits
- 10^32 possible combinations
- At 1 million attempts/second: 3 × 10^18 years to brute force
- Resistant to quantum attacks (>100 bits recommended)

**Storage:** BCrypt hashed (work factor 10)

**Generation:** SecureRandom (cryptographically secure)

**Best Practices:**
- ✅ Print codes and store in safe
- ✅ Store encrypted in password manager
- ✅ Test one code before production
- ❌ Never store in plaintext
- ❌ Never share recovery codes
- ❌ Never email recovery codes

---

## Token Management

### Bearer Token Lifecycle

**Token Properties:**
```
Format:    ezkey_[UUID without hyphens]
Lifetime:  24 hours
Storage:   PostgreSQL (ezkey_admin_tokens table)
Fields:    bearer_token, admin_id, expires_at, active
```

**Token States:**
- **ACTIVE + NOT EXPIRED:** Valid for API access
- **ACTIVE + EXPIRED:** Invalid (rejected by validator)
- **INACTIVE:** Revoked (logout, rotation)

### Token Rotation

**Automatic Rotation on Login:**
```properties
ezkey.admin.token.rotation-on-login=true
```

**Behavior:**
- On successful login, all old active tokens are deactivated
- Forces "one active token per admin" security policy
- Invalidates stolen tokens on next legitimate login

**Manual Rotation:**
```http
POST /api/v1/admin/auth/logout
Authorization: Bearer ezkey_old_token...
```

Then login again to get new token.

### Token Cleanup

**Automatic Cleanup (Scheduled):**
```properties
ezkey.admin.token.cleanup.enabled=true
ezkey.admin.token.cleanup.schedule=0 0 * * * *  # Hourly
```

**Cleanup Logic:**
- Deletes tokens that are **both expired AND inactive**
- Preserves active tokens (even if expired) for audit
- Runs on configurable schedule (default: hourly)

---

## Security Best Practices

### 1. Device Security

**Critical:** The device stores your private key
- ✅ Enable device passcode/biometric
- ✅ Don't root/jailbreak device
- ✅ Keep Ezkey app updated
- ❌ Never share device unlocked
- ❌ Never backup private keys

### 2. Recovery Codes Management

**Storage Options (Ranked):**
1. **Physical safe** - Print codes, store in fireproof safe
2. **Password manager** - Encrypted storage (1Password, Bitwarden)
3. **Encrypted USB** - Offline encrypted storage

**Never:**
- ❌ Store in cloud plaintext (Dropbox, Google Drive)
- ❌ Email to yourself
- ❌ Take unencrypted screenshots
- ❌ Share with anyone (even IT staff)

**Best Practice:**
```bash
# Test one recovery code immediately after bootstrap
# This ensures codes work BEFORE you need them in emergency
POST /recover with code #10
If successful: You have 9 codes left
If failed: Contact support immediately
```

### 3. Challenge Mode Decision

**Use challenge=false (convenience) when:**
- Trusted network (office VPN)
- Low-risk operations (read-only queries)
- Device always with you

**Use challenge=true (security) when:**
- Public networks (coffee shop, airport)
- High-risk operations (user deletion, key changes)
- Compliance requirements (SOC2, PCI-DSS)
- Production systems

**Per-Admin Configuration:**
```sql
-- Set default for high-security admin
UPDATE ezkey_admin 
SET challenge_required = true 
WHERE admin_type = 'GLOBAL_ADMIN';
```

### 4. Rate Limiting Configuration

**Production Settings:**
```properties
# Login endpoint
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=1

# IP blocking
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30

# Recovery endpoint (stricter)
ezkey.admin.rate-limit.recovery.requests=3
ezkey.admin.rate-limit.recovery.window-minutes=15
```

### 5. Audit and Monitoring

**Key Metrics to Monitor:**
```sql
-- Failed auth attempts (potential attacks)
SELECT COUNT(*) 
FROM ezkey_auth_attempt 
WHERE auth_attempt_status = 'REJECTED' 
AND created_at > NOW() - INTERVAL '1 hour';

-- Recovery code usage (unusual = investigation needed)
SELECT admin_id, username, 
       ARRAY_LENGTH(recovery_codes, 1) as codes_remaining
FROM ezkey_admin
WHERE ARRAY_LENGTH(recovery_codes, 1) < 10;

-- Active sessions per admin
SELECT a.username, COUNT(t.token_id) as active_tokens
FROM ezkey_admin a
LEFT JOIN ezkey_admin_tokens t ON t.admin_id = a.admin_id
WHERE t.active = true AND t.expires_at > NOW()
GROUP BY a.admin_id, a.username;
```

---

## Troubleshooting

### Problem: "No device enrolled for passwordless authentication"

**Cause:** Admin account exists but enrollment not bound

**Solution:**
```sql
-- Check enrollment status
SELECT a.username, e.enrollment_id, 
       e.device_public_key IS NOT NULL as is_bound
FROM ezkey_admin a
LEFT JOIN ezkey_enrollment e ON e.enrollment_id = a.mfa_enrollment_id
WHERE a.username = 'admin';
```

If `is_bound = false`:
1. Get enrollment credentials from bootstrap logs
2. Bind device using Demo Device or Mobile app
3. Retry login

### Problem: "Authentication timeout - no device response"

**Cause:** Device not checking for pending authentications

**Solution:**
1. Open Ezkey app on device
2. Click "Check Pending Authentications"
3. Approve the login request
4. Timeout is 5 minutes - retry if expired

### Problem: "Challenge value mismatch"

**Cause:** Wrong challenge code entered on device

**Solution:**
1. Check challenge code from `/login` response
2. Enter exact code on device (6 digits)
3. Case sensitive: ensure correct digits
4. Auth attempt marked INVALID - create new login request

### Problem: "Invalid recovery code"

**Possible Causes:**
1. Recovery code already used (single-use)
2. Typo in code entry (32 digits, no ambiguous chars)
3. Wrong admin username

**Solution:**
1. Verify code format: 8 groups of 4 digits
2. Try another recovery code (you have 10 total)
3. Check `codesRemaining` in response
4. If all codes exhausted: Contact system administrator

### Problem: Rate limit exceeded

**Cause:** Too many authentication attempts

**Response:**
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 300

{
  "success": false,
  "message": "Rate limit exceeded. Try again in 5 minutes."
}
```

**Solution:**
1. Wait for `Retry-After` duration (seconds)
2. Check for automated systems hitting the endpoint
3. If blocked (10+ failures): Wait 30 minutes
4. Review audit logs for suspicious activity

---

## Security Comparison

### Traditional Password-Based Auth

```
❌ Passwords can be stolen (phishing, database breach)
❌ Passwords can be guessed (brute force, dictionary)
❌ Password reuse across services
❌ Users forget passwords
❌ Weak passwords chosen for convenience
⚠️ MFA helps but password is still the weak link
```

### Ezkey Passwordless-Only Auth

```
✅ No password to steal (nothing stored in database)
✅ Cannot be guessed (106-bit recovery codes, cryptographic keys)
✅ No password reuse (device-bound credentials)
✅ Cannot be forgotten (device has key, recovery codes as backup)
✅ Strong by default (Ed25519 cryptography)
✅ Phishing resistant (cryptographic challenge-response)
```

**Threat Model Comparison:**

| Attack Vector | Password+MFA | Passwordless | Protection |
|---------------|--------------|--------------|------------|
| **Phishing** | ⚠️ Vulnerable | ✅ Immune | No secrets to steal |
| **Brute Force** | ⚠️ Possible | ✅ Impossible | No password to guess |
| **Database Breach** | ❌ Game over | ✅ No impact | No passwords stored |
| **Device Theft** | ⚠️ SMS intercept | ⚠️ Biometric | Recovery codes needed |
| **Man-in-Middle** | ❌ Session hijack | ✅ Signed requests | Cryptographic |
| **Replay Attack** | ❌ Token reuse | ✅ Impossible | One-time proof tokens |

---

## API Reference Summary

### Authentication Endpoints

| Endpoint | Method | Purpose | Auth Required |
|----------|--------|---------|---------------|
| `/admin/auth/login` | POST | Passwordless login | No |
| `/admin/auth/passwordless-wait` | POST | Wait for challenge approval | No |
| `/admin/auth/recover` | POST | Emergency recovery code auth | No |
| `/admin/enrollments/reset` | POST | Unbind lost device | Recovery token |
| `/admin/auth/logout` | POST | Invalidate bearer token | Bearer token |

### Protected Endpoints (Require Bearer Token)

All other Admin API endpoints require bearer token:

```http
GET /api/v1/admin/integrations
Authorization: Bearer ezkey_abc123...
```

---

## Production Deployment Checklist

- [ ] ✅ Migrations V1-V9 executed successfully
- [ ] ✅ Admin zero bootstrap logs captured (enrollment + recovery codes)
- [ ] ✅ Recovery codes printed and stored in secure location
- [ ] ✅ At least one recovery code tested and validated
- [ ] ✅ Device bound successfully (device_public_key set)
- [ ] ✅ Passwordless login tested (both modes)
- [ ] ✅ Challenge-based login tested (if using challenge mode)
- [ ] ✅ Rate limiting verified (test with invalid requests)
- [ ] ✅ Audit logging reviewed (auth attempts, recovery usage)
- [ ] ✅ Monitoring dashboards configured
- [ ] ✅ Incident response plan documented
- [ ] ✅ Team trained on recovery procedures

---

## Migration from Password-Based (If Applicable)

**Note:** As of v2.0, Ezkey Admin API is passwordless-only from initial deployment.

If you have an existing installation with password-based authentication:

1. **DO NOT MIGRATE** - This would require custom tooling
2. **Fresh Install** - Easier to start fresh with passwordless-only
3. **Contact Support** - For enterprise migration assistance

**Migrations V8-V9 handle schema migration but assume fresh deployment.**

---

## Conclusion

Ezkey Admin API demonstrates **passwordless authentication done right**:

1. **Security by Default:** No passwords = no password attacks
2. **User Friendly:** Device approval is intuitive
3. **Enterprise Ready:** Recovery codes, rate limiting, audit trail
4. **Future Proof:** Aligned with FIDO2/WebAuthn standards

**The future is passwordless. Ezkey leads the way.** 🚀

---

**Questions or Issues?**
- GitHub Issues: https://github.com/ezkey/ezkey
- Documentation: `/docs` folder
- Security: See `CRYPTO.md` for cryptographic details
