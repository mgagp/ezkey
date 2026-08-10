# Ezkey Auth API — Agent Notes

This file is intended for coding agents working in `ezkey-auth-api/`.

## Non-negotiables

- All new/updated project content must be **in English**.
- Do not introduce `@Autowired`. Use constructor injection.
- Auth-API is consumed by **mobile devices** (React Native). Never break the
  `EnrollmentBindResponseDto` or `AuthAttemptPendingResponseDto` contracts without coordinating
  with mobile clients.
- **Errors:** HTTP **4xx/5xx** use **RFC 9457** `ProblemDetail` (`application/problem+json`). Map named
  exceptions in `GlobalExceptionHandler` with **safe** `detail`/`title` strings from
  `AuthApiProblemCatalog` — never copy `Throwable#getMessage()` to the client for security-sensitive
  flows. OpenAPI references `ProblemDetail`; generated specs under `specs/` are produced by
  `scripts/update-specs.sh` after a clean start. Agents may run that script only when explicitly
  authorized for the current task; never hand-edit generated spec files.

## Module responsibilities

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/public/instance-info` | Public instance metadata (branding, optional `authApiPublicBaseUrl`; same JSON as Admin API) |
| `POST /api/v1/enrollments/bind` | Device claims an enrollment slot (read-once guarantee) |
| `POST /api/v1/enrollments/verify` | Device completes enrollment with crypto keys |
| `POST /api/v1/enrollments/instance-info` | Integration-signed installation branding for enrolled clients |
| `POST /api/v1/auth-attempts/pending` | Device polls for a pending auth request |
| `POST /api/v1/auth-attempts/respond` | Device responds (accept/reject) to auth request |

## Running and testing

```bash
# Run module
mvn spring-boot:run -pl ezkey-auth-api

# Safe first validation after Java changes in this repo (from the repository root)
mvn spotless:apply
mvn checkstyle:check
mvn clean
mvn install -DskipTests

# Targeted follow-up tests after the baseline succeeds
mvn test -pl 'ezkey-auth-api,!ezkey-tests'
```

---

## Integration loading in the bind flow

`Integration` has flat `name` and `description` columns on `ezkey_integration` (V7 removed the
historical `ezkey_integration_i18n` table). `EnrollmentBindService.loadIntegration()` uses
standard `integrationRepository.findById()` — there is no i18n child collection to fetch.

For audit tenant resolution, `EnrollmentController.resolveTenantId()` uses the scalar projection
`integrationRepository.findTenantIdByIntegrationId()` so no `Integration` entity is loaded into
the persistence context in that path.

---

## Audit logging

All enrollment and auth-attempt endpoints write to `ezkey_audit_log` via `AuditLogService`. The
API name to use in this module is `ApiName.AUTH_API`. Errors thrown from the service layer are
caught, logged, and re-thrown — the audit log is the primary observability mechanism.

## Security invariants

- Enrollment bind has a **read-once guarantee**: once bound, the proof token cannot be reused.
  The `findAndLockUnreadById` native query enforces this with `SELECT FOR NO KEY UPDATE`.
- Device public keys are uniqueness-checked by SHA-256 hash (`Enrollment.devicePublicKeyHash`)
  to prevent replay attacks across enrollments.
- **Pending endpoint**: Each request must present a valid signature over `deviceProofToken`. The
  device proof token is **persisted only when a pending attempt is claimed** (200). On **204** (no
  pending), nothing is stored—the same signed token may be reused on later polls until a claim
  occurs; see `AuthAttemptPendingService` Javadoc and `docs/ENDPOINT.md`.
- **Respond endpoint**: One failed validation (invalid signature, wrong challenge, or missing device
  key) marks the auth attempt as **INVALID** immediately. There is no failure counter or N-attempts
  retry; this is intentional (strict security posture). Rate limiting on `POST /api/v1/auth-attempts/respond`
  is per `authAttemptId` (from request body), default 1 request per 5 minutes.
- The `EnrollmentController.resolveTenantId()` method resolves tenant context via
  `integrationRepository.findTenantIdByIntegrationId()` (scalar projection) — it does not load a
  full `Integration` entity for audit enrichment.
