---
name: Backend error i18n strategy
overview: "Archived plan: RFC 9457 + Admin UI error i18n (Option B). Phase 1 pilot delivered; see Closing note for accomplishments and evolution (backend templating, full locale coverage)."
todos: []
isProject: false
---

# Backend error message internationalisation — revised strategy

## Verification: RFC 9457 only (current codebase)

**Admin API:** There are **no** remaining references to `ErrorResponseDto` under [ezkey-admin-api](ezkey-admin-api). [GlobalExceptionHandler](ezkey-admin-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java) and [ValidationExceptionHandler](ezkey-admin-api/src/main/java/org/ezkey/exception/ValidationExceptionHandler.java) return `ResponseEntity<ProblemDetail>` with RFC 9457 fields (`type`, `title`, `status`, `detail`, `path`).

**Auth API:** [GlobalExceptionHandler](ezkey-auth-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java) and [AuthExceptionHandlerBase](ezkey-auth-api/src/main/java/org/ezkey/exception/AuthExceptionHandlerBase.java) also build **ProblemDetail** responses.

**Conclusion:** The plan no longer includes “complete the migration to RFC 9457” as work to do. Remaining work is **i18n UX** (especially Admin UI) and optional **consistency/documentation** around problem `type` URIs.

---

## Current behaviour (unchanged from before, minus legacy DTO)

- **Detail text** is still largely **English** strings from `ex.getMessage()` or handler-built messages ([ExceptionHandlerBase.buildProblemDetail](ezkey-admin-api/src/main/java/org/ezkey/exception/ExceptionHandlerBase.java)).
- **Stable machine identifier** is already the RFC 9457 `**type`** URI (e.g. `https://ezkey.io/problems/authentication/invalid-credentials`). That is exactly what the standard is for: a URI identifying the problem type.

### Admin UI consumption

- [`getApiErrorMessage`](ezkey-admin-ui/src/lib/api-client.ts) and [`getTranslatedApiError`](ezkey-admin-ui/src/lib/api-error-i18n.ts) (pilot): `type`→`errors` namespace (Option B), with precedence rules documented in [AGENTS.md](ezkey-admin-ui/AGENTS.md).

---

## Recommendation: use `type` as the single source of truth for i18n slugs

**Principle:** Do **not** invent a parallel “error code” system unless you hit a concrete pain point. The `**type`** field is the canonical, API-stable identifier.

**i18n key derivation (Admin UI) — adopted convention:**

1. Parse `problemDetail.type` as a URI.
2. **Option B (chosen):** Strip the fixed base `https://ezkey.io/problems/` (trailing slash normalised), take the **remaining path**, replace `/` with `.`, prefix with the i18n namespace for API errors (e.g. `errors`).
  - Example: `https://ezkey.io/problems/authentication/invalid-credentials` → `errors.authentication.invalid-credentials`.
3. **Option A** (last segment only) is **not** used for Ezkey Admin UI, to avoid cross-category slug collisions and to keep keys aligned with the URI hierarchy.

**Implementation note:** Implement this in one small helper (parse URI, validate host/path prefix, build key); on parse failure or unknown prefix, fall back to `detail` / `title` as today.

### Design note: last segment only vs namespaced path (how to choose)

This is a **namespace design** choice for translation keys, not an HTTP or RFC requirement. Both are valid if you are consistent.

**Option A — last segment only** (`invalid-credentials` → `errors.invalid-credentials`)

- **Pros:** Shorter keys; less nesting in JSON; faster to type in code and for translators scanning a flat list; fewer characters in bundles.
- **Cons:** **Collision risk** if two different `type` URIs ever end with the same last segment under different categories (e.g. `…/validation/not-found` and `…/resource/not-found` both → `not-found`). If that happens, two distinct API errors would map to the **same** translation key unless you add a disambiguation rule later (rename URI or special-case in code).
- **When it fits:** Small APIs, strict naming discipline so last segments are **globally unique** across the product, or you accept that collisions require a one-off fix.

**Option B — path after base** (`authentication/invalid-credentials` → `errors.authentication.invalid-credentials`)

- **Pros:** **Naturally mirrors the URI hierarchy**; collisions between categories are structurally impossible as long as full paths stay unique (which they must be for distinct `type` URIs anyway); refactoring one category does not silently merge keys with another; easier for developers to guess the key from the `type` they see in DevTools.
- **Cons:** Deeper JSON / longer keys; slightly more ceremony in locale files (nested objects or longer dotted keys).
- **When it fits:** Multiple APIs or many problem categories, long-lived product, many contributors — the extra structure pays off in **clarity and safety**.

**Product decision:** Ezkey uses **Option B** for Admin UI API error translations (evolutive, clearer mapping from `type` to locale keys).

**Duplicate `code` extension property:** **Not required** for the design to work. Add `problem.setProperty("code", …)` only if you want to spare clients from parsing URIs or if a non-HTTP client cannot handle full URIs easily. For the Admin UI, parsing `type` once in a small helper is enough and keeps the HTTP body closer to “pure” RFC 9457.

---

## Enum / central registry: is it worth it? (design advice)

### What API design expects

- **RFC 9457 / good REST practice:** The `**type`** URI is the registry entry (conceptually). You document the base URL and the set of types; clients branch on `type` or on a derived slug. You do **not** need a second master list in code for the API to be “correct.”

### One mega-enum or one “god” registry in `ezkey-core`

- **Not recommended.** Ezkey has **Admin API**, **Auth API**, **Integration API**, etc. A single enum or class listing every problem type for all APIs would:
  - Couple unrelated services,
  - Grow without bound,
  - Force pointless core changes when only one API adds an error,
  - Read like a **god object** to new contributors.

### Pragmatic patterns (pick one; all are acceptable)

1. **No registry (status quo + docs)**
  Keep `type` string literals next to each handler (as today). Maintain clarity with:
  - Short Javadoc on handlers listing the `type` URI, and/or
  - An optional **inventory doc** under `docs/` (or the archived plan [exception-mapping-inventory](.cursor/plans/archived/2026-04/exception-mapping-inventory_1bc3c2bb.plan.md) style) for translators and QA — **not** a compile-time enum.
2. **Module-local constants (recommended if you want compile-time reuse)**
  In **each API module** only, e.g. `ezkey-admin-api`:
  - A small `final` class or package with `public static final String TYPE_INVALID_CREDENTIALS = "https://ezkey.io/problems/authentication/invalid-credentials";`
  - Split by area if a single file grows: `AuthenticationProblemTypes`, `ValidationProblemTypes`, etc.
   Handlers reference these constants so typos are caught; **no** shared mega-list in core.
3. **Shared base URL only in core (optional)**
  In `ezkey-core`, a single constant such as `ProblemUriConstants.BASE = "https://ezkey.io/problems"` plus a tiny helper `type(String... segments)` keeps URIs consistent. **Do not** move every slug into core — only what is truly shared.
4. **Java `enum` per module**
  Useful if you want exhaustiveness in **that module’s** tests (e.g. “every enum value has an OpenAPI example”). Still **split by API**, not one global enum.

### Verdict

- **Do not** create one typenum/registry for the whole product in `ezkey-core`.
- **Do** use `**type`** as the unique key for i18n; optional **module-local** constants or small per-area classes if you want safer refactors.
- **Optional** human-facing inventory (markdown) for translation and support — separate from Java compilation.

---

## Phased execution (Admin UI) — matches good practice

**Why this shape:** Ship **reusable infrastructure first** (helpers, key convention, fallback behaviour), then a **small pilot** so you can validate design and edge cases (malformed `type`, unknown prefix, missing translation) before investing in an **exhaustive** catalogue. That limits rework if the mapping rule or UX needs a tweak, and it keeps early PRs reviewable.

**Your reading is correct:** Phase 1 = pattern + minimal pilot; after that is validated, later phases = exhaustive inventory and en/fr coverage (not the other way around).


| Phase                  | Scope                               | Delivers                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ---------------------- | ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Phase 1 — Pilot**    | Admin UI infra + minimal real usage | `problemTypeToTranslationKey(type)` (**Option B**), `getTranslatedApiError(error, t, fallback)` (fallback to `detail`/`title`). Wire into **a few high-traffic paths** only (e.g. login, one tenant/enrollment mutation) so the feature is **exercised end-to-end**. Pilot **en + fr** keys for those `type` values only. Unit tests for the helper (valid URI, bad URI, missing key → fallback). Document the convention in [ezkey-admin-ui/AGENTS.md](ezkey-admin-ui/AGENTS.md). **Exit:** team agrees the pattern is solid; adjust if needed before Phase 2. |
| **Phase 2 — Coverage** | Inventory + full en/fr              | **Exhaustive inventory** of `type` URIs the Admin UI can receive (from Admin API handlers, docs, or a maintained list under `docs/` — goal is *complete* for Admin UI scope, not a Java god-registry). Add **all** corresponding keys under `errors.`* in **en** and **fr**; keep fallback to English `detail` for any gap during rollout. Optionally roll out UI callsites in batches (replace raw `getApiErrorMessage` where API errors are shown).                                                                                                           |
| **Phase 3** (optional) | Other surfaces / backend            | Same `type`→key rule for mobile or other clients if desired; or `code` extension on ProblemDetail; or server-side `Accept-Language` + `MessageSource` if product requires it.                                                                                                                                                                                                                                                                                                                                                                                   |


**Optional hygiene (any time, low risk):** Module-local URI constants in each API module so `type` strings are not duplicated literals — no cross-product registry in `ezkey-core`.

---

## Impact on controllers and handlers

- **No change required** for i18n to work: handlers already set `type`.
- **New work** is mostly **Admin UI** + **locale files** + a **documented convention** for deriving keys from `type`.
- **Tests:** Prefer asserting on `type` URI (or slug) where stable behaviour matters, plus status code.

---

## Summary

- **Confirmed:** Admin (and Auth) APIs are on **RFC 9457 ProblemDetail**; legacy ErrorResponseDto is not part of Admin API anymore.
- **i18n:** Use `**type`** as the stable identifier; derive keys with **Option B** (namespaced path after `/problems/`); no mandatory extra `code` field.
- **Registry / enum:** **Avoid** a single global typenum in core; prefer **module-local** constants or **documentation**; optional **shared base URI** helper in core only.
- **Phases:** (1) **Pilot** — reusable Admin UI pattern + minimal keys + tests + doc, validate design; (2) **Coverage** — exhaustive `type` inventory for Admin UI + full en/fr keys and broad callsite adoption; (3) optional other clients / backend locale.

---

## Closing note — status, example behaviour, and follow-up (copy-paste for future sessions)

### Example: `domain/integration-has-enrollments` (HTTP 409)

**Observed payload:** `type` = `https://ezkey.io/problems/domain/integration-has-enrollments`, English `detail` explaining that enrollments must be removed first.

**Current Admin UI behaviour** ([`getTranslatedApiError`](ezkey-admin-ui/src/lib/api-error-i18n.ts)):

- **`authentication.*`:** curated `errors.*` strings take precedence (stable FR/EN for login flows).
- **All other types (including `domain.*`, `admin.*`):** if **`detail` is non-empty**, it is shown as-is. That preserves **specific** server messages (duplicate tenant name, integration blocked by enrollments, etc.) and avoids replacing them with a generic translated title.
- If `detail` were empty, the UI would fall back to a translation keyed by `type` (Option B path), then `title` / `getApiErrorMessage`.

So for this 409, the user sees the **English `detail`** until a French string exists **and** the UI policy prefers locale over raw `detail` for that category. Today, **detail wins** — which is correct for specificity; **full FR/EN parity** for domain errors means either (a) adding matching keys under `errors.domain.integration-has-enrollments` in `en`/`fr` **and** adjusting precedence for `domain.*` similarly to `authentication.*` if we want static copy over English `detail`, or (b) structured messages from the backend (below).

### What was accomplished (pilot + hardening)

- **RFC 9457** is the sole error shape for Admin API; `type` is the stable machine identifier.
- **Admin UI:** `problemTypeToTranslationKey` (Option B), `errors` namespace, [`getTranslatedApiError`](ezkey-admin-ui/src/lib/api-error-i18n.ts) with rules for **auth vs non-auth** messages; unit tests; [AGENTS.md](ezkey-admin-ui/AGENTS.md) documentation.
- **`fetchApi`:** 401 no longer forces a full-page redirect to `/login` when the request did **not** use a session JWT (fixes login / passwordless error flashes); login and passwordless-wait use `requireAuth: false`.
- **Precedence fix:** generic i18n for `admin.invalid-argument` no longer hides server **`detail`** (duplicate tenant, etc.).

### Logical evolution (recommended order)

1. **Phase 2 (Admin UI):** Exhaustive inventory of `type` values reachable from the Admin UI; add **en** + **fr** strings under [`src/locales/*/errors.json`](ezkey-admin-ui/src/locales/en/errors.json); replace remaining `getApiErrorMessage` callsites with `getTranslatedApiError` where appropriate. Decide per **category** (`domain.*`, `admin.*`, …) whether **static locale** or **`detail`** should win (today: `detail` wins except for `authentication.*`).

2. **Quick win (UI only):** For stable domain errors with **fixed** copy (e.g. integration has enrollments), add `domain.integration-has-enrollments` in **en** and **fr** with no parameters. Optionally extend `shouldPreferI18nOverDetail` (or equivalent) for `domain.*` **only when** `detail` is redundant with `title` — avoid hiding parameterized server messages.

3. **Medium term — structured / templated errors (backend):** If **`detail` must stay English** but the UI must show **localized sentences with parameters** (tenant name, counts, IDs), consider extending RFC 9457 responses with **well-defined extension fields**, e.g. `messageKey` + `messageParams` (JSON object), or a small set of documented keys, resolved server-side with `Accept-Language` + Spring `MessageSource`. Clients then render either localized `detail` from the server **or** `t(key, params)` in the UI if the contract is “key + params only”. This is **separate** from Option B slug mapping; it complements it when **interpolation** is required.

4. **Optional:** Same `type`→key convention for **mobile** or **Auth API** consumers; avoid a **single** Java enum of all problem types in `ezkey-core` (module-local constants or docs only).

### Suggested one-liner for a new coding session

> Extend Admin UI error i18n Phase 2: inventory all `https://ezkey.io/problems/...` types used by Admin UI, fill [`ezkey-admin-ui/src/locales/en/errors.json`](ezkey-admin-ui/src/locales/en/errors.json) and [`fr/errors.json`](ezkey-admin-ui/src/locales/fr/errors.json), wire `getTranslatedApiError` everywhere needed. For errors with dynamic text, either keep showing server `detail` (English) or design RFC 9457 extensions / backend `MessageSource` + `Accept-Language` — see archived plan [`.cursor/plans/archived/2026-04/backend_error_i18n_strategy_cf455bd9.plan.md`](.cursor/plans/archived/2026-04/backend_error_i18n_strategy_cf455bd9.plan.md) Closing note.

