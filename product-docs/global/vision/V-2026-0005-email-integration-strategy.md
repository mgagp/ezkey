# V-2026-0005 — Email integration strategy and deployment-profile cohabitation

- **Date:** `2026-05-08`
- **Superseded in part by:** `V-2026-11-04-connector-spi` (2026-11-04, #1206): email goes through
  a connector, not Java Mail in the core, and carries the QR while the challenge travels
  separately. Optional, operator-triggered sending and the postures below stand; read
  `smtp-assisted-delivery` as `email-assisted-delivery`. Admin activation codes are out of scope:
  they equal both factors and never go through a connector (ADR-0017, 2026-11-05).
- **Status:** `under-review`
- **Intent:** Introduce **optional**, **operator-triggered** email (through a connector) for
  enrolment workflows while keeping Ezkey **fully operational** without SMTP
  or after send failure. R1 normalizes operational choice — it does not mandate email. Two
  **documentation postures** (per `V-2026-0010`): **integrated delivery** (on-screen + manual
  external channels OK; educational disclaimer) and **SMTP-assisted delivery** (email-first UI
  action; minimized on-screen QR fallback). Like SMS (`V-2026-0007`), email stays out of the core.
- **Signals:** Grilling D7 (2026-05-19) — disclaimers become product positioning, not "coming
  soon" warnings; PME may drag-drop QR or paste codes outside Ezkey; automation deferred. Global
  Admin configures SMTP (TI posture — `operator-alignment-guide.md`). **2026-07-25:** integrated
  delivery posture executed in Admin UI via
  `TB-2026-07-25-integrated-delivery-posture`; SMTP-assisted half remains on `I-2026-0023`.
- **Potential impact:** `admin-api`, `admin-ui`, `infra` (SMTP), `docs`, contextual help,
  `I-2026-0023` (R1 slice), `TB-2026-07-25-integrated-delivery-posture` (posture-only).
- **Next step:** implement the mail half of `I-2026-0023` through `V-2026-11-04-connector-spi`.
- **Captured by:** Marc

## Related artifacts

- `I-2026-0023` — Email channel R1: optional operator-triggered delivery
- `TB-2026-07-25-integrated-delivery-posture` — Integrated delivery Admin UI posture
- `V-2026-0007` — SMS integration strategy (same out-of-core model)
- `V-2026-11-04-connector-spi` — Connector SPI for external services
- `V-2026-0010` — Per-installation profile elaboration
