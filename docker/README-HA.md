# EZ Key Docker HA Deployment

This directory contains Docker configuration files and scripts to run the EZ Key stack in High Availability (HA) mode with load balancing for testing ShedLock distributed locking.

## Overview

The HA stack runs **2 instances** of each API (admin-api and auth-api) behind **HAProxy load balancers**, plus **Crypto API** and **Demo Device** services. This setup allows testing ShedLock's distributed locking mechanism to ensure scheduled jobs execute only once across multiple instances.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Docker Compose HA Stack                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐                                       │
│  │  PostgreSQL  │                                       │
│  │   (shared)   │                                       │
│  └──────┬───────┘                                       │
│         │                                                │
│         ├──► ┌──────────────┐  ┌──────────────┐        │
│         │    │ Admin API #1 │  │ Admin API #2 │        │
│         │    │   (9081)     │  │   (9082)     │        │
│         │    └──────┬───────┘  └──────┬───────┘        │
│         │           │                 │                 │
│         │           └────────┬────────┘                 │
│         │                    │                          │
│         │              ┌─────▼──────┐                  │
│         │              │  HAProxy   │                  │
│         │              │  Admin LB  │                  │
│         │              │   (9080)   │                  │
│         │              └────────────┘                  │
│         │                                                │
│         ├──► ┌──────────────┐  ┌──────────────┐        │
│         │    │  Auth API #1 │  │  Auth API #2 │        │
│         │    │   (8081)     │  │   (8082)     │        │
│         │    └──────┬───────┘  └──────┬───────┘        │
│         │           │                 │                 │
│         │           └────────┬────────┘                 │
│         │                    │                          │
│         │              ┌─────▼──────┐                  │
│         │              │  HAProxy   │                  │
│         │              │  Auth LB   │                  │
│         │              │   (8080)   │                  │
│         │              └────────────┘                  │
│         │                                                │
│         ├──► ┌──────────────┐                          │
│         │    │  Crypto API   │                          │
│         │    │   (9090)     │                          │
│         │    └──────────────┘                          │
│         │                                                │
│         ├──► ┌──────────────┐                          │
│         │    │ Demo Device   │                          │
│         │    │   (8083)     │                          │
│         │    └──────────────┘                          │
│         │                                                │
│         └──► Migration (one-time)                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- **Docker Desktop** (or Docker Engine + Docker Compose)
- **Encryption keys generated** (if not already done)

### Start the HA Stack

**Linux/Mac:**
```bash
./docker/start-ha.sh
```

**Windows PowerShell:**
```powershell
.\docker\start-ha.ps1
```

**Windows CMD:**
```cmd
docker\start-ha.bat
```

The script will:
1. Build Docker images for all services
2. Start PostgreSQL database
3. Run database migrations (including ShedLock table V26)
4. Start 2 instances of admin-api and auth-api
5. Start HAProxy load balancers
6. Start Crypto API
7. Start Demo Device
8. Wait for all services to be healthy

### Access the Services

Once started, you can access:

- **Admin API** (via HAProxy): http://localhost:9080
- **Auth API** (via HAProxy): http://localhost:8080
- **Crypto API**: http://localhost:9090
- **Demo Device**: http://localhost:8083

**HAProxy Statistics Pages:**
- **Admin API Load Balancer Stats**: http://localhost:9081/stats
- **Auth API Load Balancer Stats**: http://localhost:8081/stats

See the [HAProxy Statistics](#haproxy-statistics) section below for detailed information about these monitoring pages.

**Direct Instance Access** (for debugging):
- Admin API Instance 1: `docker exec ezkey-admin-api-1 curl http://localhost:9080/actuator/health`
- Admin API Instance 2: `docker exec ezkey-admin-api-2 curl http://localhost:9080/actuator/health`

## Services

### PostgreSQL (postgres)
- **Container**: `ezkey-postgres-ha`
- **Port**: `5432` (internal only)
- **Database**: `ezkey_db`
- **Shared by**: All API instances (ShedLock table shared)

### Migration (migration)
- **Type**: One-time job
- **Purpose**: Runs Flyway database migrations (including V26__create_shedlock_table.sql)
- **Depends on**: PostgreSQL (healthy)

### Admin API Instances

#### Admin API Instance 1 (admin-api-1)
- **Container**: `ezkey-admin-api-1`
- **Internal Port**: `9080` (not exposed to host)
- **Instance ID**: `admin-api-1` (for logging)
- **Access**: Via HAProxy on port 9080

#### Admin API Instance 2 (admin-api-2)
- **Container**: `ezkey-admin-api-2`
- **Internal Port**: `9080` (not exposed to host)
- **Instance ID**: `admin-api-2` (for logging)
- **Access**: Via HAProxy on port 9080

### Auth API Instances

#### Auth API Instance 1 (auth-api-1)
- **Container**: `ezkey-auth-api-1`
- **Internal Port**: `8080` (not exposed to host)
- **Instance ID**: `auth-api-1` (for logging)
- **Access**: Via HAProxy on port 8080

#### Auth API Instance 2 (auth-api-2)
- **Container**: `ezkey-auth-api-2`
- **Internal Port**: `8080` (not exposed to host)
- **Instance ID**: `auth-api-2` (for logging)
- **Access**: Via HAProxy on port 8080

### HAProxy Load Balancers

#### HAProxy Admin (haproxy-admin)
- **Container**: `ezkey-haproxy-admin`
- **Port**: `9080` (exposed to host)
- **Stats Port**: `9081` (exposed to host)
- **Backends**: admin-api-1:9080, admin-api-2:9080
- **Algorithm**: Round-robin
- **Health Checks**: HTTP GET /actuator/health every 5 seconds

#### HAProxy Auth (haproxy-auth)
- **Container**: `ezkey-haproxy-auth`
- **Port**: `8080` (exposed to host)
- **Stats Port**: `8081` (exposed to host)
- **Backends**: auth-api-1:8080, auth-api-2:8080
- **Algorithm**: Round-robin
- **Health Checks**: HTTP GET /actuator/health every 5 seconds

### Crypto API (crypto-api)
- **Container**: `ezkey-crypto-api-ha`
- **Port**: `9090` (exposed to host)
- **Purpose**: Cryptographic operations service
- **Depends on**: PostgreSQL (healthy), Migration (completed)
- **Health Check**: http://localhost:9090/actuator/health

### Demo Device (demo-device)
- **Container**: `ezkey-demo-device-ha`
- **Port**: `8083` (exposed to host)
- **Purpose**: Demo device application for testing enrollment and authentication flows
- **Depends on**: HAProxy Auth (healthy)
- **Auth API URL**: Points to HAProxy Auth (`http://haproxy-auth:8080`) for load-balanced access
- **Volume**: `demo-device-data-ha` (persistent data storage)
- **Health Check**: http://localhost:8083/actuator/health

## Management Commands

### Start Services
```bash
# Linux/Mac
./docker/manage-ha.sh start

# Windows PowerShell
.\docker\manage-ha.ps1 start

# Windows CMD
docker\manage-ha.bat start
```

### Stop Services
```bash
# Linux/Mac
./docker/manage-ha.sh stop

# Windows
docker\manage-ha.bat stop
```

### View Logs
```bash
# All services
./docker/manage-ha.sh logs

# Specific service
./docker/manage-ha.sh logs admin-api-1
./docker/manage-ha.sh logs admin-api-2
./docker/manage-ha.sh logs haproxy-admin
```

### Check Status
```bash
# Linux/Mac
./docker/manage-ha.sh status

# Windows
docker\manage-ha.bat status
```

### Clean Everything
```bash
# Linux/Mac
./docker/manage-ha.sh clean

# Windows
docker\manage-ha.bat clean
```

**Warning**: The `clean` command removes all containers, networks, and volumes, including database data.

## Verifying HA Setup

### Check Both Instances Are Running

```bash
docker ps | grep ezkey-admin-api
```

You should see both `ezkey-admin-api-1` and `ezkey-admin-api-2` running.

### HAProxy Statistics

HAProxy provides real-time statistics pages for monitoring load balancer health and performance. These pages are accessible on dedicated ports and provide comprehensive visibility into the HA setup.

#### Access URLs

- **Admin API Load Balancer**: http://localhost:9081/stats
- **Auth API Load Balancer**: http://localhost:8081/stats

#### What You'll See

The stats pages display:

**Backend Servers:**
- List of all backend instances (admin-api-1, admin-api-2 or auth-api-1, auth-api-2)
- Current status: **UP** (healthy) or **DOWN** (unhealthy)
- Health check status and last check time

**Request Distribution:**
- **Sessions**: Number of active sessions per backend
- **Total requests**: Request count distributed across instances
- **Bytes**: Data transferred per backend

**Performance Metrics:**
- **Response time**: Average response time per backend
- **Last check**: Time since last health check
- **Check status**: Success/failure of health checks

**Error Tracking:**
- **4xx errors**: Client errors (bad requests, not found, etc.)
- **5xx errors**: Server errors (internal errors, timeouts, etc.)
- **Connection errors**: Failed connection attempts

**Load Balancing:**
- **Algorithm**: Round-robin (requests distributed evenly)
- **Session distribution**: Visual representation of load distribution

#### Using the Stats Pages

1. **Monitor Health**: Check that all backend servers show **UP** status
2. **Verify Load Balancing**: Make multiple requests and watch the session count increase evenly across instances
3. **Detect Issues**: Look for error counts or DOWN status indicating problems
4. **Performance Analysis**: Monitor response times to identify slow backends

**Note**: The stats pages are read-only in this configuration. For production, consider adding authentication (`stats auth`) and admin actions (`stats admin`) for server management.

### Verify Load Balancing

Make multiple requests and check HAProxy stats to see requests distributed between instances:

```bash
# Make 10 requests
for i in {1..10}; do
  curl -s http://localhost:9080/actuator/health > /dev/null
done

# Check stats - should see requests distributed between admin-api-1 and admin-api-2
curl http://localhost:9081/stats | grep admin-api
```

### Check ShedLock Locks

Query the ShedLock table directly:

```bash
docker exec ezkey-postgres-ha psql -U postgres -d ezkey_db -c \
  "SELECT name, locked_by, locked_at, lock_until, \
   CASE WHEN lock_until > NOW() THEN 'ACTIVE' ELSE 'EXPIRED' END as status \
   FROM ezkey_shedlock ORDER BY locked_at DESC;"
```

Expected output:
- One active lock per scheduled job (KEY_PROMOTION, KEY_ROTATION, etc.)
- `locked_by` column shows which instance holds the lock
- `lock_until` is in the future for active locks

## Testing ShedLock

### Run Functional Tests

The HA stack is designed to validate ShedLock distributed locking. Run the functional tests:

```bash
mvn test -pl ezkey-tests -Dtest=ShedLockDistributedTest
```

**Prerequisites:**
- HA stack must be running (`./docker/start-ha.sh`)
- Both instances must be healthy
- PostgreSQL container must be named `ezkey-postgres-ha`

**Tests included:**

1. **Test A: Exclusion Mutuelle**
   - Verifies that scheduled jobs execute only once with 2 instances
   - Validates that only one active lock exists per job
   - Checks that locks change between instances (no affinity)

2. **Test B: Vérification Locks DB**
   - Validates lock structure in PostgreSQL
   - Verifies lock expiration times
   - Checks lock metadata (name, locked_by, locked_at, lock_until)

3. **Test C: Failover**
   - Tests recovery when an instance crashes
   - Verifies lock expiration and re-acquisition
   - Validates that jobs continue after instance failure

### Monitor Scheduled Jobs

Watch logs from both instances to verify only one executes jobs:

```bash
# Terminal 1: Watch admin-api-1 logs
./docker/manage-ha.sh logs admin-api-1 | grep -i "KEY_PROMOTION\|KEY_ROTATION"

# Terminal 2: Watch admin-api-2 logs
./docker/manage-ha.sh logs admin-api-2 | grep -i "KEY_PROMOTION\|KEY_ROTATION"
```

Expected behavior:
- Only one instance logs job execution at a time
- Lock alternates between instances over time (round-robin via HAProxy)

### Query Lock History

Check which instances have held locks:

```bash
docker exec ezkey-postgres-ha psql -U postgres -d ezkey_db -c \
  "SELECT name, locked_by, COUNT(*) as times_held \
   FROM ezkey_shedlock \
   GROUP BY name, locked_by \
   ORDER BY name, times_held DESC;"
```

## Troubleshooting

### Instances Not Starting

1. **Check Docker is running:**
   ```bash
   docker info
   ```

2. **Check for port conflicts:**
   ```bash
   # Linux/Mac
   lsof -i :9080 -i :8080 -i :9081 -i :8081 -i :5432
   
   # Windows
   netstat -ano | findstr "9080 8080 9081 8081 5432"
   ```

3. **View service logs:**
   ```bash
   ./docker/manage-ha.sh logs
   ```

### HAProxy Not Routing Requests

1. **Check HAProxy configuration:**
   ```bash
   docker exec ezkey-haproxy-admin cat /usr/local/etc/haproxy/haproxy.cfg
   ```

2. **Check HAProxy stats:**
   - Open http://localhost:9081/stats (Admin API) or http://localhost:8081/stats (Auth API)
   - Verify both backend servers show "UP" status
   - Check for any error counts or connection issues

3. **Check backend health:**
   ```bash
   docker exec ezkey-admin-api-1 curl http://localhost:9080/actuator/health
   docker exec ezkey-admin-api-2 curl http://localhost:9080/actuator/health
   ```

### ShedLock Not Working

1. **Verify ShedLock table exists:**
   ```bash
   docker exec ezkey-postgres-ha psql -U postgres -d ezkey_db -c \
     "\d ezkey_shedlock"
   ```

2. **Check for locks:**
   ```bash
   docker exec ezkey-postgres-ha psql -U postgres -d ezkey_db -c \
     "SELECT * FROM ezkey_shedlock;"
   ```

3. **Verify both instances can access database:**
   ```bash
   docker exec ezkey-admin-api-1 ping postgres
   docker exec ezkey-admin-api-2 ping postgres
   ```

4. **Check instance logs for ShedLock errors:**
   ```bash
   ./docker/manage-ha.sh logs admin-api-1 | grep -i shedlock
   ./docker/manage-ha.sh logs admin-api-2 | grep -i shedlock
   ```

### Both Instances Executing Jobs

If both instances are executing the same job simultaneously:

1. **Check ShedLock configuration:**
   - Verify `ShedLockConfiguration.java` exists in admin-api
   - Verify `@EnableSchedulerLock` annotation is present
   - Verify `LockProvider` bean is configured

2. **Check database connectivity:**
   - Both instances must be able to write to `ezkey_shedlock` table
   - Verify no database connection issues in logs

3. **Check lock table:**
   ```bash
   docker exec ezkey-postgres-ha psql -U postgres -d ezkey_db -c \
     "SELECT name, locked_by, lock_until > NOW() as active FROM ezkey_shedlock;"
   ```

## Differences from Standard Stack

| Aspect | Standard Stack | HA Stack |
|--------|----------------|----------|
| **Instances** | 1 per API | 2 per API |
| **Ports** | Direct exposure (9080, 8080, 9090, 8083) | Via HAProxy (9080, 8080), Direct (9090, 8083) |
| **Crypto API** | Included | Included |
| **Demo Device** | Included | Included (uses HAProxy Auth) |
| **Load Balancing** | None | HAProxy round-robin |
| **Health Checks** | Docker healthchecks | HAProxy + Docker healthchecks |
| **Container Names** | `ezkey-*` | `ezkey-*-ha` or `ezkey-*-1/2` |
| **Network** | `ezkey-network` | `ezkey-network-ha` |
| **Volumes** | `*-data` | `*-data-ha` |
| **PostgreSQL** | `ezkey-postgres` | `ezkey-postgres-ha` |

## Environment Variables

Same as standard stack, with additional instance identification:

- `EZKEY_INSTANCE_ID`: Instance identifier for logging (admin-api-1, admin-api-2, etc.)

## Data Persistence

- **Database**: PostgreSQL data persisted in `postgres-data-ha` volume
- **Encryption Keys**: Shared via `encryption-secrets-ha` volume (all API instances use same keys)
- **Demo Device Data**: Persisted in `demo-device-data-ha` volume

## Production Considerations

⚠️ **This HA setup is designed for development and testing.**

For production HA deployment, consider:

1. **Orchestration**: Use Kubernetes or Docker Swarm for better instance management
2. **Database**: PostgreSQL replication (primary/replica) for true HA
3. **Load Balancer**: Production-grade HAProxy/Nginx with TLS termination
4. **Monitoring**: Prometheus/Grafana for metrics and alerting
5. **Logging**: Centralized logging (ELK stack, Loki)
6. **Secrets**: Use secrets management (Docker secrets, Kubernetes secrets)
7. **Resource Limits**: Set CPU/memory limits per container
8. **Health Checks**: More aggressive health checks and auto-restart policies

## Related Documentation

- [Main Docker README](README.md) - Standard Docker stack documentation
- [HA Job Coordination](../docs/HA-JOB-COORDINATION.md) - ShedLock strategy and rationale
- [ShedLock Implementation Plan](../.cursor/plans/shedlock_ha_schedulers_2ffda450.plan.md) - Implementation details

## Support

For issues or questions:
- Check the troubleshooting section above
- Review service logs: `./docker/manage-ha.sh logs`
- Check HAProxy stats: 
  - Admin API: http://localhost:9081/stats
  - Auth API: http://localhost:8081/stats
- Review the main project documentation
