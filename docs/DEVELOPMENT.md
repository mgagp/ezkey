# Ezkey Development Guide

## Table of Contents
1. [OpenAPI Documentation](#openapi-documentation)
2. [Testing Strategy](#testing-strategy)
3. [Development Workflow](#development-workflow)
4. [Quality Assurance](#quality-assurance)

---

## OpenAPI Documentation

### Overview
This section covers the complete OpenAPI (Swagger) documentation strategy for Ezkey REST APIs across all controllers.

### Current State
- **Controllers Identified**: 3 main controllers with 21 endpoints total
- **Dependencies**: SpringDoc OpenAPI UI already configured (version 2.5.0)
- **Status**: Basic configuration in place, needs comprehensive documentation

### Controllers and Endpoints

#### IntegrationController (`/api/v1/integrations`) - 6 endpoints
- `GET /api/v1/integrations` - List all integrations
- `GET /api/v1/integrations/{id}` - Get integration by ID
- `POST /api/v1/integrations` - Create new integration
- `PUT /api/v1/integrations/{id}` - Update integration
- `DELETE /api/v1/integrations/{id}` - Delete integration
- `GET /api/v1/integrations/{id}/enrollments` - Get integration enrollments

#### EnrollmentController (`/api/v1/enrollments`) - key operations
- `GET /api/v1/enrollments` - List all enrollments (admin scope)
- `POST /api/v1/enrollments` - Create new enrollment (admin scope)
- `GET /api/v1/enrollments/{id}` - Get enrollment by ID (admin scope)
- `PUT /api/v1/enrollments/{id}` - Update enrollment (admin scope)
- `DELETE /api/v1/enrollments/{id}` - Delete enrollment (admin scope)
- `POST /api/v1/enrollments/bind` - Bind enrollment to device using proof token payload (auth scope)
- `POST /api/v1/enrollments/verify` - Complete enrollment verification (auth scope)
- `GET /api/v1/enrollments/{id}/auth-attempts` - Get enrollment auth attempts (admin scope)

#### AuthAttemptController (`/api/v1/auth-attempts`) - key operations
- `GET /api/v1/auth-attempts` - List all auth attempts (admin scope)
- `POST /api/v1/auth-attempts` - Create auth attempt (admin scope)
- `GET /api/v1/auth-attempts/{id}` - Get auth attempt by ID (admin scope)
- `GET /api/v1/auth-attempts/{id}/wait` - Wait for authentication completion (admin scope)
- `DELETE /api/v1/auth-attempts/{id}` - Delete auth attempt (admin scope)
- `POST /api/v1/auth-attempts/pending` - Retrieve pending request using proof payload (auth scope)
- `POST /api/v1/auth-attempts/respond` - Submit decision for pending request (auth scope)

### OpenAPI Configuration

#### Global Configuration
```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Ezkey API",
        version = "1.0.0",
        description = "REST API for Ezkey - Open Source MFA/Passkey Alternative",
        contact = @Contact(
            name = "Ezkey Team",
            email = "contributors@ezkey.org",
            url = "https://ezkey.org"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development server"),
        @Server(url = "https://api.ezkey.org", description = "Production server")
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
                        .description("JWT Authentication Token")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

#### Application Properties
```properties
# OpenAPI/Swagger Configuration
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.doc-expansion=none
springdoc.swagger-ui.disable-swagger-default-url=true
springdoc.swagger-ui.custom-site-title=Ezkey API Documentation
```

### Controller Documentation Examples

#### IntegrationController Documentation
```java
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "Integration management API")
public class IntegrationController {

    @Operation(summary = "Get all integrations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<List<IntegrationResponse>> getAll() { ... }

    @Operation(summary = "Get integration by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Integration found"),
        @ApiResponse(responseCode = "404", description = "Integration not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<IntegrationResponse> getById(
        @Parameter(description = "Integration ID", example = "1") 
        @PathVariable Integer id) { ... }

    @Operation(summary = "Create new integration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Integration created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data")
    })
    @PostMapping
    public ResponseEntity<IntegrationResponse> create(
        @Parameter(description = "Integration data", required = true)
        @Valid @RequestBody IntegrationCreateRequest request) { ... }
}
```

### DTO Documentation

#### Required fields: Bean Validation + `@Schema(required = true)`

Ezkey intentionally uses **both** Bean Validation annotations (e.g., `@NotNull`, `@NotBlank`) and
`@Schema(required = true)` on required DTO fields:

- Springdoc infers “required” from Bean Validation automatically.
- `@Schema(required = true)` is kept for **explicitness and consistency** across the codebase.

#### Request DTOs
```java
@Schema(description = "Request to create a new integration")
public class IntegrationCreateRequest {
    
    @Schema(description = "Unique integration code", example = "GOOGLE_AUTH", required = true)
    @NotBlank(message = "Code is required")
    private String code;
    
    @Schema(description = "Integration logo URL", example = "https://example.com/logo.png")
    private String logo;
}
```

#### Response DTOs
```java
@Schema(description = "Response containing integration details")
public class IntegrationResponse {
    
    @Schema(description = "Unique integration ID", example = "1")
    private Integer id;
    
    @Schema(description = "Unique integration code", example = "GOOGLE_AUTH")
    private String code;
    
    @Schema(description = "Integration active status", example = "true")
    private Boolean active;
    
    @Schema(description = "Creation date", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;
}
```

### Error Handling Documentation
```java
@Schema(description = "Standardized error response")
public class ErrorResponse {
    
    @Schema(description = "Error code", example = "RESOURCE_NOT_FOUND")
    private String code;
    
    @Schema(description = "Error message", example = "Integration with ID 123 not found")
    private String message;
    
    @Schema(description = "Error timestamp", example = "2025-01-15T10:30:00")
    private LocalDateTime timestamp;
    
    @Schema(description = "Request path", example = "/api/v1/integrations/123")
    private String path;
}
```

### OpenAPI Testing
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

## Development Workflow

### Code Quality Standards
- **Java 21+**: Use modern Java features
- **Spring Boot 3.x**: Follow Spring Boot best practices
- **Maven**: Use Maven for dependency management
- **UTF-8**: All files in UTF-8 without BOM
- **Javadoc**: Complete and detailed documentation

### Git Workflow
- **Conventional Commits**: Use conventional commit messages
- **Atomic Commits**: Keep commits focused and atomic
- **Issue Numbers**: Include issue numbers when applicable
- **Branch Strategy**: Feature branches for new development

### Code Review Process
- **Automated Checks**: CI/CD pipeline validation
- **Manual Review**: Peer review for all changes
- **Security Review**: Security-focused review for sensitive changes
- **Documentation**: Ensure documentation is updated

---

## Quality Assurance

### Continuous Integration
- **Build Validation**: Automated Maven builds
- **Test Execution**: Automated test suite execution
- **Code Coverage**: Coverage reporting and thresholds
- **Security Scanning**: Automated security vulnerability scanning

### Code Quality Metrics
- **Test Coverage**: Minimum 90% line coverage
- **Code Complexity**: Cyclomatic complexity limits
- **Code Duplication**: Duplicate code detection
- **Technical Debt**: SonarQube analysis

### Performance Monitoring
- **Response Times**: API response time monitoring
- **Memory Usage**: Memory consumption tracking
- **Database Performance**: Query performance analysis
- **Error Rates**: Error rate monitoring and alerting

### Security Validation
- **Dependency Scanning**: Third-party vulnerability scanning
- **Code Analysis**: Static code analysis for security issues
- **Penetration Testing**: Regular security testing
- **Compliance**: Security compliance validation

---

## 📖 Related Documentation

- **[Architecture & Security](ARCHITECTURE.md)** - System architecture and security design
- **[API Endpoints](ENDPOINT.md)** - Complete API documentation
- **[Cryptographic Implementation](CRYPTO.md)** - Detailed crypto specifications
- **[Main Project README](../README.md)** - Project overview and quick start
- **[Monitoring Setup](monitoring/README.md)** - Production monitoring guide
- **[Documentation Templates](dev-tools/README_TEMPLATE.md)** - Standardized README template

---

*This document consolidates the OpenAPI documentation strategy and comprehensive testing approach for the Ezkey project. It serves as the definitive guide for development practices and quality assurance.*
