# Backlog Idea — `I-2026-0011` Bootstrap clean-start: activation-code mode as default

## Metadata

- **ID:** `I-2026-0011`
- **Status:** `incubating`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-api`, `infra (clean-start)`, `bootstrap`, `docs`

## Intent

Make **activation-code** admin onboarding the **security-first default** for real installations on empty DB. Keep QR/full-export convenience **behind explicit flags** for clean-start Docker and functional tests. **Retire** recovery-code bootstrap entry mode.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D4-bootstrap-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D4-bootstrap-grill-me.md).

## Scope

- **In scope:**
  - Default bootstrap posture: activation-code for production narrative.
  - clean-start / demo device: explicit opt-in to legacy convenience modes.
  - Remove recovery-code bootstrap path; update docs + CONFIGURATION.md.
  - Align with `F-admin-lifecycle`, `F-recovery-codes-lifecycle`.
- **Out of scope:**
  - Redesign activation-code flow itself.
  - Migration of existing installs (re-bootstrap only if operator chooses).

## Promotion notes

Design pack: property matrix real vs clean-start; test plan for functional suite + manual clean-start.

## Links

- Grill: `../grill-sessions/blitz-2026-05-08-2-D4-bootstrap-grill-me.md`
- Plan: `plans/admin_onboarding_security_posture_rfc.plan.md`
- Profiles: `V-2026-0010`
