# Ezkey Mobile — Android platform support floor

**Status:** Product policy (documented 2026-07-22)  
**Next review due:** **2027-07** (annual)  
**Keyword / habit:** Platform support floor — annual review

This document makes the Android OS support floor for the Ezkey **reference** mobile app an
**explicit product decision**. It is not an accidental leftover from a React Native compile default.

Related:

- Build values today: `ezkey_mobile/android/build.gradle` (`minSdkVersion`, `targetSdkVersion`)
- Crypto Keystore flag hygiene (separate): MOB-012 / `HANDOFF-mob-012-unlocked-device-required-gate.md`
- Play operations: `MOBILE_PLAY_PUBLISHING.md`, `MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`

---

## Decision (2026)

| Field | Value |
| --- | --- |
| **Supported floor** | Android **12+** |
| **Corresponding `minSdkVersion`** | **31** |
| **Not supported** | Android 11 and below (including Android 9) |
| **Rejected as current floor** | `minSdk` **35** (Android 15+) — too small an addressable market for Ezkey’s adoption posture |

**Rationale (short):** Ezkey is a self-hosted cryptographic MFA client we control. We may set a
security-vs-market line and move it over time. Android 12 is the 2026 balance: credible relative to
platform security support timelines, while still covering roughly **~69%** of active Play devices
(and excluding roughly **~31%**) on the December 2025 Google Play distribution snapshot. Android 15+
as a hard install floor would exclude roughly **~73%** of that same snapshot — disproportionate for
an open-source, self-hosted MFA product.

---

## Market snapshot (Play distribution, ~1 Dec 2025)

Source class: Google Play / Android Studio cumulative distribution (devices that contacted Play in a
7-day window). Figures move; refresh them at the annual review.

| Floor `minSdk` | OS | Approx. coverage | Approx. excluded | Reading |
| --- | --- | --- | --- | --- |
| 24 (code today) | Android 7+ | ~99% | ~1% | Too low for MFA posture |
| 29 | Android 10+ | ~91% | ~9% | Drops Android 9-; possible interim bump |
| 30 | Android 11+ | ~83% | ~17% | Still broad |
| **31 (policy)** | **Android 12+** | **~69%** | **~31%** | **2026 product floor** |
| 33 | Android 13+ | ~58% | ~42% | Stricter variant |
| 34 | Android 14+ | ~44% | ~56% | Aggressive for OSS adoption |
| 35 | Android 15+ | ~27% | ~73% | Not the current floor |

Illustrative version slices (same snapshot): Android 16 ~7.5%, 15 ~19.3%, 14 ~17.2%, 13 ~13.9%,
12 ~11.4%, 11 ~13.7%, ≤10 remainder.

### Security note (as of mid-2026)

Platform security bulletins eventually drop older major versions (OEM patches may continue on some
devices). Treat “Google platform bulletin coverage” as one input to the annual review, not the only
one. Supporting Android 11 and below as a **promised** MFA client floor is no longer aligned with
Ezkey’s cryptographic-device posture.

---

## Policy vs code (implementation debt)

| Layer | Today (2026-07-22) | Policy target |
| --- | --- | --- |
| Product / docs | This document | Android 12+ / API 31 |
| Gradle `minSdkVersion` | **24** | **31** (dedicated follow-up — not done in the docs slice) |
| `targetSdkVersion` / `compileSdkVersion` | **36** | Keep modern for Play publish requirements |

Until Gradle is raised, debug/sideload builds may still run on older APIs. **Play filtering to the
policy floor only applies after a published AAB with `minSdk` 31.**

---

## Play Store lifecycle (when choices become actionable)

```text
Upload AAB --> minSdk baked in manifest/metadata
          --> Play device catalog filter (device API vs minSdk)
          --> Install or update offered (or not)
```

| Moment | What happens |
| --- | --- |
| **You set `minSdk` in Gradle and ship an AAB** | The floor becomes **distribution metadata**. There is no separate Play “policy form” for OS floor beyond what the binary declares (plus store listing copy you write yourself). |
| **User tries to install** | If device API &lt; app `minSdk`, Play **does not offer** the install (device incompatible). |
| **User already has an older build; you publish a higher `minSdk`** | Play **does not offer that update** on too-old OS versions. The user **keeps the last compatible build** until they upgrade the OS, clear the app, or sideload. |
| **App already running on a soon-to-be-unsupported OS** | Play does **not** show an in-app “update your OS” screen by itself. That requires an **explicit in-app check** (`Build.VERSION.SDK_INT`) — a later work item. |

### `minSdk` vs `targetSdk` / `compileSdk`

- **`minSdk`**: who may **install** (and receive updates of that binary).
- **`targetSdk` / `compileSdk`**: modern behavior and Play **publish** requirements for new uploads.
  Ezkey already uses a modern target (36). That does **not** define the support floor.

---

## Split from MOB-012 (Keystore unlocked-device flag)

| Topic | Mechanism | Change set |
| --- | --- | --- |
| **This policy** | Who we support / `minSdk` 31 | Product docs now; Gradle bump later |
| **MOB-012** | Runtime: enable `setUnlockedDeviceRequired(true)` only on API **35+** (avoid Android 12–14 platform bugs) | Separate hygiene fix; **must not** be “solved” by setting `minSdk` 35 |

An app with `minSdk` 31 still runs on Android 12–14; MOB-012 remains relevant on those OS versions.

---

## Platform support floor — annual review

**Next review due: 2027-07**

Checklist:

1. Refresh Google Play / Android Studio distribution numbers (cumulative by API).
2. Check which majors still appear in Android Security Bulletins / accepted EOL tables.
3. Re-check React Native / dependency constraints on `minSdk`.
4. Decide: keep 31, raise (e.g. 33/34), or document a staged bump — update this file and Gradle in the same product conversation.
5. If publishing a `minSdk` bump: release notes + Play listing honesty; smoke on the new floor API.

**Discoverability (no new methodology skill):**

- This file’s **Next review due** line
- `ezkey_mobile/AGENTS.md` (platform support floor directive)
- `MOBILE_PLAY_RELEASE_READINESS_AUDIT.md` release checklist item

The same “Platform support floor — annual review” habit can later cover an iOS minimum version
without inventing a separate workflow lane.

---

## Follow-ups (actionable handoffs)

| # | Work | Handoff | Notes |
| --- | --- | --- | --- |
| 1 | Runtime Keystore `setUnlockedDeviceRequired` gate | [`HANDOFF-mob-012-unlocked-device-required-gate.md`](../../product-docs/global/backlog/handoffs/HANDOFF-mob-012-unlocked-device-required-gate.md) | **Not** a minSdk raise; keep separate |
| 2 | Raise Gradle `minSdk` **24 → 31** + Play ship checklist | [`HANDOFF-mobile-android-minsdk-31.md`](../../product-docs/global/backlog/handoffs/HANDOFF-mobile-android-minsdk-31.md) | Closes policy vs code debt |
| 3 | In-app unsupported-OS UX | [`HANDOFF-mobile-unsupported-os-ux.md`](../../product-docs/global/backlog/handoffs/HANDOFF-mobile-unsupported-os-ux.md) | After (or with) #2 |
| 4 | Annual floor review | [`HANDOFF-mobile-platform-support-annual-review.md`](../../product-docs/global/backlog/handoffs/HANDOFF-mobile-platform-support-annual-review.md) | Dormant until **2027-07** (or earlier if asked) |

Suggested session order after crypto pass-2 MOB items: **MOB-012 → minSdk 31 → unsupported-OS UX**.
Annual review is calendar-driven, not part of that chain.

Do **not** create `I-*` / `TB-*` solely for this policy document. Promote to methodology backlog only
if a future floor change becomes a multi-sprint program.
