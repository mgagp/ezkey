# V-2026-0014 — API documentation exposure and portal posture

- **Date:** `2026-05-21`
- **Status:** `promoted`
- **Intent:** Treat per-service Springdoc Swagger UI and raw OpenAPI endpoints as operator and
  developer tooling, not as the default public surface for production-capable Ezkey deployments.
  Public-facing API documentation should be curated through an open-source portal posture aligned
  with `ezkey.org`, while the exposure of raw `/api-docs` and embedded Swagger UI is segmented
  explicitly by deployment posture.
- **Signals:** Current runtime exposure is inconsistent with the likely production posture:
  public-evaluator `exp1` currently exposes Swagger UI and `/api-docs` on the
  production-capable APIs, while the repository already contains partial hardening precedent in
  native profiles. Ezkey also has a natural documentation-hosting anchor in the static Cloudflare
  Pages site under `sites/ezkey-org/`, plus a product-specific need to segment `Admin API`,
  `Auth API`, optional `Integration API`, and test-only `Crypto API` differently.
- **Potential impact:** `docs`, `sites/ezkey-org`, `admin-api`, `auth-api`, `integration-api`,
  `crypto-api`, `infra`, Cloudflare deployment guidance, and operator-facing documentation
  policy.
- **Next step:** promoted into `I-2026-0026`, then materially realized through `TB-2026-0003`.
  Retain this note as the directional record behind the public API portal and raw-doc exposure
  posture.
- **Captured by:** Marc

## Related artifacts

- `I-2026-0026` — OpenAPI exposure and API portal posture (promoted; `done`)
- `TB-2026-0003` — OpenAPI public portal first cut
