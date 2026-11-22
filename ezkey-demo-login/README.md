# Ezkey Demo Login

A small web application that signs its users in with Ezkey, built the way an adopter would build
one: a React login box, a TypeScript back end that calls the **Integration API** through the
**TypeScript SDK**, and its own SQLite database of users and sessions. It replaces the former Demo
App Acme (#1430).

```
Browser ──► Demo Login back end ──(API key, SDK)──► Ezkey Integration API
  (React)     (Node, SQLite)                              │
                                                          ▼
                                           Ezkey app / Demo Device (approves)
```

- **Same stack as the Admin UI** on the front end (React 19, Vite, Tailwind v4), neo-brutalist yellow
  and pink so it is never mistaken for the Admin UI.
- **The application owns its users.** A username it does not know never reaches Ezkey. Each user
  maps to the User ID of an Ezkey enrollment (`--ezkey-id`, the username by default).
- **Opaque session token** in an `HttpOnly`, `SameSite=Strict` cookie; only its SHA-256 hash is stored.
- **One Docker image** for the clean start and the community instance. No third-party account is
  needed to run it.

## Run it with the clean start

The clean-start stack (`ezkey-tests/clean-start.sh`) starts Demo Login on http://localhost:8082. It
says it is not connected to Ezkey until you give it an API key:

1. In the Admin UI, create an **integration**, then an **API key** for it.
2. Create an **enrollment** in that integration with **User ID** `alice`, and activate it on the
   Demo Device (http://localhost:8083/phone/ezkey) or the mobile app.
3. Give the key to Demo Login and add the user, from `docker/`:

   ```bash
   EZKEY_INTEGRATION_KEY=ezkey_ikey_... EZKEY_SECRET_KEY=ezkey_skey_... docker compose up -d demo-login
   docker compose exec demo-login node server/cli.js user add alice --name "Alice Martin"
   ```

4. Open http://localhost:8082, sign in as `alice`, approve on the device.

The key stays in the container environment; set the same variables again on the next `up`.

## Administer users

There is no admin screen. The CLI runs in the container:

```bash
docker compose exec demo-login node server/cli.js user list
docker compose exec demo-login node server/cli.js user add bob --name "Bob Tremblay" --ezkey-id bob@example.com
docker compose exec demo-login node server/cli.js user disable bob
docker compose exec demo-login node server/cli.js user remove bob
```

The database is `/app/data/demo-login.db` on the `demo-login-data` volume. Back it up by copying the
file; losing it only loses test users and sessions.

## Develop

Node 24+. The app links the SDK from `../ezkey-sdk/javascript` (its committed `dist/`).

```bash
npm ci
EZKEY_INTEGRATION_KEY=... EZKEY_SECRET_KEY=... npm run dev   # API on 8082, Vite on 5173
npm run cli -- user add alice
npm test        # back-end tests (node:test, fake Ezkey client)
npm run build   # typecheck, then dist/web and dist/server
```

## Test matrix

[`docs/e2e/README.md`](docs/e2e/README.md): approved, refused, wrong code, expired, cancelled,
account and enrollment errors, configuration errors, sessions — with screenshots
(`npx playwright test` against a clean start).

## More

- Settings: [`CONFIGURATION.md`](CONFIGURATION.md)
- Agent notes: [`AGENTS.md`](AGENTS.md)
- SDK: [`../ezkey-sdk/javascript/README.md`](../ezkey-sdk/javascript/README.md)
