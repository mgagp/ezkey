---
name: assessment-curated
description: >-
  Runs a mandate-driven white-box hygiene assessment then one-finding-at-a-time HITL
  (briefing, decision, handoff, campaign note). Use when the operator says assessment-curated
  or asks for a focused investigation pass (e.g. mobile crypto, Java transactions) outside
  doctor/Dependabot/pentest tool lanes.
disable-model-invocation: true
---
# Assessment curated

## Purpose

Turn an operator **investigation mandate** into a **small prioritized finding lot**, walk each
finding with interactive HITL, write ephemeral handoffs, and keep a dated campaign note — without
inventing `I-*` / `TB-*` per item.

This is **code hygiene**, same weight class as `doctor-curated` / `dependabot-curated`. Entry signal
is a **human mandate + white-box assessment**, not an OSS linter shortlist.

## Boundary contract

- **Enter when:** the operator says `assessment-curated`, or asks for a focused hygiene
  investigation/assessment with HITL follow-up (crypto, transactions, protocol honesty, etc.).
- **Exit when:** the mandated lot has a campaign note with decisions (or explicit deferrals);
  handoffs exist for in-flight fix/analysis items; assessment dispositions match HITL outcomes for
  closed items.
- **Call next:** implementing agents via `HANDOFF-*.md` starters; optional `grill-me` inside a
  handoff when the operator requires design lock; optional `I-*` only if the operator funds a
  program-sized redesign.
- **Not needed when:** `doctor-curated` / `java-doctor-curated` / `mobile-doctor-curated` /
  `dependabot-curated` / `security-pentest-curated` already owns the work.

## Authority

- Method canon: [`product-docs/global/hygiene/assessment-curated/README.md`](../../product-docs/global/hygiene/assessment-curated/README.md)
- Campaign template: [`…/TEMPLATE.md`](../../product-docs/global/hygiene/assessment-curated/TEMPLATE.md)
- Handoff template: [`…/HANDOFF-TEMPLATE.md`](../../product-docs/global/hygiene/assessment-curated/HANDOFF-TEMPLATE.md)
- Hygiene index: [`product-docs/global/hygiene/README.md`](../../product-docs/global/hygiene/README.md)
- Hygiene vs program: [`product-docs/methodology/README.md`](../../product-docs/methodology/README.md) § *Three rules worth keeping*
- Root keyword: `AGENTS.md` § Assessment curated
- Copilot mirror: `.github/copilot-instructions.md` § Assessment curated

## Procedure

### A — Start or continue an assessment

1. Lock **mandate**: surface, attention axes, non-goals (see method README).
2. Explore white-box; write/update a durable assessment register with evidence and a **3–6** item lot.
3. Open or amend a dated campaign note under `product-docs/global/hygiene/<topic-lane>/` (create
   the lane folder if new; use campaign `TEMPLATE.md`).

### B — HITL loop (mandatory)

1. Present a short lot overview once.
2. For **each** finding, one at a time:
   - Briefing: verdict, code citations, observation scenario, options table, and a
     **recommended GO for this item** (what the handoff would do; what it would not do).
   - Wait for a **HITL reply** — do not ask a bare “GO / No-Go.” Canon:
     method README § **HITL replies**.
   - **GO** / **fix** = accept as work; write `HANDOFF-…`; **do not code** unless the operator
     also says implement now. **GO is not** “next finding” and **not** “I agree with the
     write-up.”
   - **defer** / **suppress** / **skip** = record that decision; no handoff unless asked;
     then the next finding.
   - On any decision: amend the campaign note decisions + rationale.
3. Do **not** replace HITL with a bulk options matrix.

### C — Closeout hygiene

- Consolidate PR links into the campaign note; **delete** completed handoffs.
- Update assessment dispositions for fixed/deferred rows.
- Promote to `I-*` / `TB-*` only when the operator explicitly chooses program funding.

## First instance

- Lane: `product-docs/global/hygiene/mobile-protocol-security/`
- Assessment: `docs/security/mobile-protocol-crypto-assessment-2026-07.md`

## If the operator asks how to invoke

Quote or paraphrase **How to invoke (operator cheat sheet)** from
[`product-docs/global/hygiene/assessment-curated/README.md`](../../product-docs/global/hygiene/assessment-curated/README.md)
(minimal kickoff, recommended kickoff with mandate lines, and one concrete example). Keep the
answer short; do not expand into a full methodology lecture.
