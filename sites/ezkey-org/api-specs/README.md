# Ezkey.org API spec assets

This directory holds the static OpenAPI JSON assets published with `sites/ezkey-org/`
(Cloudflare Pages deploys this folder as-is).

## Purpose

These files are the portal-facing copies consumed by:

- `api-docs.html`
- `admin-api-reference.html`
- `auth-api-reference.html`
- `integration-api-reference.html`

## Source of truth

Canonical specs live under repository-level `specs/`:

- `specs/admin-api/openapi-spec.json`
- `specs/auth-api/openapi-spec.json`
- `specs/integration-api/openapi-spec.json`

The `*-openapi.json` files here are **gitignored**. Copy them at update or deploy time
(no git symlinks — [#833](https://github.com/mgagp/ezkey/pull/833)):

```bash
./scripts/update-specs.sh --all          # refreshes specs/ then syncs this folder
./scripts/sync-ezkey-org-api-specs.sh    # copy only (also run by deploy-ezkey-org-*.sh)
```

Do not hand-edit the JSON assets in this directory.
