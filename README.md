# Ezkey - Open Source MFA/Passkey Alternative

<img src="logo.svg" alt="Ezkey Logo" width="200">

## Overview

Ezkey is a pragmatic, open-source alternative to complex passkey implementations. It provides a simple and secure MFA solution that can be easily integrated into any application through a modern multi-module architecture.

### Why Ezkey?

```mermaid
graph LR
    subgraph "Traditional MFA"
        A[Complex Setup]
        B[Vendor Lock-in]
        C[High Costs]
        D[Limited Control]
    end
    
    subgraph "Passkeys"
        E[Browser Dependencies]
        F[Complex Integration]
        G[Limited Support]
        H[Steep Learning Curve]
    end
    
    subgraph "Ezkey Approach"
        I[Simple REST APIs]
        J[Open Source]
        K[Self-Hosted]
        L[Developer-Friendly]
        M[Synchronous Wait API]
    end
    
    style I fill:#e8f5e8
    style J fill:#e8f5e8
    style K fill:#e8f5e8
    style L fill:#e8f5e8
    style M fill:#e8f5e8
```

### Comparison Matrix

| Feature | Traditional MFA | Passkeys | Ezkey |
|---------|----------------|----------|-------|
| **Setup Complexity** | High | High | Low |
| **Integration Effort** | Medium | High | Low |
| **Cost** | High | Free (but complex) | Free + Self-hosted |
| **Control** | Limited | Limited | Full |
| **Mobile Support** | Good | Excellent | Excellent |
| **Developer Experience** | Varies | Complex | Simple |
| **Open Source** | Rarely | Partially | Yes |
| **Self-Hosting** | Rarely | No | Yes |
| **Synchronous Integration** | Limited | No | Yes (Wait API) |

## Features

- **Multi-Module Architecture**: Separated APIs for different use cases
- **Admin API**: Complete management interface for integrations and enrollments
- **Authentication API**: Mobile-focused API for device authentication
- **Wait API**: Synchronous polling for authentication completion
- **Mobile Application**: Cross-platform React Native app for end users
- **Secure**: Cryptographic key-based authentication with signature validation
- **Open Source**: MIT licensed with comprehensive documentation
- **Developer-Friendly**: REST APIs, OpenAPI documentation, and extensive examples
- **Cross-Platform**: Works with any technology stack

## 🔐 Security Design

### **One-Time Proof Token System**

Ezkey implements a **one-time proof token** security model that prevents replay attacks and ensures authentication integrity.

#### **Key Security Principles**

1. **Unique Tokens**: Each authentication attempt gets a unique `authAttemptProofToken`
2. **One-Time Use**: Tokens can only be read once (during PENDING request)
3. **Cryptographic Proof**: Device must prove it received the original token
4. **Anti-Replay**: Prevents replay of authentication attempts

#### **Security Flow**

```mermaid
sequenceDiagram
    participant App as Protected App
    participant Admin as Admin API
    participant Auth as Auth API
    participant Device as Mobile Device
    
    App->>Admin: Create auth attempt
    Admin->>Auth: Generate unique authAttemptProofToken
    Device->>Auth: PENDING request (decrypts token)
    Device->>Auth: RESPOND request (signs token)
    Auth->>Admin: Validate signature
    App->>Admin: Wait for completion (polling)
    Admin->>App: Authentication result
```

#### **Developer Security Checklist**

- ✅ Always use `authAttemptProofToken` for RESPOND (never `enrollmentProofToken`)
- ✅ Implement proper signature validation
- ✅ Store tokens securely on device
- ✅ Never reuse tokens across attempts
- ✅ Validate all cryptographic signatures

#### **Why This Matters**

This design ensures that:
- **Only legitimate devices** can respond to authentication requests
- **Each attempt is unique** and cannot be replayed
- **Cryptographic proof** prevents man-in-the-middle attacks
- **Zero-trust architecture** with end-to-end verification

## Architecture

Ezkey is built with a modern multi-module architecture:

```
ezkey/
├── ezkey-core/              # Shared core module (entities, services, migrations)
├── ezkey-admin-api/         # Administration API (port 9080)
├── ezkey-auth-api/          # Authentication API (port 8080) 
├── ezkey_mobile/            # React Native mobile application
├── ezkey-demo-app-acme/     # Demo integration application
├── ezkey-demo-device/       # Demo device application
└── ezkey-docs/              # Project documentation
```

### System Overview

```mermaid
graph TB
    subgraph "Protected Applications"
        A[ACME Admin Portal]
        B[E-commerce Site]
        C[Banking App]
    end
    
    subgraph "Ezkey System"
        D[Admin API<br/>Port 9080]
        E[Auth API<br/>Port 8080]
        F[Core Module]
        G[(Database)]
    end
    
    subgraph "User Devices"
        H[Mobile App]
        I[Demo Device]
    end
    
    A --> D
    B --> D
    C --> D
    D --> F
    E --> F
    F --> G
    H --> E
    I --> E
    
    D -.->|Wait API| A
    D -.->|Wait API| B
    D -.->|Wait API| C
```

### Core Entities and Relationships

```mermaid
erDiagram
    Integration ||--o{ Enrollment : "has"
    Enrollment ||--o{ AuthAttempt : "generates"
    Integration {
        int id PK
        string name
        string description
        string logo
        boolean active
        timestamp created_at
    }
    Enrollment {
        int id PK
        int integration_id FK
        string device_public_key
        string enrollment_proof_token
        boolean active
        boolean challenge_required
        timestamp created_at
    }
    AuthAttempt {
        int id PK
        int enrollment_id FK
        string auth_proof_token
        boolean accepted
        boolean responded
        boolean read
        boolean valid
        timestamp created_at
    }
```

### Authentication Flow Overview

```mermaid
sequenceDiagram
    participant App as Protected App
    participant Admin as Admin API
    participant Auth as Auth API
    participant Mobile as Mobile Device
    participant DB as Database
    
    Note over App,Mobile: 1. Enrollment Process
    App->>Admin: Create Integration
    App->>Admin: Create Enrollment
    Mobile->>Auth: Bind Device (GET /bind/{id})
    Auth->>Mobile: Return Integration Info + Proof Token
    Mobile->>Auth: Verify Enrollment (POST /verify)
    Auth->>DB: Store Enrollment
    Auth->>Mobile: Enrollment Complete
    
    Note over App,Mobile: 2. Authentication Process
    App->>Admin: Create Auth Attempt
    Mobile->>Auth: Check Pending (POST /pending)
    Auth->>Mobile: Return Auth Attempt Details
    Mobile->>Auth: Respond (POST /respond)
    Auth->>DB: Update Auth Attempt
    Auth->>Mobile: Authentication Result
    
    Note over App,Mobile: 3. Wait for Completion (Optional)
    App->>Admin: Wait for Response (GET /wait)
    Admin->>DB: Poll for completion
    Admin->>App: Return final status
```

### User Journey: Application Owner

```mermaid
flowchart TD
    A[Application Owner] --> B[Create Integration]
    B --> C[Configure MFA Settings]
    C --> D[Generate Enrollment QR/Link]
    D --> E[Share with Users]
    E --> F[Monitor Enrollments]
    F --> G[Create Auth Attempts]
    G --> H[Wait for User Response]
    H --> I[View Authentication Results]
    
    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style G fill:#fff3e0
    style H fill:#fff3e0
    style I fill:#e8f5e8
```

### User Journey: End User

```mermaid
flowchart TD
    A[End User] --> B[Receive Enrollment Link/QR]
    B --> C[Open Mobile App]
    C --> D[Scan QR or Enter Code]
    D --> E[Generate Device Keys]
    E --> F[Complete Enrollment]
    F --> G[Receive Auth Notifications]
    G --> H[Approve/Deny Access]
    H --> I[Enter Challenge if Required]
    I --> J[Authentication Complete]
    
    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style G fill:#fff3e0
    style J fill:#e8f5e8
```

### Technical Integration Flow

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Admin as Admin API
    participant Auth as Auth API
    participant Mobile as Mobile Device
    
    Note over Client,Mobile: Integration Setup
    Client->>Admin: POST /integrations
    Admin-->>Client: Integration ID
    
    Note over Client,Mobile: User Enrollment
    Client->>Admin: POST /enrollments
    Admin-->>Client: Enrollment ID + Challenge
    Client->>Mobile: Share Enrollment Link/QR
    Mobile->>Auth: GET /enrollments/bind/{id}
    Auth-->>Mobile: Integration Info + Proof Token
    Mobile->>Auth: POST /enrollments/verify
    Auth-->>Mobile: Enrollment Complete
    
    Note over Client,Mobile: Authentication Request
    Client->>Admin: POST /auth-attempts
    Admin-->>Client: Auth Attempt ID
    
    Note over Client,Mobile: Wait for Completion (Synchronous)
    Client->>Admin: GET /auth-attempts/{id}/wait
    Admin->>Admin: Poll for completion
    Admin-->>Client: Final authentication status
    
    Note over Client,Mobile: Alternative: Check Status (Asynchronous)
    Client->>Admin: GET /auth-attempts/{id}
    Admin-->>Client: Current authentication status
```

### Deployment Architecture

```mermaid
graph TB
    subgraph "Development Environment"
        A[Developer Machine]
        B[Local Database]
        C[Demo Apps]
    end
    
    subgraph "Production Environment"
        D[Load Balancer]
        E[Admin API Cluster]
        F[Auth API Cluster]
        G[Database Cluster]
        H[Mobile App Store]
    end
    
    subgraph "User Devices"
        I[User Mobile Devices]
        J[Protected Applications]
    end
    
    A --> B
    A --> C
    D --> E
    D --> F
    E --> G
    F --> G
    I --> F
    J --> E
    H --> I
```

### Core Components

#### 🏗️ **ezkey-core**
Central module containing:
- **JPA Entities**: Integration, Enrollment, AuthAttempt
- **Business Services**: Authentication logic, signature validation, polling
- **Database Migrations**: Flyway-based schema management
- **Shared DTOs and Mappers**: Cross-module data structures

#### 🔧 **ezkey-admin-api** (Port 9080)
Administration interface for:
- **Integration Management**: Create, update, delete integrations
- **Enrollment Administration**: Manage device enrollments
- **Auth Attempt Creation**: Initialize authentication requests
- **Wait API**: Synchronous polling for authentication completion
- **System Monitoring**: View statistics and system health

#### 🔐 **ezkey-auth-api** (Port 8080)
Mobile-focused authentication API for:
- **Device Enrollment**: Bind devices to user accounts
- **Enrollment Verification**: Complete enrollment process
- **Authentication Flow**: Handle pending auth attempts and responses
- **Mobile Integration**: Optimized for mobile app consumption

#### 📱 **ezkey_mobile**
Cross-platform React Native application featuring:
- **QR Code Scanning**: Easy enrollment via QR codes
- **Push Notifications**: Real-time authentication requests
- **Secure Storage**: Encrypted key management
- **Biometric Support**: Local device authentication
- **Deep Links**: SMS and URL-based enrollment

### Security and Cryptography Flow

```mermaid
sequenceDiagram
    participant Device as Mobile Device
    participant Auth as Auth API
    participant Core as Core Service
    participant DB as Database
    
    Note over Device,DB: Key Generation & Enrollment
    Device->>Device: Generate RSA-2048 Key Pair
    Device->>Auth: Send Public Key + Enrollment Request
    Auth->>Core: Validate Request
    Core->>Core: Generate Proof Token
    Core->>DB: Store Enrollment
    Auth->>Device: Return Proof Token + Integration Info
    
    Note over Device,DB: Authentication Flow
    Device->>Device: Generate Device Proof Token
    Device->>Auth: Send Signed Proof Token
    Auth->>Core: Validate Signature
    Core->>Core: Verify Integration Signature
    Core->>DB: Update Auth Attempt
    Auth->>Device: Authentication Result
```

### Data Flow and Security

```mermaid
flowchart TD
    A[User Action] --> B{Action Type?}
    B -->|Enrollment| C[Generate Device Keys]
    B -->|Authentication| D[Generate Proof Token]
    B -->|Wait for Completion| E[Poll Authentication Status]
    
    C --> F[Sign Enrollment Data]
    D --> G[Sign Auth Response]
    E --> H[Check Database State]
    
    F --> I[Send to Auth API]
    G --> I
    H --> J[Return Status]
    
    I --> K[Validate Signature]
    K --> L[Process Request]
    L --> M[Store in Database]
    
    style A fill:#e1f5fe
    style C fill:#fff3e0
    style D fill:#fff3e0
    style E fill:#fff3e0
    style K fill:#f3e5f5
    style M fill:#e8f5e8
```

## Quick Start

### Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **PostgreSQL** (or H2 for development)
- **React Native CLI** (for mobile development)

### Database Setup

1. **Start PostgreSQL**:
```bash
docker run --name ezkey-postgres \
  -e POSTGRES_PASSWORD=ezkey \
  -e POSTGRES_DB=ezkey_db \
  -p 5432:5432 -d postgres:17
```

2. **Run Database Migrations**:
```bash
# Using the Flyway standalone tool
./scripts/ezkey-flyway.sh

# Or build and run with Maven
cd ezkey-core
mvn clean compile exec:java
```

### Running the APIs

1. **Clone and Build**:
```bash
git clone https://github.com/your-org/ezkey.git
cd ezkey
mvn clean install
```

2. **Start Admin API** (Port 9080):
```bash
cd ezkey-admin-api
mvn spring-boot:run
```

3. **Start Auth API** (Port 8080):
```bash
cd ezkey-auth-api
mvn spring-boot:run
```

4. **Launch Mobile App**:
```bash
cd ezkey_mobile
npm install
npx react-native run-android  # or run-ios
```

### Access Points

- **Admin API**: http://localhost:9080/api/v1/
- **Auth API**: http://localhost:8080/api/v1/
- **API Documentation**: 
  - Admin: http://localhost:9080/swagger-ui.html
  - Auth: http://localhost:8080/swagger-ui.html

## API Overview

### Admin API Endpoints (`localhost:9080`)

#### Integrations
- `GET /api/v1/integrations` - List all integrations
- `POST /api/v1/integrations` - Create new integration
- `GET /api/v1/integrations/{id}` - Get integration details
- `DELETE /api/v1/integrations/{id}` - Delete integration

#### Enrollments (Admin)
- `GET /api/v1/enrollments` - List all enrollments
- `POST /api/v1/enrollments` - Create enrollment
- `DELETE /api/v1/enrollments/{id}` - Delete enrollment

#### Auth Attempts (Admin)
- `GET /api/v1/auth-attempts` - List auth attempts
- `POST /api/v1/auth-attempts` - Create auth attempt
- `GET /api/v1/auth-attempts/{id}` - Get auth attempt status
- `GET /api/v1/auth-attempts/{id}/wait` - **Wait for authentication completion**

### Auth API Endpoints (`localhost:8080`)

#### Enrollments (Mobile)
- `GET /api/v1/enrollments/bind/{id}` - Bind device to enrollment
- `POST /api/v1/enrollments/verify` - Verify and complete enrollment

#### Auth Attempts (Mobile)
- `POST /api/v1/auth-attempts/pending/{enrollmentId}` - Get pending authentication
- `POST /api/v1/auth-attempts/respond` - Respond to auth attempt

### Wait API Usage

The Wait API provides synchronous behavior for authentication requests:

```bash
# Wait for authentication completion (default: 30s timeout, 2s polling)
GET /api/v1/auth-attempts/123/wait

# Custom timeout and polling interval
GET /api/v1/auth-attempts/123/wait?timeout=60&polling=5
```

**Response includes:**
- Authentication status (PENDING, READ, INVALID, REJECTED, ACCEPTED)
- Completion status and metadata
- Wait duration and timeout information

## Development

### Technology Stack

- **Backend**: Java 21, Spring Boot 3.5.3, Spring Data JPA
- **Database**: PostgreSQL with Flyway migrations
- **Mapping**: MapStruct for DTO conversions
- **Documentation**: SpringDoc OpenAPI
- **Testing**: JUnit 5, Spring Boot Test
- **Mobile**: React Native, Native Modules for cryptography
- **Build**: Maven multi-module setup

### Code Standards

This project follows strict formatting and quality standards:

#### Formatting Rules
- **Indentation**: 4 spaces (no tabs)
- **Line Length**: Maximum 120 characters
- **Encoding**: UTF-8 without BOM
- **Line Endings**: LF (Unix style)

#### Development Tools
- **Checkstyle**: Google Java Style Guide (`google_checks.xml`)
- **EditorConfig**: Consistent formatting (`.editorconfig`)
- **MapStruct**: Object mapping between DTOs and entities
- **JaCoCo**: Code coverage reporting

### Project Structure

#### Java Modules
```
src/main/java/org/ezkey/
├── {domain}/
│   ├── domain/
│   │   ├── entity/          # JPA entities
│   │   └── repository/      # Spring Data repositories
│   ├── service/             # Business logic
│   ├── controller/          # REST endpoints (API modules)
│   ├── dto/
│   │   ├── request/         # Request DTOs  
│   │   ├── response/        # Response DTOs
│   │   └── common/          # Shared DTOs
│   ├── mapper/              # MapStruct mappers
│   └── exception/           # Custom exceptions
```

#### React Native Structure
```
src/
├── components/              # React components
├── services/                # API services
├── hooks/                   # Custom hooks
├── screens/                 # UI screens
├── utils/                   # Utilities
└── native/                  # Native modules
    ├── android/             # Android native code
    └── ios/                 # iOS native code
```

### Development Commands

#### Backend
```bash
# Build all modules
mvn clean install

# Run quality checks
mvn checkstyle:check
mvn clean verify

# Run specific module
cd ezkey-admin-api
mvn spring-boot:run

# Database migrations
./scripts/ezkey-flyway.sh --info
./scripts/ezkey-flyway.sh --migrate
```

#### Mobile
```bash
# Install dependencies
npm install

# Run application
npx react-native run-android  # or run-ios

# Run tests
npm test

# Build release
cd android && ./gradlew assembleRelease
cd ios && xcodebuild -workspace EzkeyMobile.xcworkspace -scheme EzkeyMobile -configuration Release
```

### Testing Strategy

- **Unit Tests**: Business logic and service layers
- **Integration Tests**: API endpoints and database operations
- **Mobile Tests**: Widget and integration testing
- **API Testing**: Comprehensive endpoint validation

## Deployment

### Docker Support

Each module can be containerized:

```bash
# Build API images
docker build -t ezkey-admin-api ./ezkey-admin-api
docker build -t ezkey-auth-api ./ezkey-auth-api

# Run with docker-compose
docker-compose up -d
```

### Environment Configuration

Configure each module with environment-specific properties:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/ezkey_db
spring.datasource.username=postgres
spring.datasource.password=ezkey

# API Ports
server.port=9080  # Admin API
server.port=8080  # Auth API

# Security
# All security-related configurations handled in API-specific property files
```

## Contributing

1. **Fork the Repository**
2. **Create Feature Branch**: `git checkout -b feature/amazing-feature`
3. **Follow Code Standards**: Use provided checkstyle and editorconfig
4. **Add Tests**: Ensure comprehensive test coverage
5. **Update Documentation**: Include API and code documentation
6. **Submit Pull Request**: Use conventional commit messages

### Commit Message Format

Use conventional commit messages:
```
feat(admin-api): add integration export endpoint
fix(auth-api): resolve enrollment binding issue
docs(mobile): update React Native setup instructions
style(core): format according to Google style guide
test(admin-api): add integration controller tests
```

## Monitoring and Operations

### Database Migrations

Use the dedicated Flyway tool for production deployments:

```bash
# Windows
scripts\ezkey-flyway.bat --info

# Linux/Mac  
./scripts/ezkey-flyway.sh --migrate
```

### Health Checks

- **Admin API**: `GET /actuator/health`
- **Auth API**: `GET /actuator/health`

### Logging

Structured logging with configurable levels:
```properties
logging.level.org.ezkey=INFO
logging.level.org.springframework.web=WARN
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support and Community

- **Issues**: [GitHub Issues](https://github.com/your-org/ezkey/issues)
- **Documentation**: [Project Wiki](https://github.com/your-org/ezkey/wiki)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/ezkey/discussions)
- **Security**: Report security issues privately to security@ezkey.org

## Roadmap

### Phase 1 ✅ (Completed)
- [x] Multi-module architecture
- [x] Core entities and services
- [x] Admin and Auth APIs
- [x] **Wait API for synchronous authentication**
- [x] React Native mobile application
- [x] Database migrations
- [x] Development tooling

### Phase 2 🔄 (In Progress)
- [ ] Docker containerization
- [ ] Kubernetes deployment manifests
- [ ] API performance optimization
- [ ] Enhanced mobile features
- [ ] Comprehensive monitoring

### Phase 3 📋 (Planned)
- [ ] Microservices deployment
- [ ] Advanced security features
- [ ] Multi-tenant support
- [ ] Performance benchmarking
- [ ] Third-party integrations
