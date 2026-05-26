# Backlog Idea — `I-2026-0010` Phone-to-phone enrollment transfer ceremony

## Metadata

- **ID:** `I-2026-0010`
- **Status:** `ready`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P1-operability`, `P3-distribution`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `admin-ui`
- **Captured by:** Marc

## Intent

Self-service **new phone** flow: transfer **all enrollments** (full bundle) from old device via Auth API ceremony — **QR-primary** pairing + **6-digit challenge**, dedicated audit events, old device wiped after confirm. Operator observability only R1.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D3-phone-transfer-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D3-phone-transfer-grill-me.md).

- **QR:** predominant role; payload locked in design pack (distinct `kind`, ceremony handle only).
- **Auth API** sole ceremony interlocutor.
- **No** partial enrollment selection R1.
- Capability divergence (`V-2026-0001`): block with message, no silent downgrade.

## Scope

- **In scope:** mobile UX both phones; Auth API ceremony + audit; QR schema; state machine; audit catalog.
- **Out of scope:** cross-installation migration; operator-assisted migration; partial bundle.

## Design pack

Canonical artifact: [`../../phone-transfer-ceremony-design-pack.md`](../../phone-transfer-ceremony-design-pack.md) (2026-05-24).

Covers: transfer QR JSON schema (`kind: ezkey-device-transfer`), ceremony state machine, Auth API endpoint sketch, proposed audit events, error model, TB promotion checklist.

## Promotion notes

**Ready for TB** after crypto re-bind spike closes (`D3-10` checklist item in design pack). Do not open `TB-*` until spike documents canonical signing strings and proof-token rotation decision.

## Links

- Grill: `../grill-sessions/blitz-2026-05-08-2-D3-phone-transfer-grill-me.md`
- Design pack: [`../../phone-transfer-ceremony-design-pack.md`](../../phone-transfer-ceremony-design-pack.md)
- `V-2026-0001`, `I-2026-0001`
