# Tracer Bullet Brief — `TB-2026-0003` OpenAPI public portal first cut

## Metadata

- **ID:** `TB-2026-0003`
- **Status:** `closed`
- **Related idea:** `I-2026-0026`
- **Created at:** `2026-05-21`
- **Updated at:** `2026-05-22`
- **Captured by:** Marc

## Objective

Deliver the first validated public portal slice for Ezkey API documentation: an English-first `ezkey.org` landing page plus one page per public/prod-capable API using `ReDoc CE`, alongside a small extension of the centralized spec-update workflow for `Integration API` and the first prod-like removal of raw public Springdoc exposure from API hosts.

## Boundaries in scope

- `sites/ezkey-org/` as the public portal host surface.
- Static `ReDoc CE` rendering of Ezkey OpenAPI specs.
- Portal information architecture for `Admin API`, `Auth API`, and optional `Integration API`.
- Lightweight update of `scripts/update-specs.sh` so `Integration API` joins the centralized generated-spec workflow.
- First prod-like hardening path for raw `/api-docs` and embedded Swagger UI exposure on public API hosts.
- Documentation updates that make the public portal the normative public reference posture.

## Out of scope

- Final visual polish or broad marketing copy work.
- Public documentation for `Crypto API`.
- Public interactive “try it out” behavior.
- Broad contract redesign of existing APIs.
- Full automation of every spec publication consumer beyond what the slice strictly needs.

## Critical flows

- **Nominal public-reference path:** a reader opens the API documentation landing page on `ezkey.org`, understands the API grouping and exposure posture, then navigates to a dedicated documentation page for `Admin API`, `Auth API`, or `Integration API`.
- **Prod-like hardening path:** a public/evaluator runtime host that used to expose raw Springdoc docs no longer treats `/api-docs` or embedded Swagger UI as a public reference surface.
- **Optional-surface path:** when `Integration API` is absent in a deployment, the portal posture remains coherent and does not imply that every Ezkey deployment always exposes that surface.

## Evidence plan

- Checked-in portal landing page plan and concrete page set under `sites/ezkey-org/`.
- Checked-in `ReDoc CE` integration pattern suitable for static Cloudflare Pages hosting.
- At least one concrete specification-publication path documented for the portal pages, including how `Integration API` joins the public documentation set.
- `Integration API` spec-generation prerequisite explicitly covered in the centralized update workflow.
- Explicit implementation note for prod-like removal of raw public Springdoc exposure on the selected API-host slice.
- Updated `I-2026-0026` traceability and linked supporting docs (`openapi-exposure-matrix.md`, `openapi-portal-candidate-review.md`).
- Global first-cut design brief: [`../../openapi-public-portal-first-cut-design.md`](../../openapi-public-portal-first-cut-design.md)

## Implementation outcome

- `ezkey.org` public API portal implemented with one landing page plus one page per API.
- `ReDoc CE` integrated as a static renderer and pinned to `v2.5.2`.
- `Integration API` added to the centralized generated-spec workflow and to the portal-published static spec set.
- English-first preview delivered, visually validated, then expanded to French portal-shell parity in the same dossier.
- `EXP1` raw Springdoc exposure hardened on public API hostnames through explicit `Caddy` route blocking.

## Quality gates

- **Portal gate:** the first portal cut stays within the existing static-site model and does not introduce an unnecessary new app stack.
- **Exposure gate:** the selected prod-like runtime slice no longer depends on public raw Springdoc endpoints as the documentation posture.
- **Clarity gate:** the landing page makes the API grouping and documentation segmentation understandable without requiring prior repo knowledge.

## Exit criteria

`TB-2026-0003` is validated when:

1. `ezkey.org` has a defined first-cut API portal shape: one landing page plus one page per public/prod-capable API.
2. The first preview-ready cut is explicitly English-first, without blocking on immediate French parity.
3. `ReDoc CE` is proven as a workable static renderer in Ezkey's hosting model.
4. The public documentation set covers `Admin API` and `Auth API`, and has an explicit rule for optional `Integration API`.
5. One concrete prod-like hardening slice for raw public Springdoc exposure is defined tightly enough for implementation without reopening portal-level strategy.

All five criteria were satisfied in implementation. The slice was then extended with French portal-shell parity after the initial English-first visual validation.

`TB-2026-0003` is now intentionally **closed**. No unresolved blocker remains inside the defined slice.
