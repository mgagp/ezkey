---
name: enrollment-expiry-create
overview: Add operator-controlled enrollment invitation expiry at creation time while moving Ezkey to a secure-by-default 7-day pending enrollment window. The plan touches Admin API/core, Admin UI, generated contract workflow, tests, and living product documentation.
status: completed
completedNote: Tested end-to-end; work validated against the plan’s validation checklist.
todos:
  - id: backend-contract
    content: Add optional create-time expiresAt to Admin API DTO/domain mapping and enforce future/default behavior in EnrollmentService.
    status: completed
  - id: contract-refresh
    content: Refresh generated OpenAPI and Admin UI client through the approved generated-spec workflow after explicit authorization.
    status: completed
  - id: admin-ui-dialog
    content: Add optional invitation expiry control to the Admin UI enrollment create dialog with EN/FR copy.
    status: completed
  - id: tests-validation
    content: Add focused backend/UI tests and run appropriate validation, including Playwright judgment for the create workflow.
    status: completed
  - id: living-docs
    content: Update endpoint, configuration, and product-docs references for the 7-day secure default and pending-only semantics.
    status: completed
isProject: false
---

# Enrollment Creation Expiry Plan

**Plan completion:** Completed — scope delivered and tested (backend, contract refresh, Admin UI, docs, and validation).

## Current Findings
- The current Admin API create DTO, [`ezkey-admin-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentCreateRequestDto.java`](ezkey-admin-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentCreateRequestDto.java), does not expose `expiresAt`.
- The current generated Admin UI OpenAPI snapshot, [`ezkey-admin-ui/openapi-spec.json`](ezkey-admin-ui/openapi-spec.json), confirms `EnrollmentCreateRequestDto` has only `integrationId`, `name`, `authAttemptChallengeRequired`, `contactEmail`, `contactPhoneNumber`, and `userIdentifier`.
- The backend already has an instance-wide pending expiration hook in [`ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`](ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java): `createdAt.plusDays(enrollmentProperties.getPendingExpirationDays())`, but the default is currently null/no expiration.
- Existing semantics in [`docs/ENDPOINT.md`](docs/ENDPOINT.md) are important: `expiresAt` is the pending invitation window for create/bind/verify, not a post-verification lifetime for MFA use.
- Comparable systems generally do not leave enrollment invitations open-ended: Duo documents 24-hour activation defaults for activation codes/links, configurable API `valid_secs`, and 30-day upper-bound style behavior for longer enrollment links. For Ezkey, 7 days is a pragmatic middle point: secure by default, still operationally realistic.

## Recommended Product Decision
- Treat pending enrollment expiry as a security baseline, not a nice-to-have UI detail.
- Default new pending enrollments to **7 days** through configuration.
- Allow the creating operator to override the invitation expiry per enrollment with an optional explicit date/time.
- Keep the field additive and optional in the API contract for compatibility. Omitted `expiresAt` means “use the configured default,” not “never expires.”
- Preserve “no expiry” only as an explicit deployment policy escape hatch via configuration, not as the product default.

## Implementation Plan

1. Update backend create contract and service behavior.
- Add optional `OffsetDateTime expiresAt` to [`EnrollmentCreateRequestDto.java`](ezkey-admin-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentCreateRequestDto.java) and the corresponding domain request in [`EnrollmentCreateRequest.java`](ezkey-core/src/main/java/org/ezkey/enrollment/domain/EnrollmentCreateRequest.java).
- Update [`EnrollmentAdminMapper.java`](ezkey-admin-api/src/main/java/org/ezkey/enrollment/mapper/EnrollmentAdminMapper.java) if MapStruct does not map the new field automatically.
- In [`EnrollmentService#create`](ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java), apply this order:
  - if request `expiresAt` is present, validate it is in the future and store it;
  - else use `ezkey.enrollment.pending-expiration-days`;
  - else leave null only if the deployment explicitly configures no default.
- Change the default in [`EnrollmentProperties.java`](ezkey-core/src/main/java/org/ezkey/config/EnrollmentProperties.java) from null to `7`, with Javadoc explaining the security posture.

2. Refresh contract artifacts through the approved generated-spec workflow.
- Do not hand-edit `specs/**` or dispatched OpenAPI snapshots.
- After backend implementation and validation, run the project’s generated spec workflow only with explicit authorization and a fresh local stack: `./scripts/update-specs.sh`.
- Regenerate the Admin UI client in [`ezkey-admin-ui`](ezkey-admin-ui) after the OpenAPI snapshot includes `expiresAt`.

3. Update Admin UI create dialog.
- Extend [`ezkey-admin-ui/src/pages/enrollments.tsx`](ezkey-admin-ui/src/pages/enrollments.tsx) with an optional invitation expiry input using the same `datetime-local` approach already used on [`ezkey-admin-ui/src/pages/enrollment-detail.tsx`](ezkey-admin-ui/src/pages/enrollment-detail.tsx).
- Default the UI to a 7-day local datetime for operator visibility, but allow clearing it so the backend configured default can apply. The copy should make clear that this controls the enrollment invitation window before the mobile device binds/verifies.
- Include the resulting ISO timestamp as `expiresAt` in `EnrollmentCreateRequestDto` when the operator chooses an explicit value.
- Update `en` and `fr` i18n under [`ezkey-admin-ui/src/locales`](ezkey-admin-ui/src/locales), plus demo presets only if useful and low-risk.

4. Update tests.
- Add/adjust backend unit tests in [`ezkey-core/src/test/java/org/ezkey/enrollment/service/EnrollmentServiceTest.java`](ezkey-core/src/test/java/org/ezkey/enrollment/service/EnrollmentServiceTest.java) for explicit future `expiresAt`, past `expiresAt` rejection, and default 7-day behavior.
- Add DTO/controller coverage where appropriate in [`ezkey-admin-api`](ezkey-admin-api), especially OpenAPI annotations/validation behavior.
- Add Admin UI tests around form validation/payload assembly if existing test structure supports it cheaply.
- Browser testing judgment: this changes a security-relevant create workflow, so existing Playwright coverage should be run after implementation; add one focused scenario only if the create dialog is not already covered.

5. Update living documentation.
- Update [`docs/ENDPOINT.md`](docs/ENDPOINT.md) to document `POST /api/v1/enrollments`, including `expiresAt`, default behavior, UTC/ISO timestamp format, and pending-only semantics.
- Update [`ezkey-core/CONFIGURATION.md`](ezkey-core/CONFIGURATION.md) and [`docs/configuration/README.md`](docs/configuration/README.md) so `ezkey.enrollment.pending-expiration-days` shows default `7` and its operational meaning.
- Add the workflow decision to [`product-docs/components/admin-api/functional-flows.md`](product-docs/components/admin-api/functional-flows.md) and [`product-docs/components/admin-ui/screens-and-wireflow.md`](product-docs/components/admin-ui/screens-and-wireflow.md), keeping the product docs alive as requested.

## Validation Plan
- For Java/backend changes: run the safe Maven baseline from the repo root via the Windows-local build entrypoint, then targeted tests if needed.
- For Admin UI changes: run TypeScript/build validation in [`ezkey-admin-ui`](ezkey-admin-ui), plus relevant form/unit tests if present.
- For workflow validation: clean-start stack, create an enrollment with default expiry, create one with explicit expiry, verify expired pending enrollments are rejected by bind/verify, and confirm the UI displays the intended operator copy.

## Prioritized Suggestions
- First priority: make 7-day pending expiry the default backend policy, because relying on UI behavior alone would leave API callers and future clients inconsistent.
- Second priority: expose per-enrollment `expiresAt` at creation, because operators need control for real onboarding windows.
- Third priority: document the distinction between pending invitation expiry and verified enrollment lifetime, because conflating those would create product confusion later.
- Fourth priority: consider a future separate policy for maximum allowed create-time expiry, but do not block this chunk on it unless the team wants stronger governance immediately.