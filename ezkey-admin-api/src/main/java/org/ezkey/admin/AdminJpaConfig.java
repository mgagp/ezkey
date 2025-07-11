package org.ezkey.admin;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "org.ezkey.integration.domain.repository")
@EntityScan(basePackages = "org.ezkey.integration.domain.entity")
public class AdminJpaConfig {
	// Configuration for JPA repositories and entity scanning
}