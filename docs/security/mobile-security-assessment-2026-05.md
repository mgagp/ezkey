# Ezkey Mobile Security Assessment

## Scope
This assessment reviews the current Ezkey Mobile posture from a pragmatic attacker perspective, grounded in the repository and a small set of live passive checks against the EXP1 deployment surface.

The goal is not to maximize theoretical criticism. The goal is to identify realistic weaknesses, honest product boundaries, and the highest-value hardening actions for Ezkey's actual market position.

## Assessment Method
The review used:
- repository docs and mobile implementation code,
- mobile dependency and supply-chain triage,
- passive external checks against `exp1-*.ezkey.org`,
- attack-scenario design based on the current protocol and deployment model.

This was intentionally non-destructive. No active exploit attempts were run against EXP1.

## Executive Summary
The current mobile posture is credible in its core protocol design, especially around signed flow continuity, user-initiated polling, and native Android keystore use. This assessment should be read as an **Android-first current-state review**. iOS is a later product phase and is not a short-term parity or release target, so the absence of iOS parity should not be treated as a present security defect by itself. The most important current gaps are not exotic cryptographic breaks. They are practical implementation and deployment issues:

1. Sensitive enrollment material is still persisted in the AsyncStorage-backed enrollment collection instead of the secure-storage path.
2. The mobile documentation and assessment outputs should clearly state that current security conclusions apply to the Android-first implementation, while iOS remains a later phase.
3. The mobile client currently relies on standard platform TLS trust without certificate pinning or equivalent origin binding.
4. The EXP1 posture must be interpreted as a Cloudflare-fronted origin, so public edge observations need to be attributed carefully between Cloudflare policy and origin hardening.

The good news is that none of the strongest concerns found here indicate a trivial remote break of the Ezkey protocol itself. The weaker news is that a security product cannot afford ambiguity between implementation, documentation, and operator-facing claims.

## Threat Model Baseline
The most relevant adversaries for Ezkey Mobile are:

- A user or thief with physical access to an unlocked or weakly protected device.
- Malware or tooling on a compromised, rooted, or jailbroken device.
- A reverse engineer extracting data from an APK, logs, or local storage.
- A network attacker on hostile Wi-Fi, or an attacker controlling a user-installed root CA / MDM trust context.
- A malicious or compromised operator-side integration endpoint trying to exploit mobile routing assumptions.
- An internet attacker probing the public EXP1 edge for deployment drift, weak headers, CORS mistakes, or proxy misconfiguration.

Less relevant for this assessment:
- nation-state or certification-grade laboratory attacks,
- hardware lab extraction that materially exceeds Ezkey's stated market scope,
- criticism based on missing FIDO2/WebAuthn guarantees that Ezkey does not claim to provide.

## Trust Boundaries
The review used the following trust boundaries:

- Mobile JavaScript runtime vs native crypto module.
- Local metadata storage vs secure item storage vs native private-key storage.
- Mobile app vs Auth API.
- Auth API vs Integration API.
- Public reverse proxy / CDN layer vs origin application layer.
- Honest client assertions vs server-verified security properties.

The most important honesty boundary in the current design is this: Ezkey has real cryptographic verification in its protocol, but it does not yet have end-to-end attestation-grade proof for mobile hardware claims or server-verified device integrity.

## Findings

### 1. Sensitive enrollment material is stored in AsyncStorage
**Disposition:** Unacceptable defect to fix  
**Severity:** High  
**Primary impact:** Confidentiality, protocol material exposure, compromised-device abuse

The repository documents a split-storage model, but the implementation persists the full enrollment collection through AsyncStorage. The stored shape includes `enrollmentProofToken` and `integrationPublicKey`, and the tests explicitly validate that this collection is serialized through AsyncStorage.

Why this matters:
- `enrollmentProofToken` is sensitive protocol material.
- `integrationPublicKey` is not secret, but it is security-critical trust material and should be treated carefully.
- On a compromised device, emulator, rooted device, or during local extraction, these values are easier to recover than if they lived only in platform secure storage.

Important nuance:
- This is not a complete remote auth bypass by itself, because the private device key still stays in the native keystore path.
- It is still a real weakness, especially for a security product, because it increases the blast radius of local compromise and makes evidence extraction easier.

Repo evidence:
- `ezkey_mobile/app/services/storage/enrollmentStorage.ts`
- `ezkey_mobile/docs/MOBILE_DATA_MODEL.md`
- `ezkey_mobile/app/services/storage/__tests__/enrollmentStorage.test.ts`
- `ezkey_mobile/app/services/storage/secureStorage.ts`

Recommended action:
- Move `enrollmentProofToken` out of the AsyncStorage-backed collection and into the secure storage delegate.
- Reassess whether `integrationPublicKey` should stay in the main record or be integrity-protected separately.
- Update tests so the secure-storage split is enforced rather than only documented.

### 2. iOS is a deferred phase and should be framed as out of current security scope
**Disposition:** Honest scope boundary; documentation discoverability action  
**Severity:** Medium for product clarity, not a current Android security defect  
**Primary impact:** Integrity of product claims, reduction of false positives in reviews

The current Android path is the active mobile security reference implementation. iOS groundwork exists in the repository, but iOS is not a short-term product target and should not be treated as a parity expectation for the present assessment window.

That means two things:
- the Android implementation should continue to be assessed on its own merits,
- and repository/docs outputs should clearly say that iOS is a later phase so reviewers do not keep generating false positives about parity gaps that are currently expected.

The current iOS code remains materially different:
- the Swift module still exposes RSA key generation and RSA signing entry points,
- the bridge method names do not align with the JavaScript interface expected by `nativeCrypto`,
- the storage tier currently returns `NONE`,
- the docs already say iOS parity is not complete.

Those differences matter mainly as a documentation and positioning boundary today, not as a blocker on the current Android-first security posture.

Repo evidence:
- `ezkey_mobile/ios/EzkeyMobile/Crypto/EzkeyCryptoModule.swift`
- `ezkey_mobile/ios/EzkeyMobile/Crypto/EzkeyCryptoModuleBridge.m`
- `ezkey_mobile/app/services/crypto/nativeCrypto.ts`
- `ezkey_mobile/app/services/crypto/cryptoService.ts`
- `ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`
- `ezkey_mobile/docs/NATIVE_MODULES.md`

Recommended action:
- State explicitly in discoverable docs that current mobile security conclusions are Android-first and that iOS belongs to a later phase.
- Treat Android as the reference-strength path and iOS as explicitly deferred until EC P-256 parity is intentionally pursued.
- Do not imply Secure Enclave or equivalent server-relevant assurance until the implementation and validation model genuinely support it.

### 3. Transport trust is standard TLS only; mobile origin binding is not hardened yet
**Disposition:** Follow-up hardening opportunity  
**Severity:** Medium  
**Primary impact:** MITM resilience under hostile trust-store conditions

The mobile HTTP client centralizes transport, but certificate pinning is not implemented. The app currently relies on platform trust, ATS on iOS, HTTPS-only validation in URL parsing for non-dev hosts, and standard TLS verification.

That is acceptable for many products, but for a security product it leaves a clear attack avenue in environments where:
- the attacker controls a trusted root on the device,
- the device is under hostile MDM control,
- the user accepts an intercepting root CA,
- or a malicious enterprise network terminates traffic through a trusted interception layer.

Repo evidence:
- `ezkey_mobile/app/services/api/httpClient.ts`
- `ezkey_mobile/app/utils/urlValidation.ts`
- `ezkey_mobile/android/app/src/debug/AndroidManifest.xml`
- `ezkey_mobile/ios/EzkeyMobile/Info.plist`
- `ezkey_mobile/PRD.md`

Important nuance:
- This is not evidence that Ezkey is remotely MITM-broken on the public internet.
- It is evidence that the client does not yet add an app-level trust anchor beyond the operating system trust store.

Recommended action:
- Decide whether SPKI pinning, certificate pinning, or a comparable trust-binding strategy is worth adding for the target market.
- If not implemented soon, reflect that honestly in security positioning.

### 4. Debug and development paths can expose security-relevant material
**Disposition:** Medium-priority hardening  
**Severity:** Medium in development/support builds, Low in default production posture  
**Primary impact:** Local secret leakage through logs or UI diagnostics

Two debug-related concerns stand out:

1. In development mode, the enrollment wizard logs the raw QR payload and parsed QR content.
2. When `EZKEY_PENDING_AUTH_DEBUG_PANEL` is enabled, the app can render detailed pending payload diagnostics, hashes, payload previews, key prefixes, and signature-related artifacts on screen.

These are not default production behaviors, but they raise real risk in QA, support, demo, or pilot environments where screenshots, screen recordings, logcat captures, or bug reports circulate informally.

Repo evidence:
- `ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx`
- `ezkey_mobile/app/screens/PendingAuth/PendingAuthScreen.tsx`
- `ezkey_mobile/app/config/env.ts`

Recommended action:
- Remove raw QR payload logging even in `__DEV__` unless a tightly controlled feature flag is set.
- Treat the pending debug panel as a support-only diagnostic feature with stricter redaction.
- Add a short operator rule that screenshots and recordings must never contain real enrollment material.

### 5. EXP1 is a Cloudflare-fronted origin and should be assessed as such
**Disposition:** Clarified deployment boundary; validate edge-vs-origin policy intentionally  
**Severity:** Low to Medium depending on intended Cloudflare policy  
**Primary impact:** Attribution accuracy, public-edge policy clarity, documentation alignment

The checked-in experimental deployment docs already describe EXP1 as a Cloudflare-proxied setup where Caddy acts as the origin-facing TLS endpoint for Cloudflare rather than as a directly exposed public origin. That materially changes how public observations should be interpreted.

Observed live results:
- `https://exp1-auth-api.ezkey.org/api/v1/public/instance-info` and `https://exp1-admin-api.ezkey.org/api/v1/public/instance-info` are publicly reachable.
- The public hostname accepted TLS 1.2 during a passive probe, but in a Cloudflare orange-proxy model that reflects the effective **Cloudflare edge** posture presented to the internet, not necessarily the TLS policy enforced by the Caddy origin.
- Live headers show `Strict-Transport-Security: max-age=0`, which means HSTS is effectively disabled.
- Live headers show `X-Frame-Options: SAMEORIGIN`, while the checked-in Caddyfile expresses `DENY`.

With the Cloudflare-fronted model clarified, the right conclusion is narrower:
- the TLS 1.2 observation should **not** be treated as evidence that the origin Caddy configuration is weak or incorrectly applied;
- it should instead be treated as evidence of the current **public edge** behavior seen by internet clients;
- the remaining questions are whether that edge behavior is intentional, and whether Cloudflare and origin policies are documented clearly enough for reviewers and operators.

The origin-hardening posture remains meaningful:
- the repo playbook describes Cloudflare proxying and origin certificates,
- the deployment notes recommend allowing only Cloudflare IPs to reach the origin,
- and the Caddy TLS 1.3-only block still matters for Cloudflare-to-origin traffic if that is the chosen operator policy.

Repo evidence:
- `experimental-hybrid/lightsail/Caddyfile`
- `experimental-hybrid/DEPLOYMENT_PLAYBOOK.md`
- `experimental-hybrid/README.md`
- live passive checks against `exp1-auth-api.ezkey.org`, `exp1-admin-api.ezkey.org`, and `exp1-integration-api.ezkey.org`

Recommended action:
- Keep the analysis split between **Cloudflare edge posture** and **Cloudflare-to-origin posture**.
- Verify the actual Cloudflare zone TLS and header settings before treating public TLS 1.2 support as a hardening failure.
- Decide intentionally whether public clients should be allowed to negotiate TLS 1.2 at the Cloudflare edge, even if origin traffic stays stricter.
- Reconcile Cloudflare settings, origin config, and operator docs so reviewers do not confuse edge policy with origin policy.

### 6. Rooted-device, jailbreak, and runtime-tamper resistance are currently minimal
**Disposition:** Honest boundary and hardening opportunity  
**Severity:** Medium as a market-position discussion, not a core protocol defect  
**Primary impact:** Device compromise resilience

The current mobile surface shows no strong rooted-device, jailbreak, app-integrity, or runtime-tamper detection layer. For Ezkey's target market, that may be acceptable if it is presented honestly. It is not acceptable if the product is described as if it had strong endpoint integrity guarantees.

Repo evidence:
- no strong rooted/jailbreak/app-integrity controls surfaced in `ezkey_mobile`
- current docs emphasize protocol integrity and native key storage, not device-attestation or runtime-integrity guarantees

Recommended action:
- Keep this in the hardening backlog, but do not let it displace higher-value fixes such as local storage handling and deployment drift.
- Treat it as a tier-two improvement, not a blocker for the current product market.

## CVE And Supply-Chain Triage

### Runtime mobile dependencies
The direct runtime mobile dependency picture is better than the dev-tooling picture.

- `axios@1.15.2`: recent advisories found for earlier ranges such as `<=1.15.0` do not directly apply to the pinned version reviewed here.
- `follow-redirects`: the lock resolves `1.16.0`, which is the patched line for the April 2026 custom-auth-header redirect leak advisory.
- `react-native-keychain@10.0.0`: no direct published security advisory was found in the latest package line reviewed here.
- `react-native-vision-camera@4.7.2`: no direct published security advisory was found in the reviewed package line.
- `react-native@0.85.2`: no published GitHub security advisories were surfaced in the review.

Conclusion:
- No high-confidence direct runtime CVE currently stands out as the main mobile security risk.
- The more important current risks are implementation and deployment posture issues.

### Tooling and developer-workstation exposure
The `yarn npm audit --all --recursive` output reports multiple advisories in the development and build toolchain, including `flatted`, `tar`, `minimatch`, `picomatch`, `lodash`, and related transitive dependencies.

This matters, but mainly as:
- developer workstation risk,
- CI/codegen risk,
- and supply-chain hygiene debt.

It does **not** appear to materially change the threat model of the shipped mobile runtime.

### Code generation tooling
`orval` has had serious code-generation injection advisories in 2026, but the project uses `orval@8.9.0`, which is newer than the vulnerable ranges reviewed in this assessment.

The remaining risk is procedural:
- only generate clients from trusted OpenAPI specs,
- keep generation tooling current,
- treat spec ingestion as part of the supply-chain surface.

## Attack Scenarios Worth Running Next
These are the highest-value simulations for a follow-up execution pass.

### Scenario A: Local extraction from compromised mobile device
Objective:
- confirm exactly what an attacker can recover from app storage on Android debug/emulator and rooted Android paths.

Focus:
- `enrollmentProofToken`,
- installation routing data,
- integration verification key,
- residual logs and debug artifacts.

Expected value:
- validates the real severity of the AsyncStorage finding.

### Scenario B: Hostile QR / routing misdirection
Objective:
- feed the app malicious but syntactically valid QR payloads that point to attacker-controlled HTTPS hosts.

Focus:
- whether the app will enroll against a malicious Auth API if the QR payload is trusted,
- how clearly the trust boundary is communicated to the user,
- whether any hostname allowlist or installation trust memory would materially help.

Expected value:
- clarifies whether this is an acceptable enrollment trust assumption or a product hardening gap.

### Scenario C: MITM under hostile trust-store conditions
Objective:
- test traffic interception with a user-installed or enterprise-installed root certificate.

Focus:
- confirm that standard TLS validation succeeds,
- verify how much protocol integrity still protects the user experience,
- identify what a network attacker can learn or influence without private key extraction.

Expected value:
- validates the practical impact of the no-pinning posture.

### Scenario D: Replay and tamper around `pending` and `respond`
Objective:
- use the local stack and Crypto API to replay stale tokens, alter signed payload fields, and reorder flow steps.

Focus:
- server fail-closed behavior,
- mobile fail-closed behavior,
- operator-visible error shaping.

Expected value:
- strengthens confidence in the protocol design itself, or reveals gaps in edge-case handling.

### Scenario E: Proxy and rate-limit misconfiguration review on EXP1-like stacks
Objective:
- validate whether spoofable proxy headers or incorrect trusted-proxy CIDRs could weaken rate limiting or audit attribution.

Focus:
- `CF-Connecting-IP`,
- `X-Forwarded-For`,
- effective client-IP attribution,
- rate-limit enforcement.

Expected value:
- high-value operational assurance for public deployments.

## Hardening Backlog

### Quick wins
- Move `enrollmentProofToken` into secure storage.
- Remove raw QR logging from `__DEV__` unless an additional explicit debug gate is enabled.
- Tighten or redact the pending debug panel.
- Reconcile EXP1 public header posture with intended hardening.

### Medium-effort improvements
- Implement or explicitly reject certificate/SPKI pinning as a product decision.
- Complete iOS crypto parity or narrow iOS product claims further.
- Add a storage review test suite that enforces the intended secure-storage split.

### Deferred or conditional improvements
- Rooted/jailbreak detection.
- Play Integrity / DeviceCheck style signals.
- Stronger release artifact attestation or advanced tamper resistance.

## Honest Positioning Boundaries
The following statements are supportable only with careful wording:

- Android Keystore use is real; StrongBox is requested when available.
- Current mobile-security conclusions are Android-first, because iOS is a later planned phase rather than a short-term parity target.
- `devicePrivateKeyStorageTier` is client-reported telemetry, not server-verified proof.
- Ezkey provides stronger protocol continuity than basic OTP or weak push-MFA patterns.
- Ezkey does **not** currently provide attestation-grade proof of endpoint integrity.
- iOS should not be described as having the same mature secure-hardware-backed path as Android today, and its deferred status should be easy to discover in the docs.
- Standard TLS is in place, but the mobile app does not yet implement app-level pinning.

## Bottom Line
The current Ezkey Mobile architecture is not obviously broken at the protocol level. The highest-priority issues are practical:

- fix local storage handling,
- tighten deployment reality to match declared hardening,
- keep iOS claims conservative,
- and decide whether transport pinning is worth the complexity for the intended market.

That is a stronger and more honest position than pretending the product already offers assurance levels it does not yet implement.
