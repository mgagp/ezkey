<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# #943 — Demo Device fidelity: Auth/Integration contracts and mobile FSM

| | |
| --- | --- |
| **Status** | Draft fidelity spec (analysis + target balance). Docs only — no product code in this PR. |
| **Issue** | [#943](https://github.com/mgagp/ezkey/issues/943) |
| **Scope extension** | Marc comment 2026-10-20: also confront Demo Device with the mobile app (on-screen representation + behaviour vs mobile FSM; #834 / PR #835). |
| **Baseline** | `main` @ `0fb6b14c` (posture inventory #984/#985). Re-check file:line if rebasing. |
| **Audience** | Alex (intent), Julie (surface operability), Patrick (craft); Marc for budget go/no-go after challenge. |

**Confidence labels** used on every inventory row:

| Label | Meaning |
| --- | --- |
| **confirmed** | Verified by reading code and/or OpenAPI on this baseline SHA. |
| **observed** | Stated in living docs / AGENTS / contract; not re-proven at runtime in this pass. |
| **assumed** | Inferred, leftover, or unclear without a live stack exercise. |

---

## 1. Surfaces in interaction (who initiates)

Name the whole Ezkey interaction before judging gaps. Demo Device is a **lab device client**, not the integrating app and not the production mobile binary.

| Surface | Who initiates | Role in this subject |
| --- | --- | --- |
| **Integrating application** (Admin UI passwordless login, Acme demos, PAM/SSH, SDK) | Operator or end-user of that app starts login / MFA | Creates the auth attempt (Integration or Admin create path); owns the **wait** countdown and cancel; shows wait errors (timeout, EXPIRED, 429, proxy cut). |
| **Integration API** | Integrating app (API key) | `POST /api/v1/auth-attempts`, `GET …/{id}/wait`, `POST …/{id}/cancel`. Demo Device **does not call** these. |
| **Auth API** | Device client (Demo Device or mobile) | Device bind / verify / pending / respond. Contract source: `specs/auth-api/openapi-spec.json`. |
| **Demo Device** (`ezkey-demo-device`) | End-user (or agent) who opens the lab phone UI and Approve/Deny | Simulates the phone: pull pending, decide, show result. Lab-only; private keys on disk. |
| **Mobile app** (`ezkey_mobile`) | End-user on a real device | Production-intent reference client; canonical FSM in `ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md` (#834 / #835). |
| **Admin UI** | Operator | Enrollment QR / bind material; passwordless wait UI; often the initiator Demo Device is paired with in clean-start. |

**Implication for wait errors (#943):** “wait errors” are **initiator-side**. Demo Device fidelity means correct **device-side consequence** when wait ends first (respond → **409** expired / conflict), not implementing Integration `/wait` inside Demo Device.

---

## 2. Target fidelity level (pragmatic balance)

**Goal (Marc):** get the most from the Demo Device tool **without** the full mobile complexity.

| Layer | Target | Not a target |
| --- | --- | --- |
| **Protocol / crypto** | Same bind/verify/pending/respond payloads and signature checks as Auth API + mobile (`CLIENT_BEHAVIOUR_CONTRACT.md`, signature payload docs). | Shipping Keystore / StrongBox / biometric presence. |
| **HTTP outcome mapping** | Branch on status + ProblemDetail **`type`**; map to a small lab catalog; never treat `detail` as product copy. Cover **409** (conflict vs expired), **503** signing-unavailable, and a minimal **429** story. | Full mobile i18n catalog; Cloudflare edge catalog; Retry-After UX polish. |
| **Pending / claim** | Keep pull model + session claim for read-once; rely on respond **409** as fail-closed net when claim TTL drifts. | Phone-side countdown; inventing security from local `expiresAt`. |
| **Screens** | Distinct idle / pending / approved / denied / failed / expired / clear error; stable `data-testid`s for agents. | Pixel parity with RN; navigation stacks; Settings / branding / instance-info chrome. |
| **FSM** | Honour contract MUST rules that Demo Device can express in a server-rendered flow (one-shot claim, fail-closed pending display, 409 finality, result-sig gate). | Full RN state machine: leave-as-deny, UncertainDecision chrome, biometric Q11, auto-load refs, FR copy. |

**One-line product stance:** Demo Device remains a **honest lab phone** for clean-start and agent handshakes — protocol-correct and outcome-clear — not a second React Native app.

---

## 3. Inventory — Auth API contract vs Demo Device

Auth endpoints Demo Device calls today (**confirmed** — `AuthApiService.java`):

| Method | Path | Call site |
| --- | --- | --- |
| `POST` | `/api/v1/enrollments/bind` | `AuthApiService.java:53–77` |
| `POST` | `/api/v1/enrollments/verify` | `AuthApiService.java:87–109` |
| `POST` | `/api/v1/auth-attempts/pending` | `AuthApiService.java:123–154` |
| `POST` | `/api/v1/auth-attempts/respond` | `AuthApiService.java:176–201` |

| # | Topic | Evidence | Confidence | Gap? |
| --- | --- | --- | --- | --- |
| A1 | Respond **409** `auth-attempt-expired` → expired UI | OpenAPI respond description (`specs/auth-api/openapi-spec.json` ~335–369); `EzkeyAppController.applyRespondConflictToModel` `797–815`; tests `EzkeyAppControllerRespondConflictTest` | **confirmed** | No — already typed |
| A2 | Respond **409** other / state-conflict → “no longer active” | Same `797–815`; mobile catalog `authApiProblem.ts:23–29`, `190–194` | **confirmed** | No for happy mapping; see G5 on `detail` elsewhere |
| A3 | Respond **503** `integration-signing-unavailable` | OpenAPI 503 on respond; mobile `SigningUnavailable` FSM `MOBILE_APP_STATE_MACHINE.md:200`, `236–238`; Demo maps non-409 to generic `"server returned " + status` (`814`) | **confirmed** | **Yes** — no typed retryable UX |
| A4 | **429** + `Retry-After` on bind/verify/pending/respond | OpenAPI 429 responses on those paths; Demo has no 429-specific branch (bind may echo raw body `350–363`) | **confirmed** | **Yes** — minimal typed handling missing |
| A5 | Pending **204** → empty / “no pending” | `AuthApiService.java:145–151`; controller `590–594` | **confirmed** | No |
| A6 | Pending wire omits `expiresAt`; local claim TTL fallback | `ClaimExpiresAtResolver`; `AGENTS.md:11–20`; contract Q1/Q14 `CLIENT_BEHAVIOUR_CONTRACT.md:53–58`; FSM timers `MOBILE_APP_STATE_MACHINE.md:270–277` | **confirmed** (code+docs) | Accept drift; rely on 409 — optional property align |
| A7 | Pending OpenAPI DTO omits `expiresAt` while Demo parses raw JSON | `AuthApiService.toPendingAuthPull` `162–166`; AGENTS note #834 | **confirmed** | Spec/DTO honesty — not Demo UX work unless product reopens Q1 |
| A8 | `POST …/enrollments/instance-info` unused | Auth OpenAPI path present; mobile uses for enrolled branding; Demo never calls | **confirmed** | **Accept / omit** for lab |
| A9 | Bind **400/409** actionable copy; other statuses echo **response body** | `toBindErrorMessage` `350–363` | **confirmed** | **Yes** — body echo violates G5 / AGENTS Cloudflare note for non-400/409 |
| A10 | Verify errors → delete enrollment + exception message (no typed ProblemDetail UX) | Controller verify catch path (~365–488 region); no status-specific catalog | **confirmed** | Partial — typed 409/429 worth a thin map; full mobile bind/verify catalog optional |
| A11 | Pending errors: generic `e.getMessage()`; leftover string match `"No pending authentication request"` | `EzkeyAppController.java:596–604` | **confirmed** | **Yes** — dead/misleading branch; no typed 429/4xx |
| A12 | Pending / respond integration signature verify | Pending `560–567`; respond result `688–714` | **confirmed** | Protocol OK; Q9 presentation differs (see M-gap) |
| A13 | Bind: no fail-closed `integrationKeyAlgorithm === ed25519`; no bind payload signature check | Mobile AGENTS / utils; Demo stores key from bind blindly | **observed** (mobile rules) / **assumed** Demo omission by design historically | **Accept** unless Alex wants lab fail-closed |

---

## 4. Inventory — Integration API / wait errors

| # | Topic | Evidence | Confidence | Gap? |
| --- | --- | --- | --- | --- |
| I1 | Demo Device does not call Integration create/wait/cancel | No Integration WebClient in module; only Auth `AuthApiService` | **confirmed** | **Out of role** — not a Demo Device code gap |
| I2 | Wait contract: 200 / 403 / 404 / 429 / 500; status may be EXPIRED | `specs/integration-api/openapi-spec.json` `/api/v1/auth-attempts/{id}/wait` `104–198` | **confirmed** | Belongs to Admin UI / Acme / SDK / PAM |
| I3 | Initiator wait ends → device respond sees **409** expired/conflict | #895 product path; Demo already maps typed expiry (A1) | **observed** (docs+code path) | Verify in E2E recipe; no Demo `/wait` implementation |
| I4 | Community proxy **524** vs 120s TTL | FSM `MOBILE_APP_STATE_MACHINE.md:274`; ENDPOINT wait notes | **observed** | Deployment note for initiators; omit from Demo Device |

---

## 5. Inventory — Mobile screens and FSM vs Demo Device

Canonical FSM: `ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md` (#834 / PR #835). Normative app-independent rules: `docs/CLIENT_BEHAVIOUR_CONTRACT.md`.

### 5.1 Screen / outcome mapping

| Mobile (Pending Auth / result) | Demo Device UI | Confidence | Notes |
| --- | --- | --- | --- |
| `Empty` + Check again | `auth.html` idle + “Check for Authentication Requests” | **confirmed** | Pull model aligned |
| `Ready` Approve/Deny | `auth_pending.html` | **confirmed** | No leave-declines permanent line (Q5) |
| `Completed` → Detail badge | `auth_result.html` success/denied | **confirmed** | Lab stays on result page — acceptable |
| `NoLongerActive` (409 conflict) | `auth_result` failed + MSG_AUTH_NO_LONGER_ACTIVE | **confirmed** | Shape OK |
| Expired (409 `auth-attempt-expired`) | `auth_result` expired + MSG_AUTH_EXPIRED | **confirmed** | Shape OK |
| `SigningUnavailable` (503) | Generic failed + status code | **confirmed** | Gap A3 |
| `GlobalError` retryable (429, network) | Generic error strings | **confirmed** | Gap A4 / A11 |
| `UncertainDecision` (Q10) | No distinct state; transport errors → failed message | **confirmed** | Propose **simplify/omit** or thin “uncertain” copy |
| Result sig fail after 200 (Q9) | `failed` + tampering / missing signature messages `697–714` | **confirmed** | Shows as failed, not Back-only lock chrome — **simplify** |
| Leave-as-deny (Q5) | Side exit / back without auto-deny | **confirmed** | **Omit** for lab (agent recipe uses explicit Deny or Approve) |
| Enrollment wizard states | Manual/QR bind → challenge → verify templates | **confirmed** | Coarse parity enough |
| Instance branding / Settings | Absent | **confirmed** | **Omit** |

### 5.2 FSM / contract behaviours

| Rule / state | Mobile | Demo Device | Confidence | Spec disposition |
| --- | --- | --- | --- | --- |
| One-shot pending claim | Yes | Session claim after verified pending `510–587` | **confirmed** | Keep |
| Fail-closed pending display (Q4) | Yes | Invalid sig → error, no Approve `565–567` | **confirmed** | Keep |
| No phone countdown (Q1/Q14) | Yes | No countdown UI; local claim TTL only | **confirmed** | Keep; document drift |
| Decision lock after respond (Q2/Q10/Q13) | Refs in `usePendingAuth` | Clears claim at respond start `619–620`; no id lock | **confirmed** | **Simplify**: optional soft lock / disable double-submit; full UncertainDecision **omit** |
| Leave-as-deny (Q5) | `beforeRemove` | Not implemented | **confirmed** | **Omit** |
| 409 finality (Q8 / #895) | Back only | Result page expired / no-longer-active | **confirmed** | Keep (already) |
| 503 retryable (#844) | Try again keeps attempt | Generic status | **confirmed** | **Fix** thin catalog |
| 429 retryable | Catalog retryable | Untyped | **confirmed** | **Fix** thin catalog |
| Biometric Approve-only (Q11) | Decided; app may lag | N/A | **observed** | **Omit** |
| `integrationKeyAlgorithm` fail-closed | Required | Not checked | **observed** | **Accept / omit** unless intent changes |

---

## 6. Gaps to fix (proposed in-scope for a later implementation PR)

Ordered for value vs complexity. Implementation is **out of scope for this docs PR**.

| Priority | ID | Fix | Why |
| --- | --- | --- | --- |
| P0 | F1 | Typed respond **503** `integration-signing-unavailable` → distinct retryable lab message (and keep pending claim or allow re-show Approve/Deny without implying decision committed) | Matches OpenAPI + mobile; today’s generic status misleads agents |
| P0 | F2 | Stop echoing raw ProblemDetail / edge **bodies** into bind (and verify/pending) UI; map status+`type` → local strings only | G5 / `CLIENT_BEHAVIOUR_CONTRACT.md` §7; AGENTS Cloudflare note |
| P0 | F3 | Remove or replace dead pending catch matching `"No pending authentication request"` (`596–604`) | Misleading expiry copy on unrelated failures |
| P1 | F4 | Minimal **429** handling on bind/verify/pending/respond: local “too many requests; wait and retry” (Retry-After parse **optional**) | OpenAPI documents 429; agents hit rate limits in churn |
| P1 | F5 | Thin ProblemDetail helper shared by bind/verify/pending/respond (parse `type` only; never show `detail`) | Same pattern as `problemTypeFromError` `293–314`, extended |
| P2 | F6 | After HTTP 200 with missing/bad result signature: prefer Q9-ish copy (“response sent; confirmation not verified; check the sign-in”) over generic “failed”, and discourage a second decision for that attempt id | Contract §6; today `697–714` is closer to “failed” |
| P2 | F7 | Document + optional unit/controller tests for F1–F4; extend agent validation recipe for expiry and 409 paths | Discoverability for cold agents |

---

## 7. Gaps to accept or omit

| ID | Item | Disposition | Rationale |
| --- | --- | --- | --- |
| O1 | Integration `/wait` / create / cancel inside Demo Device | **Omit** | Wrong surface; initiator owns wait |
| O2 | `enrollments/instance-info` + branding chrome | **Omit** | Lab does not need enrolled installation branding |
| O3 | Leave-as-deny + permanent Q5 line | **Omit** | Agent/human lab uses explicit Approve/Deny; server TTL covers abandon |
| O4 | Full RN `UncertainDecision` / biometric / i18n / Settings | **Omit** | Full mobile complexity |
| O5 | Phone countdown / forcing `expiresAt` on pending wire | **Omit** (product lock Q1/Q14) | Initiator owns timer |
| O6 | Local claim TTL vs initiator TTL drift | **Accept** with 409 fail-closed | Already documented in `ezkey-demo-device/AGENTS.md` |
| O7 | Bind algorithm / bind-signature fail-closed | **Accept** by default | Lab trust posture; reopen only if Alex wants parity |
| O8 | Proxy 524 / community edge catalogues | **Omit** from Demo Device | Initiator / edge operator docs |
| O9 | Exact RN visual design | **Omit** | Stable testids + clear outcomes suffice |

---

## 8. FSM edge cases — simplify or omit

| Edge (mobile FSM) | Spec for Demo Device |
| --- | --- |
| Leave during in-flight Approve | **Simplify**: disable double-submit on respond form; no competing auto-deny |
| App kill during respond (Q12) | **Omit** (accepted limit; stranded READ until TTL) |
| Ambiguous 5xx after send (contract SHOULD) | **Simplify**: one “uncertain — check the sign-in” message; no second respond button for that attempt if cheap; else rely on server 409 |
| New attempt id after lock (Q13) | **Simplify**: only needed if F6 adds an id lock; otherwise session claim already scopes per enrollment |
| Challenge required + Deny | Keep current: Deny without challenge (aligns with mobile/server) — **confirmed** desirable |
| Auto-load pending on mount | **Omit**: explicit Check button is clearer for agents |

---

## 9. Size estimate and proposed implementation order

No calendar estimate. Technical size if Marc budgets an implementation follow-up:

| Slice | Touch surface | Relative size |
| --- | --- | --- |
| **S1** — ProblemDetail lab catalog + stop body echo (F2, F3, F5) | `EzkeyAppController` (+ small helper), templates/messages, unit tests | **S** — localized controller/strings |
| **S2** — Respond 503 + 429 thin UX (F1, F4) | Same + `auth_result` / `auth` flags; respond conflict tests extended | **S** |
| **S3** — Q9-ish result-sig presentation (F6) | Respond success path messaging; optional attempt-id soft lock | **S–M** |
| **S4** — Agent/docs verification recipe (F7) | `ezkey-demo-device/AGENTS.md`, `docs/testing/AGENT_UI_VALIDATION.md` or sibling | **S** docs/tests |

**Proposed order:** S1 → S2 → S4 (verification early) → S3. Do **not** stack separate PRs for S1–S4 unless Marc splits budget; prefer one implementation PR after go.

**Dependencies:** none on Integration API Demo Device work. Soft dependency on living Auth OpenAPI already documenting 409/503/429 (#844/#895). Pending `expiresAt` on wire is **not** required (Q1 lock).

---

## 10. How to verify (clean-start + Demo Device; agent-runnable)

Discoverability: this section + pointer from `ezkey-demo-device/AGENTS.md`. Full Admin UI ↔ Demo Device handshake remains in [`docs/testing/AGENT_UI_VALIDATION.md`](../testing/AGENT_UI_VALIDATION.md) § Step 4.

### 10.1 Baseline (today and after implementation)

1. From `ezkey-tests/`, run the standard **clean-start** stack (empty DB, default Docker parameters).
2. Confirm Demo Device and Auth API healthy per port table in `docs/LOCAL_STACK_PORTS.md` / Agent UI validation doc.
3. **Happy path (agent-runnable):** Admin UI passwordless login for bootstrap Global Admin → Demo Device enrollment card → Check until Approve/Deny → Approve → success testid → Back to enrollments. Mirror: Playwright `ezkey-admin-ui/e2e/support/auth-flow.ts`.
4. **Unit (no stack):** `mvn test -pl ezkey-demo-device` focusing on `EzkeyAppControllerRespondConflictTest` and any new catalog tests.

### 10.2 Contract / expiry / wait-consequence checks (add with implementation)

| Check | Who initiates | Expected on Demo Device |
| --- | --- | --- |
| Approve while Admin wait still live | Operator starts login; device approves | Success result; Admin proceeds |
| Wait until attempt expires, then Approve | Operator lets wait/TTL finish; device still shows Approve if claim TTL longer | Respond **409** expired UI (`demo-device-result-expired`) — fail-closed net |
| Cancel from initiator, then Approve | Operator cancels; device Approve | **409** no-longer-active (conflict) |
| Deny | Device Deny | Denied result; initiator unblocks rejected |
| (After F1) Forced 503 path | Lab/fixture or fault injection if available | Retryable signing-unavailable copy — not generic “failed” alone |
| (After F2) Bind with non-400/409 error | Invalid host / forced error | Local string only — **no** raw JSON/`detail` on screen |

### 10.3 Out of scope for Demo Device verification

- Integration `/wait` 429/404/403 bodies on the device UI.
- Mobile Maestro / RN FSM instrumentation (reference only).
- Community edge **524** behaviour.

---

## 11. Open questions (do not post review requests from this PR)

Leave for human challenge; answers gate Marc’s budget go/no-go.

### Alex — intent

1. Is the target fidelity in §2 the right balance (honest lab phone, not second mobile app)?
2. Should bind/verify fail-closed on `integrationKeyAlgorithm` (O7) move into fix scope, or stay accepted?
3. Is leave-as-deny (O3) permanently omitted for Demo Device, or a later nice-to-have?

### Julie — surface operability

1. Are expired vs no-longer-active vs success/denied screens clear enough for operators and cold agents with current copy?
2. For 429/503, is a single English lab sentence + “try again / check again” enough (no Retry-After countdown)?
3. Does the agent handshake doc need a dedicated “expiry / cancel then Approve” subsection in `AGENT_UI_VALIDATION.md`, or is §10 here enough once linked?

### Patrick — craft

1. Prefer extending `problemTypeFromError` into a tiny shared catalog helper vs ad-hoc `if (status==503)` branches?
2. On Q9 (bad result sig after 200): keep showing `auth_result` with distinct copy (F6), or force idle + lock — what’s the smallest honest craft?
3. Any objection to accepting pending claim TTL drift (O6) indefinitely while OpenAPI omits `expiresAt`?

---

## 12. Completeness for development start

| Ready? | Notes |
| --- | --- |
| **Almost** — enough to challenge and budget | Inventory covers Auth HTTP outcomes named in #943, Integration wait role split, and mobile FSM confront (#834/#835) with file:line on this baseline. |
| **Missing before coding** | (1) Answers to §11 open questions; (2) Marc go/no-go on S1–S4 budget; (3) optional adversarial critique pass (issue step 3) on this draft; (4) re-cite file:line if `main` moved. |
| **Not missing for analysis** | Live Docker proof of every HTTP code (analysis is code/OpenAPI-backed; runtime matrix is verification for the implementation PR). |

**Next methodology steps (issue #943):** collaborators refine → adversarial critique → Marc go/no-go → **one** implementation PR (not this draft).

---

## Related documents

| Doc | Role |
| --- | --- |
| [`ezkey-demo-device/AGENTS.md`](../../ezkey-demo-device/AGENTS.md) | Living Demo Device agent notes; claim expiry; QR parity |
| [`docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../CLIENT_BEHAVIOUR_CONTRACT.md) | App-independent MUST/SHOULD (includes Demo Device) |
| [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) | Canonical mobile FSM (#834/#835) |
| [`docs/testing/AGENT_UI_VALIDATION.md`](../testing/AGENT_UI_VALIDATION.md) | Clean-start agent handshake |
| [`docs/analysis/844-unsigned-error-responses.md`](844-unsigned-error-responses.md) | 409/503 respond catalog decisions |
| OpenAPI | `specs/auth-api/openapi-spec.json`, `specs/integration-api/openapi-spec.json` |
