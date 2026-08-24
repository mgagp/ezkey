# Next-phase context — OpenAPI Cloudflare schema after Auth API first slice

Resume notes after the Auth first slice. Integration EXP1 is the next executable slice
(`TB-2026-08-23-integration-api-cloudflare-schema`). Admin Cloudflare upload stays deferred.

- **Parent idea:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **First slice:** `TB-2026-06-02-auth-api-cloudflare-schema-first-slice`
- **Integration slice:** `TB-2026-08-23-integration-api-cloudflare-schema`
- **Created at:** `2026-08-23`
- **Updated at:** `2026-08-23`

## What the Auth slice proved

- Canonical Auth specs can drop top-level `servers` after fetch (`scripts/update-specs.sh --auth-only`).
- Springdoc still emits a request-host server even after `@Server` annotations are removed; the
  scripted `del(.servers)` step is the real host-neutral gate.
- Deployment hosts belong in a separately runnable packaging step
  (`scripts/package-auth-api-cloudflare-schema.sh`), not in Java annotations.
- Mobile Orval and the JavaScript Auth SDK generate successfully without canonical `servers`.
  The JS SDK wrapper already injects `config.authApiUrl`. Do not restore localhost / EXP1 hosts
  in annotations if a later generator defaults to `http://localhost`.
- Git Bash/MSYS rewrites `jq --arg` values that look like `/api/...` paths. Prefer listing
  `.paths | keys[]` and comparing in Bash.
- Cloudflare Schema Validation is **OAS 3.0 only**. Canonical Springdoc output is 3.1
  (`type: ["string","null"]`). The packaging step must downlevel to `type` + `nullable: true`
  and set `openapi: 3.0.3`. Uploading the canonical 3.1 file fails with error 50010.

## EXP1 mitigation (2026-08-23)

The operator uploaded the localized schema and set **Block** on `exp1-auth-api.ezkey.org`.
That is an EXP1 maintainer-only exception (no external evaluators; the operator wants
fail-closed feedback). Escape hatch: **None** if a self-test locks out enroll or MFA.

Product default for any later host that might see third-party traffic remains **None** until
a dedicated observe period says otherwise. Do not treat EXP1 Block as the Admin/Integration
rollout default.

Still useful to record from maintainer tests: schema-validation events on
`POST /api/v1/enrollments/instance-info` and the MFA write path.

Runbook: [`docs/cloudflare/auth-api-schema-validation.md`](../../../docs/cloudflare/auth-api-schema-validation.md).

## How to generalize

Integration EXP1 copied the Auth recipe and is uploaded. Mitigation on
`exp1-integration-api.ezkey.org` is **Block** (2026-08-23 maintainer-only exception, same
reason as Auth). Escape hatch remains None. Do not treat that as the Admin or later-host
default.

Admin remains the later analysis, not this Integration slice:

1. Keep Admin API annotations free of deployment hosts (optional cheap `del(.servers)` only if
   it stays a script change with no Admin Cloudflare upload).
2. One packaging target per public hostname (do not put multiple hosts in one Cloudflare
   artifact).
3. Leave Bruno / leftover Postman on environment base URLs.
4. Do not couple schema upload to Lightsail deploy.
5. Admin upload waits on plan size, Cloudflare body-inspection limit, and Admin contract
   volatility.

## Out of scope for the Integration slice

- Copying EXP1 Auth Block to Integration or Admin by default
- Admin API Cloudflare upload
- Coupling schema upload to Lightsail deploy
- Treating localized specs as versioned release artifacts

Integration runbook:
[`docs/cloudflare/integration-api-schema-validation.md`](../../../docs/cloudflare/integration-api-schema-validation.md).
