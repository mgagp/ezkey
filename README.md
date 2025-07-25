# Ezkey - Open Source MFA/Passkey Alternative

## Overview

Ezkey is a pragmatic, open-source, and developer-friendly alternative to complex passkey implementations. It provides a simple and secure MFA solution that can be easily integrated into any application through a modern multi-module architecture.

## Features

- **Multi-Module Architecture**: Separated APIs for different use cases
- **Admin API**: Complete management interface for integrations and enrollments
- **Authentication API**: Mobile-focused API for device authentication
- **Mobile Application**: Cross-platform Flutter app for end users
- **Secure**: Cryptographic key-based authentication with signature validation
- **Open Source**: MIT licensed with comprehensive documentation
- **Developer-Friendly**: REST APIs, OpenAPI documentation, and extensive examples
- **Cross-Platform**: Works with any technology stack

## Architecture

Ezkey is built with a modern multi-module architecture:

```
ezkey/
├── ezkey-core/              # Shared core module (entities, services, migrations)
├── ezkey-admin-api/         # Administration API (port 9080)
├── ezkey-auth-api/          # Authentication API (port 8080) 
├── ezkey_mobile/            # Flutter mobile application
├── ezkey-demo-app-acme/     # Demo integration application
├── ezkey-demo-device/       # Demo device simulation
└── ezkey-docs/              # Project documentation
```

### Core Components

#### 🏗️ **ezkey-core**
Central module containing:
- **JPA Entities**: Integration, Enrollment, AuthAttempt
- **Business Services**: Authentication logic, signature validation
- **Database Migrations**: Flyway-based schema management
- **Shared DTOs and Mappers**: Cross-module data structures

#### 🔧 **ezkey-admin-api** (Port 9080)
Administration interface for:
- **Integration Management**: Create, update, delete integrations
- **Enrollment Administration**: Manage device enrollments
- **Auth Attempt Creation**: Initialize authentication requests
- **System Monitoring**: View statistics and system health

#### 🔐 **ezkey-auth-api** (Port 8080)
Mobile-focused authentication API for:
- **Device Enrollment**: Bind devices to user accounts
- **Enrollment Verification**: Complete enrollment process
- **Authentication Flow**: Handle pending auth attempts and responses
- **Mobile Integration**: Optimized for mobile app consumption

#### 📱 **ezkey_mobile**
Cross-platform Flutter application featuring:
- **QR Code Scanning**: Easy enrollment via QR codes
- **Push Notifications**: Real-time authentication requests
- **Secure Storage**: Encrypted key management
- **Biometric Support**: Local device authentication
- **Deep Links**: SMS and URL-based enrollment

## Quick Start

### Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **PostgreSQL** (or H2 for development)
- **Flutter SDK 3.8.1+** (for mobile development)

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
flutter pub get
flutter run
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

### Auth API Endpoints (`localhost:8080`)

#### Enrollments (Mobile)
- `GET /api/v1/enrollments/bind/{id}` - Bind device to enrollment
- `POST /api/v1/enrollments/verify` - Verify and complete enrollment

#### Auth Attempts (Mobile)
- `POST /api/v1/auth-attempts/pending/{enrollmentId}` - Get pending authentication
- `POST /api/v1/auth-attempts/respond` - Respond to auth attempt

## Development

### Technology Stack

- **Backend**: Java 21, Spring Boot 3.5.3, Spring Data JPA
- **Database**: PostgreSQL with Flyway migrations
- **Mapping**: MapStruct for DTO conversions
- **Documentation**: SpringDoc OpenAPI
- **Testing**: JUnit 5, Spring Boot Test
- **Mobile**: Flutter 3.8.1+, Provider state management
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

#### Flutter Structure
```
lib/
├── models/                  # Data models
├── services/                # API services
├── providers/               # State management
├── screens/                 # UI screens
├── widgets/                 # Reusable widgets
└── utils/                   # Utilities
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
flutter pub get

# Run application
flutter run

# Run tests
flutter test

# Build release
flutter build apk
flutter build ios
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
ezkey.simulation.mode=false  # Production setting
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
docs(mobile): update Flutter setup instructions
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
- [x] Flutter mobile application
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
