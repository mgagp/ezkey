# Backlog Idea — `I-2026-08-09-mobile-signed-instance-info-integrity` Mobile: signed Public Instance Info integrity

## Metadata

- **ID:** `I-2026-08-09-mobile-signed-instance-info-integrity`
- **Status:** `active`
- **Priority:** `P2`
- **Created at:** `2026-08-09`
- **Updated at:** `2026-08-09`
- **Last reviewed at:** `2026-08-09`
- **Component tags:** `mobile`, `auth-api`, `core`, `docs`, `security`
- **Captured by:** Marc
- **GitHub issue:** none
- **Tracer bullet:** [`TB-2026-08-09-mobile-signed-instance-info`](../TB-2026-08-09-mobile-signed-instance-info.md)

## Intent

Give enrolled mobile clients an Auth API path to obtain installation branding
(`instanceName`, `instanceDescription`, `aboutUrl`, and related display fields) with
**cryptographic response integrity** that dogfoods existing Ezkey enrollment proof tokens and
integration Ed25519 signing — so a MITM (or other TLS-path attacker) cannot silently rewrite
server-advertised labels used for social engineering — while keeping the existing unauthenticated
`GET /api/v1/public/instance-info` for public/Admin consumers.

## Problem and value

- **Problem:** Today `GET /api/v1/public/instance-info` returns unsigned JSON over platform TLS
  only (no certificate pinning yet). Mobile fetches it after successful enrollment verify and
  opportunistically on Home when metadata is stale (>24h) or incomplete, then **fail-open**
  applies branding with no user confirmation on change. Trust-zone identity is correctly the
  normalized Auth URL, and code comments already treat branding as presentation-only, but the UI
  does not surface that honesty boundary. An attacker who can MITM the response can forge
  `instanceName` / `instanceDescription` (and later `aboutUrl` if surfaced as a link) to build a
  phishing narrative — for example urging the user to delete an enrollment and re-enroll
  elsewhere. There is no dedicated support-email field today; free-text description already
  suffices. This does **not** break bind/verify/pending/respond crypto; it is a UI trust /
  social-engineering channel.
- **Expected value:** Close the integrity gap on the enrolled refresh path with proportional
  complexity (reuse protocol keys and exchange patterns), plus cheap complementary UX controls
  (deferred past the first TB), without inventing a new instance-level signing ceremony in v1.

## Scope

- **In scope (program):**
  - Threat model for unsigned public instance metadata as a social-engineering surface (MITM +
    induced user action), distinct from protocol signature bypass.
  - Auth API enrolled refresh with integration-signed branding response (see Settled decisions).
  - Mobile verify-before-apply on wizard post-verify and Home refresh; no unsigned GET fallback on
    the enrolled path.
  - Complementary UX (follow-up after first TB): user-visible honesty that labels are
    server-advertised; material branding-change confirmation before replacing previously shown
    labels.
  - Gate: do not add support-contact / “contact to recover” fields to the **unsigned** GET without
    an integrity story.
- **Out of scope:**
  - Replacing or removing public unsigned `GET /api/v1/public/instance-info`.
  - A new instance-level / org CA signing key in v1.
  - Claiming that pinning alone attests branding content, or that signed branding replaces pinning.
  - Expanding contact/support fields on the unsigned public GET.
  - Device-proof request authentication for instance-info (rejected for v1).

## Current invocation model (facts)

| Moment | Behavior today |
| --- | --- |
| After successful verify (wizard) | Best-effort unsigned `instanceInfoApi.get(authUrl)` then `buildInstallation`; on failure keep host-only installation ([`useEnrollmentWizard.ts`](../../../../ezkey_mobile/app/hooks/useEnrollmentWizard.ts)) |
| Home load | One unsigned call per stale/incomplete trust zone; deduped by installation id ([`useRefreshInstallationMetadata`](../../../../ezkey_mobile/app/hooks/useEnrollments.ts), [`installationMetadata.ts`](../../../../ezkey_mobile/app/utils/installationMetadata.ts)) |
| Skipped | No enrollments; fresh complete metadata; missing resolvable Auth URL |

Post-verify fetch already runs **after** the mobile holds `integrationPublicKey` from bind — so a
signed branding response on the enrolled path does not require a new chicken-egg key.

## Settled decisions (grill 2026-08-09)

### D1 — Endpoint + request proof

| Element | Decision |
| --- | --- |
| Method/path | `POST /api/v1/enrollments/instance-info` (Auth API only) |
| Request body | `{ "enrollmentProofToken": "..." }` only (hash lookup like bind) |
| Public GET | Unchanged `GET /api/v1/public/instance-info` |
| Device proof | Out of v1 |

### D2 — Canonical payload, fail-closed, TB split

**Canonical payload to sign (integration Ed25519):**

```text
{enrollmentProofToken}|{enrollmentId}|INSTANCE_INFO|{authApiPublicBaseUrl}|{instanceName}|{instanceDescription}|{aboutUrl}
```

- Separator `|`; UTF-8; null → `""`
- NFC on `instanceName`, `instanceDescription`, `aboutUrl`
- `enrollmentProofToken`, `enrollmentId` (decimal), literal `INSTANCE_INFO`, `authApiPublicBaseUrl`
  — not NFC’d
- Response: branding fields + `enrollmentId` + `instanceInfoPayloadSignedByIntegration`
  (Base64URL Ed25519)
- Client reconstructs from JSON and verifies with stored `integrationPublicKey`

**Boundaries (design-principles §17):**

| Boundary | Posture |
| --- | --- |
| Network / HTTP failure | Fail-open — keep last good branding (or host-only if none) |
| Signature verify failure | Fail-closed on apply — do not replace; keep last good; observable |
| Enrolled path after TB lands | Never fall back to unsigned GET for wizard post-verify or Home refresh |

**Slice split:**

- **First TB** ([`TB-2026-08-09-mobile-signed-instance-info`](../TB-2026-08-09-mobile-signed-instance-info.md)):
  Auth API POST + sign, docs, mobile wire-up, tests.
- **Follow-up (same idea):** honesty copy + branding-change confirmation UI.

## Rejected / deferred alternatives

| Alternative | Why not default |
| --- | --- |
| GET + `X-Ezkey-*` proof header | Fights Auth API “crypto in body → POST” norm |
| Replace public GET entirely | Breaks Admin/public unauthenticated consumers |
| New instance signing key | Higher ceremony; revisit if key-cycling makes integration-key signing awkward |
| Device proof on request | Overkill for branding metadata read in v1 |
| Pinning alone | Orthogonal transport hardening; does not attest branding fields |
| UX disclaimer only | Necessary later; insufficient integrity |

## Key assumptions

- Installation identity remains the normalized Auth URL; signed branding never becomes a trust
  anchor that overrides URL identity ([`types.ts`](../../../../ezkey_mobile/app/services/api/types.ts)).
- Integration keys are per-enrollment today; using them to attest instance-wide display metadata is
  an accepted v1 trade-off until an instance key is justified.
- Certificate pinning
  ([`I-2026-07-25-mobile-certificate-pinning-middle-path`](I-2026-07-25-mobile-certificate-pinning-middle-path.md))
  is complementary, not a substitute for response integrity of branding fields.
- Version/compat fields considered on the same public metadata family
  ([`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md))
  inherit the same integrity concern if treated as more than advisory decoration.

## Risks and exceptions

- Over-scoping into a new instance PKI erases the 80/20 value of dogfooding integration keys.
- Under-specifying branding-change UX leaves a silent swap path for *validly signed* hostile or
  surprising operator copy — tracked as follow-up after the first TB.
- Signing with a rotated/cycled integration key while mobile still holds the old public key must
  align with
  [`I-2026-07-05-enrollment-integration-key-cycling`](I-2026-07-05-enrollment-integration-key-cycling.md)
  when that track lands.
- Operator-compromised or maliciously configured instance can still publish hostile branding that
  verifies correctly under its own keys — integrity proves “this enrollment’s integration key
  attested these fields,” not “the organization is benign.”

## Promotion notes

**Ready (grill closed 2026-08-09).** First execution slice is
[`TB-2026-08-09-mobile-signed-instance-info`](../TB-2026-08-09-mobile-signed-instance-info.md).
Idea remains `ready` / later `active` while that TB runs; honesty UI + branding-change confirmation
stay on this idea as a follow-up slice after the TB promotes.

## Links

- Tracer bullet:
  [`TB-2026-08-09-mobile-signed-instance-info.md`](../TB-2026-08-09-mobile-signed-instance-info.md)
- Adjacent transport hardening:
  [`I-2026-07-25-mobile-certificate-pinning-middle-path.md`](I-2026-07-25-mobile-certificate-pinning-middle-path.md),
  [`V-2026-0006-mobile-certificate-pinning`](../../vision/V-2026-0006-mobile-certificate-pinning.md)
- Adjacent metadata / advisory fields on the same family:
  [`I-2026-08-02-mobile-installation-version-and-compat-discovery.md`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
- Related key lifecycle:
  [`I-2026-07-05-enrollment-integration-key-cycling.md`](I-2026-07-05-enrollment-integration-key-cycling.md)
- Protocol crypto canon:
  [`docs/CRYPTO.md`](../../../../docs/CRYPTO.md),
  [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md),
  [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
- Current public contract:
  [`docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md) § Public instance metadata,
  [`PublicInstanceInfoResponseDto.java`](../../../../ezkey-core/src/main/java/org/ezkey/instance/dto/PublicInstanceInfoResponseDto.java)
- Mobile trust-zone / branding posture:
  [`product-docs/components/mobile/data-model-and-persistence.md`](../../../components/mobile/data-model-and-persistence.md),
  [`ezkey_mobile/app/services/api/types.ts`](../../../../ezkey_mobile/app/services/api/types.ts),
  [`ezkey_mobile/app/utils/installationMetadata.ts`](../../../../ezkey_mobile/app/utils/installationMetadata.ts)
- Fail-open/fail-closed compass:
  [`design-principles.md`](../../design-principles.md) §17
