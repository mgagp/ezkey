# Method log — Integrity honesty line and audit export SPI (2026-06-28)

## Metadata

- **Date:** `2026-06-28`
- **Branch:** `feature/269-i-2026-0006-nightly-integrity-validation-batch`
- **Trigger:** Maintainer reflection (Plan-style discussion in Agent mode) questioning whether the
  audit-chain integrity effort had crossed into benevolent **escalation of commitment** — security
  complexity beyond what the SME target market needs.
- **Lane:** Plan-incubation → durable canon (values compass + vision + backlog + principle).
- **Outcome documents:**
  [`../../integrity-assurance-honest-line.md`](../../integrity-assurance-honest-line.md),
  [`../../vision/V-2026-06-28-audit-archive-export-spi.md`](../../vision/V-2026-06-28-audit-archive-export-spi.md),
  [`../ideas/I-2026-06-28-audit-archive-export-spi.md`](../ideas/I-2026-06-28-audit-archive-export-spi.md),
  [`../../../../docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md),
  [`../../design-principles.md`](../../design-principles.md) (principle #16).

## The original question

Is in-database audit integrity (per-entry HMAC, checkpoint chain, 5-min windows, heartbeat, nightly
batch, alerting, conciliation) **real or illusory** as long as everything lives in the DB? Is the
complexity **essential or accidental**? Are we protecting against an honest risk for an SME, or
escalating commitment to a security narrative beyond the target market?

## What the discussion established

1. **The trust boundary is the host, not the DB.** The HMAC key lives in a **file separate from the
   database** (and from the Tink master key). So protection is **real** against accidental corruption
   and a **DB-only adversary** (the nightly batch detects forged/edited rows), and **structurally
   blind** to a **host-rooted adversary** (who can recompute the whole chain). Not illusory —
   **bounded**.
2. **The host-boundary limit must be stated honestly**, not implied away. This — not more in-DB
   machinery — is the highest-ROI Phase-1 integrity move.
3. **A reusable red-line test** for any integrity control: (a) realistic SME threat, (b) actionable
   by a non-specialist / changes the adversary class, (c) no phantom external-assurance dependency.
   Failing any one ⇒ accidental complexity.

## Maintainer correction (high-signal)

The unbuilt external export (`AuditLifecycleService` seal/eligibility/confirm FSM) is **intentional,
not a gap or a misallocation**, for three reasons: **conceptual integrity / autonomy** (no vendor
lock-in; SPI peripheral model like SMS/email), **volume margin** (virgin systems, months of runway),
and **complexity discipline** (#14 beautiful problems). The compass framing was **corrected**
accordingly (the earlier "irony/misallocation" wording was retracted).

## The materialized vision

External immutable archival is a **Phase-2** capability via a **vendor-neutral SPI**: sealed,
cryptographically-linked audit batches are detached, exported to a WORM/object-lock store by a
peripheral adapter (`ezkey-archive-s3`, `ezkey-archive-cloudflare-r2` — currently placeholder/showcase
repos), then purged from the DB (`SEALED → EXPORTED → PURGEABLE`). Self-contained no-export remains a
fully supported mode.

## Actions taken

1. Created the values compass `integrity-assurance-honest-line.md` and **revised** it after the
   maintainer correction (Phase 1 vs Phase 2 split; deliberate-deferral framing; red-line test kept).
2. Created vision `V-2026-06-28-audit-archive-export-spi` and backlog idea
   `I-2026-06-28-audit-archive-export-spi` (`P3-future`, `incubating`); registered in indexes.
3. Created `docs/SECURITY_POSTURE.md` — discoverable, plain-language "does / does not / aims to do"
   honesty surface (intermediate visibility; not README/PRD).
4. Distilled **design principle #16** (external capabilities via SPI; no core vendor lock-in).
5. Wired cross-links across `V-2026-0004`, the integrity design pack, `PROJECT_POSITIONING.md`, and
   the vision/backlog indexes.

## Not in scope of this exercise

- Public-site surfacing (`sites/ezkey-org/trust.html`) — deferred by maintainer.
- Any implementation of the export SPI or its handoff protocol (Phase 2; design via a future TB).
- Pulling export into the September 2026 R1 critical path.

## Disposition

Reflection considered **complete** by the maintainer. Conclusions absorbed into canon (compass,
principle #16) and backlog (vision + idea). Committed on the integrity feature branch.
