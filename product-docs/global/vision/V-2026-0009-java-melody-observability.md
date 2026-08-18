# V-2026-0009 — Lightweight observability posture: Java Melody first, no full APM stack initially

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **Java Melody** + standalone collector for **admin/auth/integration API** only;
  **exclude crypto-api**. Management port registration; collector persistent volume; **no Caddy**
  R1. **Opt-in** via clean-start / compose flag (`--with-java-melody` style) — not enabled by
  default (grill D11). DX/troubleshooting, not prod APM. Canon via **`R-2026-0002`** before
  `I-*`.
- **Signals:** Grilling D11 Blitz 2 (2026-05-19). Plan-prompt deleted in the 2026-08 corpus-ablation pass; decisions live here and in `R-2026-0002`.
- **Potential impact:** `infra`, boot modules, docs.
- **Next step:** finish `R-2026-0002` residual gaps (collector WAR compatibility spike), then `I-*` for implementation when funded.
- **Captured by:** Marc

## Related artifacts

- `R-2026-0002` — Java Melody collector for Ezkey
