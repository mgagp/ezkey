# Ezkey Mobile Demo - Product Requirements Document

## Overview

**Application Name:** Ezkey Demo  
**Purpose:** Demonstration application for Ezkey enrollment and authentication workflows  
**Target:** Demo/Testing environment, not production  
**Platform:** Flutter/Dart cross-platform  

## Core Concept

A **single-screen Flutter application** that demonstrates the fundamental Ezkey workflows:
1. **Enrollment** (BIND → Generate Keys → VERIFY)
2. **Authentication** (PENDING → Approve/Deny → RESPOND)

## Technical Requirements

### Architecture
- **Single Screen Application** - No navigation, no tabs, no menus
- **Dark Theme** with accent color
- **No Icons** - Text-based interface only
- **Minimal Dependencies** - Core Flutter + HTTP client only

### API Integration
- **OpenAPI Client Generation** - Similar to `ezkey-demo-device`
- **REST API Consumption** - Direct calls to Ezkey APIs
- **No Mock Data** - Real API interactions only

## User Interface

### Layout Structure
```
┌─────────────────────────────────────┐
│           EZKEY DEMO                │
├─────────────────────────────────────┤
│                                     │
│  [Enrollment URL Input Field]       │
│  [Start Enrollment Button]          │
│                                     │
│  [Enrollment Status Display]        │
│                                     │
│  [Check Pending Auth Button]        │
│                                     │
│  [Auth Status Display]              │
│                                     │
│  [Approve/Deny Buttons] (if needed) │
│  [Challenge Input] (if needed)      │
│  [Respond Button] (if needed)       │
│                                     │
└─────────────────────────────────────┘
```

### Visual Design
- **Theme:** Dark mode with accent color
- **Typography:** Simple, readable fonts
- **Spacing:** Generous padding and margins
- **Colors:** Dark background, light text, accent color for highlights
- **No Images/Icons:** Pure text interface

## Workflow Implementation

### 1. Enrollment Workflow

#### Step 1: URL Input
- User enters enrollment URL in text field
- URL format: `https://api.ezkey.org/enrollments/bind/{enrollmentId}`
- Validation: Basic URL format check

#### Step 2: BIND API Call
- **Endpoint:** `GET /api/v1/enrollments/bind/{id}`
- **Purpose:** Retrieve enrollment information
- **Response:** Integration details, enrollment code, public keys

#### Step 3: Device Key Generation
- Generate RSA key pair (2048-bit)
- Store private key securely (in-memory for demo)
- Prepare enrollment confirmation data

#### Step 4: VERIFY API Call
- **Endpoint:** `POST /api/v1/enrollments/confirm`
- **Purpose:** Complete enrollment with device keys
- **Request:** Enrollment ID, challenge response, device public key
- **Response:** Enrollment confirmation status

#### Step 5: Status Display
- Show enrollment result (Success/Failed)
- Display integration name and details
- Clear indication of enrollment state

### 2. Authentication Workflow

#### Step 1: Check Pending Auth
- **Button:** "Check for Pending Authentication"
- **API Call:** `GET /api/v1/authattempts/pending/{enrollmentId}`
- **Purpose:** Check if there are pending auth attempts

#### Step 2: Auth Attempt Display
- **If No Pending:** Display "No pending authentication requests"
- **If Pending:** Show auth attempt details and action buttons

#### Step 3: User Decision
- **Buttons:** "Approve" / "Deny"
- **Challenge:** If required, show challenge input field
- **Validation:** Ensure challenge is provided if required

#### Step 4: RESPOND API Call
- **Endpoint:** `POST /api/v1/authattempts/respond/{id}`
- **Purpose:** Send user decision to authentication system
- **Request:** Auth attempt ID, decision, challenge response (if needed)
- **Response:** Authentication result

#### Step 5: Status Display
- Show authentication result (Approved/Denied/Failed)
- Clear indication of authentication state

## API Specifications

### Required Endpoints

#### Enrollment APIs
```yaml
GET /api/v1/enrollments/bind/{id}
POST /api/v1/enrollments/confirm
```

#### Authentication APIs
```yaml
GET /api/v1/authattempts/pending/{enrollmentId}
POST /api/v1/authattempts/respond/{id}
```

### OpenAPI Generation
- Generate client code from OpenAPI specification
- Similar approach to `ezkey-demo-device`
- Type-safe API calls with generated models

## Technical Implementation

### Dependencies (Minimal)
```yaml
dependencies:
  flutter: sdk
  http: ^1.2.1
  crypto: ^3.0.3
  pointycastle: ^3.7.3
```

### Project Structure
```
ezkey_mobile_demo/
├── lib/
│   ├── main.dart
│   ├── models/
│   │   ├── enrollment.dart
│   │   └── auth_attempt.dart
│   ├── services/
│   │   ├── api_client.dart
│   │   └── crypto_service.dart
│   ├── widgets/
│   │   └── demo_screen.dart
│   └── utils/
│       └── key_generator.dart
├── pubspec.yaml
└── PRD_MOBILE_DEMO.md
```

### Key Components

#### 1. Demo Screen Widget
- Single screen with all UI elements
- State management for enrollment and auth flows
- Error handling and status display

#### 2. API Client
- Generated from OpenAPI specification
- HTTP client for API calls
- Error handling and response parsing

#### 3. Crypto Service
- RSA key pair generation
- Cryptographic signing
- Secure key storage (in-memory for demo)

#### 4. Models
- Enrollment data model
- Auth attempt data model
- API request/response models

## User Experience Flow

### Enrollment Process
1. User enters enrollment URL
2. Clicks "Start Enrollment"
3. App shows "Processing..." status
4. App displays enrollment result
5. User sees integration details

### Authentication Process
1. User clicks "Check for Pending Auth"
2. App shows pending status or "No pending requests"
3. If pending, user sees auth details and action buttons
4. User selects Approve/Deny and enters challenge if needed
5. App shows authentication result

## Error Handling

### Network Errors
- Display clear error messages
- Retry functionality for failed API calls
- Timeout handling

### Validation Errors
- URL format validation
- Required field validation
- Challenge input validation

### API Errors
- Parse and display API error messages
- Handle different HTTP status codes
- Graceful degradation

## Success Criteria

### Functional Requirements
- ✅ Complete enrollment workflow (BIND → Generate Keys → VERIFY)
- ✅ Complete authentication workflow (PENDING → Approve/Deny → RESPOND)
- ✅ Real API integration (no mocks)
- ✅ Single screen interface
- ✅ Dark theme with accent color

### Technical Requirements
- ✅ OpenAPI client generation
- ✅ RSA key pair generation
- ✅ Cryptographic signing
- ✅ Error handling
- ✅ Status display

### User Experience
- ✅ Simple, intuitive interface
- ✅ Clear status feedback
- ✅ Minimal interaction required
- ✅ Fast response times

## Development Phases

### Phase 1: Foundation (1-2 days)
- Create Flutter project structure
- Set up dark theme
- Implement basic UI layout
- Add HTTP client dependency

### Phase 2: API Integration (2-3 days)
- Generate OpenAPI client
- Implement API service layer
- Add error handling
- Test API connectivity

### Phase 3: Enrollment Workflow (2-3 days)
- Implement RSA key generation
- Add enrollment API calls
- Implement BIND → VERIFY flow
- Add status display

### Phase 4: Authentication Workflow (2-3 days)
- Implement PENDING API call
- Add Approve/Deny functionality
- Implement RESPOND API call
- Add challenge handling

### Phase 5: Polish (1 day)
- Error handling improvements
- UI refinements
- Testing and bug fixes
- Documentation

## Constraints and Limitations

### Demo-Only Features
- In-memory key storage (not persistent)
- No biometric authentication
- No push notifications
- No offline functionality
- No user preferences

### Technical Limitations
- Single enrollment at a time
- No enrollment management
- No history or logging
- No configuration options

## Conclusion

This minimal Flutter demo application will provide a clear, simple demonstration of Ezkey's core enrollment and authentication workflows. The single-screen design with dark theme and accent color will create a focused, professional demo experience that highlights the fundamental capabilities of the Ezkey platform.

The application serves as a proof-of-concept for API integration and workflow demonstration, suitable for developer demos and testing scenarios.
