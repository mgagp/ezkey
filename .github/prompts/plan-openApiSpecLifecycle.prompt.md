## Plan: OpenAPI Spec Lifecycle

TL;DR: start with Auth API because it is the highest-value Cloudflare validation target, but frame the work as the first slice of a global OpenAPI lifecycle. Canonical specs should be host-neutral; deployment-specific hosts belong to generated deployment artifacts. The first slice produces a clean Auth API EXP1/Cloudflare upload file with manual upload. Later slices generalize the rule across Admin/Auth/Integration, Postman environments, local/Docker posture, and a loosely coupled Lightsail -> Cloudflare schema deployment hook.

**Current Findings**
- `ezkey-auth-api/src/main/java/org/ezkey/auth/config/OpenApiConfig.java` hardcodes `http://localhost:8080` and `https://auth-api.ezkey.org` through `@Server` annotations.
- `ezkey-admin-api/src/main/java/org/ezkey/admin/config/OpenApiConfig.java` has the same pattern for Admin API.
- `ezkey-integration-api/src/main/java/org/ezkey/integration/api/config/OpenApiConfig.java` does not declare servers, but `specs/integration-api/openapi-spec.json` still contains a Springdoc-generated `http://localhost:7080` server. Therefore removing annotations alone is not sufficient to guarantee host-neutral canonical specs.
- `scripts/update-specs.sh` currently does three jobs together: fetch from local runtime `/api-docs`, pretty-print/validate JSON, and dispatch copies to project consumers.
- Postman collections under `postman/collections/v2.1/` already use environment variables such as `{{base_url_admin_api}}`, `{{base_url_integration_api}}`, and `{{base_url}}`; this is aligned with host-neutral OpenAPI and should not require collection changes unless endpoint contracts change.
- EXP1 public API hostnames are represented by `experimental-hybrid/lightsail/Caddyfile`: `exp1-auth-api.ezkey.org`, `exp1-admin-api.ezkey.org`, and `exp1-integration-api.ezkey.org`.
- `experimental-hybrid/scripts/full-exp-environment-upgrade.sh` already acts as the mini-pipeline coordinator for build/export/remote-up/Admin UI Cloudflare Pages deployment.
- Existing Cloudflare scripts under `scripts/cloudflare/` use root `.env` variables such as `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`, but they currently target Pages deployment, not API Shield/schema upload.

**Strategy**
- Treat OpenAPI host handling as an artifact lifecycle concern, not as business endpoint annotation design.
- Separate four artifact classes:
  1. Runtime Springdoc output: generated from a running API, allowed to reflect local runtime details.
  2. Canonical versioned spec: repository contract artifact, normalized and host-neutral.
  3. Consumer copies: SDK/mobile/UI/site copies derived from canonical specs.
  4. Deployment-localized specs: generated artifacts for Cloudflare, EXP1, PME deployments, or other ingress validators.
- Keep `update-specs.sh` as the canonical extraction/dispatch command, but add a deliberate normalization step so canonical specs are neutral even when Springdoc injects `Generated server url`.
- Keep deployment localization separate from canonical extraction. It can be invoked by deployment scripts later, but it should be independently runnable and testable.

**Steps**
1. Methodology materialization, before implementation
   - Create a `V-*` vision note under `product-docs/global/vision/` for the host-neutral OpenAPI principle.
   - Create an `I-*` backlog idea under `product-docs/global/backlog/ideas/` for global OpenAPI spec lifecycle and Cloudflare schema validation.
   - Create a narrow `TB-*` tracer bullet for the first Auth API slice.
   - Cross-link `product-docs/global/openapi-exposure-matrix.md`, because it already establishes that EXP1 should rely on curated/published artifacts rather than public raw Springdoc.
   - Include a note that this started as plan incubation and should be materialized, not retrofitted.

2. First executable slice: Auth API canonical neutrality
   - Remove hardcoded `@Server` declarations from `ezkey-auth-api/src/main/java/org/ezkey/auth/config/OpenApiConfig.java` to eliminate the misleading `auth-api.ezkey.org` product-technical assumption.
   - Update OpenAPI config Javadoc so it no longer claims development and production servers are defined there.
   - Add or define a normalization step in `scripts/update-specs.sh` for Auth API that removes the top-level `servers` array from the canonical spec after fetch and before dispatch.
   - Make the normalization explicit and named, not a hidden `jq` one-liner buried in the download block. This becomes the future extension point for Admin/Integration.
   - Keep endpoint paths, request/response schemas, security schemes, tags, and examples unchanged.

3. First executable slice: Auth API EXP1 localization
   - Add a small OpenAPI deployment packaging area, proposed path: `scripts/openapi/`.
   - Add a localizer concept that consumes `specs/auth-api/openapi-spec.json` and injects a single `servers` entry for the target deployment.
   - First supported profile: `exp1` with `auth-api=https://exp1-auth-api.ezkey.org`.
   - First supported target: `cloudflare-schema` or equivalent naming that makes the artifact purpose clear.
   - Recommended generated output: `tmp/openapi/cloudflare/exp1/auth-api-openapi.json` or another non-canonical/generated location.
   - The generated file should differ from the canonical spec only by the top-level `servers` array.

4. Documentation for first slice
   - Document the canonical/deployment split in a new focused doc, for example `docs/OPENAPI_SPEC_LIFECYCLE.md`, or in a product-docs design slice if the team wants this to stay method-governed first.
   - Extend `experimental-hybrid/DEPLOYMENT_PLAYBOOK.md` with a short manual step: generate Auth API EXP1 Cloudflare spec, upload in Cloudflare, validate endpoint enumeration.
   - State clearly that manual upload is the first-slice choice and automation is a later phase.
   - Add a short Postman note: no collection update is expected for host-neutralization because Postman already uses environment variables; only contract changes require collection updates.

5. Verification for first slice
   - Run `scripts/update-specs.sh --auth-only` against a local Auth API.
   - Confirm `specs/auth-api/openapi-spec.json` has no top-level `servers` array.
   - Confirm dispatched Auth copies are also host-neutral: `ezkey-demo-device/openapi-spec.json`, `ezkey-sdk/auth-api-spec.json`, `ezkey_mobile/openapi-spec.json`, and `sites/ezkey-org/api-specs/auth-api-openapi.json`.
   - Generate the EXP1 Cloudflare Auth spec and confirm it has exactly one server URL: `https://exp1-auth-api.ezkey.org`.
   - Run a structural diff that ignores `servers` and confirms paths/components/security are unchanged between canonical and localized specs.
   - Validate downstream generation: `yarn generate:api` in `ezkey_mobile/`; Auth SDK generation/build in `ezkey-sdk/javascript/`.
   - Manually upload the generated file to Cloudflare and confirm each Auth endpoint appears once under the EXP1 host.
   - Use Cloudflare observe/log mode before enabling blocking validation for Auth API traffic.

6. Explicit later phase: global API host-neutralization
   - Extend the canonical normalization rule to Admin API and Integration API after Auth API proves the pattern.
   - Remove Admin API hardcoded `@Server` annotations from `ezkey-admin-api/src/main/java/org/ezkey/admin/config/OpenApiConfig.java`.
   - For Integration API, rely on the normalization step because the current host comes from Springdoc's generated server URL, not from source annotations.
   - Decide whether Crypto API remains outside this policy because it is test/internal only, consistent with `product-docs/global/openapi-exposure-matrix.md`.
   - Add regression checks that all production-capable canonical specs under `specs/admin-api/`, `specs/auth-api/`, and `specs/integration-api/` are host-neutral.

7. Explicit later phase: local and Docker posture
   - Review whether local developer docs need visible base URLs elsewhere once canonical specs no longer carry them.
   - Keep local runtime `/api-docs` and Swagger UI usable; the host-neutral canonical artifact does not need to remove developer convenience from a running app.
   - Verify Docker/local generation still works when `scripts/update-specs.sh` fetches from `localhost` ports.
   - If Docker-internal spec generation is ever needed, model it as another extraction profile, not as canonical host pollution.

8. Explicit later phase: Postman alignment
   - Keep Postman collections environment-driven; do not derive host policy from OpenAPI `servers`.
   - Consider adding an EXP1 Postman environment with `base_url_auth_api`, `base_url_admin_api`, and `base_url_integration_api` values for Cloudflare/Lightsail smoke tests.
   - Review collection variable naming because Auth currently uses the generic `base_url` while Admin/Integration use API-specific variables. A later cleanup may make Auth explicit as `base_url_auth_api` if it improves clarity.
   - Update Postman only when endpoint contracts, examples, status codes, or operator workflows change; host-neutralization alone should not trigger broad collection churn.

9. Explicit later phase: Cloudflare schema deployment automation
   - Add a Cloudflare API Shield/schema upload script under `scripts/cloudflare/`, separate from Pages deployment scripts.
   - Use a dedicated token variable such as `CLOUDFLARE_API_SCHEMA_TOKEN` or document a scoped token profile distinct from Pages deployment if Cloudflare permissions differ.
   - Inputs should be generated localized specs, not canonical specs.
   - The upload script should support dry-run/list/validate modes before apply.
   - Track Cloudflare schema identifiers or names per API/deployment without hardcoding them into Java code.
   - Introduce schema version/audit metadata only after manual upload proves what Cloudflare needs operationally.

10. Explicit later phase: Lightsail mini-pipeline hook
   - Add optional flags to `experimental-hybrid/scripts/full-exp-environment-upgrade.sh`, for example `--generate-cloudflare-schemas` and later `--deploy-cloudflare-schemas`.
   - Keep Cloudflare schema deployment loosely coupled: the Lightsail script may call the Cloudflare script as a hook, but the Cloudflare script must remain independently runnable.
   - In rolling deployments, recommend schema generation/upload when API contracts changed; do not force it on every image-only deployment.
   - In destructive `full` deployments, allow schema generation/upload but keep it opt-in until the Cloudflare workflow is stable.
   - Avoid making Lightsail deployment fail because Cloudflare schema upload is unavailable unless the operator explicitly requested schema deployment.

11. Final handoff action for future prompting
   - After the first Auth API slice is validated, create a short follow-up context artifact, proposed path `plans/openapi-cloudflare-next-phase-context.md` or a new `TB-*`, summarizing what changed, what was validated, and the exact next prompt context for global rollout.
   - The context must include: canonical normalization behavior, generated EXP1 artifact location, Cloudflare manual upload observations, downstream client results, Postman impact decision, and the recommended next slice.
   - This is the last to-do of the first slice so the broader work can resume cleanly without rediscovery.

**Relevant files**
- `ezkey-auth-api/src/main/java/org/ezkey/auth/config/OpenApiConfig.java` — Auth API hardcoded servers to remove in first slice.
- `ezkey-admin-api/src/main/java/org/ezkey/admin/config/OpenApiConfig.java` — same pattern, later global cleanup.
- `ezkey-integration-api/src/main/java/org/ezkey/integration/api/config/OpenApiConfig.java` — no hardcoded servers, but generated spec still has localhost.
- `scripts/update-specs.sh` — canonical fetch, normalization, and dispatch workflow.
- `specs/auth-api/openapi-spec.json` — first canonical spec to make host-neutral.
- `specs/admin-api/openapi-spec.json` and `specs/integration-api/openapi-spec.json` — later global scope.
- `postman/collections/v2.1/` — collections already use environment variables for hosts.
- `postman/environments/local.postman_environment.json` — local host values are already environment-owned.
- `experimental-hybrid/lightsail/Caddyfile` — EXP1 public API host source of truth.
- `experimental-hybrid/scripts/full-exp-environment-upgrade.sh` — future mini-pipeline coordinator hook.
- `experimental-hybrid/scripts/export-backend-images-to-lightsail.sh` — backend image/remote-up deployment path; should stay separate from Cloudflare schema upload.
- `scripts/cloudflare/deploy-admin-ui-production.sh` and `scripts/cloudflare/deploy-ezkey-org-production.sh` — existing Cloudflare script style/token precedent.
- `product-docs/global/openapi-exposure-matrix.md` — existing public/raw OpenAPI exposure posture.

**Verification**
1. Auth canonical neutrality: no top-level `servers` in `specs/auth-api/openapi-spec.json` after refresh.
2. Auth dispatch parity: all copied Auth specs match canonical host-neutral output.
3. Auth EXP1 localization: generated Cloudflare file has exactly one `servers[0].url`, `https://exp1-auth-api.ezkey.org`.
4. Structural safety: canonical and localized Auth specs differ only by `servers`.
5. Client safety: mobile Orval generation and Auth SDK generation/build complete successfully.
6. Postman safety: no collection changes unless contract shape changes; optional smoke tests continue to use environment variables.
7. EXP1 safety: Cloudflare upload shows each endpoint once under the EXP1 host.
8. Operational safety: Cloudflare validation begins in observe/log mode before blocking.
9. Next-phase readiness: follow-up context artifact exists and contains the exact next-slice prompt material.

**Decisions**
- Canonical specs should be host-neutral.
- Runtime/local hosts are not canonical contract data.
- Deployment-specific hosts belong in generated localized artifacts.
- First implementation slice remains Auth API.
- Broader Admin/Integration rollout is explicitly deferred but planned.
- Postman remains environment-driven; no broad host-related collection rewrite in the first slice.
- Cloudflare upload automation is deferred until after manual EXP1 proof.
- Lightsail may later trigger Cloudflare schema scripts, but must not become tightly coupled to them.

**Canonical materialization**
- Materialization lane: `Lane B` plan incubation, promoted into canonical product-docs artifacts.
- Status: materialized on `2026-06-02`.
- Vision note: `product-docs/global/vision/V-2026-06-02-openapi-spec-lifecycle.md`.
- Backlog idea: `product-docs/global/backlog/ideas/I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation.md`.
- First tracer bullet: `product-docs/global/backlog/ideas/TB-2026-06-02-auth-api-cloudflare-schema-first-slice.md`.
- Methodology feedback note: `product-docs/methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md`.
- Plan role after materialization: retained source and option-space record; canonical direction now lives in the linked `V-*`, `I-*`, and `TB-*` artifacts.

**Open Points For Later Phases**
1. Confirm Cloudflare API endpoints and required token scopes for schema upload/update before automating.
2. Decide whether generated Cloudflare specs should stay in `tmp/` only or become auditable release artifacts.
3. Decide whether Auth Postman variables should be renamed from generic `base_url` to `base_url_auth_api` in a dedicated cleanup.
4. Decide whether Crypto API should stay excluded from the host-neutral canonical rule as a test/internal surface.
