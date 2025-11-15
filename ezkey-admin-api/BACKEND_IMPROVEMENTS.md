# Backend Improvements

## Date: 2025-01-XX
## Purpose: Track backend improvements and issues identified during CLI testing

---

## Issues Identified

### 1. HTTP 400 for Pending Challenge (Login Endpoint)

**Issue:** The `/api/v1/admin/auth/login` endpoint returns HTTP 400 Bad Request for pending passwordless authentication with challenge, even though this is a normal workflow state.

**Current Behavior:**
- When `challengeRequested=true`, the server returns `AdminLoginResponseDto` with `success=false` and `status="pending"`
- The controller checks only `response.success()`, not `response.status()`
- Result: HTTP 400 is returned for a normal pending state

**Impact:**
- Incorrect HTTP semantics (400 = bad request, but request is valid)
- Audit logs mark pending as failures
- Rate limiting may block legitimate users
- Clients must parse 400 responses to detect pending state

**Recommended Fix:**
- Modify `AdminAuthController.login()` to check `status` in addition to `success`
- Return HTTP 200 for pending state: `if (response.success() || "pending".equals(response.status()))`
- Update audit logging to distinguish pending from failures
- Update rate limiting to not count pending as failures

**References:**
- `AdminAuthController.java` line 108-144
- `AdminLoginResponseDto.java` line 121-138
- `AdminAuthService.java` line 192-198

**Priority:** Medium (works with workaround, but incorrect semantics)

**Status:** ✅ **FIXED** - Modified `AdminAuthController.login()` to check `status` in addition to `success`. Pending state now returns HTTP 200 with proper audit logging and rate limiting.

---

### 2. Java Compilation Flag Missing: `-parameters`

**Issue:** The audit log endpoint (`/api/v1/audit-logs`) fails with HTTP 400 when called without filter parameters, due to missing parameter name information in compiled bytecode.

**Error Message:**
```
HTTP 400: Name for argument of type [org.ezkey.audit.domain.EventType] not specified, 
and parameter name information not available via reflection. 
Ensure that the compiler uses the '-parameters' flag.
```

**Root Cause:**
- Java compiler needs `-parameters` flag to preserve parameter names in bytecode
- Spring uses parameter names for request parameter binding
- Without this flag, Spring cannot bind enum parameters from query strings

**Impact:**
- Audit log endpoint cannot be called without explicit filter parameters
- CLI must always specify `--page` and `--size` as workaround
- Other endpoints with enum parameters may have similar issues

**Recommended Fix:**
1. **Maven Configuration:** Add compiler plugin configuration with `-parameters` flag
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-compiler-plugin</artifactId>
       <configuration>
           <parameters>true</parameters>
       </configuration>
   </plugin>
   ```

2. **Gradle Configuration (if applicable):**
   ```gradle
   compileJava {
       options.compilerArgs << '-parameters'
   }
   ```

3. **Verify:** Recompile and test audit log endpoint without parameters

**References:**
- `AuditLogController.java` line 106-119
- Spring Framework parameter binding documentation

**Priority:** High (blocks normal usage of audit log endpoint)

**Status:** ✅ **FIXED** - Added `<parameters>true</parameters>` to maven-compiler-plugin configuration in parent `pom.xml` (line 219). Requires recompilation of the backend.

---

### 3. Validation Errors Return HTTP 500 Instead of 400

**Issue:** The `/api/v1/admin/auth/recover` endpoint (and potentially others) returns HTTP 500 Internal Server Error when Bean Validation fails, instead of HTTP 400 Bad Request.

**Current Behavior:**
- When `@Valid` validation fails (e.g., `@Pattern` constraint violation), Spring throws `MethodArgumentNotValidException`
- `GlobalExceptionHandler` does not have a specific handler for `MethodArgumentNotValidException`
- The exception is caught by the generic `@ExceptionHandler(Exception.class)` handler
- Result: HTTP 500 is returned for validation errors (should be 400)

**Example:**
- Request: `POST /api/v1/admin/auth/recover` with invalid recovery code format `kkkkkk`
- Expected: HTTP 400 with validation error message
- Actual: HTTP 500 with generic "An unexpected error occurred"

**Impact:**
- Incorrect HTTP semantics (500 = server error, but this is a client validation error)
- Generic error messages don't help users understand what's wrong
- Clients cannot distinguish validation errors from actual server errors
- Security: Validation errors should be 400, not 500

**Security Consideration:**
- The generic handlers (`handleRuntimeException` and `handleGenericException`) intentionally return generic messages ("An unexpected error occurred") to avoid exposing sensitive server information
- However, `MethodArgumentNotValidException` is a **client-side validation error**, not a server error
- Validation error messages are safe to expose - they don't reveal server internals, only client input validation issues
- These messages are already defined in `@Pattern` and other validation annotations, so they're part of the API contract

**Recommended Fix:**
Add a specific exception handler for `MethodArgumentNotValidException` in `GlobalExceptionHandler`:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(
    MethodArgumentNotValidException ex, WebRequest request) {
  
  // Extract validation error messages
  // These are safe to expose - they're client validation errors, not server errors
  List<String> errors = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .map(error -> error.getField() + ": " + error.getDefaultMessage())
      .collect(Collectors.toList());
  
  String message = String.join("; ", errors);
  
  ErrorResponseDto errorResponse = new ErrorResponseDto(
      "VALIDATION_ERROR",
      message,
      request.getDescription(false).replace("uri=", ""));
  
  return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
}
```

**Note:** This handler should be placed **before** the generic `RuntimeException` and `Exception` handlers to ensure it catches validation errors specifically. The order of `@ExceptionHandler` methods matters - more specific exceptions should be handled first.

**References:**
- `GlobalExceptionHandler.java` - Missing handler for `MethodArgumentNotValidException`
- `AdminRecoveryRequestDto.java` - Uses `@Pattern` validation for recovery code
- Spring Framework Bean Validation documentation

**Priority:** High (incorrect HTTP semantics, poor user experience)

**Status:** ✅ **FIXED** - Added `handleMethodArgumentNotValidException` handler in `GlobalExceptionHandler` (placed before generic handlers). Validation errors now return HTTP 400 with detailed messages.

---

## Notes

- These issues were identified during CLI testing and usability validation
- CLI has workarounds implemented, but backend fixes are recommended for proper semantics
- Focus on CLI testing continues; backend improvements will be addressed in separate work

---

## Future Improvements

(Add additional backend improvements as they are identified)

