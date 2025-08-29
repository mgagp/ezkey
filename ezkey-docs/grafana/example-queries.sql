-- Ezkey Production Monitoring SQL Queries
-- Use these queries in Grafana panels or for manual database analysis

-- =============================================================================
-- INTEGRATION MONITORING
-- =============================================================================

-- Total and active integrations overview
SELECT 
    COUNT(*) as total_integrations,
    COUNT(CASE WHEN integration_active = true THEN 1 END) as active_integrations,
    COUNT(CASE WHEN integration_active = false THEN 1 END) as inactive_integrations
FROM ezkey_integration;

-- Integration creation trend (daily)
SELECT 
    DATE(created_at) as date,
    COUNT(*) as new_integrations
FROM ezkey_integration
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY date;

-- =============================================================================
-- ENROLLMENT MONITORING  
-- =============================================================================

-- Enrollment health overview
SELECT 
    COUNT(*) as total_enrollments,
    COUNT(CASE WHEN enrollment_active = true THEN 1 END) as active_enrollments,
    COUNT(CASE WHEN enrollment_valid = true THEN 1 END) as valid_enrollments,
    COUNT(CASE WHEN enrollment_verified = true THEN 1 END) as verified_enrollments,
    COUNT(CASE WHEN enrollment_read = true THEN 1 END) as read_enrollments,
    ROUND(COUNT(CASE WHEN enrollment_active = true THEN 1 END) * 100.0 / COUNT(*), 2) as active_percentage
FROM ezkey_enrollment;

-- Enrollments by integration with detailed status
SELECT 
    i.integration_id,
    COALESCE(i18n.integration_i18n_name, 'Integration ' || i.integration_id) as integration_name,
    COUNT(e.enrollment_id) as total_enrollments,
    COUNT(CASE WHEN e.enrollment_active = true THEN 1 END) as active_enrollments,
    COUNT(CASE WHEN e.enrollment_valid = true THEN 1 END) as valid_enrollments,
    COUNT(CASE WHEN e.enrollment_verified = true THEN 1 END) as verified_enrollments,
    CASE 
        WHEN COUNT(e.enrollment_id) = 0 THEN 0
        ELSE ROUND(COUNT(CASE WHEN e.enrollment_active = true THEN 1 END) * 100.0 / COUNT(e.enrollment_id), 1)
    END as active_percentage
FROM ezkey_integration i
LEFT JOIN ezkey_enrollment e ON i.integration_id = e.integration_id
LEFT JOIN ezkey_integration_i18n i18n ON i.integration_id = i18n.integration_id 
    AND i18n.integration_i18n_lang = 'en'
WHERE i.integration_active = true
GROUP BY i.integration_id, integration_name
ORDER BY total_enrollments DESC;

-- Enrollment creation trend (daily for last 30 days)
SELECT 
    DATE(created_at) as date,
    COUNT(*) as new_enrollments
FROM ezkey_enrollment
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY date;

-- Problem enrollments (not active but valid)
SELECT 
    e.enrollment_id,
    e.enrollment_name,
    COALESCE(i18n.integration_i18n_name, 'Integration ' || i.integration_id) as integration_name,
    e.enrollment_read,
    e.enrollment_verified,
    e.enrollment_valid,
    e.enrollment_active,
    e.created_at
FROM ezkey_enrollment e
INNER JOIN ezkey_integration i ON e.integration_id = i.integration_id
LEFT JOIN ezkey_integration_i18n i18n ON i.integration_id = i18n.integration_id 
    AND i18n.integration_i18n_lang = 'en'
WHERE e.enrollment_valid = true AND e.enrollment_active = false
ORDER BY e.created_at DESC;

-- =============================================================================
-- AUTHENTICATION ATTEMPT MONITORING
-- =============================================================================

-- Authentication summary for different time periods
-- Last hour
SELECT 
    'Last Hour' as period,
    COUNT(*) as total_attempts,
    COUNT(CASE WHEN auth_attempt_read = true THEN 1 END) as read_attempts,
    COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END) as responded_attempts,
    COUNT(CASE WHEN auth_attempt_valid = true THEN 1 END) as valid_attempts,
    COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) as accepted_attempts,
    COUNT(CASE WHEN expires_at < NOW() AND auth_attempt_responded = false THEN 1 END) as expired_attempts,
    CASE 
        WHEN COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END) = 0 THEN 0
        ELSE ROUND(COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) * 100.0 / COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END), 2)
    END as success_rate_percent
FROM ezkey_auth_attempt
WHERE created_at >= NOW() - INTERVAL '1 hour'

UNION ALL

-- Last 24 hours
SELECT 
    'Last 24 Hours' as period,
    COUNT(*) as total_attempts,
    COUNT(CASE WHEN auth_attempt_read = true THEN 1 END) as read_attempts,
    COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END) as responded_attempts,
    COUNT(CASE WHEN auth_attempt_valid = true THEN 1 END) as valid_attempts,
    COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) as accepted_attempts,
    COUNT(CASE WHEN expires_at < NOW() AND auth_attempt_responded = false THEN 1 END) as expired_attempts,
    CASE 
        WHEN COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END) = 0 THEN 0
        ELSE ROUND(COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) * 100.0 / COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END), 2)
    END as success_rate_percent
FROM ezkey_auth_attempt
WHERE created_at >= NOW() - INTERVAL '24 hours'

UNION ALL

-- Last 7 days
SELECT 
    'Last 7 Days' as period,
    COUNT(*) as total_attempts,
    COUNT(CASE WHEN auth_attempt_read = true THEN 1 END) as read_attempts,
    COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END) as responded_attempts,
    COUNT(CASE WHEN auth_attempt_valid = true THEN 1 END) as valid_attempts,
    COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) as accepted_attempts,
    COUNT(CASE WHEN expires_at < NOW() AND auth_attempt_responded = false THEN 1 END) as expired_attempts,
    CASE 
        WHEN COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END) = 0 THEN 0
        ELSE ROUND(COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) * 100.0 / COUNT(CASE WHEN auth_attempt_responded = true THEN 1 END), 2)
    END as success_rate_percent
FROM ezkey_auth_attempt
WHERE created_at >= NOW() - INTERVAL '7 days';

-- Authentication attempts by hour (for time series visualization)
SELECT 
    date_trunc('hour', created_at) as time,
    COUNT(*) as total_attempts,
    COUNT(CASE WHEN auth_attempt_accepted = true THEN 1 END) as successful_attempts,
    COUNT(CASE WHEN auth_attempt_responded = true AND auth_attempt_accepted = false THEN 1 END) as denied_attempts,
    COUNT(CASE WHEN auth_attempt_responded = false AND expires_at < NOW() THEN 1 END) as expired_attempts,
    COUNT(CASE WHEN auth_attempt_responded = false AND expires_at >= NOW() THEN 1 END) as pending_attempts
FROM ezkey_auth_attempt
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY date_trunc('hour', created_at)
ORDER BY time;

-- Authentication performance by enrollment (top 20 most active)
SELECT 
    e.enrollment_name,
    COALESCE(i18n.integration_i18n_name, 'Integration ' || i.integration_id) as integration_name,
    COUNT(a.auth_attempt_id) as total_attempts_24h,
    COUNT(CASE WHEN a.auth_attempt_accepted = true THEN 1 END) as successful_attempts,
    COUNT(CASE WHEN a.auth_attempt_responded = true AND a.auth_attempt_accepted = false THEN 1 END) as denied_attempts,
    COUNT(CASE WHEN a.auth_attempt_responded = false AND a.expires_at < NOW() THEN 1 END) as expired_attempts,
    CASE 
        WHEN COUNT(CASE WHEN a.auth_attempt_responded = true THEN 1 END) = 0 THEN 0
        ELSE ROUND(COUNT(CASE WHEN a.auth_attempt_accepted = true THEN 1 END) * 100.0 / COUNT(CASE WHEN a.auth_attempt_responded = true THEN 1 END), 1)
    END as success_rate_percent,
    CASE 
        WHEN COUNT(CASE WHEN a.auth_attempt_responded = true THEN 1 END) = 0 THEN 0
        ELSE ROUND(AVG(CASE WHEN a.auth_attempt_responded = true THEN EXTRACT(EPOCH FROM (a.expires_at - a.created_at)) END), 1)
    END as avg_response_time_seconds
FROM ezkey_enrollment e
INNER JOIN ezkey_integration i ON e.integration_id = i.integration_id
LEFT JOIN ezkey_integration_i18n i18n ON i.integration_id = i18n.integration_id 
    AND i18n.integration_i18n_lang = 'en'
LEFT JOIN ezkey_auth_attempt a ON e.enrollment_id = a.enrollment_id 
    AND a.created_at >= NOW() - INTERVAL '24 hours'
WHERE e.enrollment_active = true
GROUP BY e.enrollment_id, e.enrollment_name, i.integration_id, integration_name
HAVING COUNT(a.auth_attempt_id) > 0
ORDER BY total_attempts_24h DESC
LIMIT 20;

-- =============================================================================
-- PERFORMANCE AND HEALTH MONITORING
-- =============================================================================

-- Response time analysis
SELECT 
    CASE 
        WHEN EXTRACT(EPOCH FROM (expires_at - created_at)) <= 10 THEN '0-10s'
        WHEN EXTRACT(EPOCH FROM (expires_at - created_at)) <= 30 THEN '11-30s'
        WHEN EXTRACT(EPOCH FROM (expires_at - created_at)) <= 60 THEN '31-60s'
        WHEN EXTRACT(EPOCH FROM (expires_at - created_at)) <= 120 THEN '61-120s'
        ELSE '120s+'
    END as response_time_bucket,
    COUNT(*) as attempt_count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM ezkey_auth_attempt
WHERE auth_attempt_responded = true 
    AND created_at >= NOW() - INTERVAL '24 hours'
GROUP BY response_time_bucket
ORDER BY MIN(EXTRACT(EPOCH FROM (expires_at - created_at)));

-- Current system load (active/pending attempts)
SELECT 
    COUNT(CASE WHEN auth_attempt_responded = false AND expires_at >= NOW() THEN 1 END) as pending_attempts,
    COUNT(CASE WHEN auth_attempt_responded = false AND expires_at < NOW() THEN 1 END) as expired_attempts,
    COUNT(CASE WHEN auth_attempt_read = false THEN 1 END) as unread_attempts,
    COUNT(CASE WHEN created_at >= NOW() - INTERVAL '5 minutes' THEN 1 END) as attempts_last_5min
FROM ezkey_auth_attempt;

-- =============================================================================
-- SECURITY AND ANOMALY DETECTION
-- =============================================================================

-- Detect enrollments with unusual authentication patterns
SELECT 
    e.enrollment_name,
    COALESCE(i18n.integration_i18n_name, 'Integration ' || i.integration_id) as integration_name,
    COUNT(a.auth_attempt_id) as attempts_today,
    COUNT(CASE WHEN a.auth_attempt_accepted = false AND a.auth_attempt_responded = true THEN 1 END) as denials_today,
    CASE 
        WHEN COUNT(a.auth_attempt_id) = 0 THEN 0
        ELSE ROUND(COUNT(CASE WHEN a.auth_attempt_accepted = false AND a.auth_attempt_responded = true THEN 1 END) * 100.0 / COUNT(a.auth_attempt_id), 1)
    END as denial_rate_percent
FROM ezkey_enrollment e
INNER JOIN ezkey_integration i ON e.integration_id = i.integration_id
LEFT JOIN ezkey_integration_i18n i18n ON i.integration_id = i18n.integration_id 
    AND i18n.integration_i18n_lang = 'en'
LEFT JOIN ezkey_auth_attempt a ON e.enrollment_id = a.enrollment_id 
    AND a.created_at >= CURRENT_DATE
WHERE e.enrollment_active = true
GROUP BY e.enrollment_id, e.enrollment_name, i.integration_id, integration_name
HAVING COUNT(a.auth_attempt_id) > 10 OR 
       (COUNT(a.auth_attempt_id) > 0 AND 
        COUNT(CASE WHEN a.auth_attempt_accepted = false AND a.auth_attempt_responded = true THEN 1 END) * 100.0 / COUNT(a.auth_attempt_id) > 50)
ORDER BY denial_rate_percent DESC, attempts_today DESC;

-- =============================================================================
-- CLEANUP AND MAINTENANCE QUERIES
-- =============================================================================

-- Old expired attempts that can be cleaned up
SELECT 
    COUNT(*) as expired_attempts_to_cleanup,
    MIN(created_at) as oldest_expired,
    MAX(created_at) as newest_expired
FROM ezkey_auth_attempt
WHERE expires_at < NOW() - INTERVAL '24 hours' 
    AND auth_attempt_responded = false;

-- Database size and growth monitoring
SELECT 
    schemaname,
    tablename,
    attname as column_name,
    n_distinct,
    correlation
FROM pg_stats 
WHERE schemaname = 'public' 
    AND tablename IN ('ezkey_integration', 'ezkey_enrollment', 'ezkey_auth_attempt', 'ezkey_integration_i18n')
ORDER BY tablename, attname;