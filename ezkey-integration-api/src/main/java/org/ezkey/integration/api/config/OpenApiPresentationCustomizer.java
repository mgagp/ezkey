/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiPresentationCustomizer
 * Description: Curates path order in the generated Integration API OpenAPI document.
 */

package org.ezkey.integration.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Applies lifecycle-oriented path ordering for Integration API documentation consumers.
 *
 * <p>See {@code product-docs/global/openapi-presentation-order-design.md}.
 *
 * @since 2025
 */
public final class OpenApiPresentationCustomizer implements OpenApiCustomizer {

  private static final List<String> PATH_ORDER =
      List.of(
          "/api/v1/auth-attempts",
          "/api/v1/auth-attempts/{id}/wait",
          "/api/v1/auth-attempts/{id}/cancel");

  @Override
  public void customise(OpenAPI openApi) {
    if (openApi == null || openApi.getPaths() == null) {
      return;
    }
    Paths existing = openApi.getPaths();
    Paths ordered = new Paths();
    for (String path : PATH_ORDER) {
      PathItem item = existing.get(path);
      if (item != null) {
        ordered.addPathItem(path, item);
      }
    }
    existing.keySet().stream()
        .filter(path -> !PATH_ORDER.contains(path))
        .sorted()
        .forEach(path -> ordered.addPathItem(path, existing.get(path)));
    openApi.setPaths(ordered);
  }
}
