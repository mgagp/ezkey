# Stabilization lessons

What the November 2026 stabilization around #1180 and #1259 taught us about analysis, design, tests and code structure. Read this before you touch startup code, HA behaviour, an exposure rule, a test helper, or a CI failure that looks random.

Each lesson states a rule, the evidence behind it, and a check you can run. Candidates not yet reproduced are in #1267 and #1266.

## The case in brief

- #1225 removed crypto material from the Admin and Integration APIs. Its unit tests and CI backend job were green, yet the rule was bypassed and a test tool was broken.
- `ha-smoke` failed on that PR for an unrelated reason, as it had on other branches. An earlier note blamed `start_period`. The real cause was two startup races (#1259, #1263).

## Analysis

**1. A red job is evidence, not a verdict.** Before rerunning or blaming the PR, compare the failure across branches and get the logs.
- Evidence: 3 of 115 `ha-smoke` runs shared one signature on unrelated branches. The `start_period` theory had no logs behind it.
- Check: `gh run list` and job logs across branches, then reproduce locally.

**2. An inventory by reading is a hypothesis.** Prove "no consumer needs this field" by running the consumers.
- Evidence: the #1180 inventory said no consumer needed `integrationPublicKey`. `DemoDeviceEnrollmentWriter` read it from the Admin GET.
- Check: remove the field, run every consumer on a live stack, and look at what they wrote, not only whether they passed.

**3. A rule about a value applies to every exit of that value.** Search from the source getter, not from the DTO.
- Evidence: the JSON fix left the token in the QR PNG, and #1266 found it again in the admin onboarding endpoints.
- Check: `grep -rn 'getEnrollmentProofToken\|composePayload'`. Consider JSON, images, files, logs, and sibling endpoints.

**4. Keep measuring after the first fix.** A race can hide another one.
- Evidence: with the keyset race fixed, 2 of 25 cold starts crashed on the bootstrap lock, which the first race had been masking.

## Design

**5. Startup code runs on every instance.** Treat each `ApplicationReadyEvent` listener as concurrent and repeatable, and say how it behaves in HA.
- Evidence: two `admin-api` instances inserted the same keyset row on an empty database.
- Check: `grep -rl 'EventListener(ApplicationReadyEvent' --include='*.java' .`

**6. A lock must outlive the commit.** Open the transaction inside the lock, never around it.
- Evidence: `@Transactional` around `executeWithLock` released the lock before the commit, and the next holder failed on `ezkey_admin_username_key`. `LockingTaskExecutor` now owns the transaction.

**7. Catching a database error inside a transaction does not recover it.** Spring has marked the transaction rollback-only, and PostgreSQL rejects further statements. Recover outside, in a separate transaction. H2 can hide this, so prove recovery paths on PostgreSQL.
- Evidence: the `catch` in `initializeKeysetSync` said "Don't throw", yet `UnexpectedRollbackException` ended startup.
- Check: look for `catch` of `DataAccessException`, `DataIntegrityViolationException` or `PersistenceException` inside `@Transactional` methods.

**8. Name what happens on failure, and check that it is true.** See design principle §17. A comment claiming fail-open while the behaviour is fail-closed is a defect.

## Tests

**9. Prove a fix red first, for the right reason.** Read why it fails.
- Evidence: the first red of `KeyRotationServiceConcurrentSyncTest` was a Mockito error, not the race. Only the second reproduced `UnexpectedRollbackException`.

**10. A test helper must not hide bad data.** Avoid silent defaults such as `x != null ? x : ""`. Refuse the write.
- Evidence: the writer stored an empty key while the suite stayed green.
- Check: `grep -rnE '!= null \? .* : ""|Continuing despite' ezkey-tests/src/test`

**11. Green is not the same as usable afterwards.** Run the next consumer after a suite.
- Evidence: `ezkey-tests` passed, then Playwright login failed on the Demo Device the suite had just rewritten. CI does not run this sequence.

**12. To prove a race fix, count that the race happened.** Report occurrences and final database state, not only passes.
- Evidence: 0 crashes in 40 cold starts means something because the keyset race occurred 20 times in them, each ending with 1 admin, 1 enrollment and 1 key.
- Local rates can be far higher than CI (7 of 12 against about 2.6%), which makes reproducing cheap.

**13. A job that destroys its stack must print evidence first.** `ci-ha-lb-smoke.sh` now does. Scripts with teardown need the same.

## Code structure

**14. Put an invariant in one place.** Prefer one owner over a convention each caller must remember.
- Evidence: moving the transaction into `LockingTaskExecutor` fixed every caller at once.
- Open point: the rule "proof token only while `CREATED`" still lives in three places: `EnrollmentAdminMapper`, `EnrollmentController.getQrCode` and `EnrollmentQrDeliveryService`. A single policy would have prevented the QR bypass. #1266 would add a fourth place, so decide this there.

**15. Take secrets out of templates, not configuration.** `local.bru.example` lost its localhost URLs along with the secrets, which broke g4 on a fresh clone (#1264).
