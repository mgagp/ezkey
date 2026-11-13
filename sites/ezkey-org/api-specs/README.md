# Ezkey.org API spec assets

This directory holds the static OpenAPI JSON assets published with `sites/ezkey-org/`
(Cloudflare Pages deploys this folder as-is).

## Purpose

These files are the portal-facing copies consumed by:

- `api-docs.html`
- `admin-api-reference.html`
- `auth-api-reference.html`
- `integration-api-reference.html`

(and their French mirrors under `fr/`).

## Source of truth

Canonical specs live under repository-level `specs/`:

- `specs/admin-api/openapi-spec.json`
- `specs/auth-api/openapi-spec.json`
- `specs/integration-api/openapi-spec.json`

The `*-openapi.json` files here are **committed physical copies** (no git symlinks —
[#833](https://github.com/mgagp/ezkey/pull/833)). Refresh them whenever `specs/` changes:

```bash
./scripts/sync-ezkey-org-api-specs.sh    # copy only (also run by deploy-ezkey-org-*.sh)
./scripts/update-specs.sh --all          # refreshes specs/ then syncs this folder
./scripts/check-ezkey-org-api-specs.sh   # fail if portal copies drift from specs/
```

Do not hand-edit the JSON assets in this directory.

## Publish

After merge, Edgar deploys from Rootbeer with a local `.env` (not from cloud agents):

1. `./scripts/cloudflare/deploy-ezkey-org-preview.sh`
2. Visual check of the three ReDoc reference pages
3. `./scripts/cloudflare/deploy-ezkey-org-production.sh`
