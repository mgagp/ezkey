# Plan: Orientation stratégique pour Ezkey Admin TUI

Note: Le nom de la ligne de commande restera **ezkey**, tel qu'il est présentement.

## Executive Summary

**TL;DR**: Construire une console d'administration unifiée avec Textual (Python), session-based auth (first-run setup, pas de login screen à l'exécution), dashboard/home screen, et architecture modulaire prête pour CLI commands futurs. Éviter séparation inutile; une codebase avec modes différents suffit.

---

## Strategic Context

### Ezkey Project Context
- **Open-source MFA/Passkey alternative** - developer-first, pragmatic approach
- **Current phase**: Phase 4 (Industrialization & Mobile)
- **Philosophy**: Simplicity, pragmatism, developer-first adoption
- **Current CLI**: Python-based (Click framework) with 7 command groups and full API coverage
- **Status**: MVP complete, needs administrative interface for release

### Strategic Challenge
Ezkey needs a PRIMARY administrative console for release without building a separate web UI. The CLI will be transformed into a comprehensive admin dashboard while maintaining scriptability and simplicity principles.

---

## Real-World Research Findings

### Successful Admin TUI/CLI Tools Pattern
Infrastructure/DevOps tools with TUI/CLI as primary admin interface:
- **Textual ecosystem**: Dolphie (MySQL), Harlequin (DB client), Posting (API client)
- **Go Bubble Tea ecosystem**: MinIO MC, EKS Node Viewer, Dive, WTF, Bottom
- **Traditional**: kubectl, AWS CLI, Docker CLI

### Key Finding: Login Screens are Anti-Pattern
Research shows **NO major production admin tools use login screens during execution**:
- `kubectl` - never prompts for login
- AWS CLI - never prompts for login
- Docker CLI - never prompts for login
- MinIO MC - doesn't require login for operations

**Why**: Users expect authentication configured once upfront, then uninterrupted operation.

### Architectural Patterns in Production
1. **Stateless CLI with pre-configured auth** (most common) - `kubectl`, AWS CLI, Docker
2. **Stateful TUI with session management** (emerging) - modern Textual/Bubble Tea dashboards
3. **Hybrid approach** (recommended for Ezkey) - stateless CLI + optional TUI mode
4. **Separate executables** (uncommon) - separate binaries for CLI vs TUI (code duplication risk)

---

## Strategic Recommendations

### 1. Framework Decision: Textual (Python)

**Rationale**:
- Direct integration with existing Python Click CLI
- Rich ecosystem with Real-time dashboards
- Built on Rich library (already in dependencies via colorama)
- Excellent for: dashboards, tables, panels, status displays
- Mouse support, animations, screen management

**Alternative considered**: Bubble Tea (Go)
- Pros: Single compiled binary, production-proven by AWS/NVIDIA
- Cons: Requires Go rewrite, architecture rebuild, longer timeline

**Decision**: Start with Textual for pragmatic MVP, Bubble Tea as future rewrite option.

### 2. Authentication Pattern: Session-Based (NOT Login Screen)

**Recommended Flow**:
```
FIRST RUN:
1. User runs: ezkey-admin
2. Check for session in ~/.ezkey/admin/session
3. Session missing → Display setup wizard (ONE TIME)
   - Prompt for admin URL
   - Prompt for admin credentials
   - Perform initial passwordless auth (using ezkey's own MFA)
   - Store: access_token, refresh_token, expiration
4. Load admin dashboard

SUBSEQUENT RUNS:
1. User runs: ezkey-admin
2. Load session from ~/.ezkey/admin/session
3. If expired → refresh token silently in background
4. Display dashboard (no interruption)

LOGOUT:
$ ezkey-admin logout  (clears session)
```

**Benefits**:
- Aligns with user expectations (kubectl pattern)
- No interruption during operation
- Secure token storage
- Graceful token refresh
- First-run discovery

### 3. Home Screen / Dashboard

**Essential Elements**:
- **Status Overview**: Integration count, active enrollments, recent auth attempts
- **Quick Actions**: Shortcuts to common operations
- **Session Info**: Current user, organization, token expiration warning
- **Activity Feed**: Recent audit log entries
- **Navigation Menu**: Sidebar with sections (Integrations, Users, Audit, Settings)

**Navigation Structure**:
```
┌─────────────────────────────────────────────┐
│ Ezkey Admin Console        [User] [Logout]  │
├──────────────────────────────────────────────┤
│ Dashboard  ✓ Integrations | Users | Audit  │
│                                              │
│ Status Overview                              │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│ Integrations: 3     Enrollments: 12          │
│ Auth Attempts (24h): 45     Failed: 2        │
│                                              │
│ Recent Activity                              │
│ ─────────────────────────────────────────── │
│ • Admin login successful (now)               │
│ • Integration created: ACME Corp (1h ago)    │
│ • Enrollment reset: user@example.com (3h)    │
│                                              │
│ [I] Info  [?] Help  [q] Quit  [Tab] Menu    │
└──────────────────────────────────────────────┘
```

### 4. Architecture: Unified with Clear Module Separation

**NOT**: Separate ezkey_tui executable
**INSTEAD**: Single codebase with modular design

```
ezkey-cli-python/
├── ezkey/
│   ├── __init__.py
│   ├── cli/
│   │   ├── __init__.py
│   │   ├── main.py              # Click CLI entry point
│   │   ├── groups/
│   │   │   ├── admin.py
│   │   │   ├── auth.py
│   │   │   └── ...
│   │   └── commands/
│   │       └── ...
│   ├── tui/
│   │   ├── __init__.py
│   │   ├── app.py               # Textual app entry point
│   │   ├── screens/
│   │   │   ├── home.py
│   │   │   ├── auth.py
│   │   │   ├── integrations.py
│   │   │   ├── enrollments.py
│   │   │   └── ...
│   │   ├── widgets/
│   │   │   ├── header.py
│   │   │   ├── sidebar.py
│   │   │   └── status_panel.py
│   │   └── models/
│   │       └── screen_state.py
│   ├── auth/                    # SHARED - Auth logic
│   │   ├── session.py
│   │   ├── auth_manager.py
│   │   └── login_wizard.py
│   ├── api/                     # SHARED - API calls
│   │   ├── client.py
│   │   ├── admin_api.py
│   │   └── auth_api.py
│   └── config/
│       ├── settings.py
│       └── paths.py

setup.py / pyproject.toml
├── Entry points:
│   - ezkey-cli = ezkey.cli.main:main     (existing)
│   - ezkey-admin = ezkey.tui.app:main    (new)
│   - ezkey-tui = ezkey.tui.app:main      (new, alias)
```

**Key Principles**:
- CLI and TUI share auth, API, config layers
- TUI-specific code in `tui/` directory
- No code duplication between modes
- Both modes access same session storage

### 5. Entry Points & Mode Selection

**Option A: Separate Commands** (RECOMMENDED)
```bash
ezkey [command]     # Existing stateless CLI
ezkey-admin             # New stateful TUI dashboard
ezkey-tui               # Alias for ezkey-admin
```

**Option B: Single Entry Point with Flag**
```bash
ezkey [command]         # CLI mode (default)
ezkey --tui             # Interactive TUI
ezkey tui               # Alternative
```

**Recommendation**: Option A (separate commands) is cleaner, aligns with Unix philosophy.

### 6. MVP Scope (Proof of Concept)

**Phase 1 - MVP (This sprint)**:
1. **Authentication Screen** - First-run setup wizard, login flow
2. **Admin API Integration** - Integrations list/view/basic management
3. **Home Dashboard** - Status overview, basic layout, navigation menu
4. **Session Management** - Token storage, refresh logic, logout

**Controllers to Cover (MVP)**:
- Admin Authentication (login, recovery, logout)
- Integrations (list, view, create)

**Phase 2 - Extended**:
- Enrollments management
- Auth attempts monitoring
- API keys management
- Audit log viewer

**Phase 3 - Polish**:
- CLI commands for automation
- Advanced dashboard widgets
- Search/filter functionality
- Keyboard shortcuts optimization

---

## Implementation Strategy

### Step 1: Validate Framework
- Evaluate Textual with simple proof-of-concept screen
- Test integration with existing Click CLI codebase
- Verify performance, terminal compatibility

### Step 2: Build Session/Auth Layer (Shared)
- Session storage in `~/.ezkey/admin/session`
- Token encryption/decryption
- Refresh token logic
- First-run setup wizard (separate from TUI screen)

### Step 3: Implement MVP Screens
- Start with login/setup screen
- Add home dashboard
- Add integrations screen
- Validate navigation flow

### Step 4: Create Entry Point
- Add `ezkey-admin` command to setup.py
- Wire up Textual app with session management
- Test both CLI and TUI modes

### Step 5: Documentation
- Document architecture
- Provide contribution guidelines for future screens
- Create admin guide

---

## Technical Considerations

### Dependencies to Add
```toml
textual>=0.30.0          # TUI framework
cryptography>=41.0.0     # Session encryption
```

### Session Storage Structure
```json
{
  "version": "1.0",
  "organization": "acme-corp",
  "admin_url": "https://admin-api.example.com:9080",
  "access_token": "eyJhbG...",
  "refresh_token": "ref_...",
  "expiration": "2026-01-31T12:00:00Z",
  "created_at": "2026-01-30T10:00:00Z"
}
```

### Terminal Requirements
- 256-color support
- Unicode support
- Minimum terminal size: 80x24 (will warn if too small)

---

## Addressing Original Questions

### Question 1: Rich vs Textual Framework?
**Answer**: Textual
- Rich is formatting-only; Textual is full interactive framework
- Textual = Rich + interactivity + screen management
- Better for stateful admin dashboard

### Question 2: Pragmatic Simple Tool vs Full Admin Console?
**Answer**: Full admin console, but pragmatically
- Not "simple CLI" anymore; it's an admin platform now
- But pragmatic implementation: one controller at a time (MVP)
- Modularity ensures each admin controller/feature is independent
- Start small, grow systematically

### Question 3: Login Screen Anti-Pattern?
**Answer**: YES, it's anti-pattern. Use first-run setup instead.
- No login screen during normal operation
- One-time setup wizard on first run
- Session persists across invocations
- Matches kubectl, AWS CLI, Docker user expectations

### Question 4: Separate Binary (ezkey_cli vs ezkey_tui)?
**Answer**: NO, not necessary
- Architecture already separates concerns (tui/ vs cli/)
- Shared auth/api/config layers prevent duplication
- Single codebase, multiple entry points (cleaner maintenance)
- Future: easy to split if needed, but not required now

### Question 5: Per-Controller Approach?
**Answer**: YES, exactly right
- MVP: one controller (Admin Auth + Integrations)
- Each controller = separate screen in TUI
- Clear separation in code: `screens/integrations.py`, `screens/enrollments.py`, etc.
- Scales predictably as controllers are added

---

## Decision Matrix

| **Aspect** | **Decision** | **Rationale** |
|---|---|---|
| **Framework** | Textual (Python) | Pragmatic, integrates with existing code, rich visuals |
| **Auth Pattern** | Session-based first-run setup | Industry standard, no login screen anti-pattern |
| **Architecture** | Unified codebase, modular layers | Prevents duplication, easier maintenance |
| **Entry Points** | `ezkey-admin` + `ezkey-cli` | Separate tools, clear purpose, Unix philosophy |
| **Home Screen** | YES - Dashboard with status/menu | Discoverability, admin visibility |
| **MVP Scope** | Auth + Integrations | Validates architecture, manageable |
| **State Management** | In-memory + persistent session file | Simple, secure, non-intrusive |
| **Scriptability** | Phase 2+ (CLI commands) | Phase 1 focuses on TUI; CLI automation later |

---

## Success Criteria

- [ ] First-run setup wizard works end-to-end
- [ ] Session persists across multiple runs
- [ ] Home dashboard displays real data from admin API
- [ ] Integrations screen shows list and detail view
- [ ] Navigation between screens is intuitive
- [ ] Code is modular (easy to add new screens)
- [ ] No login interruption during normal operation
- [ ] Existing CLI commands still work (backward compatible)

---

## Next Steps

1. **Architecture Review**: Validate this approach with team
2. **Dependency Verification**: Confirm Textual + dependencies compatibility
3. **Proof of Concept**: Build minimal TUI screen to validate integration
4. **Session Layer**: Implement auth/session management
5. **MVP Implementation**: Build authentication + integrations screens
6. **Testing**: Session handling, API integration, UI responsiveness
7. **Documentation**: Architecture guide + contribution guidelines
