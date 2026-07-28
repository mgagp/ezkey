---
public: true
---
# Foundational reference canon is not "legacy", and does not always need to move

## Date

2026-07-28

## Context

The same triage session that produced
[`2026-07-28-legacy-documentation-default-gravity-to-adr.md`](2026-07-28-legacy-documentation-default-gravity-to-adr.md)
also surfaced a second, distinct gap: not every durable document is an ADR (a "why X over Y"
decision) or a task/idea. Two documents were named as clear examples of a third species: `docs/LIFECYCLE_GOVERNANCE.md`
(entity hierarchy, eligibility chain, reversible/irreversible action rules — over 400 lines) and the
cryptographic protocol trio `docs/CRYPTO.md`, `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`,
`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md` (what is signed, with what key, in what order, and why it
matters). Neither records a point decision; both describe a structuring model or protocol that a
reader must understand correctly to design or review anything in that area.

Inspection showed these documents are **not** stale or wrong. `product-docs/global/lifecycle-model.md`
and `architecture-overview.md` already treat them as authoritative — they are deliberately compact
summaries that defer to the deeper documents for full detail. The actual problem is vocabulary:
both summaries label the deferred-to documents "**Legacy**", and `GOVERNANCE.md` calls `docs/` the
"legacy `docs/` hub" as a blanket description. That word is actively misleading for this specific
content: it implies staleness or pending replacement, while `docs/README.md` itself already lists
`CRYPTO.md` as a numbered "Start Here" step and a "Core Reference" — `docs/README.md` never called
itself legacy. `LIFECYCLE_GOVERNANCE.md`, `ENROLLMENT_SIGNATURE_PAYLOAD.md`, and
`AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md` were simply missing from that same "Core References" list — a
real gap, but a small one.

A blast-radius check before proposing any physical move showed why relocation is the wrong fix here:
`docs/LIFECYCLE_GOVERNANCE.md` has roughly 26 referring files (manageable); the crypto trio has
**over 100**, including Java Javadoc, TypeScript source comments, a Dart SDK, Postman collections,
frozen OpenAPI spec snapshots, and `AGENTS.md` files in sibling repositories
(`ezkey_mobile`, `ezkey-crypto-api`, `didactic`). Forcing a physical move to satisfy "everything
canonical lives under `product-docs/`" would touch production source code across multiple
repositories to fix a labeling problem — a disproportionate response by the methodology's own value
of proportional rigor.

## Working assumptions

- A third document species exists alongside ADR (decision) and process/ceremony (task, idea, working
  diary): **foundational reference / model canon** — durable, descriptive documentation of a
  structuring domain model or protocol. Criterion: if this document were wrong or missing, would a
  reader make a structurally incorrect design decision on this topic? If yes, it is canon, not
  "legacy," regardless of which folder it physically sits in.
- Canonical status is a **declaration**, not a **physical location**. `GOVERNANCE.md` Core Rule 1
  ("one canonical location per concept") is satisfied by naming the existing path unambiguously in
  the Content Ownership Map — it does not require the file to live under `product-docs/`.
- Physical relocation is appropriate for **new** foundational reference documents (write them
  directly under `product-docs/global/` or a component pack) and remains available for existing ones
  when their reference count is low enough that migration cost stays proportional (as demonstrated
  today for `API_KEY_RATE_LIMIT_NOTE.md`-scale documents). It is not proportional when the referring
  set spans production source code and sibling repositories.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Physically move all foundational reference docs under `product-docs/`, update every referrer** | Uniform physical rule ("everything canonical lives in one tree") | Disproportionate for the crypto trio (100+ referrers, several outside this repository's control); high regression risk for a labeling problem |
| **B. Declare canonical status in place; fix mislabeling; register in `GOVERNANCE.md`; close the two real content gaps (missing index entries, duplicated diagram)** | Proportional; zero regression risk on the large referrer set; fixes the actual reported confusion (which document is real canon) | Canon is split across two physical trees (`product-docs/` and `docs/`); requires the Content Ownership Map to point outside `product-docs/` in a few rows |
| **C. Leave the "legacy" wording as-is; only add missing `docs/README.md` entries** | Minimal edit | Leaves the misleading "legacy" framing in `GOVERNANCE.md`, `lifecycle-model.md`, and `architecture-overview.md`, which is the actual complaint |

## Decision

Adopt **Option B**.

- `docs/LIFECYCLE_GOVERNANCE.md` and the crypto protocol trio are declared canonical **in place**,
  not "legacy." Product-docs summaries that defer to them use "detailed reference" or "canonical
  reference" wording, never "legacy."
- `GOVERNANCE.md`'s Content Ownership Map gains explicit rows for "Entity lifecycle governance
  (detailed reference)" and "Cryptographic protocol / signature payload reference," pointing at
  their real current paths under `docs/`.
- `docs/README.md`'s "Core References" section gains the two missing entries
  (`LIFECYCLE_GOVERNANCE.md`, plus the crypto trio already partially listed via `CRYPTO.md`).
- The one real duplication found (near-identical entity-hierarchy diagram in both
  `lifecycle-model.md` and `LIFECYCLE_GOVERNANCE.md`) is resolved by keeping the full diagram in the
  detailed reference and trimming the summary's copy to a short bullet list with a pointer — a
  self-contained, low-risk edit unrelated to the wider labeling fix.
- The blanket phrase "legacy `docs/` hub" in `GOVERNANCE.md`'s AI Agent Etiquette section is kept for
  the general fallback case (most of `docs/` really is an uncurated historical mix — see the
  `ADMIN_ZERO_*` precedent), but the specific documents named here are exempted from that framing by
  the new Content Ownership Map rows.

## Consequences

- Agents and humans reading `GOVERNANCE.md`'s Content Ownership Map can now find the real canonical
  location for entity lifecycle and crypto protocol questions without being told the answer is
  "legacy" (and without wrongly inferring product-docs' thinner summary is the full canon).
- No source code, sibling repository, or frozen spec file needed to change to fix this.
- The general "legacy hub" framing for the rest of `docs/` is unaffected — this decision narrows an
  exception, it does not repeal Core Rule 1 or invite indefinite `docs/` sprawl.

## Related documents

- [`2026-07-28-legacy-documentation-default-gravity-to-adr.md`](2026-07-28-legacy-documentation-default-gravity-to-adr.md) — companion decision on ADR extraction; that decision resolves the "is this a decision?" case, this one resolves the "is this a model/protocol reference?" case.
- [`../../GOVERNANCE.md`](../../GOVERNANCE.md)
- [`../../global/lifecycle-model.md`](../../global/lifecycle-model.md)
- [`../../global/architecture-overview.md`](../../global/architecture-overview.md)
- [`../../../docs/README.md`](../../../docs/README.md)
