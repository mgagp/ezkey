---
name: AuthAttempt respond IAE catch and evolution
overview: Documents why respond uses catch (IllegalArgumentException) (AuthAttemptRequestFailedException extends IAE), and defines a gradual path toward explicit business-exception mapping end-to-end while accepting a possible HTTP contract shift when backed by tests.
todos:
  - id: narrow-catch
    content: "Optional phase 1: replace catch (IllegalArgumentException) with catch (AuthAttemptRequestFailedException) in AuthAttemptRespondService; add/adjust unit tests for respond path"
    status: pending
  - id: inheritance-followup
    content: "Optional phase 2: stop extending IllegalArgumentException for AuthAttemptRequestFailedException if API no longer relies on superclass catch (breaking change for any code that still catches IAE only)"
    status: pending
  - id: docs-endpoint
    content: "If HTTP contract changes (e.g. 400 Problem Details instead of 200 FAILED for some failures), update docs/ENDPOINT.md and auth-api OpenAPI annotations; maintainer runs update-specs after clean start"
    status: pending
isProject: false
---

# AuthAttempt respond — `IllegalArgumentException` catch and evolution

## Historical rationale (why it looks “generic”)

- [`AuthAttemptRequestFailedException`](ezkey-core/src/main/java/org/ezkey/exception/auth/AuthAttemptRequestFailedException.java) **extends `IllegalArgumentException` by design** so [`AuthAttemptRespondService`](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java) can `catch (IllegalArgumentException)` and return **HTTP 200** with a business-level `FAILED` result (and integration-signed payload when applicable), instead of letting the Auth API [`GlobalExceptionHandler`](ezkey-auth-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java) turn the same failure into **400** `ProblemDetail`.
- **State conflicts** (`AuthAttemptStateConflictException` extends `IllegalStateException`) are **not** caught and remain **409** — the superclass split is intentional.

This was a pragmatic compromise documented in Wave 1 analysis as out of scope for broad exception-mapping refactors; it is **not** accidental drift.

## Direction agreed: targeted, gradual “business” mapping

The current design is a **stable stepping stone**: it allows **incremental** moves toward **end-to-end business exception** handling (explicit types, explicit controller/API mapping, RFC 9457 catalog) **without** a big-bang rewrite.

Principles:

- **Gradual:** one vertical slice at a time (e.g. respond service catch → explicit type + handler alignment).
- **Targeted:** preserve security invariants (no raw `getMessage()` to clients; audit behavior unchanged unless explicitly revised).
- **Contract awareness:** narrowing the catch to `AuthAttemptRequestFailedException` or changing inheritance may alter the **HTTP** outcome for edge cases (e.g. a raw `IllegalArgumentException` from a library would no longer be absorbed into 200 `FAILED`). That is an **acceptable** evolution if **tests** and docs define the new contract.

## Potential API contract change (and risk mitigation)

| Scenario | Today | After narrowing catch to `AuthAttemptRequestFailedException` only |
|----------|--------|-------------------------------------------------------------------|
| `AuthAttemptRequestFailedException` | 200 + `FAILED` + safe message | Unchanged (still caught) |
| Raw `IllegalArgumentException` in respond try block | 200 + `FAILED` (absorbed by broad catch) | Likely **400** via legacy `IllegalArgumentException` handler — **behavior change** |

**Mitigation:** extend [`AuthAttemptRespondServiceTest`](ezkey-core/src/test/java/org/ezkey/authattempt/service/AuthAttemptRespondServiceTest.java) and [`AuthAttemptControllerTest`](ezkey-auth-api/src/test/java/org/ezkey/auth/controller/AuthAttemptControllerTest.java) (or equivalent) so expected status codes and body shapes are explicit; update [`docs/ENDPOINT.md`](docs/ENDPOINT.md) if the documented HTTP semantics change.

## Optional later phase: inheritance

If `AuthAttemptRequestFailedException` no longer needs to extend `IllegalArgumentException` for the respond service pattern, a follow-up could **rebase** it on `RuntimeException` (or another domain base) **after** all catch sites and `GlobalExceptionHandler` ordering are reviewed. That is a **wider** change and should be its own step.

## References

- [`docs/analysis/EXCEPTION_MAPPING_WAVE_1_ANALYSIS.md`](docs/analysis/EXCEPTION_MAPPING_WAVE_1_ANALYSIS.md) — respond catch-and-convert explicitly out of scope for Wave 1
- [`docs/ENDPOINT.md`](docs/ENDPOINT.md) — 200 with business `FAILED` on respond
