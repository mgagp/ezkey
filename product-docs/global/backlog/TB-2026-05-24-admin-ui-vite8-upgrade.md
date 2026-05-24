# Tracer Bullet Brief — `TB-2026-05-24` Admin UI Vite 8 upgrade

## Metadata

- **ID:** `TB-2026-05-24-admin-ui-vite8-upgrade`
- **Status:** `draft`
- **Posture:** `single-pass`
- **Related idea:** `I-2026-05-24-admin-ui-vite8-upgrade`
- **Lane:** `A`
- **GitHub issue:** `#153`
- **Created at:** `2026-05-24`
- **Updated at:** `2026-05-24`

## Objective

Upgrade `ezkey-admin-ui` from Vite 7 to Vite 8 in one bounded change set, prove production and
Cloudflare build paths still work, and confirm demo-mode stripping remains intact.

## Boundaries in scope

- **Admin UI toolchain:** `package.json` devDependencies (`vite`, `@tailwindcss/vite`,
  `tailwindcss`, `vitest`, optionally `@vitejs/plugin-react`).
- **Admin UI config:** `vite.config.ts` — change only if migration requires it (expect none or
  minimal).
- **Admin UI docs:** `AGENTS.md`, `README.md` stack version references.

## Out of scope

- Application pages, components, routes, API client, or generated Orval code.
- Backend modules, Docker stack, OpenAPI spec refresh.
- Playwright scenario additions.
- Vite Devtools or experimental Rolldown options.

## Critical flows

### Nominal path

1. Update dependencies in `ezkey-admin-ui/package.json`.
2. `npm install` in `ezkey-admin-ui`.
3. `npm test` — Vitest unit tests pass.
4. `npm run build` — TypeScript + Vite production build succeeds.
5. `npm run build:cloudflare:verify` — Cloudflare mode build + demo stripping assertion passes.

### Critical exception paths

- **Demo stripping failure:** `assert-no-demo-in-build.sh` fails → investigate Rolldown
  tree-shaking / `define` behaviour before merge.
- **Peer dependency conflict:** resolve Tailwind and Vitest versions in the same commit.
- **Vitest config breakage:** adjust `vite.config.ts` test block or Vitest import if types change.

## Evidence plan

- Commands and results recorded in test plan slice
  `TSP-2026-05-24-admin-ui-vite8-upgrade.md`.
- Before/after `npm run build` wall time noted (informational, not a gate).
- PR body references GitHub issue and traceability block per `github-issues-workflow.md`.

## Quality gates

- **Analysis gate:** I-* Grill Me complete; upgrade path and out-of-scope explicit.
- **Design gate:** dependency matrix decided (Vite 8 + Tailwind 4.2.2+ + Vitest alignment).
- **Implementation gate:** all required tests in TSP pass; docs updated.

## Exit criteria

`TB-2026-05-24` is validated when:

1. `vite` is on `^8.0.x` and `npm run build` succeeds.
2. `npm run build:cloudflare:verify` passes (demo stripping intact).
3. `npm test` passes.
4. `AGENTS.md` / `README.md` reference Vite 8.
5. GitHub issue closed via PR with traceability block.

## Links

- [`I-2026-05-24-admin-ui-vite8-upgrade.md`](ideas/I-2026-05-24-admin-ui-vite8-upgrade.md)
- [`test-plans/TSP-2026-05-24-admin-ui-vite8-upgrade.md`](test-plans/TSP-2026-05-24-admin-ui-vite8-upgrade.md)
- [`../../methodology/github-issues-workflow.md`](../../methodology/github-issues-workflow.md)
