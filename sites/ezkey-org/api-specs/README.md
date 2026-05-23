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
