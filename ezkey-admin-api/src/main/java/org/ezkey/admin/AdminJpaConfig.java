package org.ezkey.admin;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages = { 
                "org.ezkey.integration.domain.repository",
                "org.ezkey.authattempt.domain.repository", 
                "org.ezkey.enrollment.domain.repository"
        })
@EntityScan(
        basePackages = { 
                "org.ezkey.integration.domain.entity",
                "org.ezkey.authattempt.domain.entity", 
                "org.ezkey.enrollment.domain.entity",
                // Toutes les entités sont maintenant dans org.ezkey.integration.domain.entity
        })
public class AdminJpaConfig {
    // Configuration for JPA repositories and entity scanning
}