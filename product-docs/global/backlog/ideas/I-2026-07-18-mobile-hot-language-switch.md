# Backlog Idea — `I-2026-07-18-mobile-hot-language-switch` Mobile language switch without app restart

## Metadata

- **ID:** `I-2026-07-18-mobile-hot-language-switch`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-07-18`
- **Updated at:** `2026-07-18`
- **Last reviewed at:** `2026-07-18`
- **Closed at:** `2026-07-18`
- **Progression markers:** `P1-operability`
- **Component tags:** `mobile`
- **Lane:** `D` (post-delivery evolution — improve an already-shipped EN/FR preference so it applies at runtime)
- **Captured by:** Marc (session observation: language switch still requires stop/restart)

## Intent

Let the user switch the Ezkey mobile app between English and French **immediately**, without
closing and reopening the app, while keeping the existing simple preference model (manual selection
in Settings, English default, persisted locale).

## Problem and value

- **Problem:** The mobile app already supports EN/FR and a Settings language picker, but the first
  implementation deliberately applies the new locale only after restart. That is honest and stable,
  yet it feels unfinished: the preference is saved at once, then the UI asks the user to kill and
  relaunch the app. The in-app “Coming soon” copy already names this gap (“Language switching
  without restart”), but until this idea no canonical `I-*` / `TB-*` tracked it in product-docs.
- **Expected value:**
  - Smoother bilingual UX for demos, evaluators, and day-to-day use.
  - Alignment between product promise (Coming soon / docs) and delivered behavior.
  - A small, bounded UX slice that does not reopen security or protocol work.

## Scope

- **In scope:**
  - Apply the selected locale at runtime via `i18n.changeLanguage()` (or equivalent) after persist.
  - Refresh visible UI strings without requiring process restart (including Language screen copy and
    navigation chrome that depends on `react-i18next`).
  - Remove or replace the restart-required alert and related EN/FR strings once live switch works.
  - Update mobile conceptual docs that currently document restart-on-change
    (`MOBILE_SCREENS_AND_WIREFLOWS`, `MOBILE_STACK_AND_ARCHITECTURE`, and the mobile PRD design
    decision on localization).
  - Adjust unit/component tests that currently assert the restart message.
  - Keep English as default and the existing supported locale set (`en`, `fr`).
- **Out of scope:**
  - Additional languages beyond EN/FR.
  - Device/OS locale auto-detection as the primary control (manual Settings selection remains).
  - Native Android/iOS system UI localization beyond what the RN app already owns.
  - iOS parity as a release gate (Android-first remains the reference path).
  - Security, crypto, Auth API, or enrollment protocol changes.
  - Broader Settings redesign or new preference surfaces.

## Current-state observations (2026-07-18)

Captured during corpus + code review before creating this idea. Useful as a dossier for the next
session; not yet a design lock.

### Explicit product / copy signal (not a backlog ID)

| Location | What it says |
| -------- | ------------ |
| `ezkey_mobile/app/i18n/resources.ts` (`comingSoon.language`) | Title “Language switching without restart”; body says the current flow still asks for restart and that a future UX improvement should make changes more immediate. |
| `ezkey_mobile/PRD.md` design decision §10 | Localization keeps the first implementation simple by applying language changes **after app restart** rather than live-switching the full UI. |
| `ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md` | Language screen “persists English/French selection and asks for app restart to apply the change everywhere.” |
| `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md` | Locale preference “persisted locally and applied on next app restart.” |

### Implementation shape (why restart is required today)

1. `changeAppLanguage()` in `ezkey_mobile/app/i18n/index.ts` normalizes and **persists** the locale
   via `localeStorage.setLocale`, but does **not** call `i18n.changeLanguage()`.
2. `initializeI18n()` reads the persisted locale and applies it when the app providers mount
   (`AppProviders`); a later call can `changeLanguage` only if i18n is already initialized and the
   stored locale differs — that path is not used from the Language screen after selection.
3. `LanguageScreen` saves via `changeAppLanguage`, then shows
   `locale.restartRequiredTitle` / `locale.restartRequiredMessage`
   (“Close and reopen the app to apply the selected language everywhere.”).
4. Tests in `ezkey_mobile/__tests__/LanguageScreen.test.tsx` assert that restart messaging.

### Related backlog checked and ruled out

- `I-2026-05-29-mobile-stack-modernization` (`done`) — stack/toolchain; mentions i18n dependency
  hygiene only, not live locale switching.
- `I-2026-05-31-mobile-android-stack-followups` (`incubating`) — Android churn / Maestro / dependency
  follow-ups; no language UX slice.
- No matching `TB-*`, handoff, vision note, or roadmap line was found for hot language switch.
  Feature catalog entry `F-mobile-reference-app` covers the reference app broadly without this
  acceptance criterion.

**Conclusion of the audit:** the improvement was product-known (PRD decision + Coming soon + docs)
but **not** instantiated as a canonical backlog idea until this file.

## Key assumptions

- `i18next` / `react-i18next` can re-render subscribed screens when language changes at runtime;
  remaining gaps are mostly non-subscribed strings or one-time-resolved labels.
- Persistence in `localeStorage` remains the source of truth across restarts; hot switch must keep
  that write and then apply in-process.
- No native module restart is required for catalog-only string changes.
- This is operator-facing UX polish on an already-delivered bilingual surface, not a vision-level
  repositioning — no new `V-*` required unless grill reveals a wider localization strategy.

## Risks and exceptions

- **Partial refresh:** some screens or navigation titles may cache strings outside `useTranslation`
  and stay stale until remount; acceptance should define “everywhere that matters” and verify the
  main stacks (Home, Enrollment, Pending, Settings/About/Language).
- **Race / double select:** rapid taps while persist + `changeLanguage` are in flight (Language
  screen already disables during pending — keep that guard).
- **Doc/product drift:** mobile PRD decision §10 and Coming soon copy must be updated in the same
  change set so the corpus does not keep advertising restart after delivery.
- **Over-scope temptation:** do not expand into auto locale, more languages, or RTL in this idea.

## Promotion notes

Grill Me complete 2026-07-18 (G1–G6). Executed via
[`TB-2026-07-18-mobile-hot-language-switch`](TB-2026-07-18-mobile-hot-language-switch.md)
(`promoted`). Operator device smoke OK 2026-07-18. Idea closed `done`.

## Closeout (2026-07-18)

- Delivered: in-process EN/FR switch; Coming soon language item retired; PRD/docs/tests aligned.
- Evidence: `yarn validate:ci` + operator phone validation (see TB § Closeout).
- **GitHub issue posture:** canon sufficient; no issue opened.
- Residual: possible stale copy on future non-subscribed screens — keep using `useTranslation` / `t()`.
- Next: commit/PR when requested; index row on `main` post-merge.

## Links

- Grill Me (complete):
  [`../grill-sessions/2026-07-18-mobile-hot-language-switch-grill-me.md`](../grill-sessions/2026-07-18-mobile-hot-language-switch-grill-me.md)
- Tracer bullet:
  [`TB-2026-07-18-mobile-hot-language-switch.md`](TB-2026-07-18-mobile-hot-language-switch.md)
- Related features: `../../features-and-phases.md` (`F-mobile-reference-app`)
- Mobile PRD decision: `../../../../ezkey_mobile/PRD.md` (Localization — live-switch)
- Screens / architecture:
  - `../../../../ezkey_mobile/docs/MOBILE_SCREENS_AND_WIREFLOWS.md`
  - `../../../../ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`
  - `../../../components/mobile/screens-and-wireflow.md`
- Implementation entry points:
  - `../../../../ezkey_mobile/app/i18n/index.ts` (`changeAppLanguage`, `initializeI18n`)
  - `../../../../ezkey_mobile/app/screens/Language/LanguageScreen.tsx`
  - `../../../../ezkey_mobile/app/i18n/resources.ts` (`locale.*`)
  - `../../../../ezkey_mobile/__tests__/LanguageScreen.test.tsx`
  - `../../../../ezkey_mobile/__tests__/changeAppLanguage.test.ts`
- Adjacent (not owners of this UX):
  - [`I-2026-05-29-mobile-stack-modernization.md`](I-2026-05-29-mobile-stack-modernization.md)
  - [`I-2026-05-31-mobile-android-stack-followups.md`](I-2026-05-31-mobile-android-stack-followups.md)
- GitHub issue: _(none — deferred)_
)
