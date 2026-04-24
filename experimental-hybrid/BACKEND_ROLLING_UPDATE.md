# Rolling update — single backend image (Lightsail)

Use this when you **already run** the experimental Lightsail stack ([`README.md`](README.md), [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md)) and only need to deploy a **new image** for **one** API (or migration) **without** wiping volumes or running a full `clean-start.sh`.

**Not covered here:** database schema changes that require a **new migration** image and an explicit migration run — follow your usual Flyway process (`migration` service) if the codebase added migrations.

## Image tag

Compose uses fixed tags (`ezkey-auth-api:latest`, etc.). After `docker save` / `docker load`, the tag points at the new image layers. The **running container** still uses the **old image ID** until the service is **recreated**.

- **`docker compose restart <service>`** — restarts the **same** container; it does **not** switch to a newly loaded `:latest`. **Do not rely on this alone** after loading a new tar.
- **`docker compose up -d --no-deps --force-recreate <service>`** — creates a **new** container from the **current** local image for that tag. Use this after `docker load`.

Work from **`~/ezkey/experimental-hybrid/lightsail/`** so the Compose project name and paths stay consistent ([`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) *VM initialization*).

## 1. Build and export (development machine, repo root)

**Auth API example:**

```bash
docker build -f docker/Dockerfile --target auth-api -t ezkey-auth-api:latest .
mkdir -p docker/export
docker save -o docker/export/ezkey-auth-api.tar ezkey-auth-api:latest
```

Other backends — same pattern; only the `--target` and tar filename change (see table below).

## 2. Copy to the VM

Default SSH host for this environment is **`ezkey`** (see [`README.md`](README.md#default-lightsail-ssh-host-ezkey)). Otherwise set `LIGHTSAIL_SSH_HOST` for the export script.

```bash
scp docker/export/ezkey-auth-api.tar ezkey:~/
```

## 3. Load and recreate (on the VM)

```bash
docker load -i ~/ezkey-auth-api.tar
cd ~/ezkey/experimental-hybrid/lightsail
docker compose up -d --no-deps --force-recreate auth-api
```

- **`--no-deps`** — avoids restarting Postgres, Caddy, or other services when only this API changed.
- **`--force-recreate`** — ensures the container is built from the image you just loaded.

**Optional — watch startup:**

```bash
docker compose logs -f --tail=100 auth-api
```

**Optional — quick health check (from the VM or any client that can reach the internal port is not required; use public URL or `docker compose exec` curl):**

```bash
docker compose exec auth-api curl -sf http://localhost:8085/actuator/health
```

(Adjust port if you override `MANAGEMENT_SERVER_PORT` in Compose.)

## Service names and export targets

| Service (Compose) | Build `--target`   | Image tag                 | Example tar name              |
|-------------------|----------------------|---------------------------|-------------------------------|
| `admin-api`       | `admin-api`          | `ezkey-admin-api:latest`  | `ezkey-admin-api.tar`         |
| `auth-api`        | `auth-api`           | `ezkey-auth-api:latest`   | `ezkey-auth-api.tar`          |
| `integration-api` | `integration-api`    | `ezkey-integration-api:latest` | `ezkey-integration-api.tar` |
| `migration`       | `migration`          | `ezkey-migration:latest`  | `ezkey-migration.tar`         |

Use the **service** name in `docker compose … <service>` (first column).

## Multiple backends in one pass

Build and save each image, `scp` each tar, then on the VM:

```bash
docker load -i ~/ezkey-auth-api.tar
docker load -i ~/ezkey-integration-api.tar
cd ~/ezkey/experimental-hybrid/lightsail
docker compose up -d --no-deps --force-recreate auth-api integration-api
```

## Cleanup (optional)

After several loads, old untagged layers may accumulate. When convenient:

```bash
docker image prune -f
```

## See also

- Full build/save/load flow for **all** images: [`README.md`](README.md) *Build and export images*
- VM tree and `scp` defaults: [`DEPLOYMENT_PLAYBOOK.md`](DEPLOYMENT_PLAYBOOK.md) *VM initialization* and *Phase 2*
