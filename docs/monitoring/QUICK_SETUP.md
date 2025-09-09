# Quick Setup Guide - Ezkey Grafana Dashboard

## Prerequisites ✅
- [ ] PostgreSQL running on `localhost:5432` 
- [ ] Ezkey database `ezkey_db` with username `postgres` and password `ezkey`
- [ ] Windows 10/11 with admin privileges

## 5-Minute Setup

### 1. Install Grafana (2 minutes)
1. Download: https://grafana.com/grafana/download?platform=windows
2. Run the .msi installer as Administrator
3. Use default settings throughout installation

### 2. Start Grafana (30 seconds)
1. Press `Win+R`, type `services.msc`, Enter
2. Find "Grafana" service → Right-click → Start
3. Open browser to: http://localhost:3000
4. Login: admin/admin (change password when prompted)

### 3. Add PostgreSQL Data Source (1 minute)
1. Click gear icon ⚙️ → Data sources → Add data source
2. Select "PostgreSQL"
3. Configure:
   - **Name**: `ezkey-postgres`
   - **Host**: `localhost:5432`
   - **Database**: `ezkey_db`
   - **User**: `postgres`  
   - **Password**: `ezkey`
   - **SSL Mode**: disable
4. Click "Save & test" ✅

### 4. Import Dashboard (1 minute)
1. Click "+" icon → Import
2. Upload the `ezkey-production-dashboard.json` file from this folder
3. Select `ezkey-postgres` as the data source
4. Click "Import"

### 5. Verify (30 seconds)
✅ Dashboard loads with data  
✅ All panels show metrics  
✅ No "No data" messages  

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Database connection failed" | Check PostgreSQL service is running |
| "No data" in panels | Verify ezkey tables have data: `SELECT COUNT(*) FROM ezkey_integration;` |
| Grafana won't start | Run as Administrator, check Windows Firewall |
| Import fails | Ensure data source name is exactly `ezkey-postgres` |

## What You'll See

The dashboard provides:
- **8 key metrics** (integrations, enrollments, auth success rate, etc.)
- **6 detailed panels** (time series, tables, pie charts)
- **Auto-refresh** every 30 seconds
- **Professional dark theme**

Perfect for production monitoring and operations centers! 🚀

---
For detailed setup instructions, see the full [README.md](README.md) in this folder.