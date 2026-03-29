---
name: Prev/Next Detail Navigation
overview: Add Previous/Next navigation (with left/right arrow keyboard shortcuts) to all detail views in the admin UI, starting with audit logs, then auth attempts, then rolling the pattern uniformly across all entities.
implementationStatus: completed
implementationTested: true
archivedNotes: See "Post-implementation notes" at end of file.
todos:
  - id: phase1-hook
    content: Create reusable useDetailNavigation hook (keyboard ←/→ bindings)
    status: completed
  - id: phase1-audit
    content: Implement Prev/Next navigation in AuditLogDetailDialog with index-based state
    status: completed
  - id: phase1-locales
    content: Add prevEntry / nextEntry / keyboardHint locale keys for audit-logs (en + fr)
    status: completed
  - id: phase2-auth
    content: Replicate Prev/Next pattern for AttemptDetailDialog (auth-attempts)
    status: completed
  - id: phase3-modal
    content: Apply pattern to AdminDetailDialog and encryption key dialogs
    status: completed
  - id: phase3-fullpage
    content: Implement router-state-based Prev/Next for full-page detail views (tenants, integrations, enrollments, API keys)
    status: completed
isProject: false
---

# Prev/Next Detail Navigation — Admin UI UX Plan

## Context & Pattern Analysis

This is a well-established UX pattern called **detail-within-list navigation** (seen in Gmail, GitHub issue lists, Datadog logs, Splunk events). The premise: when a user is in investigation mode, they should never need to close a detail view to move to the next item. Prev/Next + keyboard shortcuts (←/→) dramatically reduce the click-path for sequential review.

### Current architecture — two distinct detail paradigms

**Modal-based detail** (no route change):

- Audit logs → `AuditLogDetailDialog` driven by `selectedLog` state
- Auth attempts → `AttemptDetailDialog` driven by `selectedAttempt` state
- Admins → `AdminDetailDialog` driven by `selectedAdmin` state
- Encryption keys → `KeyDetailDialog` / `BatchDetailDialog` driven by local state

**Full-page detail** (route change, list context lost):

- Tenants → `/tenants/:id`, TenantDetailPage
- Integrations → `/integrations/:id`, IntegrationDetailPage
- Enrollments → `/enrollments/:id`, EnrollmentDetailPage
- API keys → `/api-keys/:id`, ApiKeyDetailPage

---

## Entity-by-Entity Analysis


| Entity              | Detail type | Investigation pattern           | Prev/Next value | Effort |
| ------------------- | ----------- | ------------------------------- | --------------- | ------ |
| **Audit logs**      | Modal       | Sequential log review — classic | Very high       | Low    |
| **Auth attempts**   | Modal       | Sequential failure review       | Very high       | Low    |
| **Admins**          | Modal       | Rarely sequential; small lists  | Low             | Low    |
| **Encryption keys** | Modal       | Operational, not investigative  | Very low        | Low    |
| **Tenants**         | Full page   | Occasional sequential review    | Medium          | Medium |
| **Integrations**    | Full page   | Occasional sequential review    | Medium          | Medium |
| **Enrollments**     | Full page   | Could check multiple in a sweep | Medium          | Medium |
| **API keys**        | Full page   | Rarely sequential               | Low             | Medium |


### Recommendation

**Uniform rollout** — same UX pattern everywhere, for these reasons:

- Cognitive consistency: users expect the same behavior on all list screens.
- For modals, the added cost per entity after the first is ~15 min of work.
- For full-page views, the pattern is slightly more complex but still reusable.
- The only genuine exception is **encryption keys** (very few records, operational flow — but implementing it there is still harmless).

---

## Implementation Design

### Modal-based entities (audit logs, auth attempts, admins, encryption keys)

**State change** — track an index instead of a record:

```tsx
// Before
const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

// After
const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
const selectedLog = selectedIndex !== null ? data[selectedIndex] ?? null : null;
```

**Dialog contract** — add four optional props to each detail dialog:

```tsx
interface AuditLogDetailDialogProps {
  log: AuditLog | null;
  onClose: () => void;
  onPrev?: () => void;
  onNext?: () => void;
  hasPrev?: boolean;
  hasNext?: boolean;
}
```

**Keyboard hook** — one reusable hook `useDetailNavigation` in `src/hooks/`:

```tsx
// src/hooks/use-detail-navigation.ts
export function useDetailNavigation(
  isOpen: boolean,
  { onPrev, onNext, hasPrev, hasNext }: NavOptions
) {
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft' && hasPrev) onPrev?.();
      if (e.key === 'ArrowRight' && hasNext) onNext?.();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isOpen, hasPrev, hasNext, onPrev, onNext]);
}
```

**Dialog header** — add Prev/Next chevron buttons between the title and the X close button. Show arrow hint (`← →`) as keyboard shortcut hint when both are available.

### Full-page entities (tenants, integrations, enrollments, API keys)

**Approach** — pass ordered ID list as React Router navigation state:

```tsx
// In tenants.tsx onRowClick
navigate(`/tenants/${row.tenantId}`, {
  state: {
    ids: data.map(r => r.tenantId),
    currentIndex: data.findIndex(r => r.tenantId === row.tenantId),
  },
});
```

**In detail page** — read location state and render Prev/Next buttons in `AppShell` header area (next to breadcrumb):

```tsx
const location = useLocation();
const { ids, currentIndex } = location.state ?? {};
const prevId = ids?.[currentIndex - 1];
const nextId = ids?.[currentIndex + 1];
// navigate to prevId/nextId preserving the same state (with updated currentIndex)
```

Keyboard shortcuts registered via the same `useDetailNavigation` hook (always active on full-page views).

---

## Rollout Phases

### Phase 1 — Audit logs (this session)

Files to change:

- `[ezkey-admin-ui/src/pages/audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx)` — switch `selectedLog` → `selectedIndex`; wire prev/next handlers
- `[ezkey-admin-ui/src/pages/audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx)` — add Prev/Next buttons + keyboard hint in `AuditLogDetailDialog` header
- New: `[ezkey-admin-ui/src/hooks/use-detail-navigation.ts](ezkey-admin-ui/src/hooks/use-detail-navigation.ts)` — keyboard hook
- Locales `en/audit-logs.json` and `fr/audit-logs.json` — add `"prevEntry"`, `"nextEntry"`, `"keyboardHint"` keys

### Phase 2 — Auth attempts (after Phase 1 validated)

- `[ezkey-admin-ui/src/pages/auth-attempts.tsx](ezkey-admin-ui/src/pages/auth-attempts.tsx)` — same index pattern, reuse hook
- Locales for `auth-attempts`

### Phase 3 — Uniform rollout (all remaining entities)

Modal entities (admins, encryption keys) — each is a small, self-contained change.

Full-page entities (tenants, integrations, enrollments, API keys) — share a single utility function that builds the navigation state, and a shared `DetailNavBar` component rendered inside `AppShell`.

---

## Visual UX Notes

- Prev/Next buttons: small icon-only chevron buttons (`ChevronLeft` / `ChevronRight` from Lucide, already in the stack) placed left of the X close button in modal headers.
- Disabled state when at first/last record.
- Keyboard hint: small muted label `← →` visible in the header when shortcuts are active.
- Page boundary: navigation is scoped to the **current page**. When the user reaches the last item of the page and next-page records exist, show a muted "End of page" hint rather than auto-fetching (avoids surprising behavior).
- Full-page: Prev/Next appear as a compact nav bar above the breadcrumb, or inline in the breadcrumb row.

---

## Implementation status

**This plan is fully implemented and manually verified** (admin UI build, spot checks while navigating list → detail → prev/next, including keyboard shortcuts). All phased todos above are **completed**.

---

## Post-implementation notes (navigation & detail UX refinements)

These items were done **after** the core prev/next rollout; they stabilize the experience when stepping through records (especially audit log detail).

### Shared navigation building blocks

- **`useDetailNavigation`** — keyboard ←/→ when a detail surface is open (modals and full-page).
- **`DetailDialogHeaderNav`** / **`DetailPageNav`** — consistent chevron controls and optional keyboard hint copy.
- **`useListDetailPageNavigation`** + **`list-detail-navigation`** helpers — router state (`ids` + `currentIndex`) for full-page entities; navigation preserves list context across prev/next.

### Audit log detail dialog — layout stability

- **`Dialog` size `lg-wide`** — wider modal for audit detail without changing base `lg` elsewhere; documented in `Dialog` and `AGENTS.md`.
- **Scalar fields** — optional fields always rendered (placeholder `—` where empty) so the body does not jump between events.
- **Reason / Details / Error / User-Agent** — fixed minimum heights and wrapping (`pre` + `min-h-*`, `break-words`, no horizontal scrollbar) so block heights stay predictable when paging prev/next.
- **“More details” (foreign-key expansion)** — the top-right control is **always laid out**: when there are no expandable FKs, an **invisible, non-interactive** twin button (same `Button` variant/size/label as the default visible state) reserves identical space. This removes the small vertical shift that occurred when the real button appeared or disappeared between audit entries with/without related FKs.

### Documentation

- **`docs/admin-ui/DETAIL_DIALOG_STABLE_LAYOUT.md`** — traces iterations on stable audit detail layout (width, fields, blocks, FK row).

