# Critical review — hygiene method

**Keyword:** `critical-review`

**Aliases:** adversarial review · critical review · revue critique · plan hardening (Stage 2)

Launched, read-only, **forced different-model-family** cloud-agent review of one artifact (vision,
G1 brief, plan, or PR), then one-finding HITL. Same trio as `assessment-curated`: skill + this
canon + prompt template. Tools Stage 2 of the methodology two-stage pattern — it does **not**
replace `cold-agent-plan-review.prompt.md` (that file stays the **autonomous-execution hardening**
angle, included by reference).

Index: [`../README.md`](../README.md). Skill: [`.cursor/skills/critical-review/SKILL.md`](../../../../.cursor/skills/critical-review/SKILL.md).
Prompt: [`../../../templates/critical-review.prompt.md`](../../../templates/critical-review.prompt.md).

## When to use (proportionate)

**Yes:**

- Gates that ask for plan hardening (G1 briefs)
- P0/P1 visions before merge
- Security boundaries, migrations, cross-component contracts
- Plans aimed at fully autonomous cold execution

**No:** routine hygiene, Dependabot, obvious local fixes, or when a `*-curated` lane already owns
the signal.

**Ceilings:** 1 review per artifact version (at most 2 rounds); ≤10 findings; ~2-page report.

## Parameters

| # | Param | Required | Default |
| --- | --- | --- | --- |
| 1 | Baseline compass | automatic | Always-list in the prompt + Tier-2 by target |
| 2 | `target` (doc path or `PR #N`); `extras` | yes | — |
| 3 | `model` + `effort` | yes | **Forced ≠ author model family**; high effort |
| 4 | `angle` | no | General review |

**Launchers:** Mathieu (default: visions, G1 briefs); Patrick (PR craft); Christophe (security);
Marc on request.

## Report shape

Follow the prompt: verdict → premise challenge → ranked findings (S1/S2/S3) → accidental complexity
→ inconsistencies → unverified → ≤5 questions. Raw reports stay ephemeral (conversation only).

## HITL

Launcher relays to Marc: short lot overview, then **one finding at a time**.

| Operator says | Record as | Next |
| --- | --- | --- |
| **amend** or **GO** (for that finding) | `amend` | Apply on author's branch / follow-up PR — not by the reviewer |
| **defer** | `defer` | Later gate or next review |
| **skip** | `skip` | Out of scope / not worth acting |

Never ask bare “No-Go.” Never invent `I-*` / `TB-*` per finding.

## Feedback loop

1. Write `YYYY-MM-DD-<slug>.md` from [`TEMPLATE.md`](TEMPLATE.md) (decisions table + 1–3 lessons).
2. A lesson that **recurs twice** → reinject into living canon (prompt, `AGENTS.md`, principle) and
   **Accumulated lessons** below.
3. **Survival metric:** track `amend` / total per note. If the rate stays **under 30% across 3
   reviews**, adjust the prompt or **retire the method** (ablation spirit).

## Accumulated lessons (≤10)

- Caught a real contradiction with the live compass (`V-2026-09-26`) when a vision claimed Play
  production while the compass locked closed-testing only (CR1).
- Caught a disproportionate premise: expensive greenfield path locked before M1 while a smaller
  in-stack option (extend Acme) was never compared (CR1).
- Respected settled locks (C1–C5) and did not reopen them without new signal (CR1).
- Sized audience honestly: anonymous evaluator Tenant Admins vs email-reachable operators; park
  when the stated trigger is not met (CR2).
- Prefer overview fields and true dismiss over parallel endpoints and rate-limit theater on a P3
  dashboard widget (CR2).

## First instances

- [`2026-10-08-pr711-northwind-vision.md`](2026-10-08-pr711-northwind-vision.md) — CR1
- [`2026-10-08-pr724-announcements.md`](2026-10-08-pr724-announcements.md) — CR2

## How to invoke (operator cheat sheet)

Minimal:

```text
critical-review
target: product-docs/global/vision/V-….md   # or PR #N
model: <family ≠ author>  effort: high
```

Recommended:

```text
critical-review / adversarial review
target: PR #711
extras: product-docs/global/vision/V-2026-10-07-….md
angle: Play premise, onboarding load, trigger security
model: Claude Opus (author was Composer/other)  effort: high
```

Then: fill [`critical-review.prompt.md`](../../../templates/critical-review.prompt.md) → one fresh
cloud agent → HITL one finding at a time → dated note from TEMPLATE.
