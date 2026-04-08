---
name: Reason UX Review
overview: Analyze audited reason/justification entry across the Admin UI/Admin API, prioritize day-to-day operator workflows, and produce a pragmatic recommendation for quick-pick reason suggestions that preserve free-text editing. The plan will also compare common industry patterns and assess whether the suggestion lists should live in the UI or move to a shared/backend source later.
todos:
  - id: inventory-reason-surfaces
    content: Document the current day-to-day UI/API reason-entry surfaces and note mismatches or inconsistencies.
    status: completed
  - id: define-quick-pick-shortlists
    content: Propose compact FR/EN quick-pick suggestion sets per entity/operation, optimized for ordinary daily operations.
    status: completed
  - id: compare-industry-patterns
    content: Summarize comparable admin-console patterns and explain why a lightweight editable suggestion model fits Ezkey best.
    status: completed
  - id: recommend-ownership-model
    content: Recommend UI-only ownership now, with clear criteria for when a shared or backend-driven catalog would become justified later.
    status: completed
isProject: false
---

# Reason Input UX Review Plan

## Goal

Define a pragmatic UX direction for audited `reason` / `justification` entry in Ezkey’s day-to-day admin workflows: keep free-text flexibility, add a small set of plausible quick-pick suggestions, and avoid accidental backend complexity unless evidence justifies it.

## Scope

Phase 1 focuses on daily operator workflows, not specialized audit-integrity workflows.

In scope now:

- Tenant lifecycle in `[C:\github\ezkey\ezkey-admin-ui\src\pages\tenant-detail.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\tenant-detail.tsx)`
- Admin lifecycle in `[C:\github\ezkey\ezkey-admin-ui\src\pages\admins.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\admins.tsx)`
- Enrollment lifecycle in `[C:\github\ezkey\ezkey-admin-ui\src\pages\enrollment-detail.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\enrollment-detail.tsx)`
- Integration lifecycle in `[C:\github\ezkey\ezkey-admin-ui\src\pages\integration-detail.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\integration-detail.tsx)`: bulk enrollment actions (deactivate-all / reactivate-all / revoke-all), **integration retirement** (`POST /api/v1/integrations/{id}/retire` with optional `reason`), and **delete** (only after retirement, optional `reason`)
- API key revoke in `[C:\github\ezkey\ezkey-admin-ui\src\pages\api-keys.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\api-keys.tsx)`

Out of phase-1 implementation scope, but mention as appendix / future consideration:

- Encryption key rotation in `[C:\github\ezkey\ezkey-admin-ui\src\pages\encryption-keys.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\encryption-keys.tsx)`
- Audit seal / declare gap in `[C:\github\ezkey\ezkey-admin-ui\src\pages\audit-logs.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\audit-logs.tsx)`

## Validation against recent commits (last ~6 days)

A scan of `git log --since="6 days ago"` shows **material impact on this analysis** mainly in these areas:

- **Integration retirement and lifecycle** (`feat(integration-lifecycle): …`, e.g. `1391737a`): New **`POST /api/v1/integrations/{id}/retire`** with optional audited `reason` (min 10 when provided); deletion semantics tightened (delete only after retirement, no enrollments). Admin UI gained a **Retire** danger dialog alongside existing bulk/delete flows in `integration-detail.tsx`. The analysis inventory and quick-pick shortlists must explicitly include **retire** (not only bulk revoke/deactivate/reactivate/delete).
- **Bulk enrollment no-op semantics** (`feat(bulk-operations): …`, e.g. `aa357320`): Structured `affectedCount` / `noOp` responses and clearer operator feedback. This does **not** add new reason fields but affects how operators perceive “did something happen?” next to optional reasons — worth a short UX note when writing copy around confirmations.
- **Encryption key lifecycle / re-encryption / semantics docs** (`465fc604`, `8ff3daea`, `2e620a8e`, `c60394cd`, etc.): Heavy backend and documentation churn around keys and re-encryption. **Does not change the core “reason on rotate” story**, but operators reading `encryption-keys.tsx` may need vocabulary aligned with updated semantics; keep as **appendix** unless phase 1 scope expands.
- **Admin UI API error i18n** (`8c8290c5`, `93adc578`, `8fca8b26`): Better localized presentation of validation errors (including `reason` / field errors). **Reinforces** the need for consistent client-side validation (min 10, trim) so users see predictable messages — not a new reason surface.
- **Other** (recovery codes, phone fields, Playwright, navigation): Tangential to reason-entry taxonomy; no new global “reason code” model introduced.

**Docs:** `[C:\github\ezkey\docs\ENDPOINT.md](C:\github\ezkey\docs\ENDPOINT.md)` now documents integration retire/delete with optional `reason`. **`AUDIT_REASON_JUSTIFICATION_COHERENCE.md`** should still be checked for parity with **retire** and with optional `reason` on **admin activate** (ENDPOINT example for activate may still be minimal vs code).

## Current-State Findings To Capture

- The UI already centralizes demo-only quick reasons in `[C:\github\ezkey\ezkey-admin-ui\src\lib\demo-mode.ts](C:\github\ezkey\ezkey-admin-ui\src\lib\demo-mode.ts)`, but the current presets are generic and demo-oriented rather than operation-specific.
- The reusable badge component lives in `[C:\github\ezkey\ezkey-admin-ui\src\components\feature\demo-reason-badges.tsx](C:\github\ezkey\ezkey-admin-ui\src\components\feature\demo-reason-badges.tsx)`.
- The API already supports audited `reason` on most lifecycle operations; specialized audit-chain flows use `justification` instead.
- **New since the original plan:** optional audited `reason` on **integration retirement**; integration listing/filtering includes retired integrations (`includeRetired`, lifecycle status). Quick-pick suggestions for integrations should distinguish **retire** (application sunset, migration, consolidation) from **delete** (exceptional permanent removal after cleanup).
- There is a doc/code drift to note: current code supports admin activation reason, while `[C:\github\ezkey\docs\ENDPOINT.md](C:\github\ezkey\docs\ENDPOINT.md)` (activate admin example) and `[C:\github\ezkey\docs\AUDIT_REASON_JUSTIFICATION_COHERENCE.md](C:\github\ezkey\docs\AUDIT_REASON_JUSTIFICATION_COHERENCE.md)` may still lag on some rows — reconcile during the analysis write-up.

Useful implementation signals worth citing in the analysis:

```90:133:C:/github/ezkey/ezkey-admin-ui/src/pages/integration-detail.tsx
const [reason, setReason] = useState('');

const showReason = requireReason || optionalReason;
const reasonInvalid = optionalReason && reason.length > 0 && reason.length < 10;
const disabled = requireReason ? reason.length < 10 : reasonInvalid;

<Input
  id="danger-reason"
  value={reason}
  onChange={(e) => setReason(e.target.value)}
  placeholder={reasonPlaceholder ?? 'Justification...'}
/>
{renderReasonBadges?.(setReason)}
```

```319:337:C:/github/ezkey/ezkey-admin-ui/src/lib/demo-mode.ts
export interface ReasonDemoPreset {
  id: string;
  en: string;
  fr: string;
}

export const reasonDemoPresets: ReasonDemoPreset[] = [
  { id: 'routine-rotation', en: 'Routine key rotation', fr: 'Rotation de clé de routine' },
  { id: 'scheduled-rotation', en: 'Scheduled key rotation', fr: 'Rotation planifiée des clés' },
  // ...
];
```

## Analysis Deliverables

- Build an inventory matrix of audited reason-entry surfaces by entity, operation, optional vs required, and operator frequency.
- For each in-scope entity, propose a very short list of high-probability quick-pick phrases in English and French.
- Separate “common operational reasons” from “specialized / exception” reasons so the UI stays compact.
- Recommend the interaction pattern: keep the current text field, add a nearby dropdown or compact suggestion control that injects text into the field, then allow editing.
- Compare this pattern with industry norms:
  - mainstream admin consoles often record actor/time/action without forcing structured reasons for routine operations;
  - compliance-heavy or workflow-heavy tools more often use reason codes plus notes;
  - therefore Ezkey should prefer a lightweight suggestion layer, not a rigid controlled taxonomy.
- Recommend source-of-truth ownership for suggestion lists:
  - default recommendation: UI-owned static config, localized in the UI, because the need is UX acceleration rather than authoritative business reference data;
  - future option: shared UI/backend reference only if multiple clients must reuse the same catalog, policy ownership moves server-side, or analytics/reporting begin depending on normalized reason codes.

## Recommended Direction To Test In The Analysis

- Use operation-specific suggestion lists, not one global list reused everywhere.
- Keep each list intentionally small, around 3 to 6 suggestions.
- Prefer neutral, reusable phrasing that an operator can accept as-is or lightly edit.
- Do not introduce backend APIs, DB tables, or reference-data management for phase 1.
- Treat backend-driven lists as a later evolution path, not the starting point.

## Candidate grouping for the analysis

- Tenant and admin lifecycle: offboarding, temporary suspension, access restored, policy change, role or organization change.
- Enrollment lifecycle: device replaced, lost device, suspected compromise, user request, duplicate/obsolete enrollment.
- Integration lifecycle:
  - **Retire integration** (new high-signal path): application sunset, migration to another integration, vendor/app decommission, consolidation, end of pilot — distinct from day-to-day bulk tweaks.
  - Bulk enrollment actions: maintenance window, security review, tenant request, cleanup of inactive enrollments (align with no-op messaging when nothing changes).
  - **Delete integration** (after retirement): exceptional permanent removal, archival complete, confirm no remaining enrollments.
- API key revoke: key rotation completed, suspected exposure, service decommissioned, environment cleanup.

## Risks / caveats to address

- Avoid turning suggestions into a hidden mandatory taxonomy when the backend still audits free text.
- Keep FR/EN parity and avoid demo-style examples that feel artificial in production.
- Normalize validation and copy in the analysis notes, because current flows are not perfectly consistent on optional hints, trimming, and inline validation behavior.
- Explicitly call out that specialized audit-integrity justifications should probably remain separate from daily operational quick-picks.

