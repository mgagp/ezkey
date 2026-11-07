/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentQrDeliveryService
 */

package org.ezkey.admin.connector;

import com.google.zxing.WriterException;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.ezkey.admin.connector.DeliveryCapabilityService.Capability;
import org.ezkey.admin.connector.DeliveryConnectorPort.DeliveryJob;
import org.ezkey.admin.connector.DeliveryConnectorPort.DeliveryResult;
import org.ezkey.admin.connector.DeliveryConnectorPort.Outcome;
import org.ezkey.admin.connector.QrDeliveryRefusedException.Reason;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.service.EntityEligibilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends an enrollment QR through the delivery connector, for one operator action.
 *
 * <p>The call runs outside any transaction, so a slow connector holds no database connection. The
 * send route is also excluded from open-in-view ({@code AdminOpenInViewConfig}). Nothing is stored
 * for later: one click makes one attempt, and retrying stays the operator's choice.
 *
 * @since 2026
 */
@Service
public class EnrollmentQrDeliveryService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentQrDeliveryService.class);

  private static final int QR_SIZE_PIXELS = 300;
  private static final int ORGANISATION_NAME_MAX = 120;
  private static final int RECIPIENT_MAX = 255;
  private static final Pattern CONTACT_EMAIL =
      Pattern.compile("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

  /** Only these may have left, so only these count toward the daily caps. */
  private static final List<String> COUNTED_ACTIONS =
      List.of(
          AdminAuditConstants.ENROLLMENT_QR_DELIVERY_ACCEPTED,
          AdminAuditConstants.ENROLLMENT_QR_DELIVERY_UNKNOWN);

  private final DeliveryCapabilityService capabilityService;
  private final DeliveryConnectorPort port;
  private final DeliveryConnectorProperties properties;
  private final EnrollmentRepository enrollmentRepository;
  private final IntegrationRepository integrationRepository;
  private final EntityEligibilityService eligibilityService;
  private final QrCodePayloadService qrCodePayloadService;
  private final QrCodeGeneratorService qrCodeGeneratorService;
  private final QrCodeProperties qrCodeProperties;
  private final AuditLogRepository auditLogRepository;
  private final AuditLogService auditLogService;
  private final TransactionTemplate readOnlyTx;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Builds the service.
   *
   * @param capabilityService connector capability
   * @param port delivery port
   * @param properties connector settings
   * @param enrollmentRepository enrollments
   * @param integrationRepository integrations
   * @param eligibilityService parent-chain eligibility
   * @param qrCodePayloadService QR payload
   * @param qrCodeGeneratorService QR image
   * @param qrCodeProperties expected server
   * @param auditLogRepository audit counts for the daily caps
   * @param auditLogService audit writes
   * @param transactionManager transaction manager for the short reads
   */
  public EnrollmentQrDeliveryService(
      DeliveryCapabilityService capabilityService,
      DeliveryConnectorPort port,
      DeliveryConnectorProperties properties,
      EnrollmentRepository enrollmentRepository,
      IntegrationRepository integrationRepository,
      EntityEligibilityService eligibilityService,
      QrCodePayloadService qrCodePayloadService,
      QrCodeGeneratorService qrCodeGeneratorService,
      QrCodeProperties qrCodeProperties,
      AuditLogRepository auditLogRepository,
      AuditLogService auditLogService,
      PlatformTransactionManager transactionManager) {
    this.capabilityService = capabilityService;
    this.port = port;
    this.properties = properties;
    this.enrollmentRepository = enrollmentRepository;
    this.integrationRepository = integrationRepository;
    this.eligibilityService = eligibilityService;
    this.qrCodePayloadService = qrCodePayloadService;
    this.qrCodeGeneratorService = qrCodeGeneratorService;
    this.qrCodeProperties = qrCodeProperties;
    this.auditLogRepository = auditLogRepository;
    this.auditLogService = auditLogService;
    this.readOnlyTx = new TransactionTemplate(transactionManager);
    this.readOnlyTx.setReadOnly(true);
  }

  /**
   * Sends the QR of one enrollment to its contact email.
   *
   * @param enrollmentId the enrollment, already checked against the caller's access
   * @param locale template locale
   * @param principal the operator
   * @param context client context for the audit
   * @return the outcome, the send id and the recipient
   * @throws QrDeliveryRefusedException when the core refuses before any call
   * @throws ResourceNotFoundException when the enrollment does not exist
   */
  public SendReport send(
      Integer enrollmentId, String locale, AdminPrincipal principal, ClientContext context) {

    SendMaterial material = readMaterial(enrollmentId);
    String sendId = UUID.randomUUID().toString();
    Capability capability = capabilityService.current();

    try {
      assertAllowed(material, locale, capability);
    } catch (QrDeliveryRefusedException refused) {
      audit(
          material,
          principal,
          context,
          sendId,
          capability,
          locale,
          AdminAuditConstants.ENROLLMENT_QR_DELIVERY_REFUSED,
          EventStatus.FAILURE,
          refused.getReason().code(),
          null);
      throw refused;
    }

    DeliveryResult result = port.send(buildJob(material, sendId, locale));

    audit(
        material,
        principal,
        context,
        sendId,
        capability,
        locale,
        actionFor(result.outcome()),
        statusFor(result.outcome()),
        result.code(),
        result.providerReference());
    logger.info("Delivery {}: {}", sendId, result.outcome());

    return new SendReport(
        result.outcome(), sendId, material.recipient(), result.code(), capability.testMode());
  }

  private SendMaterial readMaterial(Integer enrollmentId) {
    return readOnlyTx.execute(_ -> {
      Enrollment enrollment =
          enrollmentRepository
              .findById(enrollmentId)
              .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
      Integration integration =
          integrationRepository
              .findById(enrollment.getIntegrationId())
              .orElseThrow(() ->
                  new ResourceNotFoundException("Integration", enrollment.getIntegrationId()));
      Tenant tenant = integration.getTenant();
      boolean expired =
          enrollment.getExpiresAt() != null
              && enrollment.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC));
      return new SendMaterial(
          enrollmentId,
          enrollment.getIntegrationId(),
          tenant.getTenantId(),
          enrollment.getStatus(),
          expired,
          Boolean.TRUE.equals(integration.getIsSystemIntegration()),
          eligibilityService.isIntegrationOperational(integration)
              && eligibilityService.isTenantOperational(tenant),
          trimToNull(enrollment.getContactEmail()),
          organisationName(tenant),
          enrollment.getEnrollmentProofToken());
    });
  }

  private void assertAllowed(SendMaterial material, String locale, Capability capability) {
    if (!capability.available()) {
      throw new QrDeliveryRefusedException(Reason.CONNECTOR_UNAVAILABLE);
    }
    if (!capability.locales().contains(locale)) {
      throw new QrDeliveryRefusedException(Reason.LOCALE_NOT_OFFERED);
    }
    if (material.adminEnrollment()) {
      throw new QrDeliveryRefusedException(Reason.ADMIN_ENROLLMENT);
    }
    if (material.status() != EnrollmentStatus.CREATED) {
      throw new QrDeliveryRefusedException(Reason.ENROLLMENT_NOT_CREATED);
    }
    if (material.expired()) {
      throw new QrDeliveryRefusedException(Reason.ENROLLMENT_EXPIRED);
    }
    if (!material.parentsOperational()) {
      throw new QrDeliveryRefusedException(Reason.PARENT_NOT_OPERATIONAL);
    }
    if (material.recipient() == null) {
      throw new QrDeliveryRefusedException(Reason.CONTACT_EMAIL_MISSING);
    }
    if (material.recipient().length() > RECIPIENT_MAX
        || !CONTACT_EMAIL.matcher(material.recipient()).matches()) {
      throw new QrDeliveryRefusedException(Reason.CONTACT_EMAIL_INVALID);
    }
    if (expectedServer() == null) {
      throw new QrDeliveryRefusedException(Reason.EXPECTED_SERVER_MISSING);
    }
    assertWithinDailyCaps(material.tenantId());
  }

  private void assertWithinDailyCaps(Integer tenantId) {
    OffsetDateTime startOfDay =
        OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    long tenantCount =
        auditLogRepository.countByEventTypeAndTenantIdAndEventActionInAndCreatedAtGreaterThanEqual(
            EventType.ENROLLMENT_QR_DELIVERY, tenantId, COUNTED_ACTIONS, startOfDay);
    if (tenantCount >= properties.getTenantDailyCap()) {
      throw new QrDeliveryRefusedException(Reason.DAILY_CAP_REACHED);
    }
    long instanceCount =
        auditLogRepository.countByEventTypeAndEventActionInAndCreatedAtGreaterThanEqual(
            EventType.ENROLLMENT_QR_DELIVERY, COUNTED_ACTIONS, startOfDay);
    if (instanceCount >= properties.getInstanceDailyCap()) {
      throw new QrDeliveryRefusedException(Reason.DAILY_CAP_REACHED);
    }
  }

  private DeliveryJob buildJob(SendMaterial material, String sendId, String locale) {
    byte[] png;
    try {
      png =
          qrCodeGeneratorService.generateQrCodeImage(
              qrCodePayloadService.composePayload(material.enrollmentId(), material.proofToken()),
              QR_SIZE_PIXELS,
              QR_SIZE_PIXELS);
    } catch (IOException | WriterException e) {
      throw new IllegalStateException("QR rendering failed for delivery " + sendId);
    }
    return new DeliveryJob(
        sendId,
        String.valueOf(material.tenantId()),
        locale,
        material.recipient(),
        material.organisationName(),
        expectedServer(),
        Base64.getEncoder().encodeToString(png));
  }

  /** The host of the configured Auth API base URL, or null. */
  private String expectedServer() {
    String baseUrl = trimToNull(qrCodeProperties.getAuthBaseUrl());
    if (baseUrl == null) {
      return null;
    }
    try {
      return URI.create(baseUrl).getHost();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String organisationName(Tenant tenant) {
    String name = trimToNull(tenant.getOrganizationName());
    if (name == null) {
      name = tenant.getTenantName();
    }
    return name.length() > ORGANISATION_NAME_MAX ? name.substring(0, ORGANISATION_NAME_MAX) : name;
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private void audit(
      SendMaterial material,
      AdminPrincipal principal,
      ClientContext context,
      String sendId,
      Capability capability,
      String locale,
      String action,
      EventStatus status,
      String code,
      String providerReference) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("sendId", sendId);
    details.put("connector", capability.connectorName());
    details.put("template", DeliveryConnectorPort.TEMPLATE_ENROLLMENT_QR);
    details.put("locale", locale);
    details.put("recipient", material.recipient());
    details.put("code", code);
    details.put("providerReference", providerReference);
    details.put("testMode", capability.testMode());
    auditLogService.log(
        AuditHelper.createAdminAudit(
                context, EventType.ENROLLMENT_QR_DELIVERY, action, material.tenantId())
            .eventStatus(status)
            .adminId(principal != null ? principal.adminId() : null)
            .enrollmentId(material.enrollmentId())
            .integrationId(material.integrationId())
            .eventDetails(objectMapper.writeValueAsString(details))
            .build());
  }

  private static String actionFor(Outcome outcome) {
    return switch (outcome) {
      case ACCEPTED -> AdminAuditConstants.ENROLLMENT_QR_DELIVERY_ACCEPTED;
      case REJECTED -> AdminAuditConstants.ENROLLMENT_QR_DELIVERY_REJECTED;
      case UNKNOWN -> AdminAuditConstants.ENROLLMENT_QR_DELIVERY_UNKNOWN;
    };
  }

  private static EventStatus statusFor(Outcome outcome) {
    return switch (outcome) {
      case ACCEPTED -> EventStatus.SUCCESS;
      case REJECTED, UNKNOWN -> EventStatus.FAILURE;
    };
  }

  /**
   * What the operator gets back after the connector was called.
   *
   * @param outcome what happened
   * @param sendId links logs and audit
   * @param recipient the address used
   * @param code typed error code, or null
   * @param testMode true when a test double answered
   */
  public record SendReport(
      Outcome outcome, String sendId, String recipient, String code, boolean testMode) {}

  /** Plain values read in a short transaction, so nothing needs a session during the call. */
  private record SendMaterial(
      Integer enrollmentId,
      Integer integrationId,
      Integer tenantId,
      EnrollmentStatus status,
      boolean expired,
      boolean adminEnrollment,
      boolean parentsOperational,
      String recipient,
      String organisationName,
      String proofToken) {

    @Override
    public String toString() {
      return "SendMaterial[enrollmentId=" + enrollmentId + "]";
    }
  }
}
