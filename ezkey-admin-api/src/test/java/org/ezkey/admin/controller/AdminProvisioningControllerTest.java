/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningControllerTest
 * Description: Unit tests for AdminProvisioningController listAdmins endpoint.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ezkey.admin.dto.request.AdminUpdateRequestDto;
import org.ezkey.admin.dto.response.AdminResponseDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for AdminProvisioningController listAdmins endpoint.
 *
 * <p>This test class validates the tenant-based filtering logic for listing administrators. It
 * ensures that GlobalAdmin sees all administrators while TenantAdmin sees only administrators from
 * their tenant.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li><b>listAdmins:</b> GlobalAdmin sees all admins, TenantAdmin sees only own tenant admins,
 *       pagination works correctly, tenant isolation is enforced
 *   <li><b>updateAdmin (PATCH):</b> GlobalAdmin success, stale version (409), not found
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminProvisioningController listAdmins Tests")
class AdminProvisioningControllerTest {

  @Mock private AdminProvisioningService provisioningService;

  @Mock private QrCodeGeneratorService qrCodeGeneratorService;

  @Mock private QrCodePayloadService qrCodePayloadService;

  @Mock private AuditLogService auditLogService;

  @Mock private HttpServletRequest httpRequest;

  private AdminProvisioningController controller;

  private Tenant testTenant;

  private EzkeyAdmin globalAdmin;

  private EzkeyAdmin tenantAdmin1;

  private EzkeyAdmin tenantAdmin2;

  private EzkeyAdmin otherTenantAdmin;

  @BeforeEach
  void setUp() {
    controller =
        new AdminProvisioningController(
            provisioningService, qrCodeGeneratorService, qrCodePayloadService, auditLogService);

    // Setup test tenant
    testTenant = new Tenant();
    testTenant.setTenantId(1);
    testTenant.setTenantName("Test Tenant");

    Tenant otherTenant = new Tenant();
    otherTenant.setTenantId(2);
    otherTenant.setTenantName("Other Tenant");

    // Setup global admin
    globalAdmin = new EzkeyAdmin("globaladmin", AdminType.GLOBAL_ADMIN);
    globalAdmin.setAdminId(1);
    globalAdmin.setEmail("global@example.com");
    globalAdmin.setFirstName("Global");
    globalAdmin.setLastName("Admin");
    globalAdmin.setCreatedAt(OffsetDateTime.now());
    globalAdmin.setActive(true);

    // Setup tenant admins for test tenant
    tenantAdmin1 = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
    tenantAdmin1.setAdminId(2);
    tenantAdmin1.setEmail("tenant1@example.com");
    tenantAdmin1.setFirstName("Tenant");
    tenantAdmin1.setLastName("Admin1");
    tenantAdmin1.setTenant(testTenant);
    tenantAdmin1.setVersion(0L);
    tenantAdmin1.setCreatedAt(OffsetDateTime.now());
    tenantAdmin1.setActive(true);

    tenantAdmin2 = new EzkeyAdmin("tenantadmin2", AdminType.TENANT_ADMIN);
    tenantAdmin2.setAdminId(3);
    tenantAdmin2.setEmail("tenant2@example.com");
    tenantAdmin2.setFirstName("Tenant");
    tenantAdmin2.setLastName("Admin2");
    tenantAdmin2.setTenant(testTenant);
    tenantAdmin2.setCreatedAt(OffsetDateTime.now());
    tenantAdmin2.setActive(true);

    // Setup tenant admin for other tenant
    otherTenantAdmin = new EzkeyAdmin("othertenantadmin", AdminType.TENANT_ADMIN);
    otherTenantAdmin.setAdminId(4);
    otherTenantAdmin.setEmail("other@example.com");
    otherTenantAdmin.setFirstName("Other");
    otherTenantAdmin.setLastName("Admin");
    otherTenantAdmin.setTenant(otherTenant);
    otherTenantAdmin.setCreatedAt(OffsetDateTime.now());
    otherTenantAdmin.setActive(true);
  }

  @Nested
  @DisplayName("GlobalAdmin Listing Tests")
  class GlobalAdminListingTests {

    @BeforeEach
    void setUpGlobalAdmin() {
      setupGlobalAdminAuthentication();
    }

    @Test
    @DisplayName("GlobalAdmin sees all admins across all tenants")
    void globalAdminSeesAllAdmins() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> allAdmins =
          List.of(globalAdmin, tenantAdmin1, tenantAdmin2, otherTenantAdmin);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(allAdmins, pageable, allAdmins.size());

      when(provisioningService.listAdmins(isNull(), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, null);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<AdminResponseDto> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(4, responseBody.getTotalElements());
      assertEquals(4, responseBody.getContent().size());
      verify(provisioningService).listAdmins(isNull(), eq(pageable));
    }

    @Test
    @DisplayName("GlobalAdmin with tenantId filter sees only that tenant's admins")
    void globalAdminWithTenantIdFilterSeesOnlyThatTenantsAdmins() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> tenantOneAdmins = List.of(tenantAdmin1, tenantAdmin2);
      Page<EzkeyAdmin> expectedPage =
          new PageImpl<>(tenantOneAdmins, pageable, tenantOneAdmins.size());

      when(provisioningService.listAdmins(eq(1), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, 1);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<AdminResponseDto> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(2, responseBody.getTotalElements());
      responseBody
          .getContent()
          .forEach(
              admin -> {
                assertNotNull(admin.tenantId());
                assertEquals(1, admin.tenantId());
              });
      verify(provisioningService).listAdmins(eq(1), eq(pageable));
    }

    @Test
    @DisplayName("GlobalAdmin pagination works correctly")
    void globalAdminPaginationWorks() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 2);
      List<EzkeyAdmin> pageContent = List.of(globalAdmin, tenantAdmin1);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(pageContent, pageable, 4);

      when(provisioningService.listAdmins(isNull(), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, null);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<AdminResponseDto> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(4, responseBody.getTotalElements());
      assertEquals(2, responseBody.getContent().size());
      verify(provisioningService).listAdmins(isNull(), eq(pageable));
    }
  }

  @Nested
  @DisplayName("TenantAdmin Listing Tests")
  class TenantAdminListingTests {

    @BeforeEach
    void setUpTenantAdmin() {
      setupTenantAdminAuthentication();
    }

    @Test
    @DisplayName("TenantAdmin sees only admins from their tenant")
    void tenantAdminSeesOnlyOwnTenantAdmins() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> tenantAdmins = List.of(tenantAdmin1, tenantAdmin2);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(tenantAdmins, pageable, tenantAdmins.size());

      when(provisioningService.listAdmins(eq(1), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, null);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<AdminResponseDto> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(2, responseBody.getTotalElements());
      assertEquals(2, responseBody.getContent().size());

      // Verify all returned admins belong to tenant 1
      responseBody
          .getContent()
          .forEach(
              admin -> {
                assertNotNull(admin.tenantId());
                assertEquals(1, admin.tenantId());
              });

      verify(provisioningService).listAdmins(eq(1), eq(pageable));
    }

    @Test
    @DisplayName("TenantAdmin ignores request tenantId and sees only own tenant admins")
    void tenantAdminIgnoresRequestTenantId() {
      // Arrange: TenantAdmin (tenant 1) sends tenantId=999; must be ignored
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> tenantOneAdmins = List.of(tenantAdmin1, tenantAdmin2);
      Page<EzkeyAdmin> expectedPage =
          new PageImpl<>(tenantOneAdmins, pageable, tenantOneAdmins.size());

      when(provisioningService.listAdmins(eq(1), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, 999);

      // Assert: service was called with auth tenant (1), not request tenant (999)
      assertEquals(HttpStatus.OK, response.getStatusCode());
      verify(provisioningService).listAdmins(eq(1), eq(pageable));
    }

    @Test
    @DisplayName("TenantAdmin does not see admins from other tenants")
    void tenantAdminDoesNotSeeOtherTenantAdmins() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> tenantAdmins = List.of(tenantAdmin1, tenantAdmin2);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(tenantAdmins, pageable, tenantAdmins.size());

      when(provisioningService.listAdmins(eq(1), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, null);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<AdminResponseDto> responseBody = response.getBody();
      assertNotNull(responseBody);

      // Verify otherTenantAdmin (tenant 2) is not in the results
      boolean containsOtherTenantAdmin =
          responseBody.getContent().stream()
              .anyMatch(admin -> admin.adminId().equals(otherTenantAdmin.getAdminId()));
      assertEquals(false, containsOtherTenantAdmin);

      verify(provisioningService).listAdmins(eq(1), eq(pageable));
    }

    @Test
    @DisplayName("TenantAdmin pagination works correctly")
    void tenantAdminPaginationWorks() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 1);
      List<EzkeyAdmin> pageContent = List.of(tenantAdmin1);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(pageContent, pageable, 2);

      when(provisioningService.listAdmins(eq(1), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, null);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<AdminResponseDto> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(2, responseBody.getTotalElements());
      assertEquals(1, responseBody.getContent().size());
      verify(provisioningService).listAdmins(eq(1), eq(pageable));
    }

    @Test
    @DisplayName("TenantAdmin with empty tenant sees empty results")
    void tenantAdminWithEmptyTenantSeesEmptyResults() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 20);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

      when(provisioningService.listAdmins(eq(1), any(Pageable.class))).thenReturn(expectedPage);

      // Act
      ResponseEntity<Page<AdminResponseDto>> response = controller.listAdmins(pageable, null);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<AdminResponseDto> responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(0, responseBody.getTotalElements());
      assertTrue(responseBody.getContent().isEmpty());
      verify(provisioningService).listAdmins(eq(1), eq(pageable));
    }
  }

  @Nested
  @DisplayName("Get Administrator by ID")
  class GetAdminByIdTests {

    @BeforeEach
    void setUpGlobalAdmin() {
      setupGlobalAdminAuthentication();
    }

    @Test
    @DisplayName("Returns enrollmentId when admin has linked enrollment")
    void returnsEnrollmentIdWhenLinked() {
      Enrollment enrollment = mock(Enrollment.class);
      when(enrollment.getEnrollmentId()).thenReturn(99);

      EzkeyAdmin admin = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
      admin.setAdminId(2);
      admin.setTenant(testTenant);
      admin.setEnrollment(enrollment);
      admin.setEmail("t@example.com");
      admin.setVersion(0L);
      admin.setCreatedAt(OffsetDateTime.now());
      admin.setActive(true);

      when(provisioningService.getAdminById(eq(2), any())).thenReturn(admin);

      ResponseEntity<AdminResponseDto> response =
          controller.getAdminById(2, SecurityContextHolder.getContext().getAuthentication());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(Integer.valueOf(99), response.getBody().enrollmentId());
    }

    @Test
    @DisplayName("Returns null enrollmentId when admin has no enrollment")
    void returnsNullEnrollmentIdWhenNotLinked() {
      EzkeyAdmin admin = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
      admin.setAdminId(2);
      admin.setTenant(testTenant);
      admin.setEmail("t@example.com");
      admin.setVersion(0L);
      admin.setCreatedAt(OffsetDateTime.now());
      admin.setActive(true);

      when(provisioningService.getAdminById(eq(2), any())).thenReturn(admin);

      ResponseEntity<AdminResponseDto> response =
          controller.getAdminById(2, SecurityContextHolder.getContext().getAuthentication());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertNull(response.getBody().enrollmentId());
    }
  }

  @Nested
  @DisplayName("Update Admin (PATCH) Tests")
  class UpdateAdminTests {

    @BeforeEach
    void setUpGlobalAdmin() {
      setupGlobalAdminAuthentication();
    }

    @Test
    @DisplayName("GlobalAdmin can update admin profile and returns 200")
    void globalAdminCanUpdateAdminProfile() {
      EzkeyAdmin existing = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
      existing.setAdminId(2);
      existing.setTenant(testTenant);
      existing.setEmail("old@example.com");
      existing.setFirstName("Old");
      existing.setLastName("Name");
      existing.setVersion(0L);
      existing.setCreatedAt(OffsetDateTime.now());
      existing.setActive(true);

      EzkeyAdmin updated = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
      updated.setAdminId(2);
      updated.setTenant(testTenant);
      updated.setEmail("updated@example.com");
      updated.setFirstName("Updated");
      updated.setLastName("Name");
      updated.setVersion(1L);
      updated.setCreatedAt(OffsetDateTime.now());
      updated.setActive(true);

      AdminUpdateRequestDto request =
          new AdminUpdateRequestDto(0L, "Updated", "Name", "updated@example.com", null, null);

      when(provisioningService.getAdminById(eq(2), any())).thenReturn(existing);
      when(provisioningService.updateAdmin(eq(2), any(AdminUpdateRequestDto.class), any()))
          .thenReturn(updated);

      ResponseEntity<AdminResponseDto> response =
          controller.updateAdmin(
              2, request, SecurityContextHolder.getContext().getAuthentication(), httpRequest);

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      AdminResponseDto body = response.getBody();
      assertNotNull(body);
      assertEquals("Updated", body.firstName());
      assertEquals("Name", body.lastName());
      assertEquals("updated@example.com", body.email());
      assertEquals(1L, body.version());
      verify(provisioningService).updateAdmin(eq(2), any(AdminUpdateRequestDto.class), any());
    }

    @Test
    @DisplayName("Stale version throws ObjectOptimisticLockingFailureException")
    void staleVersionThrowsOptimisticLockConflict() {
      EzkeyAdmin existing = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
      existing.setAdminId(2);
      existing.setTenant(testTenant);
      existing.setEmail("old@example.com");
      existing.setFirstName("Old");
      existing.setLastName("Name");

      AdminUpdateRequestDto request = new AdminUpdateRequestDto(3L, "New", null, null, null, null);

      when(provisioningService.getAdminById(eq(2), any())).thenReturn(existing);
      when(provisioningService.updateAdmin(eq(2), any(AdminUpdateRequestDto.class), any()))
          .thenThrow(new ObjectOptimisticLockingFailureException(EzkeyAdmin.class, 2));

      assertThrows(
          ObjectOptimisticLockingFailureException.class,
          () ->
              controller.updateAdmin(
                  2, request, SecurityContextHolder.getContext().getAuthentication(), httpRequest));
      verify(provisioningService).updateAdmin(eq(2), any(AdminUpdateRequestDto.class), any());
    }

    @Test
    @DisplayName("Admin not found throws ResourceNotFoundException")
    void adminNotFoundThrowsResourceNotFoundException() {
      EzkeyAdmin existing = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
      existing.setAdminId(999);
      existing.setTenant(testTenant);
      existing.setEmail("old@example.com");
      existing.setFirstName("Old");
      existing.setLastName("Name");

      AdminUpdateRequestDto request =
          new AdminUpdateRequestDto(null, "New", null, null, null, null);

      when(provisioningService.getAdminById(eq(999), any())).thenReturn(existing);
      when(provisioningService.updateAdmin(eq(999), any(AdminUpdateRequestDto.class), any()))
          .thenThrow(new ResourceNotFoundException("Administrator", 999));

      assertThrows(
          ResourceNotFoundException.class,
          () ->
              controller.updateAdmin(
                  999,
                  request,
                  SecurityContextHolder.getContext().getAuthentication(),
                  httpRequest));
    }
  }

  /**
   * Sets up authentication context for GlobalAdmin.
   *
   * <p>Creates an AdminPrincipal with GLOBAL_ADMIN type and null tenantId, then sets it in the
   * Spring Security context.
   */
  private void setupGlobalAdminAuthentication() {
    AdminPrincipal principal =
        new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null); // tenantId null for
    // GlobalAdmin

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            principal,
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /**
   * Sets up authentication context for TenantAdmin.
   *
   * <p>Creates an AdminPrincipal with TENANT_ADMIN type and tenantId=1, then sets it in the Spring
   * Security context.
   */
  private void setupTenantAdminAuthentication() {
    AdminPrincipal principal =
        new AdminPrincipal(2, AdminType.TENANT_ADMIN, 1, null); // tenantId=1 for TenantAdmin

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            principal,
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
