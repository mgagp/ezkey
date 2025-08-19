# GitHub Copilot Instructions for Ezkey Project

## Project Context
This is a multi-module Spring Boot project providing an open-source MFA/Passkey alternative.  
For overall product requirements and functional constraints, always refer to the top-level file `PRD.txt`.  
For build, setup, and project organization, refer to `README.md`.

## Code Style

### Java Standards
- Use Java 21+ features where appropriate (records, switch expressions, pattern matching)
- Follow idiomatic Spring Boot practices:
  - `@Service` for business logic
  - `@Repository` for persistence  
  - `@RestController` for HTTP APIs
- Use constructor injection rather than field injection
- Methods and classes should be kept small and cohesive
- Favor immutability whenever possible
- For logging, always use SLF4J with `private static final Logger log = LoggerFactory.getLogger(...)`

### Formatting Rules
- **Indentation**: 4 spaces (no tabs)
- **Line Length**: Maximum 120 characters  
- **Encoding**: UTF-8 without BOM
- **Line Endings**: LF (Unix style)

### Naming Conventions
- Classes: PascalCase (e.g., `IntegrationController`)
- Methods and variables: camelCase (e.g., `getIntegrationById`)
- Constants: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_ATTEMPTS`)
- Packages: lowercase (e.g., `org.ezkey.integration`)

## Framework & Libraries

### Core Technologies
- Spring Boot (web, security, data JPA, validation)
- JPA/Hibernate for persistence
- REST controllers return `ResponseEntity<...>`
- Tests use JUnit 5, Spring Boot Test, and Mockito where needed

### Dependency Management
- Use MapStruct for object mapping
- Use Spring Data JPA for persistence
- Prefer constructor injection over field injection
- Use `@Autowired` annotation for clarity

## Project Conventions

### Module Structure
Each sub-project corresponds to a bounded context (e.g., `core`, `api`, `infra`):

```
src/main/java/org/ezkey/{domain}/
├── domain/
│   ├── entity/          # JPA entities
│   └── repository/      # Spring Data repositories
├── service/             # Business logic (application layer)
├── controller/          # REST endpoints (API modules only)
├── dto/
│   ├── request/         # Request DTOs  
│   ├── response/        # Response DTOs
│   └── common/          # Shared DTOs
├── mapper/              # MapStruct mappers
└── exception/           # Custom exceptions
```

### Data Handling
- Domain entities go in the `domain` package, services in `application` or `service`, controllers in `web` or `api`
- SQL or repository queries must be optimized and readable
- DTOs are used to expose data at API boundaries, entities are not exposed directly
- For each DTO there must be a domain object
- Mapping DTO to domain must be done using MapStruct
- Always handle nulls safely, use `Optional` when appropriate

### Error Handling
- Errors should be represented with meaningful exception classes and mapped to HTTP status codes via `@ControllerAdvice`
- Use `ResourceNotFoundException` for 404 scenarios
- Implement global exception handling with `@RestControllerAdvice`
- Return standardized error responses with code, message, and timestamp

### REST API Standards
- Use appropriate HTTP status codes (200, 201, 404, 500)
- Return 201 Created with Location header for POST operations
- Use consistent URL patterns: `/api/v1/{resource}`
- Always return DTOs, never expose entities directly

## Documentation Requirements

### Javadoc Standards
- Add Javadoc for public classes and methods and always include the standard ezkey class header:

```java
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * [Component Type]: [ComponentName]
 * Description: [Brief description of the component's purpose]
 */
```

- Always include `@since 2025` for new classes
- Document all public methods with `@param`, `@return`, and `@throws` where applicable
- Use descriptive comments that explain "why" not "what"

### API Documentation
- Document API endpoints with OpenAPI/Swagger
- Include comprehensive request/response examples
- Document all error codes and scenarios

## Testing Standards

### Test Structure
- Add or update unit/integration tests whenever new logic is introduced
- Write unit tests for services
- Write integration tests for controllers
- Use descriptive test method names
- Follow AAA pattern (Arrange, Act, Assert)

### Coverage Requirements
- Ensure comprehensive test coverage for all core flows
- Test both happy path and error scenarios
- Include validation and edge case testing

## PR Guidance

### Code Generation
- When generating code, respect existing package structure
- Always check `PRD.txt` to ensure new code aligns with product requirements
- Always check `README.md` for project structure, build, and conventions
- When asked for changes, show full modified classes instead of just snippets
- Ensure that code compiles and follows project conventions

### Quality Assurance
- Run quality checks: `mvn checkstyle:check`
- Build verification: `mvn clean verify`
- Follow Google Java Style Guide (`google_checks.xml`)

### Git Standards
- Use conventional commit messages:
  ```
  feat(admin-api): add integration export endpoint
  fix(auth-api): resolve enrollment binding issue
  docs(mobile): update Flutter setup instructions
  style(core): format according to Google style guide
  test(admin-api): add integration controller tests
  ```
- Keep commits focused and atomic
- Include issue numbers when applicable

## Security Guidelines

### Input Validation
- Validate all input data using Bean Validation annotations
- Use parameterized queries (handled by JPA)
- Sanitize user inputs
- Implement proper authentication and authorization

### Data Security
- Use HTTPS in production
- Handle sensitive data appropriately
- Implement proper session management
- Follow security best practices for MFA/authentication systems

## Performance Considerations

### Database Optimization
- Use lazy loading for JPA relationships when appropriate
- Implement proper pagination for large datasets
- Use appropriate fetch strategies
- Consider caching for frequently accessed data
- Add appropriate database indexes

### Code Optimization
- Optimize N+1 query problems
- Use efficient algorithms and data structures
- Implement proper resource cleanup

## Mobile Development (Flutter)

### Flutter Standards
- Flutter 3.8.1+ with Provider state management
- Follow Flutter best practices for widget composition
- Implement proper state management patterns
- Use consistent theming and styling

### Project Structure
```
lib/
├── models/                  # Data models
├── services/                # API services
├── providers/               # State management
├── screens/                 # UI screens
├── widgets/                 # Reusable widgets
└── utils/                   # Utilities
```

## Build and Deployment

### Development Commands
```bash
# Build all modules
mvn clean install

# Run quality checks
mvn checkstyle:check
mvn clean verify

# Database migrations
./scripts/ezkey-flyway.sh --migrate
```

### Docker Support
- Add Docker support for self-hosting
- Ensure containerized builds work correctly
- Provide clear deployment instructions

## Additional Context

### Project Phases
- **Phase 1**: MVP with core entities and REST APIs ✅
- **Phase 2**: Quality & best practices (current focus)
- **Phase 3**: Demo applications
- **Phase 4**: Mobile app and Maven Central publication

### Technology Constraints
- Backend: Java 21, Spring Boot 3.5.3, Spring Data JPA
- Database: PostgreSQL with Flyway migrations
- Documentation: SpringDoc OpenAPI
- Testing: JUnit 5, Spring Boot Test
- Build: Maven multi-module setup

### Key Principles
- **Simplicity**: Minimalist, easy-to-understand APIs and flows
- **Pragmatism**: Solves 90% of the problem with 10% of the effort
- **Open Source**: All components are open source
- **Developer-first**: Designed for bottom-up adoption by developers
- **Proprietary by Design**: Not aiming for FIDO2/WebAuthn compatibility

## File Encoding Critical Rule

**ALWAYS generate code in UTF-8 without BOM**
- NEVER use UTF-16 or UTF-8 with BOM encoding
- Verify all Java files are in pure UTF-8
- Avoid invisible characters or BOM in generations
- Files with wrong encoding cause Maven compilation errors

Before generating any file, confirm: "This file will be in UTF-8 without BOM"