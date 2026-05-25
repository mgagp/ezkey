# Ezkey TUI - Development Guide

**The TUI is a read-only investigation and audit fallback.** For scope and positioning, see [TUI_SCOPE.md](TUI_SCOPE.md). This guide covers architecture and development for contributors.

## Table of Contents

1. [Overview](#overview)
2. [Installation](#installation)
3. [Quick Start](#quick-start)
4. [Architecture](#architecture)
5. [Design Philosophy](#design-philosophy)
6. [UI Patterns](#ui-patterns)
7. [Implementation Details](#implementation-details)
8. [Integration Management](#integration-management)
9. [Session & Token Management](#session--token-management)
10. [Development Guide](#development-guide)
11. [Testing](#testing)
12. [Troubleshooting](#troubleshooting)

---

## Overview

The Ezkey TUI is a **read-only** Text User Interface built with the Textual framework. It is an investigation and audit fallback when the web Admin UI is unavailable (e.g. SSH). **Primary admin interface:** Admin UI (web). See [TUI_SCOPE.md](TUI_SCOPE.md).

**Entry command**: `ezkey --tui`

### Key Features (read-only)

- ✅ Passwordless authentication with encrypted session storage
- ✅ Interactive setup wizard (first-run)
- ✅ Dashboard with real-time stats
- ✅ Audit logs (list, filter, follow)
- ✅ Auth attempts, Enrollments, Integrations, Tenants (list, detail, filter only — no create/update/delete)
- ✅ Keyboard-first navigation

---

## Installation

```bash
cd ezkey-cli-python

# Install package with TUI dependencies
pip install -e .

# Or just install dependencies
pip install textual>=0.30.0 cryptography>=41.0.0
```

### Verify Installation

```bash
ezkey --help
```

Should show:
```
--tui                          Start interactive admin console
```

---

## Quick Start

### Launch Admin Console

```bash
ezkey --tui
```

### First Run (Setup Wizard)

1. You'll see an interactive setup wizard
2. Enter Admin API URL (defaults to `http://localhost:9080` for Docker)
3. Enter admin username
4. Enter organization name
5. A challenge code appears → Approve on your enrolled device
6. Session is saved and dashboard loads

### Subsequent Launches

```bash
ezkey --tui  # Just works! Session loads automatically
```

### Development Mode (Docker)

The TUI automatically detects localhost and Docker hostnames:

```bash
# All these work automatically without SSL issues
ezkey --tui
# Enter: http://localhost:9080
# OR: http://host.docker.internal:9080
# OR: http://docker.for.mac.localhost:9080
```

**Development mode features:**
- ✅ HTTP connections accepted (no HTTPS required)
- ✅ SSL verification disabled
- ✅ Helpful error messages
- ✅ Can continue setup even if health check fails

---

## Architecture

### Project Structure

```
ezkey_cli/
├── auth/                          # SHARED - Authentication layer
│   ├── session.py                 # Session storage & encryption
│   ├── auth_manager.py            # Authentication flows
│   └── login_wizard.py            # Interactive setup wizard
├── tui/                           # NEW - TUI mode
│   ├── app.py                     # Textual app entry point
│   ├── screens/
│   │   ├── auth.py                # Authentication screen
│   │   ├── home.py                # Home dashboard
│   │   ├── integrations.py        # Integrations management
│   │   ├── integration_detail.py  # Integration detail view
│   │   ├── filter_modal.py        # Filter modal
│   │   └── confirmation_modal.py  # Confirmation dialog
│   ├── widgets/
│   │   ├── header.py              # Custom header widget
│   │   └── sidebar.py             # Navigation sidebar widget
│   └── api_client.py              # HTTP client for Admin API
├── cli/                           # EXISTING - CLI mode (unchanged)
├── config/                        # EXISTING - Configuration
└── main.py                        # UPDATED - Added --tui flag
```

### Design Principles

✅ **Pragmatism** - MVP focuses on essential admin operations
✅ **Simplicity** - Single CLI command, clear entry point
✅ **Modularity** - Separate concerns (auth, tui, screens)
✅ **Security** - Encrypted sessions, no plaintext tokens
✅ **User-First** - Setup wizard on first run, no login prompts
✅ **Scalability** - Easy to add new screens controller-by-controller

### Entry Point Flow

```
ezkey
  ├── --tui flag set?
  │   ├── YES → start_tui() → LoginWizard → EzkeyAdminApp
  │   └── NO  → CLI mode (existing behavior)
```

### Authentication Flow (First Run)

```
start_tui()
  └── check session
      ├── valid session exists?
      │   └── YES → load session → _start_app_with_session()
      └── NO → _run_login_wizard()
          └── LoginWizard.run()
              ├── get_admin_url() → validate connection
              ├── get_username()
              ├── get_organization()
              └── _authenticate()
                  ├── start_passwordless_auth()
                  ├── display challenge code
                  ├── wait_for_auth() → poll until approved
                  └── create_session_from_auth_response()
                      └── SessionManager.save_session()
```

---

## Design Philosophy

### Strategy: Depth-First

**Decision:** Implement one screen completely (Integrations) with all patterns (list, detail, create, filter, sort) before moving to other screens.

**Rationale:**
- Reusable pattern established and tested
- Less future refactoring
- Consistent console from the start

### Core Principles

- **Simplicity**: Simple patterns, easy to implement
- **Pragmatism**: Avoid unnecessary complexity
- **DevOps-friendly**: Keyboard-first, no excessive hand-holding
- **Consistency**: Same UX everywhere

---

## UI Patterns

### 1. Master-Detail: Separate Screen (push screen)

```
List → Enter on row → Detail screen → Esc → back to List
```

- Simple to implement
- Clear navigation (Textual stack)
- Familiar to DevOps (vim/kubectl style)

### 2. Filters: Inline or Prompt Modal

```
Press 'f' → Input modal "Filter by name:" → Apply
```

- No side panel (too complex)
- Optional sticky header to display active filters

### 3. Sorting: Cycle with Binding

```
Press 's' → Cycle: id↑, id↓, name↑, name↓
```

- Simple toggle, no menu

### 4. Creation/Editing: Modal Overlay

```
Press 'c' → Modal form wizard → Submit or Esc to cancel
```

- Isolated from context
- Focus on the task
- Simpler than side panel

### Standard Keyboard Bindings

| Key | Action |
|-----|--------|
| `h` | Home |
| `r` | Refresh |
| `Enter` | Detail |
| `c` | Create |
| `e` | Edit (in detail) |
| `d` | Delete (in detail) |
| `f` | Filter |
| `s` | Sort |
| `n/p` | Next/Prev page |
| `Esc` | Back/Cancel |
| `q` | Quit |

---

## Implementation Details

### Session Management

**SessionManager** (`auth/session.py`):
- Encrypted session storage in `~/.ezkey/admin/session`
- Automatic encryption key generation with 0o600 permissions
- Session validation (expiration checking)
- Methods: `save_session()`, `load_session()`, `clear_session()`, `is_session_valid()`

**Session Data Structure:**
```json
{
  "version": "1.0",
  "username": "admin",
  "organization": "My Organization",
  "admin_url": "https://localhost:9080",
  "access_token": "eyJhbG...",
  "refresh_token": "ref_...",
  "expiration": "2026-01-31T12:00:00Z",
  "created_at": "2026-01-30T10:00:00Z"
}
```

### Authentication Layer

**AuthManager** (`auth/auth_manager.py`):
- Passwordless authentication flows
- Token refresh logic with background refresh
- Session creation from auth response
- Methods: `start_passwordless_auth()`, `wait_for_auth()`, `refresh_token()`, `get_valid_token()`, `logout()`

**LoginWizard** (`auth/login_wizard.py`):
- Interactive first-run setup
- Admin URL validation with connection test
- Username and organization prompts
- Challenge code display
- Device approval waiting (with progress bar)

### TUI Application Base

**EzkeyAdminApp** (`tui/app.py`):
- Main application class
- Session detection and validation
- Entry point routing (login wizard vs. dashboard)
- Textual app initialization

### Error Handling UX

**Principle:** Admin console = clarity and traceability.

#### 1. Action Errors (creation/modification blocked)
- **Display:** Inline message in modal/form + visible error label
- **Color:** Red/warning to attract attention
- **Content:** Backend message (API) + error code if available
- **Example:** "Cannot create enrollment for system integration. System integrations are reserved..."
- **Persistence:** Stays visible until correction or closure

#### 2. Critical Errors (401/403, backend down)
- **Display:** Blocking modal with explanation
- **Actions:** Retry / Cancel / Logout (depending on context)
- **Example:** "Authentication failed (401). Please login again."

#### 3. Validation Errors (missing/invalid fields)
- **Display:** Inline message under the field concerned
- **Validation:** Client-side before API call when possible
- **Example:** "Integration ID must be a number"

#### 4. Success Operations
- **Display:** Brief message (✓) in list view after return
- **Alternative:** Non-blocking toast (optional for future phase)

---

## Integration Management

### Screen Flow

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
└─────────────────────────────────────────────────────────────────┘
```

### Keyboard Bindings: Integrations Screen

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

### Filter Workflow

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

### Delete Confirmation Workflow

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

### API Calls

**Create Integration:**
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
```

**Get Integrations (with filters):**
```
GET /api/v1/integrations?page=0&size=25&integrationName=ACME&active=true
Authorization: Bearer {token}
```

**Delete Integration:**
```
DELETE /api/v1/integrations/{id}
Authorization: Bearer {token}

Response: 204 No Content
```

---

## Session & Token Management

### Token Storage Architecture

**Recommendation:** Unified token storage in `~/.ezkey/ezkey.json` (ConfigManager)

**Before (Problem):**
```
├── CLI: ~/.ezkey/ezkey.json (Token A)
└── TUI: ~/.ezkey/admin/bearer-token (Token B) ← Conflicting!
```

**After (Solution):**
```
└── Unified: ~/.ezkey/ezkey.json ← Single source of truth
```

### Token Validation on Startup

**Current Problem:** Dashboard shows zeros when token is expired/invalid
**Root Cause:** No token validation on startup
**User Impact:** Silent failure - admin sees blank dashboard

**Solution:**

```python
def validate_token(self) -> bool:
    """
    Validate if bearer token is still valid.

    Returns:
        True if token is valid, False if expired/invalid
    """
    try:
        url = f"{self.admin_url}/api/v1/health"
        response = requests.get(
            url,
            headers=self.headers,
            timeout=5,
            verify=self.verify_ssl
        )

        if response.status_code == 200:
            return True
        elif response.status_code in [401, 403]:
            return False
    except:
        return False
```

**New Startup Flow:**
```
1. TUI starts
   ├─ Load token from ~/.ezkey/ezkey.json
   ├─ VALIDATE token (new step)
   │  ├─ Call /api/v1/health with token
   │  ├─ If 200 OK → token valid ✓
   │  ├─ If 401/403 → token expired/invalid ✗
   │  │  └─ Show modal: "Token expired. Please re-authenticate"
   │  │     ├─ Launch LoginWizard
   │  │     ├─ Get new token (same passwordless flow)
   │  │     └─ Save new token
   │  └─ If other error → show generic error, allow retry
   ├─ Create ApiClient with validated token
   ├─ Show HomeScreen
   └─ Dashboard loads with real data
```

### Security Features

- **Encrypted Storage**: All sessions encrypted with Fernet (AES-128)
- **Secure Permissions**: Session files with 0o600, keys with 0o600
- **Token Refresh**: Silent refresh without user interruption
- **No Login Screens**: Only setup wizard on first run (industry standard)
- **Expiration Validation**: Automatic token expiration checking

---

## Development Guide

### Add a New Screen

1. Create file in `ezkey_cli/tui/screens/myscreen.py`
2. Extend `textual.screen.Screen`
3. Import in `screens/__init__.py`
4. Add to screen manager in `tui/app.py`

### Reusable Screen Pattern

```python
from textual.screen import Screen
from textual.binding import Binding

class MyScreen(Screen):
    """My custom screen."""

    BINDINGS = [
        Binding("h", "show_home", "Home"),
        Binding("r", "refresh", "Refresh"),
        Binding("q", "quit", "Quit"),
    ]

    def compose(self):
        """Create child widgets."""
        yield Header()
        yield Container(
            # Your widgets here
        )

    def action_show_home(self):
        """Return to home screen."""
        self.app.pop_screen()

    def action_refresh(self):
        """Refresh screen data."""
        self._load_data()

    def _load_data(self):
        """Load data from API."""
        # Implementation here
        pass
```

### Add a Modal Dialog

```python
from textual.screen import ModalScreen
from textual.containers import Grid
from textual.widgets import Button, Label

class MyModal(ModalScreen[bool]):
    """My custom modal dialog."""

    def __init__(self, title: str, message: str):
        super().__init__()
        self.title = title
        self.message = message

    def compose(self):
        yield Grid(
            Label(self.title, id="title"),
            Label(self.message, id="message"),
            Button("Confirm", variant="error", id="confirm"),
            Button("Cancel", id="cancel"),
            id="dialog",
        )

    def on_button_pressed(self, event: Button.Pressed):
        if event.button.id == "confirm":
            self.dismiss(True)
        else:
            self.dismiss(False)
```

### API Client Pattern

```python
class ApiClient:
    """HTTP client for Admin API."""

    def __init__(self, admin_url: str, token: str):
        self.admin_url = admin_url
        self.token = token
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

    def _get(self, endpoint: str, params: dict = None) -> dict:
        """Execute GET request."""
        try:
            url = f"{self.admin_url}{endpoint}"
            response = requests.get(url, headers=self.headers, params=params)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            log.error(f"GET {endpoint} failed: {e}")
            return {}

    def _post(self, endpoint: str, data: dict) -> dict:
        """Execute POST request."""
        try:
            url = f"{self.admin_url}{endpoint}"
            response = requests.post(url, headers=self.headers, json=data)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            log.error(f"POST {endpoint} failed: {e}")
            return {}

    def _delete(self, endpoint: str) -> bool:
        """Execute DELETE request."""
        try:
            url = f"{self.admin_url}{endpoint}"
            response = requests.delete(url, headers=self.headers)
            return response.status_code in [200, 204]
        except Exception as e:
            log.error(f"DELETE {endpoint} failed: {e}")
            return False
```

---

## API Client & Error Handling

### RFC 7807 Problem Details Support

**Status:** Introduced in v1.0.0 - Supports RFC 7807 error responses from Admin API

The API client now gracefully handles **RFC 7807 Problem Details** for HTTP APIs, a standard format for API errors that provides structured, human-readable error messages.

**Implementation Details:**
- `_extract_error_message()` method parses error responses with priority:
  1. **RFC 7807 `detail` field** (most specific) - Business logic error
  2. **RFC 7807 `title` field** - Error category
  3. **Legacy `message` field** - Backward compatibility
  4. **Raw text** - Fallback for non-JSON responses

**Error Handling Pattern:**
```python
# API Client returns None + stores error message
success = api_client.deactivate_admin(admin_id)

if not success:
    # Display stored error message from API response
    error = api_client.last_error_message
    # Example: "Cannot deactivate your own account"
    status.update(f"❌ {error}")
```

**Example RFC 7807 Response:**
```json
{
  "type": "https://ezkey.io/problems/admin-not-allowed",
  "title": "Admin Operation Not Allowed",
  "status": 400,
  "detail": "Cannot deactivate your own account",
  "instance": "/api/v1/admins/1/deactivate"
}
```

### Extensibility for Future APIs

**Design Principle:** New endpoints using RFC 7807 work automatically

**How it works:**
1. Backend returns RFC 7807 error response (any status >= 400)
2. `_extract_error_message()` parses `detail` field
3. TUI displays the message without code changes
4. No need to modify `_post()`, `_get()`, `_delete()` methods

**Example: Adding a new deactivation endpoint**
```python
# In api_client.py - automatically supports RFC 7807!
def deactivate_resource(self, resource_id: int) -> bool:
    response = self._post(f"/api/v1/resources/{resource_id}/deactivate", data={})
    return response is not None
```

**Status Fields Available:**
- `api_client.last_error_message` - Human-readable error message
- `api_client.last_status_code` - HTTP status code
- `api_client.last_auth_error` - True if 401/403

### Migration Path for Legacy Endpoints

If an endpoint uses legacy error format with `message` field instead of `detail`:
```python
# Old format (still supported)
{"message": "Error description"}

# New format (RFC 7807)
{"detail": "Error description", "title": "...", ...}
```

The API client handles both automatically - no TUI code changes needed.

---

## Testing

### Unit Tests

```bash
cd ezkey-cli-python
pip install -e .
pip install -r requirements-test.txt
pytest tests/unit/ -v
```

TUI smoke tests: `tests/unit/test_tui_smoke.py` (imports, `ConfigManager` bearer token, legacy migration, mocked `AuthManager`).

### Integration Tests (CLI, not TUI UI)

```bash
# Ensure Docker stack is running (clean-start)
cd ezkey-cli-python
pytest tests/integration/ -v
```

There is no automated Textual/TUI integration suite yet; use the manual checklist below against a running stack.

### Manual TUI checklist (read-only — see TUI_SCOPE.md)

- [ ] `ezkey --help` shows `--tui`
- [ ] `ezkey --tui` starts; passwordless login succeeds
- [ ] Dashboard loads (overview stats)
- [ ] Each in-scope area: Audit logs, Auth attempts, Enrollments, Integrations, Tenants — list, filter, detail, refresh
- [ ] Logout clears session and exits cleanly
- [ ] Re-open TUI: quick re-auth when username is stored and token expired

---

## Troubleshooting

### TUI Won't Start

**Problem: "ModuleNotFoundError: No module named 'textual'"**
```bash
# Solution: Install dependencies
pip install textual>=0.30.0 cryptography>=41.0.0
```

**Problem: "Permission denied ~/.ezkey/admin/session"**
```bash
# Solution: Check permissions
ls -la ~/.ezkey/admin/
# Should be 0o600 (user read/write only)
```

### Connection Issues

**Problem: "Failed to connect to Admin API"**
```bash
# Solution: Verify Admin API URL is correct and running
curl http://localhost:9080/api/v1/health

# Or test with CLI first
ezkey admin integration list
```

**Problem: "Session expired"**
```bash
# Solution: Token refresh might have failed, delete session
rm ~/.ezkey/admin/session
ezkey --tui
```

### Display Issues

**Problem: TUI looks broken/garbled**
```bash
# Solution: Check terminal capabilities
echo $TERM
# Should be xterm-256color or similar

# On Windows, use Windows Terminal or PowerShell 7+
```

**Problem: Icons/glyphs don't display**
```bash
# Solution: Terminal needs Unicode support
# Install a font that supports Unicode (e.g., Cascadia Code, Fira Code)
```

### Terminal Requirements

**Minimum:**
- ANSI/VT100 control sequences
- UTF-8 encoding
- Minimum size: 80x24

**Recommended:**
- 256 colors (`TERM=xterm-256color`)
- Unicode glyphs (for icons)
- Truecolor support (optional)

**SSH / remote shells (Linux):**
- Works well if `TERM` is set to a capable value (`xterm-256color` or `screen-256color`)
- In tmux/screen, ensure 256-color passthrough is enabled
- If `TERM=dumb`, TUI will be partially or not functional

---

## Next Steps

### Phase 2 (In Progress)

- Full Textual app with screen switching
- Home dashboard with real Admin API data
- Integration management screen (complete)
- Enrollment and auth attempt tracking

### Phase 3 (Planned)

- Advanced widgets
- Keyboard shortcuts customization
- Search/filter enhancements
- User preferences storage

---

## Resources

- [Textual Documentation](https://textual.textualize.io/)
- [Ezkey Plan Document](../../.github/prompts/plan-ezkeyAdminTui.prompt.md)
- [Ezkey CLI Architecture](./README.md)
- [GitHub Repository](https://github.com/mgagp/ezkey)

---

**Status**: ✅ Phase 1 Complete | 🚀 Phase 2 In Progress

**Maintainer**: Ezkey Contributors
