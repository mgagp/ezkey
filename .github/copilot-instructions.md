# GitHub Copilot Instructions for Ezkey Project

## Project Context
This is a multi-module Spring Boot project providing an open-source cryptographic MFA platform with a backend-first trust model.
For overall product requirements and functional constraints, always refer to the top-level file `PRD.md`.
For build, setup, and project organization, refer to `README.md`.

## Product-Docs Workflow

When the task starts from a new product or design idea, align with the `product-docs` methodology:

1. Read:
   - `product-docs/methodology/README.md`
   - `product-docs/methodology/workflow-overview.md`
   - `product-docs/methodology/testing-strategy-in-workflow.md`
2. Materialize durable direction into:
   - vision notes (`V-*`) under `product-docs/global/vision/`
   - backlog ideas (`I-*`) under `product-docs/global/backlog/ideas/`
   - tracer bullets (`TB-*`) when a bounded execution slice is ready
3. Keep lifecycle and traceability explicit.

### Plan incubation vs retrofit

Treat a **current-session working plan** under `.cursor/plans/` or `plans/` as a legitimate incubation artifact, not as legacy retrofit by default.

- Use working plans for freeform brainstorming, tool comparison, and early convergence.
- When the workflow uses explicit project skills, use `plan-incubation` for this lane.
- Materialize the durable output into `V-*`, `I-*`, `TB-*`, and related canonical docs.
- Do not describe this as retrofit unless the source is genuinely historical or mixed with historical evidence.

Use the **retrofit lane** only for historical plans, verbal history, or ad hoc implementation history that must be mined and reintegrated into canonical docs via `R-*` artifacts.

## Code Style

### Java Standards
- Use Java 25+ features where appropriate (records, switch expressions, pattern matching)
- Follow idiomatic Spring Boot practices:
  - `@Service` for business logic
  - `@Repository` for persistence
  - `@RestController` for HTTP APIs
- Use constructor injection rather than field injection
- Methods and classes should be kept small and cohesive
- Favor immutability whenever possible
- For logging, always use SLF4J with `private static final Logger logger = LoggerFactory.getLogger(...)`

### Formatting Rules
- **Indentation**: 2 spaces (no tabs) - Google Java Format standard
- **Line Length**: Maximum 100 characters - Google Java Format standard
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
- Lombok not allowed, never use this library

### Dependency Management
- Use MapStruct for object mapping
- Use Spring Data JPA for persistence
- Prefer constructor injection over field injection
- Use `@Autowired` annotation for clarity

### MapStruct Enum Mapping Patterns

**Critical:** MapStruct cannot automatically convert enum types to primitive types (String, int, etc.). When mapping enum fields to different types, you must explicitly provide conversion methods.

**Pattern for enum-to-String conversion:**
```java
@Mapper(componentModel = "spring")
public interface MyMapper {

  TargetDto toDto(Entity entity);

  // Always add explicit enum conversion methods
  default String statusToString(EnumStatus status) {
    if (status == null) {
      return null;
    }
    return status.name();
  }
}
```

**Why:** MapStruct generates mapper implementations at compile time. Without explicit conversion methods, the processor cannot determine how to convert enums and will fail silently, causing `ClassNotFoundException` at runtime when the generated implementation is missing.

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
 * Ezkey - Open Source Cryptographic MFA Platform
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
- Always check `PRD.md` to ensure new code aligns with product requirements
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
  docs(mobile): update React Native setup instructions
  style(core): format according to Google style guide
  test(admin-api): add integration controller tests
  ```
- Keep commits focused and atomic
- Include issue numbers when applicable

#### Committing on Windows (avoid recurring shell failures)

This workstation's agent shell is usually **PowerShell**, which makes `git commit` fail for avoidable
reasons: Cursor may inject `--trailer "... <...>"` (PowerShell parses `<` as redirection), and
multi-layer quoting (PowerShell → `bash -lc '...'` → `git commit`) mangles any of `( ) " ' # ! < >`.
Conventional prefixes like `docs(product):` always contain `()`, so inline `-m` is fragile.

**Reliable principle:** never let the commit *message text* cross a shell boundary. Use the repo
helper `scripts/git-commit.sh` with its **canonical zero-argument flow**:

1. `git add <paths>` (stage only this commit's files).
2. Write the full commit message (subject + optional blank line + body) to **`.ezkey/commit-msg.txt`**
   with the file-writing tool — any characters are safe there.
3. Run the **single constant command** (`&` call operator required for the quoted path):

   ```text
   & "C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-commit.sh'
   ```

   Inside Git Bash, just run `./scripts/git-commit.sh`. The script commits via `git commit -F` and
   removes the message file. Power-user forms: `./scripts/git-commit.sh -F <file>` or `-m "subject"`.

Do **not** use bare PowerShell `git commit`, HEREDOC, `&&` chains, or WSL bash
(`C:\Windows\System32\bash.exe`); always use **Git Bash** (`C:\Program Files\Git\bin\bash.exe`).
Cursor mirror of this rule: `.cursor/rules/git-commit-windows.mdc`; repo-wide context:
`AGENTS.md` § *Git commit on Windows (agent shell)*.

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

## Mobile Development (React Native)

### React Native Standards
- React Native 0.72+ with native development environment setup
- Follow React Native best practices for component composition
- Implement proper state management patterns (Redux, Context API, or similar)
- Use consistent theming and styling

### Project Structure
```
app/
├── models/                  # Data models
├── services/                # API services
├── screens/                 # UI screens
├── components/              # Reusable components
├── utils/                   # Utilities
└── styles/                  # Styling and themes
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

### Product Milestones
- **Milestone 1**: MVP with core entities and REST APIs ✅
- **Milestone 2**: Quality and best practices (current focus)
- **Milestone 3**: Demo applications
- **Milestone 4**: Mobile app and Maven Central publication

### Terminology guardrail
- Use **phase** only for the methodology workflow phases.
- Use **milestone** for product-level progression and roadmap context.

### Technology Constraints
- Backend: Java 25, Spring Boot 3.5.3, Spring Data JPA
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

### Analysis and design values

When analysing or designing features (especially Admin UI and APIs), apply these criteria:

- **Simplicity and pragmatism**: 80–20 rule — target ~80% of the value with ~20% of the complexity. Prefer the simplest solution that meets the need.
- **Admin UI**: Sober, pragmatic interface that makes the operator's and their team's life easier. Avoid clutter and unnecessary decoration.
- **Admin roles**: **Global Admin** = IT-style; manages core/instance-level concerns (e.g. encryption keys). **Tenant Admin** = business-oriented; manages end users, integrations, API keys. Design and copy must reflect this split.
- **Comparables and best practices**: For any analysis or design, consider comparable projects and admin UIs; adopt widely recognised best practices from those comparables.
- **Stack and ecosystem**: Stay within the existing stack; avoid new frameworks or libraries unless there is a strong justification. Prefer what developers expect and what is considered best practice for the stack.
- **Complexity**: **Essential complexity** (required for the feature) is acceptable. **Accidental complexity** (extra indirection, unnecessary abstraction) must be minimised to keep maintenance and evolution manageable.

## File Encoding Critical Rule

**ALWAYS generate code in UTF-8 without BOM**
- NEVER use UTF-16 or UTF-8 with BOM encoding
- Verify all Java files are in pure UTF-8
- Avoid invisible characters or BOM in generations
- Files with wrong encoding cause Maven compilation errors

Before generating any file, confirm: "This file will be in UTF-8 without BOM"
