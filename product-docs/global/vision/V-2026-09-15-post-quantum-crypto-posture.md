# Vision Note — `V-2026-09-15-post-quantum-crypto-posture` Post-quantum posture of the Ezkey protocol (analysis-design map)

## Metadata

- **ID:** `V-2026-09-15-post-quantum-crypto-posture`
- **Status:** `under-review`
- **Lane:** `A`
- **Created at:** `2026-09-15`
- **Updated at:** `2026-09-15`
- **Captured by:** Marc
- **Supersedes:** the 2026-07-05 operator post-quantum status review that was cited as “not yet a standalone canon doc” from [`V-2026-07-05-enrollment-integration-key-cycling-posture`](V-2026-07-05-enrollment-integration-key-cycling-posture.md)

## Intent

Map, honestly and without a fix-now plan, how Ezkey’s **application protocol** (enrollment bind/verify, authentication pending/respond, proof tokens, device and integration signatures, recovery codes, Integration API decisions) stands if we **presume an attack on Ezkey itself** rather than treating HTTPS as the whole story. Separate what is **already post-quantum-adequate**, what is **perfectible**, and **which protocol evolutions** would actually close the remaining gaps — using the TLS 1.3 handshake (ephemeral confidentiality vs long-lived identity) as a compass, not as a mandate to copy TLS into MFA.

This note is **orientation**. It does not promote a NIST PQC migration, does not change wire formats, and does not claim that post-quantum cryptography is an existential problem for the current adopter market.

## Motivation

Ezkey is a laboratory **and** a serious MFA product. The September 2026 operable-release audience does not need a PQC program to ship. Evaluators and future-us still need a durable answer to:

1. The protocol already rides on **HTTP over TLS**. That is real security. It is not the same as **end-to-end cryptographic continuity** of enrollment and authentication.
2. A 2026-07-05 session already concluded that **proof tokens are strong for their lifetime** and that **Ed25519 / EC P-256 remain pre-quantum**, with long-lived enrollment keys as the main harvest-now / forge-later (HNDL) concern. That review never became a standalone document; only the **integration-key cycling** slice was captured ([`V-2026-07-05`](V-2026-07-05-enrollment-integration-key-cycling-posture.md) / [`I-2026-07-05`](../backlog/ideas/I-2026-07-05-enrollment-integration-key-cycling.md)).
3. Recovery-code **length** was chosen for “paranoia” and is documented as resistant to quantum attacks in service Javadoc. That wording is **too strong**. The codes are a high-entropy bootstrap secret with operational weaknesses (printouts, bootstrap logs, TLS HNDL, stolen hashes).
4. A natural but extreme idea — **rotate the device private key after every interaction** so that even classical ciphers stay “PQ-safe” by window — needs an explicit verdict: useful as a thought experiment, **not** the recommended protocol evolution.

## Classification (hygiene vs program)

| Question | Verdict |
| --- | --- |
| Hygiene pass (`assessment-curated`, one-finding HITL, handoffs)? | **No.** This is not a code-defect lot. Existing mobile-protocol assessment and key-cycling grill stay valid. |
| Methodology program? | **Yes, as orientation.** Protocol-wide uncertainty, new honest-claims surface, multi-year algorithm agility. Lightest artifact that protects the decision: this `V-*` plus a parent `I-*` at `captured` / `P3`. |
| Execution now (`TB-*`, algorithm change, rotation API)? | **No.** Same sequencing as integration-key cycling: not on the September 2026 operability compass. |

## Scope of this campaign

**In scope (analysis):**

- Application-layer crypto of Auth API ↔ mobile / Demo Device (bind, verify, pending, respond, signed instance-info).
- Integration / PIA trust in Auth API outcomes (`APPROVED` / `DENIED` / …).
- Long-lived vs ephemeral secrets: enrollment proof token, auth-attempt proof token, device key, integration key, recovery codes, API keys, admin bearer tokens, audit HMAC, Tink at-rest keys.
- Transport TLS as a **dependency**, including harvest-now-decrypt-later of recorded HTTPS, not as “therefore the protocol is PQ.”

**Out of scope (this note):**

- Implementing ML-DSA / ML-KEM / hybrid signatures.
- Redesigning Android Keystore or waiting on vendor PQC Keystore as a delivery slice.
- Reopening closed mobile-protocol findings (MOB-001…MOB-017) unless a **new** PQ-specific signal appears.
- Marketing, certification, or “Ezkey is post-quantum” copy.

## Nested threat models

Do not collapse “someone attacks Ezkey” into one adversary. Three nested models:

| ID | Adversary | What they can do **now** | What a future CRQC adds |
| --- | --- | --- | --- |
| **T0 — network, ordinary TLS** | On-path without breaking TLS 1.3 | See metadata, not payloads | Nothing extra if records stay opaque |
| **T1 — recorded HTTPS (HNDL-transport)** | Stores TLS records today | Ciphertext only | If the TLS **key exchange** is broken (ECDHE / X25519), recover HTTP bodies: proof tokens, recovery codes, API keys, public keys, signatures |
| **T2 — protocol-visible** | Breaks or bypasses TLS (hostile CA, rooted device, insider, already-decrypted dump) **or** reads public material that the protocol **publishes by design** | Sees payloads and **public keys** | Shor on device/integration public keys → **forge** later; Grover on leftover symmetric secrets |

Ezkey’s own signatures do **not** provide confidentiality. They provide **authenticity and continuity**. A CRQC does not “decrypt Ed25519.” It **derives the private key from the public key**. Recorded signatures are optional; the **public keys are enough**. Those public keys are first-class protocol fields (bind `integrationPublicKey`, verify `devicePublicKey` stored on the server).

**Today’s honest floor:** against T0, the protocol plus TLS 1.3 is a coherent MFA chain. Against T1, **transport** PQ (hybrid KEM at the edge) matters as much as anything Ezkey invents. Against T2, **long-lived elliptic-curve identity keys** are the structural weakness.

## HTTPS handshake as compass

TLS 1.3 separates two jobs that Ezkey currently blends into “the protocol”:

| TLS job | TLS 1.3 mechanism | PQ direction in the industry | Ezkey analogue |
| --- | --- | --- | --- |
| **Confidentiality of this session** | Ephemeral ECDHE (forward secrecy) | Hybrid **ML-KEM + X25519** so recorded sessions stay secret | **Not** device/integration signing keys. Closest analogue: **ephemeral proof tokens** + TLS itself. Ezkey has no application-layer KEM. |
| **Who you are talking to** | Long-lived certificates (signatures) | Hybrid / ML-DSA certificates, slower than KEM | **Device EC P-256** (enrollment identity) and **integration Ed25519** (server→device authenticity) |
| **Limit blast radius of a stolen long-term key** | Forward secrecy: stealing the certificate private key does not decrypt **past** sessions | Same, plus hybrid KEM | Proof tokens already expire. **Past MFA approvals stay historically true.** The CRQC risk is **future forgery**, not rewriting old ciphertext. |

Consequences for protocol design:

1. **Rotating a signing key every request is not “doing TLS.”** TLS rotates **ephemeral key-exchange** secrets, not the certificate, on every handshake. Copying “new identity key per HTTP call” is rotating the **certificate**, which TLS does not do.
2. **Window management** (shorter epochs for classical signatures) is the analogue of **short certificate lifetimes**, not of ECDHE. It reduces how long a harvested public key remains a valid forging tool. It does **not** make Ed25519 or P-256 Shor-safe.
3. **Algorithm replacement / hybrid signatures** is the analogue of **PQ certificates**. That is the only way the identity layer stops being pre-quantum.
4. **Application-layer HPKE/Noise** would be the analogue of “don’t trust TLS.” That is a different product (end-to-end encrypted MFA over an untrusted transport). Disproportionate for Ezkey’s self-hosted, TLS-mandatory posture unless T1 is judged untrustworthy **and** hybrid TLS is unavailable.

## Inventory — what is already PQ-adequate

Grover’s algorithm is the usual bound for symmetric search: effective bits ≈ half the classical key size. NIST-style “category 1” ≈ AES-128 classical ≈ keep **256-bit** symmetric keys and SHA-256 (still ~128-bit against Grover).

| Element | Mechanism | Why it is already in good shape |
| --- | --- | --- |
| At-rest field encryption | Tink **AES-256-GCM** (optional ChaCha20-Poly1305) | Symmetric; 256-bit. Rotation/re-encryption already exist (different domain from protocol identity). |
| Audit tamper-evidence | **HMAC-SHA256**, 256-bit dedicated key | Symmetric MAC; Grover-adjusted still in the SHA-256 band. Host-trust limit is unchanged ([`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md)). |
| Proof-token **generation** | `SignatureService.generateProofToken()`: **32 random bytes + 16 salt bytes**, URL-safe Base64 | ~256 bits of secret. One-time / short-lived on auth attempts. This is the protocol’s real “ephemeral handshake secret.” |
| Auth-attempt proof token **use** | Bind pending → respond; signed into payloads | Replay story is protocol + TTL, not elliptic curves. |
| Device proof token storage | Hash-only (ADR-0007 / `I-2026-0032` Tier 0) | Server never needs plaintext; SHA-256 verify. |
| Admin bearer tokens | Opaque, **SHA-256 hash-only** lookup | Symmetric; short TTL; revocation is a DB flag. |
| Protocol **structure** | Dual algorithms, fail-closed `integrationKeyAlgorithm`, signed bind/verify and pending/respond, NFC canonical payloads | PQ does not obsolete continuity. It obsolesces **which** signature schemes are used. |
| Recovery-code **operational** controls | Show-once, BCrypt at rest, single-use, regenerate invalidates unused set, recovery token ≠ full session (SEC-021) | Lifecycle is sound. Entropy + **claims** need honesty (below). |

These are the “positives” to keep in any evaluator narrative: Ezkey already behaves like a system with **ephemeral session secrets** and **symmetric at-rest crypto**. The gap is the **asymmetric identity layer** and a few mid-size symmetric leftovers.

## Inventory — perfectible (pre-quantum or overclaimed)

| Element | Today | Weakness under T1/T2 + CRQC | Perfectible direction (not scheduled) |
| --- | --- | --- | --- |
| **Device private key** | EC P-256 in Android Keystore, **generate-once per enrollment** | Public key stored on server after verify. Shor → forge respond / verify. Enrollment lifetime = forging window. | Epoch rotation **or** hybrid/PQC **when the platform can sign in hardware**. Not per-auth rotation (see below). |
| **Integration private key** | Ed25519, server-held, **generate-once per enrollment** | Public key on every device that bound. Shor → forge pending and result signatures (phone UX MITM). Longest-lived **server** asymmetric secret in the MFA chain. | Already incubating: [`I-2026-07-05`](../backlog/ideas/I-2026-07-05-enrollment-integration-key-cycling.md) (window management). Later: hybrid ML-DSA + Ed25519 via capability negotiation. |
| **Enrollment proof token** | Encrypted at rest; **lives with the enrollment** | T1: TLS break recovers a **long-lived** secret used in bind/verify/instance-info payloads. Not Shor; still high value. | `I-2026-0032` Tier 1 (hash-only + show-once verify field) reduces DB recoverable copies. Does not fix T1 by itself. |
| **TLS 1.3 at the edge** | Caddy / Cloudflare TLS 1.3; mobile = platform trust (pinning roadmap) | T1: classical KEX on recorded sessions. | **Ops**: enable hybrid KEM (ML-KEM) when the edge offers it. Not an Auth API wire change. Complements pinning (`V-2026-0006`) rather than replacing it. |
| **Recovery codes** | 32 digits ≈ **106 bits**; BCrypt; Javadoc says “quantum attacks” | Theoretical Grover ≈ 53 bits **if** hashes leak. BCrypt cost makes wall-clock still huge. Real weaknesses: **printed codes**, bootstrap logs, T1 plaintext in `/recover`, online guessing (rate limits). | Honesty in docs/Javadoc **now** (hygiene). Optional later: 128+ bits (e.g. more digits or Crockford alphabet), SHA-256/Argon2id for high-entropy secrets instead of BCrypt, never claim “PQ proof.” |
| **Integration API keys** | `ezkey_skey_` + 40 hex chars = **160 bits** | Grover-adjusted ~80 bits. Long-lived M2M secret. T1/T2: steal once, use until revoke. | Lengthen toward 256 bits on next key-format generation; keep rotation/expiry (already productized). |
| **Challenge codes** | 1–6 digits (often 2 or 6) | **Not a cryptographic factor.** UX / confused-deputy / “same room.” | Do not “PQC” them. Keep [`I-2026-07-07`](../backlog/ideas/I-2026-07-07-auth-attempt-challenge-protection.md) as lifecycle/UX, not crypto. |
| **`integrationKeyAlgorithm` lock** | Phase 1: exact `ed25519` | Fail-closed is correct **today** and is the **hook** for tomorrow. | Capability versioning ([`I-2026-0025`](../backlog/ideas/I-2026-0025-auth-api-protocol-capability-versioning.md) / [`V-2026-0008`](V-2026-0008-auth-api-protocol-versioning.md)) before any second algorithm. |
| **Device algorithm lock** | P-256 because Keystore / StrongBox ([ADR-0006](../architecture-decisions.md#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519)) | Hardware isolation vs PQC: today’s production Keystore is elliptic-curve. Software ML-DSA would **leave** StrongBox. | Wait for platform PQC signing **or** accept a documented hybrid (hardware P-256 + software ML-DSA) with honest UX. |

## Attack angles if we assume the Ezkey protocol is attacked

Assume T2 (payloads visible) **or** T1 after a transport break. The interesting failures are not “HTTPS is missing.”

### A — Forge the device (false APPROVED into the integrating app)

1. Obtain `devicePublicKey` (Auth API DB, verify transcript, backup).
2. CRQC → device private key.
3. For a live or attacker-created auth attempt, build `{proofToken}\|true` and sign as the device.
4. Auth API verifies ECDSA, returns **APPROVED**.
5. **PIA / Integration API** consumes that decision. Cryptographic quality of the integration is **exactly** this chain. There is no extra “integration-side PQC” that saves a forged device signature.

**Why long-lived enrollment keys matter:** one harvested public key forges **every future** attempt until the enrollment is reset. That is the structural HNDL finding from 2026-07-05, applied to the **device** side as well as integration.

### B — Forge the integration (lie to the phone)

1. Obtain `integrationPublicKey` (bind response, device sealed storage).
2. CRQC → integration private key.
3. Forge pending context (wrong amount, wrong merchant) and/or forge respond-result (`APPROVED` shown when the server denied).
4. Mobile fail-closed verification **passes** because the key is “valid” cryptographically.

This does **not** by itself approve the PIA. It **does** break the human-in-the-loop guarantee. Combined with A, or with a compromised Auth API, it is a complete story. Alone, it is a UX/integrity break.

### C — Steal long-lived enrollment secrets from recorded TLS (T1)

Bodies include enrollment proof tokens, bind material, recovery codes, API keys. After a CRQC on **TLS KEX**:

- Short-lived **auth-attempt** proof tokens are likely expired (limited replay).
- **Enrollment** proof tokens and **recovery codes** remain valuable for months or years.
- Public keys in those bodies feed attacks A and B even without forging TLS in real time.

This is why hybrid **TLS** is the highest-leverage “PQ” control that does **not** require an Ezkey protocol version.

### D — Offline search against stolen hashes

- Recovery-code **BCrypt** hashes (DB-only adversary): classical 106-bit is overkill; Grover is the theoretical cut. Still not the same class as Shor-on-P-256.
- Proof-token and bearer **SHA-256** hashes: 256-bit secrets, not interesting for Grover in practice.
- API-key BCrypt of 160-bit secrets: weaker than recovery codes; rotation remains the operational control.

### E — Do not confuse with protocol breaks that already have a classical answer

Replay of one-time proof tokens, unsigned pending, DER vs raw Ed25519 mixups, client-reported StrongBox, missing pinning: covered by [`docs/CRYPTO.md`](../../../docs/CRYPTO.md), [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md), and ADR-0006. A CRQC does not create those bugs; it **upgrades** A/B from science-fiction to a dated engineering program.

## What should evolve in the protocol — why — how

Ordered by **leverage / complexity**, not by calendar. None of this is committed work.

### Path 0 — Honest claims (cheap, should happen when this note is grilled)

- State in [`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md): Ezkey does **not** claim post-quantum resistance of device or integration signatures.
- Tone down `AdminRecoveryService` Javadoc (“resistant to … quantum attacks”) to **high-entropy single-use bootstrap secret**.
- Keep recovery-code **length** as a classical-and-practical hardening; do not advertise it as the protocol’s PQ story.

### Path 1 — Transport hybrid KEM (ops, not Auth API)

- Prefer TLS 1.3 **plus** ML-KEM hybrid at Cloudflare / Caddy when available.
- This is the TLS-handshake compass applied **at the real handshake**, which is the right layer for T1.
- Independent of mobile pinning; pinning still does not encrypt against a future CRQC.

### Path 2 — Window management on classical signatures (already incubated)

- **Integration keys:** policy-gated cycling on **terminal auth completion**, two-phase handoff, static-long default — grill D1–D10 on 2026-07-05 remain in force. **Do not** rotate on pending poll. **Do not** market as PQ.
- **Device keys:** same *idea* (epoch, not per-request), much **harder** (Keystore slots, StrongBox, enrollment as security anchor in [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)). Prefer **re-enrollment / recovery reset** as the existing device-key rotation ceremony until a design pack proves in-protocol device rotation is enrollment-safe.

### Path 3 — Symmetric leftovers

- API-key secret entropy 160 → 256 bits on a new generation (capability or format prefix).
- Optional recovery-code encoding/length bump; consider SHA-256 for high-entropy secrets (bearer-token precedent) vs BCrypt-for-passwords.
- Enrollment proof token hash-only (`I-2026-0032` Tier 1) for DB recoverable-surface reduction.

### Path 4 — Algorithm agility, then hybrid signatures (the actual PQ protocol)

This is what would make Ezkey’s **identity layer** post-quantum in the NIST sense.

1. Ship **protocol capability negotiation** ([`I-2026-0025`](../backlog/ideas/I-2026-0025-auth-api-protocol-capability-versioning.md)) so `integrationKeyAlgorithm` / a device algorithm field can grow without a flag day.
2. **Integration first** (server-held, JDK 25 can grow ML-DSA / hybrid without Keystore): sign pending/bind/result with **Ed25519 + ML-DSA** (concatenate or dual fields); mobile verifies both (**fail-closed** if either fails). Classical leftover is still Shor-vulnerable **alone**, but the hybrid is the industry pattern (same as TLS: classical + PQ, both must pass).
3. **Device second**, gated on Android/iOS hardware-backed PQC **or** an explicit hybrid that admits software PQC and says so in Admin UI (no “verified StrongBox + Dilithium” fiction).
4. Keep ADR-0006’s split of **roles** (device vs integration). Changing **algorithms** does not require collapsing to one curve.

### Path 5 — Application-layer KEM (probably reject)

Encrypting pending/respond bodies with ML-KEM so that T1 without TLS-PQ still fails: duplicates TLS, fights with pull-MFA plaintext needs, large accidental complexity. Revisit only if Ezkey must run over a transport it does not trust **and** cannot get hybrid TLS.

## Verdict on “rotate the device private key after every interaction”

**Thought-experiment: yes. Protocol evolution to adopt: no** (as the PQ strategy).

| Claim | Verdict |
| --- | --- |
| Frequent rotation shrinks the HNDL window for a **classical** key | True in the abstract |
| Frequent enough rotation ≈ PQ even with P-256 | **Misleading.** Each epoch’s public key is still Shor-broken. If the attacker harvests **this** attempt’s key and the epoch is still accepted (the current attempt), they forge **this** attempt. Per-auth rotation helps **future** attempts, not the recorded one you just stored. |
| This is how HTTPS stays safe | **False analogue.** HTTPS uses ephemeral **KEM/DH**, not a new certificate per request. |
| Cost vs grill D2/D6 | Per-auth integration rotation was already **deferred / likely reject**. Device Keystore generation, slot limits, and “failed rotation must not unenroll” (D7 analogue) make per-auth device rotation **worse**. |
| Proportionate substitute | Path 2 epochs (hours/days, min interval) **or** Path 4 hybrid signatures. Re-enrollment remains the honest “new device key” ceremony. |

Same conclusion for “new integration private key after every pending”: rejected in 2026-07-05 (thrashing, partial handoff). A **rotate-keys API** is reasonable as **policy-triggered epoch rotation** (Path 2), not as a per-call handshake.

## Integration API / PIA vs cryptographic quality

The integrating application (PIA) does not run Ed25519. It asks Ezkey whether this user approved **this** attempt.

| PIA assumption | Backed by |
| --- | --- |
| The attempt is bound to one enrollment | Enrollment id + operational eligibility |
| The approval is fresh and non-replayed | Auth-attempt proof token + TTL + one-time consume |
| The approval came from the enrolled device | Device signature over `{proofToken}\|accepted` |
| The phone showed the right context | Integration signature over pending payload (human check, not PIA) |
| Confidentiality of the decision in flight | **TLS** to Integration API (API key + HTTPS) |

PQ does not add a fourth signature on the Integration API. If A succeeds, PIA is correctly told a **lie that verifies**. Strengthening PIA means strengthening **device identity + proof tokens + Auth API integrity + TLS**, in that order. “Integration crypto quality” in the PIA sense is **API-key entropy, TLS, and not treating APPROVED as stronger than the device algorithm**.

## Honest claims we may make later (after grill)

| We may say | We must not say |
| --- | --- |
| Proof tokens and AES-256/HMAC-SHA256 are sized for a Grover-adjusted world | “Ezkey is post-quantum” |
| Enrollment identity keys are pre-quantum; window management can shrink exposure | “Key cycling = PQ resistance” |
| Recovery codes are high-entropy break-glass secrets | “106 bits = quantum-safe protocol” |
| Hybrid TLS at the edge addresses recorded-HTTPS HNDL | “Application signatures protect confidentiality” |
| A future capability-negotiated hybrid signature is the real identity-layer path | “We will rotate device keys every approve to beat quantum computers” |

## Promotion criteria

Promote substance into durable canon ([`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md), [`docs/CRYPTO.md`](../../../docs/CRYPTO.md), ADR-0006 consequences, optional ADR for hybrid signatures) when:

1. This map is grilled (or explicitly accepted without grill) — especially the **per-auth device rotation reject** and the **TLS vs protocol** split.
2. Parent idea [`I-2026-09-15-post-quantum-protocol-evolution`](../backlog/ideas/I-2026-09-15-post-quantum-protocol-evolution.md) stays `captured` / `P3` until a post–R1 crypto-hardening milestone is funded.
3. Child ideas already in the corpus (`I-2026-07-05`, `I-2026-0025`, `I-2026-0032`, pinning, recovery-code UX) are **linked**, not duplicated.
4. No `TB-*` is opened solely “for PQC” without a funded algorithm or epoch-rotation design pack.

## Related documents

- Backlog parent: [`I-2026-09-15-post-quantum-protocol-evolution`](../backlog/ideas/I-2026-09-15-post-quantum-protocol-evolution.md)
- Window-management slice: [`V-2026-07-05-enrollment-integration-key-cycling-posture`](V-2026-07-05-enrollment-integration-key-cycling-posture.md), [`I-2026-07-05-enrollment-integration-key-cycling`](../backlog/ideas/I-2026-07-05-enrollment-integration-key-cycling.md), [grill 2026-07-05](../backlog/grill-sessions/2026-07-05-enrollment-integration-key-cycling-grill-me.md)
- Protocol capability negotiation: [`V-2026-0008`](V-2026-0008-auth-api-protocol-versioning.md), [`I-2026-0025`](../backlog/ideas/I-2026-0025-auth-api-protocol-capability-versioning.md)
- Proof-token storage: [`I-2026-0032`](../backlog/ideas/I-2026-0032-proof-token-hash-only-storage.md), ADR-0007
- Algorithms: [ADR-0006](../architecture-decisions.md#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519), [`docs/CRYPTO.md`](../../../docs/CRYPTO.md), [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md), [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
- Honesty: [`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md), [`product-intent.md`](../product-intent.md) § cryptographic continuity
- Mobile protocol assessment (classical): [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)
- Transport pinning (not PQ): [`V-2026-0006-mobile-certificate-pinning.md`](V-2026-0006-mobile-certificate-pinning.md)
- Lifecycle / enrollment as anchor: [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)
- Fail-open vs fail-closed: [`design-principles.md`](../design-principles.md) §17
- Operability compass (this is **off** it): [`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)
