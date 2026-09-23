# Community Lightsail host (ezkey.online) — create & bootstrap

Short runbook for creating a **NEW** Amazon Lightsail VM for the **ezkey.online** community-host evaluation path. This is **parallel to EXP1** (`exp1-ezkey` / `*.ezkey.org`). It is **not** a public launch by itself and does **not** shut down or rebrand live EXP1.

**Stack model:** same all-in-one Docker VM as today’s experimental hybrid (Postgres + APIs + Caddy on one Lightsail instance). App layer stays in [`experimental-hybrid/lightsail/`](../../experimental-hybrid/lightsail/) — do not fork Compose/Caddy for this v1.

**Automation style:** AWS CLI only (no Terraform, no CloudFormation), thin Bash under [`scripts/lightsail/`](../../scripts/lightsail/), Git Bash friendly, dry-run by default for create / delete / ports.

**Public cutover context (do not invent product language):** publish repositioned **ezkey.org** first; then community host on **ezkey.online**. Creating a parallel Lightsail instance for testing is OK.

---

## Ownership boundary (monorepo now, extractable later)

Starting these scripts in the **monorepo** is intentional and pragmatic (one clone, reuse the EXP1 image-export path). Longer-term, the **Marc-operated canonical community host** (`ezkey.online`) may move its *operator* scripting into a separate **private** GitHub repo: that instance is owned/operated by Marc and is **not** the same story as the public OSS “how to stand up an experimental hybrid” path (which stays in [`experimental-hybrid/`](../../experimental-hybrid/), like EXP1).

| Layer | Lives in | Owns |
|-------|----------|------|
| **VM lifecycle** | [`scripts/lightsail/`](../../scripts/lightsail/) (+ this runbook) | create / ports / bootstrap Docker / status / delete |
| **App stack (OSS)** | [`experimental-hybrid/lightsail/`](../../experimental-hybrid/lightsail/) + [`export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh) | Compose, Caddyfile, clean-start, image save/scp/load |

**Extraction note:** after e2e validation, `scripts/lightsail/` + this runbook may be copied into a private ops repo with minimal edits. Keep the tree **self-contained**. Do **not** scatter community-only secrets, ezkey.online-only DNS checklists, or laptop-specific absolute paths into the public product compose tree beyond the existing OSS pattern (env vars, SSH Host aliases, gitignored PEMs / `.env` on the operator machine).

This PR does **not** create that private repo.

---

## Defaults (match EXP1 shape, new name)

| Setting | Default | Override |
|---------|---------|----------|
| AWS CLI profile | `ezkey-lightsail` | `AWS_PROFILE` |
| Region | `ca-central-1` | `LIGHTSAIL_REGION` / `AWS_REGION` |
| Instance name | **`ezkey-online`** | `--name` / `LIGHTSAIL_INSTANCE_NAME` (use **`community-ezkey`** if the preferred name is taken) |
| Blueprint | `amazon_linux_2023` | `--blueprint` |
| Bundle | `medium_3_0` | `--bundle` |
| AZ | `ca-central-1a` | `--az` |
| Key pair name | **`ezkey-online-kp`** | `--key-pair-name` / `LIGHTSAIL_KEY_PAIR_NAME` |
| SSH Host alias | `ezkey-online` | `LIGHTSAIL_SSH_HOST` / `--host` |

**Lightsail name uniqueness:** resource names are unique **across types**. The key-pair name **must differ** from the instance name (e.g. instance `ezkey-online` + key pair `ezkey-online-kp`). Using the same string for both fails `CreateInstances` with `InvalidInputException` (“names are already in use”). `create-instance.sh` refuses that combination.

**Live EXP1 (leave alone):** name `exp1-ezkey`, public IP `3.99.189.207`, AZ `ca-central-1a`, same blueprint/bundle. Scripts refuse to create or delete that name unless you force delete (discouraged).

**No Lightsail static IP in v1.** If you recreate the instance, the public IPv4 may change — the operator updates Cloudflare **A** records manually.

**IAM:** workstation profile `ezkey-lightsail` / policy **EzkeyLightsailOperator** (instance lifecycle + ports + key pairs; no static IP, no snapshots).

---

## Scripts (`scripts/lightsail/`)

| Script | Role |
|--------|------|
| [`create-instance.sh`](../../scripts/lightsail/create-instance.sh) | `create-instances`; dry-run default; `--apply` to create; waits until `running`; refuses existing name |
| [`open-ports.sh`](../../scripts/lightsail/open-ports.sh) | SSH 22 + HTTPS 443 (`open-instance-public-ports` or `--mode put`); dry-run default |
| [`bootstrap-host.sh`](../../scripts/lightsail/bootstrap-host.sh) | SSH Phase 0b/0c: Docker + Compose v2 + `docker` group; no clean-start / no image push |
| [`delete-instance.sh`](../../scripts/lightsail/delete-instance.sh) | Delete by name; requires `--apply` **and** `--i-mean-it` |
| [`status.sh`](../../scripts/lightsail/status.sh) | `get-instance` / `--list` → `get-instances` |
| [`common.sh`](../../scripts/lightsail/common.sh) | Shared defaults and helpers (sourced; not run directly) |

These scripts **never** change production DNS or certificates. They **never** commit or print PEMs, Origin CA material, `.env` secrets, or AWS keys.

---

## Product hostnames (ezkey.online)

Locked for the community host (Marc / PR #609). EXP1 keeps `exp1-*.ezkey.org` on the live EXP1 stack — do **not** replace [`experimental-hybrid/lightsail/Caddyfile`](../../experimental-hybrid/lightsail/Caddyfile).

| Role | Hostname |
|------|----------|
| Admin UI (Pages) | `admin-ui.ezkey.online` |
| Admin API | `admin-api.ezkey.online` |
| Auth API | `auth-api.ezkey.online` |
| Integration API | `integration-api.ezkey.online` |
| Demo ACME | `demo-acme.ezkey.online` |

Community Caddy site blocks: [`experimental-hybrid/lightsail/Caddyfile.ezkey-online`](../../experimental-hybrid/lightsail/Caddyfile.ezkey-online) (TLS 1.3 Origin CA paths, docs block, reverse_proxy targets — mirrors EXP1 structure).

---

## Ordered path to a working community host

Do these **after** VM create → ports → Docker bootstrap (below). No secrets in git; Origin CA / DNS / Pages stay human OK.

1. **Hostnames** — use the table above (Caddy + DNS + Origin CA SANs must match).
2. **Community Caddyfile on the VM** — Compose bind-mounts `./Caddyfile`. Keep the repo’s EXP1 [`Caddyfile`](../../experimental-hybrid/lightsail/Caddyfile) unchanged. Install the community variant as the active file on the **community** host only:
   ```bash
   export LIGHTSAIL_SSH_HOST=ezkey-online
   scp experimental-hybrid/lightsail/Caddyfile.ezkey-online \
     "${LIGHTSAIL_SSH_HOST}:ezkey/experimental-hybrid/lightsail/Caddyfile"
   ```
   **Export sync note:** `--sync-operator-files` / `--clean-start` on [`export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh) always copies the EXP1-named `Caddyfile`. After any such sync to the community VM, **re-scp** `Caddyfile.ezkey-online` → remote `Caddyfile` (or `scp` the variant then `ssh … 'cp …/Caddyfile.ezkey-online …/Caddyfile'`). Prefer this explicit copy for now over rewriting the export script.
3. **Origin CA** — Cloudflare Origin Server cert covering all API + demo hostnames above; place `origin.pem` / `origin-key.pem` in `~/ezkey/experimental-hybrid/lightsail/caddy-certs/` on the VM (`chmod 700` dir, `chmod 600` key). Never commit.
4. **DNS** — Cloudflare **A** records (orange / proxied) for those API/demo names → instance public IP (from `./scripts/lightsail/status.sh`). Admin UI name points at Pages, not the Lightsail IP.
5. **`.env`** — on the VM: copy [`.env.example`](../../experimental-hybrid/lightsail/.env.example) → `.env`, then overlay community URLs/CORS from [`.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example). **Mode B (Pages cookie)** requires all of:
   - `EZKEY_ADMIN_CORS_ALLOWED_ORIGINS=https://admin-ui.ezkey.online,https://ezkey.org,https://www.ezkey.org` (UI + community-signup marketing origins)
   - `EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS=true`
   - `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED=true`
   - UI build: `VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE=true`  
   Recreate Admin API after changing these. Do not leave credentials/cookie flags at `false` for the community cookie path.
6. **Images** — export with optional demo:
   ```bash
   export LIGHTSAIL_SSH_HOST=ezkey-online
   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --include-demo-acme
   # then re-apply community Caddyfile (step 2) if sync overwrote it
   ```
7. **Remote clean-start** (destructive first bring-up) — after layout + `.env` + certs + images:
   ```bash
   export LIGHTSAIL_SSH_HOST=ezkey-online
   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --include-demo-acme --clean-start
   # re-scp Caddyfile.ezkey-online → remote Caddyfile, then:
   ssh ezkey-online 'cd ~/ezkey/experimental-hybrid/lightsail && docker compose up -d --no-deps --force-recreate caddy'
   ```
   Or run `./clean-start.sh` on the VM from `~/ezkey/experimental-hybrid/lightsail/` after files are in place (see playbook).
8. **Pages Admin UI** — separate Cloudflare Pages project (or branch) for `admin-ui.ezkey.online` with `VITE_API_BASE_URL=https://admin-api.ezkey.online` and Mode B UI flag above. Not automated by `scripts/lightsail/`.

---

## Mode B (Admin UI cookie) — do not regress

Community Pages Admin UI uses **Mode B** (`credentials: 'include'` + HttpOnly session cookie on the API host). Bootstrap examples must keep:

```bash
EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS=true
EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED=true
EZKEY_ADMIN_CORS_ALLOWED_ORIGINS=https://admin-ui.ezkey.online,https://ezkey.org,https://www.ezkey.org
```

Canon: [`docs/cloudflare/admin-ui-pages.md`](../cloudflare/admin-ui-pages.md), [`docs/admin-ui-security.md`](../admin-ui-security.md). Shared EXP1 [`.env.example`](../../experimental-hybrid/lightsail/.env.example) may still show `false` for lab Bearer mode; community overlay is [`.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example).



## Community signup CORS + Admin UI link (do not regress)

Anonymous evaluator signup from the marketing site (https://ezkey.org/community-signup.html, and www) posts cross-origin to `https://admin-api.ezkey.online`. Two env settings must stay aligned on the community host:

1. **CORS allowlist** � include the Pages Admin UI origin **and** both marketing origins (signup form):

```bash
EZKEY_ADMIN_CORS_ALLOWED_ORIGINS=https://admin-ui.ezkey.online,https://ezkey.org,https://www.ezkey.org
```

2. **Post-signup "Open Admin UI" URL** � Spring property `ezkey.evaluator.self-registration.admin-ui-url` (env `EZKEY_EVALUATOR_SELF_REGISTRATION_ADMIN_UI_URL`). Code default is EXP1 (`https://exp1-admin-ui.ezkey.org`). On community, override to the public Pages URL (**no trailing slash / path**):

```bash
EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED=true
EZKEY_EVALUATOR_SELF_REGISTRATION_ADMIN_UI_URL=https://admin-ui.ezkey.online
```

After changing either, recreate **admin-api** only (no full wipe):

```bash
ssh ezkey-online 'cd ~/ezkey/experimental-hybrid/lightsail && docker compose up -d --no-deps --force-recreate admin-api'
```

Canon overlay: [`.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example).

---

## demo-acme HTTPS redirects

**Symptom:** `curl -sI https://demo-acme.ezkey.online/` returned `Location: http://demo-acme.ezkey.online/login` while the session cookie is `Secure` — broken behind Cloudflare Full (strict).

**Cause:** Caddy → `demo-app-acme:8082` is plain HTTP. Spring `redirect:/login` built an absolute URL from the *inbound* scheme unless forwarded headers are applied. Trusted-proxy CIDRs alone do not fix redirect scheme.

**In-repo fix:**
- `server.forward-headers-strategy=framework` in demo-acme `application.properties` (honours `X-Forwarded-Proto` / `Host`)
- Lightsail compose sets `SERVER_FORWARD_HEADERS_STRATEGY=framework` on `demo-app-acme` (works on an existing image without rebuild — Spring Boot env binding)
- Community Caddyfile passes `header_up X-Forwarded-Proto` / `Host` on the demo-acme site block

**Edgar — apply on live `ezkey-online` (no secrets):**

```bash
# From workstation: sync compose + community Caddyfile, recreate demo + caddy
export LIGHTSAIL_SSH_HOST=ezkey-online
scp experimental-hybrid/lightsail/docker-compose.yml \
  "${LIGHTSAIL_SSH_HOST}:ezkey/experimental-hybrid/lightsail/"
scp experimental-hybrid/lightsail/Caddyfile.ezkey-online \
  "${LIGHTSAIL_SSH_HOST}:ezkey/experimental-hybrid/lightsail/Caddyfile"
ssh "${LIGHTSAIL_SSH_HOST}" 'cd ~/ezkey/experimental-hybrid/lightsail && \
  docker compose up -d --no-deps --force-recreate demo-app-acme caddy'
```

Optional one-liner if compose is not synced yet: set `SERVER_FORWARD_HEADERS_STRATEGY=framework` in the VM `.env` or compose service env, then recreate `demo-app-acme`.

**Verify (after recreate):**

```bash
curl -sI https://demo-acme.ezkey.online/ | tr -d '\r' | grep -i '^location:'
# Expect: Location: https://demo-acme.ezkey.online/login
# Not:    Location: http://demo-acme.ezkey.online/login
```

---

## Operator sequence (Git Bash on Windows) — VM lifecycle

### 1. Create the VM (dry-run first)

```bash
export AWS_PROFILE=ezkey-lightsail

./scripts/lightsail/create-instance.sh
# Prefer import of an existing workstation public key:
./scripts/lightsail/create-instance.sh \
  --import-public-key ~/.ssh/id_ed25519.pub \
  --key-pair-name ezkey-online-kp \
  --apply
```

If `ezkey-online` already exists as an instance: `--name community-ezkey` (and use a distinct key-pair name such as `community-ezkey-kp`; align SSH Host).

### 2. Open ports

```bash
./scripts/lightsail/open-ports.sh --apply
# Optional: restrict SSH to your IP
./scripts/lightsail/open-ports.sh --apply --ssh-cidr 203.0.113.10/32
```

**TODO (follow-up):** restrict **443** to [Cloudflare IPv4 CIDRs](https://www.cloudflare.com/ips-v4) like EXP1. Lab v1 may leave 443 world-open; do not treat that as the long-term posture. See [`experimental-hybrid/DEPLOYMENT_PLAYBOOK.md`](../../experimental-hybrid/DEPLOYMENT_PLAYBOOK.md) Phase 0a.

### 3. SSH Host alias (separate from EXP1)

Keep **`Host ezkey`** → EXP1. Add a **new** alias, e.g. in `~/.ssh/config`:

```sshconfig
Host ezkey-online
  HostName <public-ip-from-create-or-status>
  User ec2-user
  IdentityFile ~/.ssh/<your-private-key>
  IdentitiesOnly yes
```

Scripts accept `LIGHTSAIL_SSH_HOST` and must not overwrite the EXP1 alias.

### 4. Bootstrap Docker

```bash
export LIGHTSAIL_SSH_HOST=ezkey-online
./scripts/lightsail/bootstrap-host.sh
# optional: ./scripts/lightsail/bootstrap-host.sh --print-next-steps
```

Then open a **new** SSH session so the `docker` group applies.

### 5. Reuse the EXP1 image export path

App compose stays under `experimental-hybrid/lightsail/`. Push images with the **existing** export script and the **new** SSH host, then follow **Ordered path** steps 2–8 (community Caddyfile, Origin CA, DNS, `.env`, optional `--include-demo-acme`, clean-start, Pages).

```bash
export LIGHTSAIL_SSH_HOST=ezkey-online
./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --dry-run
# then real run; optional --include-demo-acme / --sync-operator-files / --clean-start
# After any sync that copies Caddyfile, re-install Caddyfile.ezkey-online as remote Caddyfile
```

Full Phase 0–3 detail: [`experimental-hybrid/DEPLOYMENT_PLAYBOOK.md`](../../experimental-hybrid/DEPLOYMENT_PLAYBOOK.md).

### 6. Origin CA / DNS / Pages (human OK)

Covered in **Ordered path** steps 3–4 and 8. Do not automate production DNS or cert changes in `scripts/lightsail/`.

---

## Key pairs

| Approach | When |
|----------|------|
| **Import** (preferred) | `--import-public-key ~/.ssh/….pub` on `create-instance.sh` — reuses workstation keys; no private key leaves the box |
| **Create** | `--create-key-pair` — writes private key **only** to `~/.ssh/lightsail-<name>.pem` (chmod 600); **never** printed; **never** committed |

Default Lightsail key-pair name is **`ezkey-online-kp`** (not `ezkey-online`). Instance name and key-pair name must always differ.

Do not commit private keys, PEMs, Origin CA files, or `.env`.

---

## Destroy / recreate tests

```bash
./scripts/lightsail/delete-instance.sh --name ezkey-online
./scripts/lightsail/delete-instance.sh --name ezkey-online --apply --i-mean-it
```

After recreate, update Cloudflare **A** records if the public IP changed (no static IP in v1).

---

## Out of scope (this automation)

- Creating the AWS instance from CI / cloud agents without credentials
- Cloudflare DNS, Origin CA issuance, Pages project creation
- Recycling or renaming `exp1-ezkey`
- Terraform / CloudFormation / ECR
- ezkey-pam / host sshd MFA

---

## Related

- [`experimental-hybrid/DEPLOYMENT_PLAYBOOK.md`](../../experimental-hybrid/DEPLOYMENT_PLAYBOOK.md) — Phase 0 fresh AL2023; community section cross-link
- [`experimental-hybrid/lightsail/Caddyfile.ezkey-online`](../../experimental-hybrid/lightsail/Caddyfile.ezkey-online) — community hostnames
- [`experimental-hybrid/lightsail/.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example) — community URL/CORS overlay (no secrets)
- [`experimental-hybrid/scripts/export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh)
- [`docs/cloudflare/README.md`](../cloudflare/README.md) — Cloudflare script style sibling

## Naming lock (Audrey/Marc)

Community instance branding and bootstrap admin (exact spelling **Ezkey**, never EasyKey):

- `EZKEY_ORGANIZATION_NAME=Ezkey Community`
- `EZKEY_ORGANIZATION_DESCRIPTION=Public community MFA instance for evaluation — alpha, best-effort, no SLA.`
- `EZKEY_ORGANIZATION_ABOUT_URL=https://ezkey.org/community-instance.html`
- Bootstrap admin username: `admin.community` (not `admin.docker`)

Set these in the VM `experimental-hybrid/lightsail/.env` (see `.env.ezkey-online.example`). Public `GET /api/v1/public/instance-info` reads organization env at process start; clean-start reseeds the system tenant/admin from the same values. Do not claim production/SLA in these strings.

