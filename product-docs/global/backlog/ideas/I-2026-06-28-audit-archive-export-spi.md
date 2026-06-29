# Backlog Idea — `I-2026-06-28` Audit archive export SPI: sealed-batch detachment + vendor-neutral immutable retention

## Metadata

- **ID:** `I-2026-06-28`
- **Status:** `incubating`
- **Priority:** `P3`
- **Created at:** `2026-06-28`
- **Updated at:** `2026-06-28`
- **Last reviewed at:** `2026-06-28`
- **Phase tags:** `P3-future` (post-September-2026 R1)
- **Component tags:** `core`, `admin-api`, `admin-ui`, `audit`, `infra`, `docs`, peripheral (`ezkey-archive-s3`, `ezkey-archive-cloudflare-r2`)
- **Captured by:** Marc

## Intent

Implement the Phase-2 **external immutable archival** slice from
[`V-2026-06-28-audit-archive-export-spi`](../../vision/V-2026-06-28-audit-archive-export-spi.md):
when a cryptographically-linked audit batch is **sealed** (already shipped via
`AuditLifecycleService.sealArchive`), a **Service Provider Interface (SPI)** implemented by a
**separate peripheral project** exports the detached, self-contained batch to a **WORM / object-lock**
store and confirms it; the core then **purges** the exported range from the DB. This completes the
`SEALED → EXPORTED → PURGEABLE` transition the FSM already reserves. The core commits to **no storage
vendor**; reference adapters (S3, Cloudflare R2) live in peripheral repos.

## Problem and value

- **Problem:** Phase 1 integrity is bounded at the **host trust boundary** (HMAC key is off-DB, so a
  DB-only adversary and accidental corruption are detectable, but a host-rooted adversary is not).
  Audit data also accumulates in the DB indefinitely. Both are acceptable for Phase 1 but need a
  vendor-neutral answer at scale.
- **Expected value:** For **already-exported** windows, an immutable external copy bounds even a
  host-rooted adversary and enables off-box verification; DB stays lean via retention + purge. Core
  conceptual integrity preserved (no vendor lock-in); capability isolated in peripheral repos.

## Scope

- **In scope (when promoted):**
  - SPI contract: core → export handoff (notification + targeted invocation) → peripheral adapter →
    object-lock store; adapter confirms with `exportBundleDigest`.
  - Wire `confirmArchived` to a real export-confirmation flow; implement `EXPORTED → PURGEABLE` purge
    of detached, confirmed ranges.
  - Retention model: configurable DB window (e.g. 12 months) in cryptographically-linked slices;
    aged slices exported and purged.
  - Global Admin configures adapter endpoint + retention posture in config (`CONFIGURATION.md`).
  - Reference peripheral adapter(s): `ezkey-archive-s3`, `ezkey-archive-cloudflare-r2` (shell per
    `I-2026-0020`).
  - Off-box verification tool: re-check a published bundle digest against the chain without the live DB.
- **Out of scope:**
  - Any storage-vendor SDK in core platform code.
  - Auto-export without operator/retention policy; multi-adapter routing in the first slice.
  - Pulling this into the September 2026 R1 critical path.
  - Replacing the self-contained no-export mode (must remain a fully supported deployment posture).

## Key assumptions

- Phase 1 ships on virgin systems; months of runway before DB-resident volume forces export.
- Seal + nightly detection (`I-2026-0006`) are the Phase 1 integrity contract; this idea extends, not
  replaces, them.

## Risks and exceptions

- Export handoff protocol design is non-trivial (notification, idempotency, partial-failure, purge
  safety) — design via a dedicated TB, not ad hoc.
- Vendor neutrality must be enforced at the SPI boundary; resist convenience coupling to one provider.

## Promotion notes

Keep `incubating`. Promote to `ready` only when adoption/volume signals justify, and after the export
handoff protocol is sketched in a TB. Deliberate deferral — see the honest-line compass.

## Links

- Vision: [`../../vision/V-2026-06-28-audit-archive-export-spi.md`](../../vision/V-2026-06-28-audit-archive-export-spi.md)
- Compass: [`../../integrity-assurance-honest-line.md`](../../integrity-assurance-honest-line.md)
- Discussion / method log: [`../method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md`](../method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md)
- Parallel SPI precedent: `V-2026-0007`, `I-2026-0024` (SMS); `V-2026-0005`, `I-2026-0023` (email)
- Ecosystem repos: `I-2026-0020`
- Related integrity backlog: `I-2026-0005`, `I-2026-0006`, `I-2026-0007`
- Code anchor: `ezkey-core/.../AuditLifecycleService` (seal / eligibility / confirm), `AuditChainCheckpoint`
- Related principles: `#1`, `#2`, `#7`, `#12`, `#14`
