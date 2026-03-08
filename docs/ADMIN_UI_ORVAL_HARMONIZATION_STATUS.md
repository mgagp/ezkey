# Admin UI — Orval Harmonization Status

This document summarizes where the project stands in aligning the Admin UI with the backend via Orval-generated DTOs and TanStack Query hooks. It is updated as each step is completed.

---

## 1. OpenAPI spec (backend ↔ UI)

| Item | Status |
|------|--------|
| Spec files dispatched to subprojects (e.g. `ezkey-admin-ui/openapi-spec.json`) | ✅ Done |
| Spec versioned in admin-ui (always available for build) | ✅ Done |
| `sort` parameter: `style: form`, `explode: true` on all paginated list endpoints | ✅ Done (see `docs/SORT_PARAMETER_CONVENTION.md`) |
| Optional `tenantId` on `GET /api/v1/admins` (GlobalAdmin) | ✅ In spec |

The OpenAPI spec in `ezkey-admin-ui/openapi-spec.json` is the source for Orval; it is kept in sync after Docker/API changes and reflects the backend contract (including sort serialization and tenantId).

---

## 2. Orval configuration and generation

| Item | Status |
|------|--------|
| `orval.config.ts` (input: `openapi-spec.json`, output: `src/generated/admin-api`, react-query, tags-split, custom mutator) | ✅ In place |
| `npm run generate:api` | ✅ Available (no `prebuild`/`predev`; run manually or in CI before build) |
| Generated output: `src/generated/admin-api/` (model + tag-based hook files) | Gitignored; (re)created when running `generate:api` |

Running `npm run generate:api` in `ezkey-admin-ui` produces TypeScript types under `model/` and TanStack Query hooks per tag (e.g. Integrations, Enrollments, Auth Attempts, Audit Logs, Admins, etc.). The custom mutator (`src/lib/orval-mutator.ts`) wires requests to the existing auth/session logic.

---

## 3. Phase 1 — DTOs (types only)

| Item | Status |
|------|--------|
| Hand-written types removed (`src/types/`) | ✅ Done |
| UI imports types from `@/generated/admin-api/model` | ✅ Done (all pages and components use generated DTOs) |
| Build compiles with generated types | ✅ Yes (assuming `generate:api` has been run so `src/generated/` exists) |

**Conclusion:** Phase 1 is complete. The UI is fully typed with Orval-generated DTOs; the contract (shapes, enums) is aligned with the backend.

---

## 4. Phase 2 — Hooks (queries and mutations)

| Item | Status |
|------|--------|
| List pages use Orval-generated hooks via `usePaginatedFromOrval` adapter | ✅ Done (integrations, enrollments, auth-attempts, audit-logs, admins, tenant-detail, integration-detail) |
| `use-integrations.ts` uses generated `search({ size: 100 })` | ✅ Done |
| Custom `usePaginatedQuery` | ✅ Removed; replaced by `use-paginated-orval.ts` |
| Other pages use Orval-generated `useGet*` / `useMutation*` | ⏳ Partial (integration-detail uses `getById1`; create flows use generated mutations where available) |
| Raw `api.get/post/put/delete/patch` calls | ✅ Still used for onboarding, create-admin, dashboard stats, audit seal/gap/integrity, login, etc. |

**Conclusion:** Phase 2 **list migration is complete**. The UI uses:

- **`usePaginatedFromOrval`** + Orval fetch functions (`search`, `search1`, `search2`, `getAuditLogs`, `listAdmins`) for all 7 paginated lists; `DataTable` and `Pagination` unchanged.
- **Generated mutations** for integration create, enrollment create; admins create still uses `api.post` with generated DTOs.
- **`use-integrations`** backed by generated `search`; `getIntegrationName` unchanged.

Remaining optional follow-ups: replace remaining `api.*` calls with Orval hooks (e.g. enrollment-detail, api-key-detail, tenant fetch, dashboard stats, audit seal/gap/integrity, login).

---

## 5. Summary: where we are

- **Spec ↔ backend:** Aligned. Specs are up to date and include `sort` (array, form, explode true) and optional `tenantId` for admins.
- **Orval execution:** Configured and runnable. `npm run generate:api` produces DTOs and hooks from `openapi-spec.json`.
- **DTOs (Phase 1):** Harmonized. The UI uses only Orval-generated types; no hand-written API types remain.
- **Hooks (Phase 2):** List migration done. All paginated lists use `usePaginatedFromOrval` + Orval-generated fetch functions; `use-integrations` uses generated `search`; `use-paginated-query.ts` removed. Build passes.

**Optional next steps:**

1. Replace remaining **`api.*`** calls with Orval-generated hooks (enrollment-detail, api-key-detail, tenant fetch, dashboard stats, audit seal/gap/integrity, login).
2. Add **prebuild** / **predev** that runs `npm run generate:api` if desired.

Reference: `.cursor/plans/phase2_orval_hooks_migration_inventory.md` for the full inventory and plan.

---

## 6. Quality assessment and future readiness

### What is maximized today

| Area | Status | Notes |
|------|--------|------|
| **DTOs for all spec’d APIs** | ✅ Maximized | Every UI type comes from `@/generated/admin-api/model`; no hand-written API types. Contract is single-source (OpenAPI → Orval → UI). |
| **Pagination via Orval** | ✅ Maximized for lists | All 7 paginated lists use **Orval-generated fetch functions** (`search`, `search1`, `search2`, `getAuditLogs`, `listAdmins`) inside a **single generic adapter** (`usePaginatedFromOrval`). Pagination state (page/size/sort) is local; the adapter calls the generated function with the right params and maps `PagedModel*` → `{ data, pagination }` for existing `<DataTable>` / `<Pagination>`. |

So: **yes** — we maximize Orval for (1) DTO generation across everything exposed in the spec, and (2) use of Orval’s generated list APIs for all paginated lists, with one thin adapter for the UI contract.

### What is not yet maximized

- **Hooks for non-list endpoints:** Many flows still use raw `api.get/post/put/delete/patch` (dashboard stats, tenant CRUD, enrollment detail, api-keys, admins onboarding/activate/deactivate, audit seal/gap/integrity, encryption-keys, login). Those endpoints are either not in the Admin OpenAPI spec, or not yet migrated to Orval hooks. **Impact:** New APIs that are added to the spec and generated by Orval can be used via hooks immediately; existing “by hand” calls stay as-is until migrated.
- **Orval’s own `useQuery`/`useMutation` wrappers:** We use the **generated fetch functions** (e.g. `search`, `getById1`) inside our own `useQuery`/`useMutation` (or the pagination adapter). We do **not** yet use Orval’s generated hook names (e.g. `useSearch`, `useGetById1`) everywhere. **Impact:** Slight duplication (we wrap the same fetch in our query key and options). Migrating to generated hooks would remove that and align 1:1 with Orval’s API.

### Quality of the current setup

- **Generic and reusable:**  
  - **`usePaginatedFromOrval`** is a single generic hook: it accepts any Orval list fetch `(params) => Promise<PagedBody<T>>` and any `baseParams`. No list-specific logic; adding a new paginated list = one call site with the right `queryKey`, `baseParams`, and generated fetch.  
  - **`use-integrations`** is a small wrapper around generated `search({ size: 100 })` plus a cached lookup; the only “custom” part is the `getIntegrationName` + `Map` derivation.

- **Framework alignment:**  
  - TanStack Query v5 is the single data-fetching layer. The adapter uses `useQuery`, `queryKey`, `queryFn`, `placeholderData: keepPreviousData`; pages use the same `{ data, pagination, isLoading, isError, refetch }` contract.  
  - Orval is configured with `client: 'react-query'`, `useQuery`/`useMutation`, and a custom mutator that plugs into the app’s auth/session. So we follow the framework’s patterns and Orval’s intended use.

- **Minimal custom code:**  
  - **DTOs:** Zero hand-written API types; all from Orval.  
  - **Pagination:** One ~80-line adapter instead of N custom list hooks; all list-specific code is “which Orval function and which baseParams”.  
  - **Remaining custom code:** The mutator (auth/session wiring), `use-integrations` (search + lookup), and the remaining `api.*` call sites. The latter are the main source of “code we maintain by hand”; they can be reduced over time by adding endpoints to the spec and switching to generated hooks.

- **Future and technical debt:**  
  - **New endpoints in the spec:** New list endpoints → add one `usePaginatedFromOrval` call with the new generated fetch. New single-resource or mutation endpoints → use the new generated hooks or fetch functions. No new custom types; no new pagination logic.  
  - **Spec as contract:** As long as the OpenAPI spec is updated when the backend changes and `generate:api` is run, types and list APIs stay in sync; the UI compiles against the real contract and breaks at build time if the API changes.  
  - **Remaining debt:** The `api.*` usages are the main debt: they bypass Orval’s generated types and hooks. Migrating them (when the spec covers them) will further reduce manual code and centralize changes in the spec + codegen. The current state already avoids accumulating new debt on **lists and DTOs**: new list APIs and new DTOs are handled by Orval and the generic adapter.
  - **Encryption-keys / Auth / Tenants:** If those APIs live in another spec or are not yet in any spec, they will continue to use `api.*` until they are specified and generated; that’s expected and does not weaken the Orval-based design for the Admin API.

### Short summary

- **DTOs:** Maximized; 100% from Orval for the Admin API surface in the spec.  
- **Pagination:** Maximized for lists; one generic adapter + Orval-generated list fetches; no list-specific pagination code.  
- **Code quality:** Generic adapter, TanStack Query–native, minimal custom code for lists and types; remaining custom code is mostly the remaining `api.*` call sites.  
- **Future:** Well positioned: new list APIs = one adapter call; new DTOs and new endpoints = spec + codegen. This keeps technical debt low and avoids regressions as long as the spec is maintained and codegen is run (e.g. in CI or prebuild).
