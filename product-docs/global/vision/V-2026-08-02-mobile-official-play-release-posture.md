# Vision Note — `V-2026-08-02-mobile-official-play-release-posture` Mobile official Play release posture

## Metadata

- **ID:** `V-2026-08-02-mobile-official-play-release-posture`
- **Status:** `draft`
- **Lane:** `D`
- **Created at:** `2026-08-02`
- **Updated at:** `2026-08-02`
- **Captured by:** Marc

## Intent

Move Ezkey Mobile (Android) from its current **limited experimental Play release** posture to a
**credible official release** posture: retire the always-on experimental/invited-audience framing,
close the remaining Play listing and compliance gaps, and add the client-evolution mechanisms
(app-update guidance, installation version/compatibility discovery) that a long-lived public app
needs but that a one-time experimental drop did not require. This vision does **not** own Auth API
**protocol** capability negotiation — that direction stays with `V-2026-0008` /
`I-2026-0025` — but explicitly relates installation-version discovery to it so the two are not
conflated or duplicated.

## Motivation

The current release ships real cryptographic MFA capability (enroll, approve/deny, local device
crypto), but the in-app narrative still frames the app as a **limited experimental** product
requiring an activation code — regardless of build type or audience. That framing was appropriate
for the first invited Play release but does not match a decision to publish an **official** app
line. Separately, the release-readiness audit
([`ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md))
already lists concrete, non-messaging gaps (version alignment, publication-candidate evidence,
listing/compliance inputs, stack-baseline decision) that block calling the release "complete" even
before messaging is addressed.

Beyond the current release, two related mechanisms are absent from the backlog entirely:

1. **Client app-update guidance** — nothing in the app or backend tells an installed client that a
   newer version exists, or discourages continued use of a badly outdated build. This is a normal
   maturity gap for a Play app moving past a one-shot experimental drop, not a security defect by
   itself.
2. **Installation product-version / compatibility discovery** — `GET /api/v1/public/instance-info`
   ([`PublicInstanceInfoResponseDto`](../../../ezkey-core/src/main/java/org/ezkey/instance/dto/PublicInstanceInfoResponseDto.java))
   is branding-only today (name, description, about URL). There is no signal a mobile client can
   use to reason about how far its protocol expectations may have drifted from a given self-hosted
   backend's version, beyond the capability negotiation already planned in `I-2026-0025`.

Both gaps interact with a real threat model: a self-hosted Ezkey instance is operator-controlled,
and an unauthenticated `instance-info`-style claim must not become a single point through which a
compromised or malicious instance can render an otherwise-healthy installed app artificially
unusable. Any design in this area must state explicitly whether it is fail-open or fail-closed at
that boundary (see `design-principles.md` §17) and must not treat a self-hosted server's own claim
about compatibility as the sole gate for a hard block.

## Directional principles

1. **Separate distribution trust from backend trust.** App-update guidance should be anchored in a
   distribution-controlled signal (Play Store metadata, or a signed channel Ezkey controls), not
   solely in a claim made by the self-hosted backend instance the user happens to be enrolled with.
2. **Advisory before authoritative.** Any backend-reported version or compatibility signal exposed
   to the mobile client is advisory (diagnostics, operator guidance, soft prompts) until a specific,
   analyzed design earns a harder gate. The real interoperability gate remains **protocol capability
   negotiation** (`I-2026-0025`), which already has an explicit mismatch contract (RFC 9457) and a
   rolling current+previous window.
3. **No conflation with protocol versioning.** Installation/product version discovery is
   complementary to, not a replacement for, `I-2026-0025`. It answers "what build/version is this
   backend running" for UX and diagnostics; `I-2026-0025` answers "can this client and this backend
   actually interoperate."
4. **Messaging change is a product decision, not a security fix.** Removing the experimental
   framing is a copy, navigation, and product-posture change scoped independently from the Play
   compliance checklist and from the update/compatibility mechanisms.
5. **Proportional adversary model.** A manipulated self-hosted instance lying about its version to
   deny service to a legitimate installed app is a real but bounded risk (the operator already
   controls that instance's user experience through other means). Do not over-invest in
   cryptographically hardening a signal that is, by design, advisory.
6. **One store line.** Per `V-2026-0008`, Ezkey keeps a single Play Store app line; none of this
   direction introduces parallel app builds or store listings per backend generation.

## Potential impact

- **Product milestones:** `P2-hardening`, `P3-distribution`
- **Components:** `mobile`, `auth-api`, `admin-api`, `core`, `docs`
- **User segments:** mobile end users (enrollment/authentication device holders), self-hosted
  operators evaluating or running Ezkey, and the maintainer preparing the first official Play
  listing

## Signals and constraints

- Experimental/invited-audience copy is **not** gated by any build flavor or environment flag today
  — it ships in the release binary exactly as in debug
  ([`HomeScreen.tsx`](../../../ezkey_mobile/app/screens/Home/HomeScreen.tsx),
  [`ReleaseNotesScreen.tsx`](../../../ezkey_mobile/app/screens/ReleaseNotes/ReleaseNotesScreen.tsx),
  [`resources.ts`](../../../ezkey_mobile/app/i18n/resources.ts)). Removing it is a straightforward,
  low-risk product change, independent of the harness production-clean gating already in place for
  F2a / debug panels
  ([`MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`](../../../ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md)).
- The release-readiness audit and decision memo already own the non-messaging Play gaps (version
  alignment, publication-candidate AAB evidence, listing/compliance inputs, RN stack-baseline
  decision) — see
  [`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md)
  and
  [`MOBILE_RELEASE_DECISION_MEMO.md`](../../../ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md).
  This vision does not restate that audit; it references it as an existing canonical gap list.
- Release-signing operational mechanics (`EZKEY_UPLOAD_*` Gradle properties, upload keystore) are
  already validated on the maintainer workstation, outside the repository, per
  [`MOBILE_RELEASE_SIGNING.md`](../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md). What remains
  open is producing and validating the **final GA publication candidate** AAB, not rediscovering or
  re-establishing the signing path.
- `I-2026-0025` / `V-2026-0008` already own Auth API **protocol capability negotiation**, explicitly
  scoped to exclude parallel `/v2` API trees and multiple Play Store app versions. Installation
  version/compatibility discovery must be designed as a **complement**, cross-linked, not a
  duplicate track.
- The September 2026 operability compass
  ([`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md))
  explicitly **defers mobile feature work** in favor of the backend integrity/alerting cluster and
  EXP1 soak. The official Play release track described here is a **parallel, independent** track,
  not a claim on the September milestone's scope — this must stay explicit wherever the two are
  referenced together so a future session does not conflate them.
- Certificate pinning (`I-2026-07-25`), local-auth (`I-2026-0001`), and iOS (`I-2026-0027`) are
  adjacent hardening/expansion tracks. None of them is a hard prerequisite for retiring the
  experimental messaging or for closing the Play compliance checklist, if the product accepts the
  current Android-only, TLS-plus-TOFU-pinning-roadmap scope for the official release.

## Promotion criteria

Promote this vision (move to `under-review`, then canonize) once:

- the four backlog ideas listed under Related documents have moved past `captured`/`incubating` far
  enough that at least the experimental-messaging idea has a scoped `TB-*`, and
- the installation version/compatibility discovery idea has been through a grill session that
  explicitly settles the fail-open/fail-closed posture and the advisory-vs-authoritative boundary
  described above, and
- the relationship to `I-2026-0025` / `V-2026-0008` is confirmed as complementary (no scope overlap
  or duplicate contract) by that grill.

## Related documents

- Backlog ideas (materialized alongside this vision):
  - [`I-2026-08-02-mobile-exit-experimental-messaging`](../backlog/ideas/I-2026-08-02-mobile-exit-experimental-messaging.md)
  - [`I-2026-08-02-mobile-play-official-compliance-gate`](../backlog/ideas/I-2026-08-02-mobile-play-official-compliance-gate.md)
  - [`I-2026-08-02-mobile-client-update-mechanism`](../backlog/ideas/I-2026-08-02-mobile-client-update-mechanism.md)
  - [`I-2026-08-02-mobile-installation-version-and-compat-discovery`](../backlog/ideas/I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
- Related protocol-versioning direction (complementary, not superseded):
  [`V-2026-0008-auth-api-protocol-versioning.md`](V-2026-0008-auth-api-protocol-versioning.md),
  [`I-2026-0025-auth-api-protocol-capability-versioning.md`](../backlog/ideas/I-2026-0025-auth-api-protocol-capability-versioning.md)
- Adjacent hardening tracks (not blocking):
  [`V-2026-0006-mobile-certificate-pinning.md`](V-2026-0006-mobile-certificate-pinning.md),
  [`I-2026-07-25-mobile-certificate-pinning-middle-path.md`](../backlog/ideas/I-2026-07-25-mobile-certificate-pinning-middle-path.md)
- Mobile module release canon:
  [`ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md),
  [`ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md`](../../../ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md),
  [`ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md`](../../../ezkey_mobile/docs/MOBILE_PLAY_PUBLISHING.md),
  [`ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md`](../../../ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md)
- September 2026 compass (parallel track, not a scope claim on it):
  [`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)
- Fail-open/fail-closed compass: [`design-principles.md`](../design-principles.md) §17

## Incubation sources

- Working plan (Cursor, editor-local — not part of this repository):
  `mobile_official_release_prep_34f9877e.plan.md`
- Lane: `D` — plan incubation, materialized `2026-08-02`
- Plan role: retained state-of-play and phasing record on the maintainer's editor; this vision note
  is the durable, repository-native record of the directional product posture.
