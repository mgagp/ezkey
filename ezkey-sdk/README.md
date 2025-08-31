# Ezkey SDK

This directory contains Software Development Kits (SDKs) for integrating with Ezkey APIs in multiple programming languages.

## Overview

Ezkey SDKs provide easy-to-use client libraries for both Admin API and Auth API endpoints. Each SDK is generated from OpenAPI specifications and includes:

- **Admin API Client**: For managing integrations, enrollments, and authentication attempts
- **Auth API Client**: For device enrollment and authentication flows  
- **Configuration**: Configurable endpoint URLs
- **Zero Dependencies**: No external library dependencies beyond language standards
- **Demo Applications**: Complete integration examples

## Available SDKs

| Language | Directory | Description |
|----------|-----------|-------------|
| Java | [`java/`](java/) | Java 8+ compatible SDK using standard HTTP client |
| JavaScript/TypeScript | [`javascript/`](javascript/) | Node.js and browser compatible SDK |
| Python | [`python/`](python/) | Python 3.7+ compatible SDK |
| .NET | [`dotnet/`](dotnet/) | .NET 6+ compatible SDK |

## Quick Start

Each SDK includes a demo application that demonstrates the complete Ezkey integration workflow:

1. **Create Integration** - Set up a new application integration
2. **Create Enrollment** - Generate enrollment for a user device
3. **Bind & Verify** - Complete device enrollment process
4. **Create Auth Attempt** - Request user authentication
5. **Check Pending** - Poll for pending authentication requests
6. **Accept/Deny** - Handle authentication responses

## API Endpoints

The SDKs interact with two main Ezkey APIs:

### Admin API (Port 9080)
- Integration management (CRUD)
- Enrollment administration
- Authentication attempt creation and monitoring
- Wait API for synchronous authentication flows

### Auth API (Port 8080)  
- Device enrollment binding and verification
- Authentication attempt responses
- Mobile-optimized endpoints

## Documentation

- [Admin API OpenAPI Spec](../ezkey-demo-app-acme/openapi-spec.json)
- [Auth API OpenAPI Spec](../ezkey-demo-device/openapi-spec.json)
- [Ezkey Documentation](../ezkey-docs/)

## Building SDKs

Each SDK includes build scripts for easy compilation:

```bash
# Build specific SDK
cd java && ./build.sh
cd javascript && ./build.sh  
cd python && ./build.sh
cd dotnet && ./build.sh
```

## Security Considerations

All SDKs implement Ezkey's security model:
- **One-time proof tokens** for authentication attempts
- **Cryptographic signatures** for request validation
- **Secure key management** in demo applications
- **HTTPS support** for production deployments

## License

All SDKs are released under the MIT License, same as the main Ezkey project.