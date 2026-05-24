# V-2026-0007 — SMS integration strategy and SPI protocol for peripheral integrations

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Optional**, **operator-triggered** SMS for enrolment and admin-activation via
  **peripheral HTTP adapter** (e.g. `ezkey-sms-twilio`) — not in-core (contrast `V-2026-0005`
  email). R1 SPI: Admin API POSTs delivery job to one configured adapter URL. Postures
  **`integrated-delivery`** / **`sms-assisted-delivery`** (mirror email). Body R1: **challenge
  codes only** (short templates). Global Admin configures adapter; **Global Admin or Tenant
  Admin** sends per workflow RBAC and deployment geometry (`operator-alignment-guide.md`).
- **Signals:** Grilling D5 Blitz 2 (2026-05-19). No auto-send, no bus/websocket/CLI streaming
  R1. Fully operational without SMS.
- **Potential impact:** `admin-api` (outbound job + config), `admin-ui`, peripheral repos
  (`I-2026-0020`, `I-2026-0024`), `docs`, `V-2026-0010` elaboration.
- **Next step:** SPI contract sketch + `I-2026-0024`; reference adapter repo.

## Related artifacts

- `I-2026-0024` — SMS channel R1: HTTP adapter SPI + optional operator send
- `I-2026-0020` — Integration ecosystem shell catalog and publish workflow
- `V-2026-0005` — Email integration strategy (contrast: in-core model)
- `V-2026-0010` — Per-installation profile elaboration
