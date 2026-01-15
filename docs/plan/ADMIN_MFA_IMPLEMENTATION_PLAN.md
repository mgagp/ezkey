# Plan d'Implémentation: MFA Ezkey pour Admin API

**Version:** 3.0  
**Date:** 2025-10-03  
**Updated:** 2025-10-06  
**Auteur:** Ezkey Team  
**Statut:** ✅ Phase 0 + Phase 1 + Phase 0.3 Complétées

---

## 📊 Status Phases

| Phase | Description | Status | Date |
|-------|-------------|--------|------|
| **Phase 0** | Change Password API + Prerequisites | ✅ **COMPLÉTÉ** | 2025-10-05 |
| **Phase 1** | Bootstrap Integration Zero + Enrollment Zero | ✅ **COMPLÉTÉ** | 2025-10-06 |
| **Phase 0.3** | Endpoint Blocking (passwordChangeRequired) | ✅ **COMPLÉTÉ** | 2025-10-06 |
| **Phase 2** | MFA Flow Hybride (Password → Temp Token → MFA) | 📋 **TODO** | Future |
| **Phase 3** | MFA Mode Configuration (dev/prod) | 📋 **TODO** | Future |
| **Phase 4** | Tenant + Integration Admin MFA | 📋 **TODO** | Future |
| **Phase 5** | CLI Admin avec MFA Support | 📋 **TODO** | Future |

---

## Vision Globale

Implémenter l'authentification MFA hybride (Password + Ezkey) pour les administrateurs selon le principe "Eat Your Own Dog Food", avec phases progressives testables et commits stables.

### Principe "Eat Your Own Dog Food"

Ezkey utilise sa propre solution MFA pour sécuriser ses APIs administratives, démontrant ainsi la fiabilité et la valeur de la solution.

---

## Architecture Cible

### Flow Admin Login avec MFA

```
1. POST /login {username, password}
   ↓
2. Validate credentials
   ↓
3. Check MFA required?
   ├─ No (dev mode) → Bearer Token (24h)
   └─ Yes → Temp Token (5 min)
            ↓
4. POST /mfa/attempt {tempToken}
   ↓
5. Create AuthAttempt
   ↓
6. Mobile/Demo-Device: Poll → Approve/Deny
   ↓
7. POST /mfa/validate {tempToken, authAttemptId}
   ↓
8. Validate MFA → Bearer Token (24h)
```

### Configuration Modes

**Dev Mode (Default):**
- MFA optional (admin can choose)
- Auto-create enrollment for testing
- Direct bearer token if MFA disabled

**Prod Mode:**
- MFA required for all admins
- Manual enrollment binding
- No direct bearer token

---

## Bootstrap Enrollment Admin Global

### Problème "Chicken and Egg"

L'admin global nécessite un enrollment pour MFA, mais créer un enrollment nécessite une integration, et créer une integration nécessite un admin authentifié.

### Solution: Integration Zero + Enrollment Zero

**Au démarrage de l'application:**

1. **Check Integration Zero exists**
   - Query: `SELECT * FROM ezkey_integration WHERE is_system_integration = true`
   - Si existe: Skip bootstrap
   - Si n'existe pas: Continuer

2. **Create Integration Zero** (toujours)
   ```sql
   INSERT INTO ezkey_integration (
     integration_name,
     integration_description,
     tenant_id,
     is_system_integration,
     created_by_admin_id,
     integration_public_key,
     integration_private_key,
     active
   ) VALUES (
     'Ezkey System Admin',
     'System integration for admin authentication',
     [system_tenant_id],
     true,
     [admin_zero_id],
     [generated_rsa_public_key],
     [encrypted_rsa_private_key],
     true
   );
   ```

3. **Create Enrollment Zero** (si auto-enrollment=true)
   ```sql
   INSERT INTO ezkey_enrollment (
     enrollment_name,
     integration_id,
     enrollment_proof_token,
     created_by_admin_id,
     active
   ) VALUES (
     'Admin MFA',
     [integration_zero_id],
     [generated_proof_token],
     [admin_zero_id],
     true
   );
   
   UPDATE ezkey_admin 
   SET mfa_enrollment_id = [enrollment_id]
   WHERE username = 'admin';
   ```

4. **Log Enrollment URL** (dev mode)
   ```
   ✅ Integration Zero created for admin MFA
   ✅ Enrollment Zero created for admin
   📱 Enrollment URL: ezkey://bind?enrollmentId=1&integrationId=1
   🔗 Use demo-device to bind this enrollment
   ```

### Séquence Complète (Dev Mode)

```
Fresh Install
    ↓
Flyway Migrations (V1-V5)
    ↓
Admin Zero + System Tenant créés
    ↓
Application Startup
    ↓
AdminBootstrapService.bootstrapAdminMfa()
    ↓
Integration Zero créée
    ↓
Enrollment Zero créée (auto-enrollment=true)
    ↓
Logs affichent enrollment URL
    ↓
Admin bind avec demo-device
    ↓
Admin peut login avec MFA ✅
```

---

## Phase 0: Préconditions ✅ **COMPLÉTÉ** (2025-10-05)

### Objectif

Mettre en place les API manquantes nécessaires au flow MFA.

### 0.1 API de Changement de Mot de Passe ✅ **IMPLÉMENTÉ**

**Pourquoi:** Admin zero créé avec password temporaire → besoin de le changer

**Endpoint:** `POST /api/v1/admin/auth/change-password`

**Authentification:** Bearer token requis (admin doit être loggé)

**Request:**
```json
{
  "currentPassword": "generated-password-here",
  "newPassword": "MySecurePassword123!"
}
```

**Validation:**
- Minimum 12 caractères
- Au moins 1 majuscule
- Au moins 1 minuscule
- Au moins 1 chiffre
- Au moins 1 symbole spécial
- Pas dans top 10000 passwords communs (optionnel)

**Response Success (200 OK):**
```json
{
  "success": true,
  "message": "Password changed successfully",
  "passwordChangeRequired": false,
  "mfaEnrollment": {
    "enrollmentId": 1,
    "enrollmentProofToken": "abc123-def456-ghi789",
    "enrollmentChallenge": 542891,
    "bound": false,
    "message": "Use these credentials to bind your MFA enrollment for enhanced security"
  }
}
```

**Note:** La réponse inclut automatiquement les credentials MFA complètes (enrollmentId, proofToken, challenge) si l'admin a un enrollment non-bound. Cela élimine le besoin d'un endpoint GET séparé.

**Response Error (400 Bad Request):**
```json
{
  "success": false,
  "message": "Password does not meet requirements",
  "errors": [
    "Password must be at least 12 characters",
    "Password must contain at least one uppercase letter"
  ]
}
```

**Comportement:**
1. Validate bearer token (admin must be authenticated)
2. Validate current password matches
3. Validate new password meets requirements
4. Hash new password with BCrypt
5. Update password_hash in ezkey_admin
6. Set password_change_required = false
7. Invalidate all existing tokens (forced rotation)
8. Log action for audit (admin_id, action="password_change", timestamp)

**Fichiers à créer:**
- `AdminPasswordChangeRequestDto.java`
- `AdminPasswordChangeResponseDto.java`
- `AdminAuthService.changePassword()` (new method)
- `AdminAuthController.changePassword()` (new endpoint)
- `PasswordValidator.java` (utility class)

### 0.2 Renforcement Password Change Required ✅ **IMPLÉMENTÉ**

**Problème chicken-and-egg:** Admin avec `password_change_required=true` a besoin d'un token pour appeler `/change-password`, mais ne peut pas login.

**Solution implémentée:** Modifier `AdminAuthService.authenticate()`

**Approche Phase 0 (✅ Implémentée):**
```java
public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
    // 1. Validate credentials
    EzkeyAdmin admin = validateCredentials(request);
    
    // 2. Rotate tokens if enabled
    rotateTokensIfEnabled(admin);
    
    // 3. Generate and persist new token
    AdminToken token = generateAndPersistToken(admin);
    
    // 4. Build response with warning if password change required
    AdminLoginResponseDto response = buildSuccessResponse(admin, token);
    
    if (admin.getPasswordChangeRequired()) {
        response.setPasswordChangeRequired(true);
        response.setMessage("Authentication successful - Password change required. " +
            "Please use /change-password endpoint before performing other operations.");
        logger.warn("⚠️  Admin {} logged in with passwordChangeRequired=true", 
            admin.getUsername());
    }
    
    return response;
}
```

**Comportement actuel (Phase 0):**
- ✅ Admin avec `passwordChangeRequired=true` peut login et recevoir un token
- ✅ Response inclut flag `passwordChangeRequired: true` + message d'avertissement
- ✅ Admin peut utiliser ce token pour appeler `/change-password`
- ⚠️ **LIMITATION TEMPORAIRE**: Admin peut aussi utiliser le token pour d'autres endpoints (sera corrigé Phase 0.3)

**Séquence typique:**
```
1. Admin login avec password temporaire → Reçoit token + warning passwordChangeRequired=true
2. Admin appelle /change-password avec ce token → Password changé, passwordChangeRequired=false
3. Admin re-login avec nouveau password → Login normal sans avertissement
```

### 0.3 Blocage Endpoints avec Password Change Required (PHASE FUTURE)

⚠️ **IMPORTANT - À IMPLÉMENTER AVANT PRODUCTION**

**Objectif:** Empêcher l'utilisation de tous les endpoints (sauf `/change-password` et `/logout`) tant que `passwordChangeRequired=true`.

**Problème de sécurité:** 
Actuellement, un admin avec `passwordChangeRequired=true` peut utiliser son token pour accéder à tous les endpoints. Cela réduit la sécurité car il peut continuer avec un password temporaire non changé.

**Solution à implémenter:**

**Option A: Filter/Interceptor (Recommandé)**
```java
@Component
public class PasswordChangeRequiredFilter implements Filter {
    
    private final AdminAuthService authService;
    
    private static final List<String> ALLOWED_ENDPOINTS = List.of(
        "/api/v1/admin/auth/login",
        "/api/v1/admin/auth/logout",
        "/api/v1/admin/auth/change-password"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        
        // Skip filter for allowed endpoints
        if (ALLOWED_ENDPOINTS.stream().anyMatch(path::endsWith)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Extract and validate token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            EzkeyAdmin admin = authService.validateToken(token);
            
            if (admin != null && admin.getPasswordChangeRequired()) {
                // Block request, return 403 Forbidden
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write(
                    "{\"error\":\"Password change required\",\"message\":" +
                    "\"Please change your password using /change-password endpoint before " +
                    "accessing other resources.\"}"
                );
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
}
```

**Option B: Annotation + AOP (Alternative)**
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePasswordChanged {
}

@Aspect
@Component
public class PasswordChangeRequiredAspect {
    
    @Before("@annotation(RequirePasswordChanged)")
    public void checkPasswordChangeRequired(JoinPoint joinPoint) {
        // Check admin passwordChangeRequired and throw exception if true
    }
}
```

**Tests à ajouter (Phase 0.3):**
```java
@Test
public void testBlockedEndpointWithPasswordChangeRequired() {
    // Login avec passwordChangeRequired=true → Get token
    String token = loginAndGetToken("admin", "temp-password");
    
    // Try to access protected endpoint → 403 Forbidden
    ResponseEntity<String> response = restTemplate.exchange(
        "/api/v1/admin/integrations",
        HttpMethod.GET,
        new HttpEntity<>(createAuthHeaders(token)),
        String.class
    );
    
    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertTrue(response.getBody().contains("Password change required"));
}

@Test
public void testAllowedEndpointsWithPasswordChangeRequired() {
    String token = loginAndGetToken("admin", "temp-password");
    
    // /change-password should work → 200 OK
    // /logout should work → 200 OK
}
```

**Commit pour Phase 0.3:**
```
feat(security): Block endpoints when password change required

- Add PasswordChangeRequiredFilter to intercept all requests
- Allow only /login, /logout, /change-password endpoints
- Return 403 Forbidden for other endpoints when passwordChangeRequired=true
- Add comprehensive tests for endpoint blocking
- Update security documentation

Security: Ensures admins cannot use temporary passwords indefinitely
```

**Décision implémentation:**
- ✅ **Phase 0 (Actuel)**: Login permissif avec warning (permet /change-password)
- 🔜 **Phase 0.3 (Avant Prod)**: Filter/Interceptor bloque autres endpoints
- 🔜 **Phase 1+**: MFA workflow avec temp tokens (5 min) pour flow hybride

### Tests Phase 0

**Tests unitaires:**
- ✅ Change password with valid current password → success
- ✅ Change password with invalid current password → 401 Unauthorized
- ✅ Change password with weak new password → 400 Bad Request
- ✅ Password validation: too short → error
- ✅ Password validation: no uppercase → error
- ✅ Password validation: no digit → error
- ✅ Password validation: no special char → error

**Tests intégration:**
- ✅ Login with passwordChangeRequired=true → success with token + warning
- ✅ Change password → passwordChangeRequired=false
- ✅ After password change → login successful without warning
- ✅ Old tokens invalidated after password change
- ✅ Response includes MFA enrollment reminder if applicable

**Status Phase 0:**
- ✅ **0.1**: Change password API implémentée et testée
- ✅ **0.2**: Login permissif avec warning implémenté
- ⏳ **0.3**: Blocage endpoints (PHASE FUTURE - Avant Production)

**Commit:** `feat: Add change-password API with enrollment reminder

- Implement POST /auth/change-password endpoint with bearer token auth
- Add PasswordValidator utility with comprehensive strength checks
- Include MFA enrollment reminder in change-password response
- Login allowed with passwordChangeRequired=true (issues token + warning)
- Invalidate all existing tokens after password change (forced rotation)
- Add comprehensive password policy documentation

Security: 12-char minimum, complexity requirements enforced
Note: Phase 0.3 (endpoint blocking) to be implemented before production`

---

## Phase 1: Bootstrap Integration Zero + Enrollment Zero ✅ **COMPLÉTÉ** (2025-10-06)

### Objectif

Créer automatiquement l'infrastructure MFA pour l'admin global au démarrage.

### 1.1 Créer Integration Zero (Système)

**Concept:** Admin Global a besoin d'une integration pour créer son enrollment

**Service:** `AdminBootstrapService.java` (nouveau fichier)

**Location:** `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java`

**Code complet:**

```java
package org.ezkey.admin.service;

import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.Optional;

import org.ezkey.admin.config.AdminMfaProperties;
import org.ezkey.admin.config.OrganizationProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.service.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for bootstrapping admin MFA infrastructure.
 * <p>
 * This service automatically creates Integration Zero and optionally
 * Enrollment Zero for the admin global at application startup.
 * </p>
 *
 * @since 2025
 */
@Service
public class AdminBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final IntegrationRepository integrationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EzkeyAdminRepository adminRepository;
    private final TenantRepository tenantRepository;
    private final SignatureService signatureService;
    private final AdminMfaProperties mfaProperties;
    private final OrganizationProperties organizationProperties;

    public AdminBootstrapService(
            IntegrationRepository integrationRepository,
            EnrollmentRepository enrollmentRepository,
            EzkeyAdminRepository adminRepository,
            TenantRepository tenantRepository,
            SignatureService signatureService,
            AdminMfaProperties mfaProperties,
            OrganizationProperties organizationProperties) {
        this.integrationRepository = integrationRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.adminRepository = adminRepository;
        this.tenantRepository = tenantRepository;
        this.signatureService = signatureService;
        this.mfaProperties = mfaProperties;
        this.organizationProperties = organizationProperties;
    }

    /**
     * Bootstrap admin MFA infrastructure on application startup.
     * <p>
     * Creates Integration Zero and optionally Enrollment Zero
     * if they don't already exist.
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdminMfa() {
        if (!mfaProperties.getBootstrap().isEnabled()) {
            logger.info("Admin MFA bootstrap disabled by configuration");
            return;
        }

        logger.info("🚀 Starting admin MFA bootstrap...");

        try {
            // 1. Check if Integration Zero already exists
            Optional<Integration> existingIntegration = integrationRepository
                    .findByIsSystemIntegrationAndActiveTrue(true);

            if (existingIntegration.isPresent()) {
                logger.info("✅ Integration Zero already exists (ID: {})", 
                    existingIntegration.get().getIntegrationId());
                
                // Check enrollment if auto-enrollment enabled
                if (mfaProperties.getBootstrap().isAutoEnrollment()) {
                    checkAndCreateEnrollmentZero(existingIntegration.get());
                }
                return;
            }

            // 2. Create Integration Zero
            Integration integrationZero = createIntegrationZero();
            logger.info("✅ Integration Zero created (ID: {})", integrationZero.getIntegrationId());

            // 3. Optionally create Enrollment Zero
            if (mfaProperties.getBootstrap().isAutoEnrollment()) {
                createEnrollmentZero(integrationZero);
            }

        } catch (Exception e) {
            logger.error("❌ Failed to bootstrap admin MFA: {}", e.getMessage(), e);
            throw new RuntimeException("Admin MFA bootstrap failed", e);
        }
    }

    /**
     * Create Integration Zero for admin authentication.
     */
    private Integration createIntegrationZero() {
        logger.info("🔧 Creating Integration Zero...");

        // Get System Tenant
        Tenant systemTenant = tenantRepository
                .findByTenantName(organizationProperties.getName())
                .orElseThrow(() -> new RuntimeException("System tenant not found"));

        // Get Admin Zero
        EzkeyAdmin adminZero = adminRepository
                .findByUsername("admin")
                .orElseThrow(() -> new RuntimeException("Admin zero not found"));

        // Generate RSA key pair
        logger.info("🔐 Generating RSA-2048 key pair for Integration Zero...");
        KeyPair keyPair = signatureService.generateKeyPair();

        // Create Integration Zero
        Integration integrationZero = new Integration();
        integrationZero.setIntegrationName("Ezkey System Admin");
        integrationZero.setIntegrationDescription("System integration for admin authentication");
        integrationZero.setTenant(systemTenant);
        integrationZero.setIsSystemIntegration(true);
        integrationZero.setCreatedByAdmin(adminZero);
        integrationZero.setIntegrationPublicKey(keyPair.getPublic().getEncoded());
        integrationZero.setIntegrationPrivateKey(keyPair.getPrivate().getEncoded());
        integrationZero.setCreatedAt(LocalDateTime.now());
        integrationZero.setActive(true);

        integrationRepository.save(integrationZero);

        return integrationZero;
    }

    /**
     * Check and create Enrollment Zero if it doesn't exist.
     */
    private void checkAndCreateEnrollmentZero(Integration integrationZero) {
        EzkeyAdmin adminZero = adminRepository
                .findByUsername("admin")
                .orElseThrow(() -> new RuntimeException("Admin zero not found"));

        // Check if admin already has enrollment
        if (adminZero.getMfaEnrollmentId() != null) {
            logger.info("✅ Admin already has enrollment (ID: {})", adminZero.getMfaEnrollmentId());
            return;
        }

        createEnrollmentZero(integrationZero);
    }

    /**
     * Create Enrollment Zero for admin global.
     */
    private void createEnrollmentZero(Integration integrationZero) {
        logger.info("🔧 Creating Enrollment Zero...");

        // Get Admin Zero
        EzkeyAdmin adminZero = adminRepository
                .findByUsername("admin")
                .orElseThrow(() -> new RuntimeException("Admin zero not found"));

        // Generate enrollment proof token
        String enrollmentProofToken = signatureService.generateProofToken();

        // Create Enrollment Zero
        Enrollment enrollmentZero = new Enrollment();
        enrollmentZero.setEnrollmentName("Admin MFA");
        enrollmentZero.setIntegration(integrationZero);
        enrollmentZero.setEnrollmentProofToken(enrollmentProofToken);
        enrollmentZero.setCreatedByAdmin(adminZero);
        enrollmentZero.setCreatedAt(LocalDateTime.now());
        enrollmentZero.setActive(true);

        enrollmentRepository.save(enrollmentZero);

        // Link admin to enrollment
        adminZero.setMfaEnrollmentId(enrollmentZero.getEnrollmentId());
        adminRepository.save(adminZero);

        // Log enrollment URL
        String enrollmentUrl = buildEnrollmentUrl(enrollmentZero);

        // Log credentials in highly visible format
        logAdminZeroEnrollmentCredentials(enrollmentZero);
    }
    
    /**
     * Log admin zero enrollment credentials in highly visible format.
     */
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

    /**
     * Build enrollment URL for mobile app binding.
     * Note: enrollmentProofToken required for security (anti-enumeration).
     */
    private String buildEnrollmentUrl(Enrollment enrollment) {
        return String.format(
                "ezkey://bind?enrollmentId=%d&enrollmentProofToken=%s",
                enrollment.getEnrollmentId(),
                enrollment.getEnrollmentProofToken());
    }
}
```

### 1.2 Configuration Bootstrap MFA

**Fichier:** `config/application.properties`

**Ajouter à la fin:**

```properties
# ==============================================
# Admin MFA Bootstrap Configuration
# ==============================================

# Enable/disable admin MFA bootstrap
# If disabled, Integration Zero and Enrollment Zero won't be auto-created
ezkey.admin.mfa.bootstrap.enabled=true

# Mode: dev (MFA optional) or prod (MFA required)
# - dev: Admin can choose to use MFA or not
# - prod: MFA required for all admins
ezkey.admin.mfa.mode=dev

# Auto-create enrollment zero for admin (dev mode recommended)
# If enabled, creates Enrollment Zero automatically
# If disabled, admin must manually create enrollment via API
ezkey.admin.mfa.bootstrap.auto-enrollment=true
```

**Properties Class:** `AdminMfaProperties.java` (nouveau fichier)

**Location:** `ezkey-admin-api/src/main/java/org/ezkey/admin/config/AdminMfaProperties.java`

```java
package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin MFA.
 * <p>
 * Controls bootstrap behavior and MFA mode (dev vs prod).
 * </p>
 *
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.mfa")
public class AdminMfaProperties {

    /**
     * MFA mode: dev (optional) or prod (required).
     * Default: dev
     */
    private String mode = "dev";

    /**
     * Bootstrap configuration.
     */
    private BootstrapConfig bootstrap = new BootstrapConfig();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public BootstrapConfig getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(BootstrapConfig bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * Bootstrap configuration nested class.
     */
    public static class BootstrapConfig {
        
        /**
         * Enable/disable MFA bootstrap.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Auto-create enrollment zero for admin.
         * Default: true (recommended for dev)
         */
        private boolean autoEnrollment = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoEnrollment() {
            return autoEnrollment;
        }

        public void setAutoEnrollment(boolean autoEnrollment) {
            this.autoEnrollment = autoEnrollment;
        }
    }
}
```

### 1.3 Ajouter Méthode au IntegrationRepository

**Fichier:** `ezkey-core/src/main/java/org/ezkey/integration/domain/repository/IntegrationRepository.java`

**Ajouter méthode:**

```java
/**
 * Find system integration (Integration Zero).
 * 
 * @param isSystemIntegration true to find system integration
 * @return Optional containing the system integration if found
 */
Optional<Integration> findByIsSystemIntegrationAndActiveTrue(Boolean isSystemIntegration);
```

### Tests Phase 1

**Tests manuels (démarrage app):**
- ✅ Startup app → Integration Zero created
- ✅ Check logs: "✅ Integration Zero created (ID: 1)"
- ✅ Check logs: "✅ Enrollment Zero created (ID: 1)"
- ✅ Check logs: "📱 Enrollment URL: ezkey://bind?enrollmentId=1&integrationId=1"
- ✅ Verify DB: SELECT * FROM ezkey_integration WHERE is_system_integration = true
- ✅ Verify DB: SELECT * FROM ezkey_enrollment WHERE enrollment_id = 1
- ✅ Verify DB: SELECT mfa_enrollment_id FROM ezkey_admin WHERE username = 'admin'
- ✅ Startup app (2nd time) → Integration Zero not duplicated
- ✅ Integration Zero has valid RSA key pair

**Tests avec configuration:**
- ✅ Set `ezkey.admin.mfa.bootstrap.enabled=false` → No bootstrap
- ✅ Set `ezkey.admin.mfa.bootstrap.auto-enrollment=false` → Integration created, no enrollment

**Commit:** `feat: Bootstrap Integration Zero and Enrollment Zero with enhanced logging

- Create AdminBootstrapService with @EventListener(ApplicationReadyEvent.class)
- Generate Integration Zero with RSA-2048 keys for system tenant
- Auto-create Enrollment Zero with challenge code generation
- Add AdminMfaProperties for bootstrap configuration (mode, enabled, auto-enrollment)
- Add IntegrationRepository.findByIsSystemIntegrationAndActiveTrue() query method
- Implement highly visible logs (WARN level, 80-char separators)
- Include complete credentials in logs: enrollmentId, proofToken, challenge
- Respect anti-enumeration protection (POST bind instructions)
- Add enrollment credentials to change-password response (all 3 values)
- Tested: Fresh install → Bootstrap → Login → Change-password → Bind → Verify

Configuration: ezkey.admin.mfa.bootstrap.enabled/auto-enrollment
Security: Challenge code generation, proof token authentication`

---

## Phase 2: Flow MFA Hybride - Backend (3-4 jours)

### Objectif

Implémenter les endpoints MFA pour le flow hybride Password → Temp Token → MFA → Bearer Token.

### Architecture Endpoints

```
POST /api/v1/admin/auth/login              (modifier)
POST /api/v1/admin/mfa/attempt             (nouveau)
POST /api/v1/admin/mfa/validate            (nouveau)
```

### 2.1 Créer AdminTempTokenRepository

**Fichier:** `ezkey-core/src/main/java/org/ezkey/integration/domain/repository/AdminTempTokenRepository.java` (nouveau)

```java
package org.ezkey.integration.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ezkey.integration.domain.entity.AdminTempToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for AdminTempToken operations.
 *
 * @since 2025
 */
@Repository
public interface AdminTempTokenRepository extends JpaRepository<AdminTempToken, Integer> {

    /**
     * Find active temp token by token string.
     */
    Optional<AdminTempToken> findByTempTokenAndActiveTrue(String tempToken);

    /**
     * Find all active temp tokens for an admin.
     */
    List<AdminTempToken> findByAdminAdminIdAndActiveTrue(Integer adminId);

    /**
     * Deactivate expired temp tokens.
     */
    @Modifying
    @Query("UPDATE AdminTempToken t SET t.active = false WHERE t.expiresAt < :cutoff AND t.active = true")
    int deactivateExpiredTokens(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Delete old expired temp tokens.
     */
    @Modifying
    @Query("DELETE FROM AdminTempToken t WHERE t.expiresAt < :cutoff AND t.active = false")
    int deleteExpiredTokens(@Param("cutoff") LocalDateTime cutoff);
}
```

### 2.2 Modifier AdminAuthService.authenticate()

**Fichier:** `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java`

**Ajouter dépendances:**

```java
private final AdminTempTokenRepository tempTokenRepository;
private final AdminMfaProperties mfaProperties;

// Update constructor
public AdminAuthService(
        EzkeyAdminRepository adminRepository,
        AdminTokenRepository tokenRepository,
        BCryptPasswordEncoder passwordEncoder,
        AdminTokenRotationProperties rotationProperties,
        AdminTempTokenRepository tempTokenRepository,
        AdminMfaProperties mfaProperties) {
    this.adminRepository = adminRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.rotationProperties = rotationProperties;
    this.tempTokenRepository = tempTokenRepository;
    this.mfaProperties = mfaProperties;
}
```

**Modifier méthode authenticate():**

```java
public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
    logger.info("Authentication attempt for user: {}", request.getUsername());
    
    try {
        // 1. Validate credentials
        EzkeyAdmin admin = validateCredentials(request);
        
        // 2. Check password change required
        if (admin.getPasswordChangeRequired()) {
            logger.warn("Password change required for admin: {}", admin.getUsername());
            return AdminLoginResponseDto.builder()
                .success(false)
                .passwordChangeRequired(true)
                .message("Password change required. Please change your password before logging in.")
                .build();
        }
        
        // 3. Check if MFA is required
        if (shouldRequireMfa(admin)) {
            // Generate TEMP TOKEN (5 min)
            return generateTempTokenResponse(admin);
        }
        
        // 4. Dev mode or MFA disabled → Direct bearer token
        rotateTokensIfEnabled(admin);
        AdminToken token = generateAndPersistToken(admin);
        updateLastLogin(admin);
        return buildSuccessResponse(admin, token);
        
    } catch (AuthenticationException e) {
        logger.warn("Authentication failed for {}: {}", request.getUsername(), e.getMessage());
        return buildErrorResponse(e.getMessage());
    }
}

/**
 * Check if MFA should be required for this admin.
 */
private boolean shouldRequireMfa(EzkeyAdmin admin) {
    // Check global mode
    if ("dev".equals(mfaProperties.getMode())) {
        // Dev mode: MFA optional, check admin preference
        return admin.getMfaEnabled() && admin.getMfaRequired();
    }
    
    // Prod mode: MFA required for all
    return true;
}

/**
 * Generate temp token response for MFA flow.
 */
private AdminLoginResponseDto generateTempTokenResponse(EzkeyAdmin admin) {
    // Generate temp token
    String tempToken = generateTempToken();
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
    
    // Save temp token
    AdminTempToken token = new AdminTempToken();
    token.setTempToken(tempToken);
    token.setAdmin(admin);
    token.setExpiresAt(expiresAt);
    token.setMfaRequired(true);
    token.setActive(true);
    token.setCreatedAt(LocalDateTime.now());
    
    tempTokenRepository.save(token);
    
    logger.info("Temp token generated for admin: {} (expires at: {})", 
        admin.getUsername(), expiresAt);
    
    return AdminLoginResponseDto.builder()
        .success(true)
        .tempToken(tempToken)
        .mfaRequired(true)
        .expiresAt(expiresAt)
        .message("MFA verification required")
        .build();
}

/**
 * Generate secure temp token string.
 */
private String generateTempToken() {
    return "ezkey_temp_" + UUID.randomUUID().toString().replace("-", "");
}
```

### 2.3 Créer AdminMfaController

**Fichier:** `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminMfaController.java` (nouveau)

```java
package org.ezkey.admin.controller;

import org.ezkey.admin.dto.request.AdminMfaAttemptRequestDto;
import org.ezkey.admin.dto.request.AdminMfaValidateRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminMfaAttemptResponseDto;
import org.ezkey.admin.service.AdminMfaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Controller for admin MFA operations.
 *
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/admin/mfa")
public class AdminMfaController {

    private final AdminMfaService mfaService;

    public AdminMfaController(AdminMfaService mfaService) {
        this.mfaService = mfaService;
    }

    /**
     * Create MFA attempt from temp token.
     */
    @PostMapping("/attempt")
    public ResponseEntity<AdminMfaAttemptResponseDto> createMfaAttempt(
            @Valid @RequestBody AdminMfaAttemptRequestDto request) {
        
        AdminMfaAttemptResponseDto response = mfaService.createMfaAttempt(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate MFA and exchange temp token for bearer token.
     */
    @PostMapping("/validate")
    public ResponseEntity<AdminLoginResponseDto> validateMfa(
            @Valid @RequestBody AdminMfaValidateRequestDto request) {
        
        AdminLoginResponseDto response = mfaService.validateMfa(request);
        return ResponseEntity.ok(response);
    }
}
```

### 2.4 Créer AdminMfaService

**Fichier:** `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminMfaService.java` (nouveau)

[Service implementation continues in next commit...]

### 2.5 Créer DTOs

**Fichiers à créer:**
- `AdminMfaAttemptRequestDto.java`
- `AdminMfaAttemptResponseDto.java`
- `AdminMfaValidateRequestDto.java`

**Modifier:**
- `AdminLoginResponseDto.java` (add tempToken, mfaRequired fields)

### 2.6 Cleanup Temp Tokens

**Modifier:** `AdminTokenCleanupService.java`

**Ajouter méthode:**

```java
/**
 * Cleanup expired temp tokens every 5 minutes.
 */
@Scheduled(cron = "0 */5 * * * *")
@Transactional
public void cleanupExpiredTempTokens() {
    LocalDateTime cutoff = LocalDateTime.now();
    
    // Deactivate expired temp tokens
    int deactivated = tempTokenRepository.deactivateExpiredTokens(cutoff);
    
    // Delete old expired temp tokens (> 1 hour)
    LocalDateTime deleteCutoff = LocalDateTime.now().minusHours(1);
    int deleted = tempTokenRepository.deleteExpiredTokens(deleteCutoff);
    
    if (deactivated > 0 || deleted > 0) {
        logger.info("Cleaned up temp tokens: {} deactivated, {} deleted", deactivated, deleted);
    }
}
```

### Tests Phase 2

**Tests Postman:**
- ✅ Login with MFA enabled → temp token returned
- ✅ Login with MFA disabled (dev mode) → bearer token directly
- ✅ POST /mfa/attempt with valid temp token → auth attempt created
- ✅ POST /mfa/attempt with invalid temp token → 401
- ✅ POST /mfa/attempt with expired temp token → 401
- ✅ POST /mfa/validate with approved attempt → bearer token
- ✅ POST /mfa/validate with denied attempt → 401
- ✅ POST /mfa/validate with wrong temp token → 401
- ✅ Temp token expires after 5 min
- ✅ Temp token invalid after use (active=false)
- ✅ Cleanup temp tokens runs every 5 min

**Commit:** `feat: Implement MFA hybrid flow (temp token + validation)`

---

## Phase 3: Tests Postman (1 jour)

### Objectif

Valider le flow MFA complet avec Postman.

### 3.1 Collection Postman

**Fichier:** `postman/collections/Admin-MFA-Flow.postman_collection.json`

**Contenu:** [Voir plan détaillé dans archives/plan.md] (archived)

### 3.2 Test Scenarios

1. Full MFA Flow (Success)
2. MFA Denied
3. Temp Token Expired
4. Dev Mode (MFA Disabled)

**Commit:** `test: Add Postman collection for admin MFA flow`

---

## Phase 4: Tests avec Demo-Device (2 jours)

### Objectif

Tester le flow MFA complet avec demo-device.

**Commit:** `test: Validate admin MFA flow with demo-device`

---

## Phase 5: CLI Python - Login/Logout (3-4 jours)

### Objectif

Mettre à niveau le CLI Python pour gérer le flow MFA admin.

**Commit:** `feat(cli): Add admin login/logout with MFA support`

---

## Phase 6: CLI Python - Enrollment Management (2-3 jours)

### Objectif

Permettre la gestion de l'enrollment admin via CLI.

**Commit:** `feat(cli): Add admin enrollment management with secure POST bind endpoint`

---

## Phase 7: Documentation et Tests Finaux (1 jour)

### Objectif

Documenter le flow complet et valider tous les scénarios.

**Commit:** `docs: Add comprehensive admin MFA documentation`

---

## Configuration Finale

### Dev Mode (Default)

```properties
ezkey.admin.mfa.mode=dev
ezkey.admin.mfa.bootstrap.enabled=true
ezkey.admin.mfa.bootstrap.auto-enrollment=true
```

### Prod Mode

```properties
ezkey.admin.mfa.mode=prod
ezkey.admin.mfa.bootstrap.enabled=true
ezkey.admin.mfa.bootstrap.auto-enrollment=false
```

---

## Récapitulatif Commits

| Phase | Commit | Testable | Duration |
|-------|--------|----------|----------|
| **0** | `feat: Add change-password API and enforce requirement` | ✅ Postman | 1-2 jours |
| **1** | `feat: Bootstrap Integration Zero and Enrollment Zero` | ✅ Logs + DB | 2-3 jours |
| **2** | `feat: Implement MFA hybrid flow (temp token + validation)` | ✅ Postman | 3-4 jours |
| **3** | `test: Add Postman collection for admin MFA flow` | ✅ Postman | 1 jour |
| **4** | `test: Validate admin MFA flow with demo-device` | ✅ End-to-end | 2 jours |
| **5** | `feat(cli): Add admin login/logout with MFA support` | ✅ CLI | 3-4 jours |
| **6** | `feat(cli): Add admin enrollment management` | ✅ CLI | 2-3 jours |
| **7** | `docs: Add comprehensive admin MFA documentation` | ✅ Review | 1 jour |

**Total:** 15-22 jours

---

## Prochaine Étape

**Start with Phase 0:** Change Password API

**Command to track progress:**
```bash
git log --oneline --grep="feat:" --grep="test:" --grep="docs:"
```

