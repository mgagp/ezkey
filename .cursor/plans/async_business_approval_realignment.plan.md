---
name: Contextual Auth — Phase 1 & Phase 2 Roadmap
overview: >
  Two-phase roadmap for contextual authentication. Phase 1 (now): keep auth attempts
  short-term, simplify contextual fields to title+message only, add optional shorter TTL
  at creation. Phase 2 (future): introduce a separate Business Approval concept with its
  own entity, async workflow, and webhook mechanism.
isProject: false
---

# Contextual Authentication — Phase 1 & Phase 2 Roadmap

## Why Two Phases

The current auth attempt model is inherently **short-term and singular** (one active attempt
per enrollment at a time, with supersession of older attempts). Retrofitting long-duration
async business approval onto this model creates fundamental contradictions:

- **Supersession** — creating a new auth attempt cancels the previous one. Acceptable for
  login (only one login session at a time). Destructive for business approvals (multiple
  independent approvals can coexist).
- **TTL semantics** — a 2-minute window is a security feature for login. A 24-hour window for
  a payment batch approval is a completely different concept.
- **Identity model** — login: same person, two devices. Business approval: two different people
  (initiator ≠ approver).

These are not implementation details — they are **model-level differences** that warrant a
separate entity and separate API in Phase 2.

---

## Phase 1 — Short-term Contextual Auth (current scope)

**Goal:** Keep the existing auth attempt model intact. Add just enough context for a demo use
case that is NOT a pure login (e.g., "confirm this sensitive admin action"), while respecting
the short-term, one-at-a-time nature of the model.

### What Phase 1 Keeps / Adds

- ✅ `contextTitle` (VARCHAR 200) — displayed as the card header on mobile
- ✅ `contextMessage` (VARCHAR 2000) — descriptive message for the approver
- ✅ Optional `timeoutSeconds` at creation (must be ≤ configured max — shorter only, not longer)
- ✅ Supersession logic unchanged
- ✅ Short TTL semantics unchanged

### What Phase 1 Removes

- ❌ `contextDetails` — raw JSON key-value object. Too complex: requires `RawJsonDeserializer`,
  `@JsonRawValue`, special SDK serialization, Postman workarounds. Removed entirely.
- ❌ `contextLevel` (INFO / WARNING / CRITICAL) — visual severity badge. Not needed to
  demonstrate the concept. Removed entirely.

### Phase 1 To-Do List

#### Backend — Non-Negotiable (implement now)

| # | Task | Files |
|---|------|-------|
| 1 | **Flyway V40**: `DROP COLUMN context_details, context_level` from `ezkey_auth_attempt` | `V40__remove_context_details_and_level.sql` |
| 2 | Remove `contextDetails` + `contextLevel` from `AuthAttempt` entity | `AuthAttempt.java` |
| 3 | Remove from core domain objects | `AuthAttemptCreateRequest`, `AuthAttemptCreateResponse`, `AuthAttemptPendingResponse` |
| 4 | Remove from `AuthAttemptCreateResponseDto` (core) | `AuthAttemptCreateResponseDto.java` |
| 5 | Remove from `AuthAttemptCreateRequestDto` (admin-api) incl. `@JsonRawValue`, `@JsonDeserialize` | `AuthAttemptCreateRequestDto.java` |
| 6 | Remove from `AuthAttemptPendingResponseDto` (auth-api) incl. `@JsonRawValue`, `@JsonDeserialize` | `AuthAttemptPendingResponseDto.java` |
| 7 | Update `AuthAttemptService.create()` + `AuthAttemptPendingService.buildPendingResponse()` | service layer |
| 8 | Check if `RawJsonDeserializer` is used elsewhere; delete if unused | `RawJsonDeserializer.java` |
| 9 | SDK: remove `contextDetails` + `contextLevel` from `AuthAttemptContext`, `EzkeyClient`, `AuthAttemptCreateResponse` | `ezkey-sdk/java/` |
| 10 | ACME demo: remove `contextLevel` from `buildContextForScenario()` | `BusinessApprovalController.java` |
| 11 | Mobile: remove `contextDetails` + `contextLevel` from types + `PendingAuthScreen` | `types.ts`, `PendingAuthScreen.tsx` |
| 12 | Fix all tests using the old 8-arg `AuthAttemptCreateResponseDto` constructor | test files |
| 13 | Postman: remove `contextDetails` + `contextLevel` from all example requests | collections |

#### Backend — Phase 1 Planned (implement shortly after)

| # | Task | Notes |
|---|------|-------|
| 14 | **Optional `timeoutSeconds` at creation** | Caller may request a TTL shorter than or equal to the configured max (`ezkey.core.auth-attempt.ttl-seconds`). Longer requests are silently capped or rejected with 400. |
| 15 | ACME demo simplification (2 buttons: CREATE + CHECK STATUS) | Remove auto-polling and countdown timer; show async nature of the workflow clearly |

---

## Phase 2 — Business Approval (future, separate scope)

**Goal:** Introduce a first-class `BusinessApproval` concept as a new entity, independent of
`AuthAttempt`. This avoids contaminating the auth model with async semantics.

Key design decisions for Phase 2 (to be detailed in a separate plan):

- **New table** `ezkey_business_approval` — not a repurposing of `ezkey_auth_attempt`
- **No supersession** — multiple concurrent approvals for the same enrollment are valid
- **Custom TTL** — up to days/weeks, driven by the approval type
- **Rich context** — contextTitle, contextMessage, contextDetails (JSON), contextLevel can
  be reintroduced here, where they make sense
- **Polling + optional webhook** — integrated application can poll OR register a callback URL
- **Separate API** — new endpoint group (e.g., `POST /api/v1/business-approvals`)
- **Mobile UX** — dedicated approval card, distinguishable from standard MFA requests

> Phase 2 will be planned in a separate `.plan.md` when Phase 1 is complete and stable.
