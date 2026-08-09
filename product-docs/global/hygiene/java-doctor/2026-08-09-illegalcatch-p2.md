# Java doctor-curated campaign — 2026-08-09 IllegalCatch P2 findings

## Metadata

- **Date:** 2026-08-09
- **Branch / PR:** `hygiene/illegalcatch-curated-2026-08-08` (P2 follow-up)
- **Context:** Independent review after P1 IllegalCatch correction
- **Findings source:** Manual code review, not automated tool output
- **Operator:** Marc (+ agent)

## Lot under review

This session addressed two P2 findings identified after the P1 IllegalCatch hygiene pass:

1. **P2-A:** `TinkKeyManager.saveKeysetToDatabaseUnlocked()` — null repository return treated as operational success
2. **P2-B:** `checkstyle.xml` SuppressWithNearbyCommentFilter — generic suppression mechanism too broad for security rule

## Decisions

| # | Finding | Decision | Action |
|---|---------|----------|--------|
| 1 | P2-A: Null repository return in `saveKeysetToDatabaseUnlocked` | **fix** | Throw `IllegalStateException` on `null` save result (fail-closed) |
| 2 | P2-B: Generic `(\w+)` Checkstyle suppression pattern | **fix** | Restrict filter to `IllegalCatch` only; preserve existing syntax |

Both findings accepted and implemented with targeted test coverage and validation.

## Rationale (short)

### P2-A — TinkKeyManager null repository fail-open

**Observation:** After `KeysetBlob saved = keysetBlobRepository.save(keysetBlob)`, the original code treated `saved == null` as a warning-level event, defaulted `databaseKeysetVersion = 0`, then logged "Keyset saved to database..." as if successful.

**Risk:** At a critical key management boundary, a `save()` returning `null` violates the repository contract. Treating this as success can mask a persistence anomaly and leave `databaseKeysetVersion` inconsistent. This is a dangerous fail-open for state that protects at-rest encryption.

**Decision:** Fail-closed. `if (saved == null)` now throws `IllegalStateException("Keyset repository returned null on save")`. No success logging on null return.

**Preserved:** The fallback `saved.getVersion() == null → 0` remains — no local evidence suggests this is problematic. If version is absent but blob is present, version 0 is acceptable per the current model.

**Test:** Added `saveKeysetToDatabase_whenRepositoryReturnsNull_throwsIllegalStateException` in `TinkKeyManagerConcurrencyTest`. Mocks repository to return `null`, initializes manager in `DATABASE` mode, asserts `IllegalStateException` with message mentioning "repository returned null".

### P2-B — Checkstyle suppression mechanism scope

**Observation:** The branch introduced `SuppressWithNearbyCommentFilter` with pattern `CHECKSTYLE IGNORE (\w+)`, allowing inline suppression of **any** TreeWalker rule. While the intent was to suppress `IllegalCatch` at justified boundaries (78 suppressions), the generic mechanism creates a durable escape hatch for other security/design rules.

**Risk:** Generic inline suppression weakens Checkstyle as a guardrail. A rule designed to prevent specific anti-patterns can be silently bypassed without changing the central configuration.

**Decision:** Restrict the filter to `IllegalCatch` only:
- `commentFormat = "CHECKSTYLE IGNORE IllegalCatch"`
- `checkFormat = "IllegalCatch"`
- `influenceFormat = "0"`

**Preserved:** The existing syntax `// CHECKSTYLE IGNORE IllegalCatch` remains valid for all 78 suppressions. No mass file edits required.

**Effect:** Other rules cannot be suppressed inline. If future justified suppressions are needed for different rules, the mechanism must be extended explicitly (intentional friction).

## Follow-ups

None. Both findings resolved in this session.

**Suppressions added:** None (P2-A is a code fix; P2-B restricts suppressions rather than adding them)

**Code touched:**
- `ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java`
- `ezkey-core-security/src/test/java/org/ezkey/security/TinkKeyManagerConcurrencyTest.java`
- `checkstyle-config/src/main/resources/checkstyle.xml`

**Validation:**
- Tests: `mvn -pl ezkey-core-security -am -Dtest=TinkKeyManagerConcurrencyTest test` → 8 tests, 0 failures
- Checkstyle: `mvn -pl checkstyle-config,ezkey-core-security -am checkstyle:check` → 0 violations
- Whitespace: `git diff --check` → clean

## Out of scope this pass

- No refactoring of the 78 existing `IllegalCatch` suppressions (out of scope per session constraints)
- No investigation of controllers/filters/services beyond the two targeted findings
- No campaign to justify each catch block (hygiene pass, not a redesign)
