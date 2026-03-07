# Sort Query Parameter Convention (Array vs String)

This document records the project's convention for the `sort` query parameter used in paginated Admin API endpoints. It aligns the OpenAPI spec, Spring backend, and generated frontend client (Orval) on a single, consistent approach.

**Context:** Paginated list endpoints (`GET /api/v1/integrations`, `/enrollments`, `/auth-attempts`, `/audit-logs`, `/admins`) accept `page`, `size`, and `sort`. The question of representing `sort` as a single string (e.g. `"createdAt,DESC"`) vs an array of strings (e.g. `["createdAt,DESC"]`) and how to serialize it in the URL has implications for OpenAPI accuracy, client codegen, and multi-sort support.

---

## 1. What Is Standard and Expected

### Spring (backend)

- **Spring Data REST** and **Spring Data's `Pageable`** resolve the `sort` parameter as follows:
  - **Single sort:** one query parameter `sort=property,direction` (e.g. `?sort=createdAt,desc`).
  - **Multiple sorts:** multiple query parameters with the same name:  
    `?sort=createdAt,desc&sort=name,asc`  
  Each pair is one `sort` parameter; the order of parameters defines the sort order.
- Reference: [Spring Data REST – Paging and Sorting](https://docs.spring.io/spring-data/rest/reference/paging-and-sorting.html) (“keep adding as many sort=PROPERTY parameters as you need”).
- The backend already accepts both forms; no change is required on the Spring side.

### OpenAPI / HTTP

- In **OpenAPI 3**, a query parameter of type **array** is serialized according to `style` and `explode`:
  - **`explode: true`** (for `style: form`): each array element becomes a separate name=value pair:  
    `?sort=createdAt,desc&sort=name,asc`  
  This matches Spring’s expectation for multiple sort criteria.
  - **`explode: false`**: one name with comma-separated values:  
    `?sort=createdAt,desc,name,asc`  
  Spring does not define parsing for this single-parameter, comma-separated list of multiple criteria; the safe, documented convention is repeated `sort` parameters.

**Conclusion:** The regular, expected representation for `sort` in this stack is an **array of strings** (each element is `"property,direction"`), serialized with **explode: true** (repeated `sort=...` in the URL).

---

## 2. What Is Commonly Used

- **Spring-based REST APIs** routinely use repeated `sort` parameters for multi-sort; many OpenAPI specs describe `sort` as an array.
- **Frontend codegen** (e.g. Orval) typically generates a parameter of type `string[]` when the spec declares an array; serialization should follow the spec’s `style`/`explode` so that the backend receives the same format.
- **Current Admin UI** sends a single string today (e.g. `sort=createdAt,DESC`). The backend accepts it. The goal of this convention is to standardize on the array + explode form so that the spec, backend, and client stay aligned and multi-sort remains supported without ad-hoc parsing.

---

## 3. Best Practices for This Project

- **Single source of truth:** One format for `sort` everywhere: array of strings, each element `"property,direction"`, serialized with explode (repeated `sort=`).
- **Backend:** No change. Keep using `Pageable` and `@PageableDefault`; Spring’s resolver already handles one or multiple `sort` parameters.
- **OpenAPI spec:** Declare `sort` as an **array of strings** with **`explode: true`** on all paginated list endpoints. SpringDoc does not emit `style`/`explode` for the generated Pageable `sort` parameter. This project applies an **OperationCustomizer** in the Admin API (`org.ezkey.admin.config.OpenApiConfig#sortParameterExplodeCustomizer`) that sets `style: form` and `explode: true` on every query parameter named `sort` with an array schema. The spec served at `/api-docs` (and thus the JSON saved by `scripts/update-specs.sh`) therefore already contains these properties; no post-generation script is required.
- **Generated client (Orval):** Use the generated hooks with `sort` as an array (e.g. `sort: ['createdAt,DESC']` for one criterion, `sort: ['createdAt,DESC','name,asc']` for two). The pagination adapter in the Admin UI can keep a single string in component state (e.g. `'createdAt,DESC'`) and pass `sort: [stateSort]` when calling the hook—no custom serialization or string/array branching.
- **Standards:** There is no RFC that mandates a single format for sort query parameters; the combination **OpenAPI array + explode: true** and **Spring’s repeated sort=** is consistent and widely used.

---

## 4. Recommended Path (Unified Convention)

| Layer        | Action |
|-------------|--------|
| **Backend** | No change. Controllers continue to use `Pageable`; Spring already accepts one or multiple `sort=` parameters. |
| **OpenAPI** | Define `sort` as `type: array`, `items: string`, and set **`explode: true`** for the `sort` parameter on every paginated list operation. If SpringDoc does not emit this, add it via post-generation or document and enforce in client implementation. |
| **Orval / UI** | Use generated hooks with `sort` as `string[]`. In the pagination wrapper, keep UI state as a single string when only one sort is used and pass `sort: [stateSort]` to the hook. No custom URL building or fragile string/array handling. |

Result: backend behaviour unchanged and Spring-idiomatic; spec accurate and multi-sort ready; client uses only arrays and avoids brittle custom code.

---

## 5. Summary

- **Regular / expected:** `sort` as an array of `"property,direction"` strings, serialized as repeated query parameters (`sort=...&sort=...`), i.e. OpenAPI array with **explode: true**, matching Spring.
- **Backend:** Already correct; no modification required.
- **Spec:** **explode: true** (and `style: form`) for `sort` is enforced by the OperationCustomizer in `OpenApiConfig`; the generated spec at `/api-docs` and in `specs/admin-api/openapi-spec.json` after `update-specs` reflects this.
- **Client:** Always pass `sort` as an array (e.g. `['createdAt,DESC']`); derive from a single string in state when the UI only has one sort criterion.

This convention is the reference for the Phase 2 Orval migration (see `.cursor/plans/phase2_orval_hooks_migration_inventory.md`, section 4.3) and for any future paginated endpoints.
