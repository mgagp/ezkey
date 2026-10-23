/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: CryptoJpaConfig
 * Description: Minimal JPA surface for crypto-api read-only keyset blob SELECT (ADR-0016).
 */
package org.ezkey.crypto.autoconfigure;

import javax.sql.DataSource;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal JPA auto-configuration for Crypto API (ADR-0016).
 *
 * <p>Registered via {@code META-INF/spring/.../AutoConfiguration.imports} and ordered after {@link
 * DataSourceAutoConfiguration} so {@link ConditionalOnBean}{@code DataSource} sees the read-only
 * pool. Component-scan of {@code org.ezkey.crypto} excludes this package so the class is not
 * double-registered before the DataSource exists (that ordering bug yielded "Found 0 JPA repository
 * interfaces" and fail-closed encryption).
 *
 * <p>Scanning is limited to {@link KeysetBlobRepository} / {@link KeysetBlob}. DB role {@code
 * ezkey_crypto} is SELECT-only on the blob; application {@code writer=false} is defense in depth.
 *
 * @since 2026
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
@EnableJpaRepositories(
    basePackageClasses = KeysetBlobRepository.class,
    includeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = KeysetBlobRepository.class))
@EntityScan(basePackageClasses = KeysetBlob.class)
public class CryptoJpaConfig {
  // Minimal blob SELECT surface for TinkKeyManager / EncryptionService
}
