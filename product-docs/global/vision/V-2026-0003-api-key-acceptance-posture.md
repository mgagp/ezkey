# V-2026-0003 — API-key acceptance posture across Admin API and Integration API

- **Date:** `2026-05-08`
- **Status:** `promoted`
- **Intent:** **R1 (shipped):** one Admin API property, default **`false`**, gated API-key
  auth-attempt M2M on Admin API. Integration API = canonical M2M surface. **R2 (2026-08-29):**
  remove the hatch entirely — no opt-in, no Admin API-key authentication. EXP1 ACME must
  target Integration API on the next rolling upgrade.
- **Signals:** Grilling D3 (2026-05-19) — Demo ACME + Java SDK mis-targeted Admin API; uniform
  `false` default including clean-start; RFC 9457 on reject; brief docs note (no production
  fleet). R2 review (2026-08-29): leftover toggle + EXP1 ACME still on `admin-api:9080`.
- **Potential impact:** `admin-api`, `sdk-java`, `docs`, `ezkey-demo-app-acme`, EXP1 Compose,
  functional tests; `I-2026-0004`, `I-2026-08-29`.
- **Next step:** implement `TB-2026-08-29-admin-api-remove-m2m-hatch`.
- **Captured by:** Marc

## Related artifacts

- `I-2026-0004` — Admin API: configurable acceptance of API-key authentication (R1)
- `I-2026-08-29-admin-api-remove-m2m-hatch` / `TB-2026-08-29-admin-api-remove-m2m-hatch` (R2)
- `V-2026-0010` — Per-installation profile elaboration
