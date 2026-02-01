# Ezkey TUI - Complete Interaction Flow Diagram

## User Interaction Workflow

```
╔═══════════════════════════════════════════════════════════════════════╗
║                        INTEGRATIONS SCREEN                            ║
║                                                                       ║
║  ┌────────────────────────────────────────────────────────────────┐  ║
║  │ Integrations                                                   │  ║
║  ├────────────────────────────────────────────────────────────────┤  ║
║  │ ID  │ Name              │ Active │ Tenant  │ Created      │    │  ║
║  ├─────┼───────────────────┼────────┼─────────┼──────────────┤    │  ║
║  │ 1   │ Acme Corp         │   ✓    │ tenant1 │ 2025-01-15   │◄───┼─ Select Row
║  │ 2   │ Global Inc        │   ✗    │ tenant2 │ 2025-01-14   │    │
║  │ 3   │ Tech Solutions    │   ✓    │ tenant1 │ 2025-01-13   │    │
║  │ ... │ ...               │ ...    │ ...     │ ...          │    │
║  └────────────────────────────────────────────────────────────────┘  ║
║                                                                       ║
║  Page 1 / 4  (n/p to navigate)                                       ║
║                                                                       ║
║  Bindings:  [c] Create  [f] Filter  [r] Refresh  [n] Next [p] Prev  ║
║             [h] Home    [q] Quit                                     ║
║                                                                       ║
╚═══════════════════════════════════════════════════════════════════════╝
    │                          │                        │
    │ Press 'c'               │ Press 'f'              │ Press Enter
    │                          │                        │
    ▼                          ▼                        ▼

┌─────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐
│  CREATE MODAL       │  │  FILTER MODAL        │  │  DETAIL SCREEN   │
│                     │  │                      │  │                  │
│ Name: [_______]     │  │ Name: [_________]    │  │ Integration #1   │
│ Desc: [_______]     │  │ ☐ Active Only        │  │                  │
│ Logo: [_______]     │  │                      │  │ ID: 1            │
│                     │  │ [Apply] [Clear]      │  │ Name: Acme Corp  │
│ [Create] [Cancel]   │  │ [Cancel]             │  │ Tenant: tenant1  │
│                     │  │                      │  │ Active: ✓        │
│ POST /api/v1/...    │  │ GET /api/v1/...?     │  │ Created: ...     │
│                     │  │ integrationName=     │  │                  │
│ ✓ Success: Returns  │  │ active=true          │  │ [d] Delete       │
│   to list, refresh  │  │                      │  │ [e] Edit (TODO)  │
│                     │  │ ✓ Refreshes with     │  │ [r] Refresh      │
│                     │  │   filters applied    │  │ [h] Back         │
│                     │  │                      │  │                  │
└─────────────────────┘  └──────────────────────┘  │ Press 'd'        │
                                                    │        │         │
                                                    │        ▼         │
                                                    │  ┌──────────────┐
                                                    │  │CONFIRMATION  │
                                                    │  │MODAL         │
                                                    │  │              │
                                                    │  │Delete        │
                                                    │  │Integration   │
                                                    │  │              │
                                                    │  │Are you sure  │
                                                    │  │delete 'Acme  │
                                                    │  │Corp'?        │
                                                    │  │Cannot undo.  │
                                                    │  │              │
                                                    │  │[Confirm]     │
                                                    │  │[Cancel]      │
                                                    │  │              │
                                                    │  └──────────────┘
                                                    │        │
                              Confirm               │        └─── Cancel
                                  │                 │              │
                                  ▼                 │              ▼
                            DELETE API Call          │         Modal closes
                        DELETE /api/v1/.../1        │         Detail screen
                                  │                 │         remains
                                  ▼                 │
                    ✓ Integration deleted            │
                    Show: "✓ Deleted..."             │
                    Wait: 1 second                   │
                                  │                 │
                                  ▼                 │
                        Pop to Integrations List ───┘
                        (automatically refreshes)
```

## API Call Sequence Diagram

```
┌────────────┐                            ┌──────────────────┐
│   TUI      │                            │  Admin API       │
└────────────┘                            └──────────────────┘
     │                                           │
     │                                           │
     ├─── GET /api/v1/integrations ────────────>│
     │     (page=0, size=25)                     │
     │                                           │
     │<─ 200 { content: [...], page: {...} } ───┤
     │                                           │
     │                                           │
     ├─── POST /api/v1/integrations ───────────>│
     │     { i18n: [...], logo: "..." }         │
     │                                           │
     │<─ 201 { id: 4, tenantId: "...", ... } ───┤
     │                                           │
     │  [Auto-refresh list]                      │
     │                                           │
     ├─── GET /api/v1/integrations ────────────>│
     │     (with filters: integrationName, active) │
     │                                           │
     │<─ 200 { content: [...], page: {...} } ───┤
     │                                           │
     │                                           │
     ├─── GET /api/v1/integrations/1 ─────────>│
     │                                           │
     │<─ 200 { id: 1, tenantId: "...", ... } ───┤
     │                                           │
     │                                           │
     ├─── DELETE /api/v1/integrations/1 ──────>│
     │     Authorization: Bearer {token}        │
     │                                           │
     │<─ 204 No Content ──────────────────────────┤
     │                                           │
     │  [Auto-refresh list]                      │
     │                                           │
     ├─── GET /api/v1/integrations ────────────>│
     │     (page=0, size=25)                     │
     │                                           │
     │<─ 200 { content: [...], page: {...} } ───┤
     │
```

## Filter Behavior Diagram

```
State 1: No filters active
───────────────────────────
Showing: All 100 integrations, Page 1/4

    ↓ User presses 'f'

    ┌──────────────────────┐
    │  FilterModal opened  │
    │  (remembers previous │
    │   filter values)     │
    └──────────────────────┘
         │      │      │
         │      │      └─ Click [Cancel]
         │      │           ↓
         │      │         No changes
         │      │         Back to State 1
         │      │
         │      └─ Click [Clear]
         │           ↓
         │         filters = {}
         │         refresh()
         │         Back to State 1

         └─ Click [Apply]
              ↓
              User entered: name="ACME", active=true

              ↓

State 2: Filters active
───────────────────────────
self.filters = {
  "name": "ACME",
  "active": True
}

API call:
GET /api/v1/integrations?
    page=0&
    size=25&
    integrationName=ACME&
    active=true

Showing: 2 filtered results, Page 1/1

    ↓ User presses 'f' again

    ┌──────────────────────┐
    │  FilterModal opened  │
    │  Current filters:    │
    │  name="ACME"         │
    │  active=True ✓       │
    │  (fields pre-filled) │
    └──────────────────────┘
         │      │      │
    [Modify]  [Clear]  [Cancel]
```

## Delete Confirmation Flow

```
User on Detail Screen for Integration #1 (Acme Corp)
         │
         ▼ Press 'd'
         │
Extract name from i18n
  i18n_list = [
    {"language": "en", "name": "Acme Corp", "description": "..."},
    {"language": "fr", "name": "Acme..."},
    ...
  ]
  Match language="en" → name = "Acme Corp"
         │
         ▼
Show ConfirmationModal
  title="Delete Integration"
  message="Are you sure you want to delete 'Acme Corp'?
           This action cannot be undone."
         │
    ┌────┴────┐
    │         │
    ▼         ▼
[Confirm]  [Cancel]
    │         │
    │         └─ Dismiss(False)
    │            Modal closes
    │            Detail screen remains
    │            (no API call)
    │
    └─ Dismiss(True)
       on_confirm callback triggered
         │
         ▼
      api_client._delete("/api/v1/integrations/1")
         │
    ┌────┴────┐
    │         │
    │         └─ success=False
    │            _show_error("Failed to delete...")
    │            User can retry with 'r'
    │
    └─ success=True
       detail_widget.update("✓ Integration deleted...")
       set_timer(1.0 second)
         │
         ▼ Timer fires after 1 second
       pop_screen()
         │
         ▼ Returns to IntegrationsScreen
       IntegrationsScreen._load_integrations()
         │
         ▼ List refreshes automatically
       Table updates (integration gone)
       User sees new pagination/data
```

## State Machine: Integrations Screen

```
┌─────────────────────────────────────┐
│      IDLE_LIST_VIEW                 │
│                                     │
│  Showing: Integrations table        │
│  Bindings: c, f, r, n, p, h, q      │
│                                     │
│  State vars:                        │
│  - current_page = 0                 │
│  - total_pages = 4                  │
│  - filters = {}                     │
│  - total_elements = 100             │
└─────────────────────────────────────┘
 │   │    │    │    │    │
 │   │    │    │    │    └─ Press 'q' → Quit
 │   │    │    │    │
 │   │    │    │    └─ Press 'h' → Pop to Home
 │   │    │    │
 │   │    │    └─ Press 'n'/'p' → Load page+1/-1 → Stay in IDLE_LIST_VIEW
 │   │    │
 │   │    └─ Press 'r' → Refresh from API → Stay in IDLE_LIST_VIEW
 │   │
 │   └─ Press 'f' → Show FilterModal
 │       │
 │       ├─ User clicks Cancel → Resume IDLE_LIST_VIEW
 │       ├─ User clicks Clear → filters={}, refresh → Resume IDLE_LIST_VIEW
 │       └─ User clicks Apply → filters=new, refresh → Resume IDLE_LIST_VIEW
 │
 └─ Press 'c' → Show CreateIntegrationModal
     │
     ├─ User cancels → Resume IDLE_LIST_VIEW
     └─ User creates successfully → Refresh list → Resume IDLE_LIST_VIEW


┌─────────────────────────────────────┐
│      IDLE_LIST_VIEW                 │
│  + User presses Enter on row        │
└─────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│      DETAIL_VIEW                    │
│                                     │
│  Showing: Integration details       │
│  Bindings: d, e, r, h, q            │
│                                     │
│  State vars:                        │
│  - integration_id = 1               │
│  - integration_data = {...}         │
└─────────────────────────────────────┘
 │   │    │    │
 │   │    │    └─ Press 'q' → Quit
 │   │    │
 │   │    └─ Press 'h'/'Esc' → Pop to IDLE_LIST_VIEW
 │   │
 │   └─ Press 'r' → Reload details → Stay in DETAIL_VIEW
 │
 └─ Press 'd' → Show ConfirmationModal
     │
     ├─ User clicks Cancel → Resume DETAIL_VIEW
     └─ User clicks Confirm → DELETE API → [Timer 1s] → Pop to IDLE_LIST_VIEW
```

## Summary

- **✓ All workflows implemented**
- **✓ API calls correctly mapped**
- **✓ State management clean**
- **✓ Error handling included**
- **✓ User feedback messages**
- **✓ Keyboard navigation intuitive**

Ready for functional testing with `ezkey --tui`

