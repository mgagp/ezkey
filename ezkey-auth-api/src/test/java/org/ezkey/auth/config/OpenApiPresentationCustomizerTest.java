/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: OpenApiPresentationCustomizerTest
 * Description: Guards Auth API OpenAPI path order, including signed instance-info.
 */

package org.ezkey.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OpenApiPresentationCustomizer}. */
class OpenApiPresentationCustomizerTest {

  @Test
  @DisplayName("enrolled instance-info is ordered with the other enrollment operations")
  void enrolledInstanceInfo_isOrderedWithEnrollmentOperations() {
    OpenAPI openApi = new OpenAPI();
    Paths paths = new Paths();
    paths.addPathItem("/api/v1/auth-attempts/respond", getItem());
    paths.addPathItem("/api/v1/enrollments/instance-info", postItem());
    paths.addPathItem("/api/v1/public/instance-info", getItem());
    paths.addPathItem("/api/v1/enrollments/verify", postItem());
    paths.addPathItem("/api/v1/auth-attempts/pending", postItem());
    paths.addPathItem("/api/v1/enrollments/bind", postItem());
    openApi.setPaths(paths);

    new OpenApiPresentationCustomizer().customise(openApi);

    assertEquals(
        List.of(
            "/api/v1/public/instance-info",
            "/api/v1/enrollments/bind",
            "/api/v1/enrollments/verify",
            "/api/v1/enrollments/instance-info",
            "/api/v1/auth-attempts/pending",
            "/api/v1/auth-attempts/respond"),
        openApi.getPaths().keySet().stream().toList());
  }

  private static PathItem getItem() {
    return new PathItem().get(new Operation());
  }

  private static PathItem postItem() {
    return new PathItem().post(new Operation());
  }
}
