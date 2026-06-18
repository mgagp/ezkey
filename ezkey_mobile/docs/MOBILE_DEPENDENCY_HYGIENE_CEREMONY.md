# Mobile Dependency Hygiene Ceremony (Pragmatic)

Date: 2026-06-18
Scope: `ezkey_mobile`

## Intent

Define a lightweight, repeatable dependency-update routine that keeps momentum without adding ceremony overhead.

## Principles

- Keep updates incremental and isolated.
- Prefer low-risk value first.
- Defer ecosystem-blocked majors explicitly (do not force them).
- Maintain evidence for each lot (commands and outcomes).

## Minimal ceremony (5 steps)

1. **Monitor**
   - Run `yarn deps:monitor`.
   - Split result into:
     - actionable now
     - deferred by ecosystem gates

2. **Pick one lot only**
   - One dependency or one tightly coupled slice per lot.
   - Avoid mixed-risk bundles.

3. **Validate with the same ladder**
   - `yarn lint`
   - `yarn typecheck`
   - `yarn test --runInBand`
   - Android debug install path (`./scripts/build-install-debug-clean.sh`)

4. **Decide fast**
   - If green: commit and push.
   - If red: rollback immediately and capture reason.

5. **Update traceability**
   - Record the result in PR notes and relevant memo docs.
   - Keep deferred majors documented with explicit unblock conditions.

## Cadence recommendation

- Run the ceremony at least once per maintenance iteration.
- For active periods, prefer small weekly lots over large monthly batches.

## Deferred-major policy

Use deferred mode for majors blocked by current RN ecosystem alignment.

- ESLint 10: wait for RN lint-stack compatibility gate.
- Jest 30: wait for RN jest preset/runtime compatibility gate.

Reference: `MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md`.

## Operational notes

- For test pass/fail truth in PowerShell, rely on raw `yarn test --runInBand` exit code.
- For Android Windows builds, run path preflight and follow short-root guidance when MAX_PATH risk is flagged.
