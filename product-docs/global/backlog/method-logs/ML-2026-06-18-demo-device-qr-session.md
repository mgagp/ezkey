# Method Log — `ML-2026-06-18` Demo Device QR session + methodology friction (Lane E)

## Metadata

- **ID:** `ML-2026-06-18-demo-device-qr-session`
- **Related idea:** `I-2026-06-18-demo-device-qr-auth-url-parity`
- **Related tracer bullet:** `TB-2026-06-18-demo-device-qr-auth-url-parity`
- **Incubation source (incidental):** ~~`.cursor/plans/demo-device-qr-auth-url-parity.plan.md`~~ **deleted** at closeout (2026-06-18)
- **Purpose:** Running notes + **methodology retrospective input** (plan file vs `product-docs` only).
- **Rule:** Candidate methodology changes stay here until a dedicated Lane E retro promotes a
  `methodology/decisions/*` entry.

---

## Session summary (product work)

| Topic | Outcome |
|-------|---------|
| Admin UI dependency hygiene #220 / PR #221 | Merged; local branch deleted |
| Demo Device bind failure (standalone :3080 vs clean-start :8083) | Root cause: config (`exp1-auth-api`) + client did not POST `authApiBaseUrl` despite banner |
| QR `authUrl` parity scope | `I-*`, Grill Me, `TB-*` promoted; dual-repo delivery in `case-study-ezkey.md` |
| Implementation | **Done** — monorepo + standalone sync; maintainer `:3080` sign-off 2026-06-18 |

---

## Methodology retrospective entry — `.cursor/plans/` created alongside canonical artifacts

**Date:** 2026-06-18  
**Reporter:** Marc (operator expectation)  
**Agent:** cold-session Agent mode (not Plan mode first)

### What happened

After a converged discussion (incident diagnosis + scope agreement), the agent materialized:

- `product-docs/global/backlog/ideas/I-2026-06-18-demo-device-qr-auth-url-parity.md`
- `product-docs/global/backlog/grill-sessions/2026-06-18-demo-device-qr-auth-url-grill-me.md`
- `product-docs/global/backlog/TB-2026-06-18-demo-device-qr-auth-url-parity.md`
- `product-docs/methodology/case-study-ezkey.md` (dual-repo delivery pattern)

**Also created:** `.cursor/plans/demo-device-qr-auth-url-parity.plan.md`

Operator expected canonical artifacts under **`product-docs/` only**, not an additional Cursor plan file.

### Actual lane vs artifact choice

| Dimension | Reality |
|-----------|---------|
| **Lane used** | **D** (post-delivery / parity gap after runtime observation) |
| **Plan mode first?** | **No** — Agent mode throughout |
| **Direction fluidity at capture** | **Low** — root cause known; mobile parity is the obvious fix |
| **Plan incubation criteria met?** | **No** — `plan-incubation-workflow.md` says do **not** use when idea is crisp enough for direct `I-*` capture |

### Why the agent created `.cursor/plans/` anyway (honest reflex trace)

1. **`product-docs-workflow-bootstrap` rule** mentions plan-incubation lane and allows `.cursor/plans/` as a deliberate entrypoint — read as “always pair methodology with a working plan.”
2. **`plan-incubation-workflow.md`** legitimizes `.cursor/plans/` as incubation source — without the agent applying the **negative** rule (“already crisp → skip”).
3. **Session instruction** (“consult methodology, create artifacts, guide Grill Me”) was interpreted as full hybrid path (plan + canon) rather than **Lane D minimal canon** (I → Grill → TB).
4. **Redundant convenience** — the plan duplicated TB sections (design sketch, delivery order, next actions) with no unique option-space exploration.

### Operator expectation (valid)

> With a Grill + I + TB session like this, methodological documentation should **live in `product-docs/`** and **not** also spawn a Cursor plan unless Plan mode incubation genuinely preceded canon.

This aligns with:

- `plan-incubation-workflow.md` — working plan is **incubation source, not final home**
- `minimum-viable-method.md` — smallest artifact set that protects the decision
- Lane **D** — re-entry at `I-*` / `TB-*`, not Lane **B**

### Options for encadrement (candidate — not decided)

| Option | Summary |
|--------|---------|
| **A. Ban `.cursor/plans/` when Lane ≠ B and Plan mode was not used** | Strict; matches operator expectation |
| **B. Allow plan file only if it adds non-duplicated content** (option comparison, open forks) | Flexible; agent must justify in one line |
| **C. Always add `Canonical materialization` footer to plan; delete plan if 100% duplicated in TB** | Hygiene after the fact |
| **D. Move “working plan” equivalent into TB § Incubation / handoff only** | Single canonical file; no `.cursor/plans/` for slices like this |

**Provisional recommendation for skill update:** **A + D** for cold Agent sessions on crisp Lane D scope; reserve `.cursor/plans/` for Lane B or genuinely fluid Plan-mode incubation.

### Follow-up actions (methodology program — separate session)

- [ ] Lane E retro with operator: pick option A/B/C/D (or hybrid)
- [ ] Update `plan-incubation` skill: negative gate (“crisp Lane A/D → no `.cursor/plans/`”)
- [ ] Update `product-docs-workflow-bootstrap` / `session-start-guide`: Lane D default artifact set
- [ ] Consider `traceability-sync` check: if `I-*`+`TB-*` exist without Lane B, flag orphan `.cursor/plans/*`
- [ ] Promote decision to `methodology/decisions/2026-06-18-*.md` when settled

**Related (separate Lane E topic):** closeout skill before commit —
[`ML-2026-06-18-closeout-skill-before-commit-gap.md`](ML-2026-06-18-closeout-skill-before-commit-gap.md).

### Plan file disposition

`.cursor/plans/demo-device-qr-auth-url-parity.plan.md` was **deleted** at product closeout (2026-06-18).
Canonical direction: `product-docs` artifacts (`I-*`, `TB-*`, grill, this ML). Lane E retro on
`.cursor/plans/` policy remains in follow-up actions below.

---

## Closeout entry — implementation (2026-06-18)

**Lane:** D (program slice — not hygiene; artifacts retained).

**traceability-sync:** No update to `features-and-phases.md` / `spec-test-traceability.md` (simulator
slice; evidence in `TB-*` § Closeout and `ezkey-demo-device/AGENTS.md`).

**closeout:** `I-*` and `TB-*` → `done`. Operator validated standalone `:3080` QR bind against local
Auth API via ngrok. Client follow-up: `sessionStorage` + submit sync for hidden `authApiBaseUrl`.

**Post-close hygiene (2026-06-18):** Jackson 3 pom (#209 / PR #233) + standalone `AGENTS.md` sync —
consolidated in [`ML-2026-06-18-session-hygiene-consolidation-closeout.md`](ML-2026-06-18-session-hygiene-consolidation-closeout.md).

**Residual:** GitHub issue optional; `:8083` smoke not re-run after client-only fix.

---

## Entries (future — methodology program only)
