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

Encoding note: this file is UTF-8 without BOM.
