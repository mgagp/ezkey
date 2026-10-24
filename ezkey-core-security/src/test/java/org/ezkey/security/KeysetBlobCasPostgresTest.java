/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeysetBlobCasPostgresTest
 * Description: Real-Postgres CAS coverage for ezkey_keyset_blob (#938 / #961 R1).
 */
package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real-PostgreSQL integration tests for keyset blob CAS (#938 retarget / #961 R1).
 *
 * <p>#938 originally named {@code updateKeysetIfVersionMatches}; that native helper is obsolete.
 * This PR uses Hibernate {@code @Version} plus an application {@code version==read-version}
 * pre-check. These tests prove the same CAS outcomes on Postgres: stale write fails closed,
 * successful write bumps version, and concurrent writers yield one winner.
 *
 * @since 2026
 */
@SpringBootTest(classes = KeysetBlobCasPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class KeysetBlobCasPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_keyset_cas_it");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add(
        "spring.sql.init.schema-locations", () -> "classpath:keyset-blob-cas-pg-schema.sql");
    registry.add("spring.task.scheduling.enabled", () -> "false");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = KeysetBlob.class)
  @EnableJpaRepositories(basePackageClasses = KeysetBlobRepository.class)
  static class TestConfiguration {}

  @Autowired private KeysetBlobRepository keysetBlobRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @TempDir Path tempDir;

  @BeforeEach
  void cleanBlob() {
    jdbcTemplate.update("DELETE FROM ezkey_keyset_blob");
  }

  @Test
  @DisplayName("R1: successful rotateKey bumps blob version on real Postgres")
  void successfulWrite_bumpsVersion() throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    TestMasterKeys.write(masterKeyPath);
    TinkKeyManager manager = newWriterManager(masterKeyPath);
    try {
      long versionAtBoot = manager.getDatabaseKeysetVersion();
      assertEquals(1L, versionAtBoot);
      long bootPrimary = manager.getCurrentPrimaryKeyId();

      long newPrimary = manager.rotateKey();
      assertNotEquals(bootPrimary, newPrimary);
      assertEquals(versionAtBoot + 1, manager.getDatabaseKeysetVersion());
      assertEquals(
          versionAtBoot + 1,
          keysetBlobRepository.findVersion().orElseThrow(),
          "committed blob version must match in-memory cache after rotate");
      assertTrue(manager.getAllKeyIds().contains(bootPrimary));
      assertTrue(manager.getAllKeyIds().contains(newPrimary));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "R1: stale in-memory version fails closed on real Postgres (app pre-check / @Version)")
  void staleVersionWrite_failsClosed() throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    TestMasterKeys.write(masterKeyPath);
    TinkKeyManager writerA = newWriterManager(masterKeyPath);
    TinkKeyManager writerB = newWriterManager(masterKeyPath);
    try {
      long staleCachedVersion = writerA.getDatabaseKeysetVersion();
      long primaryBefore = writerA.getCurrentPrimaryKeyId();

      // Peer commits a rotation; writer A still holds the old cached version.
      writerB.rotateKey();
      assertTrue(writerB.getDatabaseKeysetVersion() > staleCachedVersion);

      OptimisticLockingFailureException thrown =
          assertThrows(
              OptimisticLockingFailureException.class,
              () -> writerA.saveKeysetToDatabase("STALE_VERSION_PROBE"),
              "stale cached version must fail closed before/at flush (not silently overwrite)");
      assertTrue(
          thrown.getMessage().contains("version mismatch")
              || thrown.getMessage().toLowerCase().contains("version"),
          "failure must cite version CAS, got: " + thrown.getMessage());
      assertEquals(
          primaryBefore,
          writerA.getCurrentPrimaryKeyId(),
          "stale writer must not install a new live primary");
      assertEquals(
          writerB.getDatabaseKeysetVersion(),
          keysetBlobRepository.findVersion().orElseThrow(),
          "peer-winning blob version must remain committed");
    } finally {
      writerA.shutdownKeysetCheckExecutor();
      writerB.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "R1: concurrent rotateKey — exactly one winner; winner blob keeps both kids; loser reloads")
  void concurrentWriters_oneWinnerLoserReloads() throws Exception {
    Path masterKeyPath = tempDir.resolve("master-concurrent.key");
    TestMasterKeys.write(masterKeyPath);
    TinkKeyManager writerA = newWriterManager(masterKeyPath);
    TinkKeyManager writerB = newWriterManager(masterKeyPath);
    try {
      long bootPrimary = writerA.getCurrentPrimaryKeyId();
      assertEquals(bootPrimary, writerB.getCurrentPrimaryKeyId());

      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      AtomicReference<Throwable> failureA = new AtomicReference<>();
      AtomicReference<Throwable> failureB = new AtomicReference<>();
      Future<Long> futureA =
          pool.submit(
              () -> {
                start.await();
                try {
                  return writerA.rotateKey();
                } catch (Throwable t) {
                  failureA.set(t);
                  throw t;
                }
              });
      Future<Long> futureB =
          pool.submit(
              () -> {
                start.await();
                try {
                  return writerB.rotateKey();
                } catch (Throwable t) {
                  failureB.set(t);
                  throw t;
                }
              });
      start.countDown();

      boolean aWon = false;
      boolean bWon = false;
      Long winnerPrimary = null;
      try {
        winnerPrimary = futureA.get(30, TimeUnit.SECONDS);
        aWon = true;
      } catch (Exception ignored) {
        // expected for the CAS loser
      }
      try {
        Long primaryB = futureB.get(30, TimeUnit.SECONDS);
        if (aWon) {
          // Both cannot commit distinct rotations against the same starting version.
          assertEquals(
              winnerPrimary,
              primaryB,
              "if both return, they must have converged on the same primary");
        } else {
          winnerPrimary = primaryB;
          bWon = true;
        }
      } catch (Exception ignored) {
        // expected for the CAS loser
      }
      pool.shutdownNow();

      assertTrue(
          aWon ^ bWon || (aWon && bWon && winnerPrimary != null),
          "exactly one CAS winner expected (or both converged); aFailed="
              + (failureA.get() != null)
              + " bFailed="
              + (failureB.get() != null));
      assertTrue(
          (failureA.get() != null) ^ (failureB.get() != null)
              || (failureA.get() == null && failureB.get() == null && aWon && bWon),
          "one loser must fail closed (or both observed the same committed winner)");

      TinkKeyManager winner = aWon && failureB.get() != null ? writerA : writerB;
      TinkKeyManager loser = winner == writerA ? writerB : writerA;
      if (failureA.get() != null && failureB.get() != null) {
        throw new AssertionError(
            "both writers failed: a=" + failureA.get() + " b=" + failureB.get());
      }
      if (failureA.get() != null) {
        winner = writerB;
        loser = writerA;
      } else if (failureB.get() != null) {
        winner = writerA;
        loser = writerB;
      }

      List<Long> winnerKids = winner.getAllKeyIds();
      assertEquals(2, winnerKids.size(), "winner blob must keep boot kid + new kid");
      assertTrue(winnerKids.contains(bootPrimary));
      assertTrue(winnerKids.contains(winner.getCurrentPrimaryKeyId()));

      assertTrue(
          loser.forceReloadFromDatabase(), "CAS loser must reload the committed winner blob");
      Set<Long> loserKids = new HashSet<>(loser.getAllKeyIds());
      assertEquals(
          new HashSet<>(winnerKids),
          loserKids,
          "after reload, loser must match winner kids (no orphan staging kid)");
      assertEquals(winner.getCurrentPrimaryKeyId(), loser.getCurrentPrimaryKeyId());
      assertEquals(winner.getDatabaseKeysetVersion(), loser.getDatabaseKeysetVersion());
    } finally {
      writerA.shutdownKeysetCheckExecutor();
      writerB.shutdownKeysetCheckExecutor();
    }
  }

  private TinkKeyManager newWriterManager(Path masterKeyPath) throws Exception {
    Path keysetPath = tempDir.resolve("unused-keyset-" + System.nanoTime() + ".json.encrypted");
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(true);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);
    properties.getRotation().setBackupBeforeRotation(false);

    ObjectProvider<KeysetBlobRepository> provider =
        new ObjectProvider<>() {
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
    TinkKeyManager manager = new TinkKeyManager(properties, provider);
    manager.initialize();
    manager.resetDatabaseCheckThrottleForTests();
    return manager;
  }
}
