# Backlog Idea — `I-2026-0012` Hard-coded `Ezkey System` user sensitivity audit

## Metadata

- **ID:** `I-2026-0012`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P0-foundations`, `P2-hardening`
- **Component tags:** `admin-api`, `core`, `infra`

## Intent

Audit whether initial system/admin identity is truly parameterizable or hard-coded. Non-default clean-start test; each finding → **parameterize** or **document invariant**. Default unchanged.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md).

**Deliverable:** [`../../ezkey-system-identity-sensitivity-report.md`](../../ezkey-system-identity-sensitivity-report.md).

## Scope

- **In scope:** grep inventory; clean-start with non-default `ezkey.admin.initial.*`; fix or document; update tests.
- **Out of scope:** changing default identity; multi-tenant system identity overrides.

## Promotion notes

Execute audit → fill report → TB for parameterize fixes.

## Links

- Report: [`../../ezkey-system-identity-sensitivity-report.md`](../../ezkey-system-identity-sensitivity-report.md)
- Grill: `../grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md`
