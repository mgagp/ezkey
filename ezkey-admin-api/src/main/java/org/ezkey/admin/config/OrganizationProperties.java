/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
 * ezkey.organization.about-url=https://www.example.com/about-ezkey
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
   * Short description of this Ezkey deployment for operators and public metadata (e.g. login shell,
   * About).
   *
   * <p>Intended to describe the <em>installation</em> in its context (e.g. on-prem or hosted MFA
   * for your organization), not the technical role of the system tenant or who is logged in.
   * Customers typically override this via configuration.
   */
  private String description = "Ezkey MFA instance for your organization";

  /**
   * Optional URL for "About" / learn more in the Admin UI (e.g. page describing this instance).
   *
   * <p>Not a secret; exposed via {@code GET /api/v1/public/instance-info}.
   */
  private String aboutUrl;

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

  /**
   * Gets the optional About / learn more URL.
   *
   * @return URL or null if not configured
   */
  public String getAboutUrl() {
    return aboutUrl;
  }

  /**
   * Sets the optional About / learn more URL.
   *
   * @param aboutUrl URL or null
   */
  public void setAboutUrl(String aboutUrl) {
    this.aboutUrl = aboutUrl;
  }
}
