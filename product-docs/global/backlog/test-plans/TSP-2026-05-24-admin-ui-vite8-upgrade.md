# Test Plan Slice — `TB-2026-05-24` Admin UI Vite 8 upgrade

## Purpose

Define the minimum test set for the Vite 7 → 8 Admin UI toolchain upgrade.

## Metadata

- **Target:** `TB-2026-05-24-admin-ui-vite8-upgrade`
- **Date:** `2026-05-24`
- **Owner:** _(operator / agent executing the slice)_

## Change risk summary

- **Primary risk:** Production demo-mode code not tree-shaken after Rolldown bundler switch.
- **Secondary risk:** Vitest or Tailwind peer dependency mismatch breaks dev/test loop.
- **Components touched:** `admin-ui` only (tooling).

## Unit tests

- **Required:** `npm test` (Vitest) in `ezkey-admin-ui`.
- **Optional:** none.
- **Rationale:** Existing unit tests exercise client logic; confirms Vitest + Vite 8 integration.

## Functional tests

- **Required scenario(s):** none (no backend or Docker stack behaviour change).
- **Optional scenario(s):** none.
- **Rationale:** Toolchain-only change; no API contract or server workflow impact.

## Elective tests

- **Candidate(s):** none.
- **Run now?** no
- **Rationale:** N/A.

## Operational tests

- **Candidate(s):** none.
- **Run now?** no
- **Rationale:** N/A.

## UI tests (Playwright)

- **Candidate(s):** existing smoke suite (`npm run test:browser`).
- **Run now?** no (optional smoke if clean-start stack already running).
- **Rationale:** No UI behaviour change; risk-based deferral per Admin UI browser test autonomy.
- **UI testability notes (if deferred):** Re-run smoke before next Admin UI feature PR if this
  upgrade sits idle before merge.

## Build verification (toolchain-specific — required)

Run from `ezkey-admin-ui/` after dependency update:

| Step | Command | Pass criterion |
|------|---------|----------------|
| 1 | `npm install` | Clean install, no blocking peer errors |
| 2 | `npm test` | All Vitest tests pass |
| 3 | `npm run build` | Exit 0; `dist/` produced |
| 4 | `npm run build:cloudflare:verify` | Exit 0; demo stripping assertion passes |

## Execution evidence

- **Commands run:**
  - `npm install` in `ezkey-admin-ui`
  - `npm test` — 8 files, 54 tests passed (Vitest 4.1.7)
  - `npm run build` — Vite 8.0.14, built in ~354ms
  - `npm run build:cloudflare:verify` — OK, demo stripping assertion passed
- **Result summary:** All required TSP gates passed on branch `feature/153-i-2026-05-24-admin-ui-vite8-upgrade`.
- **Build time (informational):** Vite 8 production build ~354–600ms (no Vite 7 baseline captured in-session).
- **Follow-up test debt (if any):** Playwright smoke deferred (no UI behaviour change).

## Implementation notes (2026-05-24)

- Bumped `@vitejs/plugin-react` to v6 (v5 peer range excluded Vite 8).
- Rolldown did not DCE static demo preset arrays; wrapped exports in `isDemoMode ? [...] : []`
  in `demo-mode.ts` so production builds pass `assert-no-demo-in-build.sh`.
