# 🚀 Ezkey Admin TUI - Quick Start Guide

## What Was Built

A **Text User Interface (TUI) admin console** for Ezkey using Textual framework.

**Entry command**: `ezkey --tui`

---

## Installation

```bash
cd ezkey-cli-python

# Install package with new dependencies
pip install -e .

# Or just install dependencies
pip install textual>=0.30.0 cryptography>=41.0.0
```

---

## Usage

### Launch Admin Console
```bash
ezkey --tui
```

### First Run
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

For full Docker setup guide, see [DOCKER_SETUP.md](./DOCKER_SETUP.md)

---

## Verify Installation

### Check help text includes --tui
```bash
ezkey --help
```

Should show:
```
--tui                          Start interactive admin console
```

### Run foundation tests
```bash
cd ezkey-cli-python
pytest tests/test_tui_foundation.py -v
```

### Verify existing CLI still works
```bash
ezkey admin integration list
ezkey configure --help
```

---

## What's Ready (Phase 1)

✅ Session encryption & persistence
✅ Passwordless authentication wizard
✅ Token refresh logic
✅ Textual app scaffold
✅ Basic screens (auth, home, integrations)
✅ Reusable widgets (header, sidebar)
✅ CLI integration (--tui flag)

---

## What's Coming (Phase 2)

🔄 Full Textual app with screen switching
🔄 Home dashboard with real Admin API data
🔄 Integration management screen
🔄 Enrollment and auth attempt tracking
🔄 Complete keyboard shortcuts

---

## Architecture Overview

```
ezkey --tui
    ↓
Check ~/.ezkey/admin/session
    ↓
Session valid?
├─ YES → Load session → Dashboard
└─ NO  → LoginWizard
         ├─ Get config
         ├─ Authenticate
         └─ Save session → Dashboard
```

---

## File Locations

- **Session data**: `~/.ezkey/admin/session` (encrypted)
- **Encryption key**: `~/.ezkey/admin/.key` (secure)
- **Source code**: `ezkey-cli-python/ezkey_cli/`
  - `auth/` - Shared authentication layer
  - `tui/` - TUI-specific code
  - `cli/` - Existing CLI (unchanged)

---

## Key Files

| File | Purpose |
|------|---------|
| `ezkey_cli/auth/session.py` | Encrypted session storage |
| `ezkey_cli/auth/auth_manager.py` | Authentication flows |
| `ezkey_cli/auth/login_wizard.py` | Interactive setup |
| `ezkey_cli/tui/app.py` | TUI app entry point |
| `ezkey_cli/tui/screens/home.py` | Dashboard |
| `ezkey_cli/main.py` | CLI integration (--tui flag) |

---

## Common Commands

```bash
# Start TUI
ezkey --tui

# Logout and clear session
# (Use 'l' key in app or delete ~/.ezkey/admin/session)

# Reset session
rm -rf ~/.ezkey/admin/

# View logs
# (Check application output for debug info)

# Test authentication layer
pytest tests/test_tui_foundation.py
```

---

## Troubleshooting

### "ModuleNotFoundError: No module named 'textual'"
→ Install: `pip install textual>=0.30.0`

### "Permission denied ~/.ezkey/admin/session"
→ Check permissions: `ls -la ~/.ezkey/admin/`
→ Should be `0o600` (user read/write only)

### "Failed to connect to Admin API"
→ Verify Admin API URL is correct
→ Check Admin API is running on that URL
→ Try `curl https://admin-url:9080/api/v1/health`

### "Session expired"
→ Token refresh might have failed
→ Delete session: `rm ~/.ezkey/admin/session`
→ Run `ezkey --tui` again to setup

---

## Terminal Capabilities (TUI)

**Minimum requirements**
- **ANSI/VT100** control sequences (cursor movement, colors)
- **UTF-8** encoding
- **Minimum size**: 80x24

**Recommended**
- **256 colors** (e.g., `TERM=xterm-256color`)
- **Unicode glyphs** (for icons like 📊/🔐)
- **Truecolor** support (optional; improves theme fidelity)

**SSH / remote shells (Linux)**
- Works well if `TERM` is set to a capable value (`xterm-256color` or `screen-256color`).
- In tmux/screen, ensure 256-color passthrough is enabled.
- If `TERM=dumb` or terminal doesn’t support ANSI, the TUI will be partially or not functional.

**Degraded/partial behavior**
- Limited colors → layout works but visuals look flat.
- Missing Unicode → icons fall back to squares or blank.
- Small terminal size → clipped panels / scrolling.

**Examples that work**
- Windows PowerShell / Windows Terminal
- Linux terminals with `xterm-256color`
- macOS Terminal / iTerm2

---

## Development

### Add a new screen
1. Create file in `ezkey_cli/tui/screens/myscreen.py`
2. Extend `textual.screen.Screen`
3. Import in `screens/__init__.py`
4. Add to screen manager in `tui/app.py`

### Test your code
```bash
pytest tests/ -v
```

### Check formatting
```bash
# Run style checks
python -m autopep8 --in-place ezkey_cli/
```

---

## Documentation

- **Architecture**: See [TUI_IMPLEMENTATION.md](./TUI_IMPLEMENTATION.md)
- **Plan**: See [../../.github/prompts/plan-ezkeyAdminTui.prompt.md](../../.github/prompts/plan-ezkeyAdminTui.prompt.md)
- **Phase 1 Summary**: See [PHASE1_SUMMARY.md](./PHASE1_SUMMARY.md)
- **Implementation Report**: See [IMPLEMENTATION_REPORT.md](./IMPLEMENTATION_REPORT.md)

---

## Support

### Textual Framework
- [Official Documentation](https://textual.textualize.io/)
- [GitHub](https://github.com/Textualize/textual)

### Cryptography
- [Documentation](https://cryptography.io/)

### Ezkey
- [Main Repository](https://github.com/mgagp/ezkey)
- [Architecture](../ARCHITECTURE.md)

---

## Next Steps

1. **Install & test**: `pip install -e .` then `ezkey --tui`
2. **Read docs**: Check [TUI_IMPLEMENTATION.md](./TUI_IMPLEMENTATION.md)
3. **Review plan**: See [plan-ezkeyAdminTui.prompt.md](../../.github/prompts/plan-ezkeyAdminTui.prompt.md)
4. **Start Phase 2**: Implement dashboard with real data

---

**Status**: ✅ Phase 1 Foundation Complete
**Ready to start**: Phase 2 Dashboard Implementation
**Maintainer**: Ezkey Contributors

