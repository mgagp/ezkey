# EZ Key Docker Deployment

This directory contains Docker configuration files and scripts to run the complete EZ Key stack in containers.

## Quick Start (5-Minute Setup)

### Prerequisites

- **Docker Desktop** (or Docker Engine + Docker Compose)
  - Windows/Mac: [Docker Desktop](https://www.docker.com/products/docker-desktop)
  - Linux: Docker Engine + Docker Compose plugin

### Start the Stack

**Linux/Mac:**
```bash
./docker/start.sh
```

**Windows:**
```cmd
docker\start.bat
```

That's it! The script will:
1. Build all Docker images
2. Start PostgreSQL database
3. Run database migrations
4. Start all API services
5. Wait for services to be healthy

### Access the Services

Once started, you can access:

- **Admin API**: http://localhost:9080
- **Auth API**: http://localhost:8080
- **Crypto API**: http://localhost:9090
- **Demo Device**: http://localhost:8083

**API Documentation (Swagger UI):**
- Admin API: http://localhost:9080/swagger-ui.html
- Auth API: http://localhost:8080/swagger-ui.html
- Crypto API: http://localhost:9090/swagger-ui.html

## Architecture

```
┌─────────────────────────────────────────────────┐
│           Docker Compose Stack                  │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────┐    ┌──────────────┐         │
│  │  PostgreSQL  │    │  Migration   │         │
│  │     17       │◄───│    Job       │         │
│  └──────┬───────┘    └──────────────┘         │
│         │                                       │
│         ├──► ┌──────────────┐                 │
│         │    │  Admin API   │                 │
│         │    │   (9080)     │                 │
│         │    └──────────────┘                 │
│         │                                       │
│         ├──► ┌──────────────┐                 │
│         │    │  Auth API    │                 │
│         │    │   (8080)     │                 │
│         │    └──────────────┘                 │
│         │                                       │
│         ├──► ┌──────────────┐                 │
│         │    │ Crypto API   │                 │
│         │    │   (9090)     │                 │
│         │    └──────────────┘                 │
│         │                                       │
│         └──► ┌──────────────┐                 │
│              │ Demo Device  │                 │
│              │   (8083)     │                 │
│              └──────────────┘                 │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Services

### PostgreSQL (postgres)
- **Image**: `postgres:17-alpine`
- **Port**: `5432` (internal only)
- **Database**: `ezkey_db`
- **Username**: `postgres`
- **Password**: `ezkey`
- **Data Persistence**: Volume `postgres-data`

### Migration (migration)
- **Type**: One-time job
- **Purpose**: Runs Flyway database migrations
- **Depends on**: PostgreSQL (healthy)
- **Runs**: Before all API services start

### Admin API (admin-api)
- **Port**: `9080`
- **Purpose**: Administration interface for integrations, enrollments, and auth attempts
- **Depends on**: PostgreSQL (healthy), Migration (completed)
- **Health Check**: http://localhost:9080/actuator/health

### Auth API (auth-api)
- **Port**: `8080`
- **Purpose**: Mobile authentication API for enrollment and authentication flows
- **Depends on**: PostgreSQL (healthy), Migration (completed)
- **Health Check**: http://localhost:8080/actuator/health

### Crypto API (crypto-api)
- **Port**: `9090`
- **Purpose**: Cryptographic services for testing (key generation, signing, validation)
- **Depends on**: PostgreSQL (healthy), Migration (completed)
- **Health Check**: http://localhost:9090/actuator/health
- **Note**: No database required, but included in stack for consistency

### Demo Device (demo-device)
- **Port**: `8083`
- **Purpose**: Simulated mobile device for testing enrollment and authentication flows
- **Depends on**: Auth API (healthy)
- **Health Check**: http://localhost:8083/actuator/health

## Management Commands

### Start Services
```bash
# Linux/Mac
./docker/manage.sh start

# Windows
docker\manage.bat start
```

### Stop Services
```bash
# Linux/Mac
./docker/manage.sh stop

# Windows
docker\manage.bat stop
```

### Restart Services
```bash
# Linux/Mac
./docker/manage.sh restart

# Windows
docker\manage.bat restart
```

### View Logs
```bash
# All services
./docker/manage.sh logs

# Specific service
./docker/manage.sh logs admin-api
./docker/manage.sh logs auth-api
./docker/manage.sh logs postgres
```

### Check Status
```bash
# Linux/Mac
./docker/manage.sh status

# Windows
docker\manage.bat status
```

### Build Images
```bash
# Linux/Mac
./docker/manage.sh build

# Windows
docker\manage.bat build
```

### Clean Everything
```bash
# Linux/Mac
./docker/manage.sh clean

# Windows
docker\manage.bat clean
```

**Warning**: The `clean` command removes all containers, networks, and volumes, including database data.

## Direct Docker Compose Usage

You can also use Docker Compose directly:

```bash
cd docker

# Start services
docker-compose up -d

# Stop services
docker-compose stop

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f admin-api

# Restart a service
docker-compose restart admin-api

# Remove everything
docker-compose down -v
```

## Environment Variables

### Database Configuration

Default values (can be overridden via environment variables):

- `POSTGRES_DB`: `ezkey_db`
- `POSTGRES_USER`: `postgres`
- `POSTGRES_PASSWORD`: `ezkey`

To override, set environment variables before running `start.sh`:

```bash
export POSTGRES_PASSWORD=mysecurepassword
./docker/start.sh
```

### Spring Profiles

All services use the `docker` Spring profile, which loads configuration from:
- `application-docker.properties` files in each module's `config/` directory

## Data Persistence

### Database Data

PostgreSQL data is persisted in a Docker volume named `postgres-data`. This means:
- Data persists across container restarts
- Data persists when containers are stopped
- Data is removed only when using `clean` command or `docker-compose down -v`

### Viewing Volume Data

```bash
# List volumes
docker volume ls

# Inspect postgres-data volume
docker volume inspect ezkey_postgres-data

# Backup database (from host)
docker exec ezkey-postgres pg_dump -U postgres ezkey_db > backup.sql

# Restore database (from host)
docker exec -i ezkey-postgres psql -U postgres ezkey_db < backup.sql
```

## Network Configuration

All services run on a bridge network named `ezkey-network`. Services can communicate using their service names:

- `postgres` - Database hostname
- `admin-api` - Admin API hostname
- `auth-api` - Auth API hostname
- `crypto-api` - Crypto API hostname
- `demo-device` - Demo Device hostname

## Troubleshooting

### Services Won't Start

1. **Check Docker is running:**
   ```bash
   docker info
   ```

2. **Check for port conflicts:**
   ```bash
   # Linux/Mac
   lsof -i :9080 -i :8080 -i :9090 -i :8083 -i :5432
   
   # Windows
   netstat -ano | findstr "9080 8080 9090 8083 5432"
   ```

3. **View service logs:**
   ```bash
   ./docker/manage.sh logs
   ```

### Database Connection Issues

1. **Check PostgreSQL is healthy:**
   ```bash
   docker exec ezkey-postgres pg_isready -U postgres
   ```

2. **Check migration completed:**
   ```bash
   docker logs ezkey-migration
   ```

3. **Verify network connectivity:**
   ```bash
   docker exec ezkey-admin-api ping postgres
   ```

### Build Failures

1. **Clean build cache:**
   ```bash
   docker system prune -a
   ```

2. **Rebuild without cache:**
   ```bash
   ./docker/manage.sh build
   ```

### Health Checks Failing

1. **Check service is running:**
   ```bash
   docker ps
   ```

2. **Check service logs:**
   ```bash
   ./docker/manage.sh logs <service-name>
   ```

3. **Manually test health endpoint:**
   ```bash
   curl http://localhost:9080/actuator/health
   ```

### Port Already in Use

If a port is already in use, you can modify `docker-compose.yml` to use different ports:

```yaml
ports:
  - "9081:9080"  # Use 9081 on host instead of 9080
```

## Development Workflow

### Making Code Changes

1. **Stop services:**
   ```bash
   ./docker/manage.sh stop
   ```

2. **Make code changes**

3. **Rebuild and restart:**
   ```bash
   ./docker/manage.sh build
   ./docker/manage.sh start
   ```

### Testing Changes

1. **View logs in real-time:**
   ```bash
   ./docker/manage.sh logs admin-api
   ```

2. **Test API endpoints:**
   ```bash
   curl http://localhost:9080/actuator/health
   ```

3. **Access Swagger UI:**
   - Open http://localhost:9080/swagger-ui.html in browser

## Production Considerations

⚠️ **This Docker setup is designed for development, QA, and demonstrations.**

For production deployment, consider:

1. **Security:**
   - Change default passwords
   - Use secrets management (Docker secrets, Kubernetes secrets)
   - Enable TLS/HTTPS
   - Configure firewall rules

2. **Performance:**
   - Use production-grade PostgreSQL configuration
   - Configure JVM memory limits
   - Set appropriate resource limits

3. **Monitoring:**
   - Add monitoring and logging solutions
   - Configure health check endpoints
   - Set up alerting

4. **High Availability:**
   - Use orchestration platform (Kubernetes, Docker Swarm)
   - Configure database replication
   - Set up load balancing

5. **Backup:**
   - Implement automated database backups
   - Test restore procedures
   - Store backups securely

## Additional Resources

- [Main README](../README.md) - Project overview and documentation
- [API Endpoints](../docs/ENDPOINT.md) - Detailed API reference
- [Architecture](../docs/ARCHITECTURE.md) - System architecture documentation

## Support

For issues or questions:
- Check the troubleshooting section above
- Review service logs: `./docker/manage.sh logs`
- Check the main project documentation
- Open an issue on GitHub

