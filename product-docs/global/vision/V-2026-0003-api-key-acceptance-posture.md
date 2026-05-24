# V-2026-0003 — API-key acceptance posture across Admin API and Integration API

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **R1:** one Admin API property, default **`false`**, gates API-key **auth-attempt
  M2M** only (`ROLE_API_KEY` scope). Integration API = canonical M2M surface. **`true` opt-in**
  for documented minimal installs (Admin + Auth only, no Integration binary). No platform profile
  code (`V-2026-0010`). Full removal from Admin API deferred.
- **Signals:** Grilling D3 (2026-05-19) — Demo ACME + Java SDK mis-targeted Admin API; uniform
  `false` default including clean-start; RFC 9457 on reject; brief docs note (no production
  fleet). Analogue Global Admin simplified mode for minimal binary count.
- **Potential impact:** `admin-api`, `sdk-java`, `docs`, `ezkey-demo-app-acme`, functional
  tests; `I-2026-0004`.
- **Next step:** implement `I-2026-0004`; align SDK/examples and Demo ACME to Integration API
  base URL for API-key flows.

## Related artifacts

- `I-2026-0004` — Admin API: configurable acceptance of API-key authentication
- `V-2026-0010` — Per-installation profile elaboration
