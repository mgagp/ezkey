# Deployment playbook — experimental hybrid (Lightsail + Cloudflare + optional local)

This document splits **what you do manually** (account, DNS, secrets, smoke tests) from **what lives in the repo** (compose, Caddyfile, env templates).

## Phase 1 — Repo artifacts (maintained in Git)

- `lightsail/docker-compose.yml` — services, volumes, healthchecks
- `lightsail/clean-start.sh` — **optional operator script** on the VM: full stack reset (`docker compose down -v`), seed encryption keys in the correct Compose volume via [`docker/generate-encryption-keys.sh`](../docker/generate-encryption-keys.sh) (`--experimental-lightsail`), then `docker compose up -d` (same role as local `ezkey-tests/clean-start.sh`, but for this cloud stack; **destructive** to volumes)
- `lightsail/Caddyfile` — public hostnames → internal services (TLS via Caddy / Let’s Encrypt; **TLS 1.3 only** at the edge)
- `lightsail/.env.example` — copy to `.env` on the VM; **no secrets committed**
- `local/docker-compose.yml` — optional Crypto / Demo Device / Demo ACME
- `local/.env.example` — public API base URLs for local companions
- `README.md` — build, `docker save`, `scp`, `docker load`, run commands

## VM initialization — `~/ezkey` tree, `scp`, and remote commands

Use a **single top-level directory on the VM** (here **`~/ezkey`**) that matches the **repository layout**: the root must contain both **`docker/`** (for [`docker/generate-encryption-keys.sh`](../docker/generate-encryption-keys.sh)) and **`experimental-hybrid/`** (for [`lightsail/clean-start.sh`](lightsail/clean-start.sh), which resolves `../../docker/...` from `experimental-hybrid/lightsail/`). That layout matches a **full Git clone** of Ezkey at `~/ezkey`, but **this runbook assumes you populate the VM from a development workstation** that already has the repo cloned (build images there, run `scp` from the repo root). You do **not** need Git on the Lightsail instance for the default path.

### Canonical directory tree (operator view)

```text
~/ezkey/                              # same role as repo root on your dev machine
├── docker/
│   └── generate-encryption-keys.sh   # required for clean-start / --experimental-lightsail
└── experimental-hybrid/
    ├── DEPLOYMENT_PLAYBOOK.md        # optional copy for offline reading
    ├── README.md
    ├── lightsail/
    │   ├── .env                      # from .env.example; not committed
    │   ├── .env.example
    │   ├── Caddyfile
    │   ├── clean-start.sh            # chmod +x
    │   └── docker-compose.yml
    └── local/                        # optional; hybrid companions on your PC (see Phase 5)
        └── …
```

**Operational rule:** run **`docker compose`** and **`./clean-start.sh`** only from **`~/ezkey/experimental-hybrid/lightsail/`** so paths and Compose project name (`name: ezkey-experimental-lightsail` in the compose file) stay consistent.

### Default path — from your PC (Git clone on the workstation, `scp` to the VM)

**Prerequisite:** clone the Ezkey repository on your **development machine** and use a shell whose working directory is the **repository root** (same level as `docker/` and `experimental-hybrid/`). Replace **`ezkey`** with your SSH host alias (`Host ezkey` in `~/.ssh/config`).

**1. Create the tree on the VM**

```bash
ssh ezkey "mkdir -p ezkey/docker ezkey/experimental-hybrid/lightsail"
```

**2. Copy the files `clean-start.sh` depends on**

```bash
scp docker/generate-encryption-keys.sh ezkey:ezkey/docker/
scp experimental-hybrid/lightsail/clean-start.sh ezkey:ezkey/experimental-hybrid/lightsail/
scp experimental-hybrid/lightsail/docker-compose.yml ezkey:ezkey/experimental-hybrid/lightsail/
scp experimental-hybrid/lightsail/Caddyfile ezkey:ezkey/experimental-hybrid/lightsail/
scp experimental-hybrid/lightsail/.env.example ezkey:ezkey/experimental-hybrid/lightsail/
```

**3. On the VM — finalize env and permissions**

```bash
ssh ezkey
cd ~/ezkey/experimental-hybrid/lightsail
cp .env.example .env
# Edit .env (editor of your choice)
chmod +x clean-start.sh
```

**4. Optional — copy the whole `local/` tree** (if you use hybrid local companions later):

```bash
scp -r experimental-hybrid/local ezkey:ezkey/experimental-hybrid/
```

**5. Optional — documentation** in `~/ezkey/experimental-hybrid/` (`README.md`, this playbook) can be copied the same way if you want them on the server.

Then continue with **Phase 2** (images) and **Phase 3** (start / clean-start).

### Alternative — Git clone directly on the VM

If you prefer **not** to use `scp` from a PC, you may install **Git** on the instance and clone the repository into **`~/ezkey`**. The resulting tree matches the diagram above; then `cp .env.example .env`, edit `.env`, and `chmod +x experimental-hybrid/lightsail/clean-start.sh`. Image build and transfer (**Phase 2**) still typically happen on a workstation with Docker, unless you build on the VM by choice.

### Migrating from an older layout

If you previously kept **`experimental-hybrid/`** at the **home directory root** (without `~/ezkey`), move or copy **`lightsail/`** contents (at least `.env`, `Caddyfile`, `docker-compose.yml`) into **`~/ezkey/experimental-hybrid/lightsail/`**, and ensure **`~/ezkey/docker/generate-encryption-keys.sh`** exists. Remove or archive the old tree once you confirm the new paths work to avoid editing the wrong `.env`.

### After initialization

- **Phase 2:** load container images (`docker load` / tars under `~/` or a chosen directory).
- **Phase 3:** from **`~/ezkey/experimental-hybrid/lightsail`**, run **`./clean-start.sh`** (full reset + encryption seed) or follow the non-wipe paths described there.

## Phase 2 — You: build and transfer images

**Docker on the VM (non-root operator):** day-to-day `docker compose` / `docker load` / [`lightsail/clean-start.sh`](lightsail/clean-start.sh) should run as a **normal** user with access to the Docker socket — typically add that user to the **`docker`** group once (`sudo usermod -aG docker "$USER"`, then re-login). You do **not** need to run these scripts as **root** on the host; the stack matches the usual “non-root human + Docker group” practice.

1. Build images from repo root (`docker compose build` or `docker build --target …`).
2. `docker save` → tar files.
3. `scp` tar files to the VM (e.g. `scp … ezkey:~/`).
4. On the VM: `docker load`.

**Rolling update (single API or migration image, keep data):** after loading a new tar, **recreate** the service so `:latest` is picked up — see [`BACKEND_ROLLING_UPDATE.md`](BACKEND_ROLLING_UPDATE.md). A plain `docker compose restart` does not switch the container to a newly loaded image.

## Phase 3 — You: VM configuration

1. Ensure the **VM directory layout** matches the **VM initialization** section above (`~/ezkey` with `docker/` and `experimental-hybrid/lightsail/`).
2. Create `lightsail/.env` from `.env.example`; set **`EZKEY_QR_AUTH_BASE_URL`** to the **public** `https://` Auth API base (must match DNS + `Caddyfile`).
3. Edit **`Caddyfile`** hostnames if they differ from the examples.
4. Ensure **80** and **443** are allowed on the instance network.
5. Start the stack **with encryption enabled from the first API startup** using **one** of:
   - **Full reset (empty slate or “like clean-start”):** from `lightsail/`, run `./clean-start.sh` (see script header for `--no-down` / `--no-keygen`). This runs `docker compose down -v` (removes **Postgres and all** named volumes), generates the master key into `ezkey-experimental-lightsail_encryption-secrets`, then `docker compose up -d`. **Do not** use this if you need to keep existing DB data.
   - **Bring up without wiping:** `bash ../../docker/generate-encryption-keys.sh --experimental-lightsail --force` then `docker compose up -d` (requires the repo `docker/` tree on the VM). The generator creates the volume if needed **before** APIs start.
   - **Minimal:** `docker compose up -d` only — then you **must** seed keys (Phase 3b manual) before relying on encryption at rest.

### Phase 3b — Encryption secrets (Docker volume `/etc/ezkey`)

**Why:** Admin, Auth, and Integration APIs mount the **`encryption-secrets`** volume at **`/etc/ezkey`**. Without **`/etc/ezkey/secrets/master.key`**, Tink logs *Master key file not found. Encryption will be disabled* and sensitive columns stay **plaintext** in Postgres.

**Do not rely on** [`scripts/generate-master-key.sh`](../scripts/generate-master-key.sh) **as-is on the VM host** for this stack: that script writes to the **host** filesystem (`/etc/ezkey`, user `ezkey`). Compose expects key material **inside the named volume** consumed by the containers (runtime user **`spring`**). Prefer **`docker/generate-encryption-keys.sh --experimental-lightsail`** (used by `lightsail/clean-start.sh`) or the manual steps below.

**Manual seeding** (e.g. stack already running and you cannot wipe volumes — adjust container name if needed):

1. Create the master key **inside** the Admin API container (adjust the container name if yours differs from `ezkey-exp-admin-api`):

```bash
docker exec -u 0 ezkey-exp-admin-api sh -c '
  apk add --no-cache openssl
  mkdir -p /etc/ezkey/secrets /etc/ezkey/keysets
  if [ ! -s /etc/ezkey/secrets/master.key ]; then
    openssl rand -base64 32 > /etc/ezkey/secrets/master.key
    chmod 600 /etc/ezkey/secrets/master.key
  fi
  chown -R spring:spring /etc/ezkey
'
```

2. Restart the three API services so they all reload the shared volume:

```bash
docker compose restart admin-api auth-api integration-api
```

3. Confirm in logs: look for **Tink encryption ready** (or equivalent) and **no** *Encryption will be disabled* warning from `TinkKeyManager` on **admin-api** at least.

4. **Backup** `master.key` securely (e.g. `docker cp ezkey-exp-admin-api:/etc/ezkey/secrets/master.key` to an encrypted store). Loss of this file with no backup means loss of ability to decrypt existing ciphertext.

5. **Optional — audit HMAC key:** Docker profile expects [`/etc/ezkey/secrets/audit-hmac.key`](../ezkey-admin-api/config/application-docker.properties). If missing, the application may **generate** one at startup (see `AuditHmacService`); you can also create a second random file the same way as `master.key` under a different filename if you prefer provisioning it explicitly.

6. **Data written while encryption was off:** Once the master key exists and services are healthy, configured **re-encryption** jobs can encrypt existing rows; see [`ezkey-admin-api/CONFIGURATION.md`](../ezkey-admin-api/CONFIGURATION.md) (`ezkey.encryption.reencryption.*`). Plan a follow-up verification pass on representative tables or logs.

## TLS 1.3 only (opinionated edge)

Ezkey’s experimental hybrid stack is **opinionated**: public HTTPS for the **three API hostnames** (Auth, Admin API, Integration API) is terminated by **Caddy** with **TLS 1.3 only** — no TLS 1.2. Older HTTP clients cannot negotiate a connection. This matches a greenfield posture (modern browsers and mobile OS stacks).

**Implemented in repo:** [`lightsail/Caddyfile`](lightsail/Caddyfile) — each site block includes:

```caddy
tls {
    protocols tls1.3 tls1.3
}
```

**Admin UI (Cloudflare Pages):** the SPA is served over HTTPS by **Cloudflare**, not by this Caddy instance. Set **Minimum TLS Version** to **1.3** in the Cloudflare **SSL/TLS** settings for the **zone** (and any custom domain used for Pages) so the browser↔Cloudflare leg matches the same policy. See also [`docs/cloudflare/admin-ui-pages.md`](../docs/cloudflare/admin-ui-pages.md) (*TLS version*).

**Verify after deploy:** `openssl s_client -connect <host>:443 -tls1_2` should fail to negotiate; `-tls1_3` should succeed. Reload Caddy after editing the Caddyfile: `docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile` (from `lightsail/`), or restart the `caddy` container.

## Phase 4 — You: Cloudflare and Admin UI (complement)

| Action | Owner |
|--------|--------|
| Create/verify **DNS** A (or CNAME) records for each API hostname → Lightsail public IP | You |
| Keep records **DNS only** (grey) while using direct Let’s Encrypt to the origin | You |
| Cloudflare **Pages** (or Workers) for Admin UI: set build env (e.g. `VITE_API_BASE_URL` → public Admin API URL); **SSL/TLS** minimum version **1.3** for the zone / custom domain (see *TLS 1.3 only* above) | You |
| API **tokens** / dashboard login — never commit to the repo | You |
| Confirm **CORS** on Admin API allows your Pages **origin** (see Java CORS configuration / tests in repo) | You + code changes in repo when needed |
| Smoke test from browser and mobile against **public** Auth URL | You |

Reference script pattern (do not commit secrets): [`scripts/cloudflare/deploy-admin-ui-preview.sh`](../scripts/cloudflare/deploy-admin-ui-preview.sh).

## Phase 5 — Optional local companions

1. Configure `local/.env` with public `EZKEY_AUTH_API_URL` / `EZKEY_ADMIN_API_URL`.
2. Sync **`/etc/ezkey`** from the VM for **crypto-api** if you run it locally (`README.md`).
3. `docker compose up -d --build` from `local/`.

## Phase 6 — Checkpoint

- Confirm HTTPS on all three API hostnames (**TLS 1.3 only** to origin — see *TLS 1.3 only* section).
- Confirm Admin UI (if on Cloudflare) talks to the Admin API with CORS OK.
- Confirm **encryption at rest** is active (no *Master key file not found* / *Encryption will be disabled* from `TinkKeyManager` after Phase 3b).
- If using SSH tunnel to Postgres (`localhost:6432` on PC), confirm tunnel and optional DBeaver connectivity.

## Automation vs manual (summary)

| Automated / versioned in repo | Manual (operator) |
|------------------------------|---------------------|
| Compose, Caddyfile (TLS 1.3 only on API hostnames), `.env.example` | Cloudflare login, API tokens, DNS UI, **minimum TLS 1.3** for Pages zone |
| Build instructions | `docker save` / `scp` / `docker load` timing |
| CORS / Java changes when implemented in PRs | Choosing hostnames and TLS/DNS-only policy |
| `lightsail/clean-start.sh`, `docker/generate-encryption-keys.sh --experimental-lightsail` | Run on VM; choosing wipe vs preserve DB |
| Repo `scripts/generate-master-key.*` (host-oriented) | Bare-metal paths only; not sufficient by itself for Compose volume |
| | Phone / real-device smoke tests |
