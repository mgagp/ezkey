# Ezkey Mobile Protocol and Crypto Security Assessment

**Date:** 2026-07-16  
**Scope:** Android-first reference app (`ezkey_mobile`), Auth API enrollment/authentication protocol, shared crypto contracts  
**Method:** Non-intrusive white-box review (repository source, docs, existing tests). No active exploitation, no device extraction, no hostile network injection against live deployments.  
**Prior assessment:** [`mobile-security-assessment-2026-05.md`](mobile-security-assessment-2026-05.md)  
**Operator HITL lane:** [`product-docs/global/hygiene/mobile-protocol-security/`](../../product-docs/global/hygiene/mobile-protocol-security/)

## 1. Executive summary

Ezkey’s protocol design for enrollment (`bind`/`verify`) and authentication (`pending`/`respond`) remains **credible and coherent**: dual algorithms (device EC P-256, integration Ed25519), one-time proof tokens, signed contextual pending payloads, signed respond decisions, and signed result outcomes. The Android reference client largely follows that contract and now seals long-lived enrollment secrets at rest via an app-level Android Keystore AES key — closing the May 2026 P1 cleartext AsyncStorage defect for the intended write path.

**Lot A (2026-07 hygiene cycle) is closed** — see §9 and the campaign note. **Lot B documentary / claim-honesty checks were completed 2026-07-19** — see §13; do not reopen those as “Admin UI overclaim” or “missing positioning” defects without new evidence.

Residual **product/protocol** gaps that remain intentional or deferred (not false positives):

1. Local “protected approval” remains **UX-gated**, not Keystore-enforced (MOB-001 Track B / CryptoObject — future program).
2. `devicePrivateKeyStorageTier` remains **client-reported and outside the signed verify payload** (MOB-003 — protocol; operator-facing honesty already verified).
3. Transport trust remains **platform TLS only** (MOB-005 pinning — roadmap / `R-2026-0001`).
4. Device integrity / tamper resistance remains minimal (MOB-009 — deferred; no overclaim found).
5. QR enrollment bootstrap remains trust-on-issuer (MOB-010 — by design; host is shown in UX).

None of the confirmed findings, by themselves, constitute a remote authentication bypass of an honest enrolled device over ordinary TLS.

## 2. Assessment method and standards mapping

### 2.1 Method

| Activity | Done |
| --- | --- |
| Read product/protocol docs and mobile design pack | Yes |
| Trace bind/verify and pending/respond in mobile + Auth API + ezkey-core | Yes |
| Review Android Keystore / StrongBox / seal / biometric code | Yes |
| Reconcile May 2026 mobile assessment findings | Yes |
| Inventory unit / functional / Maestro / static coverage | Yes |
| Active exploit, MITM lab, rooted-device extraction | **Out of scope** (future scenario lane) |

### 2.2 Standards used as analysis lenses (not certification claims)

| Framework | How used |
| --- | --- |
| **OWASP MASVS 2.1** | Control groups: STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, RESILIENCE |
| **OWASP MASTG / MASWE** | Testability language for storage, crypto, network, and resilience weaknesses |
| **OWASP ASVS** | Auth API input validation, rate limiting, state-machine abuse (server side) |
| **NIST SP 800-63B-4** | Comparison only: replay resistance, authentication intent, phishing resistance (channel/verifier-name binding), non-exportable keys. **Ezkey does not claim NIST AAL compliance.** |
| **Android Keystore / StrongBox / Key Attestation / BiometricPrompt** | Platform best-practice baseline for hardware-backed keys and auth-bound crypto |

### 2.3 Explicit non-claims

This assessment does **not** assert FIDO2/WebAuthn equivalence, NIST AAL2/AAL3 compliance, server-verified StrongBox, or Play Integrity / attestation-grade endpoint integrity.

## 3. Threat model

### 3.1 Assets

| Asset | Location | Sensitivity |
| --- | --- | --- |
| Per-enrollment EC P-256 private key | Android Keystore (`ezkey_enrollment_{id}`), StrongBox requested | Critical — signs verify/pending/respond |
| App-level AES seal key | Android Keystore (`ezkey_app_seal_v1`) | High — unwraps sealed local secrets |
| `enrollmentProofToken` | Sealed envelope in AsyncStorage (Android) | High — associates device with enrollment |
| `integrationPublicKey` | Sealed envelope in AsyncStorage (Android) | High integrity (trust material) |
| One-time `authAttemptProofToken` | Memory only (intended) | High — binds pending→respond |
| QR bootstrap (`enrollmentId`, proof token, `authUrl`) | Camera / logs / screenshots | High during enrollment |
| Auth API protocol surface | Network | Integrity of MFA decisions |

### 3.2 Actors

| Actor | Relevance |
| --- | --- |
| Physical thief / unlocked device user | High |
| Malware / rooted / compromised app process | High for local confirmation & seal key |
| Network attacker with hostile trusted CA / MDM | Medium (no pinning) |
| Malicious QR / enrollment issuer | Medium (bootstrap trust) |
| Compromised or hostile Auth API origin | Medium (self-hosted trust model) |
| Remote anonymous internet attacker | Lower for crypto bypass; higher for API abuse (rate limits, etc.) |

### 3.3 Trust boundaries

```mermaid
flowchart LR
  subgraph device [Mobile device]
    JS[React Native JS]
    Native[EzkeyCryptoModule]
    KS[Android Keystore]
    AS[AsyncStorage envelopes]
  end
  subgraph backend [Ezkey backend]
    Auth[Auth API]
    Core[ezkey-core state]
  end
  QR[Enrollment QR]
  QR --> JS
  JS --> Native
  Native --> KS
  JS --> AS
  Native --> AS
  JS --> Auth
  Auth --> Core
```

Critical honesty boundary: **honest-client assertions** (storage tier, local biometric prompt) are **not** server-proven properties unless attestation is added later.

### 3.4 Security invariants (expected)

1. Device private keys never leave Keystore / are never exposed to JS as material.
2. Enrollment signing keys and the app seal key are **distinct** Keystore roles.
3. Sensitive enrollment secrets are not stored cleartext in the metadata collection.
4. Bind, verify-result, pending, and respond-result payloads are verified with Ed25519 before trust/UI.
5. Respond signs `authAttemptProofToken|accepted`, not `enrollmentProofToken`.
6. User-initiated pending pull only (no background polling).
7. Backend remains authoritative for attempt/enrollment state and verification.

## 4. Claim-to-evidence matrix

| Product / docs claim | Verdict | Evidence |
| --- | --- | --- |
| Private key material not exposed to application code | **Supported** (Android) | `EzkeyCryptoModule` only exports public key + signatures |
| Android Keystore used for enrollment keys | **Supported** | `generateEnrollmentKeyPair` / `AndroidKeyStore` |
| StrongBox when available | **Conditionally supported** | Requested with fallback; tier is client-reported; silent fallback |
| Sealed local secrets for proof token / integration key | **Supported** (Android intended path) | `secureStorage` + `SealedSecretEnvelope`; May P1 largely remediated |
| Local confirmation before approve | **Conditionally supported** | BiometricPrompt UX exists; **not** Keystore-bound |
| Cryptographic continuity bind↔verify and pending↔respond | **Supported** | Payload builders + mobile verify steps + core services |
| Server-verified StrongBox / hardware tier | **Unsupported** (correctly documented as non-claim) | Tier outside signed verify payload; no attestation; Admin UI tooltips state client-reported (verified 2026-07-19, §13) |
| Certificate / SPKI pinning | **Unsupported** (roadmap / Coming Soon) | Axios default trust only; Coming Soon frames as planned (verified 2026-07-19, §13) |
| iOS secure-hardware parity | **Out of scope** / deferred | Docs Android-first; iOS module lag expected |

## 5. Reconciliation with May 2026 assessment

| May 2026 finding | 2026-07 status | Notes |
| --- | --- | --- |
| Sensitive material in cleartext AsyncStorage collection | **Resolved (intended path)** | `enrollmentStorage` strips secrets; Android seals via Keystore AES; legacy migration on read |
| iOS deferred framing | **Still valid boundary** | Not treated as current defect |
| No certificate pinning | **Residual** (honest roadmap) | See MOB-005 |
| Debug / QR logging | **Resolved** (Lot A) | See MOB-004 |
| EXP1 Cloudflare edge posture | **Out of this assessment** | Deployment edge, not mobile protocol |
| Minimal root/tamper resistance | **Residual / deferred** | See MOB-009 |

## 6. Findings register

Severity scale: **P0** integrity/auth bypass or secret compromise with low prerequisites; **P1** material protocol/crypto or claim integrity risk; **P2** meaningful hardening / lifecycle / maintainability risk; **P3** polish / docs / deferred resilience.

Confidence: **Confirmed** = source-evident; **Hypothesis** = needs dynamic/device evidence.

---

### MOB-001 — Local protected approval is UX-gated, not Keystore-bound

| Field | Value |
| --- | --- |
| **Severity** | P1 |
| **Confidence** | Confirmed |
| **Disposition** | **Track A completed** (2026-07-18 wording/docs lockdown); Track B (CryptoObject) deferred to a future program; Track A honesty **re-verified 2026-07-19** |
| **MASVS** | AUTH, CRYPTO, PLATFORM |
| **NIST lens** | Authentication intent not cryptographically bound to key use |

**Issue.** Enrollment keys are created with `setUserAuthenticationRequired(false)`. `signWithAuthentication` shows `BiometricPrompt`, then calls ordinary `Signature.initSign` / `sign()` without `BiometricPrompt.CryptoObject`. A compromised app process that can invoke the native module’s `sign()` can approve or deny without the prompt.

**2026-07-19 Track A residual check.** Security screen i18n remains explicit: “This protection is enforced by the app” / signing key does not require confirmation / backend receives no cryptographic proof (`ezkey_mobile/app/i18n/resources.ts` `declarativeNote*`). Do **not** re-open Track A as a wording defect. Track B (CryptoObject) remains a deliberate program gap, not an uninvestigated hygiene miss.

**Evidence.**
- [`EzkeyCryptoModule.kt`](../../ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt) — key gen ~L173; `signWithAuthentication` ~L336–398; plain `sign` ~L293–322
- [`cryptoService.ts`](../../ezkey_mobile/app/services/crypto/cryptoService.ts) — `signForRespond` branches to `signWithAuthentication` only in JS
- Docs already warn of declarative local confirmation: [`MOBILE_CRYPTO_REFERENCE.md`](../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md)

**Attack path.** Malware / accessibility abuse / compromised RN bridge on unlocked device → call `sign(enrollmentId, respondPayload)` → valid Auth API respond.

**Impact.** Local confirmation can be marketed stronger than the crypto guarantee. Does not break remote protocol integrity against an uncompromised device.

**Recommended action.**
1. Short term: lock product/Admin/docs wording to “app-enforced confirmation on honest clients.”
2. Medium term: generate auth-per-use (or time-bound) Keystore keys with `setUserAuthenticationRequired(true)` and sign via `CryptoObject` for protected enrollments / preference.
3. Protocol note: server still cannot attest local auth without attestation redesign.

**Verification class.** Unit/static for wording; **physical StrongBox-capable phone** for CryptoObject / auth-per-use path; instrumentation for Keystore flags.

---

### MOB-002 — Enrollment Keystore keys orphaned on local delete / clear-all

| Field | Value |
| --- | --- |
| **Severity** | P1 |
| **Confidence** | Confirmed |
| **Disposition** | **Fixed** — PR [#381](https://github.com/mgagp/ezkey/pull/381) (fail-open Keystore delete on wipe) |
| **MASVS** | CRYPTO (key lifecycle), STORAGE |

**Issue.** `nativeCrypto.deleteKeyPair` is implemented but never called from `enrollmentStorage.deleteEnrollment`, `clearAll`, `useDeleteEnrollment`, or Danger Zone. Local delete removes metadata + sealed secrets, leaving `ezkey_enrollment_{id}` signable if metadata/secrets were restored or another bug rehydrated identity.

**Evidence.**
- `deleteKeyPair` in `EzkeyCryptoModule.kt` / `nativeCrypto.ts`
- `enrollmentStorage.deleteEnrollment` / `clearAll` — secure remove + metadata only
- `DangerZoneScreen.tsx` — calls `clearAll` without key deletion
- `useEnrollments.ts` — delete mutation → storage only

**Attack path.** Residual key material after “delete”; forensic / malware reuse if enrollment identifiers and proof material reappear; confusing lifecycle for re-enrollment on same id.

**Impact.** Incomplete crypto lifecycle; weakens “data removed from this device” operator expectation.

**Recommended action.** Call `deleteKeyPair` on single delete and clear-all (best-effort, log failures); add unit/hook tests that assert the native delete call; optional instrumentation test that alias is gone.

**Verification class.** Unit (mock native delete called); **emulator/instrumentation** preferred; physical device optional.

---

### MOB-003 — `devicePrivateKeyStorageTier` unbound and unattested

| Field | Value |
| --- | --- |
| **Severity** | P1 (claim integrity) / P2 (protocol) |
| **Confidence** | Confirmed |
| **Disposition** | **Claim honesty verified adequate (2026-07-19)**; protocol attestation / signed-tier binding remains **deferred program** |
| **MASVS** | CRYPTO, AUTH |
| **Android** | Key Attestation not used |

**Issue.** Tier (`NONE`/`STANDARD`/`STRONG`) is client-chosen telemetry, not in the ECDSA verify payload, and not validated via Key Attestation. Residual risk was Admin UI / sales language treating `STRONG` as proven StrongBox.

**2026-07-19 verification (documentary / operator-facing — no handoff required).**

| Surface | Result |
| --- | --- |
| Admin UI badge tooltips | Explicit **client-reported / not attested by the server** for NONE, STANDARD, and STRONG (`ezkey-admin-ui/src/locales/en/enrollments.json` `keyTier.help*`; FR equivalents present) |
| Admin UI presentation | `DevicePrivateKeyTierBadge` uses help text as tooltip on enrollment detail (`device-private-key-tier-badge.tsx`, `enrollment-detail.tsx`) — no “verified hardware” label |
| Protocol / developer docs | `docs/MOBILE_DEVELOPER_GUIDE.md` trust-model table; `docs/CRYPTO.md`; `ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md` — all state server does not prove tier |
| Public site / PRD | No “server-verified StrongBox” overclaim found in spot check |

**Conclusion for future campaigns:** Do **not** re-open MOB-003 as an Admin UI honesty defect unless new copy appears that claims attestation. The remaining work (Key Attestation, extend signed verify payload) is a deliberate **protocol program**, not a missed hygiene fix.

**Verification class.** Static/docs/UI review (done); physical device only for honest-client KeyInfo mapping (covered under MOB-006 evidence).

---

### MOB-004 — `__DEV__` / warn paths can log QR enrollment material

| Field | Value |
| --- | --- |
| **Severity** | P2 |
| **Confidence** | Confirmed |
| **Disposition** | **Fixed** — PR [#383](https://github.com/mgagp/ezkey/pull/383) (redacted seed logs + debug panel; opt-in raw dump flag) |
| **MASVS** | STORAGE, PRIVACY, CODE |

**Issue.** `useEnrollmentWizard.handleQrScanned` logs raw QR JSON and parsed payload under `__DEV__`. Invalid QR path `console.warn`s raw value. Pending debug panel can show payload previews when flag enabled.

**Evidence.**
- `useEnrollmentWizard.ts` ~L407–433
- `usePendingAuth.ts` debug snapshot fields
- Semgrep rules exist but do not cover guarded `__DEV__` QR dumps

**Impact.** Pilot / QA logcat and screenshots can leak `enrollmentProofToken`.

**Recommended action.** Redact tokens; require explicit extra flag for raw dumps; tighten debug panel redaction.

**Verification class.** Unit/static (Semgrep rule + test); optional `verify-android-sensitive-storage.sh` / logcat on device.

---

### MOB-005 — No certificate / SPKI pinning (TOFU still roadmap)

| Field | Value |
| --- | --- |
| **Severity** | P2 |
| **Confidence** | Confirmed |
| **Disposition** | **Honest positioning verified (2026-07-19)**; implementation remains **deferred program** (`R-2026-0001`) |
| **MASVS** | NETWORK |
| **NIST lens** | Limited phishing / MITM resistance vs verifier-name binding models |

**Issue.** `httpClient` uses platform TLS trust. QR may supply `authUrl` (HTTPS required except loopback). Hostile root CA / MDM can MITM; protocol signatures still protect pending/respond content integrity.

**2026-07-19 verification (documentary).**

| Surface | Result |
| --- | --- |
| Implementation | No pinning in `httpClient.ts` — expected |
| Product framing | Coming Soon + release notes describe pinning as **planned** TOFU-style, not as shipped |
| Design canon | `product-docs/global/legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md` documents intended SPKI+TOFU approach |
| Overclaim check | No living claim that mobile currently pins certificates |

**Conclusion for future campaigns:** Absence of pinning is a known deferred feature with honest UX/docs. Re-open only if product copy claims pinning is active, or when promoting the pinning program to implementation.

**Verification class.** Design/docs (done); MITM lab remains optional future Scenario C.

---

### MOB-006 — Native Keystore / StrongBox / biometric path untested

| Field | Value |
| --- | --- |
| **Severity** | P2 |
| **Confidence** | Confirmed (coverage gap) |
| **Disposition** | **Fixed** — PR [#386](https://github.com/mgagp/ezkey/pull/386) (androidTest + StrongBox evidence on Pixel 7 Pro → `STRONG`) |
| **MASVS** | CRYPTO, CODE |

**Issue.** JVM tests cover `SealedSecretEnvelope` and `IntegrationKeyVerifier` only. Zero `androidTest`. Maestro pilots approve flows but do not assert StrongBox tier, Keystore delete, or CryptoObject binding. Jest mocks native crypto.

**Impact.** Regressions in the trust boundary can ship unnoticed; undermines assurance narrative.

**Recommended action.** Add focused instrumentation tests (key gen flags, delete alias, seal via module); document StrongBox-capable manual checklist; extend Maestro only where stable.

**Verification class.** Emulator for generic Keystore; **physical StrongBox phone** for STRONG tier and StrongBox fallback.

---

### MOB-007 — Duplicate pending orchestration (Detail vs hook)

| Field | Value |
| --- | --- |
| **Severity** | P2 |
| **Confidence** | Confirmed |
| **Disposition** | **Fixed** — PR [#384](https://github.com/mgagp/ezkey/pull/384) (shared `claimPendingAttempt`) |
| **MASVS** | CODE (maintainability → security drift) |

**Issue.** `EnrollmentDetailScreen.handleCheckPending` reimplements pending request + Ed25519 verify already present in `usePendingAuth.loadPendingAttempt`. Future security fixes can land in one path only.

**Evidence.**
- `EnrollmentDetailScreen.tsx` ~L78–164
- `usePendingAuth.ts` `loadPendingAttempt`

**Recommended action.** Single shared pending-claim helper used by Detail navigation and Pending screen auto-load.

**Verification class.** Unit tests on shared helper; existing hook tests extended.

---

### MOB-008 — Stale crypto path / diagnostic documentation drift

| Field | Value |
| --- | --- |
| **Severity** | P3 |
| **Confidence** | Confirmed |
| **Disposition** | **Fixed** — PR [#385](https://github.com/mgagp/ezkey/pull/385) |
| **MASVS** | CODE |

**Issue.** Examples:
- `docs/CRYPTO.md` still cites `com/ezkeymobile/...` paths; implementation is `org.ezkey.mobile.crypto`
- Historical protocol audit plan (`.github/prompts/plan-authProtocolSecurityAudit.prompt.md`) is partially stale (e.g. respond rate limiting now exists)
- May P1 investigation doc describes pre-seal storage truth and can confuse cold agents if read without date context

**Recommended action.** Patch living docs; leave historical plans clearly marked; add “superseded by seal model” banner to May P1 note if still linked.

**Verification class.** Docs review only.

---

### MOB-009 — Minimal device integrity / tamper resistance

| Field | Value |
| --- | --- |
| **Severity** | P3 |
| **Confidence** | Confirmed (absence) |
| **Disposition** | **No overclaim found (2026-07-19)**; resilience controls remain **intentionally deferred** |
| **MASVS** | RESILIENCE |

**Issue.** No rooted-device, Play Integrity, or runtime tamper controls. Acceptable if positioned honestly; not acceptable if claimed.

**2026-07-19 verification (documentary).**

| Surface | Result |
| --- | --- |
| PRD / SECURITY_POSTURE / public site spot check | No Play Integrity / DeviceCheck / “tamper-proof phone” product claim |
| Mobile docs | Emphasize protocol + Keystore; May assessment already framed integrity as honest boundary |
| Code | No root/jailbreak detection layer in `ezkey_mobile` — expected for deferred scope |

**Conclusion for future campaigns:** Do not treat “missing Play Integrity” as a regression or missed Lot A fix. Re-open only if marketing claims endpoint attestation-grade integrity, or when starting a deliberate resilience program.

**Verification class.** Product positioning review (done).

---

### MOB-010 — QR bootstrap is trust-on-issuer (hostile enrollment host)

| Field | Value |
| --- | --- |
| **Severity** | P2 |
| **Confidence** | Confirmed (by design) |
| **Disposition** | **Enrollment host visibility verified (2026-07-19)**; residual trust-on-issuer is **intentional self-hosted model** |
| **MASVS** | AUTH, NETWORK |

**Issue.** A syntactically valid HTTPS QR can point the app at an attacker Auth API. Bind signature then proves that **that** host’s integration key — not organizational identity beyond TLS.

**2026-07-19 verification (UX + docs).**

| Surface | Result |
| --- | --- |
| Enrollment wizard | Shows **Server** URL after bind when available (`EnrollmentWizardScreen` `showServerUrl` / `serverUrl`) |
| Enrollment detail | Shows installation `authUrl` in technical server block |
| URL policy | `validateAuthUrl` requires HTTPS except loopback / emulator aliases |
| Protocol docs | `MOBILE_DEVELOPER_GUIDE.md` “no end-to-end PKI/CA chain” trust-model boundary explicit |
| Pinning | Not present (MOB-005) — does not silently claim host authenticity beyond TLS + bind crypto |

**Conclusion for future campaigns:** The recommended UX hardening (show host) is present. Do not re-file MOB-010 as “app hides enrollment target.” Remaining risk is inherent to QR→HTTPS self-hosted bootstrap until pinning/TOFU (MOB-005 / `R-2026-0001`) lands. Hostile-QR lab (May Scenario B) remains optional active validation, not a documentary defect.

**Verification class.** Static UX/docs (done); optional manual hostile QR scenario later.

---

## 7. Protocol continuity notes (positive controls)

These are **not** findings; they are strengths that should be preserved:

| Control | Status |
| --- | --- |
| Fail-closed `integrationKeyAlgorithm == ed25519` on bind | Present in mobile |
| Ed25519 verify of bind / verify-result / pending / respond-result | Present |
| Canonical pending includes challenge + context (NFC) | Present (V4 from older audit largely addressed) |
| Respond signs `proofToken\|accepted` | Present |
| Device proof token CSPRNG via native module | Present |
| User-initiated pending only | Present |
| Auth API respond rate limiting by `authAttemptId` | Present in current `RateLimitFilter` (older audit plan partially stale) |
| One-shot INVALID on failed verify/respond crypto | Present server-side |

## 8. Evidence and verification matrix

| Finding | Static/source | Unit | Docker functional | Emulator/instrumentation | Physical StrongBox phone |
| --- | --- | --- | --- | --- | --- |
| MOB-001 | Yes | Partial (policy) | No | Useful | **Required for CryptoObject fix** |
| MOB-002 | Yes | Yes (mock delete) | No | Recommended | Optional |
| MOB-003 | Yes | Docs/UI | N/A | Optional KeyInfo | Optional honest-tier check |
| MOB-004 | Yes | Semgrep/unit | No | Logcat optional | Optional |
| MOB-005 | Yes | N/A | N/A | N/A | MITM lab later |
| MOB-006 | Coverage gap | Envelope/verify exist | N/A | **Required** | **Required for StrongBox** |
| MOB-007 | Yes | Yes | No | No | No |
| MOB-008 | Yes | N/A | N/A | N/A | No |
| MOB-009 | Yes | N/A | N/A | N/A | Optional future |
| MOB-010 | Yes | URL validation | No | No | Manual QR scenario |

## 9. Prioritized action backlog

### Lot A — first hygiene cycle — **closed 2026-07-19**

Durable closeout (decisions, PR links, StrongBox evidence) lives in
[`product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md).
Ephemeral cold-agent handoff prompts were retired after merge; do not re-create them for closed rows.

| Finding | Outcome | Evidence |
| --- | --- | --- |
| **MOB-001** | Track A completed (wording/docs); Track B deferred | Commit `4a1cd705` (2026-07-18); CryptoObject remains future program |
| **MOB-002** | Fixed | PR [#381](https://github.com/mgagp/ezkey/pull/381) |
| **MOB-004** | Fixed | PR [#383](https://github.com/mgagp/ezkey/pull/383) |
| **MOB-007** | Fixed | PR [#384](https://github.com/mgagp/ezkey/pull/384) |
| **MOB-006** | Fixed | PR [#386](https://github.com/mgagp/ezkey/pull/386); Pixel 7 Pro tier `STRONG` |
| **MOB-008** | Fixed | PR [#385](https://github.com/mgagp/ezkey/pull/385) |

### Lot B — documentary / claim-honesty verification — **closed 2026-07-19**

Operator-facing and product-positioning checks for items that never received Lot A handoffs. Full investigation notes: **§13**. Future mini-campaigns must read §13 before re-opening these as defects.

| Finding | Documentary / UX verdict | Residual product/protocol work |
| --- | --- | --- |
| **MOB-003** | Admin UI + docs honest (client-reported, not attested) | Attestation / signed-tier binding — future program |
| **MOB-005** | Pinning framed as planned, not shipped | SPKI TOFU — `R-2026-0001` / future program |
| **MOB-009** | No integrity overclaim found | Play Integrity / tamper — intentionally deferred |
| **MOB-010** | Enrollment host / auth URL shown in UX | Trust-on-issuer inherent until pinning; optional hostile-QR lab |
| **MOB-001 Track B** | Track A wording still adequate | CryptoObject — future program (phone required) |

## 10. Claim verdict (product-facing)

| Statement | Verdict |
| --- | --- |
| “Cryptographic continuity across enrollment and authentication” | **Supported** |
| “Device private keys in Android Keystore; StrongBox when available” | **Supported with caveats** (fallback; not server-proven) |
| “Long-lived enrollment secrets sealed at rest on Android” | **Supported** for current reference path |
| “Local biometric/device confirmation protects approvals” | **Conditionally supported** — honest-client UX only today (MOB-001 Track A); Keystore-bound auth still deferred |
| “Backend verifies StrongBox” | **Unsupported** — do not claim |
| “Pinning / phishing resistance comparable to WebAuthn” | **Unsupported** — do not claim |
| “Deleting an enrollment removes local Keystore material” | **Supported** for intended wipe path (MOB-002); delete is fail-open if Keystore delete fails |

## 11. Methodology next steps

1. Lot A closed — see campaign note `2026-07-16-pass-1.md` (canonical remediation register for that pass).  
2. Lot B **claim-honesty / documentary** verification closed 2026-07-19 — see §13. Do not re-investigate Admin UI tier honesty, pinning positioning, integrity non-claims, or enrollment host visibility without new evidence.  
3. Promote only **protocol/product** redesigns (attestation, pinning implementation, CryptoObject key model, integrity APIs) to `I-*` / `TB-*` when the operator chooses to fund them.  
4. Optional future active scenarios: May assessment Scenarios A–D (device extraction, hostile QR, MITM, replay lab).

## 12. Related documents

- Campaign closeout: [`../../product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md)
- [`mobile-security-assessment-2026-05.md`](mobile-security-assessment-2026-05.md)
- [`mobile-p1-sensitive-storage-investigation-2026-05.md`](mobile-p1-sensitive-storage-investigation-2026-05.md)
- [`../CRYPTO.md`](../CRYPTO.md)
- [`../MOBILE_DEVELOPER_GUIDE.md`](../MOBILE_DEVELOPER_GUIDE.md)
- [`../ENROLLMENT_SIGNATURE_PAYLOAD.md`](../ENROLLMENT_SIGNATURE_PAYLOAD.md)
- [`../AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
- [`../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`](../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md)
- [`../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md`](../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md)
- [`../../ezkey_mobile/docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md`](../../ezkey_mobile/docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md)
- [`../../product-docs/global/legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md`](../../product-docs/global/legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md)

## 13. Lot B documentary verification closeout (2026-07-19)

**Purpose.** Items that were never handed off as Lot A code fixes were still **investigated** for operator-facing honesty and product positioning, so a later model-driven mini-campaign does not waste time on already-resolved documentary questions.

**Method.** Static review of Admin UI locales/components, mobile i18n / enrollment UX, living crypto docs, and (spot) public/PRD non-claims. No new protocol implementation.

| ID | Question asked | Verdict | Where recorded |
| --- | --- | --- | --- |
| MOB-003 | Is Admin UI / docs transparent that storage tier is client-reported? | **Yes — adequate** | Finding §6 MOB-003 verification table |
| MOB-005 | Does product claim pinning exists today? | **No — honest roadmap** | Finding §6 MOB-005 |
| MOB-009 | Does product claim Play Integrity / tamper-proof phone? | **No overclaim** | Finding §6 MOB-009 |
| MOB-010 | Does enrollment UX show the target host / auth URL? | **Yes — present** | Finding §6 MOB-010 |
| MOB-001 Track A | Does Security screen still disclose app-enforced (not Keystore-bound) confirmation? | **Yes — still honest** | Finding §6 MOB-001 |

**Assessment status after this note.** Lot A remediation **closed**. Lot B **documentary / claim-honesty** lane **closed**. Remaining open work is **explicitly deferred program** (CryptoObject, attestation, pinning implementation, integrity APIs) — not uninvestigated gaps.

**Anti-false-positive rule for agents.** If a finding ID appears above with a 2026-07-19 documentary verdict of adequate / no overclaim / host visible, treat a re-discovery of the same static facts as **already historized**. Escalate only on **new copy**, **new code paths**, or an operator decision to fund the deferred program.
