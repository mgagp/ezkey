# ezkey-jenkins status

**Posture:** experimental lab (#972). Not product-core. Not on the operable-release
roadmap. Success means demonstrable, not shipped.

| Milestone | Status | Gate |
|-----------|--------|------|
| **M0** skeleton + Jenkins in Docker + `m0-ls` green | **done** (this folder) | Marc sees Jenkins up with the job green |
| M1 hello-world plugin + tutorial | not started | plugin built, installed, visible |
| M2 mock auth plugin + prior-art | not started | login hooked; mock accepts; break-glass admin |
| M3 SDK fit decision | not started | one-line SDK decision |
| M4 real Ezkey round trip | not started | approve/deny on Docker Jenkins |

## M0 proof

```bash
cd ezkey-jenkins && ./scripts/up.sh && ./scripts/smoke.sh
```

Expect: `M0 smoke OK: job m0-ls is green.`
