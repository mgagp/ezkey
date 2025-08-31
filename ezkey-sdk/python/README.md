# Ezkey Python SDK

The Ezkey Python SDK provides easy integration with Ezkey Admin and Auth APIs for Python applications.

## Features

- **Type Hints**: Full type annotations for better IDE support
- **Minimal Dependencies**: Uses only requests and cryptography
- **Configurable Endpoints**: Support for custom Admin and Auth API URLs
- **Complete API Coverage**: All Admin and Auth API operations
- **Exception Handling**: Comprehensive error handling
- **Python 3.7+ Compatible**: Works with Python 3.7 and higher

## Quick Start

### 1. Build the SDK

```bash
# Linux/Mac
./build.sh

# Windows
build.cmd
```

### 2. Install the SDK

```bash
pip install .
```

### 3. Basic Usage

```python
from ezkey_sdk import EzkeyClient, EzkeyException

# Initialize client with default localhost URLs
client = EzkeyClient()

# Or with custom URLs
client = EzkeyClient(
    admin_api_url="https://admin.yourcompany.com",
    auth_api_url="https://auth.yourcompany.com"
)

try:
    # Use Admin API
    integration = client.admin.create_integration(
        logo="https://company.com/logo.png",
        name="My App",
        description="My application description"
    )
    
    # Use Auth API
    binding = client.auth.bind_enrollment(enrollment_id)
    
except EzkeyException as e:
    print(f"API Error: {e.message}")
    print(f"Status: {e.status_code}")
```

## Complete Integration Example

The SDK includes a comprehensive demo application:

```bash
python demo/demo.py
```

## API Reference

See the full documentation in the source code and demo application.

## Requirements

- Python 3.7+
- requests>=2.25.0
- cryptography>=3.0.0

## License

MIT License - see LICENSE file in the project root.