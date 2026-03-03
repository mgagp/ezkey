# API Design — Inconsistencies Worth Investigating

## 1. Enrollment field naming redundancy

Every field on the Enrollment DTO is prefixed with `enrollment`: `enrollmentId`, `enrollmentName`,
`enrollmentStatus`, `enrollmentActive`. No other entity does this — `Integration` uses `id`, `code`,
`active`. This adds noise and makes mapping/display code more verbose. Worth considering a DTO
normalization pass on the backend.

## 2. Three distinct "challenge" concepts all named "challenge"

These three things coexist in the same codebase and are easy to conflate:

| Current name | Role | Suggested name |
|---|---|---|
| `challengeCode` | 2-digit code shown to the admin during their own passwordless login (formatted `%02d`) | `adminLoginChallenge` |
| `enrollmentChallenge` | Numeric binding code (6 digits, e.g. `654321`) shown during initial device setup | `enrollmentBindingCode` |
| `authAttemptChallengeRequired` | Boolean flag on an enrollment that governs whether future user auth attempts need a challenge | `mfaChallengeRequired` |

The naming doesn't clearly distinguish these three roles.

## 3. `enrollmentProofToken` optionality unclear

The field is `enrollmentProofToken?: string` (optional) in the DTO. It is present in the CREATE
response, but it is unclear whether the GET by ID also returns the full token or a masked/redacted
version after binding completes. If it is omitted after binding, the UI's "Copy Token" feature would
silently show nothing to the admin. Worth confirming the contract.

## 4. No aggregate stats endpoint — dashboard requires 10+ round-trips

The dashboard currently fires 10 parallel `size=1` queries just to get counts. A single
`GET /api/v1/dashboard/stats` endpoint returning all tenant metrics in one payload would be far more
efficient and simpler to maintain. This is a **Phase 3 candidate**.

## 5. `authAttemptChallengeRequired` was absent from the original Enrollment model

This field was added during Phase 2 based on context (TUI column, create request DTO). If the
backend does not actually return this field in `EnrollmentResponseDto`, the column will silently
show "No" for everyone. **Needs verification.**

## 6. ~~`Integration.logo` is in the model but unused~~ [RESOLVED by Phase 1]

Logo was removed from the integration model in the Flatten Integration Entity work.

## 7. `api.delete()` and 204 No Content — potential runtime issue

The `fetchApi` wrapper in `api-client.ts` unconditionally calls `response.json()`. A DELETE endpoint
typically returns `204 No Content` with an empty body, which would cause `JSON.parse('')` to throw
at runtime. The delete enrollment call will likely fail silently or throw an unhandled error when it
succeeds. This needs a guard:

```ts
if (response.status === 204 || response.headers.get('content-length') === '0') return null;
```

## 8. ~~Integration i18n adds complexity~~ [RESOLVED by Phase 2]

Integration now has a single `name` and optional `description` on the entity and DTOs; the
`ezkey_integration_i18n` table was removed.

---

**Priority summary:** Items 3, 5, and 7 are the most likely to cause observable bugs at runtime.
The others are design quality issues that won't block functionality but will accumulate as
maintenance friction.
