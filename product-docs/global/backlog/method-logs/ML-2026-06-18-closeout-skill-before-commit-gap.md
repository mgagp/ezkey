# Method Log — `ML-2026-06-18` Closeout skill before commit (Lane E)

## Metadata

- **ID:** `ML-2026-06-18-closeout-skill-before-commit-gap`
- **Lane:** `E` (methodology feedback — separate from product slice)
- **Related product slice:** `TB-2026-06-18-demo-device-qr-auth-url-parity` (trigger context only)
- **Related method log:** [`ML-2026-06-18-demo-device-qr-session.md`](ML-2026-06-18-demo-device-qr-session.md) (`.cursor/plans/` friction — different topic)
- **Purpose:** Retrospective on agent hesitation between “commit checklist” and **`traceability-sync` → `closeout`** when a program slice ends.
- **Status:** `integrated` (2026-06-18) — reinforcement applied to skills + methodology canon (see § Follow-up applied)

---

## What happened

After implementation and operator validation of the Demo Device QR `authUrl` slice:

1. The agent proposed a **pre-commit checklist** (staging scope, TB/ML doc updates as optional items, `.cursor/plans/` deletion as optional).
2. The operator asked to **commit**, then had to **redirect** the agent: consult methodology and the appropriate **skills** for a fluent closeout sequence.
3. The agent then read **`traceability-sync`** and **`closeout`**, updated `I-*` / `TB-*` / `ML-*`, deleted the orphan plan, and committed.

Outcome was correct after redirect; the friction was **discoverability and trigger routing**, not disagreement on substance.

---

## Why the agent led with “commit prep” instead of skills

Honest reflex trace (cold Agent mode, post-implementation):

| Factor | Effect |
|--------|--------|
| **Explicit “commit” user rule** | User rules prioritize git safety protocol when commit is mentioned → agent jumped to `git status` / diff / staging mental model. |
| **Hygiene closeout canon is loud** | `AGENTS.md`, `minimum-viable-method.md`, and decision `2026-06-06-*` train agents to **challenge** full methodology on closeout — correctly reduces over-materialization, but does not spell the **inverse**: when `I-*`/`TB-*` already exist and slice is done, **do** run skills before commit. |
| **`closeout` skill not auto-selected** | Skill description says “end of tracer bullets” but not “operator said commit while TB is open.” Skill has `disable-model-invocation: true` — relies on rules/docs routing, not tool auto-invoke. |
| **Lane A skill list is front-loaded** | `session-start-guide.md` lists `traceability-sync`, `closeout` at promotion/design time — less visible at **exit** of Lane D program slice. |
| **TB § Evidence plan treated as checklist** | Agent partially satisfied doc updates in prose to the operator without treating **`closeout` skill** as mandatory procedure. |
| **Engineering completion bias** | Tests green + operator “ça marche” → natural next step feels like VCS, not backlog status transitions. |

**Not the same issue as** `ML-2026-06-18-demo-device-qr-session` (spurious `.cursor/plans/`). That log is **artifact placement at intake**; this log is **skill invocation at exit**.

---

## Operator expectation (valid)

When a slice already has **`I-*` + `TB-*`** (program, not hygiene) and implementation is validated:

> Before `git commit`, the agent should **`traceability-sync` → `closeout`** on the canonical artifacts, then commit — without the operator naming the skills.

---

## Reinforcement chosen (lightweight)

Avoid new ceremony layers. Strengthen **triggers and discoverability** only:

1. **`closeout` skill** — § *When the operator asks to commit* (open `I-*`/`TB-*` gate).
2. **`minimum-viable-method.md`** — § *Program slice exit sequence* (ordered steps before VCS).
3. **`session-start-guide.md`** — prompt starter + Lane D exit note.
4. **`AGENTS.md`** — one explicit bullet (program exit before commit).

**Deferred (Lane E program):** promote to `methodology/decisions/2026-06-18-closeout-before-commit.md` if repeated friction on other slices.

---

## Follow-up applied (2026-06-18)

- [x] Update `.cursor/skills/closeout/SKILL.md`
- [x] Update `product-docs/methodology/minimum-viable-method.md`
- [x] Update `product-docs/methodology/session-start-guide.md`
- [x] Update root `AGENTS.md` § program slice exit
- [x] Cross-link from [`ML-2026-06-18-demo-device-qr-session.md`](ML-2026-06-18-demo-device-qr-session.md)

---

## Residual risk

Agents may still skip skills when the operator uses **hygiene-only** language (“just commit”) on a slice that **looks** like hygiene but has open `TB-*`. Mitigation: `closeout` skill gate checks for open `TB-*` / `I-*` in `product-docs/global/backlog/` regardless of wording.
