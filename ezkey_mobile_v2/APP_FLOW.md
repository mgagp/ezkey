# Ezkey Mobile V2 - Application Flow

## Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Ezkey Mobile V2 - App Flow                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         STEP 1: ENROLLMENT                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User Input:                                                    │
│  ┌────────────────────────────────────────┐                   │
│  │ Enrollment ID:  [_____________]        │                   │
│  │ Proof Token:    [_____________]        │                   │
│  │                 [  Bind Device  ]      │                   │
│  └────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  POST /api/v1/enrollments/bind                                │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────┐              │
│  │ Generate RSA-2048 Key Pair on Device        │              │
│  │ • Private Key (stored securely)             │              │
│  │ • Public Key (sent to backend)              │              │
│  └─────────────────────────────────────────────┘              │
│           │                                                     │
│           ▼                                                     │
│  Display Integration Info:                                     │
│  ┌────────────────────────────────────────┐                   │
│  │ [Logo]                                 │                   │
│  │ Integration Name                       │                   │
│  │ Description                            │                   │
│  │                                        │                   │
│  │ Challenge Code: [______]              │                   │
│  │                 [Verify Enrollment]   │                   │
│  └────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  Sign enrollmentProofToken with Device Private Key            │
│           │                                                     │
│           ▼                                                     │
│  POST /api/v1/enrollments/verify                             │
│  {                                                             │
│    enrollmentId,                                              │
│    challengeResponse,                                         │
│    devicePublicKey,                                           │
│    enrollmentProofTokenSigned                                 │
│  }                                                             │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────┐              │
│  │ Save Enrollment to Local Storage            │              │
│  │ • Enrollment details                        │              │
│  │ • Integration info & logo                   │              │
│  │ • Device keys                               │              │
│  │ • Proof tokens                              │              │
│  └─────────────────────────────────────────────┘              │
│           │                                                     │
│           ▼                                                     │
│      ✓ Enrollment Complete                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    STEP 2: READY TO AUTHENTICATE                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Display Summary:                                              │
│  ┌────────────────────────────────────────┐                   │
│  │ [Logo]                                 │                   │
│  │                                        │                   │
│  │ ✓ Enrolled as: John's Phone          │                   │
│  │ ✓ Integration: ACME Corp              │                   │
│  │ ✓ Enrollment ID: 123                  │                   │
│  │ ✓ Ready to authenticate               │                   │
│  │                                        │                   │
│  │    [Check for Auth Requests]          │                   │
│  └────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│      Proceed to Step 3                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  STEP 3: AUTHENTICATION REQUESTS                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User Action:                                                  │
│  ┌────────────────────────────────────────┐                   │
│  │    [Check Pending Requests]            │                   │
│  └────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────┐              │
│  │ Generate deviceProofToken                   │              │
│  │ Sign with Device Private Key                │              │
│  └─────────────────────────────────────────────┘              │
│           │                                                     │
│           ▼                                                     │
│  POST /api/v1/auth-attempts/pending                          │
│  {                                                             │
│    enrollmentId,                                              │
│    enrollmentProofToken,                                      │
│    deviceProofToken,                                          │
│    deviceProofTokenSigned                                     │
│  }                                                             │
│           │                                                     │
│           ├─── No pending ──▶ "No Pending Requests"          │
│           │                                                     │
│           └─── Pending ────▶ Display Auth Request            │
│                              │                                 │
│                              ▼                                 │
│  ┌────────────────────────────────────────┐                   │
│  │ [Logo]                                 │                   │
│  │ Authentication Request                 │                   │
│  │                                        │                   │
│  │ From: ACME Corp                       │                   │
│  │ Request ID: 456                       │                   │
│  │                                        │                   │
│  │ Challenge Code: [______] (if needed)  │                   │
│  │                                        │                   │
│  │    [  Deny  ]    [ Authorize ]        │                   │
│  └────────────────────────────────────────┘                   │
│           │              │                                     │
│           │              └──────────────┐                     │
│           ▼                              ▼                     │
│     User Denies                    User Authorizes            │
│           │                              │                     │
│           └──────────┬──────────────────┘                     │
│                      ▼                                         │
│  ┌─────────────────────────────────────────────┐              │
│  │ Sign authAttemptProofToken                  │              │
│  │ with Device Private Key                     │              │
│  └─────────────────────────────────────────────┘              │
│           │                                                     │
│           ▼                                                     │
│  POST /api/v1/auth-attempts/respond                          │
│  {                                                             │
│    authAttemptId,                                             │
│    authAttemptAccepted: true/false,                           │
│    authAttemptProofTokenSignedByDevice,                       │
│    authAttemptChallengeResponse (if needed)                   │
│  }                                                             │
│           │                                                     │
│           ▼                                                     │
│  ┌────────────────────────────────────────┐                   │
│  │ ✓ Authentication Complete              │                   │
│  │   Result: APPROVED or DENIED           │                   │
│  └────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│      Ready for Next Request                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## State Machine

```
         ┌──────────┐
         │  START   │
         └────┬─────┘
              │
              ▼
     ┌─────────────────┐
     │  STEP 1:        │
     │  ENROLLMENT     │──── Input ID & Token
     │                 │
     │  Sub-states:    │
     │  • Unbound      │◀─── Bind Failed ──┐
     │  • Binding...   │                    │
     │  • Bound        │──── Bind Success ──┤
     │  • Verifying... │                    │
     │  • Verified     │◀─── Verify Failed ─┘
     └────┬────────────┘
          │ Enrollment Complete
          ▼
     ┌─────────────────┐
     │  STEP 2:        │
     │  READY          │
     │                 │
     │  • Show Summary │
     │  • Display Logo │
     │  • Ready to Auth│
     └────┬────────────┘
          │ User Proceeds
          ▼
     ┌─────────────────┐
     │  STEP 3:        │
     │  AUTH REQUESTS  │──── Check Pending
     │                 │
     │  Sub-states:    │
     │  • Idle         │◀─── No Pending ────┐
     │  • Checking...  │                    │
     │  • Pending Auth │──── Has Pending ───┤
     │  • Responding...│                    │
     │  • Complete     │◀─── Success/Error ─┘
     └────┬────────────┘
          │ Back to Summary
          │ or Check Again
          ▼
     [Loop to Step 2/3]
```

## Data Flow

```
┌───────────────┐         ┌───────────────┐         ┌───────────────┐
│   Mobile App  │         │   Auth API    │         │   Database    │
└───────┬───────┘         └───────┬───────┘         └───────┬───────┘
        │                         │                         │
        │ 1. Bind Request         │                         │
        ├────────────────────────▶│                         │
        │    enrollmentId         │                         │
        │    proofToken           │                         │
        │                         │ 2. Validate & Fetch     │
        │                         ├────────────────────────▶│
        │                         │                         │
        │                         │◀────────────────────────┤
        │                         │ 3. Integration Details  │
        │ 4. Bind Response        │                         │
        │◀────────────────────────┤                         │
        │    integrationInfo      │                         │
        │    new proofToken       │                         │
        │                         │                         │
        │ 5. Generate Keys        │                         │
        │    (RSA-2048)           │                         │
        │                         │                         │
        │ 6. Verify Request       │                         │
        ├────────────────────────▶│                         │
        │    devicePublicKey      │                         │
        │    signed proofToken    │                         │
        │    challengeResponse    │                         │
        │                         │ 7. Validate & Store     │
        │                         ├────────────────────────▶│
        │                         │                         │
        │                         │◀────────────────────────┤
        │                         │ 8. Success              │
        │ 9. Verify Response      │                         │
        │◀────────────────────────┤                         │
        │    active: true         │                         │
        │                         │                         │
        │ 10. Store Locally       │                         │
        │     (AsyncStorage)      │                         │
        │                         │                         │
        │ ... Time Passes ...     │                         │
        │                         │                         │
        │ 11. Pending Request     │                         │
        ├────────────────────────▶│                         │
        │    signed deviceProof   │                         │
        │                         │ 12. Check Pending       │
        │                         ├────────────────────────▶│
        │                         │                         │
        │                         │◀────────────────────────┤
        │                         │ 13. Auth Attempt        │
        │ 14. Pending Response    │                         │
        │◀────────────────────────┤                         │
        │    authAttemptId        │                         │
        │    authProofToken       │                         │
        │                         │                         │
        │ 15. Respond Request     │                         │
        ├────────────────────────▶│                         │
        │    accepted/denied      │                         │
        │    signed authProof     │                         │
        │    challengeResponse    │                         │
        │                         │ 16. Validate & Update   │
        │                         ├────────────────────────▶│
        │                         │                         │
        │                         │◀────────────────────────┤
        │                         │ 17. Success             │
        │ 18. Respond Result      │                         │
        │◀────────────────────────┤                         │
        │    result: APPROVED     │                         │
        │                         │                         │
```

## Cryptographic Operations

```
┌────────────────────────────────────────────────────────────┐
│                   ENROLLMENT CRYPTO FLOW                   │
└────────────────────────────────────────────────────────────┘

1. Device Key Generation:
   ┌─────────────────────────────────┐
   │  RSA KeyPairGenerator           │
   │  • Algorithm: RSA               │
   │  • Key Size: 2048 bits          │
   │  • Format: PKCS#8 (private)     │
   │            X.509 (public)       │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  Key Pair Generated             │
   │  • Private Key (kept on device) │
   │  • Public Key (sent to backend) │
   └─────────────────────────────────┘

2. Proof Token Signing:
   ┌─────────────────────────────────┐
   │  Input: enrollmentProofToken    │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  SHA256withRSA Signature        │
   │  • Hash: SHA-256                │
   │  • Sign: RSA private key        │
   │  • Encode: Base64               │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  Signed Token (Base64)          │
   └─────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                AUTHENTICATION CRYPTO FLOW                  │
└────────────────────────────────────────────────────────────┘

1. Device Proof Token Generation:
   ┌─────────────────────────────────┐
   │  SecureRandom                   │
   │  • 256 bits random              │
   │  • Timestamp                    │
   │  • 128 bits salt                │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  Base64 URL-Safe Encoding       │
   │  Format: random.timestamp.salt  │
   └─────────────────────────────────┘

2. Device Proof Signing:
   ┌─────────────────────────────────┐
   │  Input: deviceProofToken        │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  SHA256withRSA Signature        │
   │  • Device Private Key           │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  Signed Device Proof            │
   │  (proves device identity)       │
   └─────────────────────────────────┘

3. Auth Attempt Proof Signing:
   ┌─────────────────────────────────┐
   │  Input: authAttemptProofToken   │
   │  (received from backend)        │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  SHA256withRSA Signature        │
   │  • Device Private Key           │
   └─────────────────────────────────┘
              │
              ▼
   ┌─────────────────────────────────┐
   │  Signed Auth Proof              │
   │  (proves device received token) │
   └─────────────────────────────────┘
```

## Storage Schema

```
AsyncStorage Key: @ezkey_enrollment_<enrollmentId>

Value (JSON):
{
  "enrollmentId": 123,
  "enrollmentName": "John's iPhone",
  "enrollmentProofToken": "abc123...",
  "integrationName": "ACME Corp",
  "integrationDescription": "Secure access to ACME systems",
  "integrationLogo": "https://acme.com/logo.png",
  "integrationPublicKey": "MIIBIjAN...",
  "devicePublicKey": "MIIBIjAN...",
  "devicePrivateKey": "MIIEvgIB...",  // ⚠️ Sensitive
  "verified": true,
  "createdAt": "2025-10-12T10:30:00.000Z"
}
```

## Error Handling

```
┌─────────────────────────────────────────────────────────────┐
│                     ERROR FLOW                              │
└─────────────────────────────────────────────────────────────┘

API Call
   │
   ├─── Network Error ──▶ Alert: "Cannot connect to backend"
   │                       • Check URL configuration
   │                       • Verify backend is running
   │                       • Check network connectivity
   │
   ├─── 400 Bad Request ─▶ Alert: Error message from API
   │                       • Invalid request parameters
   │                       • Validation failed
   │
   ├─── 401 Unauthorized ▶ Alert: "Invalid credentials"
   │                       • Wrong proof token
   │                       • Invalid signature
   │
   ├─── 404 Not Found ───▶ Alert: "Resource not found"
   │                       • Invalid enrollment ID
   │                       • Auth attempt not found
   │
   ├─── 500 Server Error ▶ Alert: "Server error"
   │                       • Backend exception
   │                       • Database error
   │
   └─── Success (200) ───▶ Continue to next step
```

## UI Components

```
App Component
├── SafeAreaView (root container)
│   └── ScrollView
│       ├── Header
│       │   ├── Title: "Ezkey Mobile V2"
│       │   └── Subtitle: "Step X of 3"
│       │
│       └── Step Container (conditional rendering)
│           │
│           ├── Step 1: Enrollment
│           │   ├── Input: Enrollment ID
│           │   ├── Input: Proof Token
│           │   ├── Button: Bind Device
│           │   ├── Info Box (after bind)
│           │   │   ├── Logo Image
│           │   │   ├── Integration Name
│           │   │   ├── Description
│           │   │   └── Enrollment Name
│           │   ├── Input: Challenge Code
│           │   └── Button: Verify Enrollment
│           │
│           ├── Step 2: Summary
│           │   ├── Summary Box
│           │   │   ├── Logo Image
│           │   │   └── Status Items (✓)
│           │   └── Button: Check for Auth Requests
│           │
│           └── Step 3: Auth Requests
│               ├── Button: Check Pending Requests
│               ├── Auth Box (if pending)
│               │   ├── Logo Image
│               │   ├── Request Details
│               │   ├── Input: Challenge (if needed)
│               │   └── Button Row
│               │       ├── Button: Deny
│               │       └── Button: Authorize
│               └── Button: Back to Summary
```

This visual guide provides a comprehensive overview of how the application flows from start to finish, including all state transitions, data flows, cryptographic operations, and error handling.
