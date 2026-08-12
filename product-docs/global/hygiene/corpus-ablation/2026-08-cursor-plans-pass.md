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
| `.cursor/plans/archived/audit_chain_checkpoints_search_api_2580d4ef.plan.md` | Deleted. Shipped: `GET /api/v1/audit-logs/chain-checkpoints` + controller tests; canon in `docs/ENDPOINT.md` § Audit log and chain checkpoint APIs. No new parking. |
| `.cursor/plans/archived/audit_table_tenant_visibility_0fec2b1b.plan.md` | Deleted. Shipped: tenant-scoped audit list + `tenant_id` population. In-pass: ENDPOINT visibility sentence (Tenant Admin auto-scope; null system rows excluded). |
| `.cursor/plans/archived/auth_status_mapping_analysis.md` | Deleted. RCA fixes shipped in `AuthAttemptWaitService` + ACME `LoginController`. No in-pass doc; no parking. |
| `.cursor/plans/archived/challenge_reject_status_confusion_analysis.md` | Deleted. Fix shipped in `AuthAttemptRespondService.validateChallenge` (skip challenge on deny). In-pass: ENDPOINT respond bullet (deny vs challenge / INVALID on accept). |
| `.cursor/plans/archived/contextual_authentication_differentiator_faff968d.plan.md` | Deleted. Living canon is title+message only (`CONTEXTUAL_AUTH.md` / ENDPOINT); no promote of details/level or “business approval platform” framing. No parking. |
| `.cursor/plans/archived/dashboard_api_evolution_41a6be06.plan.md` | Deleted. Shipped: `GET /api/v1/dashboard/overview` + Admin UI. In-pass: ENDPOINT § Dashboard overview; AGENTS pointer + signal-model link. |
| `.cursor/plans/archived/demo_device_tenant_grouping_43ffef3c.plan.md` | Deleted. Shipped: bind tenant fields + `EnrollmentTenantGrouper` / `tenantGroups`. In-pass: ENDPOINT bind example; Demo Device AGENTS § Home screen tenant grouping. |
| `.cursor/plans/archived/docker_bootstrap_init_3dfba789.plan.md` | Deleted. Shipped: bootstrap-init + credentials export + clean-start Docker-default. In-pass: `ezkey-tests/README.md` Docker-first bootstrap section. Parked P-033. |
| `.cursor/plans/archived/enrollment_contact_info_analysis_e30bb07a.plan.md` | Deleted. Phases 1–3 shipped (lifecycle timestamps, contact/`userIdentifier`, auth-attempt by userIdentifier). Canon: matrix + ENDPOINT. No parking. |
| `.cursor/plans/archived/enrollment_delete_api_error_handling_c33e8099.plan.md` | Deleted. Shipped: 409 `cannot-delete-linked-as-admin` + ENDPOINT/error inventory. No parking. |
| `.cursor/plans/archived/ezkey_tenant_ui_foundation_5954f835.plan.md` | Deleted. Parallel `ezkey-tenant-ui/` retired; unified `ezkey-admin-ui` (GA+TA) is canon. No parking. |
| `.cursor/plans/archived/fix_postgresql_partition_function_return_type_4d37e7fb.plan.md` | Deleted. Shipped: `create_monthly_partition` `RETURNS BOOLEAN` (V4) + `PartitionSchedulerService` `getSingleResult()`. In-pass: `ezkey-core/AGENTS.md` call-pattern pointer; SECURITY_ANALYSIS / IMPLEMENTATION / STRATEGY_SUMMARY aligned (BOOLEAN + V4 path). No parking. |
| `.cursor/plans/archived/ha_docker_environment_with_load_balancer_f73ce286.plan.md` | Deleted. Shipped: `docker-compose.ha.yml`, HAProxy cfgs, `start-ha.sh`/`manage-ha.sh`, `README-HA.md`, ShedLock distributed tests, `clean-start.sh --ha`. In-pass: HA pointers in `docker/README.md`, `LOCAL_STACK_PORTS.md`, `ezkey-tests/README.md`. Parked P-034. Parity program remains `I-2026-0003`. |
| `.cursor/plans/archived/pagination_ux_review_8913dc7b.plan.md` | Deleted. Shipped: `PaginatedTable` (top+bottom), `Pagination` aria-label + Tooltip, list screens migrated. In-pass: `ezkey-admin-ui/AGENTS.md` standard pattern → `usePaginatedFromOrval` + `PaginatedTable`. Parked P-035. |
| `.cursor/plans/archived/pending_poll_replay_analysis_97cbe46a.plan.md` | Deleted. Decision closed: 204 reuse OK; uniqueness on claim. Canon already in ENDPOINT + Auth AGENTS + `AuthAttemptPendingService`. In-pass: AUTH_SECURITY + mobile architecture docs aligned. Parked P-036. |
| `.cursor/plans/archived/phase_1_multi-tenant_6ba5f673.plan.md` | Deleted. Phase 1 shipped (AdminPrincipal, scoping, tenants/admins, deactivation, CLI, isolation tests). In-pass: `ezkey-tests/reference/MULTI_TENANT.md` Source/References → living docs. Parked P-037–P-038. |
| `.cursor/plans/archived/quarkus_migration_evaluation_2cbd3970.plan.md` | Deleted. Quarkus Auth API spike never built; native driver closed (JVM-only Spring Boot). In-pass: “not adopted” note in `docs/java-native-compilation-assessment-2026-07.md`. Parked P-039. |
| `.cursor/plans/archived/reencryption_batches_pagination_c99e1ff0.plan.md` | Deleted. Shipped: paginated batches list + filters, Admin UI, ENDPOINT, REENCRYPTION §7. In-pass: AGENTS Date range call site + §7 pointer. Parked P-040. |
| `.cursor/plans/archived/shedlock_ha_schedulers_2ffda450.plan.md` | Deleted. Shipped: ShedLock config, `@SchedulerLock`, `ADMIN_STARTUP_BOOTSTRAP`, `ezkey_shedlock` (V5), HA tests. In-pass: HA-JOB-COORDINATION status/checklist; README-HA V5 + drop plan link; admin-api AGENTS pointer. Parked P-041. |
| `.cursor/plans/archived/tenant_admin_creation_ux_a859b560.plan.md` | Deleted. Shipped: GA Create Admin tenant selector (exclude system), Zod + payload `tenantId`; TA peer omits. In-pass: `ezkey-admin-ui/AGENTS.md` § Create administrator. Parked P-042. |
| `.cursor/plans/archived/tenant_enrolment_audit_visibility_37e53453.plan.md` | Deleted. Option A shipped (`effectiveTenantId`, `resolveTenantIdForAudit`); Option B rejected. In-pass: auth-api AGENTS + ENDPOINT attribution. Parked P-043. |
| `.cursor/plans/archived/tenant_ui_docker_standalone_c11b3c43.plan.md` | Deleted. `ezkey-tenant-ui/` retired; pattern in `ezkey-admin-ui` `start.sh` + Caddy. In-pass: AGENTS — keep UI Docker standalone vs main compose. Parked P-044. |
| `.cursor/plans/archived/tenantadmin_list_admins_endpoint_cfc16d89.plan.md` | Deleted. Option 1 shipped: `GET /api/v1/admins` auto tenant scope. In-pass: `ezkey-admin-api/AGENTS.md` § Tenant-scoped list endpoints. |

## Kept this pass (retained)

| Source plan | Reason |
|-------------|--------|
| `.cursor/plans/authentication_wait_evolution_brainstorm.plan.md` | Operator Keep (2026-08-11). Lane B retained option-space; still unique vs `V-2026-07-18` / `I-2026-07-18` (axes, comparison table, questions, evidence). Incubation sources in V/I continue to point here. |

## Parking (open)

_None — burn-down applied 2026-08-12 (session closeout)._

## Parking (closed 2026-08-12 — burn-down)

| ID | Disposition |
|----|-------------|
| P-026 | Fixed: deleted unused `ezkey-admin-ui/src/lib/help-text.tsx` (copy lives in i18n). |
| P-027 | Closed no-op: prefer `ezkey-core/CONFIGURATION.md` § Encryption + ADR-0008/0011 over a new keyset-sync mega-doc. |
| P-028 | Deferred: HYBRID file↔DB sync tests when crypto hardening funds it. |
| P-029 | Deferred product: keyset reload/sync metrics when observability lane opens. |
| P-030 | Deferred product: multi-keyset / separate audit keyset — promote `V-*`/`I-*` only if funded. |
| P-033 | Deferred: bootstrap Maven tests as pure validations when test hygiene opens. |
| P-034 | Fixed: `README-HA.md` Ezkey branding + Auth diagram ports `(8080)`. |
| P-035 | Fixed: blitz-archive D8/D9 → `PaginatedTable` / AGENTS / matrix (dead plan cite removed). |
| P-036 | Fixed: `AUTH_SECURITY.md` hygiene banner + RSA→EC wording. |
| P-037 | Fixed: `multi-tenancy-strategy.md` historical banner → Phase 1 living canon. |
| P-038 | Fixed: `SECURITY_MULTI_TENANT.md` living-vs-historical banner (opaque bearer / no JWT). |
| P-039 | Deferred: GraalVM residual burn-down remains on `java-native-compilation-assessment` remediation (separate hygiene lane). |
| P-040 | Deferred product: COMPLETED re-encryption batch archival/purge if funded. |
| P-041 | Fixed: `HA-JOB-COORDINATION.md` job inventory + `ShedLockConfiguration` sample alignment. |
| P-042 | Deferred: Playwright GA create Tenant Admin when UI test budget allows. |
| P-043 | Fixed: `LIFECYCLE_GOVERNANCE.md` single system integration + audit attribution note. |
| P-044 | Fixed: rebranding prompt + flattenIntegration archived prompt → `ezkey-admin-ui`. |
| P-045 | Fixed: `MULTI_TENANT_OBSERVATIONS.md` §1 + header → resolved `GET /admins` + living canon links. |

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

- **Go** = the **single best package** for corpus realignment **including discoverability**: delete
  when liquidable, **plus** cheap in-pass fixes when warranted, **plus** parking `P-NNN` rows for
  deferred adjacent edits (parking is part of Go, not optional flavoring). Skill:
  `.cursor/skills/corpus-ablation/SKILL.md`.
- **Discoverability:** each iteration must ask whether a cold agent would find the design signal
  without the plan; prefer 2–4 line pointers over ADR / retained analysis.
- Append new parking rows at end of each iteration when a finding is deferred inside Go.
- Mark rows done by moving them to **Done this pass** (or strike with date) — do not leave stale open items after closeout.
