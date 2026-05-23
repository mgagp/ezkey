# Integration API Specifications

This directory contains the centralized OpenAPI specification for the Ezkey Integration API.

## Files

- `openapi-spec.json` - Current Integration API specification
- `openapi-spec.json.backup` - Automatic backup of previous version

## Usage

This specification is used by:
- the public API portal and related documentation surfaces
- future SDK or integration-facing tooling that needs the machine-to-machine contract

## Update Process

The specification is automatically updated from the running Integration API (port 7080) using the centralized update script:

```bash
./scripts/update-specs.sh --integration-only
```

## Manual Update

To manually update from a running API:

```bash
curl http://localhost:7080/api-docs -o openapi-spec.json
```

## Validation

The specification is automatically validated during the update process to ensure it's valid JSON and contains the expected OpenAPI structure.
