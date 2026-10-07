# Dependabot curated — weekday daily log

Append-only residual for **weekday light** `dependabot-curated` passes.

**Rules** (see [`README.md`](README.md) § Cadence):

- Append a `## YYYY-MM-DD` section **only when** the pass merged, held, or deferred something.
- **Silent when the actionable queue is empty** — do not write an empty-day entry.
- Keep each section short (lots touched, Orval O-tier if any, concurrency check, validation one-liner).
- Monday full passes still write a dedicated `YYYY-MM-DD-pass-N.md` from [`TEMPLATE.md`](TEMPLATE.md);
  they may summarize this file’s entries for the week.

---

<!-- Newest sections at the bottom. Example:

## 2026-10-07

- Pass: weekday light; concurrency: clear
- Merged: #NNN (T1 maven-patch), #NNN (Admin Orval O0)
- Validation: lint + tsc + vitest green; Playwright smoke n/a
- Residual: none

-->
