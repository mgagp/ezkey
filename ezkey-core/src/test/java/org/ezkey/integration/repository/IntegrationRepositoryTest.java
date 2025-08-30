package org.ezkey.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.IntegrationI18n;
import org.ezkey.integration.domain.repository.IntegrationI18nRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@Transactional
class IntegrationRepositoryTest {

    @Autowired
    private IntegrationRepository integrationRepository;

    @Autowired
    private IntegrationI18nRepository integrationI18nRepository;

    private Integration buildIntegrationWithChild() {
        Integration parent = new Integration();
        parent.setLogo("/logo.png");
        parent.setActive(true);
        parent.setCreatedAt(LocalDateTime.now());

        IntegrationI18n child = new IntegrationI18n();
        child.setLanguage("en");
        child.setName("Name EN");
        child.setDescription("Desc EN");
        child.setIntegration(parent);

        var list = new ArrayList<IntegrationI18n>();
        list.add(child);
        parent.setI18n(list);
        return parent;
    }

    @Test
    @DisplayName("save should cascade to i18n children and assign ids")
    void save_cascadeChildren() {
        Integration parent = buildIntegrationWithChild();
        Integration saved = integrationRepository.save(parent);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getI18n()).hasSize(1);
        IntegrationI18n child = saved.getI18n().get(0);
        assertThat(child.getId()).isNotNull();
        assertThat(child.getIntegration()).isSameAs(saved);
    }

    @Test
    @DisplayName("deleting parent should delete i18n children (orphanRemoval + cascade)")
    void delete_parentDeletesChildren() {
        Integration saved = integrationRepository.save(buildIntegrationWithChild());
        Integer childId = saved.getI18n().get(0).getId();
        integrationRepository.delete(saved);
        integrationRepository.flush();
        assertThat(integrationI18nRepository.findById(childId)).isEmpty();
    }

    @Test
    @DisplayName("removing child from collection should orphan remove it")
    void orphanRemoval_onCollectionEdit() {
        Integration saved = integrationRepository.save(buildIntegrationWithChild());
        assertThat(integrationI18nRepository.count()).isEqualTo(1);
        saved.getI18n().clear(); // mutable list
        integrationRepository.save(saved);
        integrationRepository.flush();
        assertThat(integrationI18nRepository.count()).isZero();
    }
}