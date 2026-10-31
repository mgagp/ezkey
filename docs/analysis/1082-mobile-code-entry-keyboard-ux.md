<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# #1082 — Mobile code-entry keyboard UX (6-digit enrollment, 2-digit auth)

**Status:** analysis only — awaiting Marc go / no-go. No product code in this change set.  
**Issue:** [#1082](https://github.com/mgagp/ezkey/issues/1082).  
**Evidence basis:** static read of React Native source, plus Pixel 7 Pro walks on 2026-10-30 (clean-start local instance, Running Devices mirror): enrollment verify (6 digits) and pending auth (2-digit challenge).  
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

Ezkey is a passage on the way to another application, not the person's main task ([operator-alignment guide](../../product-docs/global/operator-alignment-guide.md)). A novice who cannot see the next step will stall here, and that friction costs adoption.

### 1.1 Device observation — enrollment verify, Pixel 7 Pro, 2026-10-30

Clean-start local instance. Screen: **Ajouter un enrôlement**. System number pad (Gboard on this phone).

- Tapping the first box opens the pad. The six boxes stay. Filling all six digits does **not** dismiss the pad and does **not** reveal the primary action.
- With the pad open, **Annuler** and **Terminer l'enrôlement** are off-screen. The enrollment-details card is clipped. The existing `KeyboardAvoidingView` plus sticky footer does **not** keep the primary action above the IME on this device.
- The only controls that brought the button back were keys on the keyboard itself: the checkmark at the bottom right, and the glyph at the bottom left (square or caret, depending on the keyboard). Those keys only hide the keyboard. They do not complete enrollment.
- After that dismiss, the six digits remain. **Terminer l'enrôlement** is visible and enabled.

A person who does not recognize those glyphs has no in-app next step. Tapping the form does not surface the button. The glyphs differ by keyboard and by platform: an iOS number pad usually has no Done key. The design must not depend on them.

This replaces the earlier wording that treated both screens as the same "button just above the keyboard, first tap swallowed" bug. Enrollment is occlusion. Auth is a swallowed tap on a button that is already visible (§1.2).

### 1.2 Device observation — pending auth, 2-digit challenge, Pixel 7 Pro, 2026-10-30

Same session. Screen: **Authentification en attente**, after **Vérifier s'il y a une demande d'authentification**. Admin UI test dialog showed the 2-digit code. System number pad open. The two boxes started empty.

- **Refuser** and **Approuver** stay on screen, directly above the pad. The pad does not cover them. **Approuver** is already shown as the active (green) action while both boxes are empty.
- Tapping **Approuver** with the pad open only hides the pad. It does not approve.
- To type again, the person has to tap the blank space beside a digit box. That brings the pad back over the field.
- After both digits are entered, the instinct is to tap **Approuver** immediately. The first tap only hides the pad. A second tap on **Approuver** is required for the approval to proceed.

A person who does not know this model will conclude that **Approuver** always takes two presses whenever a 2-digit challenge is showing. The digits stay in the boxes across the dismiss. The keyboard checkmark is not what they used. They used **Approuver** itself, and the first press was spent on closing the pad.

Code-level match: Pending Auth's `ScrollView` has no `keyboardShouldPersistTaps` (default `never`), and **Approuver** is not disabled until two digits are present (§2.3). Persist-taps is the auth fix. It does nothing for enrollment until **Terminer** is on screen.

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

**Implication:** enrollment already combines KAV + sticky footer + `keyboardShouldPersistTaps="handled"` on the scroll body. The 2026-10-30 Pixel walk (§1.1) shows that footer is **covered** by the number pad, so persist-taps never gets a press on **Terminer**. The layout does not keep the primary action above the IME on this device.

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

**Implication (confirmed on the Pixel, §1.2):** with default `keyboardShouldPersistTaps="never"`, the first tap on **Approuver** while the pad is open is consumed to dismiss the keyboard and does **not** fire `onPress`. A second tap approves. That is the two-press behaviour Marc walked. It is a different gap from enrollment occlusion: here the button is already on screen. **Refuser** sits in the same scroll content, so the same first-tap dismiss can apply to it; the walked case was **Approuver** after the two digits were entered. Re-opening the pad required a tap on the blank beside a digit box, which matches the `Pressable` refocus.

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

**Fit:** Reveals a footer that was covered, which matches the Pixel enrollment dead-end once six digits are in. It does not help someone who has typed only some of the digits and is already looking for a next step. If the button moves at the moment the keyboard closes, the person taps the old spot ([BC Wallet unlock](https://github.com/bcgov/bc-wallet-mobile/issues/2395)). Use dismiss-on-last-digit only when the primary button stays put.

### (d) Single-step Approve / Complete while keyboard is open

**Idea:** With a complete code, one tap on the primary action dismisses the keyboard **and** submits. Require `keyboardShouldPersistTaps="handled"` so the first tap is not swallowed. Disable primary until length is complete (enrollment already does; auth does not).

**Pixel enrollment, 2026-10-30:** the primary action is not on screen while the pad is open, so persist-taps has nothing to receive. This option applies only after the button is visible. It remains the right guard for Pending Auth if Approve is on screen and the first tap is swallowed (§2.3).

| Pros | Cons |
| --- | --- |
| Useful once the primary action is actually on screen | Must set persist-taps on **Pending Auth** `ScrollView` (missing today) |
| Enrollment already dismisses-on-respond pattern exists for auth (`Keyboard.dismiss` in `handleRespond`) | Pixel enrollment (§1.1): the footer is covered, so this option does not apply until the button is visible |
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

**Auto-submit at full length is deliberately not recommended** for Ezkey: verify and respond are high-assurance; the code is copied from another screen, and a wrong last digit must be correctable before the network call. Explicit **Terminer** / **Approve** stays.

Comparable practice, checked against this walk:

| Pattern | What it does for a novice | Ezkey |
| --- | --- | --- |
| Segmented boxes over one real field | Shows how many digits are expected. Paste and one caret stay simple. Guides (Twilio OTP, typical OTP field write-ups) prefer this over six separate inputs. | **Keep.** `PinCodeInput` is already this. |
| Primary action visible the whole time, disabled until the length is met, one tap continues | The next step is always on screen. The person never learns the keyboard's close key. Banks and OTP screens do this. | **Lead.** The current footer failed this test on the Pixel. |
| Dismiss the pad when the last digit lands, button does not move | Removes the "code is full and I am stuck" dead-end. Tap a box to correct. | **With the lead**, only if the button stays put. |
| In-app numeric pad (card PIN, wallet PIN) | No system keyboard, so nothing can cover the confirm control. Same on every phone, including iOS. | **Fallback** if a Pixel walk still shows the system pad covering the button. More UI and accessibility work. |
| One plain text field, then validate | Simpler keyboard. Weaker "six digits" cue. Tried in a lab client. | **Set aside.** The boxes are the part that already works. |
| Teach the keyboard checkmark / hide key | Depends on glyphs the app does not own. | **Reject.** |

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
| `number-pad` Done | Pixel/Gboard showed a checkmark and a bottom-left glyph; both only hide the pad. Do not depend on them. | Usually absent. The in-app next step has to exist without a Done key. |
| Autofill OTP | Do not enable SMS OTP hints | Same |

---

## 4. Recommendation

### Preferred direction

**Keep the segmented boxes. Make the next step visible and stable. Do not depend on the keyboard's own close keys.** Awaiting Marc's go before any product code.

1. **Enrollment:** **Terminer l'enrôlement** stays on screen for the whole entry, including while the number pad is open. Disabled until 6 digits, then enabled in the **same place**. One tap completes enrollment. The current `KeyboardAvoidingView` footer failed this on the Pixel (§1.1); the next slice has to be proven on the device, not assumed from the component tree. Stay on the current React Native stack before adding a keyboard library.
2. **Do not move the button** when the pad opens or closes. A button that jumps at the moment of the last digit is a mis-tap ([BC Wallet unlock](https://github.com/bcgov/bc-wallet-mobile/issues/2395)).
3. **Dismiss the pad when the last digit lands**, only as part of (1) and (2): the button is already where the person is looking, and it becomes enabled. Tapping a box brings the pad back to correct a digit.
4. **Explicit confirm stays.** No auto-submit. No hint that teaches the checkmark or the bottom-left glyph. No SMS one-time-code autofill.
5. **Auth (2 digits):** the button is already visible (§1.2). The gap is the swallowed first tap, plus **Approuver** looking ready before two digits are in. Set `keyboardShouldPersistTaps="handled"` on the attempt `ScrollView` so one tap on **Approuver**, pad still open, both dismisses and responds once two digits are present. Disable **Approuver** until the length is met, so the green action is not offered on an empty field. Keep `Keyboard.dismiss()` at the start of a valid respond. Deny stays available with no digits. Do not rebuild this screen's layout unless a later walk shows **Approuver** covered the way **Terminer** is.
6. **Fallback:** an in-app numeric pad, with the same boxes above it, if the Pixel walk shows the system pad still covering the button. Heavier (layout, TalkBack, large font). Not the first slice.
7. **Set aside:** collapsing to one plain text field. A lab client used that and gained simpler keyboard handling; it drops the length cue the boxes already provide.

### Smallest implementation slice (after go)

| Item | Touch |
| --- | --- |
| Enrollment footer that stays visible and stable above the IME, disabled until 6 digits | `EnrollmentWizardScreen.tsx` layout. Prove on the Pixel before calling it done. |
| Dismiss pad when length is reached, without moving the footer; tap boxes to edit | `PinCodeInput.tsx` |
| Auth: persist taps + Approve disabled until 2 digits + same visibility rule | `PendingAuthScreen.tsx` |
| Copy | None required if the disabled button label is the cue |
| Protocol / API / lengths | **None** |
| In-app pad, keyboard library, plain single field, auto-submit | **Not in this slice** |

**Effort estimate (engineering, not calendar):** enablement and dismiss-on-length are **S**. Making the enrollment footer actually stay above the IME, and proving it on the Pixel, is the risky part and can land at **M** if the current `KeyboardAvoidingView` has to be rebuilt. An in-app pad is a later slice.

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

1. **Enrollment:** scan QR → land on verify stage → confirm the number pad → while typing digits 1–5, **Terminer l'enrôlement** stays visible and disabled, and it does not jump → enter the 6th digit → pad may dismiss, button stays put and enables → **one** tap completes enrollment. Do not use the keyboard checkmark or the bottom-left glyph.
2. **Wrong digit then edit:** enter 5 digits, pause; enter 6th wrong intentionally if testing auto-dismiss; tap boxes, delete last digit, correct, Complete once.
3. **Auth challenge:** trigger pending with `challengeRequired` → pad open, both boxes empty → **Approuver** is visible and disabled → enter 2 digits, pad still open → **one** tap **Approuver** responds. A dismiss-only first tap is a fail. Tap the blank beside a box only when correcting a digit.
4. **Deny:** with keyboard open and 0–1 digits, tap Deny once → expect deny respond (no challenge required).
5. **Large font:** Settings → display size largest (or font scale) → repeat Approve single-tap.
6. **TalkBack (spot check):** focus order PIN → Deny → Approve; disabled Approve announced when incomplete.

Record pass/fail only; no codes in logs or screenshots of live secrets.

---

## 5. Open questions for Marc

Answered by the 2026-10-30 walks:

- **Enrollment:** **Terminer** is covered. Keyboard chrome (checkmark, bottom-left glyph) only hides the pad. It does not complete enrollment.
- **Auth:** **Approuver** is visible above the pad and already looks active with empty boxes. The first tap only hides the pad. A second tap approves. Typing again means tapping the blank space beside a digit box.
- The six and two boxes stay. A plain single field is set aside.
- Auto-submit stays off unless Marc overrides it.
- The shared outcome is one press on the primary once the code is complete. The work is not the same slice: enrollment must put the button on screen; auth must stop swallowing the press on a button that is already there.

Still open:

1. **Go / no-go** on §4 before any product code.
2. **In-app pad:** only if the enrollment button cannot be kept visible on the Pixel. Not required for the auth two-press bug.

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
