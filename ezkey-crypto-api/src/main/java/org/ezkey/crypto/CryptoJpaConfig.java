/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: CryptoJpaConfig
 * Description: Minimal JPA surface for crypto-api read-only keyset blob SELECT (ADR-0016).
 */

package org.ezkey.crypto;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal JPA configuration for Crypto API.
 *
 * <p>Crypto API (local lab clean-start only) needs a read-only DataSource solely to {@code SELECT}
 * {@code ezkey_keyset_blob} via {@code KeysetBlobRepository}. Entity and repository scanning is
 * limited to {@code org.ezkey.security.domain.*}. Write-path services (rotation, re-encryption)
 * stay excluded from {@link CryptoApplication} component scan. The DB role {@code ezkey_crypto} is
 * SELECT-only on the blob table — application {@code writer=false} is defense in depth.
 *
 * <p>Gated on {@link DataSource} so {@code @WebMvcTest} slices (no JDBC) do not register JPA
 * repositories without an {@code EntityManagerFactory}.
 *
 * @since 2026
 */
@Configuration
@ConditionalOnBean(DataSource.class)
@EnableJpaRepositories(basePackages = {"org.ezkey.security.domain.repository"})
@EntityScan(basePackages = {"org.ezkey.security.domain.entity"})
public class CryptoJpaConfig {
  // Minimal blob SELECT surface for TinkKeyManager / EncryptionService
}
