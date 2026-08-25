# Code Review Checklist for Ezkey Project

## Pre-Review Setup
- [ ] Read the PRD.md, README.md, and ENDPOINT.md for context
- [ ] Understand the feature/change being implemented
- [ ] Check if this is part of a larger feature or bug fix
- [ ] Review any related issues or tickets

## Code Quality Standards

### Java & Spring Boot Best Practices
- [ ] **Constructor Injection**: No `@Autowired` field injection, use constructor injection
- [ ] **Naming Conventions**: 
  - Classes: PascalCase (e.g., `AdminAuthController`)
  - Methods/Variables: camelCase (e.g., `getUserById`)
  - Constants: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_ATTEMPTS`)
- [ ] **Annotations**: Proper use of Spring annotations (`@RestController`, `@Service`, `@Repository`)
- [ ] **Exception Handling**: Custom exceptions with `@ControllerAdvice` and `@ExceptionHandler`
- [ ] **Validation**: Bean Validation annotations (`@Valid`, `@NotNull`, `@Size`, etc.)

### Code Structure & Organization
- [ ] **Package Structure**: Follows established pattern (domain.entity, service, controller, dto, etc.)
- [ ] **Single Responsibility**: Each class has one clear purpose
- [ ] **Dependency Management**: Proper use of Spring IoC container
- [ ] **Imports**: Organized in correct order (Java core, third-party, Spring, project-specific)

### Documentation & Comments
- [ ] **Javadoc**: Complete documentation for all public methods with `@param`, `@return`, `@throws`
- [ ] **Class Headers**: Include `@since` with the current calendar year for new types, plus project header
- [ ] **Comments**: Explain "why" not "what", especially for complex business logic
- [ ] **API Documentation**: OpenAPI/Swagger annotations where applicable

## Security Review

### Authentication & Authorization
- [ ] **Input Validation**: All user inputs are properly validated
- [ ] **Authentication**: Proper authentication mechanisms in place
- [ ] **Authorization**: Correct permission checks for admin operations
- [ ] **Password Security**: BCrypt or equivalent for password hashing
- [ ] **API Keys**: Secure generation and storage of API keys

### Data Protection
- [ ] **SQL Injection**: Use of parameterized queries (JPA handles this)
- [ ] **XSS Prevention**: Proper output encoding
- [ ] **CSRF Protection**: CSRF tokens where appropriate
- [ ] **Sensitive Data**: No hardcoded secrets or credentials
- [ ] **Data Sanitization**: User inputs are sanitized before processing

### Audit & Logging
- [ ] **Audit Logging**: Security events are properly logged
- [ ] **Log Levels**: Appropriate use of ERROR, WARN, INFO, DEBUG
- [ ] **Sensitive Data**: No sensitive information in logs
- [ ] **Log Rotation**: Consider log file management

## API Design & REST Standards

### HTTP Standards
- [ ] **Status Codes**: Correct use of 200, 201, 400, 401, 403, 404, 500
- [ ] **HTTP Methods**: Proper use of GET, POST, PUT, DELETE, PATCH
- [ ] **URL Patterns**: Consistent `/api/v1/{resource}` structure
- [ ] **Response Format**: Standardized DTOs, never expose entities directly

### Request/Response Design
- [ ] **DTOs**: Separate request/response DTOs with proper validation
- [ ] **Error Responses**: Consistent error response format with code, message, timestamp
- [ ] **Pagination**: Proper pagination for list endpoints
- [ ] **Content Negotiation**: Proper handling of JSON/XML if needed

## Database & Persistence

### JPA & Entity Design
- [ ] **Entity Relationships**: Proper JPA relationships and cascading
- [ ] **Lazy Loading**: Appropriate use of lazy vs eager loading
- [ ] **Indexing**: Consider database performance and indexing needs
- [ ] **Migrations**: Database changes are properly migrated with Flyway

### Data Integrity
- [ ] **Constraints**: Proper database constraints and validations
- [ ] **Transactions**: Correct use of `@Transactional` annotations
- [ ] **Rollback**: Proper transaction rollback handling
- [ ] **Data Consistency**: Ensure data consistency across operations

## Performance & Scalability

### Code Performance
- [ ] **Algorithm Efficiency**: Appropriate algorithms and data structures
- [ ] **Memory Usage**: Avoid memory leaks and excessive object creation
- [ ] **Caching**: Use Spring Cache where appropriate
- [ ] **Async Processing**: `@Async` for non-blocking operations when needed

### Database Performance
- [ ] **Query Optimization**: Efficient database queries
- [ ] **N+1 Problems**: Avoid N+1 query issues
- [ ] **Batch Operations**: Use batch processing for bulk operations
- [ ] **Connection Pooling**: Proper database connection management

## Testing Requirements

### Test Coverage
- [ ] **Unit Tests**: Service layer has comprehensive unit tests
- [ ] **Integration Tests**: Controller layer has integration tests
- [ ] **Test Naming**: Descriptive test method names
- [ ] **Test Structure**: Follow AAA pattern (Arrange, Act, Assert)

### Test Quality
- [ ] **Mocking**: Proper use of mocks and test doubles
- [ ] **Test Data**: Realistic test data and scenarios
- [ ] **Edge Cases**: Test error conditions and edge cases
- [ ] **Test Isolation**: Tests don't depend on each other

## Error Handling & Resilience

### Exception Management
- [ ] **Custom Exceptions**: Use project-specific exception classes
- [ ] **Global Exception Handler**: `@RestControllerAdvice` for consistent error handling
- [ ] **Error Messages**: User-friendly error messages
- [ ] **Logging**: Exceptions are properly logged with context

### Resilience Patterns
- [ ] **Retry Logic**: Appropriate retry mechanisms for transient failures
- [ ] **Circuit Breaker**: Consider circuit breaker pattern for external services
- [ ] **Timeout Handling**: Proper timeout configuration
- [ ] **Graceful Degradation**: System degrades gracefully under load

## Configuration & Environment

### Configuration Management
- [ ] **Properties**: Use `application.properties` or `application.yml`
- [ ] **Profiles**: Environment-specific configurations with Spring Profiles
- [ ] **Configuration Properties**: Use `@ConfigurationProperties` for type-safe config
- [ ] **Secrets Management**: No hardcoded secrets, use environment variables

### Deployment Considerations
- [ ] **Docker**: Proper Docker configuration if applicable
- [ ] **Environment Variables**: All configurable values use environment variables
- [ ] **Health Checks**: Proper health check endpoints
- [ ] **Monitoring**: Integration with monitoring and observability tools

## Ezkey-Specific Requirements

### Multi-Tenancy
- [ ] **Tenant Isolation**: Proper tenant data isolation
- [ ] **Tenant Context**: Tenant context is properly maintained
- [ ] **Cross-Tenant Security**: No data leakage between tenants

### Crypto & Security
- [ ] **Signature Service**: Proper implementation of signature operations
- [ ] **Key Management**: Secure key generation and storage
- [ ] **Crypto Operations**: All crypto operations follow security best practices

### API Keys & Integration
- [ ] **API Key Security**: Secure API key generation and validation
- [ ] **Rate Limiting**: Proper rate limiting implementation
- [ ] **Integration Security**: Secure integration with external systems

### Exception Handling & Security Philosophy

#### Security-First Error Responses
- [ ] **Minimal Information Disclosure**: Error messages are intentionally vague to prevent information leakage
- [ ] **No System Details**: Never expose internal system information, stack traces, or implementation details
- [ ] **Consistent Error Format**: All errors follow the same generic format regardless of the actual cause
- [ ] **No Enumeration Attacks**: Error messages don't reveal whether usernames, tokens, or resources exist
- [ ] **Security Event Logging**: Detailed error information is logged server-side for debugging, not returned to client

#### Error Message Standards
- [ ] **Generic Messages**: Use messages like "Authentication failed" instead of "Invalid username" or "Wrong password"
- [ ] **No Timing Information**: Don't reveal processing time differences that could indicate system state
- [ ] **Consistent Response Times**: Ensure similar operations take similar time regardless of success/failure
- [ ] **No Debug Information**: Never include technical details, file paths, or internal IDs in responses

#### Exception Handling Patterns
- [ ] **Controller-Level Catching**: Controllers catch all exceptions and return generic error responses
- [ ] **Detailed Server Logging**: Full exception details are logged server-side with proper context
- [ ] **Audit Trail**: Security-related errors are properly audited for monitoring
- [ ] **Graceful Degradation**: System continues to function even when errors occur

#### Security Rationale
- [ ] **Attack Surface Reduction**: Minimal information reduces attack vectors
- [ ] **Defense in Depth**: Multiple layers of security through information control
- [ ] **Compliance**: Meets security standards for sensitive applications
- [ ] **User Experience**: Consistent error handling provides predictable API behavior

#### Examples of Good vs Bad Error Handling

**❌ BAD - Information Leakage:**
```java
// DON'T: Reveals that username exists
return ResponseEntity.badRequest()
    .body("Invalid password for user 'admin'");

// DON'T: Exposes internal system details
return ResponseEntity.status(500)
    .body("Database connection failed: Connection timeout to postgresql://...");

// DON'T: Reveals system architecture
return ResponseEntity.badRequest()
    .body("Token validation failed: JWT signature verification error");
```

**✅ GOOD - Security-First Approach:**
```java
// DO: Generic error message
return ResponseEntity.badRequest()
    .body("Authentication failed");

// DO: Log details server-side, return generic message
logger.error("Database connection failed for user {}: {}", username, e.getMessage());
return ResponseEntity.status(500)
    .body("An error occurred during processing");

// DO: Consistent error format
return ResponseEntity.badRequest()
    .body("Invalid request");
```

**Key Principles:**
- **Server Logs**: `logger.error("Detailed error: {}", e.getMessage())` - Full context for debugging
- **Client Response**: `"Authentication failed"` - Generic message for security
- **Consistency**: Same error message for different failure reasons
- **No Enumeration**: Don't reveal if usernames, tokens, or resources exist

## Final Review Checklist

### Code Readability
- [ ] **Code is self-documenting** with clear variable and method names
- [ ] **Complex logic is commented** and explained
- [ ] **Code follows consistent formatting** (Google Style)
- [ ] **No dead code** or commented-out code blocks

### Maintainability
- [ ] **Code is modular** and can be easily modified
- [ ] **Dependencies are minimal** and well-defined
- [ ] **Code is testable** and can be unit tested
- [ ] **Future changes** are considered in the design

### Compliance
- [ ] **Follows project coding standards** and conventions
- [ ] **Meets security requirements** for the Ezkey project
- [ ] **Uses product terms** (identifiable identity, operator-visible audit) — see normative-posture.md
- [ ] **Follows RESTful API** design principles

## Review Notes Template

```
## Summary
[Brief description of what was reviewed]

## Positive Aspects
- [List positive aspects of the code]

## Issues Found
### Critical (Must Fix)
- [ ] Issue 1: Description and suggested fix
- [ ] Issue 2: Description and suggested fix

### Major (Should Fix)
- [ ] Issue 1: Description and suggested fix
- [ ] Issue 2: Description and suggested fix

### Minor (Nice to Fix)
- [ ] Issue 1: Description and suggested fix
- [ ] Issue 2: Description and suggested fix

## Suggestions for Improvement
- [Suggestions for code improvement]

## Security Considerations
- [Any security concerns or recommendations]
- [ ] **Error Information Disclosure**: Verify that error messages don't leak sensitive information
- [ ] **Exception Handling**: Ensure exceptions follow the security-first philosophy (minimal client details)
- [ ] **Attack Surface**: Check that error responses don't provide attack vectors

## Performance Considerations
- [Any performance concerns or recommendations]

## Testing Recommendations
- [Suggestions for additional testing]

## Final Recommendation
[ ] Approve
[ ] Approve with minor changes
[ ] Request changes
[ ] Reject
```

## Usage Instructions

1. **Before Starting**: Read the context documents (PRD.md, README.md, ENDPOINT.md)
2. **During Review**: Go through each section systematically
3. **Document Issues**: Use the template to document findings
4. **Provide Feedback**: Give constructive, actionable feedback
5. **Follow Up**: Ensure critical issues are addressed before approval

## Quick Commands

```bash
# Check for compilation issues
mvn compile

# Run tests
mvn test

# Check code style
mvn checkstyle:check

# Generate test coverage report
mvn jacoco:report
```
