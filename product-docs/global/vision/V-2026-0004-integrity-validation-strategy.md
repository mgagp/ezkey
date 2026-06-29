# V-2026-0004 — Integrity validation strategy: rolling windows, retroactive batches, dashboard transparency

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** Two-layer integrity strategy documented normatively: **(1) Rolling lookback**
  (default 60 min, configurable within **15 min–8 h**) — checkpoint scheduler **attaches** what
  is present; does not promise manipulation detection inside that window. **(2) Daily retroactive
  batch** (default **24 h** window) — full audit HMAC + checkpoint chain validation; anomalies →
  alert flow. Dashboard shows batch last-run via widgets (`I-2026-0007`), not
  alert-on-missing-batch. Apply **#14 beautiful problems** for scale optimizations (no audit-count
  cap in R1).
- **Signals:** Grilling D5 (2026-05-19) aligned operator model with `AuditChainScheduler`
  behavior. D4 C7–C9 settled rupture handling, snooze, and widget cut lines. Volume modulation
  and weekly/monthly mega-batches rejected for R1.
- **Potential impact:** `admin-api` (schedulers, properties), `audit`
  (`AuditChainVerificationService`), `admin-ui`, operator docs.
- **Next step:** Design pack → `TB-*` for cluster items. **Release order:**
  [`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md).
  See `integrity-cluster-D4-D6-grill-me.md`.
- **Captured by:** Marc

## Scope guard

The two-layer model (rolling attach + nightly detect) stays. "Remarkable solidity" against a
host-rooted adversary is explicitly relocated to a **future external immutable export boundary**,
delivered via a vendor-neutral SPI and **deliberately deferred** to Phase 2
([`V-2026-06-28-audit-archive-export-spi`](V-2026-06-28-audit-archive-export-spi.md)) — not to in-DB
checkpoint granularity or incident-response sophistication. Any new integrity control must pass the
red-line test in [`../integrity-assurance-honest-line.md`](../integrity-assurance-honest-line.md)
before it is treated as essential rather than accidental complexity. The honest Phase-1 posture is
documented in [`../../../docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md).

## Related artifacts

- `I-2026-0005` — Checkpoint integrity breaks: declared remediation and reattachment
- `I-2026-0006` — Nightly retroactive integrity validation batch
- `I-2026-0007` — Admin Dashboard: batch health and integrity widgets
- Grill session: `product-docs/global/backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md`
