# Backlog Idea — `I-2026-08-02-mobile-installation-version-and-compat-discovery` Mobile: installation product-version and compatibility discovery

## Metadata

- **ID:** `I-2026-08-02-mobile-installation-version-and-compat-discovery`
- **Status:** `incubating`
- **Priority:** `P3`
- **Created at:** `2026-08-02`
- **Updated at:** `2026-08-09`
- **Last reviewed at:** `2026-08-02`
- **Progression markers:** `P2-hardening`, `P3-distribution`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `core`, `docs`, `security`
- **Lane:** `D`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Explore whether the public, unauthenticated `GET /api/v1/public/instance-info` endpoint (or the
enrollment payload) should carry the Ezkey **backend product version** so a mobile client can
reason about how far a given self-hosted installation may have drifted from the client's own
expectations — as a **complement** to, not a replacement for, the protocol capability negotiation
already planned in `I-2026-0025` / `V-2026-0008`.

## Problem and value

- **Problem:** Ezkey currently assumes mobile clients and self-hosted backends stay broadly
  forward/backward compatible indefinitely. `PublicInstanceInfoResponseDto`
  ([`ezkey-core/src/main/java/org/ezkey/instance/dto/PublicInstanceInfoResponseDto.java`](../../../../ezkey-core/src/main/java/org/ezkey/instance/dto/PublicInstanceInfoResponseDto.java))
  exposes branding fields only (`authApiPublicBaseUrl`, `instanceName`, `instanceDescription`,
  `aboutUrl`) — there is no signal today that a mobile client could use to detect "this backend is
  running a very old or very new version relative to what I was built for," independent of the
  capability-mismatch signal `I-2026-0025` already plans for actual protocol exchanges.
- **Expected value:** Better diagnostics and UX (e.g. "this installation may not support all
  features of your app version" guidance) for self-hosted operators and users who upgrade
  irregularly, without introducing a new authoritative interoperability gate outside the one
  `I-2026-0025` already owns.

## Scope

- **In scope (for analysis, not yet implementation):**
  - Whether to add a backend product-version (and optionally a `minRecommendedMobileVersion`
    advisory field) to `PublicInstanceInfoResponseDto` or an equivalent enrollment-time payload.
  - The **threat model** for this signal (see below) and an explicit fail-open/fail-closed decision
    per `design-principles.md` §17.
  - How this signal is surfaced in the mobile UI (e.g. a non-blocking diagnostic notice) if adopted.
  - A grill session that settles the boundary against `I-2026-0025` explicitly, so the two ideas do
    not end up specifying overlapping or conflicting contracts.
- **Out of scope:**
  - Any change to the actual Auth API protocol contract, capability fields, or mismatch handling —
    that is `I-2026-0025`'s scope.
  - A hard client-side block keyed **solely** on this signal (see Risks).
  - Multiple Play Store app lines per backend generation — unchanged from `V-2026-0008`.

## Key assumptions

- `instance-info` is intentionally **unauthenticated** (rate-limit-exempt public metadata per
  [`rate-limit-baseline-policy.md`](../../rate-limit-baseline-policy.md)) — any new field added here
  inherits that same trust level and must be designed accordingly.
- The real interoperability gate for actual client/server exchanges remains protocol capability
  negotiation (`I-2026-0025`), which already has an explicit mismatch contract (RFC 9457 Problem
  Details) and a rolling current+previous generation window. This idea's signal is advisory
  metadata, not a substitute contract.

## Threat model (why this cannot be a simple hard gate)

A self-hosted Ezkey instance is operator-controlled. If a mobile client trusted an unauthenticated
`instance-info`-style version claim as sole grounds for refusing to operate (a **fail-closed** design
at that boundary), a compromised, misconfigured, or maliciously operated instance could report a
false version and render an otherwise healthy, legitimately enrolled mobile installation
artificially unusable — a denial-of-service vector introduced by the very feature meant to help
users.

At the same time, treating the signal as pure decoration (never acted on) captures none of the
diagnostic value that motivated this idea. This is explicitly **not black-and-white**, per the
originating discussion:

- **Advisory, not authoritative:** a version-mismatch signal from `instance-info` should drive
  non-blocking UX (a diagnostic notice, a support-contact prompt) rather than refusing enrollment or
  authentication outright. This is the **fail-open** posture for this specific boundary.
- **Protocol negotiation remains the real gate:** if a genuine incompatibility exists, `I-2026-0025`'s
  capability negotiation contract is the mechanism that should surface it authoritatively, because
  it is tied to an actual attempted exchange (enrollment or authentication), not a passive claim.
- **Distribution-anchored update guidance stays separate:** any push toward "your app is outdated,
  please update" should be anchored in the distribution channel
  ([`I-2026-08-02-mobile-client-update-mechanism`](I-2026-08-02-mobile-client-update-mechanism.md)),
  not solely in what a given self-hosted backend claims about itself.
- **Post-TOFU trust helps but does not fully resolve this:** certificate pinning
  (`I-2026-07-25-mobile-certificate-pinning-middle-path`) reduces the risk of a third party spoofing
  a legitimate instance's responses after trust-on-first-use, but does not address an operator's own
  instance being compromised or intentionally misconfigured — pinning is an adjacent mitigation, not
  a resolution of this specific threat.

## Risks and exceptions

- Under-specifying this boundary risks recreating the exact failure mode described above (a
  falsified version claim causing artificial denial of service) if a future implementation
  short-cuts to a hard gate without revisiting this analysis.
- Over-specifying (building a signed, attested version-claim protocol) would exceed Ezkey's stated
  complexity budget for a feature whose primary value is diagnostic, not security-critical — proceed
  proportionally.
- If this idea and `I-2026-0025` are worked on independently without cross-review, there is a
  concrete risk of specifying two different "here is the backend's version/capability" fields that
  say overlapping things inconsistently.

## Promotion notes

**Incubating** — a grill session is **required**, not optional, before promotion to `ready`,
because the threat-model boundary above (fail-open advisory signal vs. any future fail-closed
temptation) must be settled explicitly rather than re-derived informally at implementation time.
Keep `incubating` until:

- a grill session explicitly settles the fail-open/fail-closed posture and confirms the boundary
  against `I-2026-0025` and against
  [`I-2026-08-02-mobile-client-update-mechanism`](I-2026-08-02-mobile-client-update-mechanism.md),
- there is a concrete diagnostic or support use case (e.g. recurring operator support requests tied
  to version drift) that justifies prioritizing this over other `P3-distribution` work.

## Links

- Vision:
  [`V-2026-08-02-mobile-official-play-release-posture`](../../vision/V-2026-08-02-mobile-official-play-release-posture.md)
- Sibling ideas in the same program:
  [`I-2026-08-02-mobile-exit-experimental-messaging`](I-2026-08-02-mobile-exit-experimental-messaging.md),
  [`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md),
  [`I-2026-08-02-mobile-client-update-mechanism`](I-2026-08-02-mobile-client-update-mechanism.md)
- Complementary (not duplicated) protocol-compatibility track:
  [`V-2026-0008-auth-api-protocol-versioning.md`](../../vision/V-2026-0008-auth-api-protocol-versioning.md),
  [`I-2026-0025-auth-api-protocol-capability-versioning.md`](I-2026-0025-auth-api-protocol-capability-versioning.md)
- Adjacent mitigation (not a resolution):
  [`I-2026-07-25-mobile-certificate-pinning-middle-path.md`](I-2026-07-25-mobile-certificate-pinning-middle-path.md)
- Adjacent integrity track for enrolled instance-info refresh (complementary if version fields ride
  the same metadata family):
  [`I-2026-08-09-mobile-signed-instance-info-integrity.md`](I-2026-08-09-mobile-signed-instance-info-integrity.md)
- Fail-open/fail-closed design compass:
  [`design-principles.md`](../../design-principles.md) §17,
  [`../../methodology/decisions/2026-07-18-fail-open-fail-closed-design-compass.md`](../../../methodology/decisions/2026-07-18-fail-open-fail-closed-design-compass.md)
- Current public metadata contract:
  [`PublicInstanceInfoResponseDto.java`](../../../../ezkey-core/src/main/java/org/ezkey/instance/dto/PublicInstanceInfoResponseDto.java),
  [`rate-limit-baseline-policy.md`](../../rate-limit-baseline-policy.md)
