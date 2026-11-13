/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.connector.DeliveryCapabilityService.Capability;
import org.ezkey.admin.connector.DeliveryConnectorPort.DeliveryJob;
import org.ezkey.admin.connector.DeliveryConnectorPort.DeliveryResult;
import org.ezkey.admin.connector.DeliveryConnectorPort.Outcome;
import org.ezkey.admin.connector.QrDeliveryRefusedException.Reason;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.service.EntityEligibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EnrollmentQrDeliveryService")
class EnrollmentQrDeliveryServiceTest {

  private static final Integer ENROLLMENT_ID = 7;
  private static final Integer INTEGRATION_ID = 11;
  private static final Integer TENANT_ID = 42;
  private static final int CHALLENGE = 123456;
  private static final String RECIPIENT = "person@example.com";
  private static final String TOKEN = "PROOFTOKENVALUE";
  private static final byte[] PNG = "PNGBYTES".getBytes(StandardCharsets.UTF_8);
  private static final Capability USABLE =
      new Capability(true, false, "ezkey-connector-smtp", List.of("en", "fr"));

  @Mock private DeliveryCapabilityService capabilityService;
  @Mock private DeliveryConnectorPort port;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private EntityEligibilityService eligibilityService;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private QrCodeGeneratorService qrCodeGeneratorService;
  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private Enrollment enrollment;
  @Mock private Integration integration;
  @Mock private Tenant tenant;

  private final DeliveryConnectorProperties properties = new DeliveryConnectorProperties();
  private final QrCodeProperties qrCodeProperties = new QrCodeProperties();
  private final ClientContext context = mock(ClientContext.class);
  private EnrollmentQrDeliveryService service;

  @BeforeEach
  void setUp() throws Exception {
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(new SimpleTransactionStatus());
    qrCodeProperties.setAuthBaseUrl("https://auth.example.com:8443");
    properties.setTenantDailyCap(5);
    properties.setInstanceDailyCap(10);

    when(capabilityService.current()).thenReturn(USABLE);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    when(enrollment.getIntegrationId()).thenReturn(INTEGRATION_ID);
    when(enrollment.getStatus()).thenReturn(EnrollmentStatus.CREATED);
    when(enrollment.getContactEmail()).thenReturn(RECIPIENT);
    when(enrollment.getEnrollmentProofToken()).thenReturn(TOKEN);
    when(enrollment.getEnrollmentChallenge()).thenReturn(CHALLENGE);
    when(integrationRepository.findById(INTEGRATION_ID)).thenReturn(Optional.of(integration));
    when(integration.getTenant()).thenReturn(tenant);
    when(integration.getIsSystemIntegration()).thenReturn(false);
    when(tenant.getTenantId()).thenReturn(TENANT_ID);
    when(tenant.getTenantName()).thenReturn("acme-tenant");
    when(eligibilityService.isIntegrationOperational(integration)).thenReturn(true);
    when(eligibilityService.isTenantOperational(tenant)).thenReturn(true);
    when(qrCodePayloadService.composePayload(TOKEN)).thenReturn("payload");
    when(qrCodeGeneratorService.generateQrCodeImage(anyString(), anyInt(), anyInt()))
        .thenReturn(PNG);
    when(port.send(any())).thenReturn(new DeliveryResult(Outcome.ACCEPTED, null, "ref-1", false));

    service =
        new EnrollmentQrDeliveryService(
            capabilityService,
            port,
            properties,
            enrollmentRepository,
            integrationRepository,
            tenantRepository,
            eligibilityService,
            qrCodePayloadService,
            qrCodeGeneratorService,
            qrCodeProperties,
            auditLogRepository,
            auditLogService,
            transactionManager);
  }

  private EnrollmentQrDeliveryService.SendReport send() {
    return service.send(ENROLLMENT_ID, "fr", null, context);
  }

  private DeliveryJob sentJob() {
    ArgumentCaptor<DeliveryJob> job = ArgumentCaptor.forClass(DeliveryJob.class);
    verify(port).send(job.capture());
    return job.getValue();
  }

  private AuditLog auditEntry() {
    ArgumentCaptor<AuditLog> entry = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(entry.capture());
    return entry.getValue();
  }

  private void assertRefused(Reason reason) {
    assertThatThrownBy(this::send)
        .isInstanceOfSatisfying(
            QrDeliveryRefusedException.class, e -> assertThat(e.getReason()).isEqualTo(reason));
    verify(port, never()).send(any());
    AuditLog entry = auditEntry();
    assertThat(entry.getEventAction())
        .isEqualTo(AdminAuditConstants.ENROLLMENT_QR_DELIVERY_REFUSED);
    assertThat(entry.getEventDetails()).contains(reason.code());
    assertThat(entry.getEventDetails()).doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("the job carries the QR and the recipient, and never the challenge or the token")
  void jobContent() {
    EnrollmentQrDeliveryService.SendReport report = send();

    DeliveryJob job = sentJob();
    assertThat(report.outcome()).isEqualTo(Outcome.ACCEPTED);
    assertThat(report.sendId()).isEqualTo(job.sendId());
    assertThat(job.recipient()).isEqualTo(RECIPIENT);
    assertThat(job.expectedServer()).isEqualTo("auth.example.com");
    assertThat(job.tenantRef()).isEqualTo("42");
    assertThat(job.locale()).isEqualTo("fr");
    assertThat(job.organisationName()).isEqualTo("acme-tenant");
    assertThat(job.qrPngBase64()).isEqualTo(java.util.Base64.getEncoder().encodeToString(PNG));
    assertThat(job.expiresAt()).isNull();
    assertThat(job.timeZone()).isNull();
    assertThat(List.of(job.sendId(), job.tenantRef(), job.organisationName(), job.qrPngBase64()))
        .noneMatch(value -> value.contains(String.valueOf(CHALLENGE)) || value.contains(TOKEN));
  }

  @Test
  @DisplayName("the job says until when the invitation works, in the organisation's zone")
  void jobDeadline() {
    OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7);
    when(enrollment.getExpiresAt()).thenReturn(deadline);
    when(tenant.getTimezone()).thenReturn("America/Toronto");

    send();

    DeliveryJob job = sentJob();
    assertThat(job.expiresAt()).isEqualTo(deadline);
    assertThat(job.timeZone()).isEqualTo("America/Toronto");
  }

  @Test
  @DisplayName("an unknown organisation zone is left out, so the connector uses UTC")
  void unknownZone() {
    when(tenant.getTimezone()).thenReturn("Mars/Olympus");

    send();

    assertThat(sentJob().timeZone()).isNull();
  }

  @Test
  @DisplayName("the audit holds the recipient and outcome, and never the QR or the token")
  void auditContent() {
    send();

    AuditLog entry = auditEntry();
    assertThat(entry.getEventType()).isEqualTo(EventType.ENROLLMENT_QR_DELIVERY);
    assertThat(entry.getEventAction())
        .isEqualTo(AdminAuditConstants.ENROLLMENT_QR_DELIVERY_ACCEPTED);
    assertThat(entry.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
    assertThat(entry.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(entry.getEventDetails())
        .contains(RECIPIENT, "ref-1", "ezkey-connector-smtp", "enrollment-qr", "\"fr\"");
    assertThat(entry.getEventDetails())
        .doesNotContain(TOKEN, sentJob().qrPngBase64(), String.valueOf(CHALLENGE));
  }

  @Test
  @DisplayName("rejected and unknown are reported and audited as such")
  void otherOutcomes() {
    when(port.send(any()))
        .thenReturn(new DeliveryResult(Outcome.REJECTED, "provider-rejected", null, false));
    assertThat(send().outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(auditEntry().getEventAction())
        .isEqualTo(AdminAuditConstants.ENROLLMENT_QR_DELIVERY_REJECTED);

    org.mockito.Mockito.clearInvocations(auditLogService);
    when(port.send(any()))
        .thenReturn(new DeliveryResult(Outcome.UNKNOWN, "provider-timeout", null, false));
    EnrollmentQrDeliveryService.SendReport report = send();
    assertThat(report.outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(report.code()).isEqualTo("provider-timeout");
    assertThat(auditEntry().getEventAction())
        .isEqualTo(AdminAuditConstants.ENROLLMENT_QR_DELIVERY_UNKNOWN);
  }

  @Test
  @DisplayName("each click gets its own send id")
  void sendIdPerClick() {
    assertThat(send().sendId()).isNotEqualTo(send().sendId());
  }

  @Test
  @DisplayName("no connector: refused and audited")
  void noConnector() {
    when(capabilityService.current()).thenReturn(new Capability(false, false, "", List.of()));
    assertRefused(Reason.CONNECTOR_UNAVAILABLE);
  }

  @Test
  @DisplayName("a locale the connector does not offer is refused")
  void localeNotOffered() {
    when(capabilityService.current()).thenReturn(new Capability(true, false, "c", List.of("en")));
    assertRefused(Reason.LOCALE_NOT_OFFERED);
  }

  @Test
  @DisplayName("an enrollment of a self-registered tenant is refused (#1206 step 7)")
  void selfRegisteredTenant() {
    when(tenant.getSelfRegistered()).thenReturn(true);
    assertRefused(Reason.SELF_REGISTERED_TENANT);
  }

  @Test
  @DisplayName("an admin of a self-registered tenant sees no usable connector")
  void capabilityHiddenForSelfRegisteredTenant() {
    Tenant own = new Tenant("eval-abc", "Anonymous evaluator preview tenant");
    own.setSelfRegistered(true);
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(own));

    assertThat(
            service.capabilityFor(new AdminPrincipal(1, AdminType.TENANT_ADMIN, TENANT_ID, null)))
        .extracting(Capability::available)
        .isEqualTo(false);
    assertThat(
            service.capabilityFor(
                new AdminPrincipal(2, AdminType.INTEGRATION_ADMIN, TENANT_ID, INTEGRATION_ID)))
        .extracting(Capability::available)
        .isEqualTo(false);
  }

  @Test
  @DisplayName("other callers see the connector as it is")
  void capabilityKeptForOtherCallers() {
    Tenant own = new Tenant("acme-tenant", "Acme");
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(own));

    assertThat(
            service.capabilityFor(new AdminPrincipal(1, AdminType.TENANT_ADMIN, TENANT_ID, null)))
        .isEqualTo(USABLE);
    assertThat(service.capabilityFor(new AdminPrincipal(3, AdminType.GLOBAL_ADMIN, 1, null)))
        .isEqualTo(USABLE);
    verify(tenantRepository, never()).findById(1);
  }

  @Test
  @DisplayName("an admin MFA enrollment is refused")
  void adminEnrollment() {
    when(integration.getIsSystemIntegration()).thenReturn(true);
    assertRefused(Reason.ADMIN_ENROLLMENT);
  }

  @Test
  @DisplayName("only a CREATED enrollment can be sent")
  void notCreated() {
    for (EnrollmentStatus status : EnrollmentStatus.values()) {
      if (status == EnrollmentStatus.CREATED) {
        continue;
      }
      org.mockito.Mockito.clearInvocations(auditLogService, port);
      when(enrollment.getStatus()).thenReturn(status);
      assertRefused(Reason.ENROLLMENT_NOT_CREATED);
    }
  }

  @Test
  @DisplayName("an expired invitation is refused")
  void expired() {
    when(enrollment.getExpiresAt()).thenReturn(OffsetDateTime.now().minusMinutes(1));
    assertRefused(Reason.ENROLLMENT_EXPIRED);
  }

  @Test
  @DisplayName("a retired integration or inactive tenant is refused")
  void parentNotOperational() {
    when(eligibilityService.isIntegrationOperational(integration)).thenReturn(false);
    assertRefused(Reason.PARENT_NOT_OPERATIONAL);
  }

  @Test
  @DisplayName("no contact email is refused, and blank counts as none")
  void missingEmail() {
    when(enrollment.getContactEmail()).thenReturn("  ");
    assertRefused(Reason.CONTACT_EMAIL_MISSING);
  }

  @Test
  @DisplayName("an invalid contact email is refused")
  void invalidEmail() {
    when(enrollment.getContactEmail()).thenReturn("not-an-address");
    assertRefused(Reason.CONTACT_EMAIL_INVALID);
  }

  @Test
  @DisplayName("an empty expected server is refused")
  void missingExpectedServer() {
    qrCodeProperties.setAuthBaseUrl(" ");
    assertRefused(Reason.EXPECTED_SERVER_MISSING);
  }

  @Test
  @DisplayName("the tenant daily cap refuses the send")
  void tenantCap() {
    when(auditLogRepository.countByEventTypeAndTenantIdAndEventActionInAndCreatedAtGreaterThanEqual(
            eq(EventType.ENROLLMENT_QR_DELIVERY), eq(TENANT_ID), anyCollection(), any()))
        .thenReturn(5L);
    assertRefused(Reason.DAILY_CAP_REACHED);
  }

  @Test
  @DisplayName("the instance daily cap refuses the send")
  void instanceCap() {
    when(auditLogRepository.countByEventTypeAndEventActionInAndCreatedAtGreaterThanEqual(
            eq(EventType.ENROLLMENT_QR_DELIVERY), anyCollection(), any()))
        .thenReturn(10L);
    assertRefused(Reason.DAILY_CAP_REACHED);
  }

  @Test
  @DisplayName("an unknown enrollment is not found, with no audit and no call")
  void unknownEnrollment() {
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(this::send).isInstanceOf(ResourceNotFoundException.class);
    verify(port, never()).send(any());
    verify(auditLogService, never()).log(any());
  }

  @Test
  @DisplayName("logs never hold the recipient, the token or the QR")
  void logsStayClean() {
    Logger logger = (Logger) LoggerFactory.getLogger(EnrollmentQrDeliveryService.class);
    ch.qos.logback.classic.Level previous = logger.getLevel();
    logger.setLevel(ch.qos.logback.classic.Level.ALL);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      send();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previous);
    }
    assertThat(appender.list).isNotEmpty();
    String base64 = java.util.Base64.getEncoder().encodeToString(PNG);
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(
            line -> line.contains(RECIPIENT) || line.contains(TOKEN) || line.contains(base64));
  }

  @Test
  @DisplayName("organisation name prefers the legal name, falls back, and stays within 120")
  void organisationName() {
    when(tenant.getOrganizationName()).thenReturn("  Legal Name  ");
    send();
    assertThat(sentJob().organisationName()).isEqualTo("Legal Name");

    org.mockito.Mockito.clearInvocations(port, auditLogService);
    when(tenant.getOrganizationName()).thenReturn("   ");
    send();
    assertThat(sentJob().organisationName()).isEqualTo("acme-tenant");

    org.mockito.Mockito.clearInvocations(port, auditLogService);
    when(tenant.getOrganizationName()).thenReturn(null);
    send();
    assertThat(sentJob().organisationName()).isEqualTo("acme-tenant");

    org.mockito.Mockito.clearInvocations(port, auditLogService);
    when(tenant.getOrganizationName()).thenReturn("A".repeat(120) + "TAIL");
    send();
    assertThat(sentJob().organisationName()).hasSize(120).doesNotContain("TAIL").startsWith("A");
  }

  @Test
  @DisplayName("contact email is stripped before it is sent")
  void contactEmailIsStripped() {
    when(enrollment.getContactEmail()).thenReturn("  person@example.com  ");
    send();
    assertThat(sentJob().recipient()).isEqualTo("person@example.com");
  }

  @Test
  @DisplayName("expected server is the host only, so userinfo, port and path stay out")
  void expectedServerIsHostOnly() {
    qrCodeProperties.setAuthBaseUrl("https://operator:s3cret@auth.example.com:8443/cb?x=1#frag");
    send();
    DeliveryJob job = sentJob();
    assertThat(job.expectedServer()).isEqualTo("auth.example.com");
    assertThat(job.expectedServer()).doesNotContain("s3cret", "operator", "8443", "/cb");
  }

  @Test
  @DisplayName("an auth URL with no host is refused, so the email cannot name a bad server")
  void authUrlWithoutHost() {
    qrCodeProperties.setAuthBaseUrl("auth.example.com");
    assertRefused(Reason.EXPECTED_SERVER_MISSING);

    org.mockito.Mockito.clearInvocations(auditLogService, port);
    qrCodeProperties.setAuthBaseUrl("https://");
    assertRefused(Reason.EXPECTED_SERVER_MISSING);

    org.mockito.Mockito.clearInvocations(auditLogService, port);
    qrCodeProperties.setAuthBaseUrl("not a url");
    assertRefused(Reason.EXPECTED_SERVER_MISSING);
  }
}
