/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.osivpooltest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.ezkey.admin.config.AdminOpenInViewConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Proves why the send route leaves open-in-view: with a pool of one connection, a request that read
 * the database and then waits on a slow connector must leave the connection free for others.
 *
 * <p>In its own package, so the Admin API's component scan and the test slices' configuration
 * search do not pick up its nested classes.
 */
@SpringBootTest(
    classes = AdminOpenInViewConfigPoolTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:osivpool;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.hikari.maximum-pool-size=1",
      "spring.datasource.hikari.connection-timeout=250",
      "spring.jpa.open-in-view=false",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.flyway.enabled=false"
    })
@DisplayName("Open-in-view and the database pool during a slow connector call")
class AdminOpenInViewConfigPoolTest {

  @Autowired private WebApplicationContext context;
  @Autowired private DataSource dataSource;
  @Autowired private SlowController controller;

  private boolean connectionFreeDuringSlowCall(String path) throws Exception {
    MockMvc mvc = webAppContextSetup(context).build();
    controller.reset();
    CompletableFuture<Void> request =
        CompletableFuture.runAsync(() -> {
          try {
            mvc.perform(post(path));
          } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
            throw new IllegalStateException(e);
          }
        });
    if (!controller.reachedSlowCall.await(5, TimeUnit.SECONDS)) {
      request.get(1, TimeUnit.SECONDS);
      throw new AssertionError("the request ended before the slow call");
    }
    try (Connection ignored = dataSource.getConnection()) {
      return true;
    } catch (SQLException e) {
      return false;
    } finally {
      controller.release.countDown();
      request.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @DisplayName("the pool really has one connection")
  void poolSize() {
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("the send route leaves the connection free during the slow call")
  void sendRouteHoldsNoConnection() throws Exception {
    assertThat(connectionFreeDuringSlowCall("/api/v1/enrollments/7/qr-delivery")).isTrue();
  }

  @Test
  @DisplayName("control: any other route keeps open-in-view, so it holds the connection")
  void otherRouteHoldsTheConnection() throws Exception {
    assertThat(connectionFreeDuringSlowCall("/api/v1/enrollments/7/other")).isFalse();
  }

  /** Reads the database in a transaction, then waits as a slow connector would. */
  @RestController
  static class SlowController {
    @PersistenceContext private EntityManager entityManager;
    private final TransactionTemplate tx;
    volatile CountDownLatch reachedSlowCall = new CountDownLatch(1);
    volatile CountDownLatch release = new CountDownLatch(1);

    SlowController(PlatformTransactionManager transactionManager) {
      this.tx = new TransactionTemplate(transactionManager);
    }

    void reset() {
      reachedSlowCall = new CountDownLatch(1);
      release = new CountDownLatch(1);
    }

    @PostMapping({"/api/v1/enrollments/{id}/qr-delivery", "/api/v1/enrollments/{id}/other"})
    void slow(@PathVariable("id") Integer id) throws InterruptedException {
      tx.executeWithoutResult(_ -> entityManager.createNativeQuery("SELECT 1").getSingleResult());
      reachedSlowCall.countDown();
      release.await(5, TimeUnit.SECONDS);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(AdminOpenInViewConfig.class)
  static class TestApplication {
    @Bean
    SlowController slowController(PlatformTransactionManager transactionManager) {
      return new SlowController(transactionManager);
    }
  }
}
