/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;

/**
 * Groups stored demo-device enrollments by tenant for hierarchical display on the Ezkey app home.
 */
public final class EnrollmentTenantGrouper {

  private static final String PLATFORM_TENANT_DISPLAY_NAME = "Platform";

  private static final String UNKNOWN_TENANT_NAME = "Unknown tenant";

  private EnrollmentTenantGrouper() {}

  /**
   * Groups enrollment records by tenant metadata and sorts tenants and enrollments for stable UI
   * ordering.
   *
   * @param enrollments enrollments loaded from the local store (may be empty, never null)
   * @return ordered tenant groups with sorted enrollment lists
   */
  public static List<TenantGroupViewModel> group(List<Record> enrollments) {
    Map<TenantKey, List<Record>> grouped = new HashMap<>();

    for (Record enrollment : enrollments) {
      Integer tenantId = enrollment.tenantId();
      String tenantName = normalizeTenantName(enrollment.tenantName(), enrollment.tenantId());
      String tenantDescription = normalizeTenantDescription(enrollment.tenantDescription());

      TenantKey key = new TenantKey(tenantId, tenantName, tenantDescription);
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(enrollment);
    }

    Comparator<Record> enrollmentComparator =
        Comparator.comparing(
                Record::integrationName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(
                Record::enrollmentName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Record::enrollmentId, Comparator.nullsLast(Integer::compareTo));

    for (List<Record> groupItems : grouped.values()) {
      groupItems.sort(enrollmentComparator);
    }

    Comparator<TenantKey> tenantComparator =
        Comparator.comparing(
                (TenantKey k) -> UNKNOWN_TENANT_NAME.equals(k.tenantName()) ? 1 : 0,
                Integer::compareTo)
            .thenComparing(
                TenantKey::tenantName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(TenantKey::tenantId, Comparator.nullsLast(Integer::compareTo));

    return grouped.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(tenantComparator))
        .map(
            e ->
                new TenantGroupViewModel(
                    e.getKey().tenantId(),
                    e.getKey().tenantName(),
                    e.getKey().tenantDescription(),
                    e.getValue()))
        .toList();
  }

  /**
   * Normalizes a tenant name for display: trims non-blank names, otherwise substitutes platform or
   * unknown labels consistent with home-screen grouping.
   *
   * @param tenantName raw tenant name from the API or store
   * @param tenantId tenant id, or {@code null} for platform scope
   * @return non-blank display name
   */
  public static String normalizeTenantName(String tenantName, Integer tenantId) {
    if (tenantName != null && !tenantName.isBlank()) {
      return tenantName.trim();
    }
    if (tenantId == null) {
      return PLATFORM_TENANT_DISPLAY_NAME;
    }
    return UNKNOWN_TENANT_NAME;
  }

  private static String normalizeTenantDescription(String tenantDescription) {
    if (tenantDescription == null || tenantDescription.isBlank()) {
      return null;
    }
    return tenantDescription.trim();
  }

  private record TenantKey(Integer tenantId, String tenantName, String tenantDescription) {}

  /**
   * View model for tenant enrollment groups on the Ezkey app home.
   *
   * @param tenantId tenant identifier, or {@code null} for platform-scope enrollments
   * @param tenantName human-readable tenant name
   * @param tenantDescription optional tenant description text
   * @param enrollments enrollment records in this tenant group
   */
  public record TenantGroupViewModel(
      Integer tenantId, String tenantName, String tenantDescription, List<Record> enrollments) {}
}
