# Tracer Bullet Brief — `TB-2026-10-20-demo-device-api-and-mobile-parity` Demo Device API + mobile FSM parity

## Metadata

- **ID:** `TB-2026-10-20-demo-device-api-and-mobile-parity`
- **Status:** `under-review`
- **Related idea:** `I-2026-10-20-demo-device-api-and-mobile-parity`
- **GitHub issue:** `#943`
- **GitHub PR (analysis):** `#948`
- **Lane:** `D`
- **Posture:** `analysis-first` (this PR is doc-only; F1+F4 implementation is a follow-up)
- **Created at:** `2026-10-20`
- **Updated at:** `2026-10-20`
- **Captured by:** Marc
- **Reviews:** Alex (product), Julie (operability), Patrick (craft) — decisions locked below

## Objective

Specify a **pragmatic fidelity** for the Demo Device against Auth/Integration contracts and the
mobile FSM
([`MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md)): keep the
behaviour developers need for local MFA labs, leave out full mobile complexity, and lock error-copy
honesty to mobile catalog fidelity.

No product code in this analysis PR.

---

## Product posture (compass)

The Demo Device is **never** part of any official or community deployment. It is a **local
internal test tool**, run by a developer on their own workstation (CLI or Docker). What they enroll
and test only concerns them.

| Consequence | Posture |
| --- | --- |
| External evaluators | **Not** a Demo Device audience |
| Security-grade UI locks | **Not** required (no biometric gate, no Q10 decision-lock UX) |
| Error-copy honesty | **Required** — justified by **fidelity to the mobile app**, not by protecting external users |
| Language | **English only** |

Alex’s balance (locked): Demo Device = **“faithful device stub.”** Trace that phrase in this TB and
in a planned `ezkey-demo-device/AGENTS.md` section (kept vs omitted, #943).

---

## Target fidelity level

| Axis | Target |
| --- | --- |
| Role | Local internal workstation tool — not a product mobile client |
| Behaviour | **Faithful** on Auth API bind → verify → pending → respond and on HTTP/problem outcomes |
| Exhaustiveness | **Not** required: no biometrics, Keystore tiers, Settings hub, i18n, installation chrome, race-ref matrix |
| UI honesty | Expired / no longer active / 429 / 503 distinguishable **without** HTTP codes or server `detail` |
| Initiator clocks & verdicts | Demo Device duplicates **neither** the countdown **nor** the initiator’s wait verdict (Part D) |

---

## Surfaces in interaction (who initiates)

| Surface | Who initiates | What they watch |
| --- | --- | --- |
| Integrating app (Admin UI, Acme, PAM/SSH, SDK) | Developer starts login | Create + wait; `expiresAt` / countdown; wait verdict |
| Auth API | Device protocol | `pending` (PENDING→READ), `respond` (typed 409 / 503 / 429 / signed 200) |
| Integration API | Integrating app (API key) | create (+ `ttlSeconds`), wait, cancel |
| Demo Device | Developer / agent who opens the phone UI | Auth API **client** only |
| Mobile app (reference) | Same human role as Demo Device | Full FSM |
| Evaluator self-registration | Admin API / ezkey.org | **Out of scope** |

---

## Decisions (Marc — locked 2026-10-20)

| ID | Decision |
| --- | --- |
| **Q1 Leave-as-deny** | **Not implemented.** Document caveat in `AGENTS.md` + this TB. **No** on-screen leave-declines hint. **F2 is out.** |
| **409 terminal UI** | Final state after 409 expired / no longer active: **no Approve/Deny**, **Back only** — belongs to **F1** (not F5). Acceptance: late Approve → expired page with **no** buttons. |
| **503** | Keep claim and re-show the same pending card; retryable message **above** the buttons. Today the claim is cleared on entry to respond (`EzkeyAppController` ~L620) — **F1 must reorder**: clear claim only after a terminal outcome (200 decision, non-retryable 409); on **503** (and **429** on respond) **do not** clear / restore claim so Approve/Deny remain. |
| **Language** | English only. |
| **Budget** | **F1** (includes `e.getMessage()` leak removal from former F3: ~L178, L270, L485, L603, L780 + body echo L350–363) + **F4**. Defer remainder of F3. **Omit F5 / UncertainDecision.** Free-text challenge kept. Local TTL fallback + 409 net kept; **no** pending-wire `expiresAt`. |
| **#916** | Invalid device signature on respond → generic **409** `auth-attempt-state-conflict` shown as **no longer active**. **Forbid** any client-side differentiation of signature refusal. |
| **Pending “expired” inference** | Today pending infers expired from exception string (~L599–603). When message leaks are removed, **replace** with a typed signal (or drop the special-case); no string sniffing. |

---

## Part A — API contract inventory (evidence)

Specs: `specs/auth-api/openapi-spec.json`, `specs/integration-api/openapi-spec.json`.
Problem types: `ezkey-auth-api/.../AuthApiProblemCatalog.java`.

### A1. Auth `POST /api/v1/enrollments/bind`

| | |
| --- | --- |
| **API** | 200; **400**; **409**; **429** + `Retry-After`; **500**. OpenAPI L60–132. |
| **Demo Device** | 409/400 mapped; other statuses may echo body (`toBindErrorMessage` L350–363). No 429 catalog. |
| **Classification** | **Fix (F1)** — stop body echo; Julie 429 copy; no HTTP code in UI. |

### A2. Auth `POST /api/v1/enrollments/verify`

| | |
| --- | --- |
| **API** | 200; **400**; **409**; **429** + `Retry-After`; **500**. OpenAPI L134–206. |
| **Demo Device** | Broad catch with `e.getMessage()` (~L485); deletes local enrollment on failure. |
| **Classification** | **Fix (F1)** — remove `e.getMessage()` leak; calm status-based copy for 429. **Defer (rest of F3)** — full typed verify-409 polish beyond leak removal. |

### A3. Auth `POST /api/v1/auth-attempts/pending`

| | |
| --- | --- |
| **API** | 200 (no `expiresAt` in schema); **204**; **400**; **429**. OpenAPI L272–327 / schema L789+. |
| **Demo Device** | Session claim + local TTL fallback; optional parse if `expiresAt` present; string-sniff “expired” on exception (~L599–603); 429 opaque. |
| **Classification** | **Accept** no countdown / no pending-wire `expiresAt`. **Fix (F1)** — 429 copy; replace string sniff with typed signal when leaks removed. |

### A4. Auth `POST /api/v1/auth-attempts/respond`

| | |
| --- | --- |
| **API** | 200 signed APPROVED/DENIED/FAILED; **409** `auth-attempt-expired` (#895) or `auth-attempt-state-conflict` (#844 / **#916** signature refuse); **503** `integration-signing-unavailable`; **429**. OpenAPI L335, L368–401. |
| **Demo Device** | Typed expired vs generic 409; 503/429 → `"server returned {status}"`; claim cleared at respond entry (~L620). |
| **Classification** | **Fix (F1)** — Julie copy; 409 terminal Back-only (no buttons); 503 keep claim + reorder clear; 429 retryable banner; **never** split #916 from other state-conflict 409s. **Accept** defensive 200/`EXPIRED` branch. |

### A5. Integration create / wait / cancel

| | |
| --- | --- |
| **API** | `ttlSeconds`, wait `EXPIRED`, cancel → `EXPIRED`. Integration OpenAPI L29–35, L104–248. |
| **Demo Device** | Not a client. |
| **Classification** | **Out of scope** as client. Interaction: initiator owns countdown **and** wait verdict; device only shows its own respond outcome. |

### A6. Evaluator self-registration

**Out of scope** (Admin API / ezkey.org; not a Demo Device surface).

### A7. Related hygiene

| Issue | Note |
| --- | --- |
| #799 session cookies | Done |
| #753 log scrubbing | Keep invariant |
| #916 invalid respond signature | Generic 409 → **no longer active** only; no client differentiation |

---

## Part B — On-screen state comparison (locked)

| Moment | Mobile | Demo Device (target) | Posture |
| --- | --- | --- | --- |
| Pending Ready | Approve/Deny + leave-declines hint | Approve/Deny; **no** leave hint | **Accept** (Q1) |
| Leave / side-exit with claim | Leave → Deny | Leaves without respond; READ until TTL | **Accept** — document caveat |
| 409 expired | Distinct catalog; Back only | Expired page; **no** Approve/Deny; Back only | **F1** |
| 409 no longer active (incl. #916) | One sentence; Back only | Same; **no** buttons | **F1** |
| 503 | Retryable; keep attempt | Neutral banner + Retry; same card + buttons | **F1** |
| 429 | Retryable | Neutral banner + Retry | **F1** |
| UncertainDecision | Back only + lock | Absent | **Omit** |
| Challenge | 2-digit PIN | Free text | **Accept** |
| Phone countdown / initiator verdict | None / N/A | Neither duplicated | **Accept** (Part D) |

Julie copy (**no HTTP code, no server `detail`**):

| Case | Copy | Presentation |
| --- | --- | --- |
| Expired | `This request expired. Ask for a new sign-in.` | Result / terminal; Back only |
| No longer active | `This request is no longer active.` | Result / terminal; Back only |
| 429 | `Too many attempts. Try again in a few seconds.` | **Neutral** banner + Retry (not red error) |
| 503 | `Signing is temporarily unavailable. Try again.` | **Neutral** banner above Approve/Deny + Retry |

---

## Part C — FSM map (keep / simplify / omit)

Source: [`MOBILE_APP_STATE_MACHINE.md`](../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md).

### C1. Enrollment wizard

| Mobile | Demo Device | Rationale |
| --- | --- | --- |
| Scan / camera | **Simplified** — QR file/paste + manual | Local lab browser |
| Bind / verify ceremony | **Kept (simplified)** | Same two-step |
| Mid-flight back no-op / bind re-entry guards | **Omitted** | Form posts; no security-grade UI lock |
| Signed instance-info | **Omitted** | Not needed for MFA lab |

### C2. Detail → claim pending

| Mobile | Demo Device | Rationale |
| --- | --- | --- |
| User-initiated pull | **Kept** | Same posture |
| Fail-closed bad pending sig | **Kept (simplified)** | No Approve |
| Session claim rehydration | **Simplified** | Lab substitute for `initialAttempt` |
| Phone countdown / pending `expiresAt` | **Deliberately omitted** | #834 Q1; local fallback + 409 net |

### C3. Pending Auth

| Mobile | Demo Device | Rationale |
| --- | --- | --- |
| Approve / Deny | **Kept** | Core job |
| Leave-as-deny + hint | **Deliberately omitted** | Marc Q1; caveat in AGENTS.md |
| 409 expired / state-conflict | **Kept** (F1 terminal Back-only) | Mobile fidelity; #916 undifferentiated |
| 503 keep attempt | **Kept (simplified)** — F1 + clear-reorder | Patrick S2 |
| 429 retryable | **Kept (simplified)** — F1 | Rate-limit labs |
| UncertainDecision / biometric / decision-lock refs | **Deliberately omitted** | Internal tool; no security-grade UI lock |
| Free-text challenge | **Accept** | Lab console |

### C4. Cross-cutting

| Concern | Posture |
| --- | --- |
| No Integration cancel / Admin passwordless-cancel | **Kept** |
| Cloudflare edge catalog | **Accept** — no edge catalog in Demo Device |
| #916 signature refuse | **Must** map to **no longer active** only |

---

## Part D — Balance point (Alex — locked)

**Name:** Demo Device = **“faithful device stub.”**

### In

1. Auth ceremony: bind → verify → pending (sig verify) → respond (sig verify).
2. Read-once pending via session claim (local TTL fallback; no phone timer).
3. Distinguish **expired** vs **no longer active** on respond 409; terminal UI = Back only, no buttons.
4. Calm catalog for **429** and **503** (Julie copy); never echo `detail` or status codes in UI.
5. On **503**, same pending card + buttons (claim clear reordered).
6. Fail-closed on bad pending integration signature.
7. English only.

### Out

1. Leave-as-deny and any leave-declines hint (documented caveat instead).
2. Phone countdown; pending-wire `expiresAt`; **duplicating the initiator’s wait verdict**.
3. Biometrics, Keystore tiers, Q10 UncertainDecision, decision-lock refs.
4. MOB-015; signed instance-info branding path.
5. Integration API client; evaluator signup; Acme wait UX (#728).
6. Client-side differentiation of #916 signature refuse from other state-conflict 409s.
7. Exhaustive ProblemDetail `type` matrix for every bind/verify mode (beyond F1 leak removal).

### Planned AGENTS.md line (F4)

Record under `ezkey-demo-device/AGENTS.md`: **Faithful device stub (#943)** — short kept vs omitted
list pointing at this TB.

---

## Open questions (remaining)

None for Marc on the former Q1–Q6 set (all answered 2026-10-20).

Residual for F1 implementation design only (not blocking this analysis PR):

1. Exact typed signal shape for pending failures once `e.getMessage()` sniffing is removed (status +
   ProblemDetail `type` vs status alone) — Patrick S3 follow-through in the F1 PR.

---

## Fix groups and effort (implementation follow-up)

| Group | Contents | Effort | Status |
| --- | --- | --- | --- |
| **F1 — Error catalog + terminal 409 + 503 reorder** | Julie copy; no body echo / no `e.getMessage()` UI leaks (~L178, L270, L350–363, L485, L603, L780); 409 expired & no-longer-active **Back only / no buttons**; late-Approve acceptance test; 503 keep claim (reorder clear vs ~L620) + neutral banner; 429 neutral banner; pending typed failure (no string sniff); #916 undifferentiated | **1–1.5 days** | **Budget GO** |
| **F2 — Leave-as-deny lite** | — | — | **Out** (Marc Q1) |
| **F3 — Verify conflict polish (remainder)** | Broader typed verify-409 UX beyond leak removal | Small | **Deferred** |
| **F4 — Docs acceptances** | AGENTS.md leave caveat + faithful-device-stub kept/omitted; CONFIGURATION if needed; dual-repo sync note | Tiny | **Budget GO** |
| **F5 — UncertainDecision / extras** | — | — | **Omit** |

### Walk Gate before F1 merge (Julie)

Use [`ui-walk-gate.template.md`](../../templates/ui-walk-gate.template.md). Surface: Demo Device.
Locales: **EN only**. Asserts (binary):

| # | Assert | Must NOT |
| --- | --- | --- |
| 1 | Late Approve after TTL → expired page with Julie expired copy | Approve/Deny buttons; HTTP code; server `detail` |
| 2 | Respond 409 state-conflict (incl. #916-style) → no longer active; Back only | Buttons; distinct “bad signature” copy |
| 3 | Respond/pending **429** → neutral banner + Julie 429 copy + Retry | Red error style; status code in UI |
| 4 | Respond **503** → same pending card, message above buttons, Julie 503 copy + Retry | Cleared claim forcing empty pending; red error style |

Walk ID suggestion: `WALK-2026-10-20-demo-device-error-catalog` (fill when F1 PR exists).

---

## First executable slice (follow-up PR — not #948)

1. F1 in monorepo `ezkey-demo-device` + unit tests (incl. late Approve → expired, no buttons).
2. F4 AGENTS.md updates (caveat already started in analysis PR; complete stub list on F1 closeout if needed).
3. Walk Gate with Isabelle (or agent Walk) before F1 merge.
4. Sync standalone `mgagp/ezkey-demo-device` after monorepo merge.

## Rollback

- Analysis PR: revert docs.
- F1: independently revertable; must not break agent Approve → `demo-device-back-to-enrollments`.

## Critical flows (F1 acceptance)

| Flow | Expected |
| --- | --- |
| Late Approve after TTL | Expired page; Julie copy; **no** Approve/Deny |
| State-conflict 409 (incl. bad signature #916) | No longer active; Back only; **no** signature-specific copy |
| 503 signing unavailable | Same card + buttons; neutral 503 banner |
| 429 | Neutral banner + Retry |
| Side-exit with loaded claim | Leaves without Deny; initiator waits until TTL (**documented**) |

## Evidence plan

- [x] OpenAPI + controller citations
- [x] FSM keep/simplify/omit
- [x] Alex balance locked; Julie copy; Patrick S1/S2/S3
- [x] Marc decisions + budget F1+F4
- [ ] F1 implementation + Walk Gate (follow-up)

## Quality gates

- analysis gate — complete
- design gate — collaborator reviews + Marc decisions recorded
- implementation gate — F1+F4 follow-up after #948 merges or as a sibling PR

## Exit criteria (this analysis TB / #948)

1. Draft PR documents inventory, FSM map, locked balance, Julie copy, Walk Gate plan.
2. Answered questions removed; only residual F1 design note remains.
3. No product code; CI green; not merged from this agent.

## References

- Issue [#943](https://github.com/mgagp/ezkey/issues/943); PR [#948](https://github.com/mgagp/ezkey/pull/948)
- Idea [`I-2026-10-20-demo-device-api-and-mobile-parity`](ideas/I-2026-10-20-demo-device-api-and-mobile-parity.md)
- #895, #844, #834, #833, #916, #753, #799, #728
- Mobile FSM / orientation under `ezkey_mobile/docs/`
- Walk template [`ui-walk-gate.template.md`](../../templates/ui-walk-gate.template.md)
