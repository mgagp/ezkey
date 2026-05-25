# Grill Me — Blitz 2026-05-08-2, D3 (phone-to-phone enrollment transfer)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D3) |
| **Backlog** | `I-2026-0010` |
| **Cross-links** | `V-2026-0001`, `I-2026-0001`, `V-2026-0008` |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions (operator-confirmed)

| ID | Decision |
|----|----------|
| D3-1 | Initiate from **old phone**; new phone proves via challenge |
| D3-2 | **6-digit** challenge; mismatch aborts |
| D3-3 | **QR predominant** — not optional garnish. Payload locked in [`phone-transfer-ceremony-design-pack.md`](../../phone-transfer-ceremony-design-pack.md) (`kind: ezkey-device-transfer`). **Auth API only** interlocutor; **dedicated audit** events for transfer ceremony |
| D3-4 | Ceremony endpoints on **Auth API**; Admin API audit read for operators |
| D3-5 | **Full bundle** — all enrollments on device; **no** partial selection R1 |
| D3-6 | Old phone: **deactivate + wipe** after explicit final confirm |
| D3-7 | Expiring ceremony; no orphan partial state |
| D3-8 | Capability mismatch on new phone → **block** enrollment with clear message |
| D3-9 | Operator: **observability** only R1 |
| D3-10 | R1 grill + vision; crypto binding validation before implementation |
| D3-11 | Self-hosted user autonomy without helpdesk |

### D3-3 — QR design (closed 2026-05-24)

Operator intent: **reinvent QR for this ceremony** — maximize security + pragmatism for **voluntary** phone-to-phone transfer (contrast enrollment invite QR).

**Resolved in design pack** [`phone-transfer-ceremony-design-pack.md`](../../phone-transfer-ceremony-design-pack.md):

| Decision | Resolution |
|----------|------------|
| Payload role | Short-lived `ceremonyId` + single-use `joinToken` + optional `authUrl` |
| Secret material | **None** in QR — handle only |
| Human pairing | 6-digit challenge on old phone, entered on new phone |
| Parser isolation | `kind: ezkey-device-transfer` — never mixed with enrollment invite QR |
| Encoding | JSON primary; manual `XXXX-XXXX` ceremony code fallback |

**TB gate:** crypto re-bind spike (`D3-10`) remains before implementation.

## Links

- [`../ideas/I-2026-0010-phone-to-phone-enrollment-transfer.md`](../ideas/I-2026-0010-phone-to-phone-enrollment-transfer.md)
- [`../../phone-transfer-ceremony-design-pack.md`](../../phone-transfer-ceremony-design-pack.md)
