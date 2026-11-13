# Example scenarios

- [`run.example.yaml`](cleanstart-example/run.example.yaml) — default **access-login accept** example for the `access-login-full` template.
- [`run.example.contextual-approval.yaml`](cleanstart-example/run.example.contextual-approval.yaml) — richer approval-copy variant for the `contextual-approval-full` template.
- [`run.example.reject.yaml`](cleanstart-example/run.example.reject.yaml) — **deny** path (`respond_accepted: false`).

Copy one to `run.yaml` beside it (or reference with `--config`). Prefer `EZKEY_ADMIN_TOKEN` for bearer material.

Adjust `integration_id` to match an integration your admin account can administer.
