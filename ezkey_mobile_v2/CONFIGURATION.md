# Environment Configuration Examples

This file contains example configurations for different deployment scenarios.

## Development (Android Emulator)

```typescript
// src/services/AuthApiService.ts
const AUTH_API_BASE_URL = 'http://10.0.2.2:8080/api/v1';
```

Explanation:
- `10.0.2.2` is a special alias that maps to `localhost` (127.0.0.1) on the host machine
- Works for Android emulators running on the same computer as the backend

## Development (Physical Device - Same Network)

```typescript
// src/services/AuthApiService.ts
const AUTH_API_BASE_URL = 'http://192.168.1.100:8080/api/v1';
```

Explanation:
- Replace `192.168.1.100` with your computer's local IP address
- Find your IP:
  - Linux/Mac: `ifconfig | grep "inet "` or `ip addr show`
  - Windows: `ipconfig`
- Device must be on the same WiFi network as the backend

## Development (iOS Simulator)

```typescript
// src/services/AuthApiService.ts
const AUTH_API_BASE_URL = 'http://localhost:8080/api/v1';
```

Explanation:
- iOS simulator can access localhost directly
- No special alias needed like Android emulator

## Production (HTTPS)

```typescript
// src/services/AuthApiService.ts
const AUTH_API_BASE_URL = 'https://api.ezkey.yourdomain.com/api/v1';
```

Explanation:
- Always use HTTPS in production
- Point to your production backend domain
- Requires valid SSL certificate

## Configurable Backend URL (Recommended)

For a production app, make the URL configurable:

### 1. Create config file

```typescript
// src/config/config.ts
export const Config = {
  AUTH_API_BASE_URL: process.env.EXPO_PUBLIC_AUTH_API_URL || 'http://10.0.2.2:8080/api/v1',
};
```

### 2. Update .env files

```bash
# .env.development
EXPO_PUBLIC_AUTH_API_URL=http://10.0.2.2:8080/api/v1

# .env.production
EXPO_PUBLIC_AUTH_API_URL=https://api.ezkey.yourdomain.com/api/v1
```

### 3. Use in service

```typescript
// src/services/AuthApiService.ts
import { Config } from '../config/config';

const AUTH_API_BASE_URL = Config.AUTH_API_BASE_URL;
```

### 4. Install dotenv

```bash
npm install dotenv
```

## Network Security Configuration (Android)

For development with HTTP (non-HTTPS) backends:

The `app.json` already includes:
```json
{
  "android": {
    "usesCleartextTraffic": true
  }
}
```

For production, create `android/app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Allow cleartext traffic only for development -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">192.168.1.0/24</domain>
    </domain-config>
    <!-- Production: Use HTTPS only -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

And reference it in `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

## Common Network Issues

### Issue: "Network request failed"

**Solutions:**
1. Check backend is running: `curl http://localhost:8080/actuator/health`
2. Check firewall allows connections on port 8080
3. Verify URL is correct for your deployment type (emulator vs device)
4. For device, ensure same WiFi network

### Issue: "Connection refused"

**Solutions:**
1. Backend may not be binding to all interfaces
2. Add to backend `application.properties`:
   ```properties
   server.address=0.0.0.0
   ```
3. Restart backend

### Issue: "SSL/TLS error"

**Solutions:**
1. For development, use HTTP (already configured)
2. For production, ensure valid SSL certificate
3. Check certificate is not self-signed or expired

## Testing Different Configurations

```bash
# Test emulator config
EXPO_PUBLIC_AUTH_API_URL=http://10.0.2.2:8080/api/v1 npm start

# Test device config  
EXPO_PUBLIC_AUTH_API_URL=http://192.168.1.100:8080/api/v1 npm start

# Test production config
EXPO_PUBLIC_AUTH_API_URL=https://api.ezkey.yourdomain.com/api/v1 npm start
```

## Quick Reference Table

| Environment | URL Pattern | Example |
|-------------|-------------|---------|
| Android Emulator | `http://10.0.2.2:<port>` | `http://10.0.2.2:8080/api/v1` |
| iOS Simulator | `http://localhost:<port>` | `http://localhost:8080/api/v1` |
| Physical Device (dev) | `http://<local-ip>:<port>` | `http://192.168.1.100:8080/api/v1` |
| Production | `https://<domain>` | `https://api.ezkey.yourdomain.com/api/v1` |

## Finding Your Local IP

### Linux/Mac
```bash
# Option 1
ifconfig | grep "inet "

# Option 2
ip addr show | grep "inet "

# Option 3
hostname -I
```

### Windows
```cmd
ipconfig

# Look for "IPv4 Address" under your network adapter
```

### Using Node.js
```javascript
const os = require('os');
const interfaces = os.networkInterfaces();
Object.keys(interfaces).forEach(name => {
  interfaces[name].forEach(iface => {
    if (iface.family === 'IPv4' && !iface.internal) {
      console.log(`${name}: ${iface.address}`);
    }
  });
});
```

---

For more information, see:
- [README.md](README.md) - Full documentation
- [DEPLOYMENT.md](DEPLOYMENT.md) - Deployment guide
- [QUICKSTART.md](QUICKSTART.md) - Quick start guide
