# Backlog Idea — `I-2026-0010` Phone-to-phone enrollment transfer ceremony

## Metadata

- **ID:** `I-2026-0010`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P1-operability`, `P3-distribution`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `admin-ui`

## Intent

Self-service **new phone** flow: transfer **all enrollments** (full bundle) from old device via Auth API ceremony — **QR-primary** pairing + **6-digit challenge**, dedicated audit events, old device wiped after confirm. Operator observability only R1.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D3-phone-transfer-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D3-phone-transfer-grill-me.md).

- **QR:** predominant role; payload design in follow-up design pack (not optional aid).
- **Auth API** sole ceremony interlocutor.
- **No** partial enrollment selection R1.
- Capability divergence (`V-2026-0001`): block with message, no silent downgrade.

## Scope

- **In scope:** mobile UX both phones; Auth API ceremony + audit; QR schema design session; state diagram.
- **Out of scope:** cross-installation migration; operator-assisted migration; partial bundle.

## Promotion notes

**Design pack next:** QR payload + ceremony state machine + audit event catalog. Validate enrollment re-bind crypto before TB.

## Links

- Grill: `../grill-sessions/blitz-2026-05-08-2-D3-phone-transfer-grill-me.md`
- `V-2026-0001`, `I-2026-0001`
