# V-2026-0003 — API-key acceptance posture across Admin API and Integration API

- **Date:** `2026-05-08`
- **Status:** `promoted`
- **Intent:** **R1 (shipped):** one Admin API property, default **`false`**, gated API-key
  auth-attempt M2M on Admin API. Integration API = canonical M2M surface. **R2 (2026-08-29):**
  remove the hatch entirely — no opt-in, no Admin API-key authentication. community ACME must
  target Integration API on the next rolling upgrade.
- **Signals:** Grilling D3 (2026-05-19) — Demo ACME + Java SDK mis-targeted Admin API; uniform
  `false` default including clean-start; RFC 9457 on reject; brief docs note (no production
  fleet). R2 review (2026-08-29): leftover toggle + community ACME still on `admin-api:9080`.
- **Potential impact:** `admin-api`, `sdk-java`, `docs`, `ezkey-demo-app-acme`, community Compose,
  functional tests; `I-2026-0004`, `I-2026-08-29`.
- **Next step:** implement `TB-2026-08-29-admin-api-remove-m2m-hatch`.
- **Captured by:** Marc
- **Decision (2026-11-09, #992):** pending-attempt policy is per integration, default **REPLACE**
  (expire the live attempt and create the new one) so existing Integration API callers keep that
  supersession; **REFUSE** is an explicit integration choice. Creation cooldown is a global
  platform control, not a second rate limiter. It counts device-signed outcomes only (`REJECTED`
  and `INVALID`). Reason: counting `EXPIRED` would be an unauthenticated denial of service, for
  example anyone running `ssh user@host` with PAM (#1311).
- **Decision (2026-11-09, #1311):** Admin UI names the setting "When a request is already waiting"
  (FR: "Quand une demande attend déjà"). Help recommends Refuse for interactive integrations where
  a person is waiting to sign in, and says that saving does not change a request already waiting.
  Reason: the earlier "repeatedly or automatically" sentence pointed operators at the wrong
  integrations. The system integration (administrator sign-in) stays **REPLACE**; changing it
  returns the existing `system-integration-lifecycle` refusal. Cooldown on every integration,
  including this one, counts only `REJECTED` and `INVALID`. Superseded or cancelled attempts do
  not count and do not raise the enrollment alert, whatever status they carry (#1311).

## Related artifacts

- `I-2026-0004` — Admin API: configurable acceptance of API-key authentication (R1)
- `I-2026-08-29-admin-api-remove-m2m-hatch` / `TB-2026-08-29-admin-api-remove-m2m-hatch` (R2)
- `V-2026-0010` — Per-installation profile elaboration
