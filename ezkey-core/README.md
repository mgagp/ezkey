# Ezkey Core

The core module of Ezkey is a pure library containing shared business logic, entities, services, and repositories.

## Overview

This module provides:
- **JPA Entities** : Data models for authentication and integrations
- **Business Services** : Logic for managing authentication attempts and enrollments
- **Repositories** : Spring Data JPA repositories for data access
- **Mappers** : Conversion between DTOs and entities
- **Exception Classes** : Common exception definitions
- **DTOs** : Shared data transfer objects

## Architecture

The `ezkey-core` module is designed as a pure library:

- **Purpose**: Provides shared business logic and data models
- **Design**: Standard JAR library without Spring Boot application
- **Integration**: Used as a dependency by other modules
- **Separation**: Database migrations are handled by the dedicated `ezkey-migration` module

## Usage as Library

### Dependencies

This module can be used as a dependency in other projects:

```xml
<dependency>
    <groupId>org.ezkey</groupId>
    <artifactId>ezkey-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Key Components

#### Entities
- `Integration` : Represents system integrations
- `Enrollment` : Device enrollment information
- `AuthAttempt` : Authentication attempt records

#### Services
- `EnrollmentService` : Manages device enrollments
- `AuthAttemptService` : Handles authentication attempts
- `SignatureService` : Cryptographic signature operations

#### Repositories
- `IntegrationRepository` : Data access for integrations
- `EnrollmentRepository` : Data access for enrollments
- `AuthAttemptRepository` : Data access for auth attempts

## Database Migrations

Database migrations are now handled by the dedicated `ezkey-migration` module. See the [Migration Module README](../ezkey-migration/README.md) for details.

### Running Migrations

#### Option 1: Using the Migration Module
```bash
cd ezkey-migration
mvn spring-boot:run

# Show migration information
mvn spring-boot:run -Dspring-boot.run.arguments="--info"

# Repair migration history
mvn spring-boot:run -Dspring-boot.run.arguments="--repair"
```

#### Option 2: Using Scripts
```bash
# From project root
./scripts/ezkey-flyway.sh

# Or on Windows
scripts\ezkey-flyway.bat
```

## Development

### Building the Module
```bash
cd ezkey-core
mvn clean compile
```

### Running Tests
```bash
cd ezkey-core
mvn test
```

### Dependencies

This module depends on:
- Spring Boot Data JPA
- Spring Boot Validation
- PostgreSQL Driver
- MapStruct (for object mapping)
- ZXing (for QR code generation)

## Integration with Other Modules

The core module is used by:
- **ezkey-admin-api**: Administration API
- **ezkey-auth-api**: Authentication API
- **ezkey-migration**: Database migration application
- **ezkey-demo-***: Demo applications

## Configuration

The migration application uses the same database configuration as other modules:
- Database connection: `application.properties`
- Migration scripts: `src/main/resources/db/migration/`

## Project Structure

```
ezkey-core/
├── src/main/java/org/ezkey/
│   ├── authattempt/     # Authentication attempt management
│   │   ├── domain/      # Domain objects and entities
│   │   ├── service/     # Business logic services
│   │   └── mapper/      # DTO-Entity mappers
│   ├── enrollment/       # Enrollment management
│   │   ├── domain/      # Domain objects and entities
│   │   ├── service/     # Business logic services
│   │   └── mapper/      # DTO-Entity mappers
│   ├── integration/      # Integration management
│   │   ├── domain/      # Domain objects and entities
│   │   └── repository/  # Data access layer
│   ├── signature/        # Cryptographic signature services
│   └── core/            # Main application for Flyway
├── src/main/resources/
│   ├── application.properties  # Database configuration
│   └── db/migration/          # Flyway migration scripts
└── README.md
```

## Security Principles

### Read-Once Guarantee

Ezkey implements a fundamental security principle: **read-once guarantee**. This ensures that:

- Each authentication attempt can only be read once by a legitimate device
- Once read, the attempt is marked as processed and cannot be read again
- This prevents replay attacks and ensures proof-of-possession
- The proof token returned can only be obtained by the legitimate reader

### Implementation Strategy

The security pattern follows these steps:
1. **Complete validation** before any database modification
2. **Atomic lock and marking** only if validation succeeds
3. **Immediate marking** to preserve read-once guarantee
4. **Response generation** with signed proof token

### Error Handling

- Uses secure error messages to prevent information leakage
- Logs detailed information for debugging purposes
- Maintains read-once guarantee even in error scenarios

## Configuration

### Database

The module is configured to use PostgreSQL with the following parameters:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ezkey_db
spring.datasource.username=postgres
spring.datasource.password=ezkey
spring.datasource.driver-class-name=org.postgresql.Driver
```

### Flyway

Migrations are configured to:
- Execute automatically on startup
- Use the `classpath:db/migration` directory
- Base migrations from version 1

## ezkey-flyway

### Description

`ezkey-flyway` is a dedicated command-line tool for database migration management. It allows executing Flyway commands without starting the web APIs, providing a lightweight and secure solution for database schema management.

### Installation

The `ezkey-flyway` scripts are available at the project root:
- `ezkey-flyway.bat` (Windows)
- `ezkey-flyway.sh` (Linux/Mac)

For Linux/Mac, make the script executable:
```bash
chmod +x ezkey-flyway.sh
```

### Usage

#### Available Commands

| Command | Description |
|---------|-------------|
| (none) | Execute pending migrations (default behavior) |
| `--migrate` | Execute pending migrations |
| `--info` | Display migration status information |
| `--repair` | Repair migration history |

#### Usage Examples

**Default migration:**
```bash
# Windows
ezkey-flyway.bat

# Linux/Mac
./ezkey-flyway.sh
```

**Display migration information:**
```bash
# Windows
ezkey-flyway.bat --info

# Linux/Mac
./ezkey-flyway.sh --info
```

**Repair migration history:**
```bash
# Windows
ezkey-flyway.bat --repair

# Linux/Mac
./ezkey-flyway.sh --repair
```

### Technical Operation

1. **Automatic compilation** : The script checks if the project is compiled and compiles it automatically if necessary
2. **Dependency management** : Uses Maven to retrieve the complete classpath with all dependencies
3. **Direct execution** : Launches the Java application directly without going through Maven for better performance
4. **Error handling** : Displays clear error messages in case of problems

### Advantages

- **Performance** : Direct Java execution without Maven overhead
- **Security** : No access to web APIs, only to migrations
- **Simplicity** : Intuitive command-line interface
- **Flexibility** : Support for all main Flyway commands
- **Robustness** : Automatic compilation and dependency management

### Troubleshooting

**Compilation error:**
```bash
# Check that Java 25 is installed
java -version

# Clean and recompile manually
cd ezkey-core
mvn clean compile
```

**Database connection error:**
- Verify that PostgreSQL is started
- Check connection parameters in `application.properties`
- Ensure the `ezkey_db` database exists

**Permission error (Linux/Mac):**
```bash
chmod +x ezkey-flyway.sh
```

### CI/CD Integration

The tool can be integrated into your CI/CD pipelines:

```yaml
# GitHub Actions example
- name: Run database migrations
  run: ./ezkey-flyway.sh --migrate
```

```bash
# Deployment script example
#!/bin/bash
echo "Running database migrations..."
./ezkey-flyway.sh --migrate
if [ $? -eq 0 ]; then
    echo "Migrations completed successfully"
else
    echo "Migration failed"
    exit 1
fi
```

### Output Examples

#### Info:
```
=== Flyway Migration Info ===
Current version: 1.0.0
Pending migrations: 0
Applied migrations: 1

Applied migrations:
  - 1.0.0 : Initial schema (executed: 2025-07-13T13:45:00)
=== Info Completed ===
```

#### Repair:
```
=== Starting Flyway Repair ===
=== Flyway Repair Completed ===
```

## Development Guidelines

### Code Style

- Follow Java 25 best practices
- Use Spring Boot conventions
- Implement comprehensive Javadoc documentation
- Follow the established package structure

### Security Considerations

- Always validate before modifying database records
- Use secure error messages to prevent information leakage
- Implement proper logging for debugging
- Maintain read-once guarantee in all operations

### Testing

- Write unit tests for all business logic
- Test security scenarios thoroughly
- Validate read-once guarantee behavior
- Test error handling and edge cases 