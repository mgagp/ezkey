# Backlog Idea — `I-2026-05-24` Admin UI Vite 8 upgrade

## Metadata

- **ID:** `I-2026-05-24-admin-ui-vite8-upgrade`
- **Status:** `done`
- **Priority:** `P3`
- **Created at:** `2026-05-24`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Closed at:** `2026-05-24`
- **Phase tags:** `P3-comfort`, `toolchain`
- **Component tags:** `admin-ui`
- **Lane:** `A`
- **GitHub issue:** `#153`
- **Captured by:** Marc

## Intent

Upgrade the Admin UI build toolchain from **Vite 7** to **Vite 8** (Rolldown/Oxc unified bundler)
with coordinated dependency bumps, preserving production demo-mode stripping and Cloudflare Pages
build behaviour.

## Problem and value

- **Problem:** Admin UI remains on Vite 7 (`^7.3.2`) while Vite 8 is stable (since 2026-03-12,
  currently `8.0.x`). Staying on the previous major increases drift from the ecosystem (Tailwind Vite
  plugin peer deps, Vitest alignment) and misses modest build-time improvements in CI and local
  production builds.
- **Expected value:**
  - Align with the supported Vite major and ecosystem peers.
  - Faster production builds (modest absolute gain for this SPA size; meaningful in Cloudflare CI).
  - Reduce future upgrade friction by not accumulating a larger V7→V8 gap.
  - Validate the methodology + GitHub issue workflow for **toolchain / chore** work (first use).

## Scope

- **In scope:**
  - Bump `vite` to `^8.0.x`.
  - Bump `tailwindcss` and `@tailwindcss/vite` to `^4.2.2+` (official Vite 8 peer support).
  - Evaluate and align `vitest` (likely `^4.x`) and `@vitejs/plugin-react` (v6 required for Vite 8
    peer compatibility).
  - Update `AGENTS.md` and `README.md` stack references.
  - Run validation: unit tests, production build, `build:cloudflare:verify` (demo stripping).
  - Document execution evidence in the tracer bullet.

- **Out of scope:**
  - React Router, TanStack Query, Orval, or application feature changes.
  - Vite Devtools adoption, `resolve.tsconfigPaths`, or config refactors unrelated to the upgrade.
  - Playwright suite expansion (smoke only if cheap; not mandatory for this chore).
  - Intermediate `rolldown-vite` on Vite 7 (not warranted — config is minimal; see Grill Me).
  - `@vitejs/plugin-react` v6 + React Compiler path (optional follow-up).

## Key assumptions

- Current `vite.config.ts` is migration-friendly: no `rollupOptions`, no `esbuild` options, only
  standard plugins (`@vitejs/plugin-react`, `@tailwindcss/vite`).
- Node.js in local dev and Cloudflare Pages build is **≥ 20.19** (Vite 8 requirement, same as V7).
- Demo-mode stripping via `define: { 'import.meta.env.VITE_DEMO_MODE': '"false"' }` continues to
  tree-shake correctly under Rolldown.

## Risks and exceptions

- Rolldown bundler change may alter chunk graph or tree-shaking — **demo stripping** is the critical
  regression surface (`scripts/assert-no-demo-in-build.sh`).
- Peer dependency warnings if Tailwind is not bumped in the same change set.
- Vitest major bump may require minor config or import adjustments.
- Vite 8 install size ~15 MB larger (Rolldown + Lightning CSS) — acceptable for this project.

## Grill Me (lightweight — 2026-05-24)

Condensed challenge for a bounded toolchain upgrade (no separate grill-session file).

| # | Question | Answer / decision |
|---|----------|-------------------|
| G1 | Is Vite 8 stable enough? | Yes — stable since 2026-03-12; patch releases through `8.0.14`. |
| G2 | Two-step `rolldown-vite` path? | **No** — Admin UI config is minimal (~124 TS/TSX files, no custom Rollup/esbuild). Direct V8 upgrade matches official guidance for simple projects. |
| G3 | Urgency? | **P3** — V7.3.x is healthy; preventive maintenance, not a blocker. |
| G4 | Critical regression surface? | Demo-mode production stripping + Cloudflare build path. |
| G5 | Browser tests? | Optional smoke if stack is up; not a gate for a chore with no UI behaviour change. |
| G6 | Lane A for a chore? | **Yes** — first deliberate use of full I-*/TB-* + GitHub issue for toolchain work; documents validation and traceability without product vision overhead. |

## Promotion notes

Ready for tracer bullet `TB-2026-05-24-admin-ui-vite8-upgrade` with **single-pass** posture.

## Methodology notes (first toolchain upgrade via Lane A + GitHub issue)

- **No `V-*` vision note** — product direction unchanged; I-* + TB-* suffice.
- **Lightweight inline Grill Me** replaces a full grill-session file for low-ambiguity chores.
- **GitHub issue** created from Cursor (first time); `gh` CLI path on this workstation:
  `C:\Program Files\GitHub CLI\gh.exe`.
- **Candidate methodology follow-up:** ~~add a short pointer in `github-issues-workflow.md`~~
  **Done (2026-05-24):** branch naming harmonized in `github-issues-workflow.md`; Lane A clarified
  for bounded toolchain upgrades.

  - Intermediate `rolldown-vite` on Vite 7 (not warranted — config is minimal; see Grill Me).

## Close-out (2026-05-24)

- Delivered via PR [#154](https://github.com/mgagp/ezkey/pull/154); closes issue [#153](https://github.com/mgagp/ezkey/issues/153).
- Tracer bullet `TB-2026-05-24-admin-ui-vite8-upgrade` promoted.
- Residual: Playwright smoke optional before next Admin UI feature work; no open test debt on required gates.

## Links

- GitHub issue: `#153` (closed)
- GitHub PR: `#154`
- GitHub branch: `feature/153-i-2026-05-24-admin-ui-vite8-upgrade` (merged and deleted)
- Tracer bullet: `product-docs/global/backlog/TB-2026-05-24-admin-ui-vite8-upgrade.md`
- Test plan slice: `product-docs/global/backlog/test-plans/TSP-2026-05-24-admin-ui-vite8-upgrade.md`
- Admin UI config: `ezkey-admin-ui/vite.config.ts`
- Admin UI module notes: `ezkey-admin-ui/AGENTS.md`
- Vite 8 announcement: https://vite.dev/blog/announcing-vite8
- Vite 8 migration guide: https://vite.dev/guide/migration
