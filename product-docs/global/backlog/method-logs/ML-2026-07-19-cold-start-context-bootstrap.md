# Method Log — `ML-2026-07-19` Cold-start context bootstrap alignment

## Metadata

- **ID:** `ML-2026-07-19-cold-start-context-bootstrap`
- **Lane:** hygiene / governance alignment (not a product `I-*` / `TB-*` program)
- **Purpose:** Record the decision to retire the ritual full-`ENDPOINT` + dual PRD/`product-intent` cold start in favour of a tiered compass aligned with `product-docs/GOVERNANCE.md`.
- **Status:** `integrated` (2026-07-19)

---

## Context

Early Cursor / agent rules forced every new session to read root `PRD.md`, `README.md`, and full `docs/ENDPOINT.md` (~2200 lines). After `product-docs/` became the living canon, that ritual competed with:

- canonical product framing in `product-docs/global/product-intent.md`;
- always-applied injection of root `AGENTS.md` and workflow rules;
- short protocol docs (`docs/CRYPTO.md`, enrollment/auth signature payload docs).

## Decision

1. **Tiered cold start** in root `AGENTS.md` (Tier 0 compass → Tier 1 `product-intent` → Tier 2 domain on demand).
2. **Root `PRD.md` → stub** pointing at `product-intent.md`; unique Android Keystore / StrongBox / sealed-secrets wording moved into `product-intent` Security.
3. **Project rule** `.cursor/rules/cold-start-context.mdc` (`alwaysApply`) supersedes the outdated user-rule ritual for Ezkey sessions.
4. **Methodology bootstrap dedupe:** full procedure stays in `AGENTS.md` § Fresh-session; `.cursor/rules/product-docs-workflow-bootstrap.mdc` is a short pointer only.

## Operator follow-up (Cursor User Rules)

If a **Cursor User Rule** still says: *mandatory read PRD.md, README.md, ENDPOINT.md every new session*, replace it with a short pointer, for example:

> For Ezkey, follow root `AGENTS.md` § Cold-start context and `.cursor/rules/cold-start-context.mdc`. Do not ritual-read full `docs/ENDPOINT.md`.

Project rules already state that they override that outdated user rule when both are present.

## Files touched

- `AGENTS.md`, `PRD.md`, `product-docs/global/product-intent.md`
- `README.md`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`, `.github/copilot-instructions.md`
- `.cursor/rules/cold-start-context.mdc` (new), `.cursor/rules/product-docs-workflow-bootstrap.mdc` (slimmed)

## Related

- Canon ownership: `product-docs/GOVERNANCE.md` → `global/product-intent.md`
- Hygiene vs program: `product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`
