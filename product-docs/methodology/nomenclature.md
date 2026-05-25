# Nomenclature

## Purpose

This document defines stable IDs and status vocabulary for vision, backlog, and tracer-bullet work.

## Identifier conventions

### Current format (all new artifacts)

All artifacts created after the 2026-05-22 migration use a **date + slug** identifier. No
counter lookup is needed; no index file must be read before creating a file.

- `V-YYYY-MM-DD-<slug>` — vision notes
- `I-YYYY-MM-DD-<slug>` — backlog ideas
- `TB-YYYY-MM-DD-<slug>` — tracer bullets
- `R-YYYY-MM-DD-<slug>` — legacy retrofit slices
- `F-<short-kebab-name>` — feature catalog entries (unchanged)
- `ADR-XXXX` / scoped ADR IDs — architecture or design decisions (unchanged)

Examples: `I-2026-05-22-rate-limit-analysis`, `V-2026-05-22-api-portal-posture`,
`TB-2026-05-22-mobile-enrollment-slice`.

If two artifacts are created on the same day about the same topic, differentiate the slug
(e.g., add a qualifier). No ordinal suffix is needed as a rule — the slug carries the distinction.

### Legacy format (pre-2026-05-22 artifacts — do not rename)

Existing artifacts use the sequential `NNNN` format. These identifiers are **stable** — never
renamed, never reused.

- `V-2026-NNNN` — vision notes
- `I-2026-NNNN` — backlog ideas
- `TB-2026-NNNN` — tracer bullets
- `R-2026-NNNN` — legacy retrofit slices

For multi-branch and multi-worktree work, see [`multi-branch-workflow.md`](multi-branch-workflow.md).

## Working artifact terminology

- `working plan` — a live, non-canonical planning artifact used for current-session incubation, often under `.cursor/plans/` or `plans/`

A working plan is **not** a canonical destination by itself and is **not** a retrofit slice by default. Its normal role is to support brainstorming and convergence before materialization into `V-*`, `I-*`, `TB-*`, and related canonical artifacts.

## Filename conventions

### New artifacts (date+slug)

- `V-YYYY-MM-DD-<slug>.md`
- `I-YYYY-MM-DD-<slug>.md`
- `TB-YYYY-MM-DD-<slug>.md`
- `R-YYYY-MM-DD-<slug>.md`

### Legacy artifacts (NNNN, do not rename)

- `V-YYYY-NNNN-<slug>.md`
- `I-YYYY-NNNN-<slug>.md`
- `TB-YYYY-NNNN-<slug>.md`
- `R-YYYY-NNNN-<slug>.md`

Use stable IDs. Do not reuse retired identifiers.

## Blitz scratch board filename conventions

Blitz intake scratch boards (see [`blitz-intake-pattern.md`](blitz-intake-pattern.md)) follow:

- **Active** (during the session): `product-docs/global/backlog/_blitz-YYYY-MM-DD[-N].md` (with `_` prefix as a staging signal).
- **Archived** (post-materialization): `product-docs/global/backlog/blitz-archive/blitz-YYYY-MM-DD[-N].md` (no `_` prefix).

`[-N]` is the ordinal for multiple blitz sessions on the same day (`-1`, `-2`, …). Use the suffix consistently when more than one blitz exists on the same date. Archived scratches are never deleted automatically; deletion is operator-initiated.

## Grill session filename conventions

Structured grilling output for one idea or a related cluster lives under:

- `product-docs/global/backlog/grill-sessions/<topic>-grill-me.md`

Each file must include a **Session control** section with `Resume at` (question id or section) so a later session can continue without re-deriving settled decisions. Link the grill session from the related `I-*`, `V-*`, or `TB-*` artifacts; update backlog index status when grilling moves an item to `incubating` or records new decisions.

## Backlog status vocabulary

Backlog ideas (`I-*`) use the following lifecycle:

- `captured` — raw idea recorded.
- `triaged` — clarified and categorized.
- `incubating` — being explored but not yet execution-ready.
- `ready` — actionable for tracer-bullet promotion.
- `active` — currently under execution.
- `done` — fully delivered and closed.
- `parked` — intentionally paused for later review.
- `archived` — retained for history, not active.
- `dropped` — explicitly rejected.

## Vision status vocabulary

Vision notes (`V-*`) follow a lightweight 4-state lifecycle:

- `draft` — orientation captured, not yet stress-tested.
- `under-review` — under grilling, or a decision has been tranched but is not yet recorded in a durable artifact.
- `promoted` — substance canonized into a durable artifact (e.g. `architecture-decisions.md`, `design-principles.md`, the feature catalog in `features-and-phases.md`). The vision note has fulfilled its orientation purpose successfully.
- `archived` — retained for history without canonization. Used when the orientation is abandoned, merged into another note, or made obsolete. Not a failure flag, just a non-promotion outcome.

A vision note is **not** the canonical home for a final decision. When a decision is made, the orientation transitions to `under-review`, and the substance must be canonized in a durable artifact before the note moves to `promoted`.

## Tracer-bullet status vocabulary

Tracer bullets (`TB-*`) follow the same 4-state lifecycle as vision notes:

- `draft` — slice scope and exit criteria sketched, not yet executing.
- `under-review` — slice executed, awaiting closure and canonization of learnings.
- `promoted` — learnings and decisions canonized into the durable artifacts they touched (component design notes, ADRs, feature catalog, etc.). The tracer bullet has fulfilled its bounded-execution purpose.
- `archived` — slice closed without promotion. Used when the experiment is intentionally not graduated, or when learnings are absorbed elsewhere without dedicated canonization.

## Retrofit status vocabulary

Retrofit slices (`R-*`) follow a 4-state lifecycle with explicit transition triggers:

- `captured` — retrofit slice initialized from source plans; the slice file exists with intent and source batch identified, but the canonical destinations have not yet been mapped.
- `mapped` — canonical destinations identified and listed in the slice's mapping table. Items in the table may still be marked as gaps (`pending follow-up`); the slice is `mapped` once the table itself is filled, regardless of how many entries are still gaps.
- `integrated` — **all** canonical destinations identified during mapping are updated. Gaps recorded at the time of mapping are either closed in this slice or migrated to a follow-up `R-*`. A slice with residual unaddressed gaps is not `integrated`.
- `archived` — retrofit slice closed and retained for history. Used after `integrated`, or when the slice is intentionally abandoned (rare, requires recorded rationale in the slice file).

## Priority vocabulary

- `P0` — **critical blocker.** Total blocker; must be addressed as soon as possible. Reserved for situations where the platform, a release, or a customer-facing flow is materially broken or at unacceptable risk. Should remain rare.
- `P1` — **most pressing.** Strategic, structural, foundational; items with the highest dependency footprint on other items. Use for items whose absence delays multiple downstreams, or for foundational decisions that unblock the next phase.
- `P2` — **standard.** Functional-mode work — targeted improvements across the platform. Default tier for most backlog items in routine evolution.
- `P3` — **comfort.** Polish, optional improvements, non-essential. Acceptable to defer indefinitely; review periodically to confirm continued relevance.

## Git branch names (issue-backed work)

When an `I-*` idea has a GitHub issue, the implementation branch follows:

`feature/<issue-number>-<i-artifact-id-lowercase>[-<optional-topic>]`

where `<i-artifact-id-lowercase>` is the full `I-*` identifier with `I` → `i`. Full rules,
examples, and issue↔branch linking steps: [`github-issues-workflow.md`](github-issues-workflow.md#branch-naming-and-issue-linking).

## Canonical decision recording (three scopes)

Do not mix these destinations. Each scope has one canonical home:

| Scope | Canonical location | Use for |
|-------|-------------------|---------|
| **Global product / architecture** | [`../global/architecture-decisions.md`](../global/architecture-decisions.md) | ADRs that apply across modules (protocol, lifecycle, error model, roadmap phase changes). |
| **Component pack** | `product-docs/components/<pack>/design-decisions.md` | ADRs scoped to one module (Admin API, mobile, Admin UI, …). |
| **Methodology / process** | [`decisions/YYYY-MM-DD-<slug>.md`](decisions/) | Workflow conventions, artifact formats, collaboration rules — not product behavior. |

There is **no** global `design-decisions.md`. That filename is **component-scoped only**.

When a vision note (`V-*`) or tracer bullet (`TB-*`) reaches `promoted`, its durable substance must live in one or more of the destinations above (or in other global canon such as [`../global/design-principles.md`](../global/design-principles.md), [`../global/features-and-phases.md`](../global/features-and-phases.md), operator guides, or component design notes) — not only inside the orientation artifact.

## Phase tags (backlog and vision metadata)

Phase tags classify **which roadmap phase** an idea primarily serves. They are **not** priority labels (`P0`–`P3` in the backlog) and not informal profile names.

**Closed enumeration** — use only identifiers defined in [`../global/roadmap.md`](../global/roadmap.md):

- `P0-foundations`
- `P1-operability`
- `P2-hardening`
- `P3-distribution`
- `P4-compliance-readiness`

Rules:

- An artifact may carry **one or more** phase tags when it spans phases.
- Adding a **new** phase requires updating `roadmap.md`, `features-and-phases.md`, and an ADR entry in `architecture-decisions.md` — not inventing a tag in isolation.
- Invalid examples (do not use as phase tags): `P3-comfort`, `P2-functional-mode`, `toolchain`, `mobile`.

Authoritative phase definitions: [`../global/roadmap.md`](../global/roadmap.md) and [`../global/features-and-phases.md`](../global/features-and-phases.md).

## Component tags (backlog and vision metadata)

Component tags name **which Ezkey modules or doc surfaces** an idea touches. Prefer the **closed vocabulary** below; align GitHub `component:*` labels with the same tokens (see [`github-issues-workflow.md`](github-issues-workflow.md)).

| Tag | Typical scope |
|-----|----------------|
| `core` | Shared domain, persistence, cross-cutting Java modules |
| `admin-api` | Admin API boot module and controllers |
| `auth-api` | Auth API boot module |
| `integration-api` | Integration API boot module |
| `crypto-api` | Crypto API module |
| `admin-ui` | Admin UI frontend |
| `mobile` | Mobile apps (Android / iOS) |
| `sdk` | Client SDKs (language-agnostic; use `sdk-java` only when Java SDK alone) |
| `sdk-java` | Java SDK specifically |
| `infra` | Docker, compose, deployment, HA, observability stack |
| `bootstrap` | First-run / clean-start bootstrap flows |
| `audit` | Audit chain, checkpoints, integrity |
| `repositories` | Data-access / SQL layer concerns |
| `migration` | Flyway / schema migrations |
| `docs` | `docs/` hub, operator docs, generated specs workflow |
| `methodology` | `product-docs/methodology/`, templates, skills mechanics |
| `skills` | `.cursor/skills/` or equivalent agent skills |
| `site` | Public static site (`sites/`) |

Rules:

- Use **`docs`**, not `docs (product-docs)` — the parent folder is implied.
- Adding a **new** tag requires a one-line entry in this table **and** a matching `component:*` label in `github-issues-workflow.md` when GitHub Issues are in use.
- Prefer the **smallest accurate set** (typically one to four tags).

## Superseded / Supersedes (reformulation, not abandonment)

When an artifact is **`archived`** because its substance was **reformulated** into successor artifact(s) — not because the idea was dropped — record explicit lineage:

- On the **old** artifact: `**Superseded by:**` or `**Superseded:**` with successor ID(s) and one-line reason.
- On the **successor**: `**Supersedes:**` with predecessor ID(s).

Apply to `V-*`, `I-*`, and `TB-*` when reformulation occurs. Precedent: `V-2026-0002` → `V-2026-0010`.

When an artifact is archived because the idea was **abandoned** or **merged without a named successor**, use `archived` without a supersession line and record rationale in the artifact body.

## Optional automation follow-up (soft convention)

Some backlog ideas note a **future** lightweight skill, check, or registry sync (for example after a manual doc artifact exists). This is optional metadata — not a lifecycle status.

When used, add a short optional section **`## Automation follow-up (optional)`** in the `I-*` file:

- one bullet for the candidate automation (skill name or check type),
- one bullet for the **trigger** (for example "after registry manually covers all Admin API controllers").

Do not block promotion to `ready` on automation existing. Implement skills separately via normal backlog promotion.

## Corpus completeness audit (blitz and backlog batches)

When verifying that a blitz intake or backlog batch is **fully integrated** (same check repeatable across sessions), confirm:

1. **Archive** — scratch board moved to `blitz-archive/` with materialized ID list in the header (blitz only).
2. **Materialization** — every captured item has a stable `V-*` / `I-*` / `R-*` or an explicit drop note.
3. **Grill** — each item has a `grill-sessions/*` file marked `complete`, or a documented deferral in the `I-*` / `V-*`.
4. **Status alignment** — backlog index and artifact metadata reflect post-grill status (`incubating` / `ready`, not stale `captured`).
5. **Retrofit slices** — `R-*` status at least `mapped` once the mapping table is filled; `integrated` when all mapped destinations are updated.
6. **Cross-links** — grill sessions, archives, and canonical spin-offs (matrices, policies, registries) reference each other.
7. **Supersession** — reformulated visions/items carry `Superseded` / `Supersedes` per convention above.

Gaps found during audit become normal backlog or methodology work — not silent drift.

## Identifier hygiene (avoid silent duplicates)

For **slug-based identifiers** (`F-*` feature catalog entries, and the slug portion of any artifact filename), check for similar keywords before creating a new entry:

- For `F-*`: scan `product-docs/global/features-and-phases.md` for adjacent topics or near-synonyms. Prefer extending an existing entry over creating a duplicate.
- For artifact slugs: a quick `rg` on the topic keywords in `product-docs/global/backlog/ideas/`, `product-docs/global/vision/`, and `product-docs/global/legacy-retrofit/` is usually enough to detect overlap.

For **date+slug identifiers** (current format), the date component reduces collision risk
significantly; the slug hygiene check is still valuable to avoid semantic duplicates across
different dates. For **legacy NNNN identifiers**, two parallel branches could assign the same
number — see [`multi-branch-workflow.md`](multi-branch-workflow.md) for the resolution protocol.
