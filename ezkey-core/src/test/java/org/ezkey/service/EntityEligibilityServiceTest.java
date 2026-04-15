/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EntityEligibilityServiceTest
 * Description: Unit tests for EntityEligibilityService boolean checks and guard methods.
 */

package org.ezkey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntityEligibilityService}.
 *
 * <p>Covers boolean {@code is*Operational} checks and guard {@code ensure*Operational} methods for
 * tenants, integrations, and enrollments.
 *
 * @author Ezkey contributors
 * @since 2025
 */
class EntityEligibilityServiceTest {

  private EntityEligibilityService service;

  @BeforeEach
  void setUp() {
    service = new EntityEligibilityService();
  }

  // ===== isTenantOperational =====

  @Nested
  @DisplayName("isTenantOperational")
  class IsTenantOperational {

    @Test
    @DisplayName("returns false for null tenant")
    void nullTenant_returnsFalse() {
      assertThat(service.isTenantOperational(null)).isFalse();
    }

    @Test
    @DisplayName("returns false for inactive tenant")
    void inactiveTenant_returnsFalse() {
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(false);
      assertThat(service.isTenantOperational(tenant)).isFalse();
    }

    @Test
    @DisplayName("returns false when active flag is null")
    void nullActiveTenant_returnsFalse() {
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(null);
      assertThat(service.isTenantOperational(tenant)).isFalse();
    }

    @Test
    @DisplayName("returns true for active tenant")
    void activeTenant_returnsTrue() {
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(true);
      assertThat(service.isTenantOperational(tenant)).isTrue();
    }
  }

  // ===== isIntegrationOperational =====

  @Nested
  @DisplayName("isIntegrationOperational")
  class IsIntegrationOperational {

    @Test
    @DisplayName("returns false for null integration")
    void nullIntegration_returnsFalse() {
      assertThat(service.isIntegrationOperational(null)).isFalse();
    }

    @Test
    @DisplayName("returns false when integration is RETIRED")
    void retiredIntegration_returnsFalse() {
      Integration integration = new Integration();
      integration.setLifecycleStatus(IntegrationLifecycleStatus.RETIRED);
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(true);
      integration.setTenant(tenant);
      assertThat(service.isIntegrationOperational(integration)).isFalse();
    }

    @Test
    @DisplayName("returns false when tenant is inactive")
    void activeTenantInactive_returnsFalse() {
      Integration integration = new Integration();
      integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(false);
      integration.setTenant(tenant);
      assertThat(service.isIntegrationOperational(integration)).isFalse();
    }

    @Test
    @DisplayName("returns true when integration is ACTIVE and tenant is active")
    void activeIntegrationActiveTenant_returnsTrue() {
      Integration integration = new Integration();
      integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(true);
      integration.setTenant(tenant);
      assertThat(service.isIntegrationOperational(integration)).isTrue();
    }
  }

  // ===== isEnrollmentOperational =====

  @Nested
  @DisplayName("isEnrollmentOperational")
  class IsEnrollmentOperational {

    @Test
    @DisplayName("returns false for null enrollment")
    void nullEnrollment_returnsFalse() {
      assertThat(service.isEnrollmentOperational(null)).isFalse();
    }

    @Test
    @DisplayName("returns false when enrollment status is not VERIFIED")
    void nonVerifiedStatus_returnsFalse() {
      Enrollment enrollment = new Enrollment();
      enrollment.setStatus(EnrollmentStatus.CREATED);
      enrollment.setActive(true);
      assertThat(service.isEnrollmentOperational(enrollment)).isFalse();
    }

    @Test
    @DisplayName("returns false when enrollment is VERIFIED but not active")
    void verifiedInactive_returnsFalse() {
      Enrollment enrollment = new Enrollment();
      enrollment.setStatus(EnrollmentStatus.VERIFIED);
      enrollment.setActive(false);
      assertThat(service.isEnrollmentOperational(enrollment)).isFalse();
    }

    @Test
    @DisplayName("returns true when enrollment is VERIFIED and active")
    void verifiedActive_returnsTrue() {
      Enrollment enrollment = new Enrollment();
      enrollment.setStatus(EnrollmentStatus.VERIFIED);
      enrollment.setActive(true);
      assertThat(service.isEnrollmentOperational(enrollment)).isTrue();
    }
  }

  // ===== ensureTenantOperational =====

  @Nested
  @DisplayName("ensureTenantOperational")
  class EnsureTenantOperational {

    @Test
    @DisplayName("throws TenantInactiveException for null tenant")
    void nullTenant_throws() {
      assertThatThrownBy(() -> service.ensureTenantOperational(null))
          .isInstanceOf(TenantInactiveException.class);
    }

    @Test
    @DisplayName("throws TenantInactiveException for inactive tenant")
    void inactiveTenant_throws() {
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setTenantId(1);
      tenant.setActive(false);
      assertThatThrownBy(() -> service.ensureTenantOperational(tenant))
          .isInstanceOf(TenantInactiveException.class);
    }

    @Test
    @DisplayName("does not throw for active tenant")
    void activeTenant_doesNotThrow() {
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(true);
      service.ensureTenantOperational(tenant); // should not throw
    }
  }

  // ===== ensureIntegrationOperational =====

  @Nested
  @DisplayName("ensureIntegrationOperational")
  class EnsureIntegrationOperational {

    @Test
    @DisplayName("throws IntegrationLifecycleStateException for null integration")
    void nullIntegration_throws() {
      assertThatThrownBy(() -> service.ensureIntegrationOperational(null))
          .isInstanceOf(IntegrationLifecycleStateException.class);
    }

    @Test
    @DisplayName("throws IntegrationLifecycleStateException when integration is RETIRED")
    void retiredIntegration_throws() {
      Integration integration = new Integration();
      integration.setLifecycleStatus(IntegrationLifecycleStatus.RETIRED);
      assertThatThrownBy(() -> service.ensureIntegrationOperational(integration))
          .isInstanceOf(IntegrationLifecycleStateException.class);
    }

    @Test
    @DisplayName("throws TenantInactiveException when integration is ACTIVE but tenant is inactive")
    void activeIntegrationInactiveTenant_throwsTenantInactive() {
      Integration integration = new Integration();
      integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setTenantId(1);
      tenant.setActive(false);
      integration.setTenant(tenant);
      assertThatThrownBy(() -> service.ensureIntegrationOperational(integration))
          .isInstanceOf(TenantInactiveException.class);
    }

    @Test
    @DisplayName("does not throw when integration is ACTIVE and tenant is active")
    void activeIntegrationActiveTenant_doesNotThrow() {
      Integration integration = new Integration();
      integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
      Tenant tenant = new Tenant("Acme", "test");
      tenant.setActive(true);
      integration.setTenant(tenant);
      service.ensureIntegrationOperational(integration); // should not throw
    }
  }

  // ===== ensureEnrollmentOperational =====

  @Nested
  @DisplayName("ensureEnrollmentOperational")
  class EnsureEnrollmentOperational {

    @Test
    @DisplayName("throws EnrollmentInactiveException for non-operational enrollment")
    void nonOperational_throws() {
      Enrollment enrollment = new Enrollment();
      enrollment.setStatus(EnrollmentStatus.REVOKED);
      enrollment.setActive(false);
      assertThatThrownBy(() -> service.ensureEnrollmentOperational(enrollment))
          .isInstanceOf(EnrollmentInactiveException.class);
    }

    @Test
    @DisplayName("does not throw for operational enrollment")
    void operational_doesNotThrow() {
      Enrollment enrollment = new Enrollment();
      enrollment.setStatus(EnrollmentStatus.VERIFIED);
      enrollment.setActive(true);
      service.ensureEnrollmentOperational(enrollment); // should not throw
    }
  }
}
