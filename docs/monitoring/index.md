# Ezkey Grafana Dashboard

This folder contains a complete Grafana dashboard solution for monitoring Ezkey MFA applications in production environments.

## 📁 Files Overview

| File | Description |
|------|-------------|
| **ezkey-production-dashboard.json** | Main Grafana dashboard configuration for import |
| **README.md** | Comprehensive setup guide with detailed instructions |
| **QUICK_SETUP.md** | 5-minute quick setup guide for rapid deployment |
| **example-queries.sql** | Collection of useful SQL queries for monitoring and analysis |

## 🚀 Quick Start

1. **Setup Database**: Ensure PostgreSQL is running with ezkey_db on localhost:5432
2. **Install Grafana**: Download and install Grafana for Windows
3. **Import Dashboard**: Follow [QUICK_SETUP.md](QUICK_SETUP.md) for rapid deployment
4. **Monitor**: Access your production dashboard at http://localhost:3000

## 📊 Dashboard Features

### Key Metrics Monitored
- **Integrations**: Total count, active/inactive status
- **Enrollments**: Distribution per integration, health status, creation trends
- **Authentication Attempts**: Success rates, volume analysis, performance metrics
- **System Health**: Response times, expired attempts, anomaly detection

### Visualization Types
- ⏱️ **Time Series**: Authentication attempts over time, trends
- 📊 **Tables**: Detailed breakdowns by integration and enrollment
- 🥧 **Pie Charts**: Status distributions and health overviews
- 📈 **Statistics**: Key performance indicators and success rates

### Operational Value
- **Production Monitoring**: Real-time visibility into MFA system performance
- **Issue Detection**: Quick identification of authentication problems
- **Capacity Planning**: Understanding usage patterns and growth
- **Security Analysis**: Monitoring for unusual authentication patterns

## 🎯 Target Use Cases

Perfect for:
- **Production Operations Teams** monitoring Ezkey-integrated applications
- **System Administrators** tracking MFA system health
- **Security Teams** analyzing authentication patterns
- **Development Teams** understanding system usage and performance

## 📋 Database Requirements

The dashboard works with the standard Ezkey database schema:
- `ezkey_integration` - Application integrations
- `ezkey_enrollment` - Device enrollments  
- `ezkey_auth_attempt` - Authentication attempts
- `ezkey_integration_i18n` - Internationalization data

## 🔧 Customization

All SQL queries are easily customizable:
- Modify time ranges and aggregation periods
- Add new metrics and visualizations
- Integrate with existing monitoring infrastructure
- Create custom alerts and notifications

## 📚 Documentation

- **Complete Setup**: See [README.md](README.md) for detailed installation instructions
- **Quick Deployment**: Use [QUICK_SETUP.md](QUICK_SETUP.md) for fast setup
- **Custom Queries**: Reference [example-queries.sql](example-queries.sql) for additional monitoring

---

**Database Connection**: `postgresql://postgres:ezkey@localhost:5432/ezkey_db`

Built for Ezkey v1.0+ | Compatible with Grafana 10.0+ | Optimized for Production Use