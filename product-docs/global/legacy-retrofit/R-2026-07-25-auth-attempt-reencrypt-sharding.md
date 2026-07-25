# Retrofit Slice — `R-2026-07-25-auth-attempt-reencrypt-sharding` Auth-attempt re-encryption sharding

## Metadata

- **ID:** `R-2026-07-25-auth-attempt-reencrypt-sharding`
- **Status:** `integrated`
- **Source type:** `plan`
- **Capture date:** `2026-07-25`
- **Owner:** product + AI collaboration
- **Confidence:** `high`

## Source batch

- `.cursor/plans/auth_attempt_reencrypt_sharding.plan.md`

## Search scope

- Searched: `.cursor/plans/`, `ezkey-core/`, `ezkey-admin-api/`, `ezkey-admin-ui/`,
  `docs/REENCRYPTION_OPERATIONS.md`, `product-docs/global/backlog/ideas/`
- Found relevant:
  - `.cursor/plans/auth_attempt_reencrypt_sharding.plan.md`
  - `docs/REENCRYPTION_OPERATIONS.md`
  - `ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchCreationService.java`
  - `ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchParallelRunner.java`
  - `ezkey-core/src/main/java/org/ezkey/config/TinkProperties.java`
  - `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java`
  - `ezkey-admin-ui/src/pages/encryption-keys.tsx`
- Excluded:
  - broad historical re-encryption narratives already archived and not needed for this slice

## Trigger

The source plan mixed a delivered Phase 1 (throughput sharding) and a future Phase 2 appendix
(resilience and operational hardening). This retrofit separates durable delivered signal from future
work signal to avoid keeping a point-in-time plan as the canonical home.

## Extracted decisions and invariants

- Throughput-oriented auth-attempt sharding is the accepted delivered baseline.
- Batch-level shard visibility in API/UI is part of the operational model, not optional noise.
- Future value is primarily resilience and observability hardening, not a restart of sharding design.
- Keep future lane proportionate: harden scheduler/retry/ops behavior without a heavyweight
  orchestration rewrite unless evidence requires it.

## Mapping to canonical destinations

| Canonical destination | Mapping action | Status |
|-----------------------|----------------|--------|
| `product-docs/global/backlog/ideas/I-2026-07-25-reencryption-batch-resilience-and-ops.md` | Created active future lane for residual Phase 2 signal (scheduler/retry/ops/UI hardening). | integrated |
| `docs/REENCRYPTION_OPERATIONS.md` | Reviewed: sharding baseline and current operational semantics are already documented. | integrated (review only) |
| `product-docs/global/backlog/ideas/I-2026-0029-reencryption-indexed-encryption-key-id-columns.md` | Reviewed: complementary performance lane already exists for indexed discovery. | integrated (review only) |

## Changes applied

- [x] `product-docs/global/backlog/ideas/I-2026-07-25-reencryption-batch-resilience-and-ops.md`
  — created incubating future hardening lane.
- [x] `.cursor/plans/auth_attempt_reencrypt_sharding.plan.md` — source retired after extracting
  residual future signal.
- [ ] `docs/REENCRYPTION_OPERATIONS.md` — deferred; current baseline coverage is sufficient for this
  retrofit slice.

## Confidence and residual gaps

- **Confidence high** that delivered sharding behavior is already represented in code and ops docs.
- **Residual gaps (intentional):** concrete TB slicing for the new hardening lane; final decision on
  `autoRetryFailed` semantics and stale `IN_PROGRESS` policy.

## Next action

- Promote one bounded `TB-*` from
  `I-2026-07-25-reencryption-batch-resilience-and-ops` when prioritization permits.

## Links

- Source plan (retired 2026-07-25): `.cursor/plans/auth_attempt_reencrypt_sharding.plan.md`
- Methodology: `product-docs/methodology/legacy-retrofit-workflow.md`