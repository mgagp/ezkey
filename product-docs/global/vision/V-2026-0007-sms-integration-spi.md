# V-2026-0007 — SMS integration strategy and SPI protocol for peripheral integrations

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Optional**, **operator-triggered** SMS for enrolment via
  **peripheral HTTP adapter** (e.g. `ezkey-sms-twilio`) — not in-core, same model as email
  (`V-2026-11-04-connector-spi`). Connector code incubates in the monorepo; a separate repository is a
  later question. R1 SPI: Admin API POSTs delivery job to one configured adapter URL. Postures
  **`integrated-delivery`** / **`sms-assisted-delivery`** (mirror email). Body R1: **challenge
  codes only** (short templates). Global Admin configures adapter; **Global Admin or Tenant
  Admin** sends per workflow RBAC and deployment geometry (`operator-alignment-guide.md`).
- **Signals:** Grilling D5 Blitz 2 (2026-05-19). No auto-send, no bus/websocket/CLI streaming
  R1. Fully operational without SMS.
- **Potential impact:** `admin-api` (outbound job + config), `admin-ui`, peripheral repos
  (`I-2026-0020`, `I-2026-0024`), `docs`, `V-2026-0010` elaboration.
- **Next step:** none. Parked 2026-11-25 with a detailed design and reopen signals:
  [`TB-2026-11-24-sms-challenge-connector`](../backlog/TB-2026-11-24-sms-challenge-connector.md). The single adapter URL
  (D5-7) is outdated: it would become one connector per channel.
- **Captured by:** Marc

## Related artifacts

- `I-2026-0024` — SMS channel R1: HTTP adapter SPI + optional operator send
- `I-2026-0020` — Integration ecosystem shell catalog and publish workflow
- `V-2026-0005` — Email integration strategy (same model since 2026-11-04)
- `V-2026-11-04-connector-spi` — Connector SPI: generalizes this adapter model
- `V-2026-0010` — Per-installation profile elaboration
