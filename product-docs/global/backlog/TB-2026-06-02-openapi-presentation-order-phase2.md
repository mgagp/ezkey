# Tracer Bullet — `TB-2026-06-02` OpenAPI presentation order (Phase 2 implementation)

## Metadata

- **ID:** `TB-2026-06-02-openapi-presentation-order-phase2`
- **Status:** `done`
- **Parent idea:** `I-2026-06-02-openapi-api-reference-presentation-order`
- **GitHub issue:** `#180` (closed)
- **GitHub PR:** `#181` (merged 2026-06-03)
- **GitHub branch:** `feature/180-i-2026-06-02-openapi-api-reference-presentation-order` (merged)
- **Created at:** `2026-06-02`
- **Closed at:** `2026-06-03`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`, `specs`, `sites/ezkey-org`

## Slice goal

Implement curated OpenAPI tag order (and critical operation order) so runtime Swagger UI and
public ReDoc show the same journey-oriented navigation defined in
[`openapi-presentation-order-design.md`](../../openapi-presentation-order-design.md).

## Preconditions

- Phase 1 design doc accepted.
- Local Docker stack can run Admin (9080), Auth (8080), Integration (7080) for `update-specs`.

## Tasks

### A. Shared approach

1. Add `OpenApiCustomizer` bean in each API module's `OpenApiConfig` (or small dedicated config
   class) that:
   - builds ordered `tags` list per design doc section 3;
   - removes duplicate tag definitions (Admin `Public`);
   - sets extension `x-tagGroups` on Admin OpenAPI root object only.
2. Where operation journey order matters, rebuild `paths` as insertion-ordered map (LinkedHashMap)
   or use Springdoc-supported ordering—verify output in `/api-docs` before commit.

### B. Admin API (`ezkey-admin-api`)

| File | Change |
| --- | --- |
| `.../config/OpenApiConfig.java` | Add presentation customizer bean |
| `PublicInstanceInfoController.java` | Align `@Tag` description with evaluator signup controller |
| `PublicEvaluatorSignupController.java` | Same unified `Public` description |
| `config/application.properties` (+ docker/windows variants) | Remove `tagsSorter=alpha`; review `operationsSorter` |

### C. Auth API (`ezkey-auth-api`)

| File | Change |
| --- | --- |
| `.../config/OpenApiConfig.java` | Presentation customizer: tag order + path order bind→verify, pending→respond |
| `config/application*.properties` | Remove `tagsSorter=alpha`; remove or keep `operationsSorter` based on verification |

### D. Integration API (`ezkey-integration-api`)

| File | Change |
| --- | --- |
| `.../config/OpenApiConfig.java` | Path order: create → waitForResponse → cancel |
| `config/application*.properties` | Remove `tagsSorter=alpha` |

### E. Spec regeneration (maintainer)

1. Start clean stack.
2. Run `scripts/update-specs.sh` (or `.bat`).
3. Confirm copies updated: `sites/ezkey-org/api-specs/*.json`, SDK/mobile paths per script.

### F. Verification

| Check | How |
| --- | --- |
| Admin tags[] order | `jq '.tags[].name' specs/admin-api/openapi-spec.json` |
| No duplicate Public | `jq '.tags \| map(.name) \| group_by(.) \| map(select(length>1))' specs/admin-api/openapi-spec.json` → `[]` |
| x-tagGroups present | `jq '.["x-tagGroups"]' specs/admin-api/openapi-spec.json` |
| Auth operation order | Inspect paths under enrollments and auth-attempts in spec |
| Swagger vs ReDoc | Open Swagger UI + local site ReDoc pages side by side |
| Client impact | Spot-check Orval/mobile only if path order or operationIds unchanged |

## Out of scope for this TB

- Crypto API
- French translation of OpenAPI descriptions
- Host-neutral `servers` normalization (separate TB under spec lifecycle)

## Definition of done

- [x] Tasks A–D, F (local ReDoc + Swagger) complete.
- [x] Task E (spec regen) complete via maintainer `update-specs`.
- [x] Bidirectional traceability gate (see [`2026-06-02-plan-incubation-bidirectional-traceability.md`](../../../methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md)).
- [x] PR `#181` merged to `main`; parent `I-*` moved to `done`.
- [ ] Cloudflare preview before production portal promotion — **deferred** (optional follow-up).

**Canonical reader-journey text:** design doc
[`§3.0`](../../openapi-presentation-order-design.md#30-admin-api--presentation-principles-reader-journey).

## Closeout

- **Merge:** PR `#181` → `main` (`5ddb68a2`, 2026-06-03).
- **Issue:** `#180` closed.
- **Slice closed:** presentation order for Admin/Auth/Integration delivered; methodology gate applied on branch and merged.

## Incubation sources

- Working plan (GitHub): [`.github/prompts/plan-openApiPresentationOrder.prompt.md`](../../../../.github/prompts/plan-openApiPresentationOrder.prompt.md)
- Working plan (Cursor): [`.cursor/plans/openapi_ordering_strategy_86087546.plan.md`](../../../../.cursor/plans/openapi_ordering_strategy_86087546.plan.md)
- Lane: `B` — plan incubation, materialized `2026-06-02`

## Related

- [`openapi-presentation-order-design.md`](../../openapi-presentation-order-design.md)
- [`I-2026-06-02-openapi-api-reference-presentation-order.md`](I-2026-06-02-openapi-api-reference-presentation-order.md)
- [`2026-06-02-plan-incubation-bidirectional-traceability.md`](../../../methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md)
