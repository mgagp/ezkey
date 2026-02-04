# TUI Token Startup & Validation Flow

**Date:** 2025-01-31
**Status:** ✅ Analysis Complete - Ready for Implementation
**Priority:** High (Blocks dashboard on expired token)

---

## Current Problem

**Symptom:** Dashboard shows all zeros when token is expired/invalid
**Root Cause:** No token validation on startup - TUI assumes token is valid
**User Impact:** Silent failure - admin sees blank dashboard

### Current Flow
```
1. TUI starts
   ├─ Load token from ~/.ezkey/ezkey.json
   ├─ Create ApiClient with token (no validation)
   ├─ Show HomeScreen
   │  └─ Load dashboard stats
   │     └─ API calls fail with 401
   │        └─ ApiClient logs error but returns None
   │           └─ Dashboard shows 0 for all stats
   └─ User sees nothing - confused
```

---

## Solution: Token Validation on Startup

### New Startup Flow
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

---

## Implementation Steps

### Step 1: Add Token Validation Method to ApiClient

**File:** `ezkey_cli/tui/api_client.py`

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
            log.debug("Token validation successful")
            return True
        elif response.status_code in [401, 403]:
            log.warning(f"Token validation failed: {response.status_code}")
            return False
        else:
            log.error(f"Token validation error: {response.status_code}")
            return False

    except Exception as e:
        log.error(f"Token validation failed: {e}")
        return False
```

### Step 2: Add Validation Check to App Startup

**File:** `ezkey_cli/tui/app.py` - `EzkeyAdminTUI.on_mount()`

```python
def on_mount(self) -> None:
    """Called when app is mounted."""
    # Validate token before showing dashboard
    if not self.api_client.validate_token():
        # Token is invalid - show re-auth screen
        self.push_screen(ReAuthScreen(self.config, self.admin_url))
    else:
        # Token is valid - show dashboard
        self.push_screen("home")
```

### Step 3: Create ReAuthScreen Modal

**File:** `ezkey_cli/tui/screens/reauth.py`

```python
class ReAuthScreen(ModalScreen):
    """Modal to re-authenticate when token is expired."""

    def __init__(self, config, admin_url):
        super().__init__()
        self.config = config
        self.admin_url = admin_url

    def compose(self) -> ComposeResult:
        yield Static("🔐 Token Expired")
        yield Static("Your authentication token has expired.")
        yield Static("")
        yield Button("Re-authenticate", id="reauth_btn")
        yield Button("Quit", id="quit_btn")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "reauth_btn":
            # Launch login wizard
            from ..auth.login_wizard import LoginWizard
            wizard = LoginWizard()
            success = wizard.run(config=self.config)

            if success:
                # New token obtained - reload ApiClient
                token = self.config.get('bearerToken')
                self.app.api_client.bearer_token = token
                self.app.api_client.headers["Authorization"] = f"Bearer {token}"

                # Show dashboard
                self.app.pop_screen()  # Close modal
                self.app.push_screen("home")
            else:
                # Login failed - show error and keep modal
                self.query_one(Static).update("❌ Re-authentication failed")

        elif event.button.id == "quit_btn":
            self.app.exit()
```

---

## Integration Points

### Mobile Device Demo Integration

When TUI validates token on startup:
1. User runs TUI
2. If no token or expired: LoginWizard launches
3. LoginWizard shows: Challenge code + "Waiting for device approval"
4. **Mobile Device Demo** approves on its screen
5. Device sends approval to backend (existing flow)
6. LoginWizard receives token
7. TUI shows dashboard

**No changes needed to Mobile Device Demo** - it already works with existing auth flow.

---

## Error Cases Handled

| Case | Detection | Action |
|------|-----------|--------|
| **Valid token** | `/api/v1/health` returns 200 | Show dashboard |
| **Expired token** | `/api/v1/health` returns 401 | Show ReAuthScreen |
| **Invalid token** | `/api/v1/health` returns 403 | Show ReAuthScreen |
| **Network error** | Exception during validation | Show error modal, allow retry |
| **Server down** | `/api/v1/health` times out | Show error modal, allow retry |

---

## Token Refresh Considerations

### Current Approach (Recommended for Phase 2)
- Tokens don't expire during app session
- If expired, user re-authenticates on restart
- Simple, no background refresh logic

### Future Enhancement (Phase 3)
- Implement token refresh endpoint
- Automatically refresh before expiry
- Seamless experience without re-auth

---

## Configuration

### No New Config Needed
- Uses existing endpoints: `/api/v1/health`
- Uses existing token storage: `~/.ezkey/ezkey.json`
- Uses existing LoginWizard: `LoginWizard`

---

## Testing Checklist

- [ ] Start TUI with valid token → Show dashboard
- [ ] Start TUI with expired token → Show ReAuthScreen
- [ ] Approve on device → TUI shows dashboard
- [ ] Network error during validation → Show error, allow retry
- [ ] Mobile Device Demo still works with TUI auth flow

---

## Files to Create/Modify

| File | Action | Effort |
|------|--------|--------|
| `api_client.py` | Add `validate_token()` | 5 min |
| `app.py` | Add validation in `on_mount()` | 5 min |
| `reauth.py` | Create ReAuthScreen | 10 min |
| `screens/__init__.py` | Export ReAuthScreen | 1 min |
| **Total** | | **~20 min** |

---

## Success Criteria

✅ TUI starts with valid token → Dashboard loads with real data
✅ TUI starts with expired token → Shows re-auth modal
✅ User approves on device → TUI shows dashboard
✅ No more silent 401 failures
✅ Clear visual feedback on token status

---

## Recommendation

**Implement immediately** - This is blocking the dashboard functionality.
- Low effort (20 min)
- High impact (fixes silent failures)
- No breaking changes
- No Mobile Device Demo changes needed

---
## Session Timeout Strategy (Phase 2 - Implemented ✅)

**Decision:** 60-minute session timeout with quick re-auth screen.

**Rationale:**
- ✅ Balances security (no opportunistic access) and UX (no constant re-auth)
- ✅ Aligned with industry standards (AWS, GCP)
- ✅ Admin can work 1 hour uninterrupted
- ✅ New device requires full re-auth

**Flow:**
```
Token valid + Session valid    → Dashboard (0 clicks)
Token valid + Session expired  → QuickReAuthScreen (1-click approve)
Token invalid                  → ReAuthScreen (full re-auth)
```

**Implementation:**
- `ConfigManager.set_last_auth_time()` - Record auth timestamp
- `ConfigManager.is_session_expired(timeout_minutes=60)` - Check if expired
- `QuickReAuthScreen` - 1-click approve (username pre-filled)
- `app.py on_mount()` - Decision tree: validate → timeout → show screen

---
