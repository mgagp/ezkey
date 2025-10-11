# Ezkey Admin API - Security Implementation Guide

**Version:** 1.0  
**Last Updated:** October 2025  
**Audience:** Developers integrating Ezkey  
**Status:** Production Ready

---

## Table of Contents

1. [Overview](#overview)
2. [Security Architecture](#security-architecture)
3. [Initial Setup Workflow](#initial-setup-workflow)
4. [Daily Authentication Workflow](#daily-authentication-workflow)
5. [Token Management](#token-management)
6. [MFA Configuration](#mfa-configuration)
7. [Security Best Practices](#security-best-practices)
8. [Troubleshooting](#troubleshooting)

---

## Overview

The Ezkey Admin API implements a **multi-layered security approach** combining:
- **Password-based authentication** (BCrypt hashing)
- **Multi-Factor Authentication (MFA)** using Ezkey's own MFA system
- **Bearer token** management with automatic rotation
- **Temporary tokens** for MFA flows
- **Rate limiting** to prevent brute force attacks

This guide walks you through the complete security lifecycle from initial deployment to daily operations.

---

## Security Architecture

### Components Overview

```mermaid
flowchart TB
    subgraph "Administrator"
        A[Admin User]
    end
    
    subgraph "Ezkey Admin API :9080"
        B[Login Endpoint]
        C[Password Change]
        D[MFA Endpoints]
        E[Protected Resources]
    end
    
    subgraph "Ezkey Auth API :8080"
        F[Enrollment Binding]
        G[Auth Attempt]
    end
    
    subgraph "Security Layer"
        H[Rate Limiter]
        I[Token Manager]
        J[MFA Validator]
    end
    
    subgraph "Storage"
        K[(Database)]
    end
    
    subgraph "Device"
        L[Demo Device / Mobile]
    end
    
    A -->|1. Login| H
    H --> B
    B -->|2. Issue Token| I
    I --> K
    
    A -->|3. Change Password| C
    C -->|4. Generate Temp Token| D
    
    A -->|5. MFA Attempt| D
    D -->|6. Create Challenge| K
    
    L -->|7. Approve| G
    G -->|8. Validate| J
    
    D -->|9. Issue Bearer Token| I
    I -->|10. Access| E
    
    style H fill:#ffcccc
    style I fill:#ccffcc
    style J fill:#ccccff
```

### Security Layers

| Layer | Component | Purpose |
|-------|-----------|---------|
| **1. Authentication** | Password + MFA | Verify identity |
| **2. Authorization** | Bearer Tokens | Access control |
| **3. Rate Limiting** | IP-based limits | Prevent brute force |
| **4. Token Rotation** | Automatic invalidation | Limit token lifetime |
| **5. Audit** | Login tracking | Security monitoring |

---

## Initial Setup Workflow

### Step 1: Application Bootstrap

When Ezkey Admin API starts for the first time, it automatically executes a **bootstrap process**:

```mermaid
sequenceDiagram
    participant App as Admin API
    participant Bootstrap as Bootstrap Service
    participant DB as Database
    participant Log as Console Logs
    
    Note over App,Log: First Startup
    
    App->>Bootstrap: Application Ready Event
    Bootstrap->>DB: Check Integration Zero exists?
    
    alt Integration Zero NOT exists
        Bootstrap->>DB: Create System Tenant
        Bootstrap->>DB: Create Admin Zero (username: admin)
        Bootstrap->>DB: Generate temp password
        Bootstrap->>Log: ⚠️ ADMIN ZERO CREATED
        Bootstrap->>Log: 📝 Username: admin
        Bootstrap->>Log: 📝 Password: [generated-password]
        Bootstrap->>Log: ⚠️ SAVE THESE CREDENTIALS NOW!
        
        Bootstrap->>DB: Create Integration Zero (RSA keys)
        Bootstrap->>DB: Create Enrollment Zero
        Bootstrap->>Log: 📱 ENROLLMENT CREDENTIALS:
        Bootstrap->>Log: 📝 Enrollment ID: 1
        Bootstrap->>Log: 📝 Proof Token: [token]
        Bootstrap->>Log: 📝 Challenge: [6-digit-code]
        Bootstrap->>Log: 🔗 Bind with demo-device or CLI
    else Integration Zero exists
        Bootstrap->>Log: ✅ System already initialized
    end
```

**Console Output Example:**
```
================================================================================
📱 ADMIN ZERO MFA ENROLLMENT - SAVE THESE CREDENTIALS NOW!
================================================================================

✅ Integration Zero created: Ezkey System Admin
✅ Enrollment Zero created: Admin MFA

🔐 ADMIN ZERO LOGIN:
   Username: admin
   Password: kJ9mP2xL8qN4wR7vY3hF

🔐 ENROLLMENT CREDENTIALS:
   Enrollment ID: 1
   Enrollment Proof Token: abc123-def456-ghi789-jkl012
   Challenge Code: 542891

🔗 BIND OPTIONS:

   Option A - CLI (Recommended):
     ezkey admin enroll bind \
       --enrollment-id 1 \
       --enrollment-proof-token "abc123-def456-ghi789-jkl012"

   Option B - Demo-Device:
     1. Start: cd ezkey-demo-device && mvn spring-boot:run
     2. Open: http://localhost:8082
     3. Navigate: 'Bind Enrollment'
     4. Enter credentials above

⚠️  SECURITY NOTICE:
   - These credentials are logged ONCE at startup
   - Save them in a secure password manager
   - You cannot retrieve them again without database access

================================================================================
```

**⚠️ Critical Actions:**
1. **Save admin password immediately** - Copy it to a secure password manager
2. **Save enrollment credentials** - You'll need them for MFA setup
3. **Change password on first login** - This is enforced by the system

---

### Step 2: First Login (Password Change Required)

```mermaid
sequenceDiagram
    participant Admin as Administrator
    participant API as Admin API
    participant DB as Database
    
    Note over Admin,DB: First Login Attempt
    
    Admin->>API: POST /api/v1/admin/auth/login
    Note right of Admin: {username: "admin"<br/>password: "generated-password"}
    
    API->>DB: Validate credentials
    DB-->>API: ✅ Valid (passwordChangeRequired=true)
    
    API->>DB: Generate Bearer Token A
    DB-->>API: Token A created
    
    API-->>Admin: {success: true,<br/>token: "ezkey_abc123...",<br/>passwordChangeRequired: true,<br/>message: "Password change required"}
    
    Note over Admin: Bearer Token A received<br/>⚠️ Must change password
```

**Request:**
```http
POST http://localhost:9080/api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "kJ9mP2xL8qN4wR7vY3hF"
}
```

**Response:**
```json
{
  "success": true,
  "token": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-10T10:00:00",
  "passwordChangeRequired": true,
  "message": "Authentication successful - Password change required"
}
```

**⚠️ Important:**
- Token is issued even with `passwordChangeRequired=true`
- This token can ONLY be used for `/change-password` endpoint
- After password change, this token is invalidated

---

### Step 3: Change Password (Seamless MFA Flow)

```mermaid
sequenceDiagram
    participant Admin as Administrator
    participant API as Admin API
    participant DB as Database
    
    Note over Admin,DB: Change Password Request
    
    Admin->>API: POST /api/v1/admin/auth/change-password
    Note right of Admin: Authorization: Bearer ezkey_abc123...<br/>{currentPassword, newPassword}
    
    API->>DB: Validate current password
    DB-->>API: ✅ Valid
    
    API->>API: Validate new password strength
    Note right of API: Min 12 chars<br/>Uppercase + lowercase<br/>Digit + special char
    
    API->>DB: Update password hash (BCrypt)
    API->>DB: Set passwordChangeRequired = false
    API->>DB: Invalidate ALL tokens (Token A ❌)
    
    API->>DB: Reload admin with enrollment (JOIN FETCH)
    API->>API: Check shouldRequireMfa(admin)
    
    alt MFA Required (enrollment bound)
        API->>DB: Generate Temp Token B (5 min)
        API-->>Admin: {success: true,<br/>tempToken: "ezkey_temp_xyz...",<br/>mfaRequired: true,<br/>expiresAt: "..."}
        Note over Admin: ✅ Seamless flow!<br/>No re-login needed
    else MFA Not Required
        API-->>Admin: {success: true,<br/>message: "Password changed",<br/>mfaEnrollment: {...}}
        Note over Admin: Enrollment reminder<br/>Must re-login
    end
```

**Request:**
```http
POST http://localhost:9080/api/v1/admin/auth/change-password
Authorization: Bearer ezkey_abc123def456...
Content-Type: application/json

{
  "currentPassword": "kJ9mP2xL8qN4wR7vY3hF",
  "newPassword": "MySecurePassword123!"
}
```

**Response (MFA Required):**
```json
{
  "success": true,
  "message": "Password changed successfully. MFA verification required to complete authentication.",
  "passwordChangeRequired": false,
  "tempToken": "ezkey_temp_xyz789abc...",
  "mfaRequired": true,
  "expiresAt": "2025-10-09T12:52:00"
}
```

**Response (MFA Not Required - Enrollment Not Bound):**
```json
{
  "success": true,
  "message": "Password changed successfully. All existing tokens have been invalidated.",
  "passwordChangeRequired": false,
  "mfaEnrollment": {
    "enrollmentId": 1,
    "enrollmentProofToken": "abc123-def456-ghi789-jkl012",
    "enrollmentChallenge": 542891,
    "bound": false,
    "message": "Use these credentials to bind your MFA enrollment for enhanced security"
  }
}
```

**Password Validation Rules:**
- ✅ Minimum 12 characters
- ✅ At least 1 uppercase letter (A-Z)
- ✅ At least 1 lowercase letter (a-z)
- ✅ At least 1 digit (0-9)
- ✅ At least 1 special character (!@#$%^&*...)
- ✅ Maximum 128 characters (DoS protection)
- ✅ Cannot be same as current password

---

### Step 4: MFA Enrollment Binding (One-Time Setup)

Before you can use MFA, you must bind your enrollment to a device.

#### Option A: Using Demo Device (Development)

```mermaid
sequenceDiagram
    participant Admin as Administrator
    participant Browser as Demo Device<br/>:8082
    participant AuthAPI as Auth API<br/>:8080
    participant DB as Database
    
    Note over Admin,DB: Enrollment Binding Process
    
    Admin->>Browser: Open http://localhost:8082
    Admin->>Browser: Click "Bind Enrollment"
    Admin->>Browser: Enter enrollment credentials
    Note right of Browser: Enrollment ID: 1<br/>Proof Token: abc123...<br/>Challenge: 542891
    
    Browser->>Browser: Generate RSA-2048 Key Pair
    Note right of Browser: Device Private Key<br/>Device Public Key
    
    Browser->>AuthAPI: POST /api/v1/enrollments/bind
    Note right of Browser: {enrollmentId: 1,<br/>enrollmentProofToken: "abc123..."}
    
    AuthAPI->>DB: Validate proof token
    DB-->>AuthAPI: ✅ Valid enrollment
    
    AuthAPI-->>Browser: {integrationName: "Ezkey System Admin",<br/>enrollmentProofToken: "...",<br/>challengeRequired: true}
    
    Browser->>Browser: Sign proof token with device private key
    
    Browser->>AuthAPI: POST /api/v1/enrollments/verify
    Note right of Browser: {enrollmentId: 1,<br/>devicePublicKey: "...",<br/>enrollmentProofTokenSigned: "...",<br/>challengeResponse: 542891}
    
    AuthAPI->>DB: Validate signature
    AuthAPI->>DB: Validate challenge code
    AuthAPI->>DB: Store device public key
    DB-->>AuthAPI: ✅ Enrollment bound
    
    AuthAPI-->>Browser: {active: true}
    Browser-->>Admin: ✅ Enrollment bound successfully!
```

**Demo Device Steps:**

1. **Start demo device:**
   ```bash
   cd ezkey-demo-device
   mvn spring-boot:run
   ```

2. **Open browser:**
   ```
   http://localhost:8082
   ```

3. **Bind enrollment:**
   - Click "Bind Enrollment"
   - Enter Enrollment ID: `1`
   - Enter Proof Token: `abc123-def456-ghi789-jkl012`
   - Enter Challenge Code: `542891`
   - Click "Bind"

4. **Verify success:**
   ```
   ✅ Enrollment bound successfully!
   Device public key registered for enrollment 1
   ```

#### Option B: Using CLI (Production)

```bash
# Install Ezkey CLI (if not already installed)
cd ezkey-cli-python
pip install -r requirements.txt

# Bind enrollment
ezkey admin enroll bind \
  --enrollment-id 1 \
  --enrollment-proof-token "abc123-def456-ghi789-jkl012"

# Follow interactive prompts to:
# 1. Generate device RSA keys
# 2. Enter challenge code (542891)
# 3. Complete binding
```

---

### Step 5: Complete MFA Flow (After Password Change)

If MFA was required after password change, you received a **temp token**. Use it to complete authentication:

```mermaid
sequenceDiagram
    participant Admin as Administrator
    participant AdminAPI as Admin API<br/>:9080
    participant AuthAPI as Auth API<br/>:8080
    participant Device as Demo Device<br/>:8082
    participant DB as Database
    
    Note over Admin,DB: Continue from Step 3 (temp token received)
    
    Admin->>AdminAPI: POST /api/v1/admin/mfa/attempt
    Note right of Admin: {tempToken: "ezkey_temp_xyz..."}
    
    AdminAPI->>DB: Validate temp token (active, not expired)
    DB-->>AdminAPI: ✅ Valid (admin has enrollment)
    
    AdminAPI->>DB: Create AuthAttempt
    Note right of DB: Status: PENDING<br/>Proof token: generated
    
    AdminAPI-->>Admin: {authAttemptId: 123}
    
    Note over Admin: Wait for device approval<br/>(polling or notification)
    
    Device->>AuthAPI: POST /api/v1/auth-attempts/pending
    Note right of Device: {enrollmentId: 1,<br/>enrollmentProofToken: "...",<br/>deviceProofToken: "...",<br/>signed: "..."}
    
    AuthAPI->>DB: Find PENDING auth attempt
    DB-->>AuthAPI: AuthAttempt #123
    
    AuthAPI-->>Device: {authAttemptId: 123,<br/>authAttemptProofToken: "...",<br/>challengeRequired: false}
    
    Device->>Device: Show approval dialog
    Note right of Device: "Ezkey System Admin<br/>requests authentication"
    
    Device->>AuthAPI: POST /api/v1/auth-attempts/respond
    Note right of Device: {authAttemptId: 123,<br/>authAttemptAccepted: true,<br/>authAttemptProofTokenSigned: "..."}
    
    AuthAPI->>DB: Validate signature
    AuthAPI->>DB: Update status: ACCEPTED
    DB-->>AuthAPI: ✅ Approved
    
    AuthAPI-->>Device: {result: "APPROVED"}
    
    Note over Admin: Check attempt status
    
    Admin->>AdminAPI: POST /api/v1/admin/mfa/validate
    Note right of Admin: {tempToken: "ezkey_temp_xyz...",<br/>authAttemptId: 123}
    
    AdminAPI->>DB: Validate temp token
    AdminAPI->>DB: Check attempt status = ACCEPTED
    AdminAPI->>DB: Invalidate temp token (single-use)
    
    AdminAPI->>DB: Generate Bearer Token C (24h)
    
    AdminAPI-->>Admin: {success: true,<br/>token: "ezkey_def456...",<br/>adminType: "GLOBAL_ADMIN",<br/>expiresAt: "..."}
    
    Note over Admin: ✅ Fully authenticated!<br/>Ready for daily operations
```

**Step 5.1 - Create MFA Attempt:**
```http
POST http://localhost:9080/api/v1/admin/mfa/attempt
Content-Type: application/json

{
  "tempToken": "ezkey_temp_xyz789abc..."
}
```

**Response:**
```json
{
  "authAttemptId": 123
}
```

**Step 5.2 - Approve on Device:**

Open demo device at `http://localhost:8082`:
- Click "Check Pending Auth Attempts"
- See request: "Ezkey System Admin requests authentication"
- Click "Approve"

**Step 5.3 - Validate MFA:**
```http
POST http://localhost:9080/api/v1/admin/mfa/validate
Content-Type: application/json

{
  "tempToken": "ezkey_temp_xyz789abc...",
  "authAttemptId": 123
}
```

**Response (Final Bearer Token):**
```json
{
  "success": true,
  "token": "ezkey_def456ghi789...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-10T07:47:00",
  "message": "Authentication successful"
}
```

**✅ Setup Complete!** Admin is now fully configured with MFA.

---

## Daily Authentication Workflow

After initial setup, the daily login process is streamlined:

```mermaid
flowchart TD
    Start([Administrator Needs Access]) --> Login[POST /login<br/>username + password]
    
    Login --> ValidateCreds{Credentials<br/>Valid?}
    ValidateCreds -->|No| LoginFail[401 Unauthorized]
    ValidateCreds -->|Yes| CheckMFA{MFA<br/>Required?}
    
    CheckMFA -->|No - Dev Mode| DirectToken[Issue Bearer Token<br/>24h validity]
    CheckMFA -->|Yes - Prod/Bound| TempToken[Issue Temp Token<br/>5min validity]
    
    TempToken --> CreateAttempt[POST /mfa/attempt<br/>tempToken]
    CreateAttempt --> WaitApproval[Wait for Device Approval]
    
    WaitApproval --> DeviceCheck[Device polls<br/>POST /pending]
    DeviceCheck --> UserDecision{User<br/>Approves?}
    
    UserDecision -->|No| Denied[POST /respond<br/>accepted: false]
    UserDecision -->|Yes| Approved[POST /respond<br/>accepted: true]
    
    Denied --> ValidateFail[POST /validate<br/>Returns 401]
    Approved --> Validate[POST /validate<br/>tempToken + attemptId]
    
    Validate --> FinalToken[Issue Bearer Token<br/>24h validity]
    
    DirectToken --> AccessAPI[Access Protected<br/>Resources]
    FinalToken --> AccessAPI
    
    AccessAPI --> End([Daily Operations])
    LoginFail --> End
    ValidateFail --> End
    
    style Start fill:#e1f5fe
    style DirectToken fill:#c8e6c9
    style TempToken fill:#fff9c4
    style FinalToken fill:#c8e6c9
    style AccessAPI fill:#c8e6c9
    style End fill:#e1f5fe
    style LoginFail fill:#ffcdd2
    style ValidateFail fill:#ffcdd2
```

### Scenario A: MFA Enabled (Production)

**1. Login:**
```http
POST http://localhost:9080/api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "MySecurePassword123!"
}
```

**Response (Temp Token):**
```json
{
  "success": true,
  "tempToken": "ezkey_temp_abc123...",
  "mfaRequired": true,
  "expiresAt": "2025-10-09T13:05:00",
  "message": "MFA verification required",
  "username": "admin"
}
```

**2-4. MFA Flow** (same as Step 5 above)

**Total time:** ~30 seconds (depending on device approval)

### Scenario B: MFA Disabled (Development)

**1. Login:**
```http
POST http://localhost:9080/api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "MySecurePassword123!"
}
```

**Response (Direct Bearer Token):**
```json
{
  "success": true,
  "token": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-10T13:00:00",
  "message": "Authentication successful"
}
```

**Total time:** <1 second

---

## Token Management

### Token Types

```mermaid
graph TB
    subgraph "Token Lifecycle"
        A[Bearer Token] -->|24 hours| B[Expires]
        C[Temp Token] -->|5 minutes| D[Expires]
        
        A -->|Used| E[Active]
        E -->|Login again| F[Rotated - Old Invalid]
        F -->|New token| A
        
        C -->|MFA validate| G[Single-Use]
        G -->|Invalidated| D
        
        A -->|Password change| H[All Invalidated]
        H -->|New token| A
    end
    
    style A fill:#c8e6c9
    style C fill:#fff9c4
    style B fill:#ffcdd2
    style D fill:#ffcdd2
    style F fill:#ffcdd2
    style G fill:#ffcdd2
    style H fill:#ffcdd2
```

### Bearer Token Characteristics

| Property | Value | Notes |
|----------|-------|-------|
| **Prefix** | `ezkey_` | Identifies Ezkey tokens |
| **Validity** | 24 hours | Configurable |
| **Format** | `ezkey_[UUID without dashes]` | 256-bit entropy |
| **Storage** | Database (active flag) | Can be invalidated server-side |
| **Rotation** | On login (optional) | Configured via `ezkey.admin.token.rotation-on-login` |
| **Scope** | Full API access | All endpoints |

**Using Bearer Token:**
```http
GET http://localhost:9080/api/v1/admin/integrations
Authorization: Bearer ezkey_abc123def456ghi789...
```

### Temporary Token Characteristics

| Property | Value | Notes |
|----------|-------|-------|
| **Prefix** | `ezkey_temp_` | Distinguishes from bearer tokens |
| **Validity** | 5 minutes | Short-lived for security |
| **Format** | `ezkey_temp_[UUID without dashes]` | 256-bit entropy |
| **Usage** | Single-use | Invalidated after MFA validation |
| **Purpose** | MFA flow only | Cannot access other endpoints |
| **Generation** | Login or password change | When MFA required |

**Using Temp Token:**
```http
POST http://localhost:9080/api/v1/admin/mfa/attempt
Content-Type: application/json

{
  "tempToken": "ezkey_temp_xyz789abc..."
}
```

### Token Rotation

**Configuration:**
```properties
# Enable automatic token rotation on login (recommended)
ezkey.admin.token.rotation-on-login=true
```

**Behavior:**
- When enabled: All existing active tokens are deactivated on new login
- Ensures only one active token per admin at any time
- Limits attack surface if token is stolen
- Invalidates stolen tokens on next legitimate login

**Rotation Triggers:**
1. New login (if rotation enabled)
2. Password change (always)
3. Manual logout

---

## MFA Configuration

### MFA Modes

```mermaid
graph LR
    subgraph "Dev Mode (default)"
        A[MFA Optional] --> B{Admin<br/>Choice}
        B -->|Enabled| C[MFA Required]
        B -->|Disabled| D[Password Only]
    end
    
    subgraph "Prod Mode (recommended)"
        E[MFA Required] --> F{Enrollment<br/>Bound?}
        F -->|Yes| G[MFA Enforced]
        F -->|No| H[Allow but Log Warning]
    end
    
    style C fill:#c8e6c9
    style G fill:#c8e6c9
    style D fill:#fff9c4
    style H fill:#fff9c4
```

**Configuration:**
```properties
# Development mode: MFA optional
ezkey.admin.mfa.mode=dev

# Production mode: MFA required
ezkey.admin.mfa.mode=prod
```

### MFA Decision Logic

```java
// Pseudo-code for shouldRequireMfa()
if (passwordChangeRequired) {
    return false; // Allow login to change password
}

boolean enrollmentBound = (admin.mfaEnrollment != null 
    && admin.mfaEnrollment.devicePublicKey != null);

if (mode == "prod") {
    // Production: MFA required if enrollment bound
    return enrollmentBound;
} else {
    // Dev: Check admin preferences
    return enrollmentBound 
        && admin.mfaEnabled 
        && admin.mfaRequired;
}
```

### Admin MFA Flags

Each admin has three MFA-related flags in the database:

| Flag | Type | Purpose | Default |
|------|------|---------|---------|
| `mfa_enabled` | Boolean | Admin has MFA capability | `true` |
| `mfa_required` | Boolean | Admin must use MFA | `true` |
| `mfa_enrollment_id` | Integer | Linked enrollment | `null → 1` |

**Updating flags (SQL):**
```sql
-- Disable MFA for specific admin (dev mode only)
UPDATE ezkey_admin 
SET mfa_required = false 
WHERE username = 'admin';

-- Re-enable MFA
UPDATE ezkey_admin 
SET mfa_required = true 
WHERE username = 'admin';
```

---

## Security Best Practices

### 1. Password Management

✅ **DO:**
- Use strong, unique passwords (12+ characters)
- Store passwords in a secure password manager
- Change password immediately after first login
- Use different passwords for dev/staging/prod environments

❌ **DON'T:**
- Reuse passwords across environments
- Share passwords via insecure channels (email, chat)
- Use common passwords or patterns
- Store passwords in plain text files

### 2. Token Security

✅ **DO:**
- Store bearer tokens securely (encrypted storage, secure cookies)
- Use HTTPS in production (tokens in HTTP headers)
- Implement token rotation on login
- Set appropriate token expiration times
- Clear tokens on logout

❌ **DON'T:**
- Store tokens in local storage (XSS vulnerability)
- Log bearer tokens (use `[PROTECTED]` placeholder)
- Share tokens between users or services
- Use tokens after logout

### 3. MFA Configuration

✅ **DO:**
- Enable MFA in production (`mode=prod`)
- Bind enrollment to a secure device
- Use demo-device only in development
- Protect enrollment credentials (proof token, challenge)
- Test MFA flow in staging before production

❌ **DON'T:**
- Disable MFA in production
- Share enrollment credentials
- Use same enrollment across multiple admins
- Bind to untrusted devices

### 4. Rate Limiting

**Current Configuration:**
```properties
# Login endpoint protection
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=5

# IP blocking after repeated failures
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30
```

**Monitoring:**
- Watch for `429 Too Many Requests` responses
- Review blocked IPs in logs
- Adjust limits based on legitimate usage patterns

### 5. Audit and Monitoring

**Key Metrics to Monitor:**
- Failed login attempts (potential brute force)
- Password change frequency (potential compromise)
- MFA approval/denial ratio (suspicious activity)
- Token rotation frequency
- Active token count per admin

**Log Analysis:**
```bash
# Find failed login attempts
grep "Authentication failed" admin-api.log

# Find password changes
grep "Password changed successfully" admin-api.log

# Find MFA approvals
grep "MFA approved" admin-api.log

# Find rate limit violations
grep "429 Too Many Requests" admin-api.log
```

---

## Troubleshooting

### Issue 1: "Invalid credentials" on First Login

**Symptoms:**
```json
{
  "success": false,
  "message": "Invalid credentials"
}
```

**Causes:**
1. Incorrect password (typo)
2. Username incorrect (not "admin")
3. Password already changed and you're using old password

**Resolution:**
```bash
# Check console logs for initial password
grep "ADMIN ZERO CREATED" logs/admin-api.log

# Reset password in database (emergency only)
psql -d ezkey_db -c "
UPDATE ezkey_admin 
SET password_hash = '\$2a\$10\$...', 
    password_change_required = true 
WHERE username = 'admin';
"
```

---

### Issue 2: Bearer Token After Password Change (Expected Temp Token)

**Symptoms:**
- Change password successful
- Received bearer token instead of temp token
- MFA not triggered

**Causes:**
1. MFA not configured (`mfa_required=false`)
2. Enrollment not bound yet
3. Dev mode with MFA disabled

**Resolution:**
```sql
-- Check admin MFA configuration
SELECT username, mfa_enabled, mfa_required, mfa_enrollment_id 
FROM ezkey_admin 
WHERE username = 'admin';

-- Check enrollment status
SELECT e.enrollment_id, e.enrollment_name, 
       e.device_public_key IS NOT NULL as bound
FROM ezkey_enrollment e
WHERE e.enrollment_id = 1;

-- Enable MFA if needed
UPDATE ezkey_admin 
SET mfa_enabled = true, mfa_required = true 
WHERE username = 'admin';
```

---

### Issue 3: Temp Token Expired

**Symptoms:**
```json
{
  "success": false,
  "message": "Temp token expired"
}
```

**Causes:**
- Took longer than 5 minutes to complete MFA flow
- Temp token already used (single-use)

**Resolution:**
1. Re-login to get new temp token
2. Complete MFA flow within 5 minutes
3. Check system clock synchronization

---

### Issue 4: MFA Attempt Not Found on Device

**Symptoms:**
- Created MFA attempt successfully
- Device doesn't show pending attempt

**Causes:**
1. Wrong enrollment ID
2. Device not polling correctly
3. Enrollment not bound to device

**Resolution:**
```bash
# Check enrollment binding
curl http://localhost:8080/api/v1/enrollments/bind \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "enrollmentId": 1,
    "enrollmentProofToken": "abc123-def456-ghi789-jkl012"
  }'

# Check pending attempts in database
psql -d ezkey_db -c "
SELECT auth_attempt_id, enrollment_id, 
       auth_attempt_read, auth_attempt_responded 
FROM ezkey_auth_attempt 
WHERE enrollment_id = 1 
ORDER BY created_at DESC LIMIT 5;
"
```

---

### Issue 5: Rate Limit Exceeded

**Symptoms:**
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 300

Too many login attempts. Please try again later.
```

**Causes:**
- Multiple failed login attempts (>5 in 5 minutes)
- Automated scripts hitting login endpoint
- IP blocked after 10 consecutive failures

**Resolution:**
```bash
# Wait for rate limit window to expire (5 minutes)
# Or clear rate limit cache (dev only)

# Check blocked IPs
grep "Rate limit exceeded" logs/admin-api.log

# Adjust rate limits in production (application.properties)
ezkey.admin.rate-limit.login.requests=10
ezkey.admin.rate-limit.login.window-minutes=10
```

---

## Configuration Reference

### Complete Security Configuration

```properties
# ==============================================
# Admin API Security Configuration
# ==============================================

# Server
server.port=9080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/ezkey_db
spring.datasource.username=postgres
spring.datasource.password=ezkey

# Rate Limiting
ezkey.admin.rate-limit.enabled=true
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=5
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30

# Token Management
ezkey.admin.token.cleanup.enabled=true
ezkey.admin.token.cleanup.schedule=0 0 * * * *
ezkey.admin.token.rotation-on-login=true

# MFA Configuration
ezkey.admin.mfa.bootstrap.enabled=true
ezkey.admin.mfa.mode=dev
ezkey.admin.mfa.bootstrap.auto-enrollment=true

# Organization
ezkey.organization.name=Ezkey System
ezkey.organization.description=Default system tenant

# Logging
logging.level.org.ezkey=INFO
logging.level.org.ezkey.admin.service.AdminAuthService=DEBUG
```

### Environment-Specific Settings

#### Development
```properties
ezkey.admin.mfa.mode=dev
ezkey.admin.mfa.bootstrap.auto-enrollment=true
ezkey.admin.token.rotation-on-login=false
logging.level.org.ezkey=DEBUG
```

#### Staging
```properties
ezkey.admin.mfa.mode=prod
ezkey.admin.mfa.bootstrap.auto-enrollment=false
ezkey.admin.token.rotation-on-login=true
logging.level.org.ezkey=INFO
```

#### Production
```properties
ezkey.admin.mfa.mode=prod
ezkey.admin.mfa.bootstrap.auto-enrollment=false
ezkey.admin.token.rotation-on-login=true
logging.level.org.ezkey=WARN
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}
```

---

## Quick Reference

### Common API Endpoints

| Endpoint | Method | Purpose | Auth Required |
|----------|--------|---------|---------------|
| `/api/v1/admin/auth/login` | POST | Initial authentication | No |
| `/api/v1/admin/auth/logout` | POST | Invalidate token | Bearer Token |
| `/api/v1/admin/auth/change-password` | POST | Change password | Bearer Token |
| `/api/v1/admin/mfa/attempt` | POST | Create MFA challenge | Temp Token |
| `/api/v1/admin/mfa/validate` | POST | Complete MFA flow | Temp Token |
| `/api/v1/admin/integrations` | GET | Access protected resource | Bearer Token |

### Response Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Continue normal flow |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Check request format/validation |
| 401 | Unauthorized | Check credentials or token |
| 403 | Forbidden | Check permissions |
| 404 | Not Found | Check resource exists |
| 429 | Too Many Requests | Wait and retry (check Retry-After header) |
| 500 | Server Error | Check server logs |

### Useful Commands

```bash
# Start Admin API
cd ezkey-admin-api && mvn spring-boot:run

# Start Demo Device
cd ezkey-demo-device && mvn spring-boot:run

# Check database
psql -d ezkey_db -c "SELECT * FROM ezkey_admin WHERE username = 'admin';"

# View recent logins
psql -d ezkey_db -c "
SELECT username, last_login_at, mfa_enabled 
FROM ezkey_admin 
ORDER BY last_login_at DESC;
"

# Check active tokens
psql -d ezkey_db -c "
SELECT admin_id, bearer_token, expires_at, active 
FROM ezkey_admin_token 
WHERE active = true 
ORDER BY created_at DESC;
"
```

---

## Summary

The Ezkey Admin API provides **enterprise-grade security** with:

✅ **Multi-layered authentication** (password + MFA)  
✅ **Automatic bootstrap** (zero manual setup)  
✅ **Seamless MFA flow** (no re-login after password change)  
✅ **Token management** (rotation, expiration, invalidation)  
✅ **Rate limiting** (brute force protection)  
✅ **Audit trail** (comprehensive logging)  

**Time to Production:**
- Initial setup: ~5 minutes
- MFA configuration: ~3 minutes
- Daily login: ~30 seconds (with MFA)

**Support:**
- Documentation: `docs/`
- Issues: GitHub Issues
- Security: Report privately to security@ezkey.org

---

*Document Version: 1.0*  
*Last Updated: October 2025*  
*Maintained by: Ezkey Team*

