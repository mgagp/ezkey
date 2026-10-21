# ezkey-jenkins status

**Posture:** experimental lab (#972). Not product-core. Not on the operable-release
roadmap. Success means demonstrable, not shipped.

| Milestone | Status | Gate |
|-----------|--------|------|
| **M0** skeleton + Jenkins in Docker + `m0-ls` green | **done** | Marc sees Jenkins up with the job green |
| **M1** hello-world plugin + tutorial | **done** | plugin built, installed, visible + working |
| **M2** mock auth + prior-art + break-glass | **done** | login hooked; break-glass admin |
| **M3** SDK fit decision | **done** | one-line SDK decision recorded |
| **M4** real Ezkey Integration round-trip | **done** | confirm → create → challenge → wait; approve + deny |
| M5 polish / optional Credentials | not started | — |

## M3 decision (gate)

> Java SDK (`org.ezkey:ezkey-sdk`) is **adequate as-is** for Jenkins login over the Integration API; the plugin adds only a thin UX/session adapter (confirm → create → display challenge → wait), not a new SDK.

Frame: Auth API ↔ phone (device); Integration API ↔ SDK (this plugin). See [`DESIGN.md`](DESIGN.md).

## Proof commands

```bash
cd ezkey-jenkins
./scripts/up.sh && ./scripts/smoke.sh                          # M0
./scripts/build-plugin.sh && ./scripts/install-plugin.sh \
  && ./scripts/demo-hello-job.sh                               # M1
./scripts/build-auth-plugin.sh
# M4: source Integration keys, then:
./scripts/configure-mock-auth.sh                               # break-glass + /ezkey-lab-login/
# Live: open /ezkey-lab-login/ → type ok → match challenge on Demo Device → Approve/Deny
```
