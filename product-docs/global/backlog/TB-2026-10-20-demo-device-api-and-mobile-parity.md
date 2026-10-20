# Tracer Bullet Brief — `TB-2026-10-20-demo-device-api-and-mobile-parity` Demo Device API + mobile FSM parity (draft spec)

## Metadata

- **ID:** `TB-2026-10-20-demo-device-api-and-mobile-parity`
- **Status:** `draft`
- **Related idea:** `I-2026-10-20-demo-device-api-and-mobile-parity`
- **GitHub issue:** `#943`
- **Lane:** `D`
- **Posture:** `analysis-first` (this PR is doc-only; implementation after Marc go/no-go)
- **Created at:** `2026-10-20`
- **Updated at:** `2026-10-20`
- **Captured by:** Marc

## Objective

Produce a **draft specification** that:

1. Inventories drift between `ezkey-demo-device` and current Auth / Integration OpenAPI contracts.
2. Inventories drift versus the mobile reference FSM and on-screen states
   ([`MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md)).
3. Classifies each gap (**fix** / **accept** / **out of scope**).
4. Maps each FSM state, transition, and edge case to **kept** / **simplified** / **deliberately
   omitted**, with rationale.
5. States an explicit **balance-point proposal** for Alex, Julie, Patrick, and Marc to validate,
   with open questions and rough effort per fix group.

No product code in this slice.

---

## Target fidelity level

| Axis | Target |
| --- | --- |
| Role | Internal lab / agent / QA tool — not a second product mobile client |
| Behaviour | **Faithful** on the Auth API device path (bind → verify → pending → respond) and on the
  HTTP/problem outcomes operators hit in demos |
| Exhaustiveness | **Not** required: no biometrics, no Keystore tiers, no Settings hub, no i18n pack, no
  installation trust-zone chrome, no full race-ref matrix |
| UI honesty | Enough that Approve/Deny/expired/conflict/rate-limit/signing-unavailable are
  distinguishable without reading server `detail` |
| Initiator clocks | Countdown remains on Admin UI / Acme / PAM / SDK (#834 Q1). Demo Device does not
  add a competing phone timer unless Marc explicitly opts the lab tool in |

---

## Surfaces in interaction (who initiates)

| Surface | Who initiates | What they watch |
| --- | --- | --- |
| Integrating app (Admin UI passwordless, Acme, PAM/SSH, SDK) | End user / operator starts login | Create + wait; `expiresAt` / countdown; wait verdict (`ACCEPTED` / `REJECTED` / `EXPIRED` / …) |
| Auth API | Device protocol | `pending` (PENDING→READ), `respond` (typed 409 / 503 / 429 / signed 200) |
| Integration API | Integrating app (API key) | `POST /auth-attempts` (+ optional `ttlSeconds`), `GET …/wait`, `POST …/cancel` |
| Demo Device | End user / agent who opens the phone UI to approve or deny | Bind/verify/pending/respond **client** of Auth API only |
| Mobile app (reference) | Same human role as Demo Device | Full FSM in `MOBILE_APP_STATE_MACHINE.md` |
| Evaluator self-registration | Anonymous visitor on Admin API / ezkey.org | **Not** a Demo Device surface |

Demo Device never calls Integration API. Wait/verdict fidelity is therefore **interactional**: when
wait returns `EXPIRED`, a late Demo Device Approve must surface typed expiry (409), not a silent or
opaque failure.

---

## Part A — API contract inventory (evidence)

Specs on `main`: `specs/auth-api/openapi-spec.json`, `specs/integration-api/openapi-spec.json`.
Problem types: `ezkey-auth-api/.../AuthApiProblemCatalog.java`.

### A1. Auth `POST /api/v1/enrollments/bind`

| | |
| --- | --- |
| **API** | 200 bind DTO; **400**; **409** already bound / proof used; **429** + `Retry-After`; **500**. OpenAPI path L60–132 (`specs/auth-api/openapi-spec.json`). |
| **Demo Device** | Maps **409** to clear “already bound” copy; **400** to invitation guidance; other statuses append raw response body into the UI error (`EzkeyAppController.toBindErrorMessage` L350–363, catch L258–267). No **429** catalog / Retry-After. |
| **Classification** | **fix** — stop echoing ProblemDetail bodies; add a short 429 message (Retry-After optional in lab). **Accept** not parsing every bind problem `type` beyond 409/400. |

### A2. Auth `POST /api/v1/enrollments/verify`

| | |
| --- | --- |
| **API** | 200; **400**; **409** state conflict; **429** + `Retry-After`; **500**. OpenAPI L134–206. |
| **Demo Device** | Broad `catch (Exception)` → `"Verification failed: " + e.getMessage()` and deletes local enrollment (L478–486). No typed 409/429 UX. |
| **Classification** | **fix** — map WebClient status to calm lab copy (409 conflict / 429 retry); keep fail-closed delete on hard verify failure. |

### A3. Auth `POST /api/v1/auth-attempts/pending`

| | |
| --- | --- |
| **API** | **200** pending DTO (no `expiresAt` property — schema L789+); **204** none; **400**; **429** + `Retry-After`. OpenAPI L272–327. |
| **Demo Device** | 204 → empty / “No pending…”; 200 → verify integration sig → session claim with local TTL fallback (`ClaimExpiresAtResolver`, `AuthApiService.toPendingAuthPull` L162–167). **429**/other → generic exception message on auth idle (L596–606). Optional wire `expiresAt` parsed if present (forward-compatible; schema omits it per #834). |
| **Classification** | **Accept** no phone countdown and no pending-wire `expiresAt` (aligns mobile #834 Q1). **fix** 429 calm copy on pending. **Accept** session-claim rehydration as lab substitute for mobile route `initialAttempt` (read-once). |

### A4. Auth `POST /api/v1/auth-attempts/respond`

| | |
| --- | --- |
| **API** | **200** signed `APPROVED`/`DENIED`/`FAILED` (#844); wall-clock expiry → **409** `auth-attempt-expired` (#895); other conflicts → **409** `auth-attempt-state-conflict` (#844); missing signing key → **503** `integration-signing-unavailable`; **429** + `Retry-After`. OpenAPI description L335; responses L368–401. Catalog: `AuthApiProblemCatalog` L42–59. |
| **Demo Device** | Typed **409 expired** vs generic **409** (`applyRespondConflictToModel` L797–815; tests `EzkeyAppControllerRespondConflictTest`). **503** / **429** → `"server returned {status}"` (L812–814). Still handles `EXPIRED` in **200** body (L737–743) though contract says expiry is 409. |
| **Classification** | **fix** — catalog 503 (retryable, keep claim if possible) and 429; do not show server `detail`. **Accept** defensive 200/`EXPIRED` branch (dead/legacy). **Keep** split expired vs “no longer active” (#895 parity with mobile `authApiProblem.ts`). |

### A5. Integration create / wait / cancel (initiator)

| | |
| --- | --- |
| **API** | Create: optional `ttlSeconds` (min 15, clamp 120), response carries applied TTL / `expiresAt` (#895) — OpenAPI Integration L29–35, `ttlSeconds` L331. Wait: status enum includes `EXPIRED` — L104–110, schema L440–457. Cancel: PENDING/READ → `EXPIRED`, **409** if terminal — L206–248. |
| **Demo Device** | Does not call these endpoints. |
| **Classification** | **Out of scope** as client code. **Accept** documenting the interaction: initiator owns countdown; device must honour late-respond 409. Acme wait UX (#728) stays on Acme. |

### A6. Evaluator self-registration

| | |
| --- | --- |
| **API** | Admin `POST /api/v1/public/evaluator-signup` (`specs/admin-api/openapi-spec.json` ~L463). |
| **Demo Device** | No consumer. |
| **Classification** | **Out of scope** for this TB. |

### A7. Related closed hygiene (context only)

| Issue | Status vs Demo Device |
| --- | --- |
| #799 distinct session cookies | Done — not reopened here |
| #753 log scrubbing | Done — keep invariant in any error-mapping work |

---

## Part B — On-screen state comparison (mobile vs Demo Device)

Canonical mobile screens:
[`MOBILE_SCREENS_AND_WIREFLOWS.md`](../../../ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md).
Demo Device templates under `ezkey-demo-device/src/main/resources/templates/phone/ezkey/`.

| Moment | Mobile shows | Demo Device shows | Gap note |
| --- | --- | --- | --- |
| Home / list | Installation + tenant groups; unusable rows (MOB-015) | Tenant groups (`EnrollmentTenantGrouper`); no MOB-015 unusable taxonomy | Accept omit MOB-015 |
| Enroll scan | Camera / QR wizard states | File/paste QR + manual ID/token | Simplified entry (lab) |
| Bind in flight | “Binding…” | Full page post; no in-flight button lock beyond form submit | Accept |
| Bind error | Calm banner; no server `detail` | 409/400 mapped; else may echo body | Fix body echo |
| Verify challenge | 6-digit PinCodeInput | Free-text challenge | Accept (lab console) |
| Detail → pending | Explicit **Check pending**; feedback on Detail if none / fail-closed | Auth idle + **Check for Authentication Requests**; message/error on same shell | Simplified navigation OK |
| Pending Ready | Context card; Approve/Deny; **leave-declines hint**; 2-digit challenge | Context card; Approve/Deny; **no** leave-declines hint; free-text challenge | Fix hint + optional leave-as-deny (see FSM); accept challenge UX |
| Pending Empty | Empty + Check again | Message + recheck link | Kept (simplified copy) |
| No longer active | Back only; catalog sentence | Result page “no longer active” / expired | Kept; Back to enrollments |
| Expired (#895) | Distinct `requestExpired` catalog | Distinct `MSG_AUTH_EXPIRED` + expired icon | Kept |
| 503 signing | Retryable; keeps loaded attempt | Generic failed + status code | **Fix** |
| 429 | Retryable catalog | Generic / exception text | **Fix** |
| UncertainDecision (Q10) | Back only; lock attempt id | N/A (sync WebClient; timeout → failed message) | Simplify or omit — see FSM map |
| Completed | Navigate to Detail + recent summary | Dedicated `auth_result` page | Simplified (kept) |
| Protected approve | Biometric / device credential | None | Deliberately omit |
| Side exit / leave with loaded attempt | Leave → Deny (#833) | Side-exit → enrollments **without** respond (`auth_pending.html` L192–196) | **Fix or accept with caveat** — open Q |

---

## Part C — FSM map (keep / simplify / omit)

Source of truth: [`MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md)
(plus #895 expired problem type in mobile `authApiProblem.ts`). Demo Device is **not** required to
implement an FSM library — only behaviour/UI outcomes.

### C1. Enrollment wizard FSM

| Mobile state / transition | Demo Device posture | Rationale |
| --- | --- | --- |
| `ScanIdle` / `ScannerOpen` | **Simplified** — QR file/paste + manual fields | Lab browser has no camera product path |
| `Binding` / `BindError` | **Simplified** — server-rendered bind result/errors | Enough for operators; fix body echo |
| Bind signature / algo fail-closed | **Simplified** — trusts bind response fields; no full mobile algo gate UI | Lab; protocol still signs verify later |
| `DraftReady` / `Verifying` / `ChallengeError` | **Kept (simplified)** — bind then verify form | Same two-step ceremony |
| Back clears draft without persist | **Simplified** — local JSON may exist after bind before verify | Accept: lab can delete file / re-bind |
| Mid-flight back no-op | **Omitted** | Low value for form posts |
| Signed `instance-info` after verify | **Omitted** | Display branding not needed for MFA lab |
| Duplicate bind guard (`isBinding`) | **Omitted** | Accept rare double-submit |

### C2. Detail → claim pending

| Mobile state / transition | Demo Device posture | Rationale |
| --- | --- | --- |
| Pull model, user-initiated | **Kept** | Same product posture |
| `DetailChecking` busy guard | **Simplified** — one-shot `data-armed` on recheck link (`auth.html`) | Enough against double GET |
| Claim `none` (204) | **Kept** | Idle message |
| Claim `fail_closed` (bad sig / malformed) | **Kept (simplified)** — “Invalid integration signature” / errors; does not show Approve | Aligns #834 Q4 honesty |
| Claim `attempt` → PendingAuth | **Simplified** — session `ClaimedPendingAuth` instead of route params | Lab read-once rehydration (#895 PR-C) |
| Store `expiresAt` from pending | **Simplified** — parse if present; else local TTL fallback | Wire omits field; fallback drift accepted or fixed via property |
| Phone countdown | **Deliberately omitted** | #834 Q1 — initiator owns clock |

### C3. Pending Auth FSM

| Mobile state / transition | Demo Device posture | Rationale |
| --- | --- | --- |
| `Ready` Approve / Deny | **Kept** | Core lab job |
| Challenge on Approve (2 digits) | **Simplified** — free-text numeric when required | Console demos vary digit length |
| Leave-as-deny + permanent hint | **Simplified proposed fix** — wire side-exit (and optionally Back) to Deny; show one hint line | Unblocks Admin wait; see balance proposal |
| `respondInFlightRef` / `serverDecisionAttemptIdRef` | **Simplified** — clear session claim on respond entry; no full ref matrix | Lab sync posts; accept residual double-click risk |
| HTTP 200 + result-sig verify | **Kept** | Tamper honesty |
| Result-sig fail after 200 | **Simplified** — failed result message; no Q2 Back-only lock UX | Accept for lab |
| `NoLongerActive` (409 state-conflict) | **Kept** | Already mapped |
| Expired 409 (`auth-attempt-expired`) | **Kept** | #895 PR-C |
| `SigningUnavailable` (503) | **Fix → keep (simplified)** — retryable failed copy; re-show pending if claim still open | Mobile catalog; lab needs distinguishable 503 |
| 429 retryable | **Fix → keep (simplified)** — calm “too many requests; retry shortly” | Rate-limit demos / agent churn |
| `UncertainDecision` (Q10) | **Deliberately omitted** (default) | Sync WebClient rarely yields “sent but no response”; open Q if agents need it |
| Biometric protected approve | **Deliberately omitted** | Not a lab requirement |
| App-kill during respond | **Accept omit** | Same as mobile accepted limit (#834 Q12) |
| `EXPIRED` in 200 body branch | **Accept** defensive dead code | Contract path is 409 |

### C4. Cross-cutting / API side-effects

| Concern | Posture |
| --- | --- |
| Never call Integration cancel / Admin passwordless-cancel | **Kept** (same as mobile) |
| Cloudflare edge vs origin problem bodies | **Accept** current AGENTS.md guidance — no edge catalog in Demo Device |
| Standalone repo sync after monorepo changes | **Kept** process (existing dual-repo note) |

---

## Part D — Balance-point proposal (for stakeholder validation)

**Proposal name:** Demo Device = “faithful device stub.”

### In (must remain or become true)

1. Correct Auth API ceremony: bind → verify → pending (sig verify) → respond (sig verify).
2. Read-once pending: session claim rehydration without a second successful pending claim.
3. Distinguish **expired** vs **no longer active** on respond 409 (#895 / #844).
4. Calm, non-echoing error catalog for **429** (all device endpoints) and **503** respond
   (`integration-signing-unavailable`), without showing server `detail`.
5. Leave / side-exit with a loaded attempt **declines** the request (simplified leave-as-deny) **or**
   an explicit accepted caveat documented in AGENTS.md if Marc declines the code change.
6. Fail-closed on bad pending integration signature (do not show Approve).

### Out (explicit non-goals)

1. Phone TTL countdown / pending-wire `expiresAt` for UI timer (#834 Q1 stands).
2. Biometrics, Keystore storage tiers, protected-approve policy, Settings / Danger Zone / i18n.
3. Full Q10 UncertainDecision UX and per-attempt decision-lock refs.
4. MOB-015 unusable-enrollment taxonomy; signed instance-info branding path.
5. Integration API client; evaluator signup; Acme wait-page work (#728).
6. Exhaustive ProblemDetail `type` matrix for every bind/verify failure mode.

### Stakeholder lenses

| Role | Ask |
| --- | --- |
| **Alex** (intent) | Does “faithful device stub” match the product story of Demo Device vs mobile? |
| **Julie** (operability) | Are expired / conflict / 503 / 429 / leave-as-deny copy and navigation operable in ~3 seconds? |
| **Patrick** (craft) | Is omit of Q10 / race refs proportionate? Any security honesty gap (body echo, leave without deny)? |
| **Marc** (budget) | Go / no-go on fix groups below; resolve open questions. |

---

## Open questions for Marc

1. **Leave-as-deny on Demo Device:** Implement simplified Deny-on-side-exit (and hint line), or
   **accept** “escape hatch leaves READ until TTL” with a permanent AGENTS.md caveat?
2. **503 retry UX:** After 503, should Demo Device keep the session claim and re-show
   Approve/Deny (mobile-like), or clear claim and require Check again?
3. **Local TTL fallback vs PAM 90 s:** Keep property tuning + 409 net, or invest in Auth pending
   `expiresAt` **only for Demo Device** (would reopen #834 Q1 for lab — not recommended)?
4. **UncertainDecision:** Omit entirely for Demo Device, or add a single “response may have been
   recorded — check the application” page on WebClient timeout after respond started?
5. **Challenge digit UX:** Keep free-text, or constrain auth challenge to 2 digits to match mobile?
6. **Budget ceiling:** Approve Groups F1+F2 only (errors + leave), or also F3 (verify 409 polish /
   tests)?

---

## Rough effort estimate (implementation follow-up, not this PR)

| Group | Contents | Rough size |
| --- | --- | --- |
| **F1 — Error catalog** | Bind/verify/pending/respond: 429 + respond 503 typed messages; never echo response bodies; unit tests | Small (½–1 day eng) |
| **F2 — Leave-as-deny lite** | Side-exit / leave with claim → `respond(false)`; permanent hint under buttons; agent recipe update | Small–medium |
| **F3 — Verify conflict polish** | Typed verify 409/429; reduce `e.getMessage()` leakage | Small |
| **F4 — Docs-only acceptances** | AGENTS.md / CONFIGURATION.md caveats for omitted FSM edges; dual-repo sync note | Tiny (docs) |
| **F5 — Optional** | UncertainDecision page; 2-digit challenge constraint; claim keep-on-503 | Small each; only if Q1–Q4 say yes |

Suggested default after go: **F1 + F2 + F4**; defer F3/F5 unless Julie/Patrick flag them in review.

---

## First executable slice (after go — not this PR)

1. F1 error catalog + tests in monorepo `ezkey-demo-device`.
2. F2 leave-as-deny lite if Marc answers Q1 = implement.
3. Sync standalone `mgagp/ezkey-demo-device` after monorepo merge.
4. Manual matrix: Admin passwordless leave; PAM short TTL late approve; forced 429/503 if injectable.

## Rollback or fallback posture

- Doc-only draft: revert the product-docs PR.
- Future code: each fix group is independently revertable; leave-as-deny lite must not break agent
  handshake (`demo-device-back-to-enrollments` after Approve).

## Critical flows (acceptance when implemented)

| Flow | Nominal | Exception |
| --- | --- | --- |
| Admin passwordless | Check → Approve → Admin proceeds | Leave with loaded attempt → Admin sees deny/reject, not TTL wait |
| PAM short TTL | Approve inside TTL | After TTL → expired result (409 typed), not opaque 500 text |
| Signing key missing | — | 503 retryable copy |
| Rate limit | — | 429 calm copy |
| Bad pending sig | — | No Approve UI |

## Evidence plan (this analysis PR)

- [x] OpenAPI + controller/template citations in Parts A–C
- [x] FSM keep/simplify/omit table
- [x] Balance proposal + open questions
- [ ] Collaborator critique (Alex / Julie / Patrick) — after draft PR
- [ ] Marc go/no-go on budget

## Quality gates

- analysis gate — inventory complete with file/line / OpenAPI path evidence
- design gate — balance proposal + FSM map ready for adversarial / collaborator review
- implementation gate — **deferred** until Marc go

## Exit criteria (for this draft TB)

1. Draft PR open against `main` with this brief + idea linked to #943.
2. Stakeholders can answer the open questions without reading the full mobile FSM.
3. Implementation does not start until Marc validates the balance point (or a trimmed variant).

## References

- Issue [#943](https://github.com/mgagp/ezkey/issues/943)
- Idea [`I-2026-10-20-demo-device-api-and-mobile-parity`](ideas/I-2026-10-20-demo-device-api-and-mobile-parity.md)
- Mobile FSM [`MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md)
- Mobile orientation [`MOBILE_APP_STRATEGIC_ORIENTATION.md`](../../../ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md)
- Demo Device notes [`ezkey-demo-device/AGENTS.md`](../../../ezkey-demo-device/AGENTS.md)
- Prior Demo Device TB [`TB-2026-06-18-demo-device-qr-auth-url-parity`](TB-2026-06-18-demo-device-qr-auth-url-parity.md)
