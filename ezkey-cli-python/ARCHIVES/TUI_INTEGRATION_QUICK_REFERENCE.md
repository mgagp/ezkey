# Ezkey TUI - Integration Management: Quick Reference

## Screen Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ HOME SCREEN                                                     │
│ (Dashboard with stats)                                          │
│ Press 'i' → Integrations Screen                                │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ INTEGRATIONS SCREEN (List)                                      │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ ID  │ Name          │ Active │ Tenant  │ Created            │ │
│ ├─────┼───────────────┼────────┼─────────┼────────────────────┤ │
│ │ 1   │ Acme Corp     │ ✓      │ tenant1 │ 2025-01-15 10:30   │ │
│ │ 2   │ Global Inc    │ ✗      │ tenant2 │ 2025-01-14 14:45   │ │
│ │ 3   │ Tech Solutions│ ✓      │ tenant1 │ 2025-01-13 09:15   │ │
│ └─────┴───────────────┴────────┴─────────┴────────────────────┘ │
│ Page 1 / 4 · Total 100                                          │
│                                                                  │
│ Bindings:                                                        │
│ [c] Create  [f] Filter  [r] Refresh  [n] Next  [p] Prev        │
│ [h] Home    [q] Quit                                            │
│                                                                  │
│ Actions:                                                         │
│ • Enter → View Details                                          │
│ • 'c' → Create New Integration                                  │
│ • 'f' → Open Filter Modal                                       │
│ • 'r' → Refresh List                                            │
│ • 'n'/'p' → Navigate Pages                                      │
└─────────────────────────────────────────────────────────────────┘
             │                                    │
      Press Enter               Press 'f'        Press 'c'
             │                    │                 │
             ▼                    ▼                 ▼
    ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
    │ DETAIL SCREEN    │  │ FILTER MODAL     │  │ CREATE MODAL     │
    │                  │  │                  │  │                  │
    │ ID: 1            │  │ Name filter:     │  │ Name:  [______]  │
    │ Name: Acme Corp  │  │ [ACME________]   │  │ Desc:  [______]  │
    │ Active: ✓        │  │                  │  │ Logo:  [______]  │
    │ ...              │  │ ☐ Active Only    │  │                  │
    │                  │  │                  │  │ [Create] [Cancel]│
    │ [d] Delete       │  │ [Apply] [Clear]  │  └──────────────────┘
    │ [e] Edit (TODO)  │  │ [Cancel]         │
    │ [r] Refresh      │  └──────────────────┘
    │ [h] Back         │
    └──────────────────┘
             │
      Press 'd' to Delete
             │
             ▼
    ┌──────────────────────────────┐
    │ CONFIRMATION MODAL           │
    │                              │
    │ Delete Integration           │
    │                              │
    │ Are you sure you want to     │
    │ delete 'Acme Corp'?          │
    │ This action cannot be undone.│
    │                              │
    │ [Confirm] [Cancel]           │
    └──────────────────────────────┘
```

## Keyboard Quick Reference

### Integrations Screen (List)
| Key | Action | Effect |
|-----|--------|--------|
| **c** | Create | Open create modal with form |
| **f** | Filter | Open filter modal (name + active) |
| **r** | Refresh | Reload list from API |
| **n** | Next | Go to next page |
| **p** | Prev | Go to previous page |
| **Enter** | Select | Open detail screen for selected row |
| **h** | Home | Return to home screen |
| **q** | Quit | Exit application |

### Detail Screen
| Key | Action | Effect |
|-----|--------|--------|
| **d** | Delete | Delete integration (with confirmation) |
| **e** | Edit | Edit integration (TODO) |
| **r** | Refresh | Reload integration details |
| **h** / **Esc** | Back | Return to integrations list |
| **q** | Quit | Exit application |

## Filter Modal Workflow

```
Press 'f' on Integrations Screen
         │
         ▼
FilterModal opens with current filters
         │
         ├─ Name field (text input)
         │  └─ Enter partial name: "ACME"
         │
         ├─ Active checkbox
         │  └─ Check for "Active Only"
         │
         ├─ [Apply] button
         │  └─ Refresh list with filters
         │  └─ Reset to page 1
         │  └─ Close modal
         │
         ├─ [Clear] button
         │  └─ Remove all filters
         │  └─ Show all integrations
         │  └─ Close modal
         │
         └─ [Cancel] button
            └─ Close without changes
```

## Delete Confirmation Workflow

```
Press 'd' on Detail Screen
         │
         ▼
Extract integration name from i18n
         │
         ▼
ConfirmationModal opens
         │
         ├─ Title: "Delete Integration"
         │
         ├─ Message: "Are you sure you want to
         │  delete 'Integration Name'?
         │  This action cannot be undone."
         │
         ├─ [Confirm] button (RED)
         │  └─ API: DELETE /api/v1/integrations/{id}
         │  └─ Show: "✓ Integration deleted..."
         │  └─ Wait: 1 second
         │  └─ Return: Back to Integrations list
         │  └─ List auto-refreshes
         │
         └─ [Cancel] button
            └─ Close without deletion
            └─ Integration remains
```

## API Calls Made

### Create Integration
```
POST /api/v1/integrations
Content-Type: application/json
Authorization: Bearer {token}

{
  "i18n": [
    {
      "language": "en",
      "name": "Integration Name",
      "description": "Integration Description"
    }
  ],
  "logo": "https://example.com/logo.png"
}

Response: 201 Created
{
  "id": 123,
  "tenantId": "tenant1",
  "active": true,
  "createdAt": "2025-01-15T10:30:00Z",
  "i18n": [...],
  "logo": "..."
}
```

### Get Integrations (with filters)
```
GET /api/v1/integrations?page=0&size=25&integrationName=ACME&active=true
Authorization: Bearer {token}

Response: 200 OK
{
  "content": [
    {
      "id": 1,
      "tenantId": "tenant1",
      "active": true,
      "createdAt": "2025-01-15T10:30:00Z",
      "i18n": [
        {
          "language": "en",
          "name": "Acme Corp",
          "description": "..."
        }
      ]
    },
    ...
  ],
  "page": {
    "number": 0,
    "totalPages": 4,
    "totalElements": 100
  }
}
```

### Delete Integration
```
DELETE /api/v1/integrations/{id}
Authorization: Bearer {token}

Response: 204 No Content
```

## State Variables

### IntegrationsScreen
```python
self.current_page = 0          # Current page (0-indexed)
self.page_size = 25            # Items per page
self.total_pages = 0           # Total pages from API
self.total_elements = 0        # Total items from API
self.filters = {}              # Active filters: {"name": "ACME", "active": True}
```

### IntegrationDetailScreen
```python
self.integration_id = 123      # ID of integration being viewed
self.integration_data = {}     # Full integration object from API
```

## Error Scenarios & Recovery

| Scenario | Behavior | Recovery |
|----------|----------|----------|
| **Filter with no results** | Empty table shown | Clear filter with 'f' → Clear button |
| **Delete fails (API error)** | Error message shown | Press 'r' to try again |
| **API connection lost** | "No API client available" | Check connection, restart TUI |
| **Invalid token** | 401 Unauthorized from API | Login again with 'ezkey auth login' |
| **Delete accidentally pressed** | Confirmation modal appears | Click Cancel button |

## Testing Checklist

- [ ] Create integration (fill form, click Create)
- [ ] Verify integration appears in list
- [ ] Open detail screen (Enter on row)
- [ ] Refresh details (press 'r')
- [ ] Try edit (press 'e' - should show TODO)
- [ ] Filter by name (press 'f', enter "ACME", click Apply)
- [ ] Verify filtered results
- [ ] Clear filter (press 'f', click Clear)
- [ ] Delete integration (press 'd', review confirmation, click Confirm)
- [ ] Verify integration removed from list
- [ ] Test cancel on delete (press 'd', click Cancel, verify still exists)
- [ ] Navigate pages (press 'n' and 'p')
- [ ] Return to home (press 'h')
- [ ] Quit application (press 'q')

