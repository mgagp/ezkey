# Exception Security Analysis - Ezkey

> **Historical (2025-10-14).** Leakage posture below is still useful as intent. Handler samples
> that return `ErrorResponseDto` are pre-migration — Admin/Auth/Integration APIs now return RFC
> 9457 `ProblemDetail`. Living canon:
> [`product-docs/components/admin-api/exception-and-error-model.md`](../product-docs/components/admin-api/exception-and-error-model.md).

**Date:** October 14, 2025  
**Purpose:** Security-focused analysis of exception handling  
**Critical:** Ensure no information leakage through exception messages

---

## Security Principle: Minimize Information Leakage

**Goal:** Generic error messages in public APIs, detailed logging internally

**Pattern Example:**
```java
// Service layer (detailed for logging)
throw new IllegalArgumentException("Enrollment not found for ID: 99999");

// Controller layer (security wrapper)
} catch (IllegalArgumentException e) {
    logger.warn("Validation failed: {}", e.getMessage());  // Internal log
    return ResponseEntity.badRequest().build();  // Public API: NO MESSAGE
}
```

---

## Exception Handling Patterns - Security Analysis

### Pattern A: Controller Try-Catch (SECURITY-FOCUSED)

**Used in: Admin API**

**Controllers that intercept exceptions:**
1. `AuthAttemptController.create()` - lines 178-190
2. `EnrollmentController.create()` - lines 167-179
3. `AuthAttemptController.wait()` - lines 295-316
4. `AdminEnrollmentController.reset()` - lines 117-151
5. `AdminAuthController.passwordlessWait()` - lines 176-203

**Security Pattern:**
```java
try {
    authAttemptService.create(request);
} catch (IllegalArgumentException e) {
    // ❌ NO MESSAGE EXPOSED - Security by obscurity
    return ResponseEntity.badRequest().build();
}
```

**Why this is secure:**
- Attacker tries invalid enrollmentId → 400 (generic)
- Attacker tries valid but non-existent enrollmentId → 400 (generic)
- **CANNOT enumerate** which enrollments exist

**Impact of changing to ResourceNotFoundException:**
```java
// If we change service to throw ResourceNotFoundException
try {
    authAttemptService.create(request);
} catch (ResourceNotFoundException e) {
    // Now returns 404 instead of 400
    // INFORMATION LEAK: Confirms enrollment doesn't exist
    return ResponseEntity.notFound().build();
}
```

**VERDICT:** ❌ **DO NOT CHANGE** - This is intentional security

---

### Pattern B: GlobalExceptionHandler (SEMI-SECURE)

**Used in: Auth API (mostly), Admin API (partially)**

**Security Pattern:**
```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(...) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        "INVALID_ARGUMENT", 
        ex.getMessage(),  // ⚠️ MESSAGE IS EXPOSED
        request.getDescription(false));
    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
}
```

**Security Implication:**
- If exception message is "Enrollment not found for ID: 123" → **information leak**
- If exception message is "Authentication request failed" → ✅ secure

**Current Service Messages (Security Check):**

| Service Method | Exception Message | Exposed via Handler? | Secure? |
|----------------|-------------------|---------------------|---------|
| `AuthAttemptPendingService` | "Authentication request failed" | ✅ Yes (auth-api) | ✅ SECURE (generic) |
| `EnrollmentBindService` | "Enrollment binding failed" | ✅ Yes (auth-api) | ✅ SECURE (generic) |
| `AuthAttemptService.create()` | "Enrollment not found for ID: 99999" | ❌ NO (caught in controller) | ✅ SECURE (not exposed) |

**VERDICT:** ✅ **CURRENTLY SECURE** - Generic messages OR caught in controller

---

## Detailed Exception-by-Exception Analysis

### 1. AuthAttemptService.create() - "Enrollment not found"

**Current Code:**
```java
// AuthAttemptService.java - line 206
throw new IllegalArgumentException("Enrollment not found for ID: " + authRequest.getEnrollmentId());
```

**Controller Handling:**
```java
// AuthAttemptController.create() - line 183
} catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().build();  // NO MESSAGE
}
```

**Security Analysis:**
- ✅ Exception message is **NOT** exposed (caught in controller)
- ✅ Attacker sees only 400, no details
- ✅ Cannot enumerate enrollment IDs
- ❌ Changing to ResourceNotFoundException would leak info (404 vs 400)

**RECOMMENDATION:** ✅ **KEEP AS-IS** (security-focused)

**Alternative (if we want semantics):**
```java
// Service
throw new IllegalArgumentException("Invalid request");  // Even more generic

// Or create SecurityException that controllers handle specially
throw new SecurityValidationException("Enrollment validation failed");
// Controller catches SecurityValidationException → 400 without details
```

---

### 2. AuthAttemptRespondService - Multiple validation failures

**Current Code:**
```java
// Lines 144, 150, 162, 167, 192, 218, 258
throw new IllegalArgumentException("Auth attempt record not found");
throw new IllegalArgumentException("Auth attempt not read by device");
throw new IllegalStateException("Authentication attempt superseded...");
throw new IllegalStateException("Authentication attempt expired");
throw new IllegalArgumentException("Device public key not found");
throw new IllegalArgumentException("Invalid signature for auth attempt code");
throw new IllegalArgumentException("Challenge value mismatch");
```

**Service Handling (Internal Catch):**
```java
// AuthAttemptRespondService.respond() - line 125
} catch (IllegalArgumentException e) {
    return new AuthAttemptRespondResponse(AuthenticationResult.FAILED, e.getMessage());
}
```

**Controller Handling:**
```java
// Auth API: GlobalExceptionHandler exposes messages
// BUT respond() catches internally and returns structured response
// So messages NEVER reach GlobalExceptionHandler
```

**Security Analysis:**
- ✅ Exceptions caught **internally in service**
- ✅ Returns structured `FAILED` response with safe message
- ✅ Messages like "Invalid signature" are acceptable (no enumeration)
- ⚠️ Message "Auth attempt record not found" could leak info

**RECOMMENDATION:** 
```java
// Make "not found" message more generic
throw new IllegalArgumentException("Validation failed");  // Instead of "not found"
```

**OR:**
```java
// Still use IllegalArgumentException but with generic message
if (authAttemptOpt.isEmpty()) {
    throw new IllegalArgumentException("Authentication validation failed");
}
```

---

### 3. EnrollmentService.create() - "Integration ID is required"

**Current Code:**
```java
// EnrollmentService.java - line 182
if (request.getIntegrationId() == null) {
    throw new IllegalArgumentException("Integration ID is required");
}
```

**Controller Handling:**
```java
// EnrollmentController.create() - line 172
} catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().build();  // NO MESSAGE
}
```

**Security Analysis:**
- ✅ Message not exposed (caught in controller)
- ✅ This is validation error, 400 is correct
- ✅ No information leak
- ✅ Message is generic anyway ("required" doesn't leak data)

**RECOMMENDATION:** ✅ **KEEP AS-IS** (correct as-is)

---

### 4. AuthAttemptPendingService - "Authentication request failed"

**Current Code:**
```java
// Lines 138, 160, 169, 175, 209
throw new IllegalArgumentException("Authentication request failed");
throw new IllegalStateException("Authentication request failed");
```

**API Handling:**
```java
// Auth API: GlobalExceptionHandler EXPOSES messages
// Message: "Authentication request failed"
```

**Security Analysis:**
- ✅ Generic message "Authentication request failed"
- ✅ Doesn't reveal specific failure (signature vs token reuse vs enrollment)
- ✅ Logging has details internally:
  ```java
  logger.warn("Invalid signature for enrollment: {}", enrollmentId);
  throw new IllegalArgumentException("Authentication request failed");
  ```

**RECOMMENDATION:** ✅ **KEEP AS-IS** (security-focused, perfect pattern)

---

### 5. EnrollmentBindService - "Enrollment binding failed"

**Current Code:**
```java
// Lines 138, 176, 278, 286
throw new IllegalArgumentException("Enrollment binding failed");
throw new IllegalStateException("Enrollment binding failed");
```

**API Handling:**
```java
// Auth API: GlobalExceptionHandler EXPOSES messages
// Message: "Enrollment binding failed"
```

**Security Analysis:**
- ✅ Generic message doesn't reveal why
- ✅ Internal logging has details
- ✅ Perfect security pattern

**RECOMMENDATION:** ✅ **KEEP AS-IS** (security-focused)

---

### 6. EnrollmentVerifyService - Multiple specific messages

**Current Code:**
```java
// Lines 153, 164, 195, 221, 250, 278, 289, 304
throw new IllegalStateException("Enrollment already verified");
throw new IllegalStateException("Enrollment must be bound before verification");
throw new IllegalArgumentException("Invalid bind proof token signature");
throw new IllegalArgumentException("Device public key already registered");
throw new IllegalArgumentException("Invalid challenge response");
throw new IllegalStateException("Enrollment state changed");
```

**API Handling:**
```java
// Auth API: GlobalExceptionHandler EXPOSES messages
```

**Security Analysis:**

| Message | Exposed? | Information Leak Risk | Verdict |
|---------|----------|----------------------|---------|
| "Enrollment already verified" | ✅ Yes | ⚠️ MEDIUM (confirms enrollment exists) | ⚠️ Review |
| "Enrollment must be bound before verification" | ✅ Yes | ⚠️ LOW (state machine info) | ✅ OK |
| "Invalid bind proof token signature" | ✅ Yes | ✅ NONE (crypto validation) | ✅ OK |
| "Device public key already registered" | ✅ Yes | ⚠️ MEDIUM (enumeration risk) | ⚠️ Review |
| "Invalid challenge response" | ✅ Yes | ✅ NONE (validation) | ✅ OK |

**RECOMMENDATIONS:**

```java
// BEFORE (potential leak)
throw new IllegalStateException("Enrollment already verified");

// AFTER (more secure)
throw new IllegalStateException("Enrollment verification failed");

// Internal log still has details
logger.warn("Enrollment {} already verified", enrollmentId);
```

---

### 7. SignatureService - Cryptographic failures

**Current Code:**
```java
// Lines 139, 225, 252, 264, 297
throw new RuntimeException("Failed to generate signature", e);
throw new RuntimeException("RSA key pair generation failed", e);
throw new IllegalArgumentException("Challenge digits must be between 1 and 6, got: " + digits);
throw new RuntimeException("Failed to generate secure challenge", e);
throw new RuntimeException("Failed to generate proof token", e);
```

**API Handling:**
```java
// Both APIs: GlobalExceptionHandler 
// RuntimeException → 500 with "An unexpected error occurred" (generic)
// IllegalArgumentException → 400 with message exposed
```

**Security Analysis:**

| Message | Exposed? | Information Leak | Verdict |
|---------|----------|------------------|---------|
| "Failed to generate signature" | ❌ No (replaced with "unexpected error") | ✅ NONE | ✅ OK |
| "Challenge digits must be between 1 and 6, got: X" | ✅ Yes | ✅ NONE (validation info) | ✅ OK |

**RECOMMENDATION:** ✅ **KEEP AS-IS** (secure)

---

## Critical Security Findings

### SECURE Patterns (Keep As-Is) ✅

**1. Controller Try-Catch Without Message (Admin API)**
```java
} catch (IllegalArgumentException e) {
    logger.warn("Detailed info: {}", e.getMessage());
    return ResponseEntity.badRequest().build();  // No message
}
```
**Used in:** AuthAttemptController, EnrollmentController, AdminAuthController  
**Security Level:** ✅ EXCELLENT - Zero information leakage

**2. Generic "Failed" Messages**
```java
throw new IllegalArgumentException("Authentication request failed");
throw new IllegalArgumentException("Enrollment binding failed");
```
**Used in:** AuthAttemptPendingService, EnrollmentBindService  
**Security Level:** ✅ EXCELLENT - Prevents enumeration

**3. Internal Exception Catch (Structured Response)**
```java
// AuthAttemptRespondService.respond()
try {
    // validation
} catch (IllegalArgumentException e) {
    return new Response(FAILED, e.getMessage());  // Controlled message
}
```
**Security Level:** ✅ GOOD - Messages are controlled and acceptable

---

### POTENTIAL Information Leaks (Review Needed) ⚠️

**1. EnrollmentVerifyService - Specific state messages**
```java
throw new IllegalStateException("Enrollment already verified");
// Exposed via GlobalExceptionHandler in auth-api
// Confirms enrollment exists and is verified
// ⚠️ MEDIUM RISK - Could help enumeration
```

**Recommendation:**
```java
// Make message more generic
throw new IllegalStateException("Enrollment verification failed");
logger.warn("Enrollment {} already verified", enrollmentId);  // Internal only
```

**2. EnrollmentVerifyService - Device key registered**
```java
throw new IllegalArgumentException("Device public key already registered");
// Exposed via GlobalExceptionHandler
// Confirms device key is known to system
// ⚠️ LOW-MEDIUM RISK - Minor leak
```

**Recommendation:**
```java
throw new IllegalArgumentException("Enrollment verification failed");
logger.warn("Device key {} already registered", deviceKey);  // Internal only
```

---

## HTTP Status Code Security Implications

### 404 vs 400 - Enumeration Risk

**Scenario: Attacker tries random enrollment IDs**

**Current (Admin API):**
```
POST /auth-attempts {enrollmentId: 123} → 400
POST /auth-attempts {enrollmentId: 456} → 400
POST /auth-attempts {enrollmentId: 789} → 400
```
Result: ✅ All return 400, attacker learns nothing

**If changed to 404:**
```
POST /auth-attempts {enrollmentId: 123} → 404 (doesn't exist)
POST /auth-attempts {enrollmentId: 456} → 201 (exists!)
POST /auth-attempts {enrollmentId: 789} → 404 (doesn't exist)
```
Result: ❌ **ENUMERATION ATTACK** - Attacker can discover valid enrollment IDs

**CONCLUSION:** Controller try-catch returning 400 is **CRITICAL SECURITY FEATURE**

---

### 409 vs 400 - State Machine Information

**Scenario: Attacker tries to verify already verified enrollment**

**Current (Auth API):**
```
POST /enrollments/verify {enrollmentId: 123} → 409 "Enrollment already verified"
```

**Security Implication:**
- ⚠️ Confirms enrollment 123 exists
- ⚠️ Confirms enrollment 123 is verified
- ⚠️ Helps attacker understand system state

**More Secure:**
```
POST /enrollments/verify {enrollmentId: 123} → 400 "Enrollment verification failed"
```

**Tradeoff:**
- Less helpful for legitimate users debugging
- More secure against enumeration
- Need to choose based on threat model

---

## Exception Analysis by Security Context

### Public API (Mobile → Auth API)

**Threat Model:** Untrusted clients, potential attackers

**Current Strategy:**
- ✅ Generic messages: "Authentication request failed", "Enrollment binding failed"
- ✅ GlobalExceptionHandler exposes messages (but messages are generic)
- ✅ Detailed logging internally
- ⚠️ Some specific messages exposed ("Enrollment already verified")

**Recommendations:**
1. Audit all messages exposed via GlobalExceptionHandler
2. Make specific messages more generic ("already verified" → "verification failed")
3. Consider creating `SecureException` that GlobalExceptionHandler handles specially

---

### Internal API (Admin → Admin API)

**Threat Model:** Trusted administrators, but still security-conscious

**Current Strategy:**
- ✅ **Most secure:** Controller try-catch with no message
- ✅ Detailed logging for admin troubleshooting
- ✅ Some endpoints return messages (AdminAuthController login)

**Recommendations:**
1. Keep controller try-catch pattern (excellent security)
2. Don't change exceptions if caught in controller
3. Only improve exceptions that go through GlobalExceptionHandler

---

## Revised Recommendations (Security-Aware)

### ❌ DO NOT CHANGE (Security Critical)

**1. AuthAttemptService.create() - "Enrollment not found"**
```java
// KEEP:
throw new IllegalArgumentException("Enrollment not found for ID: " + id);
// Caught in controller → 400 without message

// DO NOT change to:
throw new ResourceNotFoundException("Enrollment", id);
// Would expose enrollment existence via 404
```

**2. Controller Try-Catch Patterns**
```java
// KEEP all controller try-catch without message
} catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().build();
}
```

---

### ✅ SAFE TO CHANGE (No Security Impact)

**1. Admin API - Add IllegalStateException Handler**
```java
// ADD to GlobalExceptionHandler
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponseDto> handleIllegalStateException(...) {
    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);  // 409
}
```

**Impact:** 
- Currently falls to RuntimeException → 500
- Should be 409 for state conflicts
- Messages are generic ("Authentication request failed") so no leak
- ✅ SAFE

**2. Methods that use ResourceNotFoundException correctly**
```java
// KEEP (already correct):
AuthAttemptService.getById() → ResourceNotFoundException
AuthAttemptService.delete() → ResourceNotFoundException
EnrollmentService.getById() → ResourceNotFoundException
```

---

### ⚠️ REVIEW AND MAKE MORE GENERIC (Security Improvement)

**1. EnrollmentVerifyService - Specific state messages**

**Current (potential leak):**
```java
throw new IllegalStateException("Enrollment already verified");
throw new IllegalArgumentException("Device public key already registered");
```

**Recommended (more secure):**
```java
throw new IllegalStateException("Enrollment verification failed");
throw new IllegalArgumentException("Enrollment verification failed");

// Detailed logging (internal only)
logger.warn("Enrollment {} already verified", enrollmentId);
logger.warn("Device key {} already registered", publicKeyHash);
```

**Impact:** Reduces information leakage, maintains functionality

---

## Corrected Exception Migration Strategy

### Phase 1: Fix HTTP Status Only (NO Security Regression)

**ONLY Safe Change:** Add `IllegalStateException` → 409 handler to Admin API

```java
// ezkey-admin-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponseDto> handleIllegalStateException(
    IllegalStateException ex, WebRequest request) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        "STATE_CONFLICT",
        ex.getMessage(),  // Messages are already generic
        request.getDescription(false).replace("uri=", ""));
    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
}
```

**Safety Check:**
- All IllegalStateException messages are generic ("Authentication request failed")
- No specific IDs or details in messages
- ✅ SAFE to add this handler

**DO NOT change:**
- ❌ Don't change AuthAttemptService.create() to ResourceNotFoundException
- ❌ Don't change any exceptions caught in controllers
- ❌ Don't add Resource NotFoundException for internal lookups

---

### Phase 2: Security Message Audit (Security Improvement)

**Review all exception messages for:**
1. Enrollment/Auth Attempt IDs exposure
2. Specific state information ("already verified")
3. Enumeration risks

**Make Messages More Generic:**

| Current Message | Risk | Recommended Message |
|----------------|------|---------------------|
| "Enrollment already verified" | MEDIUM | "Enrollment verification failed" |
| "Device public key already registered" | LOW | "Enrollment verification failed" |
| "Integration not found" | HIGH | Keep as-is (caught in controller) |
| "Auth attempt record not found" | MEDIUM | "Validation failed" |

---

### Phase 3: Custom Exception Hierarchy (If Needed)

**Only introduce custom exceptions that:**
1. ✅ Don't reveal more information than current messages
2. ✅ Are caught in controllers (not exposed via GlobalExceptionHandler)
3. ✅ Improve internal code clarity without harming security

**Example Safe Custom Exception:**
```java
// Internal use only - caught in controller
public class EnrollmentNotFoundInternalException extends IllegalArgumentException {
    public EnrollmentNotFoundInternalException(Integer enrollmentId) {
        super("Validation failed");  // Generic public message
        this.enrollmentId = enrollmentId;  // Available for logging
    }
}

// Controller
} catch (EnrollmentNotFoundInternalException e) {
    logger.warn("Enrollment {} not found", e.getEnrollmentId());
    return ResponseEntity.badRequest().build();  // Still 400, no message
}
```

**Benefits:**
- Type-safe exception catching
- Better logging
- **Zero security regression** (same HTTP code, no message)

---

## Conclusion - Security-First Approach

### Current Exception System Verdict: ✅ **SECURE BY DESIGN**

**Security Features:**
1. ✅ Controller try-catch suppresses messages (Admin API)
2. ✅ Generic messages in services ("request failed")
3. ✅ Detailed logging internally
4. ✅ No enumeration possible

**Minor Improvements Needed:**
1. ⚠️ Make 2-3 messages more generic (EnrollmentVerifyService)
2. ✅ Add IllegalStateException → 409 handler (safe)
3. ❌ **DO NOT** change exceptions to ResourceNotFoundException if caught in controller

### Corrected Phase 1 Recommendations

**ONLY Safe Changes:**
1. ✅ Add `IllegalStateException` → 409 handler to admin-api GlobalExceptionHandler (30 min)
2. ⚠️ Make 3 exception messages more generic in EnrollmentVerifyService (30 min)
3. ✅ Add HTTP status integration tests (3 hours)

**Total Effort:** ~4 hours  
**Security Risk:** ✅ ZERO (only improvements)  
**Backward Compat:** ✅ 100% (same HTTP codes, same or more generic messages)

---

### DO NOT Implement (Security Regression Risk)

❌ Change AuthAttemptService.create() to ResourceNotFoundException  
❌ Change any exception caught in controller try-catch  
❌ Expose specific "not found" details via GlobalExceptionHandler  
❌ Change 400 → 404 for "not found" scenarios (enumeration risk)

---

**Next Action:** Present corrected analysis to user, get approval before ANY changes

---

**Document Version:** 1.0 (Security-Focused Revision)  
**Created:** October 14, 2025  
**Security Review:** Complete  
**Risk Level:** Critical analysis - no changes without security review

