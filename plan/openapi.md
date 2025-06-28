# Plan d'Introduction de la Documentation OpenAPI - Projet Ezkey

## Vue d'ensemble

Ce document présente un plan complet pour introduire la documentation OpenAPI (Swagger) dans le projet Ezkey afin de documenter l'ensemble des APIs REST des controllers.

## État Actuel

### Controllers Identifiés
1. **IntegrationController** (`/api/v1/integrations`) - 6 endpoints
2. **EnrollmentController** (`/api/v1/enrollments`) - 8 endpoints  
3. **AuthAttemptController** (`/api/v1/authattempts`) - 7 endpoints

### Dépendances Existantes
- SpringDoc OpenAPI UI déjà configuré dans `pom.xml` (version 2.5.0)

## Phase 1: Configuration de Base OpenAPI

### 1.1 Configuration OpenAPI Globale

**Fichier:** `src/main/java/org/ezkey/config/OpenApiConfig.java`

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Ezkey API",
        version = "1.0.0",
        description = "API REST pour Ezkey - Alternative Open Source MFA/Passkey",
        contact = @Contact(
            name = "Équipe Ezkey",
            email = "contributors@ezkey.org",
            url = "https://ezkey.org"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Serveur de développement"),
        @Server(url = "https://api.ezkey.org", description = "Serveur de production")
    }
)
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("bearerAuth", 
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT Token d'authentification")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

### 1.2 Configuration Application Properties

**Fichier:** `src/main/resources/application.properties`

```properties
# Configuration OpenAPI/Swagger
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.doc-expansion=none
springdoc.swagger-ui.disable-swagger-default-url=true
springdoc.swagger-ui.custom-site-title=Ezkey API Documentation
```

## Phase 2: Annotations OpenAPI sur les Controllers

### 2.1 IntegrationController

```java
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "API de gestion des intégrations")
public class IntegrationController {

    @Operation(summary = "Récupérer toutes les intégrations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Liste récupérée avec succès"),
        @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    @GetMapping
    public ResponseEntity<List<IntegrationResponse>> getAll() { ... }

    @Operation(summary = "Récupérer une intégration par ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Intégration trouvée"),
        @ApiResponse(responseCode = "404", description = "Intégration non trouvée")
    })
    @GetMapping("/{id}")
    public ResponseEntity<IntegrationResponse> getById(
        @Parameter(description = "ID de l'intégration", example = "1") 
        @PathVariable Integer id) { ... }

    @Operation(summary = "Créer une nouvelle intégration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Intégration créée avec succès"),
        @ApiResponse(responseCode = "400", description = "Données invalides")
    })
    @PostMapping
    public ResponseEntity<IntegrationResponse> create(
        @Parameter(description = "Données de l'intégration", required = true)
        @Valid @RequestBody IntegrationCreateRequest request) { ... }

    // ... autres méthodes avec annotations similaires
}
```

### 2.2 EnrollmentController

```java
@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "API de gestion des enrollments")
public class EnrollmentController {

    @Operation(summary = "Lier un enrollment à un appareil")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Liaison initiée avec succès"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "409", description = "Conflit d'état")
    })
    @GetMapping("/bind/{id}")
    public ResponseEntity<EzkeyEnrollmentBindResponse> bind(
        @Parameter(description = "ID de l'enrollment", example = "1")
        @PathVariable Integer id) { ... }

    @Operation(summary = "Confirmer un enrollment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Enrollment confirmé"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "409", description = "Conflit d'état")
    })
    @PostMapping("/confirm")
    public ResponseEntity<EzkeyEnrollmentConfirmResponse> confirm(
        @Parameter(description = "Données de confirmation", required = true)
        @Valid @RequestBody EzkeyEnrollmentConfirmRequest req) { ... }

    // ... autres méthodes avec annotations similaires
}
```

### 2.3 AuthAttemptController

```java
@RestController
@RequestMapping("/api/v1/authattempts")
@Tag(name = "Auth Attempts", description = "API de gestion des tentatives d'authentification")
public class AuthAttemptController {

    @Operation(summary = "Initier une tentative d'authentification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tentative initiée avec succès"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "409", description = "Conflit d'état")
    })
    @PostMapping("/initiate/{id}")
    public ResponseEntity<EzkeyAuthAttemptInitiateResponseDto> initiate(
        @Parameter(description = "ID de l'enrollment", example = "1")
        @PathVariable Integer id,
        @Parameter(description = "Données d'initiation", required = true)
        @Valid @RequestBody EzkeyAuthAttemptInitiateRequestDto request) { ... }

    @Operation(summary = "Compléter une tentative d'authentification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tentative complétée"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "409", description = "Conflit d'état")
    })
    @PostMapping("/complete/{id}")
    public ResponseEntity<EzkeyAuthAttemptCompleteResponseDto> complete(
        @Parameter(description = "ID de l'enrollment", example = "1")
        @PathVariable Integer id,
        @Parameter(description = "Données de completion", required = true)
        @Valid @RequestBody EzkeyAuthAttemptCompleteRequestDto request) { ... }

    // ... autres méthodes avec annotations similaires
}
```

## Phase 3: Documentation des DTOs

### 3.1 DTOs de Requête

```java
@Schema(description = "Requête de création d'une intégration")
public class IntegrationCreateRequest {
    
    @Schema(description = "Code unique de l'intégration", example = "GOOGLE_AUTH", required = true)
    @NotBlank(message = "Le code est obligatoire")
    private String code;
    
    @Schema(description = "URL du logo de l'intégration", example = "https://example.com/logo.png")
    private String logo;
}
```

### 3.2 DTOs de Réponse

```java
@Schema(description = "Réponse contenant les détails d'une intégration")
public class IntegrationResponse {
    
    @Schema(description = "ID unique de l'intégration", example = "1")
    private Integer id;
    
    @Schema(description = "Code unique de l'intégration", example = "GOOGLE_AUTH")
    private String code;
    
    @Schema(description = "Statut actif de l'intégration", example = "true")
    private Boolean active;
    
    @Schema(description = "Date de création", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;
}
```

## Phase 4: Gestion des Erreurs

### 4.1 DTO d'Erreur Standardisé

```java
@Schema(description = "Réponse d'erreur standardisée")
public class ErrorResponse {
    
    @Schema(description = "Code d'erreur", example = "RESOURCE_NOT_FOUND")
    private String code;
    
    @Schema(description = "Message d'erreur", example = "L'intégration avec l'ID 123 n'a pas été trouvée")
    private String message;
    
    @Schema(description = "Timestamp de l'erreur", example = "2025-01-15T10:30:00")
    private LocalDateTime timestamp;
    
    @Schema(description = "Chemin de la requête", example = "/api/v1/integrations/123")
    private String path;
}
```

### 4.2 Documentation des Erreurs Globales

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ApiResponse(responseCode = "404", description = "Ressource non trouvée")
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Données de requête invalides")
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) { ... }
}
```

## Phase 5: Tests et Validation

### 5.1 Tests d'Intégration OpenAPI

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldGenerateOpenApiSpecification() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("openapi");
        assertThat(response.getBody()).contains("Ezkey API");
    }

    @Test
    void shouldServeSwaggerUI() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui.html", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }
}
```

## Phase 6: Documentation Utilisateur

### 6.1 README API

```markdown
# Documentation API Ezkey

## Accès à la Documentation

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/api-docs
- **OpenAPI YAML:** http://localhost:8080/api-docs.yaml

## Endpoints Principaux

### Integrations
- `GET /api/v1/integrations` - Liste des intégrations
- `GET /api/v1/integrations/{id}` - Détails d'une intégration
- `POST /api/v1/integrations` - Créer une intégration
- `PUT /api/v1/integrations/{id}` - Mettre à jour une intégration
- `DELETE /api/v1/integrations/{id}` - Supprimer une intégration

### Enrollments
- `GET /api/v1/enrollments` - Liste des enrollments
- `POST /api/v1/enrollments` - Créer un enrollment
- `GET /api/v1/enrollments/bind/{id}` - Lier un enrollment
- `POST /api/v1/enrollments/confirm` - Confirmer un enrollment

### Auth Attempts
- `GET /api/v1/authattempts` - Liste des tentatives
- `POST /api/v1/authattempts/initiate/{id}` - Initier une tentative
- `POST /api/v1/authattempts/complete/{id}` - Compléter une tentative

## Codes d'Erreur
- `200` - Succès
- `201` - Créé avec succès
- `400` - Données invalides
- `404` - Ressource non trouvée
- `409` - Conflit
- `500` - Erreur interne
```

## Plan d'Implémentation

### Semaine 1: Configuration de Base
- [ ] Créer `OpenApiConfig.java`
- [ ] Configurer `application.properties`
- [ ] Tester l'accès à Swagger UI

### Semaine 2: Documentation des Controllers
- [ ] Annoter `IntegrationController`
- [ ] Annoter `EnrollmentController`
- [ ] Annoter `AuthAttemptController`

### Semaine 3: Documentation des DTOs
- [ ] Annoter tous les DTOs de requête
- [ ] Annoter tous les DTOs de réponse
- [ ] Créer `ErrorResponse`

### Semaine 4: Tests et Validation
- [ ] Créer `OpenApiIntegrationTest`
- [ ] Valider le schéma OpenAPI
- [ ] Tester tous les endpoints via Swagger UI

### Semaine 5: Documentation et Déploiement
- [ ] Créer `API_README.md`
- [ ] Configurer l'environnement de production
- [ ] Former l'équipe

## Métriques de Succès

- [ ] 100% des endpoints documentés (21 endpoints)
- [ ] 100% des DTOs annotés
- [ ] Tests d'intégration passants
- [ ] Documentation accessible en production
- [ ] Formation de l'équipe complétée

## Maintenance Continue

- Mettre à jour la documentation lors de l'ajout de nouveaux endpoints
- Réviser régulièrement les exemples et descriptions
- Surveiller l'utilisation de la documentation
- Collecter les retours utilisateurs

---

**Note:** Ce plan peut être adapté selon les besoins spécifiques du projet. 