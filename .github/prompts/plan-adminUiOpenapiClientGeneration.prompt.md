# Plan: Orval-Based API Client Generation for Admin UI

## Context & Analysis

### Current State

- `ezkey-demo-device/pom.xml` uses `openapi-generator-maven-plugin` v7.19.0, generating **only models** (DTOs) into `target/` (not committed), consumed by hand-written service classes. This is the established Java-side pattern.
- `ezkey-admin-ui/src/types/models.ts` and `src/types/api.ts` are **100% hand-written** TypeScript types — a maintenance liability as the backend evolves.
- `src/lib/api-client.ts` is a hand-written generic `fetch` wrapper.
- `src/hooks/use-integrations.ts` and similar hooks are hand-written TanStack Query hooks.
- `scripts/update-specs.sh` propagates the canonical spec to `ezkey-demo-device/`, `ezkey-demo-app-acme/`, and `ezkey-sdk/` — but **not to `ezkey-admin-ui/`**.
- No TypeScript codegen tooling exists anywhere in the project.

---

## Tooling Options Evaluated

| Tool | Output | TanStack Query integration | Zod schemas | Runtime deps | Notes |
|---|---|---|---|---|---|
| **Orval** | Types + React Query hooks + Zod | ✅ Native, v5 supported | ✅ Optional | `@tanstack/react-query` already present | **Best fit** |
| `openapi-typescript` + `openapi-fetch` | Types only + typed fetch wrapper | Manual wiring | ❌ | Very lightweight | Good if hooks generation is unwanted |
| `swagger-typescript-api` | Full API class | Manual wiring | ❌ | Standalone | Verbose, less tree-shakeable |
| `@openapitools/openapi-generator-cli` | Types + client class | Manual wiring | ❌ | JVM required at build | Consistent with Java side but heavyweight |

### Decision: Orval

The project already uses `@tanstack/react-query` v5, `react-hook-form` + `zod` v4.
Orval generates TanStack Query hooks directly from the OpenAPI spec, eliminating hand-written hooks
and hand-written types in one codegen step. This mirrors the Java pattern: generate only what is
needed (`generateApis=false` on the Java side → here we generate hooks + types, no boilerplate).

---

## Goal

Realign the entire Admin UI codebase in a single work session: replace all hand-written DTOs, types,
and hooks with Orval-generated code driven by the canonical OpenAPI spec, integrated into the npm
build pipeline. Since the project is in full development mode with no production deployment or
existing consumers, the migration is done as a clean replacement — no backward-compatibility shims,
no dual-path code. The end state must compile cleanly, all pages must render correctly, and the
generation pipeline must be validated from end to end before closing the session.

---

## Steps

### 1. Extend update-specs scripts

Extend `scripts/update-specs.sh` and `scripts/update-specs.bat` to also copy/symlink the admin-api
spec into `ezkey-admin-ui/openapi-spec.json` — one additional block after the existing SDK
propagation, in both `--admin-only` and `--all` modes.

### 2. Update CLI Python refresh command

Update `ezkey-cli-python/ezkey_cli/commands/openapi.py` to also write
`ezkey-admin-ui/openapi-spec.json` when refreshing `--app` or `--all`, keeping full parity with
the shell script.

### 3. Update .gitignore for Admin UI

Add `openapi-spec.json` and `src/generated/` to `ezkey-admin-ui/.gitignore` — treat as generated
artifacts, never committed. Consistent with Java's `target/` approach.

### 4. Install Orval

Add `orval` as a dev dependency in `ezkey-admin-ui/package.json`. No new runtime dependencies are
required when using the native `fetch` client option (Orval uses the browser's built-in `fetch`).

```bash
npm install --save-dev orval
```

### 5. Create orval.config.ts

Create `ezkey-admin-ui/orval.config.ts`:

- **Input:** `./openapi-spec.json`
- **Output target:** `./src/generated/admin-api/`
- **Mode:** `tags-split` — one file per OpenAPI tag (integrations, enrollments, admins, etc.)
- **Client:** `react-query` — generates TanStack Query v5 hooks
- **Schemas output:** `./src/generated/admin-api/model/` — TypeScript interfaces
- **Zod schemas:** `./src/generated/admin-api/zod/` — optional, can replace hand-written Zod form schemas
- **`clean: true`** — removes stale generated files
- **Base URL:** resolved from `VITE_API_BASE_URL` env var via a custom fetch mutator

### 6. Create Orval custom fetch mutator

Create `ezkey-admin-ui/src/lib/orval-mutator.ts` — a thin wrapper around the existing
`src/lib/api-client.ts` that wires Orval-generated hooks to the project's authentication/session
logic (injects Bearer token header, handles 401 redirect). The existing `api-client.ts` becomes
the backbone; it is not deleted.

### 7. Add npm scripts

Add the following scripts to `ezkey-admin-ui/package.json`:

| Script | Command | Purpose |
|---|---|---|
| `generate:api` | `orval --config orval.config.ts` | Run codegen on demand |
| `predev` | `npm run generate:api` | Auto-regenerate before `npm run dev` |
| `prebuild` | `npm run generate:api` | Auto-regenerate before `npm run build` |
| `generate:check` | `orval --config orval.config.ts` | CI validation (spec must be present) |

### 8. Replace hand-written types and hooks (full, single session)

Since the project is in active development with no production consumers, this is a clean replacement
done in one pass — no incremental dual-path approach:

1. **Delete** `src/types/models.ts` and `src/types/api.ts` entirely.
2. **Delete** `src/hooks/use-integrations.ts` and `src/hooks/use-paginated-query.ts`.
3. Update all import sites across pages and components to use generated types from
   `src/generated/admin-api/model/` and generated hooks from `src/generated/admin-api/`.
4. Replace hand-written Zod schemas in pages and forms with generated Orval Zod schemas.
5. Run `tsc --noEmit` after each file to catch breakage immediately rather than at the end.
6. The session is not complete until `npm run build` exits with code 0 and all pages render
   correctly in `npm run dev`.

### 9. Target folder structure

```
ezkey-admin-ui/
  openapi-spec.json               ← copied by update-specs.sh (gitignored)
  orval.config.ts                 ← committed
  src/
    generated/                    ← gitignored, regenerated at build time
      admin-api/
        model/                    ← TypeScript interfaces (DTOs)
        integrations.ts           ← TanStack Query hooks per tag
        enrollments.ts
        admins.ts
        api-keys.ts
        audit-logs.ts
        ...
        zod/                      ← optional: generated Zod schemas for forms
    lib/
      api-client.ts               ← kept, used as Orval mutator base
      orval-mutator.ts            ← new: Orval custom fetch mutator (auth wiring)
```

### 10. CI pipeline consideration

Document (or automate) that `openapi-spec.json` must be present before `npm run build` in CI.
Options:
- Run `scripts/update-specs.sh --admin-only` before the npm build step (requires running API).
- Or commit a pinned spec as a one-time bootstrap (update via `update-specs.sh` only).

---

## Spec Flow (End-to-End)

```
Running Admin API (SpringDoc)
      ↓  update-specs.sh --admin-only
specs/admin-api/openapi-spec.json   (canonical master, committed)
      ↓
ezkey-admin-ui/openapi-spec.json     (local copy, gitignored)
      ↓  npm run generate:api (Orval)
src/generated/admin-api/             (TypeScript types + TanStack Query hooks, gitignored)
      ↓
Pages, forms, components             (consuming generated types and hooks)
```

---

## Verification (exit criteria for the session)

All of the following must pass before the session is considered complete:

1. `npm run generate:api` — `src/generated/` populated with typed hooks and interfaces, zero errors
2. `npm run build` — TypeScript compilation clean, zero errors, zero hand-written type files remaining
3. `npm run dev` — all pages render correctly; no runtime 404 / network errors in the browser console
4. No files remain in `src/types/` (deleted) and no files remain in `src/hooks/` that duplicate generated hook logic
5. End-to-end pipeline smoke test: run `update-specs.sh --admin-only` → `npm run generate:api` → confirm `src/generated/` reflects the latest spec → `npm run build` still passes

---

## Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Codegen tool | **Orval** over `openapi-typescript` | TanStack Query v5 already present; Orval generates types + hooks + Zod in one step |
| Generated files committed? | **No** — gitignored | Consistent with Java Maven `target/` pattern |
| Spec file committed? | **No** — always derived from running API via `update-specs.sh` | Consistent with existing project policy |
| Custom fetch mutator | Thin wrapper around existing `api-client.ts` | Reuses auth/session logic; migration is incremental, no big bang rewrite |
| `update-specs.sh` as trigger | Extended to include `ezkey-admin-ui/` | Extends the established pipeline without breaking it |

---

## Phase 2 — Migration vers les hooks Orval générés (chantier différé)

### État actuel post-Phase 1

La Phase 1 a été complétée : les types DTO sont 100% générés par Orval, les fichiers `src/types/`
sont supprimés, et le build compile proprement. Ce qui **n'a pas encore migré** : les appels API
dans les pages utilisent encore `api.get<T>(...)` / `api.post<T>(...)` directement, via
`usePaginatedQuery` et des hooks manuels — au lieu des hooks TanStack Query générés par Orval
(`useGetEnrollments`, `useCreateIntegration`, etc. dans `src/generated/admin-api/`).

### Valeur ajoutée de cette migration

- Les URLs d'API et leurs paramètres de query deviennent **typés et vérifiés à la compilation** —
  si un endpoint change côté backend, `npm run generate:api` + `tsc` remonte tous les points de
  rupture immédiatement.
- Chaque nouveau endpoint ne nécessite plus d'écrire un hook manuellement dans la page.
- Les query keys TanStack Query sont générés et stables, éliminant les erreurs de string manuelle.

### Conséquence de ne pas faire (dette technique)

La dette est **linéaire** : une page = une unité de dette. Elle ne bloque rien ni ne s'amplifie
exponentiellement. En revanche, plus le nombre de pages grandit, plus la migration représente de
travail. À planifier avant que le nombre de pages dépasse ~20-25.

### Scope de la Phase 2

Pour chaque page (`enrollments`, `admins`, `api-keys`, `audit-logs`, `integrations`,
`enrollment-detail`, `integration-detail`, `dashboard`, `auth-attempts`) :

1. Remplacer `usePaginatedQuery` + `api.get(...)` par le hook `useGet*` Orval correspondant,
   en passant les params de filtre/pagination directement.
2. Remplacer les mutations `api.post/put/delete(...)` par les hooks `useCreate*` / `useUpdate*` /
   `useDelete*` générés.
3. Supprimer `src/hooks/use-paginated-query.ts` une fois toutes les pages migrées.
4. Adapter la gestion de la pagination : les hooks Orval retournent `PagedModel*Dto` directement —
   un wrapper léger ou une adaptation des hooks générés sera nécessaire.

### Estimation

~1 journée de travail. Risque de régression modéré — un test fonctionnel page par page est requis.

### Critère d'entrée recommandé

Lancer cette phase après validation fonctionnelle complète de la Phase 1 en environnement de dev
(toutes les pages s'affichent correctement avec `npm run dev`).
