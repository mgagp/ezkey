# Handoff — Raise Android minSdk to 31 (product floor)

**Status:** `done` — Gradle + in-repo docs aligned 2026-07-25  
**Lane:** Mobile product / release hygiene (not a MOB-* finding)  
**Policy canon:** [`ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`](../../../../ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md)  
**Related:** MOB-012 (runtime Keystore unlocked-device gate) is **Fixed** 2026-07-23 — do **not** set `minSdk` to 35 as a substitute.

Next related handoff: [`HANDOFF-mobile-unsupported-os-ux.md`](HANDOFF-mobile-unsupported-os-ux.md).

---

## Operator decisions (already made)

1. Product support floor = Android **12+** / API **31** (documented 2026-07-22).
2. Gradle `minSdkVersion` is now **31** (was 24 implementation debt).
3. Do **not** invent `I-*` / `TB-*` for this bump unless scope explodes.

---

## One-sentence problem

~~The reference app still declares `minSdkVersion = 24` while product policy requires API **31**.~~ **Resolved:** Gradle and policy both require API **31**.

---

## What shipped

1. `minSdkVersion = 31` in [`ezkey_mobile/android/build.gradle`](../../../../ezkey_mobile/android/build.gradle).
2. Doc drift cleared (`MOBILE_ANDROID_PLATFORM_SUPPORT.md` policy-vs-code table, architecture, AGENTS, Play readiness audit, docs README).
3. Smoke: debug APK built with `sdkVersion:'31'`; install + launch on physical Pixel 7 Pro (API **37**)
   (`adb install` after wireless timeout on Gradle `installDebug`).

### Operator Play Console notes (not in-repo)

When this binary is the one you publish to Play:

- [ ] Release notes mention Android 12+ as minimum.
- [ ] Play listing / Data Safety / support text does not claim older OS support.

In-repo checklist item in [`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md) is marked done for the Gradle/policy alignment; Console copy remains operator-owned.

### Explicitly out of scope (unchanged)

- MOB-012 `setUnlockedDeviceRequired` gate (already Fixed)
- In-app unsupported-OS screen ([`HANDOFF-mobile-unsupported-os-ux.md`](HANDOFF-mobile-unsupported-os-ux.md))
- Raising `minSdk` above 31
- iOS minimum version
