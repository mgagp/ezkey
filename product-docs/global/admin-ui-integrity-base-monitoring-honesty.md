# Admin UI — Integrity honesty when monitoring is off (base / jobs disabled)

## Metadata

- **Document ID:** `admin-ui-integrity-base-monitoring-honesty`
- **Status:** `accepted` (product GO; delivery in progress — Admin UI + `runtimeProfile` on dashboard overview)
- **Owner (intention):** Julie (Admin UI opérabilité)
- **Product direction:** Alex
- **QA observation:** Isabelle (soft honesty gaps after runtime-profile walk; not a merge blocker for the runtime profile)
- **Purpose:** Intention lock for **honest operator copy and chrome** on Integrity when scheduled integrity **monitoring** is not active — especially under the opt-in **base** runtime profile. Delivery adds a small non-secret `runtimeProfile` field and conditional Integrity copy. **Not** a layout redesign.
- **Related:**
  - PR [`#566`](https://github.com/mgagp/ezkey/pull/566) — opt-in runtime profile with monitoring jobs off; default **integrity** unchanged. Admin UI Integrity badge deferred there by design. **Product vocabulary lock (Alex): `base` (opt-in) / `integrity` (default).**
  - [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md) — Alerts = signal / Integrity = atelier
  - [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md) — job → surface map (Dashboard batch-health = signal; Integrity = atelier)
  - [`integrity-assurance-honest-line.md`](integrity-assurance-honest-line.md) — honesty of integrity claims (Christophe: no tamper-proof claims; base = limited monitoring claims)
  - Vision: [`vision/V-2026-0010-per-installation-profile-elaboration.md`](vision/V-2026-0010-per-installation-profile-elaboration.md) (runtime profile framing)

---

## Product vocabulary (locked)

| Profile | Role | Monitoring Integrity jobs |
|---------|------|---------------------------|
| **`integrity`** | Default | On (unchanged posture) |
| **`base`** | Opt-in | Off (MFA crypto on; audit-integrity monitoring off by design) |

Operator-facing product keys: `--runtime=base|integrity` and `EZKEY_RUNTIME_PROFILE=base|integrity` (unset = integrity).

Do **not** use “eval runtime” / `--runtime=eval` as the product name in Admin UI or product-docs.

Spring mechanism under the base preset: profile `docker-base` → `application-docker-base.properties` (PR #566).

---

## Problem (Isabelle / Alex)

Under **base** (or any posture where audit-integrity monitoring jobs are off), the Integrity atelier correctly tends toward an **empty** lifecycle / checkpoint view. That empty state is fine.

What is not fine: **present-tense copy that assumes scheduled monitoring is live**. The operator then reads “detection is active” when it is not.

Concrete soft gaps:

| Surface | Locale key / file | Soft risk |
|---------|-------------------|-----------|
| `/integrity` lifecycle panel hint | `integrity.lifecyclePolicyHint` in `ezkey-admin-ui/src/locales/en/audit-logs.json` (+ FR twin) | « The system progresses checkpoint lifecycle states automatically » / « Le système fait progresser automatiquement… » — implies live auto progression |
| Local help (drawer / `?`) | `help.integrityLifecycle.content` (EN + FR, same files) | « every 5 minutes » / « toutes les 5 minutes »; « nightly job » / « job nocturne » as present-tense schedule facts |
| Related help (same family) | `help.exceptionalMaintenance.content` | « Normal lifecycle progression is policy-driven and system-executed » — same assumption when jobs are off |

Empty atelier + schedule-assuming prose = honesty gap. Keep the **Alerts = signal / Integrity = workshop** split; do not paper over this by merging detection chrome into Alerts.

---

## Non-goals

- Not cut-2 layout redesign inside Integrity.
- Not changing Alerts list behavior or Resolve / Investigate deep-links.
- Not inventing fake job health or a fake “green” monitoring story.
- Not claiming push / real-time detection.
- Not inventing a second journal or putting the job matrix into `runtimeProfile`.

---

## Signal source (server truth)

### Monitoring flags (existing)

Admin API `GET /api/v1/dashboard/overview` (Global Admin) returns:

1. **`integrityConfigSummary`** — `DashboardIntegrityConfigSummaryDto`:
   - `chainCheckpointsEnabled` ← `ezkey.audit.chain.enabled`
   - `nightlyValidationEnabled` ← nightly integrity enabled flag
   - lookback / nightly window sizes (non-secret)
2. **`integrityJobs`** — last-run rows with `lastStatus` including **`NEVER_RUN`**.

### Runtime profile (delivery)

Non-secret field **`runtimeProfile: "base" | "integrity"`** on the same overview response (Global Admin only). Resolved from the live Spring environment: active profile `docker-base` → `base`; otherwise `integrity`.

`/integrity` loads overview for this field + `integrityConfigSummary` (no second journal, no Alerts chrome).

---

## UI intention (delivered shape)

Keep Alerts = signal list; Integrity = atelier. Manual atelier actions stay available.

### 1. Persistent badge on `/integrity`

- When `runtimeProfile === "base"`: **« Base profile — scheduled detection inactive »** / FR **« Profil base — détection planifiée inactive »**.
- When monitoring is off but profile is integrity: generic **« Scheduled detection inactive »** / FR twin (no fake “base”).
- Under default integrity with monitoring enabled: **no** base chrome.

### 2. Conditional copy (locale)

When monitoring is off, switch to `*MonitoringOff` variants for:

- `integrity.lifecyclePolicyHint`
- `help.integrityLifecycle.content`
- `help.exceptionalMaintenance.content`

### 3. Manual actions remain the atelier

**Verify chain**, **Verify entry HMAC**, **Run validation** stay available and described as operator-driven atelier work.

### 4. Alerts stay signal-only

Do **not** merge detection chrome into Alerts.

---

## Delivery

| Step | What |
|------|------|
| Docs PR `#567` | Intention note (draft → accepted here) |
| This delivery | Admin API `runtimeProfile` on overview + Integrity badge + conditional locale; Orval after OpenAPI refresh |
| Runtime profile mechanism | PR `#566` (`docker-base` / `--runtime=base`) |

---

## Acceptance checklist (Isabelle re-walk)

- [ ] Under `--runtime=base` / `EZKEY_RUNTIME_PROFILE=base`, `/integrity` shows **base** monitoring-off badge; schedule claims are conditional.
- [ ] Under default **integrity** with monitoring on: no base chrome; normal schedule copy OK.
- [ ] Monitoring off without base (config): generic inactive badge; no present-tense schedule claims.
- [ ] Manual verify / run validation still described as atelier actions.
- [ ] Alerts remain signal-only; no detection atelier chrome merged in.
- [ ] Dashboard batch-health remains the job last-run signal; Integrity does not contradict it.
- [ ] Christophe honesty: no tamper-proof claims; base = limited monitoring claims only.
