# ezkey-jenkins status

**Posture:** experimental lab (#972). Not product-core. Not on the operable-release
roadmap. Success means demonstrable, not shipped.

| Milestone | Status | Gate |
|-----------|--------|------|
| **M0** skeleton + Jenkins in Docker + `m0-ls` green | **done** | Marc sees Jenkins up with the job green |
| **M1** hello-world plugin + tutorial | **done** | plugin built, installed, visible + working |
| **M2** mock auth + prior-art + break-glass | **done** | login hooked; mock accepts; break-glass admin |
| M3 SDK fit decision | not started | one-line SDK decision |
| M4 real Ezkey round trip | not started | approve/deny on Docker Jenkins |

## M0 proof

```bash
cd ezkey-jenkins && ./scripts/up.sh && ./scripts/smoke.sh
```

## M1 proof

```bash
./scripts/build-plugin.sh && ./scripts/install-plugin.sh && ./scripts/demo-hello-job.sh
```

## M2 proof

```bash
./scripts/build-auth-plugin.sh
./scripts/configure-mock-auth.sh
# anonymous API → 403; admin break-glass → 200; labuser:any → 200
```

Design / prior-art / challenge rules: [`DESIGN.md`](DESIGN.md).
