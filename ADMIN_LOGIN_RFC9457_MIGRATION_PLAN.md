# Plan de Migration RFC 9457 - Admin Login API

## Document de Planification
**Statut:** En cours de formalisation
**Date de créations:** 2025-02-10
**Objectif:** Standardiser progressivement les réponses JSON des API au format RFC 9457, en commençant par le login admin

---

## 1. État Actuel (AS-IS)

### 1.1 Problème Identifié

Le login admin retourne actuellement un format **legacy** (non-RFC 9457):

```json
{
    "success": false,
    "message": "Authentication failed: Invalid credentials"
}
```

### 1.2 Structure Actuelle

**AdminAuthService.authenticate()** → Capture `AuthenticationException` → Retourne `AdminLoginResponseDto`

```java
// AdminAuthService.java (ligne ~110)
public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
    try {
        return authenticatePasswordless(request);
    } catch (AuthenticationException e) {
        return buildErrorResponse(e.getMessage()); // ❌ Pas RFC 9457
    }
}

// Réponse interne uniquement, pas levée d'exception
private AdminLoginResponseDto buildErrorResponse(String message) {
    return new AdminLoginResponseDto(false, message, null, null, null, null, null, null, null);
}
```

### 1.3 Validations Existantes

Dans `AdminAuthService.authenticatePasswordless()` (lignes 153-250), on identifie les cas d'erreur:

1. **Utilisateur introuvable** → `AuthenticationException("Invalid credentials")`
2. **Compte inactif** → `AuthenticationException("Account is inactive")`
3. **Tenant inactif** → `AuthenticationException("Your tenant has been deactivated...")`
4. **Pas d'enrollment** → `AuthenticationException("No device enrolled...")`
5. **Authentification rejetée** → `AuthenticationException("Authentication rejected by device")`
6. **Signature invalide** → `AuthenticationException("Authentication failed - invalid signature...")`
7. **Authentification expirée** → `AuthenticationException("Authentication expired...")`
8. **Timeout** → `AuthenticationException("Authentication timeout...")`

### 1.4 Référence: Pattern deactivateAdmin (RFC 9457)

Le service `AdminProvisioningService.deactivateAdmin()` (ligne 649) utilise déjà le pattern corrct:

```java
// Exceptions spécifiques levées
throw new AdminNotAllowedException("Cannot deactivate your own account");
throw new AdminLimitException("Cannot deactivate - minimum global admin limit...");
throw new ResourceNotFoundException("Admin", adminId);

// GlobalExceptionHandler mappe les exceptions à RFC 9457 ProblemDetail
// HTTP 400 + ProblemDetail avec:
// - type: "https://ezkey.io/problems/admin-not-allowed"
// - title: "Admin Operation Not Allowed"
// - detail: (le message d'exception)
// - status: 400
// - path: (l'URI requête)
```

---

## 2. État Cible (TO-BE)

### 2.1 Vision

Le login admin aura des réponses d'erreur au format **RFC 9457 ProblemDetail** avec des exceptions spécifiques claires:

```json
{
    "type": "https://ezkey.io/problems/authentication/invalid-credentials",
    "title": "Invalid Credentials",
    "status": 401,
    "detail": "Invalid username or password",
    "path": "/api/v1/admin/auth/login"
}
```

### 2.2 Exceptions Spécifiques Requises

Nouvelles exceptions à créer dans `ezkey-admin-api/src/main/java/org/ezkey/admin/exception/`:

| Exception | HTTP | Type URI | Cas d'usage |
|-----------|------|----------|------------|
| `AdminAuthenticationException` | 401 | `.../invalid-credentials` | Credentials invalides (user/pass/device) |
| `AdminAccountInactiveException` | 403 | `.../account-inactive` | Admin inactif |
| `AdminNoEnrollmentException` | 403 | `.../no-enrollment` | Pas de device enrollment |
| `AdminAuthenticationExpiredException` | 400 | `.../auth-expired` | Tentative expirée |
| `AdminAuthenticationRejectedException` | 400 | `.../auth-rejected` | Device rejection |
| `AdminDeviceSignatureInvalidException` | 400 | `.../invalid-signature` | Signature device invalide |
| `AdminAuthenticationTimeoutException` | 408 | `.../auth-timeout` | Timeout sans réponse device |
| (Réutiliser) `TenantInactiveException` | 403 | `.../tenant-inactive` | Tenant inactif |
| (Réutiliser) `RateLimitExceededException` | 429 | (selon pattern) | Rate limit dépassée |

### 2.3 Mapping Globaux Requis

| Exception | HTTP | Raison |
|-----------|------|--------|
| `AdminAuthenticationException` | **401** Unauthorized | Credentials/auth failed |
| `AdminAccountInactiveException` | **403** Forbidden | Resource access forbidden |
| `AdminNoEnrollmentException` | **403** Forbidden | Resource access forbidden |
| `TenantInactiveException` | **403** Forbidden | Tenant locked |
| `AdminAuthenticationExpiredException` | **400** Bad Request | Request invalid/stale |
| `AdminAuthenticationRejectedException` | **400** Bad Request | Business logic rejection |
| `AdminDeviceSignatureInvalidException` | **400** Bad Request | Validation failed |
| `AdminAuthenticationTimeoutException` | **408** Request Timeout | Request timeout |

### 2.4 Flux AdminAuthService (Refactorisé)

```java
public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
    // Exceptions levées → GlobalExceptionHandler → RFC 9457 ProblemDetail
    try {
        return authenticatePasswordless(request);
    } catch (AdminAuthenticationException | AdminAccountInactiveException |
             AdminNoEnrollmentException | TenantInactiveException |
             AdminAuthenticationExpiredException | AdminAuthenticationRejectedException |
             AdminDeviceSignatureInvalidException | AdminAuthenticationTimeoutException e) {
        throw e; // Re-throw → GlobalExceptionHandler
    }
}

private AdminLoginResponseDto authenticatePasswordless(AdminLoginRequestDto request) {
    // 1. Validations qui lèvent NOW
    EzkeyAdmin admin = adminRepository.findByUsernameWithEnrollment(request.username())
        .orElseThrow(() -> new AdminAuthenticationException("Invalid credentials"));

    if (!admin.getActive()) {
        throw new AdminAccountInactiveException("Account has been deactivated");
    }

    if (admin.getTenant() != null && !admin.getTenant().getActive()) {
        throw new TenantInactiveException("Tenant has been deactivated");
    }

    if (admin.getEnrollment() == null || admin.getEnrollment().getDevicePublicKey() == null) {
        throw new AdminNoEnrollmentException("No device enrollment found");
    }

    // ... reste du code

    // 2. Traitement du device response
    if ("REJECTED".equals(status)) {
        throw new AdminAuthenticationRejectedException("Device rejected authentication request");
    } else if ("INVALID".equals(status)) {
        throw new AdminDeviceSignatureInvalidException("Device signature validation failed");
    } else if ("EXPIRED".equals(status)) {
        throw new AdminAuthenticationExpiredException("Authentication attempt expired");
    } else if (Boolean.TRUE.equals(waitResp.getTimeoutReached())) {
        throw new AdminAuthenticationTimeoutException("No device response within timeout period");
    }

    // 3. Succès
    return buildSuccessResponse(...);
}
```

---

## 3. Énumération des Validations à Traiter

### Phase 1: Validations de base (input)
- [ ] Username vide/invalide → 400 Bad Request (validation déjà en place)
- [ ] Username non trouvé → 401 Unauthorized (AdminAuthenticationException)

### Phase 2: État du compte
- [ ] Admin inactif → 403 Forbidden (AdminAccountInactiveException)
- [ ] Tenant inactif → 403 Forbidden (TenantInactiveException - réutiliser)

### Phase 3: Authentification
- [ ] Pas de device enrollment → 403 Forbidden (AdminNoEnrollmentException)
- [ ] Device rejection → 400 Bad Request (AdminAuthenticationRejectedException)
- [ ] Signature invalide → 400 Bad Request (AdminDeviceSignatureInvalidException)
- [ ] Authentification expirée → 400 Bad Request (AdminAuthenticationExpiredException)
- [ ] Timeout sans réponse → 408 Request Timeout (AdminAuthenticationTimeoutException)

### Phase 4: Rate Limiting (déjà en place)
- [ ] Rate limit dépassée → 429 Too Many Requests (RateLimitExceededException)

---

## 4. Impacts sur les Clients

### 4.1 CLI Python (`ezkey-cli-python/`)

**État Actuel:**
- `http_client.py`: Déjà supporte RFC 9457 ET format legacy
- Cherche `detail` field (RFC 9457) puis fallback `message` field (legacy)

```python
# ezkey_cli/utils/http_client.py (ligne 89)
def _format_error(self, response: requests.Response) -> str:
    error_data = response.json()
    # RFC 9457: "detail" ou "title"
    # Legacy: "message" ou "error"
    if "detail" in error_data and error_data["detail"]:
        return f"HTTP {response.status_code}: {error_data['detail']}"
```

**Action Requise:**
- ✅ **AUCUNE** - Le client gère déjà les deux formats !
- La transition est transparente pour la CLI

### 4.2 TUI CLI (`ezkey-cli-python/`)

**État Actuel:**
- Le TUI affiche les erreurs à partir de la réponse HTTP
- Utilisera le même `HttpClient` que la CLI

**Action Requise:**
- ✅ **AUCUNE** - Le TUI héritera de la gestion du HttpClient
- Mais: Vérifier les tests et la documentation du TUI

### 4.3 Démo App ACME (`ezkey-demo-app-acme/`)

**État Actuel:**
- [À analyser] Probablement utilise le format legacy

**Action Requise:**
- [ ] Lire le LoginController.java pour voir l'implémentation
- [ ] Adapter si nécessaire pour gérer RFC 9457

---

## 5. Plan d'Implémentation Détaillé

### Phase 1: Conception & Exceptions (PRIORITY: P0)

**Livrables:**
1. [ ] Créer exceptions spécifiques:
   - `AdminAuthenticationException` (401)
   - `AdminAccountInactiveException` (403)
   - `AdminNoEnrollmentException` (403)
   - `AdminAuthenticationExpiredException` (400)
   - `AdminAuthenticationRejectedException` (400)
   - `AdminDeviceSignatureInvalidException` (400)
   - `AdminAuthenticationTimeoutException` (408)

**Fichiers à créer:**
```
ezkey-admin-api/src/main/java/org/ezkey/admin/exception/
├── AdminAuthenticationException.java
├── AdminAccountInactiveException.java
├── AdminNoEnrollmentException.java
├── AdminAuthenticationExpiredException.java
├── AdminAuthenticationRejectedException.java
├── AdminDeviceSignatureInvalidException.java
└── AdminAuthenticationTimeoutException.java
```

**Contrats d'usage:**
- Toutes étendent `RuntimeException`
- **JavaDoc complète:**
  - Classe: Description claire du cas d'erreur
  - Exemple de réponse RFC 9457 avec type URI, status, detail
  - HTTP status mapping explicite en JavaDoc
  - `@since 2025` tag
- **Annotations transparentes:**
  - Aucune annotation à ces exceptions (les handlers les ajoutent)
  - Pas de dépendance Swagger au niveau exception class

---

### Phase 2: Exception Handlers (PRIORITY: P0)

**Livrables:**
1. [ ] Mettre à jour `GlobalExceptionHandler.java`:
   - Ajouter @ExceptionHandler pour chaque nouvelle exception
   - Chaque handler retourne `ResponseEntity<ProblemDetail>` (RFC 9457)
   - URIs type: `https://ezkey.io/problems/authentication/invalid-credentials` etc.
   - **JavaDoc complet pour chaque handler:**
     - Description du cas d'erreur traité
     - Mapping HTTP status explicite
     - Exemple de réponse ProblemDetail JSON
   - **Ordenamento correcte des handlers:**
     - Plus spécifiques d'abord (exceptions spécialisées)
     - Plus générales à la fin (RuntimeException)

**Contrats d'usage handlers:**
- Format ProblemDetail RFC 9457 identique pour toutes
- Champs obligatoires: type, title, status, detail, (instance)
- Pas de dépendance circulaire entre handlers
- Logging uniforme: `logger.warn()` pour erreurs clients, `logger.error()` pour serveur

**Exemple handler minimum:**
```java
/**
 * Handles AdminAuthenticationException and returns HTTP 401 Unauthorized.
 *
 * <p>This handler catches AdminAuthenticationException thrown when authentication
 * credentials are invalid, such as wrong username or device rejection.
 *
 * <p><b>HTTP Status:</b> 401 Unauthorized
 *
 * <p><b>Response Format:</b> RFC 9457 ProblemDetail
 *
 * <p><b>Example Response:</b>
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/authentication/invalid-credentials",
 *   "title": "Invalid Credentials",
 *   "status": 401,
 *   "detail": "Invalid username or password",
 *   "instance": "/api/v1/admin/auth/login"
 * }
 * }</pre>
 *
 * @param ex the AdminAuthenticationException that was thrown
 * @param request the HTTP servlet request for path extraction
 * @return ResponseEntity containing ProblemDetail and HTTP 401 status
 * @since 2025
 */
@ExceptionHandler(AdminAuthenticationException.class)
public ResponseEntity<ProblemDetail> handleAdminAuthenticationException(
    AdminAuthenticationException ex,
    jakarta.servlet.http.HttpServletRequest request) {
  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.UNAUTHORIZED, ex.getMessage());
  problem.setType(URI.create("https://ezkey.io/problems/authentication/invalid-credentials"));
  problem.setTitle("Invalid Credentials");
  problem.setProperty("instance", request.getRequestURI());
  return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
}
```


---

### Phase 3: AdminAuthService Refactoring (PRIORITY: P0)

**Livrables:**
1. [ ] Remplacer tous les `new AuthenticationException(...)` par les exceptions spécifiques
2. [ ] Supprimer `buildErrorResponse()` (elle retournait un DTO error)
3. [ ] Laisser les exceptions se propager → GlobalExceptionHandler

**Étapes:**
```java
private AdminLoginResponseDto authenticatePasswordless(...) {
    // Avant: throw new AuthenticationException("Invalid credentials")
    // Après:
    EzkeyAdmin admin = adminRepository.findByUsernameWithEnrollment(request.username())
        .orElseThrow(() -> new AdminAuthenticationException("Invalid credentials"));

    // Avant: throw new AuthenticationException("Account is inactive")
    // Après:
    if (!admin.getActive()) {
        throw new AdminAccountInactiveException("Account has been deactivated");
    }

    // ... continuer pour tous les cas d'erreur
}
```

---

### Phase 4: AdminAuthController (PRIORITY: P1)

**Livrables:**
1. [ ] **JavaDoc complet pour méthode login():**
   - Décrire les trois cas de succès (approved token, pending challenge, pending non-blocking)
   - **Documenter TOUS les codes d'erreur possibles avec contexte:**
     - 400 Bad Request (validation, auth expired, device rejected)
     - 401 Unauthorized (credentials invalides)
     - 403 Forbidden (account/tenant inactive, no enrollment)
     - 408 Request Timeout (device non-responsive)
     - 429 Too Many Requests (rate limit exceeded)
     - 500 Internal Server Error (infrastructure issue)
   - Préciser que errors retournent RFC 9457 ProblemDetail
   - Ajouter `@throws` pour les exceptions majeures

2. [ ] **Annotations Swagger/OpenAPI alignées avec JavaDoc:**
   - @Operation: Courte description + mention des erreurs
   - @ApiResponses:
     - Pour chaque code HTTP (200, 400, 401, 403, 408, 429, 500)
     - Description claire du cas d'erreur
     - Référencer la structure de réponse (ProblemDetail vs AdminLoginResponseDto)
   - Ajouter @Content avec mediaType pour chaque réponse
   - Exemples RFC 9457 dans les descriptions

3. [ ] **Paramètres bien documentés:**
   - @RequestBody: Complète avec description des champs
   - @RequestHeader: Inclure les headers attendus (User-Agent, IP, etc.)
   - Lister les contraintes de validation

4. [ ] **Synchroniser JavaDoc ↔ Swagger:**
   - Swagger doit être "human-readable version" de la JavaDoc
   - Pas d'informations différentes entre les deux
   - Mettre à jour ensemble lors de futures modifications

**Exemple Swagger/OpenAPI enrichi:**
```java
/**
 * Authenticate administrator with passwordless authentication.
 *
 * <p>This endpoint authenticates an administrator using Ezkey's passwordless
 * cryptographic authentication system. Supports both challenge and non-challenge
 * flows, returning a bearer token on success.
 *
 * <p><b>Success Responses (HTTP 200):</b>
 * <ul>
 *   <li><b>Approved (status=approved):</b> Bearer token issued immediately
 *   <li><b>Pending Challenge (status=pending):</b> Challenge code returned, requires
 *       /passwordless-wait call
 *   <li><b>Pending (status=pending):</b> Non-blocking mode, client should poll
 *       /passwordless-wait
 * </ul>
 *
 * <p><b>Error Responses (RFC 9457 ProblemDetail):</b>
 * <ul>
 *   <li><b>400 Bad Request:</b> Validation error, invalid challenge, or business logic
 *       error (expired, rejected, invalid signature)
 *   <li><b>401 Unauthorized:</b> Invalid credentials (username/password or device)
 *   <li><b>403 Forbidden:</b> Account/tenant inactive or no device enrollment
 *   <li><b>408 Request Timeout:</b> No device response within timeout period
 *   <li><b>429 Too Many Requests:</b> Rate limit exceeded
 *   <li><b>500 Internal Server Error:</b> Unexpected server error
 * </ul>
 *
 * @param request the login request (username required, challengeRequested and
 *        nonBlocking optional)
 * @param httpRequest the HTTP servlet request for client context extraction
 * @return ResponseEntity with AdminLoginResponseDto (on success) or ProblemDetail
 *         (on error with 4xx/5xx status)
 * @throws AdminAuthenticationException maps to 401 Unauthorized
 * @throws AdminAccountInactiveException maps to 403 Forbidden
 * @throws AdminNoEnrollmentException maps to 403 Forbidden
 * @throws AdminAuthenticationExpiredException maps to 400 Bad Request
 * @throws AdminAuthenticationRejectedException maps to 400 Bad Request
 * @throws AdminDeviceSignatureInvalidException maps to 400 Bad Request
 * @throws AdminAuthenticationTimeoutException maps to 408 Request Timeout
 * @throws TenantInactiveException maps to 403 Forbidden
 * @throws RateLimitExceededException maps to 429 Too Many Requests
 * @since 2025
 */
@Operation(
    summary = "Authenticate administrator",
    description = "Passwordless authentication using device-bound cryptographic credentials. "
        + "Returns bearer token on success, or challenge/pending info for two-step flows. "
        + "Rate limiting applied (see HTTP 429 responses).")
@ApiResponses(
    value = {
      @ApiResponse(
          responseCode = "200",
          description = "Authentication successful or pending. Check 'status' field in response.",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = AdminLoginResponseDto.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Bad Request - Validation error, or authentication failed (expired, "
              + "rejected, invalid signature). Returns RFC 9457 ProblemDetail.",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(
                  value = "{\"type\": \"https://ezkey.io/problems/authentication/invalid-signature\", "
                      + "\"title\": \"Invalid Signature\", \"status\": 400, "
                      + "\"detail\": \"Device signature validation failed\"}"))),
      @ApiResponse(
          responseCode = "401",
          description = "Unauthorized - Invalid credentials (username or device). "
              + "Returns RFC 9457 ProblemDetail.",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(
                  value = "{\"type\": \"https://ezkey.io/problems/authentication/invalid-credentials\", "
                      + "\"title\": \"Invalid Credentials\", \"status\": 401, "
                      + "\"detail\": \"Invalid username or password\"}"))),
      @ApiResponse(
          responseCode = "403",
          description = "Forbidden - Account or tenant inactive, or no device enrollment. "
              + "Returns RFC 9457 ProblemDetail.",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(
                  value = "{\"type\": \"https://ezkey.io/problems/authentication/account-inactive\", "
                      + "\"title\": \"Account Inactive\", \"status\": 403, "
                      + "\"detail\": \"Account has been deactivated\"}"))),
      @ApiResponse(
          responseCode = "408",
          description = "Request Timeout - No device response within timeout period. "
              + "Returns RFC 9457 ProblemDetail.",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(
                  value = "{\"type\": \"https://ezkey.io/problems/authentication/auth-timeout\", "
                      + "\"title\": \"Authentication Timeout\", \"status\": 408, "
                      + "\"detail\": \"No device response within timeout period\"}"))),
      @ApiResponse(
          responseCode = "429",
          description = "Too Many Requests - Rate limit exceeded. "
              + "Check Retry-After header for backoff time."),
      @ApiResponse(
          responseCode = "500",
          description = "Internal Server Error - Unexpected error. "
              + "Returns RFC 9457 ProblemDetail.")
    })
@PostMapping("/login")
public ResponseEntity<AdminLoginResponseDto> login(
    @Valid @RequestBody AdminLoginRequestDto request,
    HttpServletRequest httpRequest) {
  // ...
}
```

---

### Phase 5: CLI Python - Validation (PRIORITY: P1)

**Livrables:**
1. [ ] Vérifier que `http_client.py._format_error()` gère correctement tous les cas
2. [ ] Ajouter tests pour les nouveaux codes HTTP (401, 403, 408, 429)
3. [ ] Documentation: Décrire les codes d'erreur possibles

**Fichiers à vérifier:**
- `ezkey-cli-python/ezkey_cli/utils/http_client.py` (ligne ~89)

---

### Phase 6: TUI CLI - Validation (PRIORITY: P1)

**Livrables:**
1. [ ] Repérer la gestion des erreurs du TUI
2. [ ] S'assurer que le TUI hérite du HttpClient upgradé
3. [ ] Tests: Vérifier affichage erreurs pour chaque code
4. [ ] Documenter les cas d'erreur

**À trouver:**
- Où est le TUI login ? (`commands/login.py` ?)
- Comment affiche-t-il les erreurs ?

---

### Phase 7: Démo App ACME (`ezkey-demo-app-acme/`) (PRIORITY: P2)

**Livrables:**
1. [ ] Analyser `LoginController.java` (JWT generation client-side)
2. [ ] S'assurer qu'elle gère RFC 9457 (ou adaptée pour un client web)
3. [ ] Tester: Erreurs d'authentification affichées correctement
4. [ ] Documentation: Exemples des réponses d'erreur

**Cas d'usage spécifique:**
- La démo app utilise probablement axios ou Fetch API
- Elle doit interpréter:
  - Codes HTTP: 401, 403, 408, 429, 500
  - Formats: RFC 9457 avec `detail` + `title`

---

### Phase 8: Tests Unitaires et d'Intégration (PRIORITY: P1)

**Tests AdminAuthService:**
- [ ] Test: Username introuvable → AdminAuthenticationException
- [ ] Test: Account inactive → AdminAccountInactiveException
- [ ] Test: Tenant inactive → TenantInactiveException
- [ ] Test: No enrollment → AdminNoEnrollmentException
- [ ] Test: Device rejected → AdminAuthenticationRejectedException
- [ ] Test: Invalid signature → AdminDeviceSignatureInvalidException
- [ ] Test: Timeout → AdminAuthenticationTimeoutException
- [ ] Test: Expired → AdminAuthenticationExpiredException

**Tests GlobalExceptionHandler:**
- [ ] Test: Chaque exception mappe au bon code HTTP
- [ ] Test: ResponseEntity<ProblemDetail> inclut type, title, detail
- [ ] Test: Type URI correct pour chaque exception

**Tests d'intégration (Controller):**
- [ ] POST /api/v1/admin/auth/login avec credentials invalides
- [ ] Vérifier HTTP 401 + ProblemDetail retourné
- [ ] (Répéter pour chaque erreur)

---

### Phase 9: Documentation (PRIORITY: P2)

**Livrables:**

1. [ ] **JavaDoc Complète & Alignement Swagger:**
   - Toutes les exception classes: Description + exemple RFC 9457
   - Tous les handlers GlobalExceptionHandler: Description + HTTP status + exemple
   - Méthode login AdminAuthController: Description + tous les codes d'erreur + @throws
   - Vérifier que Swagger/OpenAPI annotations sont **synchronisées avec JavaDoc**

2. [ ] **Révision Documentation Markdown - Points Clés:**
   - [ ] **docs/ADMIN_API_SECURITY_GUIDE.md:**
     - Section: "Authentication Error Codes"
     - Tableau: Code HTTP + Type URI + Signification + Action
     - Exemples cURL/Postman pour chaque cas d'erreur
     - Référence vers RFC 9457

   - [ ] **docs/ENDPOINT.md:**
     - Trouver section "POST /api/v1/admin/auth/login"
     - Ajouter subsection: "Error Responses"
     - Lister tous les codes (400, 401, 403, 408, 429)
     - Exemples JSON RFC 9457 pour chaque code

   - [ ] **docs/ARCHITECTURE.md:**
     - Trouver section "Error Handling"
     - Ajouter note: "Admin Login utilise exceptions spécialisées"
     - Montrer pattern flow: Exception → Handler → ProblemDetail

   - [ ] **README.md (racine):**
     - Trouver section "Authentication"
     - Ajouter: "API errors follow RFC 9457 Problem Details format"
     - Référence vers docs/ADMIN_API_SECURITY_GUIDE.md

3. [ ] **Créer/Mettre à jour docs/RFC9457_MIGRATION_STATUS.md:**
   - Suivi de l'adoption de RFC 9457 par endpoint
   - ✅ POST /api/v1/admin/auth/login (après cette migration)
   - ⏳ Autres endpoints (future work)
   - Explique pourquoi RFC 9457 + avantages pour clients

4. [ ] **CLI Python Documentation:**
   - [ ] **docs/CLI_ERROR_CODES.md (NEW):**
     - Tableau: Commande + Code HTTP → Message affichage CLI
     - Explique comment le CLI traite RFC 9457 vs. legacy
     - Exemples: `ezkey-cli login` → HTTP 401 → affichage utilisateur

   - [ ] **docs/API_KEYS_GUIDE.md (si exists):**
     - Ajouter section: "Error Handling in Clients"
     - HttpClient Python gère RFC 9457 et legacy
     - Exemples Python/TypeScript

5. [ ] **Commentaires InCode:**
   - Tous les manuels throw doivent avoir commentaire expliquant exception levée
   - Why mapper X exception to Y HTTP status
   - Reference vers GlobalExceptionHandler

6. [ ] **Exemples & Templates:**
   - [ ] Exemple cURL complet pour login success
   - [ ] Exemple cURL pour login failure (chaque code)
   - [ ] Exemple Postman collection avec variables
   - [ ] Exemple JavaScript/TypeScript pour gérer réponses

**Checklist Markdown Review:**
- [ ] Fichiers Markdown trouvés et listés (grep pour "login", "authentication", "error")
- [ ] Chaque fichier revu pour pertinence
- [ ] Lien de cohérence entre fichiers vérifiés
- [ ] Aucune documentation obsolète (référence vieilles erreurs)
- [ ] Tous les exemples JSON à jour (RFC 9457)
- [ ] Liens internes corrects (cross-references)

**Format de standardisation Markdown:**
```markdown
## Error Codes

| HTTP Code | Type URI | Title | Detail | Action |
|-----------|----------|-------|--------|--------|
| 401 | `.../invalid-credentials` | Invalid Credentials | "Invalid username or password" | Verify credentials |
| 403 | `.../account-inactive` | Account Inactive | "Account has been deactivated" | Contact admin |
```

---

## 6. Dépendances Entre Phases

```
Phase 1 (Exceptions)
    ↓
Phase 2 (Handlers) → Phase 3 (Service)
    ↓
Phase 4 (Controller)
    ↓
Phase 5/6 (CLI)
    ↓
Phase 7 (Demo)
    ↓
Phase 8 (Tests)
    ↓
Phase 9 (Docs)
```

**Chemin critique:**
1. → 2 → 3 → 4 (Backend changes - blocking)
2. → 5/6 (CLI changes - parallel)
3. → 7 (Demo - après CLI)

---

## 7. Alignement JavaDoc & Swagger/OpenAPI - CRITICAL DETAIL

### Pourquoi l'Alignement est Critique

**Problème:** Source de confusion pour les clients si JavaDoc et Swagger/OpenAPI diffèrent
- Développeurs lisent JavaDoc dans l'IDE
- Clients lisent Swagger dans la doc générée ou UI
- Si messages/exemples différent → confusion + bugs client

**Solution:** Maintenir la "Source of Truth" unique

### Principes d'Alignement

1. **JavaDoc = Source Principale**
   - Écrite dans le code source (plus stable)
   - Plus détaillée (peut inclure contexte technique)
   - Version "authoritative"

2. **Swagger/OpenAPI = Projection de JavaDoc**
   - Annotations qui "résument" la JavaDoc
   - Human-readable pour API clients
   - Version "API consumer"

3. **Format Cohérent**
   - Même terminologie pour les erreurs
   - Mêmes codes HTTP
   - Mêmes exemples JSON (ou transposables)

### Checklist d'Alignement (À Vérifier)

#### Pour chaque Exception:
- [ ] JavaDoc classe inclut: type URI, HTTP status, exemple JSON RFC 9457
- [ ] Aucune annotation sur la classe exception (elle-même)
- [ ] Mentionner où elle est lancée (dans quel service)

#### Pour chaque Handler GlobalExceptionHandler:
- [ ] JavaDoc complète avec exemple ProblemDetail
- [ ] HTTP status en JavaDoc = celui du handler
- [ ] Type URI identique entre JavaDoc et `problem.setType(...)`
- [ ] Title identique entre JavaDoc et `problem.setTitle(...)`

#### Pour chaque Endpoint (ex: login):
- [ ] **JavaDoc @throws:** Lister toutes les exceptions possibles
  ```java
  @throws AdminAuthenticationException maps to 401 Unauthorized
  @throws AdminAccountInactiveException maps to 403 Forbidden
  ```
- [ ] **Swagger @ApiResponse:** Correspondre 1:1 avec les @throws
  ```java
  @ApiResponse(responseCode = "401", description = "...")
  @ApiResponse(responseCode = "403", description = "...")
  ```
- [ ] **Swagger examples:** Montrer JSON RFC 9457 real (copié de tests)

### Outil de Vérification

Avant commit, valider avec:

```bash
# 1. Extraire mentions d'exceptions de JavaDoc
grep -A1 "@throws Admin" AdminAuthService.java

# 2. Extraire codes HTTP de Swagger
grep -A1 "responseCode" AdminAuthController.java

# 3. Vérifier GlobalExceptionHandler
grep -B2 "setType.*admin-authentication" GlobalExceptionHandler.java

# 4. Les trois listes doivent matcher
```

### Documentation Synchronization Tool (Futur)

Créer script/checklist: `docs/JAVADOC_SWAGGER_SYNC_CHECKLIST.md`
- Maintenir un tableau de mapping
- Facilite la review et l'onboarding nouveaux développeurs
- Outil pour checker régulièrement éventuelles dérives

---

## 8. Checklist de Vérification

### Avant déploiement

- [ ] Toutes les exceptions créées et documentées
- [ ] GlobalExceptionHandler updated avec tous les handlers
- [ ] AdminAuthService refactorisé (plus de buildErrorResponse)
- [ ] AdminAuthController Swagger/OpenAPI à jour
- [ ] Tous les tests unitaires passent
- [ ] Tests d'intégration couvrent tous les cas d'erreur
- [ ] CLI Python testé (401, 403, 408, 429)
- [ ] TUI CLI testé (affichage des erreurs)
- [ ] Démo app ACME testé
- [ ] Documentation complète (JavaDoc + guides)

### Après déploiement

- [ ] Monitoring: Vérifier les taux d'erreur attendus
- [ ] User feedback: Les clients gèrent bien RFC 9457
- [ ] Éventuels rollback: Procédure documentée

---

## 9. Risques et Mitigations

| Risque | Probabilité | Impact | Mitigation |
|--------|-------------|--------|-----------|
| CLI clients ne reconnaissent pas RFC 9457 | Faible | Moyen | Le HttpClient Python gère déjà les deux formats |
| Démo app casse | Moyen | Moyen | Tests complets avant merge |
| Rate limiting ne mappe pas bien | Faible | Moyen | Tester cas limite (429) |
| Swagger/OpenAPI obsolète | Moyen | Faible | Auto-générer depuis annotations |

---

## 10. Ressources de Référence

### Exceptions Existantes (pattern à suivre)
- `AdminNotAllowedException` → HTTP 400 + RFC 9457
- `AdminLimitException` → HTTP 400 + RFC 9457
- `TenantInactiveException` → HTTP 403 + RFC 9457
- `RateLimitExceededException` → HTTP 429 + ErrorResponseDto

### GlobalExceptionHandler
- Chemins: `ezkey-admin-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java`
- Pattern: @ExceptionHandler + ResponseEntity<ProblemDetail>

### Documentation RFC 9457
- Spec: RFC 9457 "Problem Details for HTTP APIs"
- Spring Boot support: `java.web.error.include-stacktrace` + ProblemDetail

### Fichiers clés à modifier/créer
```
ezkey-admin-api/src/main/java/org/ezkey/admin/exception/
├── AdminAuthenticationException.java (NEW)
├── AdminAccountInactiveException.java (NEW)
├── AdminNoEnrollmentException.java (NEW)
├── AdminAuthenticationExpiredException.java (NEW)
├── AdminAuthenticationRejectedException.java (NEW)
├── AdminDeviceSignatureInvalidException.java (NEW)
├── AdminAuthenticationTimeoutException.java (NEW)

ezkey-admin-api/src/main/java/org/ezkey/
├── exception/GlobalExceptionHandler.java (MODIFY)

ezkey-admin-api/src/main/java/org/ezkey/admin/service/
├── AdminAuthService.java (MODIFY)

ezkey-admin-api/src/main/java/org/ezkey/admin/controller/
├── AdminAuthController.java (MODIFY - Swagger)

ezkey-cli-python/
├── ezkey_cli/utils/http_client.py (VERIFY - déjà bon)
├── tests/test_.*login.* (UPDATE)

ezkey-demo-app-acme/
├── src/main/java/org/ezkey/demo/acme/controller/LoginController.java (VERIFY/UPDATE)

docs/
├── ADMIN_API_SECURITY_GUIDE.md (UPDATE)
├── RFC9457_MIGRATION_STATUS.md (NEW)
```

---

## 11. Checklist Finale

- [ ] Plan révisé et approuvé
- [ ] Issues créées dans le système de tracking (GitHub)
- [ ] Branche de développement créée
- [ ] Tests unitaires écrits avant les implémentations
- [ ] Code review organisée
- [ ] Merge strategy définie (feature branch → main)
- [ ] Hotfix plan en cas de problème

---

## 12. IMPORTANT: Révision Documentation Markdown Post-Migration

**Cette section doit être complétée APRÈS le déploiement en production.**

### Fichiers Markdown à Revoir Obligatoirement

Une fois que toutes les phases (1-11) sont complétées et validées en production, procéder à une **révision systématique** de tous les fichiers Markdown du projet:

#### À Rechercher et Mettre à Jour

```bash
# Grep pour identifier tous références anciennes formats
grep -r "\"success\": false" docs/
grep -r "\"message\":" docs/ | grep -i "auth\|error\|login"
grep -r "ErrorResponseDto" docs/ | grep -i "auth\|login"
```

#### Fichiers Prioritaires (MUST):

1. **docs/ADMIN_API_SECURITY_GUIDE.md**
   - [ ] Section "Error Handling": Ajouter exemples RFC 9457
   - [ ] Section "Login Errors": Mettre à jour tous exemples
   - [ ] Ajouter tableau: Code HTTP → Type URI → Signification

2. **docs/ENDPOINT.md** (API Reference)
   - [ ] Trouver /admin/auth/login
   - [ ] Mettre à jour tous les exemples d'erreur
   - [ ] Vérifier cohérence avec le nouveau format

3. **docs/ARCHITECTURE.md** (Architecture Docs)
   - [ ] Section "Error Handling": Mettre à jour pattern
   - [ ] Ajouter note: "Admin Login uses exception-based error handling with RFC 9457"
   - [ ] Ajouter diagram ou flowchart Exception → Handler → ProblemDetail

4. **README.md** (Root)
   - [ ] Section "Security": Mentionner RFC 9457
   - [ ] Section "Getting Started": Lien vers ADMIN_API_SECURITY_GUIDE.md
   - [ ] Ajouter badge: "RFC 9457 Compliant"

#### Fichiers Secondaires (SHOULD):

5. **docs/DEVELOPMENT.md** (Dev Guide)
   - [ ] Section "Error Handling": Expliquer pattern pour nouveaux développeurs
   - [ ] Checklist: Comment ajouter nouvelle exception
   - [ ] Template: Exception class + Handler example

6. **docs/MAINTENANCE.md** (Maintenance)
   - [ ] Section "Monitoring": Codes d'erreur à surveiller
   - [ ] Alertes suggest: HTTP 401 spikes = attacks potentiels

7. **docs/API_KEYS_GUIDE.md** (si exists)
   - [ ] Ajouter section: "Error Responses Format"
   - [ ] Exemples JavaScript/Python pour gérer RFC 9457

8. **docs/RFC9457_MIGRATION_STATUS.md** (NEW - à créer)
   - [ ] Tableau: Endpoints + RFC 9457 Status
   - [ ] ✅ POST /api/v1/admin/auth/login
   - [ ] ⏳ Autres endpoints (future phases)
   - [ ] Notes: Avantages et timeline pour d'autres endpoints

#### Fichiers Tertiaires (NICE-TO-HAVE):

9. **docs/CLI_ERROR_CODES.md** (NEW - si CLI exists)
   - [ ] Tableau: Commande CLI → Codes HTTP → Message Affichage
   - [ ] Expliquer transparent backward compatibility

10. **docs/TESTING.md** (Test Guide)
    - [ ] Ajouter test cases pour les 7 nouvelles exceptions
    - [ ] Exemples: cURL commands pour reproduire chaque erreur
    - [ ] Validation: Vérifier structure RFC 9457 en réponse

11. **Postman/Collection.json** (API Testing)
    - [ ] Mettre à jour tous les examples dans la collection
    - [ ] Tests scripts: Valider format ProblemDetail
    - [ ] POST-request scripts pour chaque cas d'erreur

### Validation Checklist

Après révision Markdown, confirmer:

- [ ] Aucun exemple JSON avec ancien format dans docs/
- [ ] Tous les exemples d'erreur affichent RFC 9457 complet
- [ ] Les URI des types problemDetails.io correspondent aux handlers
- [ ] HTTP status codes matchent entre docs et code
- [ ] Aucun lien cassé (cross-references internes)
- [ ] Tous les fichiers encodés UTF-8 without BOM
- [ ] Formatage Markdown cohérent (linter validé)

### Helper Script (Optionnel)

Créer `scripts/validate-rfc9457-docs.sh`:

```bash
#!/bin/bash
# Valide que tous les exemples JSON dans docs/ utilisent RFC 9457

echo "Checking for old format (\"success\": false)..."
grep -r "\"success\": false" docs/ && echo "❌ Found legacy format!" || echo "✅ No legacy format"

echo "Checking for RFC 9457 format (\"type\": ...)..."
grep -r "\"type\": \"https://ezkey.io" docs/ && echo "✅ RFC 9457 format found" || echo "⚠️ No RFC 9457 examples"

echo "Checking for matching status codes..."
# Compare HTTP status în docs vs. code
```

### Timeline Follow-up

- **Week 1:** First pass - priority files (1-4)
- **Week 2:** Second pass - secondary files (5-8)
- **Week 3:** Final validation - tertiary files (9-11)
- **Week 4:** Script validation + final checks

### Notes pour Future Migrations RFC 9457

Ce document servira de **template pour les futures migrations d'autres endpoints** vers RFC 9457. Points clés à réutiliser:

1. Pattern: Exceptions spécifiques → GlobalExceptionHandler → ProblemDetail
2. Alignement JavaDoc ↔ Swagger obligatoire
3. Révision Markdown **complète et systématique** après déploiement
4. Phase phases stratégiques pour risque minimal

---

## Appendix A: Exemple RFC 9457 Complète

### Réponse d'erreur (401 Unauthorized)

```json
{
  "type": "https://ezkey.io/problems/authentication/invalid-credentials",
  "title": "Invalid Credentials",
  "status": 401,
  "detail": "Invalid username or password",
  "instance": "/api/v1/admin/auth/login",
  "timestamp": "2025-02-10T14:30:00Z"
}
```

### Réponse d'erreur (403 Forbidden - Account Inactive)

```json
{
  "type": "https://ezkey.io/problems/authentication/account-inactive",
  "title": "Account Inactive",
  "status": 403,
  "detail": "Account has been deactivated",
  "instance": "/api/v1/admin/auth/login",
  "timestamp": "2025-02-10T14:30:00Z"
}
```

### Réponse d'erreur (408 Request Timeout)

```json
{
  "type": "https://ezkey.io/problems/authentication/auth-timeout",
  "title": "Authentication Timeout",
  "status": 408,
  "detail": "No device response within timeout period",
  "instance": "/api/v1/admin/auth/login",
  "timestamp": "2025-02-10T14:30:00Z"
}
```

---

**EOF**
