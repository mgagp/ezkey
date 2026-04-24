# Plan: Phase 2 — Minimal alert subsystem (`ezkey_alert`)

## TL;DR

Promote operator-facing signals out of the audit trail into a dedicated minimal
`ezkey_alert` table owned by Ezkey itself (eat-your-own-dog-food, no external
dependency). The audit-chain gap detector becomes the first producer; the
existing dashboard alert widget becomes the first consumer (via a thin contract
swap, not a UI rewrite); a new **Alerts** section in the Admin UI provides the
list+detail surface, mirroring the audit-logs / auth-attempts patterns. Gap
declaration auto-resolves the matching alert atomically. SOC 2-oriented audit
trail of alert lifecycle (`ALERT_RAISED`, `ALERT_RESOLVED`) preserved.

Lightsail reality: a *new* additive Flyway migration (V11) creates the table
and an index — pure DDL, idempotent (`IF NOT EXISTS`), zero data migration. No
risk to the existing Lightsail database; clean-start stacks pick it up via the
normal Flyway baseline.

---

## Phases

### Phase A — Backend contract (DB + producer + read API)

1. **Flyway V11 — create `ezkey_alert`** in
   `ezkey-core/src/main/resources/db/migration/V11__alerts_minimal_subsystem.sql`.
   Columns (essential complexity only):
   - `alert_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
   - `alert_type VARCHAR(64) NOT NULL` (free string, validated at app layer
     via enum `AlertType` — first value `AUDIT_CHAIN_GAP_PENDING`)
   - `severity VARCHAR(16) NOT NULL` (`INFO` / `WARNING` / `CRITICAL`)
   - `status VARCHAR(16) NOT NULL` (`OPEN` / `RESOLVED`)
   - `dedupe_key VARCHAR(256) NOT NULL`
   - `payload JSONB` (gap boundaries, anchor checkpoint id, message, etc.)
   - `created_at`, `last_seen_at`, `resolved_at TIMESTAMPTZ`
   - `occurrence_count INT NOT NULL DEFAULT 1`
   - `resolved_by_admin_id INT NULL` (nullable; null = auto-resolved by system)
   - `resolution_reason VARCHAR(64) NULL` (e.g. `GAP_DECLARED`, `MANUAL`)
   - Partial unique index `UNIQUE (dedupe_key) WHERE status = 'OPEN'`
     → enforces "one open alert per dedupe key" at the DB level
   - Index `(status, created_at DESC)` for the dashboard "5 most recent open"
   - Index `(alert_type, status, created_at DESC)` for the search page
2. **Domain in `ezkey-core`**:
   - `org.ezkey.alert.domain.AlertEntity` (JPA entity)
   - `org.ezkey.alert.domain.AlertType` enum (`AUDIT_CHAIN_GAP_PENDING` only
     for now)
   - `org.ezkey.alert.domain.AlertSeverity`, `AlertStatus`,
     `AlertResolutionReason` enums
   - `org.ezkey.alert.repository.AlertRepository extends JpaRepository`,
     `JpaSpecificationExecutor` (mirrors `AuditLogRepository` shape)
   - `org.ezkey.alert.service.AlertService` with:
     - `raiseOrTouch(AlertType, AlertSeverity, String dedupeKey, JsonNode payload)`
       → atomic upsert: if open row with same dedupe_key exists, update
       `last_seen_at` + `payload` + `++occurrence_count`; else insert.
       Implemented via "try update, else insert" inside `@Transactional`;
       partial unique index protects against race.
     - `resolveByDedupeKey(String dedupeKey, AlertResolutionReason, Integer adminId)`
     - `search(AlertSearchCriteria, Pageable)` returning `Page<AlertEntity>`
     - `findById(Long alertId)`
     - `findRecentOpen(int limit)` for dashboard widget
   - **Audit emissions**: every successful `raiseOrTouch` (when *new* row, not
     just touch) emits `EventType.ALERT_RAISED`; every `resolveByDedupeKey`
     emits `EventType.ALERT_RESOLVED`. Add both to `EventType` enum + the
     relevant `EventTypeFamily` (probably new `ALERT` family). SOC 2 trail
     preserved without polluting audit with the alert *content*.
3. **Switch the producer** —
   `AuditChainScheduler.detectPreLookbackGap()` (lines ~189–240): replace the
   `auditLogService.log(alert)` call with
   `alertService.raiseOrTouch(AUDIT_CHAIN_GAP_PENDING, WARNING, "AUDIT_CHAIN_GAP_PENDING:" + checkpointId, payloadJson)`.
   Remove the local JSON string-building (use Jackson). Inject `AlertService`,
   drop `AuditLogService` dependency from this class if no longer needed.
4. **Auto-resolve on gap declaration** —
   `AuditLifecycleService.declareGap(...)` (in `ezkey-core`): inside the same
   `@Transactional` that creates the `GAP_DECLARATION` checkpoint, call
   `alertService.resolveByDedupeKey("AUDIT_CHAIN_GAP_PENDING:" + anchorCheckpointId, GAP_DECLARED, adminId)`.
   No-op if alert not present.
5. **Deprecate `EventType.AUDIT_CHAIN_GAP_PENDING`** — mark with
   `@Deprecated(since = "0.2", forRemoval = false)` and a Javadoc note that
   historical rows may exist on long-lived deployments (Lightsail). Do not
   delete the enum value (would break enum lookup on existing audit rows).
6. **Admin API controller** —
   `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AlertController.java`,
   mirroring `AuditLogController` style:
   - `GET /api/v1/alerts` — paginated search.
     - Filters: `status`, `alertType`, `severity`, `from`, `to` (created_at
       range), `dedupeKey` (exact, debug aid).
     - Sort whitelist: `createdAt`, `lastSeenAt`, `severity`, `status`.
     - Default sort: `createdAt,DESC`. Default size: 20.
     - `@PreAuthorize("hasRole('ADMIN')")` — Global Admin only (return 403
       for Tenant Admin; alerts are instance-level).
   - `GET /api/v1/alerts/{alertId}` — single alert detail (404 if missing).
   - **Out of scope this slice**: manual resolve / acknowledge endpoint. The
     only resolution path is the auto-resolve from gap declaration.
   - DTOs in `ezkey-admin-api/.../dto/`:
     `AlertResponseDto`, `AlertSearchCriteria` (or use `@RequestParam`s
     directly like `AuditLogController` does — recommended for parity), and
     `AlertPayloadDto` polymorphic by `alertType`. For Phase A, the only
     payload variant is the gap-pending one — reuse the existing
     `DashboardGapPendingDetailsDto` shape, renamed to
     `AuditChainGapAlertPayloadDto`.
   - MapStruct `AlertMapper` in `ezkey-core` (`org.ezkey.alert.mapper`).
   - Full SpringDoc OpenAPI annotations matching `AuditLogController` style
     (operation, parameter, responses 200/400/401/403/500).

### Phase B — `scanBasePackages` and module wiring

7. **Update `scanBasePackages`** in all three boot apps to include
   `org.ezkey.alert`: `AdminApplication`, `AuthApplication`,
   `IntegrationApiApplication`. (Auth and Integration apps need it because
   `AuditLifecycleService` lives in `ezkey-core` and is now wired with
   `AlertService`; without scan, beans won't resolve at startup. *Verify per
   `AGENTS.md` rule about new core packages.*)
8. **`CONFIGURATION.md`** — no new properties for the table itself, but if
   we externalize a "max alerts retained" knob in a future slice, add it then.
   Skip for now.

### Phase C — Dashboard widget refactor (no UI redesign)

9. **`DashboardService.buildAlerts`** — replace the `auditLogService.findByFilters(EventType.AUDIT_CHAIN_GAP_PENDING, …)`
   query and the `isGapDeclared` checkpoint-cross-check with a single
   `alertService.findRecentOpen(5)` call. Remove the `parseGapPendingDetails`
   helper and the checkpoint repository dependency *if* no longer used.
10. **`DashboardAlertItemDto` / `DashboardOverviewDto`** — repurpose the
    `alerts` field to carry the new alert shape (`alertId`, `alertType`,
    `severity`, `status`, `createdAt`, `payload`). Remove `auditLogId`.
    Keep `alerts` as the field name to minimize UI churn.
11. **`dashboard.tsx` widget (~L137, L179–220)** — minimal edits:
    - Update key from `alert.auditLogId` to `alert.alertId`.
    - Read `alert.payload.anchorCheckpointId` / `payload.estimatedGapMinutes`
      instead of `alert.eventDetails.*`.
    - Wire each list row to navigate to `/alerts/{alertId}` (and add a
      "View all" link to `/alerts`) — small UX upgrade, parity with
      *recent activity* feel.
12. **Removal note**: do **not** remove `EventType.AUDIT_CHAIN_GAP_PENDING`
    (see step 5). Old Lightsail audit rows will keep displaying in the audit
    page; that's fine — they're historical.

### Phase D — Spec refresh + Postman + Orval

13. **Clean-start, rebuild, refresh spec**:
    - `scripts/build-local.cmd` (per AGENTS.md autonomous Windows path)
    - Bring up the standard Docker stack
    - `scripts/update-specs.sh` to dispatch to
      `ezkey-admin-ui/openapi-spec.json` (per repo memory:
      `/memories/repo/openapi-spec-refresh.md` — prefer copy over symlink on
      Windows; restart stack so the new endpoints appear).
14. **Postman** — add a new collection
    `postman/collections/v2.1/EZ Key Alerts admin.postman_collection.json`
    mirroring the structure of *EZ Key Audit Logs admin*: `List alerts`,
    `Get alert by id`, with parameter examples covering `status=OPEN`,
    `alertType=AUDIT_CHAIN_GAP_PENDING`, date range. Update the
    *EZ Key Dashboard admin* collection example response to reflect the new
    `alerts[].payload` shape.
15. **Admin UI Orval regen** — `cd ezkey-admin-ui && pnpm run generate-api`
    (or `npm run generate-api`, whichever the repo standardizes; see
    `ezkey-admin-ui/package.json`). Confirm new types appear under
    `src/generated/admin-api/model`.

### Phase E — Admin UI Alerts page (list + detail)

16. **Sidebar nav entry** in
    `ezkey-admin-ui/src/components/layout/sidebar.tsx`:
    add `{ labelKey: 'alerts', path: '/alerts', icon: AlertTriangle, roles: ['GLOBAL_ADMIN'] }`
    after `auditLogs`. Add `'alerts'` to the `NavLabelKey` union and the
    `nav.alerts` translation in `locales/{en,fr}/layout.json`.
17. **Routes** — register `/alerts` and `/alerts/:alertId` in the router
    (locate via `app-shell.tsx` or `App.tsx`; mirror how `audit-logs` is
    registered).
18. **`pages/alerts.tsx`** — list page modeled on `audit-logs.tsx`:
    - `usePaginatedFromOrval<AlertResponseDto, GetAlertsParams>(...)` with
      `useGetAlerts`-style hook from generated client.
    - Filter bar: status (default `OPEN`), alertType, severity, date range
      `from`/`to`, with the project's standard collapsible filter UI.
    - Refresh button matching the existing pattern.
    - Columns: created at (relative + absolute tooltip), type, severity
      (badge), status (badge), short payload summary, "View" action → row
      click opens detail.
    - `ContextHelp` panels: "What is an alert?", "How alerts get resolved",
      pointing to the gap-declaration workflow for the audit chain type.
      i18n in `locales/{en,fr}/alerts.json`.
    - Empty state: explicit "No alerts. Healthy posture." block (parity with
      audit-logs empty states).
19. **`pages/alert-detail.tsx`** — detail page modeled on
    `enrollment-detail.tsx` / `integration-detail.tsx`:
    - Header with type, severity, status badges; created/last-seen/resolved
      timestamps; occurrence count.
    - Payload section (formatted for known `AUDIT_CHAIN_GAP_PENDING`,
      raw-JSON fallback for unknown types — forward compatibility).
    - Resolution panel: shows resolved_at, resolved_by_admin (if any),
      resolution_reason. For `GAP_DECLARED`, deep-link "View gap declaration
      checkpoint" → `/audit-logs?focusCheckpointId=...`.
    - Back navigation; refresh button.
    - **No write actions** in this slice (no manual resolve button) —
      essential complexity only.
20. **Dashboard widget cross-link** (already in step 11): each alert row
    links to `/alerts/{alertId}`; widget header gets a "View all" → `/alerts`.

### Phase F — Tests

21. **Backend unit + integration**:
    - `AlertServiceTest`: raise → touch (occurrence increments, not
      duplicate row); resolve; concurrent raise (relies on partial unique
      index); search/sort.
    - `AlertControllerTest` (`@WebMvcTest`): authz (GLOBAL_ADMIN only),
      pagination, filter wiring, 404 on missing id.
    - `AuditChainSchedulerTest` updates: assert it now calls `AlertService`
      not `AuditLogService.log(...)`. Keep one regression test asserting old
      `AUDIT_CHAIN_GAP_PENDING` audit row is no longer emitted.
    - `AuditLifecycleServiceTest`: declaring a gap with a matching open
      alert resolves it atomically; declaring with no alert is a no-op.
22. **Reactor build & checkstyle**:
    `mvn spotless:apply && mvn checkstyle:check && mvn clean install -DskipTests`
    then targeted `mvn test -pl 'ezkey-admin-api,ezkey-core,!ezkey-tests'`.
23. **Admin UI**:
    - `pnpm lint && pnpm typecheck && pnpm test` (component tests for the
      new pages — at minimum a smoke render with mocked Orval client).
    - Targeted Playwright: judgement call per AGENTS.md. **Recommendation**:
      add **one** scenario — operator opens dashboard with seeded alert,
      clicks through to `/alerts`, opens detail, declares the gap from the
      audit-logs page, returns to `/alerts`, sees the alert resolved. This
      covers the producer→consumer→auto-resolve loop end to end.

### Phase G — Documentation

24. **`docs/AUDIT_LOG_INTEGRITY.md`** — operator section: replace
    "alert appears in audit log as `AUDIT_CHAIN_GAP_PENDING`" with
    "alert appears in `/alerts` and the dashboard alerts widget; declaring
    the gap auto-resolves it". Note `EventType.AUDIT_CHAIN_GAP_PENDING` is
    deprecated and historical rows may remain.
25. **New `docs/ALERTS.md`** (short — half a page): the alert subsystem,
    current alert types, lifecycle (raise/touch/resolve), dedupe model,
    SOC 2 audit trail. Future-extension note: notification channels remain
    out of scope.

---

## Manual exploratory test summary (clean-start, demo-mode)

1. **Cold-start, no alerts** — clean-start stack, log in as Global Admin →
   dashboard alerts widget shows healthy state; sidebar **Alerts** entry
   visible; `/alerts` page shows "No alerts. Healthy posture."
2. **Tenant Admin posture** — log in as Tenant Admin → sidebar **Alerts**
   entry hidden; `GET /api/v1/alerts` returns 403.
3. **Provoke a gap** — stop the stack for >60 min (or use the project's
   downtime simulation pattern), restart → wait 5 min for the scheduler
   tick → dashboard widget surfaces one alert; `/alerts` lists it; detail
   page shows payload (`gapStart`, `estimatedGapEnd`, `anchorCheckpointId`).
4. **Dedup behaviour** — wait for the next scheduler tick → confirm the
   same row's `lastSeenAt` and `occurrenceCount` advance, no second row
   appears.
5. **Auto-resolve** — go to **Audit Logs** → declare the gap with a
   justification → return to **Alerts** with status filter `OPEN` → empty;
   switch to `RESOLVED` → the alert is there, with `resolvedByAdminId`,
   `resolutionReason=GAP_DECLARED`, link to the new gap-declaration
   checkpoint resolves correctly.
6. **SOC 2 audit trail** — in **Audit Logs**, filter by event family
   `ALERT` → `ALERT_RAISED` and `ALERT_RESOLVED` entries present, with
   structured `eventDetails` containing the alert id and dedupe key.
7. **Lightsail upgrade rehearsal** — pull the new build on Lightsail →
   Flyway runs only V11 → table created, no impact on existing audit rows;
   any historical `AUDIT_CHAIN_GAP_PENDING` audit row remains visible in
   audit-logs but is *not* resurrected as an alert.

---

## Relevant files

- `ezkey-core/src/main/resources/db/migration/V11__alerts_minimal_subsystem.sql` *(new)*
- `ezkey-core/src/main/java/org/ezkey/alert/**` *(new package: domain,
  repository, service, mapper)*
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainScheduler.java`
  — replace audit emission with `alertService.raiseOrTouch(...)` (lines
  189–240)
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditLifecycleService.java`
  — add `alertService.resolveByDedupeKey(...)` inside `declareGap`
- `ezkey-core/src/main/java/org/ezkey/audit/domain/EventType.java` —
  `@Deprecated` on `AUDIT_CHAIN_GAP_PENDING`; add `ALERT_RAISED` +
  `ALERT_RESOLVED`; new `EventTypeFamily.ALERT`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AlertController.java` *(new)*
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/AlertResponseDto.java`,
  `AuditChainGapAlertPayloadDto.java` *(new; rename existing
  `DashboardGapPendingDetailsDto` if shared, else keep both for boundary
  isolation — recommend separate to keep dashboard DTOs stable)*
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/DashboardService.java`
  — `buildAlerts(...)` rewired to `AlertService.findRecentOpen(5)`; remove
  `parseGapPendingDetails` + `isGapDeclared` helpers and the unused
  `checkpointRepository` dep
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/DashboardAlertItemDto.java`
  — fields swapped to alert-shape (`alertId`, `alertType`, `severity`,
  `status`, `createdAt`, `payload`)
- Three `*Application.java` files — `scanBasePackages` += `"org.ezkey.alert"`
- `ezkey-admin-ui/src/components/layout/sidebar.tsx` — add `alerts` nav item
- `ezkey-admin-ui/src/pages/alerts.tsx` *(new — modeled on
  `audit-logs.tsx`)*
- `ezkey-admin-ui/src/pages/alert-detail.tsx` *(new — modeled on
  `enrollment-detail.tsx`)*
- `ezkey-admin-ui/src/pages/dashboard.tsx` — widget rows link to
  `/alerts/{alertId}`, header gains "View all" link, payload field path
  updated
- `ezkey-admin-ui/src/locales/{en,fr}/{alerts,layout}.json` — i18n
- `postman/collections/v2.1/EZ Key Alerts admin.postman_collection.json`
  *(new)*
- `postman/collections/v2.1/EZ Key Dashboard admin.postman_collection.json`
  — example response payload updated
- `docs/AUDIT_LOG_INTEGRITY.md` — operator workflow updated
- `docs/ALERTS.md` *(new — short overview)*

---

## Decisions (assumptions to confirm in refinement)

- **Auth scope**: alerts are instance-level; **Global Admin only**. Tenant
  Admins do not see them anywhere.
- **No manual resolve in this slice**. Only path to `RESOLVED` is the
  gap-declaration auto-resolve. Manual resolve / dismiss can land later if
  a non-auto-closing alert type appears.
- **Notification channels (email/webhook/Slack) explicitly out of scope.**
- **Dashboard contract preserved**: keep `overview.alerts` as the dashboard
  field name; only swap its inner shape. Cheaper than introducing a new
  dashboard endpoint.
- **`EventType.AUDIT_CHAIN_GAP_PENDING` kept (deprecated)** for Lightsail
  historical compatibility — never deleted in this slice.
- **Dedupe key shape**: `"<ALERT_TYPE>:<discriminator>"`. For audit-chain
  gap that's `AUDIT_CHAIN_GAP_PENDING:<anchorCheckpointId>`.
- **Severity** for the audit-chain gap producer: `WARNING` (operator action
  required, but no immediate security incident).
- **No payload encryption**: gap payload is operational metadata, not PII;
  matches current audit `eventDetails` posture.

---

## Further considerations (worth your call before kickoff)

1. **Manual resolve / dismiss action** — keep out of this slice (recommended,
   simpler, single-path lifecycle), or include a `POST /alerts/{id}/resolve`
   with `RESOLUTION_REASON=MANUAL` for completeness?
   *Recommendation*: defer. Only the gap producer exists today; auto-resolve
   covers it cleanly. Add manual resolve only when a second alert type
   without a natural auto-close arrives.
2. **Alert retention** — silent infinite growth, or a passive TTL (e.g.
   purge `RESOLVED` rows older than 90 days)?
   *Recommendation*: defer to a later slice. Volume will be tiny; SOC 2
   posture argues for keeping resolved rows around for traceability. Revisit
   when we add a second alert type.
3. **`alertType` storage**: VARCHAR (forward-friendly, accepts unknown types
   gracefully on the UI) vs Postgres ENUM (stricter, requires migration to
   add types).
   *Recommendation*: VARCHAR, validated at the JPA boundary by the Java
   `AlertType` enum. Same pattern as `EventType` audit handling and aligns
   with the project's pragmatism principle.

---

## Closure note (2026-04-23)

Plan delivered end-to-end:

- Phase A–C backend (entity, repository, `AlertService`, `AlertController`,
  Flyway V11) ✅
- `AuditChainScheduler` switched to `alertService.raiseOrTouch(...)`;
  `AuditLifecycleService.declareGap` auto-resolves with `GAP_DECLARED` ✅
- `EventType.AUDIT_CHAIN_GAP_PENDING` removed cleanly (Lightsail experimental
  posture allowed dropping the deprecation step) ✅
- Phase D contracts: `update-specs.sh` refresh, Postman collections updated
  (audit-logs, dashboard) and a new **EZ Key Alerts admin** collection ✅
- Phase E Admin UI: `/alerts` list (filters: status / severity / type),
  `/alerts/:alertId` detail with typed `AUDIT_CHAIN_GAP_PENDING` payload
  renderer + deep link to audit-logs, sidebar entry (Global Admin only),
  i18n EN+FR, dashboard widget rewired to the new shape ✅
- Phase G docs: `docs/AUDIT_LOG_INTEGRITY.md` operator section updated;
  short `docs/ALERTS.md` overview created ✅
- Validation: `mvn install -DskipTests` reactor green, backend unit tests
  green (`AlertServiceTest`, `AlertControllerTest`, `AuditLifecycleServiceTest`),
  Admin UI `tsc -b && vite build` ✅, `npm run lint` no new violations,
  `vitest run` 49/49 ✅, manual end-to-end on a clean-start stack confirmed
  the alert appears in the dashboard widget, the navigation focus works,
  and the alert detail page renders the structured payload as expected.

### Known limitation discovered during manual validation

A real-world test stopping **only `admin-api`** in Docker (auth-api and
integration-api kept running) surfaced a case the plan did not model:

1. The audit-chain scheduler lives in `admin-api`, so checkpoint creation
   stops while the module is down. The next checkpoint after recovery has a
   `window_start` matching the recovery moment, leaving a hole in the
   checkpoint timeline (concrete reproduction in the dev DB:
   checkpoint 106 → 107, hole `[2026-04-22T20:04Z, 2026-04-22T20:17Z)`).
2. `AuditChainScheduler.detectPreLookbackGap()` correctly raises an
   `AUDIT_CHAIN_GAP_PENDING` alert (alert id 1, dedupe key
   `AUDIT_CHAIN_GAP_PENDING:106`).
3. The dashboard widget displays the alert; navigation and focus on the
   timeline work as designed.
4. **However**, `auth-api` kept writing audit entries during the window
   (4 entries in the reproduction: `ADMIN_LOGIN`, `AUTH_ATTEMPT_PENDING`,
   `AUTH_ATTEMPT_RESPOND`, `ADMIN_LOGIN`). When the operator triggers
   **Declare gap**, `AuditLifecycleService.declareGap` rejects the request
   with `"Gap declaration rejected: 4 audit entry/entries found in the
   declared gap period [...). A gap can only be declared for periods with
   no audit activity."` — the safety check correctly refuses to assert
   "nothing happened" when entries exist in the window.

The current alert subsystem and gap-declaration flow are **intact and
correct**. What is missing is a recovery path for the partial-failure case
where checkpoints stopped but audit entries kept arriving. This is a
materially different problem from "an outage produced a true silent gap"
and is therefore deferred to a dedicated follow-up plan rather than
patched inline (mixing the two would blur scope and complicate testing).

The two follow-up sections below capture the framing and recommendations
for those follow-up plans.

---

## Follow-up Plan #1 — Partial backend failure: orphaned audit entries during a checkpointing outage

### Context

Ezkey runs three Spring Boot modules: `admin-api`, `auth-api`,
`integration-api`. The audit-chain scheduler (creates the periodic
`REGULAR` checkpoints that seal each window of audit entries into the
hash-chained integrity proof) lives **only inside `admin-api`**. The
audit-entry **producers** (`AuditLogService.log(...)` callers) live in all
three modules.

When `admin-api` is down but `auth-api` and/or `integration-api` keep
running, audit entries continue to be persisted into `ezkey_audit_log`
while no checkpoints are created. On recovery, the chain "jumps" over the
outage window, leaving entries that exist in the table but are not covered
by any checkpoint — *orphaned entries* relative to the integrity proof.

### Observations from the manual reproduction (2026-04-22)

- `admin-api` stopped `~20:04Z` to `~20:17Z`; `auth-api` and
  `integration-api` continued.
- 4 entries were written into the gap: `ADMIN_LOGIN` (×2),
  `AUTH_ATTEMPT_PENDING`, `AUTH_ATTEMPT_RESPOND`. (Note: those `ADMIN_LOGIN`
  entries were produced by `auth-api`, which owns the admin login flow —
  not by `admin-api` itself.)
- Checkpoints jumped from `id=106 (window_end=2026-04-22T20:04Z)` to
  `id=107 (window_start=2026-04-22T20:17Z)`.
- The alert subsystem behaved correctly: `AUDIT_CHAIN_GAP_PENDING` raised
  on the next scheduler tick with anchor checkpoint 106 and an estimated
  13-minute gap.
- `POST /api/v1/audit-logs/lifecycle/declare-gap` correctly refused the
  declaration because the safety guard "no entries in the declared period"
  is violated.

### Problem statement

The `GAP_DECLARATION` mechanism was designed under the implicit assumption
that a gap = a true outage = no writes. In practice, the most common
failure mode is the opposite: a **partial** outage where checkpointing
stops but writes continue. The existing alert+declaration flow then funnels
the operator toward a button that is guaranteed to fail, with no in-system
remediation path — they would have to either tamper with the DB directly
or accept a permanent "undeclared gap" status on the integrity report.

### Solution candidates

(Synthesis of the discussion held during this session.)

1. **Retro-cover instead of declaring a hole.** When entries exist in the
   gap window, write a new checkpoint type (`RECOVERY_SEAL` or
   `BACKFILL_SEAL`) instead of `GAP_DECLARATION`. The new checkpoint
   computes the hash over the orphaned entries and links into the chain
   normally, so the integrity property "every entry is covered by a
   checkpoint" is restored without falsely claiming the window was empty.
2. **Extend `declareGap` with an explicit acknowledgement mode.** Add an
   `acknowledgeOrphanedEntries=true` flag (or a separate
   `POST .../recover-orphaned` endpoint) that, given a stronger
   justification, performs the retro-cover described in (1). Default
   behaviour stays strict; the operator gets a clear, scoped escape hatch
   for the mixed case.
3. **Enrich the alert payload.** At alert-raise time, `AuditChainScheduler`
   already knows the gap window. It should additionally count the audit
   entries within `[gapStart, estimatedGapEnd)` and include
   `orphanedEntryCount` in the payload. The Admin UI can then **branch the
   primary action** in the alert detail / declaration dialog:
   - `orphanedEntryCount = 0` → propose "Declare gap" (current flow).
   - `orphanedEntryCount > 0` → propose "Cover orphaned entries" (new
     recovery flow), with the count visible up-front so the operator
     knows what they are signing off on.

### Recommended scoping (kickoff framing for the follow-up plan)

**Recommendation: (1) + (3), with (2) as the API surface that exposes (1).**
Reasoning:

- (1) is the only candidate that produces a *correct* integrity proof in
  the partial-failure case; the other options are at best UX patches
  around the missing primitive.
- (3) makes the UI honest from the moment the alert is raised, instead of
  letting the operator discover the failure on submit.
- (2) gives the backend a clean, auditable entry point with its own
  authorization, justification length requirement, and audit emission
  (`RECOVERY_SEAL_DECLARED`?), without overloading `declareGap`'s
  semantics.

Out of scope for that follow-up: HA-isation of the scheduler (covered in
follow-up #2), retention of `RESOLVED` alerts, manual alert resolve.

### Definition of done (for the follow-up plan)

- New checkpoint type `RECOVERY_SEAL` (or chosen name) defined,
  hash-chained exactly like `REGULAR` checkpoints, surfaced by the
  `chain-integrity` and `chain-checkpoints` endpoints with appropriate
  filters and rendering.
- Backend endpoint that performs the retro-cover atomically with alert
  auto-resolve (new `AlertResolutionReason` value, e.g.
  `ORPHANED_ENTRIES_COVERED`).
- `AUDIT_CHAIN_GAP_PENDING` alert payload carries `orphanedEntryCount` and
  `orphanedEntrySampleIds` (small sample, e.g. up to 5).
- Admin UI alert detail / Integrity panel:
  - When `orphanedEntryCount = 0`, current "Declare gap" flow.
  - When `orphanedEntryCount > 0`, the primary action is "Cover orphaned
    entries" with a confirmation dialog showing the count and a sample
    list, requiring a justification ≥ N chars.
- Reproduction test (the 2026-04-22 scenario): stop `admin-api`, generate
  authentic audit traffic via `auth-api`, restart, expect the alert to
  carry `orphanedEntryCount > 0`, expect the recovery flow to succeed and
  auto-resolve the alert.
- `docs/AUDIT_LOG_INTEGRITY.md` and `docs/ALERTS.md` updated to describe
  both flows side-by-side ("true gap" vs "orphaned entries").

### Reference material

- This plan (closed; archived under
  `.github/prompts/archived/2026-04/plan-minimalAlertSubsystem.prompt.md`).
- `ezkey-core/src/main/java/org/ezkey/audit/service/AuditChainScheduler.java`
  (`detectPreLookbackGap`).
- `ezkey-core/src/main/java/org/ezkey/audit/service/AuditLifecycleService.java`
  (`declareGap` and the "no entries in window" safety check).
- `docs/AUDIT_LOG_INTEGRITY.md` § operator workflow + § Operator alerts.
- DB reproduction fixtures: alert id 1, anchor checkpoint 106, audit
  entries 4–7 in the dev DB at the time of writing.

---

## Follow-up Plan #2 — Hardening the recovery path: scheduler responsibility and HA posture

### Context

Follow-up #1 fixes the *symptom* (orphaned entries can be sealed
correctly). This follow-up addresses the *cause*: a transverse,
instance-level responsibility (sealing the audit chain) is hosted inside
a single business module (`admin-api`) that primarily serves synchronous
HTTP traffic for operators. When that module is down, the integrity
guarantee silently degrades while the user-facing surface (auth, integration)
keeps working — i.e. the most damaging failure mode is also the least
visible.

### Observations

- The scheduler is a singleton inside `admin-api`. There is no fallback if
  `admin-api` is stopped, restarted, or scaled to zero.
- The other two modules (`auth-api`, `integration-api`) continue producing
  audit entries during such outages — confirmed by the 2026-04-22
  reproduction (4 orphaned entries in 13 minutes of single-module downtime).
- Today, this coupling is essentially historical: scheduler code happened
  to be co-located with admin endpoints. There is no functional reason for
  the dependency.
- This is also the natural locus for *future* instance-level jobs (key
  rotation, archive sealing, retention purges, etc.), so the architectural
  decision here will compound.

### Problem statement

How should Ezkey host instance-level scheduled jobs so that:

- A single-module outage does not silently suspend integrity-critical
  background work.
- The scheduling discipline scales to additional jobs without each one
  re-debating its execution model.
- The footprint stays consistent with the project's pragmatism principle
  (no new runtime to deploy "just in case").

### Solution candidates

(Synthesis of the discussion held during this session — A/B/C framing.)

**A. Keep the coupling, make it robust via a distributed lock.** Run the
scheduler in *every* module (`admin-api`, `auth-api`, `integration-api`),
but guard each tick with a Postgres advisory lock or ShedLock. Whichever
module currently holds the lock executes the tick; if it dies, the next
tick goes to whoever is still up.

- **Pros**: no new deployable; resilient as long as ≥ 1 module is up; the
  pattern composes cleanly to additional jobs.
- **Cons**: every module has to embed (or import) the scheduler code; the
  "owner" can change dynamically, complicating logs and metrics
  attribution; cross-module test scenarios become more involved.

**B. Extract a dedicated `ezkey-jobs` (or `ezkey-scheduler`) module.** A
new Spring Boot deployable that owns *all* instance-level background work
and exposes no HTTP API of its own.

- **Pros**: clean separation of concerns; the scheduler module's
  observability and lifecycle are decoupled from API traffic; future jobs
  have an obvious home.
- **Cons**: introduces a new module to build, deploy, monitor, and
  document; if it runs as a singleton it just *moves* the
  single-point-of-failure; meaningful resilience requires deploying it
  N>1 with the same kind of lock as option A → option A's complexity
  *plus* a new deployable.

**C. Accept the outage, make the recovery first-class.** Keep the
scheduler in `admin-api` but treat "checkpoints behind realtime" as a
real, observable, recoverable state:

- On `admin-api` startup, compare `MAX(checkpoint_id).window_end` with
  `MAX(audit_log_id).created_at` and run a catch-up routine that creates
  the missing checkpoints, using `RECOVERY_SEAL` (from follow-up #1) where
  audit entries exist in the gap window.
- Surface "scheduler last successful tick" as a health/observability
  signal so external monitoring (Caddy logs aside, ops dashboards
  eventually) can alert on prolonged silence.
- No changes to the deployment topology.

### Recommended scoping (kickoff framing for the follow-up plan)

**Recommendation: ship (C) first, revisit (A) only when concrete fresh-
sealing SLAs require it. Defer (B) until a second instance-level job
exists.**

Reasoning:

- (C) addresses the operational problem at minimal cost: no new module,
  no distributed locking, no observability rewrite. It depends on
  follow-up #1's `RECOVERY_SEAL` primitive being in place — which makes
  the sequencing natural.
- (A) addresses a different problem: bounding the latency of seal
  creation. Useful when there is an external requirement for it (e.g.
  audited SOC 2 with a measurable freshness SLA on the chain). Without
  that requirement, it is accidental complexity.
- (B) is appealing for cleanliness but is premature: it adds a
  deployable to solve a single-job problem and ends up needing (A)'s
  mechanics anyway for resilience.

This sequencing also keeps the architectural blast radius small per
session: follow-up #1 introduces a primitive; follow-up #2 wires it into
startup recovery; future plans can independently revisit the topology
question if a new job arrives.

### Definition of done (for the follow-up plan)

- Startup hook in `admin-api` that detects checkpoint lag and runs the
  catch-up routine (using `RECOVERY_SEAL` for windows containing
  orphaned entries, `REGULAR` for empty windows).
- Idempotent and re-entrant: re-running the hook on an already-recovered
  state is a no-op; concurrent admin-api boots (HA scenarios) coordinate
  via a Postgres advisory lock so only one performs the catch-up.
- Observable signal: a counter / health indicator exposing "seconds since
  last successful checkpoint tick" and "checkpoints created during last
  startup catch-up".
- Test coverage: an integration test that seeds an artificial lag (e.g.
  via direct DB manipulation in a test profile or by pausing a stub
  scheduler), restarts the relevant component, and asserts the chain is
  back to current with the expected mix of `REGULAR` / `RECOVERY_SEAL`
  checkpoints.
- `docs/AUDIT_LOG_INTEGRITY.md` updated to describe the recovery
  contract; brief design note (in the same doc, not a separate file) on
  why the scheduler stays in `admin-api` and what would trigger
  revisiting (A) or (B).

### Reference material

- This plan (closed; archived under
  `.github/prompts/archived/2026-04/plan-minimalAlertSubsystem.prompt.md`).
- Follow-up Plan #1 (above) — its `RECOVERY_SEAL` primitive is a
  prerequisite.
- `ezkey-core/src/main/java/org/ezkey/audit/service/AuditChainScheduler.java`
  (current scheduler entry point and tick logic).
- `AdminApplication.java`, `AuthApplication.java`,
  `IntegrationApiApplication.java` — current `scanBasePackages` posture
  illustrates how cross-module wiring already costs us coordination
  overhead, an argument relevant to the A vs B vs C trade-off.
