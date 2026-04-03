package org.ezkey.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.integration.SystemTenantTestConstants;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.integration.exception.IntegrationHasEnrollmentsException;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.ezkey.integration.mapper.IntegrationServiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

  @Mock private IntegrationRepository repository;

  @Mock private IntegrationServiceMapper mapper;

  @Mock private TenantRepository tenantRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @InjectMocks private IntegrationService service;

  private IntegrationCreateRequest buildRequest(boolean withName) {
    IntegrationCreateRequest req = new IntegrationCreateRequest();
    req.setCode("test-code");
    if (withName) {
      req.setName("Name EN");
      req.setDescription("Desc EN");
    }
    return req;
  }

  private Integration buildMappedEntity(boolean withName) {
    Integration entity = new Integration();
    entity.setLifecycleStatus(null);
    entity.setCode("test-code");
    if (withName) {
      entity.setName("Name EN");
      entity.setDescription("Desc EN");
    }
    return entity;
  }

  private EzkeyAdmin createTestGlobalAdmin() {
    EzkeyAdmin admin = new EzkeyAdmin("testadmin", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(1);
    Tenant systemTenant =
        new Tenant(
            SystemTenantTestConstants.DEFAULT_SYSTEM_TENANT_NAME,
            SystemTenantTestConstants.DEFAULT_SYSTEM_TENANT_DESCRIPTION);
    systemTenant.setTenantId(1);
    admin.setTenant(systemTenant);
    return admin;
  }

  private Tenant createSystemTenant() {
    Tenant tenant =
        new Tenant(
            SystemTenantTestConstants.DEFAULT_SYSTEM_TENANT_NAME,
            SystemTenantTestConstants.DEFAULT_SYSTEM_TENANT_DESCRIPTION);
    tenant.setTenantId(1);
    return tenant;
  }

  @Nested
  class CreateIntegration {
    @Test
    @DisplayName(
        "createIntegration with name/description should set audit + active + tenant and return"
            + " mapped response")
    void create_withName() {
      IntegrationCreateRequest req = buildRequest(true);
      Integration mapped = buildMappedEntity(true);
      Integration saved = buildMappedEntity(true);
      saved.setId(42);
      IntegrationCreateResponse expectedResponse = new IntegrationCreateResponse();
      expectedResponse.setId(42);

      EzkeyAdmin admin = createTestGlobalAdmin();
      Tenant systemTenant = createSystemTenant();

      when(mapper.toEntity(req)).thenReturn(mapped);
      when(tenantRepository.findByIsSystemTenantTrue()).thenReturn(Optional.of(systemTenant));
      when(repository.save(any(Integration.class))).thenReturn(saved);
      when(mapper.toCreateResponse(saved)).thenReturn(expectedResponse);

      IntegrationCreateResponse out = service.createIntegration(req, admin);

      assertThat(out.getId()).isEqualTo(42);

      ArgumentCaptor<Integration> captor = ArgumentCaptor.forClass(Integration.class);
      verify(repository).save(captor.capture());
      Integration toSave = captor.getValue();
      assertThat(toSave.getActive()).isTrue();
      assertThat(toSave.getCreatedAt()).isNotNull();
      assertThat(toSave.getCreatedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
      assertThat(toSave.getName()).isEqualTo("Name EN");
      assertThat(toSave.getDescription()).isEqualTo("Desc EN");
      assertThat(toSave.getTenant()).isEqualTo(systemTenant);
      assertThat(toSave.getCreatedByAdmin()).isEqualTo(admin);
      verify(mapper).toEntity(req);
      verify(mapper).toCreateResponse(saved);
    }

    @Test
    @DisplayName(
        "createIntegration without name should not fail and still set audit + active + tenant")
    void create_withoutName() {
      IntegrationCreateRequest req = buildRequest(false);
      Integration mapped = buildMappedEntity(false);
      Integration saved = buildMappedEntity(false);
      saved.setId(5);
      IntegrationCreateResponse expectedResponse = new IntegrationCreateResponse();
      expectedResponse.setId(5);

      EzkeyAdmin admin = createTestGlobalAdmin();
      Tenant systemTenant = createSystemTenant();

      when(mapper.toEntity(req)).thenReturn(mapped);
      when(tenantRepository.findByIsSystemTenantTrue()).thenReturn(Optional.of(systemTenant));
      when(repository.save(any(Integration.class))).thenReturn(saved);
      when(mapper.toCreateResponse(saved)).thenReturn(expectedResponse);

      IntegrationCreateResponse out = service.createIntegration(req, admin);
      assertThat(out.getId()).isEqualTo(5);
      ArgumentCaptor<Integration> captor = ArgumentCaptor.forClass(Integration.class);
      verify(repository).save(captor.capture());
      Integration toSave = captor.getValue();
      assertThat(toSave.getName()).isNull();
      assertThat(toSave.getDescription()).isNull();
      assertThat(toSave.getActive()).isTrue();
      assertThat(toSave.getCreatedAt()).isNotNull();
      assertThat(toSave.getTenant()).isEqualTo(systemTenant);
      assertThat(toSave.getCreatedByAdmin()).isEqualTo(admin);
    }

    @Test
    @DisplayName("createIntegration rejects inactive tenant")
    void create_whenTenantInactive_throwsTenantInactiveException() {
      IntegrationCreateRequest req = buildRequest(true);
      Integration mapped = buildMappedEntity(true);
      Tenant tenant = new Tenant("Acme", "Inactive tenant");
      tenant.setTenantId(22);
      tenant.setActive(false);

      EzkeyAdmin admin = new EzkeyAdmin("tenantadmin", AdminType.TENANT_ADMIN);
      admin.setAdminId(2);
      admin.setTenant(tenant);

      when(mapper.toEntity(req)).thenReturn(mapped);

      assertThatThrownBy(() -> service.createIntegration(req, admin))
          .isInstanceOf(TenantInactiveException.class)
          .hasMessageContaining("inactive tenant");

      verify(repository, never()).save(any(Integration.class));
    }
  }

  @Test
  void getById_found() {
    Integration integration = new Integration();
    integration.setId(10);
    when(repository.findById(10)).thenReturn(Optional.of(integration));
    Optional<Integration> result = service.getById(10);
    assertThat(result).contains(integration);
  }

  @Test
  void getById_notFound() {
    when(repository.findById(99)).thenReturn(Optional.empty());
    assertThat(service.getById(99)).isEmpty();
  }

  @Test
  void getAll_delegates() {
    when(repository.findAll()).thenReturn(List.of(new Integration(), new Integration()));
    assertThat(service.getAll()).hasSize(2);
    verify(repository).findAll();
  }

  @Test
  void delete_callsRepository_whenRetiredAndWithoutEnrollments() {
    Integration integration = new Integration();
    integration.setId(7);
    integration.setLifecycleStatus(IntegrationLifecycleStatus.RETIRED);
    when(repository.findById(7)).thenReturn(Optional.of(integration));
    when(enrollmentRepository.existsByIntegrationId(7)).thenReturn(false);
    service.delete(7);
    verify(repository).deleteById(7);
  }

  @Test
  void delete_whenEnrollmentsExist_throwsIntegrationHasEnrollmentsException() {
    Integration integration = new Integration();
    integration.setId(10);
    integration.setLifecycleStatus(IntegrationLifecycleStatus.RETIRED);
    when(repository.findById(10)).thenReturn(Optional.of(integration));
    when(enrollmentRepository.existsByIntegrationId(10)).thenReturn(true);
    assertThatThrownBy(() -> service.delete(10))
        .isInstanceOf(IntegrationHasEnrollmentsException.class)
        .hasMessageContaining("enrollments");
    verify(repository, never()).deleteById(eq(10));
  }

  @Test
  void delete_whenNotRetired_throwsIntegrationLifecycleStateException() {
    Integration integration = new Integration();
    integration.setId(11);
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
    when(repository.findById(11)).thenReturn(Optional.of(integration));

    assertThatThrownBy(() -> service.delete(11))
        .isInstanceOf(IntegrationLifecycleStateException.class)
        .hasMessageContaining("retired");

    verify(repository, never()).deleteById(eq(11));
  }

  @Test
  void retire_setsLifecycleToRetired() {
    Integration integration = new Integration();
    integration.setId(12);
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
    when(repository.findById(12)).thenReturn(Optional.of(integration));
    when(repository.save(any(Integration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Integration retired = service.retire(12);

    assertThat(retired.getLifecycleStatus()).isEqualTo(IntegrationLifecycleStatus.RETIRED);
    verify(repository).save(integration);
  }
}
