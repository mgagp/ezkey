# V-2026-0011 — Android-first real-device mobile validation as a first-class confidence layer

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** Establish Android real-device functional validation as a first-class confidence
  layer for Ezkey Mobile. The product direction is to validate the real backend-to-phone trust
  path on a physical Android device against the local clean-start stack, not only through unit
  tests and backend API functional tests. This layer should stay pragmatic, open-source-first,
  Android-first, and focused on a small number of high-signal flows rather than broad fragile UI
  coverage.
- **Signals:** The mobile app is part of Ezkey's cryptographic trust chain, so the current split
  between mobile unit/component tests and `ezkey-tests` Docker-stack API validation leaves a
  meaningful gap around real device signing, real Android keystore behavior, and intermittent
  `pending` / `respond` failures. A current-session working plan converged on an Android-first
  posture with open-source real-device automation, optional mirrored execution, Bash
  orchestration, and a hybrid init model where steady state is automated even if first-run setup
  remains partly human-assisted.
- **Potential impact:** `ezkey_mobile`, `ezkey-tests`, `auth-api`, `admin-api`, Docker
  clean-start usage, mobile test strategy, release confidence, and debugging posture for
  intermittent pending-signature or timeout issues. The direction also affects mobile testability
  conventions such as stable critical-path selectors.
- **Next step:** execute **`TB-2026-0002`** (linked from `I-2026-0019`): one enrollment path
  (hybrid init allowed), one approved pending/respond slice, and one short steady-state auth loop
  on a real Android device against the clean-start stack.

## Related artifacts

- `I-2026-0019` — Android real-device mobile functional tests
- `TB-2026-0002` — Android real-device functional pilot
