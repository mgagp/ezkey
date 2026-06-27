# Tracer Bullet Brief — `TB-2026-06-23` Retire « More details » (RelatedDetailsButton)

## Metadata

- **ID:** `TB-2026-06-23-admin-ui-retire-related-details`
- **Status:** `ready`
- **Related idea:** `I-2026-0028` (P4 row)
- **Lane:** `A` / `B` (UI removal + doc hygiene)
- **Posture:** `single-pass`
- **GitHub issue:** _(optional — open when branch starts)_
- **Created at:** `2026-06-23`
- **Captured by:** Marc (post–audit logs Tier B closeout)

## Objective

Remove the interim **« More details / Plus de détails »** pattern from Admin UI detail surfaces now
that Tier A/B list and detail DTOs carry operator labels. Delete dead code and scrub active canon
references; accept `#id` when enrichment is null (deleted entity) without on-demand expand fetch.

## Prerequisites (met)

- `I-2026-0028` § Related details retirement criteria **1–3** — done (Tier A lists, auth attempts,
  audit logs #259).
- Hygiene closeout on `main` (TB audit logs `done`, matrix audit logs `implemented`).

## In scope

### UI — remove call sites (grep `RelatedDetailsButton`)

| Page | Replacement |
|------|-------------|
| `auth-attempts.tsx` | Labels already on modal — remove button + hook |
| `integration-detail.tsx` | Show `integration.tenantName` + link inline |
| `api-key-detail.tsx` | Integration label/link from DTO + lookup |
| `admins.tsx` (detail dialog) | Enrollment link `/enrollments/{id}`; tenant already inline |
| `enrollment-detail.tsx` | `integrationName` / `tenantName` on GET; `adminListDetailHref` for created-by |
| `audit-logs.tsx` | Remove conditional button + expand rows; keep `#id` fallback only |

### Code deletion

- `related-details-button.tsx`
- `use-expandable-related-details.ts`
- `related-details-ui.ts` + test
- i18n `common.detail.moreDetails*` (if unused)
- `help.json` `patterns.fkRelatedDetails`; `HelpPatternId` in `help-topics.ts`

### Documentation (active canon only)

- `I-2026-0028` — P4 row **Done**; retirement section → shipped
- `I-2026-0014` — remove interim pattern paragraph
- `docs/admin-ui/DETAIL_DIALOG_STABLE_LAYOUT.md` — drop More details references
- `ezkey-admin-ui/AGENTS.md` — if any Related details mention remains

## Out of scope

- P3 matrix rows (encryption keys, alerts, chain checkpoints)
- Playwright suite expansion beyond one optional smoke (criteria 5 — pragmatic spot-check or skip with manual summary)
- Editing archived plans under `.cursor/plans/archived/` (historical only)

## Exit criteria

- `rg RelatedDetailsButton` / `useExpandableRelatedDetails` → zero in `ezkey-admin-ui/src`
- `npm run build` pass
- Manual smoke: integration detail, enrollment detail, audit log detail (happy path labels visible)
- `I-2026-0028` P4 marked **Done**

## Links

- Parent: [`I-2026-0028`](ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
- Prior slice: [`TB-2026-06-23-admin-ui-audit-logs-tier-b-labels.md`](TB-2026-06-23-admin-ui-audit-logs-tier-b-labels.md)
- Closeout: [`method-logs/ML-2026-06-23-admin-ui-audit-logs-tier-b-closeout.md`](method-logs/ML-2026-06-23-admin-ui-audit-logs-tier-b-closeout.md)
