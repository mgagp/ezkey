# Method log — `ML-2026-06-23` Admin UI retire Related details (P4)

## Metadata

- **ID:** `ML-2026-06-23-admin-ui-retire-related-details-closeout`
- **TB:** [`TB-2026-06-23-admin-ui-retire-related-details`](../TB-2026-06-23-admin-ui-retire-related-details.md)
- **Parent:** [`I-2026-0028`](../ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md) P4
- **Date:** `2026-06-23`
- **Lane:** hygiene / program closeout (bounded UI removal)

## What shipped

1. Removed `RelatedDetailsButton`, `useExpandableRelatedDetails`, `related-details-ui` (+ test) from
   `ezkey-admin-ui`.
2. Six call sites now show inline labels/links from DTOs; `#id` when server enrichment is null.
3. Removed `help.json` `patterns.fkRelatedDetails` and simplified Help context (topic-only).
4. i18n: dropped `common.detail.moreDetails*` and unused related-detail label keys.
5. Updated `DETAIL_DIALOG_STABLE_LAYOUT.md`, `I-2026-0028`, `I-2026-0014`, TB → `done`.

## Validation

- `npm run build` in `ezkey-admin-ui` — pass
- `rg RelatedDetailsButton|useExpandableRelatedDetails` in `ezkey-admin-ui/src` — zero
- Browser tests: not extended (presentation/workflow simplification; manual smoke recommended)

## Manual smoke (clean-start)

1. **Integration detail** — tenant row shows name + link (or `#id`).
2. **Enrollment detail** — integration, tenant, created-by admin link visible without extra click.
3. **Audit log detail** — admin/integration/enrollment/tenant labels or `#id`; no « More details » button.

## Residual

- P3 matrix rows (encryption keys, re-encryption batches, alerts, chain checkpoints) remain `draft`.
