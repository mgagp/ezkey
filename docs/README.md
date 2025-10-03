# 📚 Ezkey Documentation

Welcome to the centralized documentation for the Ezkey project - an open-source MFA/Passkey alternative designed for developers and organizations.

## 📋 Table of Contents

### 🏗️ Architecture & Design
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete system architecture, security design, and cryptographic implementation
- **[CRYPTO.md](CRYPTO.md)** - Detailed cryptographic specifications and implementation details
- **[ENDPOINT.md](ENDPOINT.md)** - API endpoint documentation and specifications

### 🛠️ Development
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Development guide, OpenAPI documentation, and testing strategy

### 📊 Monitoring & Operations
- **[monitoring/](monitoring/)** - Grafana dashboards, monitoring setup, and operational guides
- **[OPERATIONAL.md](OPERATIONAL.md)** - Production deployment, security configuration, and operational best practices

### 🔧 Development Tools
- **[dev-tools/](dev-tools/)** - Development tools, configurations, and utilities
- **[MAINTENANCE.md](MAINTENANCE.md)** - Documentation maintenance guidelines and standards

---

## 🚀 Quick Start

### For Developers
1. **Start Here**: Read [ARCHITECTURE.md](ARCHITECTURE.md) for system overview
2. **Development Setup**: Follow [DEVELOPMENT.md](DEVELOPMENT.md) for development environment
3. **API Reference**: Check [ENDPOINT.md](ENDPOINT.md) for API specifications
4. **Cryptography**: Review [CRYPTO.md](CRYPTO.md) for security implementation

### For System Administrators
1. **Architecture**: Review [ARCHITECTURE.md](ARCHITECTURE.md) for deployment planning
2. **Operations**: Follow [OPERATIONAL.md](OPERATIONAL.md) for production deployment and security
3. **Monitoring**: Set up [monitoring/](monitoring/) for production monitoring
4. **Security**: Understand [CRYPTO.md](CRYPTO.md) for security configuration

### For Contributors
1. **Project Overview**: Start with [ARCHITECTURE.md](ARCHITECTURE.md)
2. **Development Process**: Follow [DEVELOPMENT.md](DEVELOPMENT.md)
3. **Code Standards**: Review development guidelines in [DEVELOPMENT.md](DEVELOPMENT.md)

---

## 📖 Document Overview

### 🏗️ ARCHITECTURE.md
**Comprehensive system architecture and security documentation**
- Project organization and module structure
- Cryptographic architecture and SignatureService implementation
- API security and separation strategies
- Authentication security evolution phases
- Security recommendations and best practices

**Key Sections:**
- Project Organization
- Cryptographic Architecture
- API Security and Separation
- Authentication Security Evolution
- Security Recommendations

### 🔐 CRYPTO.md
**Detailed cryptographic specifications and implementation**
- SignatureService analysis and recommendations
- Cryptographic algorithm evaluation
- Security best practices and compliance
- Implementation phases and improvements
- Interoperability considerations

**Key Sections:**
- SignatureService Implementation
- Security Analysis
- Algorithm Recommendations
- Implementation Phases
- Compliance and Standards

### 🌐 ENDPOINT.md
**Complete API endpoint documentation**
- REST API specifications
- Endpoint descriptions and parameters
- Request/response formats
- Authentication and authorization
- Error handling and status codes

**Key Sections:**
- API Overview
- Endpoint Specifications
- Authentication
- Error Handling
- Examples

### 🛠️ DEVELOPMENT.md
**Development guide and testing strategy**
- OpenAPI documentation setup
- Comprehensive testing strategy
- Development workflow and standards
- Quality assurance processes
- Code quality metrics

**Key Sections:**
- OpenAPI Documentation
- Testing Strategy
- Development Workflow
- Quality Assurance

### 📊 OPERATIONAL.md
**Production deployment and operational best practices**
- Rate limiting security and IP detection
- Proxy configuration and SSL/TLS setup
- Database configuration and security
- Monitoring, logging, and performance tuning
- Backup and recovery procedures
- Troubleshooting guide

**Key Sections:**
- Rate Limiting Security
- IP Detection and Proxy Configuration
- Database Configuration
- SSL/TLS Configuration
- Logging and Monitoring
- Performance Tuning
- Security Headers
- Backup and Recovery

---

## 🎯 Project Vision

**Ezkey** is an open-source, pragmatic, developer-first MFA/Passkey alternative that provides:

- **🔒 Security**: Production-grade cryptographic signatures and secure authentication
- **🌍 Interoperability**: Cross-platform compatibility with multiple programming languages
- **⚡ Simplicity**: Easy integration and developer-friendly APIs
- **🔧 Flexibility**: Configurable and extensible architecture
- **📈 Scalability**: Designed for both small applications and enterprise deployments

---

## 🏛️ Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Mobile App    │    │   Web App       │    │   CLI Tools     │
│   (React Native)│    │   (Demo Apps)   │    │   (ezkey-cli)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │                           │
            ┌───────▼────────┐        ┌────────▼────────┐
            │  ezkey-auth-api │        │  ezkey-admin-api │
            │  (Port 8080)    │        │  (Port 9080)     │
            └─────────┬───────┘        └─────────┬───────┘
                      │                          │
                      └──────────┬───────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │      ezkey-core           │
                    │  (Business Logic)         │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │    PostgreSQL Database    │
                    │    (ezkey_db)             │
                    └───────────────────────────┘
```

---

## 🔧 Technology Stack

### Backend
- **Java 21+** - Modern Java with latest features
- **Spring Boot 3.x** - Enterprise-grade framework
- **Spring Data JPA** - Data persistence layer
- **PostgreSQL** - Primary database
- **Flyway** - Database migration management
- **Maven** - Dependency management and build

### Frontend & Mobile
- **React Native** - Cross-platform mobile application
- **HTML/CSS/JavaScript** - Demo web applications
- **Thymeleaf** - Server-side templating

### Development & Operations
- **SpringDoc OpenAPI** - API documentation
- **JUnit 5** - Testing framework
- **Grafana** - Monitoring and dashboards
- **Docker** - Containerization support

---

## 📊 Key Features

### 🔐 Security
- **RSA-2048 + SHA-256** cryptographic signatures
- **PKCS#8/X.509** key formats for interoperability
- **Row-level locking** for authentication attempts
- **Audit logging** and comprehensive monitoring
- **Zero-trust architecture** evolution path

### 🌐 APIs
- **Admin API** (Port 9080) - Organization management with secure authentication
  - Multi-tenant administration (Global, Tenant, Integration admins)
  - Secure login/logout with rate limiting protection
  - Bearer token authentication
  - Integration and enrollment management
- **Auth API** (Port 8080) - Mobile device operations
- **RESTful design** with comprehensive OpenAPI documentation
- **JWT authentication** and role-based access control

### 📱 Mobile Support
- **Cross-platform** React Native application
- **Device enrollment** and binding
- **Push notifications** for authentication requests
- **Offline capability** with local storage

### 🔧 Developer Experience
- **Multi-language SDKs** (Java, Python, JavaScript, .NET)
- **Command-line interface** for administration
- **Comprehensive documentation** and examples
- **Docker support** for easy deployment

---

## 🚀 Getting Started

### Prerequisites
- Java 21 or later
- Maven 3.8+
- PostgreSQL 13+
- Node.js 18+ (for mobile app)

### Quick Setup
1. **Clone the repository**
   ```bash
   git clone https://github.com/ezkey/ezkey.git
   cd ezkey
   ```

2. **Start the database**
   ```bash
   # Using Docker
   docker run -d --name ezkey-postgres \
     -e POSTGRES_DB=ezkey_db \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=ezkey \
     -p 5432:5432 postgres:13
   ```

3. **Build and run the core services**
   ```bash
   mvn clean install
   cd ezkey-admin-api && mvn spring-boot:run
   cd ezkey-auth-api && mvn spring-boot:run
   ```

4. **Access the APIs**
   - Admin API: http://localhost:9080/swagger-ui.html
   - Auth API: http://localhost:8080/swagger-ui.html

### Next Steps
- **Read the documentation**: Start with [ARCHITECTURE.md](ARCHITECTURE.md)
- **Set up monitoring**: Follow [monitoring/](monitoring/) guides
- **Explore examples**: Check demo applications
- **Join the community**: Contribute to the project

---

## 🤝 Contributing

We welcome contributions to the Ezkey project! Here's how to get started:

### Development Process
1. **Fork the repository** and create a feature branch
2. **Read the documentation** to understand the architecture
3. **Follow coding standards** outlined in [DEVELOPMENT.md](DEVELOPMENT.md)
4. **Write tests** for your changes
5. **Submit a pull request** with a clear description

### Areas for Contribution
- **Core functionality** - Business logic and APIs
- **Mobile application** - React Native development
- **SDKs** - Multi-language SDK development
- **Documentation** - Improve and expand documentation
- **Testing** - Increase test coverage and quality
- **Monitoring** - Enhance monitoring and observability

### Code Standards
- **Java 21+** with modern features
- **Spring Boot 3.x** best practices
- **UTF-8 encoding** without BOM
- **Comprehensive Javadoc** documentation
- **90%+ test coverage** requirement

---

## 📞 Support & Community

### Documentation
- **Project Documentation**: This directory contains all project documentation
- **API Documentation**: Available via Swagger UI when services are running
- **Code Documentation**: Comprehensive Javadoc in source code

### Getting Help
- **GitHub Issues**: Report bugs and request features
- **GitHub Discussions**: Ask questions and discuss ideas
- **Email**: contributors@ezkey.org for general inquiries

### Resources
- **Website**: https://ezkey.org
- **GitHub**: https://github.com/ezkey/ezkey
- **License**: MIT License

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.

---

*Last updated: January 2025 | Ezkey v1.0+ | Documentation maintained by the Ezkey team*
