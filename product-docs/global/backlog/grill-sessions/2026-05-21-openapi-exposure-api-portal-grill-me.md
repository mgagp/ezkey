# Grill Me — 2026-05-21 (OpenAPI exposure and API portal posture)

## Session control

| Field | Value |
|-------|--------|
| **Vision** | `V-2026-0014` |
| **Backlog** | `I-2026-0026` |
| **Cross-links** | `V-2026-0010`, [`docs/cloudflare/ezkey-org-site.md`](../../../../docs/cloudflare/ezkey-org-site.md), [`sites/ezkey-org/AGENTS.md`](../../../../sites/ezkey-org/AGENTS.md) |
| **Status** | `complete` |
| **Date** | `2026-05-21` |

## Current convergence

| ID | Working decision |
|----|------------------|
| G1 | Keep two concerns explicit: **live API-host exposure policy** and **curated API portal information architecture** must be decided together but not collapsed into one implementation choice. |
| G2 | Treat per-service Springdoc Swagger UI and raw `/api-docs` as **operator/developer tooling**, not as the default long-term public surface for production-capable Ezkey deployments. |
| G3 | Use an explicit **environment matrix** for exposure decisions: `local/dev`, `internal shared`, `evaluator (EXP1)`, `public production-like`, and `test-only`. |
| G4 | Treat `Admin API`, `Auth API`, and optional `Integration API` as the production-capable group; treat `Crypto API` as **test/internal only** and exclude it from the default public portal posture. |
| G5 | Bias the public portal toward a **curated read-only reference** rather than public “try it out” against live endpoints. Interactive tooling may still exist for internal/operator contexts. |
| G6 | The default hosting bias is to reuse the existing static site deployment model under `sites/ezkey-org/` / Cloudflare Pages, not to introduce a new service or worker unless the gains are clear. |
| G7 | The candidate shortlist worth serious comparison is `ReDoc`, `Scalar`, `Swagger UI`, and `RapiDoc`; Cloudflare API Shield is a reference point, not the default direction. |
| G8 | `Integration API` being optional by deployment means the documentation posture must tolerate **partial API groups** without implying that every deployment always exposes every production-capable surface. |
| G9 | The current generated-spec workflow already supports `Admin API` and `Auth API`; `Integration API` and `Crypto API` need an explicit publication decision rather than accidental omission. |
| G10 | The next bounded product-docs slice is documentation and recommendation work: environment matrix, candidate comparison, placement recommendation, and first implementation slice definition. |
| G11 | **Operator decision (2026-05-21):** treat `EXP1` as **prod-like** for raw OpenAPI exposure posture. Public Swagger UI and raw `/api-docs` on API hosts should not be the target evaluator posture; evaluator documentation should converge toward the curated portal / controlled-access model instead. |
| G12 | **Operator decision (2026-05-21):** the public API portal is part of the near-term documentation posture for `ezkey.org`. It may start small and sober, but it is not deferred until a much later repository-opening phase. |
| G13 | **Operator decision (2026-05-21):** the public portal posture is **read-only reference**, not public interactive execution. Any “try it out” capability stays internal or otherwise controlled outside the default public portal. |
| G14 | **Operator decision (2026-05-21):** include `Integration API` in the public/prod-capable documentation group, but keep `Crypto API` outside the public portal and outside the normal public documentation posture. |
| G15 | **Operator decision (2026-05-21):** first public portal shape = **one landing page plus one page per API**, optimized for best fit with `ezkey.org` and the static Cloudflare Pages model. |
| G16 | **Operator decision (2026-05-21):** select **ReDoc CE** as the renderer for the first public portal cut. |

## Outcome

The posture-level decisions needed for the current grill pass are now closed. The next work is not more principle grilling; it is recommendation materialization:

1. write the environment exposure matrix,
2. compare the shortlisted portal candidates,
3. define the concrete hosting / placement recommendation,
4. identify the first bounded execution slice.

All four items are now materially converged at the recommendation level:

- environment matrix documented;
- candidate review documented;
- placement recommendation converged on `ezkey.org` static hosting;
- first bounded execution slice can now be defined.

## Links

- [`../ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md`](../ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md)
- [`../../vision/product-orientation-notes.md`](../../vision/product-orientation-notes.md)
