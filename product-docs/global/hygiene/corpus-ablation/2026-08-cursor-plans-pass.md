# Corpus ablation — Cursor plans pass (2026-08)

- **Status:** queue empty (2026-08-17 documentary parking burn-down applied; remaining parking is deferred product/code)
- **Scope:** HITL prune of `.cursor/plans/*` (and related prompt noise when encountered)
- **Canon posture:** promote substance into existing `docs/`, `product-docs/`, or component docs;
  delete the plan when essence is captured or already shipped
- **Parking:** deferred adjacent findings only — burn down when the source doc is opened or at
  campaign closeout
- **Skill:** `.cursor/skills/corpus-ablation/SKILL.md`
- **Closeout note:** [`2026-08-17-cursor-plans-closeout.md`](2026-08-17-cursor-plans-closeout.md)

## Resume (cold start — do not wait for a hand-off prompt)

A cold agent should start here, then follow the skill. Keep this block current after every Go.

| Item | Current |
|------|---------|
| **Next scaffold** | None — archived `plan-*` queue empty. |
| **Then** | Kept files remain. Hors-passe (`ff-main.prompt.md`, mini-rfc) untouched. Next documentary ablation, if any: `plans/` (P-071). |
| **Kept (do not delete)** | `.cursor/plans/authentication_wait_evolution_brainstorm.plan.md`; `.github/prompts/reported/plan-worktreeDockerStackNaming.prompt.md` |
| **HITL** | Plan queue finished. Do not invent `I-*` / `TB-*` for leftover parking. |
| **Do not** | Invent `I-*` / `TB-*`. Re-create deleted plans. Re-rebase this branch unless `origin/main` moved. Push unless asked. Touch `docker/README-HA.md` without preserving Integration HA + V5 ShedLock wording. Fold experimental `ezkey-pam` / `ezkey_dart` / EXP1 facts into `docs/` or `docker/README.md` — keep those lanes in their folders (`ezkey-pam/`, `ezkey_dart/`, `experimental-hybrid/`). |
| **Branch** | `hygiene/corpus-ablation-2026-08` |
| **Open parking** | Verified residue: P-056, P-059–P-069, P-071, P-073, P-077, P-085–P-089, P-091, P-093, P-103–P-105, P-107–P-108. Grouped in the 2026-08-17 closeout note. |
| **Conflict / posture** | Git history is the archive. Project files in English. |

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
| `.cursor/plans/archived/2026-01/enrollment_uniqueness_constraint_implementation.md` | Deleted. Shipped: `idx_enrollment_unique_verified_name` (V5), `EnrollmentService` / `EnrollmentVerifyService`, uniqueness tests. In-pass: LIFECYCLE §3.3 uniqueness rules; ENDPOINT create + verify bullets. Parked P-046–P-047. |
| `.cursor/plans/archived/2026-01/maven_pom_versioning_no_branch_collisions.md` | Deleted. Shipped: CI-friendly `${revision}${buildQualifier}${changelist}`, `scripts/mvn-branch.sh`, DEVELOPMENT + AGENTS. In-pass: DEVELOPMENT defaults + dead plan cite → AGENTS / `maven.config.example`. P-046 covers archived README inventory. |
| `.cursor/plans/archived/2026-03/admin_api_pagination_uniformity_afd2226c.plan.md` | Deleted. Shipped: all Admin list/search → `Page` + UI `usePaginatedFromOrval`. In-pass: `ezkey-admin-api/AGENTS.md` § List/search paginated; policy sentence in `PAGINATION_GUIDELINES.md`. P-046 covers archived README. |
| `.cursor/plans/archived/2026-03/admin_ui_login_ux_1f4a4ccf.plan.md` | Deleted. Shipped: pin toggle + `last-username-pref.ts` + `admin-ui-security.md`. In-pass: AGENTS Auth flow → security doc. Phase 2 keyboard shortcuts cancelled (no parking). |
| `.cursor/plans/archived/2026-03/admin_ui_token_security.plan.md` | Deleted. Canon: `admin-ui-security.md` Mode A/B + Caddy headers. In-pass: AGENTS wording; validation Mode A/B rows; English section title. P-046 covers archived README. |
| `.cursor/plans/archived/2026-03/admin-ui_recovery_codes_93295a7d.plan.md` | Deleted. Shipped: login recovery funnel + reset/rebind; canon `docs/ADMIN_UI_RECOVERY.md`. In-pass: Admin UI AGENTS § Recovery funnel. |
| `.cursor/plans/archived/2026-03/auth_attempt_lifecycle_review_17873ad1.plan.md` | Deleted. Shipped: `AuthAttemptExpiryScheduler` + READ javadoc; no mobile cancel. In-pass: auth-api AGENTS device UX; admin-api CONFIGURATION §13 + config index. |
| `.cursor/plans/archived/2026-03/dashboard_auth_widget_coherence_17cbb6fd.plan.md` | Deleted. Superseded by `I-2026-0030` / `dashboard-widget-signal-model.md` (separate pending+read badges). In-pass: “statu quo” → “status quo”. |
| `.cursor/plans/archived/2026-03/enrollment-ui-review_cacd9f56.plan.md` | Deleted. Canon: `ENROLLMENT_ADMIN_UI_ATTRIBUTE_MATRIX.md` + `ENROLLMENT_WORKFLOW_GAPS.md`. In-pass: Admin UI AGENTS § Enrollment detail/metadata vs lifecycle. |
| `.cursor/plans/archived/2026-03/ezkey_instance_init_and_branding_2f27a7f3.plan.md` | Deleted. Shipped despite stale “pending” frontmatter: instance-info, org branding, QR auth URL, bootstrap names. In-pass: Admin UI AGENTS § Instance branding. Parked P-048. |
| `.cursor/plans/archived/2026-03/prev_next_detail_navigation_eebe3064.plan.md` | Deleted. Shipped: `useDetailNavigation` / list-detail nav across modals + full-page. In-pass: Admin UI AGENTS § Prev/Next detail navigation. |
| `.cursor/plans/archived/2026-03/SPEC_ADMIN_RECOVERY_AUDIT_TRAIL.md` | Deleted. Shipped: `RecoveryAuditDetails` + recover/reset EventTypes. In-pass: `ADMIN_UI_RECOVERY.md` § Audit; retarget class + `AUDIT_LOGGING_IMPLEMENTATION.md` cites. Parked P-049. |
| `.cursor/plans/archived/2026-04/acme_demo_lightsail_89788932.plan.md` | Deleted. Shipped: `--include-demo-acme`, Lightsail Compose/Caddy, progressive rollout docs in `experimental-hybrid/`. No in-pass (README/playbook already complete). |
| `.cursor/plans/archived/2026-04/admin_api_cors_config_ee3c6b9a.plan.md` | Deleted. Shipped: `ezkey.admin.cors.*` + CONFIGURATION §11. In-pass: admin-api AGENTS § Admin API CORS. |
| `.cursor/plans/archived/2026-04/admin_enrollmentid_exposure_7f58e2ee.plan.md` | Deleted. Shipped: `AdminResponseDto.enrollmentId` + Admin UI `EnrollmentFkLink`. In-pass: AGENTS Create administrator bullet. |
| `.cursor/plans/archived/2026-04/admin_session_hardening_2e417673.plan.md` | Deleted. Shipped: `/me` rehydration + CSRF + SameSite=Strict. Canon: `admin-ui-security.md` / ENDPOINT. In-pass: Admin UI AGENTS Mode B bullet. |
| `.cursor/plans/archived/2026-04/admin_session_httponly_path_c0bfa13c.plan.md` | Deleted. Shipped: Mode B HttpOnly cookie path (Phases 0–3). Canon: `admin-ui-security.md` / Pages doc / CONFIGURATION §12. Parked P-050 (cookie Max-Age vs sliding window). |
| `.cursor/plans/archived/2026-04/admin_ui_401_redirect_fix_5c1320f5.plan.md` | Deleted. Shipped: `fetchApi` 401 → login when `requireAuth` && !recovery bearer. In-pass: AGENTS `api-client.ts` one-liner aligned. |
| `.cursor/plans/archived/2026-04/admin_ui_api_error_i18n_phase2_quick_wins.plan.md` | Deleted. Shipped: inventory + en/fr + `shouldPreferI18nOverDetail` allowlist + `getTranslatedApiError` callsites. Canon: `docs/admin-ui-admin-api-error-inventory.md`, Admin UI AGENTS § Error display, `api-error-i18n.ts`. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/admin-ui_browser_tests_3ca0575c.plan.md` | Deleted. Shipped: Playwright `e2e/` + `run-ui-tests*.sh`. Canon: Admin UI AGENTS § Browser UI Tests, `docs/testing/AGENT_UI_VALIDATION.md`. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/admin-ui-doc-reframing_e5b64c6f.plan.md` | Deleted. Shipped: Admin UI + GA/TA framing in README, product-intent, ARCHITECTURE, PROJECT_POSITIONING, docs index. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/admin-ui-docs-balance_26fe42e2.plan.md` | Deleted. Shipped: `docs/ADMIN_UI.md` + entry points (README, docs index, module README). No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/audit_chain_heartbeat_20b04a41.plan.md` | Deleted. Shipped: heartbeat guard, incidents API, `AUDIT_CHAIN_HEARTBEAT_STALE`. Canon: `AUDIT_LOG_INTEGRITY.md`, CONFIGURATION, ENDPOINT, Bruno. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/audit_chain_lifecycle_b42e184a.plan.md` | Deleted. Shipped: seal-archive / declare-gap, checkpoint types, Period archive policy. Canon: `AUDIT_LOG_INTEGRITY.md`, CONFIGURATION, ENDPOINT, Bruno, Admin UI Integrity panel. Export remains `I-2026-06-28`. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/audit_event_family_filter_d00339d5.plan.md` | Deleted. Shipped: `EventTypeFamily` + UI optgroups + ENDPOINT. In-pass: core AGENTS family pointer; Bruno `eventTypeFamily` on read-audit-logs. No parking. |
| `.cursor/plans/archived/2026-04/auth_api_public_instance-info_79f69132.plan.md` | Deleted. Shipped: shared `PublicInstanceInfoService` + Auth/Admin GET; ENDPOINT, auth-api AGENTS, mobile README, Bruno public-auth. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/auth_attempt_batch_expiry_audit_revalidation.plan.md` | Deleted. Shipped: scheduler `AUTH_ATTEMPT_EXPIRED` + `expireIfStale`. Canon: ENDPOINT TTL note, CONFIGURATION, Admin UI labels. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/auth_attempt_reencrypt_sharding_679d83ad.plan.md` | Deleted. Shipped: auth-attempt shard batches + mutex + CONFIGURATION / `REENCRYPTION_OPERATIONS.md` §9 + Admin UI. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/authattempt_respond_ie_mapping_evolution.plan.md` | Deleted. Shipped: respond catches `AuthAttemptRequestFailedException` (`RuntimeException`); ENDPOINT 200 FAILED vs 400 IAE vs 409. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/backend_error_i18n_strategy_cf455bd9.plan.md` | Deleted. Strategy parent of Phase 2 quick wins. Canon: Admin UI AGENTS Option B, error inventory, admin-api exception-and-error-model. Phase 3 remains optional product. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/bootstrap_init_hardening_266ef4ca.plan.md` | Deleted. Shipped: `credentials-output-mode` full vs recovery_primary, Docker skip-init, reset log hygiene. Canon: admin-api AGENTS § Logging, CONFIGURATION, docker README, OPERATIONAL. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/dashboard_badge_drill-downs_175c29d4.plan.md` | Deleted. Shipped: drilldown links + URL sync. Canon: `dashboard-widget-signal-model.md` (`I-2026-0030`), Admin UI AGENTS § Dashboard. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/dashboard_enrollment_widget_1aaa3560.plan.md` | Deleted. Shipped: `EnrollmentDashboardStats` buckets. Canon: signal model (`I-2026-0030`). In-pass: retargeted I-2026-0030 plan cite to DTO/stats types. No parking. |
| `.cursor/plans/archived/2026-04/demo_device_qr_paste_d060241a.plan.md` | Deleted. Shipped: jsQR paste/drop on Demo Device new enrollment. Canon: `plan_demo_device.md`, demo-device AGENTS QR `authUrl`. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/device_private_key_storage_tier.plan.md` | Deleted. Shipped: `NONE`/`STANDARD`/`STRONG` at verify. Canon: ENDPOINT, MOBILE_DEVELOPER_GUIDE trust model. In-pass: enrollment attribute matrix row. No parking. |
| `.cursor/plans/archived/2026-04/docker_build_workflow_449b0fcc.plan.md` | Deleted. Shipped: `scripts/build-docker.sh` + Dockerfile `build-validation`. Canon: DEVELOPMENT.md, docker README, root AGENTS. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/ed25519_doc_alignment_f4c9529b.plan.md` | Deleted. Shipped: integration signatures documented as Ed25519 + Base64URL. Canon: CRYPTO.md, AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md, DTO/OpenAPI. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/encryption_key_records_semantics_7a94c54d.plan.md` | Deleted. Shipped: demotion baseline + drain via prefix verification. Canon: SPEC lifecycle, REENCRYPTION_OPERATIONS, `EncryptionKeyMigrationScopeService`. No in-pass; no parking. |
| `.cursor/plans/archived/2026-04/errorresponsedto_rfc9457_migration_d3394fed.plan.md` | Deleted. Shipped: ErrorResponseDto removed; ProblemDetail + catalogs. In-pass: historical banner on `ADMIN_API_EXCEPTION_HANDLING_SYNTHESIS.md`. Parked P-051. |
| `.cursor/plans/archived/2026-04/exception-mapping-inventory_1bc3c2bb.plan.md` | Deleted. Milestone through Wave 7. In-pass: WAVE_1 analysis banner; exception-and-error-model IAE/ISE bridge + no core mega-enum. No new parking. |
| `.cursor/plans/archived/2026-04/fk_more_details_ux_04799c66.plan.md` | Deleted. “More details” expand replaced by `fk-detail-links.tsx`. In-pass: Admin UI AGENTS FK name-link pointer. No parking. |
| `.cursor/plans/archived/2026-04/integration_retirement_coherence_972772a3.plan.md` | Deleted. ACTIVE/RETIRED only; bulk revoke includes CREATED/BOUND. Canon: LIFECYCLE_GOVERNANCE §3.2. In-pass: ENDPOINT + Bruno search dropped INACTIVE/`active` response. No parking. |
| `.cursor/plans/archived/2026-04/integrationkeyalgorithm_alignment_9332ba7e.plan.md` | Deleted. Bind fail-closed `ed25519`. Canon: ENDPOINT, CRYPTO, MOBILE_DEVELOPER_GUIDE, `integrationKeyAlgorithm.ts`. In-pass: mobile AGENTS Security Rules pointer. No parking. |
| `.cursor/plans/archived/2026-04/language_preference_analysis_1df9ce3f.plan.md` | Deleted. Bind has no `language`; V7 flattened i18n. Canon: EnrollmentBindRequestDto, ENDPOINT, auth-api AGENTS. In-pass: AGENTS two-field bind; BIND_ENUM_PROTECTION historical banner. Parked P-052. |
| `.cursor/plans/archived/2026-04/lifecycle_after_reencrypt_refactor_7721f359.plan.md` | Deleted. KeyUsageVerificationService + shared target discovery. Canon: SPEC lifecycle, REENCRYPTION_OPERATIONS. In-pass: core AGENTS remaining-counts pointer. No parking. |
| `.cursor/plans/archived/2026-04/mobile_device_proof_token_b70b1f8e.plan.md` | Deleted. Native CSPRNG `generateProofToken` matches SignatureService. Canon: CRYPTO.md, MOBILE_CRYPTO_REFERENCE. In-pass: mobile AGENTS anti-Date.now / no RNGetRandomValues. No parking. |
| `.cursor/plans/archived/2026-04/mobile_ux_play_alignment_0045d376.plan.md` | Deleted. Settings hub + Danger Zone; Diagnostics gone. Canon: MOBILE_SCREENS_AND_WIREFLOWS. In-pass: mobile AGENTS production IA. No parking. |
| `.cursor/plans/archived/2026-04/mobile_verify_types_alignment_f7656ece.plan.md` | Deleted. Verify `challengeResponse` required; JSON number on the wire. Canon: EnrollmentVerifyRequestDto, ENDPOINT, types.ts. In-pass: mobile AGENTS Contract-First bullet. No parking. |
| `.cursor/plans/archived/2026-04/normalize-admin-enrollment-name_4007c2a2.plan.md` | Deleted. `ezkey_admin.enrollment_id` / `EzkeyAdmin.enrollment`. In-pass: core AGENTS one-identifier rule. Parked P-053. |
| `.cursor/plans/archived/2026-04/phone-number-management_66c3de73.plan.md` | Deleted. Contact phones E.164 on admin/tenant/enrollment. In-pass: core AGENTS contact-vs-assurance; Admin UI `@/lib/phone-number`. Parked P-054. |
| `.cursor/plans/archived/2026-04/postman_enrollment_crypto_flow_b1fcbbbb.plan.md` | Deleted. payload-helper enrollment types + Bruno `enrollments-auth/`. Canon: ENROLLMENT_SIGNATURE_PAYLOAD, crypto AGENTS. In-pass: enrollment workflow no longer signs raw proof token. Parked P-055. |
| `.cursor/plans/archived/2026-04/quiet_404_exception_handling_08a688b4.plan.md` | Deleted. Quiet 404 handlers on Admin/Auth/Integration + demo apps. In-pass: exception-and-error-model unknown-route DEBUG rule. No parking. |
| `.cursor/plans/archived/2026-04/recovery-codes-lifecycle_764f8c67.plan.md` | Deleted. Full-set regenerate shipped. Canon: LIFECYCLE §3.8, ENDPOINT, ADMIN_UI_RECOVERY. In-pass: docs/README retarget; admin-api AGENTS replace-all / no top-up. No parking. |
| `.cursor/plans/archived/2026-04/re-encryption_pipeline_redesign_e144738d.plan.md` | Deleted. Service split + parallel runner + Micrometer. Canon: REENCRYPTION_OPERATIONS. In-pass: core AGENTS orchestration/mutex; historical banner on reencryption-service-design-follow-up. No parking. |
| `.cursor/plans/archived/2026-04/tenant_timezone_strategy_08096020.plan.md` | Deleted. UTC wire + IANA tenant tz + local/tenant display pref. In-pass: Admin UI AGENTS display-timezone; ENDPOINT datetime sentence. No parking. |
| `.cursor/plans/archived/2026-04/uniform-noop-pattern_fcccfde9.plan.md` | Deleted. Bulk enrollment 200 + `noOp` summary; audit only on change. Canon: ENDPOINT bulk lifecycle. In-pass: admin-api AGENTS no-op policy. No parking. |
| `.cursor/plans/archived/2026-05/admin-ui-activation-reissue_2d0589dd.plan.md` | Deleted. Reissue activation code shipped. Canon: ENDPOINT § e1, Bruno. In-pass: LIFECYCLE §3.5 row; admin-api AGENTS eligibility. No parking. |
| `.cursor/plans/archived/2026-05/admin_administrators_list_operational_closeout.plan.md` | Deleted. tenantName + Platform for global scope. In-pass: Admin UI + Demo Device AGENTS Platform convention. No parking. |
| `.cursor/plans/archived/2026-05/admin_ui_lint_cleanup_2e576db8.plan.md` | Deleted. Lint fixed at source; `lint:diagnostics` added. In-pass: Admin UI AGENTS static-components + diagnostics command. No parking. |
| `.cursor/plans/archived/2026-05/audit_chain_heartbeat_clarity_d4a8b2c1.plan.md` | Deleted. Heartbeat timing/fail-close clarity shipped. Canon: AUDIT_LOG_INTEGRITY. In-pass: core AGENTS heartbeat pointer. No parking. |
| `.cursor/plans/archived/2026-05/audit_log_timestamps_38aab43b.plan.md` | Deleted. Audit list absolute timezone-explicit first. In-pass: Admin UI AGENTS formatDateWithTimezone primary. No parking. |
| `.cursor/plans/archived/2026-05/demo_device_acme_stack_closeout_2026-05-03.plan.md` | Deleted. Demo Device + ACME challenge strip + JDK 25 rule. In-pass: acme AGENTS biz-challenge; Admin UI e2e enrollment-id env. No parking. |
| `.cursor/plans/archived/2026-05/enrollment-expiry-create_55bc637d.plan.md` | Deleted. Create `expiresAt` + 7-day default. Canon: ENDPOINT, CONFIGURATION. In-pass: core AGENTS pending expiry; LIFECYCLE §3.3 sentence. No parking. |
| `.cursor/plans/archived/2026-05/ezkey-doc-system_f1e8054a.plan.md` | Deleted. product-docs skeleton shipped; method is methodology/README. In-pass: product-docs/README living-layer pointer. No parking. |
| `.cursor/plans/archived/jpa_field_initialization_final_recommendations.md` | Deleted. JPA defaults already in core AGENTS. In-pass: expiresAt in service; no DefaultValueEntityListener. No parking. |
| `.cursor/plans/archived/phase2_orval_hooks_migration_inventory.md` | Deleted. Living list pattern is `usePaginatedFromOrval` + `PaginatedTable` (`LIST_DATA_LOADING_DESIGN.md`). In-pass: Admin UI AGENTS retarget (structure, Pattern B, naming). Left `use-integrations.ts` (thin lookup helper). No parking. |
| `.cursor/plans/archived/rejected_showing_as_expired_fix.md` | Deleted. Shipped: `AuthAttemptWaitService` completed-before-expiry + `isAttemptExpired()` final-status guard. In-pass: ENDPOINT wait `status` vs clock expiry; Javadoc aligned; `archived/` README + empty folder removed (dated dirs already gone). Parked P-056. |
| `.github/prompts/plan-adminAuthTokenStrategyAudit.prompt.md` | Deleted. Opaque DB admin sessions (not JWT) are the closed decision. In-pass: admin-api AGENTS § Admin session tokens; refreshToken cite retarget; `V-2026-06-30` JWT wording. Hash-only already ADR-0007. Parked P-057. |
| `.github/prompts/plan-adminOnboardingActivationCode.prompt.md` | Deleted. Shipped: `PENDING_ACTIVATION`, `onboardingMode`, `POST /admin/auth/activate`, login activation branch, LIFECYCLE §3.5. In-pass: ENDPOINT consume vs reactivate; recovery/provisioning copy; UI + admin-api AGENTS. Remaining default-posture is `I-2026-0011`. Parked P-058. |
| `.github/prompts/plan-adminOnboardingVsEnrollmentApiRationale.prompt.md` | Deleted. Canon already ENDPOINT § onboarding vs enrollment GET. In-pass: admin-api AGENTS pointer; ENDPOINT TenantAdmin wording; analysis-doc banner. Parked P-059. |
| `.github/prompts/plan-adminUiConceptsGapsAndRecommendations.prompt.md` | Deleted. Critical UI gaps shipped (tenants, danger zones, GA create, integrity, pagination, breadcrumbs, toasts). Canon: `screens-and-wireflow.md`. In-pass: AGENTS breadcrumb; wireflow admin `?adminId=`; lifecycle-prompt cite. Parked P-060. |
| `.github/prompts/plan-adminUiIntegrationModalDismiss.prompt.md` | Deleted. Shipped: `Dialog` `dismissible` (create/edit `false`). Canon: Admin UI AGENTS § Dialog. No in-pass; no parking. |
| `.github/prompts/plan-adminUiLifecycleAndDangerZones.prompt.md` | Deleted. Shipped: Toast, enrollment/integration Danger Zones, GA create. In-pass: AGENTS § Detail Danger Zone (reason, not type-the-name); lifecycle-prompt cite. No parking. |
| `.github/prompts/plan-adminUiOpenapiClientGeneration.prompt.md` | Deleted. Orval Phase 1+2 shipped (types, hooks, `usePaginatedFromOrval`). In-pass: AGENTS spec vs generated; drop dead `models.ts`; retarget data-model + Orval status docs. Parked P-061, P-062. |
| `.github/prompts/plan-apiKeysScreenUxEnhancement.prompt.md` | Deleted. List+detail, PATCH, revoke-with-reason, filters, backend sort shipped. In-pass: wireflow `/api-keys/:id` + edit. Parked P-063, P-064. |
| `.github/prompts/plan-auditLogsDatePresets.prompt.md` | Deleted. Backend `createdAfter`/`createdBefore` + shared `DateRangeFilter` shipped. In-pass: wireflow Audit Logs date presets. No parking. |
| `.github/prompts/plan-auditReasonJustification.prompt.md` | Deleted. Column `reason`, EventTypes, HMAC field 15, `ReasonFieldRow` shipped. Canon: `AUDIT_REASON_AND_JUSTIFICATION_UI.md`. In-pass: lifecycle-prompt cite; AGENTS pointer; HMAC field 15 in integrity + reason docs. No parking. |
| `.github/prompts/plan-auditsContextualises.prompt.md` | Deleted. Related-audits + expand-context + `/context` shipped. In-pass: ENDPOINT `integrationId`/`authAttemptId` + context GET; AGENTS + wireflow. Parked P-065. |
| `.github/prompts/plan-authAttemptChallengeProtectionStrategy.prompt.md` | Deleted. Duplicate of `I-2026-07-07` + `TB-2026-07-07` (Phase B unshipped). In-pass: I-* cite → TB; 2 vs 3–4 digit note. No parking. |
| `.github/prompts/plan-authProtocolSecurityAudit.prompt.md` | Deleted. Historical; `/respond` rate limit, trusted proxies, signed payloads shipped. Canon: 2026-07 mobile protocol assessment. In-pass: AUTH_SECURITY drift note. Parked P-066, P-067. |
| `.github/prompts/plan-backendLifecycleGovernancePlan1.prompt.md` | Deleted. `EntityEligibilityService`, ACTIVE/RETIRED, `operational` DTOs shipped. Canon: `LIFECYCLE_GOVERNANCE.md`. In-pass: ezkey-core AGENTS; archived operator-reference cite. No parking. |
| `.github/prompts/plan-dockerNonRootMigration.prompt.md` | Deleted. `USER spring:spring` / `ezkey` shipped; `su-exec` gone. In-pass: `docker/README.md` non-root note. Parked P-068. Did not edit `README-HA.md`. |
| `.github/prompts/plan-encryptionKeysFullOperability.prompt.md` | Deleted. Per-key re-encrypt, create-batches, batch detail, PRIMARY emphasis shipped. Canon: `REENCRYPTION_OPERATIONS.md`. In-pass: wireflow Encryption Keys. No parking. |
| `.github/prompts/plan-ezkeyAdminUiGlobalTenantRebranding.prompt.md` | Deleted. Unified `ezkey-admin-ui` (GA+TA) shipped; Encryption Keys is real. In-pass: AGENTS no-recreate `ezkey-tenant-ui`. Parked P-069. |
| `.github/prompts/plan-ezkeyDart.prompt.md` | Deleted. `ezkey_dart/` shipped (crypto + CLI). In-pass: CRYPTO + AUTH_ATTEMPT payload pointers; archived crypto-api cite. Parked P-070. |
| `.github/prompts/plan-ezkeyEntityLifecycleGovernance.prompt.md` | Deleted (plus `.html` preview twin). Operator canon is `LIFECYCLE_GOVERNANCE.md`. In-pass: retarget archived operator-reference + transverse v1. Parked P-071. |
| `.github/prompts/plan-ezkeyMobileAndroidPrerelease.prompt.md` | Deleted. About/splash/signing shipped; remaining option space in release audit + decision memo. In-pass: PLAY_PUBLISHING tracks/account/privacy URL; audit finding 3. Parked P-072, P-073. |
| `.github/prompts/plan-ezkeyMobileDeveloperGuide.prompt.md` | Deleted. `docs/MOBILE_DEVELOPER_GUIDE.md` shipped (Bruno, ports). In-pass: mobile AGENTS protocol routing. Parked P-074. |
| `.github/prompts/plan-ezkeyPam.prompt.md` | Deleted. Experimental PAM shipped in `ezkey-pam/`. In-pass: folder-local Integration API wording + compose `integration-api:7080`; experimental banner; dropped `README.txt`. Pulled Dart pointers back out of `docs/CRYPTO.md` / payload specs into `ezkey_dart/README.md` (same experimental-lane rule). Did **not** edit `docker/README.md`. Parked P-075. |
| `.github/prompts/plan-functional-test-docker.prompt.md` | Deleted. Host Maven against live stack is already in `ezkey-tests/`. Parked P-076. |
| `.github/prompts/plan-javaMelodyCollectorForEzkey.prompt.md` | Deleted. Unshipped; canon is `V-2026-0009` + `R-2026-0002` (opt-in, not default). In-pass: retarget V/R; drop “update plan-prompt”. No parking. |
| `.github/prompts/plan-m2mAuthAttemptTestCoverage.prompt.md` | Deleted. Integration API harness shipped (`configureForIntegrationApi`). In-pass: ezkey-tests AGENTS + WRITING_TESTS. Parked P-077. |
| `.github/prompts/plan-methodologyMicroSite.prompt.md` | Deleted. Explorer shipped (`product-docs/site/`, `methodology.ezkey.org`); 2026-08 ablation simplified to one Read track. No parking. |
| `.github/prompts/plan-openApiPresentationOrder.prompt.md` | Deleted. Shipped PR #181. Canon: design + V/I/TB 2026-06-02. In-pass: retarget working-plan cites; drop dead methodology-decision links. No parking (`I-2026-06-03` already holds intra-tag follow-on). |
| `.github/prompts/plan-openApiSpecLifecycle.prompt.md` | Deleted. Unshipped; canon is V/I/TB 2026-06-02 host-neutral specs. In-pass: retarget V/I; Bruno env-var note. No parking. |
| `.github/prompts/plan-paintingTheAdminUiWithAi.prompt.md` | Deleted. Essay published EN/FR 2026-05-26; research stays in `ezkey-org-editorial/`. No parking. |
| `.github/prompts/plan-postmanEnrollmentAuthCryptoFlow.prompt.md` | Deleted. Crypto payload-helper enrollment types + Bruno `enrollments-auth/` shipped. Parked P-078. |
| `.github/prompts/plan-prePilotageExperimentalEzkey.prompt.md` | Deleted. EXP1 lab is `experimental-hybrid/` (not September release). In-pass: README lab framing. Onboarding track remains `I-2026-05-23` (activation codes), not recovery-codes-first. Parked P-079, P-080. |
| `.github/prompts/plan-reencryptionSqlIndexing.prompt.md` | Deleted. Option B shipped as `I-2026-0029` / `TB-2026-07-26`. In-pass: ezkey-core AGENTS indexed `*_encryption_key_id` vs LIKE. No parking. |
| `.github/prompts/plan-refreshToken.prompt.md` | Deleted. Unshipped OAuth Access/Refresh; deferred in the prompt itself. Opaque single token + sliding 2h TTL shipped. In-pass: admin-api AGENTS. Parked P-081. |
| `.github/prompts/plan-securityPentestCurated.prompt.md` | Deleted. Lane shipped (`I-2026-07-12` / `TB-2026-07-12`, runner, campaign notes through pass-03). In-pass: root AGENTS keyword; retarget I/TB/evaluation plan cites. Parked P-082. |
| `.github/prompts/reported/plan-worktreeDockerStackNaming.prompt.md` | Kept. Unique Compose identity option space (`buildQualifier` → `-p` + drop `container_name`); Maven qualifier already isolates artifacts only. No `I-*`. |
| `.github/prompts/archived/2026-04/plan-acmeDemoInternetExposureHardening.prompt.md` | Deleted. Session isolation + Lightsail ACME shipped. In-pass: demo-app-acme AGENTS per-`HttpSession` + EXP1 folder pointer. Parked P-083 (closed 2026-08-15). |
| `.github/prompts/archived/2026-04/plan-adminUiLifecycleGovernancePlan2.prompt.md` | Deleted. `OperationalWarning` shipped. In-pass: Admin UI AGENTS § Detail Danger Zone; retarget operator-reference prompt. No parking. |
| `.github/prompts/archived/2026-04/plan-auditChainLifecycle.prompt.md` | Deleted. Closeout twin of already-deleted Cursor plan. In-pass: core AGENTS lifecycle pointer; INTEGRITY FSM + exceptional `seal-archive`; banners on REFRAMING / NEXT_SESSION_BRIEF; retarget data-model + integrity-cluster cites. Export remains `I-2026-06-28`. Parked P-084. |
| `.github/prompts/archived/2026-04/plan-auditGapDeclarationUx.prompt.md` | Deleted. Phase 1 UI + `ezkey_alert` shipped. In-pass: INTEGRITY auto-run; Admin UI AGENTS gap pointer; dropped dead plan cite in `audit-logs.tsx`. Parked P-085–P-087. |
| `.github/prompts/archived/2026-04/plan-backendConfigReferenceDocs.prompt.md` | Deleted. Colocated `CONFIGURATION.md` + `docs/configuration/README.md` + processor metadata shipped. Root AGENTS already has the update rule. No in-pass; no parking. |
| `.github/prompts/archived/2026-04/plan-coreSecuritySplit.prompt.md` | Deleted. `EncryptionOperations` + Holder in core; Tink impl in `ezkey-core-security`. In-pass: core AGENTS contract pointer. Parked P-088. |
| `.github/prompts/archived/2026-04/plan-dangerZoneEnrollmentCardUx.prompt.md` | Deleted. Cards + goBack-after-delete shipped. In-pass: mobile AGENTS fallback/goBack; screens matrix Danger Zone cells. No parking. |
| `.github/prompts/archived/2026-04/plan-duMonolithAuContrat.prompt.md` | Deleted. Essay shipped EN/FR (`from-monolith-to-contract` / `du-monolithe-au-contrat`); site AGENTS already has the publication checklist. No in-pass; no parking. |
| `.github/prompts/archived/2026-04/plan-enrollmentChallengePolicySurfacing.prompt.md` | Deleted. Rolled back; field gone. In-pass: mobile AGENTS protocol-minimalism rule; MOBILE_DEVELOPER_GUIDE pending-field sentence. No parking. |
| `.github/prompts/archived/2026-04/plan-ezkeyDartFinal.prompt.md` | Deleted. Closeout twin of `plan-ezkeyDart`. Canon stays in `ezkey_dart/`. No in-pass; no parking (`P-070` already covers oracle integration tests). |
| `.github/prompts/archived/2026-04/plan-lifecycleGovernanceOperatorReference.prompt.md` | Deleted. Deliverable is living `docs/LIFECYCLE_GOVERNANCE.md`. Root/core/Admin UI AGENTS already point there. No in-pass; no parking (`P-071` already covers remaining `plans/` lifecycle corpus). |
| `.github/prompts/archived/2026-04/plan-minimalAlertSubsystem.prompt.md` | Deleted. `ezkey_alert` + `/alerts` + `docs/ALERTS.md` shipped. In-pass: docs README index; core AGENTS `AlertService` pointer. Parked P-089. |
| `.github/prompts/archived/2026-04/plan-mobileAuthApiClientStrategy.prompt.md` | Deleted. Orval + local Auth spec + `yarn generate:api` shipped. Mobile AGENTS § Contract-First already covers it. No in-pass; no parking. |
| `.github/prompts/archived/2026-04/plan-mobileEzkeyArticleFr.prompt.md` | Deleted. Essay shipped FR/EN; MOBILE_DEVELOPER_GUIDE chaining + PKI/CA notes shipped. No in-pass; no parking. |
| `.github/prompts/archived/2026-04/plan-mobileInstallationModelRefactor.prompt.md` | Deleted. Nested `Installation` shipped. In-pass: mobile AGENTS nested-object / no second collection. No parking. |
| `.github/prompts/archived/2026-04/plan-mobileKeyTrustBoundary.prompt.md` | Deleted. Home IA + unsigned instance-info shipped (`I-2026-07-20` done). In-pass: AGENTS display-only branding. Parked P-090. |
| `.github/prompts/archived/2026-04/plan-mobileProductionSecurityHardening.prompt.md` | Deleted. MITM lab removed; `targetSdk` 36; production-clean contract is living. In-pass: AGENTS no-reintroduce MITM. Pinning already `V-2026-0006` / `I-2026-07-25` / `R-2026-0001`. No parking. |
| `.github/prompts/archived/2026-05/plan-mobileDependencyReview.prompt.md` | Deleted. S01–S23 inventory; living baseline is `MOBILE_STACK_AND_ARCHITECTURE.md` (RN 0.86.2). In-pass: retargeted I/TB/stack cites. Parked P-091. |
| `.github/prompts/archived/2026-05/plan-mobileReleaseMessaging.prompt.md` | Deleted. Home banner + ReleaseNotes + About split shipped. In-pass: mobile AGENTS Production IA / no floating badge. Parked P-092–P-094. |
| `.github/prompts/archived/plan-deviceSimulation.prompt.md` | Deleted. `ezkey device` enroll/auth/list/show/remove shipped. In-pass: CLI README feature + examples. Parked P-095. |
| `.github/prompts/archived/plan-enrollmentLifecycleRevocation.prompt.md` | Deleted. Phase 1 revoke/deactivate/reactivate + bulk shipped. Canon: `LIFECYCLE_GOVERNANCE.md` §3.3. In-pass: ENDPOINT single-enrollment POSTs. Parked P-096. |
| `.github/prompts/archived/plan-extractEzkeySdk.prompt.md` | Deleted. Zero-dep `EzkeyClient` + ACME shipped against Integration API. In-pass: java README + Javadoc Admin→Integration. Parked P-097–P-098. |
| `.github/prompts/archived/plan-ezkeyAdminTui.prompt.md` | Deleted. Admin UI is primary; `ezkey --tui` is read-only. In-pass: TUI_SCOPE no `ezkey-admin` competing console. Parked P-099. |
| `.github/prompts/archived/plan-ezkeyDartCryptoApi.prompt.md` | Deleted. Ed25519 + payload-helper + Bruno shipped. In-pass: crypto AGENTS Use Case → Bruno; Dart stays in `ezkey_dart/`. Parked P-100. |
| `.github/prompts/archived/plan-fixJpaSharedReferencesIntegrationI18n.prompt.md` | Deleted. Scalar tenant-id + verify flush shipped; i18n later flattened (V7). In-pass: Admin/Auth OSIV notes retargeted. Parked P-101. |
| `.github/prompts/archived/plan-flattenIntegrationEntity.prompt.md` | Deleted. Logo + i18n flatten shipped (V7). In-pass: core AGENTS no `integration_logo` / bind `integrationLogo`. Parked P-102. |
| `.github/prompts/archived/plan-integrationCodeCliTui.prompt.md` | Deleted. CLI `--code` + TUI list/detail shipped. In-pass: USAGE_GUIDE create examples (no logo/i18n). Parked P-103. |
| `.github/prompts/archived/plan-integrationUniqueness.prompt.md` | Deleted. Per-tenant `code` uniqueness + 409 shipped. In-pass: ENDPOINT create; core AGENTS unique slug. No parking. |
| `.github/prompts/archived/plan-m2mApiModule.prompt.md` | Deleted. Shipped as Integration API `7080`. In-pass: `docker/README.md` Access/Swagger. Parked P-104–P-105. |
| `.github/prompts/archived/plan-qrCodeContentEnhancement.prompt.md` | Deleted. JSON QR + optional `authUrl` shipped. In-pass: admin-api AGENTS `QrCodePayloadService`. Parked P-106. |
| `.github/prompts/archived/plan-tenantDeactivationIntegrity.prompt.md` | Deleted. Runtime master switch + RFC 9457 shipped. In-pass: admin-api AGENTS no cascade / system tenant. Parked P-107. |
| `.github/prompts/archived/plan-tenantEnrichmentTier1.prompt.md` | Deleted. Identity/contact/governance + PUT shipped (V5). In-pass: admin-api AGENTS partial PUT, no PATCH, system tenant not updatable. Parked P-108–P-109. |
| Documentary parking burn-down (2026-08-17) | Closed P-058, P-082, P-084, P-092, P-095, P-097, P-099, P-102. Remaining rows are deferred product/code. |
| Parking residue reclassification (2026-08-17) | Closed stale P-072, P-078. Closed deferred-unfunded P-057, P-070, P-075–P-076, P-079–P-080, P-090, P-094, P-096, P-098, P-100–P-101, P-106, P-109. Residue grouped in closeout note. |
| Documentary parking burn-down (2026-08-15) | Closed P-074 (`MOBILE_DEVELOPER_GUIDE` Ezkey rename), P-081 (TUI_GUIDE single opaque token), P-083 (ACME README/AGENTS Ezkey rename). |

## Kept this pass (retained)

| Source plan | Reason |
|-------------|--------|
| `.cursor/plans/authentication_wait_evolution_brainstorm.plan.md` | Operator Keep (2026-08-11). Lane B retained option-space; still unique vs `V-2026-07-18` / `I-2026-07-18` (axes, comparison table, questions, evidence). Incubation sources in V/I continue to point here. |
| `.github/prompts/reported/plan-worktreeDockerStackNaming.prompt.md` | Operator Keep (2026-08-14). Unique option space: Compose `-p` from `buildQualifier` plus dropping fixed `container_name` so worktrees can coexist. Maven `.mvn/maven.config` already isolates artifacts only. No `I-*`. |

## Parking (open — verified residue)

Not unfinished documentary work. Grouped in
[`2026-08-17-cursor-plans-closeout.md`](2026-08-17-cursor-plans-closeout.md). No new `I-*`.

| ID | Bucket | Finding |
|----|--------|---------|
| P-071 | Next ablation | Temporary `plans/` lifecycle corpus (~25 files). Next documentary pass, not this PR. |
| P-059 | Admin UI | `EnrollmentFkLink` → `/enrollments/{id}` can 403 a Tenant Admin on an admin MFA row. |
| P-060 | Admin UI | GA list pages still lack a tenant `Select` (name columns link through). |
| P-061 | Admin UI | Raw `api.get` on audit-chain incidents; `api.getPublic` for instance-info. |
| P-062 | Admin UI | Local `build`/`dev` do not run `generate:api` (`prebuild` optional). |
| P-063 | Admin UI | API-keys list still shows an ID column. |
| P-064 | Admin UI | API-keys footer status breakdown not shipped. |
| P-065 | Admin UI | Related-audits for tenant / admin / API key / encryption key were out of MVP. |
| P-069 | Admin UI | GA-only routes use auth-only `ProtectedRoute`; TA can open the URL. |
| P-085 | Admin UI | Dedicated undeclared-gaps API + lookback/cap not shipped. |
| P-086 | Admin UI | No Playwright for gap Declare / Locate. |
| P-087 | Admin UI | 409 race has no inline dialog + refresh CTA. |
| P-089 | Admin UI | No Playwright dashboard → `/alerts` → declare-gap → resolved. |
| P-056 | Security / protocol | No REJECTED-with-past-`expiresAt` characterization test. |
| P-066 | Security / protocol | `/respond` still uses unlocked `findById`. |
| P-067 | Security / protocol | `AuditHmacService.verifyHmac` uses `String.equals`, not `MessageDigest.isEqual`. |
| P-068 | Security / protocol | Compose has no optional `user:` / `no-new-privileges`. |
| P-088 | Security / protocol | Auth + Integration API still scan `org.ezkey.security`. |
| P-104 | Security / protocol | `ApiKeyAuthenticationFilter` still copied admin-api vs integration-api. |
| P-105 | Security / protocol | API-key rate limits are in-memory per instance (Caffeine). |
| P-073 | Mobile | Play listing closeout (Data Safety, screenshots, listing copy). |
| P-091 | Mobile | `ios/Podfile.lock` not in repo (needs macOS CocoaPods). |
| P-093 | Mobile | Home release banner is always-on (no dismiss). |
| P-077 | Tests | Integration-API auth-attempt wait happy paths still thin. |
| P-103 | CLI / TUI | Unused `CreateIntegrationModal` vs read-only TUI. |
| P-107 | CLI / TUI | Tenant detail swallows API Problem Detail. |
| P-108 | CLI / TUI | Tenant detail omits org/contact fields. |

## Parking (closed 2026-08-17 — residue reclassification)

| ID | Disposition |
|----|-------------|
| P-072 | Stale: `ezkey_mobile/package.json` is `1.0.0`; Android `versionName` reads that field. |
| P-078 | Stale: `postman/collections/v2.1/` JSON is gone. Stub `postman/README.md` remains (ENDPOINT still cites it). |
| P-057 | Deferred product: OIDC/SAML + token-validation cache unfunded. Sliding TTL stays CONFIGURATION §12. |
| P-070 | Deferred experimental: Dart Crypto oracle tests stay in `ezkey_dart/`. |
| P-075 | Deferred experimental: `EZKEY_M2M_API_URL` rename stays inside `ezkey-pam/`. |
| P-076 | Deferred tooling: optional `scripts/test-docker.sh`. |
| P-079 | Deferred experimental: RDS / managed Postgres for EXP1. |
| P-080 | Deferred experimental: dedicated tunnel vs orange-cloud Lightsail. |
| P-090 | Deferred product: signed installation snapshot on bind. |
| P-094 | Deferred product: remote signed release announcements. |
| P-096 | Deferred product: enrollment revoke phases 2–4 (webhooks / SCIM / `externalId`). |
| P-098 | Deferred product: device-side Auth API Java SDK. |
| P-100 | Deferred product: generic Base64/SPKI crypto endpoints. |
| P-101 | Accepted posture: OSIV stays default `true`; comments in Admin/Auth `application.properties` record why. |
| P-106 | Deferred product: `ezkey://enroll?...` native-camera deep link. |
| P-109 | Deferred product: tenant quotas / JSONB metadata / per-tenant MFA policy. |

## Parking (closed 2026-08-17 — documentary closeout)

| ID | Disposition |
|----|-------------|
| P-058 | Fixed: Bruno `authentication-login-admin/auth-activate.bru` for `POST /api/v1/admin/auth/activate`. |
| P-082 | Fixed: `I-2026-07-12` and `TB-2026-07-12` → `done`; backlog index aligned. Further runs stay in `hygiene/security-pentest/`. |
| P-084 | Fixed: deleted French `docs/AUDIT_LOG_LIFECYCLE_NEXT_SESSION_BRIEF.md`. Living: CONFIGURATION § Audit Log Archive + `AUDIT_LOG_INTEGRITY.md`. Historical: `AUDIT_LOG_LIFECYCLE_REFRAMING.md`. |
| P-092 | Fixed: `MOBILE_SCREENS_AND_WIREFLOWS.md` Home banner + What's new (`ReleaseNotes`). |
| P-095 | Fixed: `USAGE_GUIDE.md` Device simulation section. |
| P-097 | Fixed: parent `ezkey-sdk/README.md` — Java Integration API client vs other folders; no `ezkey-docs/`. |
| P-099 | Closed: living `TUI_GUIDE.md` is read-only; leftover “full Textual App” checklist is `ARCHIVES/TUI_IMPLEMENTATION.md` (historical). |
| P-102 | Fixed: `MULTI_TENANT_OBSERVATIONS.md` banner — `integrationLogo` removed in V7. |

## Parking (closed 2026-08-13 — session burn-down)

| ID | Disposition |
|----|-------------|
| P-051 | Fixed: WAVE_1 + `EXCEPTION_SECURITY_ANALYSIS_2025-10-14.md` + `SECURITY_MULTI_TENANT.md` banners — `ErrorResponseDto` samples are pre-RFC-9457; living errors are `ProblemDetail`. |
| P-052 | Fixed: `ezkey-cli-python/TUI_GUIDE.md` create-integration flatten (`code`/`name`/`description`) + search `lifecycleStatus=ACTIVE`. |
| P-053 | Fixed: historical banners on `docs/plan/ADMIN_MFA_*.md` + `PLAN_UPDATES_SUMMARY.md` (`mfaEnrollment` → `enrollment` / `enrollmentId`). |
| P-054 | Fixed: `multi-tenancy-strategy.md` banner + samples → E.164 `+15551234567`. |
| P-055 | Fixed: `postman/README.md` leftover banner; retargeted `docs/ENDPOINT.md` + `ADMIN_UI_RECOVERY.md` to Bruno. Tree not deleted. |

## Parking (closed 2026-08-15 — documentary notes)

| ID | Disposition |
|----|-------------|
| P-074 | Fixed: `docs/MOBILE_DEVELOPER_GUIDE.md` EZKey → Ezkey. |
| P-081 | Fixed: `ezkey-cli-python/TUI_GUIDE.md` single opaque `token`; QUIT keeps it, LOGOUT clears; AuthManager methods match code. |
| P-083 | Fixed: `ezkey-demo-app-acme` README/AGENTS EZKey → Ezkey. |

## Parking (closed 2026-08-12 — mid-pass burn-down)

| ID | Disposition |
|----|-------------|
| P-046 | Fixed: rewrote `.cursor/plans/archived/README.md` as lean staging scaffold (no stale inventory). |
| P-047 | Fixed: `LIFECYCLE_GOVERNANCE.md` §3.3 diagram/copy → `CREATED` / `BOUND` / `VERIFIED` (no fictional `PENDING`). |
| P-048 | Fixed: `ezkey_mobile/AGENTS.md` — QR `authUrl` / `EZKEY_API_BASE_URL` fallback; no hard-coded tunnels. |
| P-049 | Fixed: `RECOVERY_CODES_LIFECYCLE_ANALYSIS.md` historical banner + §2.6 superseded (regenerate/issue-initial shipped). |
| P-050 | Deferred product: cookie Max-Age vs DB sliding window — remains documented in admin-api CONFIGURATION §12; no code in this burn-down. |

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
| P-013 | Partial: archived scaffolds opportunistic (pre-pilotage prompt deleted this pass). |
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
