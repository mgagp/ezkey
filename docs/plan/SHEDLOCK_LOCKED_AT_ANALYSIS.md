# ShedLock "locked_at in the future" — Root Cause Analysis

## Context

After introducing optimistic locking (V44: `@Version` on Tenant, EzkeyAdmin, Enrollment, ApiKey), the elective test `ShedLockDistributedTest.testLocksInDatabase` started failing with:

```
Lock locked_at is in the future for lock: KEY_PROMOTION (locked_at=2026-03-04T22:02:57.497485Z)
```

A 15-second tolerance was added as a workaround. The user questions whether the root cause is really clock skew, given:

- Hundreds of runs over months never showed this
- Docker stack unchanged
- **The only change is optimistic locking**
- A 7-second difference is large for simple clock skew
- More plausible: optimistic locking causes transaction ordering/blocking

---

## Two Competing Hypotheses

### Hypothesis A: Clock skew (host vs Docker/PostgreSQL)

- **Mechanism:** Test runs on host, captures `OffsetDateTime.now()`. ShedLock writes `locked_at` using PostgreSQL's `NOW()` (via `usingDbTime()`). If the PostgreSQL container's clock is ahead of the host, `locked_at` appears "in the future."
- **Evidence against:** Never happened in hundreds of runs; Docker stack unchanged.
- **Evidence for:** Would explain the symptom directly.

### Hypothesis B: Transaction blocking (optimistic locking)

- **Mechanism:** Optimistic locking causes longer or blocked transactions. ShedLock needs a DB connection to acquire the lock. If the pool is exhausted (bootstrap, KEY_PROMOTION, etc. holding connections), ShedLock's lock acquisition is **delayed**. When it finally runs, PostgreSQL's `NOW()` is "later" in real time.
- **Problem with this:** If the lock is inserted at T+7, the test would see it only after T+7. When the test runs at T+10, `locked_at` (T+7) would be **in the past**, not the future. So transaction blocking alone cannot produce "locked_at in the future" from the test's perspective.
- **Exception:** The only way for `locked_at` to be "in the future" is if the **clock** that produced `locked_at` is ahead of the **clock** that produced the test's `now`. So we are back to a time-source difference (host vs DB/container).

---

## Key Finding: Time Source Mismatch

The test compares:

- `locked_at` — from PostgreSQL `NOW()` at INSERT time (DB/container time)
- `now` — from `OffsetDateTime.now()` on the host (JVM time)

For `locked_at > now`, the DB/container clock must be ahead of the host. That is a form of clock skew, regardless of *why* the DB might be ahead (NTP, Docker, VM, etc.).

**However:** Optimistic locking could **indirectly** make this more visible:

1. **Longer transactions** → connections held longer → ShedLock waits for a connection
2. **Retries** (OptimisticLockException) → more load, more contention
3. **Bootstrap + KEY_PROMOTION overlap** at startup → pool pressure
4. Under load, timing becomes more variable; a pre-existing small clock drift might only surface when the test happens to run at a "bad" moment

So: the **immediate** cause is time-source mismatch (host vs DB). The **trigger** that made it appear could be optimistic locking changing transaction timing and load.

---

## Correct Fix: Use Database Clock for Assertion

Instead of comparing `locked_at` to the host's `OffsetDateTime.now()`, compare it to **PostgreSQL's `NOW()`** in the same query. That way:

- Both values come from the same time source (PostgreSQL)
- Host vs container clock skew is irrelevant
- The assertion checks a real invariant: `locked_at` should not be unreasonably in the future **from the database's perspective**

If `locked_at > db_now` by more than a small tolerance (e.g. 1–2 seconds for execution jitter), that would indicate a real anomaly (e.g. clock jump, bug). The current failure is eliminated because we no longer mix host and DB time.

---

## Implementation

1. **ShedLockTestHelper:** Extend the query to also return `NOW() AS db_now`.
2. **ShedLockDistributedTest:** Use `db_now` instead of `OffsetDateTime.now()` for the skew check.
3. **Optional:** Log `host_now`, `db_now`, and `locked_at` for diagnostics when skew is detected.

---

## Summary

| Aspect | Conclusion |
|--------|------------|
| **Root cause** | Time source mismatch: test uses host clock, `locked_at` uses DB clock |
| **Why it appeared now** | Optimistic locking may increase transaction duration and pool contention, making timing more variable and exposing pre-existing drift |
| **Correct fix** | Use PostgreSQL's `NOW()` for the assertion instead of host time |
| **Tolerance** | Keep a small tolerance (1–2 s) for execution jitter; remove the 15 s bandaid |
