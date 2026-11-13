# Auth API deployment-localized OpenAPI artifacts

Generated packaging output for ingress validators. **Not** the canonical Auth API contract.

- Canonical spec: [`../openapi-spec.json`](../openapi-spec.json) (host-neutral; no top-level `servers`)
- Community / Cloudflare: run `./scripts/package-auth-api-cloudflare-schema.sh` from the repo root
  (OAS 3.0 downlevel for Cloudflare; do not upload the canonical 3.1 spec)
  → `community-cloudflare-openapi.json` with `https://auth-api.ezkey.online`
- Upload: `./scripts/cloudflare/upload-auth-api-schema.sh --upload` (alias:
  `upload-auth-api-schema-community.sh`)
- Operator runbook: [`docs/cloudflare/auth-api-schema-validation.md`](../../../docs/cloudflare/auth-api-schema-validation.md)

Do not hand-edit generated JSON in this folder. Regenerate before each Cloudflare upload.
