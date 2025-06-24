package org.ezkey.integration.domain.repository;

import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EzkeyIntegrationRepository extends JpaRepository<EzkeyIntegration, Integer> {
} 