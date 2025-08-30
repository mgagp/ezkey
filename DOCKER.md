# Ezkey Docker Deployment Guide

Welcome to the Ezkey Docker deployment guide! This document will walk you through the **5-minute developer experience** for getting Ezkey up and running using Docker.

## 🚀 Quick Start (5 Minutes)

### Prerequisites

- **Docker** 20.10+ and **Docker Compose** 2.0+
- **Java 17+** and **Maven 3.6+**
- **Git** for cloning the repository

### 1. Clone and Build (2 minutes)

```bash
# Clone the repository
git clone https://github.com/mgagp/ezkey.git
cd ezkey

# Build all modules and create Docker images
mvn clean install -Pdocker
```

### 2. Start the Complete Stack (1 minute)

```bash
# Start all services
docker compose up -d

# Wait for services to be healthy (optional)
docker compose ps
```

### 3. Access the Services (immediate)

| Service | URL | Description |
|---------|-----|-------------|
| **Admin API** | http://localhost:9080 | Management interface |
| **Auth API** | http://localhost:8080 | Mobile authentication |
| **Sim API** | http://localhost:8081 | Testing and simulation |
| **ACME Demo** | http://localhost:8082 | Demo business application |
| **Device Demo** | http://localhost:8083 | Demo mobile device |

### 4. API Documentation

| Service | Documentation URL |
|---------|-------------------|
| Admin API | http://localhost:9080/swagger-ui.html |
| Auth API | http://localhost:8080/swagger-ui.html |
| Sim API | http://localhost:8081/swagger-ui.html |

## 🎯 What is Ezkey?

Ezkey is an **open-source MFA/Passkey alternative** designed to be:

- **Simple**: Easy-to-integrate REST APIs
- **Pragmatic**: Solves 90% of MFA needs with 10% of the complexity
- **Self-hosted**: Full control over your authentication infrastructure
- **Developer-friendly**: Built by developers, for developers

### Core Concepts

- **Integration**: An application you want to protect (e.g., admin portal)
- **Enrollment**: A user's device registered for MFA
- **Authentication Attempt**: A request for user verification

## 📋 Step-by-Step Ezkey Workflow

Now that your Docker stack is running, let's walk through the complete Ezkey workflow using the command-line interface.

### Step 1: Configure Ezkey CLI

```bash
# Navigate to CLI directory
cd ezkey-cli

# Install dependencies (first time only)
npm install

# Configure CLI to use Docker stack
../bin/ezkey configure set adminUrl http://localhost:9080
../bin/ezkey configure set authUrl http://localhost:8080
../bin/ezkey configure set simUrl http://localhost:8081
```

### Step 2: Create an Integration

An **Integration** represents an application you want to protect with MFA.

```bash
# Create a new integration
../bin/ezkey admin integration create \
  --name "ACME Admin Portal" \
  --description "Administrative interface for ACME Inc" \
  --logo "https://acme.example.com/logo.png"
```

**What this represents**: This is like registering your application with Ezkey. Each application that needs MFA gets its own integration.

### Step 3: Create User Enrollments

An **Enrollment** links a user's device to an integration.

```bash
# Create an enrollment for the CEO
../bin/ezkey admin enrollment create \
  --integration-id 1 \
  --challenge-required true

# Create an enrollment for a regular admin
../bin/ezkey admin enrollment create \
  --integration-id 1 \
  --challenge-required false
```

**What this represents**: 
- Each user who needs access gets an enrollment
- `challenge-required` means they need to enter additional verification (like a PIN)
- You'll get enrollment IDs that users will use to bind their devices

### Step 4: Simulate Device Binding

In a real scenario, users would scan QR codes or click links to bind their devices. For this demo, we'll simulate it:

```bash
# Generate a device key pair for testing
../bin/ezkey sim keypair --key-size 2048

# Bind the device to enrollment #1
../bin/ezkey auth enrollment bind --id 1

# Complete the enrollment verification
../bin/ezkey auth enrollment verify \
  --enrollment-id 1 \
  --device-public-key "$(cat ~/.ezkey/sim-public-key.pem)" \
  --enrollment-proof-token "TOKEN_FROM_BIND_RESPONSE"
```

**What this represents**: This simulates a user installing your mobile app and completing the enrollment process.

### Step 5: Authentication Workflow

Now let's simulate an authentication request:

```bash
# User tries to access protected resource - create auth attempt
../bin/ezkey admin auth-attempt create --enrollment-id 1

# Simulate device responding to the authentication
../bin/ezkey auth auth-attempt pending --enrollment-id 1
../bin/ezkey auth auth-attempt respond \
  --enrollment-id 1 \
  --auth-attempt-proof-token "TOKEN_FROM_PENDING" \
  --accepted true

# Check the result
../bin/ezkey admin auth-attempt get --id 1
```

**What this represents**: 
1. User tries to access your admin portal
2. Your app creates an authentication attempt
3. User's mobile device receives the request
4. User approves/denies on their device
5. Your app gets the result

### Step 6: Using the Wait API (Synchronous Experience)

For a better user experience, use the Wait API:

```bash
# Create auth attempt and wait for completion
../bin/ezkey admin auth-attempt create --enrollment-id 1
../bin/ezkey admin auth-attempt wait --id 1 --timeout 60

# This will block until the user responds or timeout occurs
```

**What this represents**: Instead of polling, your application can wait synchronously for the user to respond on their device.

## 🌐 Demo Applications

The Docker stack includes two demo applications that showcase real-world integration:

### ACME Demo App (http://localhost:8082)

A **business application** demonstrating how to integrate Ezkey into your existing web application:

- Login flow with MFA challenge
- Integration management
- User enrollment process
- Authentication request handling

### Device Demo App (http://localhost:8083)

A **mobile device simulator** showing the user experience:

- Enrollment binding process
- Authentication approval/denial
- Challenge handling
- Device key management

## 🔧 Configuration Options

### Environment Variables

Customize the deployment using the `.env` file:

```bash
# Database settings
POSTGRES_DB=ezkey_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=ezkey

# Service ports
EZKEY_ADMIN_API_PORT=9080
EZKEY_AUTH_API_PORT=8080
EZKEY_SIM_API_PORT=8081

# Application settings
EZKEY_AUTH_TTL_SECONDS=120
JAVA_OPTS=-Xms256m -Xmx512m
```

### Scaling Services

Scale individual services based on load:

```bash
# Scale auth API for higher load
docker compose up -d --scale ezkey-auth-api=3

# Scale admin API
docker compose up -d --scale ezkey-admin-api=2
```

## 🔨 Development Workflow

### Building New Images

After code changes, rebuild specific services:

```bash
# Rebuild and restart specific service
mvn clean install -Pdocker -pl ezkey-admin-api
docker compose restart ezkey-admin-api

# Rebuild all services
mvn clean install -Pdocker
docker compose restart
```

### Accessing Logs

Monitor service logs:

```bash
# View all logs
docker compose logs -f

# View specific service logs
docker compose logs -f ezkey-admin-api
docker compose logs -f ezkey-postgres
```

### Database Management

Access the database directly:

```bash
# Connect to PostgreSQL
docker compose exec ezkey-postgres psql -U postgres -d ezkey_db

# View current schema
\dt

# Run migrations manually
docker compose run --rm ezkey-migration
```

## 🔧 Troubleshooting

### Common Issues

1. **Port conflicts**: Change ports in `.env` if they're already in use
2. **Memory issues**: Adjust `JAVA_OPTS` in `.env` for smaller environments
3. **Database connection**: Ensure PostgreSQL is healthy before starting APIs

### Health Checks

Check service health:

```bash
# Overall stack health
docker compose ps

# Individual service health
curl http://localhost:9080/actuator/health
curl http://localhost:8080/actuator/health
```

### Reset Everything

Start fresh:

```bash
# Stop and remove everything
docker compose down -v

# Remove images
docker images ezkey/* -q | xargs docker rmi -f

# Rebuild and start
mvn clean install -Pdocker
docker compose up -d
```

## 🚀 Production Considerations

### Security

- Change default passwords in `.env`
- Use Docker secrets for sensitive data
- Enable TLS/HTTPS with reverse proxy
- Implement proper network segmentation

### Performance

- Adjust JVM heap sizes based on load
- Use external PostgreSQL for production
- Implement monitoring and alerting
- Consider horizontal scaling

### Backup

- Backup PostgreSQL data volume
- Export integration configurations
- Document custom environment settings

## 📚 Next Steps

1. **Explore the APIs**: Use the Swagger documentation to understand all endpoints
2. **Integrate with your app**: Follow the integration patterns shown in the demo apps
3. **Customize the mobile experience**: Study the device demo for mobile integration patterns
4. **Scale for production**: Review the production considerations section

## 🤝 Getting Help

- **Documentation**: [Project Wiki](https://github.com/mgagp/ezkey/wiki)
- **Issues**: [GitHub Issues](https://github.com/mgagp/ezkey/issues)
- **Discussions**: [GitHub Discussions](https://github.com/mgagp/ezkey/discussions)

---

**Congratulations!** 🎉 You now have a complete Ezkey MFA system running locally and understand how to integrate it with your applications.