---
name: java-doctor-curated
description: >-
  Runs the Java static-analysis curated pass (SpotBugs, pinned Semgrep pack, narrow PMD) and walks
  a small P1/P2/P3 lot one finding at a time with the operator. Use when the operator says
  java-doctor-curated or asks for a Java static-analysis hygiene pass.
disable-model-invocation: true
---

# java-doctor-curated

Punctual curated pass mirroring Admin UI `doctor-curated`. **Not** a CI gate and **not** a
zero-warning campaign.

## Command

From the repository root (Git Bash on Windows):

```bash
./scripts/java-doctor-curated.sh
```

Options: `--skip-compile` when target classes already exist; `--modules csv` to narrow scope.
Default modules: `ezkey-core`, `ezkey-core-security`, `ezkey-admin-api`, `ezkey-auth-api`,
`ezkey-integration-api`. On Windows with Semgrep in Docker, the script confines
`MSYS_NO_PATHCONV=1` to the Docker subshell and scans a slim workspace under
`logs/java-doctor/scan-workspace`.

Outputs under `logs/java-doctor/` (gitignored): `java-doctor.curated.md` (shortlist + planning
contract), `java-doctor.curated.json`, `raw/` (SpotBugs/PMD XML, Semgrep JSON). Config under
`config/java-doctor/` (pinned Semgrep pack, narrow PMD ruleset, suppressions).

## HITL contract (mandatory)

1. Run the script; read `logs/java-doctor/java-doctor.curated.md`.
2. Propose a **small prioritized lot** (usually 3–6 items). A short numbered overview (rule +
   location hint) is fine.
3. **Before any code change, one finding at a time.** Do not replace the dialogue with one dense
   options matrix asking for a bulk reply (`1A, 2B, 3B…`). For each finding: what the tool said,
   where to look in source, hypothesis, options, open question; then **wait** for Go / No-Go /
   suppress / skip / questions on **that** item before presenting the next. The point is shared
   code review, not a synthetic table. A compact decision table belongs in the campaign note
   after decisions are made.
4. **Fuzzy signal:** if the finding cannot be tied clearly to source without opening bytecode,
   **skip**. Do not invent a problem. Suppress with a reason only when the pattern is understood
   and intentionally accepted.
5. **If it ain't broken, don't fix it:** when the diagnosis is clear but the flagged code is
   harmless local over-defense (redundant guards obvious within a few lines), leave the code and
   suppress with a reason. Clarity of the finding does not oblige a rewrite; extra
   characterization tests and crypto-adjacent churn for zero product gain is not hygiene.
6. Record decisions in a dated campaign note under
   [`product-docs/global/hygiene/java-doctor/`](../../../product-docs/global/hygiene/java-doctor/)
   (copy `TEMPLATE.md`). Machine-facing suppressions go in `config/java-doctor/suppressions.json`
   (and the SpotBugs exclude when class-level).
7. Only then implement accepted fixes on a **dedicated hygiene branch + PR**; put the same short
   briefing in the PR body and link the campaign note. A modest low-signal allotment after
   high-signal items is fine when the operator agrees.
8. **Security-sensitive zones** (Tink keyset, HMAC keys, login/logout, encryption listeners):
   careful counter-analysis; characterization or complementary tests before a refactor when
   behavior might change. If the decision is leave + suppress, skip those tests.

No `I-*` / `TB-*` / GitHub issue per finding.

## Authority

`product-docs/global/java-doctor-curated-evaluation-2026-07-11.md`,
`I-2026-07-11-java-doctor-curated-hygiene`, `TB-2026-07-11-java-doctor-curated-mvp`,
[`product-docs/global/hygiene/java-doctor/README.md`](../../../product-docs/global/hygiene/java-doctor/README.md).
