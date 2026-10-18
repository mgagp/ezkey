# Tracer Bullet Brief - `TB-2026-06-02-auth-api-cloudflare-schema-first-slice` Auth API Cloudflare schema first slice

## Metadata

- **ID:** `TB-2026-06-02-auth-api-cloudflare-schema-first-slice`
- **Status:** `done`
- **Related idea:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **Lane:** `B` - plan incubation promoted into executable slice
- **Posture:** `iterative`
- **GitHub PR:** `#478` (merged 2026-08-23)
- **Created at:** `2026-06-02`
- **Updated at:** `2026-08-25`
- **Closed at:** `2026-08-23`
- **Captured by:** Marc

## Objective

Prove the OpenAPI lifecycle direction through the smallest meaningful end-to-end slice: generate a
host-neutral canonical Auth API spec, derive a community-localized Cloudflare schema artifact from it,
and manually validate Cloudflare's endpoint enumeration before any blocking enforcement.

The Auth API operations that must appear **once** under `https://auth-api.ezkey.online` are:

- `GET /api/v1/public/instance-info` (unsigned, display-only)
- `POST /api/v1/enrollments/bind`
- `POST /api/v1/enrollments/verify`
- `POST /api/v1/enrollments/instance-info` (integration-signed installation trust surface)
- `POST /api/v1/auth-attempts/pending`
- `POST /api/v1/auth-attempts/respond`

Cloudflare schema validation is ingress *shape* enforcement. It does not verify
`instanceInfoPayloadSignedByIntegration`. Cryptographic authority stays on the enrolled client
(`TB-2026-08-09-mobile-signed-instance-info`). This slice does not wait for that TB's closeout —
the Auth API contract is already in the live spec.

## Boundaries in scope

- `ezkey-auth-api` OpenAPI metadata and generated `/api-docs` output.
- `scripts/update-specs.sh` canonical spec refresh and dispatch path for Auth API.
- `specs/auth-api/openapi-spec.json` as the host-neutral canonical Auth API spec.
- Generated community host/Cloudflare Auth API schema artifact.
- Downstream Auth API spec consumers: `ezkey_mobile`, `ezkey-sdk`, `ezkey-demo-device`, and
  `sites/ezkey-org` copied spec.
- Operator documentation for the community host manual Cloudflare upload (observe/log-first; both instance-info
  operations included in the enumeration check).

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
3. Add a small deployment-localization path that generates a community Cloudflare Auth API schema with
   exactly one server URL: `https://auth-api.ezkey.online`.
4. Document the manual upload and validation workflow for the community host.
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
  produces a community Cloudflare artifact with a single real public host.
- **Cloudflare manual validation path:** the operator uploads the localized spec and confirms each
  Auth endpoint above is listed once under `auth-api.ezkey.online`. Product default remains
  observe/None for any host that might see third-party traffic. **community-lab exception (2026-08-23):**
  the operator confirmed the community host has no external evaluators and that the maintainer is the only
  tester; **Block** is accepted on that hostname so schema mismatches fail at the edge. Flip back
  to None if a maintainer test locks out enroll or MFA. Do not copy community host Block to a later
  multi-user install without a fresh observe period.
- **Consumer safety path:** mobile and SDK generation still route by runtime configuration rather
  than by canonical `servers` metadata.

## Evidence plan

- Canonical spec check: `specs/auth-api/openapi-spec.json` has no top-level `servers` after refresh.
- Dispatch check: copied Auth specs match the normalized canonical spec.
- Localization check: generated community Cloudflare spec contains exactly one top-level server URL,
  `https://auth-api.ezkey.online`.
- Structural diff check: canonical and localized specs differ only by `servers`.
- Client check: `yarn generate:api` succeeds in `ezkey_mobile/`; Auth SDK generation/build succeeds
  in `ezkey-sdk/javascript/`.
- Documentation check: operator docs describe manual Cloudflare upload and observe/log-first posture.
- Cloudflare check: uploaded schema enumerates each Auth endpoint once under the community host,
  including both instance-info operations.

## Test strategy

- **Minimum checks:** JSON validation, no-`servers` canonical assertion, localized single-server
  assertion, structural diff ignoring `servers`.
- **Relevant generation checks:** mobile Orval regeneration and JavaScript Auth SDK generation/build.
- **Operational/manual check:** Cloudflare upload inspection in observe/log mode.
- **Deferred checks:** automated Cloudflare upload, global Admin/Integration regression checks, and
  community Postman environment smoke tests belong to later slices.
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
3. the community Cloudflare artifact has one and only one target server;
4. downstream Auth API generation still succeeds;
5. the manual Cloudflare upload shows each Auth endpoint once under `auth-api.ezkey.online`,
   including `GET /api/v1/public/instance-info` and `POST /api/v1/enrollments/instance-info`;
6. the next-phase context artifact exists with enough evidence to resume global rollout cleanly.

## Implementation outcome (2026-08-23)

Shipped on branch `tb/2026-06-02-auth-api-cloudflare-schema`. The operator has uploaded the
localized schema (drag-and-drop). Mitigation on the community host is **Block** per the 2026-08-23 exception
below; product default elsewhere remains None.

| Criterion | Evidence |
| --------- | -------- |
| Canonical Auth spec is host-neutral | `specs/auth-api/openapi-spec.json` has no top-level `servers` after `./scripts/update-specs.sh --auth-only` |
| Dispatch copies match | Same 10-line `servers` deletion on demo-device, mobile, JS SDK, and ezkey.org copies |
| community artifact has one server | `./scripts/package-auth-api-cloudflare-schema.sh` writes `https://auth-api.ezkey.online` |
| Cloudflare OAS 3.0 | Packaging downlevels 3.1 `type` arrays to `nullable` (Cloudflare error 50010) |
| Structural diff is servers-only | Packaging script asserts canonical vs localized differ only by `servers` |
| Client generation | Mobile Orval succeeded; `npm run generate-auth` succeeded (JS SDK uses `config.authApiUrl`) |
| Docs | [`docs/cloudflare/auth-api-schema-validation.md`](../../../docs/cloudflare/auth-api-schema-validation.md) |
| Next-phase context | [`TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md`](TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md) |
| community upload | Operator uploaded the localized OAS 3.0 artifact; six Auth operations expected once under `auth-api.ezkey.online` |
| community Set action | **Block** (2026-08-23 operator exception: no external community host traffic; maintainer-only tests). Not the product default. |
| community Bruno negative probe | Schema-invalid bind (`enrollmentId` string) → Cloudflare **403 / 1020** (`cloudflare_error`). Schema-valid pending with bad binding → origin **400** `auth-attempt-binding-failed`. Env: `bruno/environments/community.bru`. |

Notes:

- **community Set action (2026-08-23):** Block is an operator exception because the community host has no
  third-party testers. Escape hatch is None if the maintainer locks themselves out. Do not
  treat Block as the default for Admin/Integration or a later public host.

- Rebuild Auth API (clean-start) before the next `--auth-only` if you want
  `POST /enrollments/instance-info` in the curated presentation order. The 2026-08-23 refresh
  used the already-running container, so that path is present but not yet reordered.
- Do not commit Orval or OpenAPI Generator version churn from a generate-only check.
- Global `spec-test-traceability.md` was not updated: this slice changes host packaging, not
  endpoint behavior.

## Closeout (2026-08-23 / canon synced 2026-08-25)

Exit criteria met on merge of PR `#478`. community Auth schema validation is live with Set action
**Block** (maintainer-only exception). Parent `I-2026-06-02` stays `active` for Admin Cloudflare.
