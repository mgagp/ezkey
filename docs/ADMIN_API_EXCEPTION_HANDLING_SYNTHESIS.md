# Single Synthesis: Admin API Exception Handling (RFC 9457)

**Date:** 2026-02-12
**Scope:** Admin API only (Auth API excluded, no RFC 9457 in Auth API)
**Goal:** One clear, pragmatic, actionable document.

---

## 1) Current state

- `GlobalExceptionHandler` is too large (~758 lines) and duplicates `ProblemDetail` creation.
- Two response formats coexist:
  - RFC 9457 `ProblemDetail` for admin business errors.
  - Legacy `ErrorResponseDto` for validation/DB/etc.
- Some services (e.g., AdminAuthService) catch exceptions and return DTOs, so RFC 9457 is not unified.

**Key implication:** RFC 9457 migration is partial as long as the service layer transforms exceptions into DTOs.

---

## 2) Structuring decision: migration mode

### Option A: progressive migration (recommended now)
- Services keep their existing DTOs.
- `GlobalExceptionHandler` (and future handlers) handles RFC 9457 for uncaught exceptions.
- Very low risk, immediate value.

### Option B: complete migration (later)
- All services throw exceptions, no error DTOs in services.
- `GlobalExceptionHandler` becomes the single RFC 9457 source.
- Cleaner, but higher effort and risk.

---

## 3) Recommended MVP (Phase 0, 2-3 days)

Objective: small refactor, zero behavior change.

### Steps
1. **Create `ExceptionHandlerBase`**
  - Centralizes `ProblemDetail` and `ErrorResponseDto` construction.
2. **Create `AuthenticationExceptionHandler`**
  - Move 7-8 auth handlers from `GlobalExceptionHandler`.
  - Important: add `@Component`.
3. **Clean `GlobalExceptionHandler`**
  - Remove only the moved auth handlers.
4. **Verify**
  - `mvn clean test` + simple smoke tests.

Immediate value: less duplication, reusable base, established pattern.

---

## 4) Target architecture (Admin API)

### Short-term pattern: handlers separated by category

```
exception/
  GlobalExceptionHandler.java
  handler/
    ExceptionHandlerBase.java
    AuthenticationExceptionHandler.java
    AuthorizationExceptionHandler.java
    ValidationExceptionHandler.java
    StandardExceptionHandler.java
```

**Benefits:** readability, clear responsibilities, progressive evolution, low risk.

---

## 5) Real risks (low) and mitigations

- **Risk 1: missing `@Component`** -> handler not registered => 500.
  - Mitigation: checklist + review.
- **Risk 2: missed handler** during the move.
  - Mitigation: list handlers before moving.
- **Risk 3: duplication** (same exception in two handlers).
  - Mitigation: ensure each exception is declared only once.

**Estimated overall risk:** ~1-5% (mostly wiring/compilation).

---

## 6) Long-term strategy (200+ exceptions)

If RFC 9457 exceptions grow significantly (150-200+):

- **Separated handlers** become verbose (duplication, large files).
- **Annotation-driven** becomes more efficient:
  - RFC 9457 metadata in exception classes.
  - `GlobalExceptionHandler` stays small and generic.

**Pragmatic decision:**
- Short term: separated handlers (fast, low risk).
- Re-evaluate if 150-200+ exceptions are expected.

---

## 7) Tests to run

- `mvn clean test`
- Optional: `mvn clean verify`
- Admin login smoke test (401 expected for invalid credentials).

---

## 8) Simple team rules

1. Any new admin exception -> choose a category.
2. Add the handler in the specialized class.
3. Reuse `ExceptionHandlerBase` for `ProblemDetail`.
4. Do not touch Auth API (no RFC 9457 by design).

---

## 9) Executive summary

- **Do now:** MVP with `ExceptionHandlerBase` + `AuthenticationExceptionHandler`.
- **Why:** low risk, immediate value, pattern for what follows.
- **Watch for:** if we target 200+ exceptions, consider annotation-driven.

---

## 10) Lessons Learned: Handler Registration & Priority (Critical)

### Discovery: @RestControllerAdvice + @Component + @Order

When splitting `GlobalExceptionHandler` into specialized handlers, we discovered a **critical interaction** between three annotations:

**Problem:**
- Multiple `@RestControllerAdvice` classes (specialized handlers + global catch-all)
- Without explicit ordering, Spring registers handlers in unpredictable order
- Result: `GlobalExceptionHandler.handleGenericException(Exception)` (catch-all) captures exceptions intended for specialized handlers
- Example: `MethodArgumentNotValidException` → Global handler → 500 error instead of 400

**Solution (all three required):**
```java
@RestControllerAdvice       // ← Registers as exception handler
@Component                  // ← Ensures Spring picks it up early in initialization
@Order(N)                   // ← Defines explicit priority (lower = higher priority)
public class MyExceptionHandler {
  @ExceptionHandler(SpecificException.class)
  public ResponseEntity<...> handle(...) { ... }
}
```

**Why all three matter:**
1. `@RestControllerAdvice` alone may not guarantee proper registration order in complex bean graphs
2. `@Component` signals to Spring that this is a managed component needing priority control
3. `@Order` makes priority **explicit and deterministic**

### Recommended @Order Values

Use **spaced intervals** to allow future insertion without renumbering:

```
ValidationExceptionHandler (validation errors)     → @Order(10)
AuthenticationExceptionHandler (auth errors)       → @Order(20)
AuthorizationExceptionHandler (authz errors)      → @Order(30)
CustomBusinessExceptionHandler (if added later)   → @Order(40)
...future handlers...                             → @Order(50), @Order(60), etc.
GlobalExceptionHandler (catch-all 500s)           → @Order(99)
```

**Why 99 for catch-all?**
- All specific handlers (< 99) are checked first
- Provides "buffer space" for 80+ future specialized handlers without renumbering
- Self-documents: "this is the fallback, not a priority handler"
- Failure mode: if you forget @Order on a new handler, it defaults to 0 and intercepts everything (easy to spot)

### Checklist for Adding New Handlers

```
[ ] Handler class has @RestControllerAdvice
[ ] Handler class has @Component
[ ] Handler class has @Order(N) where N < 99
[ ] No two handlers share the same exception type
[ ] Test that specific exception maps to correct handler (not GlobalExceptionHandler)
[ ] Verify response status code matches exception category
```

---

## 9) Executive summary

- **Do now:** MVP with `ExceptionHandlerBase` + `AuthenticationExceptionHandler` + proper ordering.
- **Why:** low risk, immediate value, pattern for what follows.
- **Watch for:** if we target 200+ exceptions, consider annotation-driven.
- **Critical:** Use `@RestControllerAdvice` + `@Component` + `@Order(N)` together; catch-all at 99.

Encoding note: this file is UTF-8 without BOM.
