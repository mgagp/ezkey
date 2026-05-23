# Backlog Idea — `I-2026-0026` OpenAPI exposure and API portal posture

## Metadata

- **ID:** `I-2026-0026`
- **Status:** `ready`
- **Priority:** `P1`
- **Created at:** `2026-05-21`
- **Updated at:** `2026-05-21`
- **Last reviewed at:** `2026-05-21`
- **Phase tags:** `P1-operability`
- **Component tags:** `docs`, `sites/ezkey-org`, `admin-api`, `auth-api`, `integration-api`, `crypto-api`, `infra`

## Intent

Define an Ezkey-wide policy for how OpenAPI documentation is exposed across deployment postures, and choose the public/internal API portal posture that best fits Ezkey's static `ezkey.org` site, open-source preference, and production hardening expectations.

## Problem and value

- **Problem:** OpenAPI exposure is currently implicit and uneven. Public-evaluator `exp1` exposes Swagger UI and raw `/api-docs` on the production-capable APIs, while the desired production posture is likely stricter. At the same time, Ezkey still needs a credible, inspectable API documentation experience for internal use, evaluators, and future public developer-facing documentation.
- **Expected value:** Ezkey gets a clear documentation posture instead of ad hoc per-service exposure. Operators know which surfaces are public, internal, evaluator-only, or disabled. Public documentation can live in a curated portal that matches the existing `ezkey.org` deployment model without introducing unnecessary platform complexity.

## Scope

- **In scope:**
  - Define the OpenAPI exposure matrix for `Admin API`, `Auth API`, optional `Integration API`, and test-only `Crypto API`.
  - Decide how that matrix differs across local/dev, internal shared, evaluator (`EXP1`), public production-like, and test-only postures.
  - Compare credible open-source portal candidates for Ezkey's needs (`ReDoc`, `Scalar`, `Swagger UI`, `RapiDoc`; Cloudflare API Shield as a reference point only).
  - Decide whether the curated portal should live inside `sites/ezkey-org/`, in a separate Cloudflare Pages project, or in another hosting pattern.
  - Define the public/internal documentation scope and the migration path away from public per-service Swagger as the default posture.
- **Out of scope:**
  - Full implementation of the chosen portal.
  - Final visual branding or marketing copy for the portal.
  - Broad API contract refactors unrelated to documentation exposure.
  - Immediate regeneration or publication automation for every spec-producing consumer.

## Key assumptions

- The simplest acceptable path is likely the best one: reuse the existing static Cloudflare Pages marketing-site model unless a stronger reason emerges.
- Raw Springdoc endpoints should not be the long-term public-facing documentation posture for production-capable deployments.
- `EXP1` must be classified intentionally, not treated as an accidental hybrid of internal and public behavior.

## Triage notes (2026-05-21)

- The dossier naturally splits into two linked decisions:
  - live API-host exposure policy (`/api-docs`, embedded Swagger UI, profile gating);
  - curated portal posture (tool, hosting placement, public/internal scope).
- The existing repository and live-environment evidence are already strong enough to justify an `incubating` status instead of leaving the idea at simple capture.
- The leading simplification bias is to keep the public portal aligned with `ezkey.org` and Cloudflare Pages unless a compelling reason emerges to separate it.
- `EXP1` is no longer treated as an accidental evaluator exception for raw OpenAPI exposure. Working direction: align `EXP1` with the prod-like posture for public API-host Swagger/UI exposure, then satisfy evaluator/reference needs through the curated portal or another controlled documentation path.
- The portal is a near-term public documentation concern for `ezkey.org`, not a distant placeholder to be revisited only after a broader repository-opening milestone.
- The leading public-portal posture is **read-only reference**. Public “try it out” is intentionally out of the default direction and should remain internal or otherwise controlled if ever needed.
- `Integration API` belongs to the public/prod-capable documentation group; `Crypto API` remains outside the public portal and outside the normal public documentation posture.
- The preferred first-cut information architecture is intentionally simple: one landing page plus one page per API, inside the existing `ezkey.org` static site.
- `ReDoc CE` is the selected renderer for the first portal cut because it best matches the public read-only posture and the static-site simplicity target.

## Risks and exceptions

- There is a risk of mixing two concerns that should remain separable: production hardening of live API hosts, and information architecture for a public documentation portal.
- A portal choice that optimizes too hard for interactive "try it out" flows may conflict with Ezkey's public-site simplicity, Cloudflare deployment model, or the desired separation between public reference material and live runtime surfaces.
- `Integration API` and `Crypto API` do not currently share the same generated-spec workflow as `Admin API` and `Auth API`, which may complicate portal consistency if not addressed deliberately.

## Grilling notes (2026-05-21)

See [`../grill-sessions/2026-05-21-openapi-exposure-api-portal-grill-me.md`](../grill-sessions/2026-05-21-openapi-exposure-api-portal-grill-me.md).

## Supporting artifacts

- Environment matrix: [`../../openapi-exposure-matrix.md`](../../openapi-exposure-matrix.md)
- Candidate review: [`../../openapi-portal-candidate-review.md`](../../openapi-portal-candidate-review.md)

## Promotion notes

This idea is now `ready` for tracer-bullet promotion. The posture decisions, exposure matrix, candidate comparison, and preferred placement model are explicit enough to define a bounded execution slice for the first public portal cut and the associated raw-doc exposure hardening work.

Promoted tracer bullet: [`TB-2026-0003-openapi-public-portal-first-cut.md`](TB-2026-0003-openapi-public-portal-first-cut.md)

## Links

- Vision: `V-2026-0014`
- Product framing: [`../../../../PRD.md`](../../../../PRD.md), [`../../../../docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md)
- Hosting context: [`../../../../docs/cloudflare/ezkey-org-site.md`](../../../../docs/cloudflare/ezkey-org-site.md), [`../../../../sites/ezkey-org/AGENTS.md`](../../../../sites/ezkey-org/AGENTS.md)
- Current vision log: [`../../vision/product-orientation-notes.md`](../../vision/product-orientation-notes.md)
- Grill: [`../grill-sessions/2026-05-21-openapi-exposure-api-portal-grill-me.md`](../grill-sessions/2026-05-21-openapi-exposure-api-portal-grill-me.md)
