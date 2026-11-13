/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ActivationCodeBearerSecurityWebMvcTest
 * Description: Deferred-onboarding activation codes never authenticate Admin API requests (#1122).
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminCookieCsrfFilter;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminTokenAuthenticationFilter;
import org.ezkey.admin.security.EvaluatorTempAccessFilter;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.admin.service.AdminTokenValidationService;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.integration.mapper.IntegrationControllerMapper;
import org.ezkey.integration.service.IntegrationService;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Filter-chain regression for #1122: a deferred-onboarding activation code ({@code
 * ezkey_activation_…}) must never authenticate an Admin API request.
 *
 * <p>Runs the real {@link AdminTokenAuthenticationFilter} and {@link AdminTokenValidationService}
 * against an in-memory token store. The activation code is minted through the real {@link
 * AdminProvisioningService#createTenantAdmin} in {@code ACTIVATION_CODE} mode, which is the same
 * call anonymous evaluator signup makes, so the persisted row has the production shape.
 *
 * @since 2026
 */
@WebMvcTest(controllers = {IntegrationController.class, AdminProvisioningController.class})
@Import({
  SecurityConfig.class,
  TrustedProxyConfig.class,
  ActivationCodeBearerSecurityWebMvcTest.RealTokenFilterBeans.class
})
@DisplayName("Activation-code bearers never authenticate Admin API requests (#1122)")
class ActivationCodeBearerSecurityWebMvcTest {

  private static final String INTEGRATIONS_PATH = "/api/v1/integrations";
  private static final String CREATE_TENANT_ADMIN_PATH = "/api/v1/admins/tenant";
  private static final String CREATE_TENANT_ADMIN_BODY =
      "{\"username\":\"peer.admin\",\"onboardingMode\":\"ACTIVATION_CODE\"}";
  private static final int TENANT_ID = 3;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminTokenRepository tokenRepository;

  @MockitoBean private IntegrationService integrationService;
  @MockitoBean private IntegrationControllerMapper integrationControllerMapper;
  @MockitoBean private EzkeyAdminRepository ezkeyAdminRepository;
  @MockitoBean private AccessControlService accessControlService;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private EnrollmentRevocationService enrollmentRevocationService;

  @MockitoBean private AdminProvisioningService provisioningControllerService;
  @MockitoBean private QrCodeGeneratorService qrCodeGeneratorService;
  @MockitoBean private QrCodePayloadService qrCodePayloadService;

  private final Map<String, AdminToken> tokensByHash = new HashMap<>();

  private final TenantRepository mintTenantRepository = mock(TenantRepository.class);
  private final EzkeyAdminRepository mintAdminRepository = mock(EzkeyAdminRepository.class);
  private final IntegrationRepository mintIntegrationRepository = mock(IntegrationRepository.class);
  private final EnrollmentRepository mintEnrollmentRepository = mock(EnrollmentRepository.class);
  private final SignatureService mintSignatureService = mock(SignatureService.class);
  private final AdminSecurityProperties mintSecurityProperties =
      mock(AdminSecurityProperties.class);

  private AdminProvisioningService realProvisioningService;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    // Activation-code rejection leaves the request unauthenticated; a leftover principal from a
    // prior WebMvcTest in the same JVM would turn the expected 401 into 403 (AccessDenied).
    SecurityContextHolder.clearContext();
    tokensByHash.clear();
    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(anyString()))
        .thenAnswer(invocation ->
            Optional.ofNullable(tokensByHash.get(invocation.<String>getArgument(0)))
                .filter(token -> Boolean.TRUE.equals(token.getActive())));
    when(tokenRepository.save(any(AdminToken.class)))
        .thenAnswer(invocation -> {
          AdminToken token = invocation.getArgument(0);
          if (token.getTokenId() == null) {
            token.setTokenId(tokensByHash.size() + 1);
          }
          tokensByHash.put(token.getBearerTokenHash(), token);
          return token;
        });
    when(tokenRepository.touchLastUsedIfActive(anyInt(), any(), any())).thenReturn(1);
    when(tokenRepository.deactivateTokensForAdminByPurpose(anyInt(), any()))
        .thenAnswer(invocation -> {
          Integer adminId = invocation.getArgument(0);
          AdminTokenPurpose purpose = invocation.getArgument(1);
          int revoked = 0;
          for (AdminToken token : tokensByHash.values()) {
            if (Boolean.TRUE.equals(token.getActive())
                && token.getTokenPurpose() == purpose
                && adminId.equals(token.getAdmin().getAdminId())) {
              token.setActive(false);
              revoked++;
            }
          }
          return revoked;
        });
    when(integrationService.findByFilters(any(), any(), eq(false), any(), any(), any(), any()))
        .thenReturn(Page.empty());

    tenant = new Tenant("Preview", "preview");
    tenant.setTenantId(TENANT_ID);
    tenant.setActive(true);
    tenant.setIsSystemTenant(false);

    EzkeyAdmin systemCreator = new EzkeyAdmin("system", AdminType.GLOBAL_ADMIN);
    systemCreator.setAdminId(1);

    when(mintTenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    when(mintSecurityProperties.getMaxTenantAdminsPerTenant()).thenReturn(10);
    when(mintAdminRepository.findById(1)).thenReturn(Optional.of(systemCreator));
    when(mintAdminRepository.save(any(EzkeyAdmin.class)))
        .thenAnswer(invocation -> {
          EzkeyAdmin admin = invocation.getArgument(0);
          if (admin.getAdminId() == null) {
            admin.setAdminId(42);
          }
          return admin;
        });

    Integration systemIntegration = new Integration();
    systemIntegration.setId(1);
    systemIntegration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
    when(mintIntegrationRepository.findByIsSystemIntegrationTrueAndLifecycleStatus(
            IntegrationLifecycleStatus.ACTIVE))
        .thenReturn(Optional.of(systemIntegration));
    when(mintSignatureService.generateEd25519KeyPair())
        .thenReturn(new Ed25519KeyPair("privb64", "pubb64"));
    when(mintSignatureService.generateProofToken()).thenReturn("ezkey_proof_demo");
    when(mintSignatureService.generateSecureChallenge(6)).thenReturn(654321);
    when(mintEnrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> {
          Enrollment enrollment = invocation.getArgument(0);
          enrollment.setEnrollmentId(123);
          return enrollment;
        });

    realProvisioningService =
        new AdminProvisioningService(
            mintTenantRepository,
            mintAdminRepository,
            mintIntegrationRepository,
            mintEnrollmentRepository,
            mock(AdminRecoveryService.class),
            mintSignatureService,
            mintSecurityProperties,
            tokenRepository);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Evaluator-signup activation code as Bearer on GET /integrations returns 401")
  void activationCodeBearerOnIntegrationListIsUnauthorized() throws Exception {
    String activationCode = mintEvaluatorActivationCode();

    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + activationCode))
        .andExpect(status().isUnauthorized());

    verify(integrationService, never())
        .findByFilters(any(), any(), any(Boolean.class), any(), any(), any(), any());
    verify(tokenRepository, never()).touchLastUsedIfActive(anyInt(), any(), any());
  }

  @Test
  @DisplayName("Activation code as Bearer on POST /admins/tenant (Mode C peer mint) returns 401")
  void activationCodeBearerOnPeerTenantAdminCreateIsUnauthorized() throws Exception {
    String activationCode = mintEvaluatorActivationCode();

    mockMvc
        .perform(
            post(CREATE_TENANT_ADMIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_TENANT_ADMIN_BODY)
                .header("Authorization", "Bearer " + activationCode))
        .andExpect(status().isUnauthorized());

    verify(provisioningControllerService, never())
        .createTenantAdmin(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Activation code persisted with the legacy SESSION purpose still returns 401")
  void legacySessionPurposeActivationRowIsUnauthorized() throws Exception {
    String activationCode = mintEvaluatorActivationCode();
    tokensByHash
        .get(SensitiveDataHasher.sha256Hex(activationCode))
        .setTokenPurpose(AdminTokenPurpose.SESSION);

    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + activationCode))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("SESSION row for a PENDING_ACTIVATION admin without enrollment returns 401")
  void sessionRowForPendingAdminWithoutEnrollmentIsUnauthorized() throws Exception {
    mintEvaluatorActivationCode();
    EzkeyAdmin pendingAdmin = tokensByHash.values().iterator().next().getAdmin();
    String opaqueBearer = storeToken(pendingAdmin, AdminTokenPurpose.SESSION);

    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + opaqueBearer))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Activation code stays 401 after /activate; only an MFA-verified SESSION gets 200")
  void activationCodeNeverAuthenticatesUntilMfaVerifiedSession() throws Exception {
    String activationCode = mintEvaluatorActivationCode();

    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + activationCode))
        .andExpect(status().isUnauthorized());

    AdminProvisioningService.ProvisioningResult activated =
        realProvisioningService.activatePendingAdmin(activationCode);
    EzkeyAdmin admin = activated.admin();
    assertThat(admin.getLifecycleStatus()).isEqualTo(AdminLifecycleStatus.ACTIVE);
    assertThat(admin.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.CREATED);

    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + activationCode))
        .andExpect(status().isUnauthorized());

    String preBindSession = storeToken(admin, AdminTokenPurpose.SESSION);
    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + preBindSession))
        .andExpect(status().isUnauthorized());

    admin.getEnrollment().setStatus(EnrollmentStatus.VERIFIED);
    String mfaSession = storeToken(admin, AdminTokenPurpose.SESSION);
    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + mfaSession))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("EVALUATOR_TEMP after /activate (pre-bind) still authenticates")
  void evaluatorTempAfterActivationStillAuthenticates() throws Exception {
    String activationCode = mintEvaluatorActivationCode();
    EzkeyAdmin admin = realProvisioningService.activatePendingAdmin(activationCode).admin();

    String tempBearer = storeToken(admin, AdminTokenPurpose.EVALUATOR_TEMP);

    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + tempBearer))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Unprefixed ACTIVATION / RECOVERY token of an MFA-verified admin returns 401 (allowlist"
          + " only)")
  void nonSessionPurposeWithoutActivationPrefixIsUnauthorizedForVerifiedAdmin() throws Exception {
    String activationCode = mintEvaluatorActivationCode();
    EzkeyAdmin admin = realProvisioningService.activatePendingAdmin(activationCode).admin();
    admin.getEnrollment().setStatus(EnrollmentStatus.VERIFIED);

    String unprefixedActivation = storeToken(admin, AdminTokenPurpose.ACTIVATION);
    String unprefixedRecovery = storeToken(admin, AdminTokenPurpose.RECOVERY);
    String control = storeToken(admin, AdminTokenPurpose.SESSION);
    assertThat(unprefixedActivation).doesNotStartWith(AdminAuditConstants.ACTIVATION_TOKEN_PREFIX);

    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + unprefixedActivation))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + unprefixedRecovery))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + control))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Successful /activate revokes every other outstanding activation code of the admin")
  void activationRevokesSiblingActivationCodes() throws Exception {
    String presentedCode = mintEvaluatorActivationCode();
    EzkeyAdmin pendingAdmin =
        tokensByHash.get(SensitiveDataHasher.sha256Hex(presentedCode)).getAdmin();
    String siblingCode =
        storeToken(
            pendingAdmin,
            AdminTokenPurpose.ACTIVATION,
            AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "sibling");
    AdminToken siblingRow = tokensByHash.get(SensitiveDataHasher.sha256Hex(siblingCode));
    assertThat(siblingRow.getActive()).isTrue();

    realProvisioningService.activatePendingAdmin(presentedCode);

    assertThat(siblingRow.getActive()).isFalse();
    assertThatThrownBy(() -> realProvisioningService.activatePendingAdmin(siblingCode))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Invalid activation code");
    mockMvc
        .perform(get(INTEGRATIONS_PATH).header("Authorization", "Bearer " + siblingCode))
        .andExpect(status().isUnauthorized());
  }

  private String mintEvaluatorActivationCode() {
    AdminProvisioningService.ProvisioningResult result =
        realProvisioningService.createTenantAdmin(
            "evaluator.admin",
            null,
            null,
            "Evaluator",
            "Admin",
            TENANT_ID,
            AdminOnboardingMode.ACTIVATION_CODE,
            new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null));
    assertThat(result.admin().getLifecycleStatus())
        .isEqualTo(AdminLifecycleStatus.PENDING_ACTIVATION);
    assertThat(result.admin().getEnrollment()).isNull();
    assertThat(result.activationCode()).startsWith(AdminAuditConstants.ACTIVATION_TOKEN_PREFIX);
    return result.activationCode();
  }

  private String storeToken(EzkeyAdmin admin, AdminTokenPurpose purpose) {
    return storeToken(admin, purpose, "ezkey_");
  }

  private String storeToken(EzkeyAdmin admin, AdminTokenPurpose purpose, String prefix) {
    String plain = prefix + UUID.randomUUID().toString().replace("-", "");
    AdminToken token =
        new AdminToken(
            SensitiveDataHasher.sha256Hex(plain),
            admin,
            admin.getAdminType().name(),
            OffsetDateTime.now().plusHours(2),
            purpose);
    token.setTenant(admin.getTenant());
    tokenRepository.save(token);
    return plain;
  }

  /** Real token filter + validation service; other chain filters pass through. */
  @TestConfiguration
  static class RealTokenFilterBeans {

    @Bean
    @Primary
    AdminTokenValidationService adminTokenValidationService(AdminTokenRepository repository) {
      return new AdminTokenValidationService(repository, new AdminTokenRotationProperties());
    }

    @Bean
    @Primary
    AdminTokenAuthenticationFilter adminTokenAuthenticationFilter(
        AdminTokenValidationService adminTokenValidationService) {
      AdminBrowserSessionCookieProperties cookieProperties =
          new AdminBrowserSessionCookieProperties();
      cookieProperties.setBrowserSessionCookieEnabled(false);
      return new AdminTokenAuthenticationFilter(adminTokenValidationService, cookieProperties);
    }

    @Bean
    @Primary
    AdminRateLimitFilter adminRateLimitFilter() {
      return AdminCorsTestFilterBeans.passThrough(AdminRateLimitFilter.class);
    }

    @Bean
    @Primary
    AdminCookieCsrfFilter adminCookieCsrfFilter() {
      return AdminCorsTestFilterBeans.passThrough(AdminCookieCsrfFilter.class);
    }

    @Bean
    @Primary
    EvaluatorTempAccessFilter evaluatorTempAccessFilter() {
      return AdminCorsTestFilterBeans.passThrough(EvaluatorTempAccessFilter.class);
    }
  }
}
