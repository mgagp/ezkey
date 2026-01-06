# Bootstrap Credentials File Export Fix

## Problem
When running `docker-compose up` for a Clean Start, the bootstrap process was failing with:
```
Credential not found after 120 seconds
```

This was because **Admin API was not exporting bootstrap credentials to the file** even though it was configured to do so in the `docker` profile.

## Root Cause
The property `ezkey.admin.bootstrap.export.enabled` was defined **only in `application-docker.properties`**, but was **missing from the base `application.properties`** file.

When Spring Boot processes property files:
- It first loads `application.properties` (base configuration)
- Then it loads the active profile's properties file (e.g., `application-docker.properties`)
- **Profile properties override base properties**

The issue: Since the base file had no entry for `ezkey.admin.bootstrap.export.enabled`, Spring couldn't properly manage the property inheritance chain, even though the Docker profile had it set to `true`.

## Solution
Added the missing property to `ezkey-admin-api/config/application.properties`:

```properties
# Bootstrap Credentials File Export
# When enabled, exports enrollmentId/proofToken/challenge to a JSON file after bootstrap
# Used primarily in Docker and automated deployments for credential distribution
# Default: false (credentials only in logs, secure for manual deployments)
# Docker: true (enables file export to /var/lib/ezkey/bootstrap/ for automation)
ezkey.admin.bootstrap.export.enabled=false
ezkey.admin.bootstrap.export.path=/var/lib/ezkey/bootstrap/bootstrap-credentials.json
```

### Configuration Matrix

| Profile | `bootstrap.export.enabled` | `bootstrap.export.path` |
|---------|---------------------------|-------------------------|
| **default** | `false` | `/var/lib/ezkey/bootstrap/bootstrap-credentials.json` |
| **docker** | `true` | `/var/lib/ezkey/bootstrap/bootstrap-credentials.json` |
| **windows** | `false` | `C:\Temp\ezkey\bootstrap\bootstrap-credentials.json` |

## Verification

After the fix:
1. Admin API correctly receives the `docker` profile
2. Bootstrap credentials are exported to `/var/lib/ezkey/bootstrap/bootstrap-credentials.json`
3. Bootstrap Init script reads the file successfully
4. Demo Device receives the enrollment credentials
5. Clean Start completes successfully

### Logs Evidence

Admin API logs now show:
```
The following 1 profile is active: "docker"
...
Bootstrap credentials exported to file: /var/lib/ezkey/bootstrap/bootstrap-credentials.json (enrollmentId: 1)
```

Bootstrap Init logs show:
```
✅ Bootstrap credentials file found
✅ Credentials loaded: Enrollment ID: 1
✅ Device keypair generated
✅ Device bound to enrollment
✅ Enrollment verified
✅ Device credentials saved to /bootstrap/device-credentials.json
✅ Demo-device enrollment file created
✅ Bootstrap Init Complete!
```

## Files Modified

- `ezkey-admin-api/config/application.properties` - Added `ezkey.admin.bootstrap.export.enabled=false` and `ezkey.admin.bootstrap.export.path` properties

## Testing

The complete Docker Compose stack now starts cleanly with all health checks passing:
- ✅ PostgreSQL: healthy
- ✅ Admin API: healthy (exports credentials)
- ✅ Auth API: healthy
- ✅ Crypto API: healthy
- ✅ Bootstrap Init: completed successfully
- ✅ Demo Device: starting

## Impact

- **Clean Start on Docker:** Now fully functional
- **Backward Compatibility:** No breaking changes (default is `false`, matching previous behavior)
- **Windows Development:** No impact (uses `false` in Windows profile)
- **Profile System:** Reinforces proper Spring Boot property inheritance patterns
