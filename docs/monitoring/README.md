# Ezkey Grafana Dashboard Setup Guide

This guide provides step-by-step instructions for setting up the Ezkey Production Dashboard in Grafana on Windows.

## Prerequisites

- Windows 10/11 machine
- PostgreSQL database running on localhost:5432 with ezkey database
- Administrative privileges to install software

## Database Requirements

The dashboard connects to a PostgreSQL database with the following configuration:
- **Host**: localhost
- **Port**: 5432
- **Database**: ezkey_db
- **Username**: postgres
- **Password**: ezkey

Ensure your Ezkey database is running with these exact connection parameters.

## Installation Steps

### 1. Download and Install Grafana

1. Visit the [Grafana download page](https://grafana.com/grafana/download?platform=windows)
2. Download the Windows installer (.msi file) for the latest stable version
3. Run the installer as Administrator
4. Follow the installation wizard with default settings
5. Grafana will be installed to `C:\Program Files\GrafanaLabs\grafana`

### 2. Start Grafana Service

**Option A: Using Windows Services**
1. Press `Win + R`, type `services.msc`, and press Enter
2. Find "Grafana" in the services list
3. Right-click and select "Start"

**Option B: Using Command Line**
1. Open Command Prompt as Administrator
2. Navigate to Grafana directory:
   ```cmd
   cd "C:\Program Files\GrafanaLabs\grafana\bin"
   ```
3. Start Grafana:
   ```cmd
   grafana-server.exe
   ```

### 3. Access Grafana Web Interface

1. Open your web browser
2. Navigate to: `http://localhost:3000`
3. Login with default credentials:
   - **Username**: admin
   - **Password**: admin
4. You'll be prompted to change the password on first login

### 4. Configure PostgreSQL Data Source

1. In Grafana, click the gear icon (⚙️) in the left sidebar
2. Select "Data sources"
3. Click "Add data source"
4. Select "PostgreSQL" from the list
5. Configure the connection:
   - **Name**: `ezkey-postgres`
   - **Host**: `localhost:5432`
   - **Database**: `ezkey_db`
   - **User**: `postgres`
   - **Password**: `ezkey`
   - **SSL Mode**: `disable` (for local development)
6. Click "Save & test" to verify the connection

### 5. Import the Ezkey Dashboard

1. In Grafana, click the "+" icon in the left sidebar
2. Select "Import"
3. Choose one of these options:

**Option A: Upload JSON file**
1. Click "Upload JSON file"
2. Browse and select the `ezkey-production-dashboard.json` file from this directory
3. Click "Import"

**Option B: Copy/paste JSON**
1. Open the `ezkey-production-dashboard.json` file in a text editor
2. Copy the entire content
3. Paste it into the "Import via panel json" text area
4. Click "Load"

4. Configure the dashboard import:
   - **Name**: Keep as "Ezkey Production Dashboard" or customize
   - **Folder**: Select a folder or leave as "General"
   - **Data source**: Select "ezkey-postgres" (the one you configured in step 4)
5. Click "Import"

## Dashboard Overview

The Ezkey Production Dashboard provides comprehensive monitoring for your MFA system:

### Key Metrics (Top Row)
- **Total Integrations**: Count of all integrations in the system
- **Active Integrations**: Count of integrations that are currently active
- **Total Enrollments**: Count of all device enrollments
- **Active Enrollments**: Count of enrollments that are active and usable
- **Auth Success Rate (24h)**: Percentage of successful authentications in the last 24 hours
- **Expired Attempts**: Count of authentication attempts that expired without response
- **Avg Response Time (24h)**: Average time for users to respond to auth requests
- **Auth Attempts (24h)**: Total authentication attempts in the last 24 hours

### Visualization Panels

1. **Authentication Attempts (Last 24h)** - Time series showing auth attempt volume
2. **Enrollment Status Distribution** - Pie chart showing enrollment health
3. **Enrollments by Integration** - Table showing enrollment distribution across integrations
4. **Enrollment Creation Trend (30 days)** - Time series of new enrollments
5. **Auth Attempt Status Over Time (24h)** - Stacked time series of attempt outcomes
6. **Authentication Performance by Enrollment (24h)** - Detailed table with success rates and response times

### Dashboard Features
- **Auto-refresh**: Updates every 30 seconds
- **Time range**: Default view shows last 24 hours
- **Responsive design**: Works on desktop and mobile
- **Dark theme**: Professional appearance for operations centers

## Troubleshooting

### Connection Issues

**"Database connection failed"**
1. Verify PostgreSQL is running: Check Windows Services for "postgresql" service
2. Verify database exists: Connect with pgAdmin or psql and confirm `ezkey_db` exists
3. Check connection parameters: Ensure host, port, username, and password are correct
4. Test connectivity: Try connecting to the database using a PostgreSQL client

**"Plugin not found"**
1. Restart Grafana service
2. Check if PostgreSQL data source plugin is installed (it's built-in)

### Dashboard Issues

**"No data" or empty panels**
1. Verify data exists in the database:
   ```sql
   SELECT COUNT(*) FROM ezkey_integration;
   SELECT COUNT(*) FROM ezkey_enrollment;
   SELECT COUNT(*) FROM ezkey_auth_attempt;
   ```
2. Check the time range - adjust if your data is outside the default 24-hour window
3. Verify the data source is correctly configured and selected

**"Query error" messages**
1. Check that all required tables exist in your database
2. Verify the database schema matches the expected structure
3. Ensure the database user has SELECT permissions on all ezkey tables

### Performance Optimization

**Slow loading dashboards**
1. Add database indexes if missing:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_auth_attempt_created_at ON ezkey_auth_attempt(created_at);
   CREATE INDEX IF NOT EXISTS idx_enrollment_integration_id ON ezkey_enrollment(integration_id);
   CREATE INDEX IF NOT EXISTS idx_enrollment_active ON ezkey_enrollment(enrollment_active);
   ```

**High CPU usage**
1. Increase dashboard refresh interval (currently 30s)
2. Reduce time range for heavy queries
3. Consider using materialized views for complex aggregations

## Customization

### Modifying Panels
1. Click the panel title and select "Edit"
2. Modify the SQL query as needed
3. Adjust visualization settings
4. Save the panel

### Adding New Panels
1. Click "Add panel" button
2. Select visualization type
3. Write custom SQL queries against ezkey tables
4. Configure display options

### Alerting (Optional)
1. Configure notification channels (email, Slack, etc.)
2. Set up alerts on critical metrics like success rate thresholds
3. Configure alert rules for high failure rates or system issues

## Production Recommendations

1. **Security**: Change default Grafana admin password
2. **Access Control**: Set up proper user roles and permissions
3. **Backup**: Export dashboard configuration regularly
4. **Monitoring**: Set up alerts for critical thresholds
5. **Performance**: Monitor query performance and optimize as needed

## Sample SQL Queries

For custom panels or troubleshooting, here are some useful queries:

```sql
-- Integration overview
SELECT 
  integration_id,
  integration_active,
  created_at 
FROM ezkey_integration;

-- Enrollment health check
SELECT 
  COUNT(*) as total,
  COUNT(CASE WHEN enrollment_active = true THEN 1 END) as active,
  COUNT(CASE WHEN enrollment_valid = true THEN 1 END) as valid
FROM ezkey_enrollment;

-- Authentication success rate (last hour)
SELECT 
  COUNT(*) as total_attempts,
  COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) as successful,
  ROUND(COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) * 100.0 / COUNT(*), 2) as success_rate
FROM ezkey_auth_attempt 
WHERE created_at >= NOW() - INTERVAL '1 hour';

-- Top active enrollments by auth volume
SELECT 
  e.enrollment_name,
  COUNT(a.auth_attempt_id) as attempts_today
FROM ezkey_enrollment e
LEFT JOIN ezkey_auth_attempt a ON e.enrollment_id = a.enrollment_id 
  AND a.created_at >= CURRENT_DATE
GROUP BY e.enrollment_id, e.enrollment_name
ORDER BY attempts_today DESC
LIMIT 10;
```

## Support

For issues with the dashboard:
1. Check the Grafana logs in `C:\Program Files\GrafanaLabs\grafana\data\log\`
2. Verify database connectivity and data integrity
3. Consult the [Grafana documentation](https://grafana.com/docs/)
4. Check Ezkey project documentation for database schema changes