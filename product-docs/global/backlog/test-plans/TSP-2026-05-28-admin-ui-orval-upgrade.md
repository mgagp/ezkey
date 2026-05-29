# Test Plan Slice — `TB-2026-05-28` Admin UI Orval upgrade (8.5 → 8.13)

## Purpose

Define the **minimum and checkpoint-expanded** test set for the stepwise Orval migration program.
Each ladder step (see [`../TB-2026-05-28-admin-ui-orval-upgrade.md`](../TB-2026-05-28-admin-ui-orval-upgrade.md))
must satisfy the gates applicable to its tier before merge.

## Metadata

- **Target:** `TB-2026-05-28-admin-ui-orval-upgrade`
- **Related idea:** `I-2026-05-28-admin-ui-orval-upgrade`
- **Date:** `2026-05-28`
- **Owner:** operator / agent (multi-PR program)
- **Status:** `active`

## Change risk summary

- **Primary risk:** Generated TanStack Query hook / QueryKey contract drift breaks compile-time types
  and runtime cache invalidation (detail pages stale after mutations).
- **Secondary risk:** Docker / Cloudflare build fails because `generate:api` is mandatory in the
  builder stage.
- **Tertiary risk:** Discriminated union response types require page-level refactors.
- **Components touched:** `admin-ui` only (tooling + generated client consumers).

---

## Gate tiers

| Tier | Steps | Automated gates | Manual smoke | Playwright |
|------|-------|-----------------|--------------|------------|
| **T0 — Baseline** | Phase 0 | Full automated | Optional | No |
| **T1 — Low risk** | 8.5.3, 8.6.x, 8.7.0, 8.8.x, 8.9.x | Full automated | Abbreviated | Optional (run when stack up; agent default on branch) |
| **T2 — Checkpoint** | 8.10.0, 8.11.0 | Full automated + Docker path | Full | **Required** |
| **T3 — Final** | 8.12.x, 8.13.0 | Full automated + Cloudflare verify | Full | **Required** (8.13) |

---

## Automated gates (required every step)

Run from `ezkey-admin-ui/` **after** bumping Orval and syncing dependencies.

### Canonical sequence (agent-autonomous default)

Execute in order; abort the step if any command fails:

```bash
cd ezkey-admin-ui
npm install
npm ls orval
npm run generate:api
npm run lint
npm run build
npm test
```

Record in evidence: Orval version from `npm ls orval`, Orval banner from `generate:api`, and test
count from Vitest.

| # | Command | Pass criterion |
|---|---------|----------------|
| G0 | `npm install` | Lock applied; no blocking errors |
| G0b | `npm ls orval` | Version matches `package.json` pin |
| G1 | `npm run generate:api` | Exit 0; banner shows target version |
| G2 | `npm run lint` | Exit 0 |
| G3 | `npm run build` | Exit 0; `tsc -b && vite build` |
| G4 | `npm test` | All Vitest tests pass |
| G4b | `npm run test:browser` | 5/5 Playwright scenarios pass (when stack up; agent default on branch) |

**Docker-equivalent (required T2 + T3):**

| # | Command | Pass criterion |
|---|---------|----------------|
| G5 | `npm run generate:api && npm run build` | Exit 0 (mirrors `docker/Dockerfile` builder) |

**Cloudflare (required T3 final only):**

| # | Command | Pass criterion |
|---|---------|----------------|
| G6 | `npm run build:cloudflare:verify` | Exit 0; demo stripping assertion passes |

---

## Unit tests

- **Required:** `npm test` (Vitest) every step.
- **Focus areas when fixing codegen breaks:**
  - `src/lib/map-passwordless-wait-error.test.ts`
  - `src/lib/ip-whitelist-validation.test.ts`
  - `src/lib/admin-pending-activation-eligibility.test.ts`
- **Rationale:** Fast regression signal; confirms test runner + TS paths still work. Does **not**
  alone prove Orval hook compatibility — build (`tsc`) is the primary codegen gate.

---

## Functional tests

- **Required:** none at the Docker stack level for intermediate T1 steps (no backend contract
  change intended).
- **Implicit functional coverage:** manual smoke + Playwright at T2/T3 validate Admin UI ↔ Admin
  API integration through the generated client.

---

## UI tests (Playwright)

### When required

| Step | Run `npm run test:browser`? |
|------|----------------------------|
| 8.5.3 – 8.9.x | No (unless unexpected runtime regression in manual smoke) |
| **8.10.0** | **Yes** — full existing suite |
| **8.11.0** | **Yes** — full existing suite |
| 8.12.x | Recommended |
| **8.13.0** | **Yes** — full existing suite (program closeout) |

### Preconditions

- Clean-start Docker stack (Admin API, Auth API, Demo Device seeded).
- Admin UI via Playwright webServer or `./scripts/run-ui-tests.sh`.

### Rationale

Query-key and mutation-hook changes affect login, list/detail navigation, and post-mutation refresh
— the core flows covered by the existing browser suite. Risk-based escalation per
[`../../../methodology/testing-strategy-in-workflow.md`](../../../methodology/testing-strategy-in-workflow.md).

---

## Manual exploratory smoke

### Abbreviated (T1 — after merge, ~5 minutes)

Assumes clean-start stack + Admin UI dev/preview:

1. Login as Global Admin (passwordless with Demo Device).
2. **Integrations** list — pagination, open one integration detail.
3. **Admins** list — open one admin detail (exercises `getGetAdminByIdQueryKey` path).
4. Perform one low-risk mutation with reason field (e.g. deactivate/reactivate on a test entity if
   available in demo data) — confirm detail badge/status updates without hard refresh.
5. Logout.

**Pass:** no console errors; mutation visible on detail view; list reflects change after navigation
back.

### Full (T2/T3 checkpoints — ~10–15 minutes)

Abbreviated path **plus**:

6. **Enrollments** — open detail; expand related details panel if FKs present
   (`use-expandable-related-details.ts`).
7. **Encryption Keys** (Global Admin) — page loads; one read-only operational panel interaction.
8. **Audit logs** — list loads with date filter preset.
9. **Tenant detail** (if tenant exists) — timezone display (`display-timezone-context.tsx`).
10. Repeat one **irreversible or reason-gated** action only in demo-safe scope (follow demo-mode
    presets).

**Pass:** all screens load; invalidation behaviour correct on steps 4 and 6.

---

## Per-step evidence template

Copy into PR description or append below as steps complete.

```markdown
### Step N — Orval 8.x.y

- Branch:
- Orval: 8.x.y (exact pin in package.json)
- Commands:
  - [ ] G1 generate:api — pass/fail
  - [ ] G2 lint — pass/fail
  - [ ] G3 build — pass/fail
  - [ ] G4 npm test — N tests pass
  - [ ] G5 docker-equivalent — pass/fail (if T2/T3)
  - [ ] G6 cloudflare verify — pass/fail (if T3 final)
- App files changed (if any):
- Manual smoke: abbreviated / full — pass/fail
- Playwright: run / skipped — pass/fail
- Notes:
```

---

## Execution evidence

### Baseline (2026-05-28 dependency audit — pre-migration)

Recorded during the Admin UI npm audit that led to Orval pin:

- Routine deps updated; Orval held at `~8.5.2`.
- `npm run generate:api` at **8.12.3** → `npm run build` fails (TypeScript errors across generated
  client consumers).
- `npm run generate:api` at **8.5.2** → build passes after eslint/tsconfig collateral fixes from
  that session.
- **Decision:** defer Orval; create I-* / TB-* / TSP-* migration program.

### Step 1 — Orval 8.5.3 (2026-05-28)

- Branch: `feature/175-i-2026-05-28-admin-ui-orval-upgrade`
- Orval: `8.5.3` (exact pin in `package.json`)
- App files changed: none — lockfile only
- Commands:
  - [x] G1 `generate:api` — pass (orval v8.5.3)
  - [x] G2 `lint` — pass
  - [x] G3 `build` — pass
  - [x] G4 `npm test` — 8 files, 54 tests pass
- Manual smoke: deferred (T1 tier; optional abbreviated)
- Playwright: skipped (T1)
- Notes: patch-only bump from `~8.5.2`; validates migration playbook before 8.6.x

### Step 2 — Orval 8.6.1 (2026-05-28)

- Branch: `feature/175-i-2026-05-28-admin-ui-orval-upgrade`
- Orval: `8.6.1` (exact pin; **8.6.0 skipped** — npm install fails with `Unsupported URL Type "workspace:"` on broken publish)
- App files changed: none — lockfile only
- Commands:
  - [x] G1 `generate:api` — pass (orval v8.6.1)
  - [x] G2 `lint` — pass
  - [x] G3 `build` — pass
  - [x] G4 `npm test` — 8 files, 54 tests pass
- Manual smoke: deferred (T1)
- Playwright: skipped (T1)
- Notes: first installable 8.6.x line; 8.6.2 follows as step 3 (patch stabilize)

### Step 3 — Orval 8.6.2 (2026-05-28)

- Orval: `8.6.2` (latest 8.6.x patch)
- App files changed: none — lockfile only
- Commands: G1–G4 all pass (54 tests)
- Notes: completes 8.6.x line; next step **8.7.0**

### Step 4 — Orval 8.7.0 (2026-05-28)

- Orval: `8.7.0` (canonical sequence G0–G4 executed by agent)
- App files changed: none — lockfile only
- `npm ls orval` → `orval@8.7.0`; generate banner `v8.7.0`; 54 tests pass
- Notes: TB/TSP amended with reproducible validation sequence for agent autonomy

### Step 5 — Orval 8.8.0 (2026-05-28)

- Orval: `8.8.0`; canonical G0–G4 pass; 54 Vitest tests
- Playwright: **5/5 passed** (~12.8s) — agent-autonomous run with stack up
- App files changed: none — lockfile only
- Notes: step 6 target **8.8.1** (8.8.x patch stabilize)

### Step evidence (fill as PRs merge)

| Step | Version | Date | PR | G1–G4 | Manual | Playwright |
|------|---------|------|-----|-------|--------|------------|
| 0 | baseline | 2026-05-28 | — | audit recorded | — | — |
| 1 | 8.5.3 | 2026-05-28 | pending | pass | deferred (T1) | skipped (T1) |
| 2 | 8.6.1 | 2026-05-28 | pending | pass | deferred (T1) | skipped (T1) |
| 3 | 8.6.2 | 2026-05-28 | pending | pass | deferred (T1) | skipped (T1) |
| 4 | 8.7.0 | 2026-05-28 | pending | pass | deferred (T1) | skipped (T1) |
| 5 | 8.8.0 | 2026-05-28 | pending | pass | deferred | **5/5 pass** |
| 6 | 8.9.1 | | | | | |
| 7 | 8.10.0 | | | | | |
| 8 | 8.11.0 | | | | | |
| 9 | 8.12.3 | | | | | |
| 10 | 8.13.0 | | | | | |

---

## Close-out

Program complete when:

- Step 10 row filled with all gates pass.
- [`../TB-2026-05-28-admin-ui-orval-upgrade.md`](../TB-2026-05-28-admin-ui-orval-upgrade.md) exit
  criteria met.
- [`ideas/I-2026-05-28-admin-ui-orval-upgrade.md`](ideas/I-2026-05-28-admin-ui-orval-upgrade.md)
  transitioned to `done`.

---

## Links

- Tracer bullet: [`../TB-2026-05-28-admin-ui-orval-upgrade.md`](../TB-2026-05-28-admin-ui-orval-upgrade.md)
- Backlog idea: [`ideas/I-2026-05-28-admin-ui-orval-upgrade.md`](ideas/I-2026-05-28-admin-ui-orval-upgrade.md)
- Admin UI browser tests: `ezkey-admin-ui/README.md` (Browser Tests section)
- Testing methodology: [`../../methodology/testing-strategy-in-workflow.md`](../../methodology/testing-strategy-in-workflow.md)
