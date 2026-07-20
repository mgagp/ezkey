# Assessment curated — hygiene method

**Keyword:** `assessment-curated`

Mandate-driven white-box hygiene: an operator gives a **focused investigation mandate**, the agent
produces a **prioritized assessment register**, then the pair walks findings **one at a time**
(HITL) into decisions and ephemeral **handoffs**. Sibling weight class to `doctor-curated` and
`dependabot-curated` — **not** product methodology (`I-*` / `TB-*` per finding).

Index of all hygiene lanes: [`../README.md`](../README.md).

## When to use

Use when the operator wants a **scoped deep look** that tools alone will not shortlist well, for
example:

- mobile crypto / Keystore / enrollment local model,
- Java transactional boundaries across services,
- a protocol surface vs living docs honesty.

Do **not** use when a doctor/pentest/Dependabot script already owns the signal — use that lane’s
keyword instead.

## Framing the mandate (keep this short)

Before exploring, lock three lines with the operator (or infer explicitly from their kickoff):

1. **Surface** — which tree / modules (e.g. `ezkey_mobile`, `ezkey-core` + Auth API).
2. **Attention axes** — what to privilege (e.g. crypto continuity, sealed storage, JPA `@Transactional`).
3. **Non-goals** — what to skip (e.g. iOS, live DAST, zero-warning campaigns, unrelated refactors).

That mandate paragraph removes most ambiguity between “browse the repo” and “produce a curated
lot.” Write it into the assessment header and the campaign note metadata.

## Ceremony (six steps)

1. **Mandate** — surface + axes + non-goals (above).
2. **Assessment** — dated register (often under `docs/…`) with evidence-backed findings, severity,
   confidence, fail-open/fail-closed where relevant, small prioritized lot (about 3–6).
3. **Campaign note** — copy [`TEMPLATE.md`](TEMPLATE.md) into a **topic lane** folder under
   `product-docs/global/hygiene/<lane>/YYYY-MM-DD-pass-N.md` (create the lane folder if needed).
4. **HITL per finding** — human briefing with code citations + short scenario → options
   (`fix` / `defer` / `suppress` / `skip`) → discussion → decision. **One finding at a time.**
5. **Handoff** — ephemeral `product-docs/global/backlog/handoffs/HANDOFF-…` from
   [`HANDOFF-TEMPLATE.md`](HANDOFF-TEMPLATE.md); delete on PR/closeout after consolidating into the
   campaign note.
6. **Amend** — update the campaign decisions table and rationale; update assessment dispositions
   when fixes land.

## HITL briefing shape (per item)

- One-sentence verdict.
- Context + code citations (why it matters in *this* tree).
- Scenario that led to the observation (preserve in the handoff).
- Options table (fix / defer / suppress / skip).
- Wait for operator clarity / decision before writing the handoff (unless they say “handoff + fix”
  in one go).

## Anti-patterns

- Inventing `I-*` / `TB-*` / GitHub issues per finding (promote only when the operator funds a
  program-sized redesign).
- Bulk “approve all” matrices instead of one-finding HITL.
- Implementing remediations inside the assessment session unless the operator opens that scope.
- Treating this as a substitute for `doctor-curated` tool shortlists or `security-pentest-curated`.

## First instance

Mobile protocol / crypto: [`../mobile-protocol-security/`](../mobile-protocol-security/)  
Example assessment: [`../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)

## Agent entrypoints

- Cursor skill: [`.cursor/skills/assessment-curated/SKILL.md`](../../../.cursor/skills/assessment-curated/SKILL.md)
- Root keyword: `AGENTS.md` § Assessment curated
- Copilot mirror: `.github/copilot-instructions.md` § Assessment curated

## How to invoke (operator cheat sheet)

If the operator asks how to start or how to phrase an **`assessment-curated`** session, summarize
**this section** (do not invent a heavier ceremony).

**Minimal kickoff** (keyword only — agent should ask for mandate lines if missing):

```text
assessment-curated
```

**Recommended kickoff** (keyword + mandate in one message):

```text
assessment-curated

Mandate:
- Surface: <modules / trees>
- Attention axes: <what to privilege>
- Non-goals: <what to skip>

Follow product-docs/global/hygiene/assessment-curated/README.md and the assessment-curated skill.
Produce an assessment register and a small HITL lot; do not implement fixes until I decide per finding.
```

**Example (Java transactions):**

```text
assessment-curated

Mandate: surface ezkey-core + Auth/Admin/Integration APIs; attention axes = @Transactional /
propagation / self-invocation / boundary consistency; non-goals = DAST, doctor-curated shortlist,
I-*/TB-* unless we promote later.

Follow product-docs/global/hygiene/assessment-curated/README.md and the assessment-curated skill.
```

**Example (continue mobile crypto HITL):** point at the open campaign note under
`product-docs/global/hygiene/mobile-protocol-security/` and say `assessment-curated` + “continue
pass-2 HITL from MOB-0xx”.
