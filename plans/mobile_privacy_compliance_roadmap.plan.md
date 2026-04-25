# Mobile — Privacy Compliance & Security Hardening Roadmap

**Created:** April 24, 2026  
**Context:** Post-first Play Store submission (v0.0.1 tech preview). Two open items identified
during the privacy policy (`sites/ezkey-org/privacy.html` + `fr/confidentialite.html`) drafting
session. To be used as a starting brief for a future coding/planning session.

---

## Item 1 — Law 25 (Québec) Compliance Plan

### What Law 25 requires for a project at this stage

Québec's *Loi 25* (Act respecting the protection of personal information in the private sector,
in force since September 2023) imposes obligations that scale with the volume and sensitivity of
personal information processed. For Ezkey — a project that operates no central server and
processes no personal data on behalf of the project — the obligations are light but real:

- **Privacy policy** — ✅ done (`privacy.html` / `confidentialite.html`, published April 24, 2026)
- **Privacy officer designation** — the project lead (Marc Gagnon) is the de facto officer;
  `privacy@ezkey.org` alias created and documented in the policy.
- **Privacy impact assessment (PIA)** — required before launching any new project or system
  involving personal information. For v0.0.1 (no server-side processing by the project), a
  lightweight self-assessment document is sufficient.
- **Incident response procedure** — must exist in writing. Even minimal ("notify affected
  organization, notify CAI if applicable").
- **Data retention policy** — for on-device data: addressed in the privacy policy (uninstall
  removes all data). For backend data: delegated to each organization's own policy.
- **Transparency obligations** — covered by the privacy policy and the two-layer model
  explanation.

### What a "Law 25 compliance work plan" session should produce

1. A lightweight **Privacy Impact Assessment (PIA)** document scoped to the Ezkey mobile app
   and ezkey.org — one to two pages, self-assessment format, documenting what data flows exist,
   who controls what, and what mitigations are in place.
2. A minimal **Incident Response Procedure** — a short procedure document covering: detection,
   classification, notification to affected organization, CAI notification threshold (risk of
   serious harm), timeline (72h for CAI under Law 25 amendments).
3. A review of whether `privacy.html` needs a **"Privacy Officer" named section** to fully
   satisfy Law 25 article 3.1 obligations.

### Key reference
- CAI (Commission d'accès à l'information du Québec): https://www.cai.gouv.qc.ca
- Law 25 text: https://www.legisquebec.gouv.qc.ca/en/document/cs/P-39.1

---

## Item 2 — Mobile: Biometric / In-App Authentication Gate

### What this is

A feature that requires the user to authenticate (biometric or PIN) **within the app** before
performing a sensitive operation — specifically before responding to a pending authentication
challenge. This is distinct from unlocking the phone; it is an explicit in-app confirmation
step.

The mobile PRD already flags this as out of scope for v1 iteration but explicitly marked
extensible: *"Device authentication (PIN/biometric) is out-of-scope for the first iteration
but must remain extensible."* (`ezkey_mobile/PRD.md`, line 19)

### Why it matters

- Without this gate, any person who picks up an unlocked phone can approve an auth challenge
  in the Ezkey app without proving it is the enrolled user.
- The Android Keystore key already has `setUnlockedDeviceRequired(true)` — the device must be
  unlocked, but the app adds no further confirmation layer.
- Comparable apps (Google Authenticator, Aegis, Duo) offer optional biometric lock.
- For the business-domain approval use case (contextual title/message — e.g. approving a
  payment batch), the absence of an in-app gate is a meaningful security gap.

### Proposed implementation scope (for planning session)

**Option A — App-level lock (screen unlock pattern)**  
Require biometric/PIN on app resume from background. Implemented with
`react-native-biometrics` or `react-native-keychain`'s biometric prompt APIs.

**Option B — Per-operation gate (higher security, more friction)**  
Require biometric confirmation specifically before submitting the respond call. Can be
combined with Android Keystore `setUserAuthenticationRequired(true)` on the signing key —
this ties the private key unlock to a biometric event at the OS level, not just the app level.
This is the strongest option and aligns with the Ezkey trust model.

**Recommendation:** Option B for a future Phase 2 milestone, with Option A as a stepping stone
and optional user preference.

### Technical starting points

- `react-native-keychain` (already a dependency) supports biometric prompts via
  `getSupportedBiometryType()` and `getGenericPassword()` with biometric access control.
- Android Keystore: `setUserAuthenticationRequired(true)` + `setUserAuthenticationParameters()`
  on the `KeyGenParameterSpec` builder in `EzkeyCryptoModule.kt`.
- The `EzkeyCryptoModule.kt` native module would need a new method (e.g. `signWithBiometric`)
  that triggers the biometric prompt before invoking the signing operation.

### Privacy policy impact

When this feature is implemented, `privacy.html` section 5 (Android permissions table) must be
updated: the biometric row currently says *"declared, not active in v0.0.1"* and will need to
reflect active use and describe the user flow.

---

## Session start brief (copy-paste ready)

> We have two open compliance/security items for the Ezkey mobile app (`ezkey_mobile/`).
> Read `plans/mobile_privacy_compliance_roadmap.plan.md` for full context.
> 
> **Session goal:** [choose one]
> - (A) Produce the Law 25 PIA and Incident Response Procedure documents
> - (B) Design and implement the biometric in-app authentication gate (Option B — per-operation,
>   Keystore-backed) in `EzkeyCryptoModule.kt` and the React Native layer
