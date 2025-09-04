# Ezkey PAM Module - Mock Version

This is a mock implementation of the Ezkey PAM module for testing SSH integration on Rocky Linux 9.6.

## What This Module Does

This mock module simulates the complete Ezkey MFA flow:
1. Intercepts SSH authentication attempts
2. Logs all activities to `/var/log/secure`
3. Simulates API calls to Ezkey backend
4. Simulates waiting for mobile device response
5. Returns success/failure based on configuration

## Quick Installation

1. **Extract and build:**
   ```bash
   # Extract the files (if from zip)
   unzip pam-ezkey-mock.zip
   cd pam-ezkey-mock
   
   # Build the module
   make all
   ```

2. **Install (as root):**
   ```bash
   sudo ./install.sh
   ```

3. **Test the installation:**
   ```bash
   ./test/test_pam.sh
   ```

## Manual Installation Steps

If you prefer to install manually:

1. **Install dependencies:**
   ```bash
   sudo dnf install -y pam-devel gcc make
   ```

2. **Build and install module:**
   ```bash
   make all
   sudo make install
   ```

3. **Configure PAM for SSH:**
   ```bash
   sudo cp config/pam_ezkey.conf /etc/security/
   ```

4. **Edit `/etc/pam.d/sshd` and add after the first auth line:**
   ```
   auth       required     pam_ezkey.so debug
   ```

5. **Update SSH configuration in `/etc/ssh/sshd_config`:**
   ```
   UsePAM yes
   ChallengeResponseAuthentication yes
   KbdInteractiveAuthentication yes
   ```

6. **Restart SSH daemon:**
   ```bash
   sudo systemctl restart sshd
   ```

## Configuration

Edit `/etc/security/pam_ezkey.conf` to modify:
- `mock_success=1` - Set to 0 to simulate authentication failures
- `mock_delay=5` - Number of seconds to simulate waiting
- `debug=1` - Enable/disable verbose logging

You can also pass options directly in PAM configuration:
```
auth required pam_ezkey.so debug mock_delay=10 mock_failure
```

## Testing

1. **Monitor logs:**
   ```bash
   sudo tail -f /var/log/secure
   ```

2. **Test SSH connection:**
   ```bash
   ssh $USER@localhost
   ```

3. **Run test suite:**
   ```bash
   ./test/test_pam.sh
   ```

## Expected Log Output

When you SSH, you should see logs like:
```
Dec 13 10:30:15 hostname sshd[12345]: pam_ezkey: === EZKEY PAM MODULE STARTED ===
Dec 13 10:30:15 hostname sshd[12345]: pam_ezkey: Authentication requested for user: testuser
Dec 13 10:30:15 hostname sshd[12345]: pam_ezkey: MOCK: POST http://localhost:9080/api/v1/auth-attempts (user: testuser)
Dec 13 10:30:16 hostname sshd[12345]: pam_ezkey: MOCK: Created auth attempt ID: auth_12345_mock
Dec 13 10:30:16 hostname sshd[12345]: pam_ezkey: MOCK: Notification sent to user's mobile device
Dec 13 10:30:16 hostname sshd[12345]: pam_ezkey: MOCK: Waiting for user response (timeout: 30s)...
Dec 13 10:30:21 hostname sshd[12345]: pam_ezkey: MOCK: GET http://localhost:9080/api/v1/auth-attempts/auth_12345_mock/wait -> ACCEPTED
Dec 13 10:30:21 hostname sshd[12345]: pam_ezkey: MOCK: User accepted authentication on mobile device
Dec 13 10:30:21 hostname sshd[12345]: pam_ezkey: Authentication result for testuser: SUCCESS
Dec 13 10:30:21 hostname sshd[12345]: pam_ezkey: === EZKEY PAM MODULE FINISHED ===
```

## Module Arguments

You can customize behavior by passing arguments in PAM configuration:

```bash
# Basic configuration
auth required pam_ezkey.so

# Debug mode with custom delay
auth required pam_ezkey.so debug mock_delay=10

# Simulate failure for testing
auth required pam_ezkey.so debug mock_failure

# Multiple options
auth required pam_ezkey.so debug mock_delay=8 mock_failure
```

Available arguments:
- `debug` - Enable verbose logging
- `mock_delay=N` - Set delay in seconds (1-60)
- `mock_failure` - Simulate authentication failure

## Security Notes

**⚠️ Important:** This is a MOCK module for development only!

- It does NOT perform real MFA authentication
- It does NOT connect to actual Ezkey APIs
- It should NEVER be used in production
- All authentication attempts will succeed by default
- Use only for testing PAM integration

## Troubleshooting

### Module not found
```bash
# Check if module exists
ls -la /lib64/security/pam_ezkey.so

# Rebuild if missing
make clean && make all && sudo make install
```

### SSH connection hangs
```bash
# Check SSH daemon syntax
sudo sshd -t

# Check PAM configuration
sudo pamtest login $USER authenticate
```

### No logs appearing
```bash
# Check rsyslog service
sudo systemctl status rsyslog

# Check syslog configuration
grep authpriv /etc/rsyslog.conf
```

### Permission denied
```bash
# Check module permissions
ls -la /lib64/security/pam_ezkey.so
# Should be: -rwxr-xr-x root root

# Fix if needed
sudo chmod 755 /lib64/security/pam_ezkey.so
```

## Integration with Real Ezkey

To integrate with your real Ezkey system:

1. **Modify API endpoints** in `src/ezkey_config.h`:
   ```c
   #define EZKEY_ADMIN_API_URL "https://your-ezkey-server:9080"
   #define EZKEY_AUTH_API_URL "https://your-ezkey-server:8080"
   ```

2. **Replace mock functions** with real HTTP client code
3. **Add proper error handling** for network failures
4. **Implement enrollment detection** for users
5. **Add configuration file parsing** for `/etc/security/pam_ezkey.conf`

## Directory Structure

```
pam-ezkey-mock/
├── src/
│   ├── pam_ezkey.c          # Main PAM module source
│   └── ezkey_config.h       # Configuration constants
├── config/
│   └── pam_ezkey.conf       # Runtime configuration
├── test/
│   └── test_pam.sh          # Test suite
├── build/                   # Generated build files
├── Makefile                 # Build configuration
├── install.sh              # Automated installer
└── README.md               # This file
```

## License

This mock module is provided for development and testing purposes.
Adapt as needed for your Ezkey integration project.
