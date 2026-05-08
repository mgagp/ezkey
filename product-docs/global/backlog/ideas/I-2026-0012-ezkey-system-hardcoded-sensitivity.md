# Backlog Idea — `I-2026-0012` Hard-coded `Ezkey System` user sensitivity audit

## Metadata

- **ID:** `I-2026-0012`
- **Status:** `triaged`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P0-foundations`, `P2-hardening`
- **Component tags:** `admin-api`, `core`, `infra`

## Intent

Run a focused sensitivity audit to confirm whether the platform tolerates a renamed or differently-identified initial system user (today known as `Ezkey System` with ID `1`), or whether hard-coded references in the codebase make that identity effectively immutable. If hard-coded references are found, decide whether to remove them in favor of parameterization or to formally accept the constraint as a foundational invariant.

## Problem and value

- **Problem:** All current testing flows (clean-start and `EXP1`) use the default `Ezkey System` user. There may be code paths that hard-code this user's name or ID, which would silently break if an operator chose a different value at first-run. The risk is latent: nothing fails today, but it could fail unpredictably in any installation that diverges from the default.
- **Expected value:** Eliminate latent fragility in foundational identity handling; ensure the parameterization story is honest — either the platform genuinely supports a different system identity or it is documented as fixed by design.

## Scope

- **In scope:**
  - Run a sensitivity test: bootstrap a clean-start with a non-default name/ID for the initial system user; observe what breaks.
  - Inventory hard-coded references to `Ezkey System` (or equivalent strings) across the codebase.
  - For each hard-coded reference: decide between (a) remove and parameterize, or (b) document as foundational invariant with rationale.
  - Update tests and documentation to reflect the chosen posture.
- **Out of scope:**
  - Renaming the default identity itself (the default remains `Ezkey System`).
  - Multi-tenant overrides of the system identity (separate scope if ever raised).

## Key assumptions

- The system identity is established at bootstrap and stable thereafter for the lifetime of the installation.
- Hard-coded references, if any, are localized enough to be addressed in a single focused change.

## Risks and exceptions

- Removing hard-coded references may surface assumptions in tests or fixtures that need parallel updates.
- Some references may be in audit material or other immutable-by-design surfaces; those should be documented as invariants rather than rewritten.

## Promotion notes

Move to `triaged` after the sensitivity test is run and the inventory of hard-coded references is captured. The decision (parameterize vs document as invariant) belongs in the same triage step.

## Links

- Related principles: `#2` (essential vs accidental complexity — distinguishing real invariants from accidental hard-coding), `#7` (stay within the chosen stack).
