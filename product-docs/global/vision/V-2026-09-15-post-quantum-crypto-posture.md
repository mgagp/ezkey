# Vision Note — `V-2026-09-15-post-quantum-crypto-posture` Post-quantum posture of the Ezkey protocol

## Metadata

- **ID:** `V-2026-09-15-post-quantum-crypto-posture`
- **Status:** `promoted`
- **Created at:** `2026-09-15`
- **Updated at:** `2026-11-03`
- **Captured by:** Marc

## Intent

This note maps how Ezkey's application protocol stands against a future cryptographically
relevant quantum computer (CRQC). The protocol here covers:

- enrollment bind/verify;
- authentication pending/respond;
- proof tokens;
- device and integration signatures;
- recovery codes;
- Integration API decisions.

The map is honest and verified against code. It separates what is already adequate, what is
pre-quantum, and which evolutions would close the gaps. It also records **when** each evolution
becomes worth doing.

It is a **medium-term watch item**, not a delivery commitment. Nothing here changes wire formats.
The honest-claims canon lives in [`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md);
this note explains it.

## Motivation

- The protocol rides on TLS 1.3. That is real security, but it is not the same as end-to-end
  cryptographic continuity of enrollment and authentication.
- A 2026-07-05 operator session reached two conclusions:
  - proof tokens are strong for their lifetime;
  - device EC P-256 and integration Ed25519 are pre-quantum.

  That session was never written down. Only the integration-key cycling slice was captured
  ([`V-2026-07-05`](V-2026-07-05-enrollment-integration-key-cycling-posture.md)).
- The first version of this note (2026-09-15, #538) mapped the layers well. A 2026-11-03 review
  against current `main` corrected it on several points:
  - It applied "harvest now, decrypt later" to signatures.
  - It treated protocol public keys as globally public.
  - It planned to "enable hybrid TLS" when it is probably already on, but unverified.
  - It proposed Grover-motivated key lengthening that has no practical justification.
  - It assumed device-side post-quantum signing was far away.

  This version records the corrected map.
- **Honest claims landed first.** #1158 (2026-11-03) removed two "resistant to quantum attacks"
  overclaims on recovery codes and stated the limit in `SECURITY_POSTURE.md`.

## Context that shapes the decisions

| Fact (2026-11-03) | Consequence |
| --- | --- |
| **No adopters.** Local work runs on clean starts. The community instance is the only public install: an alpha with no SLA, where a clean start is acceptable. | No enrollment needs migrating. An algorithm change is a **flag day**: clean start + re-enrollment + one mobile release. |
| A protocol change is cheapest **before** the first adopter (no backward compatibility, no capability negotiation). | Algorithm-agility hooks are worth laying down **now**. The algorithms themselves can wait. |
| Device post-quantum signing is arriving in platforms: iOS 26 CryptoKit and Secure Enclave, Android 17 (API 37) Keystore. Ezkey mobile targets iOS **15.5** and Android **minSdk 31**. | Any post-quantum slice that needs those platforms is **gated by OS adoption**. Raising minimum OS versions would be premature today. |
| NIST IR 8547 (final 2026-10-27) deprecates quantum-vulnerable signatures (ECDSA, EdDSA) **after 2030** and disallows them **after 2035**. The Canadian roadmap targets high-priority systems by end of 2031 and all by end of 2035. | There is a dated external horizon. "P3, later" now has wake-up conditions. |

## Inventory (verified on `main`, 2026-11-03)

| Element | Mechanism (code) | Post-quantum status |
| --- | --- | --- |
| Proof tokens (enrollment, auth attempt, device) | 32 random bytes + 16 random "salt" bytes, Base64URL (`SignatureService.generateProofToken`) | Symmetric secret, ≥ 256 bits: adequate |
| At-rest field encryption | Tink `AES256_GCM` (optional `CHACHA20_POLY1305`) | Adequate |
| Audit chain, CSRF tokens | `HmacSHA256` (`AuditHmacService`, `AdminCsrfTokenService`) | Adequate |
| Admin bearer tokens | Opaque, SHA-256 hash-only lookup | Adequate |
| Recovery codes | 32 digits ≈ 106 bits, BCrypt, single-use (`AdminRecoveryService`) | Classical hardening. Grover gives no practical attack. Never a post-quantum claim. |
| Integration API secret keys | `ezkey_skey_` + 40 hex = 160 bits, BCrypt (`ApiKeyService`) | Adequate in practice (see Decisions: no Grover-driven lengthening) |
| **Device signature** | EC P-256, `SHA256withECDSA`, Android Keystore / StrongBox, generated once per enrollment | **Pre-quantum (Shor)** |
| **Integration signature** | Ed25519, server-held, Tink-encrypted private key, generated once per enrollment | **Pre-quantum (Shor)** |
| Algorithm identifiers on the wire | Integration: `integrationKeyAlgorithm` (`ed25519`), in the signed bind line, mobile fail-closed. Device: **none** (the server assumes P-256). | Asymmetric. Fixed by #1165 |
| No other asymmetric primitive | No JWT, no RSA in product code | — |

## Threat framing (corrected)

### Two different risks, not one

- **Confidentiality ("harvest now, decrypt later").** An adversary records TLS today and breaks its
  classical key exchange later. They then read bodies: enrollment proof tokens (sent on every
  pending poll), API keys, freshly issued recovery codes, public keys. This only matters for
  secrets **still valid** when the break happens. The defence lives at the **TLS layer** (hybrid
  ML-KEM key exchange).
- **Authenticity ("forge when a CRQC exists").** A CRQC derives a private key from its public key.
  Recording a signature today gains nothing. The risk is that a key **is still trusted** on the day
  a CRQC exists.
  - **Mosca's rule:** key shelf life + migration time must stay below the time to a CRQC.
  - With clean starts and no adopters, shelf life is close to zero today. The real exposure is a
    **migration debt with a horizon**, not a present weakness.

### Protocol public keys are not public in the PKI sense

They only travel inside TLS and live in the database and on the device:

| Key | Where it travels or rests |
| --- | --- |
| Device public key | `POST /enrollments/verify` request; stored on `ezkey_enrollment` (with `device_public_key_hash`); **returned by the Admin API enrollment DTO and shown in Admin UI / CLI TUI** |
| Integration public key | `POST /enrollments/bind` response; device sealed storage; **returned by the Admin API enrollment DTO and shown in Admin UI / CLI TUI** |

Consequences:

- With hybrid TLS on every hop, a network adversary cannot harvest these keys. The remaining
  post-CRQC sources are database access and the Admin API display.
- The Admin API display plays **no protocol role**. The server verifies device signatures with
  `enrollment.getDevicePublicKey()` from the entity (`AuthAttemptPendingService`,
  `AuthAttemptRespondService`). Every protocol actor gets the integration key from the bind
  response and generates the device key locally:
  - mobile;
  - Demo Device;
  - Dart SDK;
  - CLI device mode;
  - `bootstrap-init.sh`;
  - `protocol_lab`.

  This was verified against the published technical trace
  ([`technical-trace-ezkey-enrollment-authentication.html`](../../../sites/ezkey-org/technical-trace-ezkey-enrollment-authentication.html)).
- **Decision (Marc, 2026-11-03):** the Admin UI shows only what plays a real role in operating
  Ezkey. Crypto material without an operator use is removed, after characterization. See Path 2.
- **Related classical observation.** The same Admin GET returns `enrollmentProofToken` in clear
  (the QR channel for `CREATED`). Whether it is still returned after `VERIFIED` is to be
  characterized under Path 2. The hash-only direction is already tracked by
  [`I-2026-0032`](../backlog/ideas/I-2026-0032-proof-token-hash-only-storage.md) Tier 1.

### Attack angles once a CRQC exists

- **A — Forge the device (false `APPROVED`).**
  1. Obtain the device public key (DB or Admin API).
  2. Derive the private key.
  3. Sign `{proofToken}|true`.

  The integrating application (PIA) is told a lie that verifies. No extra Integration API crypto
  can save it; strengthening the PIA chain means strengthening device identity, proof tokens and
  TLS.
- **B — Forge the integration (lie to the phone).** Forge the pending context or the respond
  result. The human-in-the-loop guarantee breaks, but the PIA is not approved by this alone.
- **C — Replay long-lived secrets from recorded TLS.** Applies to the enrollment proof token and
  unused recovery codes. Short-lived auth-attempt proof tokens will have expired.

## TLS layer: verify, do not assume

- Hybrid key exchange (X25519MLKEM768) is negotiated only if **both** ends support it.
- On the community host, client-facing TLS is **Cloudflare** (proxied DNS). Caddy terminates the
  Cloudflare → origin hop with an Origin CA certificate, TLS 1.3 only, and **no `curves`
  override**.
- Caddy ≥ 2.10 (Go ≥ 1.24) enables X25519MLKEM768 by default. The image is the floating tag
  `caddy:2-alpine`.
  - The major-upgrade registry tracks the support line `2` (2.11.7 at the 2026-10-19 review). It
    does not track the capability.
  - Dependabot cannot move a floating tag.
  - The community deploy ledger does not record the running Caddy version.
- **Clients decide too:**
  - Android app (OkHttp/Conscrypt, minSdk 31);
  - iOS app;
  - PIA and SDK JVM clients;
  - the Cloudflare → origin hop.
- **Status (probe 2026-11-03, #1174):**
  - **Edge:** X25519MLKEM768 negotiated at the Cloudflare edge on all four public hostnames.
  - **Origin:** the Caddy origin (v2.11.4) accepts it.
  - **Negative control:** offering X25519 alone falls back to X25519.
  - **Cloudflare → origin:** Cloudflare's choice on that hop is documented behaviour (Automatic
    Key Exchange), not directly proven.
  - Record: [`docs/lightsail/community/DEPLOYED.md`](../../../docs/lightsail/community/DEPLOYED.md)
    § Transport TLS. Re-probe: `scripts/lightsail/tls-pq-probe.sh`.
- **Gap found by the probe session:** the operator SSH channel to the host (deploys, DB tunnel)
  negotiates classical `curve25519-sha256`. Tracked in #1177.

## Decisions

| Item | Decision | Why |
| --- | --- | --- |
| Rotate device or integration keys per authentication as a post-quantum strategy | **Rejected** | TLS rotates ephemeral key-exchange secrets, not certificates. Per-auth rotation does not protect the attempt being recorded. It also costs Keystore churn and the 2026-07-05 grill D2/D6 thrashing. |
| Application-layer ML-KEM wrapping of pending/respond | **Rejected** | It duplicates TLS and adds large accidental complexity. Revisit only if Ezkey must run over a transport it cannot make hybrid. |
| Lengthen API keys (160 → 256 bits) or recovery codes "because of Grover" | **Removed from this program** | Grover parallelizes poorly, and each iteration would be a BCrypt evaluation. NIST does not ask to double AES-128. Lengthening is ordinary hygiene if ever wanted, not post-quantum work. |
| Integration key cycling ([`I-2026-07-05`](../backlog/ideas/I-2026-07-05-enrollment-integration-key-cycling.md)) | **Decoupled from post-quantum** | Its value is recovery from classical key compromise. It must not be marketed as post-quantum. |
| In-protocol enrollment re-key ceremony (old key signs new hybrid key) | **Deferred, documentation only** | It only matters for long-lived enrollments. **Trigger:** first adopter with production enrollments. If needed, it must run **before** a CRQC exists, while the old signature still means something. |
| Device algorithm identifier on the wire | **Accepted, separate issue: #1165** | It is cheapest before the first adopter, and it unblocks any future device algorithm. |
| Hybrid signatures (Ed25519/P-256 + ML-DSA) | **Medium-term lab slice, gated by OS adoption** | See Path 4. The code is feasible today. Mobile minimum OS versions make it premature. |

## Evolution paths and triggers

| Path | What | Status / trigger |
| --- | --- | --- |
| **0 — Honest claims** | `SECURITY_POSTURE.md`, recovery-code wording, `CRYPTO.md` pointer | **Done** (#1158) |
| **1 — TLS probe** | `scripts/lightsail/tls-pq-probe.sh`: X25519MLKEM768 vs X25519 control at the edge and on the origin, plus Caddy version/digest. Results go to the community deploy ledger; the Caddy registry entry carries a capability note. The local proxy stack is plain HTTP (no TLS hop). | **Done** (#1174, 2026-11-03). Re-probe after Caddy image refreshes. |
| **2 — Admin crypto exposure** | Characterize what the Admin API enrollment DTO returns at each lifecycle state. Remove crypto material with no operator use (device and integration public keys, and other candidates found by the characterization). Full contract cycle: DTO, OpenAPI, Orval, Admin UI, CLI TUI, Bruno, i18n, community redeploy. | Follow-up issue, to be opened |
| **3 — Device algorithm identifier** | `deviceKeyAlgorithm` in the signed verify-device line, persisted, fail-closed dispatch | #1165 (`status:needs-challenge`) |
| **4 — Hybrid signatures** | **Design both sides, implement integration first.** Integration: Ed25519 **and** ML-DSA over the same canonical lines; both must verify; `integrationKeyAlgorithm` becomes e.g. `ed25519+ml-dsa-65`. Device later: hardware P-256 **and** ML-DSA (Keystore / Secure Enclave where available, explicit storage tier otherwise). One flag day: clean start + one mobile release. | **Medium term.** Watch triggers below. Needs a design pack and a written security-owner opinion on mobile crypto libraries. |
| **5 — Re-key ceremony** | See Decisions | Documentation only until the first adopter |

### Path 4 feasibility (facts gathered 2026-11-03)

| Surface | Means | State |
| --- | --- | --- |
| Auth API signing, Crypto API, Demo Device | JDK 25 native ML-DSA (JDK 24+, JEP 497) | Ready. No new dependency, consistent with ADR-0006 "JDK-native". Keys fit existing `TEXT` columns and Tink encryption. |
| iOS verify / sign | CryptoKit `MLDSA65`, Secure Enclave support | iOS **26+** only. App target is 15.5, so a fallback library or a minimum-OS raise is needed. |
| Android verify (minSdk 31) | No platform verifier below API 37. Candidates: Bouncy Castle, a JS library in React Native, a native library. | **Most sensitive choice.** No independent audit of `@noble/post-quantum` was found. |
| Android device signing | Keystore `KEY_ALGORITHM_ML_DSA_65` | API **37** (Android 17)+ only; hardware-backed availability per device unconfirmed |
| Dart SDK | `pqcrypto` (pure Dart, official KAT vectors pass, no FIPS validation) or `mldsa_native` (FFI) | Lab-grade feasible |
| Python CLI | pyca `cryptography` 48+ (OpenSSL 3.5+ or AWS-LC) | Ready |
| Payload size (ML-DSA-65) | Public key 1952 B; signature 3309 B (~4.4 KB Base64URL) | About +4.4 KB per signed response. Negligible over HTTPS. |

**Honest value of the integration-first slice.** It protects what the phone **displays** (attack
B). The `APPROVED` the PIA receives still rests on the device P-256 signature (attack A).

After that slice, the honest claim would be: *"integration-to-device messages are hybrid-signed;
device approvals remain P-256."*

The immediate security value is low. The value is lab learning, proven algorithm agility, and a
true partial claim.

### Watch list (close follow-up)

Re-check these at each quarterly major-upgrade review (see
[`hygiene/major-upgrades/README.md`](../hygiene/major-upgrades/README.md) § Quarterly review),
and whenever a mobile minimum-OS change is discussed.

1. Ezkey mobile minimum OS versus iOS 26 and Android API 37, and the share of active testers
   above each.
2. Android ML-DSA verification library options, and any independent audit of a JS candidate.
3. Hybrid TLS status: re-run `scripts/lightsail/tls-pq-probe.sh` after Caddy image refreshes or
   Cloudflare TLS changes.
4. External horizon: NIST IR 8547 (deprecated after 2030), Canadian ITSM.40.001 (2031 / 2035).
5. First-adopter discussions. Each one re-opens Path 5 and the agility hooks (#1165,
   [`I-2026-0025`](../backlog/ideas/I-2026-0025-auth-api-protocol-capability-versioning.md)).

**Wake-up rule for Path 4.** Move from watch to design pack when any of these is true:

- minimum OS versions can reach iOS 26 / API 37 for the device side;
- an acceptable Android verifier exists for the integration side;
- a first regulated adopter asks;
- the 2028 review arrives.

## Testability (claims → checks)

| Claim | Check |
| --- | --- |
| No post-quantum overclaim in product code or docs | Repo grep for `quantum-resistant` / `resistant to quantum` outside historical notes (manual today; cheap CI candidate) |
| Symmetric pieces are sized as stated | Characterization tests on algorithm constants and proof-token length (`SignatureService`, Tink templates, HMAC) |
| Hybrid TLS status | Path 1 probe output, dated in the community deploy ledger |
| Unknown device algorithm fails closed | #1165 acceptance tests |
| Admin API exposes only operator-useful crypto | Path 2 characterization tests before and after the contract change |

## Promotion criteria

- Paths 1–3 are tracked as GitHub issues (#1174 probe; #1165 device algorithm; the Admin
  exposure issue to open).
- Path 4 is promoted to a design pack (`TB-*` or ADR) only when the wake-up rule fires. No
  `TB-*` is opened "for post-quantum" without it.
- Parent idea
  [`I-2026-09-15-post-quantum-protocol-evolution`](../backlog/ideas/I-2026-09-15-post-quantum-protocol-evolution.md)
  carries the watch status.

## Related documents

- Honest claims: [`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md);
  algorithms: [`docs/CRYPTO.md`](../../../docs/CRYPTO.md);
  [ADR-0006](../architecture-decisions.md#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519)
- Payloads: [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md),
  [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
- Window management: [`V-2026-07-05`](V-2026-07-05-enrollment-integration-key-cycling-posture.md),
  [`I-2026-07-05`](../backlog/ideas/I-2026-07-05-enrollment-integration-key-cycling.md),
  [grill 2026-07-05](../backlog/grill-sessions/2026-07-05-enrollment-integration-key-cycling-grill-me.md)
- Agility: [`V-2026-0008`](V-2026-0008-auth-api-protocol-versioning.md),
  [`I-2026-0025`](../backlog/ideas/I-2026-0025-auth-api-protocol-capability-versioning.md), #1165
- Proof-token storage: [`I-2026-0032`](../backlog/ideas/I-2026-0032-proof-token-hash-only-storage.md)
- Transport pinning (integrity, not post-quantum): [`V-2026-0006`](V-2026-0006-mobile-certificate-pinning.md)
- Mobile protocol assessment (classical): [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)
- Community host topology: [`docs/lightsail/community-host.md`](../../../docs/lightsail/community-host.md)
- Lifecycle anchor: [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)
- External: [NIST IR 8547 (final 2026-10-27)](https://csrc.nist.gov/News/2026/transition-to-pqc-standards-nist-ir-8547),
  [Canadian Centre for Cyber Security ITSM.40.001](https://www.cyber.gc.ca/en/guidance/roadmap-migration-post-quantum-cryptography-government-canada-itsm40001),
  [Apple CryptoKit `MLDSA65`](https://developer.apple.com/documentation/cryptokit/mldsa65),
  [Android API 37 `KeyProperties`](https://developer.android.com/sdk/api_diff/37/changes/android.security.keystore.KeyProperties)
