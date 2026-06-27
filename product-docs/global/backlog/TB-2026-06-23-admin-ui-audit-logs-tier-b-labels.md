# Tracer Bullet Brief — `TB-2026-06-23` Audit logs Tier B selective name joins

## Metadata

- **ID:** `TB-2026-06-23-admin-ui-audit-logs-tier-b-labels`
- **Status:** `in_progress`
- **Related idea:** `I-2026-0028` (P2 audit logs row)
- **Lane:** `A` / `B` (Tier B list enrichment)
- **Posture:** `single-pass`
- **GitHub issue:** #258
- **GitHub branch:** `feat/admin-ui-audit-logs-tier-b`
- **Created at:** `2026-06-23`
- **Captured by:** Marc

## Objective

Replace raw FK IDs on the **audit logs** investigation surface with **selective batch joins**
(actor admin username, integration/enrollment/tenant display names) without N+1 queries per page.

## In scope

- `AuditLogResponseDto` optional enrichment fields (admin-api contract via core DTO)
- Batch enrichment on `GET /api/v1/audit-logs` and `GET /api/v1/audit-logs/{id}/context`
- `audit-logs.tsx` list **Admin** column + detail modal primary labels with links
- Mapper/controller unit tests
- Matrix audit logs row → `implemented`
- OpenAPI refresh when stack available

## Out of scope

- **P4 full removal** of `RelatedDetailsButton` on audit detail in this slice — see § Related details re-evaluation below
- Chain checkpoints table enrichment
- List column explosion (integration/enrollment stay detail-first unless filter context)

## Null enrichment fallback (FK present, label absent)

When the audit row has an FK (e.g. `integrationId`) but the optional enrichment field is **null**
(`integrationName` missing), that is **not** an API bug in the happy path. It means one of:

| Cause | Operator-visible behaviour |
|-------|----------------------------|
| Referenced entity **deleted** or never resolvable | Show `#id` — forensic anchor preserved |
| **Out of scope** for batch join (e.g. rare FK combo) | Same |
| Interim expand **succeeds** | « More details » fetches live GET and shows related row |
| Interim expand **404 / denied** | `#id` remains; no invented label |

**Posture (operability, pragmatism, clarity):** primary path is **server labels in list + detail**;
`#id` is honest when the name is gone; « More details » appears **only** when at least one FK still
lacks a label (edge-case recovery), not on every row.

**Target admin:** batch enrichment only in this slice; expand hook does not fetch `targetAdminId` —
if `targetAdminUsername` is null, show `#targetAdminId` (no expand for that FK).

## Related details re-evaluation (closeout gate — after this slice)

Canonical retirement program: [`I-2026-0028`](ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
§ *Related details retirement* (P4).

**After audit logs Tier B is merged and validated:**

1. Re-run grep `RelatedDetailsButton` / `useExpandableRelatedDetails` across Admin UI.
2. Check P4 criteria checklist — criterion **3** (audit logs names) should flip to **done**; criteria
   **1–2** already satisfied (Tier A + auth attempts).
3. Decide whether audit detail still needs the button in practice (expect: **rare**, only deleted
   entities). If other call sites (integration-detail, enrollment-detail, …) also cover daily paths
   with list/detail labels, **promote P4 TB** to remove the interim pattern site-by-site.
4. Update or remove `help.json` `patterns.fkRelatedDetails` when the last call site drops.

**This slice intentionally does not close P4** — it completes criterion 3 so the **next** program
decision is « remove vs keep minimal fallback », not « ship labels ».

## Column / display decision

| Surface | Change |
|---------|--------|
| List **Admin** | Username link; ID fallback |
| List **Time** | Keep absolute timestamp (`formatDateWithTimezone`); relative line optional secondary |
| Detail **Admin / target admin** | Username when enriched (add target row when `targetAdminId` set) |
| Detail **Integration / enrollment / tenant** | Name + link when enriched; ID as secondary; tenant row when `tenantId` present (GA-scoped list already filters tenant) |

## Join strategy (per page / context slice)

Collect distinct IDs from result set, then batch:

1. `EzkeyAdminRepository.findAllById` — `adminId`, `targetAdminId`
2. `EnrollmentRepository.findAllById` — `enrollmentId`
3. `IntegrationRepository.findAllByIdWithTenant` — `integrationId` + enrollment’s integration
4. `TenantRepository.findAllById` — `tenantId` when present on log row

Max four extra queries per page; no per-row fetches.

## Exit criteria

- List actor column shows admin username without expand-in-place fetch
- Detail modal shows integration/enrollment names when IDs present
- Maven admin-api tests + `npm run build` pass
- `I-2026-0028` audit logs row marked done on merge
