/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OpenApiPresentationCustomizer
 * Description: Curates tag order, ReDoc tag groups, and Public tag deduplication for Admin API.
 */

package org.ezkey.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Applies journey-oriented tag ordering and ReDoc {@code x-tagGroups} for Admin API documentation.
 *
 * <p>See {@code product-docs/global/openapi-presentation-order-design.md}.
 *
 * @since 2025
 */
public final class OpenApiPresentationCustomizer implements OpenApiCustomizer {

  static final String TAG_PUBLIC = "Public";
  static final String TAG_ADMIN_AUTH = "Admin Authentication";
  static final String TAG_TENANTS = "Tenants";
  static final String TAG_INTEGRATIONS = "Integrations";
  static final String TAG_ENROLLMENTS = "Enrollments";
  static final String TAG_API_KEYS = "API Keys";
  static final String TAG_AUTH_ATTEMPTS = "Auth Attempts";
  static final String TAG_ADMIN_ENROLLMENT = "Admin Enrollment Management";
  static final String TAG_ADMIN_PROVISIONING = "Administrator Provisioning";
  static final String TAG_AUDIT_LOGS = "Audit Logs";
  static final String TAG_ENCRYPTION_KEYS = "Encryption Keys";
  static final String TAG_ALERTS = "Alerts";
  static final String TAG_DASHBOARD = "Dashboard";

  private static final List<String> TAG_ORDER =
      List.of(
          TAG_PUBLIC,
          TAG_ADMIN_AUTH,
          TAG_TENANTS,
          TAG_INTEGRATIONS,
          TAG_ENROLLMENTS,
          TAG_API_KEYS,
          TAG_AUTH_ATTEMPTS,
          TAG_ADMIN_ENROLLMENT,
          TAG_ADMIN_PROVISIONING,
          TAG_AUDIT_LOGS,
          TAG_ENCRYPTION_KEYS,
          TAG_ALERTS,
          TAG_DASHBOARD);

  private static final String PUBLIC_DESCRIPTION =
      "Unauthenticated instance metadata, evaluator preview signup, and related public endpoints";

  private static final Map<String, String> DEFAULT_DESCRIPTIONS =
      Map.ofEntries(
          Map.entry(TAG_PUBLIC, PUBLIC_DESCRIPTION),
          Map.entry(
              TAG_ADMIN_AUTH,
              "Administrator authentication and session management for passwordless login and"
                  + " recovery"),
          Map.entry(TAG_TENANTS, "Tenant management API"),
          Map.entry(TAG_INTEGRATIONS, "Integration management API"),
          Map.entry(TAG_ENROLLMENTS, "Enrollment management API"),
          Map.entry(
              TAG_API_KEYS,
              "API key management for machine-to-machine (Integration API) authentication"),
          Map.entry(TAG_AUTH_ATTEMPTS, "Authentication attempt management API"),
          Map.entry(
              TAG_ADMIN_ENROLLMENT,
              "Administrator enrollment management and device recovery after loss"),
          Map.entry(TAG_ADMIN_PROVISIONING, "Administrator provisioning API"),
          Map.entry(
              TAG_AUDIT_LOGS,
              "Audit log query and reporting API for security monitoring and compliance"),
          Map.entry(
              TAG_ENCRYPTION_KEYS, "Encryption key lifecycle management and rotation operations"),
          Map.entry(
              TAG_ALERTS,
              "Operator-facing alert subsystem (Global Admin only). Surfaces internal Ezkey"
                  + " signals such as undeclared audit chain gaps; resolved automatically by the"
                  + " matching producer workflow."),
          Map.entry(TAG_DASHBOARD, "Dashboard overview and aggregated stats for Admin UI"));

  @Override
  public void customise(OpenAPI openApi) {
    if (openApi == null) {
      return;
    }
    openApi.setTags(buildOrderedTags(openApi));
    openApi.addExtension("x-tagGroups", buildTagGroups());
  }

  private static List<Tag> buildOrderedTags(OpenAPI openApi) {
    Map<String, String> descriptions = new LinkedHashMap<>(DEFAULT_DESCRIPTIONS);
    if (openApi.getTags() != null) {
      for (Tag existing : openApi.getTags()) {
        if (existing == null || existing.getName() == null) {
          continue;
        }
        if (TAG_PUBLIC.equals(existing.getName())) {
          descriptions.put(TAG_PUBLIC, PUBLIC_DESCRIPTION);
        } else if (existing.getDescription() != null) {
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

  private static List<Map<String, Object>> buildTagGroups() {
    return List.of(
        Map.of("name", "Getting started", "tags", List.of(TAG_PUBLIC, TAG_ADMIN_AUTH)),
        Map.of(
            "name",
            "Tenant and integration setup",
            "tags",
            List.of(TAG_TENANTS, TAG_INTEGRATIONS, TAG_ENROLLMENTS, TAG_API_KEYS)),
        Map.of("name", "MFA operations", "tags", List.of(TAG_AUTH_ATTEMPTS, TAG_ADMIN_ENROLLMENT)),
        Map.of("name", "Administration", "tags", List.of(TAG_ADMIN_PROVISIONING)),
        Map.of(
            "name",
            "Platform operations",
            "tags",
            List.of(TAG_AUDIT_LOGS, TAG_ENCRYPTION_KEYS, TAG_ALERTS, TAG_DASHBOARD)));
  }
}
