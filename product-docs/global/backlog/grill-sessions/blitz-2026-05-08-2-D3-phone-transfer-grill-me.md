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
| D3-3 | **QR predominant** — not optional garnish. Exact payload **TBD** in design pack (see below). **Auth API only** interlocutor; **dedicated audit** events for transfer ceremony |
| D3-4 | Ceremony endpoints on **Auth API**; Admin API audit read for operators |
| D3-5 | **Full bundle** — all enrollments on device; **no** partial selection R1 |
| D3-6 | Old phone: **deactivate + wipe** after explicit final confirm |
| D3-7 | Expiring ceremony; no orphan partial state |
| D3-8 | Capability mismatch on new phone → **block** enrollment with clear message |
| D3-9 | Operator: **observability** only R1 |
| D3-10 | R1 grill + vision; crypto binding validation before implementation |
| D3-11 | Self-hosted user autonomy without helpdesk |

### D3-3 — QR design follow-up (open, not grilled to payload detail)

Operator intent: **reinvent QR for this ceremony** — maximize security + pragmatism for **voluntary** phone-to-phone transfer (contrast enrollment invite QR).

**Design pack must explore:**

| Candidate role | Rationale |
|----------------|-----------|
| Bind **new device** identity to ceremony session | Reduces manual entry; scanner proves physical proximity |
| Carry **short-lived ceremony id** (+ signature/nonce from Auth API) | Server correlates old-phone initiation ↔ new-phone scan |
| Avoid secrets in QR | QR may be visible to cameras; treat as **handle**, not key material |
| Pair with **6-digit challenge** | QR starts/joins session; challenge proves same human at both devices |

**Explicit non-decision:** exact encoding (Ezkey QR URI schema, fields, TTL) — **next design session** before `TB-*`.

## Links

- [`../ideas/I-2026-0010-phone-to-phone-enrollment-transfer.md`](../ideas/I-2026-0010-phone-to-phone-enrollment-transfer.md)
