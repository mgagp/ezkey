---
public: false
---
# Artifact Tagging and Canonization Conventions (Tier 2 Hygiene)

## Date

2026-05-24

## Context

During the 2026-05-08 nomenclature hardening pass (second blitz materialization), several
conventions were applied in practice but left implicit. They were tracked in `I-2026-0016`
(Tier 2 follow-ups). Without explicit rules, repeat audits (for example "are blitz 1 and 2 fully
integrated?") risk inconsistent conclusions across sessions.

This decision closes `I-2026-0016` items E, F, H, I, J, and K.

## Working assumptions

- Explicit conventions beat tribal knowledge for human + AI collaboration.
- Closed enumerations with a **documented extension path** beat unbounded free-form tags.
- Not every soft pattern needs a rigid template — optional sections are enough when usage is rare.

## Options considered

| Topic | Option | Outcome |
|-------|--------|---------|
| **E — V-* vs decisions** | V-* holds decisions permanently | Rejected — canon lives in durable destinations |
| **E — V-* vs decisions** | Tranched decisions in V-* + grill until canonized | **Chosen** — `under-review` until promoted |
| **F — Decision log filename** | Single global `design-decisions.md` | Rejected — collides with component scope |
| **F — Decision log filename** | Three-scope model (global ADR / component / methodology) | **Chosen** |
| **H — Phase tags** | Open vocabulary | Rejected — drift (`P3-comfort`, `toolchain` in phase field) |
| **H — Phase tags** | Closed P0–P4 from roadmap | **Chosen** |
| **I — Component tags** | Fully open | Rejected — `docs (product-docs)` variants |
| **I — Component tags** | Closed table + extension rule | **Chosen** |
| **J — Skill candidate** | Required template fields | Rejected — ceremony without payoff |
| **J — Skill candidate** | Optional `Automation follow-up` section | **Chosen** |
| **K — Superseded** | Implicit only | Rejected — audit gaps |
| **K — Superseded** | Documented in nomenclature | **Chosen** |

## Decision

### E — Vision vs decision recording

- `V-*` = orientation; grill sessions hold session-authoritative Q&A.
- Tranched decisions may appear in `V-*` while `under-review`.
- Durable decisions must be copied to canonical destinations before `promoted`.
- Methodology/process decisions use `methodology/decisions/`, not `V-*`.

### F — Decision log filenames

| Scope | File |
|-------|------|
| Global product | `product-docs/global/architecture-decisions.md` |
| Component | `product-docs/components/<pack>/design-decisions.md` |
| Methodology | `product-docs/methodology/decisions/YYYY-MM-DD-<slug>.md` |

### H — Phase tags

Closed list: `P0-foundations`, `P1-operability`, `P2-hardening`, `P3-distribution`,
`P4-compliance-readiness`. New phases require roadmap + features catalog + ADR.

### I — Component tags

Closed vocabulary documented in `nomenclature.md`, aligned with GitHub `component:*` labels.
Use `docs`, not `docs (product-docs)`.

### J — Automation follow-up

Optional section in `I-*` and backlog template — no required fields, no lifecycle gate.

### K — Superseded / Supersedes

Required when archiving due to **reformulation** into named successors (`V-2026-0002` →
`V-2026-0010` precedent).

### Corpus completeness audit

Repeatable seven-point checklist added to `nomenclature.md` for blitz/backlog integration
verification.

## Consequences

- Updated [`nomenclature.md`](../nomenclature.md) — sections for F, H, I, J, K, and audit checklist.
- Updated `product-docs/global/vision/README.md` — section E.
- Updated [`../../templates/backlog-idea.template.md`](../../templates/backlog-idea.template.md) — tag hints + optional automation section.
- `I-2026-0016` closed as `done`.
- Legacy backlog entries with non-canonical phase/component tags remain valid history; normalize
  opportunistically when those files are next edited.

## Related documents

- [`../nomenclature.md`](../nomenclature.md)
- `product-docs/global/backlog/ideas/I-2026-0016-methodology-hygiene-followups.md`
- [`../blitz-intake-pattern.md`](../blitz-intake-pattern.md)
- `product-docs/global/roadmap.md`
