/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuditLogController
 * Description: REST controller for audit log query API.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.EventTypeFamily;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.ArchiveConfirmArchivedRequest;
import org.ezkey.audit.dto.ArchiveConfirmArchivedResult;
import org.ezkey.audit.dto.ArchiveEligibilityResult;
import org.ezkey.audit.dto.ArchiveSealRequest;
import org.ezkey.audit.dto.ArchiveSealResult;
import org.ezkey.audit.dto.AuditChainCheckpointResponseDto;
import org.ezkey.audit.dto.AuditChainIncidentResponseDto;
import org.ezkey.audit.dto.AuditLogContextResponseDto;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.ezkey.audit.dto.CheckpointType;
import org.ezkey.audit.dto.DeclareAuditChainIncidentRequest;
import org.ezkey.audit.dto.GapDeclarationRequest;
import org.ezkey.audit.dto.GapDeclarationResult;
import org.ezkey.audit.integrity.AuditChainCheckpointService;
import org.ezkey.audit.integrity.AuditChainIncidentService;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.ezkey.audit.mapper.AuditChainCheckpointMapper;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for audit log query API with tenant-scoped visibility.
 *
 * <p>This controller provides REST endpoints for querying audit logs for security monitoring,
 * compliance reporting, and forensic analysis. Supports filtering, pagination, and tenant-based
 * visibility enforcement for proper multi-tenant isolation.
 *
 * <p><b>Tenant Visibility Rules:</b>
 *
 * <ul>
 *   <li><b>Global Admin:</b> Sees all audit logs across all tenants. May optionally filter by a
 *       specific tenant using the {@code tenantId} query parameter.
 *   <li><b>Tenant Admin:</b> Sees only audit logs where {@code tenant_id} matches their own tenant.
 *       Audit entries with {@code tenant_id = NULL} (system-level events like encryption key
 *       operations) are excluded from Tenant Admin results.
 * </ul>
 *
 * <p><b>Admin API Endpoints:</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/audit-logs</b> - Query audit logs with filters, tenant scoping, and
 *       pagination
 * </ul>
 *
 * <p><b>Usage Context:</b> Part of the admin-api for security monitoring and SOC2 compliance
 * reporting. Provides comprehensive audit trail access with proper tenant isolation.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(
    name = "Audit Logs",
    description = "Audit log query and reporting API for security monitoring and compliance")
public class AuditLogController {

  private final AuditLogService auditLogService;
  private final AuditLogMapper auditLogMapper;
  private final AuditChainCheckpointService auditChainCheckpointService;
  private final AuditChainCheckpointMapper auditChainCheckpointMapper;
  private final AuditIntegrityService auditIntegrityService;
  private final AuditChainVerificationService auditChainVerificationService;
  private final AuditLifecycleService auditLifecycleService;
  private final AuditChainIncidentService auditChainIncidentService;
  private final EzkeyAdminRepository adminRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final IntegrationRepository integrationRepository;
  private final TenantRepository tenantRepository;

  /**
   * Constructs the audit log controller with required dependencies.
   *
   * @param auditLogService the audit log service
   * @param auditLogMapper the MapStruct mapper for entity-DTO conversions
   * @param auditChainCheckpointService the chain checkpoint search service
   * @param auditChainCheckpointMapper the mapper for checkpoint entity to response DTO
   * @param auditIntegrityService the integrity verification service
   * @param auditChainVerificationService the chain checkpoint verification service
   * @param auditLifecycleService the chain lifecycle service for archive sealing and gap
   *     declaration
   * @param auditChainIncidentService heartbeat operational incident listing and declaration
   * @param adminRepository repository for actor/target admin label enrichment
   * @param enrollmentRepository repository for enrollment label enrichment
   * @param integrationRepository repository for integration label enrichment
   * @param tenantRepository repository for tenant label enrichment
   */
  public AuditLogController(
      AuditLogService auditLogService,
      AuditLogMapper auditLogMapper,
      AuditChainCheckpointService auditChainCheckpointService,
      AuditChainCheckpointMapper auditChainCheckpointMapper,
      AuditIntegrityService auditIntegrityService,
      AuditChainVerificationService auditChainVerificationService,
      AuditLifecycleService auditLifecycleService,
      AuditChainIncidentService auditChainIncidentService,
      EzkeyAdminRepository adminRepository,
      EnrollmentRepository enrollmentRepository,
      IntegrationRepository integrationRepository,
      TenantRepository tenantRepository) {
    this.auditLogService = auditLogService;
    this.auditLogMapper = auditLogMapper;
    this.auditChainCheckpointService = auditChainCheckpointService;
    this.auditChainCheckpointMapper = auditChainCheckpointMapper;
    this.auditIntegrityService = auditIntegrityService;
    this.auditChainVerificationService = auditChainVerificationService;
    this.auditLifecycleService = auditLifecycleService;
    this.auditChainIncidentService = auditChainIncidentService;
    this.adminRepository = adminRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.integrationRepository = integrationRepository;
    this.tenantRepository = tenantRepository;
  }

  /**
   * Query audit logs with optional filters, tenant-scoped visibility, and pagination.
   *
   * <p>Retrieves audit logs matching the specified criteria with pagination support and automatic
   * tenant-based visibility enforcement. All filter parameters are optional - if none are provided,
   * returns audit logs visible to the caller (paginated).
   *
   * <p><b>Tenant Visibility:</b>
   *
   * <ul>
   *   <li><b>Global Admin:</b> Sees all audit logs. May optionally filter by {@code tenantId}.
   *   <li><b>Tenant Admin:</b> Sees only audit logs where {@code tenant_id} matches their tenant.
   *       System-level events ({@code tenant_id = NULL}) are excluded.
   * </ul>
   *
   * <p><b>Pagination and Sorting:</b>
   *
   * <ul>
   *   <li>Use <code>?page=0&size=20</code> for pagination (zero-based page numbers)
   *   <li>Use <code>?sort=field,direction</code> for sorting (e.g., <code>?sort=auditLogId,asc
   *       </code> or <code>?sort=createdAt,desc</code>)
   *   <li>Default: page=0, size=20, sort=createdAt,DESC
   *   <li>Sortable fields: auditLogId, createdAt, eventType, eventStatus, apiName
   * </ul>
   *
   * @param eventType optional single event type filter (mutually exclusive with {@code
   *     eventTypeFamily})
   * @param eventTypeFamily optional filter for all types in a family (mutually exclusive with
   *     {@code eventType})
   * @param eventStatus optional event status filter
   * @param apiName optional API name filter
   * @param enrollmentId optional enrollment ID filter
   * @param integrationId optional integration ID filter
   * @param adminId optional admin ID filter (actor who performed the action)
   * @param targetAdminId optional target admin ID filter (admin who is the subject of the event,
   *     e.g. created, deactivated, or activated)
   * @param tenantId optional tenant ID filter (Global Admin only; ignored for Tenant Admin whose
   *     scope is enforced automatically)
   * @param pageable pagination and sorting parameters (default: page=0, size=20,
   *     sort=createdAt,DESC)
   * @return ResponseEntity containing page of audit logs visible to the caller
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  @Operation(
      summary = "Query audit logs",
      description =
          "Retrieves audit logs with optional filters, tenant-scoped visibility, and pagination "
              + "for security monitoring and compliance reporting. Tenant Admins automatically "
              + "see only their tenant's audit logs. Global Admins see all logs and may "
              + "optionally filter by tenantId. Supports dynamic sorting via "
              + "?sort=field,direction (e.g., ?sort=auditLogId,asc). Default sort is by "
              + "creation date descending (newest first).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Page<AuditLogResponseDto>> getAuditLogs(
      @Parameter(description = "Filter by a single event type (not together with eventTypeFamily)")
          @RequestParam(required = false)
          EventType eventType,
      @Parameter(
              description =
                  "Filter by event family (all types in the family). Mutually exclusive with "
                      + "eventType.")
          @RequestParam(required = false)
          EventTypeFamily eventTypeFamily,
      @Parameter(description = "Filter by event status") @RequestParam(required = false)
          EventStatus eventStatus,
      @Parameter(description = "Filter by API name") @RequestParam(required = false)
          ApiName apiName,
      @Parameter(description = "Filter by enrollment ID") @RequestParam(required = false)
          Integer enrollmentId,
      @Parameter(description = "Filter by auth attempt ID") @RequestParam(required = false)
          Integer authAttemptId,
      @Parameter(description = "Filter by integration ID") @RequestParam(required = false)
          Integer integrationId,
      @Parameter(description = "Filter by admin ID (actor)") @RequestParam(required = false)
          Integer adminId,
      @Parameter(
              description =
                  "Filter by target admin ID (subject of event, e.g."
                      + " created/deactivated/activated)")
          @RequestParam(required = false)
          Integer targetAdminId,
      @Parameter(
              description =
                  "Filter by tenant ID (Global Admin only). Ignored for Tenant Admin "
                      + "whose scope is enforced automatically.")
          @RequestParam(required = false)
          Integer tenantId,
      @Parameter(description = "Filter by creation time (inclusive start), ISO-8601")
          @RequestParam(required = false)
          OffsetDateTime createdAfter,
      @Parameter(description = "Filter by creation time (inclusive end), ISO-8601")
          @RequestParam(required = false)
          OffsetDateTime createdBefore,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    // Extract tenant scope from authenticated principal
    Integer requesterTenantId = extractRequesterTenantId();

    // For Tenant Admin, the tenantId filter parameter is ignored;
    // their own tenantId is enforced unconditionally.
    // For Global Admin (requesterTenantId == null), the optional tenantId param is passed
    // as a filter.
    Integer filterTenantId = (requesterTenantId == null) ? tenantId : null;

    if (eventType != null && eventTypeFamily != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Specify either eventType or eventTypeFamily, not both");
    }

    Page<AuditLogResponseDto> auditLogs =
        mapAuditLogPageWithLabels(
            auditLogService.findByFilters(
                eventType,
                eventTypeFamily,
                eventStatus,
                apiName,
                enrollmentId,
                authAttemptId,
                integrationId,
                adminId,
                targetAdminId,
                requesterTenantId,
                filterTenantId,
                createdAfter,
                createdBefore,
                pageable));

    return ResponseEntity.ok(auditLogs);
  }

  /**
   * Retrieve a bounded audit-log neighborhood around a single anchor event.
   *
   * <p>This endpoint is intentionally opinionated for operator investigations: it returns a small,
   * bounded context around one anchor audit event rather than invoking the general list-search
   * model.
   *
   * @param auditLogId anchor audit log identifier
   * @param beforeCount number of older events to include before the anchor (default 10, max 50)
   * @param afterCount number of newer events to include after the anchor (default 10, max 50)
   * @param tenantId optional tenant filter for Global Admin only
   * @return bounded audit-log neighborhood around the anchor event
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{auditLogId}/context")
  @Operation(
      summary = "Get audit log context around an anchor event",
      description =
          "Retrieves a bounded neighborhood of audit events around a single anchor log for "
              + "operator investigation. Tenant visibility rules are enforced exactly like the "
              + "standard audit search.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Audit log context retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid before/after counts"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Anchor audit log not found")
      })
  public ResponseEntity<AuditLogContextResponseDto> getAuditLogContext(
      @PathVariable Long auditLogId,
      @RequestParam(required = false) Integer beforeCount,
      @RequestParam(required = false) Integer afterCount,
      @Parameter(
              description =
                  "Filter by tenant ID (Global Admin only). Ignored for Tenant Admin whose scope "
                      + "is enforced automatically.")
          @RequestParam(required = false)
          Integer tenantId) {

    int boundedBefore = normalizeContextCount(beforeCount, "beforeCount");
    int boundedAfter = normalizeContextCount(afterCount, "afterCount");
    Integer requesterTenantId = extractRequesterTenantId();
    Integer filterTenantId = (requesterTenantId == null) ? tenantId : null;

    AuditLogService.AuditLogContextSlice slice =
        auditLogService.findContextAround(
            auditLogId, boundedBefore, boundedAfter, requesterTenantId, filterTenantId);

    AuditLogContextResponseDto response = new AuditLogContextResponseDto();
    response.setAnchorAuditLogId(slice.getAnchorAuditLogId());
    response.setHasMoreBefore(slice.isHasMoreBefore());
    response.setHasMoreAfter(slice.isHasMoreAfter());
    response.setItems(mapAuditLogListWithLabels(slice.getItems()));
    return ResponseEntity.ok(response);
  }

  private int normalizeContextCount(Integer value, String parameterName) {
    if (value == null) {
      return 10;
    }
    if (value < 0 || value > 50) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, parameterName + " must be between 0 and 50");
    }
    return value;
  }

  /**
   * Search audit chain checkpoints with optional filters and pagination.
   *
   * <p>Returns checkpoints for operator visibility, SEAL range selection (checkpointIdFrom/To), and
   * Declare Gap (anchorCheckpointId). Global Admin only. All filter parameters are optional.
   * Default sort is {@code windowStart,asc} (chronological).
   *
   * @param windowStartAfter window_start >= value (inclusive); ISO-8601
   * @param windowStartBefore window_start &lt; value (exclusive); ISO-8601
   * @param entryCountMin entry_count >= value
   * @param entryCountMax entry_count <= value (e.g. 0 for empty windows only)
   * @param checkpointType REGULAR, ARCHIVE_SEAL, or GAP_DECLARATION
   * @param createdAfter created_at >= value (inclusive); ISO-8601
   * @param createdBefore created_at &lt; value (exclusive); ISO-8601
   * @param pageable pagination and sort (default: size=20, sort=windowStart,asc)
   * @return page of checkpoint response DTOs
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/chain-checkpoints")
  @Operation(
      summary = "Search audit chain checkpoints",
      description =
          "Paginated search over audit chain checkpoints with optional filters (window range, "
              + "entry count, checkpoint type, created range). Supports SEAL range selection and "
              + "Declare Gap anchor lookup. Global Admin only. Default sort: windowStart,asc.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Checkpoints retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<Page<AuditChainCheckpointResponseDto>> getChainCheckpoints(
      @Parameter(description = "Filter: window_start >= value (inclusive), ISO-8601")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime windowStartAfter,
      @Parameter(description = "Filter: window_start < value (exclusive), ISO-8601")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime windowStartBefore,
      @Parameter(description = "Filter: entry_count >= value (e.g. 1 for non-empty windows)")
          @RequestParam(required = false)
          Integer entryCountMin,
      @Parameter(description = "Filter: entry_count <= value (e.g. 0 for empty windows only)")
          @RequestParam(required = false)
          Integer entryCountMax,
      @Parameter(description = "Filter by checkpoint type") @RequestParam(required = false)
          CheckpointType checkpointType,
      @Parameter(description = "Filter: created_at >= value (inclusive), ISO-8601")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime createdAfter,
      @Parameter(description = "Filter: created_at < value (exclusive), ISO-8601")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime createdBefore,
      @ParameterObject
          @PageableDefault(size = 20, sort = "windowStart", direction = Sort.Direction.ASC)
          Pageable pageable) {

    String checkpointTypeStr = checkpointType != null ? checkpointType.name() : null;
    Page<AuditChainCheckpointResponseDto> page =
        auditChainCheckpointService
            .findCheckpoints(
                windowStartAfter,
                windowStartBefore,
                entryCountMin,
                entryCountMax,
                checkpointTypeStr,
                createdAfter,
                createdBefore,
                pageable)
            .map(auditChainCheckpointMapper::toResponseDto);
    return ResponseEntity.ok(page);
  }

  /**
   * Verifies HMAC integrity of audit log entries within a date range.
   *
   * <p>Recomputes the HMAC-SHA256 signature for each entry and compares it to the stored value.
   * Returns a summary report indicating whether any tampered entries were detected. This endpoint
   * is restricted to Global Admins only, as it is a system-level security operation.
   *
   * @param from start of the verification window (inclusive, ISO-8601)
   * @param to end of the verification window (exclusive, ISO-8601)
   * @return integrity verification report
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/integrity-check")
  @Operation(
      summary = "Verify audit log integrity",
      description =
          "Recomputes HMAC-SHA256 signatures for audit log entries in the specified date "
              + "range and reports any tampered or unsigned entries. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Integrity check completed"),
        @ApiResponse(
            responseCode = "400",
            description = "Date range required -- provide from and to as ISO-8601"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<AuditIntegrityService.IntegrityReport> checkIntegrity(
      @Parameter(description = "Start of verification window (inclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @Parameter(description = "End of verification window (exclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to) {

    if (from == null || to == null) {
      throw new IllegalArgumentException(
          "Date range is required for verification. Provide from (inclusive) and to (exclusive) as"
              + " ISO-8601.");
    }
    AuditIntegrityService.IntegrityReport report = auditIntegrityService.verifyRange(from, to);
    return ResponseEntity.ok(report);
  }

  /**
   * Verifies the HMAC integrity of a single audit log entry by its ID.
   *
   * <p>Recomputes the HMAC-SHA256 signature for the entry and compares it to the stored value.
   * Intended for targeted forensic analysis and developer experimentation -- pass any audit log ID
   * to instantly verify its integrity or confirm a tamper is detected.
   *
   * <p>Response status values:
   *
   * <ul>
   *   <li>{@code OK} - HMAC matches; entry is intact
   *   <li>{@code INTEGRITY_VIOLATION_DETECTED} - HMAC mismatch; entry was modified
   *   <li>{@code UNSIGNED} - entry has no HMAC (created before signing was enabled)
   *   <li>{@code NOT_FOUND} - no entry with the given ID (totalEntries=0)
   * </ul>
   *
   * @param id the audit log entry ID to verify
   * @return integrity verification report (totalEntries=1 when found, 0 when not found)
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/{id}/integrity-check")
  @Operation(
      summary = "Verify integrity of a single audit log entry",
      description =
          "Recomputes the HMAC-SHA256 signature for a single audit log entry and compares "
              + "it to the stored value. Returns OK if the entry is intact, "
              + "INTEGRITY_VIOLATION_DETECTED if modified, UNSIGNED if not signed, "
              + "or NOT_FOUND if the ID does not exist. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Integrity check completed"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<AuditIntegrityService.IntegrityReport> checkSingleEntryIntegrity(
      @Parameter(description = "Audit log entry ID to verify", required = true, example = "42")
          @PathVariable
          Long id) {

    AuditIntegrityService.IntegrityReport report = auditIntegrityService.verifySingle(id);
    return ResponseEntity.ok(report);
  }

  /**
   * Verifies the chain checkpoint integrity for a date range.
   *
   * <p>Recomputes entries_digest for each checkpoint window and verifies chain linkage between
   * consecutive checkpoints. Detects entry insertion, deletion, reordering, and checkpoint
   * tampering. This endpoint is restricted to Global Admins only.
   *
   * @param from start of the verification range (inclusive, ISO-8601)
   * @param to end of the verification range (exclusive, ISO-8601)
   * @return chain verification report
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/chain-integrity")
  @Operation(
      summary = "Verify audit chain checkpoint integrity",
      description =
          "Verifies chain checkpoint integrity by recomputing entry digests and validating "
              + "chain linkage. Detects entry insertion, deletion, reordering, checkpoint "
              + "tampering, and undeclared temporal gaps (missing checkpoints between consecutive "
              + "windows or uncovered leading/trailing periods in the requested range). "
              + "Global Admin only.\n\n"
              + "**Range handling:** The requested from/to may extend before the first checkpoint "
              + "or after the last in the database. Such periods are not reported as undeclared "
              + "gaps (they are before/after \"EZKey time\"). Only real gaps within the system's "
              + "checkpoint extent are reported. The response fields effectiveFrom and effectiveTo "
              + "indicate the range actually used for boundary gap reporting (clamped to "
              + "coverageStart/coverageEnd when the request extended beyond the first/last "
              + "checkpoint).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Chain verification completed"),
        @ApiResponse(
            responseCode = "400",
            description = "Date range required -- provide from and to as ISO-8601"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<AuditChainVerificationService.ChainVerificationReport> checkChainIntegrity(
      @Parameter(description = "Start of verification range (inclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @Parameter(description = "End of verification range (exclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to) {

    if (from == null || to == null) {
      throw new IllegalArgumentException(
          "Date range is required for verification. Provide from (inclusive) and to (exclusive) as"
              + " ISO-8601.");
    }
    AuditChainVerificationService.ChainVerificationReport report =
        auditChainVerificationService.verifyChain(from, to);
    return ResponseEntity.ok(report);
  }

  /**
   * Seals an audit chain period prior to archiving and dropping the corresponding DB partition.
   *
   * <p>Marks all chain checkpoints in the specified period as {@code ARCHIVE_SEAL}. Future {@code
   * verifyChain()} calls will skip entries_digest re-computation for sealed checkpoints (entries no
   * longer in DB by design) while still verifying the chain_hmac linkage.
   *
   * <p><b>Pre-condition:</b> All checkpoints in the period must pass integrity verification. The
   * operation is rejected if any violation is detected.
   *
   * <p><b>Post-condition:</b> The DBA may safely {@code DROP} the audit log partition. Include the
   * returned {@code sealChainHmac} in the Git archive manifest for long-term provenance.
   *
   * @param request period and justification for the archive seal
   * @return seal result with the last chain HMAC and metadata
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping("/lifecycle/seal-archive")
  @Operation(
      summary = "Seal an audit chain period for archival",
      description =
          "Marks all chain checkpoints in the specified period as ARCHIVE_SEAL prior to dropping "
              + "the corresponding DB partition. Runs a mandatory pre-flight integrity check -- "
              + "rejected if any violation is detected. Returns the seal HMAC to include in the "
              + "Git archive manifest. Global Admin only.\n\n"
              + "**Period identification -- two alternative modes:**\n"
              + "- **Timestamp mode**: provide `periodStart` (inclusive) and `periodEnd` "
              + "(exclusive) as ISO-8601 timestamps.\n"
              + "- **Checkpoint ID mode**: provide `checkpointIdFrom` and `checkpointIdTo` "
              + "(both inclusive integers). The service resolves the effective time range from "
              + "those checkpoints automatically. Ergonomic when working directly with the "
              + "database -- short IDs are easier to read than full timestamps.\n\n"
              + "Exactly one mode must be used. Providing both is an error.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Period sealed successfully"),
        @ApiResponse(
            responseCode = "400",
            description =
                "Invalid request: conflicting modes, missing required fields, or no checkpoints "
                    + "found for the given ID range",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Chain integrity violation detected -- resolve before sealing",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Not a Global Admin",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<ArchiveSealResult> sealArchive(
      @Valid @RequestBody ArchiveSealRequest request) {
    return ResponseEntity.ok(auditLifecycleService.sealArchive(request));
  }

  /**
   * Returns the current archive-eligibility summary for future archival automation.
   *
   * @return archive eligibility summary for the current sealed tranche
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/lifecycle/archive-eligibility")
  @Operation(
      summary = "Get archive eligibility summary",
      description =
          "Returns the current backend lifecycle summary used by future archival automation. "
              + "This endpoint does not materialize an export bundle; it reports whether external "
              + "archival is enabled and which sealed checkpoint tranche currently awaits "
              + "confirmation.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Archive eligibility returned"),
        @ApiResponse(
            responseCode = "401",
            description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Not a Global Admin",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<ArchiveEligibilityResult> getArchiveEligibility() {
    return ResponseEntity.ok(auditLifecycleService.getArchiveEligibility());
  }

  /**
   * Confirms that a sealed audit tranche has been archived externally.
   *
   * @param request confirmation payload identifying the sealed tranche and archive digest
   * @return confirmation result with exported checkpoint count and audit metadata
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping("/lifecycle/confirm-archived")
  @Operation(
      summary = "Confirm externally archived audit tranche",
      description =
          "Marks a sealed checkpoint tranche as EXPORTED after an external archival workflow has"
              + " successfully persisted the corresponding bundle. Supports both timestamp range"
              + " and checkpoint ID identification modes, mirroring seal-archive. Global Admin"
              + " only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Archive confirmation recorded"),
        @ApiResponse(
            responseCode = "400",
            description =
                "Invalid request: conflicting modes, missing required fields, or no checkpoints"
                    + " found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Checkpoint state conflict or external archival disabled by policy",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Not a Global Admin",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<ArchiveConfirmArchivedResult> confirmArchived(
      @Valid @RequestBody ArchiveConfirmArchivedRequest request) {
    return ResponseEntity.ok(
        auditLifecycleService.confirmArchived(request, extractRequesterAdminId()));
  }

  /**
   * Formally declares a downtime gap in the audit chain.
   *
   * <p>Creates a single {@code GAP_DECLARATION} checkpoint spanning the full gap period and signs
   * it into the chain with the provided justification. The next scheduler tick will chain its
   * regular checkpoints from the gap declaration, restoring continuity.
   *
   * <p><b>Operational constraint:</b> Must be called before the scheduler creates regular
   * checkpoints for the gap period (i.e., within the lookback window after system restart). The
   * request is rejected if conflicting checkpoints already exist.
   *
   * @param request gap period boundaries and downtime justification
   * @return gap declaration result with the new checkpoint ID and chain HMAC
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping("/lifecycle/declare-gap")
  @Operation(
      summary = "Declare a downtime gap in the audit chain",
      description =
          "Creates a single GAP_DECLARATION checkpoint covering the specified period and signs "
              + "it into the chain with the admin's justification. Use when the system was "
              + "offline longer than the scheduler lookback window. Must be called before the "
              + "scheduler fills the gap with regular checkpoints. Global Admin only.\n\n"
              + "**Gap start -- two alternative modes:**\n"
              + "- **Timestamp mode**: provide `gapStart` as an ISO-8601 timestamp (the moment "
              + "the system went offline).\n"
              + "- **Anchor checkpoint mode**: provide `anchorCheckpointId`, the "
              + "`checkpoint_id` of the last checkpoint recorded before the downtime. The "
              + "service derives `gapStart = anchorCheckpoint.window_end` automatically. "
              + "Ergonomic when working directly with the database -- look up the last "
              + "checkpoint ID before the gap and pass it directly, no timestamp extraction "
              + "needed.\n\n"
              + "`gapEnd` is always a timestamp when provided. In anchor checkpoint mode it is "
              + "**optional**: if omitted, the service auto-derives it as the `window_start` of "
              + "the first checkpoint that exists after the anchor (the boundary between the "
              + "undeclared gap and the scheduler's catch-up checkpoints). If no such checkpoint "
              + "exists yet, falls back to the start of the current 5-minute window. Exactly one "
              + "of `gapStart` or `anchorCheckpointId` must be provided.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Gap declared successfully"),
        @ApiResponse(
            responseCode = "400",
            description =
                "Invalid request: conflicting modes, missing required fields, audit entries "
                    + "found in gap period, or anchor checkpoint not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Conflicting regular checkpoints already exist in the gap period",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Not a Global Admin",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<GapDeclarationResult> declareGap(
      @Valid @RequestBody GapDeclarationRequest request) {
    return ResponseEntity.ok(auditLifecycleService.declareGap(request));
  }

  /**
   * Lists audit-chain heartbeat operational incidents (cryptographically separate from checkpoint
   * rows).
   *
   * @param pageable paging (default newest first)
   * @return paginated incidents
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/lifecycle/incidents")
  @Operation(
      summary = "List audit-chain heartbeat incidents",
      description =
          "Paginated list of operational incidents raised when peripheral supervision detected"
              + " stalled checkpoints (bounded unsupervised activity / degraded mode). Separate"
              + " from GAP_DECLARATION checkpoints. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Incidents retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<Page<AuditChainIncidentResponseDto>> listLifecycleIncidents(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(auditChainIncidentService.search(pageable));
  }

  /**
   * Declares closure for an incident awaiting operator justification after heartbeat recovery.
   *
   * @param incidentId incident primary key
   * @param request justification and root cause
   * @return updated incident
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping("/lifecycle/incidents/{incidentId}/declare")
  @Operation(
      summary = "Declare an audit-chain heartbeat incident closed",
      description =
          "Supplies justification and classified root cause for an incident in "
              + "RECOVERED_PENDING_DECLARATION. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Incident declared closed"),
        @ApiResponse(responseCode = "400", description = "Invalid payload or wrong incident state"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin"),
        @ApiResponse(responseCode = "404", description = "Incident not found")
      })
  public ResponseEntity<AuditChainIncidentResponseDto> declareLifecycleIncident(
      @Parameter(description = "Incident identifier", required = true, example = "1") @PathVariable
          long incidentId,
      @Valid @RequestBody DeclareAuditChainIncidentRequest request) {
    return ResponseEntity.ok(
        auditChainIncidentService.declareIncident(incidentId, request, extractRequesterAdminId()));
  }

  /**
   * Extracts the tenant ID of the authenticated administrator for tenant scoping.
   *
   * <p>Returns the tenant ID from the {@link AdminPrincipal} if the caller is a Tenant Admin, or
   * {@code null} for Global Admin (who can see all tenants).
   *
   * @return tenant ID for Tenant Admin, {@code null} for Global Admin
   */
  private Integer extractRequesterTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      return null;
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      return adminPrincipal.tenantId();
    }
    return null;
  }

  private Integer extractRequesterAdminId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      return null;
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      return adminPrincipal.adminId();
    }
    return null;
  }

  /**
   * Maps a page of audit logs to response DTOs with batch-loaded display labels (avoids N+1 on
   * investigation lists).
   *
   * @param page audit log entities from the service layer
   * @return page of enriched response DTOs
   */
  private Page<AuditLogResponseDto> mapAuditLogPageWithLabels(Page<AuditLog> page) {
    List<AuditLog> content = page.getContent();
    if (content.isEmpty()) {
      return page.map(auditLogMapper::toResponseDto);
    }
    List<AuditLogResponseDto> enriched = mapAuditLogListWithLabels(content);
    return new PageImpl<>(enriched, page.getPageable(), page.getTotalElements());
  }

  /**
   * Maps audit log entities to response DTOs with batch-loaded display labels.
   *
   * @param auditLogs audit log entities (non-null)
   * @return enriched response DTOs in the same order
   */
  private List<AuditLogResponseDto> mapAuditLogListWithLabels(List<AuditLog> auditLogs) {
    if (auditLogs.isEmpty()) {
      return List.of();
    }

    Set<Integer> adminIds = new LinkedHashSet<>();
    Set<Integer> enrollmentIds = new LinkedHashSet<>();
    Set<Integer> integrationIds = new LinkedHashSet<>();
    Set<Integer> tenantIds = new LinkedHashSet<>();

    for (AuditLog log : auditLogs) {
      if (log.getAdminId() != null) {
        adminIds.add(log.getAdminId());
      }
      if (log.getTargetAdminId() != null) {
        adminIds.add(log.getTargetAdminId());
      }
      if (log.getEnrollmentId() != null) {
        enrollmentIds.add(log.getEnrollmentId());
      }
      if (log.getIntegrationId() != null) {
        integrationIds.add(log.getIntegrationId());
      }
      if (log.getTenantId() != null) {
        tenantIds.add(log.getTenantId());
      }
    }

    Map<Integer, EzkeyAdmin> adminsById =
        adminIds.isEmpty()
            ? Map.of()
            : adminRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(EzkeyAdmin::getAdminId, Function.identity()));

    Map<Integer, Enrollment> enrollmentsById =
        enrollmentIds.isEmpty()
            ? Map.of()
            : enrollmentRepository.findAllById(enrollmentIds).stream()
                .collect(Collectors.toMap(Enrollment::getEnrollmentId, Function.identity()));

    for (Enrollment enrollment : enrollmentsById.values()) {
      if (enrollment.getIntegrationId() != null) {
        integrationIds.add(enrollment.getIntegrationId());
      }
    }

    Map<Integer, Integration> integrationsById =
        integrationIds.isEmpty()
            ? Map.of()
            : integrationRepository.findAllByIdWithTenant(integrationIds).stream()
                .collect(Collectors.toMap(Integration::getId, Function.identity()));

    for (Integration integration : integrationsById.values()) {
      if (integration.getTenant() != null && integration.getTenant().getTenantId() != null) {
        tenantIds.add(integration.getTenant().getTenantId());
      }
    }

    Map<Integer, Tenant> tenantsById =
        tenantIds.isEmpty()
            ? Map.of()
            : tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getTenantId, Function.identity()));

    List<AuditLogResponseDto> result = new ArrayList<>(auditLogs.size());
    for (AuditLog log : auditLogs) {
      EzkeyAdmin actor = log.getAdminId() != null ? adminsById.get(log.getAdminId()) : null;
      EzkeyAdmin target =
          log.getTargetAdminId() != null ? adminsById.get(log.getTargetAdminId()) : null;
      Enrollment enrollment =
          log.getEnrollmentId() != null ? enrollmentsById.get(log.getEnrollmentId()) : null;
      Integration integration = resolveIntegrationForAuditLog(log, enrollment, integrationsById);
      Tenant tenant = resolveTenantForAuditLog(log, integration, tenantsById);

      result.add(
          auditLogMapper.toResponseDtoWithLabels(
              log,
              actor != null ? actor.getUsername() : null,
              target != null ? target.getUsername() : null,
              integration != null ? integration.getName() : null,
              enrollment != null ? enrollment.getEnrollmentName() : null,
              tenant != null ? tenant.getTenantName() : null));
    }
    return result;
  }

  /**
   * Resolves the integration for an audit log using direct FK or enrollment chain.
   *
   * @param log the audit log row
   * @param enrollment preloaded enrollment for {@code log.enrollmentId}, or null
   * @param integrationsById batch-loaded integrations
   * @return integration entity, or null
   */
  private Integration resolveIntegrationForAuditLog(
      AuditLog log, Enrollment enrollment, Map<Integer, Integration> integrationsById) {
    if (log.getIntegrationId() != null) {
      return integrationsById.get(log.getIntegrationId());
    }
    if (enrollment != null && enrollment.getIntegrationId() != null) {
      return integrationsById.get(enrollment.getIntegrationId());
    }
    return null;
  }

  /**
   * Resolves tenant display context from audit log FK or integration tenant.
   *
   * @param log the audit log row
   * @param integration resolved integration, or null
   * @param tenantsById batch-loaded tenants
   * @return tenant entity, or null
   */
  private Tenant resolveTenantForAuditLog(
      AuditLog log, Integration integration, Map<Integer, Tenant> tenantsById) {
    if (log.getTenantId() != null) {
      return tenantsById.get(log.getTenantId());
    }
    if (integration != null
        && integration.getTenant() != null
        && integration.getTenant().getTenantId() != null) {
      return tenantsById.get(integration.getTenant().getTenantId());
    }
    return null;
  }
}
