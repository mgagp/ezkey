# Ezkey Mobile V2 - React Native with Expo

A minimal React Native mobile application for Ezkey MFA/Passkey authentication using Expo.

## Overview

Ezkey Mobile V2 is a single-screen mobile application that provides:

1. **Enrollment Flow**: Bind and verify device enrollment with enrollment ID and proof token
2. **Ready State**: Summary of enrolled device and integration details
3. **Authentication**: Check for and respond to pending authentication attempts

## Features

- ✅ Single-screen three-step workflow
- ✅ Native cryptographic operations (RSA-2048, SHA256withRSA)
- ✅ Secure local storage for enrollment data
- ✅ Integration logo display
- ✅ Challenge code support for enrollment and authentication
- ✅ Approve/Deny authentication requests
- ✅ React Native with Expo for easy deployment

## Technology Stack

- **Framework**: React Native with Expo
- **Cryptography**: Native Android module (Kotlin) for RSA operations
- **Storage**: AsyncStorage for secure enrollment data
- **API Communication**: Axios for REST API calls
- **UI**: React Native components with custom styling

## Prerequisites

- Node.js 18+ and npm
- Java 17+ (for Android development)
- Android Studio (for building Android app)
- Android SDK (installed via Android Studio)
- Expo CLI (installed automatically)

## Project Structure

```
ezkey_mobile_v2/
├── App.js                      # Main application component
├── src/
│   ├── services/
│   │   └── AuthApiService.ts   # Auth API integration
│   └── storage/
│       └── StorageService.ts   # Secure enrollment storage
├── modules/
│   └── expo-crypto-native/     # Native crypto module
│       ├── android/            # Android native implementation
│       │   └── src/main/java/expo/modules/cryptonative/
│       │       ├── ExpoCryptoNativeModule.kt
│       │       └── ExpoCryptoNativePackage.kt
│       ├── index.ts            # TypeScript bindings
│       └── package.json
├── package.json
└── README.md
```

## Setup

### 1. Install Dependencies

```bash
cd ezkey_mobile_v2
npm install
```

### 2. Configure API Endpoint

Edit `src/services/AuthApiService.ts` to set your backend URL:

```typescript
// For Android Emulator (default)
const AUTH_API_BASE_URL = 'http://10.0.2.2:8080/api/v1';

// For physical device, use your computer's IP
// const AUTH_API_BASE_URL = 'http://192.168.1.100:8080/api/v1';
```

## Building for Android

### Using Android Studio (Recommended)

1. **Generate Android Project**:
```bash
npx expo prebuild --platform android
```

2. **Open in Android Studio**:
```bash
# Open the android folder in Android Studio
studio android/
```

3. **Build APK**:
   - In Android Studio: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
   - APK will be in: `android/app/build/outputs/apk/debug/app-debug.apk`

4. **Install on Device/Emulator**:
```bash
# Install on connected device
adb install android/app/build/outputs/apk/debug/app-debug.apk

# Or run directly from Android Studio
# Click the green "Run" button
```

### Using Gradle Command Line

```bash
# Generate Android project
npx expo prebuild --platform android

# Build debug APK
cd android
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Build Script

A convenience script is provided:

```bash
# Make executable
chmod +x build-android.sh

# Run build
./build-android.sh
```

## Running on Simulator

### Android Emulator

1. **Start Android Emulator** (from Android Studio or command line):
```bash
# List available emulators
emulator -list-avds

# Start specific emulator
emulator -avd Pixel_5_API_33
```

2. **Run the app**:
```bash
# Using Expo
npx expo start

# Press 'a' to open in Android emulator
```

3. **Or run directly**:
```bash
npm run android
```

The app will automatically install and launch on the running emulator.

## Deployment with Expo

### Expo Go Development

For quick testing without building:

```bash
# Start development server
npx expo start

# Scan QR code with Expo Go app on your phone
```

**Note**: Native modules require a development build, so some features may not work in Expo Go.

### Expo Development Build

For full functionality with native modules:

```bash
# Install EAS CLI
npm install -g eas-cli

# Login to Expo
eas login

# Configure project
eas build:configure

# Build development client
eas build --profile development --platform android

# Install the build on your device
# Download and install the APK from the build URL
```

### Production Build with Expo

```bash
# Build production APK
eas build --profile production --platform android

# Or build AAB for Google Play
eas build --profile production --platform android --format aab
```

## Usage Guide

### Step 1: Enrollment

1. Obtain enrollment ID and proof token from your Ezkey admin
2. Launch the app
3. Enter the enrollment ID (numeric)
4. Enter the enrollment proof token (string)
5. Tap "Bind Device" - the app will generate device keys
6. Enter the 6-digit challenge code provided by admin
7. Tap "Verify Enrollment"

### Step 2: Ready State

- View enrollment summary
- See integration name, logo, and details
- Tap "Check for Auth Requests" to proceed

### Step 3: Authentication

1. Tap "Check Pending Requests" to poll for auth attempts
2. If a pending request exists:
   - View integration logo and details
   - Enter challenge code if required
   - Tap "Deny" to reject or "Authorize" to approve
3. Return to summary or check for more requests

## API Endpoints Used

The app integrates with the following Ezkey Auth API endpoints:

- `POST /api/v1/enrollments/bind` - Bind device to enrollment
- `POST /api/v1/enrollments/verify` - Verify enrollment with challenge
- `POST /api/v1/auth-attempts/pending` - Check for pending auth attempts
- `POST /api/v1/auth-attempts/respond` - Respond to auth attempt

## Cryptographic Operations

The app uses native Android cryptography via the custom native module:

- **Key Generation**: RSA-2048 key pairs
- **Signatures**: SHA256withRSA algorithm
- **Proof Tokens**: Cryptographically secure random tokens
- **Key Storage**: Secure local storage with AsyncStorage

All cryptographic operations match the Java implementation in `ezkey_mobile_v1`.

## Configuration

### Network Configuration (Android)

For development with HTTP (non-HTTPS) endpoints, the app includes network security configuration.

If you encounter network issues, ensure your backend allows HTTP connections in development.

### Storage

Enrollment data is stored locally using AsyncStorage with the following structure:

```typescript
{
  enrollmentId: number;
  enrollmentName: string;
  enrollmentProofToken: string;
  integrationName: string;
  integrationDescription: string;
  integrationLogo?: string;
  integrationPublicKey: string;
  devicePublicKey: string;
  devicePrivateKey: string;  // Stored securely
  verified: boolean;
  createdAt: string;
}
```

## Troubleshooting

### Cannot connect to backend

- **Emulator**: Use `http://10.0.2.2:8080` (maps to host's localhost)
- **Physical device**: Use your computer's local IP (e.g., `http://192.168.1.100:8080`)
- Ensure backend is running and accessible

### Native module not found

```bash
# Regenerate native modules
npx expo prebuild --clean

# Or use development build
eas build --profile development --platform android
```

### Build errors

```bash
# Clean build
cd android
./gradlew clean

# Rebuild
./gradlew assembleDebug
```

### AsyncStorage warnings

AsyncStorage is recommended for Expo. For production, consider using:
- Expo SecureStore for sensitive data
- React Native MMKV for performance

## Development

### Running in Development Mode

```bash
# Start Metro bundler
npm start

# In another terminal, run on Android
npm run android
```

### Debugging

- Use React Native debugger
- Check logs: `adb logcat`
- Expo DevTools: available at http://localhost:19002

## Security Considerations

- **Private Keys**: Stored in AsyncStorage - consider Expo SecureStore for production
- **Network**: Use HTTPS in production
- **Token Storage**: Proof tokens stored locally - implement secure deletion on logout
- **Challenge Codes**: Displayed in plain text - consider masked input for production

## Future Enhancements

- [ ] QR code scanning for enrollment
- [ ] Push notifications for auth requests
- [ ] Biometric authentication
- [ ] Multiple enrollment support
- [ ] Secure key storage with hardware backing
- [ ] iOS support

## License

MIT License - see LICENSE file in the project root.

## Related

- **Ezkey Core**: Backend API implementation
- **Ezkey Mobile V1**: Original Kotlin Android implementation
- **Ezkey Demo Device**: Reference device implementation

---

**Ezkey Mobile V2** - Simple, secure, and open-source MFA for everyone.
