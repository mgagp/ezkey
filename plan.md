# Plan d'Amélioration Sécurité Admin API

## Contexte

L'admin-api utilise actuellement un système d'authentification basique:

- **Tokens simples**: `ezkey_[UUID]` stockés en base
- **Rate limiting**: ✅ Implémenté (Phase 1 complétée)
- **Pas de rotation**: Tokens valides 24h sans renouvellement
- **MFA prévu mais non implémenté**: Architecture définie dans `SECURITY_MULTI_TENANT.md`
- **RBAC en roadmap**: 3 niveaux d'admin déjà architecturés

### Découvertes de l'analyse

✅ **Rate limiting implémenté** - Phase 1 complétée avec Bucket4j

✅ **"Eat Your Own Dog Food" établi** - Ezkey sécurisera ses propres APIs avec MFA

✅ **RBAC architecturé** - Admin Global/Tenant/Integration

✅ **CLI Python existe** - À adapter pour l'auth admin

✅ **Sessions ultra-courtes** - 2-10 minutes typiques (Login → Action → Logout)

## Objectifs

1. **Rate Limiting sur Login** ✅ **COMPLÉTÉ**
   - Protection contre brute force implémentée
   - Bucket4j + Caffeine cache
   - Configuration externalisée

2. **Token Hygiene** (Priorité 2 - En cours)
   - Phase 2A: Cleanup + Rotation Login (adapté aux sessions courtes)
   - Phase 2B: Refresh tokens (RÉÉVALUÉ - faible valeur pour Ezkey)

3. **Préparation MFA** (Priorité 3 - Roadmap)
   - Respecter l'architecture déjà définie
   - Intégration avec l'auth-api
   - Authentification MFA Ezkey en priorité

## Phase 1: Rate Limiting sur Login ✅ COMPLÉTÉ

### Statut

✅ Implémentation complète
✅ Tests validés
✅ Documentation à jour
✅ Configuration externalisée

### Composants Implémentés

- `AdminRateLimitProperties` - Configuration properties
- `AdminRateLimitFilter` - Filtre Servlet avec Bucket4j
- `AdminRateLimitConfig` - Configuration conditionnelle Spring
- `AdminAuthController` - Intégration avec succès/échec login

### Configuration Finale

```properties
# Admin API Rate Limiting
ezkey.admin.rate-limit.enabled=true
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=5
ezkey.admin.rate-limit.login.key-strategy=client-ip
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30
```

### Documentation

- `ezkey-admin-api/README_RATE_LIMITING.md` - Documentation complète
- `docs/OPERATIONAL.md` - Operational guide mis à jour
- `docs/ENDPOINT.md` - API endpoints documentés

---

## Phase 2A: Token Cleanup + Rotation Login (2-3 jours)

### Objectif

Améliorer la sécurité et l'hygiène des tokens sans complexité excessive, adapté aux sessions courtes d'Ezkey.

### Analyse: Pourquoi Skip Rotation Automatique (50% lifetime)?

**Réalité des sessions Ezkey:**

```
Scénario typique:
10:00:00 - Login → Token (expire +24h = 34:00)
10:00:05 - Créer AuthAttempt
10:00:10 - Logout → Token révoqué

Durée session : 10 secondes
Seuil rotation (50%) : 12 heures
Rotation JAMAIS atteinte ❌
```

**Use cases réels:**
- **Global Admin**: Login → Add/Modify Tenant → Logout (2-5 min)
- **Tenant Admin**: Login → Add/Modify Integration → Logout (2-5 min)  
- **Integration Admin**: Login → Create AuthAttempt → Logout (1-3 min)

**Verdict:** Rotation automatique à 50% lifetime apporte **peu de valeur** et ajoute **complexité inutile** (~150 lignes) pour Ezkey.

### Approche Optimale: Cleanup + Rotation Login

**Effort:** ~80 lignes, ~3 heures  
**Valeur:** ✅ Élevée (DB propre + 1 token actif/admin)

#### 1. Cleanup Automatique des Tokens Expirés (Critique)

**Service**: `AdminTokenCleanupService`

```java
package org.ezkey.admin.service;

import java.time.LocalDateTime;

import org.ezkey.admin.config.AdminTokenCleanupProperties;
import org.ezkey.admin.domain.repository.AdminTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for automatic cleanup of expired admin tokens.
 * <p>
 * This service runs periodically to remove expired and inactive tokens
 * from the database, improving security and performance.
 * </p>
 *
 * @since 2025
 */
@Service
public class AdminTokenCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminTokenCleanupService.class);
    
    private final AdminTokenRepository tokenRepository;
    private final AdminTokenCleanupProperties properties;
    
    public AdminTokenCleanupService(AdminTokenRepository tokenRepository,
                                   AdminTokenCleanupProperties properties) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
    }
    
    /**
     * Cleanup expired and inactive tokens every hour.
     * Reduces attack surface and improves database performance.
     */
    @Scheduled(cron = "${ezkey.admin.token.cleanup.schedule}")
    @Transactional
    public void cleanupExpiredTokens() {
        if (!properties.isEnabled()) {
            return;
        }
        
        LocalDateTime cutoff = LocalDateTime.now();
        
        // Delete tokens that are both expired AND inactive
        int deleted = tokenRepository.deleteByExpiresAtBeforeAndActiveFalse(cutoff);
        
        if (deleted > 0) {
            logger.info("🧹 Cleaned up {} expired tokens", deleted);
        } else {
            logger.debug("✅ No expired tokens to clean");
        }
    }
}
```

**Configuration:**

```properties
# Token cleanup configuration
ezkey.admin.token.cleanup.enabled=true
ezkey.admin.token.cleanup.schedule=0 0 * * * *  # Every hour
```

**Configuration Properties:**

```java
package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin token cleanup.
 *
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.token.cleanup")
public class AdminTokenCleanupProperties {
    
    private boolean enabled = true;
    private String schedule = "0 0 * * * *"; // Every hour
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getSchedule() {
        return schedule;
    }
    
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
}
```

**Repository Method:**

```java
@Repository
public interface AdminTokenRepository extends JpaRepository<AdminToken, Integer> {
    
    /**
     * Delete tokens that are expired AND inactive.
     * Active tokens are never deleted (even if expired) for audit trail.
     */
    @Modifying
    @Query("DELETE FROM AdminToken t WHERE t.expiresAt < :cutoff AND t.active = false")
    int deleteByExpiresAtBeforeAndActiveFalse(@Param("cutoff") LocalDateTime cutoff);
}
```

**Avantages:**
- ✅ Réduit surface d'attaque (moins de tokens en DB)
- ✅ Améliore performances (requêtes plus rapides)
- ✅ Pas de breaking change
- ✅ Configuration externalisée

#### 2. Rotation au Login - 1 Token Actif par Admin (Bonus Simple)

**Concept:** Invalider tous les anciens tokens à chaque nouveau login.

**Modification**: `AdminAuthService.java`

```java
@Service
@Transactional
public class AdminAuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminAuthService.class);
    
    private final EzkeyAdminRepository adminRepository;
    private final AdminTokenRepository tokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminTokenRotationProperties rotationProperties;
    
    public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
        // 1. Validate credentials (existing)
        EzkeyAdmin admin = validateCredentials(request);
        
        // 2. Rotate tokens on login (NEW)
        if (rotationProperties.isRotationOnLoginEnabled()) {
            int deactivated = tokenRepository.deactivateAllTokensForAdmin(admin.getAdminId());
            
            if (deactivated > 0) {
                logger.info("🔄 Rotated {} old tokens for admin {} on login", 
                    deactivated, admin.getUsername());
            }
        }
        
        // 3. Generate new token
        String bearerToken = generateBearerToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        
        AdminToken token = new AdminToken();
        token.setAdmin(admin);
        token.setBearerToken(bearerToken);
        token.setExpiresAt(expiresAt);
        token.setActive(true);
        token.setCreatedAt(LocalDateTime.now());
        tokenRepository.save(token);
        
        // 4. Update admin last login
        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);
        
        // 5. Return response
        return buildSuccessResponse(admin, bearerToken, expiresAt);
    }
    
    private EzkeyAdmin validateCredentials(AdminLoginRequestDto request) {
        EzkeyAdmin admin = adminRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new InvalidCredentialsException());
        
        if (!admin.getActive()) {
            throw new AdminInactiveException();
        }
        
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        
        return admin;
    }
    
    private String generateBearerToken() {
        return "ezkey_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private AdminLoginResponseDto buildSuccessResponse(EzkeyAdmin admin, 
                                                       String bearerToken,
                                                       LocalDateTime expiresAt) {
        return AdminLoginResponseDto.builder()
            .success(true)
            .bearerToken(bearerToken)
            .adminType(admin.getAdminType().name())
            .username(admin.getUsername())
            .expiresAt(expiresAt)
            .passwordChangeRequired(admin.getPasswordChangeRequired())
            .message("Authentication successful")
            .build();
    }
}
```

**Repository Method:**

```java
@Repository
public interface AdminTokenRepository extends JpaRepository<AdminToken, Integer> {
    
    /**
     * Deactivate all active tokens for a specific admin.
     * Used during login to enforce "one active token per admin" policy.
     */
    @Modifying
    @Query("UPDATE AdminToken t SET t.active = false WHERE t.admin.adminId = :adminId AND t.active = true")
    int deactivateAllTokensForAdmin(@Param("adminId") Integer adminId);
}
```

**Configuration:**

```properties
# Token rotation on login
ezkey.admin.token.rotation-on-login=true  # Can disable if needed
```

**Configuration Properties:**

```java
package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin token rotation.
 *
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.token")
public class AdminTokenRotationProperties {
    
    private boolean rotationOnLoginEnabled = true;
    
    public boolean isRotationOnLoginEnabled() {
        return rotationOnLoginEnabled;
    }
    
    public void setRotationOnLoginEnabled(boolean rotationOnLoginEnabled) {
        this.rotationOnLoginEnabled = rotationOnLoginEnabled;
    }
}
```

**Avantages:**
- ✅ Limite stricte: 1 token actif par admin
- ✅ Sécurité: Token volé invalide au prochain login légitime
- ✅ Simple: ~30 lignes de code
- ✅ Pas de breaking change
- ✅ Configuration externalisée (peut désactiver si besoin)

**Cas d'usage sécurité:**

```
Admin login from CLI laptop → Token A active
Token A stolen by attacker
Admin login from CLI desktop → Token B active, Token A deactivated
Attacker tries Token A → 401 Unauthorized ✅
```

### Tests

1. **Tests de cleanup**
   - Tokens expirés ET inactifs supprimés automatiquement
   - Tokens actifs préservés (même expirés, pour audit)
   - Scheduled task runs every hour
   - Configuration can disable cleanup

2. **Tests de rotation login**
   - Login → Anciens tokens désactivés
   - Login → Nouveau token actif
   - Anciens tokens retournent 401 après login
   - Configuration can disable rotation

3. **Tests de sécurité**
   - Token volé invalide après login légitime
   - Seul le dernier token fonctionne
   - Pas d'impact sur autres admins

### Migration et Déploiement

**Sans régression:**

- Cleanup désactivable en dev (`ezkey.admin.token.cleanup.enabled=false`)
- Rotation login désactivable (`ezkey.admin.token.rotation-on-login=false`)
- Monitoring via logs
- Pas d'impact sur clients existants

---

## Phase 2B: Refresh Tokens - RÉÉVALUÉ

### Statut: ⚠️ Complexité Non Justifiée pour Ezkey

### Analyse Révisée

**Contexte Ezkey:**
- **Sessions ultra-courtes**: 2-10 minutes (Login → Action → Logout)
- **Logout systématique**: Admins se déconnectent après chaque action
- **MFA Ezkey en roadmap**: Le MFA rendra l'authentification transparente
- **CLI mode ponctuel**: Pas besoin d'auto-relogin, tokens courts acceptables

**Valeur des Refresh Tokens:**

| Bénéfice | Standard Web App | Ezkey Admin API |
|----------|------------------|-----------------|
| Réduire fenêtre d'exploitation | ✅ Élevé | ⚠️ Faible (sessions déjà courtes) |
| Détecter vol de token | ✅ Élevé | ⚠️ Faible (logout systématique) |
| Révocation granulaire | ✅ Élevé | ⚠️ Faible (1 token/admin) |
| UX sessions longues | ✅ Élevé | ❌ Non applicable |

**Complexité ajoutée:**
- Nouvelle table `ezkey_admin_refresh_tokens`
- Nouveau endpoint `/refresh`
- Logique détection theft (famille tokens)
- Tests complexes (réutilisation, theft detection)
- Migration CLI Python
- **Effort estimé:** ~2-3 semaines

**Verdict:** ❌ **SKIP Phase 2B** - Complexité non justifiée

**Alternative recommandée:** Prioriser **Phase 4 (MFA Ezkey)** qui apporte une valeur sécurité bien supérieure avec l'authentification biométrique.

---

## Phase 3: Migration vers JWT - RÉÉVALUÉ

### Statut: ⚠️ Optionnel, Valeur Limitée pour Ezkey

### Analyse

**Avantages JWT:**
- ✅ Standard industrie (RFC 7519)
- ✅ Stateless (pas de lookup DB)
- ✅ Claims embarqués (admin type, tenant)
- ✅ Compatible OAuth2/OpenID Connect

**Inconvénients JWT pour Ezkey:**
- ❌ Révocation complexe (nécessite blacklist = lookup DB anyway)
- ❌ Sessions courtes = peu de bénéfice stateless
- ❌ Complexité accrue (signature, validation, rotation clés)
- ❌ Taille plus grande
- ❌ Audit moins direct (claims vs DB)

**Contexte Ezkey:**
- Volume d'appels modéré (pas de scale massif)
- Contrôle important (révocation immédiate nécessaire)
- Audit critique (traçabilité complète)
- Clients limités (CLI, pas de browser/mobile)

**Verdict:** ⚠️ **SKIP Phase 3** - Système actuel amélioré (Phase 2A) suffit

**Si certification future nécessite OAuth2/JWT:** Peut être réévalué dans 12+ mois.

---

## Phase 4: Intégration MFA Ezkey (Roadmap - Priorité Élevée)

### Statut: 🔵 À Planifier

### Contexte

**"Eat Your Own Dog Food"** - Ezkey utilise sa propre solution MFA pour sécuriser ses APIs admin.

**Architecture déjà définie:**
- `SECURITY_MULTI_TENANT.md` - Architecture complète
- Admin Global avec "Integration Zero" (système)
- Enrollment Zero pour premier admin
- Authentification hybride: Password → Temp Token → MFA → Full Token

### Évolution du Plan Suite à l'Analyse

**Priorisation révisée:**

1. **Phase 2A (Token Cleanup + Rotation Login)** - Implémentation immédiate
   - Effort: ~3 heures
   - Valeur: Élevée
   - Complexité: Faible

2. **Phase 4 (MFA Ezkey)** - Roadmap prioritaire
   - Effort: ~3-4 semaines
   - Valeur: Très élevée (authentification biométrique)
   - Complexité: Moyenne (architecture déjà définie)

**Phase 2B (Refresh) et Phase 3 (JWT) skippées** car:
- Complexité élevée (~5-7 semaines combinées)
- Valeur faible pour sessions ultra-courtes d'Ezkey
- MFA Ezkey apporte bien plus de valeur sécurité

### Flow Futur avec MFA Ezkey

```
1. POST /login {username, password}
   ↓
2. Valider credentials
   ↓
3. Si MFA disabled → Bearer token 24h (dev mode)
   Si MFA enabled → Temp token (5 min)
   ↓
4. POST /mfa/attempt {tempToken}
   ↓
5. Créer AuthAttempt via auth-api
   ↓
6. Mobile app: Approve/Deny (biométrie)
   ↓
7. POST /mfa/validate {tempToken, authAttemptId}
   ↓
8. Valider MFA response
   ↓
9. Générer Bearer token complet (24h)
   ↓
10. Retourner token final
```

### Endpoints à créer (selon SECURITY_MULTI_TENANT.md)

1. `POST /api/v1/admin/auth/login` - Déjà existe, à modifier
2. `POST /api/v1/admin/mfa/attempt` - Créer AuthAttempt
3. `POST /api/v1/admin/mfa/validate` - Valider MFA response
4. `POST /api/v1/admin/mfa/enrollment-zero` - Setup initial
5. `POST /api/v1/admin/mfa/activate` - Activer MFA
6. `POST /api/v1/admin/mfa/deactivate` - Désactiver MFA (dev)

### Plan de Mise en Œuvre

**Cette phase sera détaillée dans un plan séparé** car elle implique:

- Création des entités tenant/admin (déjà fait)
- Intégration avec auth-api
- Nouveaux controllers/services MFA
- Tests end-to-end complets
- Migration CLI Python pour MFA flow

---

## Récapitulatif des Phases

| Phase | Durée | Priorité | Status | Valeur Ezkey |
|-------|-------|----------|--------|--------------|
| **1. Rate Limiting** | 1 semaine | 🔴 Critique | ✅ **COMPLÉTÉ** | Sécurité immédiate |
| **2A. Cleanup + Rotation Login** | 2-3 jours | 🟡 Important | 📋 **À FAIRE** | DB propre + 1 token/admin |
| **~~2B. Refresh Tokens~~** | ~~2-3 semaines~~ | ~~🟡 Important~~ | ❌ **SKIPPÉ** | ⚠️ Faible (sessions courtes) |
| **~~3. JWT~~** | ~~3-4 semaines~~ | ~~🟢 Optionnel~~ | ❌ **SKIPPÉ** | ⚠️ Complexité non justifiée |
| **4. MFA Ezkey** | 3-4 semaines | 🔵 Prioritaire | 📅 **ROADMAP** | ✅ Très élevée (biométrie) |

---

## Recommandations Finales

### Plan Révisé Optimal pour Ezkey

**Implémentation immédiate:**

1. ✅ **Phase 1: Rate Limiting** - COMPLÉTÉ
2. 📋 **Phase 2A: Cleanup + Rotation Login** - À FAIRE (3 heures)

**Roadmap prioritaire:**

3. 📅 **Phase 4: MFA Ezkey** - Plan séparé (3-4 semaines)

### Effort vs. Valeur

| Composant | Effort | Valeur Ezkey | Décision |
|-----------|--------|--------------|----------|
| Rate Limiting | 1 sem | ✅ Élevée | ✅ FAIT |
| Cleanup tokens | 2h | ✅ Élevée | 📋 FAIRE |
| Rotation login | 1h | ✅ Bonne | 📋 FAIRE |
| Refresh tokens | 2-3 sem | ⚠️ Faible | ❌ SKIP |
| JWT | 3-4 sem | ⚠️ Faible | ❌ SKIP |
| MFA Ezkey | 3-4 sem | ✅ Très élevée | 🔵 ROADMAP |

**Total immédiat:** ~3 heures (Phase 2A)  
**ROI:** Excellent (DB propre + 1 token actif/admin + sécurité accrue)

### Considération: Pas de Serveur Séparé

✅ Toutes les phases respectent cette contrainte:

- Rate limiting: ✅ Filtre dans admin-api
- Cleanup: ✅ Service scheduled dans admin-api
- Rotation login: ✅ Logique dans AdminAuthService
- MFA: ✅ Appels à auth-api (déjà existant)

**Aucune phase ne nécessite un serveur d'autorisation séparé.**

---

## Next Steps

1. ✅ **Phase 1 complétée** - Rate Limiting opérationnel
2. 📋 **Implémenter Phase 2A** - Cleanup + Rotation Login (prochaine étape)
3. 📅 **Planifier Phase 4** - MFA Ezkey (plan séparé à créer)

---

**Document Version**: 2.0  
**Created**: 2025-10-03  
**Updated**: 2025-10-03  
**Status**: Plan révisé optimisé pour Ezkey

