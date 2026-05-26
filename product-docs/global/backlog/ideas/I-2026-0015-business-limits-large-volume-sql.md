# Backlog Idea — `I-2026-0015` Business limits on potentially large-volume SQL queries

## Metadata

- **ID:** `I-2026-0015`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`, `repositories`
- **Captured by:** Marc

## Intent

Repository-layer **business limits** on high-volume queries (complement pagination + rate limits). Priority: audit logs, auth attempts. Externalized config; limits high enough for legitimate ops.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md).

**Deliverable:** [`../../sql-business-limits-policy.md`](../../sql-business-limits-policy.md).

## Scope

- **In scope:** inventory; policy doc; apply limits + CONFIGURATION.md; cross-link paginated matrix Tier B.
- **Out of scope:** replacing paginated list contracts; per-tenant limit overrides; living controllers registry (`I-2026-0009` dropped — see downscope decision).

## Promotion notes

Fill policy inventory (targeted repository grep + paginated matrix Tier B) → implement caps on identified methods.

## Links

- Policy: [`../../sql-business-limits-policy.md`](../../sql-business-limits-policy.md)
- Decision: [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md)
- Grill: `../grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md`
- Companion: `I-2026-0013`, `I-2026-0014`
