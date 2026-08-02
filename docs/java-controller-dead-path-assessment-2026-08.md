# Java Controller Dead-Path Assessment (2026-08)

## Mandate

- **Surface:** Java controllers in team components — `ezkey-admin-api`, `ezkey-auth-api`,
  `ezkey-integration-api`, `ezkey-crypto-api`, `ezkey-demo-device`, `ezkey-demo-app-acme`
  (`src/main` only; tests used for corroboration).
- **Attention axes:** dead methods, unreachable / never-implemented controller paths, unused
  helper parameters that imply unfinished branches, error messages advertising features that do
  not exist, redundant checks whose deny path is effectively unreachable under normal control
  flow.
- **Non-goals:** runtime execution / DAST; deep service-layer dead code outside controllers;
  zero-warning campaigns; `java-doctor-curated` tool dumps as the entry signal; inventing
  `I-*` / `TB-*` per finding; program-sized dual-surface removals in this pass (noted only).

## Scope and method

- Static white-box review of **25** `*Controller.java` files under `src/main`.
- Searched for: zero-caller private helpers, unused injected fields, `if (true|false)`,
  `@Deprecated` / `TODO`/`FIXME` dead markers, commented-out mappings, phantom API fields in
  messages, unused method parameters, redundant ownership re-checks.
- HITL lane: [`product-docs/global/hygiene/java-controller-dead-paths/`](../product-docs/global/hygiene/java-controller-dead-paths/).

## Executive summary

Controllers are largely **alive**: no orphaned private methods, no unused constructor injections,
no commented-out request mappings, no classic `if (false)` scaffolding. Auth, Crypto, Demo Device,
and Demo ACME controller surfaces look clean for this mandate.

The quick-win signal concentrates in **Admin / Integration auth-attempt create helpers**: one
**phantom feature** advertised in an error string (`deviceHint`), a **bundle of unused helper
parameters**, one **redundant ownership re-check** on Integration API, and one **silent failure
catch** on Admin wait. A larger Admin API-key M2M dual surface exists but is **program-sized**
(TB-2026-05-25), not a drive-by delete.

## Findings register (HITL lot)

| ID | Title | Severity | Confidence | Quick win | Disposition |
| --- | --- | --- | --- | --- | --- |
| CTRL-DEAD-001 | Admin multi-device error advertises unimplemented `deviceHint` | P2 | High | Yes — align message with Integration API | **Closed (fixed 2026-08-02)** |
| CTRL-DEAD-002 | Unused auth-attempt helper parameters (`httpRequest`, `request`, `apiKeyId`) | P3 | High | Yes — mechanical signature cleanup | **Closed (fixed 2026-08-02)** |
| CTRL-DEAD-003 | Integration API `validateEnrollmentOwnership` after `resolveEnrollmentId` | P2 | Medium | Partial — keep as defense-in-depth **or** drop + rely on resolve | **Closed (fixed 2026-08-02)** |
| CTRL-DEAD-004 | Admin `waitForResponse` broad catch returns 500 with no log | P3 | High | Yes — log or defer to global handler | **Closed (fixed 2026-08-02)** |

## Category 2 — noted, not in this HITL lot

| ID | Title | Why deferred |
| --- | --- | --- |
| CTRL-DEAD-005 | Admin AuthAttempt still exposes full API-key M2M surface | Live until TB-2026-05-25 / `#169` gate; not dead bytecode |
| CTRL-DEAD-006 | Overlapping API-key list mappings | Both still used by Admin UI / specs; consolidation is contract work |
| CTRL-DEAD-007 | Cancel reuses wait rate-limit counters | Incomplete semantics, not unreachable code |
| CTRL-DEAD-008 | `trustedProxyProperties != null` ternary | Defensive; tests may pass null mocks |

## Evidence

### CTRL-DEAD-001 — Phantom `deviceHint` in Admin error text

When `userIdentifier` alone resolves to multiple VERIFIED enrollments, Admin API throws:

```562:565:ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java
      if (enrollments.size() > 1) {
        throw new AuthAttemptCreateValidationException(
            "Multiple enrollments for this user. Please specify enrollmentId or deviceHint.");
      }
```

Repo-wide `deviceHint` appears only in that string and an archived plan that already records:
*“API message mentions it; not implemented.”* Integration API correctly says only `enrollmentId`:

```671:674:ezkey-integration-api/src/main/java/org/ezkey/integration/api/controller/IntegrationApiAuthAttemptController.java
      if (enrollments.size() > 1) {
        throw new AuthAttemptCreateValidationException(
            "Multiple enrollments for this user. Please specify enrollmentId.");
      }
```

**Observation scenario:** Integrator or operator creates an auth attempt with `userIdentifier` for
a multi-device user; 400 text tells them to pass `deviceHint`, which is not on the DTO and will
never bind.

**Suggested fix:** Align Admin message with Integration API (drop `deviceHint`). Implementing
`deviceHint` is a separate product feature, not a dead-path cleanup.

**Disposition:** **closed** — HITL **fix**; Admin message aligned 2026-08-02.

### CTRL-DEAD-002 — Unused helper parameters

| Helper | Module | Unused parameter | Javadoc claim |
| --- | --- | --- | --- |
| `resolveEnrollmentId(...)` | Admin | `HttpServletRequest httpRequest` | “for path in error details” — never read |
| `extractApiKeyId(HttpServletRequest request)` | Admin | `request` | Auth read from `SecurityContextHolder` only |
| `validateEnrollmentOwnership(..., String apiKeyId)` | Admin + Integration | `apiKeyId` | “for logging” — logs use integration id only |

Integration API already uses zero-arg `extractApiKeyId()`.

**Suggested fix:** Remove unused params (and update call sites / Javadoc). Optionally log
`apiKeyId` if operator prefers keep-for-observability — that would make the param live, not dead.

**Disposition:** **closed** — HITL **fix**; unused params removed 2026-08-02.

### CTRL-DEAD-003 — Integration ownership re-check

`resolveEnrollmentId` already rejects cross-integration `enrollmentId` (Path 1) and resolves
`userIdentifier` paths under `extractIntegrationId()`. Create then calls
`validateEnrollmentOwnership` again (~235–252), whose only deny modes re-check the same ownership
(or fail if integration id cannot be extracted).

Under single-threaded request control flow the `AuthorizationDeniedException` branch is
**effectively unreachable** except for a narrow TOCTOU (enrollment moved between the two reads)
or a principal extraction failure that Path 1/2/3 would usually have failed earlier.

**Design fork:**

- **Keep** as intentional defense-in-depth (suppress with reason; still drop unused `apiKeyId`
  via CTRL-DEAD-002).
- **Remove** the second call and rely on resolve + service-layer checks (smaller controller).

Admin Path 1 still returns raw `enrollmentId` without ownership check, so Admin’s
`validateEnrollmentOwnership` remains **live** for API-key + enrollmentId-only creates — do not
remove there as part of this Integration finding.

**Disposition:** **closed** — HITL **fix**; Integration re-check and helper removed 2026-08-02.
Admin ownership check unchanged.

### CTRL-DEAD-004 — Silent 500 on Admin wait

```834:840:ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (AuthAttemptWaitValidationException e) {
      throw e;
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
```

Path is reachable, but observationally “dead” for operators: no logger, no audit. Create/cancel
paths surface more failure signal.

**Suggested fix:** Log at error with non-secret ids, or rethrow to the global RFC 9457 handler.

**Disposition:** **closed** — HITL **fix**; `logger.error` added 2026-08-02 (status still 500).

## Controllers scanned (25)

| Module | Controllers |
| --- | --- |
| ezkey-admin-api (14) | AdminAuth, AdminEnrollment, AdminProvisioning, Alert, ApiKey, AuditLog, AuthAttempt, Dashboard, EncryptionKey, Enrollment, Integration, PublicEvaluatorSignup, PublicInstanceInfo, Tenant |
| ezkey-auth-api (3) | AuthAttempt, Enrollment, PublicInstanceInfo |
| ezkey-integration-api (1) | IntegrationApiAuthAttempt |
| ezkey-crypto-api (1) | Crypto |
| ezkey-demo-device (3) | Home, Phone, EzkeyApp |
| ezkey-demo-app-acme (3) | Home, Login, BusinessApproval |

## Checked clean (for this mandate)

- No private controller methods with zero callers.
- No unused `private final` injections on controllers.
- No commented-out `@*Mapping` handlers.
- Crypto payload helper `switch` arms all map to used builders.
- Duplicate Admin/Auth `PublicInstanceInfo` is intentional parity.
- `PublicEvaluatorSignup` feature-flag → 404 when disabled is intentional, not dead mapping.

## Fail-open / fail-closed note

CTRL-DEAD-004’s broad catch is **fail-closed for the client** (500) but **fail-open for
observability** (silent). Prefer closing the observability gap without changing the HTTP status
contract unless the global handler already maps the same exceptions.
