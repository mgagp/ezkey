# Corpus ablation — Cursor plans pass (2026-08)

- **Status:** active
- **Scope:** HITL prune of `.cursor/plans/*` (and related prompt noise when encountered)
- **Canon posture:** promote substance into existing `docs/`, `product-docs/`, or component docs;
  delete the plan when essence is captured or already shipped
- **Parking:** deferred adjacent findings only — burn down when the source doc is opened or at
  campaign closeout

## Done this pass

| Source plan / action | Outcome |
|----------------------|---------|
| `.cursor/plans/trusted_proxy_phase1.plan.md` | Deleted. Canon: module `CONFIGURATION.md` § trusted-proxies, `docs/OPERATIONAL.md`, `docs/LOCAL_STACK_PORTS.md`, `ClientIpResolver`. Fixed stale IP section in `docs/features/AUTH_SECURITY.md`. |
| `.cursor/plans/tenants_list_pagination_backend_9e6949f6.plan.md` | Deleted. Canon: `docs/ENDPOINT.md` § List tenants, `docs/PAGINATION_GUIDELINES.md`, matrix row Tenants. Rewrote `ezkey-admin-ui/docs/LIST_DATA_LOADING_DESIGN.md`; fixed tenants invalidation guidance in `ezkey-admin-ui/AGENTS.md`. |
| `.cursor/plans/signed_respond_response_analysis_369c4bb7.plan.md` | Deleted. Canon: `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md` § Respond HTTP response; `docs/ENDPOINT.md`; `docs/CRYPTO.md`. Shipped in core + Auth API DTO + mobile + Demo Device. |
| `.cursor/plans/proof_token_payload_binding_6384e23a.plan.md` | Deleted. Canon: `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md` § Pending + Respond (device). Binding shipped; audit prompt V4 already reflects remediation. No new parking. |
| `.cursor/plans/proof_token_hash-only_storage.plan.md` | Deleted. Already materialized: ADR-0007, `I-2026-0032`, `TB-2026-07-06` (done). No unique option space left in the Cursor plan. |
| `.cursor/plans/openapi_ordering_strategy_86087546.plan.md` | Deleted. Closed slice PR #181 / #180. Canon: `openapi-presentation-order-design.md`, `I-2026-06-02`, `TB-2026-06-02-openapi-presentation-order-phase2`. |
| `.cursor/plans/mobile_doctor_curated_hygiene.plan.md` | Deleted. Canon: evaluation 2026-07-11, `I-2026-07-11`, `TB-2026-07-11`, hygiene/mobile-doctor, `yarn doctor:curated`. MVP dry-run recorded; first HITL campaign is operational follow-up, not plan substance. |
| `.cursor/plans/m2m_to_integration_api_rename.plan.md` | Deleted. Rename shipped: `ezkey-integration-api`, `ApiName.INTEGRATION_API`, Docker `integration-api`. No dedicated I/TB; code + ENDPOINT / LOCAL_STACK_PORTS are canon. |
| `.cursor/plans/login_deny_error_ux.plan.md` | Deleted. Shipped: `map-passwordless-wait-error` + Vitest, `login.tsx` rejected state, `docs/ENDPOINT.md` § passwordless-wait. No new parking. |
| `.cursor/plans/key_rotation_reencryption_optimistic_lock_rca.plan.md` | Deleted. Fix shipped: `ReencryptionBatchProcessingService` REQUIRES_NEW; docs `REENCRYPTION_OPERATIONS.md`; elective `ReencryptionFullTriggerConcurrentActivityElectiveTest`. |
| `.cursor/plans/github_ecosystem_repos_9294c370.plan.md` | Deleted. Canon: `docs/ECOSYSTEM_REPOSITORIES.md`, README pointer, `I-2026-0020`, `V-2026-0007`. Remaining publish/staging work stays on `I-2026-0020`. |
| `.cursor/plans/ezkey_mobile_app_rewrite_e9f6be40.plan.md` | Deleted. Parallel `ezkey_mobile_app/` gone; UX ported into `ezkey_mobile/` (see archived UX Play alignment). Canon: `ezkey_mobile/` + mobile docs / AGENTS. |
| `.cursor/plans/ezkey_mobile_android_real_device_automation.plan.md` | Deleted. Canon: `V-2026-0011`, `I-2026-0019`, `TB-2026-0002`, `maestro/`, `MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`, TSP-2026-06-26. Remaining work = TB execution. |
| `.cursor/plans/enrollment_proof_token_encryption_integrity.plan.md` | Deleted. Shipped: `CryptoApiClient.encrypt`, `DatabaseHelper` plaintext query, uniqueness INSERTs ENC+hash, `EncryptionIntegrityElectiveTest`. Storage posture: ADR-0007. |
| `.cursor/plans/enrollment_fields_review_5227dad6.plan.md` | Deleted. Shipped: PATCH audit, `userIdentifier`, clear flags, Postman, Admin UI, ENDPOINT. Canon inventory: `docs/ENROLLMENT_ADMIN_UI_ATTRIBUTE_MATRIX.md`. |
| `.cursor/plans/enrollment_crypto_binding_analysis.plan.md` | Deleted. Canon: `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md` (bind / verify request / verify response). Shipped in core + mobile + Demo Device. No new parking. |
| `.cursor/plans/encryption_keys_list_pagination_backend_2286e3ec.plan.md` | Deleted. Shipped: paginated `GET /encryption-keys` + `keyStatus`; ENDPOINT; UI `usePaginatedFromOrval`. Canon: ENDPOINT + PAGINATION_GUIDELINES + matrix. |
| `.cursor/plans/dynamic_error_i18n_strategy.plan.md` | Deleted stub. Canon: `api-error-i18n.ts` / Admin UI AGENTS, `docs/admin-ui-admin-api-error-inventory.md`, component exception-and-error-model docs. Full historical write-up remains under `.cursor/plans/archived/2026-04/` until archive prune (P-020). |
| `.cursor/plans/cloudflare_site_structure_d9c1b5d8.plan.md` | Deleted. Canon: `sites/ezkey-org/` + AGENTS, `docs/cloudflare/`, `scripts/cloudflare/`. `ezkey-teaser` retired. No new parking. |
| `.cursor/plans/bearer_token_hash_storage_analysis.plan.md` | Deleted. Shipped: `bearer_token_hash` (V7), lookup by SHA-256, Crypto `POST /hash-token`. Precedent recorded in ADR-0007. |
| `.cursor/plans/archive/dashboard_auth_health_widget.plan.md` | Deleted. Shipped: `DashboardAuth24hStatsDto` terminal outcomes + rates; Admin UI auth health card. Signal model closed under `I-2026-0030`. |
| `.cursor/plans/archive/strong_auth_signature_chain_f92ec773.plan.md` | Deleted. Phase 1 shipped: signed Respond response + payload docs + Postman + Demo Device; mobile verifies result signature. Canon: `AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md` / ENDPOINT / CRYPTO. |
| `.cursor/plans/archived/add_decrypt_endpoint_to_crypto_api_34c78d4c.plan.md` | Deleted. Shipped: `POST /api/v1/crypto/decrypt`, DTOs, tests, Postman, crypto-api README/AGENTS. |
| `.cursor/plans/archived/admin_login_expiry_alignment_11b130b9.plan.md` | Deleted. Shipped: login `expiresAt` from attempt create; wait via `AdminAuthAttemptWaitRequestFactory`; ENDPOINT TTL>300 note. |
| `.cursor/plans/archived/admin_ui_action_button_consistency_fc556065.plan.md` | Deleted. Shipped: list primary CTA right (Tenants/Integrations `ml-auto`); rule in `ezkey-admin-ui/AGENTS.md`. |
| `.cursor/plans/archived/admin_ui_date_range_presets_5044c56d.plan.md` | Deleted. Shipped: `DateRangeFilter` + `date-range-presets`. In-pass: discoverability pointer in `ezkey-admin-ui/AGENTS.md` § Date range filters. |
| `.cursor/plans/archived/admin_ui_garage_du_coin_placeholders.plan.md` | Deleted. Shipped: Garage du coin placeholders in locales + `demo-mode.ts`. In-pass: discoverability pointer in `ezkey-admin-ui/AGENTS.md` § Developer/Demo mode (form placeholder theme). |
| `.cursor/plans/archived/admin_ui_tooltip_strategy_b2ba2d1d.plan.md` | Deleted. Shipped: `Tooltip` + badge/page usage; living copy mostly i18n. In-pass: discoverability pointer in `ezkey-admin-ui/AGENTS.md` § Contextual help (three surfaces + restraint). Parked P-026. |
| `.cursor/plans/archived/analyse_et_documentation_du_mécanisme_de_synchronisation_keyset_b1c0b075.plan.md` | Deleted. Canon already: CONFIGURATION modes/sync-window, ADR-0008/0011, SECURITY_POSTURE, `KeyRotationSyncWindowTest`. In-pass: HA keyset sync operator summary in `ezkey-core/CONFIGURATION.md` § Encryption at Rest. Parked P-027–P-030. |
| `.cursor/plans/archived/async_business_approval_realignment.plan.md` | Deleted. Phase 1 already in `docs/CONTEXTUAL_AUTH.md` + ENDPOINT + signature binding. Operator: no Phase 2 / richer-context traces — removed future Business Approvals callout from `CONTEXTUAL_AUTH.md`; fixed stale Security Notes (Pending context is crypto-bound). No parking. |

## Kept this pass (retained)

| Source plan | Reason |
|-------------|--------|
| `.cursor/plans/authentication_wait_evolution_brainstorm.plan.md` | Operator Keep (2026-08-11). Lane B retained option-space; still unique vs `V-2026-07-18` / `I-2026-07-18` (axes, comparison table, questions, evidence). Incubation sources in V/I continue to point here. |

## Parking (open)

| ID | Finding | Where | Suggested action |
|----|---------|-------|------------------|
| P-026 | `src/lib/help-text.tsx` appears unused after tooltip copy moved to i18n; plan’s “no i18n for tooltips” is obsolete. | `ezkey-admin-ui/src/lib/help-text.tsx` | Delete or rewire when pruning Admin UI help; do not treat as living canon. |
| P-027 | No dedicated operator runbook / sequence diagrams for keyset sync beyond CONFIGURATION + ADRs. | `docs/` / ops docs when needed | Add only if ops need it; prefer CONFIGURATION + ADR-0008/0011 over a new mega-doc. |
| P-028 | No dedicated HYBRID file↔DB desync / `KeysetBlobFileSyncTest`-style coverage called out in the old analysis. | `ezkey-tests` / crypto hardening | Consider when hardening HYBRID or file↔DB sync. |
| P-029 | No metrics/alerting on keyset reload or DB sync failures. | Observability when that lane opens | Add reload/sync failure signals if ops funds it. |
| P-030 | Separate audit keyset (multi-keyset) — open product idea, not incubated. | Product backlog when prioritized | Promote to `V-*`/`I-*` only if funded. |

## Parking (closed 2026-08-11)

| ID | Disposition |
|----|-------------|
| P-001 | Fixed: testing docs + ENDPOINT tenants intro aligned (list GA-only; get-by-id own tenant). |
| P-002 | Fixed: auth-protocol audit prompt uses `ezkey.trusted-proxies.*`. |
| P-003 | Fixed: NFC list includes Respond result `message`. |
| P-004 | Fixed: dead `proof_token_hash-only_storage` plan links → ADR/I/TB. |
| P-005 | Fixed: SEC report enrollment storage wording → encrypted + hash (ADR-0007). |
| P-006 | Fixed: dead OpenAPI ordering plan links → design / I / TB. |
| P-007 | Fixed: `DEVELOPMENT.md` drops `tagsSorter=alpha`. |
| P-008 | Fixed: dead mobile-doctor plan links → evaluation / I / TB / AGENTS. |
| P-009 | Fixed: `TB-2026-07-11-mobile-doctor-curated-mvp` + backlog index → `done`. |
| P-010 | Fixed: living prompts prefer Integration API; archived `plan-m2mApiModule` left historical. |
| P-011 | Captured: candidate track #6 on `I-2026-07-25` (churn cadence); no new `I-*`. |
| P-012 | Fixed: `ECOSYSTEM_REPOSITORIES.md` softens staging links; drive via `I-2026-0020`. |
| P-013 | Partial: pre-pilotage prompt points at `ezkey_mobile/`; other archived scaffolds opportunistic. |
| P-014 | Closed no-op: `ezkey_mobile/AGENTS.md` has no FCM push conflict; ADR-0002 stands. |
| P-015 | Fixed: real-device anti-rot / incubation → TB/I/docs only. |
| P-016 | Fixed: moved to `ezkey_mobile/docs/MOBILE_PENDING_DEBUG_PLAN.md`; refs updated. |
| P-017 | Fixed: `AUTH_SECURITY.md` uses hash lookup method name. |
| P-018 | Deferred product (no `I-*`): two enrollment expiry concepts — ENDPOINT note remains; promote when funded. |
| P-019 | Fixed: matrix Encryption keys + re-encryption batch columns filled from UI. |
| P-020 | Fixed: dynamic-error i18n Cursor plans removed (living canon: AGENTS + inventory). |
| P-021 | Fixed: dead bearer hash plan links → ADR-0007 / AdminToken / hash-token. |
| P-022 | Deferred product: API-key dashboard tile — revisit only if product wants (out of `I-2026-0030`). |
| P-023 | Deferred product: challenge in device-signed Respond payload. |
| P-024 | Deferred product: UUID entity IDs. |
| P-025 | Deferred product: per-request admin-login TTL. |

## Process reminder

- **Default operator Go** = the recommended path for that plan: usually delete when essence is
  already in canon, **plus** cheap in-pass discoverability/subject fixes when those are part of
  the recommendation. Adjacent microfixes stay in parking unless included in that Go.
- **Discoverability:** each iteration must ask whether a cold agent would find the design signal
  without the plan; prefer 2–4 line `AGENTS.md` pointers over ADR / retained analysis. Skill:
  `.cursor/skills/corpus-ablation/SKILL.md`.
- Append new parking rows at end of each iteration when a finding is not fixed in-pass.
- Mark rows done by moving them to **Done this pass** (or strike with date) — do not leave stale open items after closeout.
