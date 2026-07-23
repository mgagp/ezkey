# Handoff — Platform support floor annual review (template kickoff)

**Status:** `dormant` until **Next review due** on the canon (**2027-07**, or earlier if operator asks)  
**Lane:** Mobile product hygiene  
**Policy canon:** [`ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`](../../../../ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md) § Platform support floor — annual review

Use this prompt only when running the **annual** (or ad hoc) floor review — not for the initial
`minSdk` 31 bump.

---

## Operator intent

1. Refresh market + security-support inputs.
2. Confirm or adjust the Android floor (and later iOS when applicable).
3. Update the canon doc date / Next review due; open a new minsdk handoff only if the floor changes.

---

## Checklist (from canon)

1. Refresh Google Play / Android Studio distribution numbers.
2. Check Android Security Bulletin / EOL coverage for majors at/below the floor.
3. Re-check React Native / dependency `minSdk` constraints.
4. Decide: keep / raise / stage — update canon + Gradle in the same conversation if raising.
5. If raising: clone pattern from `HANDOFF-mobile-android-minsdk-31.md` for the new value.

---

## Out of scope

- Implementing MOB-* crypto findings
- Routine Dependabot / doctor-curated passes

---

## Paste-ready starter message

```text
Platform support floor — annual review.

Read ezkey_mobile/docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md and
product-docs/global/backlog/handoffs/HANDOFF-mobile-platform-support-annual-review.md.

Run the annual checklist. Propose keep vs raise with updated market/security notes.
Update the canon (and Next review due) only after I decide. Do not bump Gradle until I say so.
Do not create I-*/TB-* unless the change becomes a multi-sprint program.
```
