# Next-phase context — OpenAPI Cloudflare schema after Auth API first slice

Resume notes after the Auth and Integration the community host slices (`done`, canon synced 2026-08-25).
Admin Cloudflare upload stays deferred — that is the remaining executable work on
`I-2026-06-02`.

- **Parent idea:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **First slice:** `TB-2026-06-02-auth-api-cloudflare-schema-first-slice` (`done`, PR `#478`)
- **Integration slice:** `TB-2026-08-23-integration-api-cloudflare-schema` (`done`, PR `#479`)
- **Created at:** `2026-08-23`
- **Updated at:** `2026-11-15`

## What the Auth slice proved

- Canonical Auth specs can drop top-level `servers` after fetch (`scripts/update-specs.sh --auth-only`).
- Springdoc still emits a request-host server even after `@Server` annotations are removed; the
  scripted `del(.servers)` step is the real host-neutral gate.
- Deployment hosts belong in a separately runnable packaging step
  (`scripts/package-auth-api-cloudflare-schema.sh`), not in Java annotations.
- Mobile Orval and the JavaScript Auth SDK generate successfully without canonical `servers`.
  The JS SDK wrapper already injects `config.authApiUrl`. Do not restore localhost / community hosts
  in annotations if a later generator defaults to `http://localhost`.
- Git Bash/MSYS rewrites `jq --arg` values that look like `/api/...` paths. Prefer listing
  `.paths | keys[]` and comparing in Bash.
- Cloudflare Schema Validation is **OAS 3.0 only**. Canonical Springdoc output is 3.1
  (`type: ["string","null"]`). The packaging step must downlevel to `type` + `nullable: true`
  and set `openapi: 3.0.3`. Uploading the canonical 3.1 file fails with error 50010.

## community host mitigation (2026-08-23)

The operator uploaded the localized schema and set **Block** on `auth-api.ezkey.online`.
That is a community host maintainer-only exception (no external evaluators; the operator wants
fail-closed feedback). Escape hatch: **None** if a self-test locks out enroll or MFA.

Product default for any later host that might see third-party traffic remains **None** until
a dedicated observe period says otherwise. Do not treat community host Block as the Admin/Integration
rollout default. Superseded 2026-11-15: see the next section. Zone `ezkey.online` is **Block**.

## community zone mitigation (2026-11-15)

Supersedes the product-default note in the 2026-08-23 section above. After that observe period,
the operator set schema-validation mitigation to **Block** on zone `ezkey.online`. That is the
current state and the recommended default: best security posture, validated by a first run.
Post-upgrade smoke on `c6d89274` passed under **Block** (Auth bind with and without
`enrollmentId`, Admin UI). The same zone decision covers Auth and Integration.

Rollback stays temporary **None** (log) if legitimate clients get an unexplained Cloudflare 403.
Those requests never reach origin — check Cloudflare security events — then fix and re-upload
the schema and return to **Block**. Re-upload the packaged schema whenever the API contract
changes. Upload scripts still do not change mitigation.

Runbooks: [`docs/cloudflare/auth-api-schema-validation.md`](../../../docs/cloudflare/auth-api-schema-validation.md),
[`docs/cloudflare/integration-api-schema-validation.md`](../../../docs/cloudflare/integration-api-schema-validation.md).

Still useful to record from maintainer tests: schema-validation events on
`POST /api/v1/enrollments/instance-info` and the MFA write path.

Runbook: [`docs/cloudflare/auth-api-schema-validation.md`](../../../docs/cloudflare/auth-api-schema-validation.md).

## How to generalize

Integration the community host copied the Auth recipe and is uploaded. Mitigation on
`integration-api.ezkey.online` is **Block**. As of 2026-11-15 that is the zone default on
`ezkey.online` (see above), not a maintainer-only exception. Escape hatch remains temporary
**None** (log), then return to **Block**.

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

- Copying community Auth Block to Admin by default (Integration is on **Block** as of 2026-11-15)
- Admin API Cloudflare upload
- Coupling schema upload to Lightsail deploy
- Treating localized specs as versioned release artifacts

Integration runbook:
[`docs/cloudflare/integration-api-schema-validation.md`](../../../docs/cloudflare/integration-api-schema-validation.md).
