# Grill Me — Blitz 2026-05-08-2, D6 + D10 (system identity audit + SQL business limits)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D6, D10) |
| **Backlog** | `I-2026-0012`, `I-2026-0015` |
| **Deliverables** | [`../../ezkey-system-identity-sensitivity-report.md`](../../ezkey-system-identity-sensitivity-report.md), [`../../sql-business-limits-policy.md`](../../sql-business-limits-policy.md) |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions (operator-confirmed)

### D6 — System identity sensitivity

| ID | Decision |
|----|----------|
| D6D10-1 | Each finding: **parameterize** or **document as invariant** — no silent hard-coding |
| D6D10-2 | Run clean-start with **non-default** `ezkey.admin.initial.*`; record failures |
| D6D10-3 | **Default unchanged** (`Ezkey System`, id 1) |
| D6D10-4 | Scope: code + tests + fixtures; audit display may stay invariant if justified |
| D6D10-10 | Deliverable: [`ezkey-system-identity-sensitivity-report.md`](../../ezkey-system-identity-sensitivity-report.md) |

### D10 — SQL business limits

| ID | Decision |
|----|----------|
| D6D10-5 | Complements pagination + rate limits — caps on **heavy / non-paginated / internal** queries |
| D6D10-6 | Enforce at **repository** layer; externalized config; CONFIGURATION.md |
| D6D10-7 | Priority: **audit logs**, **auth attempts** (Tier B tables) |
| D6D10-8 | Limits **high enough** for legitimate ops; bound abuse (#14) |
| D6D10-9 | Inventory via controllers registry + targeted grep; lightweight skill later |
| D6D10-11 | Deliverable: [`sql-business-limits-policy.md`](../../sql-business-limits-policy.md) + registry notes |

## Links

- [`../ideas/I-2026-0012-ezkey-system-hardcoded-sensitivity.md`](../ideas/I-2026-0012-ezkey-system-hardcoded-sensitivity.md)
- [`../ideas/I-2026-0015-business-limits-large-volume-sql.md`](../ideas/I-2026-0015-business-limits-large-volume-sql.md)
