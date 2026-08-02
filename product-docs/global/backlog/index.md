# Backlog Index

## Purpose

This index provides a lightweight list of active ideas and their current state.

## Current prioritization anchor (read first)

**“Where are we?” / “What should be next?”** for the September 2026 operable-release target:

→ [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)

**Summary (2026-07-05):** Wave B integrity cluster R1 **closed** (GitHub #269). Wave C alerts list polish **done**
([`TB-2026-07-03-admin-ui-alerts-list-polish.md`](backlog/TB-2026-07-03-admin-ui-alerts-list-polish.md)).
Dashboard widget signal model **done** — [`I-2026-0030`](ideas/I-2026-0030-admin-dashboard-widget-signal-model-review.md)
(enrollment invalid/revoked split + signal-model doc; closed on `I-*` without retroactive `TB-*`).
Audit chain checkpoints matrix row **done** — [`TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md`](backlog/TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md) (Wave C closed).
Wave B closeout ML:

## Active ideas

| ID | Title | Status | Priority | Components | Last reviewed |
|----|-------|--------|----------|------------|---------------|
| `I-2026-0001` | Per-enrollment local authentication policy for mobile respond | `incubating` | `P1` | `mobile`, `auth-api`, `admin-api`, `admin-ui` | `2026-07-26` |
| `I-2026-0003` | High-availability Docker stack: parity review with `cleanstart.sh` | `incubating` | `P2` | `infra`, `admin-api`, `auth-api`, `integration-api` | `2026-05-19` |
| `I-2026-0004` | Admin API: configurable acceptance of API-key authentication | `promoted` | `P1` | `admin-api`, `sdk-java`, `docs` | `2026-05-25` |
| `I-2026-0010` | Phone-to-phone enrollment transfer ceremony | `ready` | `P2` | `mobile`, `auth-api`, `admin-api`, `admin-ui` | `2026-05-24` |
| `I-2026-0011` | Bootstrap clean-start: activation-code mode as default | `incubating` | `P1` | `admin-api`, `infra`, `bootstrap`, `docs` | `2026-05-19` |
| `I-2026-0012` | Hard-coded Ezkey System user sensitivity audit | `incubating` | `P2` | `admin-api`, `core`, `infra` | `2026-05-19` |
| `I-2026-0013` | Paginated admin screens: functional and operational pertinence review | `incubating` | `P2` | `admin-ui`, `admin-api` | `2026-06-20` |
| `I-2026-0014` | Paginated screens display strategy: foreign keys vs intelligent joins | `incubating` | `P2` | `admin-ui`, `admin-api`, `audit` | `2026-06-20` |
| `I-2026-0015` | Business limits on potentially large-volume SQL queries | `incubating` | `P2` | `admin-api`, `auth-api`, `integration-api`, `repositories` | `2026-05-24` |
| `I-2026-0017` | Profile elaboration template, skill, and per-client document workflow | `captured` | `P1` | `docs (product-docs)`, `methodology`, `skills` | `2026-05-08` |
| `I-2026-0018` | Profile generator skill: elaboration document to runtime configs | `captured` | `P2` | `docs (product-docs)`, `methodology`, `skills`, `infra` | `2026-05-08` |
| `I-2026-0019` | Android real-device mobile functional tests | `active` | `P1` | `ezkey-mobile`, `ezkey-tests`, `auth-api`, `admin-api`, `docker` | `2026-06-26` |
| `I-2026-0020` | Integration ecosystem shell catalog & publish workflow | `captured` | `P2` | `docs`, GitHub ecosystem, `product-docs (traceability)` | `2026-05-11` |
| `I-2026-0022` | Admin API scheduled jobs catalog (living document) | `incubating` | `P2` | `admin-api`, `docs` | `2026-05-24` |
| `I-2026-0023` | Email channel R1: optional operator-triggered delivery | `incubating` | `P2` | `admin-api`, `admin-ui`, `infra`, `docs` | `2026-07-25` |
| `I-2026-0024` | SMS channel R1: HTTP adapter SPI + optional operator send | `incubating` | `P2` | `admin-api`, `admin-ui`, `infra`, `docs` | `2026-05-24` |
| `I-2026-0025` | Auth API protocol capability versioning | `incubating` | `P2` | `auth-api`, `mobile`, `admin-api`, `docs` | `2026-05-24` |
| `I-2026-0027` | iOS app implementation — fresh native rebuild | `ready` | `P1` | `mobile` | `2026-05-24` |
| `I-2026-05-23-exp1-anonymous-evaluator-onboarding` | EXP1 anonymous evaluator onboarding | `active` | `P2` | `admin-api`, `admin-ui`, `ezkey-org`, `infra (EXP1)`, `docs` | `2026-05-23` |
| `I-2026-05-31-mobile-android-stack-followups` | Mobile Android stack follow-ups (post-#177) | `incubating` | `P2` | `mobile`, `android`, `ezkey-tests` | `2026-05-31` |
| `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation` | OpenAPI spec lifecycle and Cloudflare validation | `ready` | `P1` | `auth-api`, `admin-api`, `integration-api`, `specs`, `scripts`, `postman`, `cloudflare`, `sdk`, `mobile` | `2026-06-02` |
| `I-2026-06-03-admin-api-openapi-intra-tag-journey-order` | Admin API OpenAPI intra-tag journey order | `captured` | `P3` | `admin-api`, `docs`, `specs`, `sites/ezkey-org` | `2026-06-02` |
| `I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement` | Enforce challenge presence on `passwordless-wait` | `ready` | `P0` | `admin-api`, `security`, `authentication`, `admin-ui-contract` | `2026-06-23` |
| `I-2026-06-28-audit-archive-export-spi` | Audit archive export SPI: sealed-batch detachment + vendor-neutral immutable retention | `incubating` | `P3` | `core`, `admin-api`, `admin-ui`, `audit`, `infra`, `docs`, peripheral | `2026-06-28` |
| `I-2026-07-07-auth-attempt-challenge-protection` | Auth attempt challenge protection strategy (lifecycle minimization) | `ready` | `P1` | `core`, `auth-api` | `2026-07-07` |
| `I-2026-07-05-distilled-admin-platform-starter` | Distilled admin platform starter (Spring Boot + operator UI) | `parked` | `P3` | `admin-api`, `admin-ui`, `docs`, ecosystem GitHub, `sdk-java` | `2026-07-05` |
| `I-2026-07-05-enrollment-integration-key-cycling` | Enrollment integration key cycling via auth-exchange hooks | `incubating` | `P3` | `auth-api`, `core`, `mobile`, `docs`, `crypto` | `2026-07-05` |
| `I-2026-0032` | Proof token hash-only storage (tiered hardening) | `incubating` | `P1` | `core`, `auth-api`, `admin-api`, `docs`, `crypto` | `2026-07-06` |
| `I-2026-07-10-admin-recovery-codes-exhausted-label-ux` | Admin recovery codes: exhausted-set vs initial-issuance label UX | `captured` | `P3` | `admin-ui`, `admin-api`, `docs` | `2026-07-09` |
| `I-2026-07-10-nightly-integrity-boundary-false-positives` | Nightly integrity: boundary false positives (grid align + alert honesty) | `active` | `P1` | `core`, `admin-api`, `admin-ui`, `audit`, `docs` | `2026-07-10` |
| `I-2026-07-11-api-key-expires-at-edit` | API key PATCH: edit or clear `expiresAt` | `captured` | `P2` | `admin-api`, `admin-ui`, `core`, `docs` | `2026-07-11` |
| `I-2026-07-11-java-doctor-curated-hygiene` | Java doctor-curated continuous hygiene (SpotBugs + Semgrep + narrow PMD) | `ready` | `P3` | `core`, `admin-api`, `auth-api`, `integration-api`, `docs`, `tooling` | `2026-07-11` |
| `I-2026-07-11-mobile-doctor-curated-hygiene` | Mobile doctor-curated continuous hygiene (react-doctor + Semgrep + Detekt) | `ready` | `P3` | `mobile`, `android`, `docs`, `tooling` | `2026-07-11` |
| `I-2026-07-12-security-pentest-curated-hygiene` | Security pentest curated hygiene (Schemathesis + ZAP + first-party Nuclei) | `ready` | `P2` | `security`, `tooling`, `infra`, `admin-api`, `auth-api`, `integration-api`, `docs` | `2026-07-12` |
| `I-2026-07-17-admin-enrollment-reset-missing-authorization-header-500` | Admin enrollment reset: missing Authorization header returns 500 instead of 401 | `captured` | `P1` | `admin-api`, `security`, `testing`, `docs` | `2026-07-17` |
| `I-2026-07-17-alert-resolved-retention-purge` | Alert resolved-row retention and purge (no DELETE today by design) | `captured` | `P3` | `core`, `admin-api`, `admin-ui`, `infra`, `docs`, `audit` | `2026-07-17` |
| `I-2026-07-17-keyset-blob-admin-first-bootstrap` | Keyset blob: Admin-first bootstrap before peripheral SELECT-only | `captured` | `P3` | `core`, `core-security`, `admin-api`, `auth-api`, `integration-api`, `infra`, `docs`, `crypto` | `2026-07-17` |
| `I-2026-07-17-desktop-authenticator-reference-app` | Desktop authenticator reference app (mobile-like enrollment and approval) | `captured` | `P3` | `desktop-authenticator`, `demo-device`, `auth-api`, `docs`, `mobile` | `2026-07-17` |
| `I-2026-07-14-admin-ui-native-html-dialog` | Admin UI native HTML `<dialog>` migration | `triaged` | `P2` | `admin-ui` | `2026-07-14` |
| `I-2026-07-18-audit-log-fail-open-exception-swallow` | Audit-log write fail-open (exception swallow) and delivery honesty | `captured` | `P2` | `core`, `audit`, `admin-api`, `auth-api`, `integration-api`, `docs`, `security` | `2026-07-18` |
| `I-2026-07-18-authentication-wait-evolution` | Authentication wait evolution | `incubating` | `P2` | `core`, `admin-api`, `integration-api`, `sdk`, `infra`, `docs` | `2026-07-18` |
| `I-2026-07-25-mobile-certificate-pinning-middle-path` | Mobile certificate pinning middle path | `incubating` | `P2` | `mobile`, `auth-api`, `admin-api`, `docs`, `security` | `2026-07-25` |
| `I-2026-07-25-reencryption-batch-resilience-and-ops` | Re-encryption batch resilience and ops hardening | `incubating` | `P2` | `core`, `admin-api`, `admin-ui`, `infra`, `docs`, `security` | `2026-07-25` |
| `I-2026-07-26-tink-native-keyset-blob-envelope` | Tink-native keyset blob envelope | `done` | `P2` | `core-security`, `core`, `admin-api`, `auth-api`, `integration-api`, `infra`, `docs`, `crypto` | `2026-08-02` |

## Tracer bullets (draft / ready / in progress)

| ID | Title | Status | Related idea |
|----|-------|--------|--------------|
| `TB-2026-07-12` | Security pentest curated MVP | `draft` | `I-2026-07-12-security-pentest-curated-hygiene` |
| `TB-2026-07-11` | Java doctor-curated curator MVP | `active` | `I-2026-07-11-java-doctor-curated-hygiene` (#326) |
| `TB-2026-07-11-mobile-doctor` | Mobile doctor-curated curator MVP | `active` | `I-2026-07-11-mobile-doctor-curated-hygiene` |
| `TB-2026-07-07-auth-attempt-challenge-protection-phase-b` | Auth attempt challenge protection — phase B | `promoted` | `I-2026-07-07-auth-attempt-challenge-protection` |
| `TB-2026-07-06` | Device proof token hash-only storage (Tier 0) | `active` | `I-2026-0032` (#296) |
| `TB-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement` | Enforce challenge presence on `passwordless-wait` | `promoted` | `I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement` |
| `TB-2026-06-02-auth-api-cloudflare-schema-first-slice` | Auth API Cloudflare schema-first slice | `draft` | `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation` |
| `TB-2026-05-25-admin-api-key-acceptance-flag` | Admin API key acceptance flag | `ready-for-implementation` | `I-2026-0004` |
| `TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut` | EXP1 anonymous evaluator signup — first cut | `under-review` | `I-2026-05-23-exp1-anonymous-evaluator-onboarding` |
| `TB-2026-0004-mobile-ios-phase2-apple-stack-baseline` | Mobile iOS phase 2 — Apple stack baseline | `draft` | `I-2026-0027` |
| `TB-2026-0002-android-real-device-functional-pilot` | Android real-device functional pilot (Maestro) | `in progress` | `I-2026-0019` |
| `TB-2026-0001-mobile-local-auth-capability-discovery` | Mobile local-auth capability discovery | `active` | `I-2026-0001-mobile-respond-local-auth-per-enrollment` |

## Recently completed

| ID | Title | Closed date | Notes |
|----|-------|-------------|-------|
| `I-2026-0029` | Re-encryption: indexed encryption key id columns (replace LIKE scans) | `2026-07-26` | TB `TB-2026-07-26-reencryption-indexed-encryption-key-id-columns`. Four `*_encryption_key_id BIGINT` columns (enrollment ×2, auth_attempt, api_key) + FKs to `ezkey_encryption_key` + composite indexes (V19); `EncryptionEntityListener`/`ReencryptionRecordCipher` write path; `ReencryptionTargetQueryService` equality lookups replace `LIKE 'ENC:{keyId}:%'`. Maven baseline, functional suite (146 tests), and elective suite (15 tests, incl. `ReencryptionFullTriggerConcurrentActivityElectiveTest`) green on clean-start; DB spot-check confirms 100% key-id population across all four columns. |
| `I-2026-07-22-tink-keyset-serialization-api-migration` | Tink keyset serialization API migration | `2026-07-26` | `TinkKeyManager` file + DB keyset codecs migrated to `TinkJsonProtoKeysetFormat`; legacy compatibility tests green; `ApiKeyControllerTest` stubbing fixed; full install green. |
| `TB-2026-07-26-admin-ui-help-corpus-overhaul` | Admin UI contextual help corpus — comprehensive FR/EN pass | `2026-07-26` | 17 topics (3 new); generalized role-scoped rendering (`HELP_EXTRA_SECTIONS`); French corpus-wide "tenant" vs "locataire" standardization. |
| `I-2026-07-26-mobile-installation-scoped-seal-key` | Installation-scoped app seal key (`ezkey_seal_{installationId}`) | `2026-07-26` | TB `TB-2026-07-26-mobile-installation-scoped-seal-key` / PR #412. Replaces single app-wide AES seal key; local at-rest protection of `enrollmentProofToken`/`integrationPublicKey` now follows the MOB-011 installation isolation boundary. |
| `TB-2026-07-25-integrated-delivery-posture` | Integrated delivery posture (Admin UI) | `2026-07-25` | Posture notices + collapsed disclosure + help + canon; SMTP half remains on `I-2026-0023`. |
| `I-2026-07-20-mobile-installation-scoped-enrollment-identity` | Installation-scoped local enrollment identity | `2026-07-23` | TB `TB-2026-07-20-mobile-installation-scoped-enrollment-identity` / PR #401. Collision-free local identity and crypto/storage handles scoped to the installation trust zone; no silent Keystore ensure on pending/respond; orphan-key cleanup after failed verify. |
| `I-2026-07-20-mobile-installation-trust-zone-canon` | Mobile installation trust-zone canon | `2026-07-20` | TB `TB-2026-07-20-mobile-installation-trust-zone-canon`. Installation identified by normalized Auth API URL as the trust zone every enrollment belongs to; foundation for collision-free multi-installation. |
| `I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence` | Audit-log peripheral INSERT-only via sequence pre-allocation for the HMAC seal | `2026-07-18` | TB `TB-2026-07-18-audit-log-insert-only-hmac-seal`. Single-INSERT seal; peripherals SELECT+INSERT only; functional + `AuditIntegrityElectiveTest` + `verify-grants.sh` green. Resolves `TB-2026-07-16` follow-up. |
| `I-2026-07-18-mobile-hot-language-switch` | Mobile language switch without app restart | `2026-07-18` | TB `TB-2026-07-18-mobile-hot-language-switch` (`draft`, `single-pass`). Runtime EN/FR switch in Settings, no restart required. |
| `I-2026-0021` | PostgreSQL application role and table permissions matrix | `2026-07-18` | Commit `568f1423` / TB `TB-2026-07-16-postgresql-application-role-split`. `ezkey_migrate` + three runtime roles; Docker/grants wiring; clean-start, grant verification, standard/elective suites, and real-mobile flow passed. |
| `I-2026-07-15-sec-021-recovery-token-privilege-boundary` | SEC-021 recovery token privilege boundary | `2026-07-15` | `#357` / TB `TB-2026-07-15-sec-021-recovery-token-privilege-boundary`. Purpose `SESSION`\|`RECOVERY`; session auth rejects recovery; reset + deactivate-after-use. |
| `I-2026-07-15-sec-022-api-key-object-authorization` | SEC-022/023 API key object authorization | `2026-07-15` | `#362` / TB `TB-2026-07-15-sec-022-api-key-object-authorization`. Tenant Admin cannot get/list/revoke foreign-tenant API keys (`canAccessIntegration`). |
| `I-2026-07-09-encryption-required-read-path-parity` | `encryption.required` read-path parity (at-rest fields) | `2026-07-11` | `Enrollment` / `AuthAttempt` getters via `AtRestEncryptionAccess`; Security Challenge Suivi → Fait. |
| `I-2026-0030` | Admin Dashboard: widget signal model review and realignment | `2026-07-05` | PR #291. Enrollment `invalid`/`revoked` badges; `dashboard-widget-signal-model.md`. Closed on `I-*` (no `TB-*`). |
| `I-2026-0007` | Admin Dashboard: batch health and integrity widgets (Wave B B3) | `2026-07-03` | PR #289 / TB `TB-2026-07-03-dashboard-batch-health-widgets`. Program #269 closed same day. |
| `I-2026-0005` | Checkpoint integrity breaks: remediation + entry conciliation (Wave B B2/B2.6) | `2026-07-03` | PR #287 (B2.6) + earlier B2 chain reconcile. TB `TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence`; investigation predecessor TB `TB-2026-07-02-retroactive-integrity-validation-operability` (`decided`) and TB `TB-2026-06-28-manipulation-integrity-remediation` (`completed`). |
| `I-2026-0006` | Nightly retroactive integrity validation batch (Wave B B1) | `2026-06-29` | PR #270 / TB `TB-2026-06-28-nightly-integrity-validation-batch`. Detective layer + job registry + `AUDIT_INTEGRITY_RUPTURE`; investigation predecessor TB `TB-2026-06-30-integrity-investigation-operability` (`decided`). |
| `I-2026-0002` | Re-encryption batch UI: async button behavior | `2026-06-28` | PR #265 / issue #264. Async manual triggers (202 Accepted); TB `TB-2026-06-27-admin-ui-encryption-reencryption-async`; matrix encryption keys + batches → `implemented`. Origin: Blitz D1. |
| — | Admin UI Tier A/B paginated lists rollout (enrollments, integrations, API keys, audit logs, auth attempts, embedded tenants; quick actions; FK label parity; retire "related details") | `2026-06-23` | Nine `done` tracer bullets under the `I-2026-0013`/`I-2026-0014` umbrella: `TB-2026-06-18-admin-ui-lists-tier-a-enrollments`, `TB-2026-06-18-admin-ui-lists-tier-a-integrations`, `TB-2026-06-18-admin-ui-lists-tier-a-api-keys`, `TB-2026-06-20-admin-ui-enrollments-list-quick-actions`, `TB-2026-06-23-admin-ui-audit-logs-tier-b-labels`, `TB-2026-06-23-admin-ui-auth-attempts-tier-b-labels`, `TB-2026-06-23-admin-ui-tier-a-completion-embedded-tenants`, `TB-2026-06-23-admin-ui-detail-fk-label-parity`, `TB-2026-06-23-admin-ui-retire-related-details`. Parent ideas remain `incubating` pending a broader review pass. |
| `I-2026-0028` | Admin UI operator experience — post–Tier A follow-up | `2026-06-27` | Program closed #236 → P4 + detail FK parity (#261, #263). ML `ML-2026-06-27-admin-ui-operator-experience-program-closeout`. P3 encryption slice spun out → `I-2026-0002` (done 2026-06-28). |
| `I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity` | Admin enrollment vs admin login UX clarity | `2026-06-06` | PR #192 / issue #191 closed. Lane D first cut: dual-state admin detail, recovery-code gating tooltip, activation/recovery copy (EN/FR). TB `TB-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity-first-cut`. Hygiene #182 first slice co-merged; #182 closed 2026-06-18 (React Doctor campaign #232/#234). |
| `I-2026-06-02-openapi-api-reference-presentation-order` | OpenAPI API reference presentation order | `2026-06-03` | PR #180. Journey-oriented tag/operation order for Admin, Auth, and Integration API specs on Swagger UI and ReDoc (`ezkey.org`); phase 2 in TB `TB-2026-06-02-openapi-presentation-order-phase2`. |
| `I-2026-06-18-demo-device-qr-auth-url-parity` | Demo Device QR authUrl routing parity | `2026-06-18` | PR #222 / issue #223; dual-repo delivery; `TB-*` + `ML-2026-06-18-demo-device-qr-session`. Post-close Jackson/standalone hygiene in consolidation ML. |
| `I-2026-05-29-mobile-stack-modernization` | Mobile stack modernization (#177) | `2026-05-31` | RN 0.85.3, VC5+Nitro, AS3, CI `validate:ci`, ESLint 9, post-merge dep bumps, canonical Android build scripts. Maestro enrollment deferred to `TB-2026-0002`. |
| `I-2026-05-28-admin-ui-orval-upgrade` | Admin UI Orval upgrade (8.5 → 8.13) | `2026-05-29` | PR #175; stepwise ladder; Orval 8.13 with verb-aware config fix at 8.10/8.11. Post-program 8.18 hygiene: PR #234. |
| `I-2026-0016` | Methodology hygiene follow-ups (Tier 2 from 2026-05-08 nomenclature pass) | `2026-05-24` | Closed Tier 2 items E–K: decision recording scopes, phase/component tags, superseded links, optional automation follow-up, corpus completeness audit checklist. Decision record `2026-05-24-artifact-tagging-canonization-conventions.md`. |
| `I-2026-0008` | Rate-limit baseline analysis and generalization | `2026-05-24` | Policy deliverable [`rate-limit-baseline-policy.md`](../rate-limit-baseline-policy.md): four families (device / integration / admin ops / login); honest no-single-baseline outcome; CONFIGURATION cross-links; no code unification R1. Closes Blitz D1 with registry downscope. |
| `I-2026-05-24-admin-ui-vite8-upgrade` | Admin UI Vite 8 upgrade | `2026-05-24` | Delivered via PR #154 / TB-2026-05-24: Vite 8, Vitest 4, Tailwind 4.2.2+, plugin-react v6; Rolldown demo DCE fix in `demo-mode.ts`. First Lane A toolchain chore with GitHub issue workflow. |
| `I-2026-0026` | OpenAPI exposure and API portal posture | `2026-05-22` | Delivered through `TB-2026-0003`: public API portal on `ezkey.org`, `ReDoc CE` pinning, `Integration API` spec publication path, and first prod-like raw-doc hardening on `EXP1`. |

## Parked

| ID | Title | Reason | Next review |
|----|-------|--------|-------------|
| `I-2026-0009` | Global controllers registry | **Dropped** 2026-05-24 — living registry downscoped; see [`methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../methodology/decisions/2026-05-24-controllers-registry-downscope.md) | n/a |
| `I-2026-0031` | SDK dogfood: first-party consumption and honest narrative | **Parked** 2026-06-30 — pre-analysis complete (layers A/B, no Admin UI → Integration SDK); deferred for September operable-release focus. Insight: [`grill-sessions/2026-06-30-sdk-dogfood-grill-me.md`](grill-sessions/2026-06-30-sdk-dogfood-grill-me.md). `I-2026-0004` unchanged. | post–Sept 2026 milestone or distribution trigger |
| `I-2026-07-05-distilled-admin-platform-starter` | Distilled admin platform starter (`ezkey-app-starter`) | **Parked** 2026-07-05 — grill complete (D1–D10); external greenfield starter, not lite platform. Insight: [`grill-sessions/2026-07-05-distilled-admin-platform-starter-grill-me.md`](grill-sessions/2026-07-05-distilled-admin-platform-starter-grill-me.md). | post–Sept 2026 milestone or distribution trigger |
