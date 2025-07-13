# Ezkey - Open Source MFA/Passkey Alternative

## Overview

Ezkey is a pragmatic, open-source, and developer-friendly alternative to complex passkey implementations. It provides a simple and secure MFA solution that can be easily integrated into any application.

## Features

- **Simple Integration**: Easy-to-use REST APIs
- **Secure**: Cryptographic key-based authentication
- **Open Source**: MIT licensed
- **Developer-Friendly**: Comprehensive documentation and examples
- **Cross-Platform**: Works with any technology stack

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Database (H2, PostgreSQL, MySQL)

### Running the Application

1. Clone the repository:
```bash
git clone https://github.com/your-org/ezkey.git
cd ezkey
```

2. Build the project:
```bash
mvn clean install
```

3. Launch a Database
```bash
docker run --name ezkey-postgres -e POSTGRES_PASSWORD=ezkey -e POSTGRES_DB=ezkey_db -p 5432:5432 -d postgres:17
```

4. Run the application:
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## API Documentation

### Base URL
```
http://localhost:8080/api/v1
```

### Endpoints

#### Integrations
- `GET /integrations` - List all integrations
- `GET /integrations/{id}` - Get integration by ID
- `POST /integrations` - Create new integration

## Development

### Code Standards

This project follows strict code formatting and quality standards:

#### Formatting
- **Indentation**: 4 spaces (no tabs)
- **Line Length**: Maximum 120 characters
- **Encoding**: UTF-8
- **Line Endings**: LF (Unix style)

#### Tools
- **EditorConfig**: `.editorconfig` file ensures consistent formatting across editors
- **Checkstyle**: `google_checks.xml` enforces Google Java Style Guide
- **MapStruct**: For object mapping between DTOs and entities

#### Running Code Quality Checks

```bash
# Run Checkstyle
mvn checkstyle:check

# Run all quality checks
mvn clean verify
```

### Project Structure

```
src/main/java/org/ezkey/
├── integration/
│   ├── domain/
│   │   ├── entity/          # JPA entities
│   │   └── repository/      # Spring Data repositories
│   ├── service/             # Business logic
│   ├── controller/          # REST endpoints
│   ├── dto/
│   │   ├── request/         # Request DTOs
│   │   ├── response/        # Response DTOs
│   │   └── common/          # Shared DTOs
│   ├── mapper/              # MapStruct mappers
│   └── exception/           # Custom exceptions
```

### Development Guidelines

1. **Follow the established package structure**
2. **Use MapStruct for object mapping**
3. **Always return DTOs from controllers**
4. **Include comprehensive Javadoc**
5. **Write unit tests for new features**
6. **Use conventional commit messages**

### IDE Configuration

#### Cursor/VS Code
- Install the EditorConfig extension
- Install the Checkstyle extension
- Configure Java formatting to use 4 spaces

#### IntelliJ IDEA
- Enable EditorConfig support
- Import the Checkstyle configuration
- Configure code style to match Google Java Style

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes following the code standards
4. Add tests for new functionality
5. Submit a pull request

### Commit Message Format

Use conventional commit messages:
```
feat: add new integration endpoint
fix: resolve authentication issue
docs: update API documentation
style: format code according to standards
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/your-org/ezkey/issues)
- **Documentation**: [Wiki](https://github.com/your-org/ezkey/wiki)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/ezkey/discussions)

## Roadmap

- [ ] Mobile application (Flutter)
- [ ] Docker support
- [ ] Kubernetes deployment
- [ ] OpenAPI/Swagger documentation
- [ ] Performance benchmarks
- [ ] Security audit
