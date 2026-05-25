# Backlog Idea — `I-2026-0024` SMS channel R1: HTTP adapter SPI + optional operator send

## Metadata

- **ID:** `I-2026-0024`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-19`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P1-operability`
- **Component tags:** `admin-api`, `admin-ui`, `infra`, `docs`, peripheral (`ezkey-sms-twilio`)

## Intent

Implement the **R1 SMS channel slice** from `V-2026-0007`: optional **operator-triggered** send for enrolment and admin-activation workflows via a **peripheral HTTP adapter** (reference: `ezkey-sms-twilio`); Global Admin configures adapter URL; Global Admin **or** Tenant Admin may send when operating the workflow. Ezkey stays operational without SMS. Challenge **codes only** in SMS body (no links/QR R1).

## Problem and value

- **Problem:** SMS requires external vendors; must not pollute core like email (Java Mail). Need minimal SPI and same operational posture as email (integrated vs assisted delivery).
- **Expected value:** Optional separated channel for PME; core stays lean; Twilio (or similar) isolated in peripheral repo.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D5-sms-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D5-sms-grill-me.md).

## Scope

- **In scope (R1):**
  - SPI contract: Admin API → HTTP POST delivery job → adapter → vendor.
  - Global Admin: adapter URL + credential posture in config.
  - **Send by SMS** on enrolment + admin activation (operator action); both Global Admin and Tenant Admin where workflow RBAC allows.
  - Body: short text + **challenge code** only.
  - Failure: error + on-screen fallback; disclaimers (`integrated-delivery` / `sms-assisted-delivery`).
  - Reference peripheral repo `ezkey-sms-twilio` (or shell per `I-2026-0020`).
- **Out of scope (R1):**
  - SMS in core platform code.
  - Auto-send, links in SMS, QR in SMS, multi-adapter routing.
  - Message bus, websocket, DB polling, CLI streaming.
  - Auth-attempt SMS workflow (template pattern documented for later).

## Promotion notes

Move to `ready` when SPI contract sketch + adapter OpenAPI/minimal doc exists. Depends on `CONFIGURATION.md` entries for adapter URL.

## Links

- Vision: `V-2026-0007`
- Parallel: `V-2026-0005`, `I-2026-0023`
- Grill: `../grill-sessions/blitz-2026-05-08-2-D5-sms-grill-me.md`
- Geometries: [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
- Ecosystem: `I-2026-0020`
