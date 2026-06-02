# Backlog Idea - `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation` OpenAPI spec lifecycle and Cloudflare validation

## Metadata

- **ID:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **Status:** `ready`
- **Priority:** `P1`
- **Created at:** `2026-06-02`
- **Updated at:** `2026-06-02`
- **Last reviewed at:** `2026-06-02`
- **Progression markers:** `P1-operability`, `P2-hardening`
- **Component tags:** `auth-api`, `admin-api`, `integration-api`, `specs`, `scripts`, `postman`, `cloudflare`, `experimental-hybrid`, `sdk`, `mobile`
- **Lane:** `B` - plan incubation materialized into Lane A backlog scope
- **Captured by:** Marc

## Intent

Define and implement an Ezkey-wide OpenAPI spec lifecycle that keeps canonical specs host-neutral,
then generates deployment-localized schema artifacts for Cloudflare and other ingress validators.
Use Auth API and EXP1 as the first concrete slice because Auth API is the highest-volume public API
surface and the most valuable initial Cloudflare schema-validation target.

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
    validation posture.
  - Verify downstream generation impact for mobile Orval and the JavaScript Auth SDK.
  - Record that Postman remains environment-driven unless endpoint contracts change.
- **Out of scope:**
  - Immediate automated Cloudflare schema upload.
  - Immediate rollout to Admin API and Integration API.
  - Broad Postman variable cleanup.
  - Cloudflare blocking enforcement before observation evidence exists.
  - Treating deployment-localized specs as auditable release artifacts before the manual workflow
    proves what Cloudflare needs operationally.

## Key assumptions

- A normalized canonical spec is preferable to embedding EXP1, Cloudflare, or customer-specific
  domains in Java annotations.
- `scripts/update-specs.sh` should remain the canonical extraction and dispatch entrypoint, while
  deployment localization should be a separately runnable packaging step.
- Postman host behavior is already mostly aligned because environments own base URLs.
- Auth API is the right first slice because it is exposed frequently and represents Ezkey's core MFA
  business flow.

## Risks and exceptions

- Springdoc may emit server URLs even when annotations are removed; normalization must handle this
  explicitly.
- Generated clients may infer a default base path when `servers` is absent; mobile and SDK
  generation must be checked before broader rollout.
- Cloudflare schema validation may have token-scope, versioning, or schema-ID behavior that should
  not be automated until the manual upload path is observed.
- A too-tight coupling between Lightsail deployment and Cloudflare schema upload would make routine
  backend deployments more fragile.

## Promotion notes

This idea is ready for the first tracer bullet because the first executable slice, scope exclusions,
and verification evidence are known. Promote through
[`TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md`](TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md).

## Traceability notes

- Source incubation plan: [`plan-openApiSpecLifecycle.prompt.md`](../../../../.github/prompts/plan-openApiSpecLifecycle.prompt.md)
- Direction note: [`V-2026-06-02-openapi-spec-lifecycle`](../../vision/V-2026-06-02-openapi-spec-lifecycle.md)
- Existing posture baseline: [`../../openapi-exposure-matrix.md`](../../openapi-exposure-matrix.md)
- Related completed portal work: `I-2026-0026`, `TB-2026-0003`
- Global spec-test matrix is not updated in this intake step because no endpoint behavior, test
  evidence, or feature status has changed yet. The first implementation slice must update
  traceability after evidence is produced.

## Links

- Source working plan: [`plan-openApiSpecLifecycle.prompt.md`](../../../../.github/prompts/plan-openApiSpecLifecycle.prompt.md)
- First tracer bullet: [`TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md`](TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md)
- Auth OpenAPI config: [`../../../../ezkey-auth-api/src/main/java/org/ezkey/auth/config/OpenApiConfig.java`](../../../../ezkey-auth-api/src/main/java/org/ezkey/auth/config/OpenApiConfig.java)
- Spec update script: [`../../../../scripts/update-specs.sh`](../../../../scripts/update-specs.sh)
- EXP1 Caddy host source: [`../../../../experimental-hybrid/lightsail/Caddyfile`](../../../../experimental-hybrid/lightsail/Caddyfile)
