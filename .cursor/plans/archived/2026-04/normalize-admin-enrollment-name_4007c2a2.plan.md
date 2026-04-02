---
name: normalize-admin-enrollment-name
overview: Standardize the admin-side enrollment foreign key naming to `enrollment_id` now, while the project is still development-only and Flyway migrations are already treated as resettable after consolidation. Update the consolidated schema, JPA/repository naming, audit payload keys, docs/Postman references, and targeted tests together so the concept stays uniform end to end.
todos:
  - id: update-flyway-baseline
    content: Rename `ezkey_admin.mfa_enrollment_id` to `enrollment_id` in the consolidated schema baseline and duplicated schema docs
    status: completed
  - id: rename-jpa-and-repositories
    content: Rename the admin entity/repository mapping from `mfaEnrollment` / `mfa_enrollment_id` to neutral enrollment naming
    status: completed
  - id: propagate-service-usage
    content: Update services, controllers, exceptions, and helper methods that reference the old admin MFA-specific naming
    status: completed
  - id: align-audit-and-docs
    content: Rename audit JSON keys, Postman variables, and documentation references where they represent the same enrollment concept
    status: completed
  - id: verify-with-format-and-tests
    content: Run Spotless first, then targeted validation and clean-start schema verification
    status: completed
isProject: false
---

# Normalize Enrollment ID Naming

## Implementation Status

Implemented and validated in April 2026.

The rename to `enrollment_id` was applied across the consolidated Flyway baseline, Java entity/repository/service usage, audit payloads, docs, Postman, targeted tests, and supporting admin UI recovery wording.

Validation completed with Spotless, targeted Maven tests, and a clean-start schema reset confirming that `ezkey_admin` now exposes `enrollment_id`.

## Recommendation

Adopt the rename now to `enrollment_id`, and treat it as a product-level consistency correction across Ezkey.

The repo shows that `mfa_enrollment_id` was historically intentional, not accidental, in the admin table:

- [ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql](ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql)
  - `mfa_enrollment_id INT REFERENCES ezkey_enrollment(enrollment_id),`
  - `COMMENT ON COLUMN ezkey_admin.mfa_enrollment_id IS 'Links to Ezkey enrollment for passwordless authentication...'`
- [ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java](ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java)
  - `@JoinColumn(name = "mfa_enrollment_id")`
  - `private Enrollment mfaEnrollment;`
- [ezkey-admin-api/src/main/java/org/ezkey/admin/audit/RecoveryAuditDetails.java](ezkey-admin-api/src/main/java/org/ezkey/admin/audit/RecoveryAuditDetails.java)
  - `.custom("mfa_enrollment_id", mfaEnrollmentId);`

Even so, the product model is now clear: Ezkey has a single enrollment concept and a single enrollment identifier, regardless of whether the enrollment belongs to an end user or an administrator. Because there is no production baggage and the project already accepts schema resets after migration consolidation, this is the right moment to remove the obsolete admin-specific vocabulary.

## Conceptual Basis

The governing rule for this work is:

- Ezkey has one enrollment model.
- `ezkey_enrollment(enrollment_id)` is the only enrollment identifier in the platform.
- Administrator authentication uses the same Ezkey enrollment model as every other Ezkey flow.
- Any admin-specific naming such as `mfa_enrollment_id` or `mfaEnrollment` is legacy wording, not a distinct domain concept.

This means the rename is not merely cosmetic. It formalizes the internal truth of the platform and aligns schema, Java code, audit payloads, tooling, and documentation with the same concept vocabulary.

## Flyway Strategy

Use the existing consolidated schema definition rather than adding a new Flyway migration.

Why this fits the current project stage:

- [ezkey-migration/README.md](ezkey-migration/README.md) explicitly states consolidation happened because there are no production deployments and developers reset the database after consolidation.
- The requested change is structural cleanup of the desired baseline schema, not historical deployment tracking.

Guardrails:

- Update the original admin table definition in [ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql](ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql) so the canonical schema is `enrollment_id` from the start.
- Do not edit generated OpenAPI specs under `specs/`; if API annotations or DTO names need changes, let future spec regeneration reflect that.
- Expect clean-start / schema reset validation after the change.

## Planned Change Areas

### 1. Database baseline

Update the admin table column name and related comments in:

- [ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql](ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql)
- Any schema or SQL documentation files that duplicate the table definition, especially:
  - [docs/features/SECURITY_MULTI_TENANT.md](docs/features/SECURITY_MULTI_TENANT.md)
  - [ezkey-migration/README.md](ezkey-migration/README.md)

### 2. JPA and repository layer

Rename the admin-side enrollment mapping from `mfaEnrollment` to `enrollment`, and align all repository methods/queries so they reflect the single Ezkey enrollment concept:

- [ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java](ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java)
- [ezkey-core/src/main/java/org/ezkey/integration/domain/repository/EzkeyAdminRepository.java](ezkey-core/src/main/java/org/ezkey/integration/domain/repository/EzkeyAdminRepository.java)

This includes:

- `@JoinColumn(name = "enrollment_id")`
- field/accessor rename from `mfaEnrollment` to `enrollment`
- repository method/query rename so code no longer encodes obsolete MFA-specific wording
- parameter/local variable cleanup where names like `mfaEnrollmentId` still refer to the normal Ezkey `enrollmentId`

### 3. Admin/auth service usage

Propagate the rename through services, controllers, exception messages, and helper methods that currently refer to admin MFA enrollment specifically:

- [ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java)
- [ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java)
- [ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java)
- [ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminRecoveryService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminTokenValidationService.java)
- [ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java)
- [ezkey-auth-api/src/main/java/org/ezkey/auth/controller/EnrollmentController.java](ezkey-auth-api/src/main/java/org/ezkey/auth/controller/EnrollmentController.java)
- [ezkey-auth-api/src/main/java/org/ezkey/auth/controller/AuthAttemptController.java](ezkey-auth-api/src/main/java/org/ezkey/auth/controller/AuthAttemptController.java)
- [ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentBindService.java](ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentBindService.java)

### 4. Audit payload and operator-facing artifacts

For consistency, rename stored JSON keys and helper variable names from `mfa_enrollment_id` / `mfaEnrollmentId` to `enrollment_id` / `enrollmentId` wherever they refer to the same Ezkey enrollment concept:

- [ezkey-admin-api/src/main/java/org/ezkey/admin/audit/RecoveryAuditDetails.java](ezkey-admin-api/src/main/java/org/ezkey/admin/audit/RecoveryAuditDetails.java)
- [postman/collections/v2.1/EZ Key Authentication Login admin.postman_collection.json](postman/collections/v2.1/EZ Key Authentication Login admin.postman_collection.json)
- Relevant markdown docs under `docs/` that still describe the admin link as a separate kind of enrollment when they are really talking about the same Ezkey enrollment model

Because there is no production history to preserve, this plan assumes we prefer uniformity over backward compatibility of stored audit JSON keys.

### 5. Tests and validation

Update focused tests and SQL helpers that assert or query the old name:

- [ezkey-admin-api/src/test/java/org/ezkey/admin/audit/RecoveryAuditDetailsTest.java](ezkey-admin-api/src/test/java/org/ezkey/admin/audit/RecoveryAuditDetailsTest.java)
- [ezkey-admin-api/src/test/java/org/ezkey/admin/service/EnrollmentRevocationServiceTest.java](ezkey-admin-api/src/test/java/org/ezkey/admin/service/EnrollmentRevocationServiceTest.java)
- [ezkey-admin-api/src/test/java/org/ezkey/admin/service/AdminTokenValidationServiceTest.java](ezkey-admin-api/src/test/java/org/ezkey/admin/service/AdminTokenValidationServiceTest.java)
- [ezkey-admin-api/src/test/java/org/ezkey/admin/service/AdminAuthServiceAuditContextTest.java](ezkey-admin-api/src/test/java/org/ezkey/admin/service/AdminAuthServiceAuditContextTest.java)
- [ezkey-auth-api/src/test/java/org/ezkey/auth/controller/EnrollmentControllerVerifyAuditTest.java](ezkey-auth-api/src/test/java/org/ezkey/auth/controller/EnrollmentControllerVerifyAuditTest.java)
- [ezkey-tests/src/test/java/org/ezkey/tests/util/DatabaseHelper.java](ezkey-tests/src/test/java/org/ezkey/tests/util/DatabaseHelper.java)

Validation sequence after edits:

1. Run `mvn spotless:apply` from the repo root.
2. Run targeted Maven tests for touched modules, excluding `ezkey-tests` unless needed.
3. Perform a clean schema start/reset path so the consolidated Flyway baseline is exercised with the renamed column.
4. Check recently edited files for lint/IDE diagnostics.

## Risks To Watch

- Neutral renaming to `enrollment` in `EzkeyAdmin` must stay readable in implementation, but readability should be solved with surrounding method/class naming, not by keeping a second conceptual enrollment name.
- Audit JSON key changes are safe only because the current assumption is no production consumers/history to preserve.
- Generated specs must not be edited manually; only source code/docs should change.
- Some docs intentionally discuss “MFA” as the authentication experience; keep that user-facing security language where appropriate, but remove it from places where it incorrectly renames the underlying enrollment concept.

