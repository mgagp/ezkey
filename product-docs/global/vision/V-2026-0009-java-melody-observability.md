# V-2026-0009 — Lightweight observability posture: Java Melody first, no full APM stack initially

- **Date:** `2026-05-08`
- **Updated at:** `2026-08-24`
- **Status:** `promoted`
- **Intent:** **JavaMelody** + standalone collector for **admin/auth/integration API** only;
  **exclude crypto-api**. Management port reports; collector persistent volume; **no Caddy**
  R1. **Opt-in** via clean-start / compose flag (`--with-java-melody`) — not enabled by
  default (grill D11). Combinable with `--ha`. DX/troubleshooting, not prod APM. Funded
  2026-08-24 as **`I-2026-08-24-java-melody-collector`** / **`TB-2026-08-24-java-melody-collector`**.
- **Signals:** Grilling D11 Blitz 2 (2026-05-19). Plan-prompt deleted in the 2026-08 corpus-ablation
  pass; decisions live here and in `R-2026-0002`. Implementation funded 2026-08-24 with mandatory
  proof on **baseline** and **HA** topologies.
- **Potential impact:** `infra`, boot modules, docs.
- **Next step:** execute `TB-2026-08-24-java-melody-collector`.
- **Captured by:** Marc

## Related artifacts

- `R-2026-0002` — Java Melody collector for Ezkey
- `I-2026-08-24-java-melody-collector`
- `TB-2026-08-24-java-melody-collector`
