<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# #1082 — Mobile code-entry keyboard UX (6-digit enrollment, 2-digit auth)

**Status:** analysis only — awaiting Marc go / no-go. No product code in this change set.  
**Issue:** [#1082](https://github.com/mgagp/ezkey/issues/1082).  
**Evidence basis:** static read of React Native source on `main` (and this analysis branch). **Not** re-run on a physical Pixel in this pass unless noted. Marc’s original observation (Pixel 7 Pro) is treated as the UX problem statement.  
**Vocabulary:** enrollment FSM states `DraftReady` / `Verifying` / `ChallengeError` and auth FSM state `Ready` / `Responding` from [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) (#834 / #835).  
**Out of scope:** challenge length, Auth API / protocol semantics, security behaviour of bind / verify / respond.

---

## 1. Surfaces in interaction

| Surface | Who initiates | Role in this UX |
| --- | --- | --- |
| Mobile app — Enrollment Wizard (verify stage) | End-user | Enters 6-digit enrollment challenge; taps primary **Complete enrollment** |
| Mobile app — Pending Auth (challenge required) | End-user | Enters 2-digit auth challenge; taps **Approve** (or **Deny**, which does not require digits) |
| Auth API (`verify` / `respond`) | Mobile app | Unchanged by any keyboard UX fix |
| Integrating app / Admin UI (shows the code) | Operator or end-user starting enrollment or login | Displays the challenge the phone must type; not in scope for layout |

Problem symptom (Marc): with the virtual keyboard open, the primary action sits just above the keyboard; the first tap often only dismisses the keyboard instead of completing Approve / Complete.

---

## 2. Current behaviour (verified from code)

### 2.1 Shared input — `PinCodeInput`

File: [`ezkey_mobile/app/components/PinCodeInput.tsx`](../../ezkey_mobile/app/components/PinCodeInput.tsx).

| Concern | Current code | Lines |
| --- | --- | --- |
| Pattern | Segmented digit boxes + hidden full-overlay `TextInput` | 111–139 |
| Focus | Outer `Pressable` calls `inputRef.current?.focus()` when editable | 112–114 |
| Keyboard | `keyboardType="number-pad"` | 132 |
| Length cap | `maxLength={length}` (6 or 2 from callers) | 133 |
| Filtering | `onChangeText` strips non-digits; paste truncates to `length` | 83–90 |
| Autofill / OTP hints | **None** — no `textContentType`, `autoComplete`, `autoCorrect`, `importantForAutofill` | — |
| IME Done / submit | **None** — no `returnKeyType`, `blurOnSubmit`, `onSubmitEditing` | — |
| Auto-dismiss at full length | **None** — filling `maxLength` does not blur or call `Keyboard.dismiss` | — |
| Accessibility | Wrapper `accessibilityLabel` / `accessibilityHint` (i18n from callers); digit boxes are visual only | 116–117 |
| Automation | Optional `testID` on the visible `Pressable` | 113 |

**Already digits-oriented.** Option “(b) numeric keyboard” from the issue is largely already shipped for both flows via this shared component. Remaining caveats are platform IME details (see §3.b), not “switch away from a full QWERTY.”

There is **no** dedicated Jest component test file for `PinCodeInput` today; length guards live in hooks (`useEnrollmentWizard`, `usePendingAuth`).

### 2.2 Enrollment — 6-digit challenge (`DraftReady`)

Files:

- Screen: [`EnrollmentWizardScreen.tsx`](../../ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx)
- Hook: [`useEnrollmentWizard.ts`](../../ezkey_mobile/app/hooks/useEnrollmentWizard.ts)
- FSM: `DraftReady` → primary + 6 digits → `Verifying` ([`MOBILE_APP_STATE_MACHINE.md`](../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) enrollment table)

| Concern | Current behaviour | Lines |
| --- | --- | --- |
| Layout shell | `KeyboardAvoidingView` (`behavior` iOS `padding` / Android `height`, Android offset 20) | 183–187 |
| Scroll | `ScrollView` with `keyboardShouldPersistTaps="handled"` | 188–192 |
| Challenge UI | `PinCodeInput` `length={6}`, centered, gap 8; editable when not submitting | 257–268 |
| Primary / secondary | Sticky footer **outside** the `ScrollView` (sibling under `KeyboardAvoidingView`) | 288–303 |
| Primary label | “Complete enrollment” / “Finishing…” (not “Accept”) | hook 590–593 |
| Primary disabled | Yes when challenge length ≠ 6 (`challengeMissing`), or while binding/submitting | hook 586–588 |
| Keyboard dismiss | No explicit dismiss on Complete; no blur handler on `PinCodeInput` | — |
| Android soft input | Activity `windowSoftInputMode="adjustResize"` | [`AndroidManifest.xml`](../../ezkey_mobile/android/app/src/main/AndroidManifest.xml) L20 |

**Implication:** enrollment already combines KAV + sticky footer + `keyboardShouldPersistTaps="handled"` on the scroll body. Sticky actions are **outside** the `ScrollView`, so persist-taps only covers taps on scroll content; footer taps depend on OS / RN delivering the press while the IME is focused. Marc still reported the two-step dismiss on Pixel — treat enrollment as **possibly still affected** until a device walk confirms whether Complete receives the first press or only the keyboard collapses.

### 2.3 Authentication — 2-digit challenge (`Ready` when `challengeRequired`)

Files:

- Screen: [`PendingAuthScreen.tsx`](../../ezkey_mobile/app/screens/PendingAuth/PendingAuthScreen.tsx)
- Hook: [`usePendingAuth.ts`](../../ezkey_mobile/app/hooks/usePendingAuth.ts) (`AUTH_CHALLENGE_LENGTH = 2` at L52–53)
- FSM: `Ready` — optional 2-digit challenge; Approve guard length === 2 ([state machine](../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) Approve / Deny table)

| Concern | Current behaviour | Lines |
| --- | --- | --- |
| Layout shell | Plain `View` — **no** `KeyboardAvoidingView` | 124 |
| Scroll | `ScrollView` for the attempt card — **no** `keyboardShouldPersistTaps` (RN default **`never`**) | 231–234 |
| Challenge UI | `PinCodeInput` with `length={AUTH_CHALLENGE_LENGTH}`, `autoFocus` | 252–265 |
| Actions | Approve / Deny **inside** the scroll card, below the PIN | 274–297 |
| Approve disabled | Only `disabled={isProcessing}` — **not** disabled when digits incomplete | 285–296 |
| Incomplete Approve | Hook sets form error, no API; does **not** dismiss keyboard before that guard | hook 473–485 |
| Keyboard dismiss | `Keyboard.dismiss()` only after challenge guard passes, at start of respond | hook 492 |
| Leave-as-deny | Unrelated to keyboard; `#831` / `#833` leave path | screen 87–105 |

**Implication (strong code-level match to the reported bug):** with default `keyboardShouldPersistTaps="never"`, the first tap on Approve / Deny while the IME is open is consumed to dismiss the keyboard and **does not** fire `onPress`. That is the classic React Native “two-step” failure mode Marc described. Auth is the clearer structural gap than enrollment.

### 2.4 When the keyboard dismisses today

| Trigger | Enrollment | Pending Auth |
| --- | --- | --- |
| Tap outside field on scroll content | Persist taps = `handled` → child handlers run; blur/dismiss depends on whether focus moves | Default `never` → first tap often dismisses only |
| System / IME Done | `number-pad` typically **has no Done** on iOS; Android may vary by OEM keyboard | Same shared input |
| Full length reached | Does **not** auto-blur | Does **not** auto-blur (`autoFocus` keeps focus on entry) |
| Successful / started submit | No explicit dismiss on Complete | `Keyboard.dismiss()` once respond proceeds (after length OK) |
| Tap digit boxes again | `Pressable` refocuses hidden `TextInput` | Same |

### 2.5 FSM touchpoints (#834 / #835)

Keyboard UX must not invent new protocol states. Relevant existing states only:

| Flow | State | What the user is doing with the keyboard |
| --- | --- | --- |
| Enrollment | `DraftReady` | Entering 6 digits; primary enabled only when complete |
| Enrollment | `Verifying` | Submitting; input not editable |
| Enrollment | `ChallengeError` | Correct / retry digits |
| Auth | `Ready` (+ `challengeRequired`) | Entering 2 digits; Approve may be pressed early → form error |
| Auth | `Responding` | Buttons disabled; keyboard already dismissed if respond started |

No change to leave-as-deny, Q8/Q10 Back-only states, or challenge semantics.

---

## 3. Options and trade-offs

### (a) Button state / color / label hint while keyboard is open

**Idea:** Visual two-step cue (e.g. muted Approve until keyboard hidden, or label “Dismiss keyboard” → “Approve”).

| Pros | Cons |
| --- | --- |
| Makes the intermediate step explicit | Still **two** physical actions; teaches a workaround rather than removing friction |
| Small visual-only change possible | Needs reliable keyboard show/hide listeners (`Keyboard` API); flicker risk on Android |
| | Accessibility: color-only cues fail WCAG without text / state change |

**Fit:** Weak as sole fix. Useful as a **secondary** cue if we keep an explicit dismiss step (not recommended).

### (b) Digits-only keyboard (`number-pad` / `numeric`)

**Status:** **Already implemented** on `PinCodeInput` (`keyboardType="number-pad"`).

| Remaining caveats | Notes |
| --- | --- |
| iOS `number-pad` has **no Done / Return** | User cannot dismiss via IME key; must tap outside, Back, or we auto-dismiss / handle Approve |
| Android OEM keyboards | Usually digit pad; some show a dismiss affordance |
| `textContentType="oneTimeCode"` / `autoComplete="sms-otp"` | **Not appropriate** for Ezkey: challenge is shown on Admin / integrating login UI, not delivered by SMS. Enabling SMS autofill could confuse OS heuristics and is a security/product smell |
| `keyboardType="numeric"` vs `number-pad` | `numeric` may show extra punctuation on some platforms; keep `number-pad` |

**Fit:** Keep as-is. Do not chase SMS OTP autofill for this challenge.

### (c) Auto-dismiss at full length (6 or 2)

**Idea:** When `value.length === length`, blur / `Keyboard.dismiss()` so the sticky or in-card actions are fully visible.

| Pros | Cons |
| --- | --- |
| Matches many banking / OTP UIs | User mid-correction: last digit commits dismiss — surprising if they meant to edit |
| Reveals Approve / Complete without an extra tap | Mitigation needed: tap field to reopen (already supported via `Pressable`); optional “Edit” is extra chrome |
| Tiny change inside `PinCodeInput` benefits both screens | Does **not** alone fix first-tap swallow on Pending Auth if the user taps Approve **before** the 2nd digit (and auth Approve stays enabled) |

**Mitigations if chosen:**

1. Tap segmented boxes → refocus (already there).
2. Backspace / delete-last still works while focused; after dismiss, one tap reopens then delete.
3. Prefer dismiss only when length reaches max **and** previous length was shorter (edge: paste of full code).

**Fit:** Strong **complement** to (d), especially for enrollment sticky footer visibility under `adjustResize`. Not sufficient alone for Pending Auth’s persist-taps gap.

### (d) Single-step Approve / Complete while keyboard is open

**Idea:** With a complete code, one tap on the primary action dismisses the keyboard **and** submits. Require `keyboardShouldPersistTaps="handled"` so the first tap is not swallowed. Disable primary until length is complete (enrollment already does; auth does not).

| Pros | Cons |
| --- | --- |
| Directly matches Marc’s pain (“I reach for Approve”) | Must set persist-taps on **Pending Auth** `ScrollView` (missing today) |
| Enrollment already dismisses-on-respond pattern exists for auth (`Keyboard.dismiss` in `handleRespond`) | Footer-outside-scroll on enrollment still needs Pixel confirmation |
| Aligns auth Approve enablement with enrollment Complete | Deny must remain tappable without digits (already true) |
| No protocol / length change | Large font / landscape: still verify scroll + KAV |

**Concrete smallest implementation slice:** see §4.

### (e) Proven patterns from OTP / authenticator / banking apps

| Pattern | Already / candidate | Notes |
| --- | --- | --- |
| Segmented boxes | **Already** (`PinCodeInput`) | Industry-standard; keep |
| Hidden single field + overlay | **Already** | Good for paste; keep |
| Auto-advance / auto-submit at full length | Candidate (variant of c + d) | Auto-**submit** on 6/2 is aggressive for MFA (wrong digit → accidental verify/respond); prefer auto-**dismiss** + explicit Approve/Complete |
| IME action Done → submit | Weak with `number-pad` | Would need `keyboardType` change or custom accessory; low value if (d) lands |
| Sticky action bar above IME | Enrollment partial; auth not | Auth could adopt KAV + sticky footer like enrollment |
| Biometric after digits | Separate (#834 Q11) | Out of scope for keyboard hide |

**Auto-submit at full length is deliberately not recommended** for Ezkey: verify and respond are high-assurance; wrong last digit should not fire the network path without an explicit Confirm.

### (f) Other findings

1. **Asymmetry:** Enrollment disables Complete until 6 digits; Pending Auth leaves Approve enabled and shows a form error. Unifying on “disabled until complete” reduces dead taps and supports (d).
2. **Pending Auth lacks KAV** while enrollment has it — under `adjustResize`, content may shrink; without persist-taps, the visible Approve still eats the first press.
3. **No `PinCodeInput` unit tests** — any dismiss-at-length behaviour should add a focused component test.
4. **iOS** is a later milestone (mobile docs Android-first); still design for number-pad Done absence so iOS does not regress later.
5. **Security:** traces already forbid logging challenge digits (`usePendingAuth` / env comments). UX changes must not add digit logging, analytics of codes, or clipboard exfiltration. No change to challenge length or respond/verify payloads.

### Accessibility (TalkBack / VoiceOver, large font, focus order)

| Topic | Guidance for any chosen option |
| --- | --- |
| Role / state | Primary button `accessibilityState={{ disabled }}` when incomplete; announce enablement when the Nth digit lands |
| Labels | Keep existing i18n labels/hints; do not replace with color-only “ready” |
| Focus order | Challenge heading → PIN → error → Deny → Approve (auth); Complete in sticky footer (enrollment) |
| Large font / display size | Sticky footer + KAV may clip; prefer scrollable actions or ensure KAV padding; device-walk with large font |
| Screen reader + keyboard | Auto-dismiss must not steal focus without announcement; prefer “code complete” announcement optional, not noisy |
| Deny | Must stay available without digits (auth leave / explicit deny) |

### Android vs iOS (summary)

| | Android (current product focus) | iOS (later) |
| --- | --- | --- |
| Soft input | `adjustResize` + optional KAV `height` | KAV `padding` already on enrollment |
| Persist taps | Critical on Pending Auth `ScrollView` | Same RN prop |
| `number-pad` Done | OEM-dependent | Usually absent → (c)/(d) matter more |
| Autofill OTP | Do not enable SMS OTP hints | Same |

---

## 4. Recommendation

### Preferred direction

**Lead with (d), optionally pack (c) in the same small PR after Marc’s go.**

1. **Pending Auth:** set `keyboardShouldPersistTaps="handled"` on the attempt `ScrollView`.
2. **Pending Auth:** disable Approve when `challengeRequired && challengeInput.length !== AUTH_CHALLENGE_LENGTH` (mirror enrollment `challengeMissing`); Deny unchanged.
3. **Keep** existing `Keyboard.dismiss()` at the start of a valid `handleRespond` so one Approve tap both dismisses and submits once digits are complete.
4. **Optional same slice:** in `PinCodeInput`, when `value` reaches `length`, blur / `Keyboard.dismiss()` (auto-dismiss), relying on existing tap-to-refocus for edits.
5. **Defer:** (a) color/label two-step teaching; SMS `oneTimeCode` hints; auto-**submit**; rebuilding Pending Auth sticky footer unless Pixel walk still shows Complete/Approve obscured after (1)–(4).
6. **Enrollment:** no mandatory change if Pixel walk shows Complete already receives the first press; if not, add explicit dismiss-on-Complete and/or auto-dismiss (4) without changing FSM guards.

### Smallest implementation slice (after go)

| Item | Touch |
| --- | --- |
| Persist taps + Approve disabled-until-complete | `PendingAuthScreen.tsx` (+ maybe thin derived flag from hook) |
| Optional auto-dismiss | `PinCodeInput.tsx` only |
| Copy | None required if disabled state is enough; optional hint string only if Marc wants it |
| Protocol / API / lengths | **None** |

**Effort estimate (engineering, not calendar):** **S** — roughly a focused half-day including Jest, plus a short Pixel walk. Auto-dismiss adds little if confined to `PinCodeInput`. Sticky-footer redesign for Pending Auth would bump to **M** and should wait for device evidence.

### Automated tests that should cover the slice

| Layer | What to assert |
| --- | --- |
| Jest — `PinCodeInput` (new) | Digits-only filter; `maxLength`; optional: reaching `length` calls blur/dismiss mock; tap container focuses |
| Jest — `usePendingAuth` (extend) | Approve path still rejects incomplete challenge; with UI disable, screen test may assert `disabled` on Approve until 2 digits |
| Jest — screen / workflow | `authenticationContinuity.workflow.test.tsx`: Approve still works when challenge complete; Deny without digits still allowed |
| Jest — enrollment | Existing `useEnrollmentWizard` length tests remain green; no behaviour change unless auto-dismiss added |
| Maestro / device | Not CI-required for this UX; optional flow assert on challenge `testID`s already present |

Do **not** assert IME geometry in Jest (not reliable). Persist-taps behaviour is best proven on device; unit tests cover enablement and dismiss helpers.

### Short Pixel walk script (manual)

**Assumptions:** clean-start Auth stack; Pixel with a debug build; Admin (or integrating app) can show enrollment QR + 6-digit code and a login that requires a 2-digit challenge.

**Surfaces:** end-user on mobile; Auth API as today; Admin / integrating app only to **display** codes (operator or end-user elsewhere).

1. **Enrollment:** scan QR → land on verify stage → confirm `number-pad` → enter 6 digits (optionally note whether keyboard auto-dismisses after change) → **single** tap **Complete enrollment** with keyboard still open if not auto-dismissed → expect verify proceeds without a prior dismiss-only tap.
2. **Wrong digit then edit:** enter 5 digits, pause; enter 6th wrong intentionally if testing auto-dismiss; tap boxes, delete last digit, correct, Complete once.
3. **Auth challenge:** trigger pending with `challengeRequired` → screen auto-focuses PIN → enter 1 digit → confirm Approve disabled (after slice) → enter 2nd → **single** tap Approve → expect respond without dismiss-only first tap.
4. **Deny:** with keyboard open and 0–1 digits, tap Deny once → expect deny respond (no challenge required).
5. **Large font:** Settings → display size largest (or font scale) → repeat Approve single-tap.
6. **TalkBack (spot check):** focus order PIN → Deny → Approve; disabled Approve announced when incomplete.

Record pass/fail only; no codes in logs or screenshots of live secrets.

---

## 5. Open questions for Marc

1. **Auto-dismiss at full length (c):** include in the first implementation slice with (d), or ship persist-taps + Approve disable first and A/B auto-dismiss on a follow-up?
2. **Approve disabled until 2 digits:** OK to mirror enrollment Complete (recommended), or keep pressable + form error for discoverability?
3. **Enrollment on Pixel:** after a controlled walk, does **Complete enrollment** already accept the first tap with the current sticky footer + KAV, or do you still see dismiss-only? (Code suggests auth is the structural bug; enrollment needs your confirmation.)
4. **Auto-submit:** confirm **no** — explicit Complete / Approve remains mandatory even when length is full?
5. **iOS:** any need to prototype Done-key / accessory behaviour now, or keep Android-first and rely on (c)/(d) for later iOS?

---

## 6. Security reminder (non-negotiable for the future code PR)

- Do not log challenge digits, proof tokens, or signatures.
- Do not enable SMS OTP autofill APIs for these fields.
- Do not change challenge lengths, payload builders, or respond/verify guards beyond UI enablement / keyboard dismissal.
- Deny and leave-as-deny semantics stay as decided under #834 / #833.

---

## 7. Discoverability

- Issue: [#1082](https://github.com/mgagp/ezkey/issues/1082)
- Living FSM: [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md)
- Screens: [`ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md`](../../ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md)
- Index pointer: [`ezkey_mobile/docs/README.md`](../../ezkey_mobile/docs/README.md) (supporting docs table)
