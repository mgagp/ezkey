package org.ezkey.integration.mapper;

import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.ezkey.integration.domain.entity.EzkeyIntegrationI18n;
import org.ezkey.integration.dto.request.CreateIntegrationRequest;
import org.ezkey.integration.dto.response.IntegrationResponse;
import org.ezkey.integration.dto.response.IntegrationI18nResponse;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface IntegrationMapper {
    IntegrationResponse toResponse(EzkeyIntegration entity);
    EzkeyIntegration toEntity(IntegrationResponse response);
    List<IntegrationResponse> toResponseList(List<EzkeyIntegration> entities);
    List<EzkeyIntegration> toEntityList(List<IntegrationResponse> responses);

    IntegrationI18nResponse toI18nResponse(EzkeyIntegrationI18n entity);
    EzkeyIntegrationI18n toI18nEntity(IntegrationI18nResponse response);
    List<IntegrationI18nResponse> toI18nResponseList(List<EzkeyIntegrationI18n> entities);
    List<EzkeyIntegrationI18n> toI18nEntityList(List<IntegrationI18nResponse> responses);

    EzkeyIntegration toEntity(CreateIntegrationRequest request);
} 