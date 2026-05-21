# Retrofit Slice — `R-2026-0001` Mobile certificate pinning (SPKI + TOFU + Ezkey-authenticated recovery)

## Metadata

- **ID:** `R-2026-0001`
- **Status:** `captured`
- **Source type:** `plan`
- **Capture date:** `2026-05-08`
- **Owner:** product + AI collaboration
- **Confidence:** `high`

## Source batch

- `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md` — active plan (~540 lines), with executive summary, todos, recommended Ezkey posture, and Android-first phasing.

## Trigger

User dictation (blitz `_blitz-2026-05-08-2.md`, item D2) re-articulated the design captured in the plan above. The retrofit canonizes the plan content into product-docs so the design is discoverable from the methodology rather than from a `.cursor/plans/` artifact.

## Extracted decisions and invariants

- **Pin the SPKI hash, not the full leaf certificate.** The SHA-256 of the DER-encoded `SubjectPublicKeyInfo` is the pinning target. This survives certificate renewal that keeps the same key pair.
- **TOFU at enrollment.** The first SPKI hash is captured at enrollment time, validated by the existing enrollment cryptographic challenge, and stored in installation-level mobile state.
- **Native pinning enforcement during normal operation.** Pinning is enforced through the platform's native networking layer, not at application level alone.
- **Auth API recovery endpoint, narrow and explicit.** A dedicated endpoint is reachable without pinning but authenticated by the existing Ezkey device/enrollment cryptography. It returns the new pin signed by integration material that the mobile app can verify.
- **User confirmation before pin replacement.** After mismatch, the user must explicitly confirm the new pin before it replaces the stored one.
- **Recovery is a controlled risk window, not a magic MITM-proof ceremony.** The plan is honest about residual risk concentrated in the trust-refresh moment.
- **Cloudflare free plan is the practical context.** Pure static certificate pinning is operationally weak because the mobile client sees the edge certificate, which can rotate without pre-announcement; the recovery endpoint exists to handle this realistically.
- **Android-first.** Validate on a real Cloudflare-backed deployment, then decide whether to mirror on iOS immediately.
- **Audit + nightly compliance batch.** Each pin transition is recorded in the audit chain. A nightly batch correlates rotation events with the deployed certificate and detects mismatches that may indicate fraud.

## Patterns

- **Backend-first integrity (`Design Principle #3`):** the trust source is the Ezkey backend, signed by integration material; the mobile app verifies, not decides.
- **Explicit trust boundary at recovery (`#4`):** the recovery endpoint is the only path where pinning is bypassed; the boundary is named, narrow, and documented.
- **Honest scope (`#12`):** the posture does not claim more security than it delivers. The recovery window is named.

## Risks (from the plan, retained)

- A bad actor with the user's enrollment key could exploit the recovery window; mitigated by user confirmation and audit visibility.
- Cloudflare-managed rotation timing is outside Ezkey control; the recovery endpoint must be available even when pinning fails on the main flow.
- iOS pinning has different platform mechanics than Android; do not assume parity at first.

## Mapping to canonical destinations

| Canonical destination | Mapping action | Status |
|-----------------------|----------------|--------|
| `product-docs/global/vision/product-orientation-notes.md` (`V-2026-0006`) | Captures the orientation note (Ezkey-style pinning posture) | **integrated** in this slice |
| `product-docs/components/mobile/MOBILE_CRYPTO_REFERENCE.md` and similar component design notes | To extend with the SPKI pinning + TOFU + recovery boundary | gap (pending follow-up) |
| `product-docs/global/architecture-decisions.md` | Candidate ADR for the pinning + recovery boundary if the design crystallizes during implementation | gap (pending follow-up) |
| Audit chain extension on `admin-api` | New audit event types for pin transition; nightly correlation batch | gap (pending follow-up) |

## Confidence and residual gaps

- **Confidence high** that the plan content is internally coherent and aligned with Ezkey product values. Several years of mobile security review converge on the SPKI + TOFU + recovery posture for similar platforms.
- **Residual gaps:**
  - Concrete Auth API recovery contract (request and response schema, error model) is not yet a canonical artifact.
  - Mobile installation-level state schema is not yet documented in component docs.
  - Audit event type catalog has not been extended to cover pin transitions.
  - iOS-side phasing decision is deferred to implementation time.

## Grill outcome (2026-05-19)

- **`V-2026-0006` grilled** — posture confirmed; session `blitz-2026-05-08-2-D2-D11-retrofit-grill-me.md`.
- Recovery challenge **optional** when device proof + backend signature suffice.
- Compliance batch **later** — not R1 blocker.
- **Android-first** confirmed.

## Next action

- Complete component-doc mapping gaps (mobile crypto reference, recovery contract sketch).
- Propose an `I-*` for Auth API recovery contract + mobile state schema when retrofit mapping is done.
- Coordinate with `V-2026-0008` (Auth API versioning) at design-pack time, not as a grill blocker.

## Links

- Source plan: `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md`
- Derived vision: `V-2026-0006`
- Adjacency: `V-2026-0001` / `I-2026-0001` (per-enrollment local-auth posture; same audience and lifecycle), `V-2026-0008` (API versioning, related to Auth API surface evolution)
- Methodology: [`legacy-retrofit-workflow.md`](../../methodology/legacy-retrofit-workflow.md)
