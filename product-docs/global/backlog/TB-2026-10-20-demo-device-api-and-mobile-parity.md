# Tracer Bullet Brief — `TB-2026-10-20-demo-device-api-and-mobile-parity` Demo Device API + mobile FSM parity

## Metadata

- **ID:** `TB-2026-10-20-demo-device-api-and-mobile-parity`
- **Status:** `under-review` → implementation on PR `#948`
- **Related idea:** `I-2026-10-20-demo-device-api-and-mobile-parity`
- **GitHub issue:** `#943` (single owner; duplicate `#1081` closed)
- **GitHub PR:** `#948`
- **Lane:** `D`
- **Created at:** `2026-10-20`
- **Updated at:** `2026-10-28`
- **Captured by:** Marc
- **Reviews:** Alex (product), Julie (operability), Patrick (craft) — 2026-10-20 locks + 2026-10-28 craft adjustments

## Objective

Ship a **faithful device stub**: Demo Device behaves correctly on the Auth API device path and
matches mobile outcome honesty where it matters for local labs, without cloning full mobile
complexity. This TB is the **single owner doc** for #943.

---

## Product posture (compass)

The Demo Device is **never** part of any official or community deployment. It is a **local
internal test tool** on a developer workstation (CLI or Docker). What they enroll and test only
concerns them.

| Consequence | Posture |
| --- | --- |
| External evaluators | Not a Demo Device audience |
| Security-grade UI locks | Not required |
| Error-copy honesty | Required — **fidelity to the mobile app** |
| Language | **English only** |

Alex balance (locked): **“faithful device stub.”**

---

## Marc locks (2026-10-20 — unchanged)

| Lock | Content |
| --- | --- |
| Leave-as-deny | **Not implemented**; no on-screen hint; **F2 out** |
| 409 terminal | Back only, no Approve/Deny — **in F1** |
| 503 | Keep claim; re-show same card; message above buttons |
| Budget | **F1 + F4**; defer rest of F3; **F5 omitted** |
| Pending `expiresAt` | **No** wire field; local TTL fallback + typed 409 net |
| Challenge | Free text |
| English | Only |

---

## Julie locked copy (no HTTP code, no server `detail`)

| Outcome | On-screen text |
| --- | --- |
| Expired | `This request expired. Ask for a new sign-in.` |
| No longer active | `This request is no longer active.` |
| Rate limited (429) | `Too many attempts. Try again in a few seconds.` |
| Signing unavailable (503) | `Signing is temporarily unavailable. Try again.` |
| Unconfirmed (Q9) | `Response sent, confirmation not verified; check the connection on the app side.` |

Presentation:

- Expired / no longer active → `auth_result` terminal; **Back only**; no Approve/Deny.
- No longer active → distinct **neutral grey** state titled **Request no longer active** (own
  `data-testid`); not orange “Authentication Failed”.
- 429 / 503 on respond → **neutral** banner on `auth_pending.html` (message slot); Approve/Deny stay.
- Unconfirmed → distinct `unconfirmed` state on `auth_result` (own `data-testid`); not `failed`.

---

## Settled: initiator cancel → device respond (open point closed)

**Truth (code + Integration API):**

1. Integration `POST …/auth-attempts/{id}/cancel` marks the row **`EXPIRED`** (cancel and clock
   expiry share that status on the initiator side — Integration OpenAPI cancel description).
2. Device `POST …/respond` against a non-`READ` status (including cancel-`EXPIRED`, already decided,
   superseded) → **409** `auth-attempt-state-conflict` (`AuthAttemptRespondService` terminal branch).
3. Wall-clock refuse while still `READ` after device ownership → **409** `auth-attempt-expired`
   (`AuthAttemptExpiredException` / #895).

**Demo Device UI:** initiator cancel → **no longer active** (Julie). Only typed
`auth-attempt-expired` → **expired** copy. Do not treat Integration wait status `EXPIRED` as the
device expired screen — Demo Device does not call wait and does not duplicate the initiator verdict.

Evidence: `AuthAttemptRespondService.validateAndGetAttempt` (terminal → state-conflict);
`ensureNotExpired` (wall-clock → expired); Integration cancel OpenAPI “status EXPIRED”.

---

## Patrick craft adjustments (2026-10-28)

| Item | Spec |
| --- | --- |
| Outcome catalog | Package-private enum `AuthApiOutcome` (`EXPIRED`, `NO_LONGER_ACTIVE`,
  `SIGNING_UNAVAILABLE`, `RATE_LIMITED`, `UNCONFIRMED`, `HTTP_OTHER`, `TRANSPORT`) + pure
  `classify(statusCode, problemType)` next to `problemTypeFromError`; one message constant per
  value; tolerate absent/old type. Names aligned with mobile `authApiProblem.ts`. **No** catalog
  class / interface. |
| Q9 unconfirmed | Stay on `auth_result`; replace “possible tampering” with Julie unconfirmed text;
  distinct `unconfirmed` model flag + `data-testid`; merge ambiguous 5xx / transport-after-send into
  same `UNCONFIRMED`. **Drop soft id lock (F6).** |
| Claim TTL drift | Acceptable if: 409 stays typed; **automated** test “claim still valid, attempt
  expired initiator-side → expired screen”; name `ClaimExpiresAtResolver` in
  `ezkey-demo-device/AGENTS.md` with reopen trigger (if `expiresAt` ever enters the pending wire). |
| Claim clear | Must **not** clear before terminal outcome (200 decision or non-retryable 409). Move
  clear after that. 503/429 keep claim. |
| Slices | One commit **respond outcomes** (respond 503/429 + Q9/unconfirmed + clear reorder). Bind /
  verify / pending 429 + leak removal in F1 commit(s). |
| Leaks | Remove pending `"Authentication check failed: " + e.getMessage()`; respond `e.getMessage()`
  / HTTP code displays; confirm by test that no URL/host/method reaches the screen. |
| #916 | Invalid signature → generic 409 → **no longer active**; forbid client differentiation. |

---

## Alex product decisions (one line each, #943)

| Decision | Reason | # |
| --- | --- | --- |
| Fail-closed `integrationKeyAlgorithm == ed25519` at bind (one comparison) | Cheap parity with mobile; refuse wrong algo before persist | #943 |
| Bind payload signature verification **accepted deferred** | Not equally cheap (multi-field NFC payload builder); reopen if an external evaluator uses Demo Device as a real approver | #943 |
| Leave-as-deny **omitted for good** | Internal lab tool; reopen if Demo Device becomes a real human approver | #943 |

---

## Surfaces in interaction

| Surface | Initiator | Watches |
| --- | --- | --- |
| Integrating app | Developer login | Countdown + wait verdict |
| Auth API | Protocol | pending / respond |
| Demo Device | Developer / agent | Approve/Deny UI |
| Mobile | Same role | Full FSM (reference) |

Demo Device duplicates **neither** the countdown **nor** the initiator verdict.

---

## Fix groups

| Group | Scope | Effort |
| --- | --- | --- |
| **F1** | Outcome enum/classify; Julie copy; 409 terminals; 503/429 respond banner + claim clear reorder; Q9 unconfirmed; bind/verify/pending 429; leak removal; ed25519 bind check; automated expired-with-live-claim test; no URL/host/method on screen | 1–1.5 days |
| **F4** | AGENTS.md durable rules; ClaimExpiresAtResolver note; AGENT_UI_VALIDATION expiry/cancel subsection + Related docs; root AGENTS.md Demo Device routing line; Walk Gate cite | Tiny |
| F2 / F5 / F6 | Out / omit / drop | — |

### Walk Gate (before treating F1 UI as human-validated)

Template: [`product-docs/templates/ui-walk-gate.template.md`](../../templates/ui-walk-gate.template.md).
EN only. Include **Approve at ~5 s** and **Approve after the TTL**. Asserts: expired, no longer
active, 429, 503. **Isabelle runs the Walk after this PR** — this PR must not claim Walk done.

---

## FSM keep / simplify / omit (summary)

Kept: bind→verify→pending→respond; fail-closed bad pending sig; typed expired vs no longer active;
503 keep claim; pull model.

Simplified: free-text challenge; session claim; outcome enum (not mobile i18n catalog).

Omitted for good: leave-as-deny; phone countdown; UncertainDecision soft lock; biometrics; bind
payload sig (deferred); pending-wire `expiresAt`.

---

## References

- Issue [#943](https://github.com/mgagp/ezkey/issues/943); PR [#948](https://github.com/mgagp/ezkey/pull/948)
- Mobile FSM / `authApiProblem.ts`; Auth `AuthAttemptRespondService`; Integration cancel OpenAPI
- `ezkey-demo-device/AGENTS.md`; `docs/testing/AGENT_UI_VALIDATION.md`
