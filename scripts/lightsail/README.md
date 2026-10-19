# Lightsail VM lifecycle scripts (community / ezkey.online)

Self-contained AWS CLI helpers for **creating and bootstrapping** the Lightsail VM for the ezkey.online community host. Dry-run by default for create / ports / delete.

**Owns:** instance create, firewall ports, Docker bootstrap, status, delete.  
**Does not own:** Compose/Caddy/app images — reuse [`experimental-hybrid/lightsail/`](../../experimental-hybrid/lightsail/) and [`export-backend-images-to-lightsail.sh`](../../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh) with **`--target community`** (or `LIGHTSAIL_SSH_HOST`). No silent SSH host default (#812).

**Runbook:** [`docs/lightsail/community-host.md`](../../docs/lightsail/community-host.md).

**Live public IPv4 (2026-10-19):** static IP `ezkey-online-community` → `15.157.54.178` on instance `ezkey-online`. These scripts never allocate, attach, or release static IPs; see the runbook decision line and deploy pitfall on attach/detach. `delete-instance.sh --apply` requires explicit `--name`; deleting `ezkey-online` also needs `EZKEY_CONFIRM_DELETE=ezkey-online`.

**Monorepo now, extractable later:** shipping here is pragmatic. After e2e validation, this directory (+ the runbook) may move to a **private** ops repo for the Marc-operated `ezkey.online` community host. Keep community-only secrets and site-specific DNS out of the public OSS compose tree. Do not create that private repo from these scripts.

```bash
export AWS_PROFILE=ezkey-lightsail
./scripts/lightsail/create-instance.sh --target community
./scripts/lightsail/open-ports.sh
./scripts/lightsail/bootstrap-host.sh --target community --dry-run
./scripts/lightsail/status.sh --help
./scripts/lightsail/delete-instance.sh --help
# Dry-run target checks: ./scripts/lightsail/target-aware.test.sh
```
