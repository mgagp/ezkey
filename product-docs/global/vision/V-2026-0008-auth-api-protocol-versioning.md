# V-2026-0008 — Auth API versioning for mobile protocol evolution

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Capability negotiation** on existing Auth API endpoints (not full `/v1`/`/v2`
  URL trees). Rolling window: **current + one previous** generation. Mobile sends
  protocol/capability version; RFC 9457 on mismatch. Backend upgraded before mandating new
  capabilities. Legacy generation may offer reduced guarantees with **operator visibility**.
  Single store app line; documented sunset when earned (#14). R1: vision + contract sketch
  (`I-2026-0025`); implement when local-auth (`V-2026-0001`) forces a break.
- **Signals:** Grilling D7 Blitz 2 (2026-05-19). App-store lag vs self-hosted backend upgrade
  pace.
- **Potential impact:** `auth-api`, `mobile`, `admin-api` (read-only generation info), docs.
- **Next step:** contract sketch in product-docs when `I-2026-0001` / local-auth direction
  stabilizes.
- **Captured by:** Marc

## Related artifacts

- `I-2026-0025` — Auth API protocol capability versioning
- `V-2026-0001` — Mobile local-auth per enrollment (trigger for first protocol break)
- `V-2026-08-02-mobile-official-play-release-posture` — complementary mobile release track
  (advisory installation version/compat discovery, distribution-anchored client-update guidance);
  does not change this vision's protocol capability negotiation contract
