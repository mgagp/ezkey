# Ezkey Exception Handling Analysis Report

**Date:** October 14, 2025  
**Scope:** Complete Ezkey system (core, admin-api, auth-api)  
**Purpose:** Exception uniformity analysis and custom exception strategy

---

## Executive Summary

**Current State:** ✅ **GOOD with room for improvement**

The Ezkey system uses a **mix of custom and generic exceptions** with consistent HTTP mapping. While the current implementation works, introducing more **domain-specific exceptions** would improve:
- Code clarity and maintainability
- API consumer experience
- Security (better control over error messages)
- Monitoring and alerting capabilities

**Recommendation:** Implement a **hierarchical custom exception system** to replace generic exceptions while maintaining 100% backward compatibility in HTTP responses.

---

## Current Exception Inventory

### Custom Exceptions (Domain-Specific)

#### ezkey-core

| Exception | Package | Extends | HTTP Status | Usage Count |
|-----------|---------|---------|-------------|-------------|
| `ResourceNotFoundException` | org.ezkey.exception | RuntimeException | 404 NOT FOUND | 3 uses |
| `NoPendingAuthAttemptException` | org.ezkey.exception | RuntimeException | 204 NO CONTENT | 1 use |

**Total Custom Exceptions (core):** 2

#### ezkey-admin-api

| Exception | Package | Extends | HTTP Status | Usage Count |
|-----------|---------|---------|-------------|-------------|
| `AuthenticationException` | org.ezkey.admin.exception | RuntimeException | 400 BAD REQUEST | Multiple uses |

**Total Custom Exceptions (admin-api):** 1

#### ezkey-auth-api

**Total Custom Exceptions (auth-api):** 0 (uses ezkey-core exceptions)

---

### Generic Exceptions (Java Standard)

| Exception | HTTP Status | Usage Count | Services Using |
|-----------|-------------|-------------|----------------|
| `IllegalArgumentException` | 400 BAD REQUEST | 24 uses | All services |
| `IllegalStateException` | 409 CONFLICT (auth-api)<br>500 INTERNAL ERROR (admin-api) | 10 uses | Auth, Enrollment |
| `RuntimeException` | 500 INTERNAL ERROR | 3 uses | SignatureService |

**Total Generic Exception Uses:** 37

---

## Exception Mapping by API

### Admin API (ezkey-admin-api/GlobalExceptionHandler.java)

```java
@ExceptionHandler Mappings:
ResourceNotFoundException     → HTTP 404 (RESOURCE_NOT_FOUND)
IllegalArgumentException      → HTTP 400 (INVALID_ARGUMENT)
RuntimeException              → HTTP 500 (INTERNAL_ERROR)
Exception                     → HTTP 500 (INTERNAL_SERVER_ERROR)
```

**Missing Handlers:**
- ❌ No handler for `IllegalStateException` (falls to RuntimeException → 500)
- ❌ No handler for `AuthenticationException` (handled in controller, not globally)
- ❌ No handler for `NoPendingAuthAttemptException` (not used in admin-api)

### Auth API (ezkey-auth-api/GlobalExceptionHandler.java)

```java
@ExceptionHandler Mappings:
ResourceNotFoundException     → HTTP 404 (RESOURCE_NOT_FOUND)
NoPendingAuthAttemptException → HTTP 204 NO CONTENT
IllegalStateException         → HTTP 409 CONFLICT ✅
IllegalArgumentException      → HTTP 400 (INVALID_ARGUMENT)
RuntimeException              → HTTP 500 (INTERNAL_ERROR)
Exception                     → HTTP 500 (INTERNAL_SERVER_ERROR)
```

**Better Coverage:**
- ✅ Handles `NoPendingAuthAttemptException` (domain-specific)
- ✅ Handles `IllegalStateException` separately (409 CONFLICT)

### Crypto API (ezkey-crypto-api/CryptoGlobalExceptionHandler.java)

```java
@ExceptionHandler Mappings:
IllegalArgumentException      → HTTP 400 (INVALID_PARAMETER)
MethodArgumentNotValidException → HTTP 400 (VALIDATION_ERROR)
RuntimeException              → HTTP 500 (INTERNAL_ERROR)
Exception                     → HTTP 500 (UNKNOWN_ERROR)
```

**Missing:**
- ❌ No handler for `ResourceNotFoundException`

---

## Exception Usage Analysis by Service

### AuthAttemptService (ezkey-core)

**Exceptions Thrown:**

| Method | Exception Type | Scenario | HTTP Status |
|--------|---------------|----------|-------------|
| `getById()` | ResourceNotFoundException ✅ | Auth attempt not found | 404 |
| `delete()` | ResourceNotFoundException ✅ | Auth attempt not found | 404 |
| `create()` | IllegalArgumentException ❌ | Enrollment not found | 400 |

**Inconsistency Identified:**
```java
// Line 207 - AuthAttemptService.create()
throw new IllegalArgumentException("Enrollment not found for ID: " + authRequest.getEnrollmentId());
// ❌ SHOULD BE: ResourceNotFoundException
```

**Why it matters:**
- Missing enrollment is a "not found" scenario → should be 404
- Currently returns 400 (bad request) which is misleading
- Inconsistent with other services

---

### AuthAttemptRespondService (ezkey-core)

**Exceptions Thrown:**

| Scenario | Exception Type | Message | HTTP Status |
|----------|---------------|---------|-------------|
| Auth attempt not found | IllegalArgumentException ❌ | "Auth attempt record not found" | 400 |
| Not in READ status | IllegalArgumentException | "Auth attempt not read by device" | 400 |
| Superseded | IllegalStateException ✅ | "Authentication attempt superseded..." | 409 |
| Expired | IllegalStateException ✅ | "Authentication attempt expired" | 409 |
| Missing device key | IllegalArgumentException ❌ | "Device public key not found" | 400 |
| Invalid signature | IllegalArgumentException | "Invalid signature for auth attempt code" | 400 |
| Challenge mismatch | IllegalArgumentException | "Challenge value mismatch" | 400 |

**Inconsistencies:**
```java
// Line 144 - Auth attempt not found
throw new IllegalArgumentException("Auth attempt record not found");
// ❌ SHOULD BE: ResourceNotFoundException

// Line 192 - Device public key not found  
throw new IllegalArgumentException("Device public key not found");
// ❌ SHOULD BE: ResourceNotFoundException or custom DeviceNotBoundException
```

**Pattern Note:** This service catches `IllegalArgumentException` and returns structured error response:
```java
} catch (IllegalArgumentException e) {
    return new AuthAttemptRespondResponse(AuthenticationResult.FAILED, e.getMessage());
}
```
This is **intentional** - not all IllegalArgumentExceptions should be custom exceptions.

---

### AuthAttemptPendingService (ezkey-core)

**Exceptions Thrown:**

| Scenario | Exception Type | Message | HTTP Status |
|----------|---------------|---------|-------------|
| Enrollment ID mismatch | IllegalArgumentException | "Authentication request failed" | 400 |
| Device key missing | IllegalStateException ✅ | "Authentication request failed" | 409 |
| Invalid signature | IllegalArgumentException | "Authentication request failed" | 400 |
| Token already used | IllegalArgumentException | "Authentication request failed" | 400 |
| No pending attempt | NoPendingAuthAttemptException ✅ | "No pending authentication request" | 204 |
| Already processed | IllegalStateException ✅ | "Authentication request failed" | 409 |

**Security Pattern:** Uses generic "Authentication request failed" message to prevent information leakage
- ✅ Good security practice
- ⚠️ Makes debugging harder
- ✅ Detailed logging compensates

---

### AuthAttemptWaitService (ezkey-core)

**Exceptions Thrown:**

| Scenario | Exception Type | Message | HTTP Status |
|----------|---------------|---------|-------------|
| Interrupted wait | RuntimeException ✅ | "Wait operation interrupted" | 500 |
| Invalid timeout | IllegalArgumentException | "Timeout must be between 1 and 300 seconds" | 400 |
| Invalid polling | IllegalArgumentException | "Polling must be between 1 and 60 seconds" | 400 |
| Polling > timeout | IllegalArgumentException | "Polling interval cannot be greater than timeout" | 400 |

**Assessment:** ✅ Good - clear validation messages

---

### EnrollmentService (ezkey-core)

**Exceptions Thrown:**

| Method | Exception Type | Scenario | HTTP Status |
|--------|---------------|----------|-------------|
| `getById()` | ResourceNotFoundException ✅ | Enrollment not found | 404 |
| `create()` | IllegalArgumentException | Integration ID missing | 400 |

**Assessment:** ✅ Consistent usage

---

### EnrollmentBindService (ezkey-core)

**Exceptions Thrown:**

| Scenario | Exception Type | Message | HTTP Status |
|----------|---------------|---------|-------------|
| Invalid proof token | IllegalArgumentException | "Enrollment binding failed" | 400 |
| Already bound | IllegalStateException ✅ | "Enrollment already bound by a device" | 409 |
| Integration not found | IllegalStateException ❌ | "Enrollment binding failed" | 409 |
| After lock: not found | IllegalArgumentException ❌ | "Enrollment not found or already bound" | 400 |
| After lock: bound | IllegalStateException ✅ | "Enrollment already bound by a device" | 409 |

**Inconsistencies:**
```java
// Line 176 - Integration not found
throw new IllegalStateException("Enrollment binding failed");
// ❌ SHOULD BE: ResourceNotFoundException
// Currently: 409 Conflict
// Should be: 404 Not Found
```

**Security Pattern:** Uses generic "Enrollment binding failed" for multiple error cases
- ✅ Prevents enumeration attacks
- ⚠️ Hard to distinguish specific failures

---

### EnrollmentVerifyService (ezkey-core)

**Exceptions Thrown:**

| Scenario | Exception Type | Message | HTTP Status |
|----------|---------------|---------|-------------|
| Already verified | IllegalStateException ✅ | "Enrollment already verified" | 409 |
| Not bound yet | IllegalStateException ✅ | "Enrollment must be bound before verification" | 409 |
| Invalid signature | IllegalArgumentException | "Invalid bind proof token signature" | 400 |
| Duplicate device key | IllegalArgumentException | "Device public key already registered" | 400 |
| Challenge mismatch | IllegalArgumentException | "Invalid challenge response" | 400 |
| State changed (race) | IllegalStateException ✅ | "Enrollment state changed" | 409 |

**Assessment:** ✅ Good - appropriate use of IllegalStateException for state conflicts

---

### SignatureService (ezkey-core)

**Exceptions Thrown:**

| Method | Exception Type | Message | HTTP Status |
|--------|---------------|---------|-------------|
| `generateSignature()` | RuntimeException ✅ | "Failed to generate signature" | 500 |
| `generateRsaKeyPair()` | RuntimeException ✅ | "RSA key pair generation failed" | 500 |
| `generateSecureChallenge()` | IllegalArgumentException | "Challenge digits must be between 1 and 6..." | 400 |
| `generateProofToken()` | RuntimeException ✅ | "Failed to generate proof token" | 500 |

**Assessment:** ✅ Excellent - cryptographic failures properly categorized as RuntimeException

---

## HTTP Status Code Mapping Analysis

### Current Mapping (Across All APIs)

| Exception Type | Admin API | Auth API | Crypto API | Consistency |
|----------------|-----------|----------|------------|-------------|
| ResourceNotFoundException | 404 | 404 | ❌ Missing | ⚠️ Inconsistent |
| NoPendingAuthAttemptException | ❌ Not used | 204 | ❌ Not used | ✅ Consistent where used |
| IllegalArgumentException | 400 | 400 | 400 | ✅ Consistent |
| IllegalStateException | 500 ❌ | 409 ✅ | ❌ Missing | ❌ INCONSISTENT |
| RuntimeException | 500 | 500 | 500 | ✅ Consistent |
| Exception (catch-all) | 500 | 500 | 500 | ✅ Consistent |

**Critical Inconsistency:** `IllegalStateException`
- Auth API: 409 CONFLICT ✅ (correct - represents state machine violation)
- Admin API: Falls through to RuntimeException → 500 ❌ (should be 409)

---

## Recommended Custom Exception Hierarchy

### Proposed Structure

```
org.ezkey.exception (ezkey-core)
├── EzkeyException (base class)
│   ├── EzkeyClientException (400-level errors)
│   │   ├── ResourceNotFoundException (404)
│   │   ├── ValidationException (400)
│   │   ├── InvalidStateException (409)
│   │   ├── EnrollmentNotBoundException (400)
│   │   ├── EnrollmentAlreadyBoundException (409)
│   │   ├── AuthAttemptExpiredException (409)
│   │   ├── AuthAttemptSupersededException (409)
│   │   ├── InvalidSignatureException (400)
│   │   ├── InvalidChallengeException (400)
│   │   ├── DeviceAlreadyRegisteredException (409)
│   │   └── NoPendingAuthAttemptException (204)
│   │
│   └── EzkeyServerException (500-level errors)
│       ├── CryptographicException (500)
│       ├── SignatureGenerationException (500)
│       └── ProofTokenGenerationException (500)
│
org.ezkey.admin.exception (ezkey-admin-api)
└── AuthenticationException (400 or 401 depending on scenario)
    ├── InvalidCredentialsException (401)
    ├── AccountInactiveException (403)
    ├── DeviceNotEnrolledException (400)
    └── RecoveryCodeInvalidException (403)
```

---

## Detailed Exception Mapping Recommendations

### 1. Resource Not Found Scenarios → Custom 404 Exceptions

**Current:**
```java
throw new ResourceNotFoundException("Authentication attempt", id);  // ✅ Good
throw new IllegalArgumentException("Enrollment not found for ID: " + id);  // ❌ Wrong HTTP code
throw new IllegalStateException("Integration not found");  // ❌ Wrong HTTP code
```

**Recommended:**
```java
// Keep existing ResourceNotFoundException
throw new ResourceNotFoundException("Authentication attempt", id);  // 404
throw new ResourceNotFoundException("Enrollment", id);  // 404
throw new ResourceNotFoundException("Integration", id);  // 404
```

**Impact:**
- Fixes 3 inconsistencies (enrollment not found in create, integration not found)
- Consistent 404 responses
- Better API semantics

---

### 2. State Conflicts → InvalidStateException (409)

**Current:**
```java
throw new IllegalStateException("Enrollment already bound");  // ✅ Good
throw new IllegalStateException("Authentication attempt superseded");  // ✅ Good
throw new IllegalStateException("Authentication attempt expired");  // ✅ Good
```

**Recommended (More Specific):**
```java
throw new EnrollmentAlreadyBoundException(enrollmentId);  // 409
throw new AuthAttemptSupersededException(attemptId, newerAttemptId);  // 409
throw new AuthAttemptExpiredException(attemptId);  // 409
throw new InvalidStateException("Enrollment must be bound before verification");  // 409
```

**Benefits:**
- Clear exception names describe exact problem
- Easier to catch specific scenarios in controllers
- Better logging and monitoring
- Structured error responses possible

**Handler Change Needed (admin-api):**
```java
@ExceptionHandler(IllegalStateException.class)  // Add this handler
public ResponseEntity<ErrorResponseDto> handleIllegalStateException(...) {
    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);  // 409, not 500
}
```

---

### 3. Validation Errors → ValidationException (400)

**Current:**
```java
throw new IllegalArgumentException("Integration ID is required");  // ✅ Works
throw new IllegalArgumentException("Challenge value mismatch");  // ✅ Works
throw new IllegalArgumentException("Invalid signature");  // ⚠️ Could be more specific
```

**Recommended:**
```java
throw new ValidationException("Integration ID is required");  // 400
throw new InvalidChallengeException(expected, actual);  // 400
throw new InvalidSignatureException("Device signature validation failed");  // 400
```

**Benefits:**
- Distinguish validation errors from argument errors
- Structured error responses (field-level errors)
- Better API documentation

---

### 4. Cryptographic Failures → CryptographicException (500)

**Current:**
```java
throw new RuntimeException("Failed to generate signature", e);  // ✅ OK
throw new RuntimeException("RSA key pair generation failed", e);  // ✅ OK
```

**Recommended:**
```java
throw new SignatureGenerationException("Failed to generate signature", e);  // 500
throw new KeyPairGenerationException("RSA key pair generation failed", e);  // 500
throw new ProofTokenGenerationException("Failed to generate proof token", e);  // 500
```

**Benefits:**
- Clear categorization of crypto failures
- Better error monitoring (track crypto errors separately)
- Easier to add retry logic
- Better logging/alerting

---

## Exception Usage Patterns by Scenario

### Scenario: Entity Not Found

**Current Usage (Inconsistent):**
```java
// ✅ Good
AuthAttemptService.getById()     → ResourceNotFoundException
EnrollmentService.getById()      → ResourceNotFoundException

// ❌ Inconsistent
AuthAttemptService.create()      → IllegalArgumentException("Enrollment not found")
EnrollmentBindService.bind()     → IllegalArgumentException("Integration not found")
AuthAttemptRespondService        → IllegalArgumentException("Auth attempt record not found")
```

**Recommended (Consistent):**
```java
// ALL should use ResourceNotFoundException
AuthAttemptService.create()      → ResourceNotFoundException("Enrollment", id)
EnrollmentBindService.bind()     → ResourceNotFoundException("Integration", id)
AuthAttemptRespondService        → ResourceNotFoundException("AuthAttempt", id)
```

**Impact:** 3 fixes needed, improves HTTP status code consistency

---

### Scenario: State Machine Violations

**Current Usage (Mostly Good):**
```java
// ✅ Good - uses IllegalStateException
"Enrollment already bound"
"Enrollment already verified"
"Authentication attempt superseded"
"Authentication attempt expired"
"Enrollment must be bound before verification"

// ⚠️ Not specific enough
"Enrollment binding failed"  // What failed exactly?
"Authentication request failed"  // Too generic
```

**Recommended:**
```java
// More specific exceptions
throw new EnrollmentAlreadyBoundException(enrollmentId);
throw new EnrollmentNotBoundException(enrollmentId);
throw new AuthAttemptSupersededException(attemptId, newerAttemptId);
throw new AuthAttemptExpiredException(attemptId, expiresAt);
throw new AuthAttemptNotReadException(attemptId);
```

**Benefits:**
- Precise error identification
- Better error recovery logic
- Improved monitoring/alerting
- Clear HTTP status codes (all 409)

---

### Scenario: Security Validation Failures

**Current Usage:**
```java
// Generic messages for security
"Authentication request failed"  // Could be signature, token reuse, etc.
"Enrollment binding failed"  // Could be proof token, signature, etc.

// Specific messages  
"Invalid signature for auth attempt code"
"Challenge value mismatch"
"Device proof token already used"
"Device public key already registered"
```

**Tension:** Security vs Debuggability
- **Secure:** Generic messages prevent enumeration
- **Debug:** Specific messages help developers

**Recommended Strategy:**
```java
// Public API: Generic message
throw new SecurityValidationException("Authentication request failed");

// Internal logging: Specific details
logger.warn("Specific failure: Invalid signature for enrollment {}", enrollmentId);

// Exception carries both:
public class SecurityValidationException extends EzkeyClientException {
    private final String publicMessage;  // Generic
    private final String internalReason;  // Specific (logged, not exposed)
    
    public SecurityValidationException(String publicMessage, String internalReason) {
        super(publicMessage);
        this.internalReason = internalReason;
    }
}
```

---

## GlobalExceptionHandler Improvements

### Required Changes

#### 1. Admin API - Add IllegalStateException Handler

**Current:** Falls through to RuntimeException → 500

**Add:**
```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponseDto> handleIllegalStateException(
    IllegalStateException ex, WebRequest request) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        "STATE_CONFLICT", 
        ex.getMessage(), 
        request.getDescription(false).replace("uri=", ""));
    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);  // 409
}
```

**Impact:** Consistent 409 CONFLICT for state machine violations

#### 2. Admin API - Add AuthenticationException Handler

**Current:** Handled in controller catch block

**Add:**
```java
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ErrorResponseDto> handleAuthenticationException(
    AuthenticationException ex, WebRequest request) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        "AUTHENTICATION_FAILED", 
        ex.getMessage(), 
        request.getDescription(false).replace("uri=", ""));
    return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);  // 401
}
```

**Impact:** Centralized authentication error handling, consistent 401 responses

#### 3. Crypto API - Add ResourceNotFoundException Handler

**Add:**
```java
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ErrorResponseDto> handleResourceNotFoundException(
    ResourceNotFoundException ex, HttpServletRequest request) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        "RESOURCE_NOT_FOUND", 
        ex.getMessage(), 
        request.getRequestURI());
    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);  // 404
}
```

---

## Implementation Plan

### Phase 1: Fix Critical Inconsistencies (High Priority)

**Effort:** 2-3 hours  
**Impact:** Fixes HTTP status code inconsistencies

**Tasks:**
1. Add `IllegalStateException` handler to Admin API GlobalExceptionHandler (409)
2. Change `AuthAttemptService.create()` to throw `ResourceNotFoundException` for missing enrollment
3. Change `AuthAttemptRespondService.validateAndGetAttempt()` to throw `ResourceNotFoundException`
4. Change `EnrollmentBindService.validateIntegration()` to throw `ResourceNotFoundException`
5. Add unit tests to validate HTTP status codes

**Files to Modify:**
- `ezkey-admin-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java`
- `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java`
- `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java`
- `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentBindService.java`

**Tests to Add:**
- Controller integration tests verifying HTTP status codes
- Update `ErrorHandlingBehaviorTest` with new scenarios

---

### Phase 2: Introduce Custom Exception Hierarchy (Medium Priority)

**Effort:** 3-4 days  
**Impact:** Better code clarity, improved monitoring

**New Exceptions to Create:**

#### Core Library (ezkey-core)

**Base Classes:**
```java
org.ezkey.exception.EzkeyException (abstract base)
org.ezkey.exception.EzkeyClientException extends EzkeyException (400-level)
org.ezkey.exception.EzkeyServerException extends EzkeyException (500-level)
```

**Client Exceptions (400-level):**
```java
ValidationException extends EzkeyClientException  // 400
InvalidStateException extends EzkeyClientException  // 409
EnrollmentNotBoundException extends InvalidStateException  // 409
EnrollmentAlreadyBoundException extends InvalidStateException  // 409
AuthAttemptExpiredException extends InvalidStateException  // 409
AuthAttemptSupersededException extends InvalidStateException  // 409
InvalidSignatureException extends ValidationException  // 400
InvalidChallengeException extends ValidationException  // 400
DeviceAlreadyRegisteredException extends InvalidStateException  // 409
ProofTokenMismatchException extends ValidationException  // 400
```

**Server Exceptions (500-level):**
```java
CryptographicException extends EzkeyServerException  // 500
SignatureGenerationException extends CryptographicException  // 500
KeyPairGenerationException extends CryptographicException  // 500
ProofTokenGenerationException extends CryptographicException  // 500
```

#### Admin API (ezkey-admin-api)

**Authentication Exceptions:**
```java
AuthenticationException extends EzkeyClientException  // Base for auth errors
InvalidCredentialsException extends AuthenticationException  // 401
AccountInactiveException extends AuthenticationException  // 403
DeviceNotEnrolledException extends AuthenticationException  // 400
RecoveryCodeInvalidException extends AuthenticationException  // 403
TokenExpiredException extends AuthenticationException  // 401
```

**Migration Strategy:**
- Create new exceptions gradually
- Maintain backward compatibility (extend same base classes)
- Update GlobalExceptionHandlers to handle new types
- Add @ExceptionHandler for each specific exception

---

### Phase 3: Add Comprehensive HTTP Status Tests (High Priority)

**Effort:** 1-2 days  
**Impact:** Prevents regressions, validates consistency

**Test Strategy:**
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AdminApiHttpStatusTest {
    
    @Test
    void whenResourceNotFound_thenReturns404() {
        // GET /integrations/99999
        ResponseEntity response = restTemplate.getForEntity("/api/v1/integrations/99999");
        assertEquals(404, response.getStatusCodeValue());
        assertEquals("RESOURCE_NOT_FOUND", errorBody.code());
    }
    
    @Test
    void whenInvalidState_thenReturns409() {
        // Try to bind already bound enrollment
        assertEquals(409, response.getStatusCodeValue());
        assertEquals("STATE_CONFLICT", errorBody.code());
    }
    
    @Test
    void whenValidationFailure_thenReturns400() {
        // POST with missing required field
        assertEquals(400, response.getStatusCodeValue());
        assertEquals("INVALID_ARGUMENT", errorBody.code());
    }
}
```

**Coverage:**
- All exception types
- All GlobalExceptionHandlers
- All controllers
- Edge cases

---

## Migration Strategy

### Step 1: Baseline Tests (Completed ✅)

- ✅ `ErrorHandlingBehaviorTest` - 15 tests documenting current behavior
- ✅ `ERROR_HANDLING_BASELINE.md` - Documentation of current state
- ✅ All tests passing

### Step 2: Fix Critical Inconsistencies (Next)

**Priority:** HIGH - Affects API contracts

1. Add `IllegalStateException` → 409 handler to Admin API
2. Fix "not found" scenarios to use `ResourceNotFoundException`
3. Add HTTP status integration tests
4. Validate no breaking changes

**Testing:**
```bash
# Run existing tests - should still pass
mvn test

# Run new HTTP status tests
mvn test -Dtest=*HttpStatusTest
```

### Step 3: Introduce Custom Exceptions (Gradual)

**Priority:** MEDIUM - Improves code quality

1. Create base exception classes
2. Create specific exceptions one domain at a time:
   - Start with AuthAttempt domain (most complex)
   - Then Enrollment domain
   - Then Integration domain
3. Update services to use new exceptions
4. Update GlobalExceptionHandlers
5. Add tests for each new exception

**Backward Compatibility:**
```java
// Before
throw new IllegalStateException("Enrollment already bound");

// After
throw new EnrollmentAlreadyBoundException(enrollmentId);

// GlobalExceptionHandler handles both:
@ExceptionHandler(InvalidStateException.class)  // Catches EnrollmentAlreadyBoundException
public ResponseEntity<ErrorResponseDto> handleInvalidState(...) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);  // Still 409
}
```

### Step 4: Validate Controller Behavior (Continuous)

- Run ErrorHandlingBehaviorTest after each change
- Add new tests for new exceptions
- Verify HTTP status codes haven't changed
- Check OpenAPI spec consistency

---

## Benefits of Custom Exception Hierarchy

### 1. Code Clarity ✅

**Before:**
```java
throw new IllegalArgumentException("Auth attempt record not found");
// What does this mean?
// - Invalid ID format?
// - ID doesn't exist in DB?
// - ID belongs to different enrollment?
```

**After:**
```java
throw new ResourceNotFoundException("AuthAttempt", id);
// Clear: Resource doesn't exist → 404
```

### 2. Better Error Handling ✅

```java
// Controllers can catch specific exceptions
try {
    return authAttemptService.respond(request);
} catch (AuthAttemptExpiredException e) {
    // Return helpful message: "This authentication request has expired. Please create a new one."
} catch (AuthAttemptSupersededException e) {
    // Return helpful message: "A newer authentication request exists. Use that one instead."
} catch (InvalidSignatureException e) {
    // Return security message: "Cryptographic validation failed"
}
```

### 3. Monitoring and Alerting ✅

```java
// Metrics can track specific exception types
meterRegistry.counter("ezkey.exceptions", "type", "AuthAttemptExpired").increment();
meterRegistry.counter("ezkey.exceptions", "type", "InvalidSignature").increment();
meterRegistry.counter("ezkey.exceptions", "type", "EnrollmentAlreadyBound").increment();

// Alert on specific patterns
if (invalidSignatureCount > threshold) {
    alert("Possible attack: High number of invalid signatures");
}
```

### 4. Security Message Control ✅

```java
public class SecurityValidationException extends ValidationException {
    private final String publicMessage = "Validation failed";
    private final String internalReason;  // Logged, not exposed
    
    @Override
    public String getMessage() {
        return publicMessage;  // Safe for API response
    }
    
    public String getInternalReason() {
        return internalReason;  // For logging only
    }
}
```

---

## Backward Compatibility Guarantee

### HTTP Status Codes - NO CHANGES

| Scenario | Current HTTP | After Custom Exceptions | Change |
|----------|--------------|------------------------|--------|
| Resource not found | 404 | 404 | ✅ No change |
| Validation error | 400 | 400 | ✅ No change |
| State conflict | 409 (auth-api)<br>500 (admin-api) | 409 (both) | ⚠️ Fix needed |
| Crypto error | 500 | 500 | ✅ No change |
| No pending | 204 | 204 | ✅ No change |

**Only Change:** Admin API IllegalStateException should return 409, not 500 (bug fix)

### Error Response Format - NO CHANGES

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "Authentication attempt with id 99999 not found",
  "path": "/api/v1/auth-attempts/99999",
  "timestamp": "2025-10-14T19:00:00Z"
}
```

Format remains identical, only internal exception types change.

---

## Testing Strategy for Migration

### 1. Baseline Tests (Already Complete ✅)

- `ErrorHandlingBehaviorTest` - 15 tests
- `ERROR_HANDLING_BASELINE.md` - Documentation
- All tests passing

### 2. HTTP Status Integration Tests (To Add)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class HttpStatusIntegrationTest {
    
    @TestTemplate
    void allExceptionsMaintainCorrectHttpStatus() {
        // Test matrix of exceptions → HTTP codes
    }
    
    @TestTemplate
    void errorResponseFormatIsConsistent() {
        // Validate JSON structure
    }
}
```

### 3. Exception Hierarchy Tests (To Add)

```java
class ExceptionHierarchyTest {
    
    @Test
    void allClientExceptionsInheritFromEzkeyClientException() {
        assertTrue(ResourceNotFoundException.class
            .isAssignableFrom(EzkeyClientException.class));
    }
    
    @Test
    void allServerExceptionsInheritFromEzkeyServerException() {
        // Validate inheritance
    }
}
```

---

## Recommendations Summary

### Immediate Actions (This Week)

1. **Add IllegalStateException → 409 handler to Admin API** (30 min)
2. **Fix "not found" scenarios to use ResourceNotFoundException** (1 hour)
3. **Add HTTP status integration tests** (3-4 hours)

**Total Effort:** ~1 day  
**Impact:** Fixes inconsistencies, validates current behavior

### Short Term (Next 2 Weeks)

4. **Design custom exception hierarchy** (4 hours planning)
5. **Implement base exception classes** (2 hours)
6. **Migrate AuthAttempt domain to custom exceptions** (1 day)
7. **Update GlobalExceptionHandlers** (2 hours)
8. **Add exception-specific tests** (1 day)

**Total Effort:** ~3-4 days  
**Impact:** Improved code quality, better monitoring

### Medium Term (Next Month)

9. **Migrate Enrollment domain** (1 day)
10. **Migrate Integration domain** (1 day)
11. **Add security validation exceptions** (1 day)
12. **Comprehensive documentation** (1 day)

**Total Effort:** ~4 days  
**Impact:** Complete exception system modernization

---

## Exception Count and Classification

### Current System

```
Custom Exceptions: 3
  - ResourceNotFoundException (core)
  - NoPendingAuthAttemptException (core)
  - AuthenticationException (admin-api)

Generic Exceptions: 37 uses
  - IllegalArgumentException: 24 uses
  - IllegalStateException: 10 uses
  - RuntimeException: 3 uses
```

### Proposed System (After Full Migration)

```
Custom Exceptions: ~20
  Base: 3 (EzkeyException, EzkeyClientException, EzkeyServerException)
  Client: ~12 (404, 400, 409 scenarios)
  Server: ~5 (crypto failures)

Generic Exception Uses: ~5 (only for truly generic scenarios)
```

---

## Risk Assessment

### Low Risk Changes ✅

- Adding `IllegalStateException` → 409 handler (affects only admin-api)
- Creating new exception classes (backward compatible)
- Adding more specific handlers (fallback to existing handlers)

### Medium Risk Changes ⚠️

- Changing existing throws from generic to custom
  - **Mitigation:** Comprehensive testing before/after
  - **Validation:** ErrorHandlingBehaviorTest ensures no regression

- Changing exception messages
  - **Mitigation:** Keep same message format
  - **Validation:** Message format tests

### Zero Risk Changes ✅

- Adding new exception classes (not used yet)
- Adding tests
- Documentation updates

---

## Conclusion

**Current State:** Functional but uses too many generic exceptions  
**Target State:** Domain-specific exception hierarchy with clear HTTP mapping  
**Migration Path:** Gradual, test-driven, backward compatible

**Recommendation:** Proceed with **Phase 1** (fix critical inconsistencies) immediately, then plan **Phase 2** (custom hierarchy) for next iteration.

---

**Next Actions:**

1. Review this report with team
2. Approve exception hierarchy design
3. Implement Phase 1 fixes (1 day)
4. Add HTTP status integration tests (1 day)
5. Plan Phase 2 implementation

---

**Document Version:** 1.0  
**Analysis Date:** October 14, 2025  
**Status:** Ready for Review and Implementation

