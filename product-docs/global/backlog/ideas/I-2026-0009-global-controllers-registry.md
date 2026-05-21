# Backlog Idea — `I-2026-0009` Global controllers registry as documentation artifact

## Metadata

- **ID:** `I-2026-0009`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P1-operability`
- **Component tags:** `docs (product-docs)`, `admin-api`, `auth-api`, `integration-api`, `crypto-api`

## Intent

Author and maintain [`../../api-controllers-registry.md`](../../api-controllers-registry.md) — canonical high-level index of REST controllers for cross-cutting analysis (rate limits, pagination, versioning, DoS). Manual curation first; lightweight stub-diff skill later if ROI.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md).

- Registry **before** rate-limit baseline (`I-2026-0008`).
- No whole-repo token scan for sync skills.

## Scope

- **In scope:**
  - Fill registry for admin-api, auth-api, integration-api, crypto-api.
  - Link from methodology README.
- **Out of scope:**
  - Replacing OpenAPI or component flow docs.
  - Auto-generation from code in R1.

## Promotion notes

Move to `ready` when admin-api section is `reviewed`; then auth/integration/crypto.

## Links

- Canonical: [`../../api-controllers-registry.md`](../../api-controllers-registry.md)
- Companion: `I-2026-0008`, `I-2026-0014`, `I-2026-0015`
- Grill: `../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`
