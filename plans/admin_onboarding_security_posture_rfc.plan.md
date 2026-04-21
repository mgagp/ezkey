---
name: Admin Onboarding Security Posture RFC
overview: Product and security posture for immediate onboarding, activation-code onboarding, deferred recovery-code revelation, and future login-surface simplification.
todos: []
isProject: false
---

# Admin Onboarding Security Posture RFC

## 1. Purpose

This note clarifies the intended security posture of Ezkey administrator onboarding so the product does not accidentally over-claim production readiness.

## 2. Current position

- `IMMEDIATE` onboarding is a bootstrap convenience mode.
- `ACTIVATION_CODE` onboarding is a bootstrap convenience mode.
- Neither should be presented as the final production-grade onboarding posture while outbound delivery channels remain absent.

## 3. Immediate quick win

- Recovery codes should be generated server-side when the first enrollment exists.
- Recovery codes should **not** be revealed in bootstrap responses or unauthenticated login/activation flows.
- Recovery codes should instead be revealed or regenerated later from an authenticated admin-management flow.

Rationale:

- avoids dumping all bootstrap and break-glass material in one unauthenticated screen;
- improves evaluator perception immediately with low implementation cost;
- preserves the future option of stronger multi-channel delivery without reworking the core lifecycle.

## 4. Target production posture

Recommended serious-production direction:

1. Activation code or invitation link starts first-time setup.
2. Binding material is split across controlled or separate channels.
3. First bind completes.
4. Recovery codes are revealed only after authenticated access is established.

Pragmatic target variant:

- QR on screen.
- Binding challenge or equivalent OTP via SMS or another separate channel.

## 5. Wording guidance

### Immediate mode

- Describe it as immediate first-enrollment bootstrap.
- Do not claim production-grade channel separation.
- State that recovery codes are deferred.

### Activation-code mode

- Describe it as deferred first-time setup bootstrap.
- State clearly that the activation code is a one-time bootstrap secret, not a durable login credential.
- State that recovery codes are deferred.

## 6. Login surface direction

The login page is accumulating too many exceptional paths in one visual surface.

Recommended next UI direction:

1. Keep passwordless login as the dominant default view.
2. Collapse recovery and activation into a lighter branch chooser.
3. Consider a dedicated second-step panel or sub-route for exceptional flows.
4. Avoid showing all secondary flows with equal visual weight on first paint.

## 7. RFC follow-up questions

1. What is the minimum acceptable production channel split: QR on screen + SMS challenge, or stronger?
2. Should recovery-code revelation require a completed first bind only, or a completed first authenticated session?
3. Should the authenticated reveal path reuse `regenerate recovery codes` or add a dedicated first-reveal flow?
4. Should the login branch chooser become a segmented control, a compact menu, or dedicated routes?