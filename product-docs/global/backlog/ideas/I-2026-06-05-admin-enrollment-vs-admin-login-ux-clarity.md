# Backlog Idea - `I-2026-06-05` Admin enrollment vs admin login UX clarity

## Metadata

- **ID:** `I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity`
- **Status:** `ready`
- **Priority:** `P1`
- **Created at:** `2026-06-05`
- **Updated at:** `2026-06-05`
- **Last reviewed at:** `2026-06-05`
- **Progression markers:** `P1-operability`, `P2-maintainability`
- **Component tags:** `admin-ui`, `admin-api`, `docs`
- **Lane:** `D`
- **Captured by:** Marc + Copilot session synthesis
- **GitHub issue:** `#191`

## Intent

Reduce operator confusion by making the distinction explicit between:

- using/verifying an enrollment at device level, and
- completing an authenticated admin login that produces admin-session side effects (`last_login_at`, recovery-code eligibility).

## Problem and value

- **Problem:** In real usage, phone interaction patterns can look the same between enrollment test and admin login. Operators infer that enrollment success implies login completion, but backend lifecycle semantics do not.
- **Expected value:** Faster troubleshooting, fewer false assumptions on recovery-code availability, and better onboarding for teams adopting Ezkey.

## Scope

- **In scope:**
  - Add explicit dual-state cues in Admin UI where operators decide security actions.
  - Show eligibility reason when initial recovery-code issuance is unavailable.
  - Align UX copy across activation/recovery/admin-detail surfaces with lifecycle semantics.
  - Add concise operator documentation explaining the distinction.
  - Add targeted validation scenarios (UI + API behavior expectations).
- **Out of scope:**
  - Changing cryptographic enrollment protocol.
  - Auto-generating recovery codes during activation bootstrap.
  - Broad redesign of admin lifecycle model.

## Key assumptions

- The current backend behavior is intentional and should remain as security baseline.
- Most confusion can be removed by clearer state visibility and message timing.
- A small, targeted UX/docs slice can materially improve adopter comprehension.

## Risks and exceptions

- Over-explaining in UI may create cognitive noise if not concise.
- If copy diverges from backend rules, trust decreases; copy must remain contract-accurate.
- Existing tests may assert current labels/messages and require focused updates.

## Candidate first slice (TB candidate)

1. Admin detail shows explicit "Enrollment status" and "Admin login status" indicators.
2. Initial recovery-code action is disabled with visible rationale when first login is missing.
3. Activation and recovery screens include one short, consistent statement clarifying that enrollment validation is not equivalent to completed admin login.
4. Add one targeted UI validation path and one API-level expectation note in test plan.

## Promotion notes

Move to `ready` when:

1. exact copy and state conditions are agreed,
2. impacted screens and API response dependencies are listed,
3. minimal test evidence plan is accepted.

Promotion gate reached on `2026-06-05` via:

- `TB-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity-first-cut`

## Links

- Vision: `product-docs/global/vision/V-2026-06-05-admin-enrollment-vs-admin-login-state-model.md`
- Session synthesis: `product-docs/global/backlog/grill-sessions/2026-06-05-admin-enrollment-vs-login-state-clarification-grill-me.md`
- Tracer bullet: `product-docs/global/backlog/ideas/TB-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity-first-cut.md`
- Lifecycle canon: `docs/LIFECYCLE_GOVERNANCE.md`
- Related code anchors:
  - `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
  - `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java`
  - `ezkey-admin-ui/src/pages/admins.tsx`
