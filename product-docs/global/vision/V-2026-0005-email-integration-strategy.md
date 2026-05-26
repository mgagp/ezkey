# V-2026-0005 — Email integration strategy and deployment-profile cohabitation

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** Introduce **optional**, **operator-triggered** email (Java Mail + SMTP config) for
  enrolment and admin-activation workflows while keeping Ezkey **fully operational** without SMTP
  or after send failure. R1 normalizes operational choice — it does not mandate email. Two
  **documentation postures** (per `V-2026-0010`): **integrated delivery** (on-screen + manual
  external channels OK; educational disclaimer) and **SMTP-assisted delivery** (email-first UI
  action; minimized on-screen QR fallback). Unlike SMS (`V-2026-0007`), email stays in-core as
  configuration only.
- **Signals:** Grilling D7 (2026-05-19) — disclaimers become product positioning, not "coming
  soon" warnings; PME may drag-drop QR or paste codes outside Ezkey; automation deferred. Global
  Admin configures SMTP (TI posture — `operator-alignment-guide.md`).
- **Potential impact:** `admin-api`, `admin-ui`, `infra` (SMTP), `docs`, contextual help,
  `I-2026-0023` (R1 slice).
- **Next step:** design pack + `I-2026-0023`; update `CONFIGURATION.md` for mail properties
  when implementing.
- **Captured by:** Marc

## Related artifacts

- `I-2026-0023` — Email channel R1: optional operator-triggered delivery
- `V-2026-0007` — SMS integration strategy (contrast: out-of-core adapter model)
- `V-2026-0010` — Per-installation profile elaboration
