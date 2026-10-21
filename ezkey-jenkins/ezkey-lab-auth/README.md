# ezkey-lab-auth

Experimental mock **SecurityRealm** for the `ezkey-jenkins` lab (#972 M2).

- Break-glass admin (local password) — anti-lockout
- All other users: mock Ezkey auto-accept (no live HTTP; that is M4)
- Ezkey base URL, integration key, and secret (`hudson.util.Secret`, never logged)

See [`../DESIGN.md`](../DESIGN.md) and [`../TUTORIAL.md`](../TUTORIAL.md).
