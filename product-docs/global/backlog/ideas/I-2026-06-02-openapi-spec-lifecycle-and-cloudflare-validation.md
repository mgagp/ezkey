# Backlog Idea - `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation` OpenAPI spec lifecycle and Cloudflare validation

## Metadata

- **ID:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **Status:** `active` (Auth + Integration EXP1 TBs done; Admin Cloudflare deferred)
- **Priority:** `P1`
- **Created at:** `2026-06-02`
- **Updated at:** `2026-08-25`
- **Last reviewed at:** `2026-08-25`
- **Progression markers:** `P1-operability`, `P2-hardening`
- **Component tags:** `auth-api`, `admin-api`, `integration-api`, `specs`, `scripts`, `postman`, `cloudflare`, `experimental-hybrid`, `sdk`, `mobile`
- **Lane:** `B` - plan incubation materialized into Lane A backlog scope
- **Captured by:** Marc

## Intent

Define and implement an Ezkey-wide OpenAPI spec lifecycle that keeps canonical specs host-neutral,
then generates deployment-localized schema artifacts for Cloudflare and other ingress validators.
Use Auth API and EXP1 as the first concrete slice because Auth API is the highest-volume public API
surface and the most valuable initial Cloudflare schema-validation target.

The Auth API public/enrolled surface now includes, besides bind / verify / pending / respond:

- `GET /api/v1/public/instance-info` — unsigned, display-only branding.
- `POST /api/v1/enrollments/instance-info` — integration-signed installation branding; this is the
  enrolled **trust surface** used by mobile to validate installation metadata
  (`I-2026-08-09-mobile-signed-instance-info-integrity`).

Cloudflare schema validation is complementary ingress hardening (HTTP method, path, and request
shape). It does **not** verify Ed25519 signatures. Cryptographic authority stays on the enrolled
client.

## Problem and value

- **Problem:** Canonical OpenAPI output currently mixes portable API contracts with local and
  deployment-specific hostnames. Cloudflare schema upload then sees duplicate endpoint groupings and
  cannot cleanly represent the intended EXP1 Auth API host without manual interpretation.
- **Expected value:** Ezkey gains a portable contract baseline for self-hosting, a deterministic
  path to deployment-localized Cloudflare schema artifacts, and a clean foundation for future
  automated schema deployment as part of the Lightsail/Cloudflare operator workflow.

## Scope

- **In scope:**
  - Establish the canonical/deployment artifact split for OpenAPI specs.
  - Normalize Auth API canonical spec output so it has no top-level `servers` array.
  - Generate an EXP1/Cloudflare Auth API schema artifact with exactly one target server:
    `https://exp1-auth-api.ezkey.org`.
  - Document first-slice operator workflow for manual Cloudflare upload and observe/log-first
    validation posture. The Cloudflare enumeration check must include both instance-info
    operations once under `exp1-auth-api.ezkey.org`.
  - Verify downstream generation impact for mobile Orval and the JavaScript Auth SDK.
  - Record that Bruno (and leftover Postman) remain environment-driven unless endpoint contracts
    change.
  - Integration API EXP1 slice: host-neutral canonical spec, one-host Cloudflare artifact, and
    operator upload on `https://exp1-integration-api.ezkey.org`
    (`TB-2026-08-23-integration-api-cloudflare-schema`).
- **Out of scope:**
  - Immediate automated Cloudflare schema upload.
  - Immediate Admin API Cloudflare upload (Integration EXP1 is the current slice).
  - Broad Postman variable cleanup.
  - Cloudflare blocking enforcement before observation evidence exists.
  - Treating deployment-localized specs as auditable release artifacts before the manual workflow
    proves what Cloudflare needs operationally.

## Key assumptions

- A normalized canonical spec is preferable to embedding EXP1, Cloudflare, or customer-specific
  domains in Java annotations.
- `scripts/update-specs.sh` should remain the canonical extraction and dispatch entrypoint, while
  deployment localization should be a separately runnable packaging step.
- Postman host behavior is already mostly aligned because environments own base URLs. Living
  operator collections under `bruno/` follow the same pattern.
- Auth API is the right first slice because it is exposed frequently and represents Ezkey's core MFA
  business flow, including the signed enrolled instance-info trust refresh.
- Cloudflare schema validation checks request/response *shape* against OpenAPI. It is not a
  substitute for `instanceInfoPayloadSignedByIntegration` verification on the device.
- The signed instance-info Auth API contract is already in the live spec; this slice does not wait
  for `TB-2026-08-09-mobile-signed-instance-info` closeout.

## Risks and exceptions

- Springdoc may emit server URLs even when annotations are removed; normalization must handle this
  explicitly.
- Generated clients may infer a default base path when `servers` is absent; mobile and SDK
  generation must be checked before broader rollout.
- Cloudflare schema validation may have token-scope, versioning, or schema-ID behavior that should
  not be automated until the manual upload path is observed.
- A too-tight coupling between Lightsail deployment and Cloudflare schema upload would make routine
  backend deployments more fragile.
- Observe/log-first remains the default whenever third-party traffic is possible: a false Block
  on `POST /api/v1/enrollments/instance-info` (or bind / verify / pending / respond) would
  interrupt the enrolled trust-refresh path.
- **EXP1 exception (2026-08-23):** the operator confirmed EXP1 has no external evaluators and
  that the maintainer is the only tester. **Block** is accepted on `exp1-auth-api.ezkey.org`
  only, so schema mismatches fail at the edge. Flip to None if a maintainer test locks out
  enroll or MFA. Do not copy that action to a later multi-user host without a fresh observe
  period.

## Promotion notes

First Auth API slice is implemented. EXP1 schema is uploaded. Mitigation on EXP1 is **Block**
(maintainer-only exception, 2026-08-23). Product default elsewhere remains None.

- Tracer bullet (Auth): [`TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md`](../TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md)
- Tracer bullet (Integration EXP1): [`TB-2026-08-23-integration-api-cloudflare-schema.md`](../TB-2026-08-23-integration-api-cloudflare-schema.md)
- Next-phase context (Admin still deferred): [`TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md`](../TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md)
- Operator runbook (Auth): [`docs/cloudflare/auth-api-schema-validation.md`](../../../../docs/cloudflare/auth-api-schema-validation.md)
- Operator runbook (Integration): [`docs/cloudflare/integration-api-schema-validation.md`](../../../../docs/cloudflare/integration-api-schema-validation.md)

## Close-out notes (2026-08-23 / canon synced 2026-08-25)

- Host-neutral Auth canonical spec + EXP1 packaging path are in repo. Auth TB
  [`TB-2026-06-02-auth-api-cloudflare-schema-first-slice`](../TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md)
  is `done` (PR `#478`).
- EXP1 Auth Cloudflare upload succeeded; Set action is Block (EXP1-only exception).
- Integration EXP1 is uploaded and on **Block** (maintainer-only exception, 2026-08-23),
  same reason as Auth. Schema `ezkey-integration-api-exp1`
  (`bf5cab36-d09c-4b0e-b551-5c23bc17682f`). Functional check: schema-invalid create → 403 /
  1020; schema-valid create with dummy Basic → origin 401. Integration TB
  [`TB-2026-08-23-integration-api-cloudflare-schema.md`](../TB-2026-08-23-integration-api-cloudflare-schema.md)
  is `done` (PR `#479`).
- Admin Cloudflare upload remains deferred pending analysis (plan size, body-inspection limit,
  contract volatility). Optional Admin host-neutral `servers` strip is not in the Integration
  slice. This idea stays `active` for that residual.
- `product-docs/global/spec-test-traceability.md` is unchanged: no endpoint contract change.

## Traceability notes

- Source incubation plan: GitHub prompt deleted in the 2026-08 corpus-ablation pass; this idea and the linked V/TB are canon.
- Direction note: [`V-2026-06-02-openapi-spec-lifecycle`](../../vision/V-2026-06-02-openapi-spec-lifecycle.md)
- Existing posture baseline: [`../../openapi-exposure-matrix.md`](../../openapi-exposure-matrix.md)
- Related completed portal work: `I-2026-0026`, `TB-2026-0003`
- Adjacent trust-surface work (already in Auth API contract; complementary, not a dependency):
  [`I-2026-08-09-mobile-signed-instance-info-integrity`](I-2026-08-09-mobile-signed-instance-info-integrity.md),
  [`TB-2026-08-09-mobile-signed-instance-info`](../TB-2026-08-09-mobile-signed-instance-info.md)
- Global spec-test matrix stays unchanged: this slice is host-metadata packaging, not an endpoint
  behavior change.

## Links

- Source working plan: GitHub prompt deleted in the 2026-08 corpus-ablation pass; this idea and the linked V/TB are canon.
- First tracer bullet: [`TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md`](../TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md)
- Integration tracer bullet: [`TB-2026-08-23-integration-api-cloudflare-schema.md`](../TB-2026-08-23-integration-api-cloudflare-schema.md)
- Next-phase context: [`TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md`](../TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md)
- Operator runbook (Auth): [`../../../../docs/cloudflare/auth-api-schema-validation.md`](../../../../docs/cloudflare/auth-api-schema-validation.md)
- Operator runbook (Integration): [`../../../../docs/cloudflare/integration-api-schema-validation.md`](../../../../docs/cloudflare/integration-api-schema-validation.md)
- Auth OpenAPI config: [`../../../../ezkey-auth-api/src/main/java/org/ezkey/auth/config/OpenApiConfig.java`](../../../../ezkey-auth-api/src/main/java/org/ezkey/auth/config/OpenApiConfig.java)
- Spec update script: [`../../../../scripts/update-specs.sh`](../../../../scripts/update-specs.sh)
- EXP1 Caddy host source: [`../../../../experimental-hybrid/lightsail/Caddyfile`](../../../../experimental-hybrid/lightsail/Caddyfile)
