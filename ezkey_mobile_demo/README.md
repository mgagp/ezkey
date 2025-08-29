# Ezkey Mobile Demo - Development Setup

## Flutter Installation Location

### Current Setup
- **Flutter SDK Location:** `C:\Tools\flutter`
- **Flutter Version:** 3.32.5 (stable)
- **Dart Version:** 3.8.1
- **Channel:** stable

### Command Usage
Since Flutter is not in the system PATH, use the full path for commands:

```powershell
# Flutter commands
C:\Tools\flutter\bin\flutter.bat --version
C:\Tools\flutter\bin\flutter.bat doctor
C:\Tools\flutter\bin\flutter.bat create ezkey_demo

# Dart commands
C:\Tools\flutter\bin\dart.bat --version
C:\Tools\flutter\bin\dart.bat pub get
```

### Adding to PATH (Optional)
To add Flutter to system PATH permanently:

1. Open System Properties → Advanced → Environment Variables
2. Edit the PATH variable
3. Add: `C:\Tools\flutter\bin`
4. Restart terminal/PowerShell

## Development Environment Status

### ✅ Available Platforms
- **Web (Chrome):** ✅ Ready for development
- **Android Studio:** ✅ Installed (version 2025.1.1)

### ⚠️ Missing Components
- **Visual Studio:** Not installed (required for Windows app development)
- **Android cmdline-tools:** Missing (optional for Android development)

### Current Focus
This project will focus on **web development** using Chrome, which is fully functional.

## Tool Access Whitelisting

### PowerShell Execution Policy
If you encounter execution policy restrictions:

```powershell
# Check current policy
Get-ExecutionPolicy

# Set policy for current user (if needed)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

# Or for all users (requires admin)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope LocalMachine
```

### Windows Defender / Antivirus
If tools are blocked by Windows Defender:

1. **Temporary Solution:**
   - Add `C:\Tools\flutter` to Windows Defender exclusions
   - Settings → Update & Security → Windows Security → Virus & threat protection
   - Manage settings → Add or remove exclusions

2. **Permanent Solution:**
   - Add the entire `C:\Tools` directory to exclusions
   - This covers Flutter, Dart, and other development tools

### Firewall Access
If network access is blocked:

1. **Allow Flutter through firewall:**
   - Windows Defender Firewall → Allow an app through firewall
   - Add `C:\Tools\flutter\bin\flutter.bat`
   - Allow on Private and Public networks

2. **Allow Dart through firewall:**
   - Add `C:\Tools\flutter\bin\dart.bat`
   - Allow on Private and Public networks

### Git Access (if needed)
If Git is blocked for Flutter dependencies:

```powershell
# Configure Git to trust the Flutter directory
git config --global --add safe.directory C:\Tools\flutter

# Or trust all directories (less secure)
git config --global --add safe.directory "*"
```

## Development Commands

### Project Setup
```powershell
# Create new Flutter project
C:\Tools\flutter\bin\flutter.bat create ezkey_demo

# Navigate to project
cd ezkey_demo

# Get dependencies
C:\Tools\flutter\bin\flutter.bat pub get
```

### Development
```powershell
# Run in debug mode (web)
C:\Tools\flutter\bin\flutter.bat run -d chrome

# Run in release mode (web)
C:\Tools\flutter\bin\flutter.bat run -d chrome --release

# Hot reload (while app is running)
# Press 'r' in terminal or save files in IDE
```

### Building
```powershell
# Build for web
C:\Tools\flutter\bin\flutter.bat build web

# Build for Android (if Android Studio is configured)
C:\Tools\flutter\bin\flutter.bat build apk
```

## Troubleshooting

### Common Issues

#### 1. "flutter command not found"
- Use full path: `C:\Tools\flutter\bin\flutter.bat`
- Or add to PATH (see above)

#### 2. "Execution policy prevents running scripts"
- Run: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

#### 3. "Access denied" or "Blocked by antivirus"
- Add `C:\Tools\flutter` to Windows Defender exclusions
- Or temporarily disable real-time protection during development

#### 4. "Network access blocked"
- Allow Flutter/Dart through Windows Firewall
- Check corporate firewall settings if applicable

#### 5. "Git repository access issues"
- Configure Git safe directories
- Check Git credentials and authentication

### Verification Commands
```powershell
# Verify Flutter installation
C:\Tools\flutter\bin\flutter.bat doctor

# Verify Dart installation
C:\Tools\flutter\bin\dart.bat --version

# Test web development
C:\Tools\flutter\bin\flutter.bat run -d chrome --web-port 8080
```

## Security Notes

### Development vs Production
- **Development:** Local tools with full access for debugging
- **Production:** Restricted access with proper security policies

### Recommended Security Practices
1. **Use specific exclusions** rather than disabling security entirely
2. **Regular updates** of Flutter SDK and dependencies
3. **Code signing** for production builds
4. **Network security** for API calls in production

### Corporate Environment
If working in a corporate environment:
- Contact IT for proper tool whitelisting
- Use approved development environments
- Follow corporate security policies
- Consider using approved IDEs (VS Code, Android Studio)

## Next Steps

1. **Verify tool access** using the commands above
2. **Create the Flutter project** using the documented commands
3. **Follow the development tasks** in `TASKS.md`
4. **Test the setup** with a simple "Hello World" app

---

**Last Updated:** January 2025  
**Flutter Version:** 3.32.5  
**Dart Version:** 3.8.1
