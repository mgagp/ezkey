# Admin UI — Integrity honesty when monitoring is off (eval / jobs disabled)

## Metadata

- **Document ID:** `admin-ui-integrity-eval-monitoring-honesty`
- **Status:** `draft` (awaiting Marc / Alex acceptance)
- **Owner (intention):** Julie (Admin UI opérabilité)
- **Product direction:** Alex
- **QA observation:** Isabelle (soft honesty gaps after eval-runtime walk; not a merge blocker for the runtime profile)
- **Purpose:** Intention lock for **honest operator copy and chrome** on Integrity (and a soft Alerts cue only if needed) when scheduled integrity **monitoring** is not active — especially under the opt-in **eval** runtime profile. **Not** an implementation plan, layout redesign, or API invention.
- **Related:**
  - PR [`#566`](https://github.com/mgagp/ezkey/pull/566) — opt-in eval runtime profile (`--runtime=eval` / `EZKEY_RUNTIME_PROFILE=eval`); monitoring jobs off; default **integrity** unchanged. Admin UI Integrity badge deferred there by design.
  - [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md) — Alerts = signal / Integrity = atelier
  - [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md) — job → surface map (Dashboard batch-health = signal; Integrity = atelier)
  - [`integrity-assurance-honest-line.md`](integrity-assurance-honest-line.md) — honesty of integrity claims
  - Vision: [`vision/V-2026-0010-per-installation-profile-elaboration.md`](vision/V-2026-0010-per-installation-profile-elaboration.md) (runtime profile framing)

---

## Problem (Isabelle / Alex)

Under **eval** (or any posture where audit-integrity monitoring jobs are off), the Integrity atelier correctly tends toward an **empty** lifecycle / checkpoint view. That empty state is fine.

What is not fine: **present-tense copy that assumes scheduled monitoring is live**. The operator then reads “detection is active” when it is not.

Concrete soft gaps observed after the #566 walk (QA PASS; honesty follow-up, not a #566 blocker):

| Surface | Locale key / file | Soft risk |
|---------|-------------------|-----------|
| `/integrity` lifecycle panel hint | `integrity.lifecyclePolicyHint` in `ezkey-admin-ui/src/locales/en/audit-logs.json` (+ FR twin) | « The system progresses checkpoint lifecycle states automatically » / « Le système fait progresser automatiquement… » — implies live auto progression |
| Local help (drawer / `?`, not always opened in walk) | `help.integrityLifecycle.content` (EN + FR, same files) | « every 5 minutes » / « toutes les 5 minutes »; « nightly job » / « job nocturne » as present-tense schedule facts |
| Related help (same family) | `help.exceptionalMaintenance.content` | « Normal lifecycle progression is policy-driven and system-executed » — same assumption when jobs are off |

Empty atelier + schedule-assuming prose = honesty gap. Keep the **Alerts = signal / Integrity = workshop** split; do not paper over this by merging detection chrome into Alerts.

---

## Non-goals

- Not cut-2 layout redesign inside Integrity.
- Not changing Alerts list behavior or Resolve / Investigate deep-links.
- Not inventing fake job health or a fake “green” monitoring story.
- Not claiming push / real-time detection.
- Not blocking merge or close-out of PR #566 (runtime profile).
- Not inventing a new Admin API solely for this slice without evidence that existing server truth is insufficient.
- No Admin UI code in **this** docs PR.

---

## Signal source (server truth — brief investigation)

**Prefer server truth the Admin UI can already consume.** Do not invent an API in this intention.

### What exists today (no dedicated “eval” flag)

Admin API `GET /api/v1/dashboard/overview` (Global Admin) already returns:

1. **`integrityConfigSummary`** — `DashboardIntegrityConfigSummaryDto`:
   - `chainCheckpointsEnabled` ← `ezkey.audit.chain.enabled`
   - `nightlyValidationEnabled` ← nightly integrity enabled flag
   - lookback / nightly window sizes (non-secret)
2. **`integrityJobs`** — last-run rows (`AUDIT_CHAIN_CHECKPOINT`, `NIGHTLY_INTEGRITY_VALIDATION`, …) with `lastStatus` including **`NEVER_RUN`**.

Dashboard already surfaces config as **disabled** and jobs as **Never run** (`ezkey-admin-ui/src/pages/dashboard.tsx` + `locales/*/dashboard.json` → `batchHealth.configDisabled`, `batchHealth.status.NEVER_RUN`).

Under eval (#566), Admin/Auth/Integration load `docker-eval` with chain + heartbeat + nightly (+ archive auto-seal/purge, etc.) **off**. Config booleans and `NEVER_RUN` therefore align with “monitoring not active.”

**There is no dedicated Admin API field today that names the product runtime profile (`eval` vs `integrity`).** PR #566 documents the operator-facing key as `--runtime` / `EZKEY_RUNTIME_PROFILE`; Spring `docker-eval` is the mechanism. That product label is **not** exposed on dashboard overview or Integrity endpoints.

`/integrity` (`ezkey-admin-ui/src/pages/integrity.tsx`) does **not** currently read `integrityConfigSummary`; schedule claims in locale strings are unconditional.

### Minimal truth the UI can use now (recommended for the follow-up PR)

Treat **monitoring as not active** when server says so, without waiting for a profile name:

- `integrityConfigSummary.chainCheckpointsEnabled === false` **and/or**
- `integrityConfigSummary.nightlyValidationEnabled === false` (for copy that mentions the nightly path) **and/or**
- integrity job rows stuck at `NEVER_RUN` with empty lifecycle / no checkpoints

Practical rule for chrome: if **chain checkpoints are disabled** (the ★ coupling in eval also turns heartbeat off), show the persistent “monitoring off” cue on Integrity. Nightly-only disable is rarer; still condition any “nightly job” present-tense claim.

Optional future (not required to ship honesty): a small non-secret field such as `runtimeProfile` or `integrityMonitoringActive` on overview (or a thin Integrity bootstrap). Only if product wants the badge to say **« Monitoring off (eval) »** with the product word **eval** rather than generic “scheduled detection inactive.” Until then, honest generic wording is enough — do not invent the API in this docs slice.

---

## UI intention (minimal)

Keep Alerts = signal list; Integrity = atelier. Manual atelier actions stay available.

### 1. Persistent badge or banner on `/integrity`

When monitoring is not active (server truth above):

- Show a calm, persistent **badge or banner** on Integrity — e.g. EN « Monitoring off » / « Scheduled detection inactive »; FR « Détection planifiée inactive » / « Monitoring désactivé (eval) » **only if** the UI can truthfully know eval (today: prefer generic wording unless a profile field lands).
- Wording: honest, not alarming — eval is a **deliberate** profile, not an incident.
- Soft cue on Alerts empty/help **only if** needed so empty Alerts is not misread as “all clear while detectors run.” Default: **Integrity carries the badge**; Alerts stay signal-only.

### 2. Conditional copy (locale)

When monitoring is off, do **not** show unconditional present-tense schedule facts:

| Key | Today (problem) | When monitoring off |
|-----|-----------------|---------------------|
| `integrity.lifecyclePolicyHint` | progresses … automatically | Switch to « when monitoring is enabled… » / hide auto-progression claim; keep “panel is for visibility” |
| `help.integrityLifecycle.content` | every 5 minutes; nightly job | « When monitoring is enabled, checkpoints seal on a schedule… »; describe **Run validation** as the on-demand detective path (same semantics as the nightly job **when that job is enabled**) |
| `help.exceptionalMaintenance.content` | system-executed normal progression | Qualify: normal progression applies when scheduled monitoring / policy jobs are enabled |

Reuse existing i18n namespaces (`audit-logs` integrity + help keys); FR+EN parity mandatory. Suggested tone (Julie): calm, concrete, no drama.

### 3. Manual actions remain the atelier

**Verify chain**, **Verify entry HMAC**, **Run validation** stay available and correctly described: they are **operator-driven** atelier work, not proof that the schedule is running. Do not disable them merely because monitoring is off (unless the API itself refuses — then surface the server error honestly).

### 4. Alerts stay signal-only

Do **not** merge detection chrome into Alerts. Do not redesign Alerts list. Deep-links into Integrity unchanged. Optional soft help line only if product sees a real empty-state misread after the Integrity badge ships.

---

## Delivery

| Step | What |
|------|------|
| **This PR** | Intention note only (`draft`). No Admin UI code. |
| **After Marc / Alex accept** | One small Admin UI follow-up PR: badge/banner + conditional locale (and wire Integrity to existing overview config summary or equivalent server truth). **Not** part of #566. |
| **Optional later** | Tiny API field if product insists on the word **eval** in the badge. |

---

## Done when (this docs PR)

- [x] Note complete under `product-docs/global/`
- [x] Cites real locale keys / files and links hard-split + job-surface map
- [x] States that no dedicated eval flag exists in Admin API today; recommends minimal existing truth
- [ ] Marc / Alex accept → status `accepted` (or `promoted`) and Admin UI follow-up may start

---

## Acceptance checklist (for the later Admin UI PR)

- Under `--runtime=eval` (or chain monitoring disabled), `/integrity` shows monitoring-off chrome; schedule claims are conditional or absent.
- Manual verify / run validation still described as atelier actions.
- Alerts remain signal-only; no detection atelier chrome merged in.
- Dashboard batch-health remains the job last-run signal (already shows disabled / never-run); Integrity does not contradict it.
- Default `--runtime=integrity` (or enabled config) keeps today’s “monitoring on” copy.
