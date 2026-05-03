# Plan: Administrators list — operational review & tenant display

**Status**

- `.cursor/plans/archived/2026-05`
- Completed
- Implemented
- Validated (functional tests satisfactory per maintainer)

## Overview

Close the **administrators list** experience end-to-end: expose **tenant context** on list/detail APIs without N+1 queries, align **Admin UI** columns and copy with **Global vs tenant** roles, keep human/docs and **Postman** consistent, match **Demo Device** tenant grouping to the same **platform** convention as the UI for global-scoped enrollments, and fix follow-on issues discovered during validation (repository query parsing, migration container bootstrap, auth API slice tests).

## Objectives

1. **API** — `AdminResponseDto` includes `tenantName` from the linked tenant where applicable; global administrators keep `tenantId` / `tenantName` as `null` in JSON (UI maps scope to “Platform”).
2. **Performance** — List/detail mapping loads `tenant` and `enrollment` in a bounded number of queries (dedicated `EzkeyAdminRepository` methods with `@EntityGraph` + explicit JPQL where Spring Data would otherwise mis-parse method names).
3. **Admin UI** — List and detail reflect role-aware tenant presentation; i18n keys aligned (`tenantPlatform`, etc.).
4. **Documentation & contracts** — `docs/ENDPOINT.md` and **Postman** (`EZ Key Admin Provisioning admin`) describe and test `tenantName`, lifecycle, and operational fields as appropriate.
5. **Demo Device** — Replace misleading “Unknown tenant” for global/bootstrap-style enrollments with **Platform** when `tenantId` is null and name is absent (parity with Admin UI convention).
6. **Hardening** — `ezkey-migration` / JPA repository bootstrap: no derived-query failure on `findAllForAdminProvisioningList`; auth-api `@WebMvcTest` imports provide `AuditChainHeartbeatGuardService` mock.

## Steps (executed)

1. Extend provisioning read path: map `tenantName` from `EzkeyAdmin.tenant` in `AdminProvisioningController.toAdminResponseDto`; service uses paginated repository methods that fetch associations for list paths.
2. Add `EzkeyAdminRepository.findAllForAdminProvisioningList` / `findByTenantTenantIdForAdminProvisioningList` with `@Query` + `@EntityGraph` (explicit JPQL required; `findAllFor*` alone is not a valid derived query).
3. Admin UI: administrators table and detail — columns and labels for global vs tenant scope; FR/EN strings.
4. Update `docs/ENDPOINT.md` for admin list/detail response fields.
5. Refresh Postman collection descriptions and tests for `AdminResponseDto` (including `tenantName`, `lifecycleStatus`, `operational` where applicable); clarify that bootstrap `AdminProvisioningResponseDto` does not include `tenantName`.
6. Demo Device: `EzkeyAppController.normalizeTenantName(tenantName, tenantId)` and bind view model.
7. Fix auth-api controller tests: `@MockitoBean AuditChainHeartbeatGuardService` where `GlobalExceptionHandler` is imported.
8. Optional housekeeping: local Windows JDK path in `scripts/build-local.cmd` for contributor consistency.

## Implemented scope (summary)

- **ezkey-core** — `EzkeyAdminRepository` provisioning list queries with JPQL + entity graph.
- **ezkey-admin-api** — `AdminResponseDto` / `AdminProvisioningService` / controller mapping; unit tests adjusted.
- **ezkey-admin-ui** — administrators list & detail UX and i18n for tenant/platform presentation.
- **ezkey-demo-device** — platform fallback label for null-tenant enrollments.
- **ezkey-auth-api** — WebMvc tests compatible with `GlobalExceptionHandler` constructor dependency.
- **docs** — `docs/ENDPOINT.md` updated as part of the change set.
- **postman** — `postman/collections/v2.1/EZ Key Admin Provisioning admin.postman_collection.json` updated.

## Relevant files (non-exhaustive)

- `ezkey-core/src/main/java/org/ezkey/integration/domain/repository/EzkeyAdminRepository.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/AdminResponseDto.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java`
- `ezkey-admin-api/src/test/java/org/ezkey/admin/service/AdminProvisioningServiceTest.java`
- `ezkey-admin-api/src/test/java/org/ezkey/admin/controller/AdminProvisioningControllerTest.java`
- `ezkey-admin-ui/src/pages/admins.tsx` (and related i18n under `src/locales/`)
- `ezkey-demo-device/src/main/java/org/ezkey/demo/device/controller/EzkeyAppController.java`
- `ezkey-auth-api/src/test/java/org/ezkey/auth/controller/AuthAttemptControllerTest.java`
- `ezkey-auth-api/src/test/java/org/ezkey/auth/controller/EnrollmentControllerTest.java`
- `docs/ENDPOINT.md`
- `postman/collections/v2.1/EZ Key Admin Provisioning admin.postman_collection.json`
- `scripts/build-local.cmd` (JDK 25 path for local builds)

## Notes

- OpenAPI artifacts under `specs/**` remain generated; refresh via `./scripts/update-specs.sh` when authorized against a running stack, then regenerate Admin UI Orval clients if the contract changed.
- Contract refresh was treated as part of the same initiative where applicable; Postman was updated in-repo to match list/detail behavior.

## Close-out

- Maintainer confirmed **functional tests satisfactory**.
- Plan archived under `.cursor/plans/archived/2026-05/`.
