## Plan: Mobile Installation Model Refactor

**Status**
- Completed on 2026-05-01.

Promote the Ezkey mobile installation trust boundary from a flattened set of enrollment fields into an explicit first-class local object named `Installation`. The refactor was carried through the model, hydration logic, storage orchestration, grouping utilities, key screens, tests, and reference documentation. The resulting shape is more specification-driven: each enrollment now composes an `installation` object with canonical identity, routing, and display metadata derived from the normalized Auth API URL and optional public instance-info data.

## Final Decisions

- Keep `Installation` as the canonical internal name because it already matches the dominant mobile documentation vocabulary while still representing the Ezkey site / trust zone.
- Treat `installation.id` as the canonical trust-zone identity derived from normalized `authUrl`.
- Move Auth API routing from root enrollment metadata to `installation.authUrl`.
- Keep `Installation` nested inside `StoredEnrollment` instead of introducing a separate persisted installation collection in this iteration.
- Preserve lightweight legacy hydration tolerance in `installationMetadata.ts` so older local dev data with flattened fields can still be normalized into the new shape.
- Keep host-hint display logic derived at view time rather than persisting a UI-only flag.

## Implemented Scope

1. Added a first-class `Installation` type in `ezkey_mobile/app/services/api/types.ts` and changed `EnrollmentSummary` to compose `installation?: Installation` instead of flattened `installation*` fields.
2. Refactored `ezkey_mobile/app/utils/installationMetadata.ts` to build and hydrate explicit installation objects, while still accepting legacy flattened storage payloads during normalization.
3. Updated `ezkey_mobile/app/hooks/useEnrollments.ts` so installation refresh is deduplicated and propagated by `installation.id` / `installation.authUrl`.
4. Refactored `ezkey_mobile/app/utils/tenantGrouping.ts` to return installation-first groups carrying `installation: Installation` explicitly.
5. Updated the main consumers in Home, Enrollment Detail, Pending Auth, Danger Zone, and Enrollment Wizard to read installation data from the nested object.
6. Updated focused tests to cover first-class installation construction, legacy hydration, normalized grouping, Danger Zone rendering, and storage persistence / rehydration.
7. Updated `MOBILE_DATA_MODEL.md` and `MOBILE_API_MAPPINGS.md` to document the installation association pipeline and the intentional mapping from enrollment context to installation.

## Key Files

- `ezkey_mobile/app/services/api/types.ts`
- `ezkey_mobile/app/utils/installationMetadata.ts`
- `ezkey_mobile/app/hooks/useEnrollments.ts`
- `ezkey_mobile/app/utils/tenantGrouping.ts`
- `ezkey_mobile/app/screens/Home/HomeScreen.tsx`
- `ezkey_mobile/app/screens/EnrollmentDetail/EnrollmentDetailScreen.tsx`
- `ezkey_mobile/app/screens/PendingAuth/PendingAuthScreen.tsx`
- `ezkey_mobile/app/screens/DangerZone/DangerZoneScreen.tsx`
- `ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx`
- `ezkey_mobile/app/utils/__tests__/installationMetadata.test.ts`
- `ezkey_mobile/app/utils/__tests__/tenantGrouping.test.ts`
- `ezkey_mobile/app/services/storage/__tests__/enrollmentStorage.test.ts`
- `ezkey_mobile/__tests__/DangerZoneScreen.test.tsx`
- `ezkey_mobile/docs/MOBILE_DATA_MODEL.md`
- `ezkey_mobile/docs/MOBILE_API_MAPPINGS.md`

## Stabilization Notes

- Legacy flattened installation fields are still tolerated inside `hydrateInstallationMetadata(...)` so local development caches do not hard-fail while the new shape settles.
- The runtime path no longer depends on root-level `installation*` fields; those legacy fields remain only in the compatibility layer and in tests that explicitly verify migration-style hydration.
- No external migration or release compatibility work was required because the mobile app is still pre-release and local-only.

## Validation

1. Focused Jest validation passed:
   - `app/utils/__tests__/installationMetadata.test.ts`
   - `app/utils/__tests__/tenantGrouping.test.ts`
   - `app/services/storage/__tests__/enrollmentStorage.test.ts`
   - `__tests__/DangerZoneScreen.test.tsx`
2. Full mobile Jest suite passed: 13 test suites, 59 tests.
3. File-scoped editor diagnostics on the changed runtime files were clean after refactor.
4. `corepack yarn typecheck` passes after updating `tsconfig.json` to extend the current exported path of `@react-native/typescript-config`.
5. `corepack yarn eslint ...` on the changed files passes after the same TypeScript-config resolution fix and the final regression cleanup.

## Deviations From Initial Plan

- A separate persisted installation collection was not introduced because the stronger immediate win came from explicit composition plus canonical hydration, without adding a second source-of-truth surface.
- The storage/orchestration seam was covered through installation metadata and grouping tests plus full-suite Jest validation, rather than by adding a dedicated hook-level test harness in this iteration.
- The global TypeScript / ESLint cleanup ended up being part of the implementation close-out because the installation refactor exposed an outdated `tsconfig` extends path that had to be updated to restore clean validation.

## Completion

The plan is completed. The mobile app now treats installation as a first-class internal concept in the main runtime path, the reference documentation describes the enrollment-to-installation association intentionally, and the implemented refactor has been validated with focused and full Jest coverage.