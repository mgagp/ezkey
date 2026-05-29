# Admin UI — Spec-Test Traceability

## Intent

This document links Admin UI features and workflows to the specifications that define their expected behavior and to the tests that verify that behavior.

It is a **living document**. Update it in the same change set as any behavior, contract, or test change. Promote items to the global matrix when they materially affect product-level coverage: [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).

## Traceability Matrix

| Feature | Spec | Acceptance criterion | Test suite | Status |
| --- | --- | --- | --- | --- |
| [`F-admin-ui-shell`](../../global/features-and-phases.md#f-admin-ui-shell) | [`functional-flows.md#w-ui-login-passwordless`](functional-flows.md#w-ui-login-passwordless), [`api-and-boundary-mappings.md#m-admin-auth`](api-and-boundary-mappings.md#m-admin-auth) | Challenge code zero-padded; abort handling safe; session populated once. | Playwright `login.*` suite against Demo Device. | `implemented` |
| [`F-admin-ui-shell`](../../global/features-and-phases.md#f-admin-ui-shell) | [`exception-and-error-model.md#auth-rejected`](exception-and-error-model.md#auth-rejected) | Rejected login shows translated `errors.authentication.auth-rejected`. | Playwright elective rejection scenario. | `implemented` |
| [`F-admin-ui-workflows`](../../global/features-and-phases.md#f-admin-ui-workflows) | [`functional-flows.md#w-ui-integration-create`](functional-flows.md#w-ui-integration-create), [`api-and-boundary-mappings.md#m-integrations`](api-and-boundary-mappings.md#m-integrations) | Create dialog is non-dismissible; list refreshes after success; reason enforced on retire/delete. | Playwright post-login workflow scenario; Admin API integration tests. | `in-progress` |
| [`F-admin-lifecycle`](../../global/features-and-phases.md#f-admin-lifecycle) | [`screens-and-wireflow.md`](screens-and-wireflow.md) (Admins detail) | Global Admin and Tenant Admin see role-appropriate actions; deactivate/activate work. | Playwright navigation smoke; Admin API tests. | `implemented` |
| [`F-tenant-lifecycle`](../../global/features-and-phases.md#f-tenant-lifecycle) | [`screens-and-wireflow.md`](screens-and-wireflow.md) (Tenants list/detail) | Deactivate/activate available only to Global Admin; system tenant protected. | Playwright navigation smoke; Admin API tests. | `implemented` |
| [`F-rfc9457-errors`](../../global/features-and-phases.md#f-rfc9457-errors) | [`exception-and-error-model.md`](exception-and-error-model.md) | Errors branch on `type`; `parameters` interpolation works; missing locale falls back cleanly. | Unit tests in `src/lib/api-error-i18n.*.test.ts` (when present); Playwright error surface coverage. | `in-progress` |
| [`F-api-key-lifecycle`](../../global/features-and-phases.md#f-api-key-lifecycle) | [`screens-and-wireflow.md`](screens-and-wireflow.md) (API Keys) | Secret shown once dialog is non-dismissible; revoke requires reason. | Playwright elective where applicable. | `in-progress` |

## Coverage Summary

- **Features in scope.** 6.
- **Implemented coverage.** 3 features covered by existing Playwright scenarios and Admin API tests.
- **In-progress coverage.** 3 features partially covered; additional Playwright scenarios or contract tests are planned.

## Open Gaps

- `F-rfc9457-errors` — the locale inventory does not yet include every problem `type`. Owner: Admin UI maintainers. Next: complete the `errors` namespace entries in FR and EN.
- `F-api-key-lifecycle` — no Playwright scenario covers the "secret shown once then cannot be retrieved" invariant. Owner: Admin UI maintainers. Next: add an elective scenario driven through Admin API seed data.
- `F-admin-ui-workflows` — retire integration flow lacks a dedicated Playwright scenario for the guarded Delete action after retirement. Owner: Admin UI maintainers. Next: add a scenario that retires, then observes Delete gated by remaining enrollments.

## Update Cadence

- Update this matrix when:
  - a workflow, screen, mapping, or error rule changes in this component,
  - a test is added, removed, or restructured,
  - a feature's status changes in the global catalog.
- Review the matrix at milestone boundaries or bounded delivery checkpoints.

## Related Documents

- [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).
- [`../../global/features-and-phases.md`](../../global/features-and-phases.md).
- Module: [`../../../ezkey-admin-ui/README.md`](../../../ezkey-admin-ui/README.md) (Browser Tests section).
