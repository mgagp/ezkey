# EZKey Demo App ACME - Agent Notes

## Purpose

Demo application for EZKey passwordless login. Shows frontend-first integration pattern.

## Key Files

| File | Description |
|------|-------------|
| `controller/HomeController.java` | Routes: `/login`, `/dashboard`, `/logout` |
| `static/js/ezkey-login.js` | Frontend login SDK (calls Admin API) |
| `templates/login.html` | Login page with username form |
| `templates/dashboard.html` | Post-login with integration mode explanation |

## Architecture

- **Mode**: Frontend-first (JavaScript calls Admin API directly)
- **Port**: 8082
- **Auth**: Token stored in `sessionStorage`
- **No backend auth logic** - all handled by `ezkey-login.js`

## Login Flow

1. User enters username on `/login`
2. JS calls `POST /api/v1/admin/auth/login` on Admin API
3. JS polls `POST /api/v1/admin/auth/wait` until approved
4. Token stored in sessionStorage, redirect to `/dashboard`

## Configuration

```properties
ezkey.admin.api.url=http://localhost:9080
ezkey.login.mode=frontend  # backend mode not implemented yet
```

## Watchouts

- **CORS**: Browser calls Admin API directly - CORS must be configured
- **Challenge code**: 2 digits for login (not 6 - that's enrollment)
- **Token expiry**: Check `expiresAt` in sessionStorage
- **Frontend only**: Backend login mode is documented but not implemented

## Common Tasks

### Test Login
1. Start Docker stack: `./docker/start.sh`
2. Open http://localhost:8082
3. Enter `admin.docker` as username
4. Approve on demo-device (http://localhost:8083)

### Debug Login Issues
1. Check browser console for API errors
2. Verify Admin API is accessible: `curl http://localhost:9080/actuator/health`
3. Check CORS headers in network tab
