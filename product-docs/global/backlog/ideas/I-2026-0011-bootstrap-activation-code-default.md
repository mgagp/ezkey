# Backlog Idea — `I-2026-0011` Bootstrap clean-start: activation-code mode as default

## Metadata

- **ID:** `I-2026-0011`
- **Status:** `triaged`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-api`, `infra (clean-start)`, `bootstrap`, `docs`

## Intent

Make the activation-code admin-onboarding mode the default for the bootstrap flow exposed by the Docker clean-start sequence on a fresh database, and revisit whether the intermediate recovery-code-based bootstrap mode still has a role given that the activation-code approach is conceptually and security-wise superior. Real installations should benefit from a security-by-default first-time-setup posture, while the local clean-start can preserve the current QR-on-screen ceremony for fast functional testing.

## Problem and value

- **Problem:** The current bootstrap default exposes the full QR + all recovery codes on the first run, which is too much sensitive material in one place. An intermediate mode using a recovery code as the bootstrap entry point was introduced earlier, but recovery codes are conceptually post-enrollment artifacts, so that intermediate mode was a mild distortion. Meanwhile, the activation-code admin onboarding flow added later (in the `EXP1` context) provides a cleaner two-step model.
- **Expected value:** Bootstrap behavior aligned with the security posture of real installations; cleaner conceptual model (admin lifecycle and recovery-code lifecycle separated, per `F-admin-lifecycle` and `F-recovery-codes-lifecycle`); reduced cognitive overhead because the bootstrap reuses the same primitives a real operator would use.

## Scope

- **In scope:**
  - Add a new bootstrap option that creates the first admin via an activation-code workflow prepared by the bootstrap sequence; make it the **security-first default** for real installations.
  - Keep the current QR-with-everything ceremony available for the local Docker clean-start used by automated functional tests, where speed matters more than production posture.
  - Review whether the intermediate recovery-code-based bootstrap still adds value or should be retired.
  - Update operator documentation to reflect the new default and the rationale (security by default).
- **Out of scope:**
  - Changes to the activation-code flow itself (already implemented).
  - Backfill or migration of existing installations (if any) — they remain on their current mode unless they re-bootstrap.

## Key assumptions

- The activation-code admin onboarding flow is mature enough to be the default first-run path on real installations.
- The local clean-start Docker flow can keep the current convenience ceremony without needing to be the default for production-leaning installations.
- The recovery-code intermediate mode was a transitional accommodation; retiring it is acceptable given the activation-code path supersedes it conceptually.

## Risks and exceptions

- If the activation-code flow has friction we have not yet observed in the bootstrap context, the new default may slow first-time setup. Mitigation: keep the QR-and-recovery-codes mode available behind an explicit flag for advanced users.
- Documentation must be unambiguous about which mode applies in which context (clean-start Docker vs real installation).

## Promotion notes

Move to `triaged` after confirming the existing activation-code flow can be invoked from the bootstrap path without modification. Promote to `incubating` and then `ready` once the bootstrap flag/parameter design is sketched. The retirement decision for the recovery-code intermediate mode can be tracked as a sibling action item in the same change set or scheduled separately.

## Links

- Source plan (background context, not retrofit at this time): `plans/admin_onboarding_security_posture_rfc.plan.md`
- Adjacent vision: `V-2026-0002` (deployment profiles — clean-start is one such profile and the default-for-real-installations posture aligns).
- Related features: `F-admin-lifecycle`, `F-recovery-codes-lifecycle`.
- Related principles: `#1` (simplicity), `#12` (security as posture).
