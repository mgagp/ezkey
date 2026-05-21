# Grill Me — Blitz 2026-05-08-2, D5 (SMS strategy + peripheral SPI)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D5) |
| **Vision** | `V-2026-0007` |
| **Backlog (R1 slice)** | `I-2026-0024` |
| **Cross-links** | `V-2026-0005`, `I-2026-0023`, `V-2026-0010`, `I-2026-0020`, [`operator-alignment-guide.md`](../../operator-alignment-guide.md) (deployment geometries) |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions

### R1 workflows — optional (D5-1, D5-2)

| Workflow | R1 SMS |
|----------|--------|
| Enrolment (challenge / invite material) | **Optional** — operator-triggered |
| Admin activation code | **Optional** — operator-triggered |
| Other (auth attempt, notifications, …) | **Optional** / later |

No automatic send on workflow completion in R1.

### Delivery postures (D5-3, D5-4, D5-5)

Mirror email (`blitz-2026-05-08-1-D7-email-grill-me.md`):

| Posture | When | Operator experience |
|---------|------|---------------------|
| **`integrated-delivery`** | No SMS adapter configured (or operator declines send) | On-screen material + **educational** disclaimer; manual external SMS acceptable (contextual help) |
| **`sms-assisted-delivery`** | HTTP adapter configured | **Send by SMS** action when applicable; minimized on-screen fallback remains **explicit** |

Documented via `V-2026-0010` Phase 2 elaboration — not platform profile code.

### SPI — HTTP adapter, one URL (D5-6, D5-7)

- **R1 transport:** Admin API **POSTs a delivery job** (JSON) to a configured **adapter base URL**; peripheral process (e.g. `ezkey-sms-twilio`) calls vendor API.
- **Out of scope R1:** shared DB polling, message bus, websocket, `ezkey-cli` streaming, in-process plugins.
- **One active adapter URL** in R1; Twilio repo = reference implementation; multi-vendor later.

### Roles (D5-8)

| Concern | Who |
|---------|-----|
| Adapter URL, credentials, channel posture | **Global Admin only** (TI) |
| **Send by SMS** in enrolment / activation workflows | **Global Admin or Tenant Admin** — whoever operates that workflow day-to-day |

**Nuance (operator-confirmed):** In **all-in-one** geometry, Global Admin creates integrations, enrollments, and may send SMS themselves. In **segmented** geometry, Tenant Admin does domain work; Global Admin retains config. Same send affordance for both roles where RBAC already allows the underlying workflow. See [`operator-alignment-guide.md`](../../operator-alignment-guide.md) § Deployment operator geometries.

### SMS body R1 (D5-9)

- **Challenge codes only** — no links, no QR in SMS for R1.
- Short templates, e.g. “Here is your code to complete your enrollment.” / “Here is your code to complete your authentication.” (exact copy at implementation).
- “Link or reference” deferred; R1 = numeric challenge delivery only.

### Failure and principle (D5-10, D5-11)

- Adapter failure: clear error; natural fallback to on-screen / copy.
- **Principle:** Ezkey **supports** SMS via peripheral adapter when configured, **remains fully operational** without SMS or after failure.

## Links

- [`../blitz-archive/blitz-2026-05-08-2.md`](../blitz-archive/blitz-2026-05-08-2.md)
- [`../ideas/I-2026-0024-sms-channel-r1-http-adapter-spi.md`](../ideas/I-2026-0024-sms-channel-r1-http-adapter-spi.md)
- [`../../vision/product-orientation-notes.md`](../../vision/product-orientation-notes.md) (`V-2026-0007`)
- [`blitz-2026-05-08-1-D7-email-grill-me.md`](blitz-2026-05-08-1-D7-email-grill-me.md)
