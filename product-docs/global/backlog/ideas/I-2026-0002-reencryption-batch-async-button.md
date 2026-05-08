# Backlog Idea — `I-2026-0002` Re-encryption batch UI: async button behavior

## Metadata

- **ID:** `I-2026-0002`
- **Status:** `triaged`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-ui`, `admin-api`

## Intent

Ensure the Admin UI re-encryption controls always trigger asynchronous batch execution and return control to the operator immediately, with progress observable through the existing batch table. No HTTP request should block while the actual re-encryption work runs.

## Problem and value

- **Problem:** When the operator triggers re-encryption for a specific encryption key from the Admin UI, the action *appears* to behave synchronously — the button stays in a busy state for the entire batch run. This perception needs to be validated against the actual implementation. If confirmed, the design does not scale: with hundreds of thousands of records to re-encrypt, the HTTP call cannot remain pending. If the perception is incorrect, the underlying cause of the apparent block must still be identified and addressed.
- **Expected value:** Predictable async UX consistent with the existing batch table; no risk of HTTP timeouts or blocked operators on large datasets; alignment with the broader `F-encryption-key-rotation` posture.

## Scope

- **In scope:**
  - Investigate the actual behavior of the per-key re-encryption trigger in `admin-api` and `admin-ui`.
  - If synchronous, refactor so the button creates the batch and triggers immediate execution as two non-blocking actions, returning control to the UI right away.
  - Confirm batch progress is visible in the existing re-encryption batch table.
  - Align the two existing trigger buttons (immediate re-encryption vs create-batch-without-execute) on a consistent async contract.
- **Out of scope:**
  - Changes to the underlying re-encryption algorithm or batch persistence model.
  - Performance tuning of the batch itself.

## Key assumptions

- The existing batch table is sufficient as the progress surface; no new dashboard widget is required for V1.
- The two existing trigger buttons share enough infrastructure that aligning them on async behavior is a single coherent change.

## Risks and exceptions

- The perceived synchronous behavior may be a UI feedback issue (for example, spinner state) rather than a real blocking call. Validation must precede any backend refactor.
- An async refactor must preserve operator clarity: the immediate feedback after click must be unambiguous (batch created, batch running) without dropping error reporting.

## Promotion notes

Move to `triaged` once the perception is validated against the real behavior. Move to `incubating` if a backend refactor is required, with a clear contract on the two button actions and integration with the existing batch table.

## Links

- Related feature: `F-encryption-key-rotation`
- Related principles: `Design Principle #1` (simplicity), `#5` (operator-first)
