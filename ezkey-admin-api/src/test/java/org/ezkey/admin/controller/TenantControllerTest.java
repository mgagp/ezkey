/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantControllerTest
 * Description: Unit tests for TenantController listTenants (paginated) endpoint.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.dto.request.TenantActivateRequestDto;
import org.ezkey.admin.dto.request.TenantDeactivateRequestDto;
import org.ezkey.admin.dto.response.TenantResponseDto;
import org.ezkey.admin.mapper.TenantMapper;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.TenantService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.TenantRepository;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for TenantController listTenants endpoint (paginated list with optional filters).
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li><b>listTenants:</b> GlobalAdmin receives paginated response; filters (tenantName, active)
 *       are passed to repository; 403 for non-GlobalAdmin
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantController listTenants Tests")
@SuppressWarnings("unchecked")
class TenantControllerTest {

  @Mock private AdminProvisioningService provisioningService;
  @Mock private TenantRepository tenantRepository;
  @Mock private TenantService tenantService;
  @Mock private TenantMapper tenantMapper;
  @Mock private AuditLogService auditLogService;
  @Mock private HttpServletRequest httpRequest;

  private TenantController controller;

  private Tenant tenant1;
  private Tenant tenant2;
  private TenantResponseDto dto1;
  private TenantResponseDto dto2;

  @BeforeEach
  void setUp() {
    controller =
        new TenantController(
            provisioningService, tenantRepository, tenantService, tenantMapper, auditLogService);

    tenant1 = new Tenant("Tenant One", "Desc 1");
    tenant1.setTenantId(1);
    tenant1.setActive(true);
    tenant1.setCreatedAt(OffsetDateTime.now());

    tenant2 = new Tenant("Tenant Two", "Desc 2");
    tenant2.setTenantId(2);
    tenant2.setActive(false);
    tenant2.setCreatedAt(OffsetDateTime.now());

    dto1 =
        new TenantResponseDto(
            1,
            0L,
            "Tenant One",
            "Desc 1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            false,
            null);
    dto2 =
        new TenantResponseDto(
            2,
            0L,
            "Tenant Two",
            "Desc 2",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            null);
  }

  @Nested
  @DisplayName("List tenants (GlobalAdmin)")
  class ListTenantsGlobalAdmin {

    @BeforeEach
    void setGlobalAdminAuth() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  principal,
                  null,
                  List.of(
                      new SimpleGrantedAuthority("ROLE_ADMIN"),
                      new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN"))));
    }

    @Test
    @DisplayName("Returns paginated list with content and metadata")
    void returnsPaginatedList() {
      Pageable pageable = PageRequest.of(0, 20);
      Page<Tenant> repoPage = new PageImpl<>(List.of(tenant1, tenant2), pageable, 2);

      when(tenantRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);
      when(tenantMapper.toResponseDto(tenant1)).thenReturn(dto1);
      when(tenantMapper.toResponseDto(tenant2)).thenReturn(dto2);

      ResponseEntity<Page<TenantResponseDto>> response =
          controller.listTenants(
              null, null, pageable, SecurityContextHolder.getContext().getAuthentication());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      Page<TenantResponseDto> body = response.getBody();
      assertNotNull(body);
      assertEquals(2, body.getTotalElements());
      assertEquals(2, body.getContent().size());
      assertEquals(1, body.getContent().get(0).tenantId());
      assertEquals(2, body.getContent().get(1).tenantId());
      verify(tenantRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Passes tenantName filter to repository")
    void passesTenantNameFilter() {
      Pageable pageable = PageRequest.of(0, 20);
      Page<Tenant> repoPage = new PageImpl<>(List.of(tenant1), pageable, 1);

      when(tenantRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);
      when(tenantMapper.toResponseDto(tenant1)).thenReturn(dto1);

      ResponseEntity<Page<TenantResponseDto>> response =
          controller.listTenants(
              "One", null, pageable, SecurityContextHolder.getContext().getAuthentication());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(1, response.getBody().getContent().size());
      verify(tenantRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Passes active filter to repository")
    void passesActiveFilter() {
      Pageable pageable = PageRequest.of(0, 20);
      Page<Tenant> repoPage = new PageImpl<>(List.of(tenant1), pageable, 1);

      when(tenantRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);
      when(tenantMapper.toResponseDto(tenant1)).thenReturn(dto1);

      ResponseEntity<Page<TenantResponseDto>> response =
          controller.listTenants(
              null, true, pageable, SecurityContextHolder.getContext().getAuthentication());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(1, response.getBody().getContent().size());
      verify(tenantRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Returns empty page when no tenants match")
    void returnsEmptyPage() {
      Pageable pageable = PageRequest.of(0, 20);
      Page<Tenant> repoPage = new PageImpl<>(List.of(), pageable, 0);

      when(tenantRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);

      ResponseEntity<Page<TenantResponseDto>> response =
          controller.listTenants(
              null, null, pageable, SecurityContextHolder.getContext().getAuthentication());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(0, response.getBody().getTotalElements());
      assertEquals(0, response.getBody().getContent().size());
    }
  }

  @Nested
  @DisplayName("List tenants (non-GlobalAdmin)")
  class ListTenantsForbidden {

    @BeforeEach
    void setTenantAdminAuth() {
      AdminPrincipal principal = new AdminPrincipal(2, AdminType.TENANT_ADMIN, 1, null);
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  principal,
                  null,
                  List.of(
                      new SimpleGrantedAuthority("ROLE_ADMIN"),
                      new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));
    }

    @Test
    @DisplayName("Returns 403 when not GlobalAdmin")
    void returns403WhenNotGlobalAdmin() {
      ResponseEntity<Page<TenantResponseDto>> response =
          controller.listTenants(
              null,
              null,
              PageRequest.of(0, 20),
              SecurityContextHolder.getContext().getAuthentication());

      assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
      // Repository is not called when principal is not GlobalAdmin
    }
  }

  @Nested
  @DisplayName("Tenant activate / deactivate (audit on state change only)")
  class TenantToggleAuditGating {

    private static final String LONG_REASON =
        "Operational justification for audit trail (min ten chars).";

    @BeforeEach
    void setGlobalAdminAuth() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  principal,
                  null,
                  List.of(
                      new SimpleGrantedAuthority("ROLE_ADMIN"),
                      new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN"))));
    }

    @Test
    @DisplayName("activateTenant does not log audit when service returns false (idempotent)")
    void activateNoAuditWhenNoStateChange() {
      when(tenantService.activateTenant(eq(2), isA(AdminPrincipal.class))).thenReturn(false);

      ResponseEntity<Void> response =
          controller.activateTenant(
              2,
              new TenantActivateRequestDto(LONG_REASON),
              SecurityContextHolder.getContext().getAuthentication(),
              httpRequest);

      assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
      verify(auditLogService, never()).log(any());
    }

    @Test
    @DisplayName("activateTenant logs audit when service returns true")
    void activateLogsAuditWhenStateChanges() {
      when(tenantService.activateTenant(eq(2), isA(AdminPrincipal.class))).thenReturn(true);

      ResponseEntity<Void> response =
          controller.activateTenant(
              2,
              new TenantActivateRequestDto(LONG_REASON),
              SecurityContextHolder.getContext().getAuthentication(),
              httpRequest);

      assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
      verify(auditLogService, times(1)).log(any());
    }

    @Test
    @DisplayName("deactivateTenant does not log audit when service returns false (idempotent)")
    void deactivateNoAuditWhenNoStateChange() {
      when(tenantService.deactivateTenant(eq(2), isA(AdminPrincipal.class))).thenReturn(false);

      ResponseEntity<Void> response =
          controller.deactivateTenant(
              2,
              new TenantDeactivateRequestDto(LONG_REASON),
              SecurityContextHolder.getContext().getAuthentication(),
              httpRequest);

      assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
      verify(auditLogService, never()).log(any());
    }

    @Test
    @DisplayName("deactivateTenant logs audit when service returns true")
    void deactivateLogsAuditWhenStateChanges() {
      when(tenantService.deactivateTenant(eq(2), isA(AdminPrincipal.class))).thenReturn(true);

      ResponseEntity<Void> response =
          controller.deactivateTenant(
              2,
              new TenantDeactivateRequestDto(LONG_REASON),
              SecurityContextHolder.getContext().getAuthentication(),
              httpRequest);

      assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
      verify(auditLogService, times(1)).log(any());
    }
  }
}
