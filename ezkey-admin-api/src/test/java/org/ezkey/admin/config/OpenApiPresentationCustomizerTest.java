/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */
package org.ezkey.admin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deterministic {@code x-tagGroups} output, so {@code update-specs} does not reorder the generated
 * spec from one run to the next (observed while refreshing specs for #1506).
 *
 * @since 2026
 */
class OpenApiPresentationCustomizerTest {

  @Test
  @DisplayName("x-tagGroups entries keep the key order name, then tags")
  @SuppressWarnings("unchecked")
  void tagGroups_haveStableKeyOrder() {
    OpenAPI openApi = new OpenAPI();

    new OpenApiPresentationCustomizer().customise(openApi);

    List<Map<String, Object>> groups =
        (List<Map<String, Object>>) openApi.getExtensions().get("x-tagGroups");
    assertFalse(groups.isEmpty());
    for (Map<String, Object> group : groups) {
      assertEquals(List.of("name", "tags"), List.copyOf(group.keySet()));
    }
    assertEquals("Getting started", groups.get(0).get("name"));
  }
}
