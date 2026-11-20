/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PublicSurfaceNoSelfServiceTest
 * Description: Pins the single onboarding path after the self-registration retirement (#1368).
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scans every Admin API controller and pins what the retirement of anonymous self-registration
 * (#1365) promises: the two retired routes are gone, and nothing under the unauthenticated {@code
 * /api/v1/public/} prefix accepts anything but {@code GET}. Tenants and administrators are only
 * created by an authenticated Global Admin (or a Tenant Admin for its peers).
 *
 * @since 2026
 */
@DisplayName("Admin API public surface has no self-service path (#1368)")
class PublicSurfaceNoSelfServiceTest {

  private static final String PUBLIC_PREFIX = "/api/v1/public/";

  private static List<Mapping> mappings;

  /**
   * One handler method: full path and HTTP methods.
   *
   * @param path class-level path plus method-level path
   * @param methods HTTP methods, empty when the mapping accepts all
   * @param handler controller class and method, for failure messages
   */
  record Mapping(String path, List<RequestMethod> methods, String handler) {}

  @BeforeAll
  static void scanControllers() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    mappings = new ArrayList<>();
    for (BeanDefinition definition : scanner.findCandidateComponents("org.ezkey")) {
      Class<?> controller = Class.forName(definition.getBeanClassName());
      RequestMapping classMapping =
          AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
      String[] prefixes =
          classMapping == null || classMapping.path().length == 0
              ? new String[] {""}
              : classMapping.path();
      for (Method method : controller.getDeclaredMethods()) {
        RequestMapping methodMapping =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (methodMapping == null) {
          continue;
        }
        String[] suffixes =
            methodMapping.path().length == 0 ? new String[] {""} : methodMapping.path();
        for (String prefix : prefixes) {
          for (String suffix : suffixes) {
            mappings.add(
                new Mapping(
                    prefix + suffix,
                    List.of(methodMapping.method()),
                    controller.getSimpleName() + "#" + method.getName()));
          }
        }
      }
    }
  }

  @Test
  @DisplayName("the scan sees the controllers (guards against an empty, vacuous pass)")
  void scanFindsKnownRoutes() {
    assertThat(mappings)
        .extracting(Mapping::path)
        .contains("/api/v1/public/instance-info", "/api/v1/admin/auth/activate");
  }

  @Test
  @DisplayName("the evaluator signup and temporary console routes no longer exist")
  void retiredRoutesAreAbsent() {
    assertThat(mappings)
        .extracting(Mapping::path)
        .noneMatch(path -> path.contains("evaluator-signup"))
        .noneMatch(path -> path.contains("evaluator-temp"));
  }

  @Test
  @DisplayName("every unauthenticated /api/v1/public/ route is GET only")
  void publicRoutesAreReadOnly() {
    List<Mapping> publicMappings =
        mappings.stream().filter(m -> m.path().startsWith(PUBLIC_PREFIX)).toList();

    assertThat(publicMappings).isNotEmpty();
    assertThat(publicMappings)
        .allSatisfy(m ->
            assertThat(m.methods())
                .as("%s %s", m.handler(), m.path())
                .containsExactly(RequestMethod.GET));
  }
}
