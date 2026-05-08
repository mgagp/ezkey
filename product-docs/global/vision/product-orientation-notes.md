# Product Orientation Notes

## Purpose

This document is a lightweight log of directional product notes that are not yet stable enough for the roadmap or feature catalog.

## Usage model

Use short entries. Promote mature entries to:

- `../roadmap.md`,
- `../features-and-phases.md`,
- or backlog ideas in `../backlog/ideas/`.

## Entry template

### `V-YYYY-NNNN` `<short title>`

- **Date:** `YYYY-MM-DD`
- **Status:** `draft` / `under-review` / `promoted` / `archived`
- **Intent:** one short paragraph
- **Signals:** key indicators, evidence, or constraints
- **Potential impact:** components, phases, or user segments
- **Next step:** promote, refine, or archive

### `V-2026-0001` Enrollment-scoped local-auth posture for mobile respond

- **Date:** `2026-05-07`
- **Status:** `under-review`
- **Intent:** Explore a product direction where local authentication protection for mobile `respond` evolves from global setting to enrollment-scoped posture, with explicit handling of capability constraints and key lifecycle implications.
- **Signals:** Current model is globally toggled, not enrollment-scoped, and not backend-attested; security investigations highlight the need for explicit trust-boundary wording and pragmatic Android-first hardening.
- **Potential impact:** mobile security UX, enrollment lifecycle semantics, future backend policy hooks, Auth API and Admin API operator expectations, and test strategy updates.
- **Next step:** promote as backlog discovery item `I-2026-0001` and run a bounded capability/design analysis before implementation commitment.

### `V-2026-0002` Deployment profiles for Ezkey installations

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Intent:** Establish deployment profiles as a first-class product concept. Each profile balances simplicity, security posture, and operational cost for a target audience (for example, a small operator running everything in a single Docker stack vs a production-grade installation with separated API binaries and high availability). Make the trade-offs explicit, default to a security-conscious posture, and document escape hatches so operators understand exactly what they accept when they choose a simpler profile.
- **Signals:** Two converging threads make the pattern visible. First, API-key acceptance on Admin API today is implicit and ambiguous (`V-2026-0003`). Second, email integration introduces another lever where the all-in-one mode is pragmatically defensible for SMEs but should not be the security-by-default position (`V-2026-0005`). The pattern repeats across features and deserves a canonical home rather than ad-hoc decisions per feature.
- **Potential impact:** configuration surface (Spring profiles, environment variables), Docker compose presets, documentation structure, security posture defaults, Admin UI copy, deployment guides, future SDK and CLI behavior.
- **Next step:** brainstorming and grilling pass to enumerate candidate profiles (for example `minimal-all-in-one`, `standard-separated`, `high-availability`), define security defaults per profile, and identify which features are mandatory versus optional per profile. Promote to backlog ideas (`I-*`) once the profile catalog is stable.

### `V-2026-0003` API-key acceptance posture across Admin API and Integration API

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** Clarify the product positioning for accepting API-key authentication on Admin API endpoints versus Integration API. Default to security-by-default (deny on Admin API), expose a deliberate opt-in for the simplified deployment profile (`V-2026-0002`), and decide whether long-term policy should remove API-key acceptance from Admin API entirely. Integration API is positioned as the high-throughput, virtual-thread-based, reduced-attack-surface entry point for machine-to-machine flows; Admin API is positioned as the operator interface.
- **Signals:** Misconfiguration risk is real. The Java SDK was observed pointing at Admin API while the operator believed Integration API was in use; everything worked, masking the misalignment. Current implicit acceptance creates ambiguity for operators and SDK consumers. The positioning parallels the global-admin / tenant-admin split: the platform supports a simplified mode for SMEs without forcing full ceremony, but the default posture should be the safer one.
- **Potential impact:** `admin-api`, `integration-api`, `sdk-java`, deployment defaults, operator documentation, security posture per profile.
- **Next step:** complete the deployment-profiles canon (`V-2026-0002`), then deliver the configuration flag (`I-2026-0004`) with deny-by-default. A follow-up decision on full removal of API-key acceptance from Admin API requires a separate review once the profile catalog is stable.

### `V-2026-0004` Integrity validation strategy: rolling windows, retroactive batches, dashboard transparency

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Intent:** Define a coherent strategy for verifying audit-chain and checkpoint integrity over time, balancing security posture (`Design Principle #12`) with simplicity (`#1`) and performance cost. The current rolling 1-hour window covers transient outages well but leaves a blind spot for tampering that becomes visible only after the rolling window has passed. Introduce a complementary periodic retroactive validation (typically nightly) over a configurable fixed time window. Make all validation outcomes visible to the operator on the Admin Dashboard.
- **Signals:** Today, deletion of an audit row three days ago is detected only by an explicit on-demand integrity check; there is no proactive surfacing. The 1-hour rolling window may be too short as a grace period for some operator postures, and a configurable window plus periodic retroactive checks fills that gap. Volume-based effort modulation was considered but explicitly **not retained for V1**: real installations have stable traffic profiles, and operators can tune a fixed window directly without an adaptive scheduler.
- **Potential impact:** `admin-api` (background batch and persistence), `audit` (validation logic), `infra` (scheduler), `admin-ui` (dashboard widgets), operator documentation, security posture defaults.
- **Next step:** companion items: `I-2026-0005` (declared remediation when an integrity break is detected, with gap declaration and reattachment), `I-2026-0006` (nightly retroactive batch with fixed configurable window), `I-2026-0007` (Admin Dashboard integrity widgets). Promote to design pack and grilling pass once the three are mutually consistent.

### `V-2026-0005` Email integration strategy and deployment-profile cohabitation

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Intent:** Introduce email as a first-class delivery channel for selected workflows (enrolment QR-code delivery, admin activation code delivery, possibly more) using the standard Java mail APIs, keeping the platform self-contained. Position the introduction as a deployment-profile question (`V-2026-0002`): the all-in-one mode without email is pragmatically defensible for very small installations and remains supported as a documented profile, while the standard profile shifts sensitive material out of the all-on-screen presentation toward separated channels.
- **Signals:** Today, screens carry placeholders with disclaimers indicating that material such as QR codes would normally be delivered through a separate channel. The Java mail API removes the need for an external integration project (unlike SMS, which lives in peripheral projects such as `ezkey-sms-twilio`), so this concern stays inside the core platform as configuration. Profile-aware UI cues (which buttons exist, which workflows offer email) need to be designed alongside the channel itself.
- **Potential impact:** `admin-api`, `admin-ui`, `mobile`, `infra` (SMTP configuration), `docs`, deployment-profile catalog (`V-2026-0002`), security posture defaults, operator UX for enrolment and admin activation.
- **Next step:** brainstorming and grilling pass on (a) the inventory of workflows that should use email, (b) per-workflow defaults per deployment profile, (c) cohabitation with the current "all on screen" mode, including disclaimers and migration path. Promote to backlog ideas after the catalog stabilizes.

### `V-2026-0006` Mobile certificate pinning posture: SPKI pinning, TOFU at enrollment, Ezkey-authenticated recovery

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Intent:** Define an Ezkey-pragmatic mobile certificate-pinning posture that pins the SHA-256 hash of the server `SubjectPublicKeyInfo` rather than the full leaf certificate, bootstraps trust on first use at enrollment, enforces pinning natively during normal operation, and uses an Ezkey-cryptographically-authenticated Auth API recovery endpoint to handle Cloudflare-managed certificate rotation. The posture is explicit about residual risk concentrated in trust-refresh moments, and aims for a middle path: materially stronger than vanilla mobile TLS, materially simpler than a full PKI ceremony.
- **Signals:** Cloudflare free-plan edge certificates rotate without a reliable pre-announcement path; full PKI ceremonies are too heavy for Ezkey product values (`#1` simplicity, `#2` essential vs accidental complexity). An existing detailed plan `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md` already captures the design and is being canonized via `R-2026-0001` (legacy retrofit).
- **Potential impact:** mobile (`ezkey_mobile`), `auth-api` (recovery endpoint), `admin-api` (audit chain extension and a nightly compliance batch correlating rotation events with the deployed certificate), security posture documentation, operator UX during recovery. Adjacency to `V-2026-0008` (API versioning) since recovery flows may evolve.
- **Next step:** complete retrofit `R-2026-0001` to canonize the existing plan into product-docs (component design briefs, error model, audit chain extension); then propose an `I-*` for implementation aligned with the canonical plan, Android-first.

### `V-2026-0007` SMS integration strategy and SPI protocol for peripheral integrations

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Intent:** Establish the strategy for adding SMS as a delivery channel for selected workflows (notably enrolment material delivery), as a parallel and complementary stream to the email channel (`V-2026-0005`). Unlike email — which lives in-core via the Java Mail API — SMS lives in a peripheral repository (e.g. `ezkey-sms-twilio`) integrating through a clearly-defined SPI protocol. The strategy must define the SPI shape, per-step usage rules, deployment-profile cohabitation with the current "all on screen" mode (`V-2026-0002`), and the policy surface in the Admin UI.
- **Signals:** Email is solvable in-core; SMS introduces an external dependency that should not pollute the core platform. The SPI mechanism (in-process plug-in vs message bus vs DB polling vs webhook vs websocket) is currently undecided. The minimalist all-on-screen mode retains pragmatic value for SME profiles and should not disappear when SMS is introduced. Streaming via `ezkey-cli` was considered but feels too heavyweight; a real SPI is preferred.
- **Potential impact:** peripheral repos (e.g. `ezkey-sms-twilio`), `admin-api` (SPI surface), `admin-ui` (policy controls and per-step configuration), `mobile` (delivery awareness), deployment-profile catalog (`V-2026-0002`), operator documentation.
- **Open questions to grill:**
  - SPI protocol shape (in-process plug-in, message bus, DB polling, webhook, websocket)?
  - Per-step toggle vs global toggle vs deployment-profile default?
  - Multiple peripheral integrations interacting concurrently (Twilio plus alternative plus ...)?
  - Failure mode when the SPI peer is unreachable: degrade to all-on-screen, or hard-fail?
  - Audit and idempotency expectations for SPI calls?
- **Next step:** grilling pass (`ezkey-grill-me`) to converge on SPI protocol shape and policy model. Then derive `I-*`s for the SPI specification, the first peripheral repo (`ezkey-sms-twilio`), and the Admin UI policy controls.

### `V-2026-0008` Auth API versioning for mobile protocol evolution

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Intent:** Establish the product position on Auth API versioning relative to mobile-app release cadence, given there is no API version concept on the protocol today. As mobile features evolve (notably the per-enrollment local-auth posture in `V-2026-0001` / `I-2026-0001`), backend protocol evolution is foreseeable. The strategy must determine whether the platform supports parallel rolling versions, mobile-first or backend-first compatibility windows, and how operators with delayed backend updates avoid breaking already-deployed mobile clients distributed via Play Store / App Store.
- **Signals:** No API versioning exists today on Auth API. A protocol-level breaking change (for example, adding a strong-cryptography proof requirement driven by `V-2026-0001`) would silently break mobile clients targeting the new format if their backend is not yet upgraded. App-store distribution lag means mobile and backend versions cannot be assumed simultaneous.
- **Potential impact:** `auth-api` (versioning surface), `mobile` (capability negotiation), `admin-api` (operator visibility into protocol versions in use), Play Store / App Store release cadence, operator documentation, security posture (since parallel rolling versions can have different guarantees).
- **Open questions to grill:**
  - URL path versioning vs header negotiation vs capability-bit handshake?
  - Number of rolling versions supported (one current, one previous, ...)?
  - Equivalence of security level across rolling versions, or accept reduced level on legacy with explicit operator visibility?
  - Operator-facing visibility: which mobile versions are in use right now?
  - Sunset policy: how does an old protocol version get retired safely?
- **Next step:** grilling pass to converge on the versioning shape and rollback semantics, then `I-*`s for the versioning surface and the operator visibility tooling.

### `V-2026-0009` Lightweight observability posture: Java Melody first, no full APM stack initially

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Intent:** Adopt a lightweight, low-friction observability posture for Ezkey backends in development and small-installation contexts, using Java Melody and its standalone collector. Instrument `admin-api`, `auth-api`, and `integration-api` for HTTP, SQL, JVM memory, and threads metrics; expose monitoring through management ports rather than business ports; run a dedicated collector container with persistent volume; explicitly exclude `crypto-api`. This is the observability counterpart of `Design Principle #1` (simplicity): get high signal-to-effort ratio without introducing a Prometheus / Grafana stack at this stage.
- **Signals:** Existing detailed plan-prompt `.github/prompts/plan-javaMelodyCollectorForEzkey.prompt.md` already maps the integration end to end (Spring Boot 4 starter, dedicated standalone collector, exclusion of `crypto-api`, no Caddy proxy at this stage). Retrofit underway via `R-2026-0002`.
- **Potential impact:** `infra` (Docker stack, clean-start), `admin-api` / `auth-api` / `integration-api` (Java Melody starter dependency, management endpoint exposure), operator documentation (DX, limits, security posture for the local environment).
- **Open question to resolve during retrofit:** the existing plan defaults to **enabled by default** in clean start, while the user's verbatim suggested **opt-in** (`--with-java-melody` style). The retrofit must settle this divergence before implementation. Trade-offs:
  - **Enabled by default:** zero-config DX for developers, immediate visibility, consistent with `Design Principle #13` (transparency).
  - **Opt-in:** lighter clean start, observability as a deliberate decision, better fit for very small production-leaning installations.
- **Next step:** complete retrofit `R-2026-0002`, settle the default-vs-opt-in question, then `I-*` for the implementation aligned with the canonical plan.
