# Admin MFA - Workflow Sécurité Complet

**Version:** 1.0  
**Date:** 2025-10-03  
**Status:** ✅ Validé et Approuvé

---

## 🎯 Vision Sécurité

Flux initial bootstrap admin global MFA est **critique** pour la sécurité d'Ezkey. Ce document définit le workflow complet avec les améliorations de sécurité validées.

---

## 🔒 Améliorations Sécurité Validées

### 1. Protection Anti-Énumération (Bind Endpoint)

**Problème résolu:** Ancien endpoint `GET /enrollments/bind/{enrollmentId}` vulnérable à l'énumération.

**Solution:** Nouveau endpoint sécurisé avec `enrollmentProofToken` requis.

```http
POST /api/v1/enrollments/bind
Content-Type: application/json

{
  "enrollmentId": 1,
  "enrollmentProofToken": "abc123-def456-ghi789"  // ✅ Requis pour authentification
}
```

**Bénéfices:**
- ✅ Impossible d'énumérer les enrollments sans token valide
- ✅ Rate limiting: 5 requêtes/10 minutes
- ✅ Audit trail complet
- ✅ Même réponse d'erreur (pas de leak d'info)

**Référence:** `docs/features/BIND_ENUM_PROTECTION.md`

---

### 2. Logs Bootstrap Hautement Visibles

**Problème résolu:** Credentials perdues dans les logs normaux, admin peut manquer les infos critiques.

**Solution:** Logs avec séparateurs visuels et instructions complètes.

```java
private void logAdminZeroEnrollmentCredentials(Enrollment enrollment) {
    String separator = "=".repeat(80);
    
    logger.warn(""); // Blank line for visibility
    logger.warn(separator);
    logger.warn("📱 ADMIN ZERO MFA ENROLLMENT - SAVE THESE CREDENTIALS NOW!");
    logger.warn(separator);
    logger.warn("");
    logger.warn("✅ Integration Zero created: {}", enrollment.getIntegration().getIntegrationName());
    logger.warn("✅ Enrollment Zero created: {}", enrollment.getEnrollmentName());
    logger.warn("");
    logger.warn("🔐 ENROLLMENT CREDENTIALS:");
    logger.warn("   Enrollment ID: {}", enrollment.getEnrollmentId());
    logger.warn("   Enrollment Proof Token: {}", enrollment.getEnrollmentProofToken());
    logger.warn("");
    logger.warn("🔗 BIND OPTIONS:");
    logger.warn("");
    logger.warn("   Option A - CLI (Recommended):");
    logger.warn("     ezkey admin enroll bind \\");
    logger.warn("       --enrollment-id {} \\", enrollment.getEnrollmentId());
    logger.warn("       --enrollment-proof-token \"{}\"", enrollment.getEnrollmentProofToken());
    logger.warn("");
    logger.warn("   Option B - Demo-Device:");
    logger.warn("     1. Start: cd ezkey-demo-device && mvn spring-boot:run");
    logger.warn("     2. Open: http://localhost:8082");
    logger.warn("     3. Navigate: 'Bind Enrollment'");
    logger.warn("     4. Enter:");
    logger.warn("        - Enrollment ID: {}", enrollment.getEnrollmentId());
    logger.warn("        - Enrollment Proof Token: {}", enrollment.getEnrollmentProofToken());
    logger.warn("");
    logger.warn("⚠️  SECURITY NOTICE:");
    logger.warn("   - These credentials are logged ONCE at startup");
    logger.warn("   - Save them in a secure password manager");
    logger.warn("   - Use 'ezkey admin status' CLI command to view enrollment info after login");
    logger.warn("");
    logger.warn(separator);
    logger.warn("");
}
```

**Bénéfices:**
- ✅ Impossible de manquer dans les logs
- ✅ Instructions complètes immédiatement disponibles
- ✅ Format copier-coller pour CLI
- ✅ Avertissements sécurité clairs

---

### 3. API Change-Password avec Rappel Enrollment

**Problème résolu:** Admin peut oublier de bind enrollment après changement de password.

**Solution:** Response inclut enrollment info comme rappel.

```json
{
  "success": true,
  "message": "Password changed successfully",
  "passwordChangeRequired": false,
  "mfaEnrollment": {
    "enrollmentId": 1,
    "enrollmentProofToken": "abc123-def456-ghi789",
    "bound": false,
    "message": "Don't forget to bind your MFA enrollment for enhanced security"
  }
}
```

**Bénéfices:**
- ✅ Rappel actif pour bind enrollment
- ✅ Credentials disponibles après password change
- ✅ UX guidée vers sécurité maximale

---

### 4. CLI Status Command avec Enrollment Info

**Problème résolu:** Admin perd les credentials enrollment, pas de moyen de les récupérer.

**Solution:** `ezkey admin status` affiche enrollment info si non bound.

```bash
$ ezkey admin status

Admin Status:
  Username: admin
  Logged in: Yes
  Token expires: 2025-10-04 10:00:00 (in 23h 45m)
  Admin type: GLOBAL_ADMIN
  
MFA Enrollment:
  Status: NOT BOUND ⚠️
  Enrollment ID: 1
  Enrollment Proof Token: abc123-def456-ghi789-012345
  
  💡 To bind your enrollment:
     ezkey admin enroll bind \
       --enrollment-id 1 \
       --enrollment-proof-token "abc123-def456-ghi789-012345"
```

**Bénéfices:**
- ✅ Admin peut toujours récupérer credentials
- ✅ Instructions claires pour binding
- ✅ Status enrollment visible

---

## 🔄 Workflow Complet: Fresh Install → Premier Login MFA

### Étape 1: Application Startup (Bootstrap)

```
┌─────────────────────────────────────────────────────────┐
│ APPLICATION STARTUP                                      │
└─────────────────────────────────────────────────────────┘
  ↓
  Flyway migrations (V1-V5)
    - Create schema
    - Create system tenant
    - Create admin zero (placeholder password)
  ↓
  AdminPasswordInitializationService:
    - Detect placeholder password
    - Generate secure random password
    - LOG PASSWORD (highly visible, warn level)
  ↓
  AdminBootstrapService:
    - Check Integration Zero exists? → No
    - Create Integration Zero (RSA-2048 keys)
    - Create Enrollment Zero (if auto-enrollment=true)
    - LOG ENROLLMENT CREDENTIALS (highly visible, warn level)
  ↓
  ⚠️  LOGS AFFICHENT (séparateur 80 chars):
  ════════════════════════════════════════════════════════════════════════════════
  ⚠️  ADMIN ZERO INITIAL CREDENTIALS
  ════════════════════════════════════════════════════════════════════════════════
  
  🔐 Username: admin
  🔐 Password: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
     ⚠️  CHANGE THIS PASSWORD IMMEDIATELY!
  
  📱 MFA Enrollment Created:
     Enrollment ID: 1
     Enrollment Proof Token: abc123-def456-ghi789-012345
     
  🔗 BIND OPTIONS:
     
     Option A - CLI (Recommended):
       ezkey admin enroll bind \
         --enrollment-id 1 \
         --enrollment-proof-token "abc123-def456-ghi789-012345"
     
     Option B - Demo-Device:
       [instructions complètes]
  
  ⚠️  SAVE THESE CREDENTIALS NOW!
  ⚠️  THEY WILL NOT BE DISPLAYED AGAIN (except via CLI status)!
  ════════════════════════════════════════════════════════════════════════════════
  ↓
  ✅ Application ready
  ✅ Admin doit sauvegarder: username, password, enrollmentId, enrollmentProofToken
```

---

### Étape 2: Admin Premier Login (Password Temporaire)

```
┌─────────────────────────────────────────────────────────┐
│ ADMIN: PREMIER LOGIN                                     │
└─────────────────────────────────────────────────────────┘
  ↓
  POST /api/v1/admin/auth/login
  {
    "username": "admin",
    "password": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
  }
  ↓
  AdminAuthService.authenticate():
    1. validateCredentials() → ✅ Valid
    2. Check passwordChangeRequired? → ✅ TRUE
    3. Return rejection with message
  ↓
  Response (400 Bad Request):
  {
    "success": false,
    "passwordChangeRequired": true,
    "message": "Password change required. Use /change-password endpoint."
  }
  ↓
  ❌ ADMIN CANNOT LOGIN until password changed
  ⚠️  ADMIN DOIT CHANGER PASSWORD
```

---

### Étape 3: Admin Change Password

```
┌─────────────────────────────────────────────────────────┐
│ ADMIN: CHANGE PASSWORD                                   │
└─────────────────────────────────────────────────────────┘
  ↓
  Question: Comment admin peut change password sans token?
  
  Option A: Login donne temp token même avec passwordChangeRequired
  Option B: Endpoint /change-password-first-time sans auth
  
  ✅ CHOIX: Option A (plus sécurisé, admin prouve identité)
  ↓
  POST /api/v1/admin/auth/login (donne temp token special)
  ↓
  POST /api/v1/admin/auth/change-password
  Authorization: Bearer {temp-token-from-first-login}
  {
    "currentPassword": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    "newPassword": "MySecurePassword123!"
  }
  ↓
  AdminAuthService.changePassword():
    1. Validate current password → ✅ Correct
    2. Validate new password strength → ✅ Strong
    3. Hash new password (BCrypt)
    4. Update ezkey_admin:
       - password_hash = new_hash
       - password_change_required = false
    5. Invalidate ALL existing tokens (rotation forcée)
    6. Log action for audit
  ↓
  Response (200 OK):
  {
    "success": true,
    "message": "Password changed successfully",
    "passwordChangeRequired": false,
    "mfaEnrollment": {
      "enrollmentId": 1,
      "enrollmentProofToken": "abc123-def456-ghi789-012345",
      "bound": false,
      "message": "Don't forget to bind your MFA enrollment for enhanced security"
    }
  }
  ↓
  ✅ PASSWORD CHANGED
  ✅ ADMIN A RAPPEL ENROLLMENT CREDENTIALS
```

---

### Étape 4: Admin Login avec Nouveau Password (Dev Mode)

```
┌─────────────────────────────────────────────────────────┐
│ ADMIN: LOGIN AVEC NOUVEAU PASSWORD                      │
└─────────────────────────────────────────────────────────┘
  ↓
  POST /api/v1/admin/auth/login
  {
    "username": "admin",
    "password": "MySecurePassword123!"
  }
  ↓
  AdminAuthService.authenticate():
    1. validateCredentials() → ✅ Valid
    2. Check passwordChangeRequired? → ✅ FALSE (OK)
    3. Check shouldRequireMfa()?
       - mode = "dev" (from config)
       - admin.mfaEnabled = true
       - admin.mfaRequired = true
       - BUT enrollment not bound yet
       → Decision: Dev mode + enrollment not bound = Skip MFA
    4. Generate bearer token (24h)
    5. Update last_login_at
  ↓
  Response (200 OK):
  {
    "success": true,
    "bearerToken": "ezkey_abc123...",
    "adminType": "GLOBAL_ADMIN",
    "username": "admin",
    "expiresAt": "2025-10-04T10:00:00Z",
    "message": "Authentication successful",
    "mfaEnrollment": {
      "enrollmentId": 1,
      "bound": false,
      "message": "Consider binding MFA enrollment for enhanced security"
    }
  }
  ↓
  ✅ ADMIN LOGGÉ (Dev Mode - MFA pas encore obligatoire)
  ⚠️  ADMIN PEUT maintenant bind enrollment
```

---

### Étape 5: Admin Check Status (Récupération Credentials)

```
┌─────────────────────────────────────────────────────────┐
│ ADMIN: CHECK STATUS (CLI)                               │
└─────────────────────────────────────────────────────────┘
  ↓
  CLI: ezkey admin status
  ↓
  Load local config (~/.ezkey/admin-config.json)
    - bearer_token exists → Use it
  ↓
  GET /api/v1/admin/me (or check local config)
  Authorization: Bearer {bearer-token}
  ↓
  CLI Display:
  
  Admin Status:
    Username: admin
    Logged in: Yes
    Token expires: 2025-10-04 10:00:00 (in 23h 45m)
    Admin type: GLOBAL_ADMIN
    
  MFA Enrollment:
    Status: NOT BOUND ⚠️
    Enrollment ID: 1
    Enrollment Proof Token: abc123-def456-ghi789-012345
    
    💡 To bind your enrollment:
       ezkey admin enroll bind \
         --enrollment-id 1 \
         --enrollment-proof-token "abc123-def456-ghi789-012345"
  ↓
  ✅ ADMIN A TOUTES LES INFOS pour bind
```

---

### Étape 6: Admin Bind MFA Enrollment (CLI)

```
┌─────────────────────────────────────────────────────────┐
│ ADMIN: BIND MFA ENROLLMENT                              │
└─────────────────────────────────────────────────────────┘
  ↓
  CLI: ezkey admin enroll bind \
       --enrollment-id 1 \
       --enrollment-proof-token "abc123-def456-ghi789-012345"
  ↓
  CLI Process:
    1. Check device keys exist?
       → No: Generate RSA-2048 key pair
       → Yes: Load existing keys
    ↓
    2. POST /api/v1/enrollments/bind (Auth-API)
       {
         "enrollmentId": 1,
         "enrollmentProofToken": "abc123-def456-ghi789-012345"
       }
    ↓
    3. Response (200 OK):
       {
         "enrollmentId": 1,
         "enrollmentName": "Admin MFA",
         "enrollmentProofToken": "abc123...",  // For signing
         "integrationName": "Ezkey System Admin",
         "integrationPublicKey": "...",
         "enrollmentStatus": "CREATED"
       }
    ↓
    4. Display enrollment info
    5. Prompt: "Bind this enrollment? [y/N]"
    6. User confirms: y
    ↓
    7. Sign enrollmentProofToken with device private key
       signature = sign(enrollmentProofToken, device_private_key)
    ↓
    8. POST /api/v1/enrollments/verify (Auth-API)
       {
         "enrollmentId": 1,
         "devicePublicKey": "...",
         "enrollmentProofTokenSignedByDevice": signature
       }
    ↓
    9. Response (200 OK):
       {
         "success": true,
         "message": "Enrollment verified successfully"
       }
    ↓
    10. Update ezkey_admin:
        - mfa_enrollment_id = 1
        - enrollment STATUS = BOUND
    ↓
    11. Save enrollment info locally
        (~/.ezkey/admin-config.json)
  ↓
  ✅ ENROLLMENT BOUND
  ✅ DEVICE REGISTERED
  ✅ MFA NOW ACTIVE
```

---

### Étape 7: Admin Login avec MFA (Après Binding)

```
┌─────────────────────────────────────────────────────────┐
│ ADMIN: LOGIN AVEC MFA                                   │
└─────────────────────────────────────────────────────────┘
  ↓
  POST /api/v1/admin/auth/login
  {
    "username": "admin",
    "password": "MySecurePassword123!"
  }
  ↓
  AdminAuthService.authenticate():
    1. validateCredentials() → ✅ Valid
    2. Check passwordChangeRequired? → ✅ FALSE
    3. Check shouldRequireMfa()?
       - mode = "dev"
       - admin.mfaEnabled = true
       - admin.mfaRequired = true
       - admin.mfaEnrollmentId = 1 (bound)
       → Decision: MFA REQUIRED
    4. Generate temp token (5 min)
    5. Save AdminTempToken to DB
  ↓
  Response (200 OK):
  {
    "success": true,
    "tempToken": "ezkey_temp_xyz789...",
    "mfaRequired": true,
    "expiresAt": "2025-10-03T10:05:00Z",
    "message": "MFA verification required"
  }
  ↓
  POST /api/v1/admin/mfa/attempt
  {
    "tempToken": "ezkey_temp_xyz789..."
  }
  ↓
  AdminMfaService.createMfaAttempt():
    1. Validate temp token → ✅ Valid
    2. Get admin from temp token
    3. Get enrollment (admin.mfaEnrollmentId)
    4. Create AuthAttempt via AuthAttemptService
  ↓
  Response (200 OK):
  {
    "authAttemptId": 123,
    "authAttemptProofToken": "ezkey_attempt_abc...",
    "expiresAt": "2025-10-03T10:07:00Z"
  }
  ↓
  CLI Display:
    📱 MFA attempt created: 123
    Waiting for approval on your device...
  ↓
  Demo-Device / Mobile App:
    - Poll GET /api/v1/auth-attempts/pending
    - See attempt ID 123
    - Display: "Admin Login Request"
    - User clicks: Approve
    - Sign authAttemptProofToken with device key
    - POST /api/v1/auth-attempts/{id}/respond
  ↓
  CLI Polling:
    - Every 1s: Check auth attempt status
    - Status changes: pending → accepted
    - CLI Display: "✅ MFA approved"
  ↓
  POST /api/v1/admin/mfa/validate
  {
    "tempToken": "ezkey_temp_xyz789...",
    "authAttemptId": 123
  }
  ↓
  AdminMfaService.validateMfa():
    1. Validate temp token → ✅ Valid
    2. Get auth attempt → ✅ Exists
    3. Check auth_attempt_accepted → ✅ TRUE
    4. Verify attempt belongs to admin's enrollment → ✅ Match
    5. Invalidate temp token (active = false)
    6. Rotate existing tokens (if enabled)
    7. Generate bearer token (24h)
    8. Update last_login_at
  ↓
  Response (200 OK):
  {
    "success": true,
    "bearerToken": "ezkey_def456...",
    "adminType": "GLOBAL_ADMIN",
    "username": "admin",
    "expiresAt": "2025-10-04T10:00:00Z",
    "message": "Authentication successful"
  }
  ↓
  ✅ ADMIN LOGGÉ AVEC MFA
  ✅ BEARER TOKEN VALIDE 24H
  ✅ SÉCURITÉ MAXIMALE
```

---

## 📋 Checklist Sécurité

### Bootstrap (Phase 1)

- ✅ Integration Zero créée automatiquement
- ✅ Enrollment Zero créé automatiquement (dev mode)
- ✅ RSA-2048 keys générées
- ✅ Logs hautement visibles (warn level, séparateurs)
- ✅ enrollmentProofToken inclus dans URL bind
- ✅ Instructions CLI + Demo-Device
- ✅ Avertissements sécurité clairs

### Change Password (Phase 0)

- ✅ API /change-password avec validation strength
- ✅ Response inclut enrollment info (rappel)
- ✅ Invalidation tokens existants (rotation forcée)
- ✅ Flag passwordChangeRequired appliqué au login
- ✅ Audit log des changements password

### CLI Status (Phase 5)

- ✅ Affiche enrollment info si not bound
- ✅ Affiche enrollmentProofToken pour récupération
- ✅ Instructions bind claires
- ✅ Status enrollment visible

### Bind Endpoint (Phase 6)

- ✅ POST /enrollments/bind (pas GET)
- ✅ enrollmentProofToken requis dans body
- ✅ Rate limiting: 5/10min
- ✅ Audit trail complet
- ✅ Pas d'énumération possible

### MFA Flow (Phase 2)

- ✅ Temp token (5 min) pour MFA
- ✅ AuthAttempt via auth-api existant
- ✅ Validation signature device
- ✅ Bearer token après MFA success
- ✅ Cleanup temp tokens (5 min schedule)

---

## 🎯 Commits Stables

| Phase | Commit | Sécurité |
|-------|--------|----------|
| **0** | `feat: Add change-password API with enrollment reminder and enforce requirement` | ✅ Password change obligatoire |
| **1** | `feat: Bootstrap Integration Zero and Enrollment Zero with enhanced logging` | ✅ Logs hautement visibles |
| **2** | `feat: Implement MFA hybrid flow (temp token + validation)` | ✅ MFA flow complet |
| **3** | `test: Add Postman collection for admin MFA flow` | ✅ Tests complets |
| **4** | `test: Validate admin MFA flow with demo-device` | ✅ End-to-end |
| **5** | `feat(cli): Add admin login/logout with MFA support` | ✅ CLI MFA flow |
| **6** | `feat(cli): Add admin enrollment management with secure POST bind endpoint` | ✅ Anti-énumération |
| **7** | `docs: Add comprehensive admin MFA documentation` | ✅ Documentation |

---

## ✅ Validation Finale

**Approche validée par l'équipe Ezkey:**
- ✅ Logs bootstrap hautement visibles
- ✅ enrollmentProofToken dans URL bind (anti-énumération)
- ✅ API change-password avec rappel enrollment
- ✅ CLI status affiche enrollment info
- ✅ Workflow complet sécurisé et guidé
- ✅ Dev mode (MFA optional) + Prod mode (MFA required)

**Prochaine étape:** Implémentation Phase 0 (Change Password API)

---

**Document Version**: 1.0  
**Created**: 2025-10-03  
**Status**: ✅ Validé et Prêt pour Implémentation

