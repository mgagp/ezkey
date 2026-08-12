# Ezkey Bruno collection

Git-native API exploratory surface for Admin, Auth, Integration, and Crypto APIs. Replaces the former Postman collections under `postman/`.

This is **operator / agent exploratory tooling**, not the contractual E2E gate (`ezkey-tests`).

## Install (Windows / macOS / Linux)

**Use local npm under this folder.** Do not use pnpm or Yarn here (Admin UI uses npm; mobile Yarn is unrelated). Prefer this over a global CLI install so the pin in `package.json` / `package-lock.json` is authoritative.

Requirements: **Node.js LTS ≥ 18** (same Node you use for Admin UI is fine).

```bash
cd bruno
npm install
npx bru --version
```

Optional personal convenience only: `npm install -g @usebruno/cli` — not the project contract.

Bruno **desktop** is separate: open this `bruno/` folder as a collection for GUI exploration.

## Environments

| File | Use |
|------|-----|
| `environments/local.bru` | Direct localhost ports (default) |
| `environments/local-via-caddy-proxy.bru` | Caddy proxy ports |
| `environments/local-ngrok.bru` | Ngrok / tunnel workflows |

Runtime secrets (`token`, `secretKey`, device keys, …) stay empty in git. Scripts populate them during a run. Never commit filled tokens.

Port map: [`docs/LOCAL_STACK_PORTS.md`](../docs/LOCAL_STACK_PORTS.md).

## CLI health (migration + ongoing smoke)

From the **repository root** (Git Bash on Windows):

```bash
./scripts/bruno-health.sh              # suite g0 (crypto + public)
./scripts/bruno-health.sh --suite g0
./scripts/bruno-health.sh --suite crypto
./scripts/bruno-health.sh --suite g4 --env-var token="$EZKEY_ADMIN_TOKEN"
```

From PowerShell:

```powershell
& "C:\Program Files\Git\bin\bash.exe" -lc './scripts/bruno-health.sh --suite g0'
```

Reports land under `logs/bruno/` (gitignored).

| Suite | What | Auth |
|-------|------|------|
| `g0` | `crypto` + public instance-info (Admin + Auth) | none |
| `g4` | One read-only happy-path per Admin catalog folder | bearer `token` / `EZKEY_ADMIN_TOKEN` |
| `all-catalog` | Full Admin catalog folders (mutating; exploratory) | bearer required |

Golden device chains (enrollments-auth / auth-attempts-auth) are run folder-by-folder after login + enrollment setup; see didactic parity doc.

## Folder map (former Postman collections)

Each former Postman collection is a folder (138 requests total), for example:

- `authentication-login-admin/` — passwordless login, wait, recover, logout
- `enrollments-auth/` — numbered Crypto-interleaved bind/verify
- `auth-attempts-auth/` — numbered pending/respond + Crypto oracle
- `crypto/` — Crypto API surface
- `auth-attempts-integration-api/` — M2M Basic auth
- Admin catalogs: `tenants-admin`, `integrations-admin`, `enrollments-admin`, …

## Maintainer notes

- When controllers/DTOs change, update the impacted `.bru` requests in the **same change set** (see root `AGENTS.md`).
- Prefer keeping scripts CLI-runnable (`./scripts/bruno-health.sh --suite g0` at minimum when touching Crypto/public).
- Historical Postman → Bru converter: `scripts/bruno-import-from-postman.js` (needs a Postman export tree from git history; not used in day-to-day work). Do not reintroduce `postman/` as source of truth.
