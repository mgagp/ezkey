# Community Lightsail host (ezkey.online) — create & bootstrap

Short runbook for creating a **NEW** Amazon Lightsail VM for the **ezkey.online** community-host evaluation path. This is the **canonical public alpha community host** (not a second lab alongside a retired stack). Former experimental lab hostnames were retired in 2026-10.

**Stack model:** same all-in-one Docker VM as today’s experimental hybrid (Postgres + APIs + Caddy on one Lightsail instance). App layer stays in [`experimental-hybrid/lightsail/`](../../experimental-hybrid/lightsail/) — do not fork Compose/Caddy for this v1.

**Automation style:** AWS CLI only (no Terraform, no CloudFormation), thin Bash under [`scripts/lightsail/`](../../scripts/lightsail/), Git Bash friendly, dry-run by default for create / delete / ports.

**SSH target (#812):** deploy/bootstrap scripts never silent-default to a bare Host alias. Pass `--target community` (→ `ezkey-online`) or set `LIGHTSAIL_SSH_HOST` explicitly; community `--sync-operator-files` installs `Caddyfile.ezkey-online` under that same remote basename (compose bind mount — #863).

**Public cutover context (do not invent product language):** publish repositioned **ezkey.org** first; then community host on **ezkey.online**.

---

## Ownership boundary (monorepo now, extractable later)

Starting these scripts in the **monorepo** is intentional and pragmatic (one clone, reuse the experimental-hybrid image-export path). Longer-term, the **Marc-operated canonical community host** (`ezkey.online`) may move its *operator* scripting into a separate **private** GitHub repo: that instance is owned/operated by Marc and is **not** the same story as the public OSS “how to stand up an experimental hybrid” path (which stays in [`experimental-hybrid/`](../../experimental-hybrid/)).

| Layer | Lives in | Owns |
|-------|----------|------|
| **VM lifecycle** | [`scripts/lightsail/`](../../scripts/lightsail/) (+ this runbook) | create / ports / bootstrap Docker / status / delete |
| **App stack (OSS)** | [`experimental-hybrid/lightsail/`](../../experimental-hybrid/lightsail/) + [`export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh) | Compose, Caddyfile, clean-start, image save/scp/load |

**Extraction note:** after e2e validation, `scripts/lightsail/` + this runbook may be copied into a private ops repo with minimal edits. Keep the tree **self-contained**. Do **not** scatter community-only secrets, ezkey.online-only DNS checklists, or laptop-specific absolute paths into the public product compose tree beyond the existing OSS pattern (env vars, SSH Host aliases, gitignored PEMs / `.env` on the operator machine).

This PR does **not** create that private repo.

---

## Defaults

| Setting | Default | Override |
|---------|---------|----------|
| AWS CLI profile | `ezkey-lightsail` | `AWS_PROFILE` |
| Region | `ca-central-1` | `LIGHTSAIL_REGION` / `AWS_REGION` |
| Instance name | **`ezkey-online`** | `--name` / `LIGHTSAIL_INSTANCE_NAME` (use **`community-ezkey`** if the preferred name is taken) |
| Blueprint | `amazon_linux_2023` | `--blueprint` |
| Bundle | `medium_3_0` | `--bundle` |
| AZ | `ca-central-1a` | `--az` |
| Key pair name | **`ezkey-online-kp`** | `--key-pair-name` / `LIGHTSAIL_KEY_PAIR_NAME` |
| SSH Host alias | **no silent default** — `--target community` → `ezkey-online` (#812) | `--target community` / `LIGHTSAIL_SSH_HOST` / `--host` |

**Lightsail name uniqueness:** resource names are unique **across types**. The key-pair name **must differ** from the instance name (e.g. instance `ezkey-online` + key pair `ezkey-online-kp`). Using the same string for both fails `CreateInstances` with `InvalidInputException` (“names are already in use”). `create-instance.sh` refuses that combination.

**Static IP (live community host):** Lightsail static IP **`ezkey-online-community`** → **`<static-ip ezkey-online-community>`**, attached to instance **`ezkey-online`**. Resolve the current IPv4 with:

```bash
aws lightsail get-static-ip \
  --static-ip-name ezkey-online-community \
  --profile ezkey-lightsail \
  --region ca-central-1
```

**Decision (2026-10-19):** Marc attached static IP `ezkey-online-community` (`<static-ip ezkey-online-community>`) to `ezkey-online`. Stop/start no longer changes the public IPv4. The address is free while it stays attached. Marc updated the four proxied Cloudflare **A** records (`admin-api`, `auth-api`, `integration-api`, `demo-acme`) and SSH aliases `ezkey-online` / `ezkey-online-db` in the same cutover.

**Decision (2026-10-19, origin IP):** The origin IPv4 is **never versioned** in this public repository — use the `<static-ip ezkey-online-community>` marker and the `get-static-ip` command above. Direct origin access is already limited by the Lightsail firewall (HTTPS sources = Cloudflare IPv4 ranges only; see § Open ports). Keeping the address out of git is **defense in depth**, not the primary control.

`scripts/lightsail/` still do **not** allocate, attach, or release static IPs — attach/detach remains an operator/console action. After any attach or detach, update Cloudflare **A** records and SSH `HostName` values immediately (see **Deploy pitfalls**).

**IAM:** workstation profile `ezkey-lightsail` / policy **EzkeyLightsailOperator** (instance lifecycle + ports + key pairs; no static-IP API in that policy). **Decision (2026-10-19, #909):** Marc added `lightsail:CreateInstanceSnapshot`, `GetInstanceSnapshot` / `GetInstanceSnapshots`, and `DeleteInstanceSnapshot` (Resource `*`) for Host OS hygiene pre-intervention snapshots. Read verified from rootbeer: 0 snapshots present; Lightsail **automatic** snapshots are **not** enabled. Create has not been exercised yet — the first H1 run validates it. Scoping Resource to the instance ARN is a Christophe follow-up. Snapshot rotation: keep the latest pre-intervention snapshot, delete older ones. Canon: [`product-docs/global/hygiene/host-os/README.md`](../../product-docs/global/hygiene/host-os/README.md).

---

## Scripts (`scripts/lightsail/`)

| Script | Role |
|--------|------|
| [`create-instance.sh`](../../scripts/lightsail/create-instance.sh) | `create-instances`; dry-run default; `--apply` to create; waits until `running`; refuses existing name |
| [`open-ports.sh`](../../scripts/lightsail/open-ports.sh) | SSH 22 + HTTPS 443 (`open-instance-public-ports` or `--mode put`); dry-run default |
| [`bootstrap-host.sh`](../../scripts/lightsail/bootstrap-host.sh) | SSH Phase 0b/0c: Docker + Compose v2 + `docker` group; no clean-start / no image push |
| [`os-patch-check.sh`](../../scripts/lightsail/os-patch-check.sh) | Read-only Host OS signal over SSH (`--target community`); releasever / security updateinfo / reboot hint / `docker ps` (#909) |
| [`delete-instance.sh`](../../scripts/lightsail/delete-instance.sh) | Delete by name; `--apply` requires explicit `--name`; community `ezkey-online` also needs `EZKEY_CONFIRM_DELETE=ezkey-online`; never releases static IPs |
| [`status.sh`](../../scripts/lightsail/status.sh) | `get-instance` / `--list` → `get-instances` |
| [`common.sh`](../../scripts/lightsail/common.sh) | Shared defaults and helpers (sourced; not run directly) |

These scripts **never** change production DNS or certificates. They **never** commit or print PEMs, Origin CA material, `.env` secrets, or AWS keys.

---

## Product hostnames (ezkey.online)

**Single source of truth** for public `*.ezkey.online` product hostnames (Marc / PR #609;
inventory 2026-10-19). Use the community Caddyfile overlay; do not invent alternate public
hostnames in generic defaults. Other docs should **link here** instead of duplicating this table.

| Host | Role | Served by | DNS | Expected on `/` |
|------|------|-----------|-----|-----------------|
| `admin-ui.ezkey.online` | Admin UI | Cloudflare Pages (custom domain) | Pages | 200 |
| `admin-api.ezkey.online` | Admin API | Caddy on Lightsail | A proxied → `<static-ip ezkey-online-community>` | 401 |
| `auth-api.ezkey.online` | Auth API (QR URL) | Caddy on Lightsail | A proxied → `<static-ip ezkey-online-community>` | 404 (no `/` route) |
| `integration-api.ezkey.online` | Integration API | Caddy on Lightsail | A proxied → `<static-ip ezkey-online-community>` | 401 |
| `demo-acme.ezkey.online` | Demo ACME | Caddy on Lightsail | A proxied → `<static-ip ezkey-online-community>` | 302 |

Community Caddy site blocks (four hosts only): [`experimental-hybrid/lightsail/Caddyfile.ezkey-online`](../../experimental-hybrid/lightsail/Caddyfile.ezkey-online) (TLS 1.3 Origin CA paths, docs block, reverse_proxy targets). Admin UI is Pages, not Caddy.

### Not served

These names are **not** product endpoints. Do not publish them in defaults, QR URLs, Bruno
environments, or client configs:

| Name | Notes |
|------|--------|
| `ezkey.online` (apex) | No A/CNAME; not an API or UI origin |
| `n.ezkey.online` | No DNS; does not exist |
| `auth.ezkey.online` | No DNS; does not exist (use `auth-api.ezkey.online`) |
| `admin.ezkey.online` | No DNS; does not exist (use `admin-api` / `admin-ui`) |
| `acme.ezkey.online` | No DNS; does not exist (use `demo-acme.ezkey.online`) |
| `api.ezkey.online` | No DNS; does not exist |
| `www.ezkey.online` | CNAME proxied; Cloudflare Single Redirect **301** → `https://ezkey.org/` (not a product origin) |

**Any other `*.ezkey.online` name does not exist** and must not be introduced without updating
this section and the CI allowlist in [`scripts/ops/check-ezkey-online-hosts.sh`](../../scripts/ops/check-ezkey-online-hosts.sh).

**Decision (2026-10-19):** hostname canon and “not served” inventory locked from Edgar’s
read-only probe + live Caddyfile.

**Decision (2026-10-19, www → ezkey.org):** Marc decided that `www.ezkey.online` and
`www.ezkey.org` each return **301** to `https://ezkey.org/` via a Cloudflare Single Redirect
(one `http_request_dynamic_redirect` ruleset per zone, one rule each). Apex `ezkey.online`
remains unchanged (still not served). Applied by Edgar at 16:19 Eastern; verified with
`curl -sI`. Resolves the #894 follow-up “www.ezkey.online DNS — delete orphan A or redirect”
(resolved by redirect; DNS is now CNAME proxied, not an orphan A). Do not record ruleset IDs
or origin IPs in git.

**CI guard:** [`scripts/ops/check-ezkey-online-hosts.sh`](../../scripts/ops/check-ezkey-online-hosts.sh)
(lightsail job in CI) rejects new `*.ezkey.online` labels outside the allowlist above; see
[`scripts/README.md`](../../scripts/README.md) § *Ops — ezkey.online hostname allowlist*.

---

## Ordered path to a working community host

Do these **after** VM create → ports → Docker bootstrap (below). No secrets in git; Origin CA / DNS / Pages stay human OK.

1. **Hostnames** — use the table above (Caddy + DNS + Origin CA SANs must match).
2. **Community Caddyfile on the VM** — Compose bind-mounts `./Caddyfile.ezkey-online`. With `--target community`, [`export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh) `--sync-operator-files` installs that file under the **same remote basename** as the compose bind (not a renamed remote `Caddyfile` — #812 / #863). Because the file is bind-mounted, plain `docker compose up -d` does **not** recreate caddy — use `--sync-operator-files --remote-up` (bind-mount preflight + sha256 verify + `force-recreate caddy`) or recreate caddy manually after a sync-only pass:
   ```bash
   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh \
     --target community --sync-operator-files --remote-up
   # or sync-only, then:
   # ssh ezkey-online 'cd ~/ezkey/experimental-hybrid/lightsail && docker compose up -d --no-deps --force-recreate caddy'
   ```
3. **Origin CA** — Cloudflare Origin Server cert covering all API + demo hostnames above; place `origin.pem` / `origin-key.pem` in `~/ezkey/experimental-hybrid/lightsail/caddy-certs/` on the VM (`chmod 700` dir, `chmod 600` key). Never commit.
4. **DNS** — Cloudflare **A** records (orange / proxied) for those API/demo names → **`<static-ip ezkey-online-community>`** (resolve via `get-static-ip` above, or `./scripts/lightsail/status.sh`). Admin UI name points at Pages, not the Lightsail IP.
5. **`.env`** — on the VM: copy [`.env.example`](../../experimental-hybrid/lightsail/.env.example) → `.env`, then overlay community URLs/CORS from [`.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example). **Mode B (Pages cookie)** requires all of:
   - `EZKEY_ADMIN_CORS_ALLOWED_ORIGINS=https://admin-ui.ezkey.online,https://ezkey.org,https://www.ezkey.org` (UI + community-signup marketing origins)
   - `EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS=true`
   - `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED=true`
   - UI build: `VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE=true`  
   Recreate Admin API after changing these. Do not leave credentials/cookie flags at `false` for the community cookie path.

   **Operator laptop copy:** the community-host operator environment lives in [`experimental-hybrid/lightsail/.local/`](../../experimental-hybrid/lightsail/) (gitignored, operator-only, never committed). Example: `env.ezkey-online` is the local file to `scp` onto the VM as `.env`. If the local copy may be older than the live VM file, treat the **live `.env` on the VM as source of truth** — compare variable **names** only; never print or log values.
6. **Images** — export with optional demo:
   ```bash
   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --target community --include-demo-acme
   ```
7. **Remote clean-start** — **first commissioning only** (destructive: `docker compose down -v` wipes Postgres). Not for upgrades. Requires `EZKEY_CONFIRM_WIPE=ezkey-online`:
   ```bash
   EZKEY_CONFIRM_WIPE=ezkey-online \
     ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh \
     --target community --include-demo-acme --clean-start
   ```
   Or run `./clean-start.sh` on the VM from `~/ezkey/experimental-hybrid/lightsail/` after files are in place (see playbook). For kept DBs use `--sync-operator-files --remote-up` / `full-exp-environment-upgrade.sh --target community rolling`.
8. **Pages Admin UI** — separate Cloudflare Pages project (or branch) for `admin-ui.ezkey.online` with `VITE_API_BASE_URL=https://admin-api.ezkey.online` and Mode B UI flag above. Not automated by `scripts/lightsail/`.

---

## Mode B (Admin UI cookie) — do not regress

Community Pages Admin UI uses **Mode B** (`credentials: 'include'` + HttpOnly session cookie on the API host). Bootstrap examples must keep:

```bash
EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS=true
EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED=true
EZKEY_ADMIN_CORS_ALLOWED_ORIGINS=https://admin-ui.ezkey.online,https://ezkey.org,https://www.ezkey.org
```

Canon: [`docs/cloudflare/admin-ui-pages.md`](../cloudflare/admin-ui-pages.md), [`docs/admin-ui-security.md`](../admin-ui-security.md). Base [`.env.example`](../../experimental-hybrid/lightsail/.env.example) may still show `false` for lab Bearer mode; community overlay is [`.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example).



## Community signup CORS + Admin UI link (do not regress)

Anonymous evaluator signup from the marketing site (https://ezkey.org/community-signup.html, and www) posts cross-origin to `https://admin-api.ezkey.online`. Two env settings must stay aligned on the community host:

1. **CORS allowlist** — include the Pages Admin UI origin **and** both marketing origins (signup form):

```bash
EZKEY_ADMIN_CORS_ALLOWED_ORIGINS=https://admin-ui.ezkey.online,https://ezkey.org,https://www.ezkey.org
```

2. **Post-signup "Open Admin UI" URL** — Spring property `ezkey.evaluator.self-registration.admin-ui-url` (env `EZKEY_EVALUATOR_SELF_REGISTRATION_ADMIN_UI_URL`) and guided tour URL (`EZKEY_EVALUATOR_SELF_REGISTRATION_GUIDED_TOUR_URL`). There is **no product default hostname**; when `enabled=true`, Admin API fails startup if either URL is blank. On community, set both to the public Pages / marketing URLs (**no trailing slash / path** on the Admin UI origin):

```bash
EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED=true
EZKEY_EVALUATOR_SELF_REGISTRATION_ADMIN_UI_URL=https://admin-ui.ezkey.online
EZKEY_EVALUATOR_SELF_REGISTRATION_GUIDED_TOUR_URL=https://ezkey.org/community-guided-tour.html
```

After changing either, recreate **admin-api** only (no full wipe):

```bash
ssh ezkey-online 'cd ~/ezkey/experimental-hybrid/lightsail && docker compose up -d --no-deps --force-recreate admin-api'
```

Canon overlay: [`.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example).

---

## Admin login rate limit (Cancel + retry)

**Symptom (2026-10-14):** passwordless login → Cancel on device / Admin UI → immediate retry → HTTP 429 on `admin-api.ezkey.online` (#831). **Cause:** login and `passwordless-wait` shared a 5/min IP bucket. **Fix:** separate wait bucket (keyed `ip+authAttemptId` + per-IP + wait backstop; login cache isolated), login 15/min greedy refill, waiter-secret `passwordless-cancel`, mobile leave-as-deny. Redeploy admin-api (+ Admin UI / mobile when those clients are updated).

**Caddy admin-api access log (#833 C1):** site-level `log` with `format filter` (delete all request/response headers; strip query keys `token` / `proof` / `challenge` / `waiterSecret` / `code`) plus `log_skip` for paths outside `/api/v1/admin/auth/*`. Do **not** write `log @matcher` — Caddy treats that token as a logger name and logs every route. Container stdout is still under Compose `x-logging` (`max-size: 20m`, `max-file: 3`) — see § Docker `json-file` log rotation below. Runtime proof: `scripts/prove-caddy-admin-auth-log.sh` (derives the HTTP proof site from the real Caddyfile log block).

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
./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh \
  --target community --sync-operator-files --apis-only
# (or manual scp of compose + Caddyfile.ezkey-online under that same remote basename)
ssh ezkey-online 'cd ~/ezkey/experimental-hybrid/lightsail && \
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

./scripts/lightsail/create-instance.sh --target community
# Prefer import of an existing workstation public key:
./scripts/lightsail/create-instance.sh --target community \
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

**443 posture (since 2026-09-23):** HTTPS is restricted to the **15 Cloudflare IPv4 ranges** published at [ips-v4](https://www.cloudflare.com/ips-v4). There is no IPv6 Lightsail firewall rule. Confirm with `aws lightsail get-instance-port-states` before a community upgrade go/no-go. **Do not run `open-ports.sh` on `ezkey-online`:** its default `HTTPS_CIDR` is `0.0.0.0/0` and `--mode put` replaces every rule, which would reopen 443 to the world.

### 3. SSH Host aliases

Add community aliases in `~/.ssh/config` (same key / IP as create-or-status). Prefer **`Host ezkey-online`**
(not a bare `ezkey` alias). `ezkey-online-db` already exists on some workstations (e.g. Rootbeer);
document it here so every operator laptop matches. `ezkey-online-metrics` is the **planned**
JavaMelody collector tunnel (#846); it is not yet configured on Rootbeer.

```sshconfig
Host ezkey-online
  HostName <static-ip ezkey-online-community>
  User ec2-user
  IdentityFile ~/.ssh/<your-private-key>
  IdentitiesOnly yes

# Postgres on the VM listens on 127.0.0.1:5432 only (compose bind). Match Rootbeer's real alias.
Host ezkey-online-db
  HostName <static-ip ezkey-online-community>
  User ec2-user
  IdentityFile ~/.ssh/<your-private-key>
  IdentitiesOnly yes
  LocalForward 127.0.0.1:6432 127.0.0.1:5432
  ExitOnForwardFailure yes

# JavaMelody collector (compose publishes 127.0.0.1:8088:8080 only — never 0.0.0.0).
# Planned alias — not yet on Rootbeer.
Host ezkey-online-metrics
  HostName <static-ip ezkey-online-community>
  User ec2-user
  IdentityFile ~/.ssh/<your-private-key>
  IdentitiesOnly yes
  LocalForward 127.0.0.1:8088 127.0.0.1:8088
  ExitOnForwardFailure yes
```

Replace `HostName` with the IPv4 from `aws lightsail get-static-ip --static-ip-name ezkey-online-community --profile ezkey-lightsail --region ca-central-1` (never commit the literal address).

Open tunnels: `ssh ezkey-online-db` / `ssh ezkey-online-metrics` (leave the session open). Collector UI
after metrics tunnel: http://127.0.0.1:8088 — see **Observability (JavaMelody)** below.

Scripts accept `LIGHTSAIL_SSH_HOST` when the Host alias differs from the `--target community` default.

### 4. Bootstrap Docker

```bash
./scripts/lightsail/bootstrap-host.sh --target community
# optional: ./scripts/lightsail/bootstrap-host.sh --target community --print-next-steps
```

Then open a **new** SSH session so the `docker` group applies.

### 5. Image export path

App compose stays under `experimental-hybrid/lightsail/`. Push images with the **existing** export script and a **declared** target (`--target community` → Host `ezkey-online`; no silent default — #812), then follow **Ordered path** steps 2–8 (Origin CA, DNS, `.env`, optional `--include-demo-acme`, clean-start, Pages).

```bash
./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --target community --dry-run
# then real run; optional --include-demo-acme / --sync-operator-files / --clean-start
# --sync-operator-files with --target community installs Caddyfile.ezkey-online
# under that same remote basename (compose bind mount)
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
EZKEY_CONFIRM_DELETE=ezkey-online \
  ./scripts/lightsail/delete-instance.sh --name ezkey-online --apply --i-mean-it
```

`--apply` requires an explicit `--name` (no silent default). `EZKEY_CONFIRM_DELETE=ezkey-online` is required when the normalized instance name is `ezkey-online` (trim + case-insensitive) **or** when static IP `ezkey-online-community` is attached to the target (read via `get-static-ip` before delete). The script prints a static-IP billing warning on dry-run and apply; it never calls `release-static-ip`.

After delete, static IP `ezkey-online-community` detaches and stays allocated (~USD 3.60/month while unattached). **Re-attach it immediately after recreate** (keeps **`<static-ip ezkey-online-community>`**); **never release `ezkey-online-community`**. Then re-run `open-ports.sh` with the Cloudflare 443 posture (see § Open ports — do not reopen 443 to `0.0.0.0/0`).

---

## Out of scope (this automation)

- Creating the AWS instance from CI / cloud agents without credentials
- Cloudflare DNS, Origin CA issuance, Pages project creation
- Terraform / CloudFormation / ECR
- ezkey-pam / host sshd MFA

---

## Host OS hygiene (#909)

Community host OS patching and AL2023 minor/major moves are a separate hygiene lane (not
app deploy). Canon: [`product-docs/global/hygiene/host-os/README.md`](../../product-docs/global/hygiene/host-os/README.md).
Read-only signal: `./scripts/lightsail/os-patch-check.sh --target community` (rootbeer-local
agent only — cloud agents have no SSH route). Live Host OS fields:
[`community/DEPLOYED.md`](community/DEPLOYED.md).

## Related

- [`experimental-hybrid/DEPLOYMENT_PLAYBOOK.md`](../../experimental-hybrid/DEPLOYMENT_PLAYBOOK.md) -- Phase 0 fresh AL2023; community section cross-link
- [`experimental-hybrid/lightsail/Caddyfile.ezkey-online`](../../experimental-hybrid/lightsail/Caddyfile.ezkey-online) -- community hostnames
- [`experimental-hybrid/lightsail/.env.ezkey-online.example`](../../experimental-hybrid/lightsail/.env.ezkey-online.example) -- community URL/CORS overlay (no secrets)
- [`experimental-hybrid/scripts/export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh)
- [`docs/cloudflare/README.md`](../cloudflare/README.md) -- Cloudflare script style sibling
- [`docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../VERSIONING_AND_DEPLOY_TRACEABILITY.md) -- Public alpha claim + git-SHA identity
- [`community/DEPLOYED.md`](community/DEPLOYED.md) -- live state ledger for ezkey.online (update after successful publish)
- [`product-docs/global/hygiene/host-os/README.md`](../../product-docs/global/hygiene/host-os/README.md) -- Host OS hygiene lane H1/H2/H3 (#909)

## Observability (JavaMelody) — #846 / memory bounds #749

Lab posture only: SSH tunnel to the collector, no public Cloudflare route. Auth for the collector
is the SSH tunnel alone (do **not** put `authorized-users` / passwords in `-D` flags — they appear
in the JVM report). Decision line also lives in
[`docs/SECURITY_POSTURE.md`](../SECURITY_POSTURE.md) § Observability.

### Host memory and swap (do not auto-create)

Edgar’s read-only inventory (2026-10-16): ~3839 MB RAM, ~1498 MB free, **no swap**, unbounded JVM
heaps (~960 MiB ergonomic ceiling each). Compose now sets cgroup + `MaxRAMPercentage` limits
(#749) in [`docker-compose.yml`](../../experimental-hybrid/lightsail/docker-compose.yml).

**`memswap_limit` = `mem_limit`:** containers cannot grow into host swap beyond the cgroup. A host
swapfile still helps the OS / non-cgroup pressure, but it does **not** raise a container’s effective
memory ceiling.

| Service | mem_limit (= memswap_limit) | MaxRAMPercentage |
|---------|------------------------------|------------------|
| admin-api | 1152m | 65% |
| auth-api | 768m | 60% |
| integration-api | 576m | 60% |
| demo-app-acme | 384m | 55% |
| postgres | 384m | n/a |
| caddy | 128m | n/a |
| migration | 512m | 60% |
| javamelody-collector (overlay) | 256m | `-Xmx160m` |

Sum with collector ≈ **3648m / 3839m** — tight. **Prefer a 1 GiB swapfile** on the VM with
`vm.swappiness=10` before enabling the collector. **Do not** create swap from this repo’s deploy
scripts; Marc / Edgar apply it once on the host if approved, for example:

```bash
# On the VM — operator-approved host change only (not run by agents / export scripts)
sudo fallocate -l 1G /swapfile   # or: sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-ezkey-swappiness.conf
sudo sysctl -p /etc/sysctl.d/99-ezkey-swappiness.conf
```

APIs use `restart: unless-stopped` (not `on-failure:3`) so a single crash does not leave a service
permanently down after three failures. Combined with `-XX:+ExitOnOutOfMemoryError`, a repeated OOM
can restart-loop until the pressure is relieved (Patrick note) — watch `RestartCount` / `docker stats`
after go-live.

### Deploy JavaMelody (Edgar — after Marc go)

Prerequisites: memory limits from `docker-compose.yml` already rolling; preferred swap in place;
operator files synced (includes `docker-compose.javamelody.yml` + `docker/javamelody/` bind context).

**Collector image is never built on the VM.** The Lightsail overlay is **image-only**
(`ezkey-javamelody-collector:2.9.0`, `pull_policy: never`, no `build:`). Remote Docker Compose
often ships buildx **0.12.x**, which is below the **0.17** Compose requirement — a remote
`build` fails. With `EZKEY_ENABLE_JAVA_MELODY=true`, export builds the collector locally from
`docker/javamelody`, includes it in save/scp/`docker load`, then runs remote
`docker compose … up -d --no-build`.

```bash
# Dry-run first — confirm collector appears in the image list and remote up uses --no-build
EZKEY_ENABLE_JAVA_MELODY=true \
  ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh \
    --target community --dry-run --sync-operator-files --remote-up

# From workstation — build+ship collector, sync compose/Caddy/ops, remote up WITH overlay
EZKEY_ENABLE_JAVA_MELODY=true \
  ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh \
    --target community --sync-operator-files --remote-up

# Or on the VM after the image is already loaded (non-destructive recreate):
cd ~/ezkey/experimental-hybrid/lightsail
docker compose -f docker-compose.yml -f docker-compose.javamelody.yml up -d --no-build
# (recreates APIs with JAVAMELODY_* and starts the collector; short per-service cutover)
# If the image is missing, compose fails fast (pull_policy: never) — re-run export with JM enabled.
```

**Keep JM on later rolling upgrades:** always pass `EZKEY_ENABLE_JAVA_MELODY=true` on export
`--remote-up` (re-ships the collector image and applies the overlay). A rolling up **without**
that flag refuses if the collector container is present (APIs would otherwise lose `JAVAMELODY_*`
while the collector stays). Intentional teardown: `EZKEY_DISABLE_JAVA_MELODY=true … --remote-up`.

**`--remove-orphans` is not passed by default** on export `--remote-up` (intentional — Edgar).
Only the `EZKEY_DISABLE_JAVA_MELODY=true` path adds `--remove-orphans` so the collector is
removed as an orphan. A plain `docker compose -f docker-compose.yml up -d` (no flag) leaves
orphan containers running; use the disable env (or the rollback command below) when you mean
to drop the collector. Export remote-up always passes **`--no-build`** so the VM never compiles
images.

Collector image/version is aligned with the API starter (**2.9.0**, WAR sha256 pinned in
`docker/javamelody/Dockerfile`). Christophe locks baked into the overlay / entrypoint:

- Collector bind: `127.0.0.1:8088:8080` only
- Management ports: base compose binds `MANAGEMENT_SERVER_ADDRESS=127.0.0.1` (healthchecks use
  `127.0.0.1`, not `localhost`). The JM overlay **must** set `MANAGEMENT_SERVER_ADDRESS=0.0.0.0`
  or the collector stays empty — export/`verify-javamelody-non-exposure.sh --check-jm-overlay-bind`
  fail closed if the overlay omits that reopen.
- Monitoring reports on management ports (`9081` / `8085` / `7081`); demo-app-acme management is
  **8086** (Actuator not on business 8082). Caddy denies `/monitoring` and `/actuator` (and nested /
  case / `//` / `;` variants) on all public API + demo-acme hostnames — defense in depth; do not
  remove the deny when ports move.
- **Deploy risk (image ↔ compose):** The dangerous pair is a **new** Acme image (classpath
  `management.server.port=8086`) with an **old** compose whose healthcheck still probes
  `8082/actuator/health` → Acme unhealthy → Caddy `depends_on: demo-app-acme: service_healthy`
  blocks public APIs on recreate. Old image + new compose is usually OK (env vars move Actuator).
  Always ship Acme image with `--sync-operator-files` on the same `--remote-up` (export refuses
  `--include-demo-acme --remote-up` without sync, and checks remote compose for
  `8086/actuator/health`).
- Business-port `/actuator/health` must be native **404**; `/monitoring` must stay non-exposed:
  only **401**, **403**, or **404** without a JavaMelody body count as OK (do not harmonize).
  **HTTP 200** or any response whose body looks like JavaMelody is an exposure failure. Any other
  code (`000`, 3xx, 5xx, 204, …) is **probe inconclusive** and fails
  (`verify-javamelody-non-exposure.sh --business-ports`).
- Init parameters are injected via `SPRING_APPLICATION_JSON` with hyphenated JavaMelody Parameter
  codes (`system-actions-enabled`, `http-transform-pattern`, …). Plain
  `JAVAMELODY_INIT_PARAMETERS_SYSTEM_ACTIONS_ENABLED` env vars bind underscore map keys that
  JavaMelody silently ignores — do not use that form. **Warning:** the overlay sets
  `SPRING_APPLICATION_JSON` on each API and **replaces** any value defined elsewhere (Compose
  env merge is last-wins for that key) — do not also set it in the base compose or host `.env`.
  Shared lock keys live in `docker/javamelody/init-parameters.base.json`;
  `./scripts/ops/check-javamelody-spring-json.sh` fails if the nine embedded copies diverge.
- `system-actions-enabled=false`, `csrf-protection-enabled=true`, `update-check-disabled=true`
- `log=false`; `http-transform-pattern=[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}|\d+`; SQL stays as prepared
  `?` (no bind values in reports)
- No secrets in `-D` / `JAVA_TOOL_OPTIONS` (heap and flags only)
- RRD retention: `obsolete-graphs-days=30` and `obsolete-stats-days=30` on named volume
  `javamelody-data`

### Verify by tunnel + non-exposure

```bash
# Tunnel
ssh ezkey-online-metrics
# Browser / curl on the laptop:
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8088/
# Expect 200 (collector UI). Management reports are Docker-internal only.

# Public non-exposure (expect 404 everywhere) — from laptop after deploy:
./scripts/ops/verify-javamelody-non-exposure.sh \
  --base-url https://admin-api.ezkey.online \
  --base-url https://auth-api.ezkey.online \
  --base-url https://integration-api.ezkey.online \
  --base-url https://demo-acme.ezkey.online

# On the VM: confirm management/collector ports are loopback-only (IPv4 + IPv6 [::])
./scripts/ops/verify-javamelody-non-exposure.sh --check-ss

# Business-port /monitoring (C2) — replayable script probes *through* the caddy
# container (official image has ash/curl, not bash; --via-caddy uses docker exec):
./scripts/ops/verify-javamelody-non-exposure.sh --business-ports --via-caddy ezkey-exp-caddy
# Authenticated refusal (Christophe) — secrets via 0600 files (never on argv/`ps`):
umask 077
printf '%s' "$TOKEN" > /tmp/jm-bearer
printf 'EZKEY_ADMIN_SESSION=%s' "$TOKEN" > /tmp/jm-cookie
chmod 0600 /tmp/jm-bearer /tmp/jm-cookie
./scripts/ops/verify-javamelody-non-exposure.sh --business-ports \
  --via-caddy ezkey-exp-caddy --auth-probe \
  --auth-bearer-file /tmp/jm-bearer \
  --auth-cookie-file /tmp/jm-cookie
shred -u /tmp/jm-bearer /tmp/jm-cookie 2>/dev/null || rm -f /tmp/jm-bearer /tmp/jm-cookie

# Runtime proof that init-parameters applied (fail-closed — Patrick craft).
# Local DEV: http://127.0.0.1:9081/actuator/monitoring
# Community: docker exec ezkey-exp-admin-api wget -q -O - http://127.0.0.1:9081/actuator/monitoring
set -e
curl -fsS 'http://127.0.0.1:9081/actuator/monitoring' -o /tmp/jm.html
grep -q 'parameter defined: system-actions-enabled=false' /tmp/jm.html
grep -q 'parameter defined: csrf-protection-enabled=true' /tmp/jm.html
grep -q 'parameter defined: http-transform-pattern=' /tmp/jm.html
! grep -qE 'action=gc|action=heap_dump' /tmp/jm.html
echo 'OK: init-parameters applied (system-actions off, CSRF on, transform set; no GC/heap links)'
# Cheap HTTP-tab capture (normalized ids use $): generate a UUID path, then:
#   curl -fsS 'http://127.0.0.1:9081/actuator/monitoring' | grep -oE '/api/[^ <\"]+' | sort -u
# Expect segments like /api/v$/…/$ rather than raw UUIDs / numeric ids when traffic exists.
```

Local regex / Caddyfile parse + smoke + **per-site import** check (no live host):
`./scripts/ops/validate-caddy-observability-regexp.sh`

JSON lock divergence check (nine embedded `SPRING_APPLICATION_JSON` copies vs base):
`./scripts/ops/check-javamelody-spring-json.sh`

### Rollback (one command)

Drops the JavaMelody overlay (APIs lose `JAVAMELODY_*`, collector removed as an orphan). **#749
memory limits in the base `docker-compose.yml` stay** — this is not a memory rollback.

```bash
cd ~/ezkey/experimental-hybrid/lightsail
docker compose -f docker-compose.yml up -d --no-build --remove-orphans
# Optional — wipe collector history (Marc go):
# docker volume rm ezkey-experimental-lightsail_javamelody-data
```

Or via export (explicit): `EZKEY_DISABLE_JAVA_MELODY=true … --remote-up`.

Re-verify public non-exposure with `verify-javamelody-non-exposure.sh`. To **keep** JM after a later
rolling upgrade, pass `EZKEY_ENABLE_JAVA_MELODY=true` again — leaving it unset while the collector
exists is refused by the export guard.

---

## Deploy pitfalls (community host)

Short operator checklist verified against repo scripts/compose (not live VM observation):

1. **Caddyfile sync name must match the compose bind mount (#863)** — Compose mounts
   `./Caddyfile.ezkey-online`; `--sync-operator-files` must scp to that same remote
   basename and verify sha256 on **that** file. Renaming to remote `Caddyfile` while
   compose still mounts `./Caddyfile.ezkey-online` makes Docker create an empty
   root-owned directory, leaves caddy `Created`, and surfaces as Cloudflare **521** on
   all API hosts. `--dry-run` and bind-mount preflight (before remote `up`) must catch
   this. Deploy scripts still require `--target community` or `LIGHTSAIL_SSH_HOST`
   (no silent Host default — #812). After `--sync-operator-files --remote-up`, the
   script runs bind-mount preflight, verifies the mounted Caddyfile sha256, and
   `force-recreate`s caddy (plain `up -d` does not reload Caddy).
2. **Grants container** — Compose service `db-grants` (`container_name: ezkey-exp-db-grants` in
   [`docker-compose.yml`](../../experimental-hybrid/lightsail/docker-compose.yml)) applies
   [`scripts/db/apply-grants.sql`](../../scripts/db/apply-grants.sql) after migration. APIs depend on
   it (`condition: service_completed_successfully`). Do not skip or remove it on a fresh bring-up.
3. **Never `--clean-start` on a kept community DB** — `--clean-start` / `full` are destructive
   (`docker compose down -v`). Community requires `EZKEY_CONFIRM_WIPE=ezkey-online` and is for
   **first commissioning only**. For upgrades: `--target community rolling` (or
   `--sync-operator-files` + `--remote-up`).
4. **Static IP attach / detach changes the instance public IPv4** — attaching or detaching
   `ezkey-online-community` (or any Lightsail static IP) immediately changes the address the
   instance presents. Update the four proxied Cloudflare **A** records
   (`admin-api`, `auth-api`, `integration-api`, `demo-acme`) and SSH `HostName` on the
   aliases that exist (`ezkey-online`, `ezkey-online-db`; planned alias
   `ezkey-online-metrics` is not yet on Rootbeer — see decision 2026-10-19) right after the
   change. The Lightsail firewall and Origin CA certificate do **not** change on
   attach/detach — only DNS **A** records and SSH `HostName` do. After an instance
   **recreate**, re-run `open-ports.sh` (Cloudflare 443 posture — do not reopen 443 to
   `0.0.0.0/0`). Until DNS and SSH catch up, API calls time out and deploy scripts fail to
   connect. Stop/start with the static IP **still attached** does **not** change
   `<static-ip ezkey-online-community>`.
5. **Docker `json-file` log rotation** — Compose sets `x-logging` on every service
   (`max-size: 20m`, `max-file: 3` — sized for a ~4 GB RAM / ~80 GB-disk VM; ceiling ≈ 480 MiB).
   Rotation applies only when a container is **created**; it does not resize logging on an
   already-running host.

   **Rolling upgrade that adds or changes `logging:`:** `docker compose up -d` recreates **all**
   services that inherit the new logging config (typically all 8, **Postgres included**). Named
   volumes are kept, but there is a short outage while containers restart. Old json-file logs
   disappear with the old container IDs — **scan before `up`**. `scripts/ops/` lands with
   `--sync-operator-files` (same pass as `--remote-up` would recreate containers), so:

   1. Export with `--target community --sync-operator-files` **without** `--remote-up` (or `scp` the script)
   2. On the VM: `bash scripts/ops/docker-log-hygiene.sh --project ezkey-experimental-lightsail --scan`
   3. Then `--target community --remote-up` (or a later sync+up pass)

   After `up`, confirm rotation with `docker inspect` or
   `bash scripts/ops/docker-log-hygiene.sh --project ezkey-experimental-lightsail`.
   Rotated-file purge needs sudo under `/var/lib/docker/containers`.

### Log rotation and hygiene (next install)

For a **new** Lightsail bring-up (not a remote action from a laptop against the live instance):

1. Deploy with the compose file above so every service inherits `x-logging`.
2. On the VM after `compose up`, run check mode:

   ```bash
   bash scripts/ops/docker-log-hygiene.sh --project ezkey-experimental-lightsail
   ```

3. Periodically (e.g. monthly) re-run check; optional `--scan` prints **counts only** of
   credential-like patterns (never values). Plan cleanup with `--purge` (dry-run; project
   containers only); apply with `--purge --apply --yes` when non-interactive.

### Post-upgrade checks (m6)

Follow the canon checklist in [`docs/OPERATIONAL.md`](../OPERATIONAL.md) § Post-upgrade checks
(m6) — including the read-only in-volume credentials check (never `cat` / never `docker cp` to
`/tmp`). Deletion of `bootstrap-credentials.json` happens **only with Marc's go**.

Canon and “what it never does”: [`docs/OPERATIONAL.md`](../OPERATIONAL.md) § Log rotation and hygiene.

**Note (stale refs checked on `main`):** this runbook no longer contains a `TODO:223` marker or an
`EZKEY_POSTGRES_CONTAINER` reference. Lab scripts under `scripts/lab/` default that env var to
`ezkey-postgres`; the Lightsail compose Postgres container is `ezkey-exp-postgres` — do not assume
the lab default on this host.

## Naming lock (Audrey/Marc)

Community instance branding and bootstrap admin (exact spelling **Ezkey**, never EasyKey):

- `EZKEY_ORGANIZATION_NAME=Ezkey Community`
- `EZKEY_ORGANIZATION_DESCRIPTION=Public community MFA instance for evaluation — alpha, best-effort, no SLA.`
- `EZKEY_ORGANIZATION_ABOUT_URL=https://ezkey.org/community-instance.html`
- Bootstrap admin username: `admin.community` (not `admin.docker`)

Set these in the VM `experimental-hybrid/lightsail/.env` (see `.env.ezkey-online.example`). Public `GET /api/v1/public/instance-info` reads organization env at process start; clean-start reseeds the system tenant/admin from the same values. Do not claim production/SLA in these strings.

