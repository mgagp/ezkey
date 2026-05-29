# Backlog Idea — `I-2026-05-28` Admin UI Orval upgrade (8.5 → 8.13)

## Metadata

- **ID:** `I-2026-05-28-admin-ui-orval-upgrade`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-05-28`
- **Updated at:** `2026-05-29`
- **Last reviewed at:** `2026-05-28`
- **Phase tags:** `P2-maintainability`, `toolchain`
- **Component tags:** `admin-ui`
- **Lane:** `A`
- **GitHub issue:** `#175`
- **Captured by:** Marc (dependency audit session, materialized 2026-05-28)

## Outcome (2026-05-29)

Migration completed on branch `feature/175-i-2026-05-28-admin-ui-orval-upgrade` (14 ladder commits,
single PR closing `#175`). Orval **8.13.0** with `^8.13.0` pin; config fix at checkpoints 8.10/8.11
(verb-aware defaults only). No application page refactors required.

## Intent

Upgrade the Admin UI OpenAPI client generator (**Orval**) from the current pinned baseline
(`~8.5.2`) to the latest stable **8.13.x**, through a **stepwise, validated migration** — not a
single bulk bump.

This work was discovered during a routine Admin UI dependency audit (2026-05-28) that upgraded
most npm dependencies with minimal risk. **Orval was intentionally excluded** after proving that
`npm run generate:api` at **8.12.x / 8.13.x** breaks TypeScript compilation against the existing
application code.

## Problem and value

### Problem

- The Admin UI consumes a **generated TanStack Query v5 client** from `openapi-spec.json` via Orval
  (`orval.config.ts`). Output lives in `src/generated/admin-api/` (gitignored; regenerated at build
  time in Docker and Cloudflare builds).
- The lockfile had drifted toward Orval **8.12.3** while application code still assumes the **8.5.x
  generation contract** (query-key factories, hook shapes, DTO access patterns).
- A naive `npm update orval` + `generate:api` produces hundreds of TypeScript errors — this is not
  a patch-level chore; it is a **contract migration**.

### Expected value

- Close the Orval gap safely (security patches, bug fixes, TS 6 alignment, query generator fixes).
- Restore confidence that `npm ci` + `generate:api` + `build` is reproducible on every workstation
  and in Docker.
- Document the generation contract and migration checkpoints so future OpenAPI / Orval changes do
  not surprise the team again.
- Unblock Dependabot Orval PRs once the ladder is complete.

## Scope

### In scope

- Orval version ladder **8.5.2 → 8.13.0** (one explicit target version per PR).
- Regenerated client under `src/generated/admin-api/` validated via `tsc` after each step.
- Application code adjustments required by generation contract changes (especially at **8.10** and
  **8.11** checkpoints).
- `orval.config.ts` decisions (global `useQuery` / `useMutation`, per-operation overrides if
  needed).
- Admin UI docs updates: `AGENTS.md`, `README.md` (Orval pin policy, validation commands).
- Docker alignment: `ezkey-admin-ui/docker/Dockerfile` already runs `generate:api` before build —
  each step must keep that path green.
- Test plan slice `TSP-2026-05-28-admin-ui-orval-upgrade.md` with per-phase gates.
- Traceability via tracer bullet `TB-2026-05-28-admin-ui-orval-upgrade.md`.

### Out of scope

- OpenAPI spec content changes (unless a step exposes a pre-existing spec defect — then fix in a
  **separate** Admin API / spec-refresh change set).
- Hand-editing files under `src/generated/**`.
- Migrating other Orval consumers (`ezkey-sdk`, mobile — separate ideas if needed).
- Replacing Orval with another generator.
- Broad Playwright suite expansion (targeted smoke only at high-risk checkpoints).

## Context — dependency audit outcome (2026-05-28)

The companion Admin UI npm dependency batch (non-Orval) was completed successfully:

- Product deps bumped within semver (i18next, lucide-react, react-router-dom, etc.).
- Tooling bumped (typescript-eslint, Playwright Docker image 1.60.0, eslint-plugin-react-refresh
  0.5.x config fix).
- Orval **pinned** at `~8.5.2` after validation proved 8.12+ breaks codegen compatibility.

**Lesson:** toolchain bumps are not uniformly “minor”. Orval is a **code generator** whose output
is the Admin UI’s API boundary — treat it like a schema migration.

## Key assumptions

- Canonical OpenAPI input remains `ezkey-admin-ui/openapi-spec.json` (copy of
  `specs/admin-api/openapi-spec.json`).
- Current application patterns (documented in `src/lib/query-keys.ts`, `AGENTS.md`) remain the
  target UX until a checkpoint explicitly changes them.
- Node **22** in Docker and Node **20+** locally remain valid for Orval 8.x.
- Each migration step gets its own PR (no mixed Orval bump + feature work).

## Risks and exceptions

| Risk | Severity | Mitigation |
|------|----------|------------|
| Query key shape changes (8.10+) | **High** | Dedicated checkpoint PR; update invalidations and `use-expandable-related-details.ts` |
| GET exposed as mutation hooks (8.11+) | **High** | Audit pages using `.mutate()` vs `useQuery`; config review |
| Discriminated union response types | **Medium** | Normalize in mutator or adapt page code (`getXxxResponseSuccess`) |
| `useQuery: true` + `useMutation: true` together | **High** | Decide config strategy at 8.10 spike (see TB) |
| Docker build fails silently in CI if generate breaks | **High** | Required gate: `npm run generate:api && npm run build` every step |
| Dependabot re-opens 8.12+ PRs during migration | **Low** | Pin Orval explicitly; close/rebase Dependabot PRs with link to TB |

## Grill Me (inline — 2026-05-28)

| # | Question | Answer / decision |
|---|----------|-------------------|
| G1 | Single PR to latest Orval? | **No** — proven breaking at 8.12+; stepwise ladder mandatory. |
| G2 | Patch-first within 8.5.x? | **Yes** — 8.5.2 → **8.5.3** as process validation step. |
| G3 | Walk every minor 8.0–8.13? | **No** — start from **8.5.2** baseline; group low-risk minors; treat **8.10** and **8.11** as functional checkpoints. |
| G4 | Browser tests every step? | **No** — required at **8.10**, **8.11**, and final **8.13**; optional smoke on intermediate minors if cheap. |
| G5 | Lane A without V-*? | **Yes** — toolchain / maintainability; I-* + TB-* + TSP suffice (same pattern as Vite 8 upgrade). |
| G6 | When to relax Orval pin? | After **8.13.0** validated — return to caret range or document ongoing pin policy in `AGENTS.md`. |

## Promotion notes

Ready for tracer bullet **`TB-2026-05-28-admin-ui-orval-upgrade`** with **`multi-pass`** posture
(one PR per ladder step; TB tracks overall program exit criteria).

## Links

- GitHub issue: `#175` — https://github.com/mgagp/ezkey/issues/175
- Tracer bullet: [`../TB-2026-05-28-admin-ui-orval-upgrade.md`](../TB-2026-05-28-admin-ui-orval-upgrade.md)
- Test plan slice: [`../test-plans/TSP-2026-05-28-admin-ui-orval-upgrade.md`](../test-plans/TSP-2026-05-28-admin-ui-orval-upgrade.md)
- Orval config: `ezkey-admin-ui/orval.config.ts`
- Mutator: `ezkey-admin-ui/src/lib/orval-mutator.ts`
- Query key conventions: `ezkey-admin-ui/src/lib/query-keys.ts`
- Admin UI agent notes: `ezkey-admin-ui/AGENTS.md`
- Orval v8 migration guide: https://orval.dev/docs/versions/v8
- Prior related chore: [`I-2026-05-24-admin-ui-vite8-upgrade.md`](I-2026-05-24-admin-ui-vite8-upgrade.md)
