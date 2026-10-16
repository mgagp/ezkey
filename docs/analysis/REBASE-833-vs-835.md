# REBASE #833 onto main (post-#835) — Step 1 report

**Location:** `docs/analysis/` (moved off repo root — Patrick nit on #833).  
**Branch:** `cursor/admin-rate-limit-passwordless-831-f1f9`  
**Base:** `origin/main` @ `0e2b3cda` (`docs(mobile): canonical app state machine (#834) (#835)`)  
**Historical tip after rebase (product):** `7fd4a484` (m1 unified 409). Later tips delivered Q10/Q9/Q5.  
**Scope (Step 1):** Rebase only. Gaps A–G below were closed in the subsequent critique-GO commit (see PR #833).

---

## 1. Conflicts resolved

| Commit during rebase | File | Nature | Resolution |
| --- | --- | --- | --- |
| `4e1c6606` → `54cc0a59` — `docs: #844 respond signing contract and Marc decision` | `docs/ENDPOINT.md` | **Substantive (docs)** | Combined both sides. Kept incoming #844 block (never-unsigned-200 + HTTP outcomes table as of that commit: 409 terminal / 400 PENDING / 404 not-found / 503 key). Kept HEAD (#835) paragraph on **Question 8** (single `auth-attempt-state-conflict`, Back only, lost approve → Q10). Did **not** drop either side. Later tip commit `7fd4a484` (m1) then rewrote the table to unified 409 + 503 only. |

No other conflict markers. Remaining 22/23 commits applied cleanly (including Java, mobile, specs, Bruno).

**Conservative note (substantive):** Preferring to retain main’s #835 Question 8 wording rather than delete it in favor of the early #844-only paragraph. The early table’s 400/404 rows were left as that commit intended; tip m1 already supersedes them in code and in the final table — no extra “align to #835” code edit was done in this step.

---

## 2. Code (#833 tip) vs new #835 docs — factual gaps / contradictions

Citations are `path:line` on the rebased tree unless noted.

### 2.1 Alignments (no gap)

| Topic | #833 code | #835 docs |
| --- | --- | --- |
| Single client 409 type for terminal / cancel / already decided | `AuthAttemptRespondService.java:51-53`, `:184-190`; `authApiProblem.ts` → `stateConflict` non-retryable | `MOBILE_APP_STRATEGIC_ORIENTATION.md:266-287` (Q8); `MOBILE_APP_STATE_MACHINE.md:195`, `:231-232`, `:270` |
| Back only on 409 (not Try again) | `usePendingAuth.ts:236` + `PendingAuthScreen.tsx:198-208` (`globalErrorRetryable` false → inactive Back) | Q8 locked: orientation `:285-286`; state machine `:195` |
| Lock on HTTP 200 before result-sig verify | `usePendingAuth.ts:489-492` | Q2: orientation `:128-150`; state machine `:224` |
| Leave-as-deny + in-flight guard | `PendingAuthScreen.tsx:84-97`; `usePendingAuth.ts:403`, `:446` | state machine leave section `:234+`; Q11 biometric Approve-only orientation `:337+` |
| Per-`authAttemptId` decision lock (Q13 intent) | `serverDecisionAttemptIdRef` `usePendingAuth.ts:402-408`, `:492`, `:567-569` | Q13 decided orientation `:392-410` (asks fix on #833) |
| Auth respond rate limit + `Retry-After` (device path) | Documented `docs/ENDPOINT.md:118`, `:257`, `:276`; filter behavior unchanged by #835 | orientation `:320` mentions one-request-per-five-minutes as context for deferred idempotent respond — not a contrary rule |
| Admin passwordless-wait separate bucket + `Retry-After` (#831) | `AdminRateLimitFilter.java:53-76`, `:232`, `:247`, `:313`, `:338` | #835 docs do not redefine Admin wait rate limits (orthogonal) |

### 2.2 Contradictions / wording gaps (code not changed in this step)

| # | #833 code (as shipped) | #835 / related docs | Gap |
| --- | --- | --- | --- |
| A | Unified **409** also for attempt/enrollment **not found** and still **PENDING** (`AuthAttemptRespondService.java:164-181`, `:227-232`; `docs/ENDPOINT.md:132`; `docs/SECURITY_POSTURE.md:45-49`) | Q8 text lists only expired / cancelled / already decided (`MOBILE_APP_STRATEGIC_ORIENTATION.md:270-273`, `:285`; `MOBILE_APP_STATE_MACHINE.md:195`, `:270`). Does **not** name not-found or PENDING. | Same wire type; doc scope of Q8 is narrower than #844 Marc m1 client surface. |
| B | Missing integration key → **503** `integration-signing-unavailable`, mobile **retryable** (`AuthAttemptRespondService.java:428-446`; `authApiProblem.ts:28`, resolve path retryable; i18n `resources.ts:266-267`; `SECURITY_POSTURE.md:42-49`; `ENDPOINT.md:133`) | #835 FSM / orientation do not mention respond **503** or a signing-unavailable catalog key. SECURITY_POSTURE #844 bullets live outside #835. | New HTTP outcome in #833/#844 not reflected in #835 mobile decision docs. |
| C | Bad/missing respond sig after HTTP 200 → catalog **`resultNotConfirmed`**: “Result not confirmed. The server may already have recorded this decision…” (`resources.ts:264-265`; set at `usePendingAuth.ts:499`, `:511`, `:532`) | Q9 locked wording: response **was sent**, confirmation could not be verified, **check the sign-in where it was started**; no tenant name; Back only (`MOBILE_APP_STRATEGIC_ORIENTATION.md:289-306`; state machine `:226`) | Same Back-only intent; **copy differs** (no “check the sign-in…” in #833 string). |
| D | Transport / HTTP fail in `catch` → `extractErrorOutcome` → often **retryable** `GlobalError` + Try again (`usePendingAuth.ts:558-574`; screen `:198`) | `UncertainDecision` decided (Q10): result uncertain, point at sign-in, **not a failure**, **no retry that sends another decision** (`MOBILE_APP_STATE_MACHINE.md:198`, `:225`, `:259`; orientation `:308-330`) | Lost-response path not a distinct non-retryable uncertain UI in #833 tip. |
| E | No permanent “leaving declines” line under Approve/Deny found in `PendingAuthScreen.tsx` | Q5 decided: permanent line under actions (`MOBILE_APP_STATE_MACHINE.md:249-252`; orientation Decision Q5 `:218+`) | Honesty-set UI not present on #833 tip. |
| F | Code implements per-id lock via `serverDecisionAttemptIdRef` (`usePendingAuth.ts:402+`) | State machine still says Q13 “Today it is screen-scoped and never resets. Fix tracked on **#833**” (`MOBILE_APP_STATE_MACHINE.md:258`) and still names ref `serverDecisionSentRef` (`:201-206`, `:301`, `:326`) | **Doc stale vs #833 code** (fix landed; FSM text not refreshed in #835 merge). |
| G | `ENDPOINT.md:132` (m1 unified 409 including not-found/PENDING) sits next to `:135-140` (#835 Q8 paragraph naming only expired/cancelled/already decided) | Same file after conflict combine + m1 | Intra-doc tension: table broader than Q8 prose. Left as-is (no behavior/doc rewrite beyond rebase combine). |

### 2.3 Out of #835 scope (noted, not contradictions)

- Admin **passwordless-wait** rate-limit split + sanitized logs + `Retry-After` (`AdminRateLimitFilter.java`) — #831 delivery on this PR; #835 is mobile FSM/orientation.
- Auth API empty-body **429** + `Retry-After` on pending/respond (`ENDPOINT.md:118`) — pre-existing contract; #835 does not change it.

---

## 3. What this step did **not** do

- No code or copy changes to close gaps A–G to post-critique Marc go/no-go.
- No merge of #833.
- No second editorial pass on #835 docs.

---

## 4. Verification plan after push

- Await **ci-gate** + **CodeQL** on rebased tip.
- Report tip SHA and short summary.
