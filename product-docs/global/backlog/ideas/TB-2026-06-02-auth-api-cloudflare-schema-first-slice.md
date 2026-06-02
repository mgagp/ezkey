# Tracer Bullet Brief - `TB-2026-06-02-auth-api-cloudflare-schema-first-slice` Auth API Cloudflare schema first slice

## Metadata

- **ID:** `TB-2026-06-02-auth-api-cloudflare-schema-first-slice`
- **Status:** `draft`
- **Related idea:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **Lane:** `B` - plan incubation promoted into executable slice
- **Posture:** `iterative`
- **Created at:** `2026-06-02`
- **Updated at:** `2026-06-02`
- **Captured by:** Marc

## Objective

Prove the OpenAPI lifecycle direction through the smallest meaningful end-to-end slice: generate a
host-neutral canonical Auth API spec, derive an EXP1-localized Cloudflare schema artifact from it,
and manually validate Cloudflare's endpoint enumeration before any blocking enforcement.

## Boundaries in scope

- `ezkey-auth-api` OpenAPI metadata and generated `/api-docs` output.
- `scripts/update-specs.sh` canonical spec refresh and dispatch path for Auth API.
- `specs/auth-api/openapi-spec.json` as the host-neutral canonical Auth API spec.
- Generated EXP1/Cloudflare Auth API schema artifact.
- Downstream Auth API spec consumers: `ezkey_mobile`, `ezkey-sdk`, `ezkey-demo-device`, and
  `sites/ezkey-org` copied spec.
- Operator documentation for EXP1 manual Cloudflare upload.

## Out of scope

- Admin API and Integration API host-neutral rollout.
- Cloudflare API upload automation.
- Cloudflare blocking validation.
- Broad Postman collection or environment cleanup.
- Public portal renderer redesign.
- Treating generated Cloudflare specs as long-lived release artifacts.

## First executable slice

1. Remove misleading Auth API `@Server` declarations from source annotations.
2. Add an explicit canonical spec normalization step that removes top-level `servers` from Auth API
   spec output after fetch and before dispatch.
3. Add a small deployment-localization path that generates an EXP1 Cloudflare Auth API schema with
   exactly one server URL: `https://exp1-auth-api.ezkey.org`.
4. Document the manual upload and validation workflow for EXP1.
5. Validate client generation and Cloudflare endpoint enumeration.

## Rollback or fallback posture

- If generated clients fail without canonical `servers`, keep the host-neutral principle but add a
  consumer-specific generation transform rather than restoring deployment hosts to annotations.
- If Cloudflare requires schema metadata not present in the first localized artifact, extend only the
  deployment-localization step.
- If Auth API normalization causes unexpected dispatch drift, pause global rollout and keep Admin
  API / Integration API unchanged until the Auth path is stable.

## Critical flows

- **Canonical refresh path:** local Auth API runtime emits `/api-docs`; `scripts/update-specs.sh
  --auth-only` fetches it, normalizes away top-level `servers`, validates JSON, and dispatches the
  host-neutral spec to consumers.
- **Deployment localization path:** a generated packaging step reads the canonical Auth API spec and
  produces an EXP1 Cloudflare artifact with a single real public host.
- **Cloudflare manual validation path:** the operator uploads the localized spec, confirms each Auth
  endpoint is listed once under `exp1-auth-api.ezkey.org`, and keeps validation in observe/log mode
  before blocking.
- **Consumer safety path:** mobile and SDK generation still route by runtime configuration rather
  than by canonical `servers` metadata.

## Evidence plan

- Canonical spec check: `specs/auth-api/openapi-spec.json` has no top-level `servers` after refresh.
- Dispatch check: copied Auth specs match the normalized canonical spec.
- Localization check: generated EXP1 Cloudflare spec contains exactly one top-level server URL,
  `https://exp1-auth-api.ezkey.org`.
- Structural diff check: canonical and localized specs differ only by `servers`.
- Client check: `yarn generate:api` succeeds in `ezkey_mobile/`; Auth SDK generation/build succeeds
  in `ezkey-sdk/javascript/`.
- Documentation check: operator docs describe manual Cloudflare upload and observe/log-first posture.
- Cloudflare check: uploaded schema enumerates each Auth endpoint once under the EXP1 host.

## Test strategy

- **Minimum checks:** JSON validation, no-`servers` canonical assertion, localized single-server
  assertion, structural diff ignoring `servers`.
- **Relevant generation checks:** mobile Orval regeneration and JavaScript Auth SDK generation/build.
- **Operational/manual check:** Cloudflare upload inspection in observe/log mode.
- **Deferred checks:** automated Cloudflare upload, global Admin/Integration regression checks, and
  EXP1 Postman environment smoke tests belong to later slices.
- **Rejected for this slice:** Playwright UI tests, because no Admin UI behavior changes.

## Quality gates

- **Contract gate:** endpoint paths, request/response schemas, tags, and security schemes remain
  unchanged except for host metadata packaging.
- **Self-hosting gate:** no deployment-specific host is introduced in Java annotations or canonical
  repository specs.
- **Packaging gate:** generated deployment-localized specs are deterministic and independently
  runnable outside Lightsail deployment.
- **Operational gate:** Cloudflare schema validation is observed before blocking.

## Traceability updates required at implementation closeout

- Update this `TB-*` with implementation outcome and evidence.
- Update `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation` status and close-out notes.
- Update `product-docs/global/spec-test-traceability.md` only after concrete spec/test evidence
  exists.
- Create the planned follow-up context artifact for the global rollout before closing the slice.

## Exit criteria

`TB-2026-06-02-auth-api-cloudflare-schema-first-slice` is validated when:

1. the canonical Auth API spec is host-neutral;
2. every Auth API dispatch copy is host-neutral;
3. the EXP1 Cloudflare artifact has one and only one target server;
4. downstream Auth API generation still succeeds;
5. the manual Cloudflare upload shows each Auth endpoint once under `exp1-auth-api.ezkey.org`;
6. the next-phase context artifact exists with enough evidence to resume global rollout cleanly.
