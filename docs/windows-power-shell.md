## Why we recommend PowerShell on Windows

Project note: Choosing PowerShell for Windows scripts is a development and maintenance decision. This document explains the rationale briefly.

- **Consistency & maintenance**: The Windows script maintained in the repository is `docker/start.ps1`. Documenting and referencing that script avoids duplication (`.ps1` vs `start.bat`) and reduces the risk of divergence.
- **Advanced capabilities**: PowerShell handles named parameters, structured error handling, exit codes, and logging more reliably than batch scripts. Patterns used in `start.ps1` (for example `-Parallel`, `-NoCache`, `-DebugCache`) are hard to reproduce faithfully in `*.bat`.
- **Reliable environment handling**: Environment variables and profiles (for example `$env:SPRING_PROFILES_ACTIVE`, `EZKEY_ENABLE_JMX`) are manipulated natively and clearly in PowerShell.
- **Security & reproducibility**: The recommended invocation includes `-ExecutionPolicy Bypass` for reproducible developer runs; this is explicit and documented for operators.
- **Cross-platform (PowerShell Core)**: `pwsh` (PowerShell Core) runs on Linux and macOS as well, which can simplify sharing scripts across environments when appropriate.
- **Lower operational risk**: Complex scenarios (parsing, encoding, timeouts, subprocess behavior) are fragile in `cmd`/batch; PowerShell reduces these risks and the support burden.

### Recommended command (Windows PowerShell)

Run from the repository root in a PowerShell session:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\\docker\\start.ps1
```

If you use PowerShell Core (`pwsh`):

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\\docker\\start.ps1
```

### Where to find this note

- This note is a technical reference for developers and operators. The project README files link to this document to explain the reasoning behind the recommendation.

---

_Created: 2026-01-13_

