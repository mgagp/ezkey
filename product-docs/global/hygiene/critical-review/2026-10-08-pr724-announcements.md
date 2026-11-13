# Critical review — 2026-10-08 — pr724-announcements

## Metadata

- **Date:** 2026-10-08
- **Keyword:** `critical-review`
- **Target:** PR #724 @ `8c693f9c` (`V-2026-10-08-global-admin-announcements`); merged note `a6d968ff`
- **Extras:** community evaluator self-registration; dashboard overview contract
- **Model / effort:** untracked in repo (CR2 ephemeral report; ≠ author family assumed)
- **Angle:** G0 GO vs park; operability of dismiss / placement
- **Launcher:** Mathieu
- **Reviewer agent:** CR2 (ephemeral report; not committed)
- **Verdict:** Parked (with re-entry triggers)
- **Amend rate:** 9 / 9 findings recorded as amend into the parked vision (100%); overall gate verdict PARKED
- **Status:** Closed

## Decisions

From Marc’s CR2 table on the merged vision (`a6d968ff`) and the CR2 report. Nothing invented;
gaps marked untracked.

| # | Finding | Severity | Decision | Notes |
| --- | --- | --- | --- | --- |
| 1 | Verdict depends on unverified community Tenant Admin count | S1 | amend | Count run: **no** active evaluator Tenant Admins → trigger (a) not met → **PARKED**; triggers reworded |
| 2 | Dismissed state contradicts itself | S2 | amend | **True dismiss** for every severity including critical; **no** collapsed strip; single localStorage `id:version` |
| 3 | “Most recent active” undefined when windows overlap | S2 | amend | Server-side selection: greatest `visible_from ≤ now`, tie-break highest id; list statuses Scheduled / Live / Shadowed / Expired |
| 4 | DB grants touchpoint missing | S2 | amend | `apply-grants.sql` / `verify-grants.sh` + matrix; read fail-open (render nothing) |
| 5 | Severity colours / Global Admin placement | S2 | amend | GA dashboard **below** Global health cards; TA **top**; « Annonce de l’opérateur » in signal model at M1 |
| 6 | Size estimate / hygiene vs program label | S2 | amend | Lane = **program** (~30–40 files) |
| 7 | G2 / Walk checklist misses states | S2 | amend | Single timed Walk Gate; strip/withdraw dropped from checklist |
| 8 | Soft-delete vocabulary | S3 | amend | **No** soft delete and **no Withdraw**; **physical delete, audited** (lifecycle Delete) |
| 9 | Security spot check | S3 | amend | Text nodes / pre-line / https-only autolink / 500-char / reject control+bidi / public text honesty |

**Also recorded (from Marc table / CR2 §3):** no dedicated announcement rate limits; read via nullable
`currentAnnouncement` on dashboard overview; gates lightened (M1 brief = G1; shared Walk Gate;
G3 = smoke line). Feature remains **PARKED** until re-entry (a)/(b)/(c) on backlog index.

**Untracked:** exact CR2 reviewer model slug / cloud-agent run id (not in merged note).

## Lessons (1–3)

- Park when the stated re-entry trigger is not met; still record amendments so re-entry needs no second G0.
- Prefer overview fields and true dismiss over parallel endpoints and rate-limit theater on a P3.
- Align delete vocabulary with lifecycle canon (physical delete) rather than inventing Withdraw.

## Follow-ups

- Vision parked on `main` @ `a6d968ff`; backlog `## Parked` row present.
- Method not yet in-repo at review time — this note backfills discoverability.
