# Lightsail VM lifecycle scripts (community / ezkey.online)

Self-contained AWS CLI helpers for **creating and bootstrapping** a NEW Lightsail VM parallel to EXP1. Dry-run by default for create / ports / delete.

**Owns:** instance create, firewall ports, Docker bootstrap, status, delete.  
**Does not own:** Compose/Caddy/app images — reuse [`experimental-hybrid/lightsail/`](../../experimental-hybrid/lightsail/) and [`export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh) with `LIGHTSAIL_SSH_HOST`.

**Runbook:** [`docs/lightsail/community-host.md`](../../docs/lightsail/community-host.md).

**Monorepo now, extractable later:** shipping here is pragmatic. After e2e validation, this directory (+ the runbook) may move to a **private** ops repo for the Marc-operated `ezkey.online` community host. Keep community-only secrets and site-specific DNS out of the public OSS compose tree. Do not create that private repo from these scripts.

```bash
export AWS_PROFILE=ezkey-lightsail
./scripts/lightsail/create-instance.sh
./scripts/lightsail/open-ports.sh
./scripts/lightsail/bootstrap-host.sh --dry-run
./scripts/lightsail/status.sh --help
./scripts/lightsail/delete-instance.sh --help
```
