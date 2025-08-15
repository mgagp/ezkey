# Plan d'Implémentation - Bean Validation pour ezkey-core
*Date: 14 Août 2025*  
*Module: ezkey-core*  
*Objectif: Implémenter Bean Validation sur tous les DTOs*  
*Priorité: P1 - Critique (Sécurité)*

## Résumé Exécutif

Ce plan détaille l'implémentation de Bean Validation sur tous les DTOs du module `ezkey-core` pour adresser la recommandation P1 du code review. L'objectif est de sécuriser les données d'entrée et d'éliminer la validation manuelle dans les services.

## Analyse des DTOs Identifiés

### DTOs de Requête (Request) - Validation Critique
1. **EnrollmentCreateRequest** - Création d'enrollment
2. **EnrollmentBindRequest** - Liaison d'enrollment
3. **EnrollmentVerifyRequest** - Vérification d'enrollment
4. **AuthAttemptCreateRequest** - Création de tentative d'auth
5. **AuthAttemptPendingRequest** - Requête de tentatives en attente
6. **AuthAttemptRespondRequest** - Réponse à une tentative d'auth
7. **IntegrationCreateRequest** - Création d'intégration
8. **IntegrationI18nCreate** - Création d'internationalisation

### DTOs de Réponse (Response) - Pas de validation nécessaire
- Tous les DTOs de réponse sont générés par le système et ne nécessitent pas de validation

## Plan d'Implémentation Détaillé

### Phase 1: Préparation et Configuration (Jour 1)

#### Tâche 1.1: Vérifier les dépendances Bean Validation
- [ ] Vérifier que `spring-boot-starter-validation` est présent dans `pom.xml`
- [ ] Ajouter les imports nécessaires pour les annotations de validation
- [ ] Configurer les messages d'erreur de validation

#### Tâche 1.2: Étendre GlobalExceptionHandler
- [ ] Ajouter la gestion de `MethodArgumentNotValidException`
- [ ] Créer des messages d'erreur standardisés
- [ ] Tester la gestion des erreurs de validation

### Phase 2: Implémentation des Validations (Jours 2-3)

#### Tâche 2.1: EnrollmentCreateRequest
**Fichier:** `ezkey-core/src/main/java/org/ezkey/enrollment/domain/EnrollmentCreateRequest.java`

```java
package org.ezkey.enrollment.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EnrollmentCreateRequest {

    @NotNull(message = "Integration ID is required")
    private Integer integrationId;

    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 64, message = "Name must be between 1 and 64 characters")
    private String name;

    private Boolean authAttemptChallengeRequired;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@NotNull` pour `integrationId`
- `@NotBlank` et `@Size` pour `name`

#### Tâche 2.2: EnrollmentBindRequest
**Fichier:** `ezkey-core/src/main/java/org/ezkey/enrollment/domain/EnrollmentBindRequest.java`

```java
package org.ezkey.enrollment.domain;

import jakarta.validation.constraints.NotNull;

public class EnrollmentBindRequest {

    @NotNull(message = "Enrollment ID is required")
    private Integer enrollmentId;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@NotNull` pour `enrollmentId`

#### Tâche 2.3: EnrollmentVerifyRequest
**Fichier:** `ezkey-core/src/main/java/org/ezkey/enrollment/domain/EnrollmentVerifyRequest.java`

```java
package org.ezkey.enrollment.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public class EnrollmentVerifyRequest {

    @NotNull(message = "Enrollment ID is required")
    private Integer enrollmentId;

    @NotNull(message = "Challenge response is required")
    @Min(value = 100000, message = "Challenge response must be a 6-digit number")
    @Max(value = 999999, message = "Challenge response must be a 6-digit number")
    private Integer challengeResponse;

    @NotBlank(message = "Device public key is required")
    private String devicePublicKey;

    @NotBlank(message = "Enrollment proof token signature is required")
    private String enrollmentProofTokenSigned;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@NotNull` pour `enrollmentId`
- `@NotNull`, `@Min`, `@Max` pour `challengeResponse` (6 chiffres)
- `@NotBlank` pour `devicePublicKey`
- `@NotBlank` pour `enrollmentProofTokenSigned`

#### Tâche 2.4: AuthAttemptCreateRequest
**Fichier:** `ezkey-core/src/main/java/org/ezkey/authattempt/domain/AuthAttemptCreateRequest.java`

```java
package org.ezkey.authattempt.domain;

import jakarta.validation.constraints.NotNull;

public class AuthAttemptCreateRequest {

    @NotNull(message = "Enrollment ID is required")
    private Integer enrollmentId;

    private Boolean challengeRequested;

    // Simulation field - no validation needed for security reasons
    private String simulationDevicePrivateKey;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@NotNull` pour `enrollmentId`
- Pas de validation pour `simulationDevicePrivateKey` (sécurité)

#### Tâche 2.5: AuthAttemptPendingRequest
**Fichier:** `ezkey-core/src/main/java/org/ezkey/authattempt/domain/AuthAttemptPendingRequest.java`

```java
package org.ezkey.authattempt.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public class AuthAttemptPendingRequest {

    @NotNull(message = "Enrollment ID is required")
    private Integer enrollmentId;

    @NotBlank(message = "Device proof token is required")
    private String deviceProofToken;

    @NotBlank(message = "Device proof token signature is required")
    private String deviceProofTokenSigned;

    // Simulation field - no validation needed for security reasons
    private String simulationDevicePrivateKey;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@NotNull` pour `enrollmentId`
- `@NotBlank` pour `deviceProofToken`
- `@NotBlank` pour `deviceProofTokenSigned`

#### Tâche 2.6: AuthAttemptRespondRequest
**Fichier:** `ezkey-core/src/main/java/org/ezkey/authattempt/domain/AuthAttemptRespondRequest.java`

```java
package org.ezkey.authattempt.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public class AuthAttemptRespondRequest {

    @NotNull(message = "Auth attempt ID is required")
    private Integer authAttemptId;

    @NotBlank(message = "Auth attempt proof token signature is required")
    private String authAttemptProofTokenSignedByDevice;

    @Min(value = 100000, message = "Challenge response must be a 6-digit number")
    @Max(value = 999999, message = "Challenge response must be a 6-digit number")
    private Integer authAttemptChallengeResponse;

    @NotNull(message = "Auth attempt acceptance status is required")
    private Boolean authAttemptAccepted;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@NotNull` pour `authAttemptId`
- `@NotBlank` pour `authAttemptProofTokenSignedByDevice`
- `@Min`, `@Max` pour `authAttemptChallengeResponse` (optionnel, 6 chiffres si présent)
- `@NotNull` pour `authAttemptAccepted`

#### Tâche 2.7: IntegrationCreateRequest
**Fichier:** `ezkey-core/src/main/java/org/ezkey/integration/domain/IntegrationCreateRequest.java`

```java
package org.ezkey.integration.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public class IntegrationCreateRequest {

    @Size(max = 255, message = "Logo URL must not exceed 255 characters")
    private String logo;

    @Valid
    private List<IntegrationI18nCreate> i18n;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@Size` pour `logo` (optionnel, max 255 caractères)
- `@Valid` pour `i18n` (validation en cascade)

#### Tâche 2.8: IntegrationI18nCreate
**Fichier:** `ezkey-core/src/main/java/org/ezkey/integration/domain/IntegrationI18nCreate.java`

```java
package org.ezkey.integration.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class IntegrationI18nCreate {

    @NotBlank(message = "Language code is required")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Language code must be in ISO format (e.g., 'en', 'fr', 'en-US')")
    private String language;

    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(min = 1, max = 255, message = "Description must be between 1 and 255 characters")
    private String description;

    // Getters and setters...
}
```

**Validations ajoutées:**
- `@NotBlank` et `@Pattern` pour `language` (format ISO)
- `@NotBlank` et `@Size` pour `name`
- `@NotBlank` et `@Size` pour `description`

### Phase 3: Mise à Jour du GlobalExceptionHandler (Jour 4)

#### Tâche 3.1: Ajouter la gestion des erreurs de validation
**Fichier:** `ezkey-core/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java`

```java
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Existing handlers...

    /**
     * Handles validation errors and returns a standardized 400 Bad Request response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        ErrorResponseDto error = new ErrorResponseDto("VALIDATION_ERROR", errorMessage, request.getDescription(false));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

### Phase 4: Mise à Jour des Services (Jour 5)

#### Tâche 4.1: Supprimer la validation manuelle
**Fichier:** `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`

```java
// SUPPRIMER cette validation manuelle:
// if (request.getIntegrationId() == null){
//     throw new IllegalArgumentException("Integration ID is required");
// }

// La validation sera maintenant gérée automatiquement par Bean Validation
public EnrollmentCreateResponse create(EnrollmentCreateRequest request) {
    // Validation automatique via @Valid dans le controller
    var enrollment = new Enrollment();
    enrollment.setIntegrationId(request.getIntegrationId());
    // ... reste du code
}
```

#### Tâche 4.2: Mettre à jour les autres services
- [ ] Supprimer la validation manuelle dans `AuthAttemptService`
- [ ] Supprimer la validation manuelle dans `IntegrationService`
- [ ] Vérifier qu'aucune validation manuelle ne reste

### Phase 5: Tests et Validation (Jour 6)

#### Tâche 5.1: Créer des tests de validation
**Fichier:** `ezkey-core/src/test/java/org/ezkey/validation/BeanValidationTest.java`

```java
package org.ezkey.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
// ... autres imports

import static org.junit.jupiter.api.Assertions.*;

class BeanValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testEnrollmentCreateRequest_Valid() {
        EnrollmentCreateRequest request = new EnrollmentCreateRequest();
        request.setIntegrationId(1);
        request.setName("Test Enrollment");
        
        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testEnrollmentCreateRequest_Invalid() {
        EnrollmentCreateRequest request = new EnrollmentCreateRequest();
        // Pas d'integrationId - devrait échouer
        
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("integrationId")));
    }

    // Tests similaires pour tous les autres DTOs...
}
```

#### Tâche 5.2: Tests d'intégration
- [ ] Tester les endpoints avec des données invalides
- [ ] Vérifier que les erreurs 400 sont retournées
- [ ] Vérifier que les messages d'erreur sont corrects

### Phase 6: Documentation et Revue (Jour 7)

#### Tâche 6.1: Mettre à jour la documentation
- [ ] Mettre à jour la Javadoc des DTOs avec les nouvelles validations
- [ ] Documenter les messages d'erreur de validation
- [ ] Créer des exemples d'utilisation

#### Tâche 6.2: Revue finale
- [ ] Vérifier que tous les DTOs ont des validations appropriées
- [ ] Confirmer que la validation manuelle a été supprimée
- [ ] Tester l'ensemble du module

## Messages d'Erreur Standardisés

### Messages Génériques
- `"{field} is required"` - Pour les champs obligatoires
- `"{field} must be between {min} and {max} characters"` - Pour les tailles de chaînes
- `"{field} must be a {type}"` - Pour les types de données

### Messages Spécifiques
- `"Integration ID is required"` - EnrollmentCreateRequest
- `"Name must be between 1 and 64 characters"` - EnrollmentCreateRequest
- `"Challenge response must be a 6-digit number"` - Pour les challenges
- `"Language code must be in ISO format (e.g., 'en', 'fr', 'en-US')"` - IntegrationI18nCreate

## Métriques de Succès

### Fonctionnelles
- [ ] 100% des DTOs de requête ont des validations Bean Validation
- [ ] 0 validation manuelle dans les services
- [ ] Tous les endpoints retournent des erreurs 400 pour données invalides

### Qualité
- [ ] Couverture de tests de validation ≥ 90%
- [ ] Messages d'erreur cohérents et informatifs
- [ ] Performance non impactée par la validation

### Sécurité
- [ ] Validation des données d'entrée avant traitement métier
- [ ] Protection contre les injections de données malveillantes
- [ ] Messages d'erreur ne révèlent pas d'informations sensibles

## Risques et Mitigations

### Risques Techniques
- **Performance:** Validation supplémentaire peut impacter les performances
  - *Mitigation:* Tests de performance, optimisation si nécessaire
- **Rétrocompatibilité:** Changements dans les messages d'erreur
  - *Mitigation:* Documentation des changements, tests d'intégration

### Risques de Projet
- **Délais:** Implémentation plus longue que prévu
  - *Mitigation:* Plan détaillé, tests incrémentaux
- **Qualité:** Validations inappropriées ou manquantes
  - *Mitigation:* Revue de code, tests complets

## Livrables

### Code
- [ ] DTOs avec annotations Bean Validation
- [ ] GlobalExceptionHandler étendu
- [ ] Services sans validation manuelle
- [ ] Tests de validation complets

### Documentation
- [ ] Javadoc mise à jour
- [ ] Messages d'erreur documentés
- [ ] Exemples d'utilisation

### Tests
- [ ] Tests unitaires de validation
- [ ] Tests d'intégration des endpoints
- [ ] Tests de performance

## Conclusion

L'implémentation de Bean Validation sur tous les DTOs du module `ezkey-core` permettra de sécuriser les données d'entrée et d'éliminer la validation manuelle dans les services. Ce plan détaillé garantit une implémentation complète et robuste qui respecte les bonnes pratiques de sécurité et de qualité du code.

**Prochaine étape:** Commencer par la Phase 1 (Préparation et Configuration) et procéder de manière incrémentale pour minimiser les risques.
