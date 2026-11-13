/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.tests.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Plays the delivery scenarios against the mock connector, on the settings in {@code
 * docker/docker-compose.connector-mock.yml}. The daily cap test runs last, since it uses up the
 * tenant's cap for the day: rerun on a clean stack.
 */
@Tag(TestTags.CONNECTOR_MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QR delivery through the mock connector")
class QrDeliveryScenariosFunctionalTest extends AbstractQrDeliveryFunctionalTest {

  private static final int SLOW_SENDS = 15;

  @Test
  @Order(1)
  @DisplayName("capability is available and shows test mode")
  void capabilityShowsTestMode() {
    Response capability = capability();
    assertThat(capability.jsonPath().getBoolean("available")).isTrue();
    assertThat(capability.jsonPath().getBoolean("testMode")).isTrue();
    assertThat(capability.jsonPath().getList("locales")).containsExactlyInAnyOrder("en", "fr");
  }

  @Test
  @Order(2)
  @DisplayName("a send is accepted, the audit records it, and a retry is a new attempt")
  void acceptedAndAudited() throws Exception {
    String recipient = "accepted.person@example.com";
    Integer enrollmentId = newEnrollment(newIntegration(), recipient);

    Response first = send(enrollmentId, "fr");
    assertThat(first.getStatusCode()).isEqualTo(200);
    assertThat(first.jsonPath().getString("outcome")).isEqualTo("accepted");
    assertThat(first.jsonPath().getString("recipient")).isEqualTo(recipient);
    assertThat(first.jsonPath().getBoolean("testMode")).isTrue();
    String sendId = first.jsonPath().getString("sendId");

    Response second = send(enrollmentId, "en");
    assertThat(second.jsonPath().getString("outcome")).isEqualTo("accepted");
    assertThat(second.jsonPath().getString("sendId")).isNotEqualTo(sendId);

    String details =
        query(
            "SELECT event_details FROM ezkey_audit_log"
                + " WHERE event_type = 'ENROLLMENT_QR_DELIVERY'"
                + " AND event_action = 'enrollment_qr_delivery_accepted'"
                + " AND event_details LIKE '%"
                + sendId
                + "%'");
    assertThat(details).contains(recipient, "ezkey-connector-mock", "enrollment-qr");
    assertThat(details).doesNotContain("iVBORw0KGgo");
  }

  @ParameterizedTest(name = "{0} is {1} with {2}")
  @Order(3)
  @CsvSource({
    "mock-provider-rejected,rejected,provider-rejected",
    "mock-recipient-refused,rejected,recipient-refused",
    "mock-provider-unavailable,rejected,provider-unavailable",
    "mock-cap-reached,rejected,connector-cap-reached",
    "mock-provider-timeout,unknown,provider-timeout",
    "mock-server-error,unknown,connector-out-of-contract",
    "mock-redirect,unknown,connector-out-of-contract",
    "mock-unreadable,unknown,connector-out-of-contract"
  })
  void outcomes(String scenario, String outcome, String code) {
    Response response = send(newEnrollment(newIntegration(), scenario + "@example.com"), "fr");

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getString("outcome")).isEqualTo(outcome);
    assertThat(response.jsonPath().getString("code")).isEqualTo(code);
  }

  @Test
  @Order(4)
  @DisplayName("refusals in the core carry a typed code")
  void refusals() {
    Integer integrationId = newIntegration();

    Response noEmail = send(newEnrollment(integrationId, null), "fr");
    assertThat(noEmail.getStatusCode()).isEqualTo(422);
    assertThat(noEmail.jsonPath().getString("code")).isEqualTo("contact-email-missing");

    Response badEmail = send(newEnrollment(integrationId, "not-an-address"), "fr");
    assertThat(badEmail.getStatusCode()).isEqualTo(422);
    assertThat(badEmail.jsonPath().getString("code")).isEqualTo("contact-email-invalid");

    assertThat(send(newEnrollment(integrationId, "a@example.com"), "de").getStatusCode())
        .isEqualTo(400);
    assertThat(send(999_999_999, "fr").getStatusCode()).isEqualTo(404);
  }

  @Test
  @Order(5)
  @DisplayName("slow sends beyond the pool size become unknown, and other reads stay fast")
  void slowConnectorHoldsNoDatabaseConnection() throws Exception {
    Integer integrationId = newIntegration();
    List<Integer> enrollments = new ArrayList<>();
    for (int i = 0; i < SLOW_SENDS; i++) {
      enrollments.add(newEnrollment(integrationId, "mock-slow@example.com"));
    }
    adminToken();

    try (ExecutorService pool = Executors.newFixedThreadPool(SLOW_SENDS)) {
      List<Future<Response>> sends = new ArrayList<>();
      for (Integer enrollmentId : enrollments) {
        sends.add(pool.submit(() -> send(enrollmentId, "fr")));
      }
      Thread.sleep(Duration.ofSeconds(2));

      long start = System.nanoTime();
      assertThat(get("/enrollments?size=5").getStatusCode()).isEqualTo(200);
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));

      for (Future<Response> send : sends) {
        Response response = send.get();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("outcome")).isEqualTo("unknown");
        assertThat(response.jsonPath().getString("code")).isEqualTo("connector-no-answer");
      }
    }
  }

  @Test
  @Order(6)
  @DisplayName("the tenant daily cap refuses further sends")
  void tenantDailyCap() {
    Integer integrationId = newIntegration();
    Response response = null;
    for (int i = 0; i < 40; i++) {
      response = send(newEnrollment(integrationId, "anyone@example.com"), "fr");
      if (response.getStatusCode() != 200) {
        break;
      }
    }
    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(429);
    assertThat(response.jsonPath().getString("code")).isEqualTo("daily-cap-reached");
  }
}
