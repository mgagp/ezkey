/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AcmeProperties
 * Description: Type-safe configuration properties for ACME demo application.
 */

package org.ezkey.demo.acme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for ACME demo application.
 *
 * <p>Binds externalized configuration from application.properties with validation and type safety.
 * Configuration changes require container restart to take effect.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey")
public class AcmeProperties {

  private String adminApiUrl;
  private String integrationKey;
  private String secretKey;
  private Users users = new Users();

  public String getAdminApiUrl() {
    return adminApiUrl;
  }

  public void setAdminApiUrl(String adminApiUrl) {
    this.adminApiUrl = adminApiUrl;
  }

  public String getIntegrationKey() {
    return integrationKey;
  }

  public void setIntegrationKey(String integrationKey) {
    this.integrationKey = integrationKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public Users getUsers() {
    return users;
  }

  public void setUsers(Users users) {
    this.users = users;
  }

  /** Users mapping configuration. */
  public static class Users {
    private String file;
    private Integer checkInterval;

    public String getFile() {
      return file;
    }

    public void setFile(String file) {
      this.file = file;
    }

    public Integer getCheckInterval() {
      return checkInterval;
    }

    public void setCheckInterval(Integer checkInterval) {
      this.checkInterval = checkInterval;
    }
  }
}
