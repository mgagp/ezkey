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
- `I-2026-07-20-mobile-installation-trust-zone-canon` — installation-as-trust-zone canon (done); anchor point for the installation-level policy tier below

## Update — 2026-07-26

Follow-up discussion widened the framing from two tiers to three: a mobile-local user preference,
a per-enrollment policy, and a per-installation (organization-owned) policy — the last one owned by
an Ezkey installation operator (e.g. an on-prem SME deployment) rather than by an individual
enrollment. The installation tier reuses the existing installation-as-trust-zone canon
(`I-2026-07-20-mobile-installation-trust-zone-canon`) rather than introducing a new mobile identity
concept. The effective local-auth decision generalizes to "the strongest of the three tiers wins."
This does not change the vision's status (`under-review`) or its discovery-first posture; it is
captured here so the fuller intent is not lost before `I-2026-0001` / `TB-2026-0001` resume. See
`I-2026-0001` for the updated scope and `ezkey_mobile/docs/MOBILE_LOCAL_AUTH_POLICY_AND_AUDIT_FUTURE_WORK.md`
for the generalized model and the signed bind-attribute transport direction.
