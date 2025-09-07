package org.ezkey.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.IntegrationI18nCreate;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.IntegrationI18n;
import org.ezkey.integration.domain.repository.IntegrationRepository;
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

    @Mock
    private IntegrationRepository repository;

    @Mock
    private IntegrationServiceMapper mapper;

    @InjectMocks
    private IntegrationService service;

    private IntegrationCreateRequest buildRequest(boolean withI18n) {
        IntegrationCreateRequest req = new IntegrationCreateRequest();
        req.setLogo("/logo.png");
        if (withI18n) {
            IntegrationI18nCreate i1 = new IntegrationI18nCreate();
            i1.setLanguage("en");
            i1.setName("Name EN");
            i1.setDescription("Desc EN");
            req.setI18n(List.of(i1));
        }
        return req;
    }

    private Integration buildMappedEntity(boolean withI18n) {
        Integration entity = new Integration();
        entity.setLogo("/logo.png");
        entity.setActive(null); // will be forced to true
        if (withI18n) {
            IntegrationI18n child = new IntegrationI18n();
            child.setLanguage("en");
            child.setName("Name EN");
            child.setDescription("Desc EN");
            entity.setI18n(List.of(child));
        }
        return entity;
    }

    @Nested
    class CreateIntegration {
        @Test
        @DisplayName("createIntegration with i18n should set audit + active + back references and return mapped response")
        void create_withI18n() {
            IntegrationCreateRequest req = buildRequest(true);
            Integration mapped = buildMappedEntity(true);
            Integration saved = buildMappedEntity(true);
            saved.setId(42);
            saved.getI18n().forEach(c -> c.setIntegration(saved));
            IntegrationCreateResponse expectedResponse = new IntegrationCreateResponse();
            expectedResponse.setId(42);

            when(mapper.toEntity(req)).thenReturn(mapped);
            when(repository.save(any(Integration.class))).thenReturn(saved);
            when(mapper.toCreateResponse(saved)).thenReturn(expectedResponse);

            IntegrationCreateResponse out = service.createIntegration(req);

            assertThat(out.getId()).isEqualTo(42);

            ArgumentCaptor<Integration> captor = ArgumentCaptor.forClass(Integration.class);
            verify(repository).save(captor.capture());
            Integration toSave = captor.getValue();
            assertThat(toSave.getActive()).isTrue();
            assertThat(toSave.getCreatedAt()).isNotNull();
            assertThat(toSave.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
            assertThat(toSave.getI18n()).hasSize(1);
            assertThat(toSave.getI18n().get(0).getIntegration()).isSameAs(toSave);
            verify(mapper).toEntity(req);
            verify(mapper).toCreateResponse(saved);
        }

        @Test
        @DisplayName("createIntegration without i18n should not fail and still set audit + active")
        void create_withoutI18n() {
            IntegrationCreateRequest req = buildRequest(false);
            Integration mapped = buildMappedEntity(false);
            Integration saved = buildMappedEntity(false);
            saved.setId(5);
            IntegrationCreateResponse expectedResponse = new IntegrationCreateResponse();
            expectedResponse.setId(5);

            when(mapper.toEntity(req)).thenReturn(mapped);
            when(repository.save(any(Integration.class))).thenReturn(saved);
            when(mapper.toCreateResponse(saved)).thenReturn(expectedResponse);

            IntegrationCreateResponse out = service.createIntegration(req);
            assertThat(out.getId()).isEqualTo(5);
            ArgumentCaptor<Integration> captor = ArgumentCaptor.forClass(Integration.class);
            verify(repository).save(captor.capture());
            Integration toSave = captor.getValue();
            assertThat(toSave.getI18n()).isNull();
            assertThat(toSave.getActive()).isTrue();
            assertThat(toSave.getCreatedAt()).isNotNull();
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
    void delete_callsRepository() {
        service.delete(7);
        verify(repository).deleteById(7);
    }
}
