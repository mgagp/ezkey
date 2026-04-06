# Re-encryption baseline measurement (manual checklist)

Use this **before and after** pipeline changes to compare throughput and operator-visible progress.

## Environment

- Stack: Docker (or target deployment), Admin API + DB.
- Approximate row counts per `(table, column)` still on old key prefix.

## Metrics to capture

1. **End-to-end time** — `POST .../reencrypt/trigger` (or scheduler run) start to last batch `COMPLETED` (from Admin UI or `ezkey_reencryption_batch`).
2. **Rows per wall-clock minute** — `(sum records_done delta) / minutes` for the heaviest batch(es), or total rows migrated / duration.
3. **Concurrent churn** (optional) — background enrollment bumps / auth traffic while re-encryption runs; note any `FAILED` batches or retries.
4. **Operator perception** — refresh encryption-keys batches table every ~30s: does `records_done` / `progress_pct` move visibly?

## Notes

- Baseline numbers are **approximate**; the goal is trend comparison, not lab-grade benchmarking.
