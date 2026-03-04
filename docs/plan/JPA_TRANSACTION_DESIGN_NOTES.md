# JPA Transaction Design Notes

Brief design notes for JPA transaction and persistence context usage in Ezkey. Intended to prevent recurring issues and guide future audits.

---

## Lessons Learned (Bootstrap / Optimistic Locking Investigation)

### 1. Self-Invocation Bypasses Proxy — Extra Sessions

**Issue:** When a Spring bean calls itself (e.g. `this::privateMethod` or `this.helper()`), the call does not go through the proxy. AOP interceptors such as `@Transactional` are not applied.

**Effect:** Each repository call runs in its own auto-commit session. Shared persistence context is lost. Example: enrollment saved in one session, admin saved in another → `TransientPropertyValueException` because the enrollment is not visible when the admin is flushed.

**Mitigation:** Place `@Transactional` on the entry point (e.g. event listener) so the whole flow runs in one transaction. Avoid passing `this::method` to executors or callbacks when that method must be transactional.

**Future:** Audit for self-invocation patterns that bypass transactional boundaries. Higher risk under multi-thread and high transaction load.

---

### 2. Nested @Transactional Anti-Pattern

**Issue:** Multiple service layers each annotated with `@Transactional`. When Service A calls Service B, each creates a transaction boundary. On exception propagation, inner transaction rollback can cause "Transaction Already Rolled Back" and obscure diagnostics.

**Mitigation:** Prefer a single transactional boundary at the top-level use case (controller/facade). Inner services should not declare `@Transactional` unless they explicitly need a separate transaction (e.g. `REQUIRES_NEW` for isolated work).

**Future:** Audit for stacked `@Transactional` across service layers. Document a clear rule (e.g. "transaction at facade only" or "one boundary per request").

---

## Audit TODO

- [ ] Scan for self-invocation of `@Transactional` methods (e.g. `this::`, lambdas capturing `this`)
- [ ] Scan for `@Transactional` on multiple service layers in the same call chain
- [ ] Document transaction boundary policy (where `@Transactional` is allowed)
