# Java transactional boundaries — assessment-curated lane

Mandate-driven white-box review of Java service-layer transaction placement: public
`@Transactional` only, no same-class public-to-public self-invocation, and a pragmatic look at
`*TxHelper` / `TransactionTemplate` as legitimate independent commits versus compensation for
an oversized service.

**Documented exception (confirms the rule):** `AuthAttemptWaitService` (`NOT_SUPPORTED`) plus
`AdminAuthAttemptTxHelper` (`REQUIRES_NEW`) so wait can poll a committed pending row for the
device respond. Do not treat that triad as a finding.

- Method canon: [`../assessment-curated/README.md`](../assessment-curated/README.md)
- Assessment register: [`../../../../docs/java-transactional-boundaries-assessment-2026-08.md`](../../../../docs/java-transactional-boundaries-assessment-2026-08.md)
- Policy notes: [`../../../../docs/plan/JPA_TRANSACTION_DESIGN_NOTES.md`](../../../../docs/plan/JPA_TRANSACTION_DESIGN_NOTES.md)
- Sibling pattern: [`../java-controller-role-validation/`](../java-controller-role-validation/)
