# Ezkey Docker Configuration

This directory contains Docker-specific configuration files and utilities for the Ezkey project.

## Files

### Core Configuration
- **`init-db.sql`** - PostgreSQL database initialization script
- **`start.sh`** - Quick start script for the complete Docker stack
- **`manage.sh`** - Management script for Docker operations

### Usage

#### Quick Start (from project root)
```bash
# Start everything
./ezkey-docker/start.sh

# Check status
./ezkey-docker/manage.sh status

# View logs
./ezkey-docker/manage.sh logs

# Stop everything
./ezkey-docker/manage.sh stop
```

#### Management Commands
```bash
# Show all available commands
./ezkey-docker/manage.sh help

# Check service health
./ezkey-docker/manage.sh health

# Restart specific service
./ezkey-docker/manage.sh restart ezkey-admin-api

# View logs for specific service
./ezkey-docker/manage.sh logs ezkey-postgres

# Rebuild images
./ezkey-docker/manage.sh build

# Reset everything (removes data!)
./ezkey-docker/manage.sh reset
```

## Docker Architecture

The Docker setup includes:

1. **PostgreSQL Database** - Data persistence with health checks
2. **Migration Service** - One-time database schema setup
3. **Admin API** - Management interface (port 9080)
4. **Auth API** - Authentication service (port 8080)
5. **Sim API** - Testing and simulation (port 8081)
6. **ACME Demo** - Business application demo (port 8082)
7. **Device Demo** - Mobile device simulator (port 8083)

## Configuration

The main configuration is handled through:
- **`.env`** file in project root for environment variables
- **`docker compose.yml`** in project root for orchestration
- **`application-docker.properties`** files for Spring Boot configuration

## Security Notes

- Default passwords are defined in `.env` - change them for production use
- Health check endpoints are exposed for monitoring
- Services communicate through Docker network `ezkey-network`
- PostgreSQL data is persisted in named volume `ezkey-postgres-data`

## Troubleshooting

### Common Issues
1. **Port conflicts**: Check if ports 5432, 8080, 8081, 8082, 8083, 9080 are available
2. **Out of memory**: Adjust `JAVA_OPTS` in `.env` file
3. **Services not starting**: Check logs with `./manage.sh logs`

### Debug Commands
```bash
# Check if PostgreSQL is ready
docker compose exec ezkey-postgres pg_isready -U postgres

# Connect to database
docker compose exec ezkey-postgres psql -U postgres -d ezkey_db

# Check container status
docker compose ps

# View resource usage
docker stats
```

## Development

When developing, you can:
1. Make code changes
2. Run `./manage.sh build` to rebuild images
3. Run `./manage.sh restart` to apply changes

For faster development, consider running individual services outside Docker while keeping the database in Docker.