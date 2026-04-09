---
name: AuthAttempt respond IAE catch and evolution
overview: "Historical plan: narrow respond catch to AuthAttemptRequestFailedException; rebase exception on RuntimeException; document HTTP contract and tests. Implementation completed."
status: completed
completedNote: "Delivered: AuthAttemptRespondService catches AuthAttemptRequestFailedException; AuthAttemptRequestFailedException extends RuntimeException; ENDPOINT.md, AuthAttemptController OpenAPI text, AuthAttemptControllerTest (legacy IAE + ARFE), AuthAttemptService/Controller javadoc."
todos:
  - id: narrow-catch
    content: "Optional phase 1: replace catch (IllegalArgumentException) with catch (AuthAttemptRequestFailedException) in AuthAttemptRespondService; add/adjust unit tests for respond path"
    status: completed
  - id: inheritance-followup
    content: "Optional phase 2: stop extending IllegalArgumentException for AuthAttemptRequestFailedException if API no longer relies on superclass catch (breaking change for any code that still catches IAE only)"
    status: completed
  - id: docs-endpoint
    content: If HTTP contract changes (e.g. 400 Problem Details instead of 200 FAILED for some failures), update docs/ENDPOINT.md and auth-api OpenAPI annotations; maintainer runs update-specs after clean start
    status: completed
isProject: false
---

# AuthAttempt respond — `IllegalArgumentException` catch and evolution

**Archived — completed.** The sections below retain the original design discussion. **Current code:** `AuthAttemptRespondService` catches `AuthAttemptRequestFailedException` only; `AuthAttemptRequestFailedException` extends `RuntimeException`.

## Historical rationale (why it looked “generic”)

- [`AuthAttemptRequestFailedException`](ezkey-core/src/main/java/org/ezkey/exception/auth/AuthAttemptRequestFailedException.java) **used to extend `IllegalArgumentException`** so [`AuthAttemptRespondService`](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java) could `catch (IllegalArgumentException)` and return **HTTP 200** with a business-level `FAILED` result (and integration-signed payload when applicable), instead of letting the Auth API [`GlobalExceptionHandler`](ezkey-auth-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java) turn the same failure into **400** `ProblemDetail`.
- **State conflicts** (`AuthAttemptStateConflictException` extends `IllegalStateException`) are **not** caught and remain **409** — the superclass split is intentional.

This was a pragmatic compromise documented in Wave 1 analysis as out of scope for broad exception-mapping refactors; it is **not** accidental drift.

## Direction agreed: targeted, gradual “business” mapping

The current design is a **stable stepping stone**: it allows **incremental** moves toward **end-to-end business exception** handling (explicit types, explicit controller/API mapping, RFC 9457 catalog) **without** a big-bang rewrite.

Principles:

- **Gradual:** one vertical slice at a time (e.g. respond service catch → explicit type + handler alignment).
- **Targeted:** preserve security invariants (no raw `getMessage()` to clients; audit behavior unchanged unless explicitly revised).
- **Contract awareness:** narrowing the catch to `AuthAttemptRequestFailedException` or changing inheritance may alter the **HTTP** outcome for edge cases (e.g. a raw `IllegalArgumentException` from a library would no longer be absorbed into 200 `FAILED`). That is an **acceptable** evolution if **tests** and docs define the new contract.

## Potential API contract change (and risk mitigation)

| Scenario | Before broad IAE catch | After implementation |
|----------|-------------------------|----------------------|
| `AuthAttemptRequestFailedException` | 200 + `FAILED` + safe message | Unchanged (still caught in respond service) |
| Raw `IllegalArgumentException` in respond try block | 200 + `FAILED` (absorbed by broad catch) | **400** via legacy `IllegalArgumentException` handler — **behavior change** |

**Mitigation:** [`AuthAttemptRespondServiceTest`](ezkey-core/src/test/java/org/ezkey/authattempt/service/AuthAttemptRespondServiceTest.java) and [`AuthAttemptControllerTest`](ezkey-auth-api/src/test/java/org/ezkey/auth/controller/AuthAttemptControllerTest.java); [`docs/ENDPOINT.md`](docs/ENDPOINT.md) updated.

## Optional later phase: inheritance

**Done:** `AuthAttemptRequestFailedException` was rebased on `RuntimeException` after narrowing the respond `catch` and reviewing `GlobalExceptionHandler` (dedicated handler remains).

## References

- [`docs/analysis/EXCEPTION_MAPPING_WAVE_1_ANALYSIS.md`](docs/analysis/EXCEPTION_MAPPING_WAVE_1_ANALYSIS.md) — respond catch-and-convert explicitly out of scope for Wave 1
- [`docs/ENDPOINT.md`](docs/ENDPOINT.md) — 200 with business `FAILED` on respond
