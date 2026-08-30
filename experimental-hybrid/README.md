# Experimental hybrid deployment (Lightsail + optional local)

This folder contains **operator-focused** artifacts to run Ezkey on **Amazon Lightsail** (Postgres, migration, Admin API, Auth API, Integration API, optional Demo ACME, **Caddy** with Let’s Encrypt) and optionally run **local** companions (Crypto API, Demo Device, Demo ACME) against the **public** HTTPS APIs.

- **Lightsail:** [`lightsail/`](lightsail/) — `docker-compose.yml`, `Caddyfile`, `.env.example`
- **Local (optional):** [`local/`](local/) — `docker-compose.yml`, `.env.example`
- **Runbook:** [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) — phases, **`~/ezkey` VM tree**, **`scp` from a dev clone** (default), optional clone-on-VM, Cloudflare split (manual vs repo)
- **Single-backend image update (Lightsail):** [`BACKEND_ROLLING_UPDATE.md`](BACKEND_ROLLING_UPDATE.md) — `docker save` / `scp` / `docker load` / `compose up --force-recreate`
- **Scripted export + optional clean-start or rolling (no DB wipe) + optional UI deploy:** [`scripts/export-backend-images-to-lightsail.sh`](scripts/export-backend-images-to-lightsail.sh) (`--sync-operator-files`, `--remote-up`), [`scripts/full-exp-environment-upgrade.sh`](scripts/full-exp-environment-upgrade.sh) subcommand **`rolling`** — see [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) *Phase 2b*
- **One-command stack upgrade (EXP1 / Lightsail, keeps Postgres data):** from repo root, **`./experimental-hybrid/scripts/full-exp-environment-upgrade.sh rolling`** — builds **migration + all backend APIs**, saves/loads images (including Flyway), copies **Caddyfile** + **`docker-compose.yml`** + `clean-start.sh` to the VM, then runs **`docker compose up -d`** remotely. Use **`rolling --include-demo-acme`** to add the ACME demo image to the build/export. This does **not** run `clean-start` and does **not** remove volumes.

The same Lightsail stack can also expose `demo-app-acme` behind a dedicated hostname such as `https://exp1-demo-acme.ezkey.org`. This keeps the public evaluation surface separate from the Admin UI and API hostnames while reusing the same Caddy + Docker operational model.

Image build targets and behaviour match the main repo [`docker/Dockerfile`](../docker/Dockerfile) and [`docker/docker-compose.yml`](../docker/docker-compose.yml).

## Default Lightsail SSH host: ezkey

For the **experimental hybrid** path, **`LIGHTSAIL_SSH_HOST`** in [`scripts/export-backend-images-to-lightsail.sh`](scripts/export-backend-images-to-lightsail.sh) and [`scripts/full-exp-environment-upgrade.sh`](scripts/full-exp-environment-upgrade.sh) **defaults to `ezkey`** (the `Host` in `~/.ssh/config` for this VM). Doc examples use the same name to keep steps short. *If your alias differs, set `LIGHTSAIL_SSH_HOST` when running those scripts.*

## Prerequisites

- Docker and Docker Compose on the **build machine** and on the **VM**. On a **new** Amazon Linux 2023 Lightsail instance, install Docker and add **`ec2-user`** to the **`docker`** group — see [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) **Phase 0**. On the VM, use a **non-root** login that can run Docker; [`lightsail/clean-start.sh`](lightsail/clean-start.sh) does not call `sudo` and checks `docker info` before running.
- **SSH** to the VM: default alias **`ezkey`** (see [above](#default-lightsail-ssh-host-ezkey)).
- DNS **A** records for your API hostnames pointing at the Lightsail **public** IP, **DNS only** (grey cloud) if Let’s Encrypt should reach Caddy directly
- Ports **80** and **443** open on the instance firewall for HTTP-01 / HTTPS

## Build and export images (dev machine)

From the **repository root**:

```bash
docker compose -f docker/docker-compose.yml build migration admin-api auth-api integration-api
```

Or build targets explicitly:

```bash
docker build -f docker/Dockerfile --target migration -t ezkey-migration:latest .
docker build -f docker/Dockerfile --target admin-api -t ezkey-admin-api:latest .
docker build -f docker/Dockerfile --target auth-api -t ezkey-auth-api:latest .
docker build -f docker/Dockerfile --target integration-api -t ezkey-integration-api:latest .
```

Save images to tar files (example destination):

```bash
mkdir -p docker/export
docker save -o docker/export/ezkey-migration.tar ezkey-migration:latest
docker save -o docker/export/ezkey-admin-api.tar ezkey-admin-api:latest
docker save -o docker/export/ezkey-auth-api.tar ezkey-auth-api:latest
docker save -o docker/export/ezkey-integration-api.tar ezkey-integration-api:latest
```

## Copy to the VM and load

```bash
scp docker/export/ezkey-migration.tar ezkey:~/
scp docker/export/ezkey-admin-api.tar ezkey:~/
scp docker/export/ezkey-auth-api.tar ezkey:~/
scp docker/export/ezkey-integration-api.tar ezkey:~/
```

On the VM:

```bash
docker load -i ~/ezkey-migration.tar
docker load -i ~/ezkey-admin-api.tar
docker load -i ~/ezkey-auth-api.tar
docker load -i ~/ezkey-integration-api.tar
```

To automate **save → scp → load**, use [`scripts/export-backend-images-to-lightsail.sh`](scripts/export-backend-images-to-lightsail.sh). Add **`--sync-operator-files`** to push **Caddy** + **Compose** files (no DB wipe), and **`--remote-up`** to run **`docker compose up -d`** on the VM after load (rolling apply: new **migration** image runs Flyway against the existing database). Do **not** combine those with **`--clean-start`** (destructive). Optional **`--include-demo-acme`**. For **`docker compose build`** + full rolling pipeline or **`full`** (destructive) + **Cloudflare** UI deploy, see [`scripts/full-exp-environment-upgrade.sh`](scripts/full-exp-environment-upgrade.sh) and [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) *Phase 2b*.

## Progressive introduction of the ACME demo (Lightsail, no database wipe)

Use this when the stack and Postgres data **already run** and you only add or update the public **ACME demo** (`exp1-demo-acme` in the default [`lightsail/Caddyfile`](lightsail/Caddyfile)). Do **not** use [`lightsail/clean-start.sh`](lightsail/clean-start.sh) or **`--clean-start`** on [`scripts/export-backend-images-to-lightsail.sh`](scripts/export-backend-images-to-lightsail.sh) if you need to keep existing volumes.

1. **Cloudflare DNS** — Add **`exp1-demo-acme.ezkey.org`** (or your zone) as an **A** record to the Lightsail public IP (or a **CNAME** to another `exp1-*` name that already points there). Enable **proxied (orange)** like the other experimental hosts. See [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) *Phase 0d* / *1b*.
2. **Origin certificate** — Include the demo FQDN in the **Cloudflare Origin Server** cert SAN list, redeploy **`origin.pem`** / **`origin-key.pem`** to **`lightsail/caddy-certs/`** on the VM, then recreate Caddy: `docker compose up -d --no-deps --force-recreate caddy` from [`lightsail/`](lightsail/) (see [`BACKEND_ROLLING_UPDATE.md`](BACKEND_ROLLING_UPDATE.md)). Without this, TLS from Cloudflare to the origin will fail for the new hostname.
3. **Build and transfer** — `docker build -f docker/Dockerfile --target demo-app-acme -t ezkey-demo-app-acme:latest .`, then run **`./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --include-demo-acme`** (optionally with **`--apis-only`** if you skip migration tars), or `docker save` / `scp` / `docker load` manually per [`BACKEND_ROLLING_UPDATE.md`](BACKEND_ROLLING_UPDATE.md).
4. **Sync files** — If the VM is older than the repo, **`scp`** current [`lightsail/docker-compose.yml`](lightsail/docker-compose.yml) and [`lightsail/Caddyfile`](lightsail/Caddyfile) to `~/ezkey/experimental-hybrid/lightsail/` (see [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) *VM initialization*). Do not run a destructive `clean-start`.
5. **Recreate services** — On the VM: `docker compose up -d --no-deps --force-recreate demo-app-acme` after `docker load`; add `--force-recreate caddy` if Caddyfile or TLS PEMs changed. See [`BACKEND_ROLLING_UPDATE.md`](BACKEND_ROLLING_UPDATE.md).
6. **App configuration** — Ensure `/app/data/acme-users.json` exists (volume **`demo-app-acme-data`**), set **API key** credentials for the **Integration API** (`EZKEY_INTEGRATION_KEY` / `EZKEY_SECRET_KEY` in `.env` or `demo-app-acme-config`), and IP whitelist the integration for the container network. Compose sets `EZKEY_ADMIN_API_URL` to `http://integration-api:7080` (the env name is historical; do not point it at Admin API). **`EZKEY_TRUSTED_PROXIES_CIDRS`** is set in [`lightsail/docker-compose.yml`](lightsail/docker-compose.yml) for the demo; align with [`lightsail/.env`](lightsail/.env.example) if you override. End-to-end login still needs a **device approval** path (e.g. mobile app) against your public Auth API.

## Run on Lightsail

**First-time VM layout:** use a single root **`~/ezkey`** on the instance that mirrors the repo (contains **`docker/`** and **`experimental-hybrid/lightsail/`**). The documented default is to **`scp`** from a machine that already has the **Git clone** (repo root → `ssh`/`mkdir`/`scp`); an optional **clone on the VM** is described in the same place. Full steps and migration from an older folder layout are in [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) (*VM initialization*).

1. After the tree exists, ensure [`lightsail/`](lightsail/) on the VM includes `docker-compose.yml`, `Caddyfile`, `.env`, and [`clean-start.sh`](lightsail/clean-start.sh) with [`docker/generate-encryption-keys.sh`](../docker/generate-encryption-keys.sh) at **`~/ezkey/docker/`** (required for [`clean-start.sh`](lightsail/clean-start.sh)).
2. Copy `lightsail/.env.example` to `lightsail/.env` and set **`EZKEY_QR_AUTH_BASE_URL`** to your **public** Auth API URL (must match the auth hostname in `Caddyfile`).
3. Edit **`lightsail/Caddyfile`** hostnames if yours differ from `exp1-*-api.ezkey.org`.
4. From `lightsail/`, start the stack **with encryption keys seeded** (pick one):
   - **Recommended for an empty / disposable VM:** `chmod +x clean-start.sh && ./clean-start.sh` — same idea as local `ezkey-tests/clean-start.sh`: wipes Compose volumes, generates master key into the correct Docker volume (`--experimental-lightsail`), then `docker compose up -d`. **Destructive** to the database; see script `--help`.
   - **Without wiping volumes:** `bash ../../docker/generate-encryption-keys.sh --experimental-lightsail --force` then `docker compose up -d`.
   - **Ad hoc:** `docker compose up -d` only, then follow **Phase 3b** in [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) to create `master.key` before relying on encryption.

**Updating one backend image** (new `docker save` / `docker load` without wiping the DB): see [`BACKEND_ROLLING_UPDATE.md`](BACKEND_ROLLING_UPDATE.md) — use `docker compose up -d --no-deps --force-recreate <service>` after `docker load`, not `restart` alone.

Caddy persists certificates and ACME state in the **`caddy-data`** volume (`/data` in the container).

Postgres is published only on **loopback** (`127.0.0.1:5432:5432`) for optional **SSH tunnel** access (e.g. DBeaver from your PC).

**TLS:** Caddy terminates HTTPS for the API hostnames and (if enabled) the ACME demo hostname with **TLS 1.3 only** (see [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md)). For the **Admin UI** on Cloudflare Pages, set **minimum TLS 1.3** in the Cloudflare zone — [`docs/cloudflare/admin-ui-pages.md`](../docs/cloudflare/admin-ui-pages.md).

### CORS (Admin UI on a different origin)

If the Admin UI is served from another hostname (e.g. `https://exp1-admin-ui.ezkey.org`) than the Admin API, set **`EZKEY_ADMIN_CORS_ALLOWED_ORIGINS`** in `.env` to that exact origin (comma-separated if several). Maps to `ezkey.admin.cors.allowed-origins` — see [`ezkey-admin-api/CONFIGURATION.md`](../ezkey-admin-api/CONFIGURATION.md) (section 11).

### Spring / Ezkey configuration

Environment variables follow the main Docker stack. See:

- [`ezkey-admin-api/CONFIGURATION.md`](../ezkey-admin-api/CONFIGURATION.md)
- [`ezkey-auth-api/CONFIGURATION.md`](../ezkey-auth-api/CONFIGURATION.md)
- [`ezkey-integration-api/CONFIGURATION.md`](../ezkey-integration-api/CONFIGURATION.md)

Set **`EZKEY_TRUSTED_PROXIES_CIDRS`** (comma-separated CIDRs) so Spring trusts `CF-Connecting-IP` / `X-Forwarded-For` when the direct peer is Caddy on the Docker network (see `lightsail/.env.example`).

## Optional local stack (`local/`)

Use when APIs run on Lightsail but you want **Crypto API**, **Demo Device**, or **Demo ACME** on your workstation.

1. Copy `local/.env.example` to `local/.env` and set **`EZKEY_AUTH_API_URL`** to the public Auth API URL and **`EZKEY_ADMIN_API_URL`** to the public **Integration API** URL (`https://exp1-integration-api.ezkey.org` — the env name is historical; ACME uses it as the M2M base).
2. **Crypto API** needs the same **`/etc/ezkey`** key material as the VM. Copy the directory from the VM into `local/encryption-secrets-local/` (see below), then set `EZKEY_ENCRYPTION_SECRETS_DIR` if you use a different path.
3. From `local/`:

```bash
docker compose up -d --build
```

### Sync encryption secrets (hybrid: crypto-api local)

On the VM (container name from [`lightsail/docker-compose.yml`](lightsail/docker-compose.yml)):

```bash
docker cp ezkey-exp-admin-api:/etc/ezkey/. ./encryption-secrets-local/
```

Copy the resulting directory to your PC at `experimental-hybrid/local/encryption-secrets-local/` (gitignored).

When **all** APIs run on the same VM, they share the **`encryption-secrets`** Docker volume — **no** manual sync between Admin, Auth, and Integration on Lightsail.

## Validation

- `https://<admin-host>/` — Admin API via Caddy (use actuator paths as documented for the Admin API)
- `https://<auth-host>/` — Auth API
- `https://<integration-host>/` — Integration API
- After `docker compose down` / `up`, HTTPS still works (Caddy **`caddy-data`** volume present)

## OpenAPI

Do not edit generated specs under `specs/` by hand. Regenerate from running services per project workflow after API changes.

For the public `EXP1` hostnames, raw Springdoc routes are intentionally not the public documentation surface:

- `/api-docs`
- `/swagger-ui`
- `/swagger-ui.html`

The Lightsail `Caddyfile` blocks those routes on the public API hostnames so the intended public reference posture stays on `ezkey.org/api-docs.html` and the per-API portal pages.

## Lab framing (not September release)

EXP1 is a **controlled experimental lab**, not the September 2026 operable-release path. Audience:
a small technical cohort, APK side-load, learning Cloudflare / Lightsail / Caddy / ops — not
multi-region HA or Play distribution. Keep EXP1 facts in this folder. Do **not** fold hostnames,
email templates, or database-topology debate into `docs/` or `docker/README.md`.

**Topology in use:** Cloudflare (proxied) in front of a public Lightsail origin with Caddy TLS and
trusted proxies. That is the filtered-origin path. A masked origin (dedicated tunnel) is not the
current lab.

**Data:** Postgres stays in the Lightsail Docker stack until backup/restore pain justifies a
managed database.

**Onboarding:** Do not revive “recovery-codes-first deferred enrollment.” Activation-code admin
onboarding is the shipped path. Anonymous evaluator signup (empty tenant + activation code) is
`I-2026-05-23` — keep that idea; do not fork a second enrollment story here.

**Operator runbook:** this README + [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md).
