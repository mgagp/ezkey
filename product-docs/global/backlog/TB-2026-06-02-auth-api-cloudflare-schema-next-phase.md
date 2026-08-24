# Next-phase context — OpenAPI Cloudflare schema after Auth API first slice

Resume notes for Admin API and Integration API host-neutral rollout. Do not start that
work until EXP1 Auth schema-validation evidence (upload + maintainer tests) is recorded.

- **Parent idea:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **First slice:** `TB-2026-06-02-auth-api-cloudflare-schema-first-slice`
- **Created at:** `2026-08-23`

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

## How to generalize (next slice, not this one)

1. Keep Admin API and Integration API annotations free of deployment hosts (Integration already
   has no `@Server`; Springdoc still emits localhost).
2. Extend `update-specs.sh` normalize to those APIs only after Auth observe evidence is in.
3. Add one packaging target per public hostname (do not put multiple hosts in one Cloudflare
   artifact).
4. Leave Bruno / leftover Postman on environment base URLs.
5. Automate Cloudflare upload only after the manual Auth path shows stable schema IDs and no
   false drops.

## Out of scope until EXP1 maintainer evidence exists

- Copying EXP1 Block to Admin API, Integration API, or a multi-user host
- Coupling schema upload to Lightsail deploy
- Treating localized specs as versioned release artifacts
