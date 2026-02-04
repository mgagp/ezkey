# Ezkey CLI - Integration Management Implementation

## Overview

This document describes the complete **Delete + Filter** integration for the Integrations management screen in the TUI (Terminal User Interface).

## Features Implemented

### 1. Filter Search (Press 'f')

**Location:** `ezkey_cli/tui/screens/integrations.py` → `action_filter()`

**Workflow:**
```
Press 'f' → FilterModal opens
  ├─ Name field (partial match, e.g., "ACME")
  ├─ Active checkbox (filter to active integrations only)
  ├─ [Apply] button → Search with filters + reset to page 1
  ├─ [Clear] button → Remove all filters
  └─ [Cancel] button → Close without changes
```

**Implementation Details:**
- Filters are stored in `self.filters` dict
- When "Apply" is pressed:
  - `self.filters` is updated with new filter values
  - `self.current_page` is reset to 0
  - `_load_integrations()` is called with filter params
  - Table updates with filtered results
- When "Clear" is pressed:
  - `self.filters` is set to `{}`
  - List refreshes to show all integrations
- When "Cancel" is pressed:
  - No changes applied, modal closes

**API Parameters Sent:**
```python
api_client.get_integrations(
    page=self.current_page,
    size=self.page_size,
    name=self.filters.get("name"),  # e.g., "ACME"
    active=self.filters.get("active")  # True if "Active Only" checked
)
```

**Backend Mapping:**
- `name` parameter → API query param: `integrationName`
- `active` parameter → API query param: `active`

---

### 2. Delete with Confirmation (Press 'd')

**Location:** `ezkey_cli/tui/screens/integration_detail.py` → `action_delete()`

**Workflow:**
```
Detail Screen: Press 'd' → ConfirmationModal shows
  ├─ Title: "Delete Integration"
  ├─ Message: "Are you sure you want to delete 'Integration Name'?"
  ├─ [Confirm] button (red) → DELETE /api/v1/integrations/{id}
  └─ [Cancel] button → Close without deletion

On Confirm:
  ├─ API DELETE call executed
  ├─ Success message: "✓ Integration deleted. Returning to list..."
  ├─ 1 second delay
  └─ Pop back to IntegrationsScreen (which auto-refreshes)

On Failure:
  └─ Error message shown: "❌ Error: Failed to delete integration {id}"
```

**Implementation Details:**
- Integration name is extracted from `i18n` list (English preferred)
- `_perform_delete()` method handles the API call
- Modal callback determines if user confirmed
- Uses `self.app.set_timer()` to delay return to list
- IntegrationsScreen auto-refreshes when popped

**API Endpoint:**
```
DELETE /api/v1/integrations/{integration_id}
Response: 204 No Content (success) or error
```

---

## Updated Files

### 1. `ezkey_cli/tui/screens/integrations.py`

**Changes:**
- Added `Binding("f", "filter", "Filter")` to BINDINGS
- Added `self.filters = {}` in `__init__()` to store active filters
- Updated `_load_integrations()` to pass filter params:
  ```python
  response = api_client.get_integrations(
      page=self.current_page,
      size=self.page_size,
      name=self.filters.get("name"),
      active=self.filters.get("active")
  )
  ```
- Added `action_filter()` method:
  - Opens FilterModal with current filters
  - Updates filters on apply
  - Resets pagination to page 0
  - Calls `_load_integrations()` with new filters

**Keyboard Bindings:**
| Key | Action | Description |
|-----|--------|-------------|
| 'f' | filter | Open filter modal |
| 'c' | create | Create new integration |
| 'r' | refresh | Refresh list |
| 'n' | next_page | Next page |
| 'p' | prev_page | Previous page |
| 'h' | show_home | Back to home |
| 'q' | quit | Quit application |

---

### 2. `ezkey_cli/tui/screens/integration_detail.py`

**Changes:**
- Implemented `action_delete()` method:
  - Extracts integration name from i18n
  - Shows ConfirmationModal with title and message
  - Calls `_perform_delete()` on confirmation
- Added `_perform_delete()` method:
  - Calls `api_client._delete(url)`
  - Shows success/error message
  - Sets 1-second timer to pop screen on success

**Keyboard Bindings:**
| Key | Action | Description |
|-----|--------|-------------|
| 'd' | delete | Delete integration (with confirmation) |
| 'e' | edit | Edit integration (TODO) |
| 'r' | refresh | Refresh details |
| 'h' / 'esc' | back | Return to list |
| 'q' | quit | Quit application |

---

### 3. `ezkey_cli/tui/screens/filter_modal.py`

**File:** Already created, no changes needed

**Features:**
- Name input field (partial match)
- Active checkbox (filter to active integrations)
- Apply/Clear/Cancel buttons
- Accepts `current_filters` parameter in constructor
- Dismisses with filter dict or None

---

### 4. `ezkey_cli/tui/screens/confirmation_modal.py`

**File:** Already created, no changes needed

**Features:**
- Generic confirmation dialog
- Accepts `title` and `message` parameters
- Confirm (red button) / Cancel (default button)
- Dismisses with True or False

---

### 5. `ezkey_cli/tui/api_client.py`

**Methods:**
- `_delete(endpoint: str) -> bool`: DELETE request, returns success/failure
- `get_integrations(..., name=None, active=None)`: Enhanced to support filters

**Filter Parameter Mapping:**
```python
params = {}
if name:
    params["integrationName"] = name  # Backend expects "integrationName"
if active:
    params["active"] = active
response = self._get("/api/v1/integrations", params=params)
```

---

## User Testing Instructions

### Test 1: Create Integration
1. Start TUI: `ezkey --tui`
2. Navigate to Integrations screen (from home)
3. Press 'c' to create
4. Fill form: Name, Description, Logo
5. Click "Create" button
6. Verify integration appears in list

### Test 2: Filter by Name
1. In Integrations screen, press 'f'
2. Type "ACME" in name field
3. Click "Apply"
4. Verify: Only integrations with "ACME" in name are shown
5. Pagination reset to page 1

### Test 3: Filter by Active Status
1. In Integrations screen, press 'f'
2. Check "Active Only" checkbox
3. Click "Apply"
4. Verify: Only active integrations (✓) shown

### Test 4: Clear Filters
1. In Integrations screen, press 'f'
2. Enter a filter (name or active)
3. Click "Apply"
4. Press 'f' again
5. Click "Clear"
6. Verify: All integrations shown again

### Test 5: View Integration Details
1. In Integrations screen, select a row and press Enter
2. Details screen opens with all information
3. Press 'r' to refresh details
4. Press 'h' or Esc to return to list

### Test 6: Delete Integration (Most Important!)
1. In Integrations screen, select a row and press Enter
2. Press 'd' to delete
3. ConfirmationModal appears with integration name
4. Read the confirmation message carefully
5. Click "Confirm" button
6. Success message shows for 1 second
7. Returns to Integrations screen
8. Verify: Integration no longer in list
9. **Test cancellation:** Repeat steps 1-4, click "Cancel", verify integration still exists

### Test 7: API Alignment
**Verify API calls match these patterns:**

Create:
```
POST /api/v1/integrations
Body: {
  "i18n": [{"language": "en", "name": "...", "description": "..."}],
  "logo": "..."
}
```

Get with filters:
```
GET /api/v1/integrations?page=0&size=25&integrationName=ACME&active=true
```

Delete:
```
DELETE /api/v1/integrations/{id}
```

---

## Design Pattern Reference

This implementation follows the **Depth-First Master-Detail Pattern** documented in `TUI_DESIGN_PATTERNS.md`:

```
List Screen (IntegrationsScreen)
├─ Pagination: 'n' / 'p' keys
├─ Refresh: 'r' key
├─ Create: 'c' key → CreateIntegrationModal (form)
├─ Filter: 'f' key → FilterModal (search)
└─ Detail: Enter key → IntegrationDetailScreen
    ├─ Refresh: 'r' key
    ├─ Edit: 'e' key (TODO)
    └─ Delete: 'd' key → ConfirmationModal (confirm)
```

---

## Error Handling

### API Errors
- `_get()` returns empty dict `{}` if request fails
- `_post()` returns `{}` if creation fails
- `_delete()` returns `False` if deletion fails

### UI Error Messages
- Shown in detail_widget: `"❌ Error: {message}"`
- User can press 'r' to retry

### Graceful Degradation
- If API client missing: Show "No API client available"
- If integration not found: Show 404 or similar from API

---

## Next Steps

1. **User Functional Testing:** Run the test scenarios above via `ezkey --tui`
2. **Postman Verification:** Verify API responses match documented schemas
3. **Potential Enhancements:**
   - Edit modal for updating integration details
   - Batch delete (select multiple, delete all)
   - Sort toggle ('s' key) for different sort orders
   - Enrollments screen (follow same pattern)
   - Audit logs screen (follow same pattern)

---

## Code Quality Checklist

- ✅ All methods follow UTF-8 encoding (no BOM)
- ✅ Constructor injection used throughout
- ✅ Keyboard bindings documented in BINDINGS array
- ✅ Error handling with try/except and logging
- ✅ SLF4J logging via Python `logging` module
- ✅ TUI patterns follow Textual >= 0.30.0 conventions
- ✅ Modal screens use proper callback pattern (dismiss with result)
- ✅ API client methods consistent (return dict or bool)
- ✅ Integration name extraction handles missing i18n gracefully

---

## Integration Alignment

**With Controller:**
- API endpoints match Admin API controller
- Request/response DTOs align with POST/GET/DELETE operations

**With Postman:**
- Filter parameters match query string format
- Bearer token authentication in Authorization header
- Response structure parsed correctly (content + page metadata)

**With CLI:**
- Shared ConfigManager for token storage
- Same authentication pattern
- Consistent API client design

