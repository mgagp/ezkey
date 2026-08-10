# Retrofit Slice — `R-2026-0001` Mobile certificate pinning (SPKI + TOFU + Ezkey-authenticated recovery)

## Metadata

- **ID:** `R-2026-0001`
- **Status:** `integrated`
- **Source type:** `plan`
- **Capture date:** `2026-05-08`
- **Owner:** product + AI collaboration
- **Confidence:** `high`

## Source batch

- `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md` — historical working plan (~540 lines),
  now retired after canonical rematerialization (`2026-07-25`).

## Trigger

User dictation (blitz `_blitz-2026-05-08-2.md`, item D2) re-articulated the design captured in the plan above. The retrofit canonizes the plan content into product-docs so the design is discoverable from the methodology rather than from a `.cursor/plans/` artifact.

## Extracted decisions and invariants

- **Pin the SPKI hash, not the full leaf certificate.** The SHA-256 of the DER-encoded `SubjectPublicKeyInfo` is the pinning target. This survives certificate renewal that keeps the same key pair.
- **TOFU at enrollment.** The first SPKI hash is captured at enrollment time, validated by the existing enrollment cryptographic challenge, and stored in installation-level mobile state.
- **Native pinning enforcement during normal operation.** Pinning is enforced through the platform's native networking layer, not at application level alone.
- **Auth API recovery endpoint, narrow and explicit.** A dedicated endpoint is reachable without pinning but authenticated by the existing Ezkey device/enrollment cryptography. It returns the new pin signed by integration material that the mobile app can verify.
- **User confirmation before pin replacement (historical).** Grill/R1 text required end-user confirm; **superseded 2026-08-10** by MATCH-only edge corroboration (no end-user certificate authentication). See Post-integration refinement and design pack.
- **Recovery is a controlled risk window, not a magic MITM-proof ceremony.** The plan is honest about residual risk concentrated in the trust-refresh moment.
- **Cloudflare free plan is the practical context.** Pure static certificate pinning is operationally weak because the mobile client sees the edge certificate, which can rotate without pre-announcement; the recovery endpoint exists to handle this realistically.
- **Android-first.** Validate on a real Cloudflare-backed deployment, then decide whether to mirror on iOS immediately.
- **Audit + nightly compliance batch.** Each pin transition is recorded in the audit chain. A nightly batch correlates rotation events with the deployed certificate and detects mismatches that may indicate fraud.

## Patterns

- **Backend-first integrity (`Design Principle #3`):** the trust source is the Ezkey backend, signed by integration material; the mobile app verifies, not decides.
- **Explicit trust boundary at recovery (`#4`):** the recovery endpoint is the only path where pinning is bypassed; the boundary is named, narrow, and documented.
- **Honest scope (`#12`):** the posture does not claim more security than it delivers. The recovery window is named.

## Risks (from the plan, retained)

- A bad actor with the user's enrollment key could exploit the recovery window; mitigated by MATCH-only edge corroboration, signed accept, and Global Admin audit visibility (see design pack).
- Cloudflare-managed rotation timing is outside Ezkey control; the recovery endpoint must be available even when pinning fails on the main flow.
- iOS pinning has different platform mechanics than Android; do not assume parity at first.

## Mapping to canonical destinations

| Canonical destination | Mapping action | Status |
|-----------------------|----------------|--------|
| `product-docs/global/vision/V-2026-0006-mobile-certificate-pinning.md` | Expanded strategic canon (middle path, complexity line, StrongBox/passkeys non-equivalence boundary, open convergence items). | integrated |
| `product-docs/global/backlog/ideas/I-2026-07-25-mobile-certificate-pinning-middle-path.md` | Created active analysis home for ongoing refinements and strategy convergence. | integrated |
| `product-docs/global/mobile-certificate-pinning-design-pack.md` | Normative protocol / UX / API / Admin design (2026-08-10). | integrated |
| `docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md` | Canonical offer/accept signature strings. | integrated |
| `docs/MOBILE_DEVELOPER_GUIDE.md` / `docs/SECURITY_POSTURE.md` / `ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md` | StrongBox/pinning honesty boundary already documented and aligned with this posture. | integrated |
| `product-docs/global/architecture-decisions.md` | Candidate ADR for the pinning + recovery boundary if needed at implementation. | deferred |
| Audit chain extension on `admin-api` | New audit event types for pin transition; nightly correlation batch. | deferred |

## Changes applied

- [x] `product-docs/global/vision/V-2026-0006-mobile-certificate-pinning.md` — rematerialized
  durable strategic posture and explicit non-claims.
- [x] `product-docs/global/backlog/ideas/I-2026-07-25-mobile-certificate-pinning-middle-path.md`
  — created incubating idea for ongoing convergence.
- [x] `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md` — source retired after
  rematerialization.
- [x] `product-docs/global/mobile-certificate-pinning-design-pack.md` — design pack (2026-08-10).
- [x] `docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md` — payload canon (2026-08-10).
- [ ] `product-docs/global/architecture-decisions.md` — deferred unless TB needs an ADR.
- [ ] Admin-api audit extension docs — deferred pending implementation slice.

## Confidence and residual gaps

- **Confidence high** that the plan content is internally coherent and aligned with Ezkey product values. Several years of mobile security review converge on the SPKI + TOFU + recovery posture for similar platforms.
- **Residual gaps (implementation):**
  - Design pack + payload canon exist; Java/mobile/Admin UI not implemented.
  - Audit `EventType` catalog not yet extended in code.
  - iOS-side phasing deferred to after Android validation.
  - Config surface for `spkiPinningMode` still open (property vs Admin UI).

## Grill outcome (2026-05-19)

- **`V-2026-0006` grilled** — posture confirmed; session `blitz-2026-05-08-2-D2-D11-retrofit-grill-me.md`.
- Recovery challenge **optional** when device proof + backend signature suffice.
- Compliance batch **later** — not R1 blocker.
- **Android-first** confirmed.

## Post-integration refinement (2026-08-09)

Cold revisit (historical; partially superseded 2026-08-10):

- Cry-wolf UX on expected ~quarterly edge rotations is a first-class constraint.
- Backend-corroborated legitimate rotation retained as the noise-reduction mechanism.
- Ezkey twist: pinning as steady-state fail-closed control **plus** operator trust-pattern signal.

## Post-integration refinement (2026-08-10)

Design pack authored — **live decision surface** for protocol/UX/API:

- **End-user certificate confirmation removed** (supersedes 2026-08-09 V6-2 / PIN-4 and grill
  “user confirmation” invariant).
- **MATCH-only rebind**; non-MATCH → blocked + retry + Global Admin audit.
- Trust-zone policy `ENFORCED` / `DISABLED`; per-installation pinning metadata; mode on public
  GET (hint) + signed instance-info (authoritative).
- Payload canon: `docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md`.

Authoritative write-ups: `V-2026-0006`, design pack
`product-docs/global/mobile-certificate-pinning-design-pack.md`, idea
`I-2026-07-25-mobile-certificate-pinning-middle-path`.

## Next action

- Open a `TB-*` from the design pack when implementation starts (Android + Auth API recovery +
  mode fields + audit).
- Coordinate with `V-2026-0008` (Auth API versioning) at TB time if the trust surface needs a
  protocol generation bump.

## Links

- Source plan (retired 2026-07-25): `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md`
- Derived vision: `V-2026-0006`
- Design pack: [`../mobile-certificate-pinning-design-pack.md`](../mobile-certificate-pinning-design-pack.md)
- Payload canon: [`../../../docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md`](../../../docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md)
- Adjacency: `V-2026-0001` / `I-2026-0001` (per-enrollment local-auth posture; same audience and lifecycle), `V-2026-0008` (API versioning, related to Auth API surface evolution)
- Methodology: [`legacy-retrofit-workflow.md`](../../methodology/legacy-retrofit-workflow.md)
