# Grill Me — 2026-07-18 (Mobile hot language switch EN/FR)

## Session control

| Field | Value |
|-------|--------|
| **Backlog** | `I-2026-07-18-mobile-hot-language-switch` |
| **Tracer bullet** | `TB-2026-07-18-mobile-hot-language-switch` |
| **Lane** | `D` (post-delivery UX evolution) |
| **Status** | `complete` |
| **Date** | `2026-07-18` |
| **Captured by** | Marc |
| **Resume at** | _(closed)_ |

## Context

- EN/FR catalogs and Settings → Language already ship.
- v1 design choice (mobile PRD §10): persist locale, apply on next process start.
- Code: `changeAppLanguage()` persists only; Language screen shows restart alert.
- `AppNavigator` already uses `useTranslation()` for stack `title` / `headerBackTitle`, so a
  successful `i18n.changeLanguage()` should refresh header titles when the navigator re-renders.
- In-app Coming soon already advertises “Language switching without restart.”
- No protocol, crypto, or Auth API impact expected.

## Settled decisions

| ID | Decision | Source |
|----|----------|--------|
| G1 | **Acceptance bar:** Language screen, Settings (on back), and visible stack header titles update immediately; Home (or another primary screen) shows the new locale without app restart. Do **not** require every already-mounted screen to refresh in place before leave. | Operator Go 2026-07-18 |
| G2 | **Tactic (a):** persist + `await i18n.changeLanguage(next)` in `changeAppLanguage`; no forced navigator/provider remount. Escalate to remount **only** if Android debug smoke shows sticky titles/screens. Conceptual alternatives briefed (a hot apply / b remount / c keep restart). | Operator Go 2026-07-18 |
| G3 | **Silent success:** no restart alert and no toast after switch; radio + live string refresh is enough feedback; keep disable-during-pending guard. | Operator Go 2026-07-18 |
| G4 | **Same change set:** retire Coming soon language card; amend mobile PRD §10 to live-switch; update screens/architecture docs; update tests that assert restart alert. | Operator Go 2026-07-18 |
| G5 | **Validation:** unit/component + short manual Android debug smoke; **no** Maestro gate. | Operator Go 2026-07-18 |
| G6 | **Scope freeze:** no extra languages, OS auto-locale as primary control, iOS parity gate, or security/protocol work; no new `V-*`. | Analysis + `I-*` out of scope (unchallenged) |

## Critical questions and analysis

### Q1 — What is “applied everywhere” for acceptance?

Surfaces that matter for demos and daily use:

1. **Language screen** itself (intro, labels, “current”).
2. **Settings** list labels/subtitles (user often pops back one level).
3. **Stack header titles** (Language, Settings, Home, …) — navigator already binds `t(...)`.
4. **Primary flows** if already mounted under the stack (Home / Enrollment Detail / Pending) —
   typically via `useTranslation` on screen remount or language-changed re-render.

**Risk:** a screen that captured a string once outside `t` / `useTranslation` could stay stale until
left and re-entered. Unlikely for the main catalog path; verify rather than over-engineer.

**Settled as G1.**

### Q2 — Implementation tactic: `changeLanguage` only vs forced remount?

- **(a)** Persist + `await i18n.changeLanguage(next)` — tell the translation engine; subscribed UI
  re-renders. Low complexity; escalate only if smoke fails.
- **(b)** Forced remount of navigator/providers — quasi-restart of UI without killing the OS
  process; higher collateral risk (nav/state).
- **(c)** Keep app restart — rejects the idea.

**Settled as G2 = (a).**

### Q3 — Post-switch feedback UX?

**Settled as G3** — silent success.

### Q4 — Coming soon / PRD / docs in the same slice?

**Settled as G4** — yes, same change set.

### Q5 — Validation minimum?

**Settled as G5** — unit/component + manual Android smoke; no Maestro gate.

### Q6 — Scope freeze vs extras?

**Settled as G6** — keep freeze from `I-*`.

## Open questions (for operator)

_All resolved → G1–G6._

## Outcome

Grill complete 2026-07-18. Idea promoted to
[`TB-2026-07-18-mobile-hot-language-switch`](../TB-2026-07-18-mobile-hot-language-switch.md)
(`draft`, `single-pass`). Ready for implementation when the operator starts the coding slice.

## Links

- [`../ideas/I-2026-07-18-mobile-hot-language-switch.md`](../ideas/I-2026-07-18-mobile-hot-language-switch.md)
- [`../TB-2026-07-18-mobile-hot-language-switch.md`](../TB-2026-07-18-mobile-hot-language-switch.md)
- Implementation: `ezkey_mobile/app/i18n/index.ts`, `LanguageScreen.tsx`, `AppNavigator.tsx`
