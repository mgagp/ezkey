# Admin API — Spec-Test Traceability

## Intent

This document links Admin API features and flows to the specifications that define their expected behavior and to the tests that verify that behavior. It is a **living document** that must be updated in the same change set as any contract, behavior, or test change.

Promote items to the global matrix when they materially affect product-level coverage: [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).

## Traceability Matrix

| Feature | Spec | Acceptance criterion | Test suite | Status |
| --- | --- | --- | --- | --- |
| [`F-admin-api-core`](../../global/features-and-phases.md#f-admin-api-core) | [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md), [`functional-flows.md#w-api-integration-create`](functional-flows.md#w-api-integration-create) | Integrations CRUD endpoints behave per contract; role scoping enforced. | `mvn test -pl 'ezkey-admin-api,!ezkey-tests'`, Postman collections under [`../../../postman/collections/`](../../../postman/collections/). | `implemented` |
| [`F-admin-lifecycle`](../../global/features-and-phases.md#f-admin-lifecycle) | [`data-model-and-persistence.md#admin`](data-model-and-persistence.md#admin), [`../../../docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md) | Admin identity lifecycle independent from MFA; minimum-admin guard; self-deactivate blocked. | Admin API tests. | `implemented` |
| [`F-tenant-lifecycle`](../../global/features-and-phases.md#f-tenant-lifecycle) | [`data-model-and-persistence.md#tenant`](data-model-and-persistence.md#tenant), [`../../global/lifecycle-model.md`](../../global/lifecycle-model.md) | Deactivation blocks downstream via eligibility chain; system tenant protected. | Admin API tests. | `implemented` |
| [`F-rfc9457-errors`](../../global/features-and-phases.md#f-rfc9457-errors) | [ADR-0003](../../global/architecture-decisions.md#adr-0003-rfc9457-as-external-error-contract), [`exception-and-error-model.md`](exception-and-error-model.md) | 4xx/5xx responses expose stable `type`, `title`, `status`; `parameters` extension supported where applicable. | Admin API contract tests; Admin UI translation tests. | `in-progress` |
| [`F-rate-limiting`](../../global/features-and-phases.md#f-rate-limiting) | [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md) (rate limits) | Limits enforced; 429 with `Retry-After`; per-resource vs per-IP fallback documented. | Admin API rate-limit tests. | `implemented` |
| [`F-encryption-key-rotation`](../../global/features-and-phases.md#f-encryption-key-rotation) | [`functional-flows.md#w-api-encryption-key-rotation`](functional-flows.md#w-api-encryption-key-rotation), [`../../../docs/REENCRYPTION_OPERATIONS.md`](../../../docs/REENCRYPTION_OPERATIONS.md) | Rotation introduces pending key; batches idempotent; drained verification guards decommissioning. | Admin API tests; re-encryption flow tests. | `in-progress` |
| [`F-audit-chain`](../../global/features-and-phases.md#f-audit-chain) | [`../../../docs/AUDIT_LOG_INTEGRITY.md`](../../../docs/AUDIT_LOG_INTEGRITY.md) (when present), [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md) (audit endpoints) | Chain integrity verifiable; archive confirmation workflow functions; incidents declarable. | Admin API tests. | `in-progress` |
| [`F-api-key-lifecycle`](../../global/features-and-phases.md#f-api-key-lifecycle) | [`data-model-and-persistence.md#api-key`](data-model-and-persistence.md#api-key), [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md) (API keys) | Create returns secret once; revoke permanent; no reactivation; optional IP allowlist enforced. | Admin API tests. | `in-progress` |

## Coverage Summary

- **Features in scope.** 8.
- **Implemented coverage.** 4.
- **In-progress coverage.** 4.

## Open Gaps

- `F-rfc9457-errors` — legacy error responses in the encryption key rotate path are not yet aligned with RFC 9457. Owner: Admin API maintainers. Next: align those error paths and promote the change in the global matrix.
- `F-audit-chain` — the heartbeat supervision behavior (503 contract across Auth API, Integration API, Admin API) needs a dedicated integration test. Owner: Admin API maintainers. Next: add a heartbeat-degraded scenario in the integration test suite.
- `F-encryption-key-rotation` — drained verification has edge cases that need explicit coverage (old key with mixed batches, partial resume). Owner: Admin API maintainers. Next: extend re-encryption tests with those cases.

## Update Cadence

- Update when an endpoint is added, modified, or removed, and when its contract tests change.
- Review at milestone boundaries or bounded delivery checkpoints.
- Promote items to the global matrix when they affect product-level acceptance.

## Related Documents

- [`../../global/spec-test-traceability.md`](../../global/spec-test-traceability.md).
- [`../../global/features-and-phases.md`](../../global/features-and-phases.md).
- [`functional-flows.md`](functional-flows.md).
- [`exception-and-error-model.md`](exception-and-error-model.md).
