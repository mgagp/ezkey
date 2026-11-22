# Demo Login — configuration

Environment variables, read at start. Restart the container after a change.

| Variable | Default | Meaning |
|----------|---------|---------|
| `EZKEY_INTEGRATION_API_URL` | `http://localhost:7080` | Integration API base URL (`http://integration-api:7080` in Docker Compose). |
| `EZKEY_INTEGRATION_KEY` | — | Integration key (`ezkey_ikey_...`). Without it, sign-in is refused with `not-configured`. |
| `EZKEY_SECRET_KEY` | — | Secret key (`ezkey_skey_...`). Never logged, never sent to the browser. |
| `DEMO_LOGIN_PORT` | `8082` | HTTP port (app and API on the same origin). |
| `DEMO_LOGIN_DB_PATH` | `./data/demo-login.db` | SQLite file (`/app/data/demo-login.db` in the image). |
| `DEMO_LOGIN_STATIC_DIR` | `./dist/web` | Built single-page app (`/app/web` in the image). |
| `DEMO_LOGIN_AUTH_TTL_SECONDS` | `120` | Lifetime asked for each sign-in request (15–300; Ezkey caps it, typically at 120). |
| `DEMO_LOGIN_SESSION_HOURS` | `8` | Session lifetime. |
| `DEMO_LOGIN_COOKIE_SECURE` | `false` | Add `Secure` to the session cookie. Set `true` behind HTTPS (community instance). |
| `DEMO_LOGIN_TRUST_PROXY` | `false` | Use the first `X-Forwarded-For` value as the client address (rate limit) and `X-Forwarded-Host` for the origin check. Set `true` only behind a proxy that overwrites these headers (Caddy on the community instance). |
| `DEMO_LOGIN_RATE_LIMIT_PER_MINUTE` | `10` | Sign-in starts allowed per client address per minute. |

## Sign-in policy

- The checkbox on the login box asks Ezkey for a two-digit challenge code (on by default). An
  enrollment that requires a challenge gets one either way.
- Unsafe requests must be JSON and same-origin; with `SameSite=Strict` cookies this closes CSRF.
- Error codes shown to the user are listed in `src/server/login-flow.ts` (`LoginErrorCode`); the
  precise cause goes to the log.
