# Handoff — MOB-012 `setUnlockedDeviceRequired` on Android 12–14

**Status:** `open` — analysis / bounded design only (2026-07-19)  
**Lane:** Mobile protocol security hygiene (pass-2) — likely **hygiene-sized fix**, not program  
**Finding:** MOB-012 (P1, Confirmed)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)  
**Assessment register:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-012

Use this prompt to start a **new Cursor session** for a **short complementary analysis** (optional mini Grill Me) on MOB-012.  
Do **not** implement remediations in that session unless the operator explicitly changes scope after the pass-2 HITL decision is recorded.

---

## Operator intent for this session

1. Read this handoff and the assessment finding.
2. Optionally run a **mini Grill Me** (skill `.cursor/skills/grill-me/SKILL.md`) focused on API gate choice and migration of **already-minted** keys — keep it short; this is not an identity redesign.
3. Produce a short **fix-shape note**: exact SDK gate, which key generators change, what happens to existing Keystore entries, tests/checklist.
4. Return a clear recommendation for the pass-2 HITL decision on MOB-012: **fix** / **defer** / **suppress** / **skip** — with rationale.
5. Do **not** create `I-*` / `TB-*` / GitHub issue unless the operator explicitly asks (unlikely for this item).
6. Do **not** fold MOB-001 Track B (CryptoObject / `setUserAuthenticationRequired`) into this change set.

---

## One-sentence problem

Ezkey enables `setUnlockedDeviceRequired(true)` from API 30 (`R`) on both enrollment EC keys and the app AES seal key while `setUserAuthenticationRequired(false)`, despite official Android docs warning of critical bugs on Android 12–14 (API 31–34) and recommending the flag only on Android 15+ (API 35+) for this non–user-auth-bound design.

---

## Scenario that led to the observation (preserve this narrative)

This was **not** a remote exploit. It came from white-box re-reading of `EzkeyCryptoModule` during Mobile Crypto Assessment Pass-2, cross-checked against the official Android Keystore builder warning for `setUnlockedDeviceRequired`.

Concrete failure scenario:

1. User on **Android 13 or 14** enrolls MFA. Native code creates:
   - per-enrollment EC key `ezkey_enrollment_{id}` with `unlockedDeviceRequired=true`, `userAuthenticationRequired=false`;
   - app seal AES key `ezkey_app_seal_v1` with the same flags (on first seal).
2. Later the user **removes the secure lock screen** (or hits documented re-auth bugs with weak biometric / shared-profile unlock).
3. Platform behavior on 12–14: unlocked-device-required keys for that user can be **automatically deleted** (or become unusable).
4. Ezkey consequences:
   - enrollment signing key gone → Auth API still bound to the old public key → pending/respond fail;
   - seal key gone → sealed proof tokens / integration keys cannot unseal;
   - UX easily misdiagnosed as “empty enrollments” (**MOB-015**) or worsened by silent `ensureEnrollmentKeyPair` regen (**MOB-013**).

**Observation label used in HITL:** *`setUnlockedDeviceRequired` enabled too early (API 30) on both signing and seal roots, without user-auth binding.*

**Non-claim:** this is **not** a remote MFA bypass over ordinary TLS. It is a **platform key-lifecycle / availability** risk on still-common OS versions.

**Docs (canonical warning):**  
[KeyGenParameterSpec.Builder.setUnlockedDeviceRequired](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setUnlockedDeviceRequired(boolean))

Documented Android 12–14 issues (summary):

- No secure lock screen → cannot generate / import / use these keys.
- Secure lock screen removed → user’s unlocked-device-required keys **auto-deleted**.
- Non-strong biometric unlock may not re-authorize.
- Biometric unlock in profiles sharing parent lock may not re-authorize.

Platform guidance: use the flag only on **Android 15+**, unless also using `setUserAuthenticationRequired(true)` (then the first two behaviors are expected for user-auth keys). Ezkey today uses `setUserAuthenticationRequired(false)` everywhere relevant.

---

## Evidence map (read these first)

### Enrollment EC key generation

- `ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt`
  - `generateEnrollmentEcKeyPair` — `.setUserAuthenticationRequired(false)` then:
    - `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { builder.setUnlockedDeviceRequired(true) }`

### App seal AES key generation

- Same file — `createAppSealKey` — identical gate (`SDK_INT >= R`) and `setUserAuthenticationRequired(false)`.
- Alias constant: `APP_SEAL_KEY_ALIAS = "ezkey_app_seal_v1"`.

### Runtime surface (not compile target)

- `ezkey_mobile/android/build.gradle` — `minSdkVersion = 24`, `targetSdkVersion = 36`.
- Bug surface is **device OS API 31–34** (and the current code also sets the flag on API 30).

### Coupling (note only; do not fix in the same PR unless operator expands scope)

- **MOB-013** — silent key regen after platform deletion hides root cause.
- **MOB-015** — unseal failure can collapse enrollment list to empty.
- **MOB-001 Track B** — future user-auth-required keys may legitimately reintroduce unlocked-device semantics; keep that redesign separate.
- **MOB-011** — different problem (identity scoping); do not merge.

---

## Product / methodology constraints

- Pass-2 is **Lane D hygiene**. Prefer a **small native gate change** over a program slice.
- Existing Keystore entries already minted with `unlockedDeviceRequired=true` **cannot** have that flag rewritten in place — call out whether the fix is **new keys only**, with optional migration / delete+recreate policy (recreate seal key has data implications; enrollment key recreate without server re-verify is unsafe).
- Keep StrongBox request + fallback behavior unchanged unless Grill Me shows a conflict.
- iOS out of scope.
- No Auth API / OpenAPI changes.
- No commit/PR unless the operator asks after HITL.

---

## Mini Grill Me — pressure questions (keep short)

Adapt from `.cursor/skills/grill-me/SKILL.md`.

1. Should the gate be **API 35+ only** (strict doc alignment), or also leave the flag **off on API 30** (current code turns it on at `R`)?
2. Apply the same gate to **both** enrollment EC and app seal keys, or treat seal differently?
3. For devices that **already** have keys with the flag: accept “fixed going forward only,” or attempt any migration (almost certainly **no** for enrollment EC without re-enroll)?
4. If seal key is deleted by the platform, is the correct UX re-enroll-all, wipe local state, or a dedicated recovery screen — and is that in or out of MOB-012?
5. Does turning the flag **off** on 12–14 meaningfully weaken the security story vs avoiding documented key destruction?
6. Minimal evidence that would **disprove** urgency (e.g. product only supports API 35+ devices — currently false given minSdk 24)?

Record decision pressure points, then propose the smallest safe fix shape.

---

## Expected deliverables from the analysis session

1. **Recommended HITL disposition** for MOB-012.
2. If **fix:** exact code change sketch (SDK constant / comparison), files touched, unit vs instrumentation vs manual checklist (lock-screen removal on API 33/34 if available).
3. Explicit **non-goals:** no CryptoObject, no `setUserAuthenticationRequired(true)`, no MOB-011 identity work.
4. One paragraph for the campaign note § MOB-012 rationale after the operator decides.

---

## Suggested first agent turns

1. Read: this handoff, assessment §14.2 MOB-012, `EzkeyCryptoModule.kt` (`generateEnrollmentEcKeyPair`, `createAppSealKey`), Android docs link above.
2. Short Grill Me on gate + existing-key posture with the operator.
3. Converge on fix shape + disposition; stop before coding.

---

## Out of scope for this handoff session

- Implementing the SDK gate change (until HITL **fix** + dedicated change set)
- MOB-001 Track B / CryptoObject
- MOB-011 / MOB-013 / MOB-015 remediations
- Active exploit lab
- Raising `minSdk` to 35 as a substitute for gating (unless operator explicitly wants that product decision)

---

## Paste-ready starter message (for the other agent)

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mob-012-unlocked-device-required-gate.md end-to-end.
Then read docs/security/mobile-protocol-crypto-assessment-2026-07.md §14.2 MOB-012 and EzkeyCryptoModule.kt (generateEnrollmentEcKeyPair + createAppSealKey).

Task: short complementary analysis (optional mini Grill Me) on MOB-012 — setUnlockedDeviceRequired(true) from API 30 without user-auth binding, against the official Android 12–14 warning. Preserve the lock-screen key-deletion scenario in the handoff. Do not implement code. Do not create I-*/TB-*. Do not expand into MOB-001 Track B.

Start with Grill Me questions 1–3 from the handoff, one cluster at a time, then recommend fix vs defer and the exact API gate.
```
