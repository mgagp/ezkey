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
- **Status:** `archived`
- **Superseded:** This orientation was reformulated during the 2026-05-08 grilling session. The single transversal "deployment profiles" platform concept was decomposed into a 3-phase plan that keeps the platform free of profile-aware code. See `V-2026-0010` for the new vision and `I-2026-0017`, `I-2026-0018` for the actionable phases.
- **Intent:** Establish deployment profiles as a first-class product concept. Each profile balances simplicity, security posture, and operational cost for a target audience (for example, a small operator running everything in a single Docker stack vs a production-grade installation with separated API binaries and high availability). Make the trade-offs explicit, default to a security-conscious posture, and document escape hatches so operators understand exactly what they accept when they choose a simpler profile.
- **Signals:** Two converging threads make the pattern visible. First, API-key acceptance on Admin API today is implicit and ambiguous (`V-2026-0003`). Second, email integration introduces another lever where the all-in-one mode is pragmatically defensible for SMEs but should not be the security-by-default position (`V-2026-0005`). The pattern repeats across features and deserves a canonical home rather than ad-hoc decisions per feature.
- **Potential impact:** configuration surface (Spring profiles, environment variables), Docker compose presets, documentation structure, security posture defaults, Admin UI copy, deployment guides, future SDK and CLI behavior.
- **Next step:** brainstorming and grilling pass to enumerate candidate profiles (for example `minimal-all-in-one`, `standard-separated`, `high-availability`), define security defaults per profile, and identify which features are mandatory versus optional per profile. Promote to backlog ideas (`I-*`) once the profile catalog is stable.

### `V-2026-0003` API-key acceptance posture across Admin API and Integration API

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **R1:** one Admin API property, default **`false`**, gates API-key **auth-attempt M2M** only (`ROLE_API_KEY` scope). Integration API = canonical M2M surface. **`true` opt-in** for documented minimal installs (Admin + Auth only, no Integration binary). No platform profile code (`V-2026-0010`). Full removal from Admin API deferred.
- **Signals:** Grilling D3 (2026-05-19) — Demo ACME + Java SDK mis-targeted Admin API; uniform `false` default including clean-start; RFC 9457 on reject; brief docs note (no production fleet). Analogue Global Admin simplified mode for minimal binary count.
- **Potential impact:** `admin-api`, `sdk-java`, `docs`, `ezkey-demo-app-acme`, functional tests; `I-2026-0004`.
- **Next step:** implement `I-2026-0004`; align SDK/examples and Demo ACME to Integration API base URL for API-key flows.

### `V-2026-0004` Integrity validation strategy: rolling windows, retroactive batches, dashboard transparency

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** Two-layer integrity strategy documented normatively: **(1) Rolling lookback** (default 60 min, configurable within **15 min–8 h**) — checkpoint scheduler **attaches** what is present; does not promise manipulation detection inside that window. **(2) Daily retroactive batch** (default **24 h** window) — full audit HMAC + checkpoint chain validation; anomalies → alert flow. Dashboard shows batch last-run via widgets (`I-2026-0007`), not alert-on-missing-batch. Apply **#14 beautiful problems** for scale optimizations (no audit-count cap in R1).
- **Signals:** Grilling D5 (2026-05-19) aligned operator model with `AuditChainScheduler` behavior. D4 C7–C9 settled rupture handling, snooze, and widget cut lines. Volume modulation and weekly/monthly mega-batches rejected for R1.
- **Potential impact:** `admin-api` (schedulers, properties), `audit` (`AuditChainVerificationService`), `admin-ui`, operator docs.
- **Next step:** D6 grilling (widgets); design pack; `TB-*` for `I-2026-0006` + batch table. See `integrity-cluster-D4-D6-grill-me.md`.

### `V-2026-0005` Email integration strategy and deployment-profile cohabitation

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** Introduce **optional**, **operator-triggered** email (Java Mail + SMTP config) for enrolment and admin-activation workflows while keeping Ezkey **fully operational** without SMTP or after send failure. R1 normalizes operational choice — it does not mandate email. Two **documentation postures** (per `V-2026-0010`): **integrated delivery** (on-screen + manual external channels OK; educational disclaimer) and **SMTP-assisted delivery** (email-first UI action; minimized on-screen QR fallback). Unlike SMS (`V-2026-0007`), email stays in-core as configuration only.
- **Signals:** Grilling D7 (2026-05-19) — disclaimers become product positioning, not “coming soon” warnings; PME may drag-drop QR or paste codes outside Ezkey; automation deferred. Global Admin configures SMTP (TI posture — `operator-alignment-guide.md`).
- **Potential impact:** `admin-api`, `admin-ui`, `infra` (SMTP), `docs`, contextual help, `I-2026-0023` (R1 slice).
- **Next step:** design pack + `I-2026-0023`; update `CONFIGURATION.md` for mail properties when implementing.

### `V-2026-0006` Mobile certificate pinning posture: SPKI pinning, TOFU at enrollment, Ezkey-authenticated recovery

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **SPKI pin** + **TOFU at enrollment**; native pinning in normal ops; **Auth API recovery** (narrow unpinned path) after Cloudflare-style rotation; device proof + backend-signed refresh; user confirm before pin replace; **Android-first**. Audit pin transitions; compliance batch later. Canon via **`R-2026-0001`** before `I-*`.
- **Signals:** Grilling D2 Blitz 2 (2026-05-19); existing plan `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md`.
- **Potential impact:** `mobile`, `auth-api`, `admin-api` (audit/compliance batch later), docs.
- **Next step:** finish `R-2026-0001` retrofit mapping; then implementation backlog slice.

### `V-2026-0007` SMS integration strategy and SPI protocol for peripheral integrations

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Optional**, **operator-triggered** SMS for enrolment and admin-activation via **peripheral HTTP adapter** (e.g. `ezkey-sms-twilio`) — not in-core (contrast `V-2026-0005` email). R1 SPI: Admin API POSTs delivery job to one configured adapter URL. Postures **`integrated-delivery`** / **`sms-assisted-delivery`** (mirror email). Body R1: **challenge codes only** (short templates). Global Admin configures adapter; **Global Admin or Tenant Admin** sends per workflow RBAC and deployment geometry (`operator-alignment-guide.md`).
- **Signals:** Grilling D5 Blitz 2 (2026-05-19). No auto-send, no bus/websocket/CLI streaming R1. Fully operational without SMS.
- **Potential impact:** `admin-api` (outbound job + config), `admin-ui`, peripheral repos (`I-2026-0020`, `I-2026-0024`), `docs`, `V-2026-0010` elaboration.
- **Next step:** SPI contract sketch + `I-2026-0024`; reference adapter repo.

### `V-2026-0008` Auth API versioning for mobile protocol evolution

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Capability negotiation** on existing Auth API endpoints (not full `/v1`/`/v2` URL trees). Rolling window: **current + one previous** generation. Mobile sends protocol/capability version; RFC 9457 on mismatch. Backend upgraded before mandating new capabilities. Legacy generation may offer reduced guarantees with **operator visibility**. Single store app line; documented sunset when earned (#14). R1: vision + contract sketch (`I-2026-0025`); implement when local-auth (`V-2026-0001`) forces a break.
- **Signals:** Grilling D7 Blitz 2 (2026-05-19). App-store lag vs self-hosted backend upgrade pace.
- **Potential impact:** `auth-api`, `mobile`, `admin-api` (read-only generation info), docs.
- **Next step:** contract sketch in product-docs when `I-2026-0001` / local-auth direction stabilizes.

### `V-2026-0009` Lightweight observability posture: Java Melody first, no full APM stack initially

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Java Melody** + standalone collector for **admin/auth/integration API** only; **exclude crypto-api**. Management port registration; collector persistent volume; **no Caddy** R1. **Opt-in** via clean-start / compose flag (`--with-java-melody` style) — not enabled by default (grill D11). DX/troubleshooting, not prod APM. Canon via **`R-2026-0002`** before `I-*`.
- **Signals:** Grilling D11 Blitz 2 (2026-05-19); plan-prompt `.github/prompts/plan-javaMelodyCollectorForEzkey.prompt.md`.
- **Potential impact:** `infra`, boot modules, docs.
- **Next step:** finish `R-2026-0002` retrofit; update plan-prompt default to opt-in; then `I-*` implementation.

### `V-2026-0010` Per-installation profile elaboration via AI-assisted methodology and generation

- **Date:** `2026-05-08`
- **Status:** `draft`
- **Supersedes:** `V-2026-0002` (original transversal "deployment profiles" platform concept; reformulated during the 2026-05-08 grilling session into the 3-phase plan below).
- **Intent:** Realize the "deployment profile" need through a 3-phase orthogonal approach that keeps the platform free of profile-aware code.
  - **Phase 1 — Clean Start status quo (acknowledged, no new platform work).** The existing `clean-start.sh` script with default options plus activable parameters **is** the dev/QA/test profile mechanism today. Phase 1 = editorial clarification in operator documentation that this is the chosen mechanism for those contexts. Future-direction note (out of current scope): possibility of grouping parameters into named presets within the script, evolving toward profile-like compositions for that audience.
  - **Phase 2 — Profile elaboration session (per installation).** Template plus AI-assisted skill plus methodology that pilots a structured Q&A session producing a per-installation/per-client profile elaboration document. Inputs: an environment questionnaire (Cloudflare, other proxies, redundancy needs, capacity, trusted proxy, network topology) and a guided traversal of the existing `CONFIGURATION.md` corpus to surface parameters relevant to the client's context. Output: a focused, pragmatic, versionable document suitable for sharing with the client as a basis for discussion or contract.
  - **Phase 3 — Profile generation from elaboration document.** AI-assisted skill that takes a Phase 2 document as input and produces the runtime artefacts needed to deploy that profile — `application.properties` per backend, Docker Compose profile files, environment files. Auditable, deterministic, no magic.
- **Signals:** Reformulation born from the 2026-05-08 grilling of `V-2026-0002`. The original framing risked profile-aware platform code (accidental complexity per `Design Principle #2`). The 3-phase reframing keeps profile knowledge external to the platform, aligning with `#1` simplicity, `#2` essential vs accidental, and `#5` operator-first. The existing `clean-start.sh` mechanism and the colocated `CONFIGURATION.md` corpus are durable foundations that this approach builds on rather than replaces.
- **Potential impact:** **Phase 1:** documentation clarification only. **Phase 2:** new template, new skill, per-client elaboration document workflow, cross-cutting traversal of `docs/configuration/README.md` and module-level `CONFIGURATION.md` corpus. **Phase 3:** new generator skill with a stable input contract from Phase 2; produces config artefacts that operators run with Docker Compose. **Cross-impact:** items that previously cited `V-2026-0002` (`V-2026-0003`, `V-2026-0005`, `V-2026-0007`, `I-2026-0011`) keep their references valid because the archived note remains findable; their links may be redirected to `V-2026-0010` opportunistically when those items are next worked.
- **Next step:** Phase 2 actionable promoted into `I-2026-0017` (template + skill design), Phase 3 actionable promoted into `I-2026-0018` (generator). Phase 1 needs only a small documentation clarification, no dedicated `I-*`. After `I-2026-0017` produces a stable elaboration format, `I-2026-0018` becomes ready for execution.

### `V-2026-0012` Dedicated batch backend (future): optional split from Admin API

- **Date:** `2026-05-19`
- **Status:** `draft`
- **Intent:** Record a **future** architectural option: extract scheduled/batch work from `admin-api` into a dedicated backend (e.g. `ezkey-batch-api`), with `admin-api` closer to an Admin UI BFF. R1 keeps all batches in Admin API as atomic all-or-nothing.
- **Signals:** Grilling C8 noted theoretical value (heap isolation, partial failure) but prior review: extra deployment complexity not justified for an unproven product.
- **Next step:** none for R1. Revisit on production evidence. See grill session `integrity-cluster-D4-D6-grill-me.md`.

### `V-2026-0013` Meta-resolution over long windows (future, exceptional)

- **Date:** `2026-05-19`
- **Status:** `draft`
- **Intent:** Record a **future-only**, heavily caveated concept raised during C9 grilling: a single operator action (“meta-resolution” / `GOD_RESOLUTION`) that reconciles many integrity ruptures across a long period (e.g. 30 days) with one justification and cryptographic patching — for catastrophe recovery when dozens of alerts accumulated from mixed false positives, downtime, and bugs.
- **Signals:** Operator explicitly rejected this for R1 as over-engineering and potentially dangerous; R1 keeps one alert, one resolution, no cap. Documented so the idea is not lost if a real customer ever needs it.
- **Potential impact:** `admin-api`, audit chain, alert model — high risk to trust model if implemented carelessly.
- **Next step:** **Do not implement** unless a concrete production need appears. Prefer normal per-alert resolution and snooze (`C9`).

### `V-2026-0011` Android-first real-device mobile validation as a first-class confidence layer

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** Establish Android real-device functional validation as a first-class confidence layer for Ezkey Mobile. The product direction is to validate the real backend-to-phone trust path on a physical Android device against the local clean-start stack, not only through unit tests and backend API functional tests. This layer should stay pragmatic, open-source-first, Android-first, and focused on a small number of high-signal flows rather than broad fragile UI coverage.
- **Signals:** The mobile app is part of Ezkey's cryptographic trust chain, so the current split between mobile unit/component tests and `ezkey-tests` Docker-stack API validation leaves a meaningful gap around real device signing, real Android keystore behavior, and intermittent `pending` / `respond` failures. A current-session working plan converged on an Android-first posture with open-source real-device automation, optional mirrored execution, Bash orchestration, and a hybrid init model where steady state is automated even if first-run setup remains partly human-assisted.
- **Potential impact:** `ezkey_mobile`, `ezkey-tests`, `auth-api`, `admin-api`, Docker clean-start usage, mobile test strategy, release confidence, and debugging posture for intermittent pending-signature or timeout issues. The direction also affects mobile testability conventions such as stable critical-path selectors.
- **Next step:** execute **`TB-2026-0002`** (linked from `I-2026-0019`): one enrollment path (hybrid init allowed), one approved pending/respond slice, and one short steady-state auth loop on a real Android device against the clean-start stack.

### `V-2026-0014` API documentation exposure and portal posture

- **Date:** `2026-05-21`
- **Status:** `promoted`
- **Intent:** Treat per-service Springdoc Swagger UI and raw OpenAPI endpoints as operator and developer tooling, not as the default public surface for production-capable Ezkey deployments. Public-facing API documentation should be curated through an open-source portal posture aligned with `ezkey.org`, while the exposure of raw `/api-docs` and embedded Swagger UI is segmented explicitly by deployment posture.
- **Signals:** Current runtime exposure is inconsistent with the likely production posture: public-evaluator `exp1` currently exposes Swagger UI and `/api-docs` on the production-capable APIs, while the repository already contains partial hardening precedent in native profiles. Ezkey also has a natural documentation-hosting anchor in the static Cloudflare Pages site under `sites/ezkey-org/`, plus a product-specific need to segment `Admin API`, `Auth API`, optional `Integration API`, and test-only `Crypto API` differently.
- **Potential impact:** `docs`, `sites/ezkey-org`, `admin-api`, `auth-api`, `integration-api`, `crypto-api`, `infra`, Cloudflare deployment guidance, and operator-facing documentation policy.
- **Next step:** promoted into `I-2026-0026`, then materially realized through `TB-2026-0003`. Retain this note as the directional record behind the public API portal and raw-doc exposure posture.
