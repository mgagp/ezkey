# Ezkey Mobile V2 - Quick Start Guide

Get started with Ezkey Mobile V2 in 5 minutes!

## 🚀 Quick Setup (5 minutes)

### 1. Prerequisites Check

```bash
# Verify Node.js (need 18+)
node --version

# Verify npm
npm --version

# Verify Java (need 17+)
java -version
```

If any are missing, install:
- Node.js: https://nodejs.org/
- Java: https://adoptium.net/

### 2. Install Dependencies

```bash
cd ezkey_mobile_v2
npm install
```

⏱️ Takes ~2 minutes

### 3. Configure Backend

Edit `src/services/AuthApiService.ts`:

```typescript
// Line 13 - Change to your backend URL
const AUTH_API_BASE_URL = 'http://10.0.2.2:8080/api/v1'; // For emulator
```

- Emulator: Use `10.0.2.2` (maps to localhost)
- Physical device: Use your computer's IP (e.g., `192.168.1.100`)

### 4. Start Backend

```bash
# Terminal 1 - Auth API
cd ezkey-auth-api
mvn spring-boot:run

# Terminal 2 - Admin API  
cd ezkey-admin-api
mvn spring-boot:run
```

### 5. Run the App

**Option A - Using Expo (Simplest)**:
```bash
npm start
# Press 'a' to open on Android emulator
```

**Option B - Build APK**:
```bash
./build-android.sh
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Using the App

### Step 1: Enroll Device

1. Get enrollment from admin:
   ```bash
   # Create integration
   curl -X POST http://localhost:9080/api/v1/integrations \
     -H "Content-Type: application/json" \
     -d '{
       "logo": "https://example.com/logo.png",
       "i18n": [{"language": "en", "name": "My App", "description": "Test App"}]
     }'
   # Returns: {"id": 1}

   # Create enrollment
   curl -X POST http://localhost:9080/api/v1/enrollments \
     -H "Content-Type: application/json" \
     -d '{
       "integrationId": 1,
       "name": "John Phone",
       "authAttemptChallengeRequired": true
     }'
   # Returns: {"enrollmentId": 1, "enrollmentChallenge": 123456}
   ```

2. In mobile app:
   - Enter Enrollment ID: `1`
   - Enter Proof Token: (from response - long string)
   - Tap "Bind Device"
   - Enter Challenge: `123456`
   - Tap "Verify Enrollment"

### Step 2: Ready State

- View enrollment summary
- See integration details
- Tap "Check for Auth Requests"

### Step 3: Authenticate

1. Create auth attempt from admin:
   ```bash
   curl -X POST http://localhost:9080/api/v1/auth-attempts \
     -H "Content-Type: application/json" \
     -d '{
       "enrollmentId": 1,
       "challengeRequested": false
     }'
   # Returns: {"authAttemptId": 1}
   ```

2. In mobile app:
   - Tap "Check Pending Requests"
   - View auth request details
   - Enter challenge if required
   - Tap "Authorize" or "Deny"

---

## 🔧 Troubleshooting

### Can't connect to backend

```bash
# Test backend is running
curl http://localhost:8080/actuator/health

# For emulator, use 10.0.2.2 in app
# For device, use your computer's IP: ifconfig | grep "inet "
```

### Native module error

```bash
# Regenerate native code
npx expo prebuild --clean
npm run android
```

### Build fails

```bash
# Clean and rebuild
cd android
./gradlew clean
./gradlew assembleDebug
```

### App crashes

```bash
# View logs
adb logcat | grep ReactNative
```

---

## 📚 Next Steps

- **Full Documentation**: See [README.md](README.md)
- **Deployment Guide**: See [DEPLOYMENT.md](DEPLOYMENT.md)
- **API Reference**: See [../docs/ENDPOINT.md](../docs/ENDPOINT.md)

---

## 🎯 Common Commands

```bash
# Development
npm start              # Start dev server
npm run android        # Run on Android

# Building  
./build-android.sh     # Build APK with script
npx expo prebuild      # Generate Android project

# Installation
adb devices            # List connected devices
adb install app.apk    # Install APK

# Debugging
adb logcat             # View logs
```

---

## ✅ Checklist

- [ ] Node.js and npm installed
- [ ] Java 17+ installed
- [ ] Dependencies installed (`npm install`)
- [ ] Backend URL configured
- [ ] Backend services running
- [ ] App running on emulator/device
- [ ] Enrollment completed
- [ ] Authentication tested

---

## 💡 Tips

- **Reload app**: Press `R` twice or shake device
- **Developer menu**: Cmd+M (Mac) or Ctrl+M (Windows)
- **Clear cache**: `npm start -c`
- **Reset state**: Clear app data or reinstall

---

**Ready to go!** If you encounter issues, see [DEPLOYMENT.md](DEPLOYMENT.md) for detailed troubleshooting.
