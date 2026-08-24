# Integration API deployment-localized OpenAPI artifacts

Generated packaging output for ingress validators. **Not** the canonical Integration API contract.

- Canonical spec: [`../openapi-spec.json`](../openapi-spec.json) (host-neutral; no top-level `servers`)
- EXP1 / Cloudflare: run `./scripts/package-integration-api-cloudflare-schema.sh` from the repo root
  (OAS 3.0 downlevel for Cloudflare; do not upload the canonical 3.1 spec)
- Operator upload: [`docs/cloudflare/integration-api-schema-validation.md`](../../../docs/cloudflare/integration-api-schema-validation.md)

Do not hand-edit generated JSON in this folder. Regenerate before each Cloudflare upload.
