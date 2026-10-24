/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationTransactionalInstallPostgresTest
 * Description: #961 R2 — live keyset install vs KeyRotationService @Transactional boundary.
 */
package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * #961 R2 — proves live keyset install does not survive rollback of {@link KeyRotationService}'s
 * {@code @Transactional} boundary after a successful blob flush.
 *
 * <p>Also covers CAS failure inside a real Spring transaction (fail closed; no orphan primary).
 *
 * @since 2026
 */
@SpringBootTest(classes = KeyRotationTransactionalInstallPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class KeyRotationTransactionalInstallPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_keyset_tx_it");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add(
        "spring.sql.init.schema-locations", () -> "classpath:keyset-blob-cas-pg-schema.sql");
    registry.add("spring.task.scheduling.enabled", () -> "false");
    registry.add("ezkey.encryption.enabled", () -> "true");
    registry.add("ezkey.encryption.required", () -> "true");
    registry.add("ezkey.encryption.rotation.enabled", () -> "true");
    registry.add("ezkey.encryption.rotation.backup-before-rotation", () -> "false");
    registry.add("ezkey.encryption.keyset.storage-mode", () -> "DATABASE");
    registry.add("ezkey.encryption.keyset.writer", () -> "true");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableTransactionManagement
  @EntityScan(basePackageClasses = {KeysetBlob.class, EncryptionKey.class})
  @EnableJpaRepositories(
      basePackageClasses = {KeysetBlobRepository.class, EncryptionKeyRepository.class})
  @Import(KeyRotationService.class)
  static class TestConfiguration {

    @Bean
    TinkProperties tinkProperties() throws Exception {
      Path dir = Files.createTempDirectory("keyset-r2");
      Path masterKeyPath = dir.resolve("master.key");
      TestMasterKeys.write(masterKeyPath);
      Path keysetPath = dir.resolve("unused-keyset.json.encrypted");
      TinkProperties properties = new TinkProperties();
      properties.setEnabled(true);
      properties.setRequired(true);
      properties.setMasterKeyFile(masterKeyPath.toString());
      properties.setKeysetFile(keysetPath.toString());
      properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
      properties.getKeyset().setWriter(true);
      properties.getRotation().setEnabled(true);
      properties.getRotation().setBackupBeforeRotation(false);
      return properties;
    }

    @Bean(destroyMethod = "shutdownKeysetCheckExecutor")
    TinkKeyManager tinkKeyManager(
        TinkProperties properties, KeysetBlobRepository keysetBlobRepository) {
      return new TinkKeyManager(properties, repositoryProvider(keysetBlobRepository));
    }
  }

  static ObjectProvider<KeysetBlobRepository> repositoryProvider(
      KeysetBlobRepository keysetBlobRepository) {
    return new ObjectProvider<>() {
      @Override
      public KeysetBlobRepository getObject() {
        return keysetBlobRepository;
      }

      @Override
      public KeysetBlobRepository getObject(Object... args) {
        return keysetBlobRepository;
      }

      @Override
      public KeysetBlobRepository getIfAvailable() {
        return keysetBlobRepository;
      }

      @Override
      public KeysetBlobRepository getIfUnique() {
        return keysetBlobRepository;
      }
    };
  }

  @MockitoBean private AuditLogService auditLogService;

  @MockitoBean private EncryptionKeyMigrationScopeService migrationScopeService;

  @MockitoBean private KeyUsageVerificationService keyUsageVerificationService;

  @Autowired private KeyRotationService keyRotationService;

  @Autowired private TinkKeyManager tinkKeyManager;

  @Autowired private TinkProperties tinkProperties;

  @Autowired private KeysetBlobRepository keysetBlobRepository;

  @Autowired private EncryptionKeyRepository encryptionKeyRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetMetadataAndSync() {
    assertTrue(
        AopUtils.isAopProxy(keyRotationService),
        "KeyRotationService must be a TX proxy so introduceNewKey opens a Spring transaction");
    jdbcTemplate.update("DELETE FROM ezkey_encryption_key");
    // Shared Spring context + Postgres: peer rotates in other tests must be visible here.
    assertTrue(
        tinkKeyManager.forceReloadFromDatabase(),
        "manager cache must match committed blob before each R2 scenario");
    keyRotationService.initializeKeysetSync();
    List<EncryptionKey> primaries =
        encryptionKeyRepository.findByKeyStatus(EncryptionKey.KeyStatus.PRIMARY);
    assertEquals(1, primaries.size(), "startup sync must materialize exactly one PRIMARY metadata");
  }

  @Test
  @DisplayName(
      "R2: forced failure after blob flush rolls back TX — live primary stays pre-mutation"
          + " (afterCommit install)")
  void transactionalRollbackAfterBlobFlush_restoresPreMutationLivePrimary() {
    long primaryBefore = tinkKeyManager.getCurrentPrimaryKeyId();
    long versionBefore = tinkKeyManager.getDatabaseKeysetVersion();
    assertEquals(
        versionBefore,
        keysetBlobRepository.findVersion().orElseThrow(),
        "precondition: in-memory version must equal committed blob");
    byte[] envelopeBefore = keysetBlobRepository.findKeyset().orElseThrow().getKeysetData().clone();

    doThrow(new RuntimeException("forced audit failure after blob flush"))
        .when(auditLogService)
        .log(any(AuditLog.class));

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> keyRotationService.introduceNewKey("R2_ROLLBACK_PROBE", true));
    assertTrue(thrown.getMessage().contains("forced audit failure"));

    assertEquals(
        primaryBefore,
        tinkKeyManager.getCurrentPrimaryKeyId(),
        "R2: in-memory primary must not survive outer TX rollback after blob flush");
    assertEquals(
        versionBefore,
        tinkKeyManager.getDatabaseKeysetVersion(),
        "R2: cached blob version must roll back with the TX");
    assertEquals(
        versionBefore,
        keysetBlobRepository.findVersion().orElseThrow(),
        "R2: committed blob version must match pre-mutation (flush rolled back)");
    assertTrue(
        Arrays.equals(
            envelopeBefore, keysetBlobRepository.findKeyset().orElseThrow().getKeysetData()),
        "R2: committed envelope must match pre-mutation");
    assertEquals(
        1,
        encryptionKeyRepository.findByKeyStatus(EncryptionKey.KeyStatus.PRIMARY).size(),
        "metadata demotion/promotion must roll back with the TX");
  }

  @Test
  @DisplayName(
      "B2: TransactionTemplate nominal path — during TX old handle/version; after COMMIT new"
          + " handle then version (Patrick B2)")
  void afterCommit_nominalPath_publishesHandleThenVersion() throws Exception {
    long primaryBefore = tinkKeyManager.getCurrentPrimaryKeyId();
    long versionBefore = tinkKeyManager.getDatabaseKeysetVersion();

    AtomicLong midPrimary = new AtomicLong();
    AtomicLong midVersion = new AtomicLong();

    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    Long newPrimary =
        tx.execute(
            _ -> {
              try {
                long id = tinkKeyManager.rotateKey();
                midPrimary.set(tinkKeyManager.getCurrentPrimaryKeyId());
                midVersion.set(tinkKeyManager.getDatabaseKeysetVersion());
                return id;
              } catch (RuntimeException runtimeException) {
                throw runtimeException;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertEquals(
        primaryBefore,
        midPrimary.get(),
        "B2: during TX live primary must stay pre-mutation (afterCommit not yet run)");
    assertEquals(
        versionBefore, midVersion.get(), "B2: during TX cached version must stay pre-mutation");
    assertEquals(
        newPrimary,
        tinkKeyManager.getCurrentPrimaryKeyId(),
        "B2: after COMMIT live primary must be the new key");
    assertEquals(
        versionBefore + 1,
        tinkKeyManager.getDatabaseKeysetVersion(),
        "B2: after COMMIT cached version must be published");
    assertTrue(
        tinkKeyManager.getAllKeyIds().contains(primaryBefore)
            && tinkKeyManager.getAllKeyIds().contains(newPrimary),
        "B2: committed live keyset must keep boot kid + new kid");

    // Order unit: between handle and version publish, version must still be the old sentinel.
    // Mutants: empty afterCommit / version-only → mid-TX or post-COMMIT asserts above;
    // version-before-handle → orderMidVersion == publishedVersion while primary still old.
    com.google.crypto.tink.KeysetHandle live =
        (com.google.crypto.tink.KeysetHandle)
            org.springframework.test.util.ReflectionTestUtils.getField(
                tinkKeyManager, "keysetHandle");
    long publishedVersion = tinkKeyManager.getDatabaseKeysetVersion();
    org.springframework.test.util.ReflectionTestUtils.setField(
        tinkKeyManager, "databaseKeysetVersion", -1L);
    AtomicLong orderMidVersion = new AtomicLong();
    AtomicLong orderMidPrimary = new AtomicLong();
    tinkKeyManager.setBetweenHandleAndVersionPublishForTests(
        () -> {
          orderMidVersion.set(tinkKeyManager.getDatabaseKeysetVersion());
          orderMidPrimary.set(tinkKeyManager.getCurrentPrimaryKeyId());
        });
    try {
      tinkKeyManager.publishLiveKeysetHandleThenVersion(live, publishedVersion);
      assertEquals(
          -1L,
          orderMidVersion.get(),
          "B2: version must not publish before handle (mid-probe still sees sentinel)");
      assertEquals(
          newPrimary, orderMidPrimary.get(), "B2: handle must already be live at the mid-probe");
      assertEquals(publishedVersion, tinkKeyManager.getDatabaseKeysetVersion());
    } finally {
      tinkKeyManager.setBetweenHandleAndVersionPublishForTests(null);
    }
  }

  @Test
  @DisplayName(
      "P-M2: afterCompletion on rollback restores corrupted live version (TX wiring, not direct"
          + " helper call)")
  void afterCompletion_onRollback_restoresPrematureLiveCorruption() {
    long primaryBefore = tinkKeyManager.getCurrentPrimaryKeyId();
    long versionBefore = tinkKeyManager.getDatabaseKeysetVersion();

    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    assertThrows(
        RuntimeException.class,
        () ->
            tx.executeWithoutResult(
                _ -> {
                  try {
                    tinkKeyManager.rotateKey();
                  } catch (RuntimeException runtimeException) {
                    throw runtimeException;
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                  // Simulate install-before-commit corruption while deferred sync is registered.
                  org.springframework.test.util.ReflectionTestUtils.setField(
                      tinkKeyManager, "databaseKeysetVersion", versionBefore + 99);
                  assertEquals(versionBefore + 99, tinkKeyManager.getDatabaseKeysetVersion());
                  throw new RuntimeException("force rollback for afterCompletion restore");
                }));

    assertEquals(
        versionBefore,
        tinkKeyManager.getDatabaseKeysetVersion(),
        "P-M2: afterCompletion must restore pre-mutation version on rollback");
    assertEquals(
        primaryBefore,
        tinkKeyManager.getCurrentPrimaryKeyId(),
        "P-M2: live primary must remain pre-mutation after rollback");
  }

  @Test
  @DisplayName(
      "m1/P-M2 helper: restorePreMutationLive undoes a premature live install (unit; wired via"
          + " afterCompletion above)")
  void restorePreMutationLive_independentlyUndoesPrematureInstall() throws Exception {
    assertTrue(tinkKeyManager.forceReloadFromDatabase());
    long primaryBefore = tinkKeyManager.getCurrentPrimaryKeyId();
    long versionBefore = tinkKeyManager.getDatabaseKeysetVersion();
    com.google.crypto.tink.KeysetHandle previousLive =
        (com.google.crypto.tink.KeysetHandle)
            org.springframework.test.util.ReflectionTestUtils.getField(
                tinkKeyManager, "keysetHandle");

    // Premature install: advance version cache without a matching commit semantic.
    org.springframework.test.util.ReflectionTestUtils.setField(
        tinkKeyManager, "databaseKeysetVersion", versionBefore + 99);
    assertEquals(versionBefore + 99, tinkKeyManager.getDatabaseKeysetVersion());

    tinkKeyManager.restorePreMutationLive(previousLive, versionBefore, "M1_RESTORE");
    assertEquals(versionBefore, tinkKeyManager.getDatabaseKeysetVersion());
    assertEquals(primaryBefore, tinkKeyManager.getCurrentPrimaryKeyId());
  }

  @Test
  @DisplayName(
      "C-M3: second keyset mutation in the same TX fails closed; rollback keeps pre-TX primary")
  void secondKeysetMutationInSameTransaction_failsClosed() {
    long primaryBefore = tinkKeyManager.getCurrentPrimaryKeyId();
    long versionBefore = tinkKeyManager.getDatabaseKeysetVersion();

    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                tx.executeWithoutResult(
                    _ -> {
                      try {
                        tinkKeyManager.rotateKey();
                        tinkKeyManager.addKeyWithoutPromotion();
                      } catch (RuntimeException runtimeException) {
                        throw runtimeException;
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
    assertTrue(
        thrown.getMessage().contains("second keyset mutation in the same transaction"),
        "got: " + thrown.getMessage());

    assertEquals(
        primaryBefore,
        tinkKeyManager.getCurrentPrimaryKeyId(),
        "C-M3: after rollback live primary must be pre-transaction");
    assertEquals(
        versionBefore,
        keysetBlobRepository.findVersion().orElseThrow(),
        "C-M3: first mutation flush must roll back with the TX");
  }

  @Test
  @DisplayName("R2: CAS failure inside a Spring TX fails closed without orphan primary")
  void casFailureInsideTransaction_failsClosed() throws Exception {
    long primaryBefore = tinkKeyManager.getCurrentPrimaryKeyId();
    long versionBefore = tinkKeyManager.getDatabaseKeysetVersion();

    TinkKeyManager peer =
        new TinkKeyManager(tinkProperties, repositoryProvider(keysetBlobRepository));
    try {
      peer.initialize();
      peer.resetDatabaseCheckThrottleForTests();
      peer.rotateKey();
      assertTrue(peer.getDatabaseKeysetVersion() > versionBefore);

      TransactionTemplate tx = new TransactionTemplate(transactionManager);
      assertThrows(
          OptimisticLockingFailureException.class,
          () ->
              tx.executeWithoutResult(
                  _ -> {
                    try {
                      tinkKeyManager.saveKeysetToDatabase("R2_CAS_IN_TX");
                    } catch (RuntimeException runtimeException) {
                      throw runtimeException;
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  }));

      assertEquals(
          primaryBefore,
          tinkKeyManager.getCurrentPrimaryKeyId(),
          "CAS failure in TX must not install an orphan live primary");
      assertEquals(
          peer.getDatabaseKeysetVersion(), keysetBlobRepository.findVersion().orElseThrow());
    } finally {
      peer.shutdownKeysetCheckExecutor();
    }
  }
}
