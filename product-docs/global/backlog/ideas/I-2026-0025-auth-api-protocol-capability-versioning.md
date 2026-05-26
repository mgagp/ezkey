# Backlog Idea — `I-2026-0025` Auth API protocol capability versioning

## Metadata

- **ID:** `I-2026-0025`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-19`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P1-operability`
- **Component tags:** `auth-api`, `mobile`, `admin-api`, `docs`
- **Captured by:** Marc

## Intent

When `V-2026-0008` moves to implementation: add **capability negotiation** on Auth API (not full URL versioning), support **current + previous** generation, operator visibility, and documented sunset — triggered by mobile protocol evolution (e.g. `V-2026-0001` local-auth).

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D7-auth-api-versioning-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D7-auth-api-versioning-grill-me.md).

## Scope

- **In scope (when promoted):** capability fields on enrollment/auth payloads; server dual-path handlers; Problem Details for mismatch; Admin UI read-only generation info; contract doc in product-docs.
- **Out of scope R1:** parallel `/v2` API tree; multiple Play Store app versions; indefinite legacy support.

## Promotion notes

**Incubating** until local-auth or other breaking mobile protocol is ready for design pack.

## Links

- Vision: `V-2026-0008`
- Grill: `../grill-sessions/blitz-2026-05-08-2-D7-auth-api-versioning-grill-me.md`
