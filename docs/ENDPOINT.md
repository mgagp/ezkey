# Ezkey API Endpoint Reference

## 📋 Table of Contents

- [Overview](#overview)
- [Security Model](#security-model)
- [Admin API Endpoints](#admin-api-endpoints)
- [Auth API Endpoints](#auth-api-endpoints)
- [Data Models](#data-models)
- [Error Handling](#error-handling)
- [Related Documentation](#related-documentation)

---

## 🎯 Overview

Ezkey provides two main APIs for different use cases:

- **Admin API** (Port 9080): Administrative management of integrations, enrollments, and authentication attempts
- **Auth API** (Port 8080): Mobile device operations for enrollment and authentication

### API Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web App       │    │   Mobile App    │    │   Backend App   │
│   (Frontend)    │    │   (User Device) │    │   (Your App)    │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          │ Admin API            │ Auth API             │ Admin API
          │ (Port 9080)          │ (Port 8080)          │ (Port 9080)
          │                      │                      │
          ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Ezkey Backend                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │   Admin     │  │    Auth     │  │    Core     │            │
│  │    API      │  │    API      │  │  Business   │            │
│  │             │  │             │  │   Logic     │            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Security Model

### Authentication Methods

- **Admin API**: Bearer token authentication (`bearerAuth`)
- **Auth API**: Cryptographic signature authentication (`signatureAuth`)

### Security Principles

1. **One-Time Proof Tokens**: Each authentication attempt has a unique, single-use token
2. **Cryptographic Signatures**: All mobile operations require device signatures
3. **Read-Once Guarantee**: Authentication attempts can only be read once
4. **Anti-Replay Protection**: Unique tokens prevent replay attacks

---

## 🛠️ Admin API Endpoints

**Base URL**: `http://localhost:9080` (Development) | `https://admin-api.ezkey.org` (Production)

### Integrations Management

#### List All Integrations
```http
GET /api/v1/integrations
```

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "logo": "https://example.com/logo.png",
    "i18n": [
      {
        "language": "en",
        "name": "ACME Corporation",
        "description": "Secure authentication system"
      }
    ]
  }
]
```

#### Get Integration by ID
```http
GET /api/v1/integrations/{id}
```

**Parameters**:
- `id` (path, integer): Integration ID

**Response**: `200 OK`
```json
{
  "id": 1,
  "logo": "https://example.com/logo.png",
  "i18n": [
    {
      "language": "en",
      "name": "ACME Corporation",
      "description": "Secure authentication system"
    }
  ]
}
```

#### Create Integration
```http
POST /api/v1/integrations
Content-Type: application/json
```

**Request Body**:
```json
{
  "logo": "https://example.com/logo.png",
  "i18n": [
    {
      "language": "en",
      "name": "ACME Corporation",
      "description": "Secure authentication system for ACME applications"
    }
  ]
}
```

**Response**: `201 Created`
```json
{
  "id": 1
}
```

#### Delete Integration
```http
DELETE /api/v1/integrations/{id}
```

**Parameters**:
- `id` (path, integer): Integration ID

**Response**: `204 No Content`

### Enrollments Management

#### List All Enrollments
```http
GET /api/v1/enrollments
```

**Response**: `200 OK`
```json
[
  {
    "id": 123,
    "integrationId": 1,
    "name": "John's iPhone",
    "challengeRequired": true,
    "active": false,
    "createdAt": "2024-01-15T10:30:00Z"
  }
]
```

#### Get Enrollment by ID
```http
GET /api/v1/enrollments/{id}
```

**Parameters**:
- `id` (path, integer): Enrollment ID

**Response**: `200 OK`
```json
{
  "id": 123,
  "integrationId": 1,
  "name": "John's iPhone",
  "challengeRequired": true,
  "active": false,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### Create Enrollment
```http
POST /api/v1/enrollments
Content-Type: application/json
```

**Request Body**:
```json
{
  "integrationId": 1,
  "name": "John's iPhone",
  "challengeRequired": true
}
```

**Response**: `201 Created`
```json
{
  "id": 123
}
```

#### Delete Enrollment
```http
DELETE /api/v1/enrollments/{id}
```

**Parameters**:
- `id` (path, integer): Enrollment ID

**Response**: `204 No Content`

### Authentication Attempts Management

#### List All Auth Attempts
```http
GET /api/v1/auth-attempts
```

**Response**: `200 OK`
```json
[
  {
    "id": 456,
    "enrollmentId": 123,
    "challengeRequested": true,
    "read": false,
    "responded": false,
    "valid": false,
    "accepted": false,
    "createdAt": "2024-01-15T11:00:00Z"
  }
]
```

#### Get Auth Attempt by ID
```http
GET /api/v1/auth-attempts/{id}
```

**Parameters**:
- `id` (path, integer): Auth Attempt ID

**Response**: `200 OK`
```json
{
  "id": 456,
  "enrollmentId": 123,
  "challengeRequested": true,
  "read": false,
  "responded": false,
  "valid": false,
  "accepted": false,
  "createdAt": "2024-01-15T11:00:00Z"
}
```

#### Create Auth Attempt
```http
POST /api/v1/auth-attempts
Content-Type: application/json
```

**Request Body**:
```json
{
  "enrollmentId": 123,
  "challengeRequested": true
}
```

**Response**: `201 Created`
```json
{
  "id": 456
}
```

#### Wait for Authentication Response
```http
GET /api/v1/auth-attempts/{id}/wait
```

**Parameters**:
- `id` (path, integer): Auth Attempt ID
- `timeout` (query, integer, optional): Maximum wait time in seconds (default: 30)
- `polling` (query, integer, optional): Polling interval in seconds (default: 2)

**Response**: `200 OK` (Authentication completed)
```json
{
  "authAttempt": {
    "id": 456,
    "enrollmentId": 123,
    "challengeRequested": true,
    "read": true,
    "responded": true,
    "valid": true,
    "accepted": true,
    "createdAt": "2024-01-15T11:00:00Z"
  },
  "status": "ACCEPTED",
  "completed": true,
  "timeoutReached": false,
  "waitDuration": 15,
  "completedAt": "2024-01-15T11:00:15Z"
}
```

**Response**: `408 Request Timeout` (Timeout reached)
```json
{
  "authAttempt": {
    "id": 456,
    "enrollmentId": 123,
    "challengeRequested": true,
    "read": false,
    "responded": false,
    "valid": false,
    "accepted": false,
    "createdAt": "2024-01-15T11:00:00Z"
  },
  "status": "PENDING",
  "completed": false,
  "timeoutReached": true,
  "waitDuration": 30,
  "completedAt": null
}
```

#### Delete Auth Attempt
```http
DELETE /api/v1/auth-attempts/{id}
```

**Parameters**:
- `id` (path, integer): Auth Attempt ID

**Response**: `204 No Content`

---

## 📱 Auth API Endpoints

**Base URL**: `http://localhost:8080` (Development) | `https://auth-api.ezkey.org` (Production)

### Enrollment Operations

#### Bind Device to Enrollment
```http
GET /api/v1/enrollments/bind/{enrollmentId}
```

**Parameters**:
- `enrollmentId` (path, integer): Enrollment ID

**Response**: `200 OK`
```json
{
  "enrollmentId": 123,
  "integrationId": 1,
  "name": "John's iPhone",
  "challengeRequired": true,
  "challenge": 123456,
  "enrollmentProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "integrationPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
}
```

#### Verify Enrollment
```http
POST /api/v1/enrollments/verify
Content-Type: application/json
```

**Request Body**:
```json
{
  "enrollmentId": 123,
  "challengeResponse": 123456,
  "devicePublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "enrollmentProofTokenSigned": "eyJhbGciOiJSUzI1NiJ9..."
}
```

**Response**: `200 OK`
```json
{
  "active": true
}
```

### Authentication Operations

#### Check for Pending Auth Requests
```http
POST /api/v1/auth-attempts/pending/{enrollmentId}
Content-Type: application/json
```

**Parameters**:
- `enrollmentId` (path, integer): Enrollment ID

**Request Body**:
```json
{
  "deviceProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "deviceProofTokenSigned": "eyJhbGciOiJSUzI1NiJ9..."
}
```

**Response**: `200 OK` (Pending request found)
```json
{
  "id": 456,
  "enrollmentId": 123,
  "challengeRequested": true,
  "challenge": 789012,
  "authAttemptProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "createdAt": "2024-01-15T11:00:00Z"
}
```

**Response**: `204 No Content` (No pending request)

#### Respond to Auth Attempt
```http
POST /api/v1/auth-attempts/respond/{authAttemptId}
Content-Type: application/json
```

**Parameters**:
- `authAttemptId` (path, integer): Auth Attempt ID

**Request Body**:
```json
{
  "accepted": true,
  "authAttemptProofTokenSigned": "eyJhbGciOiJSUzI1NiJ9...",
  "challengeResponse": 789012
}
```

**Response**: `200 OK`
```json
{
  "valid": true
}
```

---

## 📊 Data Models

### Integration Models

#### IntegrationCreateRequestDto
```json
{
  "logo": "string",
  "i18n": [
    {
      "language": "string",
      "name": "string",
      "description": "string"
    }
  ]
}
```

#### IntegrationResponseDto
```json
{
  "id": "integer",
  "logo": "string",
  "i18n": [
    {
      "language": "string",
      "name": "string",
      "description": "string"
    }
  ]
}
```

### Enrollment Models

#### EnrollmentCreateRequestDto
```json
{
  "integrationId": "integer",
  "name": "string",
  "challengeRequired": "boolean"
}
```

#### EnrollmentResponseDto
```json
{
  "id": "integer",
  "integrationId": "integer",
  "name": "string",
  "challengeRequired": "boolean",
  "active": "boolean",
  "createdAt": "string (ISO 8601)"
}
```

### Auth Attempt Models

#### AuthAttemptCreateRequestDto
```json
{
  "enrollmentId": "integer",
  "challengeRequested": "boolean"
}
```

#### AuthAttemptResponseDto
```json
{
  "id": "integer",
  "enrollmentId": "integer",
  "challengeRequested": "boolean",
  "read": "boolean",
  "responded": "boolean",
  "valid": "boolean",
  "accepted": "boolean",
  "createdAt": "string (ISO 8601)"
}
```

---

## ⚠️ Error Handling

### HTTP Status Codes

| Code | Description | Usage |
|------|-------------|-------|
| `200` | OK | Successful GET, POST operations |
| `201` | Created | Successful resource creation |
| `204` | No Content | Successful deletion, no pending requests |
| `400` | Bad Request | Invalid request parameters |
| `401` | Unauthorized | Missing or invalid authentication |
| `403` | Forbidden | Insufficient permissions |
| `404` | Not Found | Resource not found |
| `408` | Request Timeout | Wait operation timed out |
| `500` | Internal Server Error | Server-side error |

### Error Response Format

```json
{
  "timestamp": "2024-01-15T11:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request parameters",
  "path": "/api/v1/integrations"
}
```

---

## 🔄 Authentication Flow

### Complete Enrollment Flow

1. **Create Integration** (Admin API)
   ```http
   POST /api/v1/integrations
   ```

2. **Create Enrollment** (Admin API)
   ```http
   POST /api/v1/enrollments
   ```

3. **Bind Device** (Auth API)
   ```http
   GET /api/v1/enrollments/bind/{enrollmentId}
   ```

4. **Verify Enrollment** (Auth API)
   ```http
   POST /api/v1/enrollments/verify
   ```

### Complete Authentication Flow

1. **Create Auth Attempt** (Admin API)
   ```http
   POST /api/v1/auth-attempts
   ```

2. **Check Pending** (Auth API)
   ```http
   POST /api/v1/auth-attempts/pending/{enrollmentId}
   ```

3. **Respond to Auth** (Auth API)
   ```http
   POST /api/v1/auth-attempts/respond/{authAttemptId}
   ```

4. **Wait for Response** (Admin API)
   ```http
   GET /api/v1/auth-attempts/{id}/wait
   ```

---

## 📖 Related Documentation

- **[Architecture & Security](ARCHITECTURE.md)** - System architecture and security design
- **[Development Guide](DEVELOPMENT.md)** - Development workflow and testing strategy
- **[Cryptographic Implementation](CRYPTO.md)** - Detailed crypto specifications
- **[Main Project README](../README.md)** - Project overview and quick start
- **[Monitoring Setup](monitoring/README.md)** - Production monitoring guide

---

*This documentation is based on the actual OpenAPI specifications and reflects the current implementation of the Ezkey APIs. All endpoints, parameters, and response formats are validated against the live API specifications.*