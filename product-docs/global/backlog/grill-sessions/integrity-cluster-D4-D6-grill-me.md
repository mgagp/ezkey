# Grill Me — Integrity cluster (Blitz 2026-05-08-1, D4–D6)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-1.md` (D4, D5, D6) |
| **Cluster scope** | `I-2026-0005`, `I-2026-0006`, `I-2026-0007`, parent `V-2026-0004` |
| **Started** | `2026-05-17` |
| **Status** | `in-progress` |
| **Resume at** | **D5 block** — integrity validation strategy (`I-2026-0006`, `V-2026-0004`) |
| **Completed sections** | D4 grill blocks A–C (C9 settled); C8-6 suspended; D10–D18 skipped (optional) |
| **Pending sections** | C8-6; D5 block; D6 block |
| **Design readiness (D4)** | **Sufficient for component design pack** — see [C7 — Multiple ruptures](#c7--multiple-ruptures-unified-model-settled-2026-05-17) and [Design pack handoff](#design-pack-handoff-d4) |

When resuming this session, run `ezkey-grill-me` from the **D5 block** unless the operator requests a recap of settled decisions below.

## Settled decisions (D4 — `I-2026-0005`)

### A1 — Operator outcome and investigation framing

When an audit-chain integrity break is detected, the operator must be able to start a **structured investigation** to explain, justify, or escalate — not merely observe a technical error. Causes are intentionally undifferentiated at detection time:

- platform bug or corruption,
- technical failure (filesystem, database),
- inadvertent third-party or operator DB manipulation,
- partial restore or replication lag,
- or deliberate tampering / intrusion attempt.

Ezkey must **frame** the discovery and investigation pragmatically, then allow a **conciliation record** (analogous to partial-failure / heartbeat-loss handling) that restores cryptographic continuity with justification.

**Operator-facing surface (R1, keep simple):**

1. Admin console flow to create an **explanation / conciliation record** (same mental model as partial-failure recovery).
2. Under the hood: cryptographic **reattachment** plus justification persisted for operational and normative traceability.

### A1 — Open design pressure (not yet settled)

The operator raised whether justification stored only on **Audit Chain Incident** (and checkpoint conciliation rows) is sufficient, or whether this creates a **parallel audit trail** if incidents/checkpoints are not integrity-protected like audit rows. Risk: integrity assurance moves from audits to a weaker secondary store.

**Constraint for resolution (no infinite regress):** find the minimum posture that is **operationally and normatively sufficient** — avoid “validate the validator forever.” This must be closed before design pack / TB promotion. Candidate directions to grill later:

- incidents/checkpoints participate in the same chaining model as audits (heavy),
- incidents are derived + HMAC-light with explicit “convenience not proof” copy (light),
- conciliation emits a **first-class audit event** and incidents remain operational indexes only (middle).

### A2 — Roles

- **Global Admin only** for investigation, declaration, and reattachment actions.
- **Tenant Admin** may consult audits but must **not** gain cryptographic-operations visibility (key rotation, integrity remediation) — aligned with TI vs business-unit owner split.

### A3 — Reattachment quality bar (R1 mandatory)

R1 requires an **explicit cryptographic bridge**, defined by this acceptance test:

> After resolution, the **existing** integrity-validation mechanism (unchanged contract) must report continuity as healthy for the reconciled period — “it sees green” when examining cryptographic continuity.

Reattachment is not narrative-only in R1. Without this, the strong trust model is undermined from day one.

### B4 — Happy path (draft)

| Step | Actor | Action |
|------|--------|--------|
| 1 | Batch (periodic) **or** Global Admin (ad hoc range check) | Detect discontinuity in audit/checkpoint cryptographic continuity |
| 2 | Platform | Raise **alert** (reuse existing equipment/alert table; prefer extending current model over new tables) |
| 3 | Global Admin | Investigate (may use external ticketing — Jira, etc. — **outside** Ezkey) |
| 4 | Global Admin | Create conciliation record: justification + cryptographic reattachment |
| 5 | Platform | Existing validator confirms continuity restored; alert resolvable to normal |

**UI steps (target):** detection → alert → guided resolution (mirrors gap / partial-failure patterns). Exact screen count TBD in design pack; principle is **minimal new concepts**.

**Tables / entities (leaning):**

- Reuse **alert** table as the operator entry point until resolution.
- Conciliation / incident persistence on existing **Audit Chain Incident** (and checkpoint bridge) — **avoid** a parallel incident-only universe unless orthogonality review proves it necessary.
- Ezkey owns trace + justification + crypto bridge; external ticket ID optional in justification text.

**Cross-cluster note:** operator expects a **daily (or similar) retroactive continuity batch** as the primary automatic detector — aligns with `I-2026-0006`. If not already explicit in vision, treat as R1 operability requirement for the cluster (see D5 grilling).

### B5 — Detection sources (R1)

Both paths must enqueue the **same** alert + remediation process:

1. **Automatic** — periodic retroactive validation batch (`I-2026-0006`; rolling 1 h checkpoint batch alone is insufficient for older tampering).
2. **Manual** — operator-invoked integrity check over a chosen period (existing capability); discontinuity triggers the same flow as batch detection.

### C6 — False positives / benign causes

Same **technical remediation path** (crypto bridge + justification). Differentiation is in **justification text** (e.g. “filesystem corruption — false alarm”) and optional **external ticket reference** — not a separate “cancel without trace” path.

Ezkey stays self-contained for **integrity state**; it does **not** replicate full ITSM. Justification may point to external ticketing for narrative detail.

### C7 — Multiple ruptures: unified model (settled 2026-05-17)

**Framing:** Ezkey is opinionated, integrity-first, and **Phase 1 self-contained** (bounded “eat your own dogfood” — comparable to email/SMS not supported in R1, with on-screen compensating disclosure). There is no pre-existing operational mandate for every edge case; the model must be **reasonably coherent, operable, and normatively aligned** with project values without over-building for unproven scale.

**Core rule — period examination:** When integrity validation runs over any period, **every irregularity** observed in that window must be **surfaced and enter resolution**. No silent coalescing that hides distinct operational facts.

**Three rupture families** (generalize handling; do not invent a fourth parallel universe):

| Type | Typical signal | Priority (default) | R1 posture |
|------|----------------|-------------------|------------|
| **Gap** | Generalized downtime / no activity (e.g. 30 min silence) | Lowest | **Reuse existing gap-declaration flow** — explanation only; process already exists. |
| **Heartbeat / partial failure** | Admin API checkpoint heartbeat lost; Integration/Auth entries need reconciliation, signing, reattachment | Medium | **Reuse and revise if needed** existing partial-failure / degraded-mode + reconciliation path. |
| **Manipulation** | Add, delete, or modify existing audit/checkpoint data; chain no longer cryptographically continuous | Highest | **New symmetric path (D4 scope)** — one **actionable alert per distinct manipulation observation**; alert carries **where integrity fails** and **where it resumes**; resolution = human justification + period reintegration + crypto bridge so validator sees green. |

**Multiple events in one validation run:**

- Represent each distinct finding as its own **actionable alert** row (extend existing alert table — no new incident universe required for R1).
- Show Global Admin a **priority-ordered queue** (danger / urgency); operator resolves **in sequence** at human pace.
- Ezkey cannot infer root cause (topology, disk, DBA, attack) — only processes and chain state. **Explanation is always human**; Ezkey supplies observations, priority, and resolution mechanics.

**Manipulation alert minimum payload (R1):**

- Validation window context (which check produced the finding).
- **Boundary:** point where integrity ceases + point where it resumes (if resumable within window).
- Typed as manipulation; links to conciliation flow (justification + reintegration + bridge).

**Sealed audits / archival:** FSM for sealed state exists and is integrated; **archival export is future**. R1 design must not assume archive workflows; sealed segments may appear as “integrity resumes after seal boundary” in validation — detail left to design pack, not blocking C7.

**C7 supersedes** earlier “single alert per validation window” leaning — replaced by **one alert per distinct irregularity**, scoped by observation, ordered by type priority.

### Design pack handoff (D4)

Ready to open **component design pack** for `I-2026-0005` with:

- Rupture taxonomy (gap / heartbeat / manipulation).
- Alert-as-queue model + priority ordering.
- Per-type resolution contract (reuse vs new).
- Manipulation boundary fields + conciliation acceptance test (validator green).

**Still detail in design pack (not blocking C7):** exact alert schema columns, UI copy, interaction with sealed FSM, heartbeat path gap analysis vs current code.

### C8 — Degraded mode vs integrity validation (settled 2026-05-19)

**R1 model — Admin API is atomic for batches:**

- All scheduled work (checkpoints, nightly integrity validation, re-encryption, etc.) runs **inside Admin API**. No R1 sub-split of “some batches up, some down” within the same process.
- If Admin API is **down**, **no** integrity validation batch runs (including 24 h retroactive). Theoretical partial failure (heap, one SQL fails) is acknowledged but **out of R1 scope** — design stays all-or-nothing to limit complexity for an unproven product.
- **Future option (not R1):** dedicated `ezkey-batch-api` with Admin API as BFF — captured in `V-2026-0012`; prior review: deployment cost not justified yet.

**Who emits which alert:**

| Alert family | Emitter | When |
|--------------|---------|------|
| **Heartbeat / partial failure** | Integration API + Auth API | Admin API checkpoint chain missing after grace period |
| **Manipulation** | Admin API (via validation batch or ad hoc check) | Only when Admin API is healthy enough to run that job |
| **Gap** | Existing gap flow | Silence / no activity |

→ **No duplicate alert** from Admin API for heartbeat while down; heartbeat is **not** Admin API’s job to emit. Distinct backends, distinct events (C7 stands).

**Priority zero (non-negotiable):**

1. Restore **Admin API** fully.
2. Restore **heartbeat** (checkpoint chaining).
3. Reconcile partial-failure / degraded entries.
4. Only then treat **manipulation** alerts on the same timeline.

Integration/Auth remain in **degraded mode** (no new auth operations) until heartbeat alert is cleared. Operator mission: Admin API is the trust anchor.

**Happy path (C8-3):** Admin down → I/A degraded + heartbeat alert → operator restores Admin (e.g. heap/VM) → observes UI → reconciles checkpoint/gap declaration → batches resume → chain reattaches → validator green. Manipulation **before** outage surfaces only after Admin can run validation.

**C8-4 — Validation while degraded:** If integrity batch runs, Admin API is **by definition** functional enough; no separate “validation disabled” flag in R1. Down = no batches at all.

**C8-7 — Re-open closed manipulation alert:** **No.** Same scope failing again → **new** manipulation alert. Repeated cycles signal bug or recurring issue — intentional signal, not a limitation.

**C8-8 — Real-world driver:** Assume **legitimate technical outages** most of the time; attack remains possible; design for probable case without becoming a toy; best-effort normative posture.

**C8-9 / C8-10:** Degraded blocks operations until Admin/heartbeat restored. **Inacceptable:** leaving any of the three rupture types unaddressed after resolution such that **any period** cannot be validated end-to-end (gap explanation, degradation explanation, or manipulation reintegration).

**C8-11 — Heartbeat blocks manipulation work:** While heartbeat alert active, system stays degraded; no full Integration/Auth operations. Do **not** suppress manipulation findings in DB — but operator order is heartbeat first. (Batch cannot run while Admin fully down anyway.)

**Master override (deprioritized vs snooze — see C9):**

- Externalized property forcing ops despite heartbeat was considered; **operator snooze** (justification + audit, configurable duration) is **preferred** for R1.
- Property-only override remains a future fallback if snooze proves insufficient.

**C8-6 — Suspended:** False manipulation from missing checkpoints vs true heartbeat gap — see [C8-6 recap](#c8-6-recap-for-next-session) below.

**Spawned backlog (C8):**

- `I-2026-0021` — PostgreSQL role × table permissions matrix (defense in depth for audit immutability).
- `V-2026-0012` — future optional batch-api split.

### C8-6 recap for next session

**Question:** After Admin API is back and heartbeat reconciled, could integrity validation still report “manipulation” when the only issue was **expected checkpoint gap** during outage (not fraud)?

**Why it matters:** Avoid double-counting the same outage as both heartbeat (resolved) and manipulation (false positive).

**Lean hypothesis to validate in design pack:** Validation logic must **classify** discontinuity type before emitting manipulation alert — heartbeat-covered windows should not spawn manipulation family alerts. Operator to confirm or correct when resuming C8-6 or during design pack.

### C9 — Stale state without escalation (settled 2026-05-19)

**Mantra (R1):** **Detect → represent → accompany until resolution.** No harassment; no alert-on-alert; no Grafana/Jira/PRTG inside Ezkey.

**Degraded mode duration (C9-1):** Open integrity/heartbeat alert ⇒ system stays **degraded** until resolved. **No** time-based escalation tiers in R1 (unpublished product; avoid accidental complexity).

**No escalation UX (C9-2, C9-3):** No visual escalation ladder, no “seen but not treated”, no extra operator-comfort UI. Alerts stay **visible** at the appropriate Admin UI location until closed.

**Batch staleness — cut line (C9-4–C6):**

- **Do not** emit alerts because a batch has not run (“alert on alert” rejected).
- **Do** introduce **batch last-run reporting**: simple generalized concept — table (or equivalent) per batch job: `last_execution_at`, `last_status` (success / failed).
- Dashboard API exposes a section; **one or two widgets** show last run per batch; **integrity validation batch** may be visually emphasized (STANDOUT) vs others.
- Distinguish **failed run** vs **no run yet / absent** when feasible (design pack); widget shows date + status — operator infers staleness without escalation machinery.
- This replaces C9-5/C9-6 threshold/escalation questions for R1.

**Open alert accumulation (C9-7):**

- R1: **no cap**; one alert mechanism; operator resolves as they go (“work harder” if many).
- **Future vision only** (`V-2026-0013`): exceptional **meta-resolution** (“GOD_RESOLUTION”) over a long window — single justification + crypto patch for catastrophe recovery; heavy caveats; likely never needed; not R1.

**No cross-alert UI rules (C9-8):** Do not add UI that judges manipulation vs heartbeat priority beyond existing degraded rules — keep uniform resolution method.

**Master override display (C9-9):** No dedicated persistent override alert; batch/integrity widgets carry operational truth; operator responsibility.

**Honest widgets when alerts open (C9-10):** Batch/integrity widgets may show recent batch **success**, but must **not** imply global “all green” while **open resolution alerts** exist — add a **proximate caveat** on the widget (e.g. “resolution actions pending”) when actionable alerts are open.

**Operator cadence (C9-11):** Design for **daily spot-check**, not weekly-only PME; no special R1 thresholds for slow operators.

**R1 mechanisms (C9-12, C9-14, C9-15):**

| Mechanism | R1 |
|-----------|-----|
| Auto-close alert | **No** |
| Snooze (Global Admin) | **Yes — preferred** over property-only master override |
| Email / external notify | **No** |
| Delegation to Tenant Admin | **No** |

**Snooze contract:**

- Global Admin action: justification required + **audit event**.
- Default duration **24 h**; duration configurable via **externalized property** (e.g. operator may set 5 days with eyes open — operational risk accepted consciously).
- After expiry: degraded/rules apply again unless **new snooze** with new justification + audit.
- Allows continued operations despite heartbeat/manipulation blockers when operator has verified safety (e.g. known bug until next release).
- Covers **C9-15** (“accepted risk”) without a separate “deferred manipulation” status.

**Spawned / updated artifacts (C9):**

- `I-2026-0007` — expanded to batch last-run widget(s) + open-alert caveat (priority raised).
- `V-2026-0013` — future GOD_RESOLUTION meta-resolution (draft, heavy caveats).

## Open decisions

| ID | Topic | State | Notes |
|----|--------|--------|--------|
| **C7** | Multiple breaks / alert scoping | **Settled** | One alert per distinct irregularity; three families; sequential human resolution; manipulation alerts include fail/resume boundaries. |
| **C8** | Degraded mode overlap | **Settled** | Atomic Admin API; distinct alert emitters; heartbeat priority 0; no alert reopen; master override candidate. |
| **C8-6** | False manipulation vs heartbeat gap | **Suspended** | See recap above |
| **C9** | Stale / ignored alerts | **Settled** | No escalation; batch last-run widgets; snooze R1; honest widget caveat; GOD_RESOLUTION future only |
| D10–D18 | Optional D4 depth | **Skipped** | Covered sufficiently by A–C9 for design pack |
| **INC-1** | Parallel audit chain for incidents vs audits | **Open** | See A1 open design pressure |
| **INC-2** | Alerts-only vs incident table as system of record | **Settled (R1)** | **Alerts-first** — actionable queue; conciliation persistence on existing incident/checkpoint bridge; no new parallel table set for R1. |

## Operator verbatim signals (audit trail)

Captured from voice dictation, 2026-05-17 — preserved for retrofit fidelity:

- Investigation must cover bug, corruption, accidental DBA access, restore issues, or attack — single framed process.
- Conciliation “very similar” to partial heartbeat failure recovery.
- Question whether incident/checkpoint justifications create a second-class audit chain without crypto integrity.
- Global admin only; tenant admin read-only on audits, no crypto ops.
- Validator-after-fix must pass with today’s integrity check contract (R1).
- Detection: batch + operator ad hoc, same alert path; daily retroactive batch expected.
- Prefer reusing alert table; avoid new concepts/tables unless necessary.
- False positives: same bridge, different justification; external ticket reference optional.
- C7 (2026-05-17): three rupture types; one alert per irregularity; priority queue; human-only explanation; manipulation needs fail/resume boundaries; gap/heartbeat reuse existing paths; sealed/archive future.
- C8 (2026-05-19): Admin API atomic; heartbeat from I/A only; priority 0 restore Admin+heartbeat; no reopen alerts; snooze preferred over override; I-2026-0021 DB roles; V-2026-0012 batch-api future.
- C9 (2026-05-19): no escalation/alert-on-alert; batch last-run table+widgets; snooze with audit; widget caveat when alerts open; V-2026-0013 GOD_RESOLUTION future.

## Links

- `../ideas/I-2026-0021-postgresql-application-role-permissions-matrix.md`

- Blitz archive: `../blitz-archive/blitz-2026-05-08-1.md`
- `../ideas/I-2026-0005-checkpoint-integrity-break-remediation.md`
- `../ideas/I-2026-0006-nightly-integrity-validation-batch.md`
- `../ideas/I-2026-0007-admin-dashboard-integrity-widgets.md`
- `../../vision/product-orientation-notes.md` (`V-2026-0004`)
