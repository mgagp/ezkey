## Windows tooling: Git Bash, not PowerShell scripts

**Support policy:** On Windows, Ezkey repo tooling is **Bash-first** via **Git for Windows** (`C:\Program Files\Git\bin\bash.exe`). Do not use WSL's `bash.exe` for repo scripts unless explicitly requested.

Historical note: this document once recommended PowerShell wrappers such as `docker/start.ps1`. Those `.ps1` / `.bat` / `.cmd` wrappers were removed in favor of a single portable Bash surface shared with Linux and macOS.

### Recommended command (Windows)

From the repository root, prefer Git Bash:

```bash
./docker/start.sh
```

From PowerShell or CMD (agent shells often use PowerShell), invoke Git Bash explicitly:

```powershell
& "C:\Program Files\Git\bin\bash.exe" -lc './docker/start.sh'
```

Useful flags (same on all platforms):

```bash
./docker/start.sh --parallel
./docker/start.sh --no-cache
SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh
```

### Environment variables

Set variables in the Bash session (or prefix the command), for example:

```bash
export SPRING_PROFILES_ACTIVE=docker,docker-dev
export EZKEY_ENABLE_JMX=true
./docker/start.sh
```

### Exceptions

- Android Gradle wrapper: `ezkey_mobile/android/gradlew.bat` (toolchain).
- A small set of interim mobile Maestro harness scripts under `ezkey_mobile/scripts/*.ps1` (orthogonal workstream).

See [`.cursor/rules/shell-preferences.mdc`](../.cursor/rules/shell-preferences.mdc) and [`docker/README.md`](../docker/README.md).

---

_Updated: 2026-08-09_
