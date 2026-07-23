# Handoff — In-app unsupported Android OS UX

**Status:** `open` — **fix authorized** after (or with) the minSdk 31 bump  
**Lane:** Mobile product / release hygiene (not a MOB-* finding)  
**Policy canon:** [`ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`](../../../../ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md)  
**Prerequisite handoff:** [`HANDOFF-mobile-android-minsdk-31.md`](HANDOFF-mobile-android-minsdk-31.md)

Use this prompt to start a **new Cursor session** for clear operator-facing behavior when the
running device is below the product floor. Prefer **after** `minSdk` is 31 (Play already blocks
fresh installs); this handoff covers sideload, old leftover builds, and honest messaging.

---

## Operator decisions (already made)

1. Product floor = Android 12+ / API 31.
2. Play does **not** show an in-app “update your OS” screen by itself — the app must check
   `Build.VERSION.SDK_INT` if we want that UX.
3. Keep UX sober: one purpose, clear next step (update OS or use another device). No decorative clutter.
4. EN + FR strings (app already bilingual).

---

## One-sentence problem

After (or despite) raising `minSdk`, users can still land on an unsupported OS via sideload, an old
APK, or a transient debug build — without a clear, product-owned message that MFA on this device is
out of support.

---

## Intended fix shape

### Behavioral contract

| Situation | Behavior |
| --- | --- |
| `SDK_INT >= 31` | Normal app |
| `SDK_INT < 31` | Block primary MFA use (or full app entry) with a dedicated screen/dialog: floor explained, action = update OS / use another device; do not silently continue enrollment/auth |
| Copy | Honest, non-alarmist; no “security breach” wording |

### Suggested implementation sketch

1. Early gate in Android entry or RN root (prefer one place): read API level (native module or RN
   `Platform` / existing bridge if available).
2. Screen or modal with i18n keys EN/FR; link or plain text pointing at support expectation.
3. Do not call Auth API enroll/pending from this state.
4. Unit or simple component test for the gate condition; manual check on an API 30 emulator if available
   (may require a debug build that still compiles for &lt; 31 — only if you temporarily lower minSdk for
   that test, or use a pre-bump artifact; otherwise document “untestable on Play-filtered builds” and
   test the gate with a mocked API level).

### Out of scope

- Changing `minSdk` (owned by minsdk-31 handoff)
- MOB-012 Keystore flag gate
- Forcing Play Console listing edits (operator; already on minsdk ship checklist)
- iOS

---

## Suggested first agent turns

1. Confirm `minSdk` 31 is already on the branch (or operator waived ordering).
2. Read platform support canon § Play lifecycle (post-install messaging).
3. Implement gate + EN/FR copy; keep UI minimal.
4. Do not commit until the operator asks.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mobile-unsupported-os-ux.md and
ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md.

Confirm minSdk 31 is already implemented (HANDOFF-mobile-android-minsdk-31); if not, stop and report.

Task: add a sober in-app gate when Build.VERSION.SDK_INT < 31 that blocks MFA use and tells the
user to update the OS or use another device (EN+FR). Do not change minSdk. Do not implement
MOB-012. Do not create I-*/TB-*. Do not commit until I ask.
```
