# Ezkey System Identity — Sensitivity Report

## Purpose

Track findings from the **system user identity sensitivity audit** (`I-2026-0012`): whether the platform tolerates non-default initial admin/system identity, and how hard-coded references are resolved.

**Grilled:** Blitz 2026-05-08-2 D6 ([`backlog/grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md`](backlog/grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md)).

**Default (unchanged):** migration/bootstrap identity id `1`, display **`Ezkey System`** unless operator configures `ezkey.admin.initial.*`.

## Test protocol

1. clean-start with **non-default** `ezkey.admin.initial.username` / related bootstrap properties.
2. Exercise: admin login, enrollments, audit, functional test subset.
3. Record pass/fail per area.

## Findings table

| Location | Reference | Disposition | Action | Status |
|----------|-----------|-------------|--------|--------|
| *TBD audit* | | `parameterize` \| `invariant` | | `open` |

## Invariants (documented exceptions)

| Item | Rationale |
|------|-----------|
| *TBD* | |

## Related

- [`sql-business-limits-policy.md`](sql-business-limits-policy.md)
- `ezkey-admin-api/CONFIGURATION.md` — `ezkey.admin.initial.*`
