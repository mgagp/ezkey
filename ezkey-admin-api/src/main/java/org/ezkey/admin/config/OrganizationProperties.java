/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: OrganizationProperties
 * Description: Configuration properties for the organization hosting this Ezkey instance.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the organization hosting this Ezkey instance.
 *
 * <p>These properties define the system tenant that represents the organization managing this Ezkey
 * instance. This is particularly important for self-hosted deployments where each instance belongs
 * to a specific organization.
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>
 * ezkey.organization.name=Acme Corporation
 * ezkey.organization.description=Acme Corp Ezkey MFA Instance
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.organization")
public class OrganizationProperties {

  /**
   * Name of the organization hosting this Ezkey instance.
   *
   * <p>This name is used to create the system tenant that represents the organization. Defaults to
   * "Ezkey System" if not specified.
   */
  private String name = "Ezkey System";

  /**
   * Description of the organization or this Ezkey instance.
   *
   * <p>Provides additional context about the organization or the purpose of this Ezkey instance.
   */
  private String description = "Default system tenant for global administrators";

  /**
   * Gets the organization name.
   *
   * @return the organization name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the organization name.
   *
   * @param name the organization name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the organization description.
   *
   * @return the organization description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the organization description.
   *
   * @param description the organization description
   */
  public void setDescription(String description) {
    this.description = description;
  }
}
