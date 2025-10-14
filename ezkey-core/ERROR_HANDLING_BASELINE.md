# Error Handling Baseline - Ezkey Core

## Overview

This document characterizes the current error handling behavior across all services in the `ezkey-core` module. This baseline was established before implementing custom exception hierarchy to ensure that HTTP status codes remain consistent after refactoring.

## Test Results Summary

**Date:** 2025-10-13  
**Test Class:** `ErrorHandlingBehaviorTest`  
**Total Tests:** 15  
**Results:** All tests PASSED (0 failures, 0 errors)

## Current Exception Types and Behavior

### 1. ResourceNotFoundException
**Purpose:** Indicates that a requested resource cannot be found  
**Current Usage:** Used for missing entities (AuthAttempt, Enrollment)  
**Message Format:** `"{resource} with id {id} not found"`  
**Examples:**
- `"Authentication attempt with id 99999 not found"`
- `"Enrollment with id 99999 not found"`

### 2. IllegalArgumentException
**Purpose:** Indicates invalid input parameters or business rule violations  
**Current Usage:** Used for validation errors and business logic violations  
**Message Examples:**
- `"Integration ID is required"`
- `"Enrollment not found for ID: 99999"`
- `"Challenge digits must be between 1 and 6, got: 10"`

### 3. RuntimeException
**Purpose:** Indicates technical/cryptographic failures  
**Current Usage:** Used for signature generation failures and crypto errors  
**Message Examples:**
- `"Failed to generate signature"`
- `"RSA key pair generation failed"`
- `"Failed to generate secure challenge"`

### 4. NoPendingAuthAttemptException
**Purpose:** Indicates no pending authentication attempt found  
**Current Usage:** Used in authentication flow when no pending attempt exists

## Service-Specific Error Handling Patterns

### AuthAttemptService
- **getById()**: Throws `ResourceNotFoundException` for missing attempts
- **delete()**: Throws `ResourceNotFoundException` for missing attempts  
- **create()**: Throws `IllegalArgumentException` for missing enrollment (NOT ResourceNotFoundException)
- **respond()**: Returns `FAILED` response instead of throwing exceptions

### EnrollmentService
- **getById()**: Throws `ResourceNotFoundException` for missing enrollments
- **create()**: Throws `IllegalArgumentException` for missing integration ID

### SignatureService
- **generateSignature()**: Throws `RuntimeException` for invalid keys
- **generateRsaKeyPair()**: Accepts invalid key sizes (no validation currently)
- **generateSecureChallenge()**: Throws `IllegalArgumentException` for invalid digits

## Key Findings for HTTP Status Code Preservation

### Inconsistencies Identified

1. **AuthAttemptService.create()** throws `IllegalArgumentException` instead of `ResourceNotFoundException` for missing enrollment
   - **Current:** HTTP 400 Bad Request
   - **Expected:** HTTP 404 Not Found (should be consistent with other "not found" scenarios)

2. **SignatureService.generateRsaKeyPair()** accepts invalid key sizes without validation
   - **Current:** HTTP 200 OK (succeeds)
   - **Expected:** HTTP 400 Bad Request (should validate minimum key size)

### Consistent Behaviors to Preserve

1. **ResourceNotFoundException** → HTTP 404 Not Found
2. **IllegalArgumentException** → HTTP 400 Bad Request  
3. **RuntimeException** → HTTP 500 Internal Server Error
4. **AuthAttemptService.respond()** returns structured FAILED responses → HTTP 200 OK with error details

## Exception Message Consistency

### ResourceNotFoundException Messages
All follow the format: `"{resource} with id {id} not found"`
- ✅ Consistent across all services
- ✅ Provides clear context for debugging
- ✅ Includes the actual ID that was searched for

### IllegalArgumentException Messages
- ✅ Descriptive and actionable
- ✅ Include the actual invalid value when relevant
- ✅ Provide clear guidance on valid ranges/values

### RuntimeException Messages
- ✅ Include the original cause for debugging
- ✅ Use consistent "Failed to..." prefix
- ✅ Provide technical context without exposing sensitive data

## Error Handling Patterns

### Pattern 1: Resource Not Found
```java
// Consistent across services
throw new ResourceNotFoundException("ResourceType", id);
```

### Pattern 2: Input Validation
```java
// Used for business rule violations
throw new IllegalArgumentException("Descriptive message with actual value");
```

### Pattern 3: Technical Failures
```java
// Used for crypto/system errors
throw new RuntimeException("Failed to perform operation", cause);
```

### Pattern 4: Structured Error Responses
```java
// Used in AuthAttemptService.respond()
return new AuthAttemptRespondResponse(AuthenticationResult.FAILED, errorMessage);
```

## Recommendations for Refactoring

### 1. Maintain HTTP Status Code Consistency
- Ensure `ResourceNotFoundException` always maps to HTTP 404
- Ensure `IllegalArgumentException` always maps to HTTP 400
- Ensure `RuntimeException` always maps to HTTP 500

### 2. Fix Identified Inconsistencies
- Change `AuthAttemptService.create()` to throw `ResourceNotFoundException` for missing enrollment
- Add validation to `SignatureService.generateRsaKeyPair()` for minimum key size

### 3. Preserve Structured Error Responses
- Keep `AuthAttemptService.respond()` returning structured responses instead of throwing exceptions
- Maintain the pattern of returning `FAILED` responses with descriptive messages

### 4. Exception Hierarchy Design
- Create base exception classes that map to specific HTTP status codes
- Ensure all custom exceptions extend the appropriate base class
- Maintain backward compatibility with existing exception messages

## Testing Strategy

### Before Refactoring
- ✅ All current behaviors documented and tested
- ✅ HTTP status codes identified for each exception type
- ✅ Message formats characterized

### After Refactoring
- Re-run all baseline tests to ensure behavior preservation
- Add integration tests to verify HTTP status codes
- Validate that exception messages remain consistent
- Ensure no breaking changes in API responses

## Conclusion

The current error handling system is generally well-structured with consistent patterns. The main areas for improvement are:

1. **Consistency**: Fix the `AuthAttemptService.create()` exception type
2. **Validation**: Add proper validation to cryptographic operations
3. **Structure**: Maintain the existing structured error response patterns

The goal is to improve the internal exception hierarchy while preserving all existing HTTP status codes and response formats for API consumers.
