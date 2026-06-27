# Stable detail dialog layout (Admin UI)

**Status:** Analysis and recommendations recorded; audit log detail is back to the **original** `lg` single-column modal (prev/next unchanged). See Changelog.

**Context:** Prev/next navigation in detail modals makes small height changes between items noticeable (“layout shift” in rapid succession). This note records UX guidance, a per-screen decision table, and concrete implementation options.

---

## UX / HCI perspective

**Is there an official guideline?** There is no single universal rule that *requires* fixed modal heights. However, several widely cited principles support reducing unnecessary size changes when the user performs a **repeated scanning task** (e.g. stepping through audit entries or keys):

1. **Stability and predictability** — Interfaces that keep frame and control positions stable reduce extraneous cognitive load (related to *change blindness* and *layout stability* concerns; web performance guidelines often cite Cumulative Layout Shift (CLS) for the same reason: unexpected movement disrupts reading).
2. **Gestalt / spatial memory** — Users anchor on the dialog’s outer frame and primary actions; large vertical jumps between “same kind” of item break that anchor.
3. **Modal patterns (Material, HIG-adjacent practice)** — Modals often use a **maximum** height with **internal scrolling** for variable body content, so the *chrome* (header, primary actions) stays stable even when body length varies.

**Practical strategy (recommended here):**

| Strategy | Use when | Trade-off |
|----------|----------|-----------|
| **Fixed `max-height` + scroll on body** | Long or highly variable fields (`<pre>`, JSON, reasons) | Always stable outer size; user scrolls inside |
| **Minimum height for variable blocks** | One or two fields drive most of the variance (e.g. optional “notes”) | Outer size stable for “typical” items; rare overflow can still grow or scroll |
| **Always show optional rows** | Optional field present only sometimes | Render row with “—” when empty so row count is constant |
| **Do nothing** | Full-page routes, or variance is negligible | Lowest effort |

**Target:** Aim for **stable dialog outer dimensions for the common case** (e.g. ~90% of rows), not mathematical zero variance for every edge case. Exceptionally long content should use **scroll** rather than growing the modal without bound.

---

## Per-screen decisions (operator input + product note)

| Area | Modal / page | Adjustment needed? | Notes |
|------|----------------|-------------------|--------|
| Tenants | Full page | **No** | Navigation is the full page; no modal height issue. |
| Integrations | Full page | **No** | Same. |
| Enrollments | Full page | **No** | Same. |
| Auth attempts | Modal | **No** | Only proof token varies at end; does not materially change layout. |
| Admins | Modal | **No** | Per operator review. |
| **Encryption keys** (key detail) | Modal | **Yes (candidate)** | **Notes** row is conditional (`keyData.notes`); when absent the dialog is shorter than when notes exist. Reserve space or always show the row to stabilize height. |
| Re-encryption batches | Modal | **No** | Per operator review. |
| **Audit logs** | Modal | **All attributes shown** | Same `lg` single-column layout; **every scalar field always listed** with `—` when empty. Reason / Details / Error use bordered blocks (`border border-fg/10`) with placeholder `—`. Related entities use server-side labels with `#id` fallback when enrichment is null. Prev/next unchanged. |

---

## Implementation recommendations (encryption key detail dialog)

**File:** `ezkey-admin-ui/src/pages/encryption-keys.tsx` — `KeyDetailDialog`

**Cause of variance:** Conditional block:

```tsx
{keyData.notes && <Row label={...}>...</Row>}
```

**Options (pick one; A is simplest):**

- **A — Always show Notes row** — Render `Row` for notes always; value `—` when `!keyData.notes`. Same number of rows every time.
- **B — Reserved minimum height** — Always render the notes `<dd>` with `className="min-h-[...]"` (tune to one line or two of text) so the row exists but empty notes still consume space.
- **C — Scrollable body** — Give the dialog body a `max-h-[...]` and `overflow-y-auto` so the *window* height caps; long notes scroll inside (may require a small `Dialog` enhancement if not already on the inner wrapper).

**Recommendation:** Start with **A** (always show Notes row). Add **C** only if notes are often long enough to warrant inner scroll without growing the modal.

---

## Traceability

- Parent UX plan: Prev/Next detail navigation (Admin UI) — modal + full-page patterns.
- This document: follow-up on **visual stability** during prev/next, scoped by screen.

When implementation is done for `KeyDetailDialog`, update the **Status** line at the top and add a one-line changelog entry below.

### Changelog

- **Audit log detail (`AuditLogDetailDialog`):** Min-height / full-row experiment reverted; then xl + 2-column grid reverted — **back to original `lg` modal** (single column). Unused `Dialog` size `xl` removed from `dialog.tsx`.
- *TBD — KeyDetailDialog: stable layout for notes row (or equivalent).*
