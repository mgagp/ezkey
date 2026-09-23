# Community Lightsail host (ezkey.online) — create & bootstrap

Short runbook for creating a **NEW** Amazon Lightsail VM for the **ezkey.online** community-host evaluation path. This is **parallel to EXP1** (`exp1-ezkey` / `*.ezkey.org`). It is **not** a public launch by itself and does **not** shut down or rebrand live EXP1.

**Stack model:** same all-in-one Docker VM as today’s experimental hybrid (Postgres + APIs + Caddy on one Lightsail instance). App layer stays in [`experimental-hybrid/lightsail/`](../../experimental-hybrid/lightsail/) — do not fork Compose/Caddy for this v1.

**Automation style:** AWS CLI only (no Terraform, no CloudFormation), thin Bash under [`scripts/lightsail/`](../../scripts/lightsail/), Git Bash friendly, dry-run by default for create / delete / ports.

**Public cutover context (do not invent product language):** publish repositioned **ezkey.org** first; then community host on **ezkey.online**. Creating a parallel Lightsail instance for testing is OK.

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
| Key pair name | `ezkey-online` | `--key-pair-name` |
| SSH Host alias | `ezkey-online` | `LIGHTSAIL_SSH_HOST` / `--host` |

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

## Operator sequence (Git Bash on Windows)

### 1. Create the VM (dry-run first)

```bash
export AWS_PROFILE=ezkey-lightsail

./scripts/lightsail/create-instance.sh
# Prefer import of an existing workstation public key:
./scripts/lightsail/create-instance.sh \
  --import-public-key ~/.ssh/id_ed25519.pub \
  --key-pair-name ezkey-online \
  --apply
```

If `ezkey-online` already exists: `--name community-ezkey` (and align SSH Host / key pair names).

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

App compose/Caddy stay under `experimental-hybrid/lightsail/`. Push images with the **existing** export script and the **new** SSH host:

```bash
export LIGHTSAIL_SSH_HOST=ezkey-online
./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --dry-run
# then real run; optional --sync-operator-files / --clean-start per playbook
```

Full Phase 0–3 detail: [`experimental-hybrid/DEPLOYMENT_PLAYBOOK.md`](../../experimental-hybrid/DEPLOYMENT_PLAYBOOK.md).

### 6. Origin CA / DNS (human OK — out of scope for these scripts)

Cloudflare **A** records for ezkey.online API hostnames, Origin CA PEMs on the VM, and Pages project setup remain **manual** after human approval. Do not automate production DNS or cert changes in `scripts/lightsail/`.

---

## Key pairs

| Approach | When |
|----------|------|
| **Import** (preferred) | `--import-public-key ~/.ssh/….pub` on `create-instance.sh` — reuses workstation keys; no private key leaves the box |
| **Create** | `--create-key-pair` — writes private key **only** to `~/.ssh/lightsail-<name>.pem` (chmod 600); **never** printed; **never** committed |

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
- [`experimental-hybrid/scripts/export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh)
- [`docs/cloudflare/README.md`](../cloudflare/README.md) — Cloudflare script style sibling
