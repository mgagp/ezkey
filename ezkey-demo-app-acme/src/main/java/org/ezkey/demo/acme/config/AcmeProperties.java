/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AcmeProperties
 * Description: Type-safe configuration properties for ACME demo application.
 */

package org.ezkey.demo.acme.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for ACME demo application.
 *
 * <p>Binds externalized configuration from application.properties with validation and type safety.
 * Configuration changes require container restart to take effect.
 *
 * <p>Optional multi-tenant access-code slots bind from {@code ezkey.access-codes.<slotId>.*}.
 * {@link org.ezkey.demo.acme.service.AccessCodeService} validates codes at startup and uses only
 * SHA-256 digests for lookup and comparison. The plaintext code remains in this bean (and the
 * Spring {@code Environment}) after binding — do not log slot codes.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey")
public class AcmeProperties {

  private String adminApiUrl;
  private String integrationKey;
  private String secretKey;

  /**
   * Named access-code slots for the Play closed-testing path ({@code /t/{code}}).
   *
   * <p>Key = slot id (e.g. {@code northwind}). Values come from {@code
   * ezkey.access-codes.&lt;slot&gt;.{code,integration-key,secret-key,label}}.
   */
  private Map<String, AccessCodeSlotProperties> accessCodes = new LinkedHashMap<>();

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

  public Map<String, AccessCodeSlotProperties> getAccessCodes() {
    return accessCodes;
  }

  public void setAccessCodes(Map<String, AccessCodeSlotProperties> accessCodes) {
    this.accessCodes = accessCodes != null ? accessCodes : new LinkedHashMap<>();
  }

  /**
   * Raw access-code slot binding from configuration (plaintext code stays on the bean; digests are
   * used for lookup).
   */
  public static class AccessCodeSlotProperties {

    private String code;
    private String integrationKey;
    private String secretKey;
    private String label;

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
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

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }
  }
}
