---
name: integration retirement coherence
overview: Align bulk revocation with single-enrollment revocation for in-flight enrollments, and replace risky integration deletion with an enum-based integration lifecycle that supports retirement without losing history.
note: Keep the Postman collections aligned with the controllers as part of the closure criteria for this package, including lifecycle filters, retirement flow, and revised delete/revoke semantics.
implementationStatus: Fully implemented and validated.
todos:
  - id: expand-bulk-revoke
    content: Broaden bulk revoke candidate selection to include active CREATED and BOUND enrollments alongside VERIFIED.
    status: completed
  - id: add-retired-lifecycle
    content: Introduce an enum-based integration lifecycle with a RETIRED state, persistence migration, filtering, and service rules that preserve history instead of deleting operationally used integrations.
    status: completed
  - id: align-ui-and-docs
    content: Update danger-zone copy, endpoint documentation, and lifecycle vocabulary so operators understand retire vs deactivate vs revoke vs delete.
    status: completed
  - id: cover-qa-scenarios
    content: Add unit and functional tests for in-flight bulk revocation, retired-by-default filtering, and the revised integration retirement flow.
    status: completed
isProject: false
---

# Integration Retirement Coherence

## Status

This plan is fully implemented and validated.

- Backend implementation is complete.
- UI and documentation alignment are complete.
- Postman collection alignment is complete.
- Automated functional coverage is in place for the key lifecycle scenarios.
- Additional interactive console scenarios were manually validated after a clean start.

## Goal

Make integration retirement conceptually consistent for Tenant Admins:

- `revoke-all` must align with the stronger semantics of single-enrollment revoke: it must cover revocable enrollments that are already deactivated as well as in-flight enrollments (`CREATED`, `BOUND`) so operators never need to reactivate first in order to revoke.
- integrations with historical significance should be retired, not hard-deleted, so audit-relevant history remains intact while the UI hides retired records by default.
- integration lifecycle should be modeled explicitly with an `enum`, not by multiplying boolean flags.

## Current behavior to change

The bulk revoke path currently only targets `VERIFIED` + `active=true`:

```397:399:ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java
List<Enrollment> activeEnrollments =
    enrollmentRepository.findByIntegrationIdAndStatusAndActive(
        integrationId, EnrollmentStatus.VERIFIED, true);
```

Single-enrollment revoke is broader and already allows revoking non-final rows, which is the semantic anchor for this change:

```162:169:ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java
if (EnrollmentStatus.REVOKED.equals(enrollment.getStatus())) {
  logger.debug("Enrollment {} is already revoked — no-op", enrollmentId);
  return;
}

enrollment.setStatus(EnrollmentStatus.REVOKED);
enrollment.setActive(false);
```

This creates an observed inconsistency:

- bulk revoke excludes already deactivated `VERIFIED` enrollments
- bulk revoke excludes in-flight `CREATED` and `BOUND` enrollments
- single-enrollment revoke does not require the weaker state transition to be undone first

Operationally, this is backwards: `deactivate` is the weaker, reversible control; `revoke` is the stronger, permanent control and should dominate it.

## Proposed implementation

- Update bulk revoke in [ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java) so candidate selection is driven by revocability, not by the current weaker operational flag. At minimum, include:
  - `VERIFIED` enrollments whether currently active or deactivated
  - in-flight `CREATED` and `BOUND` enrollments
  - continued exclusion of already `REVOKED` rows
  Preserve the self-guard behavior.
- Adjust repository access in [ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java](ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java) only as needed for the simplest implementation path. Prefer a clear query/filter strategy over clever abstraction.
- Replace the current binary integration lifecycle in [ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Integration.java](ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Integration.java) with an explicit enum, likely something in the spirit of `ACTIVE`, `INACTIVE`, `RETIRED`. This requires a DB migration, coordinated entity/repository/service/DTO changes, and careful replacement of boolean `active` assumptions.
- Update integration search and default visibility in [ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java](ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java), [ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java), and [ezkey-admin-ui/src/pages/integrations.tsx](ezkey-admin-ui/src/pages/integrations.tsx) so the default query excludes `RETIRED`, while operators can explicitly request `RETIRED` or all lifecycle states through normal search/filtering rather than a separate retirement API surface.
- Revise the integration detail danger zone in [ezkey-admin-ui/src/pages/integration-detail.tsx](ezkey-admin-ui/src/pages/integration-detail.tsx), [ezkey-admin-ui/src/locales/en/integrations.json](ezkey-admin-ui/src/locales/en/integrations.json), [ezkey-admin-ui/src/locales/fr/integrations.json](ezkey-admin-ui/src/locales/fr/integrations.json), and [docs/ENDPOINT.md](docs/ENDPOINT.md) so the operator model becomes: `deactivate/reactivate` for reversible availability, `revoke` for enrollment credential invalidation, `retire` for integrations that must disappear from day-to-day use without losing history, and `delete` only for truly disposable records if that path remains at all.

## Lifecycle modeling decision

- Prefer a single integration lifecycle enum over multiple booleans because this is fundamentally a lifecycle problem, not a set of independent flags.
- Treat enum introduction as a conceptual cleanup as well as a feature change: replace boolean-driven wording, assumptions, and filters where they currently leak lifecycle meaning.
- Keep the model narrow unless new states are genuinely needed now; avoid speculative statuses beyond what the current product semantics require.

## Transition strategy for `active`

- During implementation, it is acceptable to retain `active` temporarily as a derived compatibility field or filter if that helps separate the migration into cleaner steps.
- This temporary compatibility does **not** imply a long-term API contract. The target model remains `lifecycleStatus` as the single source of truth.
- Before final closure of this work package, remove `active` from the integration API/UI surface unless a concrete residual product value is demonstrated.
- That cleanup includes response DTOs, search parameters, UI fallbacks, generated client types, and documentation that still treat `active` as a first-class lifecycle attribute.

## Operational posture

- A mistaken pre-created integration with no meaningful use is the strongest candidate for hard delete, but it is not the dominant risk-bearing case.
- Once an integration has enrollments, attempted onboarding, or authentication history, hard deletion becomes operationally ambiguous and normatively riskier because it erases part of the security story.
- A `retired` lifecycle is therefore the safer default posture: preserve history, stop operational use, and remove visual clutter through default filters instead of destructive deletion.
- This is also directionally consistent with comparable admin products, which commonly distinguish reversible deactivation from stronger retirement/removal workflows and gate destructive deletion more tightly.

## Validation focus

- Add unit coverage in [ezkey-admin-api/src/test/java/org/ezkey/admin/service/EnrollmentRevocationServiceTest.java](ezkey-admin-api/src/test/java/org/ezkey/admin/service/EnrollmentRevocationServiceTest.java) for:
  - inactive `VERIFIED` enrollments being bulk-revocable without prior reactivation
  - `CREATED` and `BOUND` inclusion in bulk revoke
  - no requirement to pass through a weaker lifecycle step before a stronger one
- Add functional/security coverage in [ezkey-tests/src/test/java/org/ezkey/tests/security/enrollment/EnrollmentRevocationSecurityTest.java](ezkey-tests/src/test/java/org/ezkey/tests/security/enrollment/EnrollmentRevocationSecurityTest.java) for the QA scenarios:
  - deactivate-all followed by revoke-all without intermediate reactivate-all
  - create integration, leave enrollment in `CREATED` or `BOUND`, run bulk revoke, verify onboarding can no longer complete
- Add focused tests for the revised enum lifecycle and search defaults: `ACTIVE`/`INACTIVE`/`RETIRED` visibility, explicit retired filtering, and the retirement flow after enrollments have been revoked.

## Vocabulary alignment

- Use `revoke` for enrollment credentials and enrollment lifecycle finalization.
- Use `deactivate` and `reactivate` for reversible operational availability.
- Use `retire` for integration end-of-life where history must remain visible to the system but mostly hidden from operators.
- Reserve `delete` for cases with no meaningful operational or audit history, and avoid using it as the normal retirement mechanism.
- Use lifecycle wording consistently across domain model, API docs, UI labels, and filters so operators do not have to mentally translate between boolean status, destructive actions, and historical states.
- Preserve the consequence hierarchy in behavior and wording: a stronger action must not require operators to undo a weaker state first.

## Explicit non-goal for this package

Do not silently weaken historical-data protections in the name of convenience. If a hard-delete path remains for integrations or enrollments, it should stay clearly exceptional and narrower than the normal retirement posture.

## Validated interactive console scenarios

These scenarios were manually validated after a clean start with an empty database plus bootstrap only. They are kept here as a lightweight replay guide for the Kiwi QA team.

### Suggested presets

- Tenant preset: `Garage du coin`
- Tenant preset: `La Ferme des Licornes`
- Integration preset: `Administration`
- Integration preset: `Internal Tools`
- Integration preset: `Unicorn Ride Booking`
- Integration preset: `Unicorn Breeding & Care System`
- Enrollment preset: `Marie Dupont — iPhone 15`
- Enrollment preset: `Jean Martin — Android`
- Enrollment preset: `Élodie Martin — iPhone 15`
- Reason badge: `Révoqué pour raison de sécurité`
- Reason badge: `Fin d'accès / départ`
- Reason badge: `Mise à jour politique de sécurité`
- Reason badge: `Conformité et audit`

### Scenario 1: Baseline active integration behavior

- Create a tenant using `Garage du coin`.
- Create an integration using `Administration` or `Internal Tools`.
- Verify the integration appears as `ACTIVE`.
- Verify it is visible in the default integrations list.
- Verify the detail page still offers `Create API Key` and `New Enrollment`.

### Scenario 2: Revoke-all on a CREATED enrollment

- In the integration, create an enrollment using `Marie Dupont — iPhone 15`.
- Stop before onboarding continues beyond the initial created state.
- Run `Revoke all` with `Révoqué pour raison de sécurité`.
- Verify the enrollment becomes `REVOKED`.
- Verify onboarding can no longer continue for that enrollment.

### Scenario 3: Deactivate-all then revoke-all

- Create two enrollments, for example `Marie Dupont — iPhone 15` and `Jean Martin — Android`.
- Run `Deactivate all` with `Mise à jour politique de sécurité`.
- Without reactivating, run `Revoke all` with `Révoqué pour raison de sécurité`.
- Verify the action succeeds directly.
- Verify the enrollments are revoked without any intermediate `reactivate-all`.

### Scenario 4: Retire an integration with history

- Use an integration that already has enrollments or attempted onboarding history.
- Run `Retire integration` with `Fin d'accès / départ` or `Conformité et audit`.
- Verify revocable enrollments are revoked as part of the operation.
- Verify the integration detail now shows `RETIRED`.
- Verify the integration disappears from the default integrations list.
- Verify it reappears when filtering for `Retired` or `All`.
- Verify the retired detail page no longer offers `Create API Key` or `New Enrollment`.

### Scenario 5: Delete guard and exceptional delete path

- Create a disposable integration, ideally without enrollments, such as `Unicorn Ride Booking`.
- Attempt `Delete` directly.
- Verify deletion is refused before retirement.
- Run `Retire integration`.
- Run `Delete`.
- Verify deletion succeeds only after retirement and only for the now-retired disposable record.

### Scenario 6: Alternate tenant replay

- Create `La Ferme des Licornes`.
- Add `Unicorn Breeding & Care System`.
- Add an enrollment such as `Élodie Martin — iPhone 15`.
- Retire the integration.
- Verify retired-by-default filtering and retired detail behavior remain consistent in a second tenant context.
