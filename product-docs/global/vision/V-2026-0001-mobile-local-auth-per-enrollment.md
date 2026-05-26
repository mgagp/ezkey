# V-2026-0001 — Enrollment-scoped local-auth posture for mobile respond

- **Date:** `2026-05-07`
- **Status:** `under-review`
- **Intent:** Explore a product direction where local authentication protection for mobile
  `respond` evolves from global setting to enrollment-scoped posture, with explicit handling of
  capability constraints and key lifecycle implications.
- **Signals:** Current model is globally toggled, not enrollment-scoped, and not
  backend-attested; security investigations highlight the need for explicit trust-boundary wording
  and pragmatic Android-first hardening.
- **Potential impact:** mobile security UX, enrollment lifecycle semantics, future backend policy
  hooks, Auth API and Admin API operator expectations, and test strategy updates.
- **Next step:** promote as backlog discovery item `I-2026-0001` and run a bounded
  capability/design analysis before implementation commitment.
- **Captured by:** Marc

## Related artifacts

- `I-2026-0001` — Per-enrollment local authentication policy for mobile respond
- `TB-2026-0001` — Mobile local auth capability discovery
