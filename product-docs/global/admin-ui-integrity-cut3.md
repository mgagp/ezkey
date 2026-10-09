# Admin UI — Integrity cut 3 (last verdict, micro-dashboard, verify cap)

## Metadata

- **Document ID:** `admin-ui-integrity-cut3`
- **Status:** `draft — pending Marc's review` (product decisions Marc 2026-10-09 recorded; OQ1–OQ3 and open questions still pending approval; implementation not started)
- **Owner (intention):** Julie (UX) / Marc (product) / Patrick (backend craft)
- **QA:** Isabelle via Walk Gate (after implementation PRs fill the walkable SHA)
- **Measurement:** Isabelle — baseline and after-PR-1 measurements **pending** (do not invent numbers)
- **Purpose:** Product intention lock for **Integrity cut 3** on the single `/integrity` atelier: show the last known chain verdict on open, replace the text async-job banner with Julie’s micro-dashboard, hydrate counters from the job DTO (not a sync recompute GET), and cap verification windows server-side. **Not** cut-2 density/modes redesign, **not** checkpoint-timeline rewrite, **not** job-history purge.
- **Related:**
  - Parent IA: [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md)
  - Job surface map: [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md)
  - Cut 2 phase A (density; D1 supersedes auto-verify-on-mount): [`admin-ui-integrity-cut2-phase-a.md`](admin-ui-integrity-cut2-phase-a.md)
  - Cut 2 phase B+B′ (atelier modes; tabs stay): [`admin-ui-integrity-cut2-phase-b.md`](admin-ui-integrity-cut2-phase-b.md)
  - Walk Gate (stub until impl SHA): [`backlog/walk-gates/WALK-2026-10-09-integrity-cut3.md`](backlog/walk-gates/WALK-2026-10-09-integrity-cut3.md)
  - Walk / Done canon: [`ui-walk-done-gate.md`](ui-walk-done-gate.md)
  - Quiet-window timeline (preserve): [`backlog/TB-2026-10-06-integrity-checkpoint-quiet-window-collapse.md`](backlog/TB-2026-10-06-integrity-checkpoint-quiet-window-collapse.md) (#674 / #676)
  - Dashboard precedent: [`backlog/ideas/I-2026-0007-admin-dashboard-integrity-widgets.md`](backlog/ideas/I-2026-0007-admin-dashboard-integrity-widgets.md) (I-2026-0007 — “3-second ok or not ok with honest scope”)
  - Parking residue: P-085 (lookback/cap) in [`hygiene/corpus-ablation/2026-08-cursor-plans-pass.md`](hygiene/corpus-ablation/2026-08-cursor-plans-pass.md)
  - GitHub: #692 (sync GET gate/cap), #696 (verdict scope vs picker), #676 (timeline quiet/gap + ~24 h default), #624 (async slot / soft last-result strip), #736 (async job audit emit broken)

---

## Product intention (locked Marc + Julie)

- Keep **one** `/integrity` atelier (Observer / Vérifier / Remédier tabs unchanged in structure).
- Global Admin whose core business is **not** Ezkey: at-a-glance clarity, proportionate craft, no over-engineering.
- **Invisible when healthy, explicit when not** remains the operability bar (cut-2 phase A lock) — cut 3 makes the *last verdict* the honest glance, not a fresh heavy recompute on every open.
- Values: product intent, operability, simplicity. No soft delete. Simple, regular patterns. Reuse dashboard `BatchHealthCard` / `BatchJobRow` patterns where sensible.

---

## Decisions (Marc, 2026-10-09) — verbatim

- **D1. Page open.** The page shows the last known verdict (latest job or nightly) with its scope. It runs an automatic verification ONLY if there is no verdict less than 24 h old covering the last 24 h. Otherwise, verification is an explicit "Revérifier" action. This supersedes `admin-ui-integrity-cut2-phase-a.md:32` ("auto chain-verify on mount OK").
- **D2. Window cap.** A single server-side property `max-window-days`, default 92, modeled on `operator-max-window-hours`, applies to async VERIFY jobs and to `GET /chain-integrity` (P-085).
- **D3. Structured counters on the job DTO** (additive OpenAPI fields): `chainStatus`, total, valid, invalid, archived, gaps. The UI hydrates from the DTO, not from the recompute GET. This is #692, option A, reduced to counters. `GET /chain-integrity` stays, but behind the gate and the cap.
- **D4. Micro-dashboard** (Julie's design) replaces the text banner, above the three tabs Observer / Vérifier / Remédier, which stay unchanged:
  - job state is separate from the chain verdict, and "réussi" is never used as a verdict;
  - it shows verdict, checkpoints, invalid, gaps, a check time that ages on the dashboard's 1 s clock, and "Revérifier";
  - a scope line shows the period, the source (auto / manual / nightly) and the author;
  - the new-result signal is the verdict tile's state change plus a ~2 s highlight only when the result arrives while the page is open, with an aria-live announcement and no toast when green;
  - a verdict older than 24 h is stale: grey, never green;
  - no automatic tab switch to Remédier; an "Ouvrir Remédier" button instead;
  - while the nightly runs, show "Vérification nocturne en cours…" instead of the 409 toast.
  Reuse the dashboard's `BatchHealthCard` / `BatchJobRow` patterns where sensible.
- **D5. Split.**
  - PR 1, backend: `entryHmac`-only projection with a digest characterization test, persisted counters, the gate and the cap, `findFirstByOrderByStartedAtDesc`.
  - PR 2, UI with Julie: the 24 h auto-run rule, hydration from the DTO, banner tone on `chainStatus`, the micro-dashboard, and #696 (show and align the verdict's scope).
  - Deferred: job-history purge (90 days), visibility-based polling, Orval follow-ups.
- **Values:** product intent, operability, simplicity, at-a-glance clarity for a Global Admin whose core business is not Ezkey. Proportionate, no over-engineering. No soft delete. Simple, regular patterns.

---

## Facts / current behaviour (main @ `a6d968ff`, re-verified)

Citations are file:line on current `main`. Paths for async-job / chain-verify services are under `ezkey-core/` (controllers under `ezkey-admin-api/`).

### Frontend

- Page `ezkey-admin-ui/src/pages/integrity.tsx`: `IntegrityPage` `:2485-2692`, Global-Admin redirect `:2590-2592`, mounts `IntegrityPanel` `:2676-2686`. Banner `src/components/feature/integrity-async-job-banner.tsx` ("one strip for the whole Integrity tab (Julie lock)", `:2`), mounted `active` always true (`integrity.tsx:1230`). Hand-written client `src/lib/integrity-async-jobs.ts` (base `/api/v1/audit-logs/integrity/jobs`, `:52`; "until Orval" `:2-3`). Hydration: `src/hooks/use-integrity-async-job-report-hydration.ts`, `src/lib/integrity-async-job-report-hydration.ts`.
- Tabs bar `integrity.tsx:1260-1285`; panels Observer `:1288`, Vérifier `:1358`, Remédier `:1718`. Default tab `src/lib/integrity-atelier-mode.ts:55-98`: `?mode` → investigation→Vérifier → `action=reconcile`→Remédier → **non-green→Remédier** → Observer. `chainNonGreen` from hydrated report (`integrity.tsx:1044-1066`); when `modeOverride` is null, `activeMode` follows `derivedMode` (`:1061-1068`), so a newly hydrated non-green report changes the visible tab; manual override is `selectAtelierMode` (`:1070-1080`). Tab change = conditional render (no remount); remounts on navigation back, reload, and the "affected entries only" toggle (`:2622-2688`).
- Auto-run on mount `integrity.tsx:1159-1207` (`useEffect(..., [])`): empty range → `defaultGapScanRange()` = today−7d → today (`:837-846`, comment "a backend lookback/cap is not shipped yet"). Calls `GET …/jobs/current` (`:1178`): RUNNING or non-abandoned EXPIRED/CANCELLED → nothing (`:1184-1189`); SUCCEEDED VERIFY_* → no job, hydration takes over (`:1190-1193`, `isSucceededVerifyJobForReportHydration`); none/FAILED/RUN_VALIDATION done/network error → `runChainCheck` (`:1195-1200`). `runChainCheck` (`:857-889`) POSTs `{type:'VERIFY_CHAIN_RANGE', from, to}`; bounds from `src/lib/date-range-presets.ts:170-191` (from = start of D-7, to = start of day after end, exclusive, display TZ). 409 → error toast (`busyToast`, `:879-882`), even on plain page open.
- Banner reads `/jobs/current` on activation (`banner.tsx:48-67`), polls every 3 s while RUNNING (`POLL_MS_RUNNING=3000`, `:20`, `:69-79`), ignores tab visibility. Text `:96-132`; relative time via `formatRelativeTime` (`src/lib/utils.ts:109-118`) computed once per render → "À l'instant" never ages. Tone `:134-140`: SUCCEEDED never reads `job.intact`, so gaps/violations show neutral "réussi". `role="status"` `:161`, no explicit aria-live, no animation.
- After SUCCEEDED, hydration (`use-integrity-async-job-report-hydration.ts:102-186`) calls `GET /api/v1/audit-logs/chain-integrity` with scope (`:127-130`); report card only in Vérifier tab (`integrity.tsx:1452-1504`) with period (`:1465-1472`) and Total/Valid/Invalid/Archived/Undeclared gaps (`:1473-1479`, `Stat` `:2432-2440`).
- i18n `src/locales/{fr,en}/audit-logs.json:286-308`; FR `:294` "En cours — … · depuis … · démarré par …", `:295` "Dernier résultat — réussi · … · {{summary}}". `{{summary}}` = server English `resultSummary` "Chain: status=OK, checkpoints=…, invalid=…, gaps=…" (`IntegrityAsyncJobService.java:437-445`). `verifyVsRunHint` claims "same range shown above" but the range is only visible in Vérifier.

### API

- `GET …/integrity/jobs/current` (mount + 3 s poll); `POST …/integrity/jobs` (auto on mount; manual buttons `:1372-1399` with `DateRangeFilter` `:1365-1369`, **no cap**; RUN_VALIDATION `:923-952` with optional `operator-max-window-hours`, null default, `RetroactiveIntegrityValidationService.java:211-228`); `GET /api/v1/audit-logs/chain-integrity?from&to` (auto after every VERIFY_CHAIN success); `GET /integrity-check` (after VERIFY_ENTRY).
- Global scope, all tenants, GLOBAL_ADMIN only (`IntegrityAsyncJobController.java:76,120,150,176`; `AuditLogController.java:587`). VERIFY validation: from/to required, to>from, **no max** (`IntegrityAsyncJobService.java:185-190`).
- OpenAPI `specs/admin-api/openapi-spec.json`: POST `/jobs` `:2411`, `/jobs/current` `:5787`, `/jobs/{jobId}` `:5725`, `/chain-integrity` `:5955`, `IntegrityAsyncJobStartRequest` `:7810`, `IntegrityAsyncJobResponse` `:7858`. DTO has `scopeFrom, scopeTo, resultSummary, intact, startedAt, finishedAt, startedByUsername` (`integrity-async-jobs.ts:22-39`) — **no structured counters**.

### Backend

- Single global slot: partial unique index `WHERE status='RUNNING'` (`V22__integrity_async_job.sql:63-65`), executor core=max=1 queue=1 (`IntegrityAsyncJobExecutorConfiguration.java:40-42`); 2nd POST → 409 (`IntegrityAsyncJobService.java:161-168`). `IntegrityHeavyCryptoGate` (process-local AtomicBoolean) shared with nightly → 409 (`:176-178`). Heartbeat TTL 60 min (`IntegrityAsyncJobProperties.java:44`). No cooldown/cache.
- `getCurrent()` (`:250-270`) loads **whole history** via `findAllByOrderByStartedAtDesc()` (`:258`; `IntegrityAsyncJobRepository.java:58`); repository already exposes unused `findFirstByOrderByStartedAtDesc()` (`:51`). No purge of `ezkey_integrity_async_job`.
- `verifyChain(from,to)` (`AuditChainVerificationService.java:118-342`, readOnly tx): loads all checkpoints in range (`:140`); per REGULAR checkpoint `computeEntriesDigest` (`:361-384`) does `findAll(spec, sort)` of **full `AuditLog` entities** (incl. JSONB `event_details`) per 5-min window = one SQL per checkpoint, HMAC over concatenated `entry_hmac`; special checkpoints skip (`:177-197`); chaining HMAC (`:238-255`), link/continuity (`:214-235`); `findEarliest/Latest` (`:275-276`). ≈ N+3 queries, ≈ 2N HMACs, all audit rows of the range, one transaction, no `clear()`. Entry-HMAC verify paginates by 500 (`AuditIntegrityService` `:56`); chain verify does not.
- Each job is **intended** to write `INTEGRITY_ASYNC_JOB_STARTED` (`IntegrityAsyncJobService.java:223, 487-508`) and `_COMPLETED` (`IntegrityAsyncJobStateService.java:181-186`) → **2 audit entries per auto-run open**. Sync GET writes none (`AuditLogController.java:613-631`). **Currently broken:** Isabelle’s 2026-10-09 baseline observed **0** `INTEGRITY_ASYNC_JOB_*` entries per open (`eventAction is required`, swallowed at WARN) — tracked in **#736**. Treat “2 entries per open” as the intended contract until #736 lands.
- Checkpoints: `AuditChainScheduler` cron `1 */5 * * * ?` (`:122`), window 5 min (`AuditChainProperties.java:39`), one per window even if empty (`EMPTY_WINDOW`, `AuditChainVerificationService.java:371-372`) → **288/day regardless of activity**.
- Nightly: `NightlyIntegrityProperties` cron `0 0 2 * * ?` (02:00 UTC = 22:00 ET), `windowHours=24`, enabled by default (`:31-37`); publishes last run + scope to dashboard (`last_run_scope`). The nightly does **not** write a row to `ezkey_integrity_async_job` (Isabelle 2026-10-09) — see OQ4.

### Prior specs / issues

- `admin-ui-integrity-cut2-phase-a.md:32` "Verification | Always primary; auto chain-verify on mount OK | Unchanged" — **superseded by D1** (see note on that line). Locked intent `:22-27` "invisible when healthy, explicit when not — Ezkey is not the adopter's core business" remains.
- `docs/AUDIT_LOG_INTEGRITY.md:333-335` "Chain integrity auto-runs over the selected range, or a 7-day UI default" → **PR 2 deliverable** (do not edit in this docs-only PR).
- `docs/plan/AUDIT_INTEGRITY_RANGE_RECOMMENDATIONS.md`: option A (range mandatory) chosen "to avoid full-table scans" (`:127-135`); option B (7-day default + 50k cap, `:83-97`) only adopted UI-side without cap.
- Corpus ablation P-085 (`hygiene/corpus-ablation/2026-08-cursor-plans-pass.md:245`): "Dedicated undeclared-gaps API + lookback/cap not shipped."
- PR #624 (async slot): only "Soft last-result when · who (Julie)"; nothing on scope or arrival signal.
- Issue #692 (open): sync GETs bypass gate, slot and cap; acceptance "No unbounded synchronous full-chain recompute reachable from page open/reload."
- Issue #696 (open): after reload the picker shows "last 7 days" while the hydrated report shows the job's range — "An operator can believe they're re-checking what they see when they're checking something else."
- Issue #736 (open): Integrity async job start/complete audit emit fails (`eventAction is required`, WARN) → **0** `INTEGRITY_ASYNC_JOB_*` rows per open instead of the intended 2. See Facts (Backend) and Risks.
- TB quiet-window (#674/#676): timeline grey (quiet) vs orange (real gap), ~24 h default, URL-only state — **preserve; the widget does not replace the timeline nor touch `cpQuiet`**.
- I-2026-0007 (dashboard, done): "3-second ok or not ok with honest scope" → `last_run_scope`, config summary. Direct precedent.

### Growth & cost (qualitative — early local numbers only)

- Auto window today ≈ 2,016–2,304 checkpoints (fixed 7-day UI default; e.g. ~2,126 ≈ 7 d + ~9 h). Larger ranges only via manual picker (~74 d / ~2 y), currently accepted uncapped.
- What grows with adoption is **E₇ = audit entries in 7 days**, all reloaded as full entities, **twice** per auto-run open (job + hydration GET). Cost ≈ a·N_checkpoints + b·E₇; memory ∝ E₇ × entity size.
- First local timings exist under Measurement plan (2026-10-09) — **not** a steady-state baseline (short-lived DB). Re-measure on a long-lived stack before calibrating the 92-day default. Do not invent further numbers.

### Reusable widgets (`ezkey-admin-ui/src/pages/dashboard.tsx`)

- `BatchHealthCard` `:175-271` (title, icon, jobs, isLoading, configSummary, jobsExpectedIdle, exitTo, exitLabel, testId; quiet badge/fallback `:197-236`); `BatchJobRow` `:113-173` (status badge, "Dernier run : il y a X · absolute" `:135-143`, "Portée : lastRunScope" `:144-149`); `StatCard`/`StatNum` `:275-301`; refresh strip `:439-467`; single 1 s clock `:366-396`. Primitives `components/ui/card.tsx`, `badge.tsx` (default/success/error/warning/muted), `components/feature/dashboard-stat-badge-link.tsx`. No "new result" highlight convention exists yet (only toasts `context/toast-context.tsx:49`, spinners, `animate-pulse`).

---

## Behavioral contract (cut 3)

### D1 — Page open (24 h rule)

| Situation | Behaviour |
|-----------|-----------|
| Latest chain verdict (any source: async job or nightly) finished **less than 24 h ago** (proposed OQ2 — pending Marc's review) | Show that verdict + scope. **No** automatic `POST …/jobs`. **No** automatic `GET /chain-integrity`. Explicit **Revérifier** only. |
| No such fresh verdict | Auto-run may fire (window per OQ3 proposed resolution). |
| Nightly (or other heavy path) already holds the gate | Widget shows nightly-running copy; **no** 409 error toast on open. |

**Proposed resolution (OQ2) — pending Marc's review:** Age of a verdict is time since it **finished**. There is **no** scope-coverage arithmetic (no requirement that the verdict’s `[from,to)` include `[now−24h, now)`). With the nightly enabled, the rule then practically never fires.

**Proposed resolution (OQ3) — pending Marc's review:** When D1 does fire, the auto-run window is the **last 24 h**, aligned with the nightly (`windowHours=24`) and the #676 timeline default — not the legacy 7-day UI default.

### D2 — Window cap

- Property: `ezkey.integrity.verify.max-window-days`, default **92**, modeled on `ezkey.audit.integrity.retroactive.operator-max-window-hours`.
- Applies to async VERIFY job starts and to sync `GET /chain-integrity`.
- Clear **400** beyond the cap. UI date picker bounded to the same max (PR 2).
- After the `entryHmac` projection (PR 1), the cap bounds wall-clock/TTL risk more than memory.

### D3 — Structured counters on the job DTO

Additive nullable columns / OpenAPI fields on `IntegrityAsyncJobResponse` (and Flyway on `ezkey_integrity_async_job`):

| Field | Notes |
|-------|--------|
| `chainStatus` | `OK` / `UNDECLARED_GAP_DETECTED` / `CHAIN_INTEGRITY_VIOLATION_DETECTED` |
| `totalCheckpoints` | |
| `valid` | |
| `invalid` | |
| `archived` | |
| `undeclaredGaps` | |

Served by existing `GET /jobs/current` and `/jobs/{id}`. UI hydrates tiles from the DTO — **never** parse English `resultSummary`; **never** depend on the recompute GET for the glance. `resultSummary` stays for logs. `GET /chain-integrity` remains for the public API and deep Verify work, under `heavyCryptoGate` (409 if taken) and the cap — closes #692 acceptance without removing the integrator path. Regenerate OpenAPI + Admin UI dispatched copy via the normal clean-start → `update-specs` workflow (not hand-edit). Hand-written client gets the fields; Orval later (deferred).

### D4 — Micro-dashboard (Julie)

**Principle:** separate **job state** (running / finished / failed) from **chain verdict** (intact / gaps / violation). Acceptance bar = I-2026-0007: ok/not-ok in ~3 s with honest scope.

**Placement:** one compact row above the three tabs, visible in all three, **replacing** the text banner (still "one strip", #624 Julie lock). Left→right: (1) Verdict tile (colour+icon), (2) Checkpoints verified, (3) Invalid, (4) Undeclared gaps, (5) Checked (absolute time + aging relative on a 1 s clock + **Revérifier**). One scope line below. Healthy = discreet: zeros grey, only the verdict tile sober green, small. Unhealthy: faulty tile coloured (gap orange as #676 timeline; violation red) + **Ouvrir Remédier**.

**Vocabulary (all strings i18n; never re-inject server `resultSummary`):**

| Case | FR | EN | Tone |
|------|----|----|------|
| Intact | Chaîne intacte | Chain intact | sober green |
| Gaps | Gaps non déclarés (N) | Undeclared gaps (N) | orange |
| Violation | Violation d'intégrité (N) | Integrity violation (N) | red |
| Job failed / expired / interrupted | Vérification non aboutie | Check did not complete | warning, no verdict |
| Running | Vérification en cours… | Check running… | neutral, spinner |
| Nightly running | Vérification nocturne en cours… | Nightly check running… | neutral |
| Stale (>24 h) | Dernier verdict : il y a 3 j (périmé) | Last verdict 3 d ago (stale) | grey; keeps label, loses colour |

"réussi" / "succeeded" banned for verdicts; "Terminé" never alone. Stale threshold 24 h (nightly + #676 default).

**New-result signal:** tile state change is the signal; one ~2 s ring/fade only when the result arrives while the page is open (not on re-display at open); no persistent "new" badge; no toast when green; `aria-live="polite"` on the verdict tile (e.g. "Résultat de vérification disponible : chaîne intacte"). Optional/deferrable: highlight once on return to visibility if the result arrived while the browser tab was hidden (OQ5).

**Tab behaviour (read D4 with OQ1):**

- **Proposed resolution (OQ1) — pending Marc's review:** Keep the existing **default-tab** rule from `integrity-atelier-mode.ts` when a non-green verdict is already known at page open (Julie’s position → Remédier). Ban only the **automatic switch when a result arrives while the user is working**; show **Ouvrir Remédier** instead. D4’s “no automatic tab switch to Remédier” is read this way — not as deleting the open-time default.

**Scope line** (like `lastRunScope`): e.g. "Période : 2 oct. 00:00 → 9 oct. 09:10 HE · auto à l'ouverture · par \<admin\>"; sources "auto à l'ouverture" / "manuel" / "nocturne"; author shown even when not me. #696: if the Vérifier filter differs from the verdict’s range, say so: "Le verdict porte sur une autre période que le filtre affiché." Short tooltip/doc link for “what is verified” (checkpoint chaining, entry fingerprints, time continuity) — no algorithm on screen.

**Nightly collision (409) on open:** no error toast; widget shows nightly running.

**Prerequisite:** D3 counters in the job DTO.

### D5 — Delivery split

| PR | Scope |
|----|--------|
| **PR 1 — backend** | `entryHmac`-only projection in `computeEntriesDigest` (same window filter + sort); mandatory characterization test (empty / single / multi / `created_at` ties — spirit of #586); persisted counters + additive OpenAPI; heavyCryptoGate + max-window-days on VERIFY + `GET /chain-integrity`; `getCurrent` uses `findFirstByOrderByStartedAtDesc()`; `verify-grants`; tests: DTO counters, 409 on GET while gate held, 400 beyond cap. Bar: `mvn test -pl '!ezkey-tests'`, smoke, API tests. |
| **PR 2 — UI (Julie)** | D1 24 h auto-run rule; hydrate micro-dashboard from DTO; tone from `chainStatus` / never "réussi" as verdict; aging relative time; #696 show + align verdict scope on the picker when hydrating from a job; nightly-running copy instead of 409 toast; update `docs/AUDIT_LOG_INTEGRITY.md:333-335`. |
| **Deferred** | Job-history purge (`@Scheduled`, terminated jobs older than N days, default 90 — hard delete, no soft delete; `INTEGRITY_ASYNC_JOB_*` audit entries remain); pause 3 s polling when `document.hidden`; Orval migration; process-local gate HA; checkpoint pagination (unneeded at 92 d ≈ 26k small rows). |

**No server-side cooldown / silent reuse of an old result on POST** — an explicit click must never get a silent stale answer. Single slot + 409 suffice. The 24 h automatic rule (D1) is a **UI** rule.

---

## Non-goals

- No rewrite of Observer / Vérifier / Remédier tab structure or cut-2 phase A density rules inside modes.
- No replacement of the checkpoint timeline; no change to `cpQuiet` / #676 quiet vs real-gap semantics.
- No soft delete; no job-history purge in the cut-3 delivery PRs (deferred).
- No Orval migration in this cut (hand-written client fields first).
- No inventing measured durations or heap numbers before Isabelle’s measurement pass.
- No hand-edit of generated OpenAPI under `specs/` or dispatched copies.
- No Tenant Admin access to `/integrity`.
- No Alerts chrome redesign; no second audit journal.

---

## Implementation locus

| Area | Locus |
|------|--------|
| Admin UI page / tabs | `ezkey-admin-ui/src/pages/integrity.tsx` |
| Banner → micro-dashboard | `ezkey-admin-ui/src/components/feature/integrity-async-job-banner.tsx` (replace / evolve) |
| Async job client | `ezkey-admin-ui/src/lib/integrity-async-jobs.ts` |
| Hydration | `ezkey-admin-ui/src/hooks/use-integrity-async-job-report-hydration.ts`, `…/lib/integrity-async-job-report-hydration.ts` |
| Default tab | `ezkey-admin-ui/src/lib/integrity-atelier-mode.ts` |
| Dashboard patterns to reuse | `ezkey-admin-ui/src/pages/dashboard.tsx` (`BatchHealthCard`, `BatchJobRow`, 1 s clock) |
| Job service / DTO / gate / cap | `ezkey-core/.../asyncjob/IntegrityAsyncJobService.java`, DTO, properties; Flyway additive migration |
| Digest projection | `ezkey-core/.../integrity/AuditChainVerificationService.java` (`computeEntriesDigest`) |
| Controllers | `IntegrityAsyncJobController`, `AuditLogController` (`/chain-integrity`) |
| OpenAPI | regenerate via `./scripts/update-specs.sh` after clean-start (PR 1) |
| Operator docs | `docs/AUDIT_LOG_INTEGRITY.md` (PR 2) |

---

## Scope per PR (checklist)

### PR 1 — backend

- [ ] `entryHmac`-only projection + characterization test
- [ ] Persisted counters + additive `IntegrityAsyncJobResponse` fields
- [ ] OpenAPI refresh + dispatched Admin UI copy (scripted, not hand-edited)
- [ ] Hand-written client fields (Orval deferred)
- [ ] `heavyCryptoGate` + `max-window-days` (default 92) on async VERIFY and `GET /chain-integrity`
- [ ] `getCurrent` → `findFirstByOrderByStartedAtDesc`
- [ ] `verify-grants`; unit/API tests listed in D5
- [ ] Grants unchanged for existing table

### PR 2 — UI

- [ ] Micro-dashboard replaces text banner (D4 vocabulary + Walk Gate checks)
- [ ] D1 24 h auto-run rule (per OQ2/OQ3 proposed resolutions once Marc confirms)
- [ ] Hydration from DTO counters; no `GET /chain-integrity` on open when fresh verdict
- [ ] Aging relative time (dashboard 1 s clock pattern)
- [ ] #696: verdict scope shown; picker aligns when hydrating from a job; mismatch copy when filter differs
- [ ] Nightly-running state; no 409 toast on open
- [ ] Tab: keep default-at-open non-green→Remédier; ban mid-work auto-switch; **Ouvrir Remédier** (proposed OQ1 — pending Marc's review)
- [ ] Update `docs/AUDIT_LOG_INTEGRITY.md:333-335`
- [ ] EN + FR i18n; no server `resultSummary` in the glance

---

## Acceptance criteria

1. With a fresh (<24 h) last verdict, opening `/integrity` starts **no** `POST …/jobs` and **no** `GET /chain-integrity` (network tab) (proposed OQ2 — pending Marc's review).
2. Micro-dashboard glance works in ~3 s without reading a sentence (green / gap / violation / stale / running / did-not-complete).
3. SUCCEEDED with gaps never reads "réussi" / green as the verdict.
4. Relative "checked" time ages while the page stays open.
5. Scope line + author visible in all three tabs; #696 mismatch text when filter ≠ verdict range.
6. Mid-work non-green arrival: no automatic tab switch; **Ouvrir Remédier** available.
7. Open during nightly: nightly-running copy; no 409 error toast.
8. VERIFY / `GET /chain-integrity` beyond 92 days → 400; UI picker cannot select beyond cap.
9. Digest characterization test green (projection ≡ full-entity digest on agreed fixtures).
10. Timeline quiet/gap semantics (#676) unchanged.

---

## Walk Gate checks

Fill and walk [`WALK-2026-10-09-integrity-cut3.md`](backlog/walk-gates/WALK-2026-10-09-integrity-cut3.md) after PR 2 (or a combined walkable SHA). Binary checks:

1. 3-second glance without reading a sentence — states green / gap / violation / stale / running / did-not-complete.
2. A result arriving while staying on Observer is noticed without a toast.
3. No "réussi" next to a gap.
4. Relative time ages.
5. Scope line and author visible in all three tabs.
6. Mismatch text appears when the Vérifier filter differs from the verdict range.
7. FR and EN with no server English string in the glance.
8. No tab switch mid-work.
9. Open during nightly shows the nightly state; no 409 toast.
10. With a fresh (<24 h) verdict, opening the page starts no job (network: no `POST /jobs`, no `GET /chain-integrity`) (proposed OQ2 — pending Marc's review).

---

## Measurement plan (Isabelle)

Purpose: calibrate the 92-day default and prove before/after; **does not block** the decisions. Do not invent numbers beyond recorded observations.

**Before (baseline on the live stack "Rootbeer") and after PR 1:**

- `SELECT job_type, scope_from, scope_to, finished_at - started_at AS d, result_summary FROM ezkey_integrity_async_job ORDER BY started_at DESC LIMIT 50;`
- `SELECT count(*) FROM ezkey_audit_log WHERE created_at >= now() - interval '7 days';` (E₇)
- Admin API log line `Chain verification completed: total=…`; network time of `GET /chain-integrity` in DevTools; heap before/after a job (after PR 1).
- Observe which path happens on open (new job vs hydration only); count `INTEGRITY_ASYNC_JOB_*` entries per open; capture RUNNING→SUCCEEDED from Observer; a non-green case; an open during the nightly.
- **Re-measure on a long-lived stack** before treating any timing as steady-state (see caveat below).
- **Observability gap (optional PR 1):** nightly duration is not measurable today because nothing logs its start — candidate: a clear start (and complete) log line for the nightly path so Rootbeer / after-PR-1 comparisons can include it.

### Baseline observation (2026-10-09, local stack)

**Representativeness caveat:** the DB was clean-started about **21.5 h** earlier, so there is very little history. These numbers are **not** a steady-state baseline. Keep the plan’s “re-measure on a long-lived stack” item; do not use these figures alone to lock the 92-day default.

| Observation | Value / note |
|-------------|--------------|
| First page open (empty job table) | `VERIFY_CHAIN_RANGE` **0.47 s**, **259** checkpoints, **8-day** scope |
| Nightly vs async job table | Nightly does **not** write to `ezkey_integrity_async_job`; its verdict is not visible there → **OQ4** |
| Hydration `GET /chain-integrity` | ~**300 ms** median (**261–339 ms**); full server-side recompute each time — **7** full verifications in **6** minutes of normal navigation |
| `POST /jobs` | **114 ms** |
| `GET /jobs/current` | **4** calls per page load |
| Later opens (back from dashboard, reload, mode change) | Hydration only — a SUCCEEDED job is **never** re-run however old it is |
| Stale summary vs Verify | Banner kept `checkpoints=259` while Verify showed **260** (stale `resultSummary`) — supports **D1** (show last verdict) and **D4** stale handling |
| Result visibility (Observer) | Only banner text changes (“Running — …” → “Last result — succeeded · …”); same style, no colour, no icon, no toast, no Observer-panel change; INTACT badge only in Verify |
| RUNNING UI duration | Stays ~**3 s** for a **0.47 s** job (banner polls every 3 s) |
| Audit entries per open | **0** `INTEGRITY_ASYNC_JOB_*` (intended **2**) — `eventAction is required`, swallowed at WARN → **#736** |
| Nightly duration | Not measurable (no start log) — observability gap above |

---

## Open questions

1. **OQ1 — Default tab at open vs mid-work switch.** D4 says "no automatic tab switch to Remédier"; existing default-tab rule (`integrity-atelier-mode.ts:55-98`) sends non-green → Remédier at open.
   - **Proposed resolution — pending Marc's review:** Keep the existing default-tab rule when a non-green verdict is already known at page open (Julie’s position). Ban only the automatic switch when a result arrives while the user is working; show **Ouvrir Remédier** instead. Read D4 wording this way.
2. **OQ2 — "Covering the last 24 h" (D1) precise rule.** Brief ambiguity: must the verdict scope include `[now−24h, now−ε]`? Nightly scope ends at 02:00 UTC.
   - **Proposed resolution — pending Marc's review:** Simplest reading of Marc’s words — there is **no** automatic verification on page open when the latest chain verdict, from any source (async job or nightly), finished less than 24 h ago. Age = time since finished. **No** scope-coverage arithmetic. With the nightly enabled, the rule then practically never fires.
3. **OQ3 — Auto-run window when D1 fires.** 24 h vs legacy 7 days?
   - **Proposed resolution — pending Marc's review:** Last **24 h**, aligned with the nightly and the #676 timeline default (not the legacy 7 days).
4. **OQ4 — Where the UI reads the nightly verdict** (dashboard `last_run_scope` endpoint vs integrity job history) — does nightly need counters too? *(open — no proposed resolution in this draft)*
   - **Fact (Isabelle 2026-10-09):** the nightly does **not** write to the async job table, so its verdict is not visible via `GET …/integrity/jobs/current`. For D1’s “last known verdict from the nightly,” the UI must read the dashboard’s nightly last-run data, **or** the nightly must record a job row (and/or counters). Leave open for **Marc and Patrick**.
5. **OQ5 — Highlight on return to tab visibility:** in PR 2 or deferred? *(open)*
6. **OQ6 — `docs/AUDIT_LOG_INTEGRITY.md:333-335` update** — confirmed PR 2 deliverable. *(closed as delivery placement; content still to write in PR 2)*

---

## Risks

- Behaviour change vs locked cut-2 intent (D1 supersedes phase-a:32 "auto chain-verify on mount OK").
- Digest equivalence of the projection (mitigated by characterization test).
- Additive OpenAPI drift; external integrators of `GET /chain-integrity` may now get 409/400.
- Stale verdict must never read green; multi-admin results show other author.
- Early local timings are **not** steady-state (short-lived DB) — 92-day default may need recalibration after a long-lived re-measure (does not block D1–D5).
- **#736:** intended 2 `INTEGRITY_ASYNC_JOB_*` audit entries per open currently fail silently (`eventAction is required`) — operator/forensic cost of auto-run opens is under-counted until fixed; measurement plan’s “count audit entries per open” will stay at 0 until #736 lands.
- Nightly duration / start not logged — weakens before/after cost comparison for the scheduled path (optional PR 1 observability).

---

## Related

| Ref | Role |
|-----|------|
| #692 | Sync GET bypasses gate/slot/cap — close via D3 gate+cap + DTO hydration |
| #696 | Verdict scope vs Vérifier picker mismatch — PR 2 |
| #736 | Async job STARTED/COMPLETED audit emit broken (`eventAction`) — 0 entries vs intended 2 |
| #676 / #674 | Timeline quiet vs gap; ~24 h default — preserve; align stale/auto window |
| #624 | Async slot + soft last-result strip (Julie) — micro-dashboard still "one strip" |
| P-085 | Lookback/cap parking — D2 ships the verify cap |
| I-2026-0007 | Dashboard "3-second ok/not ok with honest scope" — UX precedent |
| Cut 2 phase A/B | Density + atelier modes — still in force; D1 supersedes auto-verify-on-mount only |

---

## Done

- Intention note on `main` (this file) + Walk Gate stub + index/cross-links + superseded note at cut2-phase-a:32.
- Implementation Done = Walk Gate PASS on one SHA after PR 1 + PR 2 (or equivalent walkable tip) — see [`WALK-2026-10-09-integrity-cut3.md`](backlog/walk-gates/WALK-2026-10-09-integrity-cut3.md).
