# Validation Behavior Baseline - Ezkey Core

## Overview

This document characterizes the current validation behavior of all DTOs in the `ezkey-core` module. This baseline was established before implementing new validation annotations to ensure we understand the current behavior and can identify any valid divergences that should be preserved.

## Test Results Summary

**Date:** 2025-10-13  
**Test Class:** `ValidationBehaviorTest`  
**Total Tests:** 30  
**Results:** All tests PASSED (0 failures, 0 errors)

## Current Validation State

### Key Finding: **NO VALIDATION CURRENTLY IMPLEMENTED**

All DTOs in `ezkey-core` currently have **zero validation annotations**. This means:

- ✅ All null values are accepted
- ✅ All empty strings are accepted  
- ✅ All negative numbers are accepted
- ✅ All invalid formats are accepted
- ✅ All boundary violations are accepted

## DTOs Analyzed

### 1. AuthAttemptCreateRequest
- **enrollmentId**: No validation (accepts null, negative, zero)
- **challengeRequested**: No validation (accepts null)

### 2. AuthAttemptRespondRequest  
- **authAttemptId**: No validation (accepts null, negative, zero)
- **authAttemptProofTokenSignedByDevice**: No validation (accepts null, empty)
- **authAttemptChallengeResponse**: No validation (accepts null, negative)
- **authAttemptAccepted**: No validation (accepts null)

### 3. AuthAttemptWaitRequest
- **timeout**: No validation (accepts negative, zero, values > 300)
- **polling**: No validation (accepts negative, zero, values > timeout)

### 4. EnrollmentCreateRequest
- **integrationId**: No validation (accepts null, negative, zero)
- **name**: No validation (accepts null, empty, blank)
- **authAttemptChallengeRequired**: No validation (accepts null)

### 5. EnrollmentVerifyRequest
- **enrollmentId**: No validation (accepts null, negative, zero)
- **challengeResponse**: No validation (accepts null, negative)
- **devicePublicKey**: No validation (accepts null, empty, invalid format)
- **enrollmentProofTokenSigned**: No validation (accepts null, empty)

### 6. IntegrationCreateRequest
- **logo**: No validation (accepts null, empty, invalid URLs)
- **i18n**: No validation (accepts null, empty list)

### 7. IntegrationI18nCreate
- **language**: No validation (accepts null, empty, invalid codes)
- **name**: No validation (accepts null, empty, blank)
- **description**: No validation (accepts null, empty, blank)

## Business Logic Validation

While DTOs have no validation, some business logic validation exists in services:

### AuthAttemptWaitService
- **Timeout bounds**: 1-300 seconds (enforced in service logic)
- **Polling bounds**: 1-60 seconds (enforced in service logic)  
- **Polling ≤ timeout**: Enforced in service logic

### SignatureService
- **Key size validation**: Minimum 2048 bits (enforced in service logic)
- **Algorithm validation**: RSA algorithm validation (enforced in service logic)

## Implications for Future Validation

### 1. Safe to Add Validation
Since no validation currently exists, adding validation annotations will be **non-breaking** for existing code that follows proper practices.

### 2. Service Layer Validation
Some validation logic exists in services. When adding DTO validation, we should:
- **Preserve** existing service validation as fallback
- **Coordinate** DTO and service validation to avoid duplication
- **Document** the validation hierarchy (DTO → Service → Database)

### 3. Backward Compatibility
Any new validation should be:
- **Gradual**: Add validation incrementally
- **Configurable**: Allow disabling validation for migration periods
- **Well-documented**: Clear error messages for validation failures

## Recommended Validation Strategy

### Phase 1: Critical Fields (Non-Breaking)
Add validation for fields that are clearly invalid in any context:
- `@NotNull` for required IDs
- `@NotBlank` for required names
- `@Min(1)` for positive IDs

### Phase 2: Business Rules (Careful Review)
Add validation for business-specific rules:
- Challenge response ranges
- Timeout/polling bounds
- Cryptographic key formats

### Phase 3: Advanced Validation (Optional)
Add complex validation:
- Custom validators for cryptographic data
- Cross-field validation
- Format-specific validation (URLs, keys, etc.)

## Test Coverage

The `ValidationBehaviorTest` class provides comprehensive coverage:
- **Null value handling**: 15 test cases
- **Empty/blank string handling**: 8 test cases  
- **Boundary value testing**: 4 test cases
- **Invalid format testing**: 3 test cases

## Next Steps

1. **Review business requirements** for each DTO field
2. **Identify critical validations** that should be added first
3. **Implement validation incrementally** with proper testing
4. **Update service layer** to handle validation errors gracefully
5. **Document validation rules** in API documentation

---

**Note:** This baseline was established on 2025-10-13. Any changes to validation behavior should be documented and tested against this baseline.
