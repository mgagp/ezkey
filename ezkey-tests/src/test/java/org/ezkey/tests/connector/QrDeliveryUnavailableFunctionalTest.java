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
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * With no usable connector, Ezkey works as before. Runs in the default suite, against a stack with
 * no connector, and in the rejected credential scenario. Skips itself when a connector is usable.
 */
@Tag(TestTags.ENROLLMENT)
@DisplayName("QR delivery with no usable connector")
class QrDeliveryUnavailableFunctionalTest extends AbstractQrDeliveryFunctionalTest {

  @Test
  @DisplayName("capability says unavailable, a send is refused, and on-screen delivery works")
  void integratedDeliveryStays() {
    Response capability = capability();
    assertThat(capability.getStatusCode()).isEqualTo(200);
    Assumptions.assumeFalse(
        Boolean.TRUE.equals(capability.jsonPath().get("available")), "A connector is usable");
    assertThat(capability.jsonPath().getBoolean("testMode")).isFalse();

    Integer enrollmentId = newEnrollment(newIntegration(), "person@example.com");

    Response send = send(enrollmentId, "fr");
    assertThat(send.getStatusCode()).isEqualTo(503);
    assertThat(send.jsonPath().getString("code")).isEqualTo("connector-unavailable");

    Response qr = get("/enrollments/" + enrollmentId + "/qrcode");
    assertThat(qr.getStatusCode()).isEqualTo(200);
    assertThat(qr.getContentType()).contains("image/png");

    assertThat(get("/enrollments/" + enrollmentId).getStatusCode()).isEqualTo(200);
  }
}
