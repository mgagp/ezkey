# Deployment playbook — experimental hybrid (Lightsail + Cloudflare)

This document records the **target path** for a disposable Ezkey stack on **Amazon Lightsail** with **Cloudflare** in front (proxied / orange), **TLS 1.3** on the APIs, and **Admin UI** on **Cloudflare Pages**. It separates **operator actions** (DNS, secrets, dashboard) from **repo artifacts** (Compose, Caddyfile, scripts).

**Traffic model:** browser → **Cloudflare** (public TLS) → **Lightsail** (Caddy terminates TLS to the origin using a **Cloudflare Origin Certificate**) → **Spring APIs** in Docker. Postgres is not exposed publicly.

**SSH (experimental hybrid):** scripts use **`LIGHTSAIL_SSH_HOST`**, default **`ezkey`**. See [`README.md`](README.md#default-lightsail-ssh-host-ezkey).

---

## Phase 0 — Fresh Lightsail instance (Amazon Linux 2023): Docker and `ec2-user`

Use when the VM is **new** or rebuilt (new disk, new public IPv4). Default user: **`ec2-user`**. Day-to-day Docker commands should run **without** `sudo` after group setup.

### 0a — Lightsail networking

In **Lightsail** → instance → **Networking** → **IPv4 firewall**:

- **SSH (22)** — restrict to your IP or bastion if you can.
- **HTTPS (443)** — required. **Recommended** with Cloudflare orange: allow **only** [Cloudflare IPv4 ranges](https://www.cloudflare.com/ips-v4) (and IPv6 if you use it) so only Cloudflare can reach the origin. Maintain the list when Cloudflare updates it.
- **HTTP (80)** — **optional** for this model. With **SSL/TLS = Full (strict)** and **Origin CA** on Caddy, Cloudflare talks to the origin on **443**. You may **omit 80** on the instance firewall to reduce surface area (no HTTP-01 Let’s Encrypt on the origin in the default path).

### 0b — Install Docker and Compose (v2)

```bash
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo docker info >/dev/null && echo "Docker daemon OK"
```

On many AMIs, **`docker-compose-plugin`** is missing from `dnf`. If `docker compose version` fails, install the **Compose v2** CLI plugin:

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

Ezkey expects **`docker compose`** (v2), not the old `docker-compose` binary name.

### 0c — `ec2-user` in the `docker` group

```bash
sudo usermod -aG docker ec2-user
```

New SSH session or `newgrp docker`, then `docker info` and `docker compose version` **without** sudo.

### 0d — Public IP and DNS

After a rebuild, set each API **A** record in Cloudflare to the **new** Lightsail IPv4 before expecting traffic to work.

---

## Phase 1 — Repo artifacts (Git)

| Artifact | Role |
|----------|------|
| [`lightsail/docker-compose.yml`](lightsail/docker-compose.yml) | Stack: Postgres, migration, APIs, Caddy |
| [`lightsail/Caddyfile`](lightsail/Caddyfile) | Hostnames → `reverse_proxy`; **TLS 1.3 only**; cert files: `tls /etc/caddy/certs/origin.pem /etc/caddy/certs/origin-key.pem` |
| [`lightsail/caddy-certs/`](lightsail/caddy-certs/) | On the VM only: **Origin CA** PEMs from Cloudflare (**not** in git; `.gitignore` keeps the folder) |
| [`lightsail/clean-start.sh`](lightsail/clean-start.sh) | Optional **destructive** reset: `down -v`, keygen, `up -d` |
| [`lightsail/.env.example`](lightsail/.env.example) | Template for `lightsail/.env` |
| [`scripts/export-backend-images-to-lightsail.sh`](scripts/export-backend-images-to-lightsail.sh) | `docker save` → `scp` → `docker load`; optional **`--include-demo-acme`**. **`--sync-operator-files`**: scp `docker-compose.yml`, `Caddyfile`, `clean-start.sh` without volume wipe. **`--remote-up`**: `docker compose up -d` on the VM (rolling stack refresh). Incompatible with `--clean-start` (use one path or the other). With `--clean-start`, syncs then runs `clean-start.sh` (destructive) |
| [`scripts/full-exp-environment-upgrade.sh`](scripts/full-exp-environment-upgrade.sh) | Optional: local build + export + clean-start + Pages deploy |

---

## Phase 1b — Cloudflare: Origin CA, orange proxy, SSL

**Goal:** Browsers trust **Cloudflare’s** certificate; the origin trusts **Cloudflare Origin CA** (issued in the dashboard, long-lived).

1. **SSL/TLS** → **Full** or **Full (strict)** (strict once the origin presents the Origin cert correctly).
2. **Origin Server** → **Create certificate** → hostnames: your **API** FQDNs (e.g. `exp1-auth-api`, `exp1-admin-api`, `exp1-integration-api` under your zone) and, if you use it, the **ACME demo** hostname (e.g. `exp1-demo-acme.ezkey.org` — see [`lightsail/Caddyfile`](lightsail/Caddyfile)) so Caddy can present the cert for that site block. Save **`origin.pem`** and **`origin-key.pem`** locally (never commit). Re-issue the Origin cert when you add a new hostname.
3. On the VM: `mkdir -p ~/ezkey/experimental-hybrid/lightsail/caddy-certs`, `chmod 700`, copy PEMs, `chmod 600` on the key.
4. **DNS:** set API **A** records to the Lightsail IP, then enable **proxied (orange)**.
5. **HSTS** (optional): start with a **short** `max-age` or disable until stable; add **`includeSubDomains`** / **preload** only when the whole zone is ready. Enable **No-Sniff** if offered in the same UI.
6. **Let’s Encrypt on the origin:** not used in this path. Repeated `docker compose down -v` **without** preserving `caddy-data` was what burned **Let’s Encrypt production** rate limits when Caddy used automatic LE; **Origin CA** avoids that. Keep **`caddy-data`** if you still use local Caddy state; TLS for public names is primarily at Cloudflare.

---

## VM initialization — `~/ezkey` layout

Single directory **`~/ezkey`** matching the repo: `docker/generate-encryption-keys.sh` and `experimental-hybrid/lightsail/`. **Git on the VM is optional** if you copy from a workstation.

### Directory tree

```text
~/ezkey/
├── docker/
│   └── generate-encryption-keys.sh
└── experimental-hybrid/
    └── lightsail/
        ├── .env                 # from .env.example
        ├── .env.example
        ├── docker-compose.yml
        ├── Caddyfile
        ├── clean-start.sh
        └── caddy-certs/         # origin.pem, origin-key.pem (VM only)
```

Run **`docker compose`** and **`./clean-start.sh`** only from **`~/ezkey/experimental-hybrid/lightsail/`**.

### Copy from your PC (repo root; default SSH `Host` name is `ezkey` — see README)

```bash
ssh ezkey "mkdir -p ezkey/docker ezkey/experimental-hybrid/lightsail/caddy-certs"

scp docker/generate-encryption-keys.sh ezkey:ezkey/docker/
scp experimental-hybrid/lightsail/clean-start.sh ezkey:ezkey/experimental-hybrid/lightsail/
scp experimental-hybrid/lightsail/docker-compose.yml ezkey:ezkey/experimental-hybrid/lightsail/
scp experimental-hybrid/lightsail/Caddyfile ezkey:ezkey/experimental-hybrid/lightsail/
scp experimental-hybrid/lightsail/.env.example ezkey:ezkey/experimental-hybrid/lightsail/

scp origin.pem origin-key.pem ezkey:ezkey/experimental-hybrid/lightsail/caddy-certs/
ssh ezkey "chmod 700 ezkey/experimental-hybrid/lightsail/caddy-certs && chmod 600 ezkey/experimental-hybrid/lightsail/caddy-certs/origin-key.pem"
```

### On the VM

```bash
cd ~/ezkey/experimental-hybrid/lightsail
cp .env.example .env
# Edit .env — see .env.example; required: EZKEY_TRUSTED_PROXIES_CIDRS, CORS, URLs, etc.
chmod +x clean-start.sh
```

**`EZKEY_TRUSTED_PROXIES_CIDRS`:** comma-separated CIDRs that include the **Docker bridge** (see `.env.example`). Spring maps this to `ezkey.trusted-proxies.cidrs`. With **orange** Cloudflare, the path is **browser → Cloudflare → Caddy → API**; Caddy is the direct TCP peer to Spring, so those CIDRs must cover Caddy’s network. The apps then read **`CF-Connecting-IP`** / **`X-Forwarded-For`** for the real client. **If audit logs show only Caddy’s IP**, the variable is missing or wrong in the container — recreate the API services after fixing `.env`.

Then **Phase 2** (images) and **Phase 3** (start).

---

## Phase 2 — Build and transfer images

1. On the workstation: `docker compose -f docker/docker-compose.yml build migration admin-api auth-api integration-api` (or targets you need). For the **ACME demo** image: `docker build -f docker/Dockerfile --target demo-app-acme -t ezkey-demo-app-acme:latest .`
2. `docker save` → tars; `scp` to VM; on VM: `docker load`.

**Scripted:** [`scripts/export-backend-images-to-lightsail.sh`](scripts/export-backend-images-to-lightsail.sh) — add **`--include-demo-acme`** to push `ezkey-demo-app-acme` tars in the same pass (after a local `demo-app-acme` build). For a **no-wipe** refresh of Caddy, Compose, and all loaded images: **`--sync-operator-files --remote-up`** (optionally with **`--include-demo-acme`**). Use **`--clean-start`** only for a **destructive** VM reset (do not mix with `--sync-operator-files` or `--remote-up`). **`caddy-certs/` and `.env` are not copied** by the script (secrets / operator files).

**Presets — [`scripts/full-exp-environment-upgrade.sh`](scripts/full-exp-environment-upgrade.sh):** subcommand **`rolling`** = build (migration + APIs) + export + remove remote tars + **`--sync-operator-files` + `--remote-up`** (single delegated command, DB preserved, Flyway from new image). Add **`--include-demo-acme`** for the ACME demo. Subcommand **`full`** or no args = destructive build + export + `clean-start` + UI deploy. See script **`--help`**.

**Per-service or manual rolling:** [`BACKEND_ROLLING_UPDATE.md`](BACKEND_ROLLING_UPDATE.md).

---

## Phase 3 — Start stack and encryption

1. Layout and `lightsail/.env` as above; **`EZKEY_QR_AUTH_BASE_URL`** = public `https://` Auth API base.
2. **Caddyfile** hostnames must match DNS.
3. **First start / disposable DB:** from `lightsail/`, `./clean-start.sh` (wipes volumes — see script `--help`).
4. **Encryption:** keys live in the **`encryption-secrets`** volume; use `clean-start` or [`docker/generate-encryption-keys.sh --experimental-lightsail`](../docker/generate-encryption-keys.sh). Details: **Phase 3b** below (manual seeding if you did not wipe).

### Phase 3b — Encryption secrets (`/etc/ezkey` volume)

Without **`/etc/ezkey/secrets/master.key`**, Tink may stay disabled. Prefer **`clean-start`** or **`generate-encryption-keys.sh --experimental-lightsail`** on the VM. Do **not** use [`scripts/generate-master-key.sh`](../scripts/generate-master-key.sh) as-is for this Compose layout (host paths differ). Manual seed example and re-encryption notes remain as in the previous playbook revision — see [`ezkey-admin-api/CONFIGURATION.md`](../ezkey-admin-api/CONFIGURATION.md).

---

## TLS 1.3 (API hostnames on Caddy)

[`lightsail/Caddyfile`](lightsail/Caddyfile) uses **TLS 1.3 only** on the origin certificate block. **Public users** see **Cloudflare’s** TLS when DNS is orange; **Cloudflare → origin** uses your **Origin CA** + Full (strict).

**Admin UI (Pages):** set zone **Minimum TLS 1.3** for the UI hostname. See [`docs/cloudflare/admin-ui-pages.md`](../docs/cloudflare/admin-ui-pages.md).

**Check:** `openssl s_client -connect <api-host>:443 -tls1_2` should fail; `-tls1_3` should succeed when hitting the origin or through CF as applicable.

---

## Phase 4 — Cloudflare Pages, CORS, cookies, smoke tests

| Action | Owner |
|--------|--------|
| **A** records for APIs → Lightsail IP, **proxied** | You |
| **SSL/TLS** Full (strict), **Origin CA** on Caddy | You |
| **Pages** Admin UI: **one Pages project per instance** (see [`docs/cloudflare/admin-ui-pages.md`](../docs/cloudflare/admin-ui-pages.md) §0); per project: `VITE_API_BASE_URL`, optional `VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE=true` (repo root `.env` for script deploys) | You |
| Admin API **CORS** origins + **`EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS`** when using cookie sessions | `.env` on VM |
| **API tokens** — never commit | You |
| `curl -sI https://<admin-api>/api/v1/public/instance-info` → **200**, `Server: cloudflare` | You |
| Audit log **client IP** = real visitor (not Docker Caddy IP) when **`EZKEY_TRUSTED_PROXIES_CIDRS`** is set | You |

---

## Phase 5 — Optional local companions

[`local/docker-compose.yml`](local/docker-compose.yml) — see [`README.md`](README.md).

---

## Phase 6 — Checkpoint

- HTTPS on all three API hostnames through **Cloudflare**; origin presents **Origin CA** to Cloudflare.
- **Encryption** active (no permanent *Encryption will be disabled* from Tink on admin-api).
- **Audit** shows **client** IPs (trusted proxy + headers).
- Admin UI login and API **CORS** / **cookie** mode as configured.

---

## Automation vs manual (summary)

| In repo / scripted | Manual (operator) |
|--------------------|-------------------|
| Compose, Caddyfile, `.env.example`, export / full-upgrade scripts | Cloudflare account, Origin CA creation, DNS orange, firewall CIDRs, HSTS choices |
| `clean-start.sh`, `generate-encryption-keys.sh` | Run on VM; wipe vs keep DB |
| | `caddy-certs` PEM placement, root `.env` for Pages deploy |
| | Device / browser smoke tests |
