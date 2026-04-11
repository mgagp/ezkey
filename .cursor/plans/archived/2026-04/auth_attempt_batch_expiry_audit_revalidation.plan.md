---
name: Auth attempt batch expiry audit — revalidation
overview: COMPLETED — Persisted PENDING/READ→EXPIRED transitions from AuthAttemptExpiryScheduler now emit structured audit (AUTH_ATTEMPT_EXPIRED); manual test validated. Archived 2026-04.
implementationStatus: complete
implementationVerified: true
todos: []
isProject: true
---

# Auth attempt batch expiry audit — revalidation

**Archived 2026-04 — Plan completed.** Implementation merged; manual validation confirmed (e.g. READ on mobile, TTL expiry, persisted `EXPIRED` and audit trail).

## Implementation outcome (completed)

- **`EventType.AUTH_ATTEMPT_EXPIRED`** and audit action `auth_attempt_expired_scheduler` (core constant + Admin/Integration aliases where applicable).
- **`AuthAttemptExpiryScheduler`**: per-row conditional `expireIfStale` (no race with approve/deny/cancel), then **`AuditLogService`** for each successful persisted transition; not on every poll/wait.
- **Docs / Admin UI**: `docs/ENDPOINT.md` note; audit event labels for `AUTH_ATTEMPT_EXPIRED` (en/fr).
- **Tests**: focused scheduler tests (e.g. `AuthAttemptExpirySchedulerTest`).

**Optional follow-up** (unchanged from original note): product may still want **`AUTH_ATTEMPT_*` audit for supersession** (`EXPIRED` from a newer attempt) for full parity—separate from this scheduler work.

---

## Historical revalidation scan (2026-04-11, pre-implementation)

The following was a codebase read used to confirm the QA gap. It describes the **previous** state before the implementation above.

### Verdict at that time: core gap unchanged

The QA observation was accurate for **integration / enrollment auth attempts** (event types `AUTH_ATTEMPT_*`):

1. **`AuthAttemptExpiryScheduler`** performed a **bulk UPDATE** only (`expireAttemptsPastDeadline`) and logged to SLF4J when `updated > 0`. It did **not** call `AuditLogService`.  
   - File: [`AuthAttemptExpiryScheduler.java`](../../../../ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptExpiryScheduler.java)

2. **`EventType`** had `AUTH_ATTEMPT_CREATED`, `AUTH_ATTEMPT_PENDING`, `AUTH_ATTEMPT_RESPOND`, `AUTH_ATTEMPT_CANCELLED` — **no** `AUTH_ATTEMPT_EXPIRED` (or equivalent).  
   - File: [`EventType.java`](../../../../ezkey-core/src/main/java/org/ezkey/audit/domain/EventType.java)

3. **Precedent** for automatic lifecycle + audit was **enrollment** expiry cleanup: `EnrollmentExpiredCleanupScheduler` persists `EXPIRED` and emits `ENROLLMENT_EXPIRED` per row.  
   - File: [`EnrollmentExpiredCleanupScheduler.java`](../../../../ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentExpiredCleanupScheduler.java)

4. **Supersession** (new attempt supersedes older `PENDING`/`READ` → `EXPIRED`) in `AuthAttemptService` still updated status **without** an `AUTH_ATTEMPT_*` audit row — same class of “silent terminal transition” as the batch if product wants parity.  
   - File: [`AuthAttemptService.java`](../../../../ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java) (search `Supersession`).

5. **`AuthAttemptWaitService`** computed effective `EXPIRED` at read time for wait/poll responses; it did not replace a durable audit of the **persisted** status flip done by the scheduler.

### What changed elsewhere (do not conflate with QA scenario)

**Admin API `POST /api/v1/admin/auth/passwordless-wait`** emits audit rows for terminal outcomes (`ADMIN_LOGIN` + `login_mfa_session_issued`, `login_mfa_expired`, etc.). That improves the **admin passwordless login** story documented in [`docs/AUDIT_ADMIN_LOGIN_ACTIONS.md`](../../../../docs/AUDIT_ADMIN_LOGIN_ACTIONS.md).

It did **not** fill the gap for **non-admin** auth attempts tied to integrations: those use `AUTH_ATTEMPT_*` events from Auth/Admin/Integration API controllers; the **batch** transition to persisted `EXPIRED` needed a matching audit event — **now addressed** by the implementation summarized at the top of this file.

### Recommendation status (historical table)

| Item | Still valid? |
|------|----------------|
| Audit **persisted** expiry transitions from the scheduler (and optionally align taxonomy with cancel vs. supersession) | Addressed for scheduler-driven expiry |
| Avoid auditing every poll/`wait` iteration; rely on state change + structured details | **Yes** |
| Treat admin MFA `login_mfa_expired` as **orthogonal** to `AUTH_ATTEMPT_*` lifecycle for integration flows | **Yes** |

---

*This note complemented the older plan [`audit_vs_auth_expiry.plan.md`](../../../audit_vs_auth_expiry.plan.md) (admin MFA / UI help), which targeted a different surface.*
