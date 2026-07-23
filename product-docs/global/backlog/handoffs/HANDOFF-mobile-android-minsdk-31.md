# Handoff — Raise Android minSdk to 31 (product floor)

**Status:** `open` — **fix authorized** (product policy already locked)  
**Lane:** Mobile product / release hygiene (not a MOB-* finding)  
**Policy canon:** [`ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`](../../../../ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md)  
**Related:** MOB-012 is a **different** change (runtime Keystore gate). Do **not** set `minSdk` to 35.

Use this prompt to start a **new Cursor session** that implements the Gradle floor bump and ship hygiene.
Suggested queue: after or parallel to MOB-012; **before** the unsupported-OS in-app UX handoff.

---

## Operator decisions (already made)

1. Product support floor = Android **12+** / API **31** (documented 2026-07-22).
2. Current Gradle `minSdkVersion = 24` is **implementation debt**, not the product floor.
3. Do **not** invent `I-*` / `TB-*` for this bump unless scope explodes.

---

## One-sentence problem

The reference app still declares `minSdkVersion = 24` while product policy requires API **31**, so Play/sideload eligibility and docs/code disagree.

---

## Intended fix shape

### Code

1. Set `minSdkVersion = 31` in [`ezkey_mobile/android/build.gradle`](../../../../ezkey_mobile/android/build.gradle) (root `ext` used by the app module).
2. Grep for stale “API 24+” / `minSdk 24` claims in mobile docs; align with policy (canon already states 31).
3. Smoke: install/run on an **API 31** emulator or device (`./scripts/build-install-debug-clean.sh` or equivalent). Confirm the app refuses install on API &lt; 31 when using a release/debug build of this tree (optional quick check with an older emulator).

### Ship checklist (same PR or same release train)

When this binary is the one you publish to Play:

- [ ] Release notes mention Android 12+ as minimum.
- [ ] Play listing / Data Safety / support text does not claim older OS support.
- [ ] [`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md) checklist item for platform support confirmed.
- [ ] Update policy doc “Policy vs code” table: Gradle **31** = policy (remove debt wording).

### Explicitly out of scope

- MOB-012 `setUnlockedDeviceRequired` gate
- In-app unsupported-OS screen (see [`HANDOFF-mobile-unsupported-os-ux.md`](HANDOFF-mobile-unsupported-os-ux.md))
- Raising `minSdk` above 31
- iOS minimum version

---

## Evidence / read first

- `ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`
- `ezkey_mobile/android/build.gradle` (`minSdkVersion`)
- `ezkey_mobile/AGENTS.md` § Android platform support floor
- `ezkey_mobile/docs/MOBILE_ARCHITECTURE.md` (platform line)

---

## Suggested first agent turns

1. Read this handoff + platform support canon.
2. Bump `minSdkVersion` to 31; fix doc drift.
3. Smoke on API 31; note Play ship checklist for the operator.
4. Do not commit until the operator asks.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mobile-android-minsdk-31.md and
ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md end-to-end.

Task: raise Gradle minSdkVersion from 24 to 31 to match the product floor (Android 12+).
Align stale API 24 docs. Smoke on API 31. Complete the ship checklist items that apply in-repo;
leave Play Console copy as operator notes if Console is not available. Do not implement MOB-012
or the unsupported-OS in-app UX. Do not create I-*/TB-*. Do not commit until I ask.
```
