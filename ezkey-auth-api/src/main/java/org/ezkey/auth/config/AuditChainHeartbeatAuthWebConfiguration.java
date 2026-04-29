/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: registers peripheral heartbeat interceptor for Auth API MVC.
 */

package org.ezkey.auth.config;

import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.audit.integrity.AuditChainHeartbeatPeripheralInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the audit-chain heartbeat interceptor for Auth API peripheral endpoints.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Configuration
public class AuditChainHeartbeatAuthWebConfiguration {

  /**
   * Interceptor bean gated by heartbeat configuration inside {@link
   * AuditChainHeartbeatGuardService}.
   *
   * @param guardService heartbeat evaluation entry point (shared core bean)
   * @return interceptor instance wired into MVC
   */
  @Bean
  public AuditChainHeartbeatPeripheralInterceptor auditChainHeartbeatPeripheralInterceptor(
      AuditChainHeartbeatGuardService guardService) {
    return new AuditChainHeartbeatPeripheralInterceptor(guardService);
  }

  /**
   * MVC registrar scoped to Auth API module classpath only.
   *
   * @param interceptor interceptor bean for peripheral MFA endpoints
   * @return WebMvcConfigurer hook
   */
  @Bean
  public WebMvcConfigurer auditChainHeartbeatInterceptorRegistration(
      AuditChainHeartbeatPeripheralInterceptor interceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/auth-attempts/**");
      }
    };
  }
}
