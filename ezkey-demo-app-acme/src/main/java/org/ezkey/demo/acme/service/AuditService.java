/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditService
 * Description: Business service for querying audit logs via Admin API
 */

package org.ezkey.demo.acme.service;

import org.ezkey.demo.acme.dto.AuditLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Business service for managing audit logs via Admin API.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final WebClient ezkeyAdminApiClient;

    public AuditService(@Qualifier("ezkeyAdminApiClient") WebClient ezkeyAdminApiClient) {
        this.ezkeyAdminApiClient = ezkeyAdminApiClient;
    }

    /**
     * Retrieves audit logs with pagination.
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @return List of audit logs with pagination info
     */
    public AuditLogsResult getAuditLogs(int page, int size) {
        logger.debug("Retrieving audit logs - page: {}, size: {}", page, size);

        try {
            Map<String, Object> response = ezkeyAdminApiClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/audit-logs")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                return new AuditLogsResult(Collections.emptyList(), 0, 0);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            
            Integer totalElements = (Integer) response.get("totalElements");
            Integer totalPages = (Integer) response.get("totalPages");
            
            // Simple mapping for demo purposes
            List<AuditLogDto> auditLogs = content.stream()
                    .map(this::mapToAuditLogDto)
                    .toList();

            return new AuditLogsResult(auditLogs, 
                    totalElements != null ? totalElements : 0,
                    totalPages != null ? totalPages : 0);
        } catch (Exception e) {
            logger.error("Failed to retrieve audit logs: {}", e.getMessage(), e);
            return new AuditLogsResult(Collections.emptyList(), 0, 0);
        }
    }

    public static class AuditLogsResult {
        private final List<AuditLogDto> content;
        private final int totalElements;
        private final int totalPages;

        public AuditLogsResult(List<AuditLogDto> content, int totalElements, int totalPages) {
            this.content = content;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }

        public List<AuditLogDto> getContent() {
            return content;
        }

        public int getTotalElements() {
            return totalElements;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    private AuditLogDto mapToAuditLogDto(Map<String, Object> map) {
        AuditLogDto dto = new AuditLogDto();
        dto.setAuditLogId(getLong(map, "auditLogId"));
        dto.setEventType((String) map.get("eventType"));
        dto.setEventAction((String) map.get("eventAction"));
        dto.setEventStatus((String) map.get("eventStatus"));
        dto.setApiName((String) map.get("apiName"));
        dto.setEndpointPath((String) map.get("endpointPath"));
        dto.setHttpMethod((String) map.get("httpMethod"));
        dto.setIpAddress((String) map.get("ipAddress"));
        dto.setEventDetails((String) map.get("eventDetails"));
        dto.setErrorMessage((String) map.get("errorMessage"));
        return dto;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
