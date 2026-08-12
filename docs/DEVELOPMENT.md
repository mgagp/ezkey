# Ezkey Development Guide

## Table of Contents
1. [OpenAPI Documentation](#openapi-documentation)
2. [Testing Strategy](#testing-strategy)
3. [Development Workflow](#development-workflow)
4. [Quality Assurance](#quality-assurance)

---

## OpenAPI Documentation

### Overview
This document covers the development workflow and documentation practices for Ezkey's backend-first cryptographic MFA platform.

### Current State
- **OpenAPI support**: SpringDoc-based API documentation is part of the development workflow.
- **Scope**: Admin, mobile, and integration-facing API surfaces evolve over time and should be documented from the codebase rather than maintained as fixed counts here.
- **Status**: Treat this guide as workflow guidance, not as an authoritative inventory of current endpoints.

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
        description = "REST API for Ezkey - Open Source Cryptographic MFA Platform",
        contact = @Contact(
            name = "Ezkey Team",
            email = "info@ezkey.org",
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
springdoc.swagger-ui.path=/swagger-ui/index.html
springdoc.swagger-ui.operationsSorter=method
# Do not set tagsSorter=alpha — curated OpenAPI tags[] order must remain visible
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

#### Validation and constraint violations (HTTP 400)

Validation failures must return **HTTP 400** (not 500). Apply defense in depth:

- **DTO layer**: Use Bean Validation annotations (`@NotNull`, `@NotBlank`, etc.).
- **Controller layer**: Use `@Valid` on `@RequestBody` (and nested DTOs) so validation happens before persistence.
- **Exception mapping**: Ensure `GlobalExceptionHandler` maps:
  - `MethodArgumentNotValidException` → 400 (field-level validation errors)
  - `DataIntegrityViolationException` → 400 (database constraint violations as a safety net)

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
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }
}
```

## Development Workflow

### Code Quality Standards
- **Java 25+**: Use modern Java features
- **Spring Boot 3.x**: Follow Spring Boot best practices
- **Maven**: Use Maven for dependency management
- **UTF-8**: All files in UTF-8 without BOM
- **Javadoc**: Complete and detailed documentation

### Windows Bash Selection (Git for Windows only)

For this repository on Windows, Bash commands must run with **Git Bash from Git for Windows**.

- Required binary: `C:\Program Files\Git\bin\bash.exe`
- Forbidden for repo scripts: `C:\Windows\System32\bash.exe` (WSL shim)

When running from PowerShell or another Windows-hosted shell, always use:

```powershell
& "C:\Program Files\Git\bin\bash.exe" -lc '<command>'
```

Quick verification command:

```powershell
& "C:\Program Files\Git\bin\bash.exe" -lc 'command -v bash; uname -a'
```

Expected identity is `MINGW`/`MSYS` (Git Bash), not WSL Linux.

### Reliable Local Maven Validation

For autonomous local validation after substantive Java changes, use the repository-root Bash flow
below before any targeted module optimization:

```bash
mvn spotless:apply
mvn checkstyle:check
mvn clean
mvn install -DskipTests
```

This is the most reliable path because Checkstyle depends on the internal `checkstyle-config`
module from the Maven reactor. **`./scripts/build.sh`** is the single portable entrypoint (Git Bash
on Windows, Linux, or macOS). It auto-detects the canonical JDK 25 path on the maintainer Windows
workstation when `JAVA_HOME` is unset. From a Windows-hosted agent shell (PowerShell), invoke Git
Bash explicitly: `& "C:\Program Files\Git\bin\bash.exe" -lc './scripts/build.sh'`.

### Docker-only Java validation (no host JDK/Maven)

When you only have Docker (or want a reproducible path on QA machines), use:

```bash
./scripts/build-docker.sh
```

This mirrors `./scripts/build.sh`: it runs `spotless:apply` against the bind-mounted checkout, then
builds the Docker `build-validation` target in [`docker/Dockerfile`](../docker/Dockerfile) (Spotless
check, Checkstyle, clean, `install -DskipTests`, `test` excluding `ezkey-tests`). On **Git Bash for
Windows**, the script sets `MSYS_NO_PATHCONV` so `docker run -w /workspace` is not rewritten to a
host path.

Options:

- `--check-only` — skip `spotless:apply`; validation only (reports formatting drift without rewriting files).
- `--no-cache` — pass `--no-cache` to the validation `docker build`.
- `--diagnose-only` — print Docker/compose discovery and exit.

Maven cache notes:

- Image builds and the `build-validation` target use **BuildKit** cache mounts (`RUN --mount=type=cache` in the Dockerfile). That is not a Docker named volume.
- `spotless:apply` uses a **named volume** for container `/root/.m2` (default `ezkey-maven-spotless-cache`; override with `EZKEY_SPOTLESS_MAVEN_VOLUME`).

Only after that baseline succeeds should you run targeted follow-up commands, for example:

```bash
mvn test -pl '!ezkey-tests'
mvn test -pl 'ezkey-admin-api,!ezkey-tests'
mvn test -pl 'ezkey-auth-api,!ezkey-tests'
```

### Maven Version Management and Branch Isolation

Ezkey uses **CI-friendly Maven versions** with branch-specific qualifiers to prevent artifact collisions when working on multiple branches locally.

#### How It Works

- **Root POM version**: `${revision}${buildQualifier}${changelist}`
  - `revision`: Base version (default: `0.0.1`)
  - `buildQualifier`: Branch-specific qualifier (empty on `main`, `-<branch-name>` on feature branches)
  - `changelist`: Suffix (default: `-SNAPSHOT`)
- **Default behavior**: `.mvn/maven.config` provides defaults, so `mvn clean install` works without wrappers
- **Branch isolation**: Wrapper scripts inject `buildQualifier` based on git branch name, ensuring unique artifact coordinates per branch

#### Building Without Collisions

Use Bash on every platform, including Git Bash on Windows:

```bash
./scripts/mvn-branch.sh clean install
```

**Standard Maven (works, but no branch isolation):**
```bash
mvn clean install
```

#### How Branch Qualifiers Are Generated

- **Feature branches**: `0.0.1-feature-login-SNAPSHOT` (branch name sanitized)
- **Main/master**: `0.0.1-SNAPSHOT` (no qualifier)
- **Detached HEAD**: `0.0.1-abc1234-SNAPSHOT` (fallback to git SHA)

#### IDE Configuration

For IntelliJ IDEA or other IDEs, you can configure Maven to use the wrapper:

1. **IntelliJ IDEA**: Settings → Build, Execution, Deployment → Build Tools → Maven → Runner
   - Add VM options: `-DbuildQualifier=-$(git rev-parse --abbrev-ref HEAD)` (bash) or use the wrapper script path

2. **Alternative**: Use the wrapper script as your Maven executable path in IDE settings

#### Release Process (Future)

When preparing for Maven Central releases:

**Release Workflow:**
1. **Tag creation**: Create git tag `vX.Y.Z` (e.g., `v1.2.0`) on `main` branch
2. **CI build**: GitHub Actions detects tag and builds with:
   - `-Drevision=X.Y.Z`
   - `-Dchangelist=` (empty, removes `-SNAPSHOT`)
   - `-DbuildQualifier=` (empty)
3. **Deployment**: Artifacts deployed to Maven Central with clean, resolved versions
4. **Post-release**: `main` branch version bumped to next development line (e.g., `1.3.0-SNAPSHOT`)

**Maven Central Requirements (to be implemented):**
- GPG signing of artifacts
- Sources and Javadoc JARs
- Reproducible builds
- Proper `scm`/`licenses`/`developers` metadata in POMs
- Flatten plugin for deployed POMs (ensures no `${revision}` expressions in published artifacts)

**Branch Conventions:**
- **`main`**: Always `X.Y.Z-SNAPSHOT` (next development version)
- **Feature branches**: Use branch-specific qualifiers locally (e.g., `0.0.1-feature-login-SNAPSHOT`)
- **Release branches** (optional, for maintenance): `release/X.Y` for patch releases (`X.Y.(Z+1)`)
- **No branch-specific published versions**: Feature branches do not publish to Maven Central

See plan in `.cursor/plans/maven_pom_versioning_no_branch_collisions_3e7551eb.plan.md` for complete details.

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

### MapStruct compile warnings
MapStruct runs as a javac annotation processor. Several API mappers use
`unmappedTargetPolicy = ReportingPolicy.WARN`, so unmapped target properties produce **compiler
warnings** during `mvn compile` (for example: `Unmapped target property: "fieldName"`).

**Team convention:**
- Run a **full reactor** compile from the repository root when checking for these warnings (for
  example `mvn clean compile`), then scan the log for `Unmapped target` / `Unmapped source`.
- For each warning, confirm whether the gap is **intentional** (field filled elsewhere: controller
  security context, service layer, JPA callbacks). If intentional, add an explicit
  `@Mapping(target = "...", ignore = true)` on the mapping method and document why in Javadoc.
  If not intentional, add a real mapping or fix the model.
- **`ReportingPolicy.ERROR`** is optional hardening for a mapper once all intentional gaps are
  explicit; until then, **WARN** keeps the build green while still surfacing new omissions.

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
