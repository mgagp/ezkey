/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiPresentationCustomizer
 * Description: Curates tag and path order in the generated Auth API OpenAPI document.
 */

package org.ezkey.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Applies journey-oriented tag and path ordering for Auth API documentation consumers.
 *
 * <p>See {@code product-docs/global/openapi-presentation-order-design.md}.
 *
 * @since 2025
 */
public final class OpenApiPresentationCustomizer implements OpenApiCustomizer {

  private static final String TAG_PUBLIC = "Public";
  private static final String TAG_ENROLLMENTS = "Enrollments";
  private static final String TAG_AUTH_ATTEMPTS = "Authentication Attempts";

  private static final List<String> TAG_ORDER =
      List.of(TAG_PUBLIC, TAG_ENROLLMENTS, TAG_AUTH_ATTEMPTS);

  private static final List<String> PATH_ORDER =
      List.of(
          "/api/v1/public/instance-info",
          "/api/v1/enrollments/bind",
          "/api/v1/enrollments/verify",
          "/api/v1/enrollments/instance-info",
          "/api/v1/auth-attempts/pending",
          "/api/v1/auth-attempts/respond");

  private static final Map<String, String> TAG_DESCRIPTIONS =
      Map.of(
          TAG_PUBLIC,
          "Unauthenticated instance metadata",
          TAG_ENROLLMENTS,
          "Mobile device enrollment operations for binding devices to user accounts and completing"
              + " verification",
          TAG_AUTH_ATTEMPTS,
          "Mobile authentication attempt operations for checking pending requests and submitting"
              + " responses");

  @Override
  public void customise(OpenAPI openApi) {
    if (openApi == null) {
      return;
    }
    openApi.setTags(buildOrderedTags(openApi));
    reorderPaths(openApi);
  }

  private static List<Tag> buildOrderedTags(OpenAPI openApi) {
    Map<String, String> descriptions = new LinkedHashMap<>(TAG_DESCRIPTIONS);
    if (openApi.getTags() != null) {
      for (Tag existing : openApi.getTags()) {
        if (existing != null && existing.getName() != null && existing.getDescription() != null) {
          descriptions.putIfAbsent(existing.getName(), existing.getDescription());
        }
      }
    }
    List<Tag> ordered = new ArrayList<>();
    for (String name : TAG_ORDER) {
      ordered.add(new Tag().name(name).description(descriptions.get(name)));
    }
    return ordered;
  }

  private static void reorderPaths(OpenAPI openApi) {
    Paths existing = openApi.getPaths();
    if (existing == null || existing.isEmpty()) {
      return;
    }
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
