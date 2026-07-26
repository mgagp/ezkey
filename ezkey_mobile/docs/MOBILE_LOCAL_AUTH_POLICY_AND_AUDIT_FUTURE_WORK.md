# Ezkey Mobile — Local Auth, Policy, and Audit Future Work

## Purpose

This note captures the design discussion that emerged while implementing local approval confirmation on Android.
It is intentionally more reflective than the primary flow docs: the goal is to preserve the reasoning trail,
the misunderstandings that surfaced, the design corrections that followed, and the protocol-level questions that
remain open for future work.

This document is useful when future work touches any of the following:

- local device confirmation before `respond`
- user-controlled security preferences in the mobile app
- future enrollment policy delivered by the backend
- future installation-owned (organization) policy — see the 2026-07-26 addendum below
- protocol and audit-log extensions for `respond`
- the difference between declarative audit context and strong server-verifiable security guarantees

## Executive Summary

The current Android reference app now supports a local user preference that can require device confirmation before
approving or denying an authentication request. That preference is intentionally separated from the future concept
of an enrollment-level backend policy.

The key conclusions from the discussion were:

1. A local user preference and a future enrollment policy are different concepts and should not be fused.
2. The current implementation should keep the future enrollment policy in the model, but default it to
   `not-required` until the backend owns it.
3. The effective local-auth decision should be computed as:

   `enrollment policy requires auth OR user preference requires auth`

4. Lowering protection in Settings is itself a security-sensitive action and must be protected.
5. The current implementation improves honest-client behavior and local UX integrity, but it does not yet provide
   a strong cryptographic guarantee to the server that local authentication truly happened.
6. A future protocol extension may still be worthwhile to improve auditability by reporting why local authentication
   was required and what kind of local authentication was used.

## Historical Trace

### 1. Initial Phase 1 framing

The first Phase 1 implementation introduced a `Security` settings surface and a stronger local mode for approval-time
confirmation. At that stage, the feature was framed pragmatically:

- mobile-only
- Android-first
- no protocol change
- immediate UX value
- no backend policy yet

This was the right initial cut for EXP1, but it contained an important conceptual shortcut.

### 2. Where the misunderstanding came from

The original implementation threaded the user's chosen security mode into enrollment creation and stored that same
value on the enrollment record. That had one advantage: it made the existing note about re-enrollment logically
coherent. But it also fused two separate truths:

- `this user prefers confirmation on this phone`
- `this enrollment is governed by a policy that requires confirmation`

Once those two things were fused, the app naturally behaved as if changing the setting should only affect new
enrollments. That made sense inside the model, but it was surprising from a pure user-preference standpoint.

### 3. Reframed model

The discussion led to a better separation:

- the mobile app keeps a local user preference
- the enrollment carries a future-ready policy field
- for now, the enrollment policy is always `not-required`
- the effective decision is computed from both inputs

This change preserves the future backend direction without pretending that the current user preference is already an
organization-owned or enrollment-owned security policy.

## The Settings Downgrade Integrity Finding

### Problem statement

After separating user preference from enrollment policy, a new integrity issue became obvious:

- if the phone is already unlocked,
- and local protected confirmation is enabled,
- an attacker or opportunistic holder of the device could go to `Settings > Security`,
- switch the mode back to `Standard`,
- return to the approval flow,
- and approve without device confirmation.

This is a security-sensitive downgrade path. The issue is not concurrency. The user is not in two places at the same
time. The issue is the functional sequence that allows protected mode to be disabled just before the sensitive action.

### Resolution adopted

The chosen resolution was intentionally simple and local:

- upgrading from `Standard` to `Confirm before approvals` stays immediate
- downgrading from `Confirm before approvals` to `Standard` now requires device confirmation before the new value is saved

This keeps ownership of the preference with the user while still protecting the act of lowering protection.

## Current Android Authenticator Posture

For both approval-time confirmation and Settings downgrade confirmation, the current Android posture is:

- `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`

Meaning:

- Android may accept a strong biometric when available
- otherwise Android may use the device credential (PIN, pattern, or password)

### Why this posture was chosen

- It is stronger than accepting weak biometrics.
- It remains usable on real phones where strong biometrics may not be configured but the device credential is.
- It gives a practical EXP1 security improvement without introducing a brittle or exclusionary UX.

### What we are not doing now

- no weak-biometric policy surface
- no biometric-only strict mode in the product UI
- no multi-level end-user assurance taxonomy

Those could be revisited later, but they are intentionally out of scope for the current implementation.

## Re-authentication Cadence and Grace Periods

Another important design question is not just `whether` local confirmation is required, but `how often` it should be required.

### Current implementation posture

The current Android implementation is intentionally simple:

- when protected approval is active, each individual `respond` action triggers a fresh local-auth prompt
- there is no app-managed grace period
- there is no `while the app stays open` trust window
- there is no configurable re-authentication timeout yet

That means the current behavior is effectively:

- one local confirmation per approval or denial decision

This is consistent with the current mobile-first, declarative, low-complexity implementation.

### Why this default is defensible

For an approval action, per-action confirmation is a strong and easy-to-explain default.

Advantages:

- clear mental model for the user
- low ambiguity in audits and future reasoning
- lower risk of accidental approval during a stale trust window
- no need to explain session semantics in EXP1

This is especially reasonable because `respond` is a sensitive, explicit, human approval action rather than a high-frequency background operation.

### Why large grace periods are risky

A grace period can improve comfort, but the tradeoff becomes real very quickly.

Examples of weak defaults:

- `no re-authentication needed for one hour`
- `no re-authentication while the app stays active`

These are generally poor defaults for a security-sensitive approval flow, because:

- app foreground state is not the same thing as verified user presence
- an unlocked or recently unlocked device can be handed to someone else
- a long trust window materially weakens the value of the protected mode
- operator expectations become harder to manage because the label sounds stronger than the real enforcement cadence

In short, a long grace period tends to move the feature back toward convenience at the expense of the very integrity it was meant to add.

### Better practice ranges

Good practice depends on the sensitivity of the action and the frequency of repeated approvals.

For a flow like Ezkey mobile approval, the most defensible options are usually:

1. `every approval requires confirmation`
2. `a very short grace window for burst activity`

If a grace period is ever introduced, it should be short enough that it still feels tied to the user's immediate intention.

Reasonable future candidates to evaluate:

- `no grace period`
- `30 seconds`
- `1 minute`
- `2 minutes`

These ranges are much easier to justify than `15 minutes`, `1 hour`, or `until app close`.

### Product recommendation for Ezkey

For the current product phase, the most pragmatic recommendation is:

- keep `per-respond` confirmation as the default and only behavior for now

Why:

- it matches the current implementation
- it is easy to explain
- it preserves the integrity intent of the feature
- it avoids introducing a misleading sense of strong protection while silently keeping a broad trust window

### Should this be user-configurable?

Probably not at the current phase.

Giving end users many timing choices too early introduces several problems:

- more UI complexity
- harder product messaging
- weaker supportability
- users may choose convenience-heavy values without understanding the security impact

For EXP1, a small number of strong defaults is preferable to a timing matrix.

### Should this become policy-controlled later?

Yes, eventually this is more naturally a policy topic than a pure end-user preference topic.

The likely long-term model is:

- the user can express a local preference for stronger confirmation
- the backend or enrollment policy can impose a minimum cadence
- the effective behavior becomes the stricter of the two

Conceptually, future policy dimensions could include:

- require confirmation for every approval
- allow a short grace period
- define the acceptable local-auth method
- define whether the rule is advisory, audit-relevant, or mandatory

This fits the broader design direction already discussed in this note: keep user preference and authoritative policy separate.

### Good future sequencing

The recommended order of maturity is:

1. keep the current `every respond` behavior
2. if user friction becomes real, evaluate a very short grace period experimentally
3. only later consider making cadence part of backend-owned policy
4. if stronger cryptographic binding is added in the future, re-evaluate cadence in that new model rather than carrying over a convenience-first assumption

### Practical conclusion

For the current Ezkey mobile security feature, the cleanest answer is:

- yes, the app currently re-prompts on every protected `respond`
- yes, that is a reasonable default
- no, a long grace period should not be the default
- if a grace period is ever introduced, it should be short, explicit, and eventually policy-aware

## Important Security Caveat: Local Enforcement vs Strong Integrity

During the discussion, another issue surfaced that is separate from the Settings downgrade finding but still important.

## Educational Note: What "Cryptographic Proof of Local Authentication" Actually Means

This section captures an important conceptual clarification that emerged during the discussion.

The easy mistake is to blur together these three ideas:

- the private key is stored securely on the phone
- the app showed a biometric or device-credential prompt before signing
- the server has a strong cryptographic proof that local authentication happened as part of the signing operation

These are not the same thing.

### Level 1 — Secure key storage

At the first level, the private key is protected by the platform:

- the key lives in Android Keystore
- StrongBox is requested when available
- private key material is not exposed to application code

This is already valuable. It means the app does not simply keep a raw signing secret in JavaScript or in ordinary app
storage.

But by itself, this does not prove that the user authenticated locally just before a given `respond` signature.

### Level 2 — App-enforced local confirmation

At the second level, the app asks Android to show a local-auth prompt before it signs.

Conceptually:

1. the app decides local confirmation is required
2. Android shows biometric or device-credential UI
3. the user succeeds
4. the app then signs with the enrollment key

This is stronger than doing nothing. It improves:

- honest-client behavior
- user-facing security
- resistance to casual misuse on a real phone

But it is still, fundamentally, an application-orchestrated sequence.

The important limitation is that the signature operation and the local-auth prompt are still modeled as two separate
steps. The backend sees a valid signature, but it does not automatically receive a formal, independently verifiable
cryptographic guarantee that the prompt was inseparably part of the signature operation.

This is the current Ezkey Android posture.

### Level 3 — Key usage cryptographically gated by local authentication

At the third level, the platform is configured so that the private key itself cannot be used unless local
authentication has been satisfied according to the key's policy.

That is the important conceptual shift.

The model becomes:

- not merely `the app asked for authentication before signing`
- but rather `the signature could not have been produced unless the platform accepted the required local authentication`

This is much stronger, because the trust boundary moves away from app control and into platform-enforced key usage.

In plain language:

- weaker model: the app claims it performed local auth, then signs
- stronger model: the platform refuses to let the key sign unless local auth happened

That is what makes the second model much more interesting for a security protocol.

### Why the stronger model is still not automatically a full server proof

Even with an auth-bound key, there is still an extra question:

- can the backend verify that property as part of the protocol?

Those are related but distinct ideas.

An auth-bound key can give a much stronger real security property on the device itself. But for the server to treat
that property as formally verified, the protocol usually needs more than just a normal business signature.

Possible additional building blocks include:

- key attestation
- validation of platform certificate chains
- explicit protocol semantics about assurance level
- backend logic that distinguishes audit metadata from verified security guarantees

So the progression is typically:

1. secure key storage
2. app-enforced local prompt
3. platform-enforced auth-bound key usage
4. optional server-verifiable attestation and policy enforcement

Only the fourth stage starts to justify stronger server-side claims.

### Why this matters so much for Ezkey

Ezkey already has a cryptographic chain around challenge, response, and signature verification. The concept discussed
here is valuable because it could potentially strengthen the `respond` leg of that chain.

The relevant future ambition would be:

- not only `this response was signed by the enrolled device key`
- but eventually `this response was signed by the enrolled device key under a platform-enforced local-auth condition`

That is potentially a major qualitative improvement.

### Why this cannot be bolted on casually

The discussion also revealed why this must be approached carefully.

Binding local auth directly into key usage changes the lifecycle and usability of the key. It raises practical design
questions such as:

- which operations must require local auth
- whether enrollment and respond should use the same key
- whether silent/background operations must remain possible
- whether the key should require auth on every use or within a time window
- how recovery, rotation, and re-enrollment should behave

This is why a rushed attempt to make the enrollment key itself auth-bound created functional breakage earlier in the
session. The concept is powerful, but it is architecture-level, not just UI-level.

### Best concise mental model

The simplest accurate distinction is:

- declarative mode: `the client says it asked for local auth`
- cryptographically linked mode: `the signature could not exist unless local auth happened because the platform gated key usage`

And after that, there is one more level:

- server-verifiable mode: `the backend can validate that this stronger property was really present and is not merely asserted`

That is the conceptual ladder.

### The caveat

Today, the mobile app can say:

- `before this respond action, I required local device confirmation`

But the server does not currently have a cryptographic proof that this local-auth step was inseparably bound to the
signature operation itself.

In other words:

- the app can enforce a real local user experience on an honest client
- the server cannot yet independently verify that this happened as a strong security fact

### Why this matters

That means the current design improves:

- user-facing security
- resistance to casual misuse on the real phone
- local flow integrity

But it does not yet provide:

- a backend-verifiable proof that local authentication happened
- a cryptographically attested assurance level for the `respond` signature

This distinction should remain explicit in future product and protocol work.

## Audit and Protocol Discussion

The discussion then moved from enforcement integrity to auditability.

### The valuable intuition

Even before strong server-verifiable guarantees exist, it may still be valuable for the mobile app to report more
security context when it submits `respond`, so the backend can persist richer audit information.

This is valuable for:

- audit readability
- operator understanding
- future troubleshooting
- phased protocol evolution

### The key caution

If the mobile app reports local-auth context today, that information should be treated as:

- useful audit context
- helpful client-reported security metadata

and not as:

- strong cryptographic proof
- server-verified assurance

That distinction is critical. A good future design should preserve the audit value without overstating the guarantee.

## What Could Be Reported in a Future `respond` Extension

The discussion explored several shapes for future reporting.

### Option A — report only the final fact

Example idea:

- `localAuthPerformed = true | false`

Pros:

- minimal
- easy to understand

Cons:

- loses why the auth happened
- loses which policy or preference triggered it
- loses what method Android actually used

### Option B — report the two causes independently plus the final result

Example idea:

- `enrollmentPolicyRequiredAuth`
- `userPreferenceRequiredAuth`
- `effectiveLocalAuthRequired`
- `localAuthPerformed`

Pros:

- preserves the separate truths cleanly
- makes future debugging easier
- aligns with the reframed model

Cons:

- a bit more verbose
- some redundancy unless carefully documented

### Option C — report all context including method/source

Example idea:

- `enrollmentPolicyRequiredAuth`
- `userPreferenceRequiredAuth`
- `effectiveLocalAuthRequired`
- `localAuthPerformed`
- `localAuthSource = NONE | USER_PREFERENCE | ENROLLMENT_POLICY | BOTH`
- `localAuthMethod = NONE | BIOMETRIC_STRONG | DEVICE_CREDENTIAL | UNKNOWN`

Pros:

- best audit value
- best future compatibility
- allows clean operator-facing audit wording later

Cons:

- more protocol surface
- more care needed to explain which fields are declarative vs security-significant

## Recommended Direction for Future Protocol Work

The most reasonable future direction is to keep the underlying truths separate and optionally add one derived field.

### Recommended future shape

At a minimum, preserve these separate concepts:

- `enrollmentPolicyRequiredAuth`
- `userPreferenceRequiredAuth`
- `localAuthPerformed`
- `localAuthMethod`

Optionally add a derived field for readability:

- `localAuthSource = NONE | USER_PREFERENCE | ENROLLMENT_POLICY | BOTH`

### Why this is better than a single fused flag

- It keeps the future backend-owned policy distinct from the user-owned preference.
- It matches the current mobile mental model.
- It gives better audit value.
- It avoids ambiguity in future investigations.

## Informational vs Security-Significant Fields

One of the most important outcomes of the conversation was the need to classify future fields by their meaning.

### Informational-only or declarative fields

These are useful for audit but should not be marketed as proof on day one:

- `userPreferenceRequiredAuth`
- `enrollmentPolicyRequiredAuth` (until server-owned and trusted end to end)
- `localAuthPerformed`
- `localAuthMethod`
- `localAuthSource`

### Stronger security-significant future fields

These would require a later protocol/security design to justify stronger claims:

- any field that claims local auth was cryptographically bound to the signature
- any field that claims hardware attestation or server-verified assurance
- any field used as a hard backend authorization guarantee rather than an audit statement

## Recommended Future Workstreams

### Workstream 1 — Audit-first protocol extension

Goal:

- enrich `respond`
- persist richer audit facts
- do not yet claim strong proof

This is a moderate, high-value future step.

### Workstream 2 — Stronger local-auth integrity model

Goal:

- analyze whether local authentication can become technically inseparable from the signature operation
- revisit auth-bound key usage, `CryptoObject`, or other platform-backed enforcement approaches
- decide what the backend can honestly rely on

This is a deeper security and protocol investigation, not just an audit enhancement.

### Workstream 3 — Enrollment policy from backend

Goal:

- make enrollment policy real and backend-owned
- decide where the policy lives and how it is propagated
- define how it combines with user preference and how it is audited

This remains a separate future phase, but the current mobile model has been prepared for it.

## Addendum (2026-07-26) — A Third Policy Tier: Installation-Owned Policy

A follow-up discussion widened Workstream 3 from a single "enrollment policy" concept to a
**three-tier** model. This section preserves that reasoning so it is not lost before the tracer
bullet (`TB-2026-0001`) resumes.

### Why a third tier

The original framing already separated a **local user preference** from a **future enrollment
policy**. That framing under-represents one real actor: an Ezkey **installation** — a specific
on-prem or hosted deployment, typically operated by one adopting organization — may legitimately
want to impose a security floor across *all* enrollments it issues, independent of what any single
enrollment or end user decides. Authentication is never anyone's core business except Ezkey's; the
end user generally wants friction-free authentication, while the adopting organization may
legitimately want a stronger guarantee. Both are real and neither should be assumed away.

This third tier does not require a new mobile identity concept. It reuses the installation as a
**trust zone**, already canonical since `I-2026-07-20-mobile-installation-trust-zone-canon`: every
enrollment already belongs to exactly one installation trust zone, identified by its normalized
Auth API URL.

### The generalized model

| Tier | Owner | Scope | Status today |
| --- | --- | --- | --- |
| User preference | End user | This phone, all enrollments on it | Implemented (`Security` settings) |
| Enrollment policy | Future: backend / tenant admin | One enrollment | Modeled, always `not-required` (stub) |
| Installation policy | Future: backend / Global Admin | All enrollments issued by one installation | Not modeled |

The existing merge rule generalizes cleanly from two inputs to three, preserving the same
"strongest wins" intuition:

```
effectiveLocalAuthRequired =
    installationPolicyRequiresAuth
    OR enrollmentPolicyRequiresAuth
    OR userPreferenceRequiresAuth
```

This is consistent with the Global Admin vs Tenant Admin split already used elsewhere in the
product: an installation-level floor is naturally a **Global Admin** (instance-level) concern,
while a per-enrollment override remains closer to a **Tenant Admin** (business-relationship)
concern. Neither role is designed in this addendum — only the conceptual anchor is recorded.

**Note (2026-07-26) — the installation trust-zone anchor just got stronger.** A same-day hygiene
fix, `MOB-017` (`ADR-MOB-0006` in `product-docs/components/mobile/design-decisions.md`, merged via
PR #412), scoped the Android app-level AES seal key to the installation trust zone instead of the
whole app (previously one shared `ezkey_app_seal_v1` key for every installation on the phone; now
one `ezkey_seal_{installationScopeId}` key per installation). Combined with the existing MOB-011
installation-scoped signing-key aliases and local enrollment ids, the installation trust zone is
now isolated end to end at the crypto layer: signing key, local identity, **and** at-rest seal key
are all installation-scoped. This does not change anything in this addendum's design — it
**strengthens** the case for anchoring a future installation-owned policy tier on that same trust
zone, since it is now a complete isolation boundary rather than an identity label only. At the time
of this edit that fix is merged to `origin/main` (PR #412) but not yet present in this branch's
working tree; no conflict is expected since it does not touch this document's other content.

### How installation policy would reach the device

If and when installation policy becomes backend-owned, the most natural transport is **not** a new
protocol message. The enrollment **bind** response already carries integration-signed, non-crypto
metadata (tenant id/name/description, enrollment name) inside one Ed25519-signed payload (see
`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md` § Bind response). A policy attribute would sit alongside
those fields, verified by the mobile app the same way it already verifies the rest of the bind
payload — no new signature scheme, no new key material.

That gives the mobile app an **honest, tamper-evident** signal at bind time about the policy that
applied when the enrollment was created, with the same trust boundary as everything else at bind:
it proves what the installation's integration key asserted, not an attested runtime fact.

### Consequence chain if this is funded later

Making installation policy real (backend-owned, not just a mobile-side placeholder) would require,
in this order:

1. A DB representation for installation-scoped policy (new column or small table, at whatever
   granularity the design settles on — one flag first, before any tiered vocabulary).
2. An Admin API surface to read and write that policy (Global Admin scope).
3. An Admin UI control surfacing it (consistent with the Global Admin vs Tenant Admin split).
4. The bind-payload extension described above, plus mobile-side merge logic generalized to three
   tiers.

**This addendum does not commit to that chain.** It exists so a future design pass starts from a
named target instead of rediscovering the shape from scratch. The current mobile-only discovery
slice (`TB-2026-0001`) stays scoped to capability matrix + granularity model + transport direction,
not implementation.

### Why StrongBox/CryptoObject key-binding is not a "quick win"

A related question that surfaced in the same discussion: could the local preference simply be
strengthened to *require* a Keystore-enforced, auth-bound key (Level 3 in the ladder above,
StrongBox-eligible) instead of today's Level 2 (app-orchestrated `BiometricPrompt`)? Technically
yes — Android supports `setUserAuthenticationRequired(true)` together with
`setIsStrongBoxBacked(true)` on the same key. But this is the single highest-risk step in the whole
roadmap, not a quick win, because:

- Biometric enrollment changes (adding/removing a fingerprint, changing the device credential)
  **permanently invalidate** an auth-bound key (`KeyPermanentlyInvalidatedException`) — the app
  must detect this and drive the user through key regeneration or re-enrollment.
- Android version fragmentation is already a live concern for this exact key generator (see
  `MOB-012` — `setUnlockedDeviceRequired` gated to API 35+ for unrelated reasons); adding
  auth-binding reopens that fragmentation surface.
- An earlier attempt to make the enrollment key itself auth-bound produced functional breakage in
  this codebase (see Historical Trace above) — this is empirically confirmed, not theoretical.

The defensible next steps, in order, are the Android capability matrix (`TB-2026-0001`) and the
audit-first `respond` extension (Workstream 1) — both zero key-lifecycle risk. Level 3 key-binding
(Workstream 2) should only be scheduled after the capability matrix quantifies the real migration
cost.

## Current Practical Posture

As of the current workspace state:

- local approval confirmation exists on Android
- the Settings downgrade path is protected
- the mobile model separates user preference from future enrollment policy
- the protocol does not yet carry local-auth context in `respond`
- the backend audit log does not yet capture these local-auth dimensions

That means the current implementation is already materially better for local UX security and local flow integrity,
but the future protocol and audit work discussed here is still open and worth preserving.

## Decision Aid for the Next Phase

When this topic is resumed, the first question should be:

`Do we want better auditability first, or strong server-verifiable enforcement first?`

Recommended default answer:

1. improve auditability first
2. keep the semantics honest and declarative
3. only then decide whether stronger protocol guarantees are worth the added complexity

That ordering preserves clarity and keeps Ezkey aligned with its pragmatic design values.