/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminBootstrapServiceLoggingTest
 * Description: Bootstrap logs keep token/challenge/QR; recovery codes and email stay out (#750).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.config.AdminMfaProperties;
import org.ezkey.admin.config.BootstrapCredentialsOutputMode;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.config.OrganizationProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Output-capture and TTL tests for global-admin MFA bootstrap (issue #750).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceLoggingTest {

  private static final String USERNAME = "admin.docker";
  private static final String EMAIL = "admin@secret-bootstrap.example";
  private static final String PROOF_TOKEN = "bootstrapProofTokenPart.saltPartValueXX";
  private static final int CHALLENGE = 654321;
  private static final String RECOVERY_CODE = "ABCD-EFGH-IJKL-MNOP-QRST-UVWX";

  @Mock private IntegrationRepository integrationRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private SignatureService signatureService;
  @Mock private AdminRecoveryService recoveryService;
  @Mock private QrCodeAsciiRenderer qrCodeAsciiRenderer;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private LockingTaskExecutor lockingTaskExecutor;
  @Mock private BootstrapCredentialsFileExporter bootstrapCredentialsFileExporter;

  private AdminMfaProperties mfaProperties;
  private AdminBootstrapService service;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger bootstrapLogger;

  @BeforeEach
  void setUp() {
    mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setEnabled(true);
    mfaProperties.getBootstrap().setAutoEnrollment(true);
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);
    mfaProperties.getBootstrap().setEnrollmentExpirationHours(24);

    InitialGlobalAdminProperties initialProps = new InitialGlobalAdminProperties();
    initialProps.setUsername(USERNAME);
    initialProps.setEmail(EMAIL);
    initialProps.setFirstName("Admin");
    initialProps.setLastName("Docker");

    OrganizationProperties organizationProperties = new OrganizationProperties();
    organizationProperties.setName("Ezkey System");
    organizationProperties.setDescription("test");

    service =
        new AdminBootstrapService(
            integrationRepository,
            enrollmentRepository,
            adminRepository,
            tenantRepository,
            signatureService,
            mfaProperties,
            organizationProperties,
            initialProps,
            recoveryService,
            qrCodeAsciiRenderer,
            qrCodePayloadService,
            lockingTaskExecutor,
            bootstrapCredentialsFileExporter);

    when(lockingTaskExecutor.executeWithLock(anyString(), any(Duration.class), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(2, Runnable.class).run();
              return true;
            });

    when(bootstrapCredentialsFileExporter.recoveryCodesLogPointer())
        .thenReturn("<see /var/lib/ezkey/bootstrap/bootstrap-credentials.json (0600)>");

    bootstrapLogger = (Logger) LoggerFactory.getLogger(AdminBootstrapService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    bootstrapLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    bootstrapLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("bootstrap logs keep token/challenge/QR; omit recovery codes and email; set TTL")
  void bootstrapLogsAndSetsEnrollmentExpiry() {
    Tenant systemTenant = new Tenant();
    systemTenant.setTenantName("Ezkey System");
    systemTenant.setTenantDescription("test");
    when(tenantRepository.findByIsSystemTenantTrue()).thenReturn(Optional.of(systemTenant));

    Integration systemIntegration = new Integration();
    systemIntegration.setId(1);
    systemIntegration.setName("Ezkey System Admin");
    when(integrationRepository.findByIsSystemIntegrationTrue())
        .thenReturn(Optional.of(systemIntegration));

    EzkeyAdmin admin = new EzkeyAdmin(USERNAME, AdminType.GLOBAL_ADMIN);
    admin.setEmail(EMAIL);
    admin.setFirstName("Admin");
    admin.setLastName("Docker");
    admin.setEnrollment(null);
    when(adminRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));

    when(signatureService.generateEd25519KeyPair()).thenReturn(new Ed25519KeyPair("priv", "pub"));
    when(signatureService.generateProofToken()).thenReturn(PROOF_TOKEN);
    when(signatureService.generateSecureChallenge(6)).thenReturn(CHALLENGE);
    when(qrCodePayloadService.composePayload(any(), any())).thenReturn("{}");
    when(qrCodeAsciiRenderer.renderAscii(any())).thenReturn("QRLINE1\nQRLINE2");

    when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(
            invocation -> {
              Enrollment e = invocation.getArgument(0);
              e.setEnrollmentId(99);
              return e;
            });
    when(adminRepository.save(any(EzkeyAdmin.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminRecoveryService.RecoveryCodesResult codes =
        new AdminRecoveryService.RecoveryCodesResult(List.of(RECOVERY_CODE), List.of("$2a$hashed"));
    when(recoveryService.generateRecoveryCodes()).thenReturn(codes);

    service.bootstrapAdminMfa();

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains(PROOF_TOKEN);
    assertThat(joined).contains(String.valueOf(CHALLENGE));
    assertThat(joined).contains("QRLINE1");
    assertThat(joined).contains("RECOVERY CODES:");
    assertThat(joined).contains("bootstrap-credentials.json (0600)");
    assertThat(joined).doesNotContain(RECOVERY_CODE);
    assertThat(joined).doesNotContain(EMAIL);
    assertThat(joined).contains(USERNAME);

    ArgumentCaptor<Enrollment> enrollmentCaptor = ArgumentCaptor.forClass(Enrollment.class);
    verify(enrollmentRepository).save(enrollmentCaptor.capture());
    Enrollment saved = enrollmentCaptor.getValue();
    assertThat(saved.getExpiresAt()).isNotNull();
    assertThat(saved.getExpiresAt())
        .isAfter(OffsetDateTime.now().plusHours(23))
        .isBefore(OffsetDateTime.now().plusHours(25));

    verify(bootstrapCredentialsFileExporter)
        .exportIfEnabled(any(Enrollment.class), anyString(), anyString(), any());
  }
}
