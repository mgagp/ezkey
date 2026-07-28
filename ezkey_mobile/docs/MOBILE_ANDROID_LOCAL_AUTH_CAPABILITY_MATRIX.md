# Android Local-Auth Capability Matrix (Keystore / StrongBox / BiometricPrompt)

## Purpose

This is the capability-matrix deliverable required to promote `I-2026-0001` (see its Promotion
notes) and to close the first exit criterion of `TB-2026-0001`. It answers, with citations to
Android's own API documentation and to the current `EzkeyCryptoModule.kt` implementation, the
questions raised in the grill session (`2026-05-07-mobile-local-auth-capability-discovery-grill-me.md`):
what is fixed at key-generation time versus configurable at runtime, what invalidates a key, and
how StrongBox interacts with auth-binding.

This document is **research and current-state analysis**, not a design decision. It feeds the
decision already recorded in that grill session (Option A for V1; Level 3 key-binding deferred
until this matrix exists — it now does).

## Reference floor

Ezkey's mobile platform floor is Android 12 (API 31) — see
[`MOBILE_ANDROID_PLATFORM_SUPPORT.md`](MOBILE_ANDROID_PLATFORM_SUPPORT.md). All Android Keystore
APIs discussed below (`setUserAuthenticationParameters`, `setUnlockedDeviceRequired`,
`BiometricManager.Authenticators` bitmask) require API 30 (Android 11) or lower, so **Ezkey's own
platform floor is already past the point where these APIs are universally available.** This
removes one fragmentation axis that older analyses had to worry about in the abstract.

## 1. What is fixed at key-generation time vs configurable at runtime

Every attribute below is set once, in the `KeyGenParameterSpec` passed to
`KeyPairGenerator.initialize()` (or `KeyGenerator.init()` for the AES seal key), when the key is
created. **None of them can be changed on an existing key.** Changing any of them requires deleting
the alias and generating a new key — which, for the enrollment signing key, means the device public
key on file with the backend no longer matches, i.e. a re-enrollment-shaped event.

| Attribute | Current Ezkey value | Fixed at generation? | Runtime-alterable? |
| --- | --- | --- | --- |
| Algorithm / purpose (`EC` `secp256r1`, `SIGN\|VERIFY`) | Set | Yes | No |
| `setIsStrongBoxBacked` | `true`, with fallback | Yes | No |
| `setUserAuthenticationRequired` | `false` (Level 2 posture) | Yes | No |
| `setUserAuthenticationParameters(timeout, type)` | Not set (n/a while auth not required) | Yes | No |
| `setInvalidatedByBiometricEnrollment` | Default (`true`); irrelevant while auth not required | Yes | No |
| `setUnlockedDeviceRequired` | `true` on API 35+ only (MOB-012) | Yes | No |

**What is genuinely runtime-configurable today, without touching key generation:**

- Which `BiometricPrompt` posture is *requested* before signing (`protectedAuthenticators()` —
  `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`, `EzkeyCryptoModule.kt` L110–113). This is an app-level
  choice, not a Keystore constraint — it governs the Level 2 UX gate, not the key itself.
  This is exactly why Level 2 is not cryptographically binding: the prompt and the signature are
  two independent runtime steps that the app chooses to sequence, not one Keystore-enforced
  operation.
- The local user preference (`standard` / `confirm-before-approvals`) and the (currently stubbed)
  `approvalPolicy` on `StoredEnrollment` — both pure app/data-layer state, no Keystore interaction.

**Conclusion:** there is no middle ground where "policy strength" changes without a key
regeneration event, once Level 3 (`setUserAuthenticationRequired(true)`) is in play. This confirms
the Grill Me's risk #1 (key lifecycle mismatch) is real, not speculative.

## 2. Invalidation behavior (when does a key become unusable)

Source: [`KeyProtection.Builder#setInvalidatedByBiometricEnrollment`](https://developer.android.com/reference/android/security/keystore/KeyProtection.Builder#setInvalidatedByBiometricEnrollment(boolean)),
[`KeyPermanentlyInvalidatedException`](https://developer.android.com/reference/android/security/keystore/KeyPermanentlyInvalidatedException).

| Trigger | Applies to today's keys (`setUserAuthenticationRequired(false)`)? | Applies to a future Level 3 key |
| --- | --- | --- |
| Secure lock screen disabled or force-reset (Device Admin) | No | **Yes, always**, once `setUserAuthenticationRequired(true)` is set — independent of timeout value |
| New biometric enrolled, or all biometrics removed | No | **Only if the key requires auth on every use** (`setUserAuthenticationParameters(0, type)`, i.e. no positive validity window). A key with a positive timeout window is not invalidated by this specific trigger. |
| App reinstall / data clear | Keystore aliases survive app data clear (Keystore is OS-scoped, not app-data-scoped) unless the app explicitly deletes them or the user removes the app entirely | Same |

**Important nuance confirmed by the official docs** (this corrects an implicit assumption in the
original discussion): the "invalidated by biometric enrollment change" behavior is **not** a blanket
property of every auth-bound key — it specifically applies to keys configured for **per-use**
authentication (timeout `0`). A key with a short validity window (e.g. 30 seconds) is *not*
invalidated by a fingerprint being added or removed; it is still invalidated if the lock screen
itself is disabled or reset. Since Ezkey's own living design note already argues against long
validity windows and recommends per-action confirmation (see
`MOBILE_LOCAL_AUTH_POLICY_AND_AUDIT_FUTURE_WORK.md` § Re-authentication Cadence), **the per-respond
posture Ezkey would actually want (timeout 0) is exactly the one most exposed to biometric-enrollment
invalidation.** This is a real, quantified cost of the recommended UX posture, not an edge case that
a different timeout choice could sidestep for free.

## 3. StrongBox and auth-binding: compatible, not free

`setIsStrongBoxBacked(true)` and `setUserAuthenticationRequired(true)` are independent flags and can
be set on the same `KeyGenParameterSpec` — Android does not reject the combination. Practical
consequences observed in the current codebase's own StrongBox-availability handling
(`generateEnrollmentEcKeyPair`, `StrongBoxUnavailableException` fallback):

- The existing fallback pattern (`try StrongBox, catch StrongBoxUnavailableException, retry without
  StrongBox`) would need to remain in place for an auth-bound key exactly as it does today — no new
  fallback shape is needed.
- Auth-binding does not change *where* the key lives (StrongBox vs TEE); it changes *when Android
  will let the key operate*. The two concerns are orthogonal, confirming they can be evaluated and
  shipped independently — which is consistent with `MOB-017` (seal key) having just landed
  independently of Level 3 (signing key auth-binding).

## 4. `BIOMETRIC_STRONG` vs `DEVICE_CREDENTIAL` for a future stricter tier

Today's posture (`protectedAuthenticators()`) accepts either `BIOMETRIC_STRONG` or
`DEVICE_CREDENTIAL`. If a future "strict" policy tier ever wanted to require **biometric only** (no
PIN/pattern/password fallback), that is expressible via the `type` argument of
`setUserAuthenticationParameters` (`KeyProperties.AUTH_BIOMETRIC_STRONG` without
`AUTH_DEVICE_CREDENTIAL`) — but this would fail key generation on any device with no strong
biometric enrolled, per Android's own documented precondition ("if the key requires authentication
for every use, at least one biometric must be enrolled"). This is a real device-population risk:
Ezkey does not control the hardware/enrollment state of adopters' phones, so a biometric-only
requirement would need an explicit, honest failure path (e.g. block enrollment on that device rather
than silently fall back to a weaker posture).

## 5. Answers to the grill session's critical questions (this matrix as evidence)

| # | Question | Answer from this matrix |
| --- | --- | --- |
| 1 | What must be true for Level 3 to avoid forced re-enrollment? | Nothing makes this free: any Level 2→3 transition requires generating a new key, which means the backend-known device public key changes. There is no in-place upgrade path. |
| 2 | Can local-auth requirements change post-enrollment without invalidating the signing key? | No. All relevant flags are fixed at generation (§1). |
| 3 | Which key attributes are fixed vs runtime-configurable? | See §1 table. Everything Keystore-relevant is fixed; only the app-level `BiometricPrompt` posture and local preference/policy state are runtime. |
| 4 | Is today's gate a UI preference only, or key-bound? | UI preference only (Level 2) — confirmed, `signWithAuthentication` never wraps a `CryptoObject`. |
| 5 | What is the exact failure mode when a key doesn't satisfy a new policy? | `KeyPermanentlyInvalidatedException` at next use, for auth-bound keys under the triggers in §2; for a policy tier change with no matching key (Level 2→3), the failure is a generation-time re-key, not a Keystore exception on an existing key. |
| 6 | What happens on devices with no `BIOMETRIC_STRONG` but with device credential? | `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` (current posture) accepts device credential; a biometric-only posture (§4) would fail key generation outright on such a device. |
| 7 | What migration behavior is acceptable for enrollments created under a weaker policy? | Consistent with the MOB-011/MOB-017 precedent already set in this codebase (greenfield cutover, no migration path, forward-only) — no production fleet exists yet, so the same posture applies if/when Level 3 ships. |
| 8 | Which behavior differences are Android-version dependent? | None of the APIs in this matrix are version-fragmented above Ezkey's own API 31 floor (see Reference floor above). The only remaining version-dependent flag in this codebase is `setUnlockedDeviceRequired` (MOB-012, gated to API 35+), which is orthogonal to auth-binding. |

## 6. What this matrix does *not* cover (explicitly out of scope)

- Key Attestation (proving to the backend that a key is StrongBox-resident and/or auth-bound) —
  separate future program (`MOB-003`).
- iOS Secure Enclave equivalents — Android-first product posture; not evaluated here.
- Empirical on-device timing/UX measurement of the biometric-enrollment invalidation trigger in §2 —
  this matrix is a documentation-and-source read, not a physical-device test. A physical-device
  verification pass (add/remove a fingerprint, observe `KeyPermanentlyInvalidatedException`) remains
  useful before any Level 3 implementation slice, using the same StrongBox-capable-phone posture as
  `MOBILE_STRONGBOX_MANUAL_CHECKLIST.md`.

## Related

- `product-docs/global/backlog/TB-2026-0001-mobile-local-auth-capability-discovery.md`
- `product-docs/global/backlog/grill-sessions/2026-05-07-mobile-local-auth-capability-discovery-grill-me.md`
- `product-docs/global/backlog/ideas/I-2026-0001-mobile-respond-local-auth-per-enrollment.md`
- `MOBILE_LOCAL_AUTH_POLICY_AND_AUDIT_FUTURE_WORK.md`
- `docs/security/mobile-protocol-crypto-assessment-2026-07.md` § MOB-001, MOB-012
- `MOBILE_STRONGBOX_MANUAL_CHECKLIST.md`
