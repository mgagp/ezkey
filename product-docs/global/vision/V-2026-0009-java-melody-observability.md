# V-2026-0009 — Lightweight observability posture: Java Melody first, no full APM stack initially

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Java Melody** + standalone collector for **admin/auth/integration API** only;
  **exclude crypto-api**. Management port registration; collector persistent volume; **no Caddy**
  R1. **Opt-in** via clean-start / compose flag (`--with-java-melody` style) — not enabled by
  default (grill D11). DX/troubleshooting, not prod APM. Canon via **`R-2026-0002`** before
  `I-*`.
- **Signals:** Grilling D11 Blitz 2 (2026-05-19); plan-prompt
  `.github/prompts/plan-javaMelodyCollectorForEzkey.prompt.md`.
- **Potential impact:** `infra`, boot modules, docs.
- **Next step:** finish `R-2026-0002` retrofit; update plan-prompt default to opt-in; then
  `I-*` implementation.
- **Captured by:** Marc

## Related artifacts

- `R-2026-0002` — Java Melody collector for Ezkey
