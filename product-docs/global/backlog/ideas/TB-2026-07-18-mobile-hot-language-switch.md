# Tracer Bullet Brief — `TB-2026-07-18-mobile-hot-language-switch` Mobile language switch without restart

## Metadata

- **ID:** `TB-2026-07-18-mobile-hot-language-switch`
- **Status:** `promoted`
- **Related idea:** `I-2026-07-18-mobile-hot-language-switch`
- **Lane:** `D`
- **Posture:** `single-pass`
- **Created at:** `2026-07-18`
- **Updated at:** `2026-07-18`
- **Closed at:** `2026-07-18`
- **Captured by:** Marc
- **GitHub issue:** _(none — canon sufficient; optional issue deferred)_

## Objective

Prove that choosing English or French in Settings → Language applies **immediately** in-process
(no app restart), while the preference remains persisted for the next cold start, and product copy /
docs no longer describe restart-first behavior.

## Boundaries in scope

- `ezkey_mobile` i18n runtime (`changeAppLanguage` / `initializeI18n`)
- Language screen UX (drop restart alert; silent success)
- EN/FR string catalogs (`resources.ts`) — Coming soon language card retirement + locale intro copy
- Mobile conceptual docs + mobile PRD localization decision
- Unit/component tests for language change behavior

## Out of scope

- Additional languages, OS auto-locale as primary control, RTL
- Forced navigator/provider remount unless Android smoke proves sticky UI (escalation only)
- Maestro gate
- iOS parity as release gate (Android-first reference)
- Auth API / crypto / enrollment protocol / security settings

## First executable slice

1. **`changeAppLanguage`:** after `localeStorage.setLocale`, `await i18n.changeLanguage(nextLocale)`
   (normalize first; no-op path stays safe).
2. **`LanguageScreen`:** remove restart `Alert`; keep pending/disable guard; rely on live `t()` refresh.
3. **Copy:** retire `comingSoon.language` (and any About/Coming soon bullets that still promise
   restart-tied language UX); update `locale.intro` so it no longer says “applied after restart.”
4. **Docs:** mobile PRD §10 → live-switch; `MOBILE_SCREENS_AND_WIREFLOWS` Language row;
   `MOBILE_STACK_AND_ARCHITECTURE` localization note.
5. **Tests:** update `LanguageScreen` tests; add/adjust coverage that `changeAppLanguage` applies
   language in-process.
6. **Validate:** `yarn validate:ci` (or equivalent mobile CI path); short Android debug manual smoke
   per G1/G5.

## Rollback or fallback posture

- Revert the change set restores restart-first behavior.
- If smoke shows sticky headers/strings: escalate to a **narrow** remount tactic (Grill G2), do not
  silently keep restart messaging.

## Critical flows

| Flow | Nominal | Exception |
|------|---------|-----------|
| Switch language | Select other locale → UI strings + headers update; no restart alert | Persist or `changeLanguage` fails → keep disable guard; do not claim success |
| Navigate after switch | Back to Settings / Home shows new locale | Stale non-subscribed string → leave/re-enter screen; escalate remount only if systemic |
| Cold start | Kill app → relaunch keeps selected locale | Storage read failure → existing default/fallback (`en`) |

## Evidence plan

### Unit / component (required)

- `changeAppLanguage` persists and calls `i18n.changeLanguage`
- Language screen: no restart alert; selection updates “current” / intro in the new locale path

### Manual exploratory (required — Android debug)

1. Settings → Language → EN→FR: Language + header update; no restart dialog.
2. Back to Settings (and Home): new locale without restart.
3. Kill and reopen app: preference still applied.
4. FR→EN: same checks.

### Maestro

- **Deferred** — not a gate (Grill G5).

### Documentation (required — same change set)

- `ezkey_mobile/PRD.md` § Localization
- `ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md`
- `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`
- In-app Coming soon / locale strings in `resources.ts`
- Component pack: `product-docs/components/mobile/screens-and-wireflow.md` (i18n note)

## Quality gates

- analysis gate — current restart path documented on `I-*` ✅
- design gate — Grill Me G1–G5 (+ G6 scope freeze) confirmed ✅
- implementation gate — code + unit/component + Android smoke ✅
- traceability gate — I / grill / TB linked ✅; `F-mobile-reference-app` remains `implemented` (UX
  refinement, no new feature ID); global/component `spec-test-traceability` skipped (no API contract
  change); backlog `index.md` deferred to post-merge on `main`

## Exit criteria

1. Hot switch meets Grill **G1** acceptance bar without process restart. ✅
2. No restart-required alert (**G3**); Coming soon language item retired; PRD/docs/tests aligned (**G4**). ✅
3. Unit/component tests green; Android manual smoke recorded (**G5**). ✅
4. Persistence across kill/relaunch still works. ✅
5. Remount escalation unused unless smoke forced it (then document why in TB closeout). ✅ unused

## Grill decisions (imported)

| ID | Decision |
|----|----------|
| G1 | Language + Settings + headers live; Home without restart; no in-place refresh mandate for every mounted screen |
| G2 | Persist + `changeLanguage`; remount only if smoke fails |
| G3 | Silent success |
| G4 | Docs / Coming soon / tests in same change set |
| G5 | Unit/component + manual Android smoke; no Maestro gate |
| G6 | Scope freeze (no extra languages / auto-locale / iOS gate / security) |

## Closeout (2026-07-18)

### Evidence summary

- **Code:** `changeAppLanguage` persists then `i18n.changeLanguage`; Language screen silent success;
  Coming soon language card + release-notes bullet retired; locale intro copy updated.
- **Automated:** `yarn validate:ci` green (174 tests), including `changeAppLanguage.test.ts` and
  updated `LanguageScreen` / `ComingSoonScreen` tests.
- **Manual:** Operator validated OK on physical device (Pixel 7 Pro debug install) — hot EN↔FR,
  headers/Settings/Home, persistence after kill/relaunch. Remount escalation **not** needed.
- **Docs:** mobile PRD (design decision §10 + NFR Localization),
  `MOBILE_SCREENS_AND_WIREFLOWS`, `MOBILE_STACK_AND_ARCHITECTURE`, component
  `screens-and-wireflow` i18n note.

### Deferred

- Maestro coverage for language switch (explicit non-gate).
- Backlog index row — post-merge on `main` (multi-branch convention).
- GitHub issue — not required for this single-module UX slice; open only if a PR needs a board anchor.

### Residual risks

- A future screen that caches copy outside `useTranslation` / `t()` could show a stale string until
  remount; unlikely for current subscribed surfaces. Mitigate by keeping i18n usage consistent.

### Next action

- Commit/PR when the operator requests it (closeout complete before commit).
- No follow-up `I-*` / `TB-*` from this slice.

## Links

- Idea: [`I-2026-07-18-mobile-hot-language-switch.md`](I-2026-07-18-mobile-hot-language-switch.md)
- Grill Me:
  [`../grill-sessions/2026-07-18-mobile-hot-language-switch-grill-me.md`](../grill-sessions/2026-07-18-mobile-hot-language-switch-grill-me.md)
- Feature: `F-mobile-reference-app`
