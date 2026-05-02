# Admin UI — Design Decisions

## Purpose

This document records architecture and design decisions **scoped to the Admin UI**. Global decisions live in [`../../global/architecture-decisions.md`](../../global/architecture-decisions.md). Each entry follows the [architecture decision template](../../templates/architecture-decision.template.md).

## Index

| ID | Title | Status | Date |
|----|-------|--------|------|
| [ADR-UI-0001](#adr-ui-0001-token-in-session-storage) | Token in `sessionStorage`, never in `localStorage` | accepted | 2025-08-15 |
| [ADR-UI-0002](#adr-ui-0002-server-side-sort-for-paginated-lists) | Server-side sort for paginated lists | accepted | 2025-11-10 |
| [ADR-UI-0003](#adr-ui-0003-non-dismissible-dialogs-for-forms) | Non-dismissible dialogs for forms and critical state | accepted | 2025-12-05 |

## ADR-UI-0001 — Token in `sessionStorage`, never in `localStorage`

### Metadata

- **ID:** ADR-UI-0001.
- **Date:** 2025-08-15.
- **Status:** accepted.
- **Scope:** component:admin-ui.

### Context

An earlier prototype stored the bearer token in `localStorage` for convenience. That persists the token across tabs and browser restarts and increases exposure to cross-site scripting incidents. The Admin UI is an operator surface with direct access to sensitive lifecycle actions; its token handling must be strict.

### Decision

The bearer token, when the Admin UI uses token mode, is stored exclusively in `sessionStorage`. An optional **remember username** toggle may store only the non-secret username string in `localStorage` under `ezkey_admin_username_pref`.

### Alternatives Considered

- **Keep the token in `localStorage`.** Rejected — persists too long and exposes the token to other tabs.
- **Encrypt the token client-side.** Rejected — client-side encryption without an external secret provides no real protection and adds complexity.

### Consequences

- **Positive.** Tokens are cleared on tab close; no cross-tab leakage.
- **Negative.** Operators who close and reopen the tab must log in again.

### Impact

- All pages of the Admin UI.
- [`exception-and-error-model.md`](exception-and-error-model.md) — a 401 on a session-authenticated request clears the session.
- [`data-model-and-persistence.md`](data-model-and-persistence.md).

### Related Decisions

- Complements global [ADR-0003](../../global/architecture-decisions.md#adr-0003-rfc9457-as-external-error-contract) for 401 handling.

## ADR-UI-0002 — Server-side sort for paginated lists

### Metadata

- **ID:** ADR-UI-0002.
- **Date:** 2025-11-10.
- **Status:** accepted.
- **Scope:** component:admin-ui.

### Context

Early list views implemented client-side sort on the visible page. This quietly misrepresented sort intent: operators assumed "sort by name" applied to the full result set, but it only reordered the current page.

### Decision

For any paginated list (Spring Data Pattern B), the Admin UI always sends the `sort` parameter to the backend. The backend orders the global result set, and the UI paginates through it. Columns without backend sort support do not expose a `sortKey`.

### Alternatives Considered

- **Continue with client-side sort for UX simplicity.** Rejected — misleading behavior on paginated data.
- **Hybrid (client-side when server omits `sort` support).** Rejected — introduces inconsistent expectations across lists.

### Consequences

- **Positive.** Sort reflects the real intent of the operator.
- **Negative.** Slight latency cost on sort toggles (one additional request).

### Impact

- `usePaginatedQuery` and `usePaginatedFromOrval` hooks.
- Every list page.

## ADR-UI-0003 — Non-dismissible dialogs for forms and critical state

### Metadata

- **ID:** ADR-UI-0003.
- **Date:** 2025-12-05.
- **Status:** accepted.
- **Scope:** component:admin-ui.

### Context

Accidental dialog dismissal (backdrop click or Escape) caused data loss in create/edit flows and in flows that show a secret once (API key creation). The impact on operator trust is disproportionate to the UX cost.

### Decision

The shared `Dialog` component accepts a `dismissible` prop. Dialogs containing a form, a secret shown once, or a live test in progress use `dismissible={false}`. Only the explicit close button and the action buttons can close the dialog. Read-only detail dialogs and simple confirmations keep the default dismissible behavior.

### Alternatives Considered

- **Keep all dialogs dismissible.** Rejected — the data-loss cost is high for forms.
- **Confirm-before-dismiss pattern.** Rejected — noisy and still error-prone.

### Consequences

- **Positive.** No accidental data loss in write dialogs.
- **Negative.** Operators must use the explicit close button; a trivial affordance.

### Impact

- `Dialog` component and every create/edit screen.
- [`screens-and-wireflow.md`](screens-and-wireflow.md).
