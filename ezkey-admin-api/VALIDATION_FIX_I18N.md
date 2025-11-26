# Fix: Database Constraint Violations Return 500 Instead of 400

## Problem Summary

The test `testCreateIntegrationInvalidI18nReturns400` was returning HTTP 500 (Internal Server Error) instead of HTTP 400 (Bad Request) when invalid i18n data was sent (missing required `name` field).

### Root Cause

1. **Missing Bean Validation**: The `IntegrationI18nCreateDto` had no validation annotations (`@NotNull`, `@NotBlank`)
2. **Missing @Valid Annotation**: The controller method didn't use `@Valid` on the `@RequestBody`
3. **No Exception Handler**: `DataIntegrityViolationException` (thrown by database constraint violations) wasn't handled, falling through to generic `RuntimeException` handler which returns 500

### Error Flow

1. Test sends invalid i18n data: `{language: "en"}` (missing `name` and `description`)
2. No validation at DTO level (no `@Valid` or validation annotations)
3. Data passes through to service layer
4. Service saves to database via `integrationRepository.save()`
5. Database rejects insertion: `null value in column "integration_i18n_name" violates not-null constraint`
6. Hibernate throws `DataIntegrityViolationException`
7. Exception falls through to generic `RuntimeException` handler → **500 Internal Server Error**

## Solution Implemented

### 1. Added Bean Validation Annotations

**File**: `IntegrationI18nCreateDto.java`

Added validation annotations to enforce required fields at the DTO level:

```java
public record IntegrationI18nCreateDto(
    @NotNull(message = "Language code is required")
    @NotBlank(message = "Language code cannot be blank")
    String language,
    
    @NotNull(message = "Name is required")
    @NotBlank(message = "Name cannot be blank")
    String name,
    
    String description) {}
```

### 2. Added @Valid Annotation to Controller

**File**: `IntegrationController.java`

Added `@Valid` annotation to trigger Bean Validation:

```java
@PostMapping
public ResponseEntity<IntegrationCreateResponseDto> create(
    @RequestBody
    @jakarta.validation.Valid  // ← Added this
    IntegrationCreateRequestDto request) {
```

### 3. Added DataIntegrityViolationException Handler

**File**: `GlobalExceptionHandler.java`

Added specific handler for database constraint violations to return 400 instead of 500:

```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolationException(
    DataIntegrityViolationException ex, WebRequest request) {
  
  // Extract user-friendly error message
  String message = extractConstraintViolationMessage(ex.getMessage());
  
  ErrorResponseDto errorResponse = new ErrorResponseDto(
      "CONSTRAINT_VIOLATION", 
      message, 
      request.getDescription(false).replace("uri=", ""));
  
  return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
}
```

## Defense in Depth

The solution implements **defense in depth** with three layers:

1. **Layer 1 - Bean Validation**: Catches invalid data at the DTO level (fastest, most user-friendly)
2. **Layer 2 - Exception Handler**: Catches database constraint violations and converts to 400 (safety net)
3. **Layer 3 - Database Constraints**: Final enforcement at database level (last resort)

## Benefits

1. **Better Error Messages**: Bean Validation provides clear, user-friendly error messages
2. **Correct HTTP Status**: Returns 400 (Bad Request) instead of 500 (Internal Server Error)
3. **Early Validation**: Catches errors before database round-trip (better performance)
4. **Consistent API**: All validation errors return 400 with consistent format

## Testing

The test `testCreateIntegrationInvalidI18nReturns400` should now:
- ✅ Return HTTP 400 (not 500)
- ✅ Include clear error message about missing required field
- ✅ Have consistent error response format

## Related Files

- `ezkey-admin-api/src/main/java/org/ezkey/integration/dto/IntegrationI18nCreateDto.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java`
- `ezkey-tests/src/test/java/org/ezkey/tests/security/integration/IntegrationManagementSecurityTest.java`

