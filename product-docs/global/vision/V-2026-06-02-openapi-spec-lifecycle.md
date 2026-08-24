# V-2026-06-02-openapi-spec-lifecycle - Host-neutral OpenAPI spec lifecycle

## Metadata

- **ID:** `V-2026-06-02-openapi-spec-lifecycle`
- **Status:** `draft`
- **Lane:** `B` - plan incubation materialized into canonical direction
- **Created at:** `2026-06-02`
- **Updated at:** `2026-08-23`
- **Captured by:** Marc

## Intent

Ezkey should treat versioned OpenAPI specifications as host-neutral contract artifacts. Runtime
hosts, Cloudflare ingress hosts, local development ports, and self-hosted installation domains
belong to deployment packaging or operator configuration, not to canonical API annotations or
repository specs.

## Motivation

Cloudflare API schema validation makes OpenAPI specs operationally valuable beyond documentation.
The first Cloudflare upload experiment for Auth API exposed that current specs mix `localhost` and
non-real or deployment-specific public hosts. That creates duplicate endpoint views in Cloudflare
and blurs the boundary between portable Ezkey contracts and one installation's runtime topology.

The Auth API public/enrolled surface now also includes unsigned `GET /api/v1/public/instance-info`
and integration-signed `POST /api/v1/enrollments/instance-info` (the enrolled installation trust
surface). Cloudflare schema validation remains HTTP/OpenAPI *shape* enforcement. It does not
replace device-side Ed25519 verification of `instanceInfoPayloadSignedByIntegration`.

## Potential impact

- Product milestones: milestone 2 quality and best practices; milestone 3 demo/evaluator
  operations; future self-hosting adoption.
- Components: `auth-api`, `admin-api`, `integration-api`, `specs`, `scripts`, `postman`,
  `ezkey_mobile`, `ezkey-sdk`, `sites/ezkey-org`, `experimental-hybrid`.
- User segments: Ezkey maintainers, self-hosting operators, evaluator deployment operators,
  developers consuming SDK/mobile/generated clients.

## Signals and constraints

- `Auth API` and `Admin API` currently declare explicit `@Server` annotations for `localhost` and
  `*.ezkey.org` hosts.
- `Integration API` does not declare `@Server`, but Springdoc still emits a generated localhost
  server in the canonical spec output, so annotation cleanup alone is not sufficient.
- Postman historically modeled hosts through environments. Living operator collections under `bruno/` do the same (environment base URLs); that is the host-neutral proof to follow, not the Postman tree.
- `EXP1` has concrete Cloudflare/Lightsail public hosts in `experimental-hybrid/lightsail/Caddyfile`.
- Ezkey's self-hosting posture argues against embedding project-specific or operator-specific
  domains in portable source annotations.

## Promotion criteria

The Auth API first slice is implemented (2026-08-23). Integration EXP1 is uploaded
([`TB-2026-08-23-integration-api-cloudflare-schema.md`](../backlog/TB-2026-08-23-integration-api-cloudflare-schema.md)).
EXP1 Auth and Integration may use **Block** as a maintainer-only exception; that is not the
default for later hosts. Admin Cloudflare upload stays deferred. See
[`TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md`](../backlog/TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md).

The first Auth API slice needed to prove that Ezkey can:

- generate a host-neutral canonical Auth API spec;
- generate an EXP1-localized Cloudflare upload artifact from that canonical spec;
- keep downstream clients and Bruno (and leftover Postman environments) aligned without broad host-related churn;
- record enough evidence to generalize the rule to Admin API and Integration API.

## Related documents

- Source working plan: GitHub prompt deleted in the 2026-08 corpus-ablation pass; this vision note and the linked I/TB are canon.
- Backlog idea: [`I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`](../backlog/ideas/I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation.md)
- First tracer bullet: [`TB-2026-06-02-auth-api-cloudflare-schema-first-slice`](../backlog/TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md)
- Integration tracer bullet: [`TB-2026-08-23-integration-api-cloudflare-schema`](../backlog/TB-2026-08-23-integration-api-cloudflare-schema.md)
- Next-phase context: [`TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md`](../backlog/TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md)
- Existing exposure posture: [`openapi-exposure-matrix.md`](../openapi-exposure-matrix.md)
- Prior portal/exposure direction: `V-2026-0014`, `I-2026-0026`, `TB-2026-0003`
