/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuditLogDto
 * Description: Response DTO for audit log entries
 */

package org.ezkey.admin.dto.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Response DTO for audit log entries.
 * <p>
 * This DTO represents an audit log entry returned by the admin API
 * for security monitoring and compliance reporting.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Audit log entry for security and compliance tracking")
public class AuditLogDto {

    @Schema(description = "Unique audit log ID", example = "123")
    private Long auditLogId;

    @Schema(description = "Event type classification", example = "AUTH_ATTEMPT")
    private String eventType;

    @Schema(description = "Specific action performed", example = "CREATE")
    private String eventAction;

    @Schema(description = "Event outcome status", example = "SUCCESS")
    private String eventStatus;

    @Schema(description = "API that generated the event", example = "ADMIN_API")
    private String apiName;

    @Schema(description = "Endpoint path accessed", example = "/api/v1/auth-attempts")
    private String endpointPath;

    @Schema(description = "HTTP method used", example = "POST")
    private String httpMethod;

    @Schema(description = "Client IP address", example = "192.168.1.100")
    private String ipAddress;

    @Schema(description = "User agent string")
    private String userAgent;

    @Schema(description = "Admin ID if applicable", example = "1")
    private Integer adminId;

    @Schema(description = "Integration ID if applicable", example = "1")
    private Integer integrationId;

    @Schema(description = "Enrollment ID if applicable", example = "1")
    private Integer enrollmentId;

    @Schema(description = "Auth attempt ID if applicable", example = "1")
    private Integer authAttemptId;

    @Schema(description = "Tenant ID if applicable", example = "1")
    private Integer tenantId;

    @Schema(description = "Additional event details in text format")
    private String eventDetails;

    @Schema(description = "Error message if event failed or errored")
    private String errorMessage;

    @Schema(description = "Timestamp when the event occurred")
    private LocalDateTime createdAt;

    // Getters and Setters

    public Long getAuditLogId() {
        return auditLogId;
    }

    public void setAuditLogId(Long auditLogId) {
        this.auditLogId = auditLogId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventAction() {
        return eventAction;
    }

    public void setEventAction(String eventAction) {
        this.eventAction = eventAction;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public void setEndpointPath(String endpointPath) {
        this.endpointPath = endpointPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Integer getAdminId() {
        return adminId;
    }

    public void setAdminId(Integer adminId) {
        this.adminId = adminId;
    }

    public Integer getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(Integer integrationId) {
        this.integrationId = integrationId;
    }

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }

    public String getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(String eventDetails) {
        this.eventDetails = eventDetails;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
