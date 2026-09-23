# Ezkey.org API spec assets

This directory contains the static OpenAPI JSON assets published with `sites/ezkey-org/`.

## Purpose

These files are the portal-facing copies consumed by:

- `api-docs.html`
- `admin-api-reference.html`
- `auth-api-reference.html`
- `integration-api-reference.html`

## Source of truth

The canonical generated specs remain under the repository-level `specs/` directory:

- `specs/admin-api/openapi-spec.json`
- `specs/auth-api/openapi-spec.json`
- `specs/integration-api/openapi-spec.json`

Use the centralized update workflow to refresh them and publish portal copies in one step:

```bash
./scripts/update-specs.sh --all
```

Do not hand-edit the JSON assets in this directory.

## Last live refresh

- **Date:** 2026-09-23
- **Stack:** clean-start Docker (Admin `:9080`, Auth `:8080`, Integration `:7080` `/api-docs`)
- **Command:** `./scripts/update-specs.sh` (jq validate/format; auth + integration `servers` stripped)
- **Result:** regenerated JSON byte-identical to committed `specs/{admin,auth,integration}-api/openapi-spec.json` (no contract drift)
