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

- **Commands run:** _(fill at implementation)_
- **Result summary:** _(fill at implementation)_
- **Build time (informational):** Vite 7 baseline ___ s → Vite 8 ___ s
- **Follow-up test debt (if any):** _(none expected)_
