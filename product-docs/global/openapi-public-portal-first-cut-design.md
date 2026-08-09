# OpenAPI Public Portal — First Cut Design

## Purpose

Define the first executable design slice for `TB-2026-0003`: a public `ezkey.org` API portal using `ReDoc CE`, with a simple information architecture and a matching first prod-like hardening step for raw Springdoc exposure.

## Slice summary

The first cut is intentionally a **bounded one-shot implementation slice**, not a chain of artificial micro-slices:

1. add a public API portal landing page on `ezkey.org`;
2. add one public documentation page per API in the public/prod-capable group;
3. extend the centralized spec-update workflow so `Integration API` joins the published spec set;
4. publish the required static spec assets for those pages;
5. define and implement the first prod-like hardening step on the public API hosts, starting with `EXP1`.

## Scope in this slice

- Public site pages under `sites/ezkey-org/` for the API portal.
- Static `ReDoc CE` integration pattern compatible with Cloudflare Pages.
- Static publication of public-portal OpenAPI specs for `Admin API`, `Auth API`, and optional `Integration API`.
- Lightweight extension of `scripts/update-specs.sh` so `Integration API` has the same centralized publication path as `Admin API` and `Auth API`.
- First hardening step that removes raw Springdoc as a public reference surface on `EXP1` API hostnames.
- Minimal documentation updates to make the portal the public reference entry point.

## Proposed public information architecture

### Navigation integration

- Add a **new primary navigation item** dedicated to the public API portal.
- Keep the existing `Docs` / `Documentation` item for the broader repository/source/evaluation context.
- Do **not** merge language labels into the nav item itself; keep the existing bilingual mirroring pattern of the site.

Selected labels:

- English: `API Docs`
- French: `Documentation API`

Recommended primary-nav order after this addition:

- English: `Home | Docs | API Docs | Run locally | Trust | Updates | Guides | Articles`
- French: `Accueil | Documentation | Documentation API | Exécuter en local | Confiance | Mises à jour | Guides | Articles`

### Public pages

- `API portal landing page`
  - purpose: explain the Ezkey API group, public/reference posture, and links to each API page
  - audience: evaluators, integrators, technically curious readers
- `Admin API page`
  - operator/admin reference surface
- `Auth API page`
  - mobile/device-facing reference surface
- `Integration API page`
  - machine-to-machine reference surface

### Public exclusions

- No public page for `Crypto API`
- No public interactive execution
- No implication that every Ezkey deployment always includes `Integration API`

### Launch posture

- Launch the first portal cut in **English first**.
- Keep the route structure bilingual-ready from day one so French parity can be added without reshaping the IA later.
- French parity is a planned follow-up, not a blocker for the first preview deployment.
- Implementation close-out: the first preview was validated in English, then French portal-shell parity was added in the same dossier before close-out.
- The accepted French level for this dossier is the **portal shell and navigation**. Generated OpenAPI description text remains in the source-language contract artifacts; no annotation-by-annotation translation is required for this slice.

### Recommended routes and filenames

Use the existing site pattern: flat HTML pages at the site root, with mirrored filenames under `fr/`.

Recommended landing page:

- English: `/api-docs.html`
- French: `/fr/api-docs.html`

Recommended API pages:

- `Admin API`
  - English: `/admin-api-reference.html`
  - French: `/fr/admin-api-reference.html`
- `Auth API`
  - English: `/auth-api-reference.html`
  - French: `/fr/auth-api-reference.html`
- `Integration API`
  - English: `/integration-api-reference.html`
  - French: `/fr/integration-api-reference.html`

Why these route shapes:

- `api-docs.html` is short, natural, and consistent with the selected nav label.
- Per-API filenames remain explicit enough that they are clearly documentation pages, not runtime hostnames.
- The mirrored `fr/` filenames stay aligned with the current bilingual IA rules already used by the site.

## Static spec publication model

### First-cut source model

- Treat checked-in generated specs as the portal input for the first cut:
  - existing: `specs/admin-api/openapi-spec.json`
  - existing: `specs/auth-api/openapi-spec.json`
  - required prerequisite in this slice: establish the equivalent publication path for `Integration API`
- Publish portal-consumable copies as static assets alongside the public site deployment.

### Practical first-cut posture

- `Admin API` and `Auth API` can be wired immediately from the existing generated-spec workflow.
- `Integration API` must join through one explicit publication path in the same slice so the portal does not encode an accidental asymmetry forever.
- The current scripts prove the pattern already; the expected change is intentionally **small**:
  - add `Integration API` URL handling to `scripts/update-specs.sh`;
  - create and maintain `specs/integration-api/openapi-spec.json`;
  - update `specs/README.md` so the centralized spec workflow documents the new surface.
- `Crypto API` remains outside this publication set.

## ReDoc CE integration posture

### First-cut implementation model

- Keep the integration static and simple:
  - one HTML page per API;
  - each page loads `ReDoc CE`;
  - each page points to a local static spec asset published with the site.
- Keep the landing page editorial and navigational rather than trying to make ReDoc solve portal-wide information architecture by itself.

### Version pinning posture

- Use the official Redocly CDN for the first cut, but pin it to an explicit version.
- Do **not** use the floating `latest` alias for the deployed public portal.
- Current pinned version for the delivered slice: `ReDoc CE v2.5.2`.
- Self-hosting the bundle may be revisited later, but is not required for this first validated cut.

### Why this is the right first cut

- best fit with the existing `sites/ezkey-org/` static model;
- easy to review and reason about;
- no new frontend build stack required for the public portal;
- keeps the renderer replaceable later if Ezkey ever wants a richer shell.

## Hardening slice on public API hosts

### Selected first runtime target

Use `EXP1` as the first concrete prod-like hardening target.

### First-cut hardening objective

On `EXP1` public API hostnames:

- raw `/api-docs` should no longer be treated as a public documentation surface;
- embedded Swagger UI should no longer be treated as a public documentation surface;
- the public portal on `ezkey.org` becomes the intended public reference entry point.

### Acceptable implementation forms for this slice

- Springdoc disable / profile-based disable on the relevant public runtime posture;
- reverse-proxy deny or equivalent route blocking on public hostnames;
- another explicit mechanism that achieves the same public effect cleanly.

The slice does **not** need to settle the final universal mechanism for every future deployment, but it must establish one clean prod-like pattern on `EXP1`.

## Data and mapping impact

- `specs/**` become the durable source inputs for the public portal pages.
- Public site assets need a clear mapping from:
  - portal page -> static OpenAPI asset
  - API group -> portal visibility rule
  - deployment posture -> raw-doc exposure rule

## Validation and error behavior

- If `Integration API` is not available for a given deployment/documentation set, the landing page must say so explicitly instead of showing a broken or misleading link.
- If a spec asset is missing, the page should fail in a diagnosable way during review/build preparation, not silently degrade in production.
- Public runtime hardening should fail closed for raw host exposure on the chosen prod-like slice.

## Contract impact

- No business API contract change is required for the first portal cut.
- Runtime documentation exposure behavior does change on the selected prod-like host slice and must be documented as an operational/public-surface change.

## Test impact

- Public-site review of landing page and API pages.
- Validation that each page resolves its intended static spec asset.
- Validation that the centralized spec-update workflow now produces the `Integration API` spec from the running local stack.
- Validation that `EXP1` no longer exposes the selected raw Springdoc public routes once the hardening slice is implemented.
- Manual exploratory verification is appropriate for the first cut; deeper automation can follow if the pattern stabilizes.

## Documentation impact

- `sites/ezkey-org/` gains a new API-portal lane.
- Public docs posture should be reflected in the relevant Cloudflare/site notes if the public navigation changes.
- `I-2026-0026`, `TB-2026-0003`, `openapi-exposure-matrix.md`, and `openapi-portal-candidate-review.md` remain the canonical reasoning chain for this direction.
- `sites/ezkey-org/AGENTS.md` should be updated when implementation starts so the primary-nav table and mirrored-URL rules explicitly include the new API portal pages.

## Resolved in this slice

- `EXP1` hardening mechanism selected for the first implementation cut: **edge/proxy block** on the public API hostnames via the Lightsail `Caddyfile`.
- `ReDoc CE` deployment posture selected for the first implementation cut: **official CDN with explicit version pin** (`v2.5.2`).
- French parity decision for the first implementation cut: **portal-shell parity is sufficient**; OpenAPI annotation text remains in the source-language generated specs.

## Version re-evaluation rule

The pinned `ReDoc CE` version is **not** reviewed on a calendar cadence for this dossier. Re-evaluate it only when one of these triggers appears:

1. a security, availability, or CDN-trust problem affects the current deployment posture;
2. a rendering bug, compatibility issue, or missing capability is blocking the Ezkey portal with the current pinned version;
3. Ezkey deliberately chooses to revisit the renderer posture itself (for example, deeper customization, self-hosting, or a renderer replacement discussion).

When one of those triggers applies, the update path is fixed:

1. choose an explicit target version;
2. pin it;
3. deploy a preview;
4. run human visual validation;
5. then promote to production.

Without one of those triggers, **stay on `v2.5.2`** and treat the dependency posture as closed for this slice.

## Close-out state

No open question remains that blocks closing this dossier at the first-cut level.

## Links

- Tracer bullet: [`backlog/TB-2026-0003-openapi-public-portal-first-cut.md`](backlog/TB-2026-0003-openapi-public-portal-first-cut.md)
- Backlog: [`backlog/ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md`](backlog/ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md)
- Candidate review: [`openapi-portal-candidate-review.md`](openapi-portal-candidate-review.md)
- Exposure matrix: [`openapi-exposure-matrix.md`](openapi-exposure-matrix.md)
