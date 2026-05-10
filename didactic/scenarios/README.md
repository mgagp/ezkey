# Example scenarios

- [`run.example.yaml`](cleanstart-example/run.example.yaml) — **accept** MFA respond (`scenario.respond_accepted: true`).
- [`run.example.reject.yaml`](cleanstart-example/run.example.reject.yaml) — **deny** path (`respond_accepted: false`).

Copy one to `run.yaml` beside it (or reference with `--config`). Prefer `EZKEY_ADMIN_TOKEN` for bearer material.

Adjust `integration_id` to match an integration your admin account can administer.
