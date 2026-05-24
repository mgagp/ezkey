# V-2026-0006 — Mobile certificate pinning posture: SPKI pinning, TOFU at enrollment, Ezkey-authenticated recovery

- **Date:** `2026-05-08`
- **Status:** `under-review`
- **Intent:** **SPKI pin** + **TOFU at enrollment**; native pinning in normal ops; **Auth API
  recovery** (narrow unpinned path) after Cloudflare-style rotation; device proof +
  backend-signed refresh; user confirm before pin replace; **Android-first**. Audit pin
  transitions; compliance batch later. Canon via **`R-2026-0001`** before `I-*`.
- **Signals:** Grilling D2 Blitz 2 (2026-05-19); existing plan
  `.cursor/plans/auth_api_spki_pinning_recovery_analysis.plan.md`.
- **Potential impact:** `mobile`, `auth-api`, `admin-api` (audit/compliance batch later), docs.
- **Next step:** finish `R-2026-0001` retrofit mapping; then implementation backlog slice.

## Related artifacts

- `R-2026-0001` — Mobile certificate pinning (SPKI + TOFU + Ezkey-authenticated recovery)
