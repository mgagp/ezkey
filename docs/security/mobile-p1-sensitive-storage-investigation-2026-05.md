# Ezkey Mobile P1 Investigation: Sensitive Enrollment Storage

## Purpose
This note investigates the P1 concern around `enrollmentProofToken` storage in Ezkey Mobile.

The goal is to answer four questions:
- What is the current local data model after the recent `Enrollment` / `Installation` refactor?
- Where does the sensitive material actually live at runtime and at rest?
- Does the current Android Keystore / StrongBox path protect `enrollmentProofToken` in the way one might expect?
- What should be changed before Play Store release if the intended model is "sensitive protocol material encrypted at rest and only unlocked for use at runtime"?

## Short Answer
The current implementation does **not** protect `enrollmentProofToken` the way you described.

Today:
- the device private key stays in the native keystore path,
- but `enrollmentProofToken` is persisted directly inside `StoredEnrollment`,
- and `StoredEnrollment` is serialized as JSON into the AsyncStorage-backed enrollment collection.

There is currently **no** implemented path where:
- the token is encrypted with a key derived from or wrapped by the Android Keystore / StrongBox key,
- the token is decrypted only at runtime for `pending`,
- or visibility of sensitive enrollment data is gated by unsealing through the device key.

So the strongest conclusion is:

**The current security boundary protects the device private key, but not the enrollment proof token at rest.**

## The Current Data Model

### Conceptual structure after the refactor
The recent documentation is actually quite clear about the new conceptual model.

The durable local center is:
- `Installation`: normalized Ezkey site / trust-zone object derived from `authUrl`
- `EnrollmentSummary`: mobile-facing business metadata
- `StoredEnrollment`: `EnrollmentSummary` plus the crypto material needed for later auth flows

That model is documented in:
- `ezkey_mobile/docs/MOBILE_DATA_MODEL.md`
- `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`
- `ezkey_mobile/docs/MOBILE_API_MAPPINGS.md`

### `Installation`
`Installation` is a first-class local concept used to normalize and group enrollments by Auth API host / trust zone.

It includes:
- normalized `authUrl`,
- normalized `installation.id`,
- host,
- human-facing instance name / description,
- metadata freshness timestamp.

This part is not the core problem. It is presentation and routing metadata.

### `StoredEnrollment`
The actual local persisted enrollment record is the critical part.

Per `ezkey_mobile/docs/MOBILE_DATA_MODEL.md`, `StoredEnrollment` intentionally contains:
- enrollment identity and display fields,
- nested `installation`,
- `enrollmentProofToken`,
- `integrationPublicKey`,
- plus friendly labels such as `enrollmentName` / `deviceLabel`.

This is also reflected directly in code:
- `ezkey_mobile/app/services/storage/enrollmentStorage.ts`
- `ezkey_mobile/app/services/api/types.ts`

So the refactor did improve conceptual clarity around `Installation`, but it did **not** produce an encrypted-at-rest split for the proof token.

## The Actual Write Path

### Where the token enters persistent storage
During enrollment finalization, the wizard builds the local record and persists it.

In `ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx`, after successful verify-result signature validation, the app constructs:

- `record.enrollmentProofToken = draft.enrollmentProofToken`
- `record.integrationPublicKey = draft.integrationPublicKey`
- `record.installation = ...`

Then it calls:
- `saveEnrollment.mutateAsync(record)`

### Where the record is saved
`useSaveEnrollment()` in `ezkey_mobile/app/hooks/useEnrollments.ts` calls:
- `enrollmentStorage.saveEnrollment(record)`

And `enrollmentStorage.saveEnrollment(...)` does this:
- loads the current collection from AsyncStorage,
- replaces or appends the full `StoredEnrollment`,
- serializes the full array with `JSON.stringify(nextItems)`,
- writes it back through `AsyncStorage.setItem(...)`.

That means the persisted record includes the proof token directly in the AsyncStorage collection.

### Proof from the current docs
The most explicit documentation is in `ezkey_mobile/docs/MOBILE_DATA_MODEL.md`:

- `StoredEnrollment` is sensitive because it contains `enrollmentProofToken` and `integrationPublicKey`
- `enrollmentProofToken` storage location: `StoredEnrollment` in AsyncStorage-backed collection
- `integrationPublicKey` storage location: `StoredEnrollment` in AsyncStorage-backed collection
- secure-storage wrapper exists, but the current enrollment storage class persists the main collection in AsyncStorage

`ezkey_mobile/docs/MOBILE_API_MAPPINGS.md` says the same thing:
- `StoredEnrollment.enrollmentProofToken` is persisted and used later by `pending`
- `StoredEnrollment.integrationPublicKey` is persisted for signature verification

So the implementation and the recent conceptual docs are aligned with each other, even though that alignment is not what was apparently intended from a security perspective.

## Why StrongBox Is Not Protecting This Data Today

### What StrongBox actually protects in the current app
The current Android native crypto module protects the **private signing key**.

In `ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt`:
- an EC P-256 keypair is generated in Android Keystore,
- StrongBox is requested when available,
- the key is used for signing,
- the private key is not exported.

That part is real and valuable.

### What the native crypto module does not do
The native crypto module does **not** expose any method for:
- encrypting arbitrary application data,
- decrypting arbitrary application data,
- wrapping / unwrapping an AES key for storage encryption,
- sealing / unsealing `enrollmentProofToken`,
- or deriving a local content-encryption key for the enrollment record.

Its exposed responsibilities are basically:
- generate enrollment keypair,
- get public key,
- sign,
- verify integration signature,
- report storage tier,
- generate proof token,
- delete keypair.

That is a signing-oriented design, not a local secret-encryption design.

### Important additional nuance: the key is not user-auth gated
In the Android key-generation path, the builder explicitly sets:
- `setUserAuthenticationRequired(false)`

That means the signing key is not even configured as a "must re-authenticate before use" key.

So even if the app had chosen to bind local secret visibility to key usage, the current key configuration would not enforce a biometric/PIN gate.

### StrongBox here is not a data-vault
Right now, StrongBox is:
- a better storage location for the **private signing key**
- not a storage or decryption mechanism for the **proof token**

That distinction is the core misunderstanding behind the current P1 concern.

## Why This Probably Felt Like It Was Protected
There are two reasons this could easily have been misunderstood.

### 1. Some docs still say the opposite
Several docs still express the intended or older security story:

- `ezkey_mobile/README.md`: "sensitive values are stored via secure storage abstractions only"
- `ezkey_mobile/AGENTS.md`: "proof tokens stay in memory or secure storage only"
- `ezkey_mobile/PRD.md`: "No sensitive tokens stored in plain AsyncStorage"
- `ezkey_mobile/docs/MOBILE_ARCHITECTURE.md`: "Proof tokens are handled in memory or secure storage only"

But the newer conceptual docs say the actual current behavior:
- `ezkey_mobile/docs/MOBILE_DATA_MODEL.md`
- `ezkey_mobile/docs/MOBILE_API_MAPPINGS.md`
- `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`

So the repository currently contains a **documentation contradiction**:
- some files describe the intended security model,
- other files describe the real implemented model.

### 2. The secure-storage wrapper exists but is unused for the main enrollment record
`ezkey_mobile/app/services/storage/secureStorage.ts` exists and wraps `react-native-keychain`.

But `enrollmentStorage.ts` never calls:
- `this.secure.setItem(...)`
- `this.secure.getItem(...)`
- `this.secure.removeItem(...)`

It injects `secureStorage` into the class constructor, but the current implementation does not use it for the persisted enrollment collection.

That makes it look like a split-storage system exists when, in practice, the proof token still rides inside the AsyncStorage JSON blob.

## Security Impact

### What is still well protected
- The device private key is still the strongest secret and remains in the native keystore path.
- An attacker who only steals `enrollmentProofToken` does not automatically gain full ability to sign `pending` or `respond`.
- The protocol still requires valid device signatures.

### Why this is still critical
Even so, persisting `enrollmentProofToken` in AsyncStorage is a serious issue for a security product because:

- it weakens the local-at-rest story,
- it increases extraction exposure on rooted devices, emulators, backups, or debug paths,
- it creates a gap between claimed and actual security posture,
- and it undermines trust in the mobile-side handling of protocol material.

For Ezkey, this matters because the proof token is not random UI metadata. It is persistent protocol material used to prove enrollment association during `pending`.

## Recommended Direction

### Immediate conclusion
If the intended model is:

> sensitive local protocol material is encrypted at rest and only unsealed for use at runtime,

then the current implementation does **not** meet that requirement.

### Recommended remediation model
The cleanest direction is:

1. Keep `Installation` and non-sensitive `EnrollmentSummary` metadata in AsyncStorage.
2. Move `enrollmentProofToken` out of the AsyncStorage collection.
3. Store the proof token in secure storage or in an encrypted blob protected by a keystore-managed wrapping key.
4. Rehydrate the proof token only when building a `pending` request.
5. Keep `integrationPublicKey` separate from the proof token decision:
   - it is not secret,
   - but it is trust-critical,
   - so decide whether it stays in AsyncStorage, moves with the secure split, or gains integrity protection separately.

### Important design choice
There are two reasonable implementation approaches:

#### Option A: Simple secure-storage split
- Store `enrollmentProofToken` in `react-native-keychain` keyed by enrollment ID.
- Keep the main `StoredEnrollment` metadata in AsyncStorage.
- Load the token from secure storage only when needed.

Pros:
- simpler,
- lower refactor risk,
- probably enough for the current product phase.

Cons:
- depends on the behavior and limits of the platform credential store abstraction,
- may not give the exact "sealed by signing key" model you had in mind.

#### Option B: Explicit encrypted-blob design
- Generate or manage a dedicated local content-encryption key through Android Keystore.
- Encrypt the proof token before placing it in local storage.
- Decrypt only when needed for `pending`.

Pros:
- matches the "encrypted at rest, unsealed at runtime" mental model more closely,
- clearer cryptographic story.

Cons:
- more implementation complexity,
- requires careful native design,
- still separate from the existing signing-key path unless you deliberately redesign around a local encryption key.

### Strong recommendation for this release window
For Play Store readiness, Option A is the pragmatic first fix.

Why:
- it closes the current contradiction fastest,
- it materially improves the local storage posture,
- it aligns with the existing secure-storage abstraction,
- and it avoids trying to redesign the whole crypto/storage architecture under release pressure.

Then, if desired, a later iteration can move toward a stronger sealed-blob model.

## Documentation Cleanup Required
Before or alongside the code fix, the docs should be reconciled.

At minimum:
- keep the conceptual docs accurate,
- remove any claim that sensitive tokens are already secure-only if they are still in AsyncStorage,
- or, preferably, make the implementation match that claim and then keep only the corrected version.

The current contradiction is dangerous because it can make maintainers and reviewers believe the problem is already solved.

## Bottom Line
The current Android keystore / StrongBox path protects the device private key used for signing.
It does **not** currently protect `enrollmentProofToken` at rest.

That is the core result of this investigation.

The recent `Enrollment` / `Installation` refactor improved the conceptual structure of the app, but it did not yet implement the secure split that would keep the proof token out of AsyncStorage.

For the current release context, the right next step is:
- treat this as a real security gap,
- move the proof token out of the AsyncStorage-backed `StoredEnrollment`,
- use secure storage immediately,
- and only then consider whether a stronger keystore-backed encryption model is worth the extra complexity.
