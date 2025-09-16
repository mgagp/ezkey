# Ezkey Migration Module

## Overview

The `ezkey-migration` module is a dedicated Spring Boot application responsible for managing database schema migrations using Flyway. This module provides a clean separation between the core library functionality and database migration operations.

## Features

- **Dedicated Migration App**: Separate from core library functionality
- **Flyway Integration**: Automated database schema management
- **Spring Boot CLI**: Command-line interface for migration operations
- **Standalone JAR**: Can be run independently for migration tasks
- **Configuration Management**: Environment-specific database configurations

## Usage

### Running Migrations

#### Option 1: Using Maven
```bash
cd ezkey-migration
mvn spring-boot:run
```

#### Option 2: Using the Scripts
```bash
# From project root
./scripts/ezkey-flyway.sh

# Or on Windows
scripts\ezkey-flyway.bat
```

#### Option 3: Standalone JAR
```bash
cd ezkey-migration
mvn clean package -Pmigration-jar
java -jar target/ezkey-migration.jar
```

### Configuration

The application uses standard Spring Boot configuration properties. Configuration can be provided via:

- `application.properties` file
- Environment variables
- Command-line arguments

#### Database Configuration
```properties
# Database connection
spring.datasource.url=jdbc:postgresql://localhost:5432/ezkey_db
spring.datasource.username=postgres
spring.datasource.password=ezkey

# Flyway configuration
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

#### Environment Variables
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ezkey_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=ezkey
```

## Architecture

### Dependencies
- **ezkey-core**: Core business logic and entities
- **Spring Boot**: Application framework
- **Spring Data JPA**: Database access
- **Flyway**: Database migration tool
- **PostgreSQL**: Database driver

### Key Components
- `EzkeyMigrationApp`: Main Spring Boot application class
- `application.properties`: Configuration properties
- Migration scripts: Located in `src/main/resources/db/migration/`

## Development

### Building the Module
```bash
cd ezkey-migration
mvn clean compile
```

### Running Tests
```bash
cd ezkey-migration
mvn test
```

### Building the JAR
```bash
cd ezkey-migration
mvn clean package -Pmigration-jar
```

## Migration Scripts

Migration scripts are located in `src/main/resources/db/migration/` and follow Flyway naming conventions:

- `V{version}__{description}.sql`
- Example: `V1__Initial_schema.sql`

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Verify PostgreSQL is running
   - Check connection parameters in `application.properties`
   - Ensure database exists

2. **Migration Scripts Not Found**
   - Verify scripts are in `src/main/resources/db/migration/`
   - Check Flyway configuration in `application.properties`

3. **Permission Issues**
   - Ensure database user has necessary permissions
   - Check file permissions for migration scripts

### Logging

The application provides detailed logging for troubleshooting:

```properties
logging.level.org.ezkey=INFO
logging.level.org.flywaydb=INFO
logging.level.org.springframework.boot.autoconfigure.flyway=INFO
```

## Integration

This module is designed to work seamlessly with the Ezkey ecosystem:

- **ezkey-core**: Provides entities and business logic
- **ezkey-admin-api**: Uses migrated database schema
- **ezkey-auth-api**: Uses migrated database schema
- **Scripts**: Automated migration execution

## Security

- Database credentials should be provided via environment variables in production
- Migration scripts are validated before execution
- Flyway provides built-in protection against concurrent migrations

## License

This module is part of the Ezkey project and is licensed under the MIT License.
