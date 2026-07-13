# Mobile doctor-curated config

Pinned inputs for the `mobile-doctor-curated` punctual hygiene pass.

| Path | Role |
|------|------|
| [`suppressions.json`](suppressions.json) | Rule-level suppressions with written reasons |

Analyzers (v1 Go):

1. **react-doctor** — React / RN structure and anti-patterns
2. **Semgrep** — `ezkey_mobile/semgrep/rules/mobile-security.yml`
3. **Detekt** — `ezkey_mobile/detekt/detekt.yml` (narrow)

Entrypoint: `./scripts/mobile-doctor-curated.sh` or `yarn doctor:curated` from `ezkey_mobile/`.

Outputs (gitignored via root `logs/`): `logs/mobile-doctor/mobile-doctor.curated.md|json`.

Campaign notes: `product-docs/global/hygiene/mobile-doctor/`.
