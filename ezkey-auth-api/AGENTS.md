# Ezkey Auth API — Agent Notes

This file is intended for coding agents working in `ezkey-auth-api/`.

## Non-negotiables

- All new/updated project content must be **in English**.
- Do not introduce `@Autowired`. Use constructor injection.
- Auth-API is consumed by **mobile devices** (React Native). Never break the
  `EnrollmentBindResponseDto` or `AuthAttemptPendingResponseDto` contracts without coordinating
  with mobile clients.

## Module responsibilities

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/enrollments/bind` | Device claims an enrollment slot (read-once guarantee) |
| `POST /api/v1/enrollments/verify` | Device completes enrollment with crypto keys |
| `POST /api/v1/auth-attempts/pending` | Device polls for a pending auth request |
| `POST /api/v1/auth-attempts/respond` | Device responds (accept/reject) to auth request |

## Running and testing

```bash
# Run module
mvn spring-boot:run -pl ezkey-auth-api

# Unit tests
mvn test -pl ezkey-auth-api

# Build
mvn -DskipTests package -pl ezkey-auth-api
```

---

## Critical pattern: read-only Integration loading in the bind flow

### Why this matters in auth-api

`EnrollmentController` and `EnrollmentBindService` (in `ezkey-core`) load the `Integration` entity
to resolve i18n display names and tenant info for the bind response. The `Integration.i18n`
association is mapped with `CascadeType.ALL + orphanRemoval = true` in the shared core.

Under concurrent load — multiple devices binding enrollments that all reference the same integration
(especially the system integration `id=1` used for all admin MFA) — Hibernate can raise:

```
HibernateException: Found shared references to a collection:
    org.ezkey.integration.domain.entity.Integration.i18n
```

This was observed on 2026-02-20 during `MultiTenantGlobalAdminTest` parallel setup.

### The rule

`EnrollmentBindService.loadIntegration()` **must** use
`IntegrationRepository.findByIdWithI18nAndTenant()` (read-only, JOIN FETCH) instead of the
standard `findById()`. This method applies `@QueryHints(org.hibernate.readOnly=true)` so Hibernate
does not track the `i18n` collection for dirty-checking or cascades.

**Never revert this to `findById()`** without understanding the concurrency implication.

See `ezkey-core/AGENTS.md` §"CascadeType.ALL + orphanRemoval shared-reference hazard" for the
full design pattern documentation and the general rule applicable to all future code.

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
- **Respond endpoint**: One failed validation (invalid signature, wrong challenge, or missing device
  key) marks the auth attempt as **INVALID** immediately. There is no failure counter or N-attempts
  retry; this is intentional (strict security posture). Rate limiting on `POST /api/v1/auth-attempts/respond`
  is per `authAttemptId` (from request body), default 1 request per 5 minutes.
- The `EnrollmentController.resolveTenantId()` method navigates `Enrollment → Integration → Tenant`
  chain for audit context. It uses standard `findById()` (read-only chain, no i18n access) —
  this is safe because it does not access any cascade-tracked collection.
