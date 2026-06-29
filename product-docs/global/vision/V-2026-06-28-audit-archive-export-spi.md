# V-2026-06-28 — Audit archive export SPI: vendor-neutral immutable retention and cryptographic batch detachment

- **Date:** `2026-06-28`
- **Status:** `under-review`
- **Intent:** Phase-2 orientation for **external immutable archival** of audit data, **without
  committing the Ezkey core to any storage vendor**. When a cryptographically-linked audit batch
  becomes archive-eligible, the core **seals** it (already shipped), then a **Service Provider
  Interface (SPI)** implemented by a **separate peripheral project** (e.g. `ezkey-archive-s3`,
  `ezkey-archive-cloudflare-r2`) performs the **export** of the detached, self-contained batch to a
  **WORM / object-lock** store and confirms it back to the core, which then **purges** the exported
  range from the DB. This realizes the `SEALED → EXPORTED → PURGEABLE` transition that the
  `AuditChainCheckpoint` FSM already reserves. **Self-contained by default:** an operator who never
  configures an export SPI is fully supported and simply accepts the host-boundary integrity limit,
  stated honestly.
- **Why deferred (Phase 1 stays as-is):** (1) **Conceptual integrity / autonomy** — opinionated
  no-vendor-lock-in stance, identical to the SMS/email peripheral-adapter model (`V-2026-0007`,
  `V-2026-0005`); (2) **volume margin** — Phase 1 ships on virgin systems; months of runway before
  DB-resident audit volume is a real concern; (3) **complexity discipline (#14 beautiful problems)**
  — build the export pipeline when real adoption forces it, not speculatively.
- **Security framing:** Phase 1 integrity is **detectable** (per-entry HMAC + checkpoint chain +
  nightly batch) and bounded at the **host trust boundary**. This export SPI is the move that, for
  **already-exported windows**, bounds even a host-rooted adversary by placing an immutable copy
  outside the host's mutable control. See the honest line / red-line compass
  [`../integrity-assurance-honest-line.md`](../integrity-assurance-honest-line.md).
- **Lifecycle / retention sketch (illustrative):** DB retains e.g. **12 months** of audit data in
  cryptographically-linked **monthly slices**; as each slice ages past the window, an SPI invocation
  exports it to an object-lock store with a long retention (e.g. **10 years**, append-only,
  non-erasable) and the slice is purged from the DB after confirmation. The seal HMAC and
  `exportBundleDigest` bind the external object to the chain.
- **SPI / protocol notes (to be designed in a later TB):** core remains authoritative for seal and
  purge; the **export handoff protocol** (notification + targeted invocation on an eligible sealed
  batch) is **future, to-be-determined**. Reference adapters are currently **placeholder/showcase
  repos** on GitHub announcing the storage SPI direction (alongside SMS SPI peripherals). No core
  code depends on any adapter.
- **Potential impact:** `ezkey-core` (`AuditLifecycleService` export confirmation + purge wiring),
  `admin-api` (SPI config + invocation surface), `admin-ui` (operator visibility of export/retention
  state), peripheral repos (`ezkey-archive-*`), `docs` (`SECURITY_POSTURE.md`, `CONFIGURATION.md`),
  `V-2026-0010` per-installation profile elaboration.
- **Next step:** keep `under-review`; promote the backlog idea
  [`../backlog/ideas/I-2026-06-28-audit-archive-export-spi.md`](../backlog/ideas/I-2026-06-28-audit-archive-export-spi.md)
  to `ready` only when volume/adoption signals justify, then design the export handoff protocol via a
  dedicated TB. Do **not** pull into the September 2026 R1 critical path.
- **Captured by:** Marc

## Related artifacts

- `V-2026-0004` — Integrity validation strategy (rolling attach + nightly detect); this vision is its
  Phase-2 external-immutability complement
- `V-2026-0007` — SMS integration SPI (peripheral-adapter precedent, no vendor lock-in)
- `V-2026-0005` — Email integration strategy (contrast: in-core)
- `V-2026-0012` — Dedicated batch backend (future) — adjacent peripheral-processing direction
- `I-2026-0020` — Integration ecosystem shell catalog & publish workflow (placeholder/showcase repos)
- `I-2026-06-28` — Audit archive export SPI (backlog idea, this vision's actionable slice)
- [`../integrity-assurance-honest-line.md`](../integrity-assurance-honest-line.md) — honest line / red-line compass
- [`../backlog/method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md`](../backlog/method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md) — discussion that produced this vision
- Code anchor: `ezkey-core/.../AuditLifecycleService` (seal / archive-eligibility / confirm FSM),
  `AuditChainCheckpoint` (`SEALED`/`EXPORTED`/`PURGEABLE` states already reserved)
- Related principles: `#1`, `#2`, `#7`, `#12`, `#14`
