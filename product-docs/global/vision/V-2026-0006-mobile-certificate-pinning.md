# V-2026-0006 — Mobile certificate pinning posture: Ezkey middle path

- **Date:** `2026-05-08`
- **Updated:** `2026-08-10`
- **Status:** `under-review`
- **Captured by:** Marc

## Intent

Define an Ezkey-specific certificate pinning posture that is stronger than platform TLS-only traffic
while staying below the complexity and assurance ceremony of passkey/FIDO-grade ecosystems.

Target direction:

- SPKI pinning (`sha256(SPKI DER)`) at **trust-zone / installation** scope,
- TOFU bootstrap at enrollment when the zone enforces pinning,
- native mobile enforcement in steady state (**fail-closed** on pin mismatch),
- narrow Auth API recovery path with device proof + integration-signed responses,
- **MATCH-only** quiet rebind via backend edge corroboration (no end-user certificate confirm),
- blocked / retry when corroboration fails (Global Admin signal),
- trust-zone policy to **support or not support** pinning (`ENFORCED` / `DISABLED`),
- Android-first rollout,
- operator-visible **trust-pattern signals** (not absolute MITM-proof pinning theater).

**Design pack (normative detail):**
[`../mobile-certificate-pinning-design-pack.md`](../mobile-certificate-pinning-design-pack.md)

## Strategic positioning (product line in the sand)

Ezkey keeps an explicit complexity boundary:

- We do not attempt to replicate the full formal assurance stack found around passkeys/WebAuthn.
- We do not claim server-verified StrongBox attestation or equivalent certification-grade proof.
- We do use StrongBox when available and surface the signal in backend data as client-reported
  metadata.

This is intentional and opinionated: Ezkey is a proportionate middle path for backend-first teams.
It aims to be stronger than passwords and classic TOTP/SMS flows for relevant contexts, without
claiming top-tier formal assurance equivalence.

Adopters retain the final risk decision for their organization: if they require stronger formal
guarantees, they should choose that higher-assurance route.

### Analogy: StrongBox signal vs formal attestation

In WebAuthn-class ecosystems, StrongBox (or equivalent) participation can be bound into a strict
attestation ceremony with a trust authority. Ezkey deliberately does **not** pursue that parity.
Using StrongBox when available and recording a client-reported signal is the same kind of
opinionated choice as this pinning posture: extract useful practical value from a technology
without carrying the full formal ceremony and complexity that Ezkey, as a platform, does not
claim to own.

### Ezkey twist: pinning as trust-pattern signal

Classical hard pinning aims to block unexpected certificates at any cost and often assumes
operator-controlled certificate lifecycle. Ezkey's operational context (e.g. Cloudflare-fronted
Auth API, rotations without pre-announcement, often a single live edge certificate) makes pure
static pinning a poor fit.

The Ezkey twist is therefore not "maximal pinning." It is:

1. **Steady-state integrity** — after TOFU, the mobile client refuses traffic that does not match
   the stored SPKI pin (fail-closed), when the trust zone enforces pinning.
2. **Controlled recovery** — a narrow, Ezkey-authenticated recovery path rebinds trust **only**
   when the backend edge probe corroborates the mobile-proposed SPKI (`MATCH`).
3. **Signal / non-conformity** — recovery attempts, accepts, and blocks are recorded for Global
   Admin visibility. Richer pattern heuristics incubate later under the backlog idea.

Pinning here is partly **repurposed**: a pragmatic generator of observability around trust
transitions, not a promise of absolute certificate immutability.

### Honesty boundaries (multi-install market)

- **Transport ≠ tenant isolation.** The pin is the Auth API **edge** identity for a trust zone.
  Tenants and integrations behind one hostname share pin fate.
- **One Ezkey Mobile app, many trust zones.** Distinct Auth URLs are independent installations;
  each carries its own pinning metadata (mode, stored SPKI, blocked state). Zones do not couple.
- **TOFU enrollment residual.** Wrong or hostile Auth URL at enrollment still gets a pin for that
  zone (trust-on-issuer). Pinning does not fix enrollment phishing — align with existing mobile
  security scope (e.g. MOB-010); do not overclaim.
- **Unsigned public instance-info** may advertise pinning mode as a hint; **signed** enrolled
  instance-info is authoritative after enrollment (response signature verified; never TLS-only).
- **Mobile version skew.** Builds without pinning code do not enforce pins even if the server
  says `ENFORCED`.

## Settled decisions

### 2026-05-19 grill (retained)

SPKI pin; TOFU at enrollment; native enforcement; Auth API recovery + device proof; Android-first;
compliance batch later. Historical grill required user confirmation before pin replacement —
**superseded** below.

### 2026-08-09 / 2026-08-10 (current)

| ID | Decision |
|----|----------|
| V6-1 | **Pin mismatch is fail-closed** on normal Auth API traffic when pinning is enforced. |
| V6-2 | **Superseded.** End users must **never** confirm or authenticate certificates. |
| V6-3 | **Cry-wolf is first-class.** Expected edge rotations must not train fear or rubber-stamping. |
| V6-4 | **MATCH-only rebind.** Quiet rebind only when backend edge probe SPKI equals mobile proposal. `MISMATCH` / `UNAVAILABLE` → blocked + retry + Global Admin audit signal. |
| V6-5 | **Signal layer** in scope (audit R1; richer heuristics later). |
| V6-6 | **Trust-zone policy.** A zone may not support pinning (`DISABLED`) or enforce it (`ENFORCED`). Not globally mandatory. Debug builds: enforcement off. |
| V6-7 | **Global Admin** sees pinning blocked/recovery signals; **not Tenant Admin** in R1 (IT vs business-unit DevOps analogy). |

### Fail-open vs fail-closed (design principle #17)

| Boundary | Posture | Meaning |
|----------|---------|---------|
| Steady-state HTTP (pinned client) | **Fail-closed** | Pin mismatch stops the primary path. |
| Recovery endpoint | **Narrow exception** | Unpinned path + enrollment crypto only. |
| Pin replacement | **Fail-closed until MATCH** | No user override; no accept without corroboration. |
| Probe unavailable | **Fail-closed (blocked)** | Never silent auto-rebind. |
| Mode `DISABLED` / debug | Feature off | Platform TLS; honest claims. |

## Backend-corroborated legitimate rotation

On recovery propose: mobile sends observed SPKI; Auth API probes its public edge; **MATCH** →
signed offer with `rebindAllowed=true` → quiet UX → device-signed accept → pin replaced.
**MISMATCH** / **UNAVAILABLE** → blocked UX (retry / contact administrator); audit
`SPKI_RECOVERY_BLOCKED`.

This is not empty theater: a hostile cert shown only to the phone fails corroboration against the
honest edge. Residual risks (probe trustworthiness, edge skew, same-hostname attacker) are named
in the design pack and accepted for the middle path.

## Complexity budget

| In budget (essential) | Out of budget for early slices |
|-----------------------|--------------------------------|
| SPKI pin, TOFU, native enforcement | Full enterprise cert-lifecycle ceremony |
| MATCH-only recovery + edge probe | Passkey/FIDO/attestation-chain parity |
| Trust-zone mode + per-installation metadata | End-user or per-enrollment toggles |
| Audit events (Global Admin) | Nightly compliance batch / fancy anomaly ML as R1 |
| Quiet MATCH / blocked retry UX | iOS before Android validation; manual force-trust SPKI |

## Why this remains under review

Strategic posture is stable; implementation awaits a tracer bullet. Open items that remain
deliberately light:

1. Exact config surface for `spkiPinningMode` (property vs Admin UI vs both).
2. Long-lived `DISABLED` framing (lab convenience vs durable adopter choice).
3. Operator signal heuristics beyond R1 audit.
4. iOS timing after Android validation.

Protocol, payloads, UX, and Admin visibility are specified in the design pack.

## Non-goals and non-claims

- Not passkey/FIDO2 compatibility.
- Not full PKI or complete attestation-chain equivalence.
- Not a claim that StrongBox tier is cryptographically proven server-side today.
- Not absolute / classical hard pinning as the default story.
- Not a claim that recovery is MITM-proof.
- Not end-user certificate authentication.
- Not maximal-security ceremony at any complexity cost.

## Canon trajectory

- Retrofit: `R-2026-0001`
- Backlog idea: `I-2026-07-25-mobile-certificate-pinning-middle-path`
- **Design pack:** `product-docs/global/mobile-certificate-pinning-design-pack.md`
- **Payloads:** `docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md`

## Related artifacts

- `R-2026-0001` — Mobile certificate pinning retrofit
- `I-2026-07-25-mobile-certificate-pinning-middle-path`
- Design pack (above)
- `docs/SPKI_RECOVERY_SIGNATURE_PAYLOAD.md`
- `docs/MOBILE_DEVELOPER_GUIDE.md` (StrongBox trust-model boundary)
- `docs/SECURITY_POSTURE.md` (honest claims and non-claims)
- Grill: `product-docs/global/backlog/grill-sessions/blitz-2026-05-08-2-D2-D11-retrofit-grill-me.md`
