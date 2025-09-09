# Auth API Specifications

This directory contains the centralized OpenAPI specification for the Ezkey Auth API.

## Files

- `openapi-spec.json` - Current Auth API specification
- `openapi-spec.json.backup` - Automatic backup of previous version

## Usage

This specification is used by:
- `ezkey-demo-device` - Demo device application
- `ezkey-sdk` - SDK generation for all languages

## Update Process

The specification is automatically updated from the running Auth API (port 8080) using the centralized update script:

```bash
./scripts/update-specs.sh
```

## Manual Update

To manually update from a running API:

```bash
curl http://localhost:8080/api-docs -o openapi-spec.json
```

## Validation

The specification is automatically validated during the update process to ensure it's valid JSON and contains the expected OpenAPI structure.
