/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Real send through the SMTP connector into Mailpit (#1206 step 5).
 */
package org.ezkey.tests.connector;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Sends an enrollment QR through the SMTP connector and reads what Mailpit received: the fixed
 * French wording, the expected server, the QR inline as the same PNG the Admin API renders, and no
 * challenge.
 */
@Tag(TestTags.CONNECTOR_SMTP)
@DisplayName("QR delivery through the SMTP connector")
class QrDeliverySmtpFunctionalTest extends AbstractQrDeliveryFunctionalTest {

  static final String MAILPIT =
      System.getProperty("ezkey.tests.mailpit.url", "http://localhost:8025");

  /** Host of EZKEY_QR_AUTH_BASE_URL in docker-compose.connector-smtp.yml. */
  static final String EXPECTED_SERVER =
      System.getProperty("ezkey.tests.expected.server", "localhost");

  @Test
  @DisplayName("an invitation gets an address at CREATED, then its QR arrives by email")
  void qrArrivesByEmail() throws Exception {
    Map<String, Object> cap = capability().then().statusCode(200).extract().jsonPath().getMap("$");
    assertThat(cap.get("available")).isEqualTo(true);
    assertThat(cap.get("testMode")).isEqualTo(false);

    Integer enrollmentId = newEnrollment(newIntegration(), null);
    Response missing = send(enrollmentId, "fr");
    assertThat(missing.statusCode()).isEqualTo(422);
    assertThat(missing.jsonPath().getString("code")).isEqualTo("contact-email-missing");

    String recipient = "smtp-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    JsonPath created = get("/enrollments/" + enrollmentId).jsonPath();
    Object version = created.get("version");
    configureForAdminApi(dockerStackConfig);
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken())
        .body(Map.<String, Object>of("version", version, "contactEmail", recipient))
        .when()
        .patch("/enrollments/" + enrollmentId)
        .then()
        .statusCode(200);

    Response sent = send(enrollmentId, "fr");
    assertThat(sent.statusCode()).isEqualTo(200);
    assertThat(sent.jsonPath().getString("outcome")).isEqualTo("accepted");
    assertThat(sent.jsonPath().getBoolean("testMode")).isFalse();
    assertThat(sent.jsonPath().getString("recipient")).isEqualTo(recipient);

    JsonPath message = mailpitMessageTo(recipient);
    assertThat(message.getString("Subject"))
        .endsWith("vous invite à configurer l’approbation de connexion");
    assertThat(message.getString("Text"))
        .contains(EXPECTED_SERVER, "code à 6 chiffres", "Vous n’attendiez pas ce courriel")
        .doesNotContain(String.valueOf(created.getInt("enrollmentChallenge")));
    assertThat(message.getString("HTML")).contains("cid:enrollment-qr");
    List<Map<String, Object>> inline = message.getList("Inline");
    assertThat(inline).hasSize(1);
    assertThat(inline.get(0).get("ContentType")).isEqualTo("image/png");
    assertThat(inline.get(0).get("ContentID")).isEqualTo("enrollment-qr");

    byte[] emailed =
        RestAssured.given()
            .baseUri(MAILPIT)
            .basePath("")
            .port(-1)
            .when()
            .get(
                "/api/v1/message/"
                    + message.getString("ID")
                    + "/part/"
                    + inline.get(0).get("PartID"))
            .then()
            .statusCode(200)
            .extract()
            .asByteArray();
    configureForAdminApi(dockerStackConfig);
    byte[] onScreen =
        given()
            .header("Authorization", "Bearer " + adminToken())
            .accept("image/png")
            .when()
            .get("/enrollments/" + enrollmentId + "/qrcode")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray();
    assertThat(emailed).isEqualTo(onScreen);
  }

  /** Waits for Mailpit to hold the message, then returns its details. */
  static JsonPath mailpitMessageTo(String recipient) throws InterruptedException {
    for (int i = 0; i < 20; i++) {
      JsonPath search =
          RestAssured.given()
              .baseUri(MAILPIT)
              .basePath("")
              .port(-1)
              .queryParam("query", "to:\"" + recipient + "\"")
              .when()
              .get("/api/v1/search")
              .then()
              .statusCode(200)
              .extract()
              .jsonPath();
      List<String> ids = search.getList("messages.ID");
      if (!ids.isEmpty()) {
        assertThat(ids).hasSize(1);
        return RestAssured.given()
            .baseUri(MAILPIT)
            .basePath("")
            .port(-1)
            .when()
            .get("/api/v1/message/" + ids.get(0))
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();
      }
      Thread.sleep(500);
    }
    throw new AssertionError("Mailpit never received the message");
  }
}
