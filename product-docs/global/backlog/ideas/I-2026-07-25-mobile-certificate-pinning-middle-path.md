# Backlog Idea — `I-2026-07-25-mobile-certificate-pinning-middle-path` Mobile certificate pinning middle path

## Metadata

- **ID:** `I-2026-07-25-mobile-certificate-pinning-middle-path`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-07-25`
- **Updated at:** `2026-08-10`
- **Last reviewed at:** `2026-08-10`
- **Progression markers:** `P2-hardening`, `P3-distribution`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `docs`, `security`
- **Lane:** `D`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Converge and deliver Ezkey's opinionated certificate pinning posture as a proportionate middle
path:

- materially stronger than TLS-only mobile transport,
- explicitly below passkey/FIDO-style formal assurance ceremony,
- operationally viable for Cloudflare-fronted self-hosted deployments,
- MATCH-only quiet rebind (no end-user certificate confirmation),
- usable without cry-wolf UX on expected legitimate edge rotations,
- valuable partly as an **operator trust-pattern signal**,
- policy-governed per **trust zone** (may not support pinning).

**Design pack (normative):**
[`../../mobile-certificate-pinning-design-pack.md`](../../mobile-certificate-pinning-design-pack.md)

## Problem and value

- **Problem:** Classical hard pinning fights Cloudflare-like unmanaged rotations; end-user
  “confirm certificate” UX is incoherent for MFA users; local/lab installs cannot always run
  edge corroboration.
- **Expected value:** One incubating idea + design pack that freeze protocol, UX, API, Admin
  visibility, and honesty boundaries before the first `TB-*`.

## Settled decisions (absorb from `V-2026-0006` + design pack)

| ID | Decision |
|----|----------|
| PIN-1 | SPKI pin at **trust-zone / installation** scope; TOFU at enrollment when enforced; native enforcement in steady state. |
| PIN-2 | Pin mismatch on normal traffic is **fail-closed** when mode is `ENFORCED`. |
| PIN-3 | Narrow Auth API recovery path (unpinned) + enrollment proof + integration-signed offer/accept. |
| PIN-4 | **Superseded.** No end-user certificate confirmation. |
| PIN-5 | Cry-wolf avoided by quiet `MATCH` rebind, not by asking users to rubber-stamp. |
| PIN-6 | **MATCH-only rebind** via edge probe. `MISMATCH` / `UNAVAILABLE` → blocked + retry + audit. |
| PIN-7 | Audit every propose / accept / block (corroboration status + trust-zone identity). Heuristics later. |
| PIN-8 | Android-first; iOS after Android validation. |
| PIN-9 | Trust zone may be `ENFORCED` or `DISABLED`; debug builds do not enforce; no per-enrollment toggle. |
| PIN-10 | Per-installation mobile metadata owns mode, pinned SPKI, blocked state. |
| PIN-11 | Mode on public GET (hint) + signed instance-info (authoritative); verify response signatures. |
| PIN-12 | Pinning blocked/recovery signals: **Global Admin only** (not Tenant Admin) in R1. |

## Scope

- **In scope:**
  - Implement design-pack protocol, payloads, Android enforcement, mode discovery, audit events.
  - Extend enrolled instance-info signed payload with `spkiPinningMode`.
  - Global Admin audit visibility for recovery/blocked events.
  - Honesty docs aligned with MOB-010 / security assessments (TOFU residual).
- **Out of scope:**
  - Passkey/FIDO2/WebAuthn equivalence or attestation-chain assurance.
  - Maximal certificate-lifecycle ceremony.
  - End-user or per-enrollment pinning toggles.
  - Tenant Admin pinning empowerment; manual force-trust SPKI in R1.
  - iOS parity as a gate before Android validation.
  - Claiming MATCH rebind is MITM-proof.

## Strategic guardrails

1. **Line in the sand on complexity:** do not reproduce passkey formal assurance layers.
2. **Honest posture:** document best-effort vs proven vs client-reported; unsigned GET ≠ authority.
3. **Security proportionnée:** practical gain for self-hosted backend-first adopters.
4. **Adopter agency:** a trust zone may not support pinning; claims must match mode.
5. **End-user non-responsibility:** never ask the MFA user to authenticate certificates.

## Candidate paths retained

1. **Ezkey default (design pack):** SPKI + TOFU + MATCH-only signed recovery + zone policy.
2. **Higher-assurance optional path:** dedicated mobile hostname / tighter cert lifecycle where
   the operator environment supports fewer unexpected rotations.

## Design questions still open (narrow)

Most protocol questions are closed by the design pack. Remaining:

1. Config surface for `spkiPinningMode` (`ezkey.*` vs Admin UI vs both).
2. Long-lived `DISABLED` product framing (lab vs durable adopter choice).
3. Whether R1 ships audit-only or also a dedicated `AlertType` (pack prefers audit-only).
4. First `TB-*` bounding and Android native pin store details.

## Risks and exceptions

- Over-complicating corroboration erases middle-path value.
- Forcing `ENFORCED` where Auth API cannot probe its public URL bricks users — use `DISABLED`.
- Overclaiming pinning or StrongBox creates trust debt.
- Treating unsigned public GET mode as a security control — reject.
- Reintroducing end-user cert confirmation — reject (superseded).

## Promotion notes

Keep `incubating` until a `TB-*` is opened from the design pack. Promote toward `ready` /
`active` when TB scope is bounded (Android + Auth API recovery + mode fields + audit).

## Links

- Vision:
  [`V-2026-0006-mobile-certificate-pinning`](../../vision/V-2026-0006-mobile-certificate-pinning.md)
- Design pack:
  [`../../mobile-certificate-pinning-design-pack.md`](../../mobile-certificate-pinning-design-pack.md)
- Payload canon:
  [`../../../../docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md`](../../../../docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md)
- Retrofit foundation:
  [`R-2026-0001-mobile-certificate-pinning-spki`](../../legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md)
- Adjacent response-integrity track (branding / instance-info, complementary to transport pinning):
  [I-2026-08-09-mobile-signed-instance-info-integrity.md](I-2026-08-09-mobile-signed-instance-info-integrity.md)
- Security claim boundary:
  [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md)
- StrongBox trust-model boundary:
  [`docs/MOBILE_DEVELOPER_GUIDE.md`](../../../../docs/MOBILE_DEVELOPER_GUIDE.md)
- Mobile crypto wording guardrails:
  [`ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`](../../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md)
