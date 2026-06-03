# OpenAPI API Reference — Presentation Order and Grouping

## Purpose

Define how Ezkey controls **tag order**, **tag grouping**, and **operation order** in OpenAPI
documentation for Admin, Auth, and Integration APIs—across runtime Swagger UI and public ReDoc on
`ezkey.org`.

This document is the Phase 1 deliverable (research + decisions) and the **canonical guide** for
presentation order. Phase 2 implements the chosen mechanisms in code.

**Status:** Complete — merged via PR `#181` (2026-06-03). Issue `#180` closed.

**Related IDs:** `V-2026-06-02-openapi-api-reference-presentation-order`,
`I-2026-06-02-openapi-api-reference-presentation-order`,
`TB-2026-06-02-openapi-presentation-order-phase2`.

## Incubation sources

- Working plan (GitHub): [`.github/prompts/plan-openApiPresentationOrder.prompt.md`](../../.github/prompts/plan-openApiPresentationOrder.prompt.md)
- Working plan (Cursor): [`.cursor/plans/openapi_ordering_strategy_86087546.plan.md`](../../.cursor/plans/openapi_ordering_strategy_86087546.plan.md)
- Lane: `B` — plan incubation, materialized `2026-06-02`
- Methodology gate: [`2026-06-02-plan-incubation-bidirectional-traceability.md`](../methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md)

---

## 1. Baseline audit (state before implementation)

The subsections below describe the **pre-change** situation (2026-06-02). They remain useful as
evidence of why curation was needed. Current implementation touchpoints are in **section 1.6**.

### 1.1 Springdoc / Swagger UI configuration (historical)

All three APIs previously set (example: `ezkey-admin-api/config/application.properties`):

```properties
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
```

| Setting | Effect on Swagger UI | Effect on ReDoc (portal) |
| --- | --- | --- |
| `tagsSorter=alpha` | Tags sorted A→Z; **ignores** root `tags[]` order in spec | Not applied |
| `operationsSorter=method` | Operations grouped by HTTP verb within tag | Not applied |

**Implication:** Curating `tags[]` in the spec alone does **not** change Swagger UI until
`tagsSorter=alpha` is removed or replaced. ReDoc already follows spec order.

### 1.2 Root `tags[]` order in canonical specs (2026-06-02 snapshot)

**Admin API** (`specs/admin-api/openapi-spec.json`) — discovery order, duplicate name:

1. Encryption Keys
2. Dashboard
3. Admin Enrollment Management
4. Alerts
5. Auth Attempts
6. Admin Authentication
7. Audit Logs
8. Public
9. Tenants
10. Administrator Provisioning
11. Enrollments
12. API Keys
13. Integrations
14. **Public** (duplicate entry, different description)

**Auth API** (`specs/auth-api/openapi-spec.json`):

1. Enrollments
2. Authentication Attempts
3. Public

**Integration API** (`specs/integration-api/openapi-spec.json`):

1. Auth Attempts (only tag)

### 1.3 Operation order snapshot

| API | Tag | Current spec path / operation order | Issue |
| --- | --- | --- | --- |
| Auth | Enrollments | `verify` then `bind` | Journey is bind → verify |
| Auth | Authentication Attempts | `respond` then `pending` | Journey is pending → respond |
| Auth | Public | `getInstanceInfo` | OK as single op |
| Integration | Auth Attempts | `create`, `cancel`, `waitForResponse` | Journey is create → wait → cancel |
| Admin | (many) | Path map order ≠ journey within tag | Large surface; needs path reorder in customizer for key flows |

Swagger UI with `operationsSorter=method` further reorders by verb (DELETE, GET, POST, …) within
each tag, which can break CRUD and lifecycle narratives.

### 1.4 Public portal (ReDoc)

- Pages: `sites/ezkey-org/*-api-reference.html` load `/api-specs/*-openapi.json` (copies from
  `specs/` via `scripts/update-specs.sh`).
- `Redoc.init` options: theme only—no `sortTagsAlphabetically` or similar.
- Navigation = spec `tags[]` + `x-tagGroups` (if present) + path order.

### 1.5 Code touchpoints (historical)

Before Phase 2, only `@Tag` annotations and Swagger UI properties controlled presentation; no
programmatic tag or path ordering existed.

### 1.6 Current implementation (after Phase 2)

| Area | File / artifact |
| --- | --- |
| Admin presentation customizer | `ezkey-admin-api/.../OpenApiPresentationCustomizer.java` — ordered `tags[]`, deduped `Public`, `x-tagGroups` |
| Auth presentation customizer | `ezkey-auth-api/.../OpenApiPresentationCustomizer.java` — tag order + path journey order |
| Integration presentation customizer | `ezkey-integration-api/.../OpenApiPresentationCustomizer.java` — path lifecycle order |
| Bean registration | `OpenApiConfig.java` in each module (`openApiPresentationCustomizer` bean) |
| Swagger UI alignment | `tagsSorter=alpha` removed; Auth/Integration also dropped `operationsSorter=method` |
| Admin `@Tag` cleanup | Unified `Public` description on both public controllers |
| Spec pipeline | `scripts/update-specs.sh` → `specs/` and `sites/ezkey-org/api-specs/` |

**Deferred:** Admin API path reorder within large tags (optional follow-up; tag-level order is
implemented).

---

## 2. Mechanism inventory and control depth

| Mechanism | What it controls | Swagger UI | ReDoc CE | Portable (OpenAPI standard) | Maintenance |
| --- | --- | --- | --- | --- | --- |
| `@Tag` on controllers | Tag name + description | Yes | Yes | Yes | Per controller |
| Root `tags[]` array order | Sidebar tag sequence | Only if `tagsSorter` not alpha | Yes | Yes | Customizer or manual spec |
| `springdoc.swagger-ui.tagsSorter` | Client-side tag sort | Yes | No | No | Properties file |
| `springdoc.swagger-ui.operationsSorter` | Client-side op sort by method | Yes | No | No | Properties file |
| `OpenApiCustomizer` / `GlobalOpenApiCustomizer` | Rewrite `tags`, `paths`, extensions | Via spec | Via spec | Yes | Central per API |
| `x-tagGroups` (ReDoc extension) | Nested nav groups | Ignored | Yes | Vendor ext. | Customizer; all tags must be grouped |
| `springdoc.writer-with-order-by-keys` | Alphabetize JSON keys | Indirect | Indirect | Yes | Global; may fight path journey order |
| Hand-edit `specs/*.json` | Anything | Yes | Yes | Yes | **Forbidden** (generated artifacts) |

### Recommended combination (Phase 2 — implemented)

1. **`OpenApiCustomizer` per API** — single source of truth for:
   - ordered `tags[]` (deduplicated);
   - optional `x-tagGroups` (Admin API);
   - optional `paths` LinkedHashMap reorder for lifecycle-critical operations (Auth, Integration; Admin paths deferred).
2. **Swagger UI alignment** — removed `springdoc.swagger-ui.tagsSorter=alpha` on all three APIs;
   removed `operationsSorter=method` on Auth and Integration; Admin keeps `operationsSorter=method` for CRUD-heavy tags.
3. **Annotation cleanup** — unified Admin `Public` tag description on both controllers.
4. **Regenerate** — maintainer runs `scripts/update-specs.sh`; verify portal copies.

**Rejected as primary strategy:** Swagger UI-only sorters (no ReDoc parity).

**Deferred:** `GroupedOpenApi` multiple documents—unnecessary; one spec per service is enough.
Admin intra-tag path journey reorder (large surface).

---

## 3. Ideal conceptual order (target guides)

Rules for all APIs:

- **Public / unauthenticated** surfaces first where they exist.
- **Authentication and session** before tenant and resource management.
- **Core MFA entities** (integrations, enrollments, auth attempts) in dependency order.
- **Platform security and compliance** (audit, encryption keys, alerts) after day-to-day ops.
- **Dashboard / aggregates** last (UI-oriented read models).
- **Within a tag:** prefer business journey order over HTTP method order.

### 3.0 Admin API — presentation principles (reader journey)

The Admin API reference is ordered as an **operator discovers and runs the platform**—from the
outside (public metadata) inward (tenant setup, MFA operations, platform governance)—not as a
technical inventory sorted by discovery order or alphabet.

This follows the same cognitive-comfort posture as curated methodology packs on `ezkey.org`:
documentation navigation is a **product choice**, not a file-browser default
([`2026-05-29-curated-public-pack-ordering.md`](../methodology/decisions/2026-05-29-curated-public-pack-ordering.md)).

#### Guiding principles

1. **Curated journey, not inventory** — tags and groups tell a story; the reader should not hunt
   across alphabetized or Springdoc-discovery-ordered sections.

2. **Public before authenticated** — `Public` opens the reference (instance metadata, evaluator
   signup). No tenant or MFA concept should appear before the reader knows how to reach the
   instance and authenticate.

3. **Session before configuration** — `Admin Authentication` follows immediately (login, logout,
   recovery, passwordless wait). Tenant and integration setup assumes an authenticated operator.

4. **Foundations, then MFA resources in dependency order** — `Tenants` → `Integrations` →
   `Enrollments` → `API Keys` mirrors how an installation is built: multi-tenant root, protected
   application, user–device links, then machine-to-machine access for the Integration API.

5. **MFA operations at the product center** — `Auth Attempts` and `Admin Enrollment Management`
   cover day-to-day MFA administration and operator-driven recovery. These are distinct from
   provisioning admin *accounts*.

6. **Administrator provisioning is its own concern** — `Administrator Provisioning` is grouped
   separately from end-user enrollments: org-level admin lifecycle (global/tenant admins,
   onboarding) is not the same problem as device binding or enrollment recovery.

7. **Platform, security, and compliance after daily operations** — `Audit Logs`, `Encryption Keys`,
   and `Alerts` appear once the installation exists. They are rarer, more sensitive, and often
   Global Admin–scoped.

8. **UI aggregates last** — `Dashboard` closes the flat tag list: aggregated stats for the Admin UI,
   not integration primitives. Putting dashboards first would hide the APIs operators configure
   with.

9. **Business journey over HTTP verb order** — within a tag, prefer lifecycle narrative where it
   matters (fully applied on Auth and Integration; Admin relies primarily on tag order today).

#### What we explicitly avoid

| Anti-pattern | Why |
| --- | --- |
| `tagsSorter=alpha` in Swagger UI | Hides curated order; ReDoc does not share this sorter anyway |
| Springdoc discovery order as documentation order | Arbitrary (e.g. Encryption Keys first, duplicate `Public`) |
| HTTP method sorting where lifecycle matters | Breaks bind→verify, pending→respond, create→wait→cancel narratives |
| Hand-editing `specs/*.json` | Generated artifacts; order must live in backend customizers |

#### One-line summary

**The Admin API reads like an operator's path through Ezkey: discover the instance, authenticate,
stand up tenant and MFA resources, run MFA operations, provision admins, then govern and observe
the platform—with UI dashboards at the end.**

### 3.1 Admin API — tag order (reference table)

| # | Tag | Rationale |
| --- | --- | --- |
| 1 | Public | Instance metadata and evaluator signup without auth |
| 2 | Admin Authentication | Login, logout, recovery, passwordless wait |
| 3 | Tenants | Multi-tenant foundation |
| 4 | Integrations | Applications protected by MFA |
| 5 | Enrollments | User–device links for integrations |
| 6 | API Keys | M2M access for Integration API |
| 7 | Auth Attempts | MFA attempt administration |
| 8 | Admin Enrollment Management | Operator-driven enrollment / device recovery |
| 9 | Administrator Provisioning | Admin user lifecycle |
| 10 | Audit Logs | Compliance and security monitoring |
| 11 | Encryption Keys | Global Admin crypto lifecycle |
| 12 | Alerts | Operator signals |
| 13 | Dashboard | Aggregated UI stats |

**Taxonomy fix:** single `Public` tag; merged description covering instance info and evaluator signup.

### 3.2 Admin API — `x-tagGroups` (ReDoc)

ReDoc uses `x-tagGroups` as a **second navigation layer** above flat tags. **Every tag must appear
in exactly one group**; an ungrouped tag is hidden in ReDoc CE.

| Group | Tags | Intent |
| --- | --- | --- |
| Getting started | Public, Admin Authentication | Discover the instance and establish an operator session |
| Tenant and integration setup | Tenants, Integrations, Enrollments, API Keys | Build the multi-tenant MFA footprint |
| MFA operations | Auth Attempts, Admin Enrollment Management | Run and recover MFA flows |
| Administration | Administrator Provisioning | Manage admin accounts and onboarding |
| Platform operations | Audit Logs, Encryption Keys, Alerts, Dashboard | Govern, secure, observe, and summarize the platform |

Every tag in section 3.1 must appear in exactly one group. Group order follows the same journey
as section 3.0.

### 3.3 Auth API — tag order

| # | Tag | Rationale |
| --- | --- | --- |
| 1 | Public | `GET` instance info |
| 2 | Enrollments | Device binding lifecycle |
| 3 | Authentication Attempts | Pull-based MFA |

**Operations within tag:**

- Enrollments: `bind` → `verify`
- Authentication Attempts: `pending` → `respond`

### 3.4 Integration API — tag order

Single tag **Auth Attempts** (keep name aligned with product language).

**Operations within tag:**

1. `create`
2. `waitForResponse` (wait)
3. `cancel`

---

## 4. Mapping — ideal vs mechanisms

| Need | Annotations only | + OpenApiCustomizer | + x-tagGroups | Residual risk |
| --- | --- | --- | --- | --- |
| Admin tag sequence | Partial (no order guarantee) | **Yes** | N/A for flat order | Low |
| Admin ReDoc grouping | No | Partial | **Yes** | Must list all tags in groups |
| Dedupe Public tag | Partial (same name, duplicate spec entries) | **Yes** | — | Low |
| Auth tag sequence | Partial | **Yes** | Optional (small API) | Low |
| Auth op journey order | No | **Yes** (path reorder) | — | Medium (test regen clients) |
| Integration op order | No | **Yes** | — | Low |
| Swagger = ReDoc tag order | No | **Yes** + drop `tagsSorter=alpha` | — | Low |

---

## 5. Phase 2 implementation backlog (summary)

Full task list: [`TB-2026-06-02-openapi-presentation-order-phase2.md`](backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md).

| Step | Action | Status |
| --- | --- | --- |
| 1 | Add `OpenApiPresentationCustomizer` in each `OpenApiConfig` | Done |
| 2 | Admin: ordered tags, merge Public, inject `x-tagGroups` | Done |
| 3 | Auth: ordered tags + path order bind/verify, pending/respond | Done |
| 4 | Integration: path order create → wait → cancel | Done |
| 5 | Remove `tagsSorter=alpha` from `application*.properties` | Done |
| 6 | Review `operationsSorter=method` per API | Done (Admin keeps; Auth/Integration removed) |
| 7 | Unify `@Tag` descriptions for Public (Admin) | Done |
| 8 | Maintainer: `scripts/update-specs.sh`, diff `specs/` and portal copies | Done (maintainer) |
| 9 | Manual check: Swagger UI + local ReDoc (`npx serve sites/ezkey-org`) | Done (operator) |
| — | Admin optional: intra-tag path reorder for key flows | Deferred |

### Verification checklist

- [x] Root `tags[]` matches section 3.1 order per API
- [x] No duplicate tag names in Admin spec
- [x] `x-tagGroups` present on Admin spec; ReDoc shows grouped nav
- [x] Auth enrollment and auth-attempt operations in journey order in spec
- [x] Integration operations in create → wait → cancel order
- [x] Swagger UI tag sidebar matches ReDoc tag order (no alpha override)
- [x] No hand-edits under `specs/` except via regeneration
- [ ] Cloudflare preview validation (optional before production promotion) — **deferred**

---

## 6. Relationship to other initiatives

- **OpenAPI spec lifecycle** (`V-2026-06-02-openapi-spec-lifecycle`): host/`servers` normalization
  is orthogonal; customizers should not reintroduce host coupling.
- **Public portal** (`TB-2026-0003`, `I-2026-0026`): this work improves navigability of published
  static specs, not exposure posture.
- **Methodology ordering** (`2026-05-29-curated-public-pack-ordering`): same cognitive-comfort
  principle applied to API reference navigation.

---

## 7. Phase 1 definition of done

- [x] Baseline audit documented (section 1)
- [x] Mechanism matrix and recommendation (section 2)
- [x] Ideal order per API (section 3)
- [x] Admin reader-journey principles documented (section 3.0)
- [x] Bidirectional traceability gate applied (Incubation sources + plan Canonical materialization)
- [x] Mapping and Phase 2 backlog (sections 4–5)
- [x] Canonical artifacts cross-linked (`V-*`, `I-*`, `TB-*`, working plan prompt)

---

## 8. Closeout (2026-06-03)

- **Delivery:** PR `#181` merged to `main`; closes `#180`.
- **Artifacts:** `I-*` and `TB-*` → `done`; `V-*` → `accepted`.
- **Deferred:** Cloudflare preview deploy; Admin intra-tag path reorder within large tags.
- **Maintenance rule:** new Admin tags → update `OpenApiPresentationCustomizer` + `x-tagGroups` per §3.0.
