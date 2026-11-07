/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminOpenInViewConfig
 */

package org.ezkey.admin.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Open-in-view as before on every route, except the QR delivery send.
 *
 * <p>With open-in-view, the session holds its database connection until the response. A send calls
 * a slow external service, so it must not hold one. {@code spring.jpa.open-in-view=false} turns off
 * Spring Boot's own interceptor, and this registers the same interceptor with that one exclusion.
 *
 * @since 2026
 */
@Configuration
public class AdminOpenInViewConfig implements WebMvcConfigurer {

  /** Routes that call an external service and must not hold a database connection. */
  static final String QR_DELIVERY_SEND_PATTERN = "/api/v1/enrollments/*/qr-delivery";

  private final ObjectProvider<EntityManagerFactory> entityManagerFactory;

  /**
   * Builds the configuration.
   *
   * @param entityManagerFactory absent in web-only test slices
   */
  public AdminOpenInViewConfig(ObjectProvider<EntityManagerFactory> entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    EntityManagerFactory factory = entityManagerFactory.getIfAvailable();
    if (factory == null) {
      return;
    }
    OpenEntityManagerInViewInterceptor interceptor = new OpenEntityManagerInViewInterceptor();
    interceptor.setEntityManagerFactory(factory);
    registry.addWebRequestInterceptor(interceptor).excludePathPatterns(QR_DELIVERY_SEND_PATTERN);
  }
}
