# Integrity Assurance — Honest Line and Complexity Compass

## Metadata

- **Document ID:** `integrity-assurance-honest-line`
- **Status:** `active`
- **Created at:** `2026-06-28`
- **Last reviewed at:** `2026-06-28`
- **Revision note:** `2026-06-28` — corrected the export framing from "irony/misallocation" to a
  deliberate, principled Phase 1 deferral (vendor-neutral SPI, volume margin, autonomy); added the
  Phase 1 vs Phase 2 split and the honest-posture-doc action.
- **Owner:** Marc (maintainer reflection captured below)
- **Discussion / method log:** [`backlog/method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md`](backlog/method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md)
- **Kind:** Values compass / scope guard (normative for the audit-integrity cluster)
- **Companion compass:** [`vision/V-2026-09-26-public-alpha-posture-closeout.md`](vision/V-2026-09-26-public-alpha-posture-closeout.md)
  (live); historical Waves:
  [`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md)
- **Governs scope of:** [`V-2026-0004`](vision/V-2026-0004-integrity-validation-strategy.md),
  [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md),
  `I-2026-0005` / `I-2026-0006` / `I-2026-0007`

## Why this exists

The audit-integrity cluster is growing: rolling checkpoints, a heartbeat/degraded-mode guard,
gap declaration, a nightly retroactive batch, a rupture classifier, and a planned
manipulation-remediation flow. This document is a **deliberate, cold cost/benefit line** so that
future work on audit integrity does not drift into benevolent **escalation of commitment** —
security effort that keeps growing while its marginal return on the **actual target market (SMEs)**
plateaus or regresses.

It answers one question: **for an SME-targeted, self-hosted, opinionated MFA product, where does
integrity work stop buying real, verifiable security and start buying an illusion of it?**

It is normative: a proposed integrity control that fails the [red-line test](#the-red-line-test-the-compass)
is presumed **accidental complexity** (principles #1, #2, #14 in
[`design-principles.md`](design-principles.md)) until argued otherwise.

## The honest question (restated)

> As long as the chain, the per-entry HMACs, and the validations all live **inside the database**,
> is the protection real or illusory? Is the complexity essential or accidental? Are we protecting
> against an honest risk, or escalating commitment to a security narrative beyond what the target
> market needs?

## Where the trust boundary actually is today

The protection is **neither illusory nor remarkable**: it is **real against one adversary class and
structurally blind to another.** The deciding fact is *where the HMAC key lives*.

- The audit HMAC key is held in a **key file** (`ezkey.audit.integrity.hmac-key-file`), **separate
  from the database** and separate from the Tink encryption master key
  (`AuditHmacService`, `AuditHmacProperties`).
- Therefore an attacker who controls **only the database** cannot forge valid HMACs — they can
  delete, alter, or reorder rows, but the chain will **not** re-verify, and the
  nightly batch detects it.
- An attacker who controls **the host** (reads the key file *and* the database) can rewrite history
  **and** recompute the entire chain silently. **No in-database mechanism defeats this — ever.**

| Threat / fault | What it can do | Covered by HMAC chain + nightly detection? |
|---|---|---|
| **Accidental corruption** (bug, disk fault, bad replication) | Alters rows without re-signing | **Yes — genuine tamper-evidence.** |
| **DB-only adversary** (SQL injection, stolen Postgres credentials, rogue DBA, no host access) | Deletes/alters/reorders rows; **cannot forge a valid HMAC** | **Yes — the nightly batch (`I-2026-0006`) detects the rupture. This is the real value.** |
| **Host-rooted adversary** (reads key file + DB) | Rewrites history *and* recomputes the whole chain, silently | **No. Undetectable in-DB. Only an external, already-published immutable record bounds this — and only for windows already exported.** |

**Conclusion:** in-DB integrity is real protection, but **bounded** at exactly the line the
maintainer's intuition identified. The bound is the host trust boundary.

## The export boundary is a deliberate Phase 1 deferral (not a gap)

> **Analysis correction (2026-06-28).** An earlier draft framed the unbuilt export as an "irony" and
> a possible misallocation signal. That framing was wrong and is retracted. The maintainer confirmed
> the deferral is **intentional and principled**. What remains true — and is the durable lesson — is
> only that **Phase 1's integrity ceiling is bounded at the host trust boundary**, and that this must
> be stated honestly rather than implied away.

A protocol for external archival already exists in `AuditLifecycleService`:
`sealArchive()` → operator exports out-of-band → `confirmArchived()` records an `exportBundleDigest`.
The code is explicit that the **actual export is intentionally out of scope for now**:
`getArchiveEligibility()` *"does not materialize an export bundle"*; `confirmArchived()` *"does not
perform the export itself … future archival workflow."* The finite-state machine deliberately
reserves the `SEALED` → `EXPORTED` → `PURGEABLE` transition for that future mechanism.

This is a **conscious architectural decision**, justified by three project values:

1. **Conceptual integrity and autonomy (opinionated, self-contained).** Ezkey commits to **no
   external vendor** for core function — not for SMS, not for email, not for archival storage. It
   will not hard-couple the core to AWS S3, Cloudflare R2, or any single provider. External
   capabilities arrive through a **Service Provider Interface (SPI)** implemented by separate
   peripheral projects, exactly like the SMS/email peripheral-adapter model (`V-2026-0007`,
   `V-2026-0005`). A **self-contained operator who never exports is a fully supported mode** — they
   simply accept the host-boundary limit, stated honestly.
2. **Volume margin.** Every Phase 1 deployment starts on a **virgin system with zero data**. Even a
   significant SME would take **months** to accumulate audit volume where DB-resident retention is a
   real concern. There is no near-term operational forcing function for export.
3. **Complexity discipline.** Building a real, provider-neutral, cryptographically-detached export
   pipeline now is large accidental complexity for a problem that does not yet exist — a textbook
   **beautiful problem (#14)** to let manifest before solving.

The Phase 2 vision is captured in
[`V-2026-06-28-audit-archive-export-spi.md`](vision/V-2026-06-28-audit-archive-export-spi.md) and the
backlog idea [`I-2026-06-28-audit-archive-export-spi.md`](backlog/ideas/I-2026-06-28-audit-archive-export-spi.md):
sealed, cryptographically-linked audit batches are detached, exported to a WORM/object-lock store
via an SPI peripheral, then purged from the DB — completing the entity lifecycle.

**The corrected crux:** the host-boundary limit is real, but the right Phase 1 response is **not** to
rush an external dependency. It is to **be honest about it in discoverable documentation** (what
Phase 1 does, does not, and aims to do) and to **keep in-DB incident-response machinery proportional**
— because that machinery also sits on the DB side a host adversary already owns, and assumes an
operator persona the SME does not have.

## How the credible references actually earn "remarkable solidity"

HashiCorp Vault's audit backend (already cited in `AuditHmacService` Javadoc), Certificate
Transparency, and Trillian / Rekor / Sigstore do **not** earn end-to-end trust through ever-finer
in-database detection. They earn it through **external verifiability**: append-only logs, third-party
witnesses, and **published digests the log operator itself cannot retroactively rewrite.**

The lesson for Ezkey is unambiguous: **one notch of immutable external export is worth ten notches
of internal detection refinement.**

## Verdict — keep / finish / cap

| Control | Verdict | Rationale |
|---|---|---|
| Per-entry HMAC, key off-DB | **Keep** (already shipped, near-free) | Genuine tamper-evidence vs accident + DB-only adversary. |
| **Nightly retroactive batch** (`I-2026-0006`) | **Finish — the detective minimum** | Without it, the HMACs are write-only decoration. It is what makes the chain *honest*. |
| Immutable external export via **SPI** (object-lock / append-only + cryptographic batch detachment + purge) | **Phase 2, deliberately deferred** (not a Phase 1 gap) | The move that *would* change the adversary class — but bound to no vendor, reserved in the FSM, and gated behind real volume. Vision: `V-2026-06-28-audit-archive-export-spi`. |
| 5-minute granularity, heartbeat→manipulation classifier elaboration, full conciliation/remediation UI, snooze (`I-2026-0005`, parts of `I-2026-0007`) | **Cap / keep proportional** | Defends mostly the DB side a host adversary already owns, **and** assumes a forensic-analyst operator the SME does not have. Regressive ROI in Phase 1. |
| **Honest, discoverable posture doc** (what Phase 1 does / does not / aims to do) | **Build now — the real Phase 1 leverage** | With export deliberately deferred, *documentation honesty* is the highest-ROI integrity move available today. See [`docs/SECURITY_POSTURE.md`](../../docs/SECURITY_POSTURE.md). |
| Positioning / marketing claims | **Reframe** | Claim "tamper-**evident**, with a planned off-box verifiable export," never "tamper-proof." (Principle #5: no certification narratives.) |

## Phase 1 vs Phase 2 (honest scope split)

| Capability | Phase 1 (now) | Phase 2 (if adoption justifies) |
|---|---|---|
| Per-entry HMAC, off-DB key | **Yes** | Yes |
| Checkpoint chain + rolling attach | **Yes** | Yes |
| Heartbeat / degraded mode | **Yes** | Yes |
| Nightly retroactive detection | **Yes** (`I-2026-0006`) | Yes |
| Seal / archive-eligibility FSM | **Yes** (bookkeeping only) | Yes |
| External immutable export + DB purge | **No — deferred by design** | **Yes, via vendor-neutral SPI** (`V-2026-06-28-audit-archive-export-spi`) |
| Detection ceiling | DB-only adversary + accidental corruption | + bounds the host adversary for exported windows |

**Honest one-liner:** *Phase 1 delivers cryptographically **detectable** integrity against accidental
corruption and DB-level tampering, on a fully self-contained system; Phase 2 adds vendor-neutral
external immutability when real volume and adoption justify the added moving parts.*

## The red-line test (the compass)

A proposed integrity control **earns its complexity only if it passes all three**:

1. **Threat realism for the target SME.** It addresses accidental corruption, a single rogue insider
   with DB access, or opportunistic DB compromise — **not** a patient, persistent, host-rooted
   adversary.
2. **Actionable by a non-specialist.** It either (a) **changes the adversary class or detection
   capability**, or (b) produces a signal a Global Admin can resolve in minutes. It is **not** a
   control that only **refines the classification** of an already-detected anomaly for a forensic
   analyst who does not exist in an SME.
3. **No phantom external assurance.** If immutability depends on an external store, **that store and
   its turnkey export must exist before** the surrounding in-DB machinery is considered complete.
   Otherwise, build the export first.

**Failing any one test means the red line is crossed:** the complexity is **accidental**, not
essential, per principles #1 / #2 / #14.

## Positioning honesty (claims we may and may not make)

| May claim | Must not claim |
|---|---|
| "Tamper-**evident** audit chain with off-host verifiable export." | "Tamper-proof" / "immutable" while the export is unbuilt. |
| "Detects accidental corruption and DB-level tampering via nightly cryptographic validation." | "Detects any tampering, including by a compromised host." |
| "Pragmatic, opinionated, self-hosted; stronger than passwords and classic TOTP for backend-oriented contexts." | Formal attestation chains, SOC 2 equivalence, or certification-grade assurance. |

This mirrors the public-site editorial posture: *do not overstate security assurance; treat
complexity judgments as the project's perception, not objective claims.*

## Implications for the current backlog

- **`I-2026-0006` (nightly batch):** keep on the critical path — it is the detective minimum that
  makes everything else meaningful.
- **`I-2026-0005` (manipulation remediation / conciliation UI):** **scope down**. R1 should
  raise/touch the rupture alert and point the operator to a documented response; a full forensic
  remediation UI is **not** SME-justified until evidence demands it.
- **`I-2026-0007` (dashboard widgets):** keep the "3-second ok/not-ok" health snapshot; defer
  finer widgetry.
- **Export SPI (Phase 2):** materialized as vision `V-2026-06-28-audit-archive-export-spi` and
  backlog idea `I-2026-06-28-audit-archive-export-spi`. **Do not** pull it into Phase 1 on the basis
  of the host-boundary argument alone — the deferral is a deliberate, principled call (autonomy,
  volume margin, complexity discipline).
- **Honest posture doc (Phase 1, now):** `docs/SECURITY_POSTURE.md` states what Ezkey does, does
  not, and aims to do — the highest-ROI integrity move while export is deferred.
- **`V-2026-0004`:** scope-guard note links here; the two-layer model stays, and "remarkable
  solidity" is explicitly relocated to the **future export boundary (SPI)**, not to in-DB
  granularity.

## Traceability

| Artifact | Role |
|---|---|
| [`V-2026-0004`](vision/V-2026-0004-integrity-validation-strategy.md) | Two-layer integrity strategy this compass scopes |
| [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md) | Wave B design pack; "Out of scope" should reference this line |
| [`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md) | Historical Waves A–C + freeze D release-order; complementary *complexity* compass is this doc |
| [`vision/V-2026-09-26-public-alpha-posture-closeout.md`](vision/V-2026-09-26-public-alpha-posture-closeout.md) | Live public alpha posture closeout |
| [`design-principles.md`](design-principles.md) | #1 simplicity, #2 essential vs accidental, #5 operator-first / no certification narrative, #14 beautiful problems |
| [`docs/ALERTS.md`](../../docs/ALERTS.md) | Runtime alert reference (`AUDIT_INTEGRITY_RUPTURE`, etc.) |
| `AuditHmacService` / `AuditLifecycleService` | Code evidence: off-DB key; seal/export stub |

## Review cadence

- Wave B exit and 2026-08-01 mid-horizon checks are **past**. Integrity-cluster R1 is closed.
- **Supersede, do not silently edit**, if the threat model or target market changes (e.g. a move
  from laboratory / public-alpha posture toward regulated/enterprise customers with a real security team).
