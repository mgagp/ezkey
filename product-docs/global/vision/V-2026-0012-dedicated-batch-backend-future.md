# V-2026-0012 — Dedicated batch backend (future): optional split from Admin API

- **Date:** `2026-05-19`
- **Status:** `draft`
- **Intent:** Record a **future** architectural option: extract scheduled/batch work from
  `admin-api` into a dedicated backend (e.g. `ezkey-batch-api`), with `admin-api` closer to an
  Admin UI BFF. R1 keeps all batches in Admin API as atomic all-or-nothing.
- **Signals:** Grilling C8 noted theoretical value (heap isolation, partial failure) but prior
  review: extra deployment complexity not justified for an unproven product.
- **Next step:** none for R1. Revisit on production evidence. See grill session
  `integrity-cluster-D4-D6-grill-me.md`.

## Related artifacts

- `V-2026-0004` — Integrity validation strategy (origin of this discussion)
- Grill session: `product-docs/global/backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md`
